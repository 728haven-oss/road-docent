package kr.roaddocent.log

import kr.roaddocent.core.GeoPoint

/** One stop in today's trip. */
data class Visit(val name: String, val point: GeoPoint, val arrivedMs: Long, var leftMs: Long? = null) {
    val stayMin: Int get() = (((leftMs ?: System.currentTimeMillis()) - arrivedMs) / 60_000).toInt()
}

/**
 * Today's trip as a picture: route trace, stops with dwell time, totals.
 * The dwell times feed the "how long do people stay here" dataset.
 */
data class TripLog(
    val trace: List<GeoPoint> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val distanceKm: Float = 0f,
    val drivingMin: Int = 0,
    val episodesHeard: Int = 0,
    val quizzesRight: Int = 0,
    val phrasesLearned: Int = 0,
    /** Kids mode: one sticker per quiz answered; shown on the travel log. */
    val stickers: Int = 0,
    /** Titles the rider said "save" on. */
    val saved: List<String> = emptyList(),
    /** Episode id → thumbs up/down. */
    val feedback: Map<String, Boolean> = emptyMap()
) {
    val stayMin: Int get() = visits.sumOf { it.stayMin }
    val longest: Visit? get() = visits.maxByOrNull { it.stayMin }

    fun headline(lang: kr.roaddocent.core.Lang): String {
        val l = longest ?: return if (lang == kr.roaddocent.core.Lang.KO) "오늘의 여행 일지" else "Today's travel log"
        val detour = trace.size > 6
        return if (lang == kr.roaddocent.core.Lang.KO)
            (if (detour) "오늘 좀 돌았네요. " else "오늘은 거의 근처였어요. ") + "${l.name}에 가장 오래 있었어요."
        else (if (detour) "A bit of a detour today. " else "Everything was close today. ") + "You stayed longest at ${l.name}."
    }
}
