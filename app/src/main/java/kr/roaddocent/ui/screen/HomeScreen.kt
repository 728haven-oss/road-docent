package kr.roaddocent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Themes
import kr.roaddocent.ui.theme.CardBorder
import kr.roaddocent.ui.theme.HeroBand
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Navy
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.SectionTitle
import kr.roaddocent.ui.theme.SkySoft

/** Home: greet, resume, "near me now", pick by theme (tile grid), destination cards. No sideways scrolling. */
@Composable
fun HomeScreen(
    userName: String, lang: Lang, modeLine: String, guides: List<Guide>, theme: String,
    resume: Pair<Guide, Long>?, onTheme: (String) -> Unit, onStart: (Guide) -> Unit, onNearby: () -> Unit, onResume: () -> Unit, onDismissResume: () -> Unit
) {
    val ko = lang == Lang.KO
    var info by remember { mutableStateOf<Guide?>(null) }
    val list = guides.filter { theme.isBlank() || theme in it.themes }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroBand {
            Text(if (ko) "$userName 님, 어디로 갈까요?" else "Where to, $userName?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(modeLine, color = Color(0xDDFFFFFF), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(Color(0x26FFFFFF), RoundedCornerShape(12.dp)).clickable(onClick = onNearby).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📍", fontSize = 20.sp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (ko) "지금 주변 이야기 듣기" else "A story near me now", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (ko) "목적지 없이, 지금 있는 곳 5km 안의 이야기" else "No destination, a story within 5 km", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                }
                Text("›", color = Color.White, fontSize = 22.sp)
            }
        }
        resume?.let { (g, ago) ->
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▶", color = Navy, fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clickable(onClick = onResume)) {
                        Text(if (ko) "이어서 듣기" else "Resume", color = Quiet, fontSize = 12.sp)
                        Text((g.destination.name[lang.tag] ?: g.id) + if (ko) " · ${ago}분 전" else " · ${ago} min ago", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("✕", Modifier.clickable(onClick = onDismissResume).padding(6.dp), color = Quiet, fontSize = 14.sp)
                }
            }
        }
        SectionTitle(if (ko) "어떤 여행을 좋아하세요?" else "What do you enjoy?")
        Grid(listOf("") + Themes.order.filter { it != "car" }, columns = 4, gap = 8) { t ->
            val label = if (t.isBlank()) (if (ko) "✨ 전체" else "✨ All") else Themes.label(t, lang)
            PictureTile(label.substringBefore(" "), label.substringAfter(" "), selected = theme == t) { onTheme(t) }
        }
        SectionTitle(if (ko) "추천 코스 ${list.size}" else "Courses ${list.size}")
        if (list.isEmpty()) Text(if (ko) "이 테마의 코스는 준비 중이에요. '전체'를 눌러 보세요." else "No course for this theme yet. Try All.", color = Quiet, fontSize = 14.sp)
        list.forEach { g -> DestinationCard(g, lang, primary = false, onStart = { onStart(g) }, onInfo = { info = g }) }
        Spacer(Modifier.height(8.dp))
    }
    info?.let { g ->
        Dialog(onDismissRequest = { info = null }) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pictogram(g.destination.category), fontSize = 28.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ko) "출발 전 알아두기" else "Know before you go", color = Quiet, fontSize = 12.sp)
                        Text(g.destination.name[lang.tag] ?: g.id, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                g.knowBeforeYouGo[lang.tag].orEmpty().forEach { line ->
                    val (icon, label, body) = kbygItem(line)
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(icon, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                        Column { Text(label, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(body, color = Ink, fontSize = 13.sp, lineHeight = 19.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (ko) "출발 당일 공식 안내로 한 번 더 확인하세요" else "Re-check with the venue on the day", color = Quiet, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ko) "닫기" else "Close", Modifier.weight(1f).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { info = null }.padding(12.dp), color = Navy, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(if (ko) "출발" else "Go", Modifier.weight(1f).background(Navy, RoundedCornerShape(12.dp)).clickable { info = null; onStart(g) }.padding(12.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** "운영: 09:00~18:00…" → icon + label + body for the know-before-you-go sheet. */
fun kbygItem(line: String): Triple<String, String, String> {
    val i = line.indexOf(':'); val label = if (i in 1..12) line.substring(0, i).trim() else ""; val body = if (label.isNotEmpty()) line.substring(i + 1).trim() else line
    val icon = when {
        label.contains("운영") || label.startsWith("Hours") -> "🕘"; label.contains("요금") || label.startsWith("Fee") -> "🎫"; label.contains("주차") || label.startsWith("Parking") -> "🅿️"
        label.contains("추천") || label.startsWith("Best") || label.startsWith("Suggested") -> "⭐"; label.contains("챙길") || label.startsWith("Bring") -> "🎒"; label.contains("예산") || label.startsWith("Budget") -> "💳"
        label.contains("아이") || label.startsWith("Kids") -> "🧒"; else -> "📌"
    }
    return Triple(icon, label.ifEmpty { "안내" }, body)
}
