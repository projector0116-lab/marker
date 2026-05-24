package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.util.GeoPoint
import com.example.util.MockRoad
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.*

@Composable
fun ScenicMapView(
    currentLocation: GeoPoint,
    recordedPath: List<com.example.data.RoutePoint>,
    selectedRoad: MockRoad,
    gpsStatus: GpsStatus,
    overlayRoutePoints: List<com.example.data.RoutePoint>?,
    modifier: Modifier = Modifier,
    zoomScale: Float = 220000f, // Keep signature compatibility
    autoCenter: Boolean = true
) {
    // Current LatLng
    val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)

    // Remember camera position
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
    }

    // Center on location update
    LaunchedEffect(currentLocation.latitude, currentLocation.longitude, autoCenter) {
        if (autoCenter) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(currentLatLng),
                1000
            )
        }
    }

    // Map properties and UI Settings for Light Mode
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false // Will draw our own custom Marker as target position selector
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            // 1. Draw highway lines
            if (selectedRoad.nodes.isNotEmpty()) {
                val roadPoints = selectedRoad.nodes.map { LatLng(it.latitude, it.longitude) }
                // Bottom casing
                Polyline(
                    points = roadPoints,
                    color = Color(0xFFD1D5DB),
                    width = 24f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
                // Center line
                Polyline(
                    points = roadPoints,
                    color = Color(0xFFFFFFFF),
                    width = 16f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
            }

            // 2. Draw historical saved overlay trails in Green color
            if (!overlayRoutePoints.isNullOrEmpty()) {
                val overlayPoints = overlayRoutePoints.map { LatLng(it.latitude, it.longitude) }
                Polyline(
                    points = overlayPoints,
                    color = Color(0x6616A34A),
                    width = 16f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
                Polyline(
                    points = overlayPoints,
                    color = Color(0xFF16A34A),
                    width = 10f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
            }

            // 3. Draw recorded live path in blue color
            if (recordedPath.size > 1) {
                val pPoints = recordedPath.map { LatLng(it.latitude, it.longitude) }
                
                // Diffuse blue backing shadow
                Polyline(
                    points = pPoints,
                    color = Color(0xFF1A73E8).copy(alpha = 0.35f),
                    width = 18f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )

                // Sleep royal blue path line
                Polyline(
                    points = pPoints,
                    color = Color(0xFF1A73E8),
                    width = 10f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )

                // Circle dots for interpolations
                recordedPath.forEach { pt ->
                    if (pt.isInterpolated) {
                        Circle(
                            center = LatLng(pt.latitude, pt.longitude),
                            radius = 4.0, // 4 meters radius circle on interpolated tunnel coordinate
                            fillColor = Color(0xFFEA580C),
                            strokeColor = Color.White,
                            strokeWidth = 3f
                        )
                    }
                }
            }

            // 4. Custom neat Marker representing current location coordinates
            val currentSpeedLabel = if (recordedPath.isNotEmpty()) {
                "${String.format("%.1f", recordedPath.last().speedKmh)} km/h"
            } else {
                "0.0 km/h"
            }

            Marker(
                state = MarkerState(position = currentLatLng),
                title = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) "位置自律推測 (トンネル内)" else "現在地",
                snippet = "速度: $currentSpeedLabel"
            )
        }
    }
}
