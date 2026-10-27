package kr.roaddocent.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.roaddocent.core.GeoPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A labelled point on the illustrated map. */
data class MapMark(val point: GeoPoint, val label: String, val icon: String = "")

/**
 * Simplified basemap of South Korea for illustration — not for navigation (the car's navigation does that).
 * Coastline, the Han River through Seoul, mountains, cities and Seoul landmarks; the view zooms to the route.
 */
object Basemap {
    private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon)
    /** Mainland outline, clockwise from the north-east coast (simplified, ~25 km precision). */
    val land = listOf(
        p(38.62, 128.35), p(38.20, 128.59), p(37.90, 128.80), p(37.75, 128.95), p(37.45, 129.15), p(37.10, 129.37), p(36.70, 129.47),
        p(36.35, 129.40), p(36.03, 129.57), p(35.55, 129.45), p(35.18, 129.20), p(35.05, 128.95), p(34.85, 128.55), p(34.88, 128.05),
        p(34.74, 127.73), p(34.60, 127.30), p(34.55, 126.90), p(34.30, 126.52), p(34.65, 126.30), p(34.80, 126.38), p(35.10, 126.30),
        p(35.40, 126.45), p(35.75, 126.55), p(35.98, 126.62), p(36.25, 126.52), p(36.50, 126.30), p(36.80, 126.15), p(36.98, 126.45),
        p(37.00, 126.80), p(37.25, 126.62), p(37.45, 126.58), p(37.60, 126.55), p(37.75, 126.45), p(37.95, 126.68), p(38.10, 127.10),
        p(38.30, 127.55), p(38.33, 128.00), p(38.62, 128.35)
    )
    val jeju = listOf(p(33.56, 126.17), p(33.52, 126.55), p(33.47, 126.95), p(33.30, 126.88), p(33.22, 126.55), p(33.23, 126.20), p(33.40, 126.15), p(33.56, 126.17))
    /** Han River from Haengju to Misa (Seoul). */
    val hanRiver = listOf(
        p(37.612, 126.770), p(37.595, 126.815), p(37.575, 126.855), p(37.555, 126.885), p(37.540, 126.915), p(37.531, 126.935),
        p(37.520, 126.955), p(37.515, 126.975), p(37.512, 126.995), p(37.522, 127.010), p(37.530, 127.030), p(37.533, 127.055),
        p(37.540, 127.085), p(37.548, 127.110), p(37.558, 127.135), p(37.575, 127.175)
    )
    data class Feature(val point: GeoPoint, val label: String, val icon: String, val minSpan: Double, val maxSpan: Double)
    /** Shown only when the view's span (degrees latitude) is within [minSpan, maxSpan]. */
    val features = listOf(
        Feature(p(37.593, 126.973), "북악산", "⛰", 0.0, 0.35), Feature(p(37.585, 126.958), "인왕산", "⛰", 0.0, 0.2),
        Feature(p(37.5512, 126.9882), "남산", "🗼", 0.0, 0.4), Feature(p(37.444, 126.963), "관악산", "⛰", 0.0, 0.5),
        Feature(p(37.5665, 126.9780), "시청", "🏢", 0.0, 0.12), Feature(p(37.5260, 126.9250), "여의도", "🌳", 0.0, 0.3),
        Feature(p(37.5113, 127.0980), "잠실", "🏟", 0.0, 0.3), Feature(p(37.5759, 126.9769), "광화문", "🏯", 0.0, 0.1),
        Feature(p(37.5665, 126.978), "서울", "🏙", 0.35, 9.0), Feature(p(37.456, 126.705), "인천", "✈", 0.25, 9.0),
        Feature(p(37.658, 126.832), "고양", "🏙", 0.2, 3.0), Feature(p(37.342, 127.920), "원주", "🏘", 0.6, 9.0),
        Feature(p(37.370, 128.390), "평창", "🏔", 0.6, 9.0), Feature(p(37.752, 128.876), "강릉", "🌊", 0.6, 9.0),
        Feature(p(37.184, 128.462), "영월", "🏞", 0.6, 9.0), Feature(p(37.132, 128.191), "제천", "🏘", 0.6, 9.0),
        Feature(p(37.881, 127.730), "춘천", "🏘", 0.6, 9.0), Feature(p(37.690, 128.750), "대관령", "⛰", 0.5, 9.0),
        Feature(p(38.120, 128.460), "설악산", "⛰", 0.8, 9.0), Feature(p(37.790, 128.540), "오대산", "⛰", 0.8, 9.0),
        Feature(p(37.370, 128.060), "치악산", "⛰", 0.8, 9.0), Feature(p(36.960, 128.480), "소백산", "⛰", 0.8, 9.0),
        Feature(p(36.350, 127.385), "대전", "🏙", 1.5, 9.0), Feature(p(35.180, 129.075), "부산", "🏙", 1.5, 9.0)
    )
}

private val Land = Color(0xFFF1EDE2)
private val LandEdge = Color(0xFFD9D1BE)
private val Sea = Color(0xFFCFE6F2)
private val River = Color(0xFF9CCDE6)
private val Grid = Color(0x14002C5F)
private val RouteDone = Color(0xFF002C5F)
private val RouteLeft = Color(0xFF6E8BB0)

@Composable
fun IllustratedMap(route: List<GeoPoint>, here: GeoPoint?, marks: List<MapMark>, start: String, dest: String, modifier: Modifier = Modifier, visitRadii: List<Pair<GeoPoint, Float>> = emptyList()) {
    val tm = rememberTextMeasurer()
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Sea)) {
        Canvas(Modifier.fillMaxSize()) {
            val pts = route + listOfNotNull(here) + marks.map { it.point } + visitRadii.map { it.first }
            if (pts.isEmpty()) return@Canvas
            // ---- projection: fit the route bbox (+ padding), equirectangular with cos(lat) correction
            var minLat = pts.minOf { it.lat }; var maxLat = pts.maxOf { it.lat }; var minLon = pts.minOf { it.lon }; var maxLon = pts.maxOf { it.lon }
            val k = cos(Math.toRadians((minLat + maxLat) / 2))
            val spanLat0 = max(maxLat - minLat, 0.01); val spanLon0 = max((maxLon - minLon) * k, 0.01)
            val padLat = spanLat0 * 0.22 + 0.004; val padLon = spanLon0 * 0.18 / k + 0.004
            minLat -= padLat; maxLat += padLat; minLon -= padLon; maxLon += padLon
            val sx = size.width / ((maxLon - minLon) * k); val sy = size.height / (maxLat - minLat)
            val sc = min(sx, sy)
            val ox = (size.width - (maxLon - minLon) * k * sc) / 2; val oy = (size.height - (maxLat - minLat) * sc) / 2
            fun x(p: GeoPoint) = (ox + (p.lon - minLon) * k * sc).toFloat()
            fun y(p: GeoPoint) = (size.height - oy - (p.lat - minLat) * sc).toFloat()
            fun o(p: GeoPoint) = Offset(x(p), y(p))
            val viewSpan = (size.height / sc)   // degrees latitude visible

            clipRect {
                // land
                listOf(Basemap.land, Basemap.jeju).forEach { poly ->
                    val path = Path().apply { moveTo(x(poly[0]), y(poly[0])); poly.drop(1).forEach { lineTo(x(it), y(it)) }; close() }
                    drawPath(path, Land); drawPath(path, LandEdge, style = Stroke(width = 2f))
                }
                // faint grid paper
                val step = size.width / 8
                var gx = 0f; while (gx < size.width) { drawLine(Grid, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
                var gy = 0f; while (gy < size.height) { drawLine(Grid, Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
                // Han River (width scales with zoom)
                val rw = (0.0016 * sc).toFloat().coerceIn(2f, 26f)
                val river = Path().apply { moveTo(x(Basemap.hanRiver[0]), y(Basemap.hanRiver[0])); Basemap.hanRiver.drop(1).forEach { lineTo(x(it), y(it)) } }
                drawPath(river, River, style = Stroke(width = rw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (viewSpan < 0.5) label(tm, "한강", Offset(x(Basemap.hanRiver[6]) - 10f, y(Basemap.hanRiver[6]) + rw), Color(0xFF3E87AD), 10f, italic = true)
                // features for this zoom
                Basemap.features.filter { viewSpan >= it.minSpan && viewSpan <= it.maxSpan }.forEach { f ->
                    val c = o(f.point)
                    if (c.x < -20 || c.y < -20 || c.x > size.width + 20 || c.y > size.height + 20) return@forEach
                    pin(tm, f.icon, f.label, c, 15f, Color(0xFF6B6455), 10f)
                }
                // route: white casing, driven part solid navy, remaining lighter dashed
                if (route.size >= 2) {
                    val full = Path().apply { moveTo(x(route[0]), y(route[0])); route.drop(1).forEach { lineTo(x(it), y(it)) } }
                    drawPath(full, Color.White, style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(full, RouteLeft, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                    here?.let { h ->
                        val idx = route.indices.minByOrNull { val d = route[it]; (d.lat - h.lat) * (d.lat - h.lat) + (d.lon - h.lon) * (d.lon - h.lon) } ?: 0
                        val done = Path().apply { moveTo(x(route[0]), y(route[0])); route.drop(1).take(idx).forEach { lineTo(x(it), y(it)) }; lineTo(x(h), y(h)) }
                        drawPath(done, RouteDone, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                // visits (travel log): circle size = time spent
                visitRadii.forEach { (p, r) -> drawCircle(Color(0x55E39A2D), r, o(p)); drawCircle(Color(0xFFE39A2D), r, o(p), style = Stroke(3f)) }
                // story marks — a label is skipped if it would overlap one already drawn (the number still shows)
                val taken = mutableListOf<androidx.compose.ui.geometry.Rect>()
                // reserve the start/destination name areas so stop labels never cover them
                fun reserve(name: String, c: Offset) { if (name.isBlank()) return; val l = tm.measure(name, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)); taken += androidx.compose.ui.geometry.Rect(c + Offset(-l.size.width / 2f - 6f, -40f), androidx.compose.ui.geometry.Size(l.size.width + 12f, l.size.height + 50f)) }
                if (route.isNotEmpty()) { reserve(dest, o(route.last())); reserve(start, o(route.first()) + Offset(0f, 8f)) }
                marks.forEachIndexed { i, m ->
                    val c = o(m.point)
                    drawCircle(Color.White, 15f, c); drawCircle(Color(0xFF00AAD2), 15f, c, style = Stroke(4f))
                    val ln0 = tm.measure("${i + 1}", TextStyle(color = Color(0xFF002C5F), fontSize = 11.sp, fontWeight = FontWeight.Bold)); drawText(ln0, topLeft = c - Offset(ln0.size.width / 2f, ln0.size.height / 2f))
                    if (m.label.isNotBlank()) {
                        val ln = tm.measure(m.label, TextStyle(color = Color(0xFF002C5F), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        val right = c.x + 22f + ln.size.width < size.width
                        val tl = Offset(if (right) c.x + 22f else c.x - 22f - ln.size.width, c.y - ln.size.height / 2f)
                        val rect = androidx.compose.ui.geometry.Rect(tl - Offset(6f, 3f), androidx.compose.ui.geometry.Size(ln.size.width + 12f, ln.size.height + 6f))
                        if (taken.any { it.overlaps(rect) }) return@forEachIndexed
                        taken += rect
                        drawRoundRect(Color(0xF2FFFFFF), tl - Offset(6f, 3f), androidx.compose.ui.geometry.Size(ln.size.width + 12f, ln.size.height + 6f), androidx.compose.ui.geometry.CornerRadius(12f, 12f))
                        drawText(ln, topLeft = tl)
                    }
                }
                // start / destination
                if (route.isNotEmpty()) {
                    val s = o(route.first()); drawCircle(Color.White, 12f, s); drawCircle(RouteDone, 8f, s)
                    if (start.isNotBlank()) pin(tm, "", start, s + Offset(0f, 8f), 0f, RouteDone, 12f, bold = true, chip = true)
                    val d = o(route.last())
                    pin(tm, "🏁", dest, d, 20f, Color(0xFFB36B00), 12f, bold = true, chip = true)
                }
                // car
                here?.let { h -> val c = o(h); drawCircle(Color(0x3300AAD2), 34f, c); drawCircle(Color.White, 20f, c)
                    val lc = tm.measure("🚗", TextStyle(fontSize = 16.sp)); drawText(lc, topLeft = c - Offset(lc.size.width / 2f, lc.size.height / 2f)) }
            }
            // compass + scale
            label(tm, "N ↑", Offset(size.width - 40f, 10f), Color(0xFF6B6455), 11f, bold = true)
            val kmPerPx = 111.0 / sc
            val niceKm = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { it / kmPerPx > size.width * 0.18 } ?: 100.0
            val bar = (niceKm / kmPerPx).toFloat()
            drawLine(Color(0xFF6B6455), Offset(14f, size.height - 14f), Offset(14f + bar, size.height - 14f), 3f)
            label(tm, if (niceKm < 1) "${(niceKm * 1000).toInt()} m" else "${niceKm.toInt()} km", Offset(14f, size.height - 34f), Color(0xFF6B6455), 10f)
        }
    }
}

/** Icon centred above the point, name centred just below it — measured, so they never overlap. */
private fun DrawScope.pin(tm: TextMeasurer, icon: String, name: String, c: Offset, iconSp: Float, nameColor: Color, nameSp: Float, bold: Boolean = false, chip: Boolean = false) {
    if (icon.isNotEmpty()) { val li = tm.measure(icon, TextStyle(fontSize = iconSp.sp)); drawText(li, topLeft = c - Offset(li.size.width / 2f, li.size.height.toFloat() - 4f)) }
    if (name.isNotEmpty()) {
        val ln = tm.measure(name, TextStyle(color = nameColor, fontSize = nameSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
        val tl = c + Offset(-ln.size.width / 2f, 4f)
        if (chip) drawRoundRect(Color(0xE6FFFFFF), tl - Offset(5f, 2f), androidx.compose.ui.geometry.Size(ln.size.width + 10f, ln.size.height + 4f), androidx.compose.ui.geometry.CornerRadius(10f, 10f))
        drawText(ln, topLeft = tl)
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, color: Color, sizeSp: Float, bold: Boolean = false, italic: Boolean = false, chip: Boolean = false) {
    val style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null)
    val layout = tm.measure(text, style)
    if (chip) drawRoundRect(Color(0xE6FFFFFF), at - Offset(4f, 2f), androidx.compose.ui.geometry.Size(layout.size.width + 8f, layout.size.height + 4f), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
    drawText(layout, topLeft = at)
}
