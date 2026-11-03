package fr.ivry.os.data

import java.time.LocalDate

/** Valeur d'une source avec sa date de récupération. On garde toujours la dernière valeur connue. */
data class Live<T>(
    val value: T? = null,
    val fetchedAt: Long = 0L,
    val error: String? = null,
    val staleAfterMs: Long = 0L,
) {
    fun isStale(now: Long): Boolean = value != null && now - fetchedAt > staleAfterMs
}

data class CurrentWeather(
    val temp: Double,
    val feels: Double,
    val code: Int,
    val precip: Double,
    val wind: Double,
    val gusts: Double,
    val min: Double,
    val max: Double,
    val cloudCover: Int? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null,
)

data class HourPoint(val hour: Int, val temp: Double, val rainProb: Int, val code: Int)

data class DayPoint(
    val date: LocalDate,
    val min: Double,
    val max: Double,
    val code: Int,
    val rain: Double,
    val windMax: Double,
)

data class Forecast(val hourly: List<HourPoint>, val daily: List<DayPoint>)

data class Departure(val destination: String, val expectedAt: Long, val atStop: Boolean)

/** messagesAvailable = false quand l'appel trafic a échoué : on n'affiche alors pas « aucune perturbation ». */
data class Transit(
    val departures: List<Departure>,
    val messages: List<String>,
    val messagesAvailable: Boolean,
)

data class Velib(
    val code: String,
    val name: String,
    val mechanical: Int,
    val ebike: Int,
    val docks: Int,
    val installed: Boolean,
    val renting: Boolean,
    val returning: Boolean,
)

fun wmoLabel(code: Int): String = when (code) {
    0 -> "Ciel clair"
    1 -> "Peu nuageux"
    2 -> "Partiellement nuageux"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    51, 53, 55 -> "Bruine"
    56, 57 -> "Bruine verglaçante"
    61 -> "Pluie faible"
    63 -> "Pluie"
    65 -> "Forte pluie"
    66, 67 -> "Pluie verglaçante"
    71 -> "Neige faible"
    73 -> "Neige"
    75 -> "Forte neige"
    77 -> "Grains de neige"
    80 -> "Averses faibles"
    81 -> "Averses"
    82 -> "Fortes averses"
    85, 86 -> "Averses de neige"
    95 -> "Orage"
    96, 99 -> "Orage et grêle"
    else -> "—"
}
