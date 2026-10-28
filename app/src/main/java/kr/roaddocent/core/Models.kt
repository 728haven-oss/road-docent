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
    val poiId: String = "", val poiSubId: String = "0",
    /** If set, "go there" hands over to this guide so the docent keeps talking on the next leg (경복궁 → 국립중앙박물관). */
    val guideId: String? = null,
    /** Tap-to-read story about the place (adults / kids), what to look for, and practical visit tips. */
    val story: Map<String, String> = emptyMap(),
    val kidsStory: Map<String, String> = emptyMap(),
    val highlights: Map<String, List<String>> = emptyMap(),
    val visit: Map<String, List<String>> = emptyMap()
)

/** A question asked on the road; the answer is revealed after a pause (or on "tell me more"). */
@Serializable
data class Quiz(val question: Map<String, String>, val answer: Map<String, String>, val revealAfterSec: Int = 8) {
    fun questionFor(lang: Lang) = question[lang.tag] ?: question.values.first()
    fun answerFor(lang: Lang) = answer[lang.tag] ?: answer.values.first()
}

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
    /** A vehicle signal event (new-car onboarding): first_drive, speed_60, speed_100, rear_seat, trip_5km, trip_15km, park. */
    @Serializable data class VehicleEvent(val event: String) : Trigger()
    /** Nothing has been said for N minutes while driving: a short filler so the car never goes quiet for long. */
    @Serializable data class AfterSilence(val minutes: Int = 6) : Trigger()
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
    val priority: Int = 5,                  // higher wins when triggers coincide
    /** story (default) · fact · quiz · phrase · food · tip · dialogue — shown as a badge, drives tone. */
    val kind: String = "story",
    val quiz: Quiz? = null,
    /** Layered content: what "tell me more" reads instead of repeating the story (museum-guide practice). */
    val deep: Map<String, String> = emptyMap(),
    /** One teaser sentence spoken at the end, pointing at the next stop (cliffhanger). */
    val teaser: Map<String, String> = emptyMap(),
    /** Visitor version (explains what Koreans already know: driving rules, tipping, hanbok…); `text` is for Korean residents. */
    val foreignText: Map<String, String> = emptyMap(),
    val foreignKidsText: Map<String, String> = emptyMap(),
    /** Empty = everyone; otherwise any of "adult", "kids", "foreign". Lets visitor-only episodes (Korea 101, phrases) stay out of a Korean family's drive. */
    val forAudiences: List<String> = emptyList()
) {
    fun titleFor(lang: Lang) = title[lang.tag] ?: title.values.first()
    fun textFor(lang: Lang, audience: Audience): String = when (audience) {
        Audience.KIDS -> kidsText[lang.tag] ?: text[lang.tag] ?: text.values.first()
        Audience.FOREIGN -> foreignText[lang.tag] ?: text[lang.tag] ?: text.values.first()
        Audience.ADULT -> text[lang.tag] ?: text.values.first()
    }
    fun appliesTo(audience: Audience): Boolean = forAudiences.isEmpty() || when (audience) { Audience.KIDS -> "kids"; Audience.FOREIGN -> "foreign"; Audience.ADULT -> "adult" } in forAudiences
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
    val dataSources: List<String> = emptyList(), // where the facts came from
    /** Travel themes this guide serves: art, history, nature, thrill, food, family, car. */
    val themes: List<String> = emptyList(),
    /** "Know before you go": hours, fees, parking, best start time, what to bring — per language. Shown before departure. */
    val knowBeforeYouGo: Map<String, List<String>> = emptyMap(),
    /** Optional handoff when parked: the venue's own app or page (e.g. the museum's indoor guide). */
    val handoff: Handoff? = null,
    /** Kids-mode quizzes asked between stories (tap or say the number). */
    val kidsQuizzes: List<KidsQuiz> = emptyList()
)

/** Kids-mode window quiz with tappable options; asked between stories when a child is riding. */
@Serializable
data class KidsQuiz(val id: String, val question: Map<String, String>, val options: Map<String, List<String>>, val answer: Int, val praise: Map<String, String> = emptyMap()) {
    fun q(lang: Lang) = question[lang.tag] ?: question.values.first()
    fun opts(lang: Lang) = options[lang.tag] ?: options.values.first()
}

@Serializable
data class Handoff(val label: Map<String, String>, val url: String, val note: Map<String, String> = emptyMap())

/** Theme labels for the picker and "where next?" ranking. */
object Themes {
    val order = listOf("history", "art", "nature", "thrill", "food", "family", "car")
    fun label(t: String, lang: Lang) = when (t) {
        "history" -> if (lang == Lang.KO) "🏯 역사" else "🏯 History"
        "art" -> if (lang == Lang.KO) "🎨 예술" else "🎨 Art"
        "nature" -> if (lang == Lang.KO) "🌊 자연" else "🌊 Nature"
        "thrill" -> if (lang == Lang.KO) "🎢 스릴" else "🎢 Thrill"
        "food" -> if (lang == Lang.KO) "🍜 미식" else "🍜 Food"
        "family" -> if (lang == Lang.KO) "👨‍👩‍👧 가족" else "👨‍👩‍👧 Family"
        "car" -> if (lang == Lang.KO) "🚗 내 차" else "🚗 My car"
        else -> t
    }
}

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
    val tripTimeMin: Int = 0,
    /** EV: plugged in and charging; long stories fit the 20–30 min wait. */
    val charging: Boolean = false,
    val batteryPct: Int = -1
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
