package fr.ivry.os.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

val PARIS: ZoneId = ZoneId.of("Europe/Paris")

object Sources {
    // Centre d'Ivry-sur-Seine (mairie)
    private const val LAT = "48.8139"
    private const val LON = "2.3848"
    private const val OM =
        "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&timezone=Europe%2FParis&wind_speed_unit=kmh"

    const val CURRENT_URL =
        "$OM&current=temperature_2m,apparent_temperature,weather_code,precipitation,wind_speed_10m,wind_gusts_10m,cloud_cover" +
            "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=1"

    const val FORECAST_URL =
        "$OM&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max&forecast_days=6"

    const val VELIB_INFO =
        "https://velib-metropole-opendata.smovengo.cloud/opendata/Velib_Metropole/station_information.json"
    const val VELIB_STATUS =
        "https://velib-metropole-opendata.smovengo.cloud/opendata/Velib_Metropole/station_status.json"

    // ---------- Météo (Open-Meteo) ----------

    fun parseCurrent(txt: String): CurrentWeather {
        val o = JSONObject(txt)
        val c = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        return CurrentWeather(
            temp = c.getDouble("temperature_2m"),
            feels = c.getDouble("apparent_temperature"),
            code = c.getInt("weather_code"),
            precip = c.optDouble("precipitation", 0.0),
            wind = c.optDouble("wind_speed_10m", 0.0),
            gusts = c.optDouble("wind_gusts_10m", 0.0),
            min = d.getJSONArray("temperature_2m_min").getDouble(0),
            max = d.getJSONArray("temperature_2m_max").getDouble(0),
            cloudCover = if (c.has("cloud_cover")) c.optInt("cloud_cover") else null,
            sunrise = d.optJSONArray("sunrise")?.optString(0)?.let(::localToEpoch),
            sunset = d.optJSONArray("sunset")?.optString(0)?.let(::localToEpoch),
        )
    }

    private fun localToEpoch(s: String): Long? =
        runCatching { LocalDateTime.parse(s).atZone(PARIS).toInstant().toEpochMilli() }.getOrNull()

    fun parseForecast(txt: String): Forecast {
        val o = JSONObject(txt)
        val h = o.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val probs = h.optJSONArray("precipitation_probability")
        val codes = h.getJSONArray("weather_code")
        val nowH = LocalDateTime.now(PARIS).withMinute(0).withSecond(0).withNano(0)
        val hours = ArrayList<HourPoint>()
        for (i in 0 until times.length()) {
            val t = LocalDateTime.parse(times.getString(i))
            if (t.isBefore(nowH)) continue
            hours += HourPoint(t.hour, temps.optDouble(i), probs?.optInt(i, 0) ?: 0, codes.optInt(i))
            if (hours.size >= 12) break
        }
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = ArrayList<DayPoint>()
        // Index 0 = aujourd'hui : on affiche les cinq jours suivants.
        for (i in 1 until minOf(6, dt.length())) {
            days += DayPoint(
                date = LocalDate.parse(dt.getString(i)),
                min = d.getJSONArray("temperature_2m_min").optDouble(i),
                max = d.getJSONArray("temperature_2m_max").optDouble(i),
                code = d.getJSONArray("weather_code").optInt(i),
                rain = d.optJSONArray("precipitation_sum")?.optDouble(i, 0.0) ?: 0.0,
                windMax = d.optJSONArray("wind_speed_10m_max")?.optDouble(i, 0.0) ?: 0.0,
            )
        }
        return Forecast(hours, days)
    }

    // ---------- Transports (PRIM via proxy) ----------

    fun stopMonitoringUrl(proxy: String, ref: String) =
        proxy.trimEnd('/') + "/stop-monitoring?MonitoringRef=" + enc(normalizeStop(ref))

    fun generalMessageUrl(proxy: String, line: String) =
        proxy.trimEnd('/') + "/general-message?LineRef=" + enc(normalizeLine(line))

    /**
     * Accepte l'identifiant tel qu'il est copié depuis les données IDFM et le convertit au format SIRI :
     * « IDFM:monomodalStopPlace:43135 », « IDFM:22396 » ou « 22396 » → STIF:StopArea:SP:<id>:
     * « IDFM:463158 » (quai, 6 chiffres)                         → STIF:StopPoint:Q:463158:
     * Un identifiant déjà au format STIF est gardé tel quel.
     */
    fun normalizeStop(raw: String): String {
        val r = raw.trim()
        if (r.startsWith("STIF:")) return if (r.endsWith(":")) r else "$r:"
        val digits = r.substringAfterLast(':').filter { it.isDigit() }
        if (digits.isEmpty()) return r
        // IDFM : zones d'arrêt (gares, stations) sur 5 chiffres au plus, quais sur 6 chiffres (4xxxxx)
        return if (r.contains("monomodalStopPlace", true) || r.contains("StopArea", true) || digits.length <= 5)
            "STIF:StopArea:SP:$digits:" else "STIF:StopPoint:Q:$digits:"
    }

    /** « C01377 », « IDFM:C01377 » ou « line:IDFM:C01377 » → STIF:Line::C01377: */
    fun normalizeLine(raw: String): String {
        val r = raw.trim()
        if (r.startsWith("STIF:")) return r
        val code = Regex("C\\d{5}").find(r)?.value ?: return r
        return "STIF:Line::$code:"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** txt = {"sm": <réponse stop-monitoring>, "gm": <réponse general-message ou null>} */
    fun parseTransit(txt: String, filter: String, max: Int = 8): Transit {
        val root = JSONObject(txt)
        // Filtre : mots séparés par des virgules ; « !mot » exclut, les autres mots doivent correspondre
        val words = filter.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val excludes = words.filter { it.startsWith("!") }.map { it.drop(1).trim() }.filter { it.isNotEmpty() }
        val keywords = words.filterNot { it.startsWith("!") }
        val visits = root.getJSONObject("sm").getJSONObject("Siri").getJSONObject("ServiceDelivery")
            .getJSONArray("StopMonitoringDelivery").getJSONObject(0)
            .optJSONArray("MonitoredStopVisit") ?: JSONArray()

        val now = System.currentTimeMillis()
        val deps = ArrayList<Departure>()
        for (i in 0 until visits.length()) {
            val mvj = visits.optJSONObject(i)?.optJSONObject("MonitoredVehicleJourney") ?: continue
            val call = mvj.optJSONObject("MonitoredCall") ?: continue
            val dest = mvj.valueOf("DestinationName") ?: call.valueOf("DestinationDisplay") ?: "?"
            val dir = mvj.valueOf("DirectionName") ?: ""
            if (excludes.any { k -> dest.contains(k, ignoreCase = true) || dir.contains(k, ignoreCase = true) }) continue
            if (keywords.isNotEmpty() && keywords.none { k ->
                    dest.contains(k, ignoreCase = true) || dir.contains(k, ignoreCase = true)
                }) continue
            val timeStr = listOf("ExpectedDepartureTime", "ExpectedArrivalTime", "AimedDepartureTime", "AimedArrivalTime")
                .map { call.optString(it) }.firstOrNull { it.isNotEmpty() } ?: continue
            val t = runCatching { OffsetDateTime.parse(timeStr).toInstant().toEpochMilli() }.getOrNull() ?: continue
            if (t < now - 60_000) continue
            val atStop = call.optBoolean("VehicleAtStop", false) || call.optString("VehicleAtStop") == "true"
            deps += Departure(dest, t, atStop)
        }
        deps.sortBy { it.expectedAt }

        val gm = root.optJSONObject("gm")
        val messages = if (gm != null) parseMessages(gm) else emptyList()
        return Transit(deps.take(max), messages, gm != null)
    }

    private fun parseMessages(gm: JSONObject): List<String> {
        val out = LinkedHashSet<String>()
        val deliveries = gm.optJSONObject("Siri")?.optJSONObject("ServiceDelivery")
            ?.optJSONArray("GeneralMessageDelivery") ?: return emptyList()
        for (d in 0 until deliveries.length()) {
            val infos = deliveries.optJSONObject(d)?.optJSONArray("InfoMessage") ?: continue
            for (i in 0 until infos.length()) {
                val msgs = infos.optJSONObject(i)?.optJSONObject("Content")?.optJSONArray("Message") ?: continue
                var short: String? = null
                var any: String? = null
                for (m in 0 until msgs.length()) {
                    val mo = msgs.optJSONObject(m) ?: continue
                    val text = mo.valueOf("MessageText") ?: continue
                    if (any == null) any = text
                    if (mo.optString("MessageType").equals("SHORT_MESSAGE", true)) short = text
                }
                (short ?: any)?.let { out += it.trim() }
            }
        }
        return out.toList()
    }

    // ---------- Vélib' (GBFS) ----------

    /** Retourne (station_id, nom) pour un code station, ou null si le code n'existe pas. */
    fun findVelibStation(infoTxt: String, code: String): Pair<String, String>? {
        val stations = JSONObject(infoTxt).getJSONObject("data").getJSONArray("stations")
        for (i in 0 until stations.length()) {
            val s = stations.getJSONObject(i)
            if (s.optString("stationCode") == code) return s.get("station_id").toString() to s.optString("name")
        }
        return null
    }

    fun findVelibStatus(statusTxt: String, stationId: String): JSONObject? {
        val stations = JSONObject(statusTxt).getJSONObject("data").getJSONArray("stations")
        for (i in 0 until stations.length()) {
            val s = stations.getJSONObject(i)
            if (s.get("station_id").toString() == stationId) return s
        }
        return null
    }

    fun parseVelib(txt: String): Velib {
        val o = JSONObject(txt)
        val st = o.getJSONObject("status")
        var mech = 0
        var ebike = 0
        val types = st.optJSONArray("num_bikes_available_types")
        if (types != null) for (i in 0 until types.length()) {
            val t = types.optJSONObject(i) ?: continue
            mech += t.optInt("mechanical", 0)
            ebike += t.optInt("ebike", 0)
        }
        return Velib(
            code = o.optString("code"),
            name = o.optString("name"),
            mechanical = mech,
            ebike = ebike,
            docks = st.optInt("num_docks_available", st.optInt("numDocksAvailable", 0)),
            installed = st.optInt("is_installed", 1) == 1,
            renting = st.optInt("is_renting", 1) == 1,
            returning = st.optInt("is_returning", 1) == 1,
        )
    }

    /** Les champs SIRI Lite peuvent être une chaîne, un objet {value} ou un tableau d'objets {value}. */
    private fun JSONObject.valueOf(key: String): String? {
        val a = optJSONArray(key)
        if (a != null) {
            if (a.length() == 0) return null
            return when (val x = a.opt(0)) {
                is JSONObject -> x.optString("value").ifEmpty { null }
                is String -> x.ifEmpty { null }
                else -> null
            }
        }
        val o = optJSONObject(key)
        if (o != null) return o.optString("value").ifEmpty { null }
        return optString(key).ifEmpty { null }
    }
}
