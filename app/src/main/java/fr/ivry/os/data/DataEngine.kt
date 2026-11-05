package fr.ivry.os.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val SEC = 1_000L
private const val MIN = 60_000L

/**
 * IVRY DATA ENGINE : une boucle par source, cache disque, dernière valeur connue,
 * pause complète quand le launcher n'est plus au premier plan, rythme réduit en inactivité.
 */
class DataEngine(context: Context, private val settings: SettingsRepo, private val scope: CoroutineScope) {
    private val ctx = context.applicationContext
    private val cache = DiskCache(File(ctx.cacheDir, "ivry-data"))

    val weatherNow = MutableStateFlow(Live<CurrentWeather>(staleAfterMs = 45 * MIN))
    val forecast = MutableStateFlow(Live<Forecast>(staleAfterMs = 90 * MIN))
    val metro = MutableStateFlow(Live<Transit>(staleAfterMs = 3 * MIN))
    val rer = MutableStateFlow(Live<Transit>(staleAfterMs = 3 * MIN))
    val velib = MutableStateFlow(Live<Velib>(staleAfterMs = 4 * MIN))
    val news = MutableStateFlow(Live<List<String>>(staleAfterMs = 90 * MIN))
    val agenda = MutableStateFlow(Live<List<AgendaEvent>>(staleAfterMs = 6 * 60 * MIN))
    val online = MutableStateFlow(true)
    val idle = MutableStateFlow(false)

    private val foreground = MutableStateFlow(false)
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @Volatile private var lastInteraction = System.currentTimeMillis()
    @Volatile private var velibStation: Pair<String, String>? = null
    @Volatile private var velibStationCode: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        watchNetwork()

        loop("weather_now", 15 * MIN, weatherNow, { Http.get(Sources.CURRENT_URL) }) { t, _ -> Sources.parseCurrent(t) }
        loop("forecast", 30 * MIN, forecast, { Http.get(Sources.FORECAST_URL) }) { t, _ -> Sources.parseForecast(t) }
        // Horaires (stop-monitoring) et messages trafic (general-message) sont deux API PRIM
        // distinctes, chacune avec son propre quota (≈1000 requêtes/jour pour une clé récente).
        // Les horaires sont interrogés à ~1x/min — PRIM ne les republie pas plus vite de toute
        // façon — pour rester vraiment temps réel ; les messages trafic, qui changent rarement,
        // beaucoup moins souvent, afin de ne pas gaspiller le quota pour rien.
        transitLoop("metro7", metro, { it.metroStopRef }, { it.metroLineRef }, { it.metroFilter })
        transitLoop("rerC", rer, { it.rerStopRef }, { it.rerLineRef }, { it.rerFilter })
        loop("velib", 60 * SEC, velib, { fetchVelib(it) }) { t, _ -> Sources.parseVelib(t) }
        loop("news", 10 * MIN, news, { p ->
            News.toJson(News.parse(Http.get(p.newsUrl, mapOf("Accept" to "application/rss+xml, application/xml, text/xml"))))
        }) { t, _ -> News.fromJson(t) }
        loop("agenda", 15 * MIN, agenda, { p ->
            if (p.icalUrl.isBlank()) throw IllegalStateException("Agenda non configuré")
            val url = p.icalUrl.trim().replaceFirst("webcal://", "https://")
            Agenda.toJson(Agenda.today(Http.get(url, mapOf("Accept" to "text/calendar"))))
        }) { t, _ -> Agenda.fromJson(t) }

        scope.launch {
            while (isActive) {
                delay(30 * SEC)
                idle.value = System.currentTimeMillis() - lastInteraction > 5 * MIN
            }
        }
        // Toute modification des sources dans les paramètres déclenche une actualisation.
        scope.launch {
            settings.prefs
                .map { listOf(it.proxyUrl, it.proxyToken, it.metroStopRef, it.metroLineRef, it.metroFilter,
                    it.rerStopRef, it.rerLineRef, it.rerFilter, it.velibCode, it.newsUrl, it.icalUrl) }
                .distinctUntilChanged().drop(1)
                .collect { refreshNow() }
        }
    }

    fun refreshNow() { refreshSignal.tryEmit(Unit) }

    fun setForeground(v: Boolean) { foreground.value = v }

    fun onUserInteraction() {
        lastInteraction = System.currentTimeMillis()
        if (idle.value) idle.value = false
    }

    private fun <T> loop(
        key: String,
        intervalMs: Long,
        target: MutableStateFlow<Live<T>>,
        fetch: suspend (Prefs) -> String,
        slowWhenIdle: Boolean = true,
        pauseAtNight: Boolean = false,
        parse: (String, Prefs) -> T,
    ) {
        scope.launch(Dispatchers.IO) {
            cache.read(key)?.let { (time, text) ->
                val prefs = settings.prefs.first()
                runCatching { parse(text, prefs) }
                    .onSuccess { target.value = target.value.copy(value = it, fetchedAt = time) }
            }
            while (isActive) {
                foreground.first { it }   // aucun appel réseau quand le launcher est en arrière-plan
                if (pauseAtNight && isNightClosure(System.currentTimeMillis())) {
                    // Réseau fermé la nuit : on évite d'interroger PRIM pour rien (et de se faire limiter).
                    withTimeoutOrNull(5 * MIN) { refreshSignal.first() }
                    continue
                }
                val prefs = settings.prefs.first()
                try {
                    val text = fetch(prefs)
                    val parsed = parse(text, prefs)
                    cache.write(key, text)
                    target.value = target.value.copy(value = parsed, fetchedAt = System.currentTimeMillis(), error = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    target.value = target.value.copy(error = describe(e))
                }
                val wait = if (idle.value && slowWhenIdle) intervalMs * 3 else intervalMs
                withTimeoutOrNull(wait) { refreshSignal.first() }
            }
        }
    }

    /**
     * Fermeture nocturne approximative du réseau (Métro/RER francilien) : ~01h15–05h30.
     * Horaires génériques publiés par IDFM/RATP, pas une donnée temps réel — sert seulement
     * à suspendre les appels PRIM quand il n'y a de toute façon plus de service.
     */
    private fun isNightClosure(ms: Long): Boolean {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(PARIS).toLocalTime()
        return t.isAfter(java.time.LocalTime.of(1, 15)) && t.isBefore(java.time.LocalTime.of(5, 30))
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "Pas de connexion"
        is SocketTimeoutException -> "Délai dépassé"
        is HttpStatusException -> when (e.code) {
            429 -> "Trop de requêtes, réessai dans un instant"
            in 500..599 -> "Service indisponible, réessai en cours"
            401, 403 -> "Accès refusé (jeton à vérifier)"
            404 -> "Ressource introuvable"
            else -> "Erreur réseau (${e.code})"
        }
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun headers(p: Prefs): Map<String, String> =
        if (p.proxyToken.isBlank()) emptyMap() else mapOf("x-ivry-token" to p.proxyToken)

    /**
     * Horaires et messages trafic d'une ligne, interrogés indépendamment à leur propre rythme
     * (voir le commentaire dans start()). Les deux alimentent le même [Live]<Transit> : chacun ne
     * touche que sa partie (departures ou messages), l'autre partie et la dernière bonne valeur
     * connue sont préservées en cas d'erreur, pour ne jamais faire disparaître des données valides.
     */
    private fun transitLoop(
        key: String,
        target: MutableStateFlow<Live<Transit>>,
        stopRefOf: (Prefs) -> String,
        lineRefOf: (Prefs) -> String,
        filterOf: (Prefs) -> String,
    ) {
        val smKey = "${key}_sm"
        val gmKey = "${key}_gm"

        // Horaires : quasi temps réel.
        scope.launch(Dispatchers.IO) {
            cache.read(smKey)?.let { (time, text) ->
                val prefs = settings.prefs.first()
                runCatching { Sources.parseStopMonitoring(text, filterOf(prefs)) }.onSuccess { deps ->
                    val cur = target.value.value
                    target.value = target.value.copy(value = (cur ?: Transit(deps, emptyList(), false)).copy(departures = deps), fetchedAt = time)
                }
            }
            while (isActive) {
                foreground.first { it }
                if (isNightClosure(System.currentTimeMillis())) {
                    withTimeoutOrNull(5 * MIN) { refreshSignal.first() }
                    continue
                }
                val prefs = settings.prefs.first()
                val stop = stopRefOf(prefs)
                try {
                    if (prefs.proxyUrl.isBlank()) throw IllegalStateException("Proxy PRIM non configuré")
                    if (stop.isBlank()) throw IllegalStateException("Arrêt non configuré")
                    val text = Http.get(Sources.stopMonitoringUrl(prefs.proxyUrl, stop), headers(prefs))
                    cache.write(smKey, text)
                    val deps = Sources.parseStopMonitoring(text, filterOf(prefs))
                    val cur = target.value.value
                    target.value = target.value.copy(
                        value = (cur ?: Transit(deps, emptyList(), false)).copy(departures = deps),
                        fetchedAt = System.currentTimeMillis(), error = null,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    target.value = target.value.copy(error = describe(e))
                }
                val wait = if (idle.value) 3 * MIN else 60 * SEC
                withTimeoutOrNull(wait) { refreshSignal.first() }
            }
        }

        // Messages trafic : changent rarement, interrogés beaucoup moins souvent.
        scope.launch(Dispatchers.IO) {
            cache.read(gmKey)?.let { (_, text) ->
                runCatching { Sources.parseMessages(text) }.onSuccess { messages ->
                    val cur = target.value.value
                    if (cur != null) target.value = target.value.copy(value = cur.copy(messages = messages, messagesAvailable = true))
                }
            }
            while (isActive) {
                foreground.first { it }
                if (isNightClosure(System.currentTimeMillis())) {
                    withTimeoutOrNull(5 * MIN) { refreshSignal.first() }
                    continue
                }
                val prefs = settings.prefs.first()
                val line = lineRefOf(prefs)
                if (prefs.proxyUrl.isNotBlank() && line.isNotBlank()) {
                    try {
                        val text = Http.get(Sources.generalMessageUrl(prefs.proxyUrl, line), headers(prefs))
                        cache.write(gmKey, text)
                        val messages = Sources.parseMessages(text)
                        val cur = target.value.value
                        if (cur != null) target.value = target.value.copy(value = cur.copy(messages = messages, messagesAvailable = true))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Les horaires restent affichés même si les messages trafic échouent.
                        val cur = target.value.value
                        if (cur != null) target.value = target.value.copy(value = cur.copy(messagesAvailable = false))
                    }
                }
                val wait = if (idle.value) 30 * MIN else 10 * MIN
                withTimeoutOrNull(wait) { refreshSignal.first() }
            }
        }
    }

    private suspend fun fetchVelib(p: Prefs): String {
        val code = p.velibCode.trim()
        if (velibStation == null || velibStationCode != code) {
            val info = Http.get(Sources.VELIB_INFO)
            velibStation = Sources.findVelibStation(info, code)
                ?: throw IllegalStateException("Station $code absente des données officielles")
            velibStationCode = code
        }
        val (id, name) = velibStation!!
        val status = Sources.findVelibStatus(Http.get(Sources.VELIB_STATUS), id)
            ?: throw IllegalStateException("Statut indisponible pour la station $code")
        return JSONObject().put("code", code).put("name", name).put("status", status).toString()
    }

    private fun watchNetwork() {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        online.value = cm.activeNetwork != null
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { online.value = true; refreshNow() }
                override fun onLost(network: Network) { online.value = false }
            })
        }
    }
}
