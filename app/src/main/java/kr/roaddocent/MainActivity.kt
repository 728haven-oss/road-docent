package kr.roaddocent

import android.Manifest
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.roaddocent.core.Audience
import kr.roaddocent.core.DocentPhase
import kr.roaddocent.core.Lang
import kr.roaddocent.ui.DocentViewModel
import kr.roaddocent.ui.Source
import kr.roaddocent.ui.screen.ActionButton
import kr.roaddocent.ui.screen.BriefingScreen
import kr.roaddocent.ui.screen.KidsPanel
import kr.roaddocent.ui.screen.PlayingScreen
import kr.roaddocent.ui.screen.SettingsScreen
import kr.roaddocent.ui.screen.TravelLogScreen
import kr.roaddocent.ui.screen.VoiceFamilyScreen
import kr.roaddocent.ui.screen.WhereNextScreen
import androidx.compose.ui.graphics.Color
import kr.roaddocent.ui.theme.Accent
import kr.roaddocent.ui.theme.Bg
import kr.roaddocent.ui.theme.Body
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.TwoPane
import kr.roaddocent.ui.theme.Pill
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.RoadDocentTheme

class MainActivity : ComponentActivity() {
    private val vm: DocentViewModel by viewModels()

    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val loc = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.requestMic = { mic.launch(Manifest.permission.RECORD_AUDIO) }
        mic.launch(Manifest.permission.RECORD_AUDIO)
        loc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        setContent { RoadDocentTheme { Root(vm) { number -> dial(number) } } }
    }

    private fun dial(number: String) {
        // Bluetooth-connected phone picks this up on Pleos Connect; ACTION_DIAL needs no runtime permission.
        runCatching { startActivity(if (number.startsWith("http")) Intent(Intent.ACTION_VIEW, Uri.parse(number)) else Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
}

@Composable
fun Root(vm: DocentViewModel, onDial: (String) -> Unit) {
    val source by vm.source.collectAsState()
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (source == null) ConnectScreen(onPick = { vm.connect(it) }) else Shell(vm, onDial)
    }
}

@Composable
fun ConnectScreen(onPick: (Source) -> Unit) {
    Column(Modifier.fillMaxSize().padding(44.dp), verticalArrangement = Arrangement.Center) {
        Headline("로드 도슨트 · Road Docent", 36)
        Spacer(Modifier.height(8.dp))
        Body("목적지를 찍는 순간, 내 차에 그 지역 전문 가이드가 탑승한다.", size = 18)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionButton("Pleos 차량 연결 (NaviHelper · Vehicle SDK)", primary = true) { onPick(Source.PLEOS) }
            ActionButton("시뮬레이션 · 데모") { onPick(Source.SIMULATOR) }
            ActionButton("📍 휴대폰 GPS · 실제 위치") { onPick(Source.PHONE_GPS) }
        }
        Spacer(Modifier.height(18.dp))
        Body("Pleos 연결은 Pleos Connect AVD 또는 실차에서 동작합니다. 시뮬레이션은 어떤 Android 기기에서도 전체 흐름을 보여줍니다. 휴대폰 GPS는 실제 위치로 길목 해설·도착·재탑승이 일어납니다 (목적지를 고르거나 🪟 창밖으로 그냥 달리기).", size = 13)
    }
}

@Composable
fun Shell(vm: DocentViewModel, onDial: (String) -> Unit) {
    val engine = vm.engine
    val phase by engine.phase.collectAsState()
    val lang by engine.lang.collectAsState()
    val audience by engine.audience.collectAsState()
    val log by engine.log.collectAsState()
    val paused by engine.paused.collectAsState()
    val toast by engine.toast.collectAsState()
    val nav by engine.navState.collectAsState()
    val speaking by engine.speaking.collectAsState()
    val quiz by engine.quiz.collectAsState()
    val vehicle by engine.vehicleState.collectAsState()
    val companionUrl by vm.companionUrl.collectAsState()
    var companionOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val voiceSetup by vm.voiceSetup.collectAsState()
    val voiceProfile by vm.voiceProfile.collectAsState()
    val recording by vm.recorder.recording.collectAsState()
    LaunchedEffect(toast) { if (toast != null) { delay(3000); engine.clearToast() } }

    val settingsOpen by vm.settingsOpen.collectAsState()
    val prefs by vm.settings.prefs.collectAsState()
    val listening by vm.localVoice.listening.collectAsState()
    val heard by vm.localVoice.heard.collectAsState()
    kr.roaddocent.ui.theme.TypeScale.factor = prefs.fontScale
    androidx.activity.compose.BackHandler(enabled = true) { if (companionOpen) companionOpen = false else vm.back() }
    if (!prefs.onboarded) {
        Box(Modifier.fillMaxSize().padding(horizontal = if (kr.roaddocent.ui.theme.isCompact()) 12.dp else 36.dp, vertical = if (kr.roaddocent.ui.theme.isCompact()) 10.dp else 22.dp)) { OnboardingScreen(prefs, onDone = { vm.applyPrefs(it.copy(onboarded = true)) }) }
        return
    }
    if (companionOpen) {
        Box(Modifier.fillMaxSize().padding(horizontal = if (kr.roaddocent.ui.theme.isCompact()) 12.dp else 36.dp, vertical = if (kr.roaddocent.ui.theme.isCompact()) 10.dp else 22.dp)) { CompanionPanel(vm, lang, companionUrl) { companionOpen = false } }
        return
    }
    if (settingsOpen) {
        Box(Modifier.fillMaxSize().padding(horizontal = if (kr.roaddocent.ui.theme.isCompact()) 12.dp else 36.dp, vertical = if (kr.roaddocent.ui.theme.isCompact()) 10.dp else 22.dp)) {
            SettingsScreen(prefs, voiceProfile, vm.localVoice.available, onChange = vm::applyPrefs,
                onVoiceSetup = { vm.openSettings(false); vm.openVoiceSetup(true) }, onDeleteVoice = vm::deleteVoice, onClose = { vm.openSettings(false) })
        }
        return
    }
    if (voiceSetup) {
        Box(Modifier.fillMaxSize().padding(horizontal = if (kr.roaddocent.ui.theme.isCompact()) 12.dp else 36.dp, vertical = if (kr.roaddocent.ui.theme.isCompact()) 10.dp else 22.dp)) {
            val verr by vm.voiceError.collectAsState()
            VoiceFamilyScreen(lang, recording, vm.cloudAvailable, error = verr, onStart = vm::startSample, onStop = vm::stopSample, onBuild = vm::buildVoice, onClose = { vm.clearVoiceError(); vm.openVoiceSetup(false) })
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = if (kr.roaddocent.ui.theme.isCompact()) 12.dp else 36.dp, vertical = if (kr.roaddocent.ui.theme.isCompact()) 10.dp else 22.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("←", Modifier.background(Chip, RoundedCornerShape(999.dp)).clickable { vm.back() }.padding(horizontal = 12.dp, vertical = 4.dp), color = Ink, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (lang == Lang.KO) "로드 도슨트" else "Road Docent", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (nav.active) Text("  · ${nav.destinationName}${if (lang == Lang.KO) "까지 " else " in "}${nav.remainTimeSec / 60}${if (lang == Lang.KO) "분" else " min"}", color = Quiet, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            toast?.let { Text(it, Modifier.background(Chip, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp).widthIn(max = 220.dp), color = Ink, fontSize = 13.sp, maxLines = 1); Spacer(Modifier.width(6.dp)) }
            if (heard.isNotBlank()) { Text("“$heard”", Modifier.background(Chip, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp).widthIn(max = 160.dp), color = Ink, fontSize = 13.sp, maxLines = 1); Spacer(Modifier.width(6.dp)) }
            if (prefs.localVoiceCommands && vm.localVoice.available) {
                Text(if (listening) "🎤 …" else "🎤", Modifier.background(if (listening) Accent else Chip, RoundedCornerShape(999.dp)).clickable { vm.listen() }.padding(horizontal = 14.dp, vertical = 6.dp), color = if (listening) Color(0xFF06111F) else Ink, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
            }
            voiceProfile?.let { Pill(if (lang == Lang.KO) "🎙 ${it.name} 목소리" else "🎙 ${it.name}'s voice"); Spacer(Modifier.width(8.dp)) }
            Pill(if (lang == Lang.KO) "🎧 가이드" else "🎧 Guide")
            Spacer(Modifier.width(8.dp))
            Pill(if (audience == Audience.KIDS) (if (lang == Lang.KO) "👨‍👩‍👧 키즈 모드" else "👨‍👩‍👧 Kids") else if (lang == Lang.KO) "👤 일반" else "👤 Adult", Modifier)
        }
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f)) {
            when (val p = phase) {
                is DocentPhase.Idle -> IdleScreen(lang, vm)
                is DocentPhase.Guiding -> Column {
                    PlayingScreen(
                        ep = p.episode, all = engine.guide?.episodes?.filter { it.trigger !is kr.roaddocent.core.Trigger.Reboard } ?: emptyList(),
                        played = p.playedIds, lang = lang, audience = audience, congestion = p.congestion, paused = paused, speaking = speaking, quiz = quiz,
                        driving = !vehicle.gearPark && vehicle.speedKmh > 5f && !prefs.alwaysFull,
                        onSkip = engine::cmdSkip, onMore = engine::cmdMore, onSave = engine::cmdSave, onQuiet = engine::cmdQuiet
                    )
                }
                is DocentPhase.Briefing -> BriefingScreen(p.place, p.alternatives, lang, nav.remainTimeSec / 60,
                    onReserve = { engine.cmdReserve()?.phone?.let(onDial) }, onOther = { engine.cmdSkip() })
                is DocentPhase.Parked -> ParkedScreen(p.place, lang, engine.guide?.handoff, onOpen = onDial) { vm.openVoiceSetup(true) }
                is DocentPhase.WhereNext -> WhereNextScreen(p.leftAfterMin, p.suggestions, p.disappointed, lang, onGo = vm::goThere, onHome = engine::cmdShowLog)
                is DocentPhase.TravelLog -> TravelLogScreen(log, lang, vm.history.days(), onShare = { vm.shareToday() }) { engine.cmdShowLog() }
            }
        }

        val hintCtx = when (phase) { is DocentPhase.Briefing -> "briefing"; is DocentPhase.WhereNext -> "wherenext"; is DocentPhase.Parked -> "parked"; else -> null }
        if (hintCtx != null) { Spacer(Modifier.height(8.dp)); kr.roaddocent.ui.screen.VoiceHint(lang, hintCtx) }
        if (audience == Audience.KIDS && phase is DocentPhase.Guiding && vehicle.gearPark) { Spacer(Modifier.height(12.dp)); KidsPanel(lang, log.quizzesRight) }
        val courses = engine.guide?.creatorCourses.orEmpty()
        if (courses.isNotEmpty() && (phase is DocentPhase.Briefing || phase is DocentPhase.WhereNext)) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text(if (lang == Lang.KO) "🎬 크리에이터 코스 따라가기" else "🎬 Follow a creator's course", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    courses.take(3).forEach { c ->
                        Column(Modifier.weight(1f)) {
                            Text("${c.creator} · ${c.platform}", color = Quiet, fontSize = 12.sp, maxLines = 1)
                            Text(c.title[lang.tag] ?: c.title.values.first(), color = Ink, fontSize = 14.sp, maxLines = 2)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        DemoBar(vm, lang, audience, onCompanion = { vm.startCompanion(); companionOpen = true })
    }
}

@Composable
fun IdleScreen(lang: Lang, vm: DocentViewModel) {
  var preview by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kr.roaddocent.core.Guide?>(null) }
  TwoPane() {
    Panel(Modifier.weight(1.3f).fillMaxHeight()) {
        Headline(if (lang == Lang.KO) "목적지를 설정하면 가이드가 탑승합니다" else "Set a destination and your guide boards", 28)
        Spacer(Modifier.height(10.dp))
        Body(if (lang == Lang.KO) "Pleos 연결 시 차량 내비에서 목적지를 설정하세요. 데모에서는 아래 버튼으로 서울→강릉 경로를 시작합니다." else "With Pleos, set a destination in the built-in navigation. In the demo, start the Seoul → Gangneung route below.", size = 16)
        Spacer(Modifier.height(20.dp))
        val prefs by vm.settings.prefs.collectAsState()
        Text(if (lang == Lang.KO) "테마로 고르기" else "Pick by theme", color = Quiet, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (lang == Lang.KO) "전체" else "All", Modifier.clickable { vm.setTheme("") }, selected = prefs.theme.isBlank())
            kr.roaddocent.core.Themes.order.filter { it != "car" }.forEach { t -> Pill(kr.roaddocent.core.Themes.label(t, lang), Modifier.clickable { vm.setTheme(t) }, selected = prefs.theme == t) }
        }
        Spacer(Modifier.height(12.dp))
        Text(if (lang == Lang.KO) "데모 목적지 (Pleos에서는 내비 목적지로 자동 시작)" else "Demo destinations (on Pleos, the navi destination starts it)", color = Quiet, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        val list = vm.guides().filter { prefs.theme.isBlank() || prefs.theme in it.themes }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { g ->
                val th = g.themes.joinToString(" ") { kr.roaddocent.core.Themes.label(it, lang).take(2) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton((g.startName[lang.tag] ?: "") + " → " + (g.destination.name[lang.tag] ?: g.id) + "   " + th, primary = g.id == "seoul_gyeongbokgung") { vm.demoStartTrip(g.id) }
                    if (g.knowBeforeYouGo.isNotEmpty()) Small(if (lang == Lang.KO) "ℹ 알아두기" else "ℹ Before you go") { preview = g }
                }
            }
        }
    }
    Panel(Modifier.weight(1f).fillMaxHeight()) {
        val g = preview ?: vm.guides().firstOrNull { it.id == "seoul_gyeongbokgung" }
        Text(if (lang == Lang.KO) "출발 전 알아두기 · " + (g?.destination?.name?.get(lang.tag) ?: "") else "Know before you go · " + (g?.destination?.name?.get(lang.tag) ?: ""), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        var more by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val lines = g?.knowBeforeYouGo?.get(lang.tag).orEmpty()
        (if (kr.roaddocent.ui.theme.isCompact() && !more) lines.take(3) else lines).forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) { Text("•", color = Quiet, fontSize = 14.sp, modifier = Modifier.width(16.dp)); Text(line, color = Ink, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        if (kr.roaddocent.ui.theme.isCompact() && lines.size > 3) Small(if (more) (if (lang == Lang.KO) "접기" else "Less") else (if (lang == Lang.KO) "더 보기 (${lines.size - 3})" else "More (${lines.size - 3})")) { more = !more }
        Spacer(Modifier.weight(1f))
        Body(if (lang == Lang.KO) "운영시간·요금·주차는 출발 당일 공식 안내로 한 번 더 확인하세요." else "Re-check hours, fees and parking with the venue on the day.", size = 12)
    }
  }
}

@Composable
fun ParkedScreen(place: kr.roaddocent.core.Place, lang: Lang, handoff: kr.roaddocent.core.Handoff? = null, onOpen: (String) -> Unit = {}, onVoice: () -> Unit = {}) {
    val name = place.name[lang.tag] ?: ""
    val steps = place.onArrival[lang.tag].orEmpty()
    TwoPane() {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Headline(if (lang == Lang.KO) "$name 도착" else "Arrived: $name", 28)
            Spacer(Modifier.height(10.dp))
            Body(place.note[lang.tag] ?: "", size = 16)
            Spacer(Modifier.height(10.dp))
            Body(if (lang == Lang.KO) "다시 출발하시면(기어 D) 다음 행선지를 제안해 드릴게요." else "When you set off again (gear D) I'll suggest where next.", size = 14)
            if (handoff != null) {
                Spacer(Modifier.height(14.dp))
                ActionButton("📱 " + (handoff.label[lang.tag] ?: ""), primary = true) { onOpen(handoff.url) }
                Spacer(Modifier.height(6.dp))
                Body(handoff.note[lang.tag] ?: "", size = 12)
            }
            Spacer(Modifier.height(14.dp))
            ActionButton(if (lang == Lang.KO) "🎙 가족 목소리 녹음" else "🎙 Record family voice") { onVoice() }
        }
        Panel(Modifier.weight(1.4f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "여기서 할 일" else "What to do here", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            steps.forEachIndexed { i, st ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text("${i + 1}", color = Quiet, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                    Text(st, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

/** Demo controls: stand in for real signals. Hidden in production builds via BuildConfig. */
@Composable
fun DemoBar(vm: DocentViewModel, lang: Lang, audience: Audience, onCompanion: () -> Unit = {}) {
    val sim = vm.sim
    Row(Modifier.fillMaxWidth().background(Chip.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (sim != null) "DEMO" else if (vm.gps != null) "GPS" else "PLEOS", color = Quiet, fontSize = 12.sp)
        Small(if (lang == Lang.KO) "EN" else "KO") { vm.setLang(if (lang == Lang.KO) Lang.EN else Lang.KO) }
        Small(if (audience == Audience.KIDS) "일반" else "키즈") { vm.setAudience(if (audience == Audience.KIDS) Audience.ADULT else Audience.KIDS) }
        if (sim != null) {
            val auto by vm.autoDemo.collectAsState()
            Small(if (auto) "⏸ 시연 중" else "▶ 시연") { vm.setAutoDemo(!auto) }
            Small("⏩ 빨리감기") { sim.fastForward(30) }
            Small("🚗 정체") { sim.triggerCongestion(15) }
            Small("🏁 도착·P") { sim.park() }
            Small("▶ D (재출발)") { sim.shiftToDrive() }
            Small("🧒 뒷좌석") { sim.setRearSeat(true) }
            Small("🪟 창밖") { vm.demoFreeRide() }
        }
        vm.gps?.let { g -> Small("🧒 뒷좌석") { g.rearSeat = !g.rearSeat }; Small("🪟 창밖") { vm.demoFreeRide() } }
        Small("🗺 일지") { vm.engine.cmdShowLog() }
        Small("📱 동승자") { onCompanion() }
        Small("⚙ 설정") { vm.openSettings(true) }
    }
}

@Composable
private fun Small(t: String, onClick: () -> Unit) {
    Text(t, Modifier.background(Chip, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontSize = 13.sp)
}


/** First run: three taps — language, who's riding, theme. Everything else lives in ⚙. */
@Composable
fun OnboardingScreen(prefs: kr.roaddocent.core.Prefs, onDone: (kr.roaddocent.core.Prefs) -> Unit) {
    var p by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(prefs) }
    val ko = p.lang == Lang.KO
    Panel(Modifier.fillMaxSize()) {
        Headline(if (ko) "30초면 끝나요" else "Thirty seconds to set up", 34)
        Spacer(Modifier.height(18.dp))
        Text(if (ko) "1. 언어" else "1. Language", color = Quiet, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("한국어", primary = p.lang == Lang.KO) { p = p.copy(lang = Lang.KO) }
            ActionButton("English", primary = p.lang == Lang.EN) { p = p.copy(lang = Lang.EN) }
        }
        Spacer(Modifier.height(14.dp))
        Text(if (ko) "2. 누가 타나요?" else "2. Who's riding?", color = Quiet, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(if (ko) "어른만" else "Adults", primary = p.audience == Audience.ADULT) { p = p.copy(audience = Audience.ADULT) }
            ActionButton(if (ko) "아이와 함께" else "With kids", primary = p.audience == Audience.KIDS) { p = p.copy(audience = Audience.KIDS) }
            ActionButton(if (ko) "한국 방문객 (렌터카)" else "Visiting Korea (rental)", primary = p.audience == Audience.FOREIGN) { p = p.copy(audience = Audience.FOREIGN, lang = Lang.EN) }
        }
        Spacer(Modifier.height(14.dp))
        Text(if (ko) "3. 오늘은 어떤 여행?" else "3. What kind of trip?", color = Quiet, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kr.roaddocent.core.Themes.order.filter { it != "car" }.forEach { t -> Pill(kr.roaddocent.core.Themes.label(t, p.lang), Modifier.clickable { p = p.copy(theme = t) }, selected = p.theme == t) }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(if (ko) "시작" else "Start", primary = true) { onDone(p) }
            Small(if (ko) "글자 크게" else "Larger text") { p = p.copy(fontScale = if (p.fontScale > 1.1f) 1.0f else 1.2f) }
        }
    }
}

/** QR for the rear-seat phone: scan once, the phone follows the story, quiz and stickers. */
@Composable
fun CompanionPanel(vm: DocentViewModel, lang: Lang, url: String?, onClose: () -> Unit) {
    val qr = androidx.compose.runtime.remember(url) { vm.companion.qr(520) }
    TwoPane() {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Headline(if (lang == Lang.KO) "동승자 화면" else "Passenger screen", 30)
            Spacer(Modifier.height(10.dp))
            Body(if (lang == Lang.KO) "뒷좌석 폰으로 QR을 한 번 찍으면 지금 이야기·퀴즈 카드·스티커·오늘의 한국어가 폰에 따라옵니다. 차 화면은 운전자용으로 최소만 남깁니다. (차 핫스팟 또는 같은 Wi-Fi)" else "Scan once from a rear-seat phone and it follows the story, quiz card, stickers and today's Korean phrases. The car screen stays minimal for the driver. (Car hotspot or same Wi-Fi)", size = 16)
            Spacer(Modifier.height(10.dp))
            Text(url ?: (if (lang == Lang.KO) "네트워크 없음 — 핫스팟을 켜 주세요" else "No network — turn on the hotspot"), color = Accent, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            ActionButton(if (lang == Lang.KO) "닫기" else "Close", primary = true, onClick = onClose)
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            if (qr != null) androidx.compose.foundation.Image(qr.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxWidth())
            else Body(if (lang == Lang.KO) "QR을 만들 수 없어요" else "Could not build the QR", size = 16)
        }
    }
}
