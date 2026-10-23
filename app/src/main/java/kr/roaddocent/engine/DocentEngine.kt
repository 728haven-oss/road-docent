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
import kr.roaddocent.voice.Command
import kr.roaddocent.voice.CommandBus
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
    /** Quiz on screen: question, and the answer once revealed. */
    data class QuizView(val question: String, val answer: String?)
    private val _quiz = MutableStateFlow<QuizView?>(null)
    val quiz: StateFlow<QuizView?> = _quiz.asStateFlow()
    private var quizJob: Job? = null
    @Volatile private var quizPending = false

    /** Speed above which speech pauses at a sentence boundary (settings). */
    var safetyPauseKmh: Int = 100
    val navState: StateFlow<NavState> get() = nav.nav
    val vehicleState: StateFlow<VehicleState> get() = veh.vehicle
    val speaking: StateFlow<Boolean> get() = speaker.speaking

    var guide: Guide? = null; private set
    /** Optional overlay (new-car onboarding): its VehicleEvent/Congestion episodes fire on top of the destination guide. */
    var overlay: Guide? = null
        set(v) { field = v; overlayPlayed.clear(); firedEvents.clear() }
    private val overlayPlayed = linkedSetOf<String>()
    private val firedEvents = linkedSetOf<String>()
    /** Theme preference used to rank "where next?" suggestions ("" = none). */
    var theme: String = ""
    /** Optional public storytelling bank for the "tell me more" fallback layer. */
    var storyBank: kr.roaddocent.content.StoryBank? = null
    private val played = linkedSetOf<String>()
    private var slowSinceMs: Long? = null
    private var congestion = false
    private var arrivedAtMs: Long? = null
    private var wasPark = true
    private var lastLoc: GeoPoint? = null
    private var lastLocMs = 0L
    private var lastSpeechEndMs = 0L
    private var chargingStory = false
    private var prevRear = false
    /** "조용히": stay silent until the next trigger instead of auto-advancing. */
    @Volatile private var quietUntilTrigger = false
    private var watcher: Job? = null
    private var currentVisit: Visit? = null

    fun setLang(l: Lang) { _lang.value = l }
    fun setAudience(a: Audience) { _audience.value = a }

    private var advancer: Job? = null
    private var commands: Job? = null
    /** Pending "go there" choices, 1-based, for voice "두 번째로 가자". */
    private var lastSuggestions: List<Place> = emptyList()

    fun start() {
        nav.start(); veh.start()
        watcher = scope.launch {
            combine(nav.nav, veh.vehicle) { n, v -> n to v }.collect { (n, v) -> onTick(n, v) }
        }
        // Auto-advance: when an episode ends and nothing else fired, the next queued story follows after a breath.
        advancer = scope.launch {
            var wasSpeaking = false
            speaker.speaking.collect { speaking ->
                if (wasSpeaking && !speaking) lastSpeechEndMs = System.currentTimeMillis()
                if (wasSpeaking && !speaking && !_paused.value && !quizPending && !quietUntilTrigger && _phase.value is DocentPhase.Guiding) {
                    delay(2_500)
                    if (!speaker.speaking.value && _queue.value.any { it.id !in played }) playNext()
                }
                wasSpeaking = speaking
            }
        }
        // Voice commands from Gleo / on-device recognizer / buttons all land here.
        commands = scope.launch {
            CommandBus.events.collect { e ->
                e ?: return@collect
                CommandBus.consume()
                when (e.command) {
                    Command.SKIP -> cmdSkip()
                    Command.TELL_MORE -> cmdMore()
                    Command.SAVE -> cmdSave()
                    Command.QUIET -> cmdQuiet()
                    Command.WHERE_NEXT -> cmdWhereNext()
                    Command.GO_THERE -> { val i = e.param?.toIntOrNull()?.minus(1) ?: 0; lastSuggestions.getOrNull(i)?.let { cmdGoThere(it) } }
                    Command.RESERVE -> cmdReserve()
                    Command.SHOW_LOG -> cmdShowLog()
                }
                _toast.value = (if (_lang.value == Lang.KO) "음성 명령: " else "Voice: ") + e.command.name.lowercase().replace('_', ' ')
            }
        }
    }

    /** "다른 데 없어?" at any time: suggest from the guide's nearby list. */
    private fun rank(places: List<Place>): List<Place> {
        if (theme.isBlank()) return places
        val pref = when (theme) { "art" -> setOf("museum", "gallery"); "nature" -> setOf("beach", "park", "mountain", "village"); "thrill" -> setOf("observatory", "activity", "park"); "food" -> setOf("restaurant", "market", "food", "cafe"); "history" -> setOf("palace", "museum", "village", "temple"); "family" -> setOf("museum", "park", "beach"); else -> emptySet() }
        return places.sortedByDescending { if (it.category in pref) 1 else 0 }
    }

    fun cmdWhereNext() {
        val g = guide ?: return
        val sugg = rank(g.nearby.filter { it.category != "cafe" }).take(3)
        lastSuggestions = sugg
        _phase.value = DocentPhase.WhereNext(0, sugg, false)
        speaker.speak(if (_lang.value == Lang.KO) "차로 10분 안에 " + sugg.joinToString(", ") { it.name["ko"] ?: "" } + "이 있어요. 거기로 가자, 라고 말씀하세요."
            else "Within ten minutes: " + sugg.joinToString(", ") { it.name["en"] ?: "" } + ". Say let's go there.", _lang.value)
    }

    fun stop() { watcher?.cancel(); advancer?.cancel(); commands?.cancel(); quizJob?.cancel(); speaker.stop(); nav.stop(); veh.stop() }

    /** Called when a quiz answer is revealed (UI plays a short chime / sticker animation). */
    var onQuizRevealed: (() -> Unit)? = null

    /**
     * "지금 창밖" (free-ride) mode: no destination set. Every NearPoint story from every guide becomes live, so a drive
     * around town triggers whatever is nearby; silence fillers keep it going. Nothing arrival-related fires.
     */
    fun startFreeRide(all: List<Guide>) {
        quizJob?.cancel(); quizPending = false; _quiz.value = null
        val eps = all.flatMap { g -> g.episodes.filter { it.trigger is Trigger.NearPoint || it.trigger is Trigger.AfterSilence }.map { it.copy(id = g.id + ":" + it.id) } }
        val intro = Episode("freeride_intro", mapOf("ko" to "지금 창밖", "en" to "Out the window"),
            mapOf("ko" to "목적지 없이 달리시네요. 지나는 곳마다 그 자리 이야기를 하나씩 들려드릴게요. 조용하면 제가 먼저 말할게요.",
                  "en" to "No destination today. As we pass places, I'll tell their stories one by one, and if it goes quiet I'll start."),
            trigger = Trigger.AtStart(0), durationSec = 15, source = "Road Docent", kind = "tip")
        val g = Guide(id = "freeride", destination = Place("freeride", mapOf("ko" to "지금 창밖", "en" to "Out the window"), GeoPoint(0.0, 0.0), category = "freeride"),
            intro = mapOf("ko" to "", "en" to ""), episodes = listOf(intro) + eps, themes = listOf("history", "art", "nature"))
        guide = g; played.clear(); congestion = false; arrivedAtMs = null; wasPark = false; slowSinceMs = null
        _queue.value = listOf(intro)
        _phase.value = DocentPhase.Guiding(intro, emptyList(), false)
        playNext()
    }

    /** Hand over to another guide without stopping: the docent keeps talking on the next leg. */
    private fun chainTo(g: Guide) {
        quizJob?.cancel(); quizPending = false; _quiz.value = null
        guide = g
        played.clear(); congestion = false; arrivedAtMs = null; wasPark = false; slowSinceMs = null
        nav.planLeg(g.route, g.driveMin)
        nav.setDestination(g.destination)
        val starts = g.episodes.filter { it.trigger is Trigger.AtStart }.sortedBy { (it.trigger as Trigger.AtStart).order }
        _queue.value = starts
        _phase.value = DocentPhase.Guiding(starts.first(), emptyList(), false)
        _toast.value = (if (_lang.value == Lang.KO) "가이드 이어서: " else "Guide continues: ") + (g.destination.name[_lang.value.tag] ?: g.id)
        playNext()
    }

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
        if (v.rearSeatOccupied && !prevRear && _audience.value == Audience.ADULT) { _audience.value = Audience.KIDS; _toast.value = if (_lang.value == Lang.KO) "뒷좌석 탑승 감지 · 키즈 모드로 전환" else "Rear passenger detected · Kids mode on" }
        prevRear = v.rearSeatOccupied

        // new-car overlay: vehicle events, each once per trip
        overlay?.let { ov ->
            fun ev(name: String, cond: Boolean) { if (cond && name !in firedEvents) { firedEvents += name; fireOverlay(ov, name) } }
            ev("first_drive", !v.gearPark && n.active)
            ev("rear_seat", v.rearSeatOccupied)
            ev("speed_60", v.speedKmh >= 60f)
            ev("speed_100", v.speedKmh >= 100f)
            ev("trip_5km", v.tripDistanceKm >= 5f)
            ev("trip_15km", v.tripDistanceKm >= 15f)
            ev("park", n.arrived && v.gearPark)
            ev("charging", v.charging)
        }
        // EV charging: the 20–30 minute wait is the best slot for the longest story left
        if (v.charging && !chargingStory) {
            chargingStory = true
            val longStory = g.episodes.filter { it.id !in played && it.trigger !is Trigger.Reboard && it.trigger !is Trigger.RemainUnderMin }.maxByOrNull { it.durationSec }
            if (longStory != null) fire(longStory, prefix = if (_lang.value == Lang.KO) "충전하는 동안 긴 이야기 하나 할게요. " else "While we charge, here's a longer story. ")
        }
        if (!v.charging) chargingStory = false

        // never quiet for long: filler after N minutes of silence while driving
        if (n.active && !v.gearPark && !speaker.speaking.value && !quizPending && lastSpeechEndMs > 0) {
            g.episodes.filter { it.trigger is Trigger.AfterSilence && it.id !in played }.firstOrNull { now - lastSpeechEndMs > (it.trigger as Trigger.AfterSilence).minutes * 60_000L }?.let { fire(it) }
        }

        // safety gate: fast or winding -> pause at sentence boundary
        val unsafe = v.speedKmh > safetyPauseKmh
        if (unsafe && !_paused.value) { _paused.value = true; speaker.pause() }
        if (!unsafe && _paused.value) { _paused.value = false; speaker.resume() }

        // congestion detection
        if (n.active && v.speedKmh < 20f && !v.gearPark) { if (slowSinceMs == null) slowSinceMs = now } else slowSinceMs = null
        val nowCongested = slowSinceMs?.let { now - it > 60_000 } == true
        if (nowCongested && !congestion) {
            congestion = true
            val main = g.episodes.firstOrNull { it.trigger is Trigger.Congestion && it.id !in played }
            if (main != null) fire(main) else overlay?.episodes?.firstOrNull { it.trigger is Trigger.Congestion && it.id !in overlayPlayed }?.let { overlayPlayed += it.id; fire(it, force = true) }
        }
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
                    val alts = g.nearby.filter { it.id != cafe.id }.take(3)
                    lastSuggestions = listOf(cafe) + alts
                    _phase.value = DocentPhase.Briefing(cafe, alts)
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
            val sugg = rank(g.nearby.filter { it.category != "cafe" }).take(3)
            lastSuggestions = sugg
            _phase.value = DocentPhase.WhereNext(stayMin, sugg, disappointed)
            arrivedAtMs = null
            fire(g.episodes.firstOrNull { it.trigger is Trigger.Reboard }, prefix = if (disappointed) (if (_lang.value == Lang.KO) "기대와 달랐나요? " else "Not what you hoped? ") else "")
        }
        wasPark = v.gearPark
    }

    /** Overlay episode for a vehicle event; waits for the current sentence queue only if nothing is mid-story. */
    private fun fireOverlay(ov: Guide, event: String) {
        val ep = ov.episodes.firstOrNull { (it.trigger as? Trigger.VehicleEvent)?.event == event && it.id !in overlayPlayed } ?: return
        overlayPlayed += ep.id
        fire(ep, prefix = if (_lang.value == Lang.KO) "잠깐, 내 차 이야기. " else "A quick word about the car. ", force = true)
    }

    private fun fire(ep: Episode?, prefix: String = "", force: Boolean = false) {
        ep ?: return
        quietUntilTrigger = false
        if (!force && ep.id in played && ep.trigger !is Trigger.Reboard) return
        if (!force) played += ep.id
        _nowPlaying.value = ep
        _log.update { it.copy(episodesHeard = it.episodesHeard + 1) }
        if (_phase.value is DocentPhase.Guiding || _phase.value is DocentPhase.Idle) _phase.value = DocentPhase.Guiding(ep, played.toList(), congestion)
        val q = ep.quiz
        quizJob?.cancel(); quizPending = false; _quiz.value = null
        val teaser = ep.teaser[_lang.value.tag]?.let { " $it" } ?: ""
        if (q == null) {
            speaker.speak(prefix + ep.textFor(_lang.value, _audience.value) + teaser, _lang.value)
        } else {
            // Story, then the question; the answer follows after a pause so the car can guess.
            quizPending = true
            _quiz.value = QuizView(q.questionFor(_lang.value), null)
            speaker.speak(prefix + ep.textFor(_lang.value, _audience.value) + " " + q.questionFor(_lang.value), _lang.value)
            quizJob = scope.launch {
                delay(1_500)
                while (speaker.speaking.value) delay(300)
                delay(q.revealAfterSec * 1000L)
                revealQuiz()
            }
        }
    }

    /** Speak the pending quiz answer now (timer, or "tell me more"). */
    fun revealQuiz() {
        val ep = _nowPlaying.value ?: return
        val q = ep.quiz ?: return
        if (!quizPending) return
        quizPending = false
        _quiz.value = QuizView(q.questionFor(_lang.value), q.answerFor(_lang.value))
        _log.update { it.copy(quizzesRight = it.quizzesRight + 1, stickers = if (_audience.value == Audience.KIDS) it.stickers + 1 else it.stickers) }
        onQuizRevealed?.invoke()
        speaker.speak((if (_lang.value == Lang.KO) "정답은, " else "The answer: ") + q.answerFor(_lang.value) + (if (_audience.value == Audience.KIDS) (if (_lang.value == Lang.KO) " 스티커 하나 획득!" else " You earned a sticker!") else ""), _lang.value)
    }

    /** Play the next queued AtStart episode (after current finishes or on "skip"). */
    fun playNext() {
        val q = _queue.value
        val next = q.firstOrNull { it.id !in played } ?: return
        _queue.value = q.filter { it.id != next.id }
        fire(next)
    }

    // ---- voice commands (Gleo Interface in production; buttons in demo) ----
    fun cmdSkip() {
        quizJob?.cancel(); quizPending = false; _quiz.value = null; speaker.stop()
        if (_queue.value.any { it.id !in played }) playNext()
        else _toast.value = if (_lang.value == Lang.KO) "지금은 다음 이야기가 없어요. 길목에서 이어갈게요" else "Nothing queued right now; the next stop will bring more"
    }
    fun cmdMore() { if (quizPending) { quizJob?.cancel(); revealQuiz(); return }; val ep = _nowPlaying.value ?: return; ep.deep[_lang.value.tag]?.let { d -> speaker.speak(d + (if (_lang.value == Lang.KO) " 출처는 ${ep.source}." else " Source: ${ep.source}."), _lang.value); return }
        if (_lang.value == Lang.KO) storyBank?.bestFor(ep.title["ko"] ?: "", guide?.destination?.name?.get("ko") ?: "")?.let { st -> speaker.speak("한국관광공사 해설로 더 자세히. ${st.title}. ${st.text}", Lang.KO); _toast.value = "출처: 관광 스토리텔링 DB · ${st.spot}"; return }
        speaker.speak((if (_lang.value == Lang.KO) "출처는 ${ep.source}. " else "Source: ${ep.source}. ") + ep.textFor(_lang.value, Audience.ADULT), _lang.value) }
    fun cmdQuiet() { quietUntilTrigger = true; quizJob?.cancel(); quizPending = false; _quiz.value = null; speaker.stop(); _toast.value = if (_lang.value == Lang.KO) "조용히 할게요. 다음 길목에서 다시 말할게요" else "Going quiet until the next stop" }
    fun cmdSave() {
        val ep = _nowPlaying.value
        _toast.value = if (_lang.value == Lang.KO) "여행 기록에 저장했어요" else "Saved to your travel log"
        _log.update { it.copy(saved = it.saved + listOfNotNull(ep?.titleFor(_lang.value)), phrasesLearned = if (ep?.kind == "phrase") it.phrasesLearned + 1 else it.phrasesLearned) }
    }
    fun cmdGoThere(p: Place) {
        val chained = p.guideId?.let { id -> runCatching { guides.load(id) }.getOrNull() }
        if (chained != null) {
            speaker.speak(if (_lang.value == Lang.KO) "${p.name["ko"]}로 갈게요. 가는 길에도 제가 계속 이야기할게요." else "Heading to ${p.name["en"]}. I'll keep the stories going on the way.", _lang.value)
            chainTo(chained); return
        }
        nav.setDestination(p)
        arrivedAtMs = null; wasPark = false
        played.removeAll { id -> guide?.episodes?.firstOrNull { it.id == id }?.trigger is Trigger.RemainUnderMin }
        _phase.value = DocentPhase.Guiding(_nowPlaying.value ?: guide!!.episodes.first(), played.toList(), false)
        speaker.speak(if (_lang.value == Lang.KO) "${p.name["ko"]}로 안내할게요. ${p.note["ko"] ?: ""}" else "Routing to ${p.name["en"]}. ${p.note["en"] ?: ""}", _lang.value)
    }
    private var beforeLog: DocentPhase? = null
    fun cmdShowLog() {
        if (_phase.value is DocentPhase.TravelLog) { _phase.value = beforeLog ?: DocentPhase.Idle; return }
        beforeLog = _phase.value; _phase.value = DocentPhase.TravelLog(_log.value); speaker.speak(_log.value.headline(_lang.value), _lang.value)
    }

    /** Back: log → previous screen; where-next → parked; any trip screen → stop the trip and return to the picker. Returns false when already at the root. */
    fun goBack(): Boolean = when (val p = _phase.value) {
        is DocentPhase.TravelLog -> { _phase.value = beforeLog ?: DocentPhase.Idle; true }
        is DocentPhase.WhereNext -> { val vis = currentVisit; _phase.value = if (vis != null && guide != null) DocentPhase.Parked(guide!!.destination, vis.arrivedMs) else DocentPhase.Idle; true }
        is DocentPhase.Idle -> false
        else -> { endTrip(); true }
    }

    /** Stop talking and drop the current guide; the picker shows again. */
    fun endTrip() {
        quizJob?.cancel(); quizPending = false; _quiz.value = null
        speaker.stop(); guide = null; played.clear(); _queue.value = emptyList(); _nowPlaying.value = null
        congestion = false; arrivedAtMs = null; wasPark = true
        _phase.value = DocentPhase.Idle
    }
    fun cmdReserve(): Place? {
        val b = (_phase.value as? DocentPhase.Briefing)?.place ?: return null
        if (b.phone.isNullOrBlank()) { speaker.speak(if (_lang.value == Lang.KO) "${b.name["ko"]}은 전화 예약이 없어요. 온라인 예약 링크를 폰으로 보냈어요." else "${b.name["en"]} has no phone booking; I've sent the online link to your phone.", _lang.value); _toast.value = if (_lang.value == Lang.KO) "전화번호 없음 · 온라인 예약 안내" else "No phone · online booking"; return null }
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
