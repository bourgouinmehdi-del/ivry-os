package fr.ivry.os.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ivry.os.data.AppEntry
import fr.ivry.os.data.Live
import fr.ivry.os.data.PARIS
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun rememberTick(periodMs: Long): Long {
    val state = produceState(System.currentTimeMillis(), periodMs) {
        while (true) { delay(periodMs); value = System.currentTimeMillis() }
    }
    return state.value
}

/** Plaque de verre fumé : surface de base de toute l'interface. Focusable seulement si onClick est fourni. */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    focusRequester: FocusRequester? = null,
    background: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    val perf = LocalPerf.current
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused && !perf) 1.035f else 1f, tween(140), label = "plate")
    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(background ?: p.glass)
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = .06f), .35f to Color.Transparent))
        .border(if (focused) 3.dp else 1.dp, if (focused) p.focus else p.text.copy(alpha = .09f), shape)
    if (onClick != null) {
        if (focusRequester != null) m = m.focusRequester(focusRequester)
        m = m.onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Box(m) { content(focused) }
}

@Composable
fun Logo(size: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("IVRY", fontSize = size, fontWeight = FontWeight.Black, color = p.text,
            letterSpacing = (-0.5).sp, fontFamily = Condensed)
        Box(
            Modifier.padding(horizontal = (size.value * .16f).dp)
                .size(width = (size.value * .38f).dp, height = (size.value * .82f).dp)
                .background(p.accent, CutCornerShape(topEndPercent = 100))
        )
        Text("OS", fontSize = size, fontWeight = FontWeight.Light, color = p.text, fontFamily = Condensed)
    }
}

@Composable
fun PanelLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CutCornerShape(topEndPercent = 100)))
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), color = LocalPalette.current.text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp, fontFamily = Condensed)
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text, color = Ivry.Asphalte, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
        fontFamily = Condensed,
        modifier = Modifier.background(color, CutCornerShape(topEnd = 6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun LineBadge(code: String, color: Color, round: Boolean) {
    Box(
        Modifier.size(28.dp).background(color, if (round) CircleShape else RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(code, color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 16.sp) }
}

@Composable
fun ActionButton(
    label: String,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Plate(modifier.height(34.dp), shape = CutCornerShape(topEnd = 10.dp), focusRequester = focusRequester, onClick = onClick) { f ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (f) p.focus else p.text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = if (f) p.focus else p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = Condensed, letterSpacing = .8.sp)
        }
    }
}

/** Indication de fraîcheur : chaque donnée périmée ou en échec est signalée. */
@Composable
fun Freshness(live: Live<*>, now: Long, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val (text, warn) = when {
        live.value == null && live.error != null -> "Indisponible · ${live.error}" to true
        live.value == null -> "Chargement…" to false
        live.isStale(now) -> "PÉRIMÉ · donnée de ${hhmm(live.fetchedAt)}" to true
        live.error != null -> "Échec actualisation · valeur de ${hhmm(live.fetchedAt)}" to true
        else -> "Actualisé ${ago(now - live.fetchedAt)}" to false
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (warn) {
            Icon(Icons.Default.Warning, null, tint = p.warn, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 9.5.sp, color = if (warn) p.warn else p.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
fun hhmm(ms: Long): String = HHMM.format(Instant.ofEpochMilli(ms).atZone(PARIS))

fun ago(d: Long): String = when {
    d < 20_000 -> "à l'instant"
    d < 60_000 -> "il y a ${d / 1000} s"
    d < 3_600_000 -> "il y a ${d / 60_000} min"
    else -> "il y a ${d / 3_600_000} h"
}

@Composable
fun AppTile(app: AppEntry, favorite: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Plate(modifier, onClick = onClick) { f ->
        if (app.banner != null) {
            Image(bitmap = app.banner, contentDescription = app.label, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                app.icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.height(4.dp))
                Text(app.label, fontSize = 11.sp, color = p.text, maxLines = 2, textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (favorite) Icon(Icons.Default.Star, null, tint = p.focus,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp))
        if (f && app.banner != null) Text(
            app.label, fontSize = 10.sp, color = Ivry.Ivoire, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = .65f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun EditDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
