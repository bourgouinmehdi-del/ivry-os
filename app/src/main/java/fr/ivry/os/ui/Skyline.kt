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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** Bandeau d'en-tête : ville, heure, et ligne d'horizon d'Ivry qui suit l'heure et la météo réelles. */
@Composable
fun IvryHero(weather: CurrentWeather?, online: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val hour = Instant.ofEpochMilli(rememberTick(60_000)).atZone(PARIS).hour
    Box(modifier.clip(CutCornerShape(bottomEnd = 34.dp))) {
        Skyline(hour, weather?.code ?: -1, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Ivry-sur-Seine", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ivry.Ivoire,
                    letterSpacing = (-0.5).sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val line = greeting(hour) + (weather?.let { " · ${wmoLabel(it.code)}, ${deg(it.temp)}" } ?: "")
                    Text(line, fontSize = 12.sp, color = Ivry.Ivoire.copy(alpha = .82f))
                    Spacer(Modifier.width(10.dp))
                    Tag(if (online) "EN LIGNE" else "HORS LIGNE", if (online) p.nature else p.warn)
                }
            }
            Clock()
            Spacer(Modifier.width(12.dp))
            Plate(Modifier.size(38.dp), shape = CutCornerShape(topEnd = 12.dp), onClick = onSettings) { f ->
                Icon(Icons.Default.Settings, "Paramètres", tint = if (f) p.focus else Ivry.Ivoire,
                    modifier = Modifier.align(Alignment.Center).size(19.dp))
            }
        }
    }
}

@Composable
private fun Clock() {
    val z = Instant.ofEpochMilli(rememberTick(1_000)).atZone(PARIS)
    Column(horizontalAlignment = Alignment.End) {
        Text(TIME.format(z), fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = Ivry.Ivoire)
        Text(DATE.format(z).replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = Ivry.Ivoire.copy(alpha = .8f))
    }
}

private fun greeting(h: Int) = when (h) {
    in 5..11 -> "Bonjour"
    in 12..17 -> "Bon après-midi"
    in 18..22 -> "Bonsoir"
    else -> "Bonne nuit"
}

/**
 * Silhouette dessinée, sans photo : Manufacture des Œillets (toits en sheds), étoiles de Renaudie,
 * terrasses de Renée Gailhoustet, grands ensembles, grues d'Ivry Confluences, la Seine au premier plan.
 */
@Composable
private fun Skyline(hour: Int, code: Int, modifier: Modifier) {
    val p = LocalPalette.current
    Spacer(modifier.drawWithCache {
        val w = size.width
        val h = size.height
        val night = hour < 6 || hour >= 20
        val dusk = hour in 17..19
        val dawn = hour in 6..8
        val cloudy = code in listOf(2, 3, 45, 48) || code >= 51
        val snow = code in 71..77 || code in 85..86
        val rain = code >= 51 && !snow

        val top = when { night -> Color(0xFF0B1116); dusk -> Color(0xFF2A2233); dawn -> Color(0xFF26283A); else -> Color(0xFF2B4250) }
        var low = when { night -> Color(0xFF1B2530); dusk -> Color(0xFF8A4A3A); dawn -> Color(0xFF7A5A55); else -> Color(0xFF5F7F8C) }
        if (cloudy) low = lerp(low, Color(0xFF3A3F44), .55f)
        val sky = Brush.verticalGradient(listOf(top, low))
        val ground = Color(0xFF0E1011)
        val far = lerp(ground, low, .38f)
        val concrete = lerp(ground, Ivry.Beton, .16f)
        val brick = lerp(ground, Ivry.Brique, .5f)
        val green = p.nature
        val base = h * .80f
        val light = Ivry.Jaune.copy(alpha = if (night) .75f else .0f)

        onDrawBehind {
            drawRect(sky)

            // Astre
            if (night) drawCircle(Ivry.Ivoire.copy(alpha = .75f), h * .06f, Offset(w * .60f, h * .24f))
            else if (!cloudy) drawCircle(
                (if (dusk || dawn) Ivry.Orange else Ivry.Ivoire).copy(alpha = if (dusk || dawn) .55f else .22f),
                h * .09f, Offset(w * .60f, h * (if (dusk || dawn) .62f else .26f)),
            )
            if (cloudy) for (i in 0..5) {
                val cx = w * (.08f + i * .17f)
                drawOval(Ivry.Beton.copy(alpha = .07f), Offset(cx, h * (.12f + (i % 3) * .07f)), Size(w * .14f, h * .12f))
            }

            // Plan lointain : Paris et la petite couronne
            for (i in 0 until 26) {
                val x = i * w / 26f
                val th = h * (.12f + ((i * 37) % 11) / 55f)
                drawRect(far, Offset(x, base - th), Size(w / 30f, th))
            }

            // Manufacture des Œillets : brique et toits en sheds
            val mx0 = w * .03f; val mx1 = w * .27f; val roof = base - h * .20f; val tooth = (mx1 - mx0) / 8f
            drawPath(Path().apply {
                moveTo(mx0, base); lineTo(mx0, roof)
                var x = mx0
                while (x < mx1 - 1f) { lineTo(x, roof - h * .09f); lineTo(x + tooth, roof); x += tooth }
                lineTo(mx1, base); close()
            }, brick)
            var wx = mx0 + tooth * .25f
            while (wx < mx1 - tooth * .4f) {
                drawRect(Ivry.Sable.copy(alpha = if (night) .35f else .18f), Offset(wx, roof + h * .04f), Size(tooth * .45f, h * .09f))
                wx += tooth
            }

            // Étoiles de Renaudie : volumes triangulaires en gradins, terrasses plantées
            tri(Offset(w * .29f, base), Offset(w * .365f, base - h * .38f), Offset(w * .44f, base), concrete)
            tri(Offset(w * .35f, base), Offset(w * .44f, base - h * .50f), Offset(w * .53f, base), lerp(concrete, Ivry.Sable, .12f))
            tri(Offset(w * .43f, base), Offset(w * .50f, base - h * .32f), Offset(w * .57f, base), concrete)
            for (k in 1..4) {
                val t = k / 5f
                val px = w * .44f + (w * .09f) * t
                val py = base - h * .50f * (1 - t)
                tri(Offset(px, py), Offset(px + w * .012f, py), Offset(px, py - h * .035f), green.copy(alpha = .8f))
                val qx = w * .44f - (w * .09f) * t
                tri(Offset(qx, py), Offset(qx - w * .012f, py), Offset(qx, py - h * .035f), green.copy(alpha = .6f))
            }

            // Terrasses de Renée Gailhoustet : tour en gradins végétalisés
            val gx = w * .60f; val gw = w * .05f
            drawPath(Path().apply {
                moveTo(gx, base); lineTo(gx, base - h * .64f); lineTo(gx + gw * .6f, base - h * .64f)
                for (s in 0 until 4) {
                    val y = base - h * (.64f - s * .13f)
                    lineTo(gx + gw * (.6f + s * .2f), y); lineTo(gx + gw * (.6f + s * .2f), y + h * .13f)
                    lineTo(gx + gw * (.8f + s * .2f), y + h * .13f)
                }
                lineTo(gx + gw * 1.4f, base); close()
            }, lerp(concrete, Ivry.Beton, .1f))
            for (s in 0 until 4) {
                val y = base - h * (.51f - s * .13f)
                drawCircle(green, h * .018f, Offset(gx + gw * (.72f + s * .2f), y - h * .012f))
            }

            // Grands ensembles
            listOf(.68f to .44f, .73f to .36f, .78f to .52f).forEach { (fx, fh) ->
                val bx = w * fx; val bw = w * .04f; val bt = base - h * fh
                drawRect(concrete, Offset(bx, bt), Size(bw, h * fh))
                var yy = bt + h * .05f
                var row = 0
                while (yy < base - h * .04f) {
                    for (c in 0..2) if ((row + c + fx.hashCode()) % 3 == 0)
                        drawRect(light, Offset(bx + bw * (.15f + c * .28f), yy), Size(bw * .14f, h * .025f))
                    yy += h * .06f; row++
                }
            }

            // Grues d'Ivry Confluences
            crane(Offset(w * .87f, base), h * .62f, w * .09f, p.accent2.copy(alpha = .55f))
            crane(Offset(w * .95f, base), h * .48f, w * .06f, p.accent2.copy(alpha = .4f))

            // Arbres des quais
            for (i in 0 until 9) drawCircle(lerp(ground, green, .45f), h * .045f, Offset(w * (.05f + i * .11f), base - h * .02f))

            // La Seine
            drawRect(lerp(ground, Ivry.Bleu, .32f), Offset(0f, base), Size(w, h - base))
            for (i in 0 until 14) {
                val rx = w * (.02f + i * .07f)
                drawLine(
                    (if (night) Ivry.Jaune else Ivry.Ivoire).copy(alpha = if (night) .22f else .12f),
                    Offset(rx, base + h * (.06f + (i % 3) * .04f)), Offset(rx + w * .03f, base + h * (.06f + (i % 3) * .04f)),
                    strokeWidth = 2f,
                )
            }

            // Pluie ou neige
            if (rain) for (i in 0 until 60) {
                val x = (i * 97 % 1000) / 1000f * w; val y = (i * 53 % 1000) / 1000f * base
                drawLine(Ivry.Pluie.copy(alpha = .22f), Offset(x, y), Offset(x - h * .03f, y + h * .08f), 1.5f)
            }
            if (snow) for (i in 0 until 50) {
                drawCircle(Ivry.Ivoire.copy(alpha = .45f), 2.2f, Offset((i * 97 % 1000) / 1000f * w, (i * 53 % 1000) / 1000f * base))
            }

            // Voile pour la lisibilité du texte
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent, Color.Black.copy(alpha = .3f))))
        }
    })
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

/** Frise en dents de scie : les toits en sheds de la Manufacture. */
@Composable
fun ShedFrieze(modifier: Modifier = Modifier, color: Color = LocalPalette.current.accent) {
    Spacer(modifier.fillMaxWidth().height(8.dp).drawWithCache {
        val tooth = size.height * 2.4f
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x < size.width) { lineTo(x, 0f); lineTo(x + tooth, size.height); x += tooth }
            close()
        }
        onDrawBehind { drawPath(path, color.copy(alpha = .4f)) }
    })
}

/** Petite terrasse plantée en coin de module, clin d'œil à Renaudie. */
@Composable
fun BoxScope.TerraceMark(bottom: Boolean = true) {
    val shape = GenericShape { s, _ ->
        if (bottom) { moveTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height) }
        else { moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height) }
        close()
    }
    Box(
        Modifier.align(if (bottom) Alignment.BottomEnd else Alignment.TopEnd).padding(6.dp).size(14.dp)
            .background(LocalPalette.current.nature.copy(alpha = .85f), shape)
    )
}
