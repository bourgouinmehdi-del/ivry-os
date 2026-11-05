package fr.ivry.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ivry.os.MainViewModel
import fr.ivry.os.data.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

val M7_COLOR = Color(0xFFFA9ABA)
val RERC_COLOR = Color(0xFFFFCE00)

@Composable
fun HomeScreen(
    vm: MainViewModel,
    prefs: Prefs,
    apps: List<AppEntry>,
    onDetail: (Detail) -> Unit,
    onSection: (Section) -> Unit,
) {
    val p = LocalPalette.current
    val e = vm.engine
    val weather by e.weatherNow.collectAsStateWithLifecycle()
    val forecast by e.forecast.collectAsStateWithLifecycle()
    val metro by e.metro.collectAsStateWithLifecycle()
    val rer by e.rer.collectAsStateWithLifecycle()
    val velib by e.velib.collectAsStateWithLifecycle()
    val online by e.online.collectAsStateWithLifecycle()
    val news by e.news.collectAsStateWithLifecycle()
    val agenda by e.agenda.collectAsStateWithLifecycle()

    val canal = remember(apps) { AppsRepository.findCanal(apps) }
    val youtube = remember(apps) { AppsRepository.findYoutube(apps) }
    val others = remember(apps, prefs.favorites, prefs.launchCounts) {
        AppsRepository.secondary(apps, prefs, setOfNotNull(canal?.pkg, youtube?.pkg))
    }
    val videoCount = remember(apps) { apps.count { it.isVideo } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 12.dp)) {
        // ZONE 1 — identité et heure, posées dans le ciel
        IvryHeader(weather.value, online, { onSection(Section.SETTINGS) })
        val alerts = remember(weather.value, forecast.value, velib.value, metro.value, rer.value) {
            buildAlerts(weather.value, forecast.value, velib.value, metro.value, rer.value)
        }
        if (alerts.isNotEmpty()) AlertsRow(alerts, Modifier.padding(top = 6.dp))
        val update by vm.update.collectAsStateWithLifecycle()
        update.info?.let { info ->
            Plate(Modifier.padding(top = 6.dp).height(30.dp), shape = CutCornerShape(topEnd = 8.dp),
                background = Ivry.Jaune.copy(alpha = .9f), onClick = { vm.installUpdate() }) { f ->
                val text = when {
                    update.busy -> "Téléchargement de ${info.name}…"
                    update.message != null -> update.message!!
                    info.notes != null -> "${info.name} : ${info.notes} · OK pour installer"
                    else -> "Mise à jour disponible : ${info.name} · OK pour installer"
                }
                Text(
                    text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ivry.Asphalte,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
                )
            }
        }

        // La ville, dégagée, entre l'en-tête et les modules
        Spacer(Modifier.weight(.36f))

        // ZONE 2 — IVRY LIVE, plaques de verre posées sur la Seine
        Row(Modifier.fillMaxWidth().weight(.34f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherPanel(weather, forecast, Modifier.weight(1.3f)) { onDetail(Detail.WEATHER) }
            MetroBoard("7", M7_COLOR, "Mairie d'Ivry", "→ La Courneuve",
                metro, CutCornerShape(bottomStart = 22.dp), Modifier.weight(1f)) { onDetail(Detail.METRO) }
            RerBoard("C", RERC_COLOR, "Ivry-sur-Seine", "→ Paris, nord",
                rer, CutCornerShape(topStart = 22.dp), Modifier.weight(1f)) { onDetail(Detail.RER) }
            VelibPanel(velib, Modifier.weight(.9f)) { onDetail(Detail.VELIB) }
        }
        Spacer(Modifier.height(10.dp))

        // ZONE 3 — agenda du jour, puis les applis
        Row(Modifier.fillMaxWidth().weight(.20f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AgendaCard(agenda, prefs.icalUrl.isNotBlank(), Modifier.weight(1.15f))
            LazyRow(Modifier.weight(1.85f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                item(key = "canal") {
                    AppTile(canal, false, Modifier.fillMaxHeight().aspectRatio(1.2f), fallbackName = "myCANAL",
                        accent = Ivry.Ivoire, focusRequester = firstFocus) {
                        canal?.let { vm.requestLaunch(it.pkg) } ?: vm.openStore(AppsRepository.CANAL_STORE)
                    }
                }
                item(key = "youtube") {
                    AppTile(youtube, false, Modifier.fillMaxHeight().aspectRatio(1.2f), fallbackName = "YouTube",
                        accent = Color(0xFFFF0033)) {
                        youtube?.let { vm.requestLaunch(it.pkg) } ?: vm.openStore(AppsRepository.YOUTUBE_TV)
                    }
                }
                items(others, key = { it.pkg }) { app ->
                    AppTile(app, app.pkg in prefs.favorites, Modifier.fillMaxHeight().aspectRatio(1.2f)) {
                        vm.requestLaunch(app.pkg)
                    }
                }
            }
        }

        // Fil d'actualité défilant
        Spacer(Modifier.height(8.dp))
        NewsTicker(news, News.sourceName(prefs.newsUrl))
    }
}

/** Agenda du jour : événements à venir ou en cours, tirés de l'adresse iCal. */
@Composable
private fun AgendaCard(live: Live<List<AgendaEvent>>, configured: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val now = rememberTick(60_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(topStart = 18.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            PanelLabel("Agenda · aujourd'hui", p.accent)
            Spacer(Modifier.height(4.dp))
            val all = live.value
            val evs = all?.filter { it.allDay || it.end > now }
            when {
                !configured -> Text("Ajoute ton agenda dans Paramètres › Sources.", fontSize = 11.sp, lineHeight = 14.sp, color = p.textDim)
                evs == null -> Text(if (live.error != null) "Agenda indisponible pour le moment" else "Chargement…",
                    fontSize = 11.sp, color = p.textDim)
                evs.isEmpty() -> Text(if (all.isEmpty()) "Rien de prévu aujourd'hui" else "Plus rien de prévu aujourd'hui",
                    fontSize = 12.sp, color = p.textDim)
                else -> {
                    val shown = if (evs.size > 3) evs.take(2) else evs
                    shown.forEach { ev ->
                        val ongoing = !ev.allDay && ev.start <= now
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ev.allDay) "Journée" else hhmm(ev.start), fontSize = 12.sp, lineHeight = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = if (ongoing) p.focus else p.text,
                                modifier = Modifier.width(54.dp))
                            Text(ev.title, fontSize = 12.sp, lineHeight = 15.sp, color = p.text, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (evs.size > 3) Text("+ ${evs.size - 2} autres", fontSize = 11.sp, lineHeight = 14.sp, color = p.textDim)
                }
            }
        }
    }
}

/** Fil d'actualité défilant, sur toute la largeur, avancé à chaque image pour un mouvement régulier. */
@Composable
private fun NewsTicker(live: Live<List<String>>, source: String) {
    val items = live.value
    if (items.isNullOrEmpty()) return
    val text = remember(items) { items.joinToString("      •      ") + "      •      " }
    var width by remember { mutableIntStateOf(0) }
    val offset = remember { mutableFloatStateOf(0f) }
    val speed = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(width) {
        if (width == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) offset.floatValue = (offset.floatValue + (n - last) / 1_000_000_000f * speed) % width
                last = n
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(Color.Black.copy(alpha = .5f), CutCornerShape(topEnd = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().background(Ivry.Orange).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(source.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ivry.Asphalte,
                fontFamily = Condensed, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -offset.floatValue }) {
                Text(text, fontSize = 13.sp, color = Ivry.Ivoire, maxLines = 1, softWrap = false,
                    modifier = Modifier.onSizeChanged { width = it.width })
                Text(text, fontSize = 13.sp, color = Ivry.Ivoire, maxLines = 1, softWrap = false)
            }
        }
    }
}

fun deg(v: Double) = "${v.roundToInt()}°"
fun one(v: Double) = String.format(Locale.FRANCE, "%.1f", v)

fun minutesLabel(d: Departure, now: Long): String {
    val m = ((d.expectedAt - now) / 60_000L).toInt()
    return when {
        d.atStop && m <= 1 -> "À quai"
        m <= 0 -> "Imminent"
        else -> "$m min"
    }
}

// ---------- Modules IVRY LIVE ----------

@Composable
private fun WeatherPanel(weather: Live<CurrentWeather>, forecast: Live<Forecast>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(topEnd = 26.dp), onClick = onClick) {
        TerraceMark()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            PanelLabel("Météo", p.nature)
            Spacer(Modifier.height(2.dp))
            val w = weather.value
            if (w == null) {
                Text(weather.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(w.code, isNightHour(java.time.LocalTime.now(PARIS).hour, w.sunrise, w.sunset), Modifier.size(40.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(deg(w.temp), fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(wmoLabel(w.code), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("Ressenti ${deg(w.feels)}", fontSize = 11.sp, color = p.textDim)
                        Text("Min ${deg(w.min)} · Max ${deg(w.max)}", fontSize = 11.sp, color = p.textDim)
                    }
                }
                Text("Pluie ${one(w.precip)} mm · Vent ${w.wind.roundToInt()} km/h", fontSize = 10.5.sp, color = p.textDim)
            }
            Spacer(Modifier.weight(1f))
            forecast.value?.hourly?.take(5)?.let { hours ->
                Row(Modifier.fillMaxWidth().padding(end = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    hours.forEach { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${h.hour}h", fontSize = 9.5.sp, lineHeight = 11.sp, color = p.textDim)
                            WeatherIcon(h.code, isNightHour(h.hour, weather.value?.sunrise, weather.value?.sunset),
                                Modifier.padding(vertical = 1.dp).size(18.dp))
                            Text(deg(h.temp), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                            Text("${h.rainProb}%", fontSize = 9.sp, lineHeight = 11.sp, color = Ivry.Pluie)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Freshness(weather, now)
        }
    }
}

/**
 * Les deux modules transport reprennent le style des vrais panneaux voyageurs (fond noir,
 * roundel de ligne, chiffres ambrés) plutôt que le verre du reste de l'écran — comme une
 * vraie borne encastrée dans l'interface. Mêmes données, mêmes états de secours que le reste.
 */
@Composable
private fun MetroBoard(
    code: String, color: Color, station: String, direction: String,
    live: Live<Transit>, shape: Shape, modifier: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = shape, background = Color(0xFF0A0A0C), onClick = onClick) { f ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(21.dp).border(1.6.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(21.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(code, color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "$station  $direction", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Box(Modifier.background(Ivry.Jaune, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(hhmm(now), color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 10.5.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(color))
            val t = live.value
            val deps = t?.departures?.filter { it.expectedAt > now - 60_000 }
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetroColumn("1er métro", deps?.getOrNull(0), now, Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight(.55f).align(Alignment.CenterVertically).background(Color.White.copy(alpha = .14f)))
                MetroColumn("2e métro", deps?.getOrNull(1), now, Modifier.weight(1f))
            }
            MetroFooter(t, live, p)
        }
    }
}

@Composable
private fun MetroColumn(label: String, dep: Departure?, now: Long, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = .55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
        Spacer(Modifier.height(2.dp))
        if (dep == null) {
            Text("—", color = Ivry.Jaune.copy(alpha = .5f), fontSize = 24.sp, fontWeight = FontWeight.Black)
        } else {
            val mins = ((dep.expectedAt - now) / 60_000L).toInt().coerceAtLeast(0)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$mins", color = Ivry.Jaune, fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(3.dp))
                Text("min", color = Ivry.Jaune, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun MetroFooter(t: Transit?, live: Live<Transit>, p: Palette) {
    if (t == null) {
        Text(
            when {
                live.error?.contains("Proxy") == true -> "Ajoute le proxy PRIM dans Paramètres › Sources."
                live.error?.contains("Arrêt") == true -> "Renseigne l'arrêt dans Paramètres › Sources."
                else -> live.error ?: "Chargement…"
            },
            fontSize = 9.5.sp, lineHeight = 12.sp, color = Color.White.copy(alpha = .55f),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp),
        )
        return
    }
    val (txt, warn) = when {
        !t.messagesAvailable -> "Info trafic indisponible" to false
        t.messages.isEmpty() -> "Aucune perturbation signalée" to false
        else -> t.messages.first() to true
    }
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Info, null, tint = if (warn) p.warn else Color(0xFF1F2D4A), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            txt, fontSize = 9.sp, lineHeight = 11.sp, color = Color(0xFF111111),
            fontWeight = if (warn) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RerBoard(
    code: String, color: Color, station: String, direction: String,
    live: Live<Transit>, shape: Shape, modifier: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = shape, background = Color(0xFF0A0A0C), onClick = onClick) { f ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(19.dp).background(color, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    Text(code, color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "$station  $direction", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Text(hhmm(now), color = Ivry.Jaune, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            val t = live.value
            val deps = t?.departures?.filter { it.expectedAt > now - 60_000 }?.take(4)
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp)) {
                when {
                    t == null -> Text(
                        when {
                            live.error?.contains("Proxy") == true -> "Ajoute le proxy PRIM dans Paramètres › Sources."
                            live.error?.contains("Arrêt") == true -> "Renseigne l'arrêt dans Paramètres › Sources."
                            else -> live.error ?: "Chargement…"
                        },
                        fontSize = 9.5.sp, lineHeight = 12.sp, color = Color.White.copy(alpha = .55f),
                    )
                    deps.isNullOrEmpty() -> Text("Aucun passage annoncé", fontSize = 11.sp, color = Color.White.copy(alpha = .55f))
                    else -> deps.forEachIndexed { i, d ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                        RerRow(code, color, d, now)
                    }
                }
            }
            if (t != null) {
                val (txt, warn) = when {
                    !t.messagesAvailable -> "Info trafic indisponible" to false
                    t.messages.isEmpty() -> "Aucune perturbation signalée" to false
                    else -> t.messages.first() to true
                }
                Text(
                    txt, fontSize = 9.sp, lineHeight = 11.sp, color = if (warn) p.warn else Color.White.copy(alpha = .45f),
                    fontWeight = if (warn) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun RerRow(code: String, color: Color, d: Departure, now: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(15.dp).background(color, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
            Text(code, color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 8.5.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            shortDest(d.destination), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(hhmm(d.expectedAt), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, lineHeight = 13.sp)
            Text(
                minutesLabel(d, now), fontSize = 8.5.sp, lineHeight = 10.sp,
                color = if (d.atStop) Ivry.Jaune else Color.White.copy(alpha = .5f),
            )
        }
    }
}

@Composable
private fun VelibPanel(live: Live<Velib>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(bottomEnd = 26.dp), onClick = onClick) {
        TerraceMark(bottom = false)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            PanelLabel("Vélib'", p.nature)
            val v = live.value
            if (v == null) {
                Spacer(Modifier.height(6.dp))
                Text(live.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
            } else {
                Text(v.name, fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Count(v.mechanical, "mécaniques", p.nature, Modifier.weight(1f))
                    Count(v.ebike, "électriques", Ivry.Bleu, Modifier.weight(1f))
                }
                Spacer(Modifier.height(3.dp))
                Text("${v.docks} bornettes libres", fontSize = 10.5.sp, color = p.textDim)
                Spacer(Modifier.height(3.dp))
                val ok = v.installed && v.renting
                Tag(if (ok) "EN SERVICE" else "HORS SERVICE", if (ok) p.nature else p.warn)
            }
            Spacer(Modifier.weight(1f))
            Freshness(live, now)
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text("$n", fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(color))
        Text(label, fontSize = 9.sp, lineHeight = 11.sp, color = p.textDim, maxLines = 1)
    }
}

// ---------- Applications ----------

/** Carte d'appli prioritaire au style d'IVRY OS : icône officielle, nom, fine barre de couleur. */
@Composable
private fun AppCard(
    title: String, subtitle: String, app: AppEntry?, storePkg: String, accent: Color,
    modifier: Modifier, focusRequester: FocusRequester?, vm: MainViewModel,
) {
    val p = LocalPalette.current
    Plate(
        modifier, shape = CutCornerShape(topEnd = 18.dp), focusRequester = focusRequester,
        onClick = { if (app != null) vm.requestLaunch(app.pkg) else vm.openStore(storePkg) },
    ) { f ->
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(4.dp).background(accent))
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .3f)),
                contentAlignment = Alignment.Center,
            ) {
                if (app?.icon != null) Image(bitmap = app.icon, contentDescription = title, modifier = Modifier.fillMaxSize().padding(4.dp))
                else Text(title.take(1), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(subtitle.uppercase(), fontSize = 10.sp, letterSpacing = 1.6.sp, color = p.textDim, fontWeight = FontWeight.Bold,
                    fontFamily = Condensed)
                Text(title, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                Text(
                    when { app == null -> "OK pour l'installer"; f -> "OK pour lancer"; else -> "Lancement direct" },
                    fontSize = 11.sp, color = if (f) p.focus else p.textDim,
                )
            }
        }
    }
}

/** Carte « salle de quartier » : marquise lumineuse, applis vidéo à l'affiche. */
@Composable
private fun CinemaCard(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(topEnd = 30.dp), background = Color(0xFF1C1411), onClick = onClick) { f ->
        val bulb = if (f) Ivry.Jaune else Ivry.Sable.copy(alpha = .45f)
        Canvas(Modifier.fillMaxSize()) {
            val step = 9.dp.toPx(); val r = 1.6.dp.toPx(); val inset = 5.dp.toPx()
            var x = step / 2
            while (x < size.width - 30.dp.toPx()) {
                drawCircle(bulb, r, Offset(x, inset)); drawCircle(bulb, r, Offset(x, size.height - inset)); x += step
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("À L'AFFICHE", fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                color = Ivry.Jaune)
            Column {
                Text("$count", fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                Text(if (count > 1) "applis vidéo" else "appli vidéo", fontSize = 11.sp, color = p.textDim)
            }
            Text("Cinéma ›", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (f) p.focus else p.textDim)
        }
    }
}

// ---------- Alertes utiles ----------

/** Alertes tirées uniquement des données réelles ; aucune n'est affichée si rien ne le justifie. */
private fun buildAlerts(w: CurrentWeather?, f: Forecast?, v: Velib?, metro: Transit?, rer: Transit?): List<String> {
    val out = ArrayList<String>()
    // Perturbations trafic : priorité, car elles changent le trajet du jour.
    metro?.messages?.firstOrNull()?.let { out += "Métro 7 : $it" }
    rer?.messages?.firstOrNull()?.let { out += "RER C : $it" }
    val rainingNow = w != null && (w.code in 51..67 || w.code in 80..82 || w.code >= 95)
    if (w != null && f != null && !rainingNow) {
        f.hourly.drop(1).take(3).firstOrNull { it.rainProb >= 60 }?.let {
            out += "Pluie probable vers ${it.hour} h : prends un parapluie"
        }
    }
    if (w != null && w.gusts >= 60) out += "Rafales jusqu'à ${w.gusts.roundToInt()} km/h"
    f?.daily?.firstOrNull()?.let { if (it.min <= 0.0) out += "Gel possible demain matin (${deg(it.min)})" }
    if (v != null) when {
        v.mechanical + v.ebike == 0 -> out += "Plus aucun vélo à la station Vélib'"
        v.ebike == 0 -> out += "Plus de vélo électrique à la station Vélib'"
    }
    if (v != null && v.docks == 0) out += "Plus de bornette libre pour rendre un vélo"
    return out.take(3)
}

@Composable
private fun AlertsRow(alerts: List<String>, modifier: Modifier) {
    val p = LocalPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        alerts.forEach { a ->
            Row(
                Modifier.background(Color.Black.copy(alpha = .45f), CutCornerShape(topEnd = 8.dp))
                    .border(1.dp, p.warn.copy(alpha = .55f), CutCornerShape(topEnd = 8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Warning, null, tint = p.warn, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(a, fontSize = 12.sp, color = Ivry.Ivoire, maxLines = 1)
            }
        }
    }
}

/** « Pont de Rungis Aéroport d'Orly » → « Pont de Rungis », « La Courneuve-8-Mai-1945 » → « La Courneuve ». */
private fun shortDest(d: String): String {
    val sb = StringBuilder()
    for (w in d.replace('-', ' ').split(' ').filter { it.isNotBlank() }) {
        if (sb.length >= 9) break
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(w)
    }
    return sb.toString()
}
