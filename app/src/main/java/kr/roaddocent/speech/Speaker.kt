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

class AndroidSpeaker(context: Context) : Speaker {
    private val tag = "Speaker"
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private var ready = false
    private var pending: Pair<String, Lang>? = null
    private var paused = false
    private var remaining: List<String> = emptyList()
    private var lang = Lang.KO

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) Log.w(tag, "TTS init failed: $status")
        pending?.let { (t, l) -> pending = null; speak(t, l) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { if (!paused) speakNext() }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { _speaking.value = false }
        })
    }

    override fun speak(text: String, lang: Lang, utteranceId: String) {
        if (!ready) { pending = text to lang; return }
        this.lang = lang
        tts.language = if (lang == Lang.KO) Locale.KOREAN else Locale.US
        tts.setSpeechRate(0.95f)
        // Sentence-level queue so pause() can stop at a boundary and resume() continues.
        remaining = text.split(Regex("(?<=[.!?。]|다\\.|요\\.)\\s+")).filter { it.isNotBlank() }
        paused = false
        speakNext()
    }

    private fun speakNext() {
        val s = remaining.firstOrNull() ?: run { _speaking.value = false; return }
        remaining = remaining.drop(1)
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "rd-${System.nanoTime()}")
    }

    override fun pause() { paused = true; tts.stop(); _speaking.value = false }
    override fun resume() { if (paused) { paused = false; speakNext() } }
    override fun stop() { paused = false; remaining = emptyList(); tts.stop(); _speaking.value = false }
    override fun shutdown() { tts.shutdown() }
}
