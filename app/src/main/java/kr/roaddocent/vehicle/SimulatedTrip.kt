package kr.roaddocent.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roaddocent.core.GeoPoint
import kr.roaddocent.core.NavState
import kr.roaddocent.core.Place
import kr.roaddocent.core.VehicleState
import kotlin.math.max

/**
 * In-app simulator: replays a Seoul -> Gangneung drive on a compressed clock so the whole
 * flow (start, waypoints, congestion, rest area, arrival, park, re-board) can be shown in
 * a few minutes on any Android device or the Pleos Connect AVD without a real route.
 *
 * Implements both signal interfaces; the engine cannot tell it from the real SDK adapters.
 */
class SimulatedTrip : NavSignals, VehicleSignals {
    private val _nav = MutableStateFlow(NavState())
    private val _veh = MutableStateFlow(VehicleState())
    override val nav: StateFlow<NavState> = _nav.asStateFlow()
    override val vehicle: StateFlow<VehicleState> = _veh.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Seconds of real time per simulated minute. 1.0 = 60x faster than life. */
    var secondsPerSimMinute = 1.0
    private var congestionUntilMin = -1
    private var legPlanned = false

    // Polyline Seoul (Gangnam) -> Yangyang Expwy -> Gangneung Anmok beach, sampled per ~5 sim-min.
    private val route = listOf(
        GeoPoint(37.4979, 127.0276), GeoPoint(37.4556, 127.1690), GeoPoint(37.4432, 127.3480),
        GeoPoint(37.4818, 127.5560), GeoPoint(37.5075, 127.7470), GeoPoint(37.4890, 127.9890), // Hoengseong
        GeoPoint(37.5050, 128.2080), GeoPoint(37.5490, 128.3950), // Pyeongchang
        GeoPoint(37.6410, 128.6300), GeoPoint(37.7000, 128.7900), // Daegwallyeong
        GeoPoint(37.7580, 128.8760), GeoPoint(37.7720, 128.9470)  // Anmok
    )
    private val totalMin = 160

    override fun start() { if (job == null) _veh.value = VehicleState(gearPark = true) }
    override fun stop() { job?.cancel(); job = null }

    /** Route polyline and length for the main leg; set by the ViewModel from the chosen guide. */
    var mainRoute: List<GeoPoint> = route
    var mainMinutes: Int = totalMin

    /** Begin the drive to [place] from the current position (or Seoul on first call). */
    override fun setDestination(place: Place) {
        job?.cancel()
        val from = _nav.value.location ?: route.first()
        val isMainLeg = !legPlanned
        legPlanned = true
        val path = if (isMainLeg) mainRoute else listOf(from, place.point)
        val minutes = if (isMainLeg) mainMinutes else 12
        val name = place.name["ko"] ?: place.name.values.first()
        _nav.update { NavState(active = true, destinationName = name, destination = place.point, location = from, remainTimeSec = minutes * 60, remainDistanceM = if (isMainLeg) 212_000 else 6_000) }
        _veh.update { it.copy(gearPark = false, speedKmh = 60f) }
        job = scope.launch {
            var simMin = 0
            while (simMin < minutes) {
                delay((secondsPerSimMinute * 1000).toLong())
                simMin++
                val congested = simMin < congestionUntilMin
                val speed = when {
                    congested -> 12f
                    isMainLeg && simMin in 95..118 -> 55f   // Daegwallyeong, winding
                    isMainLeg -> 95f
                    else -> 45f
                }
                val t = simMin.toFloat() / minutes
                val loc = interpolate(path, t)
                _nav.update { it.copy(location = loc, remainTimeSec = max(0, (minutes - simMin) * 60 + if (congested) 15 * 60 else 0), remainDistanceM = ((1 - t) * (if (isMainLeg) 212_000 else 6_000)).toInt()) }
                _veh.update { it.copy(speedKmh = speed, tripDistanceKm = it.tripDistanceKm + speed / 60f, tripTimeMin = it.tripTimeMin + 1) }
            }
            _nav.update { it.copy(arrived = true, remainTimeSec = 0, remainDistanceM = 0, location = place.point) }
            _veh.update { it.copy(speedKmh = 0f) }
        }
    }

    // ---- demo controls ----
    fun jumpToMinute(min: Int) { /* handled via fastForward for simplicity */ }
    fun fastForward(simMinutes: Int) { scope.launch { repeat(simMinutes) { delay(1) }; } ; secondsPerSimMinute = 0.05; scope.launch { delay((simMinutes * 50).toLong()); secondsPerSimMinute = 1.0 } }
    fun triggerCongestion(simMinutes: Int = 15) { congestionUntilMin = currentSimMinute() + simMinutes }
    fun park() { job?.cancel(); job = null; _veh.update { it.copy(gearPark = true, speedKmh = 0f) }; _nav.update { it.copy(active = false, arrived = true) } }
    fun shiftToDrive() { _veh.update { it.copy(gearPark = false) } }
    fun setRearSeat(occupied: Boolean) { _veh.update { it.copy(rearSeatOccupied = occupied) } }
    fun setLocation(p: GeoPoint) { _nav.update { it.copy(location = p) } }

    private fun currentSimMinute(): Int {
        val n = _nav.value
        val total = if (n.remainDistanceM > 10_000) mainMinutes else 12
        return total - n.remainTimeSec / 60
    }

    private fun interpolate(path: List<GeoPoint>, t: Float): GeoPoint {
        if (path.size == 1) return path[0]
        val seg = (t * (path.size - 1)).coerceIn(0f, (path.size - 1).toFloat())
        val i = seg.toInt().coerceAtMost(path.size - 2)
        val f = seg - i
        val a = path[i]; val b = path[i + 1]
        return GeoPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
    }
}
