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
    // Voice Family: cloned voice when a profile is active, on-device TTS otherwise.
    private val voiceClient = VoiceCloneClient(apiKey = null) // set from settings / BuildConfig, never hard-coded
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

    fun setLang(l: Lang) = engine.setLang(l)
    fun setAudience(a: Audience) = engine.setAudience(a)
    fun goThere(p: Place) = engine.cmdGoThere(p)

    override fun onCleared() { if (::engine.isInitialized) engine.stop(); speaker.shutdown() }
}
