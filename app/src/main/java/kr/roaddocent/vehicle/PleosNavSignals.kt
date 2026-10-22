package kr.roaddocent.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
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

/**
 * NaviHelper SDK adapter. Polls destination + current location every few seconds and
 * forwards arrival events. Requires pleos.car.permission.NAVI_ROUTE / NAVI_ROUTE_SEARCH.
 */
class PleosNavSignals(private val context: Context) : NavSignals, NaviHelperEventListener {
    private val tag = "PleosNav"
    private val _nav = MutableStateFlow(NavState())
    override val nav: StateFlow<NavState> = _nav.asStateFlow()

    private var helper: NaviHelper? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var poll: Job? = null

    override fun start() {
        if (helper != null) return
        try {
            val h = NaviHelper(context.applicationContext)
            h.initialize()
            h.addListener(this)
            helper = h
            poll = scope.launch {
                while (true) {
                    runCatching { h.getDestinationInfo(); h.getCurrentLocationInfo(); h.getRouteStateInfo() }
                        .onFailure { Log.w(tag, "poll failed: $it") }
                    delay(3_000)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "NaviHelper init failed", e)
            helper = null
        }
    }

    override fun stop() {
        poll?.cancel(); poll = null
        helper?.let { runCatching { it.removeListener(this); it.release() } }
        helper = null
    }

    override fun setDestination(place: Place) {
        val h = helper ?: return
        h.requestRoute(
            RouteInfo(
                latitude = place.point.lat, longitude = place.point.lon,
                displayLatitude = null, displayLongitude = null,
                poiName = place.name["ko"] ?: place.name.values.first(),
                poiId = place.poiId, address = place.address, poiSubId = place.poiSubId,
                routeOption = RouteOption.FREE, poiProvider = "", isMobileRequest = false
            )
        )
    }

    // ---- NaviHelperEventListener ----
    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
        _nav.update {
            it.copy(
                active = true,
                destinationName = destinationInfo.name,
                destination = GeoPoint(destinationInfo.latitude, destinationInfo.longitude),
                remainDistanceM = destinationInfo.remainDistance,
                remainTimeSec = destinationInfo.remainTime,
                arrived = false
            )
        }
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
        _nav.update { it.copy(location = GeoPoint(currentLocationInfo.latitude, currentLocationInfo.longitude)) }
    }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        _nav.update { it.copy(arrived = true, remainDistanceM = 0, remainTimeSec = 0) }
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        if (status == NavigationStatus.FREE_DRIVE || status == NavigationStatus.INACTIVE) {
            _nav.update { it.copy(active = false) }
        }
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) { _nav.update { it.copy(active = true, arrived = false) } }
    override fun onRouteCancelled() { _nav.update { it.copy(active = false) } }
    override fun onDestinationChanged() { helper?.getDestinationInfo() }
    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {}
    override fun onError(errorCode: NaviErrorCode) { Log.w(tag, "navi error $errorCode") }

    // Unused callbacks
    @Deprecated("Deprecated by NaviHelperEventListener") override fun onRouteEnded() {}
    override fun onRoutingCompleted() {}
    override fun onReRouteEnded() {}
    override fun onRouteOptionChanged(routeOption: RouteOption) {}
    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {}
    override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {}
    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {}
    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {}
    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {}
    override fun onCurrentRoadSpeedLimit(speedLimit: Int) {}
    override fun onCameraAlert(cameraAlertList: List<CameraAlert>) {}
    override fun onDrivingInfo(drivingInfo: DrivingInfo) {}
    override fun onBookMarkChanged() {}
    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {}
    override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) {}
    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {}
    override fun onTripInfo(tripInfo: TripInfo) {}
}
