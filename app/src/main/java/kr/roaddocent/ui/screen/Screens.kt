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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.ChipInk
import kr.roaddocent.ui.theme.Headline
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Panel
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.Tag
import kr.roaddocent.ui.theme.Warm

@Composable
fun ActionButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .background(if (primary) Accent else Chip, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        color = if (primary) AccentInk else Color(0xFFCFE3FF), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
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
                  onSkip: () -> Unit, onMore: () -> Unit, onSave: () -> Unit, onQuiet: () -> Unit) {
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
            Spacer(Modifier.height(18.dp))
            Headline(ep.titleFor(lang), 40)
            Spacer(Modifier.height(18.dp))
            Wave(active = speaking && !paused)
            if (quiz != null) { Spacer(Modifier.height(14.dp)); Text("❓ " + quiz.question, color = Ink, fontSize = carSp(24), maxLines = 2); quiz.answer?.let { Text("→ $it", color = Accent, fontSize = carSp(22), maxLines = 2) } }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEachIndexed { i, e -> Box(Modifier.width(if (i == idx) 28.dp else 10.dp).height(10.dp).background(if (i == idx) Accent else if (e.id in played) ChipInk else Chip, RoundedCornerShape(5.dp))) }
                Spacer(Modifier.weight(1f))
                VoiceHint(lang, if (quiz != null && quiz.answer == null) "quiz" else "playing")
            }
        }
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.3f).fillMaxHeight().border(2.dp, Accent.copy(alpha = 0.9f * glow), RoundedCornerShape(24.dp))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (congestion) (if (lang == Lang.KO) "정체 구간 · 심층 해설" else "Traffic · long story") else (if (lang == Lang.KO) "지금 지나는 곳" else "Passing now"))
                if (audience == Audience.KIDS) Tag(if (lang == Lang.KO) "키즈 모드" else "Kids mode")
                if (paused) Tag(if (lang == Lang.KO) "고속 구간 · 잠시 멈춤" else "High speed · paused")
                kindLabel(ep.kind, lang)?.let { Tag(it) }
            }
            Spacer(Modifier.height(14.dp))
            Headline(ep.titleFor(lang), 30)
            Spacer(Modifier.height(14.dp))
            Wave(active = speaking && !paused)
            Spacer(Modifier.height(12.dp))
            Body(ep.textFor(lang, audience), size = 15)
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(if (lang == Lang.KO) "\"넘겨\"" else "\"Skip\"", onClick = onSkip)
                ActionButton(if (lang == Lang.KO) "\"더 자세히\"" else "\"Tell me more\"", onClick = onMore)
                ActionButton(if (lang == Lang.KO) "\"저장해\"" else "\"Save\"", onClick = onSave)
                ActionButton(if (lang == Lang.KO) "\"조용히\"" else "\"Quiet\"", onClick = onQuiet)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "오늘의 이야기" else "Today's stories", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            all.forEachIndexed { i, e ->
                val st = when { e.id == ep.id -> if (lang == Lang.KO) "재생 중" else "playing"; e.id in played -> if (lang == Lang.KO) "들음" else "heard"; else -> "" }
                EpisodeRow(i + 1, e, lang, st, e.id == ep.id)
            }
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
    val heights = listOf(14, 30, 44, 22, 38, 18, 40, 26, 12, 34)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.height(44.dp)) {
        heights.forEach { h -> Box(Modifier.width(7.dp).height((if (active) h else 8).dp).background(if (active) Accent else Chip, RoundedCornerShape(4.dp))) }
    }
}

/** 2. Arrival briefing */
@Composable
fun BriefingScreen(place: Place, alternatives: List<Place>, lang: Lang, remainMin: Int, onReserve: () -> Unit, onOther: () -> Unit) {
    val l = lang.tag
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "지금 시간엔 여기가 좋아요" else "Good pick for this hour", color = Ink, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Headline(place.name[l] ?: place.name.values.first(), 34)
            Spacer(Modifier.height(8.dp))
            Body(place.note[l] ?: "", size = 17)
            Body(place.address, size = 14)
            Spacer(Modifier.height(18.dp))
            Text(if (lang == Lang.KO) "\"4명, 도착 시간으로 예약할까요?\"" else "\"Reserve for four at arrival time?\"", color = Ink, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(if (lang == Lang.KO) "📞 예약 전화 걸기" else "📞 Call to reserve", primary = true, onClick = onReserve)
                ActionButton(if (lang == Lang.KO) "다른 곳 들어볼게" else "Other options", onClick = onOther)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "함께 보면 좋은 곳" else "Nearby, worth pairing", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            alternatives.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(30.dp).height(30.dp).background(Chip, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text("${i + 2}", color = ChipInk, fontSize = 13.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(p.name[l] ?: "", color = Ink, fontSize = 16.sp); Text(p.note[l] ?: "", color = Quiet, fontSize = 13.sp, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Body(if (lang == Lang.KO) "외국인 모드: 전화 대신 온라인 예약 링크를 폰으로 보내고, 도착 10분 전 한국어 한마디를 연습해요" else "Foreign mode: a booking link goes to your phone instead, and 10 minutes before arrival we practise one Korean phrase", size = 13)
        }
    }
}

/** 3. Where next */
@Composable
fun WhereNextScreen(leftAfterMin: Int, suggestions: List<Place>, disappointed: Boolean, lang: Lang, onGo: (Place) -> Unit, onHome: () -> Unit) {
    val l = lang.tag
    val top = suggestions.firstOrNull()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Text(
                if (lang == Lang.KO) (if (disappointed) "\"기대와 달랐나요? 차로 10분 안에\n가볼 만한 곳이 있어요.\"" else "\"다시 출발하시네요. 차로 10분 안에\n가볼 만한 곳이 있어요.\"")
                else (if (disappointed) "\"Not what you hoped? There are places\nwithin ten minutes by car.\"" else "\"Setting off again. There are places\nwithin ten minutes by car.\""),
                color = Ink, fontSize = 22.sp, lineHeight = 32.sp
            )
            Spacer(Modifier.height(16.dp))
            top?.let {
                Headline(it.name[l] ?: "", 36)
                Body(it.note[l] ?: "", size = 17)
                it.typicalStayMin?.let { m -> Body(if (lang == Lang.KO) "방문객은 보통 ${m}분 머물러요 (체류 데이터)" else "Visitors usually stay about $m min (dwell data)", size = 14) }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                top?.let { ActionButton(if (lang == Lang.KO) "\"거기로 가자\"" else "\"Let's go there\"", primary = true) { onGo(it) } }
                ActionButton(if (lang == Lang.KO) "그냥 집으로" else "Head home", onClick = onHome)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "다른 제안" else "Other suggestions", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            suggestions.drop(1).forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clickable { onGo(p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(30.dp).height(30.dp).background(Chip, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text("${i + 2}", color = ChipInk, fontSize = 13.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(p.name[l] ?: "", color = Ink, fontSize = 16.sp); Text(p.note[l] ?: "", color = Quiet, fontSize = 13.sp, maxLines = 1) }
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
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.2f).fillMaxHeight()) {
            Text(if (lang == Lang.KO) "오늘의 여행 일지" else "Today's travel log", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            RouteCanvas(log, Modifier.fillMaxWidth().weight(1f))
            Body(if (lang == Lang.KO) "원의 크기 = 머문 시간" else "Circle size = time spent", size = 12)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Text("“${log.headline(lang)}”", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kpi("%.0f km".format(log.distanceKm), if (lang == Lang.KO) "총 주행" else "driven")
                    Kpi("${log.drivingMin / 60}h ${log.drivingMin % 60}m", if (lang == Lang.KO) "이동" else "moving")
                    Kpi("${log.stayMin / 60}h ${log.stayMin % 60}m", if (lang == Lang.KO) "머문 시간" else "stayed")
                    Kpi("${log.episodesHeard}", if (lang == Lang.KO) "들은 이야기" else "stories")
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Canvas(modifier.background(Color(0xFF0E1A2A), RoundedCornerShape(18.dp)).padding(16.dp)) {
        val pts = log.trace
        if (pts.size < 2) return@Canvas
        val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
        fun x(lon: Double) = ((lon - minLon) / ((maxLon - minLon).coerceAtLeast(1e-4)) * size.width).toFloat()
        fun y(lat: Double) = (size.height - (lat - minLat) / ((maxLat - minLat).coerceAtLeast(1e-4)) * size.height).toFloat()
        val path = Path().apply { moveTo(x(pts[0].lon), y(pts[0].lat)); pts.drop(1).forEach { lineTo(x(it.lon), y(it.lat)) } }
        drawPath(path, Accent, style = Stroke(width = 6f, pathEffect = PathEffect.cornerPathEffect(30f)))
        drawCircle(Ink, 10f, Offset(x(pts[0].lon), y(pts[0].lat)))
        val longest = log.longest
        log.visits.forEach { v ->
            val r = (14 + v.stayMin.coerceAtMost(120) * 0.4f)
            val c = if (v == longest) Warm else Accent
            drawCircle(c.copy(alpha = 0.35f), r, Offset(x(v.point.lon), y(v.point.lat)))
            drawCircle(c, r, Offset(x(v.point.lon), y(v.point.lat)), style = Stroke(2f))
        }
    }
}

/** 5. Kids overlay panel (shown inside Playing when audience == KIDS) */
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
