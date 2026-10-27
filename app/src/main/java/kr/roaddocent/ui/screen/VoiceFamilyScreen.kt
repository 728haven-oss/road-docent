package kr.roaddocent.ui.screen

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
import kr.roaddocent.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.roaddocent.core.Lang
import kr.roaddocent.speech.VoiceSamples
import kr.roaddocent.ui.theme.Accent
import kr.roaddocent.ui.theme.Body
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.TwoPane
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.Tag

/**
 * Voice Family setup: who is recording → consent → 15 sample sentences → build voice.
 * Only usable while parked (gear P); the engine blocks it while driving.
 */
@Composable
fun VoiceFamilyScreen(
    lang: Lang,
    recording: Boolean,
    cloudAvailable: Boolean,
    error: String? = null,
    onStart: (index: Int) -> Unit,
    onStop: () -> Unit,
    onBuild: (name: String, isChild: Boolean, guardianConsent: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val ko = lang == Lang.KO
    val samples = VoiceSamples.forLang(lang)
    var who by remember { mutableStateOf("mom") }
    var consent by remember { mutableStateOf(false) }
    var guardian by remember { mutableStateOf(false) }
    var idx by remember { mutableIntStateOf(0) }
    var done by remember { mutableIntStateOf(0) }
    val isChild = who == "child"
    val name = when (who) { "mom" -> if (ko) "엄마" else "Mum"; "dad" -> if (ko) "아빠" else "Dad"; else -> if (ko) "아이" else "Child" }

    TwoPane() {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Headline(if (ko) "가족 목소리 가이드" else "Voice Family", 26)
            Body(if (ko) "녹음한 목소리로 가이드가 이야기를 읽어줘요. 아빠가 운전해도 엄마 목소리로." else "The guide reads every story in a recorded voice. Dad drives, Mum narrates.", size = 15)
            Spacer(Modifier.height(16.dp))
            Text(if (ko) "누가 녹음하나요?" else "Who is recording?", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            kr.roaddocent.ui.theme.ChipRow {
                listOf("mom" to (if (ko) "엄마" else "Mum"), "dad" to (if (ko) "아빠" else "Dad"), "child" to (if (ko) "아이 (보호자 동의)" else "Child (guardian consent)")).forEach { (k, l) ->
                    Text(l, Modifier.background(if (who == k) Accent else Chip, RoundedCornerShape(10.dp)).clickable { who = k }.padding(horizontal = 14.dp, vertical = 8.dp), color = if (who == k) androidx.compose.ui.graphics.Color.White else Ink, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            CheckLine(consent, if (ko) "내 목소리를 로드 도슨트 가이드 음성으로만 쓰는 데 동의해요. 언제든 삭제할 수 있어요." else "I agree my voice is used only as the Road Docent guide voice. I can delete it anytime.") { consent = !consent }
            if (isChild) CheckLine(guardian, if (ko) "보호자로서 14세 미만 아이의 목소리 사용에 동의해요." else "As guardian, I consent for a child under 14.") { guardian = !guardian }
            Spacer(Modifier.height(12.dp))
            Body(if (ko) "원본 녹음은 목소리 모델이 만들어지면 삭제돼요. 목소리는 가이드 대본에만 쓰이고, 생성 음성에는 워터마크가 들어가요." else "Raw recordings are deleted once the voice model exists. The voice is used only for guide scripts and generated audio is watermarked.", size = 12)
            Spacer(Modifier.weight(1f))
            kr.roaddocent.ui.theme.ButtonFlow {
                ActionButton(if (ko) "닫기" else "Close", onClick = onClose)
                if (done >= samples.size) ActionButton(if (ko) "목소리 만들기" else "Build voice", primary = true) { onBuild(name, isChild, guardian) }
            }
        }
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(if (ko) "문장 ${idx + 1} / ${samples.size}" else "Sentence ${idx + 1} / ${samples.size}")
                Spacer(Modifier.width(8.dp))
                if (idx == 0) Tag(if (ko) "본인 확인 문장" else "Consent line")
                Spacer(Modifier.weight(1f))
                Text(if (cloudAvailable) (if (ko) "음성 복제 연결됨" else "Cloning connected") else (if (ko) "API 키 없음 · 기기 TTS로 재생" else "No API key · on-device TTS"), color = Quiet, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("“${samples[idx]}”", color = Ink, fontSize = 24.sp, lineHeight = 34.sp)
            Spacer(Modifier.height(20.dp))
            Wave(active = recording)
            Spacer(Modifier.weight(1f))
            val canRecord = consent && (!isChild || guardian)
            var hint by remember { mutableStateOf<String?>(null) }
            (error ?: hint)?.let { Body(it, color = Accent, size = 14); Spacer(Modifier.height(6.dp)) }
            kr.roaddocent.ui.theme.ButtonFlow {
                if (!recording) ActionButton(if (ko) "🎙 녹음 시작" else "🎙 Record", primary = canRecord) { if (canRecord) onStart(idx) else hint = if (ko) "위의 동의 항목에 체크하면 녹음 버튼이 켜져요." else "Tick the consent box above to enable recording." }
                else ActionButton(if (ko) "■ 녹음 끝" else "■ Stop", primary = true) { onStop(); done = maxOf(done, idx + 1); if (idx < samples.size - 1) idx++ }
                if (idx > 0 && !recording) ActionButton(if (ko) "이전" else "Back") { idx-- }
                if (idx < done && idx < samples.size - 1 && !recording) ActionButton(if (ko) "다음" else "Next") { idx++ }
            }
            Spacer(Modifier.height(8.dp))
            Body(if (ko) "조용한 곳에서, 평소 아이에게 말하듯 읽어주세요. 15문장이면 약 2분이에요." else "Somewhere quiet, read as you'd talk to your child. 15 sentences take about 2 minutes.", size = 12)
        }
    }
}

@Composable
private fun CheckLine(checked: Boolean, text: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp).height(22.dp).background(if (checked) Accent else Chip, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { if (checked) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp) }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
