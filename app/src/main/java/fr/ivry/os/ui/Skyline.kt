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
    val month: Int = 1,
    val day: Int = 1,
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
            month = Instant.ofEpochMilli(now).atZone(PARIS).monthValue,
            day = Instant.ofEpochMilli(now).atZone(PARIS).dayOfMonth,
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
    val base = h * .395f
    val u = h * .27f
}

/** Horloge de la scène, en secondes, rafraîchie à 20 images/s au plus. Figée si désactivée. */
@Composable
private fun rememberSceneClock(enabled: Boolean, slow: Boolean): State<Float> {
    val state = remember { mutableFloatStateOf((System.currentTimeMillis() % 3_600_000L) / 1000f) }
    LaunchedEffect(enabled, slow) {
        val step = 33L
        if (!enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (now - last >= step) {
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
fun CityScene(
    sky: SkyState,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    slow: Boolean = false,
    trainTimes: List<Long> = emptyList(),
) {
    val p = LocalPalette.current
    val time = rememberSceneClock(enabled = animate, slow = slow)
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

            // RER C : un arrêt en gare toutes les 90 s, avec voyageurs
            drawRer(g, sky, t, trainTimes)

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

            // Moments de la ville : feux d'artifice (14 juillet, Nouvel An), guirlandes en décembre
            val fireworks = (sky.month == 7 && sky.day == 14) || (sky.month == 12 && sky.day == 31) || (sky.month == 1 && sky.day == 1)
            if (fireworks && night > .5f) for (b in 0 until 3) {
                val period = 3.4f
                val tt = t + b * 1.13f
                val k = (tt / period).toInt()
                val ph = (tt % period) / period
                val cx = g.w * (.18f + ((k * 37 + b * 13) % 64) / 100f)
                val cy = g.h * (.08f + ((k * 17 + b * 7) % 16) / 100f)
                val col = listOf(Ivry.Jaune, Ivry.Orange, Ivry.Ivoire, Color(0xFFFA9ABA), Ivry.Pluie)[(k + b) % 5]
                if (ph < .18f) {
                    drawLine(col.copy(alpha = .7f), Offset(cx, g.base), Offset(cx, g.base - (g.base - cy) * ph / .18f), 2f)
                } else {
                    val e = (ph - .18f) / .82f
                    val r = g.h * .09f * e
                    for (i in 0 until 20) {
                        val a = 2 * PI.toFloat() * i / 20
                        drawCircle(col.copy(alpha = (1f - e).coerceIn(0f, 1f)), 2.2f,
                            Offset(cx + cos(a) * r, cy + sin(a) * r + e * e * g.h * .025f))
                    }
                    drawCircle(col.copy(alpha = .08f * (1f - e)), r * 1.2f, Offset(cx, cy))
                }
            }
            if (sky.month == 12) {
                val y0 = g.base - g.u * .14f
                val bulbs = listOf(Ivry.Jaune, Ivry.Orange, Ivry.Ivoire, Color(0xFF7FA387))
                for (sw in 0 until 6) {
                    val x0 = g.w * (.02f + sw * .165f)
                    val x1 = x0 + g.w * .15f
                    var i = 0
                    var x = x0
                    while (x <= x1) {
                        val f = (x - x0) / (x1 - x0)
                        val y = y0 + 4f * f * (1f - f) * g.u * .07f
                        val glow = .45f + .45f * sin(t * 2.2f + i * 1.3f + sw)
                        drawCircle(bulbs[(i + sw) % bulbs.size].copy(alpha = (glow * (.4f + .6f * night)).coerceIn(0f, 1f)), 2.2f, Offset(x, y))
                        x += g.w * .011f; i++
                    }
                }
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
    return Offset(g.w * (.12f + .76f * t), g.h * (.40f - .28f * sin(PI * t).toFloat()))
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

    // Voie du RER C le long des quais
    val ty = base - u * .10f
    drawRect(lerp(ground, Ivry.Beton, .14f + .10f * day), Offset(0f, ty + u * .11f), Size(w, u * .03f))
    var px = 0f
    while (px < w) { drawRect(lerp(ground, Ivry.Beton, .10f + .08f * day), Offset(px, ty + u * .14f), Size(u * .02f, u * .06f)); px += w * .05f }
    // Caténaire : poteaux et fil au-dessus de la voie
    val deck = base + u * .01f
    val wire = deck - u * .23f
    val mast = lerp(ground, Ivry.Beton, .22f + .15f * day)
    var mx = w * .02f
    while (mx < w) {
        drawLine(mast, Offset(mx, deck), Offset(mx, wire - u * .03f), 2f)
        drawLine(mast, Offset(mx, wire - u * .02f), Offset(mx + w * .015f, wire - u * .02f), 1.5f)
        mx += w * .09f
    }
    drawLine(mast, Offset(0f, wire), Offset(w, wire), 1.2f)

    // Gare d'Ivry-sur-Seine : quai, abri, panneau RER
    val q0 = w * STATION_X0; val q1 = w * STATION_X1
    drawRect(lerp(ground, Ivry.Beton, .30f + .25f * day), Offset(q0, deck), Size(q1 - q0, u * .05f))
    drawRect(lerp(ground, Ivry.Beton, .18f + .15f * day), Offset(q0, deck + u * .05f), Size(q1 - q0, u * .03f))
    val roofY = deck - u * .21f
    drawRect(lerp(ground, Ivry.Beton, .25f + .2f * day), Offset(q0 + w * .02f, roofY), Size(w * .16f, u * .02f))
    for (k in 0..3) drawLine(mast, Offset(q0 + w * (.03f + k * .05f), roofY), Offset(q0 + w * (.03f + k * .05f), deck), 2f)
    drawRect(Color(0xFF1F2D4A), Offset(q0 + w * .005f, deck - u * .16f), Size(w * .022f, u * .07f))
    drawCircle(Color(0xFFF3D311), u * .022f, Offset(q0 + w * .016f, deck - u * .125f))
    drawLine(mast, Offset(q0 + w * .016f, deck - u * .09f), Offset(q0 + w * .016f, deck), 2f)

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

// ======================= RER C et voyageurs =======================

private const val STATION_X0 = .16f
private const val STATION_X1 = .62f

private fun h32(a: Long, b: Int = 0): Int {
    var x = a * 31L + b * 1_000_003L
    x = x xor (x ushr 33)
    x *= 0x62A9D9ED799705F5L
    x = x xor (x ushr 29)
    return (x and 0x7fffffffL).toInt()
}

private fun DrawScope.drawRer(g: Geo, sky: SkyState, t: Float, @Suppress("UNUSED_PARAMETER") realTimes: List<Long>) {
    val night = sky.night
    val n = 4
    val len = g.w * .40f
    val car = len / n
    val hh = g.u * .16f
    val deck = g.base + g.u * .01f
    val xStop = g.w * (STATION_X0 + .03f)
    val now = System.currentTimeMillis()

    // Un arrêt en gare toutes les 90 s (rythme fixe, indépendant des vrais horaires)
    val cycle = 90_000L
    val k = now / cycle
    val times = listOf((k - 1) * cycle + 12_000L, k * cycle + 12_000L)
    for (tt in times) {
        val s = (now - tt) / 1000f
        if (s < -12f || s > 26f) continue
        val x = when {
            s < -4f -> { val q = (s + 12f) / 8f; val e = 1f - (1f - q) * (1f - q); g.w + 10f + (xStop - g.w - 10f) * e }
            s < 6f -> xStop
            else -> { val q = ((s - 6f) / 8f).coerceAtMost(1.2f); xStop + (-len - 20f - xStop) * q * q }
        }
        if (x > -len - 10f) drawTrain(x, deck, hh, car, n, sky, doorsOpen = s in -3.5f..5.5f)

        // Voyageurs qui descendent : plus nombreux aux heures de pointe
        val hour = sky.minute / 60
        val crowd = when (hour) { in 7..9, in 17..19 -> 26; in 22..23, in 0..5 -> 4; else -> 12 }
        val q0 = g.w * STATION_X0; val q1 = g.w * STATION_X1
        val size = hh * .62f
        for (i in 0 until crowd) {
            val ts = -3.2f + i * .2f
            if (s < ts) continue
            val r = h32(tt, i)
            val doorCar = (r % n)
            val doorX = xStop + doorCar * car + car * (if ((r shr 3) % 2 == 0) .11f else .89f)
            val dir = if ((r shr 5) % 2 == 0) -1f else 1f
            val speed = g.w * (.018f + ((r shr 7) % 10) / 900f)
            val px = doorX + dir * speed * (s - ts)
            if (px < q0 || px > q1) continue
            val shade = listOf(Color(0xFF2A2F3A), Color(0xFF4A2F2A), Color(0xFF2F3A2E), Color(0xFF3A3346), Color(0xFF203040))[r % 5]
            person(px, deck, size, lerp(shade, Color(0xFF8A919A), sky.daylight * .4f), (s - ts) * 9f, moving = true)
        }

        // Quelqu'un court après le train, de temps en temps
        val rr = h32(tt, 999)
        if (rr % 3 == 0) {
            val catches = (rr shr 4) % 2 == 0
            val target = xStop + len * .97f
            val speed = g.w * .07f
            val arrive = if (catches) 5.0f else 7.0f
            val x0 = target + speed * (arrive - 1.5f)
            if (s >= 1.5f) {
                if (s < arrive) person(x0 - speed * (s - 1.5f), deck, size, Color(0xFFE76A43), s * 16f, moving = true)
                else if (!catches && s < 14f) person(target, deck, size, Color(0xFFE76A43), 0f, moving = false, armsUp = true)
            }
        }
    }
}

private fun DrawScope.drawTrain(x: Float, deck: Float, hh: Float, car: Float, n: Int, sky: SkyState, doorsOpen: Boolean) {
    val y = deck - hh
    val body = lerp(Color(0xFF3A4046), Color(0xFFE9EBEC), .25f + .75f * sky.daylight)
    val band = Color(0xFF1F2D4A)
    val lit = lerp(Color(0xFF55606A), Color(0xFFF2D47A), sky.night)
    val steelC = lerp(Color(0xFF1A1F22), Color(0xFF8A9296), sky.daylight)
    for (c in 0 until n) {
        val cx = x + c * car
        when (c) {
            0 -> drawPath(Path().apply {
                moveTo(cx + car * .12f, y); lineTo(cx + car - 2f, y); lineTo(cx + car - 2f, y + hh)
                lineTo(cx, y + hh); lineTo(cx, y + hh * .45f); close()
            }, body)
            n - 1 -> drawPath(Path().apply {
                moveTo(cx + 1f, y); lineTo(cx + car * .88f, y); lineTo(cx + car, y + hh * .45f)
                lineTo(cx + car, y + hh); lineTo(cx + 1f, y + hh); close()
            }, body)
            else -> drawRect(body, Offset(cx + 1f, y), Size(car - 3f, hh))
        }
        for (row in 0..1) {
            val by = y + hh * (.12f + row * .34f)
            drawRect(band, Offset(cx + car * .16f, by), Size(car * .68f, hh * .24f))
            var wx = cx + car * .19f
            while (wx < cx + car * .8f) {
                drawRect(lit, Offset(wx, by + hh * .04f), Size(car * .05f, hh * .16f))
                wx += car * .075f
            }
        }
        for (d in listOf(.09f, .87f)) {
            val door = if (doorsOpen) lerp(Color(0xFF55606A), Color(0xFFF2D47A), .5f + .5f * sky.night) else band
            drawRect(door, Offset(cx + car * d, y + hh * .36f), Size(car * .045f, hh * .5f))
        }
        drawRect(Color(0xFFC8102E), Offset(cx + 1f, y + hh * .86f), Size(car - 3f, hh * .05f))
    }
    drawPath(Path().apply {
        moveTo(x + car * .12f, y + hh * .05f); lineTo(x + car * .2f, y + hh * .05f)
        lineTo(x + car * .06f, y + hh * .4f); lineTo(x + car * .01f, y + hh * .4f); close()
    }, band)
    drawCircle(Ivry.Ivoire.copy(alpha = .95f), hh * .05f, Offset(x + car * .03f, y + hh * .72f))
    drawCircle(Ivry.Ivoire.copy(alpha = .95f), hh * .05f, Offset(x + car * .08f, y + hh * .72f))
    val pxx = x + car * 1.5f
    val top = deck - size.height * .27f * .23f
    drawLine(steelC, Offset(pxx - car * .08f, y), Offset(pxx, top), 1.5f)
    drawLine(steelC, Offset(pxx + car * .08f, y), Offset(pxx, top), 1.5f)
    drawLine(steelC, Offset(pxx - car * .07f, top), Offset(pxx + car * .07f, top), 2f)
}

/** Silhouette simple : tête, corps, jambes qui alternent en marchant. */
private fun DrawScope.person(x: Float, foot: Float, s: Float, color: Color, phase: Float, moving: Boolean, armsUp: Boolean = false) {
    val swing = if (moving) sin(phase) * s * .14f else 0f
    val hip = foot - s * .38f
    drawLine(color, Offset(x, hip), Offset(x - swing, foot), s * .09f)
    drawLine(color, Offset(x, hip), Offset(x + swing, foot), s * .09f)
    drawRect(color, Offset(x - s * .1f, foot - s * .78f), Size(s * .2f, s * .42f))
    drawCircle(color, s * .11f, Offset(x, foot - s * .88f))
    if (armsUp) {
        drawLine(color, Offset(x - s * .08f, foot - s * .74f), Offset(x - s * .2f, foot - s * 1.05f), s * .07f)
        drawLine(color, Offset(x + s * .08f, foot - s * .74f), Offset(x + s * .2f, foot - s * 1.05f), s * .07f)
    }
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
