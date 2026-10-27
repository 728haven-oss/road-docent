package kr.roaddocent.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF0E1A2A))) {
        if (bmp != null) Image(bmp, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(pictogram(category), fontSize = (height * 0.38f).sp)
        }
        Text(title, Modifier.align(Alignment.BottomStart).background(Color(0x990B1526), RoundedCornerShape(topEnd = 12.dp)).padding(horizontal = 12.dp, vertical = 6.dp), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Trip map: the route polyline with start, destination, story points and the car's current position. */
@Composable
fun TripMap(route: List<GeoPoint>, here: GeoPoint?, points: List<Pair<GeoPoint, String>>, startName: String, destName: String, modifier: Modifier = Modifier) {
    val all = route + listOfNotNull(here) + points.map { it.first }
    Box(modifier.background(Color(0xFF0E1A2A), RoundedCornerShape(18.dp))) {
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
                drawPath(path, Accent.copy(alpha = 0.9f), style = Stroke(width = 7f))
                drawCircle(Ink, 9f, Offset(x(route.first()), y(route.first())))
                drawCircle(Warm, 12f, Offset(x(route.last()), y(route.last())))
            }
            points.forEach { (p, _) -> drawCircle(Accent.copy(alpha = 0.25f), 16f, Offset(x(p), y(p))); drawCircle(Accent, 5f, Offset(x(p), y(p))) }
            here?.let { drawCircle(Color.White, 11f, Offset(x(it), y(it))); drawCircle(Accent, 7f, Offset(x(it), y(it))) }
        }
        Text(startName, Modifier.align(Alignment.BottomStart).padding(8.dp), color = Quiet, fontSize = 12.sp)
        Text("🏁 $destName", Modifier.align(Alignment.TopEnd).padding(8.dp), color = Warm, fontSize = 12.sp)
    }
}

/** Destination list entry: pictogram + names + drive time + themes, one tap to start, ℹ for the details sheet. */
@Composable
fun DestinationCard(g: Guide, lang: Lang, primary: Boolean, onStart: () -> Unit, onInfo: () -> Unit) {
    val name = g.destination.name[lang.tag] ?: g.id
    Row(Modifier.fillMaxWidth().background(if (primary) Accent.copy(alpha = 0.18f) else Chip, RoundedCornerShape(16.dp)).clickable(onClick = onStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val bmp = assetImage(g.destination.poiId.takeIf { it.endsWith(".jpg") || it.endsWith(".webp") })
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E1A2A)), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text(pictogram(g.destination.category), fontSize = 30.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text((g.startName[lang.tag] ?: "") + (if (lang == Lang.KO) "에서 ${g.driveMin}분 · " else " · ${g.driveMin} min · ") + g.episodes.count { it.trigger !is kr.roaddocent.core.Trigger.Reboard } + (if (lang == Lang.KO) "편" else " stories"), color = Quiet, fontSize = 13.sp, maxLines = 1)
            Text(g.themes.joinToString("  ") { Themes.label(it, lang) }, color = Quiet, fontSize = 12.sp, maxLines = 1)
        }
        if (g.knowBeforeYouGo.isNotEmpty()) Text("ℹ", Modifier.background(Color(0xFF0E1A2A), RoundedCornerShape(999.dp)).clickable(onClick = onInfo).padding(horizontal = 12.dp, vertical = 6.dp), color = Ink, fontSize = 16.sp)
    }
}
