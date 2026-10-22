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
import kr.roaddocent.speech.AndroidSpeaker
import kr.roaddocent.vehicle.NavSignals
import kr.roaddocent.vehicle.PleosNavSignals
import kr.roaddocent.vehicle.PleosVehicleSignals
import kr.roaddocent.vehicle.SimulatedTrip
import kr.roaddocent.vehicle.VehicleSignals

enum class Source { PLEOS, SIMULATOR }

class DocentViewModel(app: Application) : AndroidViewModel(app) {
    private val speaker = AndroidSpeaker(app)
    private val guides = GuideRepository(app)

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
