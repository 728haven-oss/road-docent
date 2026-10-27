package kr.roaddocent.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.roaddocent.core.GeoPoint
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Themes
import kr.roaddocent.ui.theme.Accent
import kr.roaddocent.ui.theme.AccentInk
import kr.roaddocent.ui.theme.Chip
import kr.roaddocent.ui.theme.Ink
import kr.roaddocent.ui.theme.Quiet
import kr.roaddocent.ui.theme.Warm

/** Big flat pictogram per place category — the visual anchor when no photo is bundled. */
fun pictogram(category: String): String = when (category) {
    "palace", "temple" -> "🏯"; "museum", "gallery" -> "🏛️"; "beach" -> "🏖️"; "mountain", "observatory" -> "🏔️"
    "park", "village" -> "🌳"; "market", "food", "restaurant" -> "🍜"; "cafe" -> "☕"; "shopping" -> "🛍️"
    "car" -> "🚗"; "rest_area" -> "⛽"; "film" -> "🎬"; "freeride" -> "🪟"; else -> "📍"
}

/** Photo from assets/images/<name> if the guide ships one (public-domain / 공공누리 1유형 only), else null. */
@Composable
fun assetImage(name: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    return remember(name) {
        if (name.isNullOrBlank()) null else runCatching { ctx.assets.open("images/$name").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
}

/** Hero: photo when available, otherwise a pictogram tile with the place name. */
@Composable
fun Hero(image: String?, category: String, title: String, modifier: Modifier = Modifier, height: Int = 150) {
    val bmp = assetImage(image)
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(18.dp)).background(kr.roaddocent.ui.theme.MapBg)) {
        if (bmp != null) Image(bmp, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(pictogram(category), fontSize = (height * 0.38f).sp)
        }
        Text(title, Modifier.align(Alignment.BottomStart).background(androidx.compose.ui.graphics.Color(0xCCFFFFFF), RoundedCornerShape(topEnd = 12.dp)).padding(horizontal = 12.dp, vertical = 6.dp), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Trip map: the route polyline with start, destination, story points and the car's current position. */
@Composable
fun TripMap(route: List<GeoPoint>, here: GeoPoint?, points: List<Pair<GeoPoint, String>>, startName: String, destName: String, modifier: Modifier = Modifier) {
    val all = route + listOfNotNull(here) + points.map { it.first }
    Box(modifier.background(kr.roaddocent.ui.theme.MapBg, RoundedCornerShape(18.dp))) {
        Canvas(Modifier.fillMaxSize().padding(22.dp)) {
            if (all.size < 2) return@Canvas
            val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }; val minLon = all.minOf { it.lon }; val maxLon = all.maxOf { it.lon }
            val sx = size.width / (maxLon - minLon).coerceAtLeast(1e-4); val sy = size.height / (maxLat - minLat).coerceAtLeast(1e-4)
            val sc = minOf(sx, sy).toFloat()
            val ox = (size.width - ((maxLon - minLon) * sc).toFloat()) / 2; val oy = (size.height - ((maxLat - minLat) * sc).toFloat()) / 2
            fun x(p: GeoPoint) = ox + ((p.lon - minLon) * sc).toFloat()
            fun y(p: GeoPoint) = size.height - oy - ((p.lat - minLat) * sc).toFloat()
            if (route.size >= 2) {
                val path = Path().apply { moveTo(x(route[0]), y(route[0])); route.drop(1).forEach { lineTo(x(it), y(it)) } }
                drawPath(path, kr.roaddocent.ui.theme.Navy, style = Stroke(width = 7f))
                drawCircle(kr.roaddocent.ui.theme.Navy, 9f, Offset(x(route.first()), y(route.first())))
                drawCircle(Warm, 12f, Offset(x(route.last()), y(route.last())))
            }
            points.forEach { (p, _) -> drawCircle(kr.roaddocent.ui.theme.Sky.copy(alpha = 0.25f), 16f, Offset(x(p), y(p))); drawCircle(kr.roaddocent.ui.theme.Sky, 6f, Offset(x(p), y(p))) }
            here?.let { drawCircle(kr.roaddocent.ui.theme.Sky.copy(alpha = 0.3f), 22f, Offset(x(it), y(it))); drawCircle(Color.White, 11f, Offset(x(it), y(it))); drawCircle(kr.roaddocent.ui.theme.Sky, 7f, Offset(x(it), y(it))) }
        }
        Text("● $startName", Modifier.align(Alignment.BottomStart).padding(8.dp), color = kr.roaddocent.ui.theme.Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("🏁 $destName", Modifier.align(Alignment.TopEnd).padding(8.dp), color = kr.roaddocent.ui.theme.Warm, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Destination list entry: pictogram + names + drive time + themes, one tap to start, ℹ for the details sheet. */
@Composable
fun DestinationCard(g: Guide, lang: Lang, primary: Boolean, onStart: () -> Unit, onInfo: () -> Unit) {
    val name = g.destination.name[lang.tag] ?: g.id
    Row(Modifier.fillMaxWidth().background(if (primary) Accent.copy(alpha = 0.18f) else Chip, RoundedCornerShape(16.dp)).clickable(onClick = onStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val bmp = assetImage(g.destination.poiId.takeIf { it.endsWith(".jpg") || it.endsWith(".webp") })
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(kr.roaddocent.ui.theme.MapBg), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text(pictogram(g.destination.category), fontSize = 30.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text((g.startName[lang.tag] ?: "") + (if (lang == Lang.KO) "에서 ${g.driveMin}분 · " else " · ${g.driveMin} min · ") + g.episodes.count { it.trigger !is kr.roaddocent.core.Trigger.Reboard } + (if (lang == Lang.KO) "편" else " stories"), color = Quiet, fontSize = 13.sp, maxLines = 1)
            Text(g.themes.joinToString("  ") { Themes.label(it, lang) }, color = Quiet, fontSize = 12.sp, maxLines = 1)
        }
        if (g.knowBeforeYouGo.isNotEmpty()) Text("ℹ", Modifier.background(kr.roaddocent.ui.theme.MapBg, RoundedCornerShape(999.dp)).clickable(onClick = onInfo).padding(horizontal = 12.dp, vertical = 6.dp), color = Ink, fontSize = 16.sp)
    }
}

/** Fixed-column grid without nested scrolling (rows of equal-weight cells). */
@Composable
fun <T> Grid(items: List<T>, columns: Int, modifier: Modifier = Modifier, gap: Int = 10, cell: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Picture tile: big emoji over a label, selectable. Used for themes, audiences, connection modes. */
@Composable
fun PictureTile(icon: String, label: String, selected: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().background(if (selected) kr.roaddocent.ui.theme.SkySoft else kr.roaddocent.ui.theme.Card, shape)
        .border(if (selected) 2.dp else 1.dp, if (selected) kr.roaddocent.ui.theme.Navy else kr.roaddocent.ui.theme.CardBorder, shape)
        .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) kr.roaddocent.ui.theme.Navy else Ink, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        sub?.let { Text(it, color = Quiet, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

/** "주차: 경복궁 주차장…" → (🅿️, "주차", "경복궁 주차장…"). Turns the arrival checklist into icon cards. */
fun arrivalItem(line: String): Triple<String, String, String> {
    val i = line.indexOf(':'); val label = if (i in 1..12) line.substring(0, i).trim() else ""; val body = if (label.isNotEmpty()) line.substring(i + 1).trim() else line
    val icon = when {
        label.contains("주차") || label.startsWith("Parking") -> "🅿️"; label.contains("입장") || label.startsWith("Entry") -> "🎫"
        label.contains("순서") || label.contains("동선") || label.startsWith("Order") -> "🗺️"; label.contains("시간 맞추기") || label.startsWith("Timing") -> "⏰"
        label.contains("꼭 볼") || label.startsWith("Must") -> "⭐"; label.contains("아이") || label.startsWith("With kids") -> "🧒"
        label.contains("예절") || label.startsWith("Etiquette") -> "🙏"; label.contains("점심") || label.contains("식사") || label.startsWith("Lunch") -> "🍽️"
        label.contains("머무") || label.startsWith("Stay") -> "⏳"; label.contains("다음") || label.startsWith("Next") -> "➡️"
        label.contains("숍") || label.contains("Shop") -> "🛍️"; label.contains("예약") -> "📅"; else -> "📌"
    }
    return Triple(icon, label.ifEmpty { "안내" }, body)
}

/** The one line worth saying aloud on arrival: the must-see, else the first line. */
fun arrivalHighlight(lines: List<String>): String = (lines.firstOrNull { it.startsWith("꼭 볼") || it.startsWith("Must") } ?: lines.firstOrNull() ?: "").let { arrivalItem(it).third }
