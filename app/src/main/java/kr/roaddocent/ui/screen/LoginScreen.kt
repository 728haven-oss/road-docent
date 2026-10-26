package kr.roaddocent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.roaddocent.ui.theme.Accent
import kr.roaddocent.ui.theme.Body
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.TwoPane

/** Sign-in: local test account or Google. Shown before anything else; the terms follow on first sign-in. */
@Composable
fun LoginScreen(error: String?, googleReady: Boolean, busy: Boolean, onLocal: (String, String) -> Unit, onGoogle: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    TwoPane() {
        Panel(Modifier.weight(1.1f)) {
            Text("ROAD DOCENT", color = Accent, fontSize = 14.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Headline("로드 도슨트", 34)
            Body("목적지를 찍는 순간, 내 차에 그 지역 전문 가이드가 탑승한다.", size = 16)
            Spacer(Modifier.height(22.dp))
            Field(id, { id = it }, "아이디 (예: tester)", password = false)
            Spacer(Modifier.height(10.dp))
            Field(pw, { pw = it }, "비밀번호", password = true)
            Spacer(Modifier.height(10.dp))
            error?.let { Body(it, color = Accent, size = 14); Spacer(Modifier.height(6.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(if (busy) "확인 중…" else "로그인", primary = true) { if (!busy) onLocal(id, pw) }
                ActionButton(if (googleReady) "G  Google로 계속" else "G  Google (설정 필요)") { onGoogle() }
            }
            Spacer(Modifier.height(14.dp))
            Body("테스트 계정 · tester / pleos2026!  ·  judge / hyundai2026  ·  family / family2026", size = 13)
            if (!googleReady) Body("Google 로그인은 Google Cloud Console의 웹 클라이언트 ID를 gradle.properties의 GOOGLE_WEB_CLIENT_ID에 넣으면 켜집니다.", size = 12)
        }
        Panel(Modifier.weight(1f)) {
            Text("로그인하면 이렇게 돼요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            listOf("여행 일지·스티커·저장한 이야기가 계정에 묶입니다 (기기 안에만)", "가족 목소리 프로필이 계정별로 유지됩니다", "첫 로그인 때 약관과 개인정보 처리방침에 동의합니다", "렌터카·공유차에서는 내리면서 로그아웃하면 기록이 남지 않습니다").forEach {
                Row(Modifier.padding(vertical = 4.dp)) { Text("•", color = Quiet, fontSize = 14.sp, modifier = Modifier.width(16.dp)); Text(it, color = Ink, fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, password: Boolean) {
    Column(Modifier.fillMaxWidth().background(Chip, RoundedCornerShape(10.dp)).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, color = Quiet, fontSize = 15.sp)
        BasicTextField(value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Email),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth())
    }
}

/** First sign-in: the terms must be scrolled and both boxes ticked. */
@Composable
fun TermsScreen(terms: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    var a by remember { mutableStateOf(false) }
    var b by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Panel(Modifier.fillMaxSize()) {
        Headline("이용약관 · 개인정보 처리방침", 24)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().background(Chip.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp).verticalScroll(scroll)) {
            terms.lines().forEach { line ->
                when {
                    line.startsWith("# ") -> Text(line.removePrefix("# "), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    line.startsWith("## ") -> { Spacer(Modifier.height(8.dp)); Text(line.removePrefix("## "), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    line.isBlank() -> Spacer(Modifier.height(4.dp))
                    else -> Text(line.removePrefix("- "), color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TermsCheck(a, "이용약관에 동의합니다 (필수)") { a = !a }
        TermsCheck(b, "위치·주행 기록을 해설과 여행 일지에 쓰는 것에 동의합니다 (필수, 기기에만 저장)") { b = !b }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("동의하고 시작", primary = a && b) { if (a && b) onAccept() }
            ActionButton("동의하지 않음 (로그아웃)") { onDecline() }
        }
    }
}

@Composable
fun TermsCheck(checked: Boolean, text: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(if (checked) "☑" else "☐", Modifier.clickable(onClick = onToggle), color = if (checked) Accent else Quiet, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
