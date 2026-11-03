package fr.ivry.os.data

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ======================= Fil d'actualité (RSS / Atom) =======================

object News {
    const val DEFAULT_URL = "https://www.francetvinfo.fr/titres.rss"

    /** Titres des derniers articles, dans l'ordre du fil. */
    fun parse(xml: String): List<String> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val out = ArrayList<String>()
        var inItem = false
        var tag: String? = null
        val sb = StringBuilder()
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    if (p.name == "item" || p.name == "entry") inItem = true
                    tag = p.name
                    if (tag == "title") sb.setLength(0)
                }
                XmlPullParser.TEXT -> if (inItem && tag == "title") sb.append(p.text)
                XmlPullParser.END_TAG -> {
                    if (inItem && p.name == "title") sb.toString().trim().takeIf { it.isNotEmpty() }?.let { out += it }
                    if (p.name == "item" || p.name == "entry") inItem = false
                    tag = null
                }
            }
            p.next()
        }
        return out.distinct().take(15)
    }

    fun toJson(list: List<String>): String = JSONArray(list).toString()
    fun fromJson(s: String): List<String> = JSONArray(s).let { a -> List(a.length()) { a.getString(it) } }

    /** Nom court affiché devant le fil. */
    fun sourceName(url: String): String = when {
        url.contains("francetvinfo") || url.contains("franceinfo") -> "franceinfo"
        url.contains("lemonde") -> "Le Monde"
        url.contains("france24") -> "France 24"
        url.contains("liberation") -> "Libération"
        else -> runCatching { java.net.URI(url).host.removePrefix("www.").substringBefore('.') }.getOrNull() ?: "Actu"
    }
}

// ======================= Agenda du jour (iCal) =======================

data class AgendaEvent(val title: String, val start: Long, val end: Long, val allDay: Boolean)

/**
 * Lit un agenda au format iCal (adresse secrète Google Agenda) et calcule les événements du jour,
 * y compris les répétitions courantes (quotidiennes, hebdomadaires, mensuelles, annuelles),
 * les exceptions (EXDATE) et les occurrences modifiées (RECURRENCE-ID).
 */
object Agenda {
    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private data class Ev(
        val uid: String, val summary: String, val start: ZonedDateTime, val end: ZonedDateTime?, val allDay: Boolean,
        val rrule: Map<String, String>?, val exdates: Set<LocalDate>, val recurrenceId: LocalDate?, val cancelled: Boolean,
    )

    fun today(ics: String, day: LocalDate = LocalDate.now(PARIS)): List<AgendaEvent> {
        val evs = ArrayList<Ev>()
        var cur: HashMap<String, MutableList<Pair<String, String>>>? = null
        for (line in unfold(ics)) {
            when {
                line == "BEGIN:VEVENT" -> cur = HashMap()
                line == "END:VEVENT" -> { cur?.let { build(it)?.let(evs::add) }; cur = null }
                cur != null -> {
                    val i = line.indexOf(':')
                    if (i > 0) {
                        val head = line.substring(0, i)
                        cur.getOrPut(head.substringBefore(';').uppercase()) { mutableListOf() }.add(head to line.substring(i + 1))
                    }
                }
            }
        }
        val overridden = evs.filter { it.recurrenceId != null }.groupBy { it.uid }
            .mapValues { (_, v) -> v.mapNotNull { it.recurrenceId }.toSet() }
        val dayStart = day.atStartOfDay(PARIS)
        val dayEnd = dayStart.plusDays(1)
        val out = ArrayList<AgendaEvent>()
        for (e in evs) {
            if (e.cancelled) continue
            val dur = e.end?.let { Duration.between(e.start, it) } ?: if (e.allDay) Duration.ofDays(1) else Duration.ofHours(1)
            if (e.rrule == null || e.recurrenceId != null) {
                val en = e.start.plus(dur)
                if (e.start < dayEnd && en > dayStart) out += AgendaEvent(e.summary, e.start.ms(), en.ms(), e.allDay)
            } else if (occurs(e, day, overridden[e.uid].orEmpty())) {
                val s = if (e.allDay) dayStart else day.atTime(e.start.toLocalTime()).atZone(PARIS)
                out += AgendaEvent(e.summary, s.ms(), s.plus(dur).ms(), e.allDay)
            }
        }
        return out.sortedWith(compareBy<AgendaEvent>({ !it.allDay }, { it.start }))
    }

    private fun ZonedDateTime.ms() = toInstant().toEpochMilli()

    private fun unfold(ics: String): List<String> {
        val out = ArrayList<String>()
        for (raw in ics.split("\n")) {
            val l = raw.trimEnd('\r')
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + l.substring(1)
            else out += l
        }
        return out
    }

    private fun build(m: Map<String, List<Pair<String, String>>>): Ev? {
        val (dsHead, dsVal) = m["DTSTART"]?.firstOrNull() ?: return null
        val start = parseDate(dsHead, dsVal) ?: return null
        val allDay = dsVal.trim().length == 8
        val end = m["DTEND"]?.firstOrNull()?.let { parseDate(it.first, it.second) }
            ?: m["DURATION"]?.firstOrNull()?.let { runCatching { start.plus(Duration.parse(it.second.trim())) }.getOrNull() }
        val rrule = m["RRULE"]?.firstOrNull()?.second?.split(';')?.mapNotNull { kv ->
            kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] }
        }?.toMap()
        val ex = m["EXDATE"].orEmpty().flatMap { (h, v) -> v.split(',').mapNotNull { parseDate(h, it)?.toLocalDate() } }.toSet()
        val rid = m["RECURRENCE-ID"]?.firstOrNull()?.let { parseDate(it.first, it.second)?.toLocalDate() }
        val summary = unescape(m["SUMMARY"]?.firstOrNull()?.second ?: "Sans titre")
        val cancelled = m["STATUS"]?.firstOrNull()?.second?.trim().equals("CANCELLED", ignoreCase = true)
        val uid = m["UID"]?.firstOrNull()?.second ?: summary
        return Ev(uid, summary, start, end, allDay, rrule, ex, rid, cancelled)
    }

    private fun parseDate(head: String, value: String): ZonedDateTime? = runCatching {
        val s = value.trim()
        when {
            s.length == 8 -> LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(PARIS)
            s.endsWith("Z") -> LocalDateTime.parse(s.dropLast(1), FMT).atZone(ZoneOffset.UTC).withZoneSameInstant(PARIS)
            else -> {
                val tz = Regex("TZID=([^;:]+)").find(head)?.groupValues?.get(1)
                    ?.let { runCatching { ZoneId.of(it.trim('"')) }.getOrNull() } ?: PARIS
                LocalDateTime.parse(s, FMT).atZone(tz).withZoneSameInstant(PARIS)
            }
        }
    }.getOrNull()

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun dow(s: String): DayOfWeek? = when (s.uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY; "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun occurs(e: Ev, day: LocalDate, overridden: Set<LocalDate>): Boolean {
        val r = e.rrule ?: return false
        val d0 = e.start.toLocalDate()
        if (day < d0 || day in e.exdates || day in overridden) return false
        r["UNTIL"]?.let { u -> parseDate("", u)?.toLocalDate()?.let { if (day > it) return false } }
        val interval = (r["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1).toLong()

        fun nth(d: LocalDate, n: Int): Boolean =
            if (n > 0) (d.dayOfMonth - 1) / 7 + 1 == n else (d.lengthOfMonth() - d.dayOfMonth) / 7 + 1 == -n

        fun matches(d: LocalDate): Boolean = when (r["FREQ"]?.uppercase()) {
            "DAILY" -> ChronoUnit.DAYS.between(d0, d) % interval == 0L
            "WEEKLY" -> {
                val days = r["BYDAY"]?.split(',')?.mapNotNull { dow(it.takeLast(2)) }?.toSet() ?: setOf(d0.dayOfWeek)
                val w = ChronoUnit.WEEKS.between(d0.with(DayOfWeek.MONDAY), d.with(DayOfWeek.MONDAY))
                d.dayOfWeek in days && w % interval == 0L
            }
            "MONTHLY" -> {
                val m = ChronoUnit.MONTHS.between(d0.withDayOfMonth(1), d.withDayOfMonth(1))
                if (m % interval != 0L) false
                else r["BYDAY"]?.let { by ->
                    by.split(',').any { tok -> dow(tok.takeLast(2)) == d.dayOfWeek && (tok.dropLast(2).toIntOrNull()?.let { nth(d, it) } ?: true) }
                } ?: r["BYMONTHDAY"]?.let { bm -> bm.split(',').any { it.toIntOrNull() == d.dayOfMonth } }
                ?: (d.dayOfMonth == d0.dayOfMonth)
            }
            "YEARLY" -> d.month == d0.month && d.dayOfMonth == d0.dayOfMonth && (d.year - d0.year) % interval == 0L
            else -> false
        }

        if (!matches(day)) return false
        r["COUNT"]?.toIntOrNull()?.let { count ->
            var n = 0
            var d = d0
            var guard = 0
            while (d <= day && guard < 5000) { if (matches(d) && d !in e.exdates) n++; d = d.plusDays(1); guard++ }
            if (n > count) return false
        }
        return true
    }

    fun toJson(list: List<AgendaEvent>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("t", it.title).put("s", it.start).put("e", it.end).put("a", it.allDay)) }
    }.toString()

    fun fromJson(s: String): List<AgendaEvent> = JSONArray(s).let { a ->
        List(a.length()) { i -> a.getJSONObject(i).let { AgendaEvent(it.getString("t"), it.getLong("s"), it.getLong("e"), it.getBoolean("a")) } }
    }
}
