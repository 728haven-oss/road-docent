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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
import kr.roaddocent.ui.screen.TravelLogScreen
import kr.roaddocent.ui.screen.VoiceFamilyScreen
import kr.roaddocent.ui.screen.WhereNextScreen
import kr.roaddocent.ui.theme.Bg
import kr.roaddocent.ui.theme.Body
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.Pill
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.RoadDocentTheme

class MainActivity : ComponentActivity() {
    private val vm: DocentViewModel by viewModels()

    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mic.launch(Manifest.permission.RECORD_AUDIO)
        setContent { RoadDocentTheme { Root(vm) { number -> dial(number) } } }
    }

    private fun dial(number: String) {
        // Bluetooth-connected phone picks this up on Pleos Connect; ACTION_DIAL needs no runtime permission.
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
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
            ActionButton("시뮬레이션 · 서울→강릉 데모") { onPick(Source.SIMULATOR) }
        }
        Spacer(Modifier.height(18.dp))
        Body("Pleos 연결은 Pleos Connect AVD 또는 실차에서 동작합니다. 시뮬레이션은 어떤 Android 기기에서도 전체 흐름을 보여줍니다.", size = 13)
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

    val voiceSetup by vm.voiceSetup.collectAsState()
    val voiceProfile by vm.voiceProfile.collectAsState()
    val recording by vm.recorder.recording.collectAsState()
    LaunchedEffect(toast) { if (toast != null) { delay(3000); engine.clearToast() } }

    if (voiceSetup) {
        Box(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
            VoiceFamilyScreen(lang, recording, vm.cloudAvailable, onStart = vm::startSample, onStop = vm::stopSample, onBuild = vm::buildVoice, onClose = { vm.openVoiceSetup(false) })
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (lang == Lang.KO) "로드 도슨트" else "Road Docent", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (nav.active) Text("  · ${nav.destinationName}${if (lang == Lang.KO) "까지 " else " in "}${nav.remainTimeSec / 60}${if (lang == Lang.KO) "분" else " min"}", color = Quiet, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            toast?.let { Pill(it); Spacer(Modifier.width(8.dp)) }
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
                        played = p.playedIds, lang = lang, audience = audience, congestion = p.congestion, paused = paused, speaking = speaking,
                        onSkip = engine::cmdSkip, onMore = engine::cmdMore, onSave = engine::cmdSave, onQuiet = engine::cmdQuiet
                    )
                }
                is DocentPhase.Briefing -> BriefingScreen(p.place, p.alternatives, lang, nav.remainTimeSec / 60,
                    onReserve = { engine.cmdReserve()?.phone?.let(onDial) }, onOther = { engine.cmdSkip() })
                is DocentPhase.Parked -> ParkedScreen(p.place, lang) { vm.openVoiceSetup(true) }
                is DocentPhase.WhereNext -> WhereNextScreen(p.leftAfterMin, p.suggestions, p.disappointed, lang, onGo = vm::goThere, onHome = engine::cmdShowLog)
                is DocentPhase.TravelLog -> TravelLogScreen(log, lang) { engine.cmdShowLog() }
            }
        }

        if (audience == Audience.KIDS && phase is DocentPhase.Guiding) { Spacer(Modifier.height(12.dp)); KidsPanel(lang, log.quizzesRight) }

        Spacer(Modifier.height(12.dp))
        DemoBar(vm, lang, audience)
    }
}

@Composable
fun IdleScreen(lang: Lang, vm: DocentViewModel) {
    Panel(Modifier.fillMaxSize()) {
        Headline(if (lang == Lang.KO) "목적지를 설정하면 가이드가 탑승합니다" else "Set a destination and your guide boards", 28)
        Spacer(Modifier.height(10.dp))
        Body(if (lang == Lang.KO) "Pleos 연결 시 차량 내비에서 목적지를 설정하세요. 데모에서는 아래 버튼으로 서울→강릉 경로를 시작합니다." else "With Pleos, set a destination in the built-in navigation. In the demo, start the Seoul → Gangneung route below.", size = 16)
        Spacer(Modifier.height(20.dp))
        ActionButton(if (lang == Lang.KO) "🎙 가족 목소리 녹음 (엄마·아빠·아이)" else "🎙 Record a family voice") { vm.openVoiceSetup(true) }
        Spacer(Modifier.height(16.dp))
        Text(if (lang == Lang.KO) "데모 목적지" else "Demo destinations", color = Quiet, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.guides().forEach { g ->
                ActionButton((g.startName[lang.tag] ?: "") + " → " + (g.destination.name[lang.tag] ?: g.id), primary = g.id == "seoul_gangneung") { vm.demoStartTrip(g.id) }
            }
        }
    }
}

@Composable
fun ParkedScreen(place: kr.roaddocent.core.Place, lang: Lang, onVoice: () -> Unit = {}) {
    val name = place.name[lang.tag] ?: ""
    val steps = place.onArrival[lang.tag].orEmpty()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Headline(if (lang == Lang.KO) "$name 도착" else "Arrived: $name", 28)
            Spacer(Modifier.height(10.dp))
            Body(place.note[lang.tag] ?: "", size = 16)
            Spacer(Modifier.height(10.dp))
            Body(if (lang == Lang.KO) "다시 출발하시면(기어 D) 다음 행선지를 제안해 드릴게요." else "When you set off again (gear D) I'll suggest where next.", size = 14)
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
fun DemoBar(vm: DocentViewModel, lang: Lang, audience: Audience) {
    val sim = vm.sim
    Row(Modifier.fillMaxWidth().background(Chip.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (sim != null) "DEMO" else "PLEOS", color = Quiet, fontSize = 12.sp)
        Small(if (lang == Lang.KO) "EN" else "KO") { vm.setLang(if (lang == Lang.KO) Lang.EN else Lang.KO) }
        Small(if (audience == Audience.KIDS) "일반" else "키즈") { vm.setAudience(if (audience == Audience.KIDS) Audience.ADULT else Audience.KIDS) }
        if (sim != null) {
            Small("⏩ 빨리감기") { sim.fastForward(30) }
            Small("🚗 정체") { sim.triggerCongestion(15) }
            Small("🏁 도착·P") { sim.park() }
            Small("▶ D (재출발)") { sim.shiftToDrive() }
            Small("🧒 뒷좌석") { sim.setRearSeat(true) }
        }
        Small("🗺 일지") { vm.engine.cmdShowLog() }
    }
}

@Composable
private fun Small(t: String, onClick: () -> Unit) {
    Text(t, Modifier.background(Chip, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontSize = 13.sp)
}
