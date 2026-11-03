package fr.ivry.os.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val name: String, val apkUrl: String)

/**
 * Mises à jour depuis les versions publiées sur GitHub par GitHub Actions.
 * Android impose une confirmation à l'installation : rien n'est installé sans l'accord de l'utilisateur.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/bourgouinmehdi-del/ivry-os/releases/latest"

    fun currentVersion(ctx: Context): Int {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }

    /** Retourne la nouvelle version si elle est plus récente que celle installée, sinon null. */
    suspend fun check(ctx: Context): UpdateInfo? {
        val o = JSONObject(Http.get(LATEST, mapOf("Accept" to "application/vnd.github+json")))
        val code = o.optString("tag_name").removePrefix("v").toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
        }
        if (url == null || code <= currentVersion(ctx)) return null
        return UpdateInfo(code, o.optString("name", "IVRY OS 1.$code"), url)
    }

    suspend fun download(ctx: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "ivry-os-${info.versionCode}.apk")
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = true
            c.connectTimeout = 10_000
            c.readTimeout = 60_000
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally { c.disconnect() }
        out
    }

    /** Vrai si Android autorise déjà IVRY OS à installer des applications. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Ouvre le réglage « Installer des applis inconnues » pour IVRY OS (ou les paramètres de sécurité). */
    fun openInstallPermission(ctx: Context) {
        val specific = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        for (i in listOf(specific, Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: ActivityNotFoundException) { }
        }
    }

    /** Lance l'installateur Android, qui demande confirmation. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
