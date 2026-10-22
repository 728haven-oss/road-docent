package kr.roaddocent.vehicle

import kr.roaddocent.core.NavState
import kr.roaddocent.core.Place
import kr.roaddocent.core.VehicleState
import kotlinx.coroutines.flow.StateFlow

/**
 * Navigation signals. Implemented by [PleosNavSignals] (NaviHelper SDK) and [SimulatedTrip].
 * The engine only ever talks to this interface, so the same logic runs on a Pleos Connect AVD,
 * a real vehicle, or the in-app simulator used for demos.
 */
interface NavSignals {
    val nav: StateFlow<NavState>
    /** Ask the built-in navigation to route to [place] (used after "let's go there"). */
    fun setDestination(place: Place)
    fun start()
    fun stop()
}

/** Vehicle signals: speed, gear, rear-seat occupancy, trip odometer. */
interface VehicleSignals {
    val vehicle: StateFlow<VehicleState>
    fun start()
    fun stop()
}
