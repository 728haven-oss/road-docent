package kr.roaddocent.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roaddocent.content.GuideRepository
import kr.roaddocent.core.Audience
import kr.roaddocent.core.DocentPhase
import kr.roaddocent.core.Episode
import kr.roaddocent.core.GeoPoint
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Lang
import kr.roaddocent.core.NavState
import kr.roaddocent.core.Place
import kr.roaddocent.core.Trigger
import kr.roaddocent.core.VehicleState
import kr.roaddocent.log.TripLog
import kr.roaddocent.log.Visit
import kr.roaddocent.speech.Speaker
import kr.roaddocent.vehicle.NavSignals
import kr.roaddocent.vehicle.VehicleSignals
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The docent. Watches navigation + vehicle signals and decides WHEN to speak and WHAT.
 * The driver never asks; the car speaks first.
 *
 * Rules (from the plan):
 *  - route set            -> intro + city mood, then queued AtStart episodes
 *  - near a point         -> that place's episode
 *  - speed < 20 for 60s   -> congestion: long-form episode; resumes short form when traffic clears
 *  - rest area 5 km ahead -> local specialty + layout, "shall we stop?"
 *  - remain < 20 min      -> arrival briefing (+ reservation offer)
 *  - arrived + gear P     -> parked; dwell timer starts
 *  - gear P -> D again    -> "where next?" (disappointed variant if dwell < 30 min)
 *  - speed > 100 / nav voice -> pause at sentence boundary, resume after
 */
class DocentEngine(
    private val nav: NavSignals,
    private val veh: VehicleSignals,
    private val speaker: Speaker,
    private val guides: GuideRepository,
    private val scope: CoroutineScope
) {
    private val _phase = MutableStateFlow<DocentPhase>(DocentPhase.Idle)
    val phase: StateFlow<DocentPhase> = _phase.asStateFlow()

    private val _lang = MutableStateFlow(Lang.KO)
    val lang: StateFlow<Lang> = _lang.asStateFlow()
    private val _audience = MutableStateFlow(Audience.ADULT)
    val audience: StateFlow<Audience> = _audience.asStateFlow()
    private val _log = MutableStateFlow(TripLog())
    val log: StateFlow<TripLog> = _log.asStateFlow()
    private val _nowPlaying = MutableStateFlow<Episode?>(null)
    val nowPlaying: StateFlow<Episode?> = _nowPlaying.asStateFlow()
    private val _queue = MutableStateFlow<List<Episode>>(emptyList())
    val queue: StateFlow<List<Episode>> = _queue.asStateFlow()
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val navState: StateFlow<NavState> get() = nav.nav
    val speaking: StateFlow<Boolean> get() = speaker.speaking

    var guide: Guide? = null; private set
    private val played = linkedSetOf<String>()
    private var slowSinceMs: Long? = null
    private var congestion = false
    private var arrivedAtMs: Long? = null
    private var wasPark = true
    private var lastLoc: GeoPoint? = null
    private var lastLocMs = 0L
    private var watcher: Job? = null
    private var currentVisit: Visit? = null

    fun setLang(l: Lang) { _lang.value = l }
    fun setAudience(a: Audience) { _audience.value = a }

    fun start() {
        nav.start(); veh.start()
        watcher = scope.launch {
            combine(nav.nav, veh.vehicle) { n, v -> n to v }.collect { (n, v) -> onTick(n, v) }
        }
    }

    fun stop() { watcher?.cancel(); speaker.stop(); nav.stop(); veh.stop() }

    /** Driver chose a destination in the car's navigation (or the demo picked one). */
    fun onRouteSet(destinationName: String) {
        val g = guides.forDestination(destinationName)
        guide = g
        played.clear(); congestion = false; arrivedAtMs = null
        val starts = g.episodes.filter { it.trigger is Trigger.AtStart }.sortedBy { (it.trigger as Trigger.AtStart).order }
        _queue.value = starts
        _phase.value = DocentPhase.Guiding(starts.first(), emptyList(), false)
        playNext()
    }

    private suspend fun onTick(n: NavState, v: VehicleState) {
        val g = guide ?: return
        val now = System.currentTimeMillis()

        // speed from location delta (for SDK adapter that has no speed signal)
        n.location?.let { loc ->
            lastLoc?.let { prev -> if (now - lastLocMs > 1000) { val kmh = haversineM(prev, loc) / ((now - lastLocMs) / 1000f) * 3.6f; (veh as? kr.roaddocent.vehicle.PleosVehicleSignals)?.updateSpeed(kmh) } }
            lastLoc = loc; lastLocMs = now
            _log.update { it.copy(trace = (it.trace + loc).takeLast(600)) }
        }
        _log.update { it.copy(distanceKm = v.tripDistanceKm, drivingMin = v.tripTimeMin) }

        // kids mode offer when rear seat occupied
        if (v.rearSeatOccupied && _audience.value == Audience.ADULT) { _audience.value = Audience.KIDS; _toast.value = if (_lang.value == Lang.KO) "뒷좌석 탑승 감지 · 키즈 모드로 전환" else "Rear passenger detected · Kids mode on" }

        // safety gate: fast or winding -> pause at sentence boundary
        val unsafe = v.speedKmh > 100f
        if (unsafe && !_paused.value) { _paused.value = true; speaker.pause() }
        if (!unsafe && _paused.value) { _paused.value = false; speaker.resume() }

        // congestion detection
        if (n.active && v.speedKmh < 20f && !v.gearPark) { if (slowSinceMs == null) slowSinceMs = now } else slowSinceMs = null
        val nowCongested = slowSinceMs?.let { now - it > 60_000 } == true
        if (nowCongested && !congestion) { congestion = true; fire(g.episodes.firstOrNull { it.trigger is Trigger.Congestion }) }
        if (!nowCongested && congestion) { congestion = false }

        // geo triggers
        n.location?.let { loc ->
            g.episodes.filter { it.trigger is Trigger.NearPoint && it.id !in played }.forEach { ep ->
                val t = ep.trigger as Trigger.NearPoint
                if (haversineM(loc, t.point) < t.radiusM) fire(ep)
            }
            g.episodes.filter { it.trigger is Trigger.RestAreaAhead && it.id !in played }.forEach { ep ->
                val t = ep.trigger as Trigger.RestAreaAhead
                val ra = g.restAreas.firstOrNull { it.id == t.placeId } ?: return@forEach
                val d = haversineM(loc, ra.point)
                if (d < t.kmBefore * 1000 + 1500 && d > 500) fire(ep)
            }
        }

        // arrival briefing
        if (n.active && n.remainTimeSec in 1..(20 * 60)) {
            g.episodes.filter { it.trigger is Trigger.RemainUnderMin && it.id !in played }.forEach { ep ->
                if (n.remainTimeSec <= (ep.trigger as Trigger.RemainUnderMin).minutes * 60) {
                    fire(ep)
                    val cafe = g.nearby.firstOrNull { it.category == "cafe" } ?: g.destination
                    _phase.value = DocentPhase.Briefing(cafe, g.nearby.filter { it.id != cafe.id }.take(3))
                }
            }
        }

        // arrived + parked
        if (n.arrived && v.gearPark && arrivedAtMs == null) {
            arrivedAtMs = now
            val place = if (n.destination == g.destination.point) g.destination else g.nearby.firstOrNull { it.point == n.destination } ?: Place("dest", mapOf("ko" to n.destinationName, "en" to n.destinationName), n.destination ?: GeoPoint(0.0, 0.0))
            currentVisit = Visit(place.name[_lang.value.tag] ?: place.name.values.first(), place.point, now).also { vis -> _log.update { it.copy(visits = it.visits + vis) } }
            _phase.value = DocentPhase.Parked(place, now)
            val steps = g.destination.onArrival[_lang.value.tag].orEmpty()
            if (steps.isNotEmpty()) speaker.speak((if (_lang.value == Lang.KO) "도착했어요. 여기서 할 일을 순서대로 알려드릴게요. " else "We've arrived. Here's what to do, in order. ") + steps.joinToString(" "), _lang.value) else speaker.stop()
        }

        // re-board: P -> D after being parked
        if (arrivedAtMs != null && wasPark && !v.gearPark) {
            val stayMin = ((now - arrivedAtMs!!) / 60_000).toInt()
            currentVisit?.leftMs = now
            val disappointed = stayMin < 30
            val sugg = g.nearby.filter { it.category != "cafe" }.take(3)
            _phase.value = DocentPhase.WhereNext(stayMin, sugg, disappointed)
            arrivedAtMs = null
            fire(g.episodes.firstOrNull { it.trigger is Trigger.Reboard }, prefix = if (disappointed) (if (_lang.value == Lang.KO) "기대와 달랐나요? " else "Not what you hoped? ") else "")
        }
        wasPark = v.gearPark
    }

    private fun fire(ep: Episode?, prefix: String = "") {
        ep ?: return
        if (ep.id in played && ep.trigger !is Trigger.Reboard) return
        played += ep.id
        _nowPlaying.value = ep
        _log.update { it.copy(episodesHeard = it.episodesHeard + 1) }
        if (_phase.value is DocentPhase.Guiding || _phase.value is DocentPhase.Idle) _phase.value = DocentPhase.Guiding(ep, played.toList(), congestion)
        speaker.speak(prefix + ep.textFor(_lang.value, _audience.value), _lang.value)
        if (ep.trigger is Trigger.Reboard && _audience.value == Audience.KIDS) _log.update { it.copy(quizzesRight = it.quizzesRight + 1) }
    }

    /** Play the next queued AtStart episode (after current finishes or on "skip"). */
    fun playNext() {
        val q = _queue.value
        val next = q.firstOrNull { it.id !in played } ?: return
        _queue.value = q.filter { it.id != next.id }
        fire(next)
    }

    // ---- voice commands (Gleo Interface in production; buttons in demo) ----
    fun cmdSkip() { speaker.stop(); playNext() }
    fun cmdMore() { val ep = _nowPlaying.value ?: return; speaker.speak((if (_lang.value == Lang.KO) "출처는 ${ep.source}. " else "Source: ${ep.source}. ") + ep.textFor(_lang.value, Audience.ADULT), _lang.value) }
    fun cmdQuiet() { speaker.stop() }
    fun cmdSave() { _toast.value = if (_lang.value == Lang.KO) "여행 기록에 저장했어요" else "Saved to your travel log"; _log.update { it.copy(phrasesLearned = it.phrasesLearned + 1) } }
    fun cmdGoThere(p: Place) {
        nav.setDestination(p)
        arrivedAtMs = null; wasPark = false
        played.removeAll { id -> guide?.episodes?.firstOrNull { it.id == id }?.trigger is Trigger.RemainUnderMin }
        _phase.value = DocentPhase.Guiding(_nowPlaying.value ?: guide!!.episodes.first(), played.toList(), false)
        speaker.speak(if (_lang.value == Lang.KO) "${p.name["ko"]}로 안내할게요. ${p.note["ko"] ?: ""}" else "Routing to ${p.name["en"]}. ${p.note["en"] ?: ""}", _lang.value)
    }
    fun cmdShowLog() { _phase.value = DocentPhase.TravelLog(_log.value); speaker.speak(_log.value.headline(_lang.value), _lang.value) }
    fun cmdReserve(): Place? {
        val b = (_phase.value as? DocentPhase.Briefing)?.place ?: return null
        speaker.speak(if (_lang.value == Lang.KO) "${b.name["ko"]}에 전화를 걸게요. 4명, 도착 시간으로 말씀하시면 돼요." else "Calling ${b.name["en"]}. Say: four people, at our arrival time.", _lang.value)
        return b
    }
    fun clearToast() { _toast.value = null }

    private fun haversineM(a: GeoPoint, b: GeoPoint): Float {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return (2 * r * atan2(sqrt(h), sqrt(1 - h))).toFloat()
    }
}
