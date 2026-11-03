package fr.ivry.os.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import java.util.Locale

/** Identification de l'appareil : version Android, capacités graphiques, ressources. */
object DeviceInfo {
    fun lines(ctx: Context): List<Pair<String, String>> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dm = ctx.resources.displayMetrics
        val mode = ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
        return listOf(
            "Appareil" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Sortie vidéo" to (mode?.let {
                "${it.physicalWidth} × ${it.physicalHeight} à ${String.format(Locale.FRANCE, "%.0f", it.refreshRate)} Hz"
            } ?: "inconnue"),
            "Rendu de l'interface" to "${dm.widthPixels} × ${dm.heightPixels} px · ${dm.densityDpi} dpi",
            "OpenGL ES" to am.deviceConfigurationInfo.glEsVersion,
            "Mémoire" to "${mi.availMem / 1_048_576} Mo libres sur ${mi.totalMem / 1_048_576} Mo",
            "Mémoire allouée à l'app" to "${am.memoryClass} Mo",
            "Appareil à faible mémoire" to if (am.isLowRamDevice) "oui" else "non",
        )
    }
}
