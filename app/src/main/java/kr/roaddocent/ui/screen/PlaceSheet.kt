package kr.roaddocent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.roaddocent.core.Audience
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Place
import kr.roaddocent.ui.theme.CardBorder
import kr.roaddocent.ui.theme.ChipRow
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Navy
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.SectionTitle
import kr.roaddocent.ui.theme.Tag
import kr.roaddocent.ui.theme.Text
import kr.roaddocent.ui.theme.WarmSoft

/** The story to read aloud / show for a place, picked for the audience; falls back to the one-line note. */
fun placeStory(p: Place, lang: Lang, audience: Audience): String {
    val kids = if (audience == Audience.KIDS) p.kidsStory[lang.tag] else null
    return kids ?: p.story[lang.tag] ?: p.story["ko"] ?: p.note[lang.tag].orEmpty()
}

/** Icon for a "label: text" highlight line. */
private fun highlightIcon(label: String): String = when {
    label.contains("정자각") || label.contains("배치") || label.contains("layout", true) -> "🏯"; label.startsWith("능") || label.contains("mound", true) -> "⛰️"
    label.contains("엄흥도") || label.contains("memorial", true) -> "🪦"; label.contains("위패") || label.contains("장판옥") || label.contains("altar", true) -> "🕯️"
    label.contains("역사관") || label.contains("History", true) -> "📚"; label.contains("누각") || label.contains("자규루") || label.contains("pavilion", true) -> "🐦"
    label.contains("시조") || label.contains("lament", true) -> "📜"; label.contains("투영") || label.contains("Planetarium", true) -> "🌌"
    label.contains("관측") || label.contains("Observation", true) -> "🔭"; label.contains("전망") || label.contains("view", true) -> "🌇"
    label.contains("낮") || label.contains("Day", true) -> "☀️"; label.contains("영화") || label.contains("film", true) -> "🎬"
    label.contains("다방") || label.contains("tearoom", true) -> "☕"; label.contains("위치") || label.contains("town", true) -> "📍"
    else -> "✨"
}

/**
 * Tap a place anywhere (다른 제안, 함께 보면 좋은 곳) → this card: picture, story, what to look for, visit tips,
 * and buttons to hear it read aloud or drive there.
 */
@Composable
fun PlaceSheet(place: Place, lang: Lang, audience: Audience, onHear: (String) -> Unit, onGo: ((Place) -> Unit)?, onClose: () -> Unit) =
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PlaceCard(place, lang, audience, onHear, onGo, onClose, Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
    }

/** The card itself (also rendered directly in screenshot tests). */
@Composable
fun PlaceCard(place: Place, lang: Lang, audience: Audience, onHear: (String) -> Unit, onGo: ((Place) -> Unit)?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ko = lang == Lang.KO
    val l = lang.tag
    val name = place.name[l] ?: place.name.values.firstOrNull().orEmpty()
    val story = placeStory(place, lang, audience)
    run {
        Panel(modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hero(place.poiId.takeIf { it.endsWith(".jpg") || it.endsWith(".webp") }, place.category, name, height = 130)
                Column {
                    Text(name, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    place.note[l]?.let { Text(it, color = Quiet, fontSize = 14.sp, lineHeight = 20.sp) }
                }
                ChipRow {
                    place.typicalStayMin?.let { Tag(if (ko) "⏳ 보통 ${it}분" else "⏳ ~$it min") }
                    if (place.address.isNotBlank()) Tag("📍 " + place.address.split(" ").drop(1).take(2).joinToString(" "))
                }
                if (story.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().background(WarmSoft, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text(if (audience == Audience.KIDS && place.kidsStory[l] != null) (if (ko) "🧒 어린이 해설" else "🧒 For kids") else (if (ko) "🎧 도슨트 해설" else "🎧 The story"),
                            color = Color(0xFFB0681A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(story, color = Ink, fontSize = 15.sp, lineHeight = 23.sp)
                    }
                }
                place.highlights[l]?.takeIf { it.isNotEmpty() }?.let { items ->
                    SectionTitle(if (ko) "여기서 꼭 볼 것" else "Don't miss")
                    items.forEach { line ->
                        val cut = line.indexOf(':').takeIf { it in 1..24 }
                        val label = cut?.let { line.substring(0, it).trim() } ?: line
                        val body = cut?.let { line.substring(it + 1).trim() } ?: line
                        Row(verticalAlignment = Alignment.Top) {
                            Text(highlightIcon(label), fontSize = 20.sp, modifier = Modifier.width(32.dp))
                            Column { Text(label, color = Navy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); if (body != line) Text(body, color = Ink, fontSize = 14.sp, lineHeight = 20.sp) }
                        }
                    }
                }
                place.visit[l]?.takeIf { it.isNotEmpty() }?.let { items ->
                    SectionTitle(if (ko) "방문 팁" else "Visit tips")
                    items.forEach { line ->
                        val (icon, label, body) = kbygItem(line)
                        Row(verticalAlignment = Alignment.Top) {
                            Text(icon, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                            Text(if (label != "안내") "$label · $body" else body, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ko) "🔊 들려주기" else "🔊 Read aloud", Modifier.weight(1f).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { onHear(story) }.padding(vertical = 12.dp),
                    color = Navy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                onGo?.let { go ->
                    Text(if (ko) "🚗 여기로 출발" else "🚗 Go here", Modifier.weight(1f).background(Navy, RoundedCornerShape(12.dp)).clickable { onClose(); go(place) }.padding(vertical = 12.dp),
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            Text(if (ko) "닫기" else "Close", Modifier.fillMaxWidth().clickable(onClick = onClose).padding(top = 12.dp, bottom = 2.dp), color = Quiet, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

