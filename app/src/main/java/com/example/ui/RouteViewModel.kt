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

    // Sim/Timer job controllers
    private var trackingTimerJob: Job? = null
    private var simulationJob: Job? = null
    private var simIndex = 0
    private var targetSimSpeedKmh = 50.0 // User controls this slider
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

    private fun resetToRoadStart() {
        val road = selectedRoad.value
        if (road.nodes.isNotEmpty()) {
            _currentLocation.value = road.nodes[0]
            simIndex = 0
            _currentSpeedKmh.value = 0.0
            _totalDistanceKm.value = 0.0
            _averageSpeedKmh.value = 0.0
            _durationSeconds.value = 0L
            _recordedPath.value = emptyList()
        }
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

        // Initialize state
        _gpsStatus.value = if (isSnapped.value) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
        _recordedPath.value = emptyList()
        _totalDistanceKm.value = 0.0
        _averageSpeedKmh.value = 0.0
        _durationSeconds.value = 0L

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

        // Shutdown jobs
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
        targetSimSpeedKmh = kmh
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
            _currentSpeedKmh.value = targetSimSpeedKmh
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
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .build()

            stopRealGpsTracking() // Make sure to clean up first

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return

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
    private fun processIncomingLocation(lat: Double, lng: Double, rawSpeedKmh: Double) {
        if (_gpsStatus.value == GpsStatus.PAUSED || _gpsStatus.value == GpsStatus.STOPPED) return

        // 1. Check if we are in tunnel. If the emulator mock approaches a predetermined tunnel index, we enter EXTRAPOLATING_TUNNEL automatically!
        val activeRoad = selectedRoad.value
        val simulatedTunnelDetection = activeRoad.isNodeIndexInTunnel(simIndex)

        if (simulatedTunnelDetection && _gpsStatus.value != GpsStatus.EXTRAPOLATING_TUNNEL) {
            // Auto entered tunnel range!
            lastSpeedKmh = if (rawSpeedKmh > 5.0) rawSpeedKmh else targetSimSpeedKmh
            _gpsStatus.value = GpsStatus.EXTRAPOLATING_TUNNEL
            Log.d("RouteViewModel", "Entered tunnel automatically at node index $simIndex! Dead reckoning initiated.")
        } else if (!simulatedTunnelDetection && _gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL && simulatedTunnelDetection != isSimulationRunning) {
            // Left tunnel!
            _gpsStatus.value = if (isSnapped.value) GpsStatus.EXCELLENT else GpsStatus.RAW_ONLY
        }

        // 2. Extrapolation handler or GPS Lock Snap
        val finalPoint: RoutePoint
        val incomingGeo = GeoPoint(lat, lng)

        if (_gpsStatus.value == GpsStatus.EXTRAPOLATING_TUNNEL) {
            // Tunnel Dead Reckoning is active!
            // We ignore raw parameters, extrapolate along road path at lastSpeedKmh!
            finalPoint = applyDeadReckoningExtrapolation()
        } else {
            // Normal operation
            val speedToRecord = if (rawSpeedKmh > 1.0) rawSpeedKmh else targetSimSpeedKmh
            _currentSpeedKmh.value = speedToRecord

            if (isSnapped.value) {
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
                // No snapping: user gets raw erratic coordinate (simulated with a tiny wiggle to mimic GPS jitter)
                val jitterLat = lat + (Math.random() - 0.5) * 0.00018
                val jitterLng = lng + (Math.random() - 0.5) * 0.00018
                _currentLocation.value = GeoPoint(jitterLat, jitterLng)
                finalPoint = RoutePoint(
                    latitude = jitterLat,
                    longitude = jitterLng,
                    speedKmh = speedToRecord,
                    timestamp = System.currentTimeMillis(),
                    isInterpolated = false
                )
            }
        }

        appendPointToActiveTrail(finalPoint)
    }

    /**
     * Executes dead-reckoned geographic extrapolation inside tunnels.
     * Computes vectors using last known pace and follows the road segments.
     */
    private fun applyDeadReckoningExtrapolation(): RoutePoint {
        val road = selectedRoad.value
        val lastPoint = _recordedPath.value.lastOrNull()
        val defaultPosition = _currentLocation.value

        // Maintain constant tunnel drift speed
        _currentSpeedKmh.value = lastSpeedKmh

        val nextIndex = (simIndex + 1).coerceAtMost(road.nodes.size - 1)
        val roadSegmentTarget = road.nodes[nextIndex]

        // Travel along the road nodes to mimic natural cave following
        val bearingLat = roadSegmentTarget.latitude - defaultPosition.latitude
        val bearingLng = roadSegmentTarget.longitude - defaultPosition.longitude
        val angle = Math.atan2(bearingLng, bearingLat)

        // Calculate travel displacement (60km/h is 16.6m/s. We update every 1s, so delta is ~16.6m)
        // 1 degree latitude is exactly ~111,000 meters. Delta in degrees:
        val speedMs = (lastSpeedKmh * 1000.0) / 3600.0
        val deltaMeter = speedMs * 1.0 // 1 second step
        val deltaLat = (deltaMeter * cos(angle)) / 111120.0
        val deltaLng = (deltaMeter * sin(angle)) / (111120.0 * cos(Math.toRadians(defaultPosition.latitude)))

        val newLat = (defaultPosition.latitude + deltaLat).coerceIn(35.0, 36.0)
        val newLng = (defaultPosition.longitude + deltaLng).coerceIn(138.0, 140.0)

        val newGeo = GeoPoint(newLat, newLng)
        _currentLocation.value = newGeo

        // In a tunnel, we guarantee snapping to road center because dead-reckoning uses road vectors!
        val snappedGeo = road.snapPoint(newGeo)

        return RoutePoint(
            latitude = snappedGeo.latitude,
            longitude = snappedGeo.longitude,
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
     * Glides smoothly between preset road nodes.
     */
    private fun startSimulatedDriving() {
        isSimulationRunning = true
        _currentSpeedKmh.value = targetSimSpeedKmh

        simulationJob = viewModelScope.launch {
            val road = selectedRoad.value
            if (simIndex >= road.nodes.size - 1) {
                simIndex = 0 // loop
            }

            // Simulate point iterations
            while (isSimulationRunning && _gpsStatus.value != GpsStatus.STOPPED) {
                if (_gpsStatus.value != GpsStatus.PAUSED) {
                    val node = road.nodes[simIndex]

                    // Run processor
                    processIncomingLocation(node.latitude, node.longitude, targetSimSpeedKmh)

                    // Increment index
                    simIndex++
                    if (simIndex >= road.nodes.size) {
                        // End of road reached. Let's automatically halt and save.
                        stopTracking(saveAutomatically = true)
                        break
                    }
                }
                delay(1000L) // Refresh rate once per second. High alignment.
            }
        }
    }

    private fun startRecordingTimer() {
        trackingTimerJob?.cancel()
        _durationSeconds.value = 0L

        trackingTimerJob = viewModelScope.launch {
            while (_gpsStatus.value != GpsStatus.STOPPED) {
                if (_gpsStatus.value != GpsStatus.PAUSED) {
                    delay(1000L)
                    _durationSeconds.value += 1
                } else {
                    delay(500L)
                }
            }
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
