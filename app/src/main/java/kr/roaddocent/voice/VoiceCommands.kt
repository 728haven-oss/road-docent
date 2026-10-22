package kr.roaddocent.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roaddocent.core.Lang
import org.json.JSONObject

/**
 * The six things a driver says. Everything else is noise.
 * Same commands arrive from three sources and land on one router:
 *  - Gleo Interface (Pleos Connect): broadcast intent per function in assets/gleo-schema.json
 *  - On-device speech recognition (any Android): keyword match, no cloud
 *  - Screen buttons (demo / parked only)
 */
enum class Command(val gleoFunction: String) {
    SKIP("SKIP_EPISODE"),
    TELL_MORE("TELL_MORE"),
    SAVE("SAVE_PLACE"),
    QUIET("QUIET"),
    WHERE_NEXT("WHERE_NEXT"),
    GO_THERE("GO_THERE"),      // param: placeId or index ("1","2","3")
    RESERVE("RESERVE"),
    SHOW_LOG("SHOW_TRAVEL_LOG");

    companion object {
        fun fromGleo(action: String?) = entries.firstOrNull { it.gleoFunction == action }
    }
}

data class CommandEvent(val command: Command, val param: String? = null, val source: String)

/** Single entry point for commands; the engine subscribes. */
object CommandBus {
    private val _events = MutableStateFlow<CommandEvent?>(null)
    val events: StateFlow<CommandEvent?> = _events.asStateFlow()
    fun emit(e: CommandEvent) { _events.value = e }
    fun consume() { _events.value = null }
}

/**
 * Gleo Interface receiver. Gleo AI resolves the driver's utterance to one of the functions in
 * assets/gleo-schema.json and sends an Intent whose action is the function name, with the
 * parameters as JSON in extra "ACTION_PARAM". We answer on APP_ACTION_RESPONSE.
 */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = Command.fromGleo(intent.action) ?: return
        val params = intent.getStringExtra("ACTION_PARAM")?.let { runCatching { JSONObject(it) }.getOrNull() }
        val param = params?.optString("place")?.takeIf { it.isNotBlank() } ?: params?.optString("index")?.takeIf { it.isNotBlank() }
        Log.i("Gleo", "action=${intent.action} param=$param")
        CommandBus.emit(CommandEvent(cmd, param, "gleo"))
        respond(context, cmd.gleoFunction, "success", "ok")
    }

    companion object {
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        fun respond(context: Context, functionName: String, status: String, message: String, data: String = "[]") {
            val json = JSONObject().put("functionName", functionName).put("status", status).put("message", message).put("data", data)
            context.sendBroadcast(Intent(RESPONSE_ACTION).apply {
                putExtra("result", status); putExtra("packageName", context.packageName); putExtra("response", json.toString())
            })
        }
    }
}

/**
 * On-device recognizer for phones/tablets and the demo: listens in short bursts, matches keywords.
 * On Pleos Connect this is replaced by Gleo (the car's mic and wake word), so it is off when Gleo is present.
 */
class LocalVoiceCommands(private val context: Context) {
    private var sr: SpeechRecognizer? = null
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    private val _heard = MutableStateFlow("")
    val heard: StateFlow<String> = _heard.asStateFlow()
    var lang = Lang.KO

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun listenOnce() {
        if (!available) return
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(listener) }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == Lang.KO) "ko-KR" else "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        _listening.value = true
        sr?.startListening(intent)
    }

    fun release() { sr?.destroy(); sr = null; _listening.value = false }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            _listening.value = false
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            _heard.value = texts.firstOrNull().orEmpty()
            texts.firstNotNullOfOrNull { parse(it) }?.let { CommandBus.emit(it) }
        }
        override fun onError(error: Int) { _listening.value = false }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    /** Keyword grammar. Order matters: "다른 데 없어?" must not be read as "넘겨". */
    fun parse(text: String): CommandEvent? {
        val t = text.lowercase().replace(" ", "")
        fun has(vararg k: String) = k.any { t.contains(it.replace(" ", "")) }
        val cmd = when {
            has("거기로가자", "거기가자", "그리로가", "let's go there", "go there", "take me there") -> Command.GO_THERE
            has("다른데", "다른곳", "어디갈까", "다음은어디", "where next", "anywhere else", "alternatives") -> Command.WHERE_NEXT
            has("예약", "전화걸어", "reserve", "book it", "call them") -> Command.RESERVE
            has("일지", "기록보여", "오늘어디", "travel log", "show the log") -> Command.SHOW_LOG
            has("더자세히", "자세히", "더알려", "tell me more", "more detail") -> Command.TELL_MORE
            has("저장", "기억해", "save", "remember this") -> Command.SAVE
            has("조용", "그만", "멈춰", "quiet", "stop", "pause") -> Command.QUIET
            has("넘겨", "다음", "스킵", "skip", "next") -> Command.SKIP
            else -> null
        } ?: return null
        val idx = Regex("(첫|1|하나|둘|2|두|셋|3|세|first|second|third|one|two|three)").find(t)?.value
        val param = when (idx) { "첫", "1", "하나", "first", "one" -> "1"; "둘", "2", "두", "second", "two" -> "2"; "셋", "3", "세", "third", "three" -> "3"; else -> null }
        return CommandEvent(cmd, param, "local")
    }
}
