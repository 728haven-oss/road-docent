package kr.roaddocent.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.Json
import kr.roaddocent.OnboardingScreen
import kr.roaddocent.ParkedScreen
import kr.roaddocent.core.Audience
import kr.roaddocent.core.GeoPoint
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Prefs
import kr.roaddocent.core.Trigger
import kr.roaddocent.log.TripLog
import kr.roaddocent.log.Visit
import kr.roaddocent.ui.screen.BriefingScreen
import kr.roaddocent.ui.screen.LoginScreen
import kr.roaddocent.ui.screen.PlayingScreen
import kr.roaddocent.ui.screen.SettingsScreen
import kr.roaddocent.ui.screen.TermsScreen
import kr.roaddocent.ui.screen.TravelLogScreen
import kr.roaddocent.ui.screen.WhereNextScreen
import kr.roaddocent.ui.theme.RoadDocentTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every screen at Galaxy S2x size (411×891 dp) so layout breakage is visible before shipping. Output: app/build/shots/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private fun guide(id: String): Guide = json.decodeFromString(Guide.serializer(), File("src/main/assets/guides/$id.json").readText())
    private val gb by lazy { guide("seoul_gyeongbokgung") }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { RoadDocentTheme { Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) { content() } } }
        rule.onRoot().captureRoboImage(File(System.getProperty("user.dir"), "build/shots/$name.png").absolutePath)
    }

    @Test fun s01_login() = shot("01_login") { LoginScreen(error = null, googleReady = false, busy = false, onLocal = { _, _ -> }, onGoogle = {}) }
    @Test fun s02_terms() = shot("02_terms") { TermsScreen(File("src/main/assets/terms_ko.md").readText(), onAccept = {}, onDecline = {}) }
    @Test fun s03_onboarding() = shot("03_onboarding") { OnboardingScreen(Prefs(), onDone = {}) }
    @Test fun s05_playing() = shot("05_playing") {
        val eps = gb.episodes.filter { it.trigger !is Trigger.Reboard }
        val ep = eps.first { it.id == "namdaemun" }
        PlayingScreen(ep, eps, eps.take(3).map { it.id }, Lang.KO, Audience.ADULT, congestion = false, paused = false, speaking = true, onSkip = {}, onMore = {}, onSave = {}, onQuiet = {})
    }
    @Test fun s06_briefing() = shot("06_briefing") { BriefingScreen(gb.nearby.first { it.id == "tongin" }, gb.nearby.drop(1).take(3), Lang.KO, 14, onReserve = {}, onOther = {}) }
    @Test fun s07_parked() = shot("07_parked") { ParkedScreen(gb.destination, Lang.KO, gb.handoff) }
    @Test fun s08_wherenext() = shot("08_wherenext") { WhereNextScreen(42, gb.nearby.take(3), false, Lang.KO, onGo = {}, onHome = {}) }
    @Test fun s09_log() = shot("09_log") {
        val log = TripLog(trace = gb.route, visits = listOf(Visit("경복궁", gb.destination.point, 0, 120 * 60_000L)), distanceKm = 54f, drivingMin = 72, episodesHeard = 9, quizzesRight = 3, stickers = 3)
        TravelLogScreen(log, Lang.KO, onShare = {}, onBack = {})
    }
    @Test fun s04_home() = shot("04_home") {
        val all = listOf("seoul_gyeongbokgung", "seoul_national_museum", "yeongwol_danjong", "seoul_gangneung", "goyang_motorstudio").map { guide(it) }
        kr.roaddocent.ui.screen.HomeScreen("테스터", Lang.KO, "시연 모드 · 코스를 고르면 내비가 설정되고 가이드가 탑승해요.", all, "", all[0] to 12L, onTheme = {}, onStart = {}, onNearby = {}, onResume = {}, onDismissResume = {})
    }
    @Test fun s04b_tripintro() = shot("04b_tripintro") { kr.roaddocent.TripIntroScene(gb, Lang.KO, gb.route.first()) }
    @Test fun s05b_kidsquiz() = shot("05b_kidsquiz") { kr.roaddocent.ui.screen.KidsQuizCard(kr.roaddocent.engine.DocentEngine.KidsQuizView(gb.kidsQuizzes.first(), null, null), Lang.KO, onAnswer = {}) }
    @Test fun s11_map_museum() = shot("11_map_museum") {
        val g = guide("seoul_national_museum")
        val pts = g.episodes.mapNotNull { e -> (e.trigger as? Trigger.NearPoint)?.let { it.point to e.titleFor(Lang.KO) } }
        kr.roaddocent.ui.screen.TripMap(g.route, g.route[3], pts, "경복궁", "국립중앙박물관", Modifier.fillMaxSize())
    }
    @Test fun s12_map_gangneung() = shot("12_map_gangneung") {
        val g = guide("seoul_gangneung")
        val pts = g.episodes.mapNotNull { e -> (e.trigger as? Trigger.NearPoint)?.let { it.point to e.titleFor(Lang.KO) } }
        kr.roaddocent.ui.screen.TripMap(g.route, g.route[5], pts, "서울", "강릉", Modifier.fillMaxSize())
    }
    @Test fun s10_settings() = shot("10_settings") { SettingsScreen(Prefs(audience = Audience.KIDS), null, true, onChange = {}, onVoiceSetup = {}, onDeleteVoice = {}, onClose = {}) }
}
