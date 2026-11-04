package fr.ivry.os.data

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "ivry_settings")

/** RER C à Ivry : on écarte les trains vers le sud pour ne garder que ceux vers Paris et le nord. */
const val RER_NORD = "!Juvisy,!Brétigny,!Bretigny,!Étampes,!Etampes,!Dourdan,!Massy,!Rungis,!Orly,!Choisy,!Ardoines,!Athis,!Savigny,!Saint-Michel-sur-Orge,!Vitry"

enum class ThemeName(val label: String, val tagline: String) {
    MANUFACTURE("Manufacture", "Brique, acier, noir industriel"),
    RENAUDIE("Renaudie", "Géométrie, béton, sable"),
    GAILHOUSTET("Gailhoustet", "Béton clair, terrasses, végétal"),
    POPULAIRE("Populaire", "Orange, jaune, affiches"),
    CONFLUENCES("Confluences", "Bleu, vert, lumière"),
}

data class Prefs(
    val theme: ThemeName = ThemeName.MANUFACTURE,
    val permanentDisplay: Boolean = false,
    val antiBurnIn: Boolean = true,
    val dimLevel: Int = 0,          // 0..6 → 0 à 60 %
    val darkBoost: Boolean = false,
    val performance: Boolean = false,
    val glass: Int = 9,             // 2..9 → opacité du verre
    val proxyUrl: String = "",
    val proxyToken: String = "",
    val metroStopRef: String = "",
    val metroLineRef: String = "STIF:Line::C01377:",
    val metroFilter: String = "Courneuve",
    val rerStopRef: String = "",
    val rerLineRef: String = "STIF:Line::C01727:",
    val rerFilter: String = RER_NORD,
    val velibCode: String = "42021",
    val newsUrl: String = News.DEFAULT_URL,
    val icalUrl: String = "",
    val metroWalk: Int = 5,
    val rerWalk: Int = 8,
    val favorites: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val appOrder: List<String> = emptyList(),
    val launchCounts: Map<String, Int> = emptyMap(),
)

object Keys {
    val theme = stringPreferencesKey("theme")
    val permanent = booleanPreferencesKey("permanent")
    val antiBurn = booleanPreferencesKey("anti_burn")
    val dim = intPreferencesKey("dim")
    val darkBoost = booleanPreferencesKey("dark_boost")
    val performance = booleanPreferencesKey("performance")
    val glass = intPreferencesKey("glass")
    val proxyUrl = stringPreferencesKey("proxy_url")
    val proxyToken = stringPreferencesKey("proxy_token")
    val metroStop = stringPreferencesKey("metro_stop")
    val metroLine = stringPreferencesKey("metro_line")
    val metroFilter = stringPreferencesKey("metro_filter")
    val rerStop = stringPreferencesKey("rer_stop")
    val rerLine = stringPreferencesKey("rer_line")
    val rerFilter = stringPreferencesKey("rer_filter")
    val velibCode = stringPreferencesKey("velib_code")
    val newsUrl = stringPreferencesKey("news_url")
    val icalUrl = stringPreferencesKey("ical_url")
    val metroWalk = intPreferencesKey("metro_walk")
    val rerWalk = intPreferencesKey("rer_walk")
    val favorites = stringPreferencesKey("favorites")
    val hidden = stringPreferencesKey("hidden")
    val appOrder = stringPreferencesKey("app_order")
    val counts = stringPreferencesKey("counts")
    val firstRunDone = booleanPreferencesKey("first_run_done")
}

class SettingsRepo(context: Context) {
    private val ctx = context.applicationContext

    val prefs: Flow<Prefs> = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toPrefs() }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        ctx.dataStore.edit { it[key] = value }
    }

    suspend fun toggleFavorite(pkg: String) {
        ctx.dataStore.edit {
            val l = decodeList(it[Keys.favorites]).toMutableList()
            if (!l.remove(pkg)) l.add(pkg)
            it[Keys.favorites] = l.joinToString(",")
        }
    }

    suspend fun moveFavorite(pkg: String, delta: Int) {
        ctx.dataStore.edit {
            val l = decodeList(it[Keys.favorites]).toMutableList()
            val i = l.indexOf(pkg)
            val j = i + delta
            if (i >= 0 && j in l.indices) {
                l.removeAt(i); l.add(j, pkg)
                it[Keys.favorites] = l.joinToString(",")
            }
        }
    }

    /** Masque une application des listes (accueil, « Applications ») sans la désinstaller. */
    suspend fun toggleHidden(pkg: String) {
        ctx.dataStore.edit {
            val l = decodeList(it[Keys.hidden]).toMutableList()
            val wasHidden = l.remove(pkg)
            if (!wasHidden) {
                l.add(pkg)
                // Un favori masqué n'aurait plus de sens sur l'accueil.
                val favs = decodeList(it[Keys.favorites]).toMutableList()
                if (favs.remove(pkg)) it[Keys.favorites] = favs.joinToString(",")
            }
            it[Keys.hidden] = l.joinToString(",")
        }
    }

    /**
     * Déplace une application dans la liste « Applications », qu'elle soit favorite ou non.
     * [order] est l'ordre actuellement affiché : on l'adopte comme nouvel ordre de référence,
     * ce qui couvre aussi les applications jamais déplacées jusqu'ici.
     */
    suspend fun moveApp(pkg: String, delta: Int, order: List<String>) {
        ctx.dataStore.edit {
            val l = order.toMutableList()
            val i = l.indexOf(pkg)
            val j = i + delta
            if (i >= 0 && j in l.indices) {
                l.removeAt(i); l.add(j, pkg)
                it[Keys.appOrder] = l.joinToString(",")
            }
        }
    }

    suspend fun recordLaunch(pkg: String) {
        ctx.dataStore.edit {
            val m = decodeCounts(it[Keys.counts]).toMutableMap()
            m[pkg] = (m[pkg] ?: 0) + 1
            it[Keys.counts] = m.entries.joinToString("|") { e -> "${e.key}=${e.value}" }
        }
    }

    /** Premier lancement : on adapte le mode performance aux ressources réelles de l'appareil. */
    suspend fun firstRun() {
        ctx.dataStore.edit {
            if (it[Keys.firstRunDone] != true) {
                it[Keys.firstRunDone] = true
                val am = ctx.getSystemService(ActivityManager::class.java)
                it[Keys.performance] = am?.isLowRamDevice == true
            }
        }
    }

    private fun Preferences.toPrefs() = Prefs(
        theme = this[Keys.theme]?.let { n -> ThemeName.entries.firstOrNull { it.name == n } } ?: ThemeName.MANUFACTURE,
        permanentDisplay = this[Keys.permanent] ?: false,
        antiBurnIn = this[Keys.antiBurn] ?: true,
        dimLevel = this[Keys.dim] ?: 0,
        darkBoost = this[Keys.darkBoost] ?: false,
        performance = this[Keys.performance] ?: false,
        glass = this[Keys.glass] ?: 9,
        proxyUrl = this[Keys.proxyUrl] ?: "",
        proxyToken = this[Keys.proxyToken] ?: "",
        metroStopRef = this[Keys.metroStop] ?: "",
        metroLineRef = this[Keys.metroLine] ?: "STIF:Line::C01377:",
        metroFilter = this[Keys.metroFilter] ?: "Courneuve",
        rerStopRef = this[Keys.rerStop] ?: "",
        rerLineRef = this[Keys.rerLine] ?: "STIF:Line::C01727:",
        rerFilter = this[Keys.rerFilter] ?: RER_NORD,
        velibCode = this[Keys.velibCode] ?: "42021",
        newsUrl = this[Keys.newsUrl]?.takeIf { it.isNotBlank() } ?: News.DEFAULT_URL,
        icalUrl = this[Keys.icalUrl] ?: "",
        metroWalk = this[Keys.metroWalk] ?: 5,
        rerWalk = this[Keys.rerWalk] ?: 8,
        favorites = decodeList(this[Keys.favorites]),
        hidden = decodeList(this[Keys.hidden]),
        appOrder = decodeList(this[Keys.appOrder]),
        launchCounts = decodeCounts(this[Keys.counts]),
    )

    private fun decodeList(s: String?): List<String> =
        s?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    private fun decodeCounts(s: String?): Map<String, Int> =
        s?.split('|')?.mapNotNull { e ->
            val i = e.lastIndexOf('=')
            if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toIntOrNull() ?: 0)
        }?.toMap() ?: emptyMap()
}
