package fr.ivry.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ivry.os.MainViewModel
import fr.ivry.os.data.*
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DetailScreen(vm: MainViewModel, detail: Detail, onClose: () -> Unit) {
    val e = vm.engine
    when (detail) {
        Detail.WEATHER -> {
            val w by e.weatherNow.collectAsStateWithLifecycle()
            val f by e.forecast.collectAsStateWithLifecycle()
            DetailFrame("Météo", "Ivry-sur-Seine · Open-Meteo", e::refreshNow, onClose) { WeatherDetail(w, f) }
        }
        Detail.METRO -> {
            val t by e.metro.collectAsStateWithLifecycle()
            DetailFrame("Métro 7 · Mairie d'Ivry", "Direction La Courneuve – 8 Mai 1945 · IDFM", e::refreshNow, onClose) {
                TransitDetail(t)
            }
        }
        Detail.RER -> {
            val t by e.rer.collectAsStateWithLifecycle()
            DetailFrame("RER C · Ivry-sur-Seine", "Prochains trains · IDFM", e::refreshNow, onClose) { TransitDetail(t) }
        }
        Detail.VELIB -> {
            val v by e.velib.collectAsStateWithLifecycle()
            DetailFrame("Vélib'", "Données officielles GBFS Vélib' Métropole", e::refreshNow, onClose) { VelibDetail(v) }
        }
    }
}

@Composable
private fun DetailFrame(
    title: String, subtitle: String, onRefresh: () -> Unit, onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 26.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(subtitle.uppercase(), fontSize = 10.sp, letterSpacing = 1.6.sp, color = p.textDim, fontFamily = Condensed)
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = p.text, fontFamily = Condensed)
            }
            ActionButton("Actualiser", Icons.Default.Refresh, fr, onClick = onRefresh)
            Spacer(Modifier.width(8.dp))
            ActionButton("Retour", Icons.Default.KeyboardArrowLeft, onClick = onClose)
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)

@Composable
private fun WeatherDetail(w: Live<CurrentWeather>, f: Live<Forecast>) {
    val p = LocalPalette.current
    val now = rememberTick(15_000)
    val c = w.value
    if (c != null) Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(c.code, isNightHour(java.time.LocalTime.now(fr.ivry.os.data.PARIS).hour, c.sunrise, c.sunset), Modifier.size(72.dp))
        Spacer(Modifier.width(12.dp))
        Text(deg(c.temp), fontSize = 72.sp, fontWeight = FontWeight.Black, color = p.text, fontFamily = Condensed)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(wmoLabel(c.code), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.text)
            Text("Ressenti ${deg(c.feels)} · Min ${deg(c.min)} · Max ${deg(c.max)}", fontSize = 14.sp, color = p.textDim)
            Text("Pluie ${one(c.precip)} mm · Vent ${c.wind.roundToInt()} km/h · Rafales ${c.gusts.roundToInt()} km/h",
                fontSize = 14.sp, color = p.textDim)
        }
    }
    Freshness(w, now)
    Spacer(Modifier.height(16.dp))
    PanelLabel("Prochaines heures", p.nature)
    Spacer(Modifier.height(6.dp))
    val fc = f.value
    if (fc == null) Text(f.error ?: "Chargement…", color = p.textDim, fontSize = 12.sp)
    else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            fc.hourly.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${h.hour}h", fontSize = 11.sp, color = p.textDim)
                    WeatherIcon(h.code, isNightHour(h.hour, c?.sunrise, c?.sunset), Modifier.padding(vertical = 2.dp).size(26.dp))
                    Text(deg(h.temp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text)
                    Text("${h.rainProb}%", fontSize = 10.sp, color = Ivry.Pluie)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PanelLabel("Cinq jours", p.accent)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fc.daily.forEach { d ->
                Plate(Modifier.weight(1f), shape = CutCornerShape(topEnd = 14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(DAY.format(d.date).replaceFirstChar { it.uppercase() }, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = p.text)
                        WeatherIcon(d.code, false, Modifier.padding(vertical = 4.dp).size(30.dp))
                        Text("${deg(d.max)} / ${deg(d.min)}", fontSize = 20.sp, fontWeight = FontWeight.Black,
                            color = p.text, fontFamily = Condensed)
                        Text(wmoLabel(d.code), fontSize = 11.sp, color = p.textDim, maxLines = 1)
                        Text("${one(d.rain)} mm · ${d.windMax.roundToInt()} km/h", fontSize = 10.sp, color = Ivry.Pluie)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Freshness(f, now)
}

@Composable
private fun TransitDetail(live: Live<Transit>) {
    val p = LocalPalette.current
    val now = rememberTick(10_000)
    val t = live.value
    Freshness(live, now)
    Spacer(Modifier.height(10.dp))
    if (t == null) {
        Text(live.error ?: "Chargement…", fontSize = 14.sp, color = p.textDim)
        return
    }
    val deps = t.departures.filter { it.expectedAt > now - 60_000 }
    if (deps.isEmpty()) Text("Aucun passage annoncé", fontSize = 14.sp, color = p.textDim)
    deps.forEach { d ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.destination, fontSize = 16.sp, color = p.text, modifier = Modifier.weight(1f))
            Text(hhmm(d.expectedAt), fontSize = 13.sp, color = p.textDim)
            Spacer(Modifier.width(18.dp))
            Text(minutesLabel(d, now), fontSize = 26.sp, fontWeight = FontWeight.Black, color = p.text,
                fontFamily = Condensed, modifier = Modifier.widthIn(min = 100.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    PanelLabel("Information trafic", p.warn)
    Spacer(Modifier.height(6.dp))
    when {
        !t.messagesAvailable -> Text("Informations trafic indisponibles pour le moment.", fontSize = 13.sp, color = p.textDim)
        t.messages.isEmpty() -> Text("Aucune perturbation signalée par Île-de-France Mobilités.", fontSize = 13.sp, color = p.nature)
        else -> t.messages.forEach { m ->
            Plate(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(m, fontSize = 13.sp, color = p.text, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun VelibDetail(live: Live<Velib>) {
    val p = LocalPalette.current
    val now = rememberTick(10_000)
    val v = live.value
    Freshness(live, now)
    Spacer(Modifier.height(10.dp))
    if (v == null) {
        Text(live.error ?: "Chargement…", fontSize = 14.sp, color = p.textDim)
        return
    }
    Text(v.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.text)
    Text("Station n° ${v.code} · identifiée dans les données officielles", fontSize = 12.sp, color = p.textDim)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        BigNumber(v.mechanical, "vélos mécaniques")
        BigNumber(v.ebike, "vélos électriques")
        BigNumber(v.docks, "bornettes libres")
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(if (v.installed) "INSTALLÉE" else "NON INSTALLÉE", if (v.installed) p.nature else p.warn)
        Tag(if (v.renting) "LOCATION OUVERTE" else "LOCATION FERMÉE", if (v.renting) p.nature else p.warn)
        Tag(if (v.returning) "RETOUR OUVERT" else "RETOUR FERMÉ", if (v.returning) p.nature else p.warn)
    }
}

@Composable
private fun BigNumber(n: Int, label: String) {
    val p = LocalPalette.current
    Column {
        Text("$n", fontSize = 64.sp, fontWeight = FontWeight.Black, color = p.text, fontFamily = Condensed)
        Text(label, fontSize = 13.sp, color = p.textDim)
    }
}
