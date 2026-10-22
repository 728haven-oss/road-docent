package kr.roaddocent.log

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.roaddocent.core.GeoPoint

/** One finished day, kept across sessions when "keep travel log" is on. */
@Serializable
data class DayRecord(val date: String, val distanceKm: Float, val drivingMin: Int, val visits: List<VisitRecord>, val episodesHeard: Int, val quizzesRight: Int)

@Serializable
data class VisitRecord(val name: String, val lat: Double, val lon: Double, val stayMin: Int)

/**
 * Multi-day history on device. Dwell times per place accumulate into the "how long do people stay here"
 * dataset that improves typicalStayMin suggestions; nothing leaves the car unless the user opts in.
 */
class TripHistory(context: Context) {
    private val sp = context.getSharedPreferences("roaddocent_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun days(): List<DayRecord> = runCatching { json.decodeFromString<List<DayRecord>>(sp.getString("days", "[]") ?: "[]") }.getOrDefault(emptyList())

    /** Merge today's log into the day record for [date] (same calendar day = replace). */
    fun save(date: String, log: TripLog) {
        val rec = DayRecord(date, log.distanceKm, log.drivingMin,
            log.visits.map { VisitRecord(it.name, it.point.lat, it.point.lon, it.stayMin) }, log.episodesHeard, log.quizzesRight)
        val all = days().filter { it.date != date } + rec
        sp.edit().putString("days", json.encodeToString(all.takeLast(90))).apply()
    }

    fun clear() = sp.edit().remove("days").apply()

    /** Average dwell at a place across history (minutes), or null if never visited. Feeds "where next?" notes. */
    fun typicalStayMin(name: String): Int? {
        val stays = days().flatMap { it.visits }.filter { it.name == name && it.stayMin > 0 }.map { it.stayMin }
        return if (stays.isEmpty()) null else stays.average().toInt()
    }

    fun totals(): Triple<Float, Int, Int> { val d = days(); return Triple(d.sumOf { it.distanceKm.toDouble() }.toFloat(), d.sumOf { it.drivingMin }, d.sumOf { it.visits.size }) }
}
