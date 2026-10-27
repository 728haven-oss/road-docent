package kr.roaddocent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import kr.roaddocent.ui.theme.Text
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            kr.roaddocent.ui.theme.HeroBand {
                Text("ROAD DOCENT", color = Color(0xFF9EE6F7), fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("로드 도슨트", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("목적지를 찍는 순간,\n내 차에 그 지역 전문 가이드가 탑승합니다.", color = Color(0xE6FFFFFF), fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("🚗", "🏯", "🏛️", "🌊", "🎢").forEach { Text(it, Modifier.weight(1f).background(Color(0x26FFFFFF), RoundedCornerShape(12.dp)).padding(vertical = 6.dp), fontSize = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                kr.roaddocent.ui.theme.SectionTitle("로그인")
                Spacer(Modifier.height(10.dp))
                Field(id, { id = it }, "아이디", password = false)
                Spacer(Modifier.height(8.dp))
                Field(pw, { pw = it }, "비밀번호", password = true)
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFC0392B), fontSize = 13.sp) }
                Spacer(Modifier.height(12.dp))
                kr.roaddocent.ActionButtonWide(if (busy) "확인 중…" else "로그인", primary = true) { if (!busy) onLocal(id, pw) }
                Spacer(Modifier.height(8.dp))
                kr.roaddocent.ActionButtonWide(if (googleReady) "G   Google 계정으로 계속" else "G   Google 계정으로 계속 (준비 중)") { onGoogle() }
            }
            Column(Modifier.fillMaxWidth().background(kr.roaddocent.ui.theme.SkySoft, RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text("🧪 테스트 계정", color = kr.roaddocent.ui.theme.Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                listOf("test" to "1234").forEach { (a, b) ->
                    Row(Modifier.fillMaxWidth().clickable { id = a; pw = b }.padding(vertical = 3.dp)) {
                        Text(a, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp)); Text(b, color = Quiet, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f)); Text("채우기", color = kr.roaddocent.ui.theme.Navy, fontSize = 12.sp)
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            kr.roaddocent.ui.theme.SectionTitle("로그인하면")
            listOf("🗺️" to "여행 일지·스티커·저장한 이야기가 내 계정에 남아요 (기기 안에만)", "🎙" to "가족 목소리 프로필이 계정별로 유지돼요", "📄" to "첫 로그인 때 약관에 한 번 동의해요", "🚙" to "렌터카에서는 내릴 때 로그아웃하면 기록이 남지 않아요").forEach { (i, t) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text(i, fontSize = 20.sp, modifier = Modifier.width(34.dp)); Text(t, color = Ink, fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, password: Boolean) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, kr.roaddocent.ui.theme.CardBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 14.dp)) {
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
        kr.roaddocent.ActionButtonWide(if (a && b) "동의하고 시작" else "두 항목에 동의해 주세요", primary = a && b) { if (a && b) onAccept() }
        Spacer(Modifier.height(8.dp))
        Text("동의하지 않음 (로그아웃)", Modifier.fillMaxWidth().clickable { onDecline() }.padding(8.dp), color = Quiet, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun TermsCheck(checked: Boolean, text: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(Modifier.size(24.dp).background(if (checked) kr.roaddocent.ui.theme.Navy else Color.White, RoundedCornerShape(6.dp)).border(1.5.dp, if (checked) kr.roaddocent.ui.theme.Navy else kr.roaddocent.ui.theme.CardBorder, RoundedCornerShape(6.dp)).clickable(onClick = onToggle), contentAlignment = Alignment.Center) { if (checked) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
