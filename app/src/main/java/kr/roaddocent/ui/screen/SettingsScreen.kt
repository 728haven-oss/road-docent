package kr.roaddocent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.roaddocent.core.Audience
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Prefs
import kr.roaddocent.speech.VoiceProfile
import kr.roaddocent.ui.theme.Accent
import kr.roaddocent.ui.theme.Body
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.Quiet

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) =
    Text(label, Modifier.background(if (on) Accent else Chip, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), color = if (on) Color(0xFF06111F) else Ink, fontSize = 14.sp)

@Composable
private fun Label(t: String) = Text(t, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))

@Composable
fun SettingsScreen(
    prefs: Prefs, voice: VoiceProfile?, localVoiceAvailable: Boolean,
    onChange: (Prefs) -> Unit, onVoiceSetup: () -> Unit, onDeleteVoice: () -> Unit, onClose: () -> Unit
) {
    val ko = prefs.lang == Lang.KO
    var key by remember { mutableStateOf(prefs.voiceApiKey) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Headline(if (ko) "설정" else "Settings", 26)
            Label(if (ko) "가이드 언어" else "Guide language")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("한국어", prefs.lang == Lang.KO) { onChange(prefs.copy(lang = Lang.KO)) }
                Choice("English", prefs.lang == Lang.EN) { onChange(prefs.copy(lang = Lang.EN)) }
            }
            Label(if (ko) "탑승자" else "Passengers")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(if (ko) "일반" else "Adults", prefs.audience == Audience.ADULT) { onChange(prefs.copy(audience = Audience.ADULT)) }
                Choice(if (ko) "아이와 함께" else "With kids", prefs.audience == Audience.KIDS) { onChange(prefs.copy(audience = Audience.KIDS)) }
                Choice(if (ko) "외국인" else "Visitor", prefs.audience == Audience.FOREIGN) { onChange(prefs.copy(audience = Audience.FOREIGN)) }
            }
            if (prefs.audience == Audience.KIDS) {
                Label(if (ko) "아이 나이 (난이도)" else "Child's age (difficulty)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(if (ko) "5~7 노래·짧은 이야기" else "5–7 songs", prefs.childAge <= 7) { onChange(prefs.copy(childAge = 6)) }
                    Choice(if (ko) "8~10 퀴즈·역사" else "8–10 quizzes", prefs.childAge in 8..10) { onChange(prefs.copy(childAge = 9)) }
                    Choice(if (ko) "11~13 토론형" else "11–13 discussion", prefs.childAge >= 11) { onChange(prefs.copy(childAge = 12)) }
                }
            }
            Label(if (ko) "안전" else "Safety")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(80, 100, 110).forEach { v -> Choice(if (ko) "${v}km/h 이상 멈춤" else "pause over $v km/h", prefs.safetyPauseKmh == v) { onChange(prefs.copy(safetyPauseKmh = v)) } }
            }
            Label(if (ko) "데이터" else "Data")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(if (ko) "여행 일지 저장" else "Keep travel log", prefs.keepTravelLog) { onChange(prefs.copy(keepTravelLog = true)) }
                Choice(if (ko) "저장 안 함" else "Don't keep", !prefs.keepTravelLog) { onChange(prefs.copy(keepTravelLog = false)) }
            }
            Body(if (ko) "위치 데이터는 가이드 생성에만 쓰고 서버에 영구 저장하지 않아요." else "Location data is used only to build the guide and is never stored permanently.", size = 12)
            Spacer(Modifier.weight(1f))
            ActionButton(if (ko) "닫기" else "Close", primary = true, onClick = onClose)
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Headline(if (ko) "목소리" else "Voice", 26)
            Label(if (ko) "가족 목소리" else "Family voice")
            if (voice != null) {
                Body(if (ko) "현재: ${voice.name} 목소리 ${if (voice.modelId != null) "(복제 모델 연결됨)" else "(샘플만 저장, API 키 필요)"}" else "Current: ${voice.name} ${if (voice.modelId != null) "(cloned model active)" else "(samples saved, API key needed)"}", color = Ink, size = 14)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(if (ko) "다시 녹음" else "Re-record", onClick = onVoiceSetup)
                    ActionButton(if (ko) "삭제" else "Delete", onClick = onDeleteVoice)
                }
            } else {
                Body(if (ko) "엄마·아빠·아이 목소리를 녹음하거나 녹음 파일을 가져오면 그 목소리로 가이드가 읽어줘요." else "Record Mum, Dad or the child, or import recordings, and the guide reads in that voice.", size = 14)
                Spacer(Modifier.height(8.dp))
                ActionButton(if (ko) "🎙 목소리 녹음·가져오기" else "🎙 Record or import", primary = true, onClick = onVoiceSetup)
            }
            Label(if (ko) "음성 복제 API 키" else "Voice-cloning API key")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("ElevenLabs", prefs.voiceProvider == "elevenlabs") { onChange(prefs.copy(voiceProvider = "elevenlabs")) }
                Choice("Typecast", prefs.voiceProvider == "typecast") { onChange(prefs.copy(voiceProvider = "typecast")) }
            }
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = key, onValueChange = { key = it },
                textStyle = TextStyle(color = Ink, fontSize = 15.sp), visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().background(Chip, RoundedCornerShape(10.dp)).padding(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(if (ko) "키 저장" else "Save key") { onChange(prefs.copy(voiceApiKey = key.trim())) }
            Body(if (ko) "키는 이 기기에만 저장돼요. 없으면 기기 기본 TTS로 재생해요." else "The key stays on this device. Without it, on-device TTS is used.", size = 12)
            Label(if (ko) "음성 명령" else "Voice commands")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(if (ko) "Gleo (Pleos)" else "Gleo (Pleos)", !prefs.localVoiceCommands) { onChange(prefs.copy(localVoiceCommands = false)) }
                Choice(if (ko) "기기 음성인식" else "On-device", prefs.localVoiceCommands) { onChange(prefs.copy(localVoiceCommands = true)) }
            }
            Body(if (localVoiceAvailable) (if (ko) "이 기기에서 음성인식을 쓸 수 있어요. “넘겨”, “더 자세히”, “거기로 가자”, “다른 데 없어?”, “예약해”, “일지 보여줘”" else "Speech recognition is available. “skip”, “tell me more”, “let's go there”, “where next”, “reserve”, “show the log”")
                 else (if (ko) "이 기기에는 음성인식 엔진이 없어요 (Pleos Connect에서는 Gleo가 처리)" else "No speech engine on this device (Gleo handles it on Pleos Connect)"), size = 12)
        }
    }
}
