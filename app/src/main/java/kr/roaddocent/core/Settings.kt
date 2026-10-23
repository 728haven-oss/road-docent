package kr.roaddocent.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Driver preferences. Small, so SharedPreferences; nothing here leaves the car. */
data class Prefs(
    val lang: Lang = Lang.KO,
    val audience: Audience = Audience.ADULT,
    val childAge: Int = 8,                 // 5-7 songs, 8-10 quizzes, 11-13 discussion
    val voiceApiKey: String = "",          // voice-cloning provider key; never shipped in the APK
    val voiceProvider: String = "elevenlabs",
    val keepTravelLog: Boolean = true,     // off = nothing persisted after the trip
    val safetyPauseKmh: Int = 100,
    val localVoiceCommands: Boolean = true, // on-device recognizer when Gleo is absent
    /** New-car onboarding overlay: sensors explained as they work, maintenance intervals. Auto-off after 1,000 km in production. */
    val newCarMode: Boolean = false,
    /** Preferred travel theme for the picker and "where next?" ranking: history, art, nature, thrill, food, family, car; "" = all. */
    val theme: String = "",
    /** First-run 3-tap onboarding done. */
    val onboarded: Boolean = false,
    /** Driving text size: 1.0 = car default (24sp body), 1.2 = large. */
    val fontScale: Float = 1.0f,
    /** Show the full text panel even while driving (passenger-operated screens). */
    val alwaysFull: Boolean = false
)

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("roaddocent", Context.MODE_PRIVATE)
    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    private fun load() = Prefs(
        lang = Lang.entries.firstOrNull { it.tag == sp.getString("lang", "ko") } ?: Lang.KO,
        audience = Audience.entries.firstOrNull { it.name == sp.getString("audience", "ADULT") } ?: Audience.ADULT,
        childAge = sp.getInt("childAge", 8),
        voiceApiKey = sp.getString("voiceApiKey", "") ?: "",
        voiceProvider = sp.getString("voiceProvider", "elevenlabs") ?: "elevenlabs",
        keepTravelLog = sp.getBoolean("keepTravelLog", true),
        safetyPauseKmh = sp.getInt("safetyPauseKmh", 100),
        localVoiceCommands = sp.getBoolean("localVoiceCommands", true),
        newCarMode = sp.getBoolean("newCarMode", false),
        theme = sp.getString("theme", "") ?: "",
        onboarded = sp.getBoolean("onboarded", false),
        fontScale = sp.getFloat("fontScale", 1.0f),
        alwaysFull = sp.getBoolean("alwaysFull", false)
    )

    fun update(block: (Prefs) -> Prefs) {
        val p = block(_prefs.value)
        sp.edit()
            .putString("lang", p.lang.tag).putString("audience", p.audience.name).putInt("childAge", p.childAge)
            .putString("voiceApiKey", p.voiceApiKey).putString("voiceProvider", p.voiceProvider)
            .putBoolean("keepTravelLog", p.keepTravelLog).putInt("safetyPauseKmh", p.safetyPauseKmh)
            .putBoolean("localVoiceCommands", p.localVoiceCommands)
            .putBoolean("newCarMode", p.newCarMode).putString("theme", p.theme)
            .putBoolean("onboarded", p.onboarded).putFloat("fontScale", p.fontScale).putBoolean("alwaysFull", p.alwaysFull).apply()
        _prefs.value = p
    }
}
