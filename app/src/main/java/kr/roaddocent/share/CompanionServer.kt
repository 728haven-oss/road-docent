package kr.roaddocent.share

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * Rear-seat companion: the car runs a tiny HTTP server on its hotspot/LAN; a passenger scans the QR once and their phone
 * shows the current story title, the quiz card and the sticker count (polls /state every 2 s). The car screen stays minimal.
 */
class CompanionServer(private val port: Int = 8765) {
    data class State(val title: String = "", val text: String = "", val quiz: String? = null, val answer: String? = null, val stickers: Int = 0, val lang: String = "ko", val phrases: List<Pair<String, String>> = emptyList())
    private val state = AtomicReference(State())
    private var server: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun update(s: State) = state.set(s)

    fun start(): Boolean = runCatching {
        if (server != null) return true
        server = ServerSocket(port)
        job = scope.launch { while (true) { val sock = server?.accept() ?: break; launch { handle(sock) } } }
        true
    }.getOrDefault(false)

    fun stop() { job?.cancel(); runCatching { server?.close() }; server = null }

    fun url(): String? = ip()?.let { "http://$it:$port/" }

    private fun ip(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    fun qr(size: Int = 400): Bitmap? = url()?.let { u ->
        val m = QRCodeWriter().encode(u, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { b -> for (x in 0 until size) for (y in 0 until size) b.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE) }
    }

    private fun handle(sock: Socket) = sock.use { s ->
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        val line = reader.readLine() ?: return
        val path = line.split(" ").getOrNull(1) ?: "/"
        val (type, body) = if (path.startsWith("/state")) "application/json" to json() else "text/html; charset=utf-8" to PAGE
        val bytes = body.toByteArray(Charsets.UTF_8)
        s.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
        s.getOutputStream().write(bytes); s.getOutputStream().flush()
    }

    private fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    private fun json(): String { val st = state.get(); return """{"title":"${esc(st.title)}","text":"${esc(st.text)}","quiz":${st.quiz?.let { "\"${esc(it)}\"" } ?: "null"},"answer":${st.answer?.let { "\"${esc(it)}\"" } ?: "null"},"stickers":${st.stickers},"lang":"${st.lang}","phrases":[${st.phrases.joinToString(",") { "[\"${esc(it.first)}\",\"${esc(it.second)}\"]" }}]}""" }

    private val PAGE = """<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Road Docent · 동승자</title>
<style>body{margin:0;background:#0B1526;color:#F4EFE6;font-family:-apple-system,'Noto Sans KR',sans-serif;padding:20px}h1{font-size:22px;margin:0 0 6px}p{line-height:1.5}.card{background:#132340;border-radius:16px;padding:16px;margin:12px 0}.q{color:#5AA9FF;font-weight:700}.st{font-size:40px}.ph{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #243654}</style></head>
<body><p style="color:#C9A24B;letter-spacing:2px;font-size:12px">ROAD DOCENT · 동승자 화면</p><h1 id="t">연결 중…</h1><p id="x"></p>
<div class="card" id="qc" style="display:none"><div class="q">❓ 퀴즈</div><p id="q"></p><p id="a" style="color:#BFD0E6"></p></div>
<div class="card"><div>스티커</div><div class="st" id="s">⭐ 0</div></div>
<div class="card" id="pc" style="display:none"><div class="q">🗣 오늘의 한국어</div><div id="p"></div></div>
<script>async function tick(){try{const r=await fetch('/state?'+Date.now());const s=await r.json();document.getElementById('t').textContent=s.title||'…';document.getElementById('x').textContent=s.text||'';
const qc=document.getElementById('qc');if(s.quiz){qc.style.display='block';document.getElementById('q').textContent=s.quiz;document.getElementById('a').textContent=s.answer?('정답: '+s.answer):'생각해 보세요…'}else qc.style.display='none';
document.getElementById('s').textContent='⭐ '+s.stickers;const pc=document.getElementById('pc');if(s.phrases&&s.phrases.length){pc.style.display='block';document.getElementById('p').innerHTML=s.phrases.map(p=>'<div class="ph"><b>'+p[0]+'</b><span>'+p[1]+'</span></div>').join('')}}catch(e){}}
tick();setInterval(tick,2000);</script></body></html>"""
}
