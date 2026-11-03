package fr.ivry.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ivry.os.data.CurrentWeather
import fr.ivry.os.data.PARIS
import fr.ivry.os.data.wmoLabel
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ======================= État du ciel =======================

/** Tout ce dont la scène a besoin, en minutes locales (Paris). */
data class SkyState(
    val minute: Int,
    val sunrise: Int,
    val sunset: Int,
    val cloud: Int,
    val code: Int,
    val precip: Double,
    val moon: Float,
) {
    /** 1 en plein jour, 0 en pleine nuit, transition de 90 min autour du lever et du coucher. */
    val daylight: Float
        get() = when {
            minute < sunrise - 45 || minute > sunset + 45 -> 0f
            minute < sunrise + 45 -> (minute - (sunrise - 45)) / 90f
            minute > sunset - 45 -> 1f - (minute - (sunset - 45)) / 90f
            else -> 1f
        }.coerceIn(0f, 1f)
    val night: Float get() = 1f - daylight
    val rain: Boolean get() = code in 51..67 || code in 80..82 || code >= 95
    val snow: Boolean get() = code in 71..77 || code in 85..86
    val fog: Boolean get() = code == 45 || code == 48
    val storm: Boolean get() = code >= 95
}

/** Recalculé toutes les 5 minutes ou à chaque nouvelle donnée météo. */
@Composable
fun rememberSky(weather: CurrentWeather?): SkyState {
    val now = rememberTick(300_000)
    return remember(now, weather) {
        fun mins(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(PARIS).let { it.hour * 60 + it.minute }
        val code = weather?.code ?: -1
        SkyState(
            minute = mins(now),
            sunrise = weather?.sunrise?.let(::mins) ?: (7 * 60 + 30),
            sunset = weather?.sunset?.let(::mins) ?: (18 * 60),
            cloud = weather?.cloudCover ?: when (code) { 0 -> 5; 1 -> 25; 2 -> 55; -1 -> 30; else -> 90 },
            code = code,
            precip = weather?.precip ?: 0.0,
            moon = moonPhase(now),
        )
    }
}

/** 0 = nouvelle lune, 0,5 = pleine lune. Référence : nouvelle lune du 6 janvier 2000, 18 h 14 UTC. */
private fun moonPhase(ms: Long): Float {
    val days = (ms - 947_182_440_000L) / 86_400_000.0
    val f = (days / 29.530588853) % 1.0
    return (if (f < 0) f + 1 else f).toFloat()
}

private typealias Grad = Pair<Color, Color>
private val NIGHT: Grad = Color(0xFF070C12) to Color(0xFF15202D)
private val DAWN: Grad = Color(0xFF1E2236) to Color(0xFFB8664C)
private val DAY: Grad = Color(0xFF2C5068) to Color(0xFF7FA2B2)
private val DUSK: Grad = Color(0xFF241E33) to Color(0xFFC86E48)
private fun mix(a: Grad, b: Grad, t: Float): Grad = lerp(a.first, b.first, t) to lerp(a.second, b.second, t)

private fun SkyState.gradient(): Grad {
    val m = minute; val r = sunrise; val s = sunset
    val base = when {
        m < r - 60 || m > s + 60 -> NIGHT
        m < r -> mix(NIGHT, DAWN, (m - (r - 60)) / 60f)
        m < r + 60 -> mix(DAWN, DAY, (m - r) / 60f)
        m < s - 60 -> DAY
        m < s -> mix(DAY, DUSK, (m - (s - 60)) / 60f)
        else -> mix(DUSK, NIGHT, (m - s) / 60f)
    }
    val grey = lerp(Color(0xFF161B20), Color(0xFF4A5157), daylight)
    val k = cloud / 100f
    return lerp(base.first, grey, k * .45f) to lerp(base.second, grey, k * .7f)
}

// ======================= Scène =======================

/** Géométrie commune aux calques de la scène. */
private class Geo(val w: Float, val h: Float) {
    val base = h * .52f
    val u = h * .36f
}

/** Horloge de la scène, en secondes, rafraîchie à 20 images/s au plus. Figée si désactivée. */
@Composable
private fun rememberSceneClock(enabled: Boolean): State<Float> {
    val state = remember { mutableFloatStateOf((System.currentTimeMillis() % 3_600_000L) / 1000f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (now - last >= 50) {
                    last = now
                    state.floatValue = (System.currentTimeMillis() % 3_600_000L) / 1000f
                }
            }
        }
    }
    return state
}

/**
 * Ivry dessinée, sans photo : Manufacture des Œillets (sheds), étoiles de Renaudie, terrasses de
 * Renée Gailhoustet, grands ensembles, grues d'Ivry Confluences, quais et Seine. Réagit à l'heure réelle,
 * au lever/coucher du soleil, aux nuages, à la pluie, à la neige, au brouillard et à la phase de la lune.
 * Calques fixes (ciel, ville) mis en cache ; seuls les calques animés se redessinent.
 */
@Composable
fun CityScene(sky: SkyState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val time = rememberSceneClock(enabled = !LocalPerf.current)
    Box(modifier) {
        // 1. Ciel fixe : dégradé et astre
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val g = Geo(size.width, size.height)
            val (top, low) = sky.gradient()
            val clouds = sky.cloud / 100f
            onDrawBehind {
                drawRect(Brush.verticalGradient(listOf(top, low), startY = 0f, endY = g.base))
                if (sky.daylight > .5f) {
                    val sp = sky.sunPos(g)
                    val warm = sky.sunT() < .15f || sky.sunT() > .85f
                    val c = if (warm) Ivry.Orange else Color(0xFFF6EBD0)
                    drawCircle(c.copy(alpha = .10f * (1f - clouds * .8f)), g.h * .12f, sp)
                    drawCircle(c.copy(alpha = .85f * (1f - clouds * .85f)), g.h * .035f, sp)
                } else if (sky.night > .3f) {
                    val mp = Offset(g.w * .70f, g.h * .14f)
                    val r = g.h * .032f
                    val k = ((1 - cos(2 * PI * sky.moon)) / 2).toFloat()
                    drawCircle(Ivry.Ivoire.copy(alpha = .05f * (1f - clouds)), r * 3.5f, mp)
                    drawCircle(Ivry.Ivoire.copy(alpha = .9f * (1f - clouds * .7f)), r, mp)
                    val dir = if (sky.moon < .5f) -1f else 1f
                    drawCircle(top, r * 1.02f, Offset(mp.x + dir * 2f * r * k, mp.y))
                }
            }
        })

        // 2. Ciel animé : étoiles qui scintillent, nuages qui dérivent
        Spacer(Modifier.fillMaxSize().drawBehind {
            val t = time.value
            val g = Geo(size.width, size.height)
            val clouds = sky.cloud / 100f
            if (sky.night > .2f) for (i in 0 until 90) {
                val x = ((i * 7919) % 1000) / 1000f * g.w
                val y = ((i * 104729) % 1000) / 1000f * g.base * .8f
                val tw = .6f + .4f * sin(t * (.8f + (i % 7) * .15f) + i)
                drawCircle(Ivry.Ivoire.copy(alpha = ((.15f + (i % 5) * .1f) * sky.night * (1f - clouds) * tw).coerceIn(0f, 1f)),
                    1.1f + (i % 3) * .5f, Offset(x, y))
            }
            val n = (clouds * 9).toInt()
            val col = lerp(Color(0xFF2A3036), Color(0xFFB7C0C5), sky.daylight).copy(alpha = .10f + clouds * .14f)
            for (i in 0 until n) {
                val cw = g.w * (.16f + (i % 3) * .05f)
                val speed = g.w / (420f + (i % 4) * 120f)
                val span = g.w + cw
                val cx = ((g.w * ((i * .37f) % 1f) + t * speed) % span) - cw
                val cy = g.h * (.06f + (i % 4) * .07f)
                drawOval(col, Offset(cx, cy), Size(cw, g.h * .06f))
                drawOval(col, Offset(cx + cw * .2f, cy - g.h * .025f), Size(cw * .55f, g.h * .06f))
            }
        })

        // 3. Ville fixe : bâtiments, fenêtres, quais, Seine
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val g = Geo(size.width, size.height)
            val (_, low) = sky.gradient()
            onDrawBehind { drawCity(g, sky, low, p.nature, p.accent2) }
        })

        // 4. Avant-plan animé : RER C sur les quais, reflets, feux des grues, pluie, neige
        Spacer(Modifier.fillMaxSize().drawBehind {
            val t = time.value
            val g = Geo(size.width, size.height)
            val clouds = sky.cloud / 100f
            val night = sky.night

            // RER C le long de la Seine : un passage toutes les 90 s environ
            val cycle = 90f
            val ph = (t % cycle) / 12f
            if (ph < 1f) {
                val len = g.w * .16f
                val x = -len + ph * (g.w + len)
                val y = g.base - g.u * .07f
                drawRect(lerp(Color(0xFF1A1F22), Color(0xFF7D868A), sky.daylight * .6f), Offset(x, y), Size(len, g.u * .06f))
                drawRect(Color(0xFFF3D311).copy(alpha = .9f), Offset(x, y + g.u * .045f), Size(len, g.u * .008f))
                var wx = x + len * .03f
                while (wx < x + len - len * .05f) {
                    drawRect(Ivry.Jaune.copy(alpha = .25f + .6f * night), Offset(wx, y + g.u * .012f), Size(len * .045f, g.u * .022f))
                    wx += len * .07f
                }
                drawLine(Ivry.Jaune.copy(alpha = .15f + .2f * night), Offset(x, g.base + 10f), Offset(x + len, g.base + 10f), 3f)
            }

            // Reflets de l'astre qui ondulent
            val astre = if (sky.daylight > .5f) sky.sunPos(g) else Offset(g.w * .70f, g.h * .14f)
            val refl = if (sky.daylight > .5f) Color(0xFFF6EBD0) else Ivry.Ivoire
            for (i in 0 until 8) {
                val y = g.base + (g.h - g.base) * (.04f + i * .05f)
                val half = g.w * (.035f - i * .003f) * (1f + .25f * sin(t * .9f + i))
                val dx = g.w * .006f * sin(t * .6f + i * 1.7f)
                drawLine(refl.copy(alpha = ((.22f - i * .025f) * (1f - clouds * .7f)).coerceAtLeast(0f)),
                    Offset(astre.x - half + dx, y), Offset(astre.x + half + dx, y), 2f)
            }
            if (night > .3f) for (i in 0 until 16) {
                val rx = g.w * (.05f + i * .06f) + g.w * .004f * sin(t * .7f + i)
                val ry = g.base + 8f + (i % 3) * 7f
                drawLine(Ivry.Jaune.copy(alpha = .16f * night * (.7f + .3f * sin(t + i))), Offset(rx, ry), Offset(rx + g.w * .02f, ry), 2f)
            }

            // Feux des grues qui clignotent la nuit
            if (night > .5f && (t % 2f) < 1f) {
                drawCircle(Color(0xFFE24B4A).copy(alpha = .9f), 2.5f, Offset(g.w * .88f, g.base - g.u * 1.2f - 4f))
                drawCircle(Color(0xFFE24B4A).copy(alpha = .9f), 2.5f, Offset(g.w * .955f, g.base - g.u * .9f - 4f))
            }

            // Pluie (densité selon les mm réels) et neige qui tombent
            if (sky.rain) {
                val n = (30 + sky.precip * 40).toInt().coerceIn(30, 180)
                for (i in 0 until n) {
                    val x0 = ((i * 7919) % 1000) / 1000f * g.w
                    val y = ((((i * 6007) % 1000) / 1000f * g.h) + t * g.h * .9f) % g.h
                    val x = x0 - (y / g.h) * g.h * .1f
                    drawLine(Ivry.Pluie.copy(alpha = .22f), Offset(x, y), Offset(x - g.h * .015f, y + g.h * .045f), 1.5f)
                }
            }
            if (sky.snow) for (i in 0 until 110) {
                val y = ((((i * 6007) % 1000) / 1000f * g.h) + t * g.h * .06f) % g.h
                val x = ((i * 7919) % 1000) / 1000f * g.w + g.w * .01f * sin(t * .8f + i)
                drawCircle(Ivry.Ivoire.copy(alpha = .5f), 1.5f + (i % 3), Offset(x, y))
            }
        })
    }
}

private fun SkyState.sunT(): Float =
    ((minute - sunrise).toFloat() / (sunset - sunrise).coerceAtLeast(1)).coerceIn(0f, 1f)

private fun SkyState.sunPos(g: Geo): Offset {
    val t = sunT()
    return Offset(g.w * (.12f + .76f * t), g.h * (.46f - .36f * sin(PI * t).toFloat()))
}

/** Calque fixe de la ville. */
private fun DrawScope.drawCity(g: Geo, sky: SkyState, low: Color, nature: Color, steel: Color) {
    val w = g.w; val h = g.h; val base = g.base; val u = g.u
    val day = sky.daylight
    val night = sky.night
    val ground = Color(0xFF0D0F10)
    val far = lerp(ground, low, .30f + .15f * day)
    val concrete = lerp(ground, Ivry.Beton, .12f + .14f * day)
    val brick = lerp(ground, Ivry.Brique, .35f + .30f * day)
    val green = lerp(ground, nature, .65f + .35f * day)
    val river = lerp(ground, low, .28f)

    // Plan lointain : Paris et la petite couronne
    for (i in 0 until 34) {
        val x = i * w / 34f
        val th = u * (.22f + ((i * 37) % 11) / 30f)
        drawRect(far, Offset(x, base - th), Size(w / 40f, th))
    }

    // Manufacture des Œillets : brique, toits en sheds, verrières
    val mx0 = w * .08f; val mx1 = w * .33f; val roof = base - u * .42f; val tooth = (mx1 - mx0) / 9f
    drawPath(Path().apply {
        moveTo(mx0, base); lineTo(mx0, roof)
        var x = mx0
        while (x < mx1 - 1f) { lineTo(x, roof - u * .2f); lineTo(x + tooth, roof); x += tooth }
        lineTo(mx1, base); close()
    }, brick)
    var wx = mx0 + tooth * .2f
    while (wx < mx1 - tooth * .4f) {
        drawRect(Ivry.Sable.copy(alpha = .14f + .36f * night), Offset(wx, roof + u * .08f), Size(tooth * .5f, u * .2f))
        wx += tooth
    }

    // Étoiles de Renaudie : volumes triangulaires, terrasses plantées
    tri(Offset(w * .35f, base), Offset(w * .41f, base - u * .75f), Offset(w * .47f, base), concrete)
    tri(Offset(w * .40f, base), Offset(w * .475f, base - u * 1.0f), Offset(w * .55f, base), lerp(concrete, Ivry.Sable, .1f))
    tri(Offset(w * .48f, base), Offset(w * .535f, base - u * .62f), Offset(w * .59f, base), concrete)
    for (k in 1..4) {
        val t = k / 5f
        val py = base - u * (1f - t)
        val rx = w * .475f + w * .075f * t
        val lx = w * .475f - w * .075f * t
        tri(Offset(rx, py), Offset(rx + w * .011f, py), Offset(rx, py - u * .07f), green)
        tri(Offset(lx, py), Offset(lx - w * .011f, py), Offset(lx, py - u * .07f), green.copy(alpha = .8f))
    }

    // Terrasses de Renée Gailhoustet
    val gx = w * .62f; val gw = w * .028f
    drawPath(Path().apply {
        moveTo(gx, base); lineTo(gx, base - u * 1.15f); lineTo(gx + gw, base - u * 1.15f)
        for (s in 0 until 4) {
            val y = base - u * (1.15f - s * .22f)
            lineTo(gx + gw * (1f + s * .5f), y); lineTo(gx + gw * (1f + s * .5f), y + u * .22f)
        }
        lineTo(gx + gw * 3.2f, base - u * .27f); lineTo(gx + gw * 3.2f, base); close()
    }, lerp(concrete, Ivry.Beton, .08f))
    for (s in 0 until 4) {
        val y = base - u * (.93f - s * .22f)
        drawCircle(green, u * .035f, Offset(gx + gw * (1.25f + s * .5f), y - u * .02f))
    }
    windows(gx + gw * .15f, base - u * 1.1f, gw * .7f, u * 1.05f, 2, sky, 3)

    // Grands ensembles
    listOf(.71f to .80f, .755f to .95f, .80f to .70f).forEachIndexed { i, (fx, fh) ->
        val bx = w * fx; val bw = w * .036f; val bt = base - u * fh
        drawRect(concrete, Offset(bx, bt), Size(bw, u * fh))
        windows(bx + bw * .1f, bt + u * .05f, bw * .8f, u * fh - u * .1f, 3, sky, 11 + i * 17)
    }

    // Grues d'Ivry Confluences
    crane(Offset(w * .88f, base), u * 1.2f, w * .08f, steel.copy(alpha = .5f + .2f * day))
    crane(Offset(w * .955f, base), u * .9f, w * .05f, steel.copy(alpha = .35f + .2f * day))

    // Arbres des quais
    for (i in 0 until 14) drawCircle(lerp(ground, nature, .35f + .25f * day), u * .07f, Offset(w * (.03f + i * .072f), base - u * .03f))

    // La Seine
    drawRect(Brush.verticalGradient(listOf(river, lerp(river, ground, .6f)), startY = base, endY = h), Offset(0f, base), Size(w, h - base))

    // Brouillard et orage
    if (sky.fog) for (i in 0 until 5) drawRect(Color(0xFF9AA4A8).copy(alpha = .10f), Offset(0f, h * (.18f + i * .07f)), Size(w, h * .05f))
    if (sky.storm) {
        drawRect(Color(0xFF3C3489).copy(alpha = .10f))
        drawPath(Path().apply {
            moveTo(w * .30f, h * .05f); lineTo(w * .28f, h * .17f); lineTo(w * .305f, h * .17f); lineTo(w * .275f, h * .30f)
        }, Ivry.Ivoire.copy(alpha = .45f), style = Stroke(2f))
    }

    // Voiles de lisibilité : en-tête en haut, modules en bas
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent), endY = h * .18f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .35f)), startY = h * .5f, endY = h))
}

/** Fenêtres qui s'allument progressivement avec la nuit (motif fixe, pas d'animation). */
private fun DrawScope.windows(x: Float, y: Float, w: Float, h: Float, cols: Int, sky: SkyState, seed: Int) {
    val rows = (h / (size.height * .028f)).toInt().coerceAtLeast(1)
    val cw = w / cols
    val rh = h / rows
    for (r in 0 until rows) for (c in 0 until cols) {
        val lit = ((r * 7 + c * 13 + seed * 31) % 100) < (sky.night * 62 + 4)
        if (lit) drawRect(Ivry.Jaune.copy(alpha = .75f), Offset(x + c * cw + cw * .3f, y + r * rh + rh * .3f), Size(cw * .35f, rh * .4f))
    }
}

private fun DrawScope.tri(a: Offset, b: Offset, c: Offset, color: Color) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
}

private fun DrawScope.crane(foot: Offset, height: Float, jib: Float, color: Color) {
    val top = Offset(foot.x, foot.y - height)
    drawLine(color, foot, top, strokeWidth = 3f)
    drawLine(color, Offset(top.x - jib * .35f, top.y + 6f), Offset(top.x + jib, top.y + 6f), strokeWidth = 2.5f)
    drawLine(color, Offset(top.x, top.y - 10f), Offset(top.x + jib * .7f, top.y + 6f), strokeWidth = 1.5f)
    drawLine(color, Offset(top.x + jib * .8f, top.y + 6f), Offset(top.x + jib * .8f, top.y + height * .35f), strokeWidth = 1.2f)
    drawRect(color, Offset(top.x - jib * .35f, top.y + 6f), Size(jib * .12f, 10f), style = Stroke(2f))
}

// ======================= En-tête =======================

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** En-tête posé directement dans le ciel, sans cadre. */
@Composable
fun IvryHeader(weather: CurrentWeather?, online: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val hour = Instant.ofEpochMilli(rememberTick(60_000)).atZone(PARIS).hour
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Ivry-sur-Seine", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = Ivry.Ivoire, letterSpacing = (-0.5).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val line = greeting(hour) + (weather?.let { " · ${wmoLabel(it.code).lowercase()}, ${deg(it.temp)}" } ?: "")
                Text(line, fontSize = 13.sp, color = Ivry.Ivoire.copy(alpha = .85f))
                Spacer(Modifier.width(10.dp))
                Tag(if (online) "EN LIGNE" else "HORS LIGNE", if (online) p.nature else p.warn)
            }
        }
        Clock()
        Spacer(Modifier.width(14.dp))
        Plate(Modifier.size(40.dp), shape = CutCornerShape(topEnd = 12.dp), onClick = onSettings) { f ->
            Icon(Icons.Default.Settings, "Paramètres", tint = if (f) p.focus else Ivry.Ivoire,
                modifier = Modifier.align(Alignment.Center).size(20.dp))
        }
    }
}

@Composable
private fun Clock() {
    val z = Instant.ofEpochMilli(rememberTick(1_000)).atZone(PARIS)
    Column(horizontalAlignment = Alignment.End) {
        Text(TIME.format(z), fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, color = Ivry.Ivoire)
        Text(DATE.format(z).replaceFirstChar { it.uppercase() }, fontSize = 13.sp, color = Ivry.Ivoire.copy(alpha = .85f))
    }
}

private fun greeting(h: Int) = when (h) {
    in 5..11 -> "Bonjour"
    in 12..17 -> "Bon après-midi"
    in 18..22 -> "Bonsoir"
    else -> "Bonne nuit"
}

// ======================= Petits motifs =======================

/** Petite terrasse plantée en coin de module, clin d'œil à Renaudie. */
@Composable
fun BoxScope.TerraceMark(bottom: Boolean = true) {
    val shape = GenericShape { s, _ ->
        if (bottom) { moveTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height) }
        else { moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height) }
        close()
    }
    Box(
        Modifier.align(if (bottom) Alignment.BottomEnd else Alignment.TopEnd).padding(6.dp).size(12.dp)
            .background(LocalPalette.current.nature.copy(alpha = .85f), shape)
    )
}
