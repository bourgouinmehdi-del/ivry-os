package fr.ivry.os.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ivry.os.MainViewModel
import fr.ivry.os.data.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

val M7_COLOR = Color(0xFFFA9ABA)
val RERC_COLOR = Color(0xFFF3D311)

@Composable
fun HomeScreen(
    vm: MainViewModel,
    prefs: Prefs,
    apps: List<AppEntry>,
    onDetail: (Detail) -> Unit,
    onSection: (Section) -> Unit,
) {
    val p = LocalPalette.current
    val e = vm.engine
    val weather by e.weatherNow.collectAsStateWithLifecycle()
    val forecast by e.forecast.collectAsStateWithLifecycle()
    val metro by e.metro.collectAsStateWithLifecycle()
    val rer by e.rer.collectAsStateWithLifecycle()
    val velib by e.velib.collectAsStateWithLifecycle()
    val online by e.online.collectAsStateWithLifecycle()

    val canal = remember(apps) { AppsRepository.findCanal(apps) }
    val youtube = remember(apps) { AppsRepository.findYoutube(apps) }
    val others = remember(apps, prefs.favorites, prefs.launchCounts) {
        AppsRepository.secondary(apps, prefs, setOfNotNull(canal?.pkg, youtube?.pkg))
    }
    val videoCount = remember(apps) { apps.count { it.isVideo } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 26.dp, top = 16.dp, bottom = 14.dp)) {
        TopBar(online) { onSection(Section.SETTINGS) }
        Spacer(Modifier.height(10.dp))

        // ZONE 2 — IVRY LIVE : quatre modules visibles simultanément
        Row(Modifier.fillMaxWidth().weight(.44f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherPanel(weather, forecast, Modifier.weight(1.3f)) { onDetail(Detail.WEATHER) }
            TransitPanel("7", true, M7_COLOR, "MÉTRO 7", "Mairie d'Ivry", "→ La Courneuve – 8 Mai 1945",
                metro, CutCornerShape(bottomStart = 22.dp), Modifier.weight(1f)) { onDetail(Detail.METRO) }
            TransitPanel("C", false, RERC_COLOR, "RER C", "Ivry-sur-Seine", "→ Paris et nord de la ligne",
                rer, CutCornerShape(topStart = 22.dp), Modifier.weight(1f)) { onDetail(Detail.RER) }
            VelibPanel(velib, Modifier.weight(.9f)) { onDetail(Detail.VELIB) }
        }
        Spacer(Modifier.height(12.dp))

        // ZONE 3 — deux applications prioritaires, composition asymétrique
        Row(Modifier.fillMaxWidth().weight(.30f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAppCard("myCANAL", "CANAL+", canal, AppsRepository.CANAL_STORE, Color(0xFF0B0B0B),
                CutCornerShape(topStart = 26.dp), Modifier.weight(1f), firstFocus, vm)
            BigAppCard("YouTube", "Vidéo", youtube, AppsRepository.YOUTUBE_TV, Color(0xFF1E1E1E),
                CutCornerShape(bottomEnd = 26.dp), Modifier.weight(1f), null, vm)
            Plate(Modifier.weight(.55f).fillMaxHeight(), shape = CutCornerShape(topEnd = 40.dp),
                background = p.accent.copy(alpha = .22f), onClick = { onSection(Section.CINEMA) }) { f ->
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    PanelLabel("Cinéma", p.accent)
                    Column {
                        Text("$videoCount", fontSize = 34.sp, fontWeight = FontWeight.Black, color = p.text)
                        Text("applis vidéo installées", fontSize = 11.sp, color = if (f) p.focus else p.textDim)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // « Le reste »
        PanelLabel("Le reste", p.nature)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(.20f)) {
            if (others.isEmpty()) {
                Text("Aucune autre application détectée.", fontSize = 12.sp, color = p.textDim)
            } else {
                LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(others, key = { it.pkg }) { app ->
                        AppTile(app, app.pkg in prefs.favorites, Modifier.fillMaxHeight().aspectRatio(16f / 9f)) {
                            vm.requestLaunch(app.pkg)
                        }
                    }
                }
            }
        }
    }
}

// ---------- ZONE 1 ----------

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

@Composable
private fun TopBar(online: Boolean, onSettings: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("IVRY-SUR-SEINE", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                color = p.text, fontFamily = Condensed)
            Text("Val-de-Marne · 94", fontSize = 10.sp, color = p.textDim)
        }
        Spacer(Modifier.weight(1f))
        Tag(if (online) "EN LIGNE" else "HORS LIGNE", if (online) p.nature else p.warn)
        Spacer(Modifier.width(18.dp))
        Clock()
        Spacer(Modifier.width(14.dp))
        Plate(Modifier.size(40.dp), shape = CutCornerShape(topEnd = 12.dp), onClick = onSettings) { f ->
            Icon(Icons.Default.Settings, "Paramètres", tint = if (f) p.focus else p.text,
                modifier = Modifier.align(Alignment.Center).size(20.dp))
        }
    }
}

@Composable
private fun Clock() {
    val p = LocalPalette.current
    val now = rememberTick(1_000)
    val z = Instant.ofEpochMilli(now).atZone(PARIS)
    Column(horizontalAlignment = Alignment.End) {
        Text(TIME.format(z), fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, color = p.text,
            fontFamily = Condensed)
        Text(DATE.format(z).replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = p.textDim)
    }
}

// ---------- Modules IVRY LIVE ----------

fun deg(v: Double) = "${v.roundToInt()}°"
fun one(v: Double) = String.format(Locale.FRANCE, "%.1f", v)

@Composable
private fun WeatherPanel(weather: Live<CurrentWeather>, forecast: Live<Forecast>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(topEnd = 26.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            PanelLabel("Météo", p.nature)
            Spacer(Modifier.height(6.dp))
            val w = weather.value
            if (w == null) {
                Text(weather.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(deg(w.temp), fontSize = 50.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, color = p.text,
                        fontFamily = Condensed)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(wmoLabel(w.code), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("Ressenti ${deg(w.feels)}", fontSize = 12.sp, color = p.textDim)
                        Text("Min ${deg(w.min)} · Max ${deg(w.max)}", fontSize = 12.sp, color = p.textDim)
                    }
                }
                Text("Pluie ${one(w.precip)} mm · Vent ${w.wind.roundToInt()} km/h", fontSize = 11.sp, color = p.textDim)
            }
            Spacer(Modifier.weight(1f))
            forecast.value?.hourly?.take(5)?.let { hours ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    hours.forEach { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${h.hour}h", fontSize = 10.sp, color = p.textDim)
                            Text(deg(h.temp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.text)
                            Text("${h.rainProb}%", fontSize = 9.5.sp, color = Ivry.Pluie)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Freshness(weather, now)
        }
    }
}

fun minutesLabel(d: Departure, now: Long): String {
    val m = ((d.expectedAt - now) / 60_000L).toInt()
    return when {
        d.atStop && m <= 1 -> "À quai"
        m <= 0 -> "Imminent"
        else -> "$m min"
    }
}

@Composable
private fun TransitPanel(
    code: String, round: Boolean, color: Color, title: String, station: String, direction: String,
    live: Live<Transit>, shape: Shape, modifier: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = shape, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineBadge(code, color, round)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = p.textDim,
                        fontFamily = Condensed)
                    Text(station, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1)
                }
            }
            Text(direction, fontSize = 10.sp, color = p.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val t = live.value
            val deps = t?.departures?.filter { it.expectedAt > now - 60_000 }?.take(3)
            when {
                t == null -> Text(live.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
                deps.isNullOrEmpty() -> Text("Aucun passage annoncé", fontSize = 12.sp, color = p.textDim)
                else -> deps.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.destination, fontSize = 11.sp, color = p.textDim, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(minutesLabel(d, now), fontSize = 20.sp, fontWeight = FontWeight.Black, color = p.text,
                            fontFamily = Condensed)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (t != null) {
                val (txt, warn) = when {
                    !t.messagesAvailable -> "Info trafic indisponible" to false
                    t.messages.isEmpty() -> "Aucune perturbation signalée" to false
                    else -> t.messages.first() to true
                }
                Text(txt, fontSize = 10.sp, color = if (warn) p.warn else p.nature, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontWeight = if (warn) FontWeight.Bold else FontWeight.Normal)
                Spacer(Modifier.height(2.dp))
            }
            Freshness(live, now)
        }
    }
}

@Composable
private fun VelibPanel(live: Live<Velib>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(bottomEnd = 26.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            PanelLabel("Vélib'", p.nature)
            val v = live.value
            if (v == null) {
                Spacer(Modifier.height(6.dp))
                Text(live.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
            } else {
                Text(v.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Count(v.mechanical, "mécaniques", p.nature)
                    Count(v.ebike, "électriques", Ivry.Bleu)
                }
                Spacer(Modifier.height(4.dp))
                Text("${v.docks} bornettes libres", fontSize = 11.sp, color = p.textDim)
                Spacer(Modifier.height(4.dp))
                val ok = v.installed && v.renting
                Tag(if (ok) "EN SERVICE" else "HORS SERVICE", if (ok) p.nature else p.warn)
            }
            Spacer(Modifier.weight(1f))
            Freshness(live, now)
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color) {
    val p = LocalPalette.current
    Column {
        Text("$n", fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, color = p.text, fontFamily = Condensed)
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(color))
        Text(label, fontSize = 9.5.sp, color = p.textDim)
    }
}

// ---------- Applications prioritaires ----------

@Composable
private fun BigAppCard(
    title: String, subtitle: String, app: AppEntry?, storePkg: String, fallbackBg: Color, shape: Shape,
    modifier: Modifier, focusRequester: FocusRequester?, vm: MainViewModel,
) {
    val p = LocalPalette.current
    Plate(
        modifier.fillMaxHeight(), shape = shape, focusRequester = focusRequester, background = fallbackBg,
        onClick = { if (app != null) vm.requestLaunch(app.pkg) else vm.openStore(storePkg) },
    ) { f ->
        when {
            app?.banner != null -> Image(bitmap = app.banner, contentDescription = app.label,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            app?.icon != null -> Image(bitmap = app.icon, contentDescription = app.label,
                modifier = Modifier.align(Alignment.Center).size(56.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(subtitle.uppercase(), fontSize = 10.sp, letterSpacing = 1.6.sp, color = p.accent, fontWeight = FontWeight.Bold,
                fontFamily = Condensed)
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ivry.Ivoire, fontFamily = Condensed)
            Text(
                when {
                    app == null -> "Non installée · OK pour l'installer"
                    f -> "OK pour lancer"
                    else -> "Lancement direct"
                },
                fontSize = 11.sp, color = if (f) p.focus else Ivry.Beton.copy(alpha = .8f),
            )
        }
    }
}
