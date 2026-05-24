package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Paint
import android.graphics.Typeface
import com.example.util.GeoPoint
import com.example.util.MockRoad
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ScenicMapView(
    currentLocation: GeoPoint,
    recordedPath: List<com.example.data.RoutePoint>,
    selectedRoad: MockRoad,
    gpsStatus: GpsStatus,
    overlayRoutePoints: List<com.example.data.RoutePoint>?,
    modifier: Modifier = Modifier,
    zoomScale: Float = 240000f,
    autoCenter: Boolean = true,
    onMapDragged: (() -> Unit)? = null,
    onReCenter: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Smooth animated drag tracking
    val animDragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Smoothly transition / snap drag offsets to zero when centering is requested
    LaunchedEffect(autoCenter, currentLocation) {
        if (autoCenter) {
            animDragOffset.animateTo(Offset.Zero, animationSpec = tween(400))
        }
    }

    // Set up native paints for high-fidelity text annotations
    val gridLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            alpha = 140
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    val landmarkLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#1E293B")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEFECE5)) // Warm sand slate offline background
            .pointerInput(autoCenter, currentLocation) {
                // Handle manual drag panning
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        animDragOffset.snapTo(animDragOffset.value + dragAmount)
                    }
                    onMapDragged?.invoke()
                }
            }
            .pointerInput(Unit) {
                // Double tap back to center shortcut
                detectTapGestures(
                    onDoubleTap = {
                        onReCenter?.invoke()
                    }
                )
            }
    ) {
        // High fidelity vector canvas mapping coordinates to 2D screen points
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerLat = currentLocation.latitude
            val centerLng = currentLocation.longitude
            val zoom = zoomScale

            // Angle alignment in radians for longitude compression correction
            val cosLat = Math.cos(Math.toRadians(centerLat))

            // Mathematical projection mapping (Geo coordinate -> 2D screen coordinate)
            fun project(lat: Double, lng: Double): Offset {
                val dx = (lng - centerLng) * cosLat * zoom
                val dy = -(lat - centerLat) * zoom
                return Offset(
                    x = (width / 2f) + dx.toFloat() + animDragOffset.value.x,
                    y = (height / 2f) + dy.toFloat() + animDragOffset.value.y
                )
            }

            // Estimate visible coordinate boundaries
            val latHalfSpan = (height / 2f) / zoom
            val lngHalfSpan = (width / 2f) / (zoom * cosLat)
            
            val minLat = centerLat - latHalfSpan - abs(animDragOffset.value.y) / zoom
            val maxLat = centerLat + latHalfSpan + abs(animDragOffset.value.y) / zoom
            val minLng = centerLng - lngHalfSpan - abs(animDragOffset.value.x) / (zoom * cosLat)
            val maxLng = centerLng + lngHalfSpan + abs(animDragOffset.value.x) / (zoom * cosLat)

            // Draw beautiful natural geography scenery based on selected course
            if (selectedRoad.id == "coast_kamakura") {
                // Draw sea polygon spanning the screen below Lat 35.305
                val seaPath = Path().apply {
                    val topLeft = project(35.305, 139.510)
                    val topRight = project(35.305, 139.590)
                    val bottomRight = project(35.280, 139.590)
                    val bottomLeft = project(35.280, 139.510)
                    
                    moveTo(topLeft.x, topLeft.y)
                    lineTo(topRight.x, topRight.y)
                    lineTo(bottomRight.x, bottomRight.y)
                    lineTo(bottomLeft.x, bottomLeft.y)
                    close()
                }
                drawPath(seaPath, Color(0xFF90E0EF).copy(alpha = 0.6f))
                
                // Draw Enoshima Island (江の島)
                val enoshimaCenter = project(35.300, 139.510)
                drawCircle(
                    color = Color(0xFF0F766E).copy(alpha = 0.8f),
                    radius = 28.dp.toPx(),
                    center = enoshimaCenter
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "江の島 (Enoshima Island)",
                        enoshimaCenter.x,
                        enoshimaCenter.y + 4.dp.toPx(),
                        landmarkLabelPaint
                    )
                }
            } else if (selectedRoad.id == "hakone_pass") {
                // Draw Lake Ashi (芦ノ湖) as a gorgeous serene fluid lake body
                val lakeCenter = project(35.200, 139.025)
                val lakeWidth = 45.dp.toPx()
                val lakeHeight = 110.dp.toPx()
                drawOval(
                    color = Color(0xFF0077B6).copy(alpha = 0.5f),
                    topLeft = Offset(lakeCenter.x - lakeWidth, lakeCenter.y - lakeHeight),
                    size = androidx.compose.ui.geometry.Size(lakeWidth * 2f, lakeHeight * 2f)
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "芦ノ湖 (Lake Ashi)",
                        lakeCenter.x,
                        lakeCenter.y,
                        landmarkLabelPaint
                    )
                }
                
                // Draw Mountain Peak "Hakone Summit"
                val peakCenter = project(35.208, 139.040)
                drawCircle(
                    color = Color(0x3314532D),
                    radius = 50.dp.toPx(),
                    center = peakCenter
                )
                drawCircle(
                    color = Color(0x3314532D),
                    radius = 30.dp.toPx(),
                    center = peakCenter
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "神山 Mt. Kamiyama (1,438m)",
                        peakCenter.x,
                        peakCenter.y - 12.dp.toPx(),
                        landmarkLabelPaint
                    )
                }
            } else if (selectedRoad.id == "expressway_tunnels") {
                // Draw majestic Sagami River cutting across
                val riverPath = Path().apply {
                    val startPt = project(35.380, 139.390)
                    val endPt = project(35.330, 139.395)
                    moveTo(startPt.x, startPt.y)
                    lineTo(endPt.x, endPt.y)
                }
                drawPath(
                    path = riverPath,
                    color = Color(0x904CC9F0),
                    style = Stroke(
                        width = 32.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
                val labelPt = project(35.355, 139.392)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "相模川 (Sagami River)",
                        labelPt.x,
                        labelPt.y,
                        landmarkLabelPaint
                    )
                }
            }

            // Draw a neat coordinate radar background grid
            // Grid degree grid step is adjusted dynamically based on magnification
            val step = when {
                zoom > 350000 -> 0.001
                zoom > 150000 -> 0.002
                else -> 0.005
            }

            val startLat = Math.floor(minLat / step) * step
            val endLat = Math.ceil(maxLat / step) * step
            val startLng = Math.floor(minLng / step) * step
            val endLng = Math.ceil(maxLng / step) * step

            // Vertical Longitude Lines
            var lng = startLng
            while (lng <= endLng) {
                val topPt = project(endLat, lng)
                val bottomPt = project(startLat, lng)
                drawLine(
                    color = Color(0x18475569),
                    start = topPt,
                    end = bottomPt,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
                // Draw coordinate text label at the bottom spacing bounds safely
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        String.format("%.3f°E", lng),
                        topPt.x + 10f,
                        height - 30f,
                        gridLabelPaint
                    )
                }
                lng += step
            }

            // Horizontal Latitude Lines
            var lat = startLat
            while (lat <= endLat) {
                val leftPt = project(lat, minLng)
                val rightPt = project(lat, maxLng)
                drawLine(
                    color = Color(0x18475569),
                    start = leftPt,
                    end = rightPt,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        String.format("%.3f°N", lat),
                        30f,
                        leftPt.y - 10f,
                        gridLabelPaint
                    )
                }
                lat += step
            }

            // Draw Selected Road (Scenic route highway trace overlays)
            if (selectedRoad.nodes.isNotEmpty()) {
                val roadPath = Path().apply {
                    val startPt = project(selectedRoad.nodes[0].latitude, selectedRoad.nodes[0].longitude)
                    moveTo(startPt.x, startPt.y)
                    for (i in 1 until selectedRoad.nodes.size) {
                        val toPt = project(selectedRoad.nodes[i].latitude, selectedRoad.nodes[i].longitude)
                        lineTo(toPt.x, toPt.y)
                    }
                }

                // Outer casing
                drawPath(
                    path = roadPath,
                    color = Color(0xFF94A3B8),
                    style = Stroke(
                        width = 16.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Main asphalt surface
                drawPath(
                    path = roadPath,
                    color = Color(0xFF475569),
                    style = Stroke(
                        width = 11.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Dotted yellow center lane divider
                drawPath(
                    path = roadPath,
                    color = Color(0xFFFBBF24),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 15f), 0f)
                    )
                )

                // Render Tunnel Overpass bounds
                selectedRoad.tunnelRanges.forEach { tunnelRange ->
                    val tunnelPath = Path()
                    val nodes = selectedRoad.nodes
                    val runStart = tunnelRange.first.coerceIn(0, nodes.size - 1)
                    val runEnd = tunnelRange.last.coerceIn(0, nodes.size - 1)
                    if (runStart < runEnd) {
                        val pStart = project(nodes[runStart].latitude, nodes[runStart].longitude)
                        tunnelPath.moveTo(pStart.x, pStart.y)
                        for (idx in runStart + 1..runEnd) {
                            val pTo = project(nodes[idx].latitude, nodes[idx].longitude)
                            tunnelPath.lineTo(pTo.x, pTo.y)
                        }

                        // Thick tunnel walls casing
                        drawPath(
                            path = tunnelPath,
                            color = Color(0xFF334155),
                            style = Stroke(
                                width = 24.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Dark mountain tunnel interior
                        drawPath(
                            path = tunnelPath,
                            color = Color(0xFF0F172A),
                            style = Stroke(
                                width = 14.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Neon orange warning stripes on tunnel limits
                        drawPath(
                            path = tunnelPath,
                            color = Color(0xFFF97316),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
                            )
                        )

                        // Render beautiful labels over mountain portals
                        val midIndex = (runStart + runEnd) / 2
                        val midPt = project(nodes[midIndex].latitude, nodes[midIndex].longitude)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "トンネル (TUNNEL)",
                                midPt.x,
                                midPt.y - 20.dp.toPx(),
                                landmarkLabelPaint
                            )
                        }
                    }
                }

                // Render Preset Station Landmarks (e.g., Enoshima Shore, Yuigahama, Hakone Summit)
                if (selectedRoad.nodes.isNotEmpty()) {
                    val id = selectedRoad.id
                    val endpoints = listOf(
                        0 to "START (起点)",
                        (selectedRoad.nodes.size - 1) to "GOAL (終点)"
                    )
                    endpoints.forEach { (index, text) ->
                        if (index < selectedRoad.nodes.size) {
                            val pt = project(selectedRoad.nodes[index].latitude, selectedRoad.nodes[index].longitude)
                            drawCircle(
                                color = if (index == 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = pt
                            )
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    text,
                                    pt.x,
                                    pt.y - 14.dp.toPx(),
                                    landmarkLabelPaint
                                )
                            }
                        }
                    }

                    // Curated scenic text callouts based on coordinates
                    if (id == "coast_kamakura" && selectedRoad.nodes.size > 100) {
                        // Enoshima Coast
                        val enoshimaPt = project(selectedRoad.nodes[0].latitude, selectedRoad.nodes[0].longitude)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText("江の島海岸 Shoreline", enoshimaPt.x, enoshimaPt.y + 35.dp.toPx(), landmarkLabelPaint)
                        }
                        // Yuigahama
                        val yuiIndex = (selectedRoad.nodes.size * 0.75).toInt()
                        if (yuiIndex < selectedRoad.nodes.size) {
                            val yuiPt = project(selectedRoad.nodes[yuiIndex].latitude, selectedRoad.nodes[yuiIndex].longitude)
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText("由比ヶ浜 Yuigahama Beach", yuiPt.x, yuiPt.y + 35.dp.toPx(), landmarkLabelPaint)
                            }
                        }
                    } else if (id == "hakone_pass" && selectedRoad.nodes.size > 100) {
                        // Hakone Checkpoint
                        val checkpointIndex = (selectedRoad.nodes.size * 0.8).toInt()
                        if (checkpointIndex < selectedRoad.nodes.size) {
                            val cpPt = project(selectedRoad.nodes[checkpointIndex].latitude, selectedRoad.nodes[checkpointIndex].longitude)
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText("箱根関所 Checkpoint", cpPt.x, cpPt.y + 35.dp.toPx(), landmarkLabelPaint)
                            }
                        }
                    }
                }
            }

            // Draw Recorded Active GPS Trail route line
            if (recordedPath.isNotEmpty()) {
                val tracePath = Path().apply {
                    val startPt = project(recordedPath[0].latitude, recordedPath[0].longitude)
                    moveTo(startPt.x, startPt.y)
                    for (i in 1 until recordedPath.size) {
                        val toPt = project(recordedPath[i].latitude, recordedPath[i].longitude)
                        lineTo(toPt.x, toPt.y)
                    }
                }

                // Semi-transparent wide blue glow boundary
                drawPath(
                    path = tracePath,
                    color = Color(0x403B82F6),
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Main electric blue tracking ribbon
                drawPath(
                    path = tracePath,
                    color = Color(0xFF1E3A8A),
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Fine point dots over core telemetry recordings
                recordedPath.forEach { point ->
                    val screenPt = project(point.latitude, point.longitude)
                    drawCircle(
                        color = if (point.isInterpolated) Color(0xFFF97316) else Color(0xFF60A5FA),
                        radius = 2.2f.dp.toPx(),
                        center = screenPt
                    )
                }
            }

            // Draw Database History Overlay comparisons traces (Emerald Trails)
            if (!overlayRoutePoints.isNullOrEmpty() && overlayRoutePoints.size > 1) {
                val overlayPath = Path().apply {
                    val sPt = project(overlayRoutePoints[0].latitude, overlayRoutePoints[0].longitude)
                    moveTo(sPt.x, sPt.y)
                    for (i in 1 until overlayRoutePoints.size) {
                        val toPt = project(overlayRoutePoints[i].latitude, overlayRoutePoints[i].longitude)
                        lineTo(toPt.x, toPt.y)
                    }
                }

                drawPath(
                    path = overlayPath,
                    color = Color(0x4010B981),
                    style = Stroke(
                        width = 10.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                drawPath(
                    path = overlayPath,
                    color = Color(0xFF10B981),
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw Compass orientation widget in bottom-left corner
            val compassCenter = Offset(70.dp.toPx(), height - 130.dp.toPx())
            val compassRadius = 32.dp.toPx()
            
            // Outer Dial
            drawCircle(
                color = Color(0xDDFFFFFF),
                radius = compassRadius,
                center = compassCenter
            )
            drawCircle(
                color = Color(0xFF64748B),
                radius = compassRadius,
                center = compassCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
            
            // Orient text labels inside compass
            drawIntoCanvas { canvas ->
                val p = Paint().apply {
                    color = android.graphics.Color.parseColor("#475569")
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText("N", compassCenter.x, compassCenter.y - compassRadius + 22f, p)
                canvas.nativeCanvas.drawText("S", compassCenter.x, compassCenter.y + compassRadius - 8f, p)
            }

            // Direction pointer pointing true North
            val needleLen = compassRadius - 10f
            drawLine(
                color = Color(0xFFEF4444), // North (Red)
                start = companionOffset(compassCenter, 0f, 0f),
                end = companionOffset(compassCenter, 0f, -needleLen),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = Color(0xFF94A3B8), // South (Slate)
                start = companionOffset(compassCenter, 0f, 0f),
                end = companionOffset(compassCenter, 0f, needleLen),
                strokeWidth = 3.dp.toPx()
            )
            drawCircle(
                color = Color(0xFF334155),
                radius = 4.dp.toPx(),
                center = compassCenter
            )
        }

        // Animated Pulse for location dot
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animatedRadius by infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 42f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radius"
        )
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerLat = currentLocation.latitude
            val centerLng = currentLocation.longitude
            val zoom = zoomScale
            val cosLat = Math.cos(Math.toRadians(centerLat))

            fun project(lat: Double, lng: Double): Offset {
                val dx = (lng - centerLng) * cosLat * zoom
                val dy = -(lat - centerLat) * zoom
                return Offset(
                    x = (width / 2f) + dx.toFloat() + animDragOffset.value.x,
                    y = (height / 2f) + dy.toFloat() + animDragOffset.value.y
                )
            }

            val currentPt = project(currentLocation.latitude, currentLocation.longitude)

            // Dynamic Pulse rings
            drawCircle(
                color = Color(0xFF1F2937).copy(alpha = animatedAlpha),
                radius = animatedRadius.dp.toPx(),
                center = currentPt
            )

            // Core Location point beacon
            drawCircle(
                color = Color.White,
                radius = 8.5.dp.toPx(),
                center = currentPt
            )

            drawCircle(
                color = Color(0xFF10B981), // Emerald Green GPS Dot for extreme visibility
                radius = 5.dp.toPx(),
                center = currentPt
            )

            // Direct flight indicator (Arrow) pointing visual velocity bearing direction
            val bearingDegrees = if (recordedPath.size >= 2) {
                val pLast = recordedPath.last()
                val pPrev = recordedPath[recordedPath.size - 2]
                val dLat = pLast.latitude - pPrev.latitude
                val dLng = (pLast.longitude - pPrev.longitude) * cosLat
                val rad = Math.atan2(dLng, dLat)
                Math.toDegrees(rad).toFloat()
            } else {
                0f
            }

            rotate(degrees = bearingDegrees, pivot = currentPt) {
                val trianglePath = Path().apply {
                    moveTo(currentPt.x, currentPt.y - 14.dp.toPx()) // Tip
                    lineTo(currentPt.x - 7.dp.toPx(), currentPt.y + 10.dp.toPx()) // bottom left
                    lineTo(currentPt.x, currentPt.y + 6.dp.toPx()) // indent
                    lineTo(currentPt.x + 7.dp.toPx(), currentPt.y + 10.dp.toPx()) // bottom right
                    close()
                }
                drawPath(
                    path = trianglePath,
                    color = Color(0xFF1E3A8A)
                )
            }
        }

        // Coordinates Status overlay HUD
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDDFFFFFF)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 115.dp, end = 12.dp)
                .border(1.dp, Color(0x3364748B), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (gpsStatus != GpsStatus.STOPPED && gpsStatus != GpsStatus.PAUSED) Color(0xFF10B981) else Color(0xFF94A3B8),
                                androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = "完全オフライン・ローカルMAP",
                        color = Color(0xFF1A73E8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = String.format("LAT (緯度): %.6f°", currentLocation.latitude),
                    color = Color(0xFF0F172A),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("LNG (経度): %.6f°", currentLocation.longitude),
                    color = Color(0xFF0F172A),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                val statusLabel = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> "GPS測位良好 (常時)"
                    GpsStatus.RAW_ONLY -> "GPS実測値トレース"
                    GpsStatus.EXTRAPOLATING_TUNNEL -> "自律予測補完中"
                    GpsStatus.PAUSED -> "一時停止中"
                    GpsStatus.STOPPED -> "レコーダー待機"
                }
                Text(
                    text = "ステータス: $statusLabel",
                    color = Color(0xFF64748B),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Scale Indicator HUD in bottom-start corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 120.dp)
                .background(Color(0xBB475569), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            val scaleMeters = remember(zoomScale) {
                // If 1 degree latitude = 111000m.
                // 100 pixels = 100 / zoomScale degrees = (100 * 111000) / zoomScale meters.
                val metersIn100Px = (100f * 111000f) / zoomScale
                when {
                    metersIn100Px > 2000 -> "${String.format("%.1f", metersIn100Px / 1000f)} km"
                    else -> "${metersIn100Px.toInt()} m"
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = scaleMeters,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Draw a small physical line to serve as the scale unit representation
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(Color.White)
                )
            }
        }

        // Map auxiliary recentering trigger floating action button
        if (!autoCenter) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .clickable { onReCenter?.invoke() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "地図を非固定中です",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "中心に戻す",
                        color = Color(0xFF1A73E8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Simple compass rotation math helper
private fun companionOffset(base: Offset, dx: Float, dy: Float): Offset {
    return Offset(base.x + dx, base.y + dy)
}
