package kr.roaddocent.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kr.roaddocent.core.Lang
import kr.roaddocent.log.TripLog
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** "오늘의 여행" share card: route as a line, stops as circles sized by dwell time, totals. 1080×1350 (feed ratio). */
object ShareCard {
    fun render(log: TripLog, lang: Lang, date: String): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0B1526"))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4EFE6"); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val quiet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8FA0B8") }
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9A24B") }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5AA9FF"); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9A24B"); alpha = 190 }

        ink.textSize = 34f; gold.textSize = 30f; quiet.textSize = 30f
        c.drawText("ROAD DOCENT", 72f, 110f, gold)
        ink.textSize = 64f
        c.drawText(if (lang == Lang.KO) "오늘의 여행" else "Today's journey", 72f, 190f, ink)
        c.drawText(date, 72f, 250f, quiet)

        // map area
        val area = RectF(72f, 300f, 1008f, 960f)
        val bg = Paint().apply { color = Color.parseColor("#0F1F38") }
        c.drawRoundRect(area, 32f, 32f, bg)
        val pts = log.trace.ifEmpty { log.visits.map { it.point } }
        if (pts.size >= 2) {
            val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }; val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
            val sx = (area.width() - 120f) / max(1e-6, maxLon - minLon).toFloat(); val sy = (area.height() - 120f) / max(1e-6, maxLat - minLat).toFloat()
            val sc = min(sx, sy)
            fun x(lon: Double) = area.left + 60f + ((lon - minLon) * sc).toFloat() + (area.width() - 120f - ((maxLon - minLon) * sc).toFloat()) / 2
            fun y(lat: Double) = area.bottom - 60f - ((lat - minLat) * sc).toFloat() - (area.height() - 120f - ((maxLat - minLat) * sc).toFloat()) / 2
            val path = Path(); pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.lon), y(p.lat)) else path.lineTo(x(p.lon), y(p.lat)) }
            c.drawPath(path, accent)
            val maxStay = max(1, log.visits.maxOfOrNull { it.stayMin } ?: 1)
            ink.textSize = 28f
            log.visits.forEach { v ->
                val r = 14f + 40f * sqrt(v.stayMin.toFloat() / maxStay)
                c.drawCircle(x(v.point.lon), y(v.point.lat), r, dot)
                c.drawText("${v.name} ${v.stayMin}m", x(v.point.lon) + r + 10f, y(v.point.lat) + 10f, ink)
            }
        } else {
            quiet.textSize = 32f; c.drawText(if (lang == Lang.KO) "아직 기록이 없어요" else "No trace yet", area.left + 40f, area.centerY(), quiet)
        }

        // KPIs
        val kpis = listOf(
            "%.0f km".format(log.distanceKm) to (if (lang == Lang.KO) "주행" else "driven"),
            "${log.drivingMin / 60}h ${log.drivingMin % 60}m" to (if (lang == Lang.KO) "이동" else "moving"),
            "${log.stayMin / 60}h ${log.stayMin % 60}m" to (if (lang == Lang.KO) "머묾" else "stayed"),
            "${log.episodesHeard}" to (if (lang == Lang.KO) "이야기" else "stories"),
            "${log.quizzesRight}" to (if (lang == Lang.KO) "퀴즈" else "quizzes"))
        val cw = (1008f - 72f) / kpis.size
        kpis.forEachIndexed { i, (v, l) ->
            ink.textSize = 48f; quiet.textSize = 26f
            c.drawText(v, 72f + i * cw, 1080f, ink); c.drawText(l, 72f + i * cw, 1120f, quiet)
        }
        ink.textSize = 36f
        c.drawText("“${log.headline(lang)}”", 72f, 1220f, ink)
        quiet.textSize = 26f
        c.drawText(if (lang == Lang.KO) "자동차의 목적지는 예술작품이 되어 간다." else "Every destination becomes a work of art.", 72f, 1290f, gold)
        return bmp
    }

    /** Save to cache and open the system share sheet. */
    fun share(context: Context, log: TripLog, lang: Lang, date: String) {
        val bmp = render(log, lang, date)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "roaddocent_$date.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(send, if (lang == Lang.KO) "오늘의 여행 공유" else "Share today's journey").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
