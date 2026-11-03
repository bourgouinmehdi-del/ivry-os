package fr.ivry.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
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

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 12.dp)) {
        // ZONE 1 — identité et heure, posées dans le ciel
        IvryHeader(weather.value, online, { onSection(Section.SETTINGS) })

        // La ville reste visible entre l'en-tête et les modules
        Spacer(Modifier.weight(.25f))

        // ZONE 2 — IVRY LIVE, plaques de verre posées sur la Seine
        Row(Modifier.fillMaxWidth().weight(.40f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherPanel(weather, forecast, Modifier.weight(1.3f)) { onDetail(Detail.WEATHER) }
            TransitPanel("7", true, M7_COLOR, "MÉTRO 7", "Mairie d'Ivry", "→ La Courneuve – 8 Mai 1945",
                metro, CutCornerShape(bottomStart = 22.dp), Modifier.weight(1f)) { onDetail(Detail.METRO) }
            TransitPanel("C", false, RERC_COLOR, "RER C", "Ivry-sur-Seine", "→ Paris et nord de la ligne",
                rer, CutCornerShape(topStart = 22.dp), Modifier.weight(1f)) { onDetail(Detail.RER) }
            VelibPanel(velib, Modifier.weight(.9f)) { onDetail(Detail.VELIB) }
        }
        Spacer(Modifier.height(10.dp))

        // ZONE 3 — applications prioritaires
        Row(Modifier.fillMaxWidth().weight(.18f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAppCard("myCANAL", "CANAL+", canal, AppsRepository.CANAL_STORE, Color(0xFF0B0B0B),
                CutCornerShape(topStart = 26.dp), Modifier.weight(1f), firstFocus, vm)
            BigAppCard("YouTube", "Vidéo", youtube, AppsRepository.YOUTUBE_TV, Color(0xFF1E1E1E),
                CutCornerShape(bottomEnd = 26.dp), Modifier.weight(1f), null, vm)
            CinemaCard(videoCount, Modifier.weight(.55f)) { onSection(Section.CINEMA) }
        }
        Spacer(Modifier.height(8.dp))

        // « Le reste »
        PanelLabel("Le reste", p.nature)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().weight(.16f)) {
            if (others.isEmpty()) {
                Text("Aucune autre application détectée.", fontSize = 12.sp, color = p.textDim)
            } else {
                LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 3.dp)) {
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

fun deg(v: Double) = "${v.roundToInt()}°"
fun one(v: Double) = String.format(Locale.FRANCE, "%.1f", v)

fun minutesLabel(d: Departure, now: Long): String {
    val m = ((d.expectedAt - now) / 60_000L).toInt()
    return when {
        d.atStop && m <= 1 -> "À quai"
        m <= 0 -> "Imminent"
        else -> "$m min"
    }
}

// ---------- Modules IVRY LIVE ----------

@Composable
private fun WeatherPanel(weather: Live<CurrentWeather>, forecast: Live<Forecast>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(topEnd = 26.dp), onClick = onClick) {
        TerraceMark()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            PanelLabel("Météo", p.nature)
            Spacer(Modifier.height(2.dp))
            val w = weather.value
            if (w == null) {
                Text(weather.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(deg(w.temp), fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(wmoLabel(w.code), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("Ressenti ${deg(w.feels)}", fontSize = 11.sp, color = p.textDim)
                        Text("Min ${deg(w.min)} · Max ${deg(w.max)}", fontSize = 11.sp, color = p.textDim)
                    }
                }
                Text("Pluie ${one(w.precip)} mm · Vent ${w.wind.roundToInt()} km/h", fontSize = 10.5.sp, color = p.textDim)
            }
            Spacer(Modifier.weight(1f))
            forecast.value?.hourly?.take(5)?.let { hours ->
                Row(Modifier.fillMaxWidth().padding(end = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    hours.forEach { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${h.hour}h", fontSize = 9.5.sp, lineHeight = 11.sp, color = p.textDim)
                            Text(deg(h.temp), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                            Text("${h.rainProb}%", fontSize = 9.sp, lineHeight = 11.sp, color = Ivry.Pluie)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Freshness(weather, now)
        }
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
        TerraceMark()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineBadge(code, color, round)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                        color = p.textDim, fontFamily = Condensed)
                    Text(station, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1)
                }
            }
            Text(direction, fontSize = 10.sp, color = p.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val t = live.value
            val deps = t?.departures?.filter { it.expectedAt > now - 60_000 }?.take(3)
            when {
                t == null -> Text(
                    when {
                        live.error?.contains("Proxy") == true -> "Ajoute le proxy PRIM dans Paramètres › Sources pour voir les passages."
                        live.error?.contains("Arrêt") == true -> "Renseigne l'arrêt dans Paramètres › Sources."
                        else -> live.error ?: "Chargement…"
                    },
                    fontSize = 11.sp, lineHeight = 14.sp, color = p.textDim,
                )
                deps.isNullOrEmpty() -> Text("Aucun passage annoncé", fontSize = 12.sp, color = p.textDim)
                else -> deps.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.destination, fontSize = 10.5.sp, color = p.textDim, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(minutesLabel(d, now), fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold,
                            color = p.text)
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
            Freshness(live, now, Modifier.padding(end = 14.dp))
        }
    }
}

@Composable
private fun VelibPanel(live: Live<Velib>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(bottomEnd = 26.dp), onClick = onClick) {
        TerraceMark(bottom = false)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            PanelLabel("Vélib'", p.nature)
            val v = live.value
            if (v == null) {
                Spacer(Modifier.height(6.dp))
                Text(live.error ?: "Chargement…", fontSize = 12.sp, color = p.textDim)
            } else {
                Text(v.name, fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Count(v.mechanical, "mécaniques", p.nature, Modifier.weight(1f))
                    Count(v.ebike, "électriques", Ivry.Bleu, Modifier.weight(1f))
                }
                Spacer(Modifier.height(3.dp))
                Text("${v.docks} bornettes libres", fontSize = 10.5.sp, color = p.textDim)
                Spacer(Modifier.height(3.dp))
                val ok = v.installed && v.renting
                Tag(if (ok) "EN SERVICE" else "HORS SERVICE", if (ok) p.nature else p.warn)
            }
            Spacer(Modifier.weight(1f))
            Freshness(live, now)
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text("$n", fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(color))
        Text(label, fontSize = 9.sp, lineHeight = 11.sp, color = p.textDim, maxLines = 1)
    }
}

// ---------- Applications ----------

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
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(subtitle.uppercase(), fontSize = 10.sp, letterSpacing = 1.6.sp, color = p.accent, fontWeight = FontWeight.Bold,
                fontFamily = Condensed)
            Text(title, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ivry.Ivoire)
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

/** Carte « salle de quartier » : marquise lumineuse, applis vidéo à l'affiche. */
@Composable
private fun CinemaCard(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Plate(modifier.fillMaxHeight(), shape = CutCornerShape(topEnd = 30.dp), background = Color(0xFF1C1411), onClick = onClick) { f ->
        val bulb = if (f) Ivry.Jaune else Ivry.Sable.copy(alpha = .45f)
        Canvas(Modifier.fillMaxSize()) {
            val step = 9.dp.toPx(); val r = 1.6.dp.toPx(); val inset = 5.dp.toPx()
            var x = step / 2
            while (x < size.width - 30.dp.toPx()) {
                drawCircle(bulb, r, Offset(x, inset)); drawCircle(bulb, r, Offset(x, size.height - inset)); x += step
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("À L'AFFICHE", fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                color = Ivry.Jaune)
            Column {
                Text("$count", fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                Text(if (count > 1) "applis vidéo" else "appli vidéo", fontSize = 11.sp, color = p.textDim)
            }
            Text("Cinéma ›", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (f) p.focus else p.textDim)
        }
    }
}
