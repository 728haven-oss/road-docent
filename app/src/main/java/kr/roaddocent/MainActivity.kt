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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import kr.roaddocent.ui.theme.Text
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
    val user by vm.user.collectAsState()
    val authError by vm.authError.collectAsState()
    val authBusy by vm.authBusy.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var termsOk by androidx.compose.runtime.remember(user) { androidx.compose.runtime.mutableStateOf(!vm.termsNeeded) }
    val pad = Modifier.fillMaxSize().padding(horizontal = if (kr.roaddocent.ui.theme.isCompact()) 12.dp else 36.dp, vertical = if (kr.roaddocent.ui.theme.isCompact()) 10.dp else 22.dp)
    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            user == null -> Box(pad) { kr.roaddocent.ui.screen.LoginScreen(authError, vm.googleReady, authBusy, onLocal = vm::signInLocal, onGoogle = { vm.signInGoogle(ctx) }) }
            !termsOk -> Box(pad) { kr.roaddocent.ui.screen.TermsScreen(vm.termsText(), onAccept = { vm.acceptTerms(); termsOk = true }, onDecline = { vm.signOut() }) }
            source == null -> ConnectScreen(user!!.name, onPick = { vm.connect(it) }, onSignOut = { vm.signOut() })
            else -> Shell(vm, onDial)
        }
    }
}

@Composable
fun ConnectScreen(userName: String, onPick: (Source) -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(if (kr.roaddocent.ui.theme.isCompact()) 20.dp else 44.dp), verticalArrangement = Arrangement.Center) {
        Headline("로드 도슨트 · Road Docent", 36)
        Spacer(Modifier.height(8.dp))
        Body("$userName 님, 어떤 차에 연결할까요?", size = 18)
        Spacer(Modifier.height(32.dp))
        if (kr.roaddocent.ui.theme.isCompact()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("시뮬레이션 · 데모", primary = true) { onPick(Source.SIMULATOR) }
            ActionButton("📍 휴대폰 GPS · 실제 위치") { onPick(Source.PHONE_GPS) }
            ActionButton("Pleos 차량 연결 (차량·AVD에서만)") { onPick(Source.PLEOS) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionButton("Pleos 차량 연결 (NaviHelper · Vehicle SDK)", primary = true) { onPick(Source.PLEOS) }
            ActionButton("시뮬레이션 · 데모") { onPick(Source.SIMULATOR) }
            ActionButton("📍 휴대폰 GPS · 실제 위치") { onPick(Source.PHONE_GPS) }
        }
        Spacer(Modifier.height(18.dp))
        Body("Pleos 연결은 Pleos Connect AVD 또는 실차에서 동작합니다. 시뮬레이션은 어떤 Android 기기에서도 전체 흐름을 보여줍니다. 휴대폰 GPS는 실제 위치로 길목 해설·도착·재탑승이 일어납니다 (목적지를 고르거나 🪟 창밖으로 그냥 달리기).", size = 13)
        Spacer(Modifier.height(14.dp))
        Row { Small("로그아웃") { onSignOut() } }
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
    val kidsQuiz by engine.kidsQuiz.collectAsState()
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
                onVoiceSetup = { vm.openSettings(false); vm.openVoiceSetup(true, fromSettings = true) }, onDeleteVoice = vm::deleteVoice, onClose = { vm.openSettings(false) })
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
        // top bar: back · title + where-we-are · mic · menu
        var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        var demoPanel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon("←") { vm.back() }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (lang == Lang.KO) "로드 도슨트" else "Road Docent", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val sub = when {
                    toast != null -> toast!!
                    nav.active -> "📍 " + nearLabel(engine.guide, nav.location, lang) + "  →  ${nav.destinationName} · ${nav.remainTimeSec / 60}${if (lang == Lang.KO) "분" else " min"}"
                    heard.isNotBlank() -> "🎤 “$heard”"
                    else -> (if (audience == Audience.KIDS) (if (lang == Lang.KO) "🧒 키즈 모드" else "🧒 Kids mode") else "") + (voiceProfile?.let { "  🎙 ${it.name}" } ?: "")
                }
                if (sub.isNotBlank()) Text(sub, color = if (toast != null) kr.roaddocent.ui.theme.Navy else Quiet, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (prefs.localVoiceCommands && vm.localVoice.available) { RoundIcon(if (listening) "🎙" else "🎤", active = listening) { vm.listen() }; Spacer(Modifier.width(8.dp)) }
            Box {
                RoundIcon("⋮") { menu = true }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = androidx.compose.ui.graphics.Color.White) {
                    @Composable fun item(t: String, f: () -> Unit) = androidx.compose.material3.DropdownMenuItem(text = { Text(t, color = Ink, fontSize = 15.sp) }, onClick = { menu = false; f() })
                    item(if (lang == Lang.KO) "🗺  오늘의 여행 일지" else "🗺  Today's log") { engine.cmdShowLog() }
                    item(if (lang == Lang.KO) "📱  동승자 화면 (QR)" else "📱  Passenger screen") { vm.startCompanion(); companionOpen = true }
                    item(if (audience == Audience.KIDS) (if (lang == Lang.KO) "🧑  어른 모드로" else "🧑  Adult mode") else (if (lang == Lang.KO) "🧒  키즈 모드로" else "🧒  Kids mode")) { vm.setAudience(if (audience == Audience.KIDS) Audience.ADULT else Audience.KIDS) }
                    if (audience == Audience.KIDS) item(if (lang == Lang.KO) "❓  창밖 퀴즈 내기" else "❓  Ask a window quiz") { engine.askKidsQuiz() }
                    item(if (lang == Lang.KO) "🌐  English" else "🌐  한국어") { vm.setLang(if (lang == Lang.KO) Lang.EN else Lang.KO) }
                    item(if (lang == Lang.KO) "⚙  설정" else "⚙  Settings") { vm.openSettings(true) }
                    if (vm.sim != null || vm.gps != null) item(if (lang == Lang.KO) "🎬  시연 컨트롤" else "🎬  Demo controls") { demoPanel = true }
                    item(if (lang == Lang.KO) "🚪  로그아웃" else "🚪  Sign out") { vm.signOut() }
                }
            }
        }
        if (demoPanel) androidx.compose.ui.window.Dialog(onDismissRequest = { demoPanel = false }) { DemoPanel(vm, lang) { demoPanel = false } }
        Spacer(Modifier.height(16.dp))

        val tripIntro by vm.tripIntro.collectAsState()
        Box(Modifier.weight(1f)) {
            val intro = tripIntro
            if (intro != null) { TripIntroScene(intro, lang, nav.location); return@Box }
            when (val p = phase) {
                is DocentPhase.Idle -> IdleScreen(lang, vm)
                is DocentPhase.Guiding -> Column {
                    engine.guide?.let { g -> if (!kr.roaddocent.ui.theme.isCompact() || vehicle.gearPark) { TripMapStrip(g, nav.location, lang); Spacer(Modifier.height(10.dp)) } }
                    PlayingScreen(
                        ep = p.episode, all = engine.guide?.episodes?.filter { it.trigger !is kr.roaddocent.core.Trigger.Reboard } ?: emptyList(),
                        played = p.playedIds, lang = lang, audience = audience, congestion = p.congestion, paused = paused, speaking = speaking, quiz = quiz,
                        driving = !vehicle.gearPark && vehicle.speedKmh > 5f && !prefs.alwaysFull && !kr.roaddocent.ui.theme.isCompact(),
                        onSkip = engine::cmdSkip, onMore = engine::cmdMore, onSave = engine::cmdSave, onQuiet = engine::cmdQuiet,
                        onFeedback = engine::feedback
                    )
                }
                is DocentPhase.Briefing -> BriefingScreen(p.place, p.alternatives, lang, nav.remainTimeSec / 60,
                    onReserve = { engine.cmdReserve()?.phone?.let(onDial) }, onOther = { engine.cmdWhereNext() })
                is DocentPhase.Parked -> ParkedScreen(p.place, lang, engine.guide?.handoff, onOpen = onDial, onNext = { engine.cmdWhereNext() }, onHear = { engine.sayNow(it) }) { vm.openVoiceSetup(true) }
                is DocentPhase.WhereNext -> WhereNextScreen(p.leftAfterMin, p.suggestions, p.disappointed, lang, onGo = vm::goThere, onHome = engine::cmdShowLog)
                is DocentPhase.TravelLog -> TravelLogScreen(log, lang, vm.history.days(), onShare = { vm.shareToday() }) { engine.cmdShowLog() }
            }
        }

        val hintCtx = when (phase) { is DocentPhase.Briefing -> "briefing"; is DocentPhase.WhereNext -> "wherenext"; is DocentPhase.Parked -> "parked"; else -> null }
        if (hintCtx != null) { Spacer(Modifier.height(8.dp)); kr.roaddocent.ui.screen.VoiceHint(lang, hintCtx) }
        kidsQuiz?.let { kq -> Spacer(Modifier.height(10.dp)); kr.roaddocent.ui.screen.KidsQuizCard(kq, lang, onAnswer = engine::answerKidsQuiz) }
        if (audience == Audience.KIDS && kidsQuiz == null && phase is DocentPhase.Guiding && vehicle.gearPark) { Spacer(Modifier.height(12.dp)); KidsPanel(lang, log.quizzesRight) }
        val courses = engine.guide?.creatorCourses.orEmpty()
        if (courses.isNotEmpty() && !kr.roaddocent.ui.theme.isCompact() && (phase is DocentPhase.Briefing || phase is DocentPhase.WhereNext)) {
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


    }
}

@Composable
fun IdleScreen(lang: Lang, vm: DocentViewModel) {
    val prefs by vm.settings.prefs.collectAsState()
    val user by vm.user.collectAsState()
    val guides = androidx.compose.runtime.remember { vm.guides() }
    val resume = vm.resumable()?.let { (gid, ago) -> guides.firstOrNull { it.id == gid }?.let { it to ago } }
    val ko = lang == Lang.KO
    val modeLine = when {
        vm.sim != null -> if (ko) "시연 모드 · 코스를 고르면 내비가 설정되고 가이드가 탑승해요. 이야기가 나오는 동안은 시간이 멈춰요." else "Demo · pick a course; time pauses while each story plays."
        vm.gps != null -> if (ko) "휴대폰 GPS · 실제 위치로 길목 해설이 나와요" else "Phone GPS · stories follow your real position"
        else -> if (ko) "Pleos · 차량 내비에서 목적지를 설정하면 자동으로 시작돼요" else "Pleos · set a destination in the car's navigation"
    }
    kr.roaddocent.ui.screen.HomeScreen(user?.name ?: "", lang, modeLine, guides, prefs.theme, resume,
        onTheme = vm::setTheme, onStart = { vm.demoStartTrip(it.id) }, onNearby = { vm.playNearbyNow() },
        onResume = { resume?.let { vm.demoStartTrip(it.first.id) } }, onDismissResume = { vm.clearResume() })
}

@Composable
fun ParkedScreen(place: kr.roaddocent.core.Place, lang: Lang, handoff: kr.roaddocent.core.Handoff? = null, onOpen: (String) -> Unit = {}, onNext: () -> Unit = {}, onHear: (String) -> Unit = {}, onVoice: () -> Unit = {}) {
    val ko = lang == Lang.KO
    val name = place.name[lang.tag] ?: ""
    val lines = place.onArrival[lang.tag].orEmpty()
    val items = lines.map { kr.roaddocent.ui.screen.arrivalItem(it) }
    val tip = kr.roaddocent.ui.screen.arrivalHighlight(lines)
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    TwoPane() {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            kr.roaddocent.ui.theme.HeroBand {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        kr.roaddocent.ui.theme.Tag(if (ko) "🏁 도착" else "🏁 Arrived", color = Color(0x33FFFFFF), ink = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(place.note[lang.tag] ?: "", color = Color(0xDDFFFFFF), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Text(kr.roaddocent.ui.screen.pictogram(place.category), fontSize = 52.sp)
                }
            }
            if (tip.isNotBlank()) Column(Modifier.fillMaxWidth().background(kr.roaddocent.ui.theme.WarmSoft, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Text(if (ko) "💡 하나만 기억하세요" else "💡 If you remember one thing", color = kr.roaddocent.ui.theme.Warm, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(tip, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
            }
            ActionButtonWide(if (ko) "다음은 어디로 갈까요?" else "Where next?", primary = true) { onNext() }
            if (handoff != null) ActionButtonWide("📱 " + (handoff.label[lang.tag] ?: "")) { onOpen(handoff.url) }
            Text(if (ko) "🎙 가족 목소리로 듣기 설정" else "🎙 Set up a family voice", Modifier.clickable { onVoice() }.padding(vertical = 4.dp), color = Quiet, fontSize = 13.sp)
        }
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            kr.roaddocent.ui.theme.SectionTitle(if (ko) "여기서 할 일" else "What to do here")
            kr.roaddocent.ui.screen.Grid(items.withIndex().toList(), columns = 2) { (i, it) ->
                val (icon, label, body) = it
                val expanded = open == i
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).border(1.dp, if (expanded) kr.roaddocent.ui.theme.Navy else kr.roaddocent.ui.theme.CardBorder, RoundedCornerShape(14.dp))
                    .clickable { open = if (expanded) null else i; if (!expanded) onHear(label + ". " + body) }.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 20.sp); Spacer(Modifier.width(6.dp)); Text(label, color = kr.roaddocent.ui.theme.Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Spacer(Modifier.height(4.dp))
                    Text(body, color = Ink, fontSize = 13.sp, lineHeight = 19.sp, maxLines = if (expanded) 12 else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Text(if (ko) "카드를 누르면 펼쳐지고 읽어 드려요" else "Tap a card to expand and hear it", color = Quiet, fontSize = 12.sp)
        }
    }
}

/** Full-width button (phone primary actions). */
@Composable
fun ActionButtonWide(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(text, Modifier.fillMaxWidth().background(if (primary) kr.roaddocent.ui.theme.Navy else Color.White, shape).border(1.dp, if (primary) kr.roaddocent.ui.theme.Navy else kr.roaddocent.ui.theme.CardBorder, shape)
        .clickable(onClick = onClick).padding(vertical = 14.dp), color = if (primary) Color.White else kr.roaddocent.ui.theme.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
}

/** Demo controls in a dialog (⋮ → 시연 컨트롤): stand-ins for real vehicle signals. */
@Composable
fun DemoPanel(vm: DocentViewModel, lang: Lang, onClose: () -> Unit) {
    val sim = vm.sim
    val engine = vm.engine
    val auto by vm.autoDemo.collectAsState()
    val v by engine.vehicleState.collectAsState()
    val ko = lang == Lang.KO
    Panel(Modifier.fillMaxWidth()) {
        kr.roaddocent.ui.theme.SectionTitle(if (ko) "🎬 시연 컨트롤" else "🎬 Demo controls")
        Text(if (ko) "실제 차량에서는 내비·차량 신호가 이 역할을 해요" else "In a real car the navigation and vehicle signals do this", color = Quiet, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        val items = buildList<Triple<String, String, () -> Unit>> {
            if (sim != null) {
                add(Triple(if (auto) "⏸" else "▶", if (ko) (if (auto) "자동 시연 중" else "자동 시연") else "Auto demo") { vm.setAutoDemo(!auto) })
                add(Triple("⏩", if (ko) "30분 앞으로" else "+30 min") { sim.fastForward(30) })
                add(Triple("🚗", if (ko) "정체 발생" else "Traffic jam") { sim.triggerCongestion(15) })
                add(Triple("🏁", if (ko) "도착·주차" else "Arrive · P") { sim.park(); onClose() })
                add(Triple("🔄", if (ko) "다시 출발" else "Drive again") { sim.shiftToDrive(); onClose() })
                add(Triple("🧒", if (ko) (if (v.rearSeatOccupied) "뒷좌석 비움" else "뒷좌석 탑승") else "Rear seat") { sim.setRearSeat(!v.rearSeatOccupied) })
            }
            vm.gps?.let { g -> add(Triple("🧒", if (ko) "뒷좌석 탑승" else "Rear seat") { g.rearSeat = !g.rearSeat }) }
            add(Triple("🪟", if (ko) "목적지 없이 달리기" else "Free ride") { vm.demoFreeRide(); onClose() })
            if (sim != null && vm.settings.prefs.value.newCarMode) add(Triple("🔌", if (ko) "충전 시작" else "Plug in") { sim.plugIn(true) })
        }
        kr.roaddocent.ui.screen.Grid(items, columns = 3, gap = 8) { (i, t, f) -> kr.roaddocent.ui.screen.PictureTile(i, t) { f() } }
        Spacer(Modifier.height(12.dp))
        ActionButtonWide(if (ko) "닫기" else "Close") { onClose() }
    }
}

@Composable
private fun RoundIcon(icon: String, active: Boolean = false, onClick: () -> Unit) =
    Box(Modifier.size(40.dp).background(if (active) kr.roaddocent.ui.theme.Navy else androidx.compose.ui.graphics.Color.White, RoundedCornerShape(999.dp))
        .border(1.dp, kr.roaddocent.ui.theme.CardBorder, RoundedCornerShape(999.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(icon, color = if (active) androidx.compose.ui.graphics.Color.White else Ink, fontSize = 18.sp)
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
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Headline(if (ko) "어떤 여행인가요?" else "What kind of trip?", 26)
        Text(if (ko) "세 가지만 고르면 가이드가 맞춰 드려요" else "Three picks and the guide adapts", color = Quiet, fontSize = 14.sp)
        Step(1, if (ko) "언어" else "Language")
        kr.roaddocent.ui.screen.Grid(listOf(Lang.KO to "🇰🇷 한국어", Lang.EN to "🇺🇸 English"), columns = 2) { (l, t) ->
            kr.roaddocent.ui.screen.PictureTile(t.take(4).trim(), t.drop(4).trim(), selected = p.lang == l) { p = p.copy(lang = l) }
        }
        Step(2, if (ko) "누가 타나요?" else "Who's riding?")
        kr.roaddocent.ui.screen.Grid(listOf(Triple(Audience.ADULT, "🧑", if (ko) "어른만" else "Adults"), Triple(Audience.KIDS, "🧒", if (ko) "아이와 함께" else "With kids"), Triple(Audience.FOREIGN, "🌏", if (ko) "외국인 관광객" else "Visitor")), columns = 3) { (a, i, t) ->
            kr.roaddocent.ui.screen.PictureTile(i, t, selected = p.audience == a) { p = p.copy(audience = a, lang = if (a == Audience.FOREIGN) Lang.EN else p.lang) }
        }
        Step(3, if (ko) "좋아하는 여행" else "What you enjoy")
        kr.roaddocent.ui.screen.Grid(kr.roaddocent.core.Themes.order.filter { it != "car" }, columns = 3) { t ->
            val label = kr.roaddocent.core.Themes.label(t, p.lang)
            kr.roaddocent.ui.screen.PictureTile(label.substringBefore(" "), label.substringAfter(" "), selected = p.theme == t) { p = p.copy(theme = if (p.theme == t) "" else t) }
        }
        Row(Modifier.fillMaxWidth().clickable { p = p.copy(fontScale = if (p.fontScale > 1.1f) 1.0f else 1.2f) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.fontScale > 1.1f) "☑" else "☐", fontSize = 18.sp, color = kr.roaddocent.ui.theme.Navy); Spacer(Modifier.width(8.dp)); Text(if (ko) "글자를 크게 볼래요" else "Larger text", color = Ink, fontSize = 14.sp)
        }
        ActionButtonWide(if (ko) "시작하기" else "Start", primary = true) { onDone(p) }
    }
}

@Composable
private fun Step(n: Int, title: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text("$n", Modifier.background(kr.roaddocent.ui.theme.Navy, RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 2.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.width(8.dp)); kr.roaddocent.ui.theme.SectionTitle(title)
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


/** "Where we are now": the nearest story point within 6 km, else the start/destination name by progress. */
fun nearLabel(g: kr.roaddocent.core.Guide?, here: kr.roaddocent.core.GeoPoint?, lang: Lang): String {
    g ?: return ""; here ?: return g.startName[lang.tag] ?: ""
    fun d(a: kr.roaddocent.core.GeoPoint, b: kr.roaddocent.core.GeoPoint): Double { val dl = Math.toRadians(b.lat - a.lat); val dn = Math.toRadians(b.lon - a.lon); val h = Math.sin(dl / 2) * Math.sin(dl / 2) + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dn / 2) * Math.sin(dn / 2); return 2 * 6371000 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)) }
    val pts = g.episodes.mapNotNull { e -> (e.trigger as? kr.roaddocent.core.Trigger.NearPoint)?.let { it.point to e.titleFor(lang).substringBefore(" ·").substringBefore(":") } }
    val near = pts.minByOrNull { d(it.first, here) }
    if (near != null && d(near.first, here) < 6000) return (if (lang == Lang.KO) "${near.second} 부근" else "near ${near.second}")
    val toDest = d(here, g.destination.point); val total = g.route.firstOrNull()?.let { d(it, g.destination.point) } ?: 1.0
    return if (toDest / total > 0.85) (g.startName[lang.tag] ?: "") else if (lang == Lang.KO) "이동 중" else "en route"
}

/** 3-second scene: the navigation accepts the destination, the map shows where we are and where we go. */
@Composable
fun TripIntroScene(g: kr.roaddocent.core.Guide, lang: Lang, here: kr.roaddocent.core.GeoPoint?) {
    Panel(Modifier.fillMaxWidth()) {
        Text(if (lang == Lang.KO) "🧭 내비게이션 목적지 설정" else "🧭 Navigation destination set", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Headline((g.startName[lang.tag] ?: "") + "  →  " + (g.destination.name[lang.tag] ?: g.id), 26)
        Text(if (lang == Lang.KO) "현재 위치: ${g.startName["ko"]} · 약 ${g.driveMin}분 · 가이드가 탑승합니다…" else "Current location: ${g.startName["en"]} · about ${g.driveMin} min · your guide is boarding…", color = Quiet, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        val pts = g.episodes.mapNotNull { e -> (e.trigger as? kr.roaddocent.core.Trigger.NearPoint)?.let { it.point to e.titleFor(lang) } }
        kr.roaddocent.ui.screen.TripMap(g.route, here ?: g.route.firstOrNull(), pts, g.startName[lang.tag] ?: "", g.destination.name[lang.tag] ?: "", Modifier.fillMaxWidth().height(260.dp))
        Spacer(Modifier.height(8.dp))
        Text(if (lang == Lang.KO) "가는 길 이야기 ${pts.size}곳" else "${pts.size} stories on the way", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        kr.roaddocent.ui.theme.ChipRow { pts.forEachIndexed { i, (_, t) -> kr.roaddocent.ui.theme.Tag("${i + 1}  " + kr.roaddocent.ui.screen.stopName(t)) } }
        Spacer(Modifier.height(10.dp))
        kr.roaddocent.ui.screen.Hero(g.destination.poiId.takeIf { it.endsWith(".jpg") || it.endsWith(".webp") }, g.destination.category, g.destination.name[lang.tag] ?: "", height = 110)
    }
}

/** Compact map strip above the story: route, story dots, the car. */
@Composable
fun TripMapStrip(g: kr.roaddocent.core.Guide, here: kr.roaddocent.core.GeoPoint?, lang: Lang) {
    val pts = g.episodes.mapNotNull { e -> (e.trigger as? kr.roaddocent.core.Trigger.NearPoint)?.let { it.point to e.titleFor(lang) } }
    if (g.route.size >= 2) kr.roaddocent.ui.screen.TripMap(g.route, here, pts, g.startName[lang.tag] ?: "", g.destination.name[lang.tag] ?: "", Modifier.fillMaxWidth().height(if (kr.roaddocent.ui.theme.isCompact()) 120.dp else 150.dp))
}
