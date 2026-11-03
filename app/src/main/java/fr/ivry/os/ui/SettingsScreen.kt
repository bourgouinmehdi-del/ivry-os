package fr.ivry.os.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ivry.os.MainViewModel
import fr.ivry.os.data.DeviceInfo
import fr.ivry.os.data.Keys
import fr.ivry.os.data.Prefs
import fr.ivry.os.data.ThemeName

private data class TextSetting(val label: String, val value: String, val key: Preferences.Key<String>)

private const val RESTORE_TEXT =
    "Depuis un ordinateur relié en ADB :\n" +
        "adb shell pm enable com.google.android.apps.tv.launcherx\n" +
        "adb shell pm enable com.google.android.tungsten.setupwraith\n" +
        "puis redémarrer : adb reboot. L'accueil Google TV d'origine revient. " +
        "Pour retirer IVRY OS : adb uninstall fr.ivry.os"

@Composable
fun SettingsScreen(vm: MainViewModel, prefs: Prefs) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<TextSetting?>(null) }
    val info = remember { DeviceInfo.lines(ctx) }
    val velib by vm.engine.velib.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 26.dp, top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Paramètres", fontSize = 30.sp, fontWeight = FontWeight.Black, color = p.text, fontFamily = Condensed)

        Group("Ivry Live")
        ToggleRow("Affichage permanent",
            "Garde l'écran allumé tant que l'accueil est au premier plan. La mise en veille du téléviseur reste prioritaire.",
            prefs.permanentDisplay) { vm.set(Keys.permanent, it) }
        ToggleRow("Anti-marquage",
            "Décale l'interface de quelques pixels toutes les 3 min et atténue les éléments lumineux après 5 min sans action.",
            prefs.antiBurnIn) { vm.set(Keys.antiBurn, it) }
        StepRow("Réduction de luminosité", "${prefs.dimLevel * 10} %",
            { vm.set(Keys.dim, (prefs.dimLevel - 1).coerceAtLeast(0)) },
            { vm.set(Keys.dim, (prefs.dimLevel + 1).coerceAtMost(6)) })
        ToggleRow("Mode sombre renforcé", "Fond presque noir, verre et textes atténués.", prefs.darkBoost) {
            vm.set(Keys.darkBoost, it)
        }

        Group("Apparence")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeName.entries.forEach { t ->
                val pal = paletteFor(t, false, 6)
                Plate(Modifier.weight(1f).height(78.dp), shape = CutCornerShape(topEnd = 16.dp),
                    background = pal.bg, onClick = { vm.set(Keys.theme, t.name) }) { f ->
                    Column(Modifier.fillMaxSize().padding(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(pal.accent, pal.accent2, pal.nature, pal.focus).forEach {
                                Box(Modifier.size(width = 16.dp, height = 6.dp).background(it))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(t.label.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = Condensed,
                            color = if (f || prefs.theme == t) p.focus else Ivry.Ivoire)
                        Text(t.tagline, fontSize = 9.5.sp, color = Ivry.Beton.copy(alpha = .7f), maxLines = 1)
                    }
                }
            }
        }
        StepRow("Opacité du verre", "${prefs.glass * 10} %",
            { vm.set(Keys.glass, (prefs.glass - 1).coerceAtLeast(2)) },
            { vm.set(Keys.glass, (prefs.glass + 1).coerceAtMost(9)) })
        ToggleRow("Mode performance", "Supprime l'animation de démarrage, les agrandissements au focus et la transition de lancement.",
            prefs.performance) { vm.set(Keys.performance, it) }

        Group("Sources de données")
        TextRow("Proxy PRIM (URL)", prefs.proxyUrl, "Non configuré") { editing = TextSetting("Proxy PRIM (URL)", prefs.proxyUrl, Keys.proxyUrl) }
        TextRow("Jeton du proxy", if (prefs.proxyToken.isBlank()) "" else "••••••", "Aucun") {
            editing = TextSetting("Jeton du proxy", prefs.proxyToken, Keys.proxyToken)
        }
        TextRow("Métro 7 · arrêt (MonitoringRef)", prefs.metroStopRef, "À renseigner") {
            editing = TextSetting("Métro 7 · arrêt", prefs.metroStopRef, Keys.metroStop)
        }
        TextRow("Métro 7 · filtre de direction", prefs.metroFilter, "Aucun") {
            editing = TextSetting("Métro 7 · filtre (mots séparés par des virgules)", prefs.metroFilter, Keys.metroFilter)
        }
        TextRow("Métro 7 · ligne (trafic)", prefs.metroLineRef, "Aucune") {
            editing = TextSetting("Métro 7 · LineRef", prefs.metroLineRef, Keys.metroLine)
        }
        TextRow("RER C · arrêt (MonitoringRef)", prefs.rerStopRef, "À renseigner") {
            editing = TextSetting("RER C · arrêt", prefs.rerStopRef, Keys.rerStop)
        }
        TextRow("RER C · filtre de direction", prefs.rerFilter, "Aucun") {
            editing = TextSetting("RER C · filtre (mots séparés par des virgules)", prefs.rerFilter, Keys.rerFilter)
        }
        TextRow("RER C · ligne (trafic)", prefs.rerLineRef, "Aucune") {
            editing = TextSetting("RER C · LineRef", prefs.rerLineRef, Keys.rerLine)
        }
        StepRow("Temps de marche jusqu'au métro 7", "${prefs.metroWalk} min",
            { vm.set(Keys.metroWalk, (prefs.metroWalk - 1).coerceAtLeast(0)) },
            { vm.set(Keys.metroWalk, (prefs.metroWalk + 1).coerceAtMost(30)) })
        StepRow("Temps de marche jusqu'au RER C", "${prefs.rerWalk} min",
            { vm.set(Keys.rerWalk, (prefs.rerWalk - 1).coerceAtLeast(0)) },
            { vm.set(Keys.rerWalk, (prefs.rerWalk + 1).coerceAtMost(30)) })
        TextRow("Vélib' · code station", prefs.velibCode, "À renseigner") {
            editing = TextSetting("Vélib' · code station", prefs.velibCode, Keys.velibCode)
        }
        Text(
            velib.value?.let { "Station vérifiée dans les données officielles : ${it.name} (n° ${it.code})" }
                ?: velib.error?.let { "Vérification Vélib' : $it" } ?: "Vérification Vélib' en cours…",
            fontSize = 12.sp, color = if (velib.value != null) p.nature else p.textDim,
        )
        ActionButton("Actualiser toutes les données", Icons.Default.Refresh) { vm.engine.refreshNow() }

        Group("Centre de contrôle")
        ControlCenter(ctx)

        Group("Système")
        info.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth()) {
                Text(k, fontSize = 12.sp, color = p.textDim, modifier = Modifier.width(200.dp))
                Text(v, fontSize = 12.sp, color = p.text)
            }
        }

        Group("Restaurer le launcher Google TV")
        Text(RESTORE_TEXT, fontSize = 12.sp, color = p.textDim, lineHeight = 18.sp)
    }

    editing?.let { s ->
        EditDialog(s.label, s.value, onDismiss = { editing = null }) {
            vm.set(s.key, it.trim()); editing = null
        }
    }
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(10.dp))
    PanelLabel(title, LocalPalette.current.accent)
}

@Composable
private fun ToggleRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Plate(Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = { onChange(!value) }) { f ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (f) p.focus else p.text)
                Text(desc, fontSize = 11.sp, color = p.textDim)
            }
            Spacer(Modifier.width(12.dp))
            Tag(if (value) "ACTIVÉ" else "DÉSACTIVÉ", if (value) p.nature else p.textDim)
        }
    }
}

@Composable
private fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Plate(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                Text(value, fontSize = 12.sp, color = p.textDim)
            }
            ActionButton("–", onClick = onMinus)
            Spacer(Modifier.width(6.dp))
            ActionButton("+", onClick = onPlus)
        }
    }
}

@Composable
private fun TextRow(title: String, value: String, empty: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Plate(Modifier.fillMaxWidth().heightIn(min = 46.dp), onClick = onClick) { f ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (f) p.focus else p.text,
                modifier = Modifier.weight(1f))
            Text(value.ifBlank { empty }, fontSize = 12.sp, color = if (value.isBlank()) p.warn else p.textDim, maxLines = 1)
        }
    }
}

@Composable
private fun ControlCenter(ctx: Context) {
    val p = LocalPalette.current
    val items = listOf(
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Son" to Settings.ACTION_SOUND_SETTINGS,
        "Affichage" to Settings.ACTION_DISPLAY_SETTINGS,
        "Applications" to Settings.ACTION_APPLICATION_SETTINGS,
        "Android" to Settings.ACTION_SETTINGS,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, action) ->
            Plate(Modifier.weight(1f).height(64.dp), shape = CutCornerShape(bottomEnd = 14.dp),
                onClick = { openSystemSettings(ctx, action) }) { f ->
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(width = 22.dp, height = 4.dp).background(if (f) p.focus else p.accent))
                    Spacer(Modifier.height(6.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (f) p.focus else p.text,
                        fontFamily = Condensed)
                }
            }
        }
    }
}

/** Ouvre le panneau système correspondant ; à défaut, les paramètres Android généraux. */
private fun openSystemSettings(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
