package fr.ivry.os.ui

import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import fr.ivry.os.data.ThemeName

object Ivry {
    val Asphalte = Color(0xFF17191A)
    val Brique = Color(0xFF9C4935)
    val Beton = Color(0xFFD8D5C8)
    val Sable = Color(0xFFD1B994)
    val Jaune = Color(0xFFC9A62B)
    val Vert = Color(0xFF57765F)
    val Orange = Color(0xFFE76A43)
    val Bleu = Color(0xFF547C91)
    val Ivoire = Color(0xFFF1EEE7)
    val Pluie = Color(0xFF8FB4C6)
}

@Immutable
data class Palette(
    val theme: ThemeName,
    val bg: Color,
    val glass: Color,
    val accent: Color,
    val accent2: Color,
    val textDim: Color,
    val focus: Color = Ivry.Jaune,
    val nature: Color = Ivry.Vert,
    val text: Color = Ivry.Ivoire,
    val warn: Color = Ivry.Orange,
)

fun paletteFor(t: ThemeName, darkBoost: Boolean, glassLevel: Int): Palette {
    val a = glassLevel.coerceIn(2, 9) / 10f
    val p = when (t) {
        ThemeName.MANUFACTURE -> Palette(t, bg = Ivry.Asphalte, glass = Color(0xFF26292B).copy(alpha = a),
            accent = Ivry.Brique, accent2 = Ivry.Bleu, textDim = Ivry.Beton.copy(alpha = .62f))
        ThemeName.RENAUDIE -> Palette(t, bg = Color(0xFF1C1D1A), glass = Color(0xFF2E2F2A).copy(alpha = a),
            accent = Ivry.Sable, accent2 = Ivry.Beton, textDim = Ivry.Sable.copy(alpha = .7f))
        ThemeName.POPULAIRE -> Palette(t, bg = Color(0xFF1A1716), glass = Color(0xFF2C2522).copy(alpha = a),
            accent = Ivry.Orange, accent2 = Ivry.Brique, textDim = Ivry.Beton.copy(alpha = .65f))
        ThemeName.CONFLUENCES -> Palette(t, bg = Color(0xFF141A1C), glass = Color(0xFF22303A).copy(alpha = a),
            accent = Ivry.Bleu, accent2 = Ivry.Vert, textDim = Color(0xFFB9C6CC))
    }
    return if (darkBoost) p.copy(
        bg = Color(0xFF0A0B0B),
        glass = p.glass.copy(alpha = a * .7f),
        text = Ivry.Ivoire.copy(alpha = .86f),
        textDim = p.textDim.copy(alpha = .5f),
    ) else p
}

/** Variation discrète des éléments lumineux en cas d'inactivité (anti-marquage). */
fun Palette.dimmed() = copy(
    text = text.copy(alpha = text.alpha * .85f),
    focus = focus.copy(alpha = .85f),
    accent = accent.copy(alpha = .8f),
)

val LocalPalette = staticCompositionLocalOf { paletteFor(ThemeName.MANUFACTURE, false, 6) }
val LocalPerf = staticCompositionLocalOf { false }

/** Police système condensée : rappel de la signalétique urbaine. */
val Condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
