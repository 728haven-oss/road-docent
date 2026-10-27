package kr.roaddocent.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import kr.roaddocent.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.roaddocent.core.Audience
import kr.roaddocent.core.Episode
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Place
import kr.roaddocent.log.TripLog
import kr.roaddocent.ui.theme.Accent
import kr.roaddocent.ui.theme.carSp
import kr.roaddocent.ui.theme.AccentInk
import kr.roaddocent.ui.theme.Body
import kr.roaddocent.ui.theme.IconAction
import kr.roaddocent.ui.theme.ButtonFlow
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.ChipInk
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.TwoPane
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.Tag
import kr.roaddocent.ui.theme.Warm

@Composable
fun ActionButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        Modifier
            .background(if (primary) kr.roaddocent.ui.theme.Navy else kr.roaddocent.ui.theme.Card, shape)
            .border(1.dp, if (primary) kr.roaddocent.ui.theme.Navy else kr.roaddocent.ui.theme.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = if (primary) Color.White else kr.roaddocent.ui.theme.Navy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
    )
}

@Composable
fun EpisodeRow(index: Int, ep: Episode, lang: Lang, state: String, now: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(30.dp).height(30.dp).background(if (now) Accent else Chip, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Text("$index", color = if (now) AccentInk else ChipInk, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(ep.titleFor(lang), color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(state, color = Quiet, fontSize = 13.sp)
    }
}

/** 1. Playing */
@Composable
fun PlayingScreen(ep: Episode, all: List<Episode>, played: List<String>, lang: Lang, audience: Audience, congestion: Boolean, paused: Boolean, speaking: Boolean,
                  quiz: kr.roaddocent.engine.DocentEngine.QuizView? = null, driving: Boolean = false,
                  onSkip: () -> Unit, onMore: () -> Unit, onSave: () -> Unit, onQuiet: () -> Unit, onFeedback: (Boolean) -> Unit = {}) {
    // Speaking glow: the panel border brightens for a second when the guide starts talking, so passengers notice without reading.
    val glow by androidx.compose.animation.core.animateFloatAsState(if (speaking && !paused) 1f else 0f, androidx.compose.animation.core.tween(600), label = "glow")
    if (driving) {
        // Driving view: title, waveform and progress dots only. Full text opens in P.
        val idx = all.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
        Column(Modifier.fillMaxSize().border(3.dp, Accent.copy(alpha = 0.15f + 0.85f * glow), RoundedCornerShape(24.dp)).padding(28.dp), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (congestion) (if (lang == Lang.KO) "정체 · 심층 해설" else "Traffic · long story") else (if (lang == Lang.KO) "지금 지나는 곳" else "Passing now"))
                if (audience == Audience.KIDS) Tag(if (lang == Lang.KO) "키즈" else "Kids")
                if (paused) Tag(if (lang == Lang.KO) "고속 · 잠시 멈춤" else "High speed · paused")
                kindLabel(ep.kind, lang)?.let { Tag(it) }
            }
            Spacer(Modifier.height(14.dp))
            Headline(ep.titleFor(lang), 34)
            Spacer(Modifier.height(10.dp))
            Wave(active = speaking && !paused)
            if (quiz != null) { Spacer(Modifier.height(14.dp)); Text("❓ " + quiz.question, color = Ink, fontSize = carSp(24), maxLines = 2); quiz.answer?.let { Text("→ $it", color = Accent, fontSize = carSp(22), maxLines = 2) } }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEachIndexed { i, e -> Box(Modifier.width(if (i == idx) 28.dp else 10.dp).height(10.dp).background(if (i == idx) Accent else if (e.id in played) ChipInk else Chip, RoundedCornerShape(5.dp))) }
                Spacer(Modifier.weight(1f))
                VoiceHint(lang, if (quiz != null && quiz.answer == null) "quiz" else "playing")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction("⏭", if (lang == Lang.KO) "넘기기" else "Skip", onSkip)
                IconAction("➕", if (lang == Lang.KO) "자세히" else "More", onMore)
                IconAction("🔖", if (lang == Lang.KO) "저장" else "Save", onSave)
                IconAction("🔇", if (lang == Lang.KO) "조용히" else "Quiet", onQuiet)
                IconAction("👍", if (lang == Lang.KO) "좋아요" else "Like", { onFeedback(true) })
                IconAction("👎", if (lang == Lang.KO) "별로" else "Meh", { onFeedback(false) })
            }
        }
        return
    }
    TwoPane() {
        Panel(Modifier.weight(1.3f).fillMaxHeight().border(2.dp, Accent.copy(alpha = 0.9f * glow), RoundedCornerShape(24.dp))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (congestion) (if (lang == Lang.KO) "정체 구간 · 심층 해설" else "Traffic · long story") else (if (lang == Lang.KO) "지금 지나는 곳" else "Passing now"))
                if (audience == Audience.KIDS) Tag(if (lang == Lang.KO) "키즈 모드" else "Kids mode")
                if (paused) Tag(if (lang == Lang.KO) "고속 구간 · 잠시 멈춤" else "High speed · paused")
                kindLabel(ep.kind, lang)?.let { Tag(it) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(kr.roaddocent.ui.theme.SkySoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(kindEmoji(ep.kind), fontSize = 26.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Headline(ep.titleFor(lang), 21) }
            }
            Spacer(Modifier.height(10.dp))
            Wave(active = speaking && !paused)
            Spacer(Modifier.height(10.dp))
            Body(ep.textFor(lang, audience), color = Ink, size = 15)
            ep.source.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(6.dp)); Text((if (lang == Lang.KO) "출처 · " else "Source · ") + it, color = Quiet, fontSize = 11.sp) }
            if (quiz != null) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().background(Chip, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(if (lang == Lang.KO) "❓ 퀴즈" else "❓ Quiz", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(quiz.question, color = Ink, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(quiz.answer ?: (if (lang == Lang.KO) "생각해 보세요… 잠시 뒤 정답 (\"더 자세히\"로 바로 듣기)" else "Think… answer in a moment (say \"tell me more\" to hear it now)"),
                        color = if (quiz.answer == null) Quiet else Ink, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction("⏭", if (lang == Lang.KO) "넘기기" else "Skip", onSkip)
                IconAction("➕", if (lang == Lang.KO) "자세히" else "More", onMore)
                IconAction("🔖", if (lang == Lang.KO) "저장" else "Save", onSave)
                IconAction("🔇", if (lang == Lang.KO) "조용히" else "Quiet", onQuiet)
                IconAction("👍", if (lang == Lang.KO) "좋아요" else "Like", { onFeedback(true) })
                IconAction("👎", if (lang == Lang.KO) "별로" else "Meh", { onFeedback(false) })
            }
        }
        if (kr.roaddocent.ui.theme.isCompact()) {
            val cur = all.indexOfFirst { it.id == ep.id }
            val next = all.getOrNull(cur + 1)
            Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏭", fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (lang == Lang.KO) "다음 이야기" else "Up next", color = Quiet, fontSize = 12.sp)
                        Text(next?.titleFor(lang) ?: (if (lang == Lang.KO) "다음 길목에서 이어져요" else "continues at the next stop"), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                    Text("${(cur + 1).coerceAtLeast(1)}/${all.size}", color = Quiet, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(Chip, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(((cur + 1).coerceAtLeast(1)).toFloat() / all.size.coerceAtLeast(1)).height(6.dp).background(kr.roaddocent.ui.theme.Sky, RoundedCornerShape(3.dp)))
                }
            }
            return@TwoPane
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "오늘의 이야기" else "Today's stories", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val compact = kr.roaddocent.ui.theme.isCompact()
            val cur = all.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
            val shown = if (compact) all.withIndex().filter { it.index in (cur - 1)..(cur + 2) } else all.withIndex().toList()
            shown.forEach { (i, e) ->
                val st = when { e.id == ep.id -> if (lang == Lang.KO) "재생 중" else "playing"; e.id in played -> if (lang == Lang.KO) "들음" else "heard"; else -> "" }
                EpisodeRow(i + 1, e, lang, st, e.id == ep.id)
            }
            if (compact && shown.size < all.size) Text(if (lang == Lang.KO) "… 총 ${all.size}편" else "… ${all.size} in total", color = Quiet, fontSize = 13.sp)
        }
    }
}

/** One or two voice phrases that make sense right now, instead of a permanent row of buttons. */
@Composable
fun VoiceHint(lang: Lang, context: String) {
    val hint = when (context) {
        "quiz" -> if (lang == Lang.KO) "🎤 “정답 알려줘”" else "🎤 “tell me the answer”"
        "briefing" -> if (lang == Lang.KO) "🎤 “예약해” · “다른 데 없어?”" else "🎤 “reserve” · “where else?”"
        "wherenext" -> if (lang == Lang.KO) "🎤 “첫 번째로 가자”" else "🎤 “let's go to the first one”"
        "parked" -> if (lang == Lang.KO) "🎤 “일지 보여줘”" else "🎤 “show the log”"
        else -> if (lang == Lang.KO) "🎤 “넘겨” · “더 자세히”" else "🎤 “skip” · “tell me more”"
    }
    Text(hint, color = Quiet, fontSize = carSp(20), maxLines = 1)
}

fun kindEmoji(kind: String): String = when (kind) { "quiz" -> "❓"; "fact" -> "💡"; "food" -> "🍜"; "phrase" -> "🗣"; "tip" -> "✅"; "dialogue" -> "🎭"; else -> "📖" }

fun kindLabel(kind: String, lang: Lang): String? = when (kind) {
    "quiz" -> if (lang == Lang.KO) "❓ 퀴즈" else "❓ Quiz"
    "fact" -> if (lang == Lang.KO) "💡 알고 보면" else "💡 Did you know"
    "food" -> if (lang == Lang.KO) "🍜 맛" else "🍜 Food"
    "phrase" -> if (lang == Lang.KO) "🗣 한국어 한마디" else "🗣 Korean phrase"
    "tip" -> if (lang == Lang.KO) "✅ 팁" else "✅ Tip"
    else -> null
}

@Composable
fun Wave(active: Boolean) {
    val heights = listOf(10, 20, 28, 16, 24, 12, 26, 18, 8, 22, 14, 24)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(30.dp)) {
        heights.forEach { h -> Box(Modifier.width(5.dp).height((if (active) h else 5).dp).background(if (active) kr.roaddocent.ui.theme.Sky else Chip, RoundedCornerShape(3.dp))) }
        Spacer(Modifier.width(8.dp))
        Text(if (active) "읽는 중" else "", color = Quiet, fontSize = 12.sp)
    }
}

/** 2. Arrival briefing */
@Composable
fun BriefingScreen(place: Place, alternatives: List<Place>, lang: Lang, remainMin: Int, onReserve: () -> Unit, onOther: () -> Unit) {
    val l = lang.tag
    TwoPane() {
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Hero(place.poiId.takeIf { it.endsWith(".jpg") || it.endsWith(".webp") }, place.category, place.name[l] ?: "", height = 120)
            Spacer(Modifier.height(10.dp))
            Tag(if (lang == Lang.KO) "⏱ ${remainMin}분 뒤 도착 · 지금 시간엔 여기" else "⏱ $remainMin min to go · good for now")
            Spacer(Modifier.height(6.dp))
            Headline(place.name[l] ?: place.name.values.first(), 24)
            Spacer(Modifier.height(8.dp))
            Body(place.note[l] ?: "", size = 17)
            Body(place.address, size = 14)
            Spacer(Modifier.height(18.dp))
            Text(if (lang == Lang.KO) "\"4명, 도착 시간으로 예약할까요?\"" else "\"Reserve for four at arrival time?\"", color = Ink, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            kr.roaddocent.ui.theme.ButtonFlow {
                ActionButton(if (lang == Lang.KO) "📞 예약 전화 걸기" else "📞 Call to reserve", primary = true, onClick = onReserve)
                ActionButton(if (lang == Lang.KO) "다른 곳 들어볼게" else "Other options", onClick = onOther)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "함께 보면 좋은 곳" else "Nearby, worth pairing", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            alternatives.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(kr.roaddocent.ui.theme.SkySoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(pictogram(p.category), fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(p.name[l] ?: "", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(p.note[l] ?: "", color = Quiet, fontSize = 12.sp, maxLines = 2) }
                }
            }
            Spacer(Modifier.height(10.dp))

        }
    }
}

/** 3. Where next */
@Composable
fun WhereNextScreen(leftAfterMin: Int, suggestions: List<Place>, disappointed: Boolean, lang: Lang, onGo: (Place) -> Unit, onHome: () -> Unit) {
    val l = lang.tag
    val top = suggestions.firstOrNull()
    TwoPane() {
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Text(
                if (lang == Lang.KO) (if (disappointed) "기대와 달랐나요? 근처에 이런 곳이 있어요" else "다음은 여기 어때요?")
                else (if (disappointed) "Not what you hoped? Try these nearby" else "How about this next?"),
                color = Quiet, fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            top?.let {
                Hero(it.poiId.takeIf { x -> x.endsWith(".jpg") || x.endsWith(".webp") }, it.category, it.name[l] ?: "", height = 120)
                Spacer(Modifier.height(10.dp))
                Headline(it.name[l] ?: "", 24)
                Body(it.note[l] ?: "", size = 17)
                it.typicalStayMin?.let { m -> Body(if (lang == Lang.KO) "방문객은 보통 ${m}분 머물러요 (체류 데이터)" else "Visitors usually stay about $m min (dwell data)", size = 14) }
            }
            Spacer(Modifier.weight(1f))
            kr.roaddocent.ui.theme.ButtonFlow {
                top?.let { ActionButton(if (lang == Lang.KO) "🚗 여기로 출발" else "🚗 Go here", primary = true) { onGo(it) } }
                ActionButton(if (lang == Lang.KO) "🗺 오늘 일지 보기" else "🗺 Today's log", onClick = onHome)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "다른 제안" else "Other suggestions", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            suggestions.drop(1).forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clickable { onGo(p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(kr.roaddocent.ui.theme.SkySoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(pictogram(p.category), fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(p.name[l] ?: "", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(p.note[l] ?: "", color = Quiet, fontSize = 12.sp, maxLines = 2) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Body(if (lang == Lang.KO) "${leftAfterMin}분 머무셨어요 · 체류 시간은 이 장소에 다녀간 사용자들의 실제 데이터예요" else "You stayed $leftAfterMin min · stay times come from real visitor data", size = 13)
        }
    }
}

/** 4. Travel log */
@Composable
fun TravelLogScreen(log: TripLog, lang: Lang, history: List<kr.roaddocent.log.DayRecord> = emptyList(), onShare: () -> Unit = {}, onBack: () -> Unit) {
    TwoPane() {
        Panel(Modifier.weight(1.2f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "오늘의 여행 일지" else "Today's travel log", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            RouteCanvas(log, if (kr.roaddocent.ui.theme.isCompact()) Modifier.fillMaxWidth().height(220.dp) else Modifier.fillMaxWidth().weight(1f))
            Body(if (lang == Lang.KO) "원의 크기 = 머문 시간" else "Circle size = time spent", size = 12)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Text("“${log.headline(lang)}”", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                Spacer(Modifier.height(12.dp))
                val ko = lang == Lang.KO
                Grid(listOf(Triple("🚗", "%.0f km".format(log.distanceKm), if (ko) "총 주행" else "driven"), Triple("⏱", if (ko) "${log.drivingMin / 60}시간 ${log.drivingMin % 60}분" else "${log.drivingMin / 60}h ${log.drivingMin % 60}m", if (ko) "이동" else "moving"),
                            Triple("📍", if (ko) "${log.stayMin / 60}시간 ${log.stayMin % 60}분" else "${log.stayMin / 60}h ${log.stayMin % 60}m", if (ko) "머문 시간" else "stayed"), Triple("📖", if (ko) "${log.episodesHeard}편" else "${log.episodesHeard}", if (ko) "들은 이야기" else "stories")), columns = 2, gap = 8) { (i, v, l) ->
                    Row(Modifier.fillMaxWidth().background(kr.roaddocent.ui.theme.SkySoft, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(i, fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                        Column { Text(v, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(l, color = Quiet, fontSize = 12.sp) }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                val days = history.size; val km = history.sumOf { it.distanceKm.toDouble() }; val stops = history.sumOf { it.visits.size }
                Text(if (lang == Lang.KO) (if (days > 1) "지금까지 ${days}일 · %.0f km · 들른 곳 ${stops}곳".format(km) else "첫 기록이에요. 쌓일수록 체류 시간을 배워 더 잘 추천해요")
                     else (if (days > 1) "So far: $days days · %.0f km · $stops stops".format(km) else "First entry. As it grows, I learn your dwell times and suggest better"), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Body(if (lang == Lang.KO) "맞힌 퀴즈 ${log.quizzesRight}개 · 배운 인사말 ${log.phrasesLearned}개 · 이 기기에만 저장(설정에서 끌 수 있어요)" else "${log.quizzesRight} quizzes · ${log.phrasesLearned} phrases · stored on this device only (switch off in settings)", size = 14)
                if (history.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    history.takeLast(4).reversed().forEach { d -> Body("${d.date} · %.0f km · ".format(d.distanceKm) + d.visits.joinToString(" → ") { "${it.name} ${it.stayMin}m" }, size = 13) }
                }
            }
            kr.roaddocent.ui.theme.ButtonFlow {
                ActionButton(if (lang == Lang.KO) "📤 오늘의 여행 공유" else "📤 Share today", primary = true, onClick = onShare)
                if (log.stickers > 0) Tag("⭐ " + (if (lang == Lang.KO) "스티커 ${log.stickers}개" else "${log.stickers} stickers"))
                ActionButton(if (lang == Lang.KO) "닫기" else "Close", onClick = onBack)
            }
        }
    }
}

@Composable
private fun Kpi(v: String, l: String) {
    Column(Modifier.background(Chip, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(v, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(l, color = Quiet, fontSize = 12.sp)
    }
}

@Composable
fun RouteCanvas(log: TripLog, modifier: Modifier) {
    val pts = log.trace
    IllustratedMap(pts, null, emptyList(), log.visits.firstOrNull()?.name?.let { "" } ?: "", log.visits.lastOrNull()?.name ?: "", modifier,
        visitRadii = log.visits.map { it.point to (16f + it.stayMin.coerceAtMost(180) * 0.25f) })
}

/** 5. Kids overlay panel (shown inside Playing when audience == KIDS) */
/** Kids window quiz card: question + three big option buttons; works on the car screen and on the companion phone. */
@Composable
fun KidsQuizCard(v: kr.roaddocent.engine.DocentEngine.KidsQuizView, lang: Lang, onAnswer: (Int) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(if (lang == Lang.KO) "🪟 창밖 퀴즈" else "🪟 Window quiz", color = kr.roaddocent.ui.theme.WarmSoft, ink = kr.roaddocent.ui.theme.Warm)
            Spacer(Modifier.weight(1f))
            Text(if (lang == Lang.KO) "번호를 누르거나 말해요" else "Tap or say the number", color = Quiet, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(v.quiz.q(lang), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            v.quiz.opts(lang).forEachIndexed { i, o ->
                val n = i + 1
                val right = v.chosen != null && n == v.quiz.answer
                val wrong = v.chosen == n && n != v.quiz.answer
                val bg = when { right -> Color(0xFFE3F6EC); wrong -> Color(0xFFFBE7E6); else -> Color.White }
                val line = when { right -> Color(0xFF2E9E6A); wrong -> Color(0xFFD9534F); else -> kr.roaddocent.ui.theme.CardBorder }
                Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).border(1.5.dp, line, RoundedCornerShape(14.dp)).clickable(enabled = v.chosen == null) { onAnswer(n) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(if (right) Color(0xFF2E9E6A) else kr.roaddocent.ui.theme.Navy, RoundedCornerShape(999.dp)), contentAlignment = Alignment.Center) { Text(if (right) "✓" else "$n", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Text(o, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        v.correct?.let { ok -> Spacer(Modifier.height(8.dp)); Text(if (ok) (if (lang == Lang.KO) "⭐ 정답이에요! 스티커를 받았어요" else "⭐ Correct! Sticker earned") else (if (lang == Lang.KO) "아쉬워요. 정답은 ${v.quiz.answer}번이에요" else "So close. It's number ${v.quiz.answer}"), color = if (ok) Color(0xFF2E9E6A) else Quiet, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun KidsPanel(lang: Lang, quizzes: Int) {
    Panel(Modifier.fillMaxWidth()) {
        Text(if (lang == Lang.KO) "🪟 창밖 퀴즈 · 지금!" else "🪟 Window quiz · now!", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Body(if (lang == Lang.KO) "“오른쪽에 큰 호수가 보여요. 이름이 뭘까요?” · 맞힌 퀴즈 ${quizzes}개" else "“There's a big lake on the right. What's its name?” · $quizzes right", size = 15)
        Spacer(Modifier.height(8.dp))
        Text(if (lang == Lang.KO) "🙇 도착하면 이렇게 인사해요" else "🙇 How to greet when we arrive", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Body(if (lang == Lang.KO) "카페에 들어가면 “안녕하세요”, 나올 때 “감사합니다”" else "Say “annyeong-haseyo” going in and “gamsa-hamnida” coming out", size = 15)
    }
}
