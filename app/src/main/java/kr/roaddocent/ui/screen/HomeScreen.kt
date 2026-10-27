package kr.roaddocent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import kr.roaddocent.ui.theme.ChipRow
import kr.roaddocent.ui.theme.WarmSoft
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
import kr.roaddocent.ui.theme.Text
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

/**
 * Home, built around the one thing the app does: "pick a destination → a guide boards your car".
 * 1) navigation-style destination search  2) how it works in three pictures  3) two quick actions
 * 4) theme chips that wrap (no sideways scrolling)  5) picture course cards with an explicit "출발" button.
 */
@Composable
fun HomeScreen(
    userName: String, lang: Lang, modeLine: String, guides: List<Guide>, theme: String,
    resume: Pair<Guide, Long>?, onTheme: (String) -> Unit, onStart: (Guide) -> Unit, onNearby: () -> Unit, onResume: () -> Unit, onDismissResume: () -> Unit
) {
    val ko = lang == Lang.KO
    var info by remember { mutableStateOf<Guide?>(null) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val list = guides.filter { g ->
        (theme.isBlank() || theme in g.themes) &&
            (q.isEmpty() || g.destination.name.values.any { it.contains(q, ignoreCase = true) } || g.startName.values.any { it.contains(q, ignoreCase = true) } ||
                g.episodes.any { e -> e.title.values.any { it.contains(q, ignoreCase = true) } })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1 — header + destination search (looks like the car's navigation input)
        HeroBand {
            Text(if (ko) "$userName 님, 반가워요" else "Hi, $userName", color = Color(0xCCFFFFFF), fontSize = 14.sp)
            Text(if (ko) "어디로 갈까요?" else "Where to?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔍", fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(if (ko) "목적지 검색  예) 경복궁, 강릉" else "Search a destination, e.g. Gyeongbokgung", color = Quiet, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
                }
                if (query.isNotEmpty()) Text("✕", Modifier.clickable { query = "" }.padding(start = 8.dp), color = Quiet, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))
            // 2 — how it works, in three pictures
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val steps = if (ko) listOf("🎯" to "목적지 선택", "🎧" to "가는 길 이야기", "🏁" to "도착 안내") else listOf("🎯" to "Pick a place", "🎧" to "Stories en route", "🏁" to "Arrival tips")
                steps.forEachIndexed { i, (icon, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, Modifier.background(Color(0x26FFFFFF), RoundedCornerShape(999.dp)).padding(8.dp), fontSize = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    }
                    if (i < steps.lastIndex) Text("›", color = Color(0x99FFFFFF), fontSize = 20.sp)
                }
            }
        }
        // 3 — two quick actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Modifier.weight(1f), "📍", if (ko) "지금 여기 이야기" else "Near me now", if (ko) "목적지 없이 바로 듣기" else "No destination needed", SkySoft, onNearby)
            if (resume != null) {
                val (g, ago) = resume
                QuickAction(Modifier.weight(1f), "▶️", if (ko) "이어 듣기" else "Resume", (g.destination.name[lang.tag] ?: g.id) + if (ko) " · ${ago}분 전" else " · ${ago}m ago", WarmSoft, onResume, onClose = onDismissResume)
            } else {
                val pick = guides.firstOrNull()
                QuickAction(Modifier.weight(1f), "⭐", if (ko) "오늘의 추천" else "Today's pick", pick?.destination?.name?.get(lang.tag) ?: "", WarmSoft, { pick?.let(onStart) })
            }
        }
        // 4 — theme chips (wrap, never scroll sideways)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(if (ko) "테마로 고르기" else "By theme")
            ChipRow {
                (listOf("") + Themes.order.filter { it != "car" }).forEach { t ->
                    val label = if (t.isBlank()) (if (ko) "✨ 전체" else "✨ All") else Themes.label(t, lang)
                    kr.roaddocent.ui.theme.Pill(label, Modifier.clip(RoundedCornerShape(999.dp)).clickable { onTheme(t) }, selected = theme == t)
                }
            }
        }
        // 5 — course cards
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle(when { q.isNotEmpty() -> if (ko) "'$q' 검색 결과" else "Results for '$q'"; else -> if (ko) "추천 코스" else "Courses" })
            Spacer(Modifier.width(6.dp)); Text("${list.size}", color = Quiet, fontSize = 15.sp)
        }
        if (list.isEmpty()) Text(if (ko) "맞는 코스가 아직 없어요. 다른 이름이나 '전체' 테마로 찾아보세요." else "No course yet. Try another name or All.", color = Quiet, fontSize = 14.sp)
        list.forEach { g -> CourseCard(g, lang, onStart = { onStart(g) }, onInfo = { info = g }) }
        Text(modeLine, color = Quiet, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
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

@Composable
private fun QuickAction(modifier: Modifier, icon: String, title: String, sub: String, tint: Color, onClick: () -> Unit, onClose: (() -> Unit)? = null) {
    Box(modifier.background(tint, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Column {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(sub, color = Quiet, fontSize = 12.sp, maxLines = 1)
        }
        onClose?.let { Text("✕", Modifier.align(Alignment.TopEnd).clickable(onClick = it).padding(2.dp), color = Quiet, fontSize = 13.sp) }
    }
}

/** Soft background per main theme, so cards are recognisable at a glance. */
private fun themeTint(g: Guide): Color = when (g.themes.firstOrNull()) {
    "history" -> Color(0xFFF6EBDD); "art" -> Color(0xFFEDE7F6); "nature" -> Color(0xFFE0F2EF); "thrill" -> Color(0xFFFDE8DF)
    "food" -> Color(0xFFFFF3D9); "car" -> Color(0xFFE3ECF7); else -> Color(0xFFE9EEF3)
}

/** Picture course card: illustration band, name, time · stories, theme tags, and two clear buttons. */
@Composable
fun CourseCard(g: Guide, lang: Lang, onStart: () -> Unit, onInfo: () -> Unit) {
    val ko = lang == Lang.KO
    val name = g.destination.name[lang.tag] ?: g.id
    val stories = g.episodes.count { it.trigger !is kr.roaddocent.core.Trigger.Reboard }
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, CardBorder, shape).clickable(onClick = onStart)) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(themeTint(g), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))) {
            val bmp = assetImage(g.destination.poiId.takeIf { it.endsWith(".jpg") || it.endsWith(".webp") })
            if (bmp != null) androidx.compose.foundation.Image(bmp, contentDescription = name, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
            else Text(pictogram(g.destination.category), Modifier.align(Alignment.CenterEnd).padding(end = 24.dp), fontSize = 52.sp)
            Text((g.startName[lang.tag] ?: "") + if (ko) " → $name" else " → $name", Modifier.align(Alignment.BottomStart).padding(10.dp)
                .background(Color(0xE6FFFFFF), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = Ink, fontSize = 12.sp, maxLines = 1)
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (ko) "🚗 ${g.driveMin}분" else "🚗 ${g.driveMin} min", color = Ink, fontSize = 14.sp, maxLines = 1)
                Text(if (ko) "🎧 이야기 ${stories}편" else "🎧 $stories stories", color = Ink, fontSize = 14.sp, maxLines = 1)
            }
            ChipRow { g.themes.forEach { kr.roaddocent.ui.theme.Tag(Themes.label(it, lang)) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g.knowBeforeYouGo.isNotEmpty())
                    Text(if (ko) "알아두기" else "Before you go", Modifier.weight(1f).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable(onClick = onInfo).padding(vertical = 11.dp),
                        color = Navy, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                Text(if (ko) "▶ 이 코스로 출발" else "▶ Start", Modifier.weight(1.6f).background(Navy, RoundedCornerShape(12.dp)).clickable(onClick = onStart).padding(vertical = 11.dp),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
    }
}
