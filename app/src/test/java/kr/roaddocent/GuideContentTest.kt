package kr.roaddocent

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Content checks: every guide JSON parses, ids are unique, chained guides exist, quizzes are bilingual. */
class GuideContentTest {
    private val json = Json {
        ignoreUnknownKeys = true; classDiscriminator = "type"
        serializersModule = SerializersModule { polymorphic(Trigger::class) {
            subclass(Trigger.AtStart::class); subclass(Trigger.NearPoint::class); subclass(Trigger.RemainUnderMin::class)
            subclass(Trigger.Congestion::class); subclass(Trigger.RestAreaAhead::class); subclass(Trigger.Reboard::class); subclass(Trigger.VehicleEvent::class); subclass(Trigger.AfterSilence::class) } }
    }
    private val assets = File("src/main/assets")
    private fun guides() = File(assets, "guides").listFiles { f -> f.extension == "json" }!!.sorted().map { it.nameWithoutExtension to json.decodeFromString(Guide.serializer(), it.readText()) }
    private fun overlays() = File(assets, "overlays").listFiles { f -> f.extension == "json" }!!.map { json.decodeFromString(Guide.serializer(), it.readText()) }

    @Test fun allGuidesParseAndHaveCoreEpisodes() {
        val gs = guides(); assertTrue(gs.size >= 5)
        gs.forEach { (file, g) ->
            assertEquals(file, g.id)
            assertTrue("$file has no AtStart", g.episodes.any { it.trigger is Trigger.AtStart })
            assertTrue("$file has no arrival briefing", g.episodes.any { it.trigger is Trigger.RemainUnderMin })
            assertTrue("$file has no reboard", g.episodes.any { it.trigger is Trigger.Reboard })
            assertTrue("$file has no congestion story", g.episodes.any { it.trigger is Trigger.Congestion })
            assertEquals("$file duplicate episode ids", g.episodes.size, g.episodes.map { it.id }.toSet().size)
            assertTrue("$file onArrival ko", g.destination.onArrival["ko"].orEmpty().size >= 5)
            assertTrue("$file themes", g.themes.isNotEmpty())
            assertTrue("$file knowBeforeYouGo ko/en", g.knowBeforeYouGo["ko"].orEmpty().size >= 4 && g.knowBeforeYouGo["en"].orEmpty().size >= 4)
            g.episodes.filter { it.deep.isNotEmpty() }.forEach { e -> assertTrue("$file/${e.id} deep bilingual", e.deep.keys.containsAll(listOf("ko", "en"))) }
            g.episodes.forEach { e ->
                assertNotNull("$file/${e.id} ko", e.text["ko"]); assertNotNull("$file/${e.id} en", e.text["en"])
                e.quiz?.let { q -> assertTrue("$file/${e.id} quiz bilingual", q.question.keys.containsAll(listOf("ko", "en")) && q.answer.keys.containsAll(listOf("ko", "en"))) }
            }
        }
    }

    @Test fun chainedGuidesExist() {
        val ids = guides().map { it.first }.toSet()
        guides().forEach { (file, g) -> g.nearby.mapNotNull { it.guideId }.forEach { assertTrue("$file chains to missing $it", it in ids) } }
        val gb = guides().first { it.first == "seoul_gyeongbokgung" }.second
        assertEquals("seoul_national_museum", gb.nearby.first().guideId)
    }

    @Test fun overlayCoversVehicleEvents() {
        val ov = overlays().first { it.id == "car_onboarding" }
        val events = ov.episodes.mapNotNull { (it.trigger as? Trigger.VehicleEvent)?.event }.toSet()
        assertTrue(events.containsAll(listOf("first_drive", "rear_seat", "speed_60", "speed_100", "trip_5km", "trip_15km", "park")))
    }

    @Test fun kidsTextFallsBackToAdult() {
        val g = guides().first().second
        val e = g.episodes.first { it.kidsText.isEmpty() }
        assertEquals(e.text["en"], e.textFor(Lang.EN, kr.roaddocent.core.Audience.KIDS))
    }
}
