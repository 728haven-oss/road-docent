package kr.roaddocent.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.roaddocent.core.GeoPoint
import kr.roaddocent.core.NavState
import kr.roaddocent.core.Place
import kr.roaddocent.core.VehicleState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-location mode for phone testing: the phone's GPS stands in for NaviHelper (position, remaining distance/time to the
 * chosen destination, arrival) and for the Vehicle SDK (speed from GPS; "parked" = stationary for 90 s; rear seat from settings).
 * Everything the engine does — NearPoint stories, congestion, arrival checklist, re-board — then happens where you actually are.
 */
class PhoneGpsSignals(private val context: Context) : NavSignals, VehicleSignals {
    private val _nav = MutableStateFlow(NavState())
    private val _veh = MutableStateFlow(VehicleState(gearPark = true))
    override val nav: StateFlow<NavState> = _nav.asStateFlow()
    override val vehicle: StateFlow<VehicleState> = _veh.asStateFlow()

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var dest: Place? = null
    private var stillSinceMs: Long? = null
    private var lastFix: Location? = null
    private var tripM = 0.0
    private var tripStartMs = 0L
    /** Assumed average speed for the remaining-time estimate when no route service is available. */
    var assumedKmh = 35f
    var rearSeat = false
        set(v) { field = v; _veh.update { it.copy(rearSeatOccupied = v) } }

    private val listener = LocationListener { loc -> onFix(loc) }

    @SuppressLint("MissingPermission")
    override fun start() {
        tripStartMs = System.currentTimeMillis()
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 3f, listener, Looper.getMainLooper())
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 10f, listener, Looper.getMainLooper())
            (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let { onFix(it) }
        }
    }

    override fun stop() { runCatching { lm.removeUpdates(listener) } }

    override fun setDestination(place: Place) {
        dest = place
        val here = _nav.value.location
        val d = here?.let { haversineM(it, place.point) } ?: 0f
        _nav.update { NavState(active = true, destinationName = place.name["ko"] ?: place.name.values.first(), destination = place.point, location = here,
            remainDistanceM = d.toInt(), remainTimeSec = (d / (assumedKmh / 3.6f)).toInt(), arrived = false) }
        _veh.update { it.copy(gearPark = false) }
        stillSinceMs = null
    }

    private fun onFix(loc: Location) {
        val now = System.currentTimeMillis()
        val p = GeoPoint(loc.latitude, loc.longitude)
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else lastFix?.let { prev -> val dt = (loc.time - prev.time) / 1000f; if (dt > 0) haversineM(GeoPoint(prev.latitude, prev.longitude), p) / dt * 3.6f else 0f } ?: 0f
        lastFix?.let { prev -> tripM += haversineM(GeoPoint(prev.latitude, prev.longitude), p).toDouble() }
        lastFix = loc
        // parked = still for 90 s (a traffic light is shorter); moving again = D
        if (kmh < 2f) { if (stillSinceMs == null) stillSinceMs = now } else stillSinceMs = null
        val parked = stillSinceMs?.let { now - it > 90_000 } == true
        val d = dest
        val remainM = d?.let { haversineM(p, it.point) } ?: 0f
        val arrived = d != null && remainM < 150f
        _nav.update { it.copy(location = p, remainDistanceM = remainM.toInt(), remainTimeSec = (remainM / (assumedKmh / 3.6f)).toInt(), arrived = it.arrived || arrived, active = d != null) }
        _veh.update { it.copy(speedKmh = kmh, gearPark = parked, tripDistanceKm = (tripM / 1000).toFloat(), tripTimeMin = ((now - tripStartMs) / 60_000).toInt()) }
    }

    private fun haversineM(a: GeoPoint, b: GeoPoint): Float {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return (2 * r * atan2(sqrt(h), sqrt(1 - h))).toFloat()
    }
}
