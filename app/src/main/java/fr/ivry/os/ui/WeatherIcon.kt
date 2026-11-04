package fr.ivry.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Familles de temps, regroupées à partir des codes WMO d'Open-Meteo. */
enum class Sky { CLEAR, FEW, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN, SNOW, STORM }

fun skyOf(code: Int): Sky = when (code) {
    0 -> Sky.CLEAR
    1 -> Sky.FEW
    2 -> Sky.PARTLY
    3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 66, 80 -> Sky.RAIN
    63, 65, 67, 81, 82 -> Sky.HEAVY_RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.CLOUDY
}

private val SUN = Color(0xFFF2C14E)
private val MOON = Color(0xFFEDE6D3)
private val CLOUD = Color(0xFFDCE2E6)
private val CLOUD_DARK = Color(0xFF9AA4AC)
private val DROP = Color(0xFF7CB7E0)
private val BOLT = Color(0xFFF2C14E)

/** Petit dessin météo, à plat, dans l'esprit des pictogrammes de la ville. */
@Composable
fun WeatherIcon(code: Int, night: Boolean, modifier: Modifier = Modifier) {
    val kind = skyOf(code)
    Canvas(modifier) {
        val s = size.minDimension
        when (kind) {
            Sky.CLEAR -> astre(Offset(s * .5f, s * .5f), s * .26f, night, rays = true)
            Sky.FEW -> {
                astre(Offset(s * .42f, s * .4f), s * .22f, night, rays = true)
                cloud(Offset(s * .62f, s * .7f), s * .5f, CLOUD)
            }
            Sky.PARTLY -> {
                astre(Offset(s * .36f, s * .36f), s * .2f, night, rays = !night)
                cloud(Offset(s * .55f, s * .64f), s * .72f, CLOUD)
            }
            Sky.CLOUDY -> {
                cloud(Offset(s * .62f, s * .46f), s * .5f, CLOUD_DARK)
                cloud(Offset(s * .48f, s * .64f), s * .78f, CLOUD)
            }
            Sky.FOG -> {
                cloud(Offset(s * .5f, s * .45f), s * .7f, CLOUD_DARK)
                for (i in 0..2) drawLine(CLOUD, Offset(s * (.14f + i * .06f), s * (.68f + i * .1f)),
                    Offset(s * (.86f - i * .06f), s * (.68f + i * .1f)), s * .055f, StrokeCap.Round)
            }
            Sky.DRIZZLE -> {
                cloud(Offset(s * .5f, s * .42f), s * .76f, CLOUD)
                for (i in 0..2) drawCircle(DROP, s * .045f, Offset(s * (.32f + i * .18f), s * (.78f + (i % 2) * .08f)))
            }
            Sky.RAIN -> {
                cloud(Offset(s * .5f, s * .4f), s * .76f, CLOUD)
                rain(s, 3, s * .16f)
            }
            Sky.HEAVY_RAIN -> {
                cloud(Offset(s * .5f, s * .38f), s * .8f, CLOUD_DARK)
                rain(s, 5, s * .22f)
            }
            Sky.SNOW -> {
                cloud(Offset(s * .5f, s * .4f), s * .76f, CLOUD)
                for (i in 0..2) flake(Offset(s * (.3f + i * .2f), s * (.8f + (i % 2) * .06f)), s * .07f)
            }
            Sky.STORM -> {
                cloud(Offset(s * .5f, s * .38f), s * .82f, CLOUD_DARK)
                drawPath(Path().apply {
                    moveTo(s * .54f, s * .52f); lineTo(s * .38f, s * .76f); lineTo(s * .5f, s * .76f)
                    lineTo(s * .42f, s * .98f); lineTo(s * .66f, s * .68f); lineTo(s * .53f, s * .68f); close()
                }, BOLT)
                drawLine(DROP, Offset(s * .24f, s * .66f), Offset(s * .2f, s * .8f), s * .05f, StrokeCap.Round)
                drawLine(DROP, Offset(s * .78f, s * .66f), Offset(s * .74f, s * .8f), s * .05f, StrokeCap.Round)
            }
        }
    }
}

/** Soleil à rayons le jour, croissant de lune la nuit. */
private fun DrawScope.astre(c: Offset, r: Float, night: Boolean, rays: Boolean) {
    if (night) {
        val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * .55f, c.y - r * .3f), r * .88f)) }
        drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, full, bite), MOON)
        return
    }
    drawCircle(SUN, r, c)
    if (rays) for (i in 0 until 8) {
        val a = (i * PI / 4).toFloat()
        drawLine(SUN, Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f),
            Offset(c.x + cos(a) * r * 1.75f, c.y + sin(a) * r * 1.75f), r * .22f, StrokeCap.Round)
    }
}

/** Nuage centré en c, de largeur w : trois bosses et une base plate. */
private fun DrawScope.cloud(c: Offset, w: Float, color: Color) {
    val h = w * .42f
    val base = c.y + h * .38f
    drawRoundRect(color, Offset(c.x - w / 2, base - h * .46f), Size(w, h * .46f),
        androidx.compose.ui.geometry.CornerRadius(h * .23f))
    drawCircle(color, h * .34f, Offset(c.x - w * .2f, base - h * .42f))
    drawCircle(color, h * .46f, Offset(c.x + w * .06f, base - h * .54f))
    drawCircle(color, h * .28f, Offset(c.x + w * .3f, base - h * .32f))
}

private fun DrawScope.rain(s: Float, n: Int, len: Float) {
    for (i in 0 until n) {
        val x = s * (.24f + i * (.52f / (n - 1)))
        val y = s * (.66f + (i % 2) * .06f)
        drawLine(DROP, Offset(x, y), Offset(x - len * .3f, y + len), s * .05f, StrokeCap.Round)
    }
}

private fun DrawScope.flake(c: Offset, r: Float) {
    for (i in 0 until 3) {
        val a = (i * PI / 3).toFloat()
        drawLine(Color.White, Offset(c.x - cos(a) * r, c.y - sin(a) * r), Offset(c.x + cos(a) * r, c.y + sin(a) * r),
            r * .3f, StrokeCap.Round)
    }
}

/** Nuit à cette heure-là ? Les heures de lever/coucher du jour servent pour les heures à venir. */
fun isNightHour(hour: Int, sunriseMs: Long?, sunsetMs: Long?): Boolean {
    fun h(ms: Long?) = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(fr.ivry.os.data.PARIS).hour }
    val rise = h(sunriseMs) ?: 8
    val set = h(sunsetMs) ?: 18
    return hour < rise || hour >= set
}
