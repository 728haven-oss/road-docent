package kr.roaddocent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roaddocent.content.GuideRepository
import kr.roaddocent.core.Audience
import kr.roaddocent.core.Lang
import kr.roaddocent.core.Place
import kr.roaddocent.core.Prefs
import kr.roaddocent.core.SettingsStore
import kr.roaddocent.voice.LocalVoiceCommands
import kr.roaddocent.engine.DocentEngine
import kotlinx.coroutines.launch
import kr.roaddocent.speech.AndroidSpeaker
import kr.roaddocent.speech.CloudVoiceSpeaker
import kr.roaddocent.speech.VoiceCloneClient
import kr.roaddocent.speech.VoiceProfile
import kr.roaddocent.speech.VoiceRecorder
import kr.roaddocent.vehicle.NavSignals
import kr.roaddocent.vehicle.PleosNavSignals
import kr.roaddocent.vehicle.PleosVehicleSignals
import kr.roaddocent.vehicle.SimulatedTrip
import kr.roaddocent.vehicle.VehicleSignals

enum class Source { PLEOS, SIMULATOR }

class DocentViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore(app)
    /** Multi-day history (kept only when "keep travel log" is on). */
    val history = kr.roaddocent.log.TripHistory(app)
    val storyBank = kr.roaddocent.content.StoryBank(app)
    /** Rear-seat companion page served from the car; QR on the "동승자" panel. */
    val companion = kr.roaddocent.share.CompanionServer()
    private val _companionUrl = MutableStateFlow<String?>(null)
    val companionUrl: StateFlow<String?> = _companionUrl.asStateFlow()
    private val tone by lazy { runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    private var companionJob: kotlinx.coroutines.Job? = null

    fun startCompanion() { if (companion.start()) _companionUrl.value = companion.url() }
    fun shareToday() = kr.roaddocent.share.ShareCard.share(getApplication(), engine.log.value, engine.lang.value, java.time.LocalDate.now().toString())
    fun demoFreeRide() {
        val loop = listOf(kr.roaddocent.core.GeoPoint(37.5796, 126.977), kr.roaddocent.core.GeoPoint(37.572, 126.9768), kr.roaddocent.core.GeoPoint(37.56, 126.9753),
            kr.roaddocent.core.GeoPoint(37.5547, 126.9707), kr.roaddocent.core.GeoPoint(37.538, 126.971), kr.roaddocent.core.GeoPoint(37.524, 126.9803),
            kr.roaddocent.core.GeoPoint(37.5335, 127.003), kr.roaddocent.core.GeoPoint(37.556, 126.99), kr.roaddocent.core.GeoPoint(37.5759, 126.9769))
        sim?.freeRide(loop)
        engine.startFreeRide(guides.all())
    }
    private var historyJob: kotlinx.coroutines.Job? = null
    val localVoice = LocalVoiceCommands(app)
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()
    fun openSettings(open: Boolean) { _settingsOpen.value = open }

    // Voice Family: cloned voice when a profile is active, on-device TTS otherwise.
    private val voiceClient = VoiceCloneClient(apiKey = settings.prefs.value.voiceApiKey.ifBlank { null })
    private val speaker = CloudVoiceSpeaker(app, voiceClient, AndroidSpeaker(app))
    private val guides = GuideRepository(app)
    val recorder = VoiceRecorder(app)

    private val _voiceSetup = MutableStateFlow(false)
    val voiceSetup: StateFlow<Boolean> = _voiceSetup.asStateFlow()
    private val _voiceProfile = MutableStateFlow<VoiceProfile?>(null)
    val voiceProfile: StateFlow<VoiceProfile?> = _voiceProfile.asStateFlow()
    val cloudAvailable get() = voiceClient.available

    fun openVoiceSetup(open: Boolean) { _voiceSetup.value = open }
    fun startSample(i: Int) { runCatching { recorder.start("family", i) } }
    fun stopSample() = recorder.stop()
    fun buildVoice(name: String, isChild: Boolean, guardianConsent: Boolean) {
        viewModelScope.launch {
            val samples = recorder.samples("family")
            val modelId = voiceClient.createVoice("RoadDocent-$name", samples)
            val profile = VoiceProfile("family", name, isChild, guardianConsent, modelId, System.currentTimeMillis())
            _voiceProfile.value = profile
            speaker.profile = profile
            if (modelId != null) recorder.purge("family")   // raw recordings deleted once the model exists
            _voiceSetup.value = false
        }
    }
    fun deleteVoice() { recorder.purge("family"); _voiceProfile.value = null; speaker.profile = null }

    private val _source = MutableStateFlow<Source?>(null)
    val source: StateFlow<Source?> = _source.asStateFlow()

    var sim: SimulatedTrip? = null; private set
    lateinit var engine: DocentEngine; private set

    fun connect(src: Source) {
        val nav: NavSignals; val veh: VehicleSignals
        if (src == Source.SIMULATOR) { val s = SimulatedTrip(); sim = s; nav = s; veh = s }
        else { nav = PleosNavSignals(getApplication()); veh = PleosVehicleSignals(getApplication()) }
        engine = DocentEngine(nav, veh, speaker, guides, viewModelScope)
        engine.storyBank = storyBank
        engine.onQuizRevealed = { tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 180) }
        companionJob?.cancel()
        companionJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(engine.nowPlaying, engine.quiz, engine.log, engine.lang) { ep, q, l, lang -> kr.roaddocent.share.CompanionServer.State(
                title = ep?.titleFor(lang) ?: "", text = ep?.textFor(lang, engine.audience.value) ?: "", quiz = q?.question, answer = q?.answer, stickers = l.stickers, lang = lang.tag,
                phrases = engine.guide?.phrases.orEmpty().take(5).map { it.ko to (it.meaning["en"] ?: it.romanized) }) }.collect { companion.update(it) }
        }
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            engine.log.collect { l -> if (settings.prefs.value.keepTravelLog && (l.visits.isNotEmpty() || l.distanceKm > 0f)) history.save(java.time.LocalDate.now().toString(), l) }
        }
        val p = settings.prefs.value
        engine.setLang(p.lang); engine.setAudience(p.audience); engine.safetyPauseKmh = p.safetyPauseKmh; localVoice.lang = p.lang
        engine.theme = p.theme; engine.overlay = if (p.newCarMode) guides.loadOverlay("car_onboarding") else null
        engine.start()
        _source.value = src
    }

    /** Demo: set the Seoul -> Gangneung route. With Pleos, the driver sets it in the built-in navi. */
    fun demoStartTrip(guideId: String = "seoul_gangneung") {
        val g = guides.load(guideId)
        sim?.let { s -> if (g.route.isNotEmpty()) { s.mainRoute = g.route; s.mainMinutes = g.driveMin }; s.setDestination(g.destination) }
        engine.onRouteSet(g.destination.name["ko"]!!)
    }
    fun guides() = guides.all()
    fun setTheme(t: String) { settings.update { it.copy(theme = t) }; engine.theme = t }
    fun setNewCarMode(on: Boolean) = applyPrefs(settings.prefs.value.copy(newCarMode = on))

    fun setLang(l: Lang) { engine.setLang(l); localVoice.lang = l; settings.update { it.copy(lang = l) } }
    fun setAudience(a: Audience) { engine.setAudience(a); settings.update { it.copy(audience = a) } }
    fun applyPrefs(p: Prefs) {
        settings.update { p }
        voiceClient.apiKey = p.voiceApiKey.ifBlank { null }
        voiceClient.baseUrl = if (p.voiceProvider == "typecast") "https://typecast.ai/api" else "https://api.elevenlabs.io/v1"
        if (::engine.isInitialized) {
            engine.setLang(p.lang); engine.setAudience(p.audience); engine.safetyPauseKmh = p.safetyPauseKmh; engine.theme = p.theme
            val want = p.newCarMode; if (want != (engine.overlay != null)) engine.overlay = if (want) guides.loadOverlay("car_onboarding") else null
        }
        localVoice.lang = p.lang
    }
    fun listen() = localVoice.listenOnce()
    fun goThere(p: Place) = engine.cmdGoThere(p)

    override fun onCleared() { if (::engine.isInitialized) engine.stop(); speaker.shutdown(); localVoice.release(); companion.stop(); tone?.release() }
}
