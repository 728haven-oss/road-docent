package kr.roaddocent.core

import kotlinx.serialization.Serializable

/** Guide language. Chosen from rental booking / vehicle locale / first-start prompt. */
enum class Lang(val tag: String) { KO("ko"), EN("en") }

/** Who is riding. Drives tone and content difficulty. */
enum class Audience { ADULT, KIDS, FOREIGN }

/** A geographic point. */
@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

/** A place the guide can talk about or route to. */
@Serializable
data class Place(
    val id: String,
    val name: Map<String, String>,          // lang -> name
    val point: GeoPoint,
    val address: String = "",
    val phone: String? = null,
    val category: String = "",              // cafe, beach, museum, rest_area ...
    val typicalStayMin: Int? = null,        // learned from user dwell data; seeded here
    val note: Map<String, String> = emptyMap(), // lang -> one-line reason ("sunset at 17:40")
    /** What to do once you're there, in order: tickets, route inside, must-try, etiquette. Spoken on parking, listed on screen. */
    val onArrival: Map<String, List<String>> = emptyMap(),
    val poiId: String = "", val poiSubId: String = "0"
)

/** When an episode should play. */
@Serializable
sealed class Trigger {
    /** Route just started. */
    @Serializable data class AtStart(val order: Int = 0) : Trigger()
    /** Vehicle enters radius (m) of a point. */
    @Serializable data class NearPoint(val point: GeoPoint, val radiusM: Int = 2500) : Trigger()
    /** Remaining time to destination drops under N minutes. */
    @Serializable data class RemainUnderMin(val minutes: Int) : Trigger()
    /** Traffic congestion detected (speed low for a while). Longer content. */
    @Serializable object Congestion : Trigger()
    /** Approaching a rest area on the route (km before). */
    @Serializable data class RestAreaAhead(val placeId: String, val kmBefore: Int = 5) : Trigger()
    /** Vehicle parked at destination then shifted back to D: "where next?" */
    @Serializable object Reboard : Trigger()
}

/** One spoken episode. Text per language; kids variant optional. */
@Serializable
data class Episode(
    val id: String,
    val title: Map<String, String>,
    val text: Map<String, String>,
    val kidsText: Map<String, String> = emptyMap(),
    val trigger: Trigger,
    val durationSec: Int = 60,
    val source: String = "",                // provenance, read back on request
    val priority: Int = 5                   // higher wins when triggers coincide
) {
    fun titleFor(lang: Lang) = title[lang.tag] ?: title.values.first()
    fun textFor(lang: Lang, audience: Audience): String {
        val kids = if (audience == Audience.KIDS) kidsText[lang.tag] else null
        return kids ?: text[lang.tag] ?: text.values.first()
    }
}

/** A whole guide for one destination. */
@Serializable
data class Guide(
    val id: String,
    val destination: Place,
    val intro: Map<String, String>,         // one-line city mood per lang
    val episodes: List<Episode>,
    val restAreas: List<Place> = emptyList(),
    val nearby: List<Place> = emptyList(),  // "where next?" candidates
    val phrases: List<Phrase> = emptyList(), // travel Korean for foreigners
    val route: List<GeoPoint> = emptyList(), // demo polyline from the start city
    val driveMin: Int = 60,                  // demo drive length
    val startName: Map<String, String> = emptyMap(),
    val creatorCourses: List<CreatorCourse> = emptyList(),
    val dataSources: List<String> = emptyList() // where the facts came from
)

/** A course published by a travel creator (YouTube / Instagram / blog), followed stop by stop. */
@Serializable
data class CreatorCourse(
    val creator: String,
    val platform: String,                   // youtube, instagram, blog, film
    val title: Map<String, String>,
    val stops: List<String>,                // place ids in order
    val quote: Map<String, String> = emptyMap(), // one line the guide cites at the stop
    val url: String = ""
)

@Serializable
data class Phrase(val ko: String, val romanized: String, val meaning: Map<String, String>)

/** Live navigation snapshot from NaviHelper (or simulator). */
data class NavState(
    val active: Boolean = false,
    val destinationName: String = "",
    val destination: GeoPoint? = null,
    val location: GeoPoint? = null,
    val remainDistanceM: Int = 0,
    val remainTimeSec: Int = 0,
    val arrived: Boolean = false
)

/** Live vehicle snapshot from Vehicle SDK (or simulator). */
data class VehicleState(
    val speedKmh: Float = 0f,
    val gearPark: Boolean = true,
    val rearSeatOccupied: Boolean = false,
    val tripDistanceKm: Float = 0f,
    val tripTimeMin: Int = 0
)

/** What the engine is doing right now (drives the UI). */
sealed class DocentPhase {
    object Idle : DocentPhase()
    data class Guiding(val episode: Episode, val playedIds: List<String>, val congestion: Boolean) : DocentPhase()
    data class Briefing(val place: Place, val alternatives: List<Place>) : DocentPhase()
    data class Parked(val place: Place, val sinceEpochMs: Long) : DocentPhase()
    data class WhereNext(val leftAfterMin: Int, val suggestions: List<Place>, val disappointed: Boolean) : DocentPhase()
    data class TravelLog(val log: kr.roaddocent.log.TripLog) : DocentPhase()
}
