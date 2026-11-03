package fr.ivry.os.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val isVideo: Boolean,
)

object AppsRepository {
    const val CANAL_STORE = "com.canal.android.canal"
    const val YOUTUBE_TV = "com.google.android.youtube.tv"

    private val videoHints = listOf(
        "canal", "youtube", "netflix", "disney", "amazon.avod", "primevideo", "francetv", "arte",
        "tf1", "m6", "molotov", "plex", "vlc", "kodi", "appletv", "paramount", "crunchyroll",
        "mubi", "dazn", "rakuten", "pluto", "twitch",
    )

    /** Détection dynamique : uniquement ce qui est réellement installé. */
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val p = ri.activityInfo.packageName
                if (p != ctx.packageName && p !in seen) seen[p] = ri
            }
        }
        return seen.values.map { ri ->
            val ai = ri.activityInfo
            // Tuiles uniformes : on garde l'icône officielle, pas la bannière (recadrages disparates)
            val banner: ImageBitmap? = null
            val icon = runCatching { ai.loadIcon(pm).toBitmap(192, 192).asImageBitmap() }.getOrNull()
            val video = ai.applicationInfo.category == ApplicationInfo.CATEGORY_VIDEO ||
                videoHints.any { ai.packageName.contains(it, ignoreCase = true) }
            AppEntry(ai.packageName, ri.loadLabel(pm).toString(), banner, icon, video)
        }.sortedBy { it.label.lowercase() }
    }

    fun findCanal(apps: List<AppEntry>): AppEntry? =
        apps.firstOrNull { it.pkg.contains("canal", true) || it.label.contains("canal", true) }

    fun findYoutube(apps: List<AppEntry>): AppEntry? =
        apps.firstOrNull { it.pkg == YOUTUBE_TV } ?: apps.firstOrNull { it.label.equals("YouTube", true) }

    fun byUsage(apps: List<AppEntry>, prefs: Prefs): List<AppEntry> =
        apps.sortedWith(compareByDescending<AppEntry> { prefs.launchCounts[it.pkg] ?: 0 }.thenBy { it.label.lowercase() })

    /** « Le reste » : favoris dans l'ordre choisi, puis les autres par fréquence d'utilisation. */
    fun secondary(apps: List<AppEntry>, prefs: Prefs, exclude: Set<String>): List<AppEntry> {
        val favs = prefs.favorites.mapNotNull { f -> apps.firstOrNull { it.pkg == f } }.filter { it.pkg !in exclude }
        val rest = byUsage(apps, prefs).filter { it.pkg !in exclude && it.pkg !in prefs.favorites }
        return favs + rest
    }

    fun cinema(apps: List<AppEntry>, prefs: Prefs): List<AppEntry> {
        val top = listOfNotNull(findCanal(apps), findYoutube(apps))
        return top + byUsage(apps.filter { it.isVideo && it !in top }, prefs)
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: ActivityNotFoundException) { false }
    }

    fun openStore(ctx: Context, pkg: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ); true
    } catch (e: ActivityNotFoundException) { false }
}
