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
import kr.roaddocent.content.GuideSource
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
    private val guides: GuideSource,
    private val scope: CoroutineScope
) {
    /** Wall clock; tests inject virtual time. */
    var clock: () -> Long = { System.currentTimeMillis() }
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
    /** Kids window quiz with options; null when none is open. */
    data class KidsQuizView(val quiz: kr.roaddocent.core.KidsQuiz, val chosen: Int?, val correct: Boolean?)
    private val _kidsQuiz = MutableStateFlow<KidsQuizView?>(null)
    val kidsQuiz: StateFlow<KidsQuizView?> = _kidsQuiz.asStateFlow()
    private val kidsAsked = linkedSetOf<String>()
    private var storiesSinceKidsQuiz = 0

    /** Speed above which speech pauses at a sentence boundary (settings). */
    var safetyPauseKmh: Int = 100
    val navState: StateFlow<NavState> get() = nav.nav
    val vehicleState: StateFlow<VehicleState> get() = veh.vehicle
    val speaking: StateFlow<Boolean> get() = speaker.speaking
    /** True while a story is being read or one is waiting right behind it — the demo clock holds on this. */
    fun busy(): Boolean = speaker.speaking.value || pending.isNotEmpty() || quizPending

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
    /** Utterance counter so a story that starts and ends between two polls is still seen as "ended". */
    @Volatile private var utter = 0
    private fun say(text: String, lang: Lang) { utter++; speaker.speak(text, lang) }
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
        // Polling (not collect+delay): a story that starts and ends during a wait must not be missed.
        advancer = scope.launch {
            var handled = 0
            var endedAt: Long? = null
            var kidsCounted = false
            while (true) {
                delay(250)
                val speaking = speaker.speaking.value
                if (!speaking && handled != utter) { handled = utter; lastSpeechEndMs = clock(); endedAt = clock(); kidsCounted = false }
                if (speaking) endedAt = null
                val since = endedAt?.let { clock() - it }
                if (since != null && !quizPending && _kidsQuiz.value == null) {
                    if (!kidsCounted && _audience.value == Audience.KIDS && _phase.value is DocentPhase.Guiding) {
                        kidsCounted = true; storiesSinceKidsQuiz++
                        if (storiesSinceKidsQuiz >= 2 && askKidsQuiz()) continue
                    }
                    if (pending.isNotEmpty() && since >= 1_200) { pending.removeFirstOrNull()?.let { play(it.ep, it.prefix) }; endedAt = null }
                    else if (pending.isEmpty() && since >= 2_500 && !_paused.value && !quietUntilTrigger && _phase.value is DocentPhase.Guiding && _queue.value.any { it.id !in played }) { playNext(); endedAt = null }
                }
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
                    Command.GO_THERE -> { val n = e.param?.toIntOrNull(); if (_kidsQuiz.value != null && n != null) answerKidsQuiz(n) else lastSuggestions.getOrNull((n ?: 1) - 1)?.let { cmdGoThere(it) } }
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
        say(if (_lang.value == Lang.KO) "차로 10분 안에 " + sugg.joinToString(", ") { it.name["ko"] ?: "" } + "이 있어요. 거기로 가자, 라고 말씀하세요."
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
        played.clear(); congestion = false; arrivedAtMs = null; wasPark = false; slowSinceMs = null; kidsAsked.clear(); storiesSinceKidsQuiz = 0; _kidsQuiz.value = null; pending.clear()
        nav.planLeg(g.route, g.driveMin)
        nav.setDestination(g.destination)
        val starts = eps(g).filter { it.trigger is Trigger.AtStart }.sortedBy { (it.trigger as Trigger.AtStart).order }
        _queue.value = starts
        _phase.value = DocentPhase.Guiding(starts.first(), emptyList(), false)
        _toast.value = (if (_lang.value == Lang.KO) "가이드 이어서: " else "Guide continues: ") + (g.destination.name[_lang.value.tag] ?: g.id)
        playNext()
    }

    /** Driver chose a destination in the car's navigation (or the demo picked one). */
    /** Episodes of [g] that apply to the current audience. */
    private fun eps(g: Guide) = g.episodes.filter { it.appliesTo(_audience.value) }

    fun onRouteSet(destinationName: String) {
        val g = guides.forDestination(destinationName)
        guide = g
        played.clear(); congestion = false; arrivedAtMs = null; kidsAsked.clear(); storiesSinceKidsQuiz = 0; _kidsQuiz.value = null; pending.clear()
        val starts = eps(g).filter { it.trigger is Trigger.AtStart }.sortedBy { (it.trigger as Trigger.AtStart).order }
        _queue.value = starts
        _phase.value = DocentPhase.Guiding(starts.first(), emptyList(), false)
        playNext()
    }

    private suspend fun onTick(n: NavState, v: VehicleState) {
        val g = guide ?: return
        val now = clock()

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
            val longStory = eps(g).filter { it.id !in played && it.trigger !is Trigger.Reboard && it.trigger !is Trigger.RemainUnderMin }.maxByOrNull { it.durationSec }
            if (longStory != null) fire(longStory, prefix = if (_lang.value == Lang.KO) "충전하는 동안 긴 이야기 하나 할게요. " else "While we charge, here's a longer story. ")
        }
        if (!v.charging) chargingStory = false

        // never quiet for long: filler after N minutes of silence while driving
        if (n.active && !v.gearPark && !speaker.speaking.value && !quizPending && lastSpeechEndMs > 0) {
            eps(g).filter { it.trigger is Trigger.AfterSilence && it.id !in played }.firstOrNull { now - lastSpeechEndMs > (it.trigger as Trigger.AfterSilence).minutes * 60_000L }?.let { fire(it) }
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
            val main = eps(g).firstOrNull { it.trigger is Trigger.Congestion && it.id !in played }
            if (main != null) fire(main) else overlay?.episodes?.firstOrNull { it.trigger is Trigger.Congestion && it.id !in overlayPlayed }?.let { overlayPlayed += it.id; fire(it, force = true) }
        }
        if (!nowCongested && congestion) { congestion = false }

        // geo triggers
        n.location?.let { loc ->
            eps(g).filter { it.trigger is Trigger.NearPoint && it.id !in played }.forEach { ep ->
                val t = ep.trigger as Trigger.NearPoint
                if (haversineM(loc, t.point) < t.radiusM) fire(ep)
            }
            eps(g).filter { it.trigger is Trigger.RestAreaAhead && it.id !in played }.forEach { ep ->
                val t = ep.trigger as Trigger.RestAreaAhead
                val ra = g.restAreas.firstOrNull { it.id == t.placeId } ?: return@forEach
                val d = haversineM(loc, ra.point)
                if (d < t.kmBefore * 1000 + 1500 && d > 500) fire(ep)
            }
        }

        // arrival briefing
        if (n.active && n.remainTimeSec in 1..(20 * 60)) {
            eps(g).filter { it.trigger is Trigger.RemainUnderMin && it.id !in played }.forEach { ep ->
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
            if (steps.isNotEmpty()) say((if (_lang.value == Lang.KO) "도착했어요. 여기서 할 일을 순서대로 알려드릴게요. " else "We've arrived. Here's what to do, in order. ") + steps.joinToString(" "), _lang.value) else speaker.stop()
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
            fire(eps(g).firstOrNull { it.trigger is Trigger.Reboard }, prefix = if (disappointed) (if (_lang.value == Lang.KO) "기대와 달랐나요? " else "Not what you hoped? ") else "")
        }
        wasPark = v.gearPark
    }

    /** Overlay episode for a vehicle event; waits for the current sentence queue only if nothing is mid-story. */
    private fun fireOverlay(ov: Guide, event: String) {
        val ep = ov.episodes.firstOrNull { (it.trigger as? Trigger.VehicleEvent)?.event == event && it.id !in overlayPlayed } ?: return
        overlayPlayed += ep.id
        fire(ep, prefix = if (_lang.value == Lang.KO) "잠깐, 내 차 이야기. " else "A quick word about the car. ", force = true)
    }

    /** Episodes triggered while another is still being read; played in order when speech ends. */
    private data class Pending(val ep: Episode, val prefix: String, val force: Boolean)
    private val pending = ArrayDeque<Pending>()
    /** Triggers that may interrupt mid-story (safety-relevant or time-critical); everything else waits its turn. */
    private fun interrupts(ep: Episode) = ep.trigger is Trigger.RemainUnderMin || ep.trigger is Trigger.Reboard || ep.trigger is Trigger.AtStart && ep.id.endsWith("intro")

    private fun fire(ep: Episode?, prefix: String = "", force: Boolean = false) {
        ep ?: return
        quietUntilTrigger = false
        if (!force && ep.id in played && ep.trigger !is Trigger.Reboard) return
        if (!force) played += ep.id
        if (speaker.speaking.value && !interrupts(ep) && _nowPlaying.value?.id != ep.id) {
            if (pending.none { it.ep.id == ep.id }) pending.addLast(Pending(ep, prefix, force))
            return
        }
        play(ep, prefix)
    }

    private fun play(ep: Episode, prefix: String) {
        _nowPlaying.value = ep
        _log.update { it.copy(episodesHeard = it.episodesHeard + 1) }
        if (_phase.value is DocentPhase.Guiding || _phase.value is DocentPhase.Idle) _phase.value = DocentPhase.Guiding(ep, played.toList(), congestion)
        val q = ep.quiz
        quizJob?.cancel(); quizPending = false; _quiz.value = null
        val teaser = ep.teaser[_lang.value.tag]?.let { " $it" } ?: ""
        if (q == null) {
            say(prefix + ep.textFor(_lang.value, _audience.value) + teaser, _lang.value)
        } else {
            // Story, then the question; the answer follows after a pause so the car can guess.
            quizPending = true
            _quiz.value = QuizView(q.questionFor(_lang.value), null)
            say(prefix + ep.textFor(_lang.value, _audience.value) + " " + q.questionFor(_lang.value), _lang.value)
            quizJob = scope.launch {
                delay(1_500)
                while (speaker.speaking.value) delay(300)
                delay(q.revealAfterSec * 1000L)
                revealQuiz()
            }
        }
    }

    /** Autio-style "play a story near me now": nearest unplayed NearPoint story from any guide within [maxKm]. */
    fun playNearbyNow(all: List<Guide>, maxKm: Double = 5.0): Boolean {
        val here = nav.nav.value.location ?: run { _toast.value = if (_lang.value == Lang.KO) "아직 위치를 못 잡았어요" else "No location yet"; return false }
        val cand = all.flatMap { g -> g.episodes.filter { it.trigger is Trigger.NearPoint }.map { g to it } }
            .map { (g, e) -> Triple(g, e, haversineM(here, (e.trigger as Trigger.NearPoint).point) / 1000.0) }
            .filter { it.third <= maxKm && (it.first.id + ":" + it.second.id) !in played && it.second.id !in played }
            .minByOrNull { it.third } ?: run { _toast.value = if (_lang.value == Lang.KO) "${maxKm.toInt()}km 안에 새 이야기가 없어요" else "No new story within ${maxKm.toInt()} km"; return false }
        val (g, e, km) = cand
        if (guide == null) { guide = g; _phase.value = DocentPhase.Guiding(e, emptyList(), false) }
        fire(e.copy(id = g.id + ":" + e.id), prefix = if (_lang.value == Lang.KO) "여기서 ${"%.1f".format(km)}킬로미터, ${g.destination.name["ko"]} 쪽 이야기예요. " else "About ${"%.1f".format(km)} km from here, towards ${g.destination.name["en"]}. ", force = true)
        return true
    }

    /** 👍/👎 on the story playing: kept in the log (data for ranking) and read back as a toast. */
    fun feedback(up: Boolean) {
        val ep = _nowPlaying.value ?: return
        _log.update { it.copy(feedback = it.feedback + (ep.id to up)) }
        _toast.value = if (up) (if (_lang.value == Lang.KO) "👍 좋았어요 · 비슷한 이야기를 더 골라요" else "👍 Noted, more like this") else (if (_lang.value == Lang.KO) "👎 알겠어요 · 이런 이야기는 줄일게요 (오류면 출처 확인)" else "👎 Noted, fewer like this")
    }

    /** Ask the next unasked kids quiz for this guide. Returns false when none is left. */
    fun askKidsQuiz(): Boolean {
        val g = guide ?: return false
        val q = g.kidsQuizzes.firstOrNull { it.id !in kidsAsked } ?: return false
        kidsAsked += q.id; storiesSinceKidsQuiz = 0
        _kidsQuiz.value = KidsQuizView(q, null, null)
        val opts = q.opts(_lang.value).mapIndexed { i, o -> if (_lang.value == Lang.KO) "${i + 1}번, $o" else "${i + 1}, $o" }.joinToString(". ")
        say((if (_lang.value == Lang.KO) "창밖 퀴즈! " else "Window quiz! ") + q.q(_lang.value) + " " + opts + (if (_lang.value == Lang.KO) ". 번호를 말하거나 눌러 봐!" else ". Say or tap the number!"), _lang.value)
        return true
    }

    /** Child taps or says a number (1-based). */
    fun answerKidsQuiz(index: Int) {
        val v = _kidsQuiz.value ?: return
        if (v.chosen != null) return
        val ok = index == v.quiz.answer
        _kidsQuiz.value = v.copy(chosen = index, correct = ok)
        val ansText = v.quiz.opts(_lang.value).getOrNull(v.quiz.answer - 1) ?: ""
        if (ok) { _log.update { it.copy(quizzesRight = it.quizzesRight + 1, stickers = it.stickers + 1) }; onQuizRevealed?.invoke() }
        say(if (ok) ((v.quiz.praise[_lang.value.tag] ?: (if (_lang.value == Lang.KO) "딩동댕, 정답!" else "Ding, correct!")) + (if (_lang.value == Lang.KO) " 스티커 하나 획득!" else " You earned a sticker!"))
                      else (if (_lang.value == Lang.KO) "아쉽다! 정답은 ${v.quiz.answer}번, $ansText." else "Not quite! The answer is ${v.quiz.answer}, $ansText."), _lang.value)
        scope.launch { delay(4_000); if (_kidsQuiz.value?.chosen != null) _kidsQuiz.value = null }
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
        say((if (_lang.value == Lang.KO) "정답은, " else "The answer: ") + q.answerFor(_lang.value) + (if (_audience.value == Audience.KIDS) (if (_lang.value == Lang.KO) " 스티커 하나 획득!" else " You earned a sticker!") else ""), _lang.value)
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
        pending.removeFirstOrNull()?.let { play(it.ep, it.prefix); return }
        if (_queue.value.any { it.id !in played }) playNext()
        else _toast.value = if (_lang.value == Lang.KO) "지금은 다음 이야기가 없어요. 길목에서 이어갈게요" else "Nothing queued right now; the next stop will bring more"
    }
    fun cmdMore() { if (quizPending) { quizJob?.cancel(); revealQuiz(); return }; val ep = _nowPlaying.value ?: return; ep.deep[_lang.value.tag]?.let { d -> say(d + (if (_lang.value == Lang.KO) " 출처는 ${ep.source}." else " Source: ${ep.source}."), _lang.value); return }
        if (_lang.value == Lang.KO) storyBank?.bestFor(ep.title["ko"] ?: "", guide?.destination?.name?.get("ko") ?: "")?.let { st -> say("한국관광공사 해설로 더 자세히. ${st.title}. ${st.text}", Lang.KO); _toast.value = "출처: 관광 스토리텔링 DB · ${st.spot}"; return }
        say((if (_lang.value == Lang.KO) "출처는 ${ep.source}. " else "Source: ${ep.source}. ") + ep.textFor(_lang.value, Audience.ADULT), _lang.value) }
    fun cmdQuiet() { quietUntilTrigger = true; pending.clear(); quizJob?.cancel(); quizPending = false; _quiz.value = null; speaker.stop(); _toast.value = if (_lang.value == Lang.KO) "조용히 할게요. 다음 길목에서 다시 말할게요" else "Going quiet until the next stop" }
    fun cmdSave() {
        val ep = _nowPlaying.value
        _toast.value = if (_lang.value == Lang.KO) "여행 기록에 저장했어요" else "Saved to your travel log"
        _log.update { it.copy(saved = it.saved + listOfNotNull(ep?.titleFor(_lang.value)), phrasesLearned = if (ep?.kind == "phrase") it.phrasesLearned + 1 else it.phrasesLearned) }
    }
    fun cmdGoThere(p: Place) {
        val chained = p.guideId?.let { id -> runCatching { guides.load(id) }.getOrNull() }
        if (chained != null) {
            say(if (_lang.value == Lang.KO) "${p.name["ko"]}로 갈게요. 가는 길에도 제가 계속 이야기할게요." else "Heading to ${p.name["en"]}. I'll keep the stories going on the way.", _lang.value)
            chainTo(chained); return
        }
        nav.setDestination(p)
        arrivedAtMs = null; wasPark = false
        played.removeAll { id -> guide?.episodes?.firstOrNull { it.id == id }?.trigger is Trigger.RemainUnderMin }
        _phase.value = DocentPhase.Guiding(_nowPlaying.value ?: guide!!.episodes.first(), played.toList(), false)
        say(if (_lang.value == Lang.KO) "${p.name["ko"]}로 안내할게요. ${p.note["ko"] ?: ""}" else "Routing to ${p.name["en"]}. ${p.note["en"] ?: ""}", _lang.value)
    }
    private var beforeLog: DocentPhase? = null
    fun cmdShowLog() {
        if (_phase.value is DocentPhase.TravelLog) { _phase.value = beforeLog ?: DocentPhase.Idle; return }
        beforeLog = _phase.value; _phase.value = DocentPhase.TravelLog(_log.value); say(_log.value.headline(_lang.value), _lang.value)
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
        speaker.stop(); guide = null; played.clear(); _queue.value = emptyList(); _nowPlaying.value = null; _kidsQuiz.value = null; kidsAsked.clear(); pending.clear()
        congestion = false; arrivedAtMs = null; wasPark = true
        _phase.value = DocentPhase.Idle
    }
    fun cmdReserve(): Place? {
        val b = (_phase.value as? DocentPhase.Briefing)?.place ?: return null
        if (b.phone.isNullOrBlank()) { say(if (_lang.value == Lang.KO) "${b.name["ko"]}은 전화 예약이 없어요. 온라인 예약 링크를 폰으로 보냈어요." else "${b.name["en"]} has no phone booking; I've sent the online link to your phone.", _lang.value); _toast.value = if (_lang.value == Lang.KO) "전화번호 없음 · 온라인 예약 안내" else "No phone · online booking"; return null }
        say(if (_lang.value == Lang.KO) "${b.name["ko"]}에 전화를 걸게요. 4명, 도착 시간으로 말씀하시면 돼요." else "Calling ${b.name["en"]}. Say: four people, at our arrival time.", _lang.value)
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
