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
import androidx.compose.ui.geometry.Size
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
import com.example.util.NavigationEngine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class MapLandmark(
    val name: String,
    val lat: Double,
    val lng: Double,
    val description: String,
    val color: Color,
    val isShrine: Boolean = false,
    val isMountain: Boolean = false,
    val isOcean: Boolean = false
)

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
    
    // Smooth animated drag tracking offsets
    val animDragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Smoothly transition and snap drag offsets back to zero when centering is requested
    LaunchedEffect(autoCenter, currentLocation) {
        if (autoCenter) {
            animDragOffset.animateTo(Offset.Zero, animationSpec = tween(400))
        }
    }

    // Set up native paints for high-fidelity text annotations & markings
    val gridLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            alpha = 130
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    val landmarkLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val landmarkSubPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    }

    // Load available landmarks dynamically depending on road context
    val landmarks = remember(selectedRoad, currentLocation) {
        val centerLat = currentLocation.latitude
        val centerLng = currentLocation.longitude
        val isNearShonan = centerLat in 35.25..35.38 && centerLng in 139.35..139.60
        val isNearHakone = centerLat in 35.15..35.25 && centerLng in 138.95..139.10

        when {
            selectedRoad.id == "coast_kamakura" || (selectedRoad.id == "real_gps_free" && isNearShonan) -> listOf(
                MapLandmark("江の島 (Enoshima Island)", 35.300, 139.510, "観光名所 / 展望灯台", Color(0xFF0F766E), isOcean = true),
                MapLandmark("鎌倉大仏 (Great Buddha)", 35.3168, 139.5361, "高徳院 / 国宝大仏", Color(0xFF1E3A8A), isShrine = true),
                MapLandmark("長谷寺 (Hase-dera)", 35.3125, 139.5332, "長谷観音 / 鎌倉観音霊場", Color(0xFF334155), isShrine = true),
                MapLandmark("由比ヶ浜 (Yuigahama)", 35.3110, 139.5500, "海岸 / ビーチスポット", Color(0xFFEA580C)),
                MapLandmark("七里ヶ浜 (Shichirigahama)", 35.3055, 139.5180, "湘南絶景シーサイド", Color(0xFF0284C7), isOcean = true)
            )
            selectedRoad.id == "hakone_pass" || (selectedRoad.id == "real_gps_free" && isNearHakone) -> listOf(
                MapLandmark("芦ノ湖 (Lake Ashi)", 35.2010, 139.0150, "火山湖 / 観光海賊船", Color(0xFF0077B6), isOcean = true),
                MapLandmark("箱根神社 (Hakone Shrine)", 35.2040, 139.0260, "朱色の平和の鳥居", Color(0xFFDC2626), isShrine = true),
                MapLandmark("箱根関所 (Checkpoint)", 35.1910, 139.0260, "江戸時代 歴史関所跡", Color(0xFF1E293B)),
                MapLandmark("元箱根港 (Motohakone)", 35.1990, 139.0320, "海賊船・遊覧船乗船口", Color(0xFF0369A1)),
                MapLandmark("神山 (Mt. Kamiyama)", 35.2180, 139.0430, "標高1,438m / 箱根最高峰", Color(0xFF14532D), isMountain = true)
            )
            selectedRoad.id == "expressway_tunnels" -> listOf(
                MapLandmark("相模川 (Sagami River)", 35.3550, 139.3920, "一級河川 / 湘南大橋", Color(0xFF0369A1), isOcean = true),
                MapLandmark("辻堂海浜公園 (Tsujido Park)", 35.3210, 139.4510, "広大な芝生とプール", Color(0xFF16A34A)),
                MapLandmark("藤沢駅周辺 (Fujisawa Town)", 35.3380, 139.4870, "湘南エリア交通の端", Color(0xFF334155)),
                MapLandmark("茅ヶ崎JCT", 35.3520, 139.4100, "湘南バイパスジャンクション", Color(0xFF475569))
            )
            else -> listOf(
                MapLandmark("📍 現在地ロック (Local GPS Lock)", centerLat, centerLng, "測位衛星信号捕捉アンカー", Color(0xFF1D4ED8)),
                MapLandmark("🌳 まちのセントラルパーク", centerLat + 0.0045, centerLng - 0.0040, "市民憩いの緑地・地域避難場所", Color(0xFF16A34A), isMountain = false),
                MapLandmark("⛩️ 平和記念神社", centerLat - 0.0035, centerLng + 0.0050, "地域守護・歴史文化スポット", Color(0xFFDC2626), isShrine = true),
                MapLandmark("☕ リバーサイドテラスモール", centerLat, centerLng + 0.0030, "お買い物とグルメの複合スポット", Color(0xFFEA580C))
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEDE9E2)) // Warm Sand / Canvas Map Ground Background
            .pointerInput(autoCenter, currentLocation) {
                // Handle smooth manual dragging displacement panning
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        animDragOffset.snapTo(animDragOffset.value + dragAmount)
                    }
                    onMapDragged?.invoke()
                }
            }
            .pointerInput(Unit) {
                // Double-click back to Center shortcut key.
                detectTapGestures(
                    onDoubleTap = {
                        onReCenter?.invoke()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerLat = currentLocation.latitude
            val centerLng = currentLocation.longitude
            val zoom = zoomScale

            // Longitudinal correction angle calculation
            val cosLat = cos(Math.toRadians(centerLat))

            // Mathematical projection converter (WGS84 GPS coordinate -> 2D screen coordinate pixels)
            fun project(lat: Double, lng: Double): Offset {
                val dx = (lng - centerLng) * cosLat * zoom
                val dy = -(lat - centerLat) * zoom
                return Offset(
                    x = (width / 2f) + dx.toFloat() + animDragOffset.value.x,
                    y = (height / 2f) + dy.toFloat() + animDragOffset.value.y
                )
            }

            // Estimate current visible coordinate ranges
            val latHalfSpan = (height / 2f) / zoom
            val lngHalfSpan = (width / 2f) / (zoom * cosLat)
            
            val minLat = centerLat - latHalfSpan - animDragOffset.value.y / zoom
            val maxLat = centerLat + latHalfSpan - animDragOffset.value.y / zoom
            val minLng = centerLng - lngHalfSpan - animDragOffset.value.x / (zoom * cosLat)
            val maxLng = centerLng + lngHalfSpan - animDragOffset.value.x / (zoom * cosLat)

            // Dynamic view center
            val viewCenterLat = centerLat - animDragOffset.value.y / zoom
            val viewCenterLng = centerLng + animDragOffset.value.x / (zoom * cosLat)

            // Check if coordinates lie near Shonan or Hakone regions so we draw local offline geometry gracefully
            val isNearShonan = centerLat in 35.25..35.38 && centerLng in 139.35..139.60
            val isNearHakone = centerLat in 35.15..35.25 && centerLng in 138.95..139.10

            val drawShonanFeatures = selectedRoad.id == "coast_kamakura" ||
                    selectedRoad.id == "expressway_tunnels" ||
                    (selectedRoad.id == "real_gps_free" && isNearShonan)

            val drawHakoneFeatures = selectedRoad.id == "hakone_pass" ||
                    (selectedRoad.id == "real_gps_free" && isNearHakone)

            // ==========================================
            // LAYER 1: WATER BODIES & LAND BACKGROUNDS
            // ==========================================
            if (drawShonanFeatures) {
                // Pacific Ocean (Sagami Bay - 相模湾) fills everything south of latitude 35.305
                val pCoastL = project(35.305, minLng)
                val pCoastR = project(35.305, maxLng)
                val oceanPath = Path().apply {
                    moveTo(pCoastL.x, pCoastL.y)
                    lineTo(pCoastR.x, pCoastR.y)
                    lineTo(pCoastR.x, height + 100f)
                    lineTo(pCoastL.x, height + 100f)
                    close()
                }
                drawPath(oceanPath, Color(0xFFA5DCF8)) // Beautiful ocean blue

                // Coastal Sand Beach line along the Pacific Shore
                val beachLinePath = Path().apply {
                    moveTo(pCoastL.x, pCoastL.y)
                    lineTo(pCoastR.x, pCoastR.y)
                }
                drawPath(
                    path = beachLinePath,
                    color = Color(0xFFE5D4BA), // Warm golden sand
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Draw Tsujido Seaside Park (辻堂海浜公園) green meadow zone
                val parkCenter = project(35.3210, 139.4510)
                drawCircle(
                    color = Color(0xFFA3D9A5).copy(alpha = 0.8f),
                    radius = 50.dp.toPx(),
                    center = parkCenter
                )
            } else if (drawHakoneFeatures) {
                // Hakone Mountain forested green surroundings (Ashigarashimo District)
                drawRect(
                    color = Color(0xFFE2EAD8), // Forest/mountain green land base
                    topLeft = Offset.Zero,
                    size = size
                )

                // Serene Lake Ashi (芦ノ湖) large water body
                val lakeCenter = project(35.2010, 139.0150)
                val lakeW = 60.dp.toPx()
                val lakeH = 125.dp.toPx()
                drawOval(
                    color = Color(0xFF0284C7).copy(alpha = 0.65f), // Rich blue water
                    topLeft = Offset(lakeCenter.x - lakeW, lakeCenter.y - lakeH),
                    size = Size(lakeW * 2f, lakeH * 2f)
                )

                // Lake shoreline outline
                drawOval(
                    color = Color(0xFFE5D4BA),
                    topLeft = Offset(lakeCenter.x - lakeW, lakeCenter.y - lakeH),
                    size = Size(lakeW * 2f, lakeH * 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
                // Procedural beautiful River passing near current coordinate center (e.g. Kyoto Kamogawa or local river) to make map rich!
                val proceduralRiverPath = Path().apply {
                    val pStart = project(centerLat + 0.012, centerLng - 0.008)
                    val pMid = project(centerLat, centerLng + 0.002)
                    val pEnd = project(centerLat - 0.012, centerLng + 0.010)
                    moveTo(pStart.x, pStart.y)
                    quadraticTo(pMid.x, pMid.y, pEnd.x, pEnd.y)
                }
                drawPath(
                    path = proceduralRiverPath,
                    color = Color(0xFFA5DCF8).copy(alpha = 0.80f),
                    style = Stroke(
                        width = 16.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw winding Sagami River (相模川)
            if (selectedRoad.id == "expressway_tunnels" || (selectedRoad.id == "real_gps_free" && isNearShonan)) {
                val riverPath = Path().apply {
                    val pStart = project(35.3900, 139.3900)
                    val pMid = project(35.3550, 139.3930)
                    val pEnd = project(35.3050, 139.3870)
                    moveTo(pStart.x, pStart.y)
                    quadraticTo(pMid.x, pMid.y, pEnd.x, pEnd.y)
                }
                drawPath(
                    path = riverPath,
                    color = Color(0xFFA5DCF8),
                    style = Stroke(
                        width = 36.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // ==========================================
            // LAYER 2: PROCEDURAL URBAN GRID STREETS
            // ==========================================
            // Renders a realistic background city street mesh over land parts
            val streetStepLat = 0.003
            val streetStepLng = 0.0036
            
            val startStLat = Math.floor(minLat / streetStepLat) * streetStepLat
            val endStLat = Math.ceil(maxLat / streetStepLat) * streetStepLat
            val startStLng = Math.floor(minLng / streetStepLng) * streetStepLng
            val endStLng = Math.ceil(maxLng / streetStepLng) * streetStepLng

            // 2.1 Horizontal Local Streets
            var curStLat = startStLat
            while (curStLat <= endStLat) {
                val pL = project(curStLat, minLng)
                val pR = project(curStLat, maxLng)
                // Filter: skip drawing horizontal streets deep inside the Pacific Ocean (below coast)
                if (!drawShonanFeatures || curStLat > 35.3060) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.65f),
                        start = pL,
                        end = pR,
                        strokeWidth = 2.dp.toPx()
                    )
                }
                curStLat += streetStepLat
            }

            // 2.2 Vertical Local Streets
            var curStLng = startStLng
            while (curStLng <= endStLng) {
                val pT = project(maxLat, curStLng)
                val pB = project(minLat, curStLng)
                // Skip if entirely in Southern Ocean
                if (!drawShonanFeatures || minLat > 35.3060 || maxLat > 35.3060) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.65f),
                        start = pT,
                        end = pB,
                        strokeWidth = 2.dp.toPx()
                    )
                }
                curStLng += streetStepLng
            }

            // ==========================================
            // LAYER 3: PRESET RAILWAYS (Enoden & Hakone Line)
            // ==========================================
            // Beautiful classic railroad markings for Shonan/Kamakura areas
            if (selectedRoad.id == "coast_kamakura" || (selectedRoad.id == "real_gps_free" && isNearShonan)) {
                val railPath = Path().apply {
                    val pEno = project(35.3090, 139.4880) // Enoshima Station
                    val pGok = project(35.3070, 139.5280) // Gokurakuji curve
                    val pHase = project(35.3115, 139.5350) // Hase Station
                    val pKam = project(35.3190, 139.5504) // Kamakura terminus
                    moveTo(pEno.x, pEno.y)
                    lineTo(pGok.x, pGok.y)
                    lineTo(pHase.x, pHase.y)
                    lineTo(pKam.x, pKam.y)
                }
                // Solid dark base
                drawPath(
                    path = railPath,
                    color = Color(0xFF475569),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // Dashed white ticks
                drawPath(
                    path = railPath,
                    color = Color.White,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            }

            // ==========================================
            // LAYER 4: LATITUDE / LONGITUDE RADAR GRID
            // ==========================================
            val gridStep = when {
                zoom > 350000 -> 0.001
                zoom > 150000 -> 0.002
                else -> 0.005
            }

            val startGridLat = Math.floor(minLat / gridStep) * gridStep
            val endGridLat = Math.ceil(maxLat / gridStep) * gridStep
            val startGridLng = Math.floor(minLng / gridStep) * gridStep
            val endGridLng = Math.ceil(maxLng / gridStep) * gridStep

            // Longitude dashed grid lines
            var curGridLng = startGridLng
            while (curGridLng <= endGridLng) {
                val pT = project(endGridLat, curGridLng)
                val pB = project(startGridLat, curGridLng)
                drawLine(
                    color = Color(0x221E293B),
                    start = pT,
                    end = pB,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        String.format("%.3f°E", curGridLng),
                        pT.x + 8f,
                        height - 35f,
                        gridLabelPaint
                    )
                }
                curGridLng += gridStep
            }

            // Latitude dashed grid lines
            var curGridLat = startGridLat
            while (curGridLat <= endGridLat) {
                val pL = project(curGridLat, minLng)
                val pR = project(curGridLat, maxLng)
                drawLine(
                    color = Color(0x221E293B),
                    start = pL,
                    end = pR,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        String.format("%.3f°N", curGridLat),
                        35f,
                        pL.y - 8f,
                        gridLabelPaint
                    )
                }
                curGridLat += gridStep
            }

            // ==========================================
            // LAYER 5: PRESET ROAD OVERLAY (The Driving Course)
            // ==========================================
            // Loop and draw all predefined roads as light secondary roads
            NavigationEngine.PRESET_ROADS.forEach { road ->
                if (road.nodes.isNotEmpty()) {
                    val isSelected = road.id == selectedRoad.id
                    val rPath = Path().apply {
                        val first = project(road.nodes[0].latitude, road.nodes[0].longitude)
                        moveTo(first.x, first.y)
                        for (idx in 1 until road.nodes.size) {
                            val next = project(road.nodes[idx].latitude, road.nodes[idx].longitude)
                            lineTo(next.x, next.y)
                        }
                    }

                    // 1. Casing / Shadow outline
                    drawPath(
                        path = rPath,
                        color = if (isSelected) Color(0xFF64748B) else Color(0x3394A3B8),
                        style = Stroke(
                            width = (if (isSelected) 15.dp else 10.dp).toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 2. Core Asphalt Lane Surface
                    drawPath(
                        path = rPath,
                        color = if (isSelected) Color(0xFF334155) else Color(0x3AAFA89F),
                        style = Stroke(
                            width = (if (isSelected) 10.dp else 6.dp).toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 3. Middle yellow/white separating stripe line
                    if (isSelected) {
                        drawPath(
                            path = rPath,
                            color = Color(0xFFFBBF24),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
                            )
                        )

                        // 4. Render Tunnel tubes (Overlay dark mountain tubes with orange caution lines)
                        road.tunnelRanges.forEach { tRange ->
                            val rStart = tRange.first.coerceIn(0, road.nodes.size - 1)
                            val rEnd = tRange.last.coerceIn(0, road.nodes.size - 1)
                            if (rStart < rEnd) {
                                val tPath = Path().apply {
                                    val start = project(road.nodes[rStart].latitude, road.nodes[rStart].longitude)
                                    moveTo(start.x, start.y)
                                    for (i in rStart + 1..rEnd) {
                                        val next = project(road.nodes[i].latitude, road.nodes[i].longitude)
                                        lineTo(next.x, next.y)
                                    }
                                }

                                // Dark thick outer tunnel concrete walls
                                drawPath(
                                    path = tPath,
                                    color = Color(0xFF1E293B),
                                    style = Stroke(
                                        width = 20.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )

                                // Pitch black tunnel interior lane
                                drawPath(
                                    path = tPath,
                                    color = Color(0xFF020617),
                                    style = Stroke(
                                        width = 11.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )

                                // Glowing neon warning indicators along the tunnel wall
                                drawPath(
                                    path = tPath,
                                    color = Color(0xFFF97316),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 12f), 0f)
                                    )
                                )

                                // Render "TUNNEL" label at the center of each dome
                                val midIdx = (rStart + rEnd) / 2
                                val midPt = project(road.nodes[midIdx].latitude, road.nodes[midIdx].longitude)
                                drawIntoCanvas { canvas ->
                                    val tLabelPaint = Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = 21f
                                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                        textAlign = Paint.Align.CENTER
                                    }
                                    canvas.nativeCanvas.drawText(
                                        "トンネル (TUNNEL)",
                                        midPt.x,
                                        midPt.y - 18.dp.toPx(),
                                        tLabelPaint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // LAYER 6: ROUTE LABELS (START & GOAL PINPOINTS)
            // ==========================================
            if (selectedRoad.nodes.isNotEmpty()) {
                val nodesPoint = selectedRoad.nodes
                val endpoints = listOf(
                    0 to "起点 (START)",
                    (nodesPoint.size - 1) to "終点 (GOAL)"
                )
                endpoints.forEach { (index, text) ->
                    val pt = project(nodesPoint[index].latitude, nodesPoint[index].longitude)
                    drawCircle(
                        color = if (index == 0) Color(0xFF10B981) else Color(0xFFEF4444),
                        radius = 8.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = pt
                    )
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            text,
                            pt.x,
                            pt.y - 15.dp.toPx(),
                            landmarkLabelPaint
                        )
                    }
                }
            }

            // ==========================================
            // LAYER 7: RECORDED GPS ACCUMULATED TRAIL (Blue Ribbon)
            // ==========================================
            if (recordedPath.isNotEmpty()) {
                val trailPath = Path().apply {
                    val origin = project(recordedPath[0].latitude, recordedPath[0].longitude)
                    moveTo(origin.x, origin.y)
                    for (i in 1 until recordedPath.size) {
                        val next = project(recordedPath[i].latitude, recordedPath[i].longitude)
                        lineTo(next.x, next.y)
                    }
                }

                // Wide soft tracking glow
                drawPath(
                    path = trailPath,
                    color = Color(0x3F2563EB),
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Strong visible royal blue alignment core ribbon
                drawPath(
                    path = trailPath,
                    color = Color(0xFF1D4ED8),
                    style = Stroke(
                        width = 5.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Tiny coordinate markers
                recordedPath.forEach { rPoint ->
                    val dotPt = project(rPoint.latitude, rPoint.longitude)
                    drawCircle(
                        color = if (rPoint.isInterpolated) Color(0xFFF97316) else Color(0xFF38BDF8),
                        radius = 2.5.dp.toPx(),
                        center = dotPt
                    )
                }
            }

            // ==========================================
            // LAYER 8: HISTORICAL DATABASE OVERLAY TRAIL (Green Coral)
            // ==========================================
            if (!overlayRoutePoints.isNullOrEmpty() && overlayRoutePoints.size > 1) {
                val oPath = Path().apply {
                    val sPoint = project(overlayRoutePoints[0].latitude, overlayRoutePoints[0].longitude)
                    moveTo(sPoint.x, sPoint.y)
                    for (i in 1 until overlayRoutePoints.size) {
                        val next = project(overlayRoutePoints[i].latitude, overlayRoutePoints[i].longitude)
                        lineTo(next.x, next.y)
                    }
                }

                // Semi-transparent base
                drawPath(
                    path = oPath,
                    color = Color(0x3F10B981),
                    style = Stroke(
                        width = 10.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Emerald green lining
                drawPath(
                    path = oPath,
                    color = Color(0xFF10B981),
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // ==========================================
            // LAYER 9: LANDMARKS AND POI FLAGMARK LABELS
            // ==========================================
            landmarks.forEach { lm ->
                val lmPt = project(lm.lat, lm.lng)
                
                // Only draw if within bounds for optimized processing
                if (lmPt.x in -100f..(width + 100f) && lmPt.y in -100f..(height + 100f)) {
                    // Draw localized icons based on features
                    when {
                        lm.isShrine -> {
                            // Top Red bar
                            drawLine(
                                color = Color(0xFFE11D48),
                                start = Offset(lmPt.x - 10.dp.toPx(), lmPt.y - 8.dp.toPx()),
                                end = Offset(lmPt.x + 10.dp.toPx(), lmPt.y - 8.dp.toPx()),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Columns
                            drawLine(
                                color = Color(0xFFE11D48),
                                start = Offset(lmPt.x - 5.dp.toPx(), lmPt.y - 8.dp.toPx()),
                                end = Offset(lmPt.x - 5.dp.toPx(), lmPt.y + 6.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFFE11D48),
                                start = Offset(lmPt.x + 5.dp.toPx(), lmPt.y - 8.dp.toPx()),
                                end = Offset(lmPt.x + 5.dp.toPx(), lmPt.y + 6.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                        lm.isMountain -> {
                            // Peak triangle
                            val triPath = Path().apply {
                                moveTo(lmPt.x, lmPt.y - 12.dp.toPx())
                                lineTo(lmPt.x - 10.dp.toPx(), lmPt.y + 6.dp.toPx())
                                lineTo(lmPt.x + 10.dp.toPx(), lmPt.y + 6.dp.toPx())
                                close()
                            }
                            drawPath(triPath, Color(0xFF166534))
                            // Snow cap
                            val snowPath = Path().apply {
                                moveTo(lmPt.x, lmPt.y - 12.dp.toPx())
                                lineTo(lmPt.x - 3.dp.toPx(), lmPt.y - 5.dp.toPx())
                                lineTo(lmPt.x + 3.dp.toPx(), lmPt.y - 5.dp.toPx())
                                close()
                            }
                            drawPath(snowPath, Color.White)
                        }
                        lm.isOcean -> {
                            // Concentric blue POI bubbles
                            drawCircle(
                                color = Color(0x3F0284C7),
                                radius = 10.dp.toPx(),
                                center = lmPt
                            )
                            drawCircle(
                                color = Color(0xFF0284C7),
                                radius = 4.dp.toPx(),
                                center = lmPt
                            )
                        }
                        else -> {
                            // Generic map flag marker pin
                            val pinPath = Path().apply {
                                moveTo(lmPt.x, lmPt.y)
                                cubicTo(lmPt.x - 5.dp.toPx(), lmPt.y - 7.dp.toPx(),
                                        lmPt.x - 5.dp.toPx(), lmPt.y - 14.dp.toPx(),
                                        lmPt.x, lmPt.y - 14.dp.toPx())
                                cubicTo(lmPt.x + 5.dp.toPx(), lmPt.y - 14.dp.toPx(),
                                        lmPt.x + 5.dp.toPx(), lmPt.y - 7.dp.toPx(),
                                        lmPt.x, lmPt.y)
                                close()
                            }
                            drawPath(pinPath, lm.color)
                        }
                    }

                    // Render gorgeous high contrast vector map text labels
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            lm.name,
                            lmPt.x,
                            lmPt.y + 19.dp.toPx(),
                            landmarkLabelPaint
                        )
                        canvas.nativeCanvas.drawText(
                            lm.description,
                            lmPt.x,
                            lmPt.y + 29.dp.toPx(),
                            landmarkSubPaint
                        )
                    }
                }
            }

            // ==========================================
            // LAYER 10: COMPASS ROSE HUD (Bottom-Left Side)
            // ==========================================
            val compassCenter = Offset(80.dp.toPx(), height - 125.dp.toPx())
            val compassRad = 32.dp.toPx()
            
            // Outer Dial Circle
            drawCircle(
                color = Color(0xEBFFFFFF),
                radius = compassRad,
                center = compassCenter
            )
            drawCircle(
                color = Color(0xFF475569),
                radius = compassRad,
                center = compassCenter,
                style = Stroke(width = 1.6.dp.toPx())
            )
            
            // Cardinal directions
            drawIntoCanvas { canvas ->
                val p = Paint().apply {
                    color = android.graphics.Color.parseColor("#475569")
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText("N", compassCenter.x, compassCenter.y - compassRad + 21f, p)
                canvas.nativeCanvas.drawText("S", compassCenter.x, compassCenter.y + compassRad - 8f, p)
            }

            // Compass Magnetic Needle pointing True North
            val needleLen = compassRad - 10f
            drawLine(
                color = Color(0xFFEF4444), // North Pointer (Red)
                start = compassCenter,
                end = Offset(compassCenter.x, compassCenter.y - needleLen),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF94A3B8), // South Pointer (Slate Grey)
                start = compassCenter,
                end = Offset(compassCenter.x, compassCenter.y + needleLen),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Center pin rivet
            drawCircle(
                color = Color(0xFF1E293B),
                radius = 4.dp.toPx(),
                center = compassCenter
            )
        }

        // ==========================================
        // LAYER 11: CURRENT GPS BEACON PULSATING RIPPLE
        // ==========================================
        val infiniteTransition = rememberInfiniteTransition(label = "gps_beacon")
        val animatedRadius by infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 42f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart
            ),
            label = "radius"
        )
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
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
            val cosLat = cos(Math.toRadians(centerLat))

            fun project(lat: Double, lng: Double): Offset {
                val dx = (lng - centerLng) * cosLat * zoom
                val dy = -(lat - centerLat) * zoom
                return Offset(
                    x = (width / 2f) + dx.toFloat() + animDragOffset.value.x,
                    y = (height / 2f) + dy.toFloat() + animDragOffset.value.y
                )
            }

            val currentBeaconPt = project(currentLocation.latitude, currentLocation.longitude)

            // Dynamic GPS beacon circles
            drawCircle(
                color = Color(0xFF10B981).copy(alpha = animatedAlpha),
                radius = animatedRadius.dp.toPx(),
                center = currentBeaconPt
            )

            // Inner Core Bead
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = currentBeaconPt
            )

            drawCircle(
                color = Color(0xFF10B981), // Pure high-visibility Emerald Green for current trace
                radius = 5.dp.toPx(),
                center = currentBeaconPt
            )

            // Current travel Direction Chevron Pointer
            val currentBearingDegees = if (recordedPath.size >= 2) {
                val pLast = recordedPath.last()
                val pPrev = recordedPath[recordedPath.size - 2]
                val dLat = pLast.latitude - pPrev.latitude
                val dLng = (pLast.longitude - pPrev.longitude) * cosLat
                val rad = Math.atan2(dLng, dLat)
                Math.toDegrees(rad).toFloat()
            } else {
                0f
            }

            rotate(degrees = currentBearingDegees, pivot = currentBeaconPt) {
                val triPath = Path().apply {
                    moveTo(currentBeaconPt.x, currentBeaconPt.y - 13.dp.toPx()) // Pointer Tip
                    lineTo(currentBeaconPt.x - 7.dp.toPx(), currentBeaconPt.y + 9.dp.toPx())
                    lineTo(currentBeaconPt.x, currentBeaconPt.y + 5.dp.toPx()) // Indent
                    lineTo(currentBeaconPt.x + 7.dp.toPx(), currentBeaconPt.y + 9.dp.toPx())
                    close()
                }
                drawPath(
                    path = triPath,
                    color = Color(0xFF1E3A8A) // Royal Blue Direction marker
                )
            }
        }

        // ==========================================
        // COGNITIVE METRICS OVERLAYS & CONTROLS HUD
        // ==========================================

        // Coordinates Panel Card (Top-right)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEBFFFFFF)),
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
                        text = "超高速ベクトル地図モデル",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = String.format("LAT (緯度): %.6f°N", currentLocation.latitude),
                    color = Color(0xFF0F172A),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("LNG (経度): %.6f°E", currentLocation.longitude),
                    color = Color(0xFF0F172A),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                val statusLabel = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> "GPS良好 (道路吸着中)"
                    GpsStatus.RAW_ONLY -> "GPS良好 (生データ追従)"
                    GpsStatus.EXTRAPOLATING_TUNNEL -> "トンネル自律予測中🛰️"
                    GpsStatus.PAUSED -> "一時停止"
                    GpsStatus.STOPPED -> "レコーダー待機"
                }
                Text(
                    text = "位置状態: $statusLabel",
                    color = Color(0xFF475569),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Adaptive Map Scale HUD indicator (Bottom-Start corner)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 125.dp)
                .background(Color(0xBB334155), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            val scaleLabelStr = remember(zoomScale) {
                // 1 degree latitude = 111,120 meters.
                // 100 pixels = 100 / zoomScale degrees = (100 * 111120) / zoomScale meters.
                val metersPer100Px = (100f * 111120f) / zoomScale
                when {
                    metersPer100Px > 2000 -> "${String.format("%.1f", metersPer100Px / 1000f)} km"
                    else -> "${metersPer100Px.toInt()} m"
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = scaleLabelStr,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(2.dp)
                        .background(Color.White)
                )
            }
        }

        // Float Re-centering Button action
        if (!autoCenter) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                    .clickable { onReCenter?.invoke() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "地図非固定（スクロール中）",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "中心に戻す",
                        color = Color(0xFF1D4ED8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
