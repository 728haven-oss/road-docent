package kr.roaddocent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kr.roaddocent.content.GuideSource
import kr.roaddocent.core.Audience
import kr.roaddocent.core.DocentPhase
import kr.roaddocent.core.GeoPoint
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Lang
import kr.roaddocent.core.NavState
import kr.roaddocent.core.Place
import kr.roaddocent.core.VehicleState
import kr.roaddocent.engine.DocentEngine
import kr.roaddocent.speech.Speaker
import kr.roaddocent.vehicle.NavSignals
import kr.roaddocent.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The whole docent flow without a device: fake navigation, fake vehicle, fake speaker, the real guide JSON.
 * Each test drives signals the way the car (or the simulator) would and asserts what the engine says and shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineFlowTest {

    // ---- doubles -------------------------------------------------------------------------------------------
    class FileGuides : GuideSource {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
        private val dir = File("src/main/assets")
        override fun load(id: String): Guide = json.decodeFromString(Guide.serializer(), File(dir, "guides/$id.json").readText())
        override fun all(): List<Guide> = File(dir, "guides").listFiles { f -> f.extension == "json" }!!.sorted().map { load(it.nameWithoutExtension) }
        override fun forDestination(name: String): Guide = all().firstOrNull { g -> g.destination.name.values.any { name.contains(it) || it.contains(name) } } ?: load("seoul_gangneung")
        override fun loadOverlay(id: String): Guide? = runCatching { json.decodeFromString(Guide.serializer(), File(dir, "overlays/$id.json").readText()) }.getOrNull()
    }

    class FakeNav : NavSignals {
        private val _nav = MutableStateFlow(NavState())
        override val nav: StateFlow<NavState> = _nav.asStateFlow()
        var plannedRoute: List<GeoPoint>? = null
        val destinations = mutableListOf<Place>()
        override fun setDestination(place: Place) { destinations += place; _nav.update { NavState(active = true, destinationName = place.name["ko"] ?: "", destination = place.point, location = it.location, remainTimeSec = 60 * 60, remainDistanceM = 50_000) } }
        override fun planLeg(route: List<GeoPoint>, minutes: Int) { plannedRoute = route }
        override fun start() {}
        override fun stop() {}
        fun at(p: GeoPoint, remainSec: Int = 3600, arrived: Boolean = false) = _nav.update { it.copy(location = p, remainTimeSec = remainSec, arrived = arrived) }
    }

    class FakeVehicle : VehicleSignals {
        private val _v = MutableStateFlow(VehicleState(gearPark = false, speedKmh = 60f))
        override val vehicle: StateFlow<VehicleState> = _v.asStateFlow()
        override fun start() {}
        override fun stop() {}
        fun set(block: (VehicleState) -> VehicleState) = _v.update(block)
    }

    class FakeSpeaker : Speaker {
        private val _speaking = MutableStateFlow(false)
        override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
        val spoken = mutableListOf<String>()
        override fun speak(text: String, lang: Lang, utteranceId: String) { spoken += text; _speaking.value = true }
        /** The fake "finishes" an utterance when the test says so. */
        fun finish() { _speaking.value = false }
        override fun pause() {}
        override fun resume() {}
        override fun stop() { _speaking.value = false }
        override fun shutdown() {}
        fun last() = spoken.lastOrNull() ?: ""
    }

    private class Rig(scope: TestScope) {
        val nav = FakeNav(); val veh = FakeVehicle(); val spk = FakeSpeaker(); val guides = FileGuides()
        val engine = DocentEngine(nav, veh, spk, guides, scope).also { e -> e.clock = { scope.testScheduler.currentTime }; e.start() }
        /** What the app does on a destination: the engine picks the guide and the navigation gets the route (ViewModel.demoStartTrip). */
        fun start(name: String) { engine.onRouteSet(name); nav.setDestination(engine.guide!!.destination) }
    }

    private fun TestScope.tick() = advanceTimeBy(100)

    // ---- tests ---------------------------------------------------------------------------------------------
    @Test fun destinationSetStartsWithGreetingAndQueue() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick()
        assertTrue(r.engine.phase.value is DocentPhase.Guiding)
        assertTrue("first words are the greeting", r.spk.last().contains("로드 도슨트"))
        assertTrue("start queue has more stories", r.engine.queue.value.isNotEmpty())
        r.engine.stop()
    }

    @Test fun autoAdvanceAfterSpeechEnds() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick()
        val first = r.spk.spoken.size
        r.spk.finish(); advanceTimeBy(3_000)
        assertTrue("next queued story plays ~2.5 s after the first ends", r.spk.spoken.size > first)
        r.engine.stop()
    }

    @Test fun quietStaysQuietUntilNextTrigger() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick()
        r.engine.cmdQuiet(); val n = r.spk.spoken.size
        advanceTimeBy(10_000)
        assertEquals("nothing auto-plays after quiet", n, r.spk.spoken.size)
        r.nav.at(GeoPoint(37.56, 126.9753)); advanceTimeBy(500)   // 남대문
        assertTrue("a real trigger speaks again", r.spk.spoken.size > n && r.spk.last().contains("남대문"))
        r.engine.stop()
    }

    @Test fun nearPointFiresOnceAndCongestionTellsLongStory() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick(); r.spk.finish()
        r.nav.at(GeoPoint(37.572, 126.9768)); advanceTimeBy(500)      // 광화문
        assertTrue(r.spk.last().contains("세종대왕"))
        val n = r.spk.spoken.size
        r.nav.at(GeoPoint(37.5721, 126.9769)); advanceTimeBy(500)
        assertEquals("same point does not fire twice", n, r.spk.spoken.size)
        r.veh.set { it.copy(speedKmh = 10f) }; advanceTimeBy(61_000)
        r.nav.at(GeoPoint(37.5722, 126.977)); advanceTimeBy(500)
        assertTrue("congestion >60 s brings the long story", r.spk.last().contains("세 번") || r.spk.last().contains("1395"))
        r.engine.stop()
    }

    @Test fun quizRevealsAfterPauseAndCountsSticker() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.engine.setAudience(Audience.KIDS)
        r.start("경복궁"); tick()
        // play through the queue until a quiz episode is up
        repeat(6) { if (r.engine.quiz.value == null) { r.spk.finish(); advanceTimeBy(3_000) } }
        assertNotNull("a quiz episode is reached in the start queue", r.engine.quiz.value)
        assertEquals(null, r.engine.quiz.value!!.answer)
        r.spk.finish(); advanceTimeBy(10_000)
        assertNotNull("answer revealed after the pause", r.engine.quiz.value!!.answer)
        assertTrue(r.spk.last().startsWith("정답은"))
        assertEquals(1, r.engine.log.value.stickers)
        r.engine.stop()
    }

    @Test fun arrivalBriefingParkChecklistReboardAndChainToMuseum() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick(); r.spk.finish()
        r.nav.at(GeoPoint(37.575, 126.976), remainSec = 14 * 60); advanceTimeBy(500)
        assertTrue("briefing phase at <15 min", r.engine.phase.value is DocentPhase.Briefing)
        assertTrue(r.spk.last().contains("15분"))
        // arrive and park
        r.nav.at(GeoPoint(37.5796, 126.977), remainSec = 0, arrived = true)
        r.veh.set { it.copy(gearPark = true, speedKmh = 0f) }; advanceTimeBy(500)
        val parked = r.engine.phase.value
        assertTrue("parked phase", parked is DocentPhase.Parked)
        assertTrue("checklist spoken", r.spk.last().contains("여기서 할 일"))
        // re-board after 40 (simulated) minutes → not disappointed; museum is the first suggestion
        advanceTimeBy(40 * 60 * 1000L)
        r.veh.set { it.copy(gearPark = false, speedKmh = 5f) }; advanceTimeBy(500)
        val wn = r.engine.phase.value
        assertTrue("where-next phase", wn is DocentPhase.WhereNext)
        wn as DocentPhase.WhereNext
        assertEquals("seoul_national_museum", wn.suggestions.first().guideId)
        // go there → the guide chains and keeps talking
        r.engine.cmdGoThere(wn.suggestions.first()); advanceTimeBy(500)
        assertEquals("국립중앙박물관", r.engine.guide?.destination?.name?.get("ko"))
        assertTrue("next leg's route handed to navigation", (r.nav.plannedRoute?.size ?: 0) > 2)
        assertTrue(r.engine.phase.value is DocentPhase.Guiding)
        assertTrue("museum intro starts", r.spk.spoken.takeLast(2).any { it.contains("박물관") })
        r.engine.stop()
    }

    @Test fun rearSeatSwitchesKidsOnceAndCanBeSwitchedBack() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick()
        r.veh.set { it.copy(rearSeatOccupied = true) }; advanceTimeBy(300)
        assertEquals(Audience.KIDS, r.engine.audience.value)
        r.engine.setAudience(Audience.ADULT); r.veh.set { it.copy(speedKmh = 61f) }; advanceTimeBy(300)
        assertEquals("manual switch back sticks while the seat stays occupied", Audience.ADULT, r.engine.audience.value)
        r.engine.stop()
    }

    @Test fun safetyPauseAboveLimit() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick()
        r.veh.set { it.copy(speedKmh = 120f) }; advanceTimeBy(300)
        assertTrue(r.engine.paused.value)
        r.veh.set { it.copy(speedKmh = 80f) }; advanceTimeBy(300)
        assertFalse(r.engine.paused.value)
        r.engine.stop()
    }

    @Test fun backFromTripReturnsToIdleAndSecondTripWorks() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.start("경복궁"); tick()
        assertTrue(r.engine.goBack())
        assertTrue(r.engine.phase.value is DocentPhase.Idle)
        r.start("강릉"); tick()
        assertTrue(r.engine.guide?.destination?.name?.get("ko")?.startsWith("강릉") == true)
        assertTrue(r.spk.last().contains("강릉"))
        r.engine.stop()
    }

    @Test fun freeRideFiresNearPointsFromAnyGuide() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.engine.startFreeRide(r.guides.all()); tick(); r.spk.finish()
        r.nav.at(GeoPoint(37.5547, 126.9707)); advanceTimeBy(500)   // 서울역 (museum guide)
        assertTrue(r.spk.last().contains("서울역"))
        r.engine.stop()
    }

    @Test fun newCarOverlayExplainsSensorsAsTheyWork() = runTest(StandardTestDispatcher()) {
        val r = Rig(this)
        r.engine.overlay = r.guides.loadOverlay("car_onboarding")
        r.start("경복궁"); tick(); r.spk.finish()
        advanceTimeBy(300)
        assertTrue("first drive sensor map", r.spk.spoken.any { it.contains("센서") })
        r.veh.set { it.copy(speedKmh = 65f) }; advanceTimeBy(300)
        assertTrue(r.spk.last().contains("카메라"))
        r.engine.stop()
    }
}
