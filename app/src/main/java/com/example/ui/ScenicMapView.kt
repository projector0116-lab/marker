package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.util.GeoPoint
import com.example.util.MockRoad
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ScenicMapView(
    currentLocation: GeoPoint,
    recordedPath: List<com.example.data.RoutePoint>,
    selectedRoad: MockRoad,
    gpsStatus: GpsStatus,
    overlayRoutePoints: List<com.example.data.RoutePoint>?,
    modifier: Modifier = Modifier,
    zoomScale: Float = 220000f, // Scale factor: pixels per degree
    autoCenter: Boolean = true
) {
    // Interactive map offsets for panning
    var mapOffsetLat by remember { mutableStateOf(0.0) }
    var mapOffsetLng by remember { mutableStateOf(0.0) }

    // Synchronize map center around vehicle or manual pans
    val centerLat = if (autoCenter) currentLocation.latitude else (currentLocation.latitude + mapOffsetLat)
    val centerLng = if (autoCenter) currentLocation.longitude else (currentLocation.longitude + mapOffsetLng)

    // Reset offsets when auto-centering is re-enabled
    LaunchedEffect(autoCenter) {
        if (autoCenter) {
            mapOffsetLat = 0.0
            mapOffsetLng = 0.0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(autoCenter) {
                if (!autoCenter) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Convert pixel drags back into latitude/longitude offsets
                        // positive dy in screen is down, which corresponds to lower latitude
                        val dLat = -(dragAmount.y.toDouble() / zoomScale)
                        val dLng = dragAmount.x.toDouble() / (zoomScale * cos(Math.toRadians(centerLat)))
                        mapOffsetLat += dLat
                        mapOffsetLng += dLng
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val halfW = width / 2f
            val halfH = height / 2f

            // Helper to translate latitude/longitude coordinates to local canvas coordinate space (x, y)
            fun toCanvasCoords(lat: Double, lng: Double): Offset {
                val cosFactor = cos(Math.toRadians(centerLat))
                val x = halfW + ((lng - centerLng) * zoomScale * cosFactor).toFloat()
                val y = halfH - ((lat - centerLat) * zoomScale).toFloat()
                return Offset(x, y)
            }

            // 1. Draw Cosmic Navigation Grid (Grid lines every 0.005 degrees)
            val gridStep = 0.005
            val startLat = (centerLat - (halfH / zoomScale)).coerceAtLeast(20.0)
            val endLat = (centerLat + (halfH / zoomScale)).coerceAtMost(50.0)
            val startLng = (centerLng - (halfW / zoomScale)).coerceAtLeast(100.0)
            val endLng = (centerLng + (halfW / zoomScale)).coerceAtMost(160.0)

            // Dynamic grid colors
            val gridColor = Color(0xFF1E293B)
            val subGridColor = Color(0xFF0F172A)

            // Fill canvas with deep slate blue tech deck background
            drawRect(color = Color(0xFF070A13))

            // Snap guidelines
            var latGrid = (startLat / gridStep).toInt() * gridStep
            while (latGrid <= endLat) {
                val pStart = toCanvasCoords(latGrid, startLng)
                val pEnd = toCanvasCoords(latGrid, endLng)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, pStart.y),
                    end = Offset(width, pEnd.y),
                    strokeWidth = 1f
                )
                latGrid += gridStep
            }

            var lngGrid = (startLng / gridStep).toInt() * gridStep
            while (lngGrid <= endLng) {
                val pStart = toCanvasCoords(startLat, lngGrid)
                val pEnd = toCanvasCoords(endLat, lngGrid)
                drawLine(
                    color = gridColor,
                    start = Offset(pStart.x, 0f),
                    end = Offset(pEnd.x, height),
                    strokeWidth = 1f
                )
                lngGrid += gridStep
            }

            // 2. Draw Scenic Landscape Elements specific to selected routes
            if (selectedRoad.id == "coast_kamakura") {
                // Ocean overlay (Pacific Ocean) on the Southern side (lower latitude < 35.3060)
                val coastLinePoints = listOf(
                    GeoPoint(35.3050, 139.5200),
                    GeoPoint(35.3055, 139.5300),
                    GeoPoint(35.3040, 139.5400),
                    GeoPoint(35.3045, 139.5480),
                    GeoPoint(35.3062, 139.5530),
                    GeoPoint(35.3090, 139.5580),
                    GeoPoint(35.3100, 139.5640),
                    GeoPoint(35.3080, 139.5750),
                    GeoPoint(35.3060, 139.5900)
                )

                // Draw a beautiful blue ocean fill on the bottom half
                val oceanPath = Path()
                if (coastLinePoints.isNotEmpty()) {
                    val first = toCanvasCoords(coastLinePoints[0].latitude, coastLinePoints[0].longitude)
                    oceanPath.moveTo(first.x, first.y)
                    for (i in 1 until coastLinePoints.size) {
                        val pt = toCanvasCoords(coastLinePoints[i].latitude, coastLinePoints[i].longitude)
                        oceanPath.lineTo(pt.x, pt.y)
                    }
                    // Complete bounds to the screen base
                    oceanPath.lineTo(width, height)
                    oceanPath.lineTo(0f, height)
                    oceanPath.close()

                    drawPath(
                        path = oceanPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x250284C7), Color(0x500369A1))
                        )
                    )
                }
            }

            // 3. Draw Road Bed (Tarmac with dual-borders and white lanes)
            if (selectedRoad.nodes.size > 1) {
                val roadPath = Path()
                val startP = toCanvasCoords(selectedRoad.nodes[0].latitude, selectedRoad.nodes[0].longitude)
                roadPath.moveTo(startP.x, startP.y)

                for (i in 1 until selectedRoad.nodes.size) {
                    val pt = toCanvasCoords(selectedRoad.nodes[i].latitude, selectedRoad.nodes[i].longitude)
                    roadPath.lineTo(pt.x, pt.y)
                }

                // Draw black/slate base (the wide street pavement)
                drawPath(
                    path = roadPath,
                    color = Color(0xFF1E293B),
                    style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw grey center wear tarmac
                drawPath(
                    path = roadPath,
                    color = Color(0xFF334155),
                    style = Stroke(width = 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Center dashed lines indicating road center lane dividers
                drawPath(
                    path = roadPath,
                    color = Color(0x60FFFFFF),
                    style = Stroke(
                        width = 2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), 0f)
                    )
                )

                // 4. Paint Tunnels on top of the roads
                selectedRoad.tunnelRanges.forEach { range ->
                    val tunnelPath = Path()
                    val tStart = toCanvasCoords(selectedRoad.nodes[range.first].latitude, selectedRoad.nodes[range.first].longitude)
                    tunnelPath.moveTo(tStart.x, tStart.y)

                    for (idx in range.first + 1..range.last) {
                        if (idx < selectedRoad.nodes.size) {
                            val pt = toCanvasCoords(selectedRoad.nodes[idx].latitude, selectedRoad.nodes[idx].longitude)
                            tunnelPath.lineTo(pt.x, pt.y)
                        }
                    }

                    // Draw protective brown hollow tube representing mountain shell structure overlay
                    drawPath(
                        path = tunnelPath,
                        color = Color(0xFF78350F),
                        style = Stroke(width = 40f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = tunnelPath,
                        color = Color(0xFF1E1B4B), // inside dark cave
                        style = Stroke(width = 32f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
                    )

                    // Draw tunnel indicator lines
                    drawPath(
                        path = tunnelPath,
                        color = Color(0xFFF59E0B),
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f))
                        )
                    )

                    // Write text "TUNNEL" near the center of the range
                    val midIdx = (range.first + range.last) / 2
                    if (midIdx < selectedRoad.nodes.size) {
                        val midPt = toCanvasCoords(selectedRoad.nodes[midIdx].latitude, selectedRoad.nodes[midIdx].longitude)
                        drawCircle(
                            color = Color(0xFFD97706),
                            radius = 6f,
                            center = midPt
                        )
                    }
                }
            }

            // 5. Draw Historic Overlay Trails (Saved routes in Green color!)
            if (!overlayRoutePoints.isNullOrEmpty()) {
                val overlayPath = Path()
                val oStart = toCanvasCoords(overlayRoutePoints[0].latitude, overlayRoutePoints[0].longitude)
                overlayPath.moveTo(oStart.x, oStart.y)

                for (i in 1 until overlayRoutePoints.size) {
                    val pt = toCanvasCoords(overlayRoutePoints[i].latitude, overlayRoutePoints[i].longitude)
                    overlayPath.lineTo(pt.x, pt.y)
                }

                // Draw thick emerald green trace
                drawPath(
                    path = overlayPath,
                    color = Color(0xFF00E676),
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Core bright core
                drawPath(
                    path = overlayPath,
                    color = Color(0xFFB9F6CA),
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 6. Draw Recorded Traveled Trail (The blue line!)
            // Highly critical: "移動したところがあおいろのせんできろくされる"
            if (recordedPath.size > 1) {
                // To display tunnel interpolation elegantly, we can split drawing blocks:
                // Normal coordinates -> solid deep blue glow
                // Interpolated coordinates (inside tunnel) -> warning orange-blue segmented glow!
                var currentPath = Path()
                var isSegmentInterpolated = recordedPath[0].isInterpolated

                val firstLoc = toCanvasCoords(recordedPath[0].latitude, recordedPath[0].longitude)
                currentPath.moveTo(firstLoc.x, firstLoc.y)

                for (i in 1 until recordedPath.size) {
                    val pt = recordedPath[i]
                    val drawPt = toCanvasCoords(pt.latitude, pt.longitude)

                    if (pt.isInterpolated != isSegmentInterpolated) {
                        // Flush the previous path block with its respective style
                        val strokeColor = if (isSegmentInterpolated) Color(0xFF00B0FF) else Color(0xFF00E5FF)
                        val strokeStyle = if (isSegmentInterpolated) {
                            Stroke(
                                width = 12f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f)
                            )
                        } else {
                            Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        }

                        // Soft outer glow of the trails
                        drawPath(
                            path = currentPath,
                            color = strokeColor.copy(alpha = 0.35f),
                            style = if (isSegmentInterpolated) strokeStyle else Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Precise core line
                        drawPath(
                            path = currentPath,
                            color = if (isSegmentInterpolated) Color(0xFFFF9100) else Color(0xFFE0F7FA), // warning orange or ice glow
                            style = strokeStyle
                        )

                        // Start a new path from the intersection
                        currentPath = Path()
                        currentPath.moveTo(drawPt.x, drawPt.y)
                        isSegmentInterpolated = pt.isInterpolated
                    } else {
                        currentPath.lineTo(drawPt.x, drawPt.y)
                    }
                }

                // Flush final active trail segment
                val strokeColor = if (isSegmentInterpolated) Color(0xFF0284C7) else Color(0xFF00E5FF)
                val strokeStyle = if (isSegmentInterpolated) {
                    Stroke(
                        width = 10f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                } else {
                    Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                }

                // Wide diffuse backing glow
                drawPath(
                    path = currentPath,
                    color = strokeColor.copy(alpha = 0.40f),
                    style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Solid center neon tracking core
                drawPath(
                    path = currentPath,
                    color = if (isSegmentInterpolated) Color(0xFFFF9100) else Color(0xFF00E5FF),
                    style = strokeStyle
                )
            }

            // 7. Draw Vehicle cursor with a dynamic pulsing navigation ring at current coordinates
            val carOffset = toCanvasCoords(currentLocation.latitude, currentLocation.longitude)

            // Draw range radar rings depending on signal locks
            val ringColor = when (gpsStatus) {
                GpsStatus.EXCELLENT -> Color(0xFF00E5FF)
                GpsStatus.RAW_ONLY -> Color(0xFF38BDF8)
                GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFFF59E0B) // warning orange during tunnel supplementary
                GpsStatus.PAUSED -> Color(0xFF94A3B8)
                GpsStatus.STOPPED -> Color(0xFF64748B)
            }

            // Draw dynamic pulsating coordinate sonar
            drawCircle(
                color = ringColor.copy(alpha = 0.2f),
                radius = 28f,
                center = carOffset
            )

            drawCircle(
                color = ringColor.copy(alpha = 0.4f),
                radius = 18f,
                center = carOffset,
                style = Stroke(width = 1.5f)
            )

            // Dynamic Arrow / Compass Direction.
            // Let's compute bearing heading degrees using simple segment vectors direction to look polished
            var angleDegrees = 0.0
            if (recordedPath.size > 1) {
                val last = recordedPath.last()
                val prev = recordedPath[recordedPath.size - 2]
                val dLat = last.latitude - prev.latitude
                val dLng = last.longitude - prev.longitude
                angleDegrees = Math.toDegrees(Math.atan2(dLng, dLat))
            }

            val angleRad = Math.toRadians(angleDegrees)
            val arrowLength = 16f
            val backWidth = 10f

            // Vector math for delta cursor triangle heads
            val headX = (carOffset.x + arrowLength * sin(angleRad)).toFloat()
            val headY = (carOffset.y - arrowLength * cos(angleRad)).toFloat()

            // Left tail
            val leftRad = angleRad + Math.toRadians(140.0)
            val leftX = (carOffset.x + backWidth * sin(leftRad)).toFloat()
            val leftY = (carOffset.y - backWidth * cos(leftRad)).toFloat()

            // Right tail
            val rightRad = angleRad - Math.toRadians(140.0)
            val rightX = (carOffset.x + backWidth * sin(rightRad)).toFloat()
            val rightY = (carOffset.y - backWidth * cos(rightRad)).toFloat()

            // Draw beautiful triangular fighter spaceship dashboard cursor
            val cursorPath = Path().apply {
                moveTo(headX, headY)
                lineTo(leftX, leftY)
                lineTo(carOffset.x, carOffset.y) // retract center
                lineTo(rightX, rightY)
                close()
            }

            drawPath(
                path = cursorPath,
                color = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color(0xFFF59E0B) else Color(0xFFFFFFFF)
            )

            drawPath(
                path = cursorPath,
                color = ringColor,
                style = Stroke(width = 3f, join = StrokeJoin.Miter)
            )

            // Draw a neat solid dot indicator in the exact center
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = carOffset
            )
        }
    }
}
