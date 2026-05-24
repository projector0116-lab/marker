package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.RoutePoint
import com.example.data.RouteRepository
import com.example.data.SavedRoute
import com.example.util.GeoPoint
import com.example.util.MockRoad
import com.example.util.NavigationEngine
import com.google.android.gms.location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

enum class GpsStatus {
    EXCELLENT,             // GPS良好 (Locked and Snap ON)
    RAW_ONLY,              // GPS良好 (Snap OFF)
    EXTRAPOLATING_TUNNEL,  // 補完中 (Tunnel Dead Reckoning active)
    PAUSED,                // 一時停止
    STOPPED                // 停止中
}

class RouteViewModel(private val repository: RouteRepository) : ViewModel() {

    private var appContext: Context? = null

    // Preset roads available for driving simulation/snapping demo
    val availableRoads = NavigationEngine.PRESET_ROADS
    val selectedRoad = MutableStateFlow<MockRoad>(availableRoads[0])

    // Current vehicle location
    private val _currentLocation = MutableStateFlow(GeoPoint(35.3090, 139.5286))
    val currentLocation: StateFlow<GeoPoint> = _currentLocation.asStateFlow()

    // Stream of recorded path coordinates representing the blue trail.
    // Contains RoutePoints which track coordinates, speed, and if it was interpolated.
    private val _recordedPath = MutableStateFlow<List<RoutePoint>>(emptyList())
    val recordedPath: StateFlow<List<RoutePoint>> = _recordedPath.asStateFlow()

    // Active speed display (km/h) for the bottom left.
    private val _currentSpeedKmh = MutableStateFlow(0.0)
    val currentSpeedKmh: StateFlow<Double> = _currentSpeedKmh.asStateFlow()

    // Calculated metrics
    private val _totalDistanceKm = MutableStateFlow(0.0)
    val totalDistanceKm: StateFlow<Double> = _totalDistanceKm.asStateFlow()

    private val _averageSpeedKmh = MutableStateFlow(0.0)
    val averageSpeedKmh: StateFlow<Double> = _averageSpeedKmh.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    // GPS Snap configuration (Lock to middle of the road)
    val isSnapped = MutableStateFlow(true)

    // Tunnel mode configuration (Auto-detect tunnel segments vs purely manual)
    val isAutoTunnelEnabled = MutableStateFlow(true)

    fun toggleAutoTunnel() {
        isAutoTunnelEnabled.value = !isAutoTunnelEnabled.value
    }

    // App Customization Settings State (User preferences)
    val trailColorHex = MutableStateFlow("#1D4ED8") // Default Royal Blue
    val isSpeedometerVisible = MutableStateFlow(true)
    val isDistanceVisible = MutableStateFlow(true)
    val hudPosition = MutableStateFlow("top") // Options: "top", "bottom_left", "bottom_right", "split"

    // Current operational state
    private val _gpsStatus = MutableStateFlow(GpsStatus.STOPPED)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    // Historical saved routes flow from Room database
    val savedRoutesHistory: StateFlow<List<SavedRoute>> = repository.allRoutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Overlay database route on map for visual history comparison
    private val _overlayRoute = MutableStateFlow<SavedRoute?>(null)
    val overlayRoute: StateFlow<SavedRoute?> = _overlayRoute.asStateFlow()

    // Live GPS setup
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var lastGpsUpdateTime = 0L

    // Sim/Timer job controllers
    private var trackingTimerJob: Job? = null
    private var simulationJob: Job? = null
    private var simIndex = 0
    private var simDistanceMeters = 0.0 // Accumulative driving simulator meters
    val targetSimSpeedKmh = MutableStateFlow(60.0) // Expose simulation speed flow (default 60 km/h)
    private var isSimulationRunning = false

    // Last known telemetry before entering tunnel
    private var lastValidHeadingDegrees = 90.0
    private var lastSpeedKmh = 45.0

    init {
        // Position on the start of Kamakura Coastal Route by default
        resetToRoadStart()
    }

    fun selectRoad(road: MockRoad) {
        selectedRoad.value = road
        stopTracking(false)
        resetToRoadStart()
    }

    fun ensureDynamicRoadNodes(pivot: GeoPoint) {
        val lat = pivot.latitude
        val lng = pivot.longitude

        // Check if there is an actual preset road within 2km of pivot so we align to standard roads
        var nearestPresetRoad: MockRoad? = null
        var minPresetDistance = Double.MAX_VALUE
        NavigationEngine.PRESET_ROADS.forEach { road ->
            if (road.id != "real_gps_free") {
                road.nodes.forEach { node ->
                    val dist = pivot.distanceTo(node)
                    if (dist < minPresetDistance) {
                        minPresetDistance = dist
                        nearestPresetRoad = road
                    }
                }
            }
        }

        if (nearestPresetRoad != null && minPresetDistance < 2.0) {
            val road = nearestPresetRoad!!
            selectedRoad.value = road
            if (isSnapped.value) {
                _currentLocation.value = road.snapPoint(pivot)
            }
            return
        }

        // We generate a virtual road passing exactly through the pivot (running East-West) to align to a simulated street center
        val roadLat = lat
        val nodes = listOf(
            GeoPoint(roadLat, lng - 0.04),
            GeoPoint(roadLat, lng - 0.02),
            GeoPoint(roadLat, lng),
            GeoPoint(roadLat, lng + 0.02),
            GeoPoint(roadLat, lng + 0.04)
        )
        val road = MockRoad(
            id = "real_gps_free",
            name = "フリー軌跡計測 (自律道路吸着中)",
            nodes = nodes,
            tunnelRanges = emptyList()
        )
        selectedRoad.value = road

        // If snapped mode is enabled, immediately lock the initial position to this road center
        if (isSnapped.value) {
            _currentLocation.value = road.snapPoint(pivot)
        }
    }

    private fun resetToRoadStart() {
        val road = selectedRoad.value
        if (road.id == "real_gps_free") {
            ensureDynamicRoadNodes(_currentLocation.value)
        } else if (road.nodes.isNotEmpty()) {
            _currentLocation.value = road.nodes[0]
        }
        simIndex = 0
        simDistanceMeters = 0.0
        _currentSpeedKmh.value = 0.0
        _totalDistanceKm.value = 0.0
        _averageSpeedKmh.value = 0.0
        _durationSeconds.value = 0L
        _recordedPath.value = emptyList()
    }

    /**
     * Toggles snapping to the middle of the road
     */
    fun toggleSnapping() {
        isSnapped.value = !isSnapped.value
        // Update current status string
        if (_gpsStatus.value == GpsStatus.EXCELLENT && !isSnapped.value) {
            _gpsStatus.value = GpsStatus.RAW_ONLY
        } else if (_gpsStatus.value == GpsStatus.RAW_ONLY && isSnapped.value) {
            _gpsStatus.value = GpsStatus.EXCELLENT
        }
    }

    fun selectOverlayRoute(route: SavedRoute?) {
        _overlayRoute.value = route
    }

    /**
     * Start recording & tracking.
     * Supports both Simulated Driving and real Android GPS.
     */
    fun startTracking(context: Context, useLiveGps: Boolean) {
        if (_gpsStatus.value == GpsStatus.EXCELLENT || _gpsStatus.value == GpsStatus.RAW_ONLY || _gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL) {
            return // Already tracking
        }

        // Initialize state & service
        this.appContext = context.applicationContext
        com.example.util.TrackingService.startService(context)

        _gpsStatus.value = if (isSnapped.value) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
        _recordedPath.value = emptyList()
        _totalDistanceKm.value = 0.0
        _averageSpeedKmh.value = 0.0
        _durationSeconds.value = 0L

        // Snap immediately to nearest road center on start
        if (isSnapped.value) {
            val road = selectedRoad.value
            if (road.nodes.isNotEmpty()) {
                val current = _currentLocation.value
                val snapped = road.snapPoint(current)
                _currentLocation.value = snapped
                Log.d("RouteViewModel", "Snapped starting location to road: $snapped")
            }
        }

        // Start duration timer
        startRecordingTimer()

        if (useLiveGps) {
            startRealGpsTracking(context)
        } else {
            startSimulatedDriving()
        }
    }

    /**
     * Pause tracking. Keeps the line recorded but stops appending points.
     */
    fun pauseTracking() {
        if (_gpsStatus.value == GpsStatus.PAUSED) {
            // Resume
            _gpsStatus.value = if (isSnapped.value) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
        } else {
            _gpsStatus.value = GpsStatus.PAUSED
            _currentSpeedKmh.value = 0.0
        }
    }

    /**
     * Stop and save the track.
     */
    fun stopTracking(saveAutomatically: Boolean) {
        val currentStatus = _gpsStatus.value
        if (currentStatus == GpsStatus.STOPPED) return

        _gpsStatus.value = GpsStatus.STOPPED
        _currentSpeedKmh.value = 0.0

        // Shutdown jobs & service
        appContext?.let {
            com.example.util.TrackingService.stopService(it)
        }
        trackingTimerJob?.cancel()
        simulationJob?.cancel()
        isSimulationRunning = false
        stopRealGpsTracking()

        val points = _recordedPath.value
        if (saveAutomatically && points.size > 2 && _totalDistanceKm.value > 0.01) {
            saveRecordedRoute()
        }
    }

    /**
     * Saves the current active path to Room Database list
     */
    fun saveRecordedRoute(customName: String? = null) {
        val points = _recordedPath.value
        if (points.isEmpty()) return

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateString = sdf.format(Date())
        val roadName = selectedRoad.value.name.substringBefore(" (")
        val finalName = customName ?: "${roadName}ドライブ $dateString"

        viewModelScope.launch {
            val route = SavedRoute(
                name = finalName,
                pointsCsv = RoutePoint.serializeList(points),
                distanceKm = _totalDistanceKm.value,
                durationSec = _durationSeconds.value,
                averageSpeedKmh = _averageSpeedKmh.value
            )
            repository.insertRoute(route)
            Log.d("RouteViewModel", "Saved route: $finalName, points count: ${points.size}")
        }
    }

    fun deleteRoute(id: Int) {
        viewModelScope.launch {
            repository.deleteRouteById(id)
            if (_overlayRoute.value?.id == id) {
                _overlayRoute.value = null
            }
        }
    }

    fun setTargetSpeed(kmh: Double) {
        targetSimSpeedKmh.value = kmh
        if (_gpsStatus.value != GpsStatus.PAUSED && _gpsStatus.value != GpsStatus.STOPPED && _gpsStatus.value != GpsStatus.EXTRAPOLATING_TUNNEL) {
            _currentSpeedKmh.value = kmh
        }
    }

    /**
     * Manual Trigger for Tunnels: Simulates a sudden transition into a tunnel.
     * Demonstrates GPS補完 (Dead Reckoning) immediately.
     */
    fun toggleTunnelSimulation() {
        if (_gpsStatus.value == GpsStatus.STOPPED || _gpsStatus.value == GpsStatus.PAUSED) return

        if (_gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL) {
            // Emerge from Tunnel!
            _gpsStatus.value = if (isSnapped.value) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
            _currentSpeedKmh.value = targetSimSpeedKmh.value
        } else {
            // Enter Tunnel!
            lastSpeedKmh = _currentSpeedKmh.value.coerceAtLeast(15.0) // Must continue moving
            _gpsStatus.value = GpsStatus.EXTRAPOLATING_TUNNEL
        }
    }

    /**
     * Simple live GPS interface. Registers to FusedLocationClient and captures updates.
     */
    @SuppressLint("MissingPermission")
    private fun startRealGpsTracking(context: Context) {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .build()

            stopRealGpsTracking() // Make sure to clean up first

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    lastGpsUpdateTime = System.currentTimeMillis()
                    if (_gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL && isAutoTunnelEnabled.value) {
                        _gpsStatus.value = if (isSnapped.value && selectedRoad.value.nodes.isNotEmpty()) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
                    }

                    viewModelScope.launch {
                        processIncomingLocation(location.latitude, location.longitude, location.speed * 3.6)
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("RouteViewModel", "Failed to start GPS tracking", e)
        }
    }

    private fun stopRealGpsTracking() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    /**
     * Processes raw coordinates from real GPS or simulated paths.
     * Handles road snapping, distance math, speed, and appends to the track.
     */
    private fun processIncomingLocation(
        lat: Double,
        lng: Double,
        rawSpeedKmh: Double,
        forceRecord: Boolean = true
    ) {
        if (_gpsStatus.value == GpsStatus.PAUSED || _gpsStatus.value == GpsStatus.STOPPED) return

        var activeRoad = selectedRoad.value
        val incomingGeo = GeoPoint(lat, lng)

        // Avoid minor stationary GPS telemetry jitter/shaking when sitting inside the house
        if (!isSimulationRunning) {
            val distFromLast = incomingGeo.distanceTo(_currentLocation.value)
            if (rawSpeedKmh < 1.5 && distFromLast < 0.005) {
                // Ignore micro GPS drift updates when stationary
                return
            }
        }

        // 1. If in real_gps_free mode and snapping is active, dynamically update the virtual road past the driver
        if (activeRoad.id == "real_gps_free" && isSnapped.value && _gpsStatus.value != GpsStatus.EXTRAPOLATING_TUNNEL) {
            val currentList = _recordedPath.value
            val heading = if (currentList.size >= 2) {
                val pLast = currentList.last()
                val pPrev = currentList[currentList.size - 2]
                Math.atan2(pLast.longitude - pPrev.longitude, pLast.latitude - pPrev.latitude)
            } else if (currentList.size == 1) {
                val pLast = currentList.last()
                Math.atan2(lng - pLast.longitude, lat - pLast.latitude)
            } else {
                Math.toRadians(90.0) // fallback to east-west
            }

            // Project 300 meters ahead so there is a road waiting
            val speedMs = (rawSpeedKmh * 1000.0) / 3600.0
            val projDist = if (speedMs > 2.0) speedMs * 20.0 else 300.0
            val deltaLat = (projDist * cos(heading)) / 111120.0
            val deltaLng = (projDist * sin(heading)) / (111120.0 * cos(Math.toRadians(lat)))

            val ahead1 = GeoPoint(lat + deltaLat * 0.5, lng + deltaLng * 0.5)
            val ahead2 = GeoPoint(lat + deltaLat, lng + deltaLng)

            val newNodes = mutableListOf<GeoPoint>()
            // Retain historical points to keep the road stable as they drive
            val maxHistory = currentList.takeLast(12)
            if (maxHistory.isNotEmpty()) {
                newNodes.addAll(maxHistory.map { GeoPoint(it.latitude, it.longitude) })
            } else {
                newNodes.add(GeoPoint(lat - deltaLat * 0.5, lng - deltaLng * 0.5))
            }
            newNodes.add(incomingGeo)
            newNodes.add(ahead1)
            newNodes.add(ahead2)

            selectedRoad.value = MockRoad(
                id = "real_gps_free",
                name = "フリー軌跡計測 (自律道路吸着中)",
                nodes = newNodes,
                tunnelRanges = emptyList()
            )
            activeRoad = selectedRoad.value
        }

        // 2. Check if we are in tunnel. If auto tunnel is enabled and we approach a predetermined tunnel index, enter EXTRAPOLATING_TUNNEL!
        if (isAutoTunnelEnabled.value && activeRoad.nodes.isNotEmpty()) {
            val simulatedTunnelDetection = activeRoad.isNodeIndexInTunnel(simIndex)
            if (simulatedTunnelDetection && _gpsStatus.value != GpsStatus.EXTRAPOLATING_TUNNEL) {
                // Auto entered tunnel range!
                lastSpeedKmh = if (isSimulationRunning) targetSimSpeedKmh.value else (if (rawSpeedKmh > 5.0) rawSpeedKmh else 30.0)
                _gpsStatus.value = GpsStatus.EXTRAPOLATING_TUNNEL
                Log.d("RouteViewModel", "Entered tunnel automatically at node index $simIndex! Dead reckoning initiated.")
            } else if (!simulatedTunnelDetection && _gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL && simulatedTunnelDetection != isSimulationRunning) {
                // Left tunnel!
                _gpsStatus.value = if (isSnapped.value) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
            }
        }

        // 3. Extrapolation handler or GPS Lock Snap
        val finalPoint: RoutePoint

        if (_gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL) {
            // Tunnel Dead Reckoning is active!
            // We ignore raw parameters, extrapolate along road path at lastSpeedKmh!
            finalPoint = applyDeadReckoningExtrapolation()
        } else {
            // Normal operation
            val speedToRecord = if (isSimulationRunning) {
                targetSimSpeedKmh.value
            } else {
                if (rawSpeedKmh >= 0.1) rawSpeedKmh else 0.0
            }
            _currentSpeedKmh.value = speedToRecord

            if (isSnapped.value && activeRoad.nodes.isNotEmpty()) {
                // Snap coordinate to the MIDDLE OF THE ROAD (orthogonal geometry mapping)
                val snappedGeo = activeRoad.snapPoint(incomingGeo)
                _currentLocation.value = snappedGeo
                finalPoint = RoutePoint(
                    latitude = snappedGeo.latitude,
                    longitude = snappedGeo.longitude,
                    speedKmh = speedToRecord,
                    timestamp = System.currentTimeMillis(),
                    isInterpolated = false
                )
            } else {
                // Real GPS coordinates directly, absolutely no artificial jitter
                _currentLocation.value = incomingGeo
                finalPoint = RoutePoint(
                    latitude = lat,
                    longitude = lng,
                    speedKmh = speedToRecord,
                    timestamp = System.currentTimeMillis(),
                    isInterpolated = false
                )
            }
        }

        if (forceRecord) {
            appendPointToActiveTrail(finalPoint)
        }
    }

    /**
     * Executes dead-reckoned geographic extrapolation inside tunnels.
     * Computes vectors using last known pace and follows the road segments.
     */
    private fun applyDeadReckoningExtrapolation(): RoutePoint {
        val road = selectedRoad.value
        val defaultPosition = _currentLocation.value

        // Maintain constant tunnel drift speed
        _currentSpeedKmh.value = lastSpeedKmh

        val angle = if (road.nodes.isNotEmpty()) {
            val nextIndex = (simIndex + 1).coerceAtMost(road.nodes.size - 1)
            val roadSegmentTarget = road.nodes[nextIndex]
            val bearingLat = roadSegmentTarget.latitude - defaultPosition.latitude
            val bearingLng = roadSegmentTarget.longitude - defaultPosition.longitude
            Math.atan2(bearingLng, bearingLat)
        } else {
            val path = _recordedPath.value
            if (path.size >= 2) {
                val p1 = path[path.size - 2]
                val p2 = path.last()
                Math.atan2(p2.longitude - p1.longitude, p2.latitude - p1.latitude)
            } else {
                Math.toRadians(90.0) // Fallback heading East
            }
        }

        // Calculate travel displacement (60km/h is 16.6m/s. We update every 1s, so delta is ~16.6m)
        // 1 degree latitude is exactly ~111,000 meters. Delta in degrees:
        val speedMs = (lastSpeedKmh * 1000.0) / 3600.0
        val deltaMeter = speedMs * 1.0 // 1 second step
        val deltaLat = (deltaMeter * cos(angle)) / 111120.0
        val deltaLng = (deltaMeter * sin(angle)) / (111120.0 * cos(Math.toRadians(defaultPosition.latitude)))

        val newLat = (defaultPosition.latitude + deltaLat).coerceIn(-90.0, 90.0)
        val newLng = (defaultPosition.longitude + deltaLng).coerceIn(-180.0, 180.0)

        val newGeo = GeoPoint(newLat, newLng)
        _currentLocation.value = newGeo

        val finalGeo = if (road.nodes.isNotEmpty()) road.snapPoint(newGeo) else newGeo

        return RoutePoint(
            latitude = finalGeo.latitude,
            longitude = finalGeo.longitude,
            speedKmh = lastSpeedKmh,
            timestamp = System.currentTimeMillis(),
            isInterpolated = true // Flagged orange on the UI!
        )
    }

    private fun appendPointToActiveTrail(point: RoutePoint) {
        val currentList = _recordedPath.value.toMutableList()
        val prevPoint = currentList.lastOrNull()

        currentList.add(point)
        _recordedPath.value = currentList

        // Recompute distance
        if (prevPoint != null) {
            val dist = GeoPoint(prevPoint.latitude, prevPoint.longitude)
                .distanceTo(GeoPoint(point.latitude, point.longitude))
            _totalDistanceKm.value += dist
        }

        // Recompute average speed
        if (_durationSeconds.value > 0) {
            _averageSpeedKmh.value = (_totalDistanceKm.value / (_durationSeconds.value / 3600.0))
                .coerceAtMost(220.0)
        }
    }

    /**
     * Driving Simulation Loop. Runs on local clocks to easily test on standard emulators.
     * Glides smoothly along road coordinates by tracking distance traveled physically.
     */
    private fun startSimulatedDriving() {
        isSimulationRunning = true
        _currentSpeedKmh.value = targetSimSpeedKmh.value
        simDistanceMeters = 0.0

        simulationJob = viewModelScope.launch {
            val road = selectedRoad.value
            val roadLength = getRoadTotalLengthMeters(road)
            val stepMs = 100L // 100ms step for buttery-smooth visual updates
            val stepSec = stepMs / 1000.0 // 0.1s step size
            var lastRecordTime = 0L

            // Simulate point iterations
            while (isSimulationRunning && _gpsStatus.value != GpsStatus.STOPPED) {
                if (_gpsStatus.value != GpsStatus.PAUSED) {
                    val currentSpeed = targetSimSpeedKmh.value
                    _currentSpeedKmh.value = currentSpeed

                    if (road.nodes.isNotEmpty()) {
                        // Traverse distance based on exact speed (meters per fraction of second)
                        val speedMs = (currentSpeed * 1000.0) / 3600.0
                        simDistanceMeters += speedMs * stepSec

                        if (roadLength > 0 && simDistanceMeters >= roadLength) {
                            // Reached the end of the road!
                            val lastNode = road.nodes.last()
                            processIncomingLocation(lastNode.latitude, lastNode.longitude, currentSpeed, forceRecord = true)
                            stopTracking(saveAutomatically = true)
                            break
                        }

                        // Interpolate exact coordinate at simDistanceMeters
                        val (nextPoint, currentIndex) = getPositionAtDistance(road, simDistanceMeters)
                        simIndex = currentIndex

                        val now = System.currentTimeMillis()
                        // Record trail points exactly once per second to prevent route line congestion
                        val recordNow = (now - lastRecordTime) >= 1000L
                        if (recordNow) {
                            lastRecordTime = now
                        }

                        processIncomingLocation(
                            nextPoint.latitude,
                            nextPoint.longitude,
                            currentSpeed,
                            forceRecord = recordNow
                        )
                    }
                }
                delay(stepMs)
            }
        }
    }

    private fun getRoadTotalLengthMeters(road: MockRoad): Double {
        if (road.nodes.isEmpty()) return 0.0
        var total = 0.0
        for (i in 0 until road.nodes.size - 1) {
            total += road.nodes[i].distanceTo(road.nodes[i + 1]) * 1000.0
        }
        return total
    }

    private fun getPositionAtDistance(road: MockRoad, distanceMeters: Double): Pair<GeoPoint, Int> {
        if (road.nodes.isEmpty()) return Pair(_currentLocation.value, 0)
        if (road.nodes.size == 1) return Pair(road.nodes[0], 0)

        var accumulatedDistance = 0.0
        for (i in 0 until road.nodes.size - 1) {
            val nodeA = road.nodes[i]
            val nodeB = road.nodes[i + 1]
            val segDist = nodeA.distanceTo(nodeB) * 1000.0
            if (accumulatedDistance + segDist >= distanceMeters) {
                val remain = distanceMeters - accumulatedDistance
                val fraction = if (segDist > 0) remain / segDist else 0.0
                val lat = nodeA.latitude + (nodeB.latitude - nodeA.latitude) * fraction
                val lng = nodeA.longitude + (nodeB.longitude - nodeA.longitude) * fraction
                return Pair(GeoPoint(lat, lng), i)
            }
            accumulatedDistance += segDist
        }
        return Pair(road.nodes.last(), road.nodes.size - 1)
    }

    private fun startRecordingTimer() {
        trackingTimerJob?.cancel()
        _durationSeconds.value = 0L

        trackingTimerJob = viewModelScope.launch {
            while (_gpsStatus.value != GpsStatus.STOPPED) {
                if (_gpsStatus.value != GpsStatus.PAUSED) {
                    delay(1000L)
                    _durationSeconds.value += 1

                    // Automatic Tunnel detection logic for real GPS tracking:
                    // If auto-tunnel is enabled, and we haven't received a callback update for over 4 seconds, we assume tunnel entry / signal loss.
                    if (isAutoTunnelEnabled.value &&
                        _gpsStatus.value != GpsStatus.EXTRAPOLATING_TUNNEL &&
                        _gpsStatus.value != GpsStatus.STOPPED &&
                        _gpsStatus.value != GpsStatus.PAUSED &&
                        lastGpsUpdateTime > 0L &&
                        System.currentTimeMillis() - lastGpsUpdateTime > 4000L
                    ) {
                        lastSpeedKmh = _currentSpeedKmh.value.coerceAtLeast(15.0)
                        _gpsStatus.value = GpsStatus.EXTRAPOLATING_TUNNEL
                        Log.d("RouteViewModel", "No GPS updates for 4 seconds. Automatically entering tunnel/dead-reckoning mode!")
                    }

                    // If in extrapolating tunnel state, tick the extrapolation step forward on our timer clock
                    if (_gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL) {
                        val finalPoint = applyDeadReckoningExtrapolation()
                        appendPointToActiveTrail(finalPoint)
                    }
                } else {
                    delay(500L)
                }
            }
        }
    }

    /**
     * Obtains the user's current raw GPS position once on application launch or permission grant.
     */
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocationOnce(context: Context) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val geo = GeoPoint(location.latitude, location.longitude)
                    _currentLocation.value = geo
                    Log.d("RouteViewModel", "Fetched startup location: $geo")
                    ensureDynamicRoadNodes(geo)
                } else {
                    // Cache is empty; actively request a single high-accuracy location update to lock position instantly
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                        .setMaxUpdates(1)
                        .build()
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val loc = result.lastLocation ?: return
                            val geo = GeoPoint(loc.latitude, loc.longitude)
                            _currentLocation.value = geo
                            ensureDynamicRoadNodes(geo)
                            client.removeLocationUpdates(this)
                        }
                    }
                    client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                }
            }
        } catch (e: Exception) {
            Log.e("RouteViewModel", "Failed to fetch startup location", e)
        }
    }
}

/**
 * Custom Factory as required by Architecture Guidelines (Simple constructor dependency injection)
 */
class RouteViewModelFactory(private val repository: RouteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RouteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RouteViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
