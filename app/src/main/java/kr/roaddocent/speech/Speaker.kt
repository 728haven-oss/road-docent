package kr.roaddocent.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roaddocent.core.Lang
import java.util.Locale

/**
 * Text-to-speech abstraction.
 * Today: Android platform TTS ([AndroidSpeaker]).
 * Target: Pleos TextToSpeech SDK once its status moves from "Not Available Yet" to "Request Required"
 * on Pleos Playground — swap the implementation, the engine does not change.
 */
interface Speaker {
    val speaking: StateFlow<Boolean>
    fun speak(text: String, lang: Lang, utteranceId: String = System.nanoTime().toString())
    /** Pause at sentence boundary (safety gate / navigation voice has priority). */
    fun pause()
    fun resume()
    fun stop()
    fun shutdown()
}

/**
 * Platform TTS, tuned to sound like someone talking rather than a list being read:
 *  - all sentences of a story are queued back to back (QUEUE_ADD), so the engine keeps its natural rhythm
 *    instead of a stiff gap and a prosody reset after every sentence;
 *  - the best installed (offline) Korean voice is chosen, at normal rate and pitch;
 *  - pause() still stops at the current sentence and resume() continues from it.
 */
class AndroidSpeaker(context: Context) : Speaker {
    private val tag = "Speaker"
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private var ready = false
    private var pending: Pair<String, Lang>? = null
    private var paused = false
    private var sentences: List<String> = emptyList()
    /** Index of the sentence being spoken now (from onStart), used to resume after pause(). */
    @Volatile private var current = 0
    /** Bumped on every new story / stop, so callbacks from a flushed batch are ignored. */
    @Volatile private var gen = 0
    private var lang = Lang.KO

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) Log.w(tag, "TTS init failed: $status") else runCatching { pickVoice(Lang.KO) }
        pending?.let { (t, l) -> pending = null; speak(t, l) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { parse(id)?.let { (g, i) -> if (g == gen) { current = i; _speaking.value = true } } }
            override fun onDone(id: String?) { parse(id)?.let { (g, i) -> if (g == gen && !paused && i >= sentences.lastIndex) _speaking.value = false } }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { onDone(id) }
            override fun onError(id: String?, errorCode: Int) { onDone(id) }
        })
    }

    private fun parse(id: String?): Pair<Int, Int>? = id?.removePrefix("rd-")?.split("-")?.takeIf { it.size == 2 }?.let { it[0].toIntOrNull()?.let { g -> it[1].toIntOrNull()?.let { i -> g to i } } }

    /** Highest-quality installed voice for the language; network voices only if nothing local exists. */
    private fun pickVoice(l: Lang) {
        val loc = if (l == Lang.KO) Locale.KOREAN else Locale.US
        tts.language = loc
        val best = tts.voices.orEmpty()
            .filter { it.locale.language == loc.language && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<android.speech.tts.Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.latency })
            .firstOrNull()
        best?.let { tts.voice = it }
        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)
    }

    override fun speak(text: String, lang: Lang, utteranceId: String) {
        // Report "speaking" immediately — onStart arrives later, and anything deciding "is the guide talking?"
        // in between would otherwise cut this story off.
        _speaking.value = true
        if (!ready) { pending = text to lang; return }
        if (lang != this.lang || tts.voice == null) runCatching { pickVoice(lang) }
        this.lang = lang
        sentences = SpeechText.sentences(SpeechText.normalize(text, lang))
        paused = false
        gen++
        queueFrom(0)
    }

    private fun queueFrom(start: Int) {
        if (start > sentences.lastIndex) { _speaking.value = false; return }
        current = start
        sentences.forEachIndexed { i, s ->
            if (i >= start) tts.speak(s, if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "rd-$gen-$i")
        }
    }

    override fun pause() { paused = true; gen++; tts.stop(); _speaking.value = false }
    override fun resume() { if (paused) { paused = false; gen++; _speaking.value = true; queueFrom(current) } }
    override fun stop() { paused = false; gen++; sentences = emptyList(); tts.stop(); _speaking.value = false }
    override fun shutdown() { tts.shutdown() }
}
