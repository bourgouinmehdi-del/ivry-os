package fr.ivry.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ivry.os.MainViewModel
import fr.ivry.os.data.Prefs
import fr.ivry.os.data.ThemeName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector?) {
    HOME("ACCUEIL", Icons.Default.Home),
    CINEMA("CINÉMA", Icons.Default.PlayArrow),
    LIVE("TV EN DIRECT", null),
    APPS("APPLICATIONS", Icons.Default.List),
    FAVORITES("FAVORIS", Icons.Default.Star),
    SETTINGS("PARAMÈTRES", Icons.Default.Settings),
}

enum class Detail { WEATHER, METRO, RER, VELIB }

@Composable
fun IvryRoot(vm: MainViewModel) {
    val prefsState by vm.settings.prefs.collectAsStateWithLifecycle(initialValue = null)
    val prefs = prefsState
    if (prefs == null) {
        Box(Modifier.fillMaxSize().background(Ivry.Asphalte))
        return
    }
    val idle by vm.engine.idle.collectAsStateWithLifecycle()
    val weather by vm.engine.weatherNow.collectAsStateWithLifecycle()
    val sky = rememberSky(weather.value)
    val rer by vm.engine.rer.collectAsStateWithLifecycle()
    val trainTimes = remember(rer.value) { rer.value?.departures?.map { it.expectedAt } ?: emptyList() }
    val base = remember(prefs.theme, prefs.darkBoost, prefs.glass) { paletteFor(prefs.theme, prefs.darkBoost, prefs.glass) }
    val palette = if (idle && prefs.antiBurnIn) base.dimmed() else base
    val perf = prefs.performance || idle
    var booted by rememberSaveable { mutableStateOf(false) }
    val shift = antiBurnShift(prefs.antiBurnIn)

    // Transition immersive au lancement d'une application.
    val pending by vm.pendingLaunch.collectAsStateWithLifecycle()
    val curtain = remember { Animatable(0f) }
    LaunchedEffect(pending) {
        val pkg = pending ?: return@LaunchedEffect
        if (!prefs.performance) curtain.animateTo(1f, tween(160))
        vm.doLaunch(pkg)
        delay(700)
        curtain.snapTo(0f)
        vm.pendingLaunch.value = null
    }

    val scheme = darkColorScheme(
        primary = Ivry.Jaune, onPrimary = Ivry.Asphalte,
        surface = Color(0xFF26292B), onSurface = Ivry.Ivoire,
        background = Ivry.Asphalte, onBackground = Ivry.Ivoire,
        surfaceContainerHigh = Color(0xFF26292B),
    )
    MaterialTheme(colorScheme = scheme, typography = ivryTypography()) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalPerf provides perf,
            LocalTextStyle provides TextStyle(fontFamily = Bricolage),
        ) {
            Box(Modifier.fillMaxSize().background(palette.bg)) {
                CityScene(sky, Modifier.fillMaxSize(), animate = !prefs.performance, slow = idle, trainTimes = trainTimes)
                Box(Modifier.fillMaxSize().offset { shift }) {
                    if (!booted) Splash(skip = prefs.performance) { booted = true }
                    else MainShell(vm, prefs)
                }
                if (prefs.dimLevel > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.dimLevel / 10f)))
                if (curtain.value > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = curtain.value)))
            }
        }
    }
}

/** Anti-marquage : déplacement de quelques pixels toutes les 3 minutes. */
@Composable
private fun antiBurnShift(enabled: Boolean): IntOffset {
    val state = produceState(IntOffset.Zero, enabled) {
        value = IntOffset.Zero
        if (!enabled) return@produceState
        val steps = listOf(IntOffset(0, 0), IntOffset(4, 2), IntOffset(-3, 4), IntOffset(-5, -2), IntOffset(3, -4))
        var i = 0
        while (true) { delay(180_000); i = (i + 1) % steps.size; value = steps[i] }
    }
    return state.value
}

@Composable
private fun MainShell(vm: MainViewModel, prefs: Prefs) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var detail by rememberSaveable { mutableStateOf<Detail?>(null) }
    val apps by vm.apps.collectAsStateWithLifecycle()

    // Retour : ferme un détail, sinon revient à l'accueil. Sur l'accueil, un launcher reste en place.
    BackHandler(enabled = true) {
        when {
            detail != null -> detail = null
            section != Section.HOME -> section = Section.HOME
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Hors de l'accueil, la ville passe en arrière-plan atténué
        if (detail != null || section != Section.HOME) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .58f)))
        Box(Modifier.fillMaxSize().padding(start = 76.dp)) {
            val d = detail
            when {
                d != null -> DetailScreen(vm, d) { detail = null }
                section == Section.HOME -> HomeScreen(vm, prefs, apps,
                    onDetail = { detail = it },
                    onSection = { section = it })
                section == Section.SETTINGS -> SettingsScreen(vm, prefs)
                else -> AppsScreen(vm, prefs, apps, section)
            }
        }
        Rail(section, Modifier.align(Alignment.TopStart)) { s ->
            if (s == Section.LIVE) vm.launchLive() else { detail = null; section = s }
        }
    }
}

/** Menu en colonne fine d'icônes, qui s'ouvre avec les libellés dès qu'on y navigue. */
@Composable
private fun Rail(current: Section, modifier: Modifier, onSelect: (Section) -> Unit) {
    val p = LocalPalette.current
    val perf = LocalPerf.current
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 184.dp else 52.dp, if (perf) snap() else tween(160), label = "rail")
    Column(
        modifier.fillMaxHeight().padding(start = 12.dp, top = 14.dp, bottom = 14.dp).width(width)
            .background(Color(0xFF111820).copy(alpha = if (expanded) .94f else .62f))
            .border(1.dp, Ivry.Ivoire.copy(alpha = .10f))
            .onFocusChanged { expanded = it.hasFocus }
            .padding(horizontal = 6.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.CenterStart) {
            if (expanded) Logo(17.sp, Modifier.padding(start = 6.dp))
            else Text("IV", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.text,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        Section.entries.forEach { s -> RailItem(s, s == current, expanded) { onSelect(s) } }
    }
}

@Composable
private fun RailItem(s: Section, active: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> p.focus
        active -> p.text.copy(alpha = .14f)
        else -> Color.Transparent
    }
    val fg = if (focused) Ivry.Asphalte else p.text
    Row(
        Modifier.fillMaxWidth().height(38.dp)
            .background(bg, CutCornerShape(topEnd = 10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            if (s.icon != null) Icon(s.icon, s.label, tint = fg, modifier = Modifier.size(18.dp))
            else Box(Modifier.size(10.dp).background(Ivry.Orange, CircleShape))
        }
        if (expanded) Text(s.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = fg,
            fontFamily = Condensed, maxLines = 1)
        if (active && !focused) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(end = 6.dp).size(width = 3.dp, height = 18.dp).background(p.focus))
        }
    }
}

/** Démarrage : le logo apparaît sur fond sombre, balayé par un léger trait de lumière. */
@Composable
private fun Splash(skip: Boolean, onDone: () -> Unit) {
    val p = LocalPalette.current
    val alpha = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.3f) }
    LaunchedEffect(Unit) {
        if (skip) { onDone(); return@LaunchedEffect }
        launch { sweep.animateTo(1.3f, tween(1500)) }
        alpha.animateTo(1f, tween(900))
        delay(650)
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.alpha(alpha.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(64.sp)
            Spacer(Modifier.height(8.dp))
            Text("IVRY-SUR-SEINE", fontSize = 12.sp, letterSpacing = 6.sp, color = p.textDim, fontFamily = Condensed)
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * sweep.value
            drawRect(Brush.linearGradient(
                listOf(Color.Transparent, Ivry.Ivoire.copy(alpha = .07f), Color.Transparent),
                start = Offset(x - 220f, 0f), end = Offset(x + 220f, size.height),
            ))
        }
    }
}
