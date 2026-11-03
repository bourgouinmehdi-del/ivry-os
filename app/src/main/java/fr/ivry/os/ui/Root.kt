package fr.ivry.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.text.font.FontWeight
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
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides palette, LocalPerf provides perf) {
            Box(Modifier.fillMaxSize().background(palette.bg)) {
                Backdrop(palette)
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

    Row(Modifier.fillMaxSize()) {
        Sidebar(section) { s ->
            if (s == Section.LIVE) vm.launchLive() else { detail = null; section = s }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
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
    }
}

@Composable
private fun Sidebar(current: Section, onSelect: (Section) -> Unit) {
    val p = LocalPalette.current
    val stripes = listOf(p.accent, p.accent2, Ivry.Orange, p.nature, p.focus, p.textDim)
    Column(
        Modifier.width(136.dp).fillMaxHeight().background(Color.Black.copy(alpha = .28f))
            .padding(start = 14.dp, end = 10.dp, top = 22.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Logo(20.sp)
        Text("Ivry-sur-Seine", fontSize = 9.sp, color = p.textDim, letterSpacing = 1.sp)
        Spacer(Modifier.height(16.dp))
        Section.entries.forEachIndexed { i, s -> NavItem(s, s == current, stripes[i % stripes.size]) { onSelect(s) } }
        Spacer(Modifier.weight(1f))
        Text("v1.0", fontSize = 9.sp, color = p.textDim)
    }
}

@Composable
private fun NavItem(s: Section, active: Boolean, stripe: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> p.focus
        active -> p.text.copy(alpha = .10f)
        else -> Color.Transparent
    }
    val fg = if (focused) Ivry.Asphalte else p.text
    Row(
        Modifier.fillMaxWidth().height(36.dp)
            .background(bg, CutCornerShape(topEnd = 10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (active) p.focus else stripe))
        Spacer(Modifier.width(8.dp))
        if (s.icon != null) Icon(s.icon, null, tint = fg, modifier = Modifier.size(16.dp))
        else Box(Modifier.padding(horizontal = 3.dp).size(9.dp).background(Ivry.Orange, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(s.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = fg,
            fontFamily = Condensed, maxLines = 1)
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

/** Fonds dessinés (aucune fausse photographie) : un motif propre à chaque thème. */
@Composable
private fun Backdrop(p: Palette) {
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val w = size.width
        val h = size.height
        onDrawBehind {
            when (p.theme) {
                ThemeName.MANUFACTURE -> {
                    val rows = 34
                    val bh = h / rows
                    val bw = bh * 3
                    for (row in rows / 2 until rows) {
                        val fade = (row - rows / 2f) / (rows / 2f)
                        val y = row * bh
                        var x = if (row % 2 == 0) 0f else -bw / 2
                        var k = 0
                        while (x < w * .55f) {
                            val a = (if ((row + k) % 7 == 0) .08f else .04f) * fade
                            drawRect(p.accent.copy(alpha = a), Offset(x + 1, y + 1), Size(bw - 2, bh - 2))
                            x += bw; k++
                        }
                    }
                    // Verrière de la Manufacture
                    val gx = w * .64f
                    for (i in 0..8) {
                        val sx = gx + i * w * .045f
                        drawLine(p.accent2.copy(alpha = .10f), Offset(sx, 0f), Offset(sx - h * .14f, h * .34f), 2f)
                    }
                    drawLine(p.accent2.copy(alpha = .10f), Offset(gx - h * .14f, h * .34f), Offset(w, h * .34f), 2f)
                }
                ThemeName.RENAUDIE -> {
                    fun tri(a: Offset, b: Offset, c: Offset, col: Color) {
                        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, col)
                    }
                    tri(Offset(w * .55f, h), Offset(w * .82f, h * .38f), Offset(w, h), Ivry.Beton.copy(alpha = .05f))
                    tri(Offset(w * .70f, h), Offset(w * .90f, h * .58f), Offset(w * 1.05f, h), Ivry.Sable.copy(alpha = .06f))
                    tri(Offset(w * .78f, 0f), Offset(w, 0f), Offset(w, h * .30f), Ivry.Beton.copy(alpha = .04f))
                    tri(Offset(0f, h * .70f), Offset(w * .18f, h), Offset(0f, h), Ivry.Sable.copy(alpha = .05f))
                }
                ThemeName.POPULAIRE -> {
                    rotate(-8f, Offset(w * .82f, h * .2f)) {
                        drawRect(Ivry.Orange.copy(alpha = .07f), Offset(w * .70f, -h * .1f), Size(w * .26f, h * .45f))
                    }
                    rotate(5f, Offset(w * .9f, h * .8f)) {
                        drawRect(Ivry.Jaune.copy(alpha = .06f), Offset(w * .74f, h * .55f), Size(w * .3f, h * .5f))
                    }
                    val step = h / 40
                    var y = h * .62f
                    while (y < h) {
                        var x = w * .02f
                        while (x < w * .22f) {
                            drawCircle(Ivry.Ivoire.copy(alpha = .05f), radius = step * .22f * ((y - h * .6f) / (h * .4f)), center = Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
                ThemeName.CONFLUENCES -> {
                    val seine = Path().apply {
                        moveTo(w * .35f, h); cubicTo(w * .55f, h * .7f, w * .75f, h * .75f, w, h * .45f)
                        lineTo(w, h * .62f); cubicTo(w * .8f, h * .85f, w * .6f, h * .85f, w * .48f, h); close()
                    }
                    drawPath(seine, Brush.horizontalGradient(listOf(Ivry.Bleu.copy(alpha = .05f), Ivry.Bleu.copy(alpha = .16f))))
                    val marne = Path().apply {
                        moveTo(w, h * .2f); cubicTo(w * .9f, h * .35f, w * .85f, h * .45f, w * .8f, h * .62f)
                        lineTo(w * .86f, h * .64f); cubicTo(w * .9f, h * .48f, w * .95f, h * .38f, w, h * .3f); close()
                    }
                    drawPath(marne, Ivry.Vert.copy(alpha = .14f))
                }
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .35f))))
        }
    })
}
