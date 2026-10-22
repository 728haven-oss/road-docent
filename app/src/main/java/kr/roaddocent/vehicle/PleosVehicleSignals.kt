package kr.roaddocent.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.roaddocent.core.VehicleState

/**
 * Vehicle SDK adapter.
 *  - Gear P/D      -> parked / re-board detection ("where next?")
 *  - Row-2 seats   -> rear passenger present -> kids mode offer
 *  - Trip distance/time -> travel log numbers
 * Speed is derived from NaviHelper location deltas (no public speed signal in Vehicle SDK 2.3).
 */
class PleosVehicleSignals(private val context: Context) : VehicleSignals {
    private val tag = "PleosVehicle"
    private val _vehicle = MutableStateFlow(VehicleState())
    override val vehicle: StateFlow<VehicleState> = _vehicle.asStateFlow()

    private var v: Vehicle? = null
    private val rearSeats = SeatArea(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { _vehicle.update { it.copy(gearPark = gear == VehicleGear.GEAR_PARK) } }
        override fun onFailed(e: Exception) { Log.w(tag, "gear: $e") }
    }
    private val seatListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupied: Boolean?) { _vehicle.update { it.copy(rearSeatOccupied = occupied == true) } }
        override fun onFailed(e: Exception) { Log.w(tag, "seat: $e") }
    }
    private val distListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) { km?.let { d -> _vehicle.update { it.copy(tripDistanceKm = d) } } }
        override fun onFailed(e: Exception) { Log.w(tag, "dist: $e") }
    }
    private val timeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(min: Int?) { min?.let { m -> _vehicle.update { it.copy(tripTimeMin = m) } } }
        override fun onFailed(e: Exception) { Log.w(tag, "time: $e") }
    }

    override fun start() {
        if (v != null) return
        try {
            val veh = Vehicle(context.applicationContext)
            veh.initialize()
            v = veh
            veh.getGear().getCurrentGearSelectionState({ g -> _vehicle.update { it.copy(gearPark = g == VehicleGear.GEAR_PARK) } }, { Log.w(tag, "gear read: $it") })
            veh.getGear().registerGearSelection(gearListener)
            veh.getSeat().getCurrentSeatOccupancy(rearSeats, { o -> _vehicle.update { it.copy(rearSeatOccupied = o == true) } }, { Log.w(tag, "seat read: $it") })
            veh.getSeat().registerSeatOccupancy(rearSeats, seatListener)
            veh.getOdometer().registerTripCurrentDrivingDistance(distListener)
            veh.getOdometer().registerTripCurrentDrivingTime(timeListener)
        } catch (e: Exception) {
            Log.e(tag, "Vehicle SDK init failed", e); v = null
        }
    }

    override fun stop() {
        v?.let { veh ->
            runCatching {
                veh.getGear().unregisterGearSelection(gearListener)
                veh.getSeat().unregisterSeatOccupancy(rearSeats, seatListener)
                veh.getOdometer().unregisterTripCurrentDrivingDistance(distListener)
                veh.getOdometer().unregisterTripCurrentDrivingTime(timeListener)
                veh.release()
            }
        }
        v = null
    }

    /** Called by the engine with speed derived from navigation location deltas. */
    fun updateSpeed(kmh: Float) = _vehicle.update { it.copy(speedKmh = kmh) }
}
