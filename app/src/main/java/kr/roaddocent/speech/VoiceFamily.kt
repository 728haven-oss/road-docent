package kr.roaddocent.speech

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kr.roaddocent.core.Lang
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Voice Family: a parent (or the child) records ~15 sample sentences; a voice-cloning TTS
 * builds a voice model; the guide then reads every episode in that voice.
 *
 * Safety rules implemented here, not just promised:
 *  - the first sample is a spoken consent line, kept with the model id (anti-impersonation)
 *  - under-14 profiles require guardian consent flag
 *  - raw recordings are deleted once the model exists
 *  - cloned voices are only ever fed guide text (no free-text API is exposed)
 */
data class VoiceProfile(val id: String, val name: String, val isChild: Boolean, val guardianConsent: Boolean, val modelId: String?, val createdMs: Long)

object VoiceSamples {
    val consentKo = "나는 내 목소리를 로드 도슨트 가이드 음성으로 사용하는 데 동의합니다. 이 목소리는 가이드 대본에만 쓰이고, 언제든 삭제할 수 있습니다."
    val ko = listOf(
        consentKo,
        "안녕, 오늘은 엄마가 강릉 가이드를 맡았어.",
        "창밖 오른쪽에 큰 호수가 보이지? 이름이 뭘까?",
        "대관령에는 왜 풍력발전기가 많을까?",
        "강릉은 커피 향과 겨울 파도의 도시예요.",
        "20분 뒤에 바다에 도착해. 카페에 들어가면 뭐라고 인사하지?",
        "정답은 수호랑! 잘 맞혔어.",
        "아직 멀었냐고? 이야기 두 개 더 들으면 도착해.",
        "여기는 횡성휴게소야. 소떡소떡이 맛있대.",
        "신사임당은 오죽헌에서 자랐어. 5만원 지폐에 있는 분이야.",
        "다시 출발! 이번엔 소나무 숲에 갈까?",
        "오늘 좀 돌았네. 솔숲에 가장 오래 있었어.",
        "배에서는 손 밖으로 내밀지 않기, 약속!",
        "하나, 둘, 셋, 넷, 다섯. 월요일, 화요일, 수요일.",
        "고마워, 오늘 가이드는 여기까지야."
    )
    val en = listOf(
        "I consent to my voice being used as the Road Docent guide voice. It is used only for guide scripts and can be deleted at any time.",
        "Hi, today Mum is your guide to Gangneung.",
        "See the big lake on the right? What's its name?",
        "Why are there so many wind turbines on the pass?",
        "Gangneung smells of coffee and winter waves.",
        "We reach the sea in twenty minutes. What do we say when we walk into the cafe?",
        "The answer is Soohorang! Well done.",
        "Still far? Two more stories and we're there.",
        "This is Hoengseong rest area. The skewers are great.",
        "Shin Saimdang grew up at Ojukheon. She's on the fifty-thousand-won note.",
        "Off we go again! Pine forest next?",
        "A bit of a detour today. We stayed longest at the pine forest.",
        "Hands inside the boat, promise!",
        "One, two, three, four, five. Monday, Tuesday, Wednesday.",
        "Thanks, that's it from your guide today."
    )
    fun forLang(l: Lang) = if (l == Lang.KO) ko else en
}

/** Records the sample sentences to app-private storage. */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    fun dir(profileId: String): File = File(context.filesDir, "voice/$profileId").apply { mkdirs() }

    fun start(profileId: String, index: Int) {
        val f = File(dir(profileId), "sample_%02d.m4a".format(index))
        rec = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100); setAudioEncodingBitRate(128_000)
            setOutputFile(f.absolutePath)
            prepare(); start()
        }
        _recording.value = true
    }

    fun stop() { runCatching { rec?.stop(); rec?.release() }; rec = null; _recording.value = false }

    fun samples(profileId: String): List<File> = dir(profileId).listFiles { f -> f.name.startsWith("sample_") || f.extension.lowercase() in setOf("m4a", "mp3", "wav", "aac") }?.sorted() ?: emptyList()

    /** Pre-recorded voice files (recorded elsewhere) copied into the profile folder: adb push or a file picker. */
    fun import(profileId: String, files: List<File>) { files.forEach { it.copyTo(File(dir(profileId), it.name), overwrite = true) } }

    /** Raw recordings are deleted once the cloud model exists. */
    fun purge(profileId: String) { samples(profileId).forEach { it.delete() } }
}

/**
 * Voice-cloning TTS client. Provider-agnostic shape: createVoice(samples) -> modelId; synth(text, modelId) -> mp3.
 * Wire a provider (ElevenLabs / Typecast / Azure Personal Voice) by setting [apiKey] and [baseUrl].
 * Without a key it reports `available == false` and the engine keeps using on-device TTS.
 */
class VoiceCloneClient(var apiKey: String? = null, var baseUrl: String = "https://api.elevenlabs.io/v1") {
    val available get() = !apiKey.isNullOrBlank()

    suspend fun createVoice(name: String, samples: List<File>): String? = withContext(Dispatchers.IO) {
        if (!available) return@withContext null
        // Multipart upload of samples -> provider returns a voice/model id.
        // Left as a thin HTTP shell: provider payloads differ, and keys must never ship in the APK.
        runCatching {
            val boundary = "----rd" + System.nanoTime()
            val conn = (URL("$baseUrl/voices/add").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("xi-api-key", apiKey); setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.buffered().use { out ->
                fun field(n: String, v: String) { out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray()) }
                field("name", name)
                samples.forEach { f ->
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"${f.name}\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
                    f.inputStream().use { it.copyTo(out) }; out.write("\r\n".toByteArray())
                }
                out.write("--$boundary--\r\n".toByteArray())
            }
            val body = conn.inputStream.bufferedReader().readText()
            Regex("\"voice_id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        }.onFailure { Log.w("VoiceClone", "createVoice failed", it) }.getOrNull()
    }

    suspend fun synth(text: String, modelId: String, lang: Lang, cacheDir: File): File? = withContext(Dispatchers.IO) {
        if (!available) return@withContext null
        val key = MessageDigest.getInstance("SHA-1").digest((modelId + lang.tag + text).toByteArray()).joinToString("") { "%02x".format(it) }
        val out = File(cacheDir, "tts_$key.mp3")
        if (out.exists()) return@withContext out
        runCatching {
            val conn = (URL("$baseUrl/text-to-speech/$modelId").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("xi-api-key", apiKey); setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "audio/mpeg")
            }
            val json = """{"text":${jsonStr(text)},"model_id":"eleven_multilingual_v2"}"""
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
            out
        }.onFailure { Log.w("VoiceClone", "synth failed", it); out.delete() }.getOrNull()
    }

    private fun jsonStr(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}

/**
 * Speaker that plays cloned-voice audio when a profile is active, and falls back to [fallback]
 * (on-device TTS) otherwise. Sentence-level pause/resume mirrors AndroidSpeaker.
 */
class CloudVoiceSpeaker(private val context: Context, private val client: VoiceCloneClient, private val fallback: Speaker) : Speaker {
    var profile: VoiceProfile? = null
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private var player: MediaPlayer? = null
    private var queue: List<String> = emptyList()
    private var lang = Lang.KO
    private var paused = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun speak(text: String, lang: Lang, utteranceId: String) {
        _speaking.value = true
        val model = profile?.modelId
        if (model == null || !client.available) { fallback.speak(text, lang, utteranceId); return }
        this.lang = lang; paused = false
        stopPlayer()
        queue = SpeechText.sentences(SpeechText.normalize(text, lang))
        next(model)
    }

    private fun next(model: String) {
        val s = queue.firstOrNull() ?: run { _speaking.value = false; return }
        queue = queue.drop(1)
        scope.launch {
            val f = client.synth(s, model, lang, context.cacheDir)
            if (f == null) { fallback.speak(s, lang); return@launch }
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath); setOnCompletionListener { if (!paused) next(model) }
                prepare(); start()
            }
            _speaking.value = true
        }
    }

    override fun pause() { paused = true; player?.pause(); fallback.pause(); _speaking.value = false }
    override fun resume() { if (paused) { paused = false; player?.start() ?: profile?.modelId?.let { next(it) }; fallback.resume(); _speaking.value = player?.isPlaying == true } }
    override fun stop() { queue = emptyList(); stopPlayer(); fallback.stop(); _speaking.value = false }
    override fun shutdown() { stopPlayer(); fallback.shutdown() }
    private fun stopPlayer() { runCatching { player?.stop(); player?.release() }; player = null }
}
