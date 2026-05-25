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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import android.graphics.Typeface
import android.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.GeoPoint
import com.example.util.MockRoad
import com.example.util.NavigationEngine
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.sinh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class MajorCity(
    val name: String,
    val lat: Double,
    val lng: Double,
    val description: String,
    val isCapital: Boolean = false
)

private val HONSHU_POLY = listOf(
    GeoPoint(41.54, 140.91), GeoPoint(40.52, 141.52), GeoPoint(39.64, 141.98), GeoPoint(38.26, 141.00),
    GeoPoint(36.94, 140.79), GeoPoint(35.73, 140.85), GeoPoint(35.15, 140.32), GeoPoint(34.90, 139.88),
    GeoPoint(35.31, 139.82), GeoPoint(35.65, 139.75), GeoPoint(35.45, 139.65), GeoPoint(35.13, 139.62),
    GeoPoint(35.305, 139.51), GeoPoint(35.25, 139.15), GeoPoint(35.00, 139.08), GeoPoint(34.60, 138.85),
    GeoPoint(34.80, 138.75), GeoPoint(35.10, 138.86), GeoPoint(35.01, 138.52), GeoPoint(34.60, 138.22),
    GeoPoint(34.70, 137.60), GeoPoint(34.58, 137.02), GeoPoint(35.05, 136.85), GeoPoint(34.27, 136.87),
    GeoPoint(33.43, 135.75), GeoPoint(34.23, 135.15), GeoPoint(34.69, 135.45), GeoPoint(34.69, 135.20),
    GeoPoint(34.80, 134.45), GeoPoint(34.60, 133.95), GeoPoint(34.35, 132.45), GeoPoint(33.95, 130.93),
    GeoPoint(34.67, 131.85), GeoPoint(35.45, 133.05), GeoPoint(35.53, 134.22), GeoPoint(35.48, 135.35),
    GeoPoint(35.75, 136.10), GeoPoint(36.60, 136.63), GeoPoint(37.52, 137.35), GeoPoint(36.80, 137.20),
    GeoPoint(37.18, 138.25), GeoPoint(37.95, 139.05), GeoPoint(38.92, 139.85), GeoPoint(39.72, 140.10),
    GeoPoint(40.82, 140.75)
)

private val HOKKAIDO_POLY = listOf(
    GeoPoint(45.52, 141.93), GeoPoint(44.35, 143.35), GeoPoint(44.02, 144.30), GeoPoint(44.36, 145.31),
    GeoPoint(43.38, 145.81), GeoPoint(42.95, 144.38), GeoPoint(41.93, 143.25), GeoPoint(42.35, 140.97),
    GeoPoint(41.77, 140.73), GeoPoint(41.35, 140.20), GeoPoint(42.06, 140.05), GeoPoint(42.79, 140.23),
    GeoPoint(43.20, 141.01), GeoPoint(43.93, 141.63), GeoPoint(45.42, 141.67)
)

private val SHIKOKU_POLY = listOf(
    GeoPoint(34.34, 134.05), GeoPoint(34.07, 134.57), GeoPoint(33.25, 134.18), GeoPoint(32.72, 133.02),
    GeoPoint(33.22, 132.55), GeoPoint(33.84, 132.76), GeoPoint(34.07, 133.00)
)

private val KYUSHU_POLY = listOf(
    GeoPoint(33.95, 130.95), GeoPoint(33.23, 131.60), GeoPoint(32.58, 131.67), GeoPoint(31.90, 131.43),
    GeoPoint(31.00, 130.66), GeoPoint(31.58, 130.55), GeoPoint(32.80, 130.70), GeoPoint(32.75, 129.87),
    GeoPoint(33.45, 129.97), GeoPoint(33.60, 130.40)
)

private val JAPAN_MAJOR_CITIES = listOf(
    MajorCity("札幌 (Sapporo)", 43.06, 141.35, "北海道"),
    MajorCity("青森 (Aomori)", 40.82, 140.75, "東北部"),
    MajorCity("仙台 (Sendai)", 38.26, 140.88, "東北 / 杜の都"),
    MajorCity("新潟 (Niigata)", 37.91, 139.04, "日本海側"),
    MajorCity("東京 (Tokyo)", 35.68, 139.76, "日本首都", isCapital = true),
    MajorCity("横浜 (Yokohama)", 35.44, 139.64, "神奈川県"),
    MajorCity("静岡 (Shizuoka)", 34.97, 138.38, "東海地方"),
    MajorCity("名古屋 (Nagoya)", 35.18, 136.90, "中京圏"),
    MajorCity("京都 (Kyoto)", 35.01, 135.76, "古都"),
    MajorCity("大阪 (Osaka)", 34.69, 135.50, "関西中心"),
    MajorCity("広島 (Hiroshima)", 34.38, 132.45, "平和都市"),
    MajorCity("高松 (Takamatsu)", 34.34, 134.04, "四国地方"),
    MajorCity("福岡 (Fukuoka)", 33.59, 130.40, "九州最大市"),
    MajorCity("鹿児島 (Kagoshima)", 31.59, 130.55, "薩摩"),
    MajorCity("那覇 (Naha)", 26.21, 127.68, "沖縄県")
)

data class NationalHighway(
    val name: String,
    val enName: String,
    val points: List<GeoPoint>,
    val color: Color
)

private val JAPAN_EXPRESSWAYS = listOf(
    NationalHighway(
        "東北道 (Tohoku Expwy)", "Tohoku Expwy",
        listOf(
            GeoPoint(35.68, 139.76),
            GeoPoint(36.36, 139.80),
            GeoPoint(37.50, 140.10),
            GeoPoint(38.26, 140.88),
            GeoPoint(39.70, 141.15),
            GeoPoint(40.82, 140.75)
        ),
        Color(0xFF15803D) // Standard green sign color for Japanese Expressways
    ),
    NationalHighway(
        "東名高速 (Tomei Expwy)", "Tomei Expwy",
        listOf(
            GeoPoint(35.68, 139.76),
            GeoPoint(35.40, 139.40),
            GeoPoint(34.97, 138.38),
            GeoPoint(34.75, 137.75),
            GeoPoint(35.18, 136.90)
        ),
        Color(0xFF15803D)
    ),
    NationalHighway(
        "名神高速 (Meishin Expwy)", "Meishin Expwy",
        listOf(
            GeoPoint(35.18, 136.90),
            GeoPoint(35.25, 136.25),
            GeoPoint(35.01, 135.76),
            GeoPoint(34.69, 135.50)
        ),
        Color(0xFF15803D)
    ),
    NationalHighway(
        "中央道 (Chuo Expwy)", "Chuo Expwy",
        listOf(
            GeoPoint(35.68, 139.76),
            GeoPoint(35.65, 138.55),
            GeoPoint(36.00, 138.10),
            GeoPoint(35.35, 137.30),
            GeoPoint(35.18, 136.90)
        ),
        Color(0xFF16A34A)
    ),
    NationalHighway(
        "北陸道 (Hokuriku Expwy)", "Hokuriku Expwy",
        listOf(
            GeoPoint(35.25, 136.25),
            GeoPoint(36.06, 136.20),
            GeoPoint(36.60, 136.63),
            GeoPoint(36.80, 137.20),
            GeoPoint(37.18, 138.25),
            GeoPoint(37.91, 139.04)
        ),
        Color(0xFF15803D)
    ),
    NationalHighway(
        "山陽道 (Sanyo Expwy)", "Sanyo Expwy",
        listOf(
            GeoPoint(34.69, 135.50),
            GeoPoint(34.70, 135.20),
            GeoPoint(34.66, 133.92),
            GeoPoint(34.38, 132.45),
            GeoPoint(34.15, 131.47),
            GeoPoint(33.95, 130.95),
            GeoPoint(33.59, 130.40)
        ),
        Color(0xFF15803D)
    ),
    NationalHighway(
        "九州道 (Kyushu Expwy)", "Kyushu Expwy",
        listOf(
            GeoPoint(33.59, 130.40),
            GeoPoint(32.78, 130.73),
            GeoPoint(31.59, 130.55)
        ),
        Color(0xFF15803D)
    ),
    NationalHighway(
        "道央道 (Hokkaido Expwy)", "Hokkaido Expwy",
        listOf(
            GeoPoint(41.77, 140.73),
            GeoPoint(42.32, 140.97),
            GeoPoint(43.06, 141.35),
            GeoPoint(43.77, 142.36)
        ),
        Color(0xFF15803D)
    )
)

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
    routeColor: Color = Color(0xFF1D4ED8),
    onMapDragged: (() -> Unit)? = null,
    onReCenter: (() -> Unit)? = null,
    onStartRecording: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Smooth animated drag tracking offsets
    val animDragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    
    // Add state for map orientation
    var isHeadingUp by remember { mutableStateOf(false) }
    var manualRotation by remember { mutableFloatStateOf(0f) }

    // Smoothly transition and snap drag offsets back to zero when centering is requested
    LaunchedEffect(autoCenter) {
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
    val landmarks = remember(selectedRoad) {
        val pivot = selectedRoad.nodes.firstOrNull() ?: currentLocation
        val centerLat = pivot.latitude
        val centerLng = pivot.longitude
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
                MapLandmark("🌳 まちのセントラルパーク", centerLat + 0.0045, centerLng - 0.0040, "市民憩いの緑地・地域避難場所", Color(0xFF16A34A), isMountain = false),
                MapLandmark("⛩️ 平和記念神社", centerLat - 0.0035, centerLng + 0.0050, "地域守護・歴史文化スポット", Color(0xFFDC2626), isShrine = true),
                MapLandmark("🏔️ みはらしの丘 (Scenic Hill)", centerLat + 0.0020, centerLng + 0.0035, "美しい景色が広がる見事な丘", Color(0xFF15803D), isMountain = true)
            )
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEDE9E2)) // Light background
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, rotation ->
                    if (pan != Offset.Zero) {
                        coroutineScope.launch {
                            animDragOffset.snapTo(animDragOffset.value + pan)
                        }
                        onMapDragged?.invoke()
                    }
                    if (abs(rotation) > 0.1f) {
                        if (isHeadingUp) {
                            // If it was heading-up, initialize manual rotation with current heading
                            val p1 = recordedPath.lastOrNull()
                            val p2 = if (recordedPath.size >= 2) recordedPath[recordedPath.size - 2] else null
                            if (p1 != null && p2 != null) {
                                val dLat = p1.latitude - p2.latitude
                                val dLng = (p1.longitude - p2.longitude) * cos(Math.toRadians(p1.latitude))
                                val rad = Math.atan2(dLng, dLat)
                                manualRotation = Math.toDegrees(rad).toFloat()
                            }
                            isHeadingUp = false
                        }
                        manualRotation += rotation
                        onMapDragged?.invoke()
                    }
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
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val centerLat = currentLocation.latitude
        val centerLng = currentLocation.longitude
        val zoom = zoomScale

        // Longitudinal correction angle calculation
        val cosLat = cos(Math.toRadians(centerLat))

        // Determine Map Bearing for rotation (outside Canvas for HUD access)
        val currentBearingDegrees = if (isHeadingUp && recordedPath.size >= 2) {
            val pLast = recordedPath.last()
            val pPrev = recordedPath[recordedPath.size - 2]
            val dLat = pLast.latitude - pPrev.latitude
            val dLng = (pLast.longitude - pPrev.longitude) * cosLat
            val rad = Math.atan2(dLng, dLat)
            Math.toDegrees(rad).toFloat()
        } else {
            0f
        }

        // Mathematical projection converter (WGS84 GPS coordinate -> 2D screen coordinate pixels)
        val project: (Double, Double) -> Offset = { lat, lng ->
            val dx = (lng - centerLng) * cosLat * zoom
            val dy = -(lat - centerLat) * zoom
            Offset(
                x = (width / 2f) + dx.toFloat() + animDragOffset.value.x,
                y = (height / 2f) + dy.toFloat() + animDragOffset.value.y
            )
        }

        val effectiveRotation = if (isHeadingUp) currentBearingDegrees else manualRotation

        // Estimate current visible coordinate ranges
        val latHalfSpan = (height / 2f) / zoom
        val lngHalfSpan = (width / 2f) / (zoom * cosLat)
        
        val minLat = centerLat - latHalfSpan - animDragOffset.value.y / zoom
        val maxLat = centerLat + latHalfSpan - animDragOffset.value.y / zoom
        val minLng = centerLng - lngHalfSpan - animDragOffset.value.x / (zoom * cosLat)
        val maxLng = centerLng + lngHalfSpan - animDragOffset.value.x / (zoom * cosLat)

        // Dynamic Web Mercator Tile calculation (No API Key real map, no shop names)
        val osmZoom = (java.lang.Math.log(1.40877 * cosLat * zoom) / java.lang.Math.log(2.0)).roundToInt().coerceIn(2, 18)
        val n = 1 shl osmZoom

        val ftileXMin = n * (minLng + 180.0) / 360.0
        val ftileXMax = n * (maxLng + 180.0) / 360.0
        val ftileYMin = n * (1.0 - java.lang.Math.log(java.lang.Math.tan(java.lang.Math.toRadians(maxLat)) + 1.0 / java.lang.Math.cos(java.lang.Math.toRadians(maxLat))) / java.lang.Math.PI) / 2.0
        val ftileYMax = n * (1.0 - java.lang.Math.log(java.lang.Math.tan(java.lang.Math.toRadians(minLat)) + 1.0 / java.lang.Math.cos(java.lang.Math.toRadians(minLat))) / java.lang.Math.PI) / 2.0

        val minTileX = floor(ftileXMin).toInt().coerceIn(0, n - 1)
        val maxTileX = ceil(ftileXMax).toInt().coerceIn(0, n - 1)
        val minTileY = floor(ftileYMin).toInt().coerceIn(0, n - 1)
        val maxTileY = ceil(ftileYMax).toInt().coerceIn(0, n - 1)

        val tileXRange = minTileX..maxTileX.coerceAtMost(minTileX + 8)
        val tileYRange = minTileY..maxTileY.coerceAtMost(minTileY + 8)

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Render dynamic web map tiles underneath the Canvas overlays
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = -effectiveRotation
                    }
            ) {
                for (tileY in tileYRange) {
                    for (tileX in tileXRange) {
                        val tileLngStart = tileX.toDouble() * 360.0 / n - 180.0
                        val tileLngEnd = (tileX + 1).toDouble() * 360.0 / n - 180.0
                        val tileLatStart = java.lang.Math.toDegrees(java.lang.Math.atan(java.lang.Math.sinh(java.lang.Math.PI * (1.0 - 2.0 * tileY.toDouble() / n))))
                        val tileLatEnd = java.lang.Math.toDegrees(java.lang.Math.atan(java.lang.Math.sinh(java.lang.Math.PI * (1.0 - 2.0 * (tileY + 1).toDouble() / n))))

                        val pTopLeft = project(tileLatStart, tileLngStart)
                        val pBottomRight = project(tileLatEnd, tileLngEnd)

                        val tileW = pBottomRight.x - pTopLeft.x
                        val tileH = pBottomRight.y - pTopLeft.y

                        val tileWDp = with(density) { tileW.coerceAtLeast(0f).toDp() }
                        val tileHDp = with(density) { tileH.coerceAtLeast(0f).toDp() }
                        val tileOffsetX = with(density) { pTopLeft.x.toDp() }
                        val tileOffsetY = with(density) { pTopLeft.y.toDp() }

                        AsyncImage(
                            model = "https://cyberjapandata.gsi.go.jp/xyz/std/$osmZoom/$tileX/$tileY.png", // Geospatial Information Authority of Japan (GSI): Clean, precise official maps of Japan, no shop names!
                            contentDescription = null,
                            modifier = Modifier
                                .absoluteOffset(x = tileOffsetX, y = tileOffsetY)
                                .requiredSize(width = tileWDp, height = tileHDp),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
            val drawWidth = size.width
            val drawHeight = size.height

            // Longitudinal correction angle calculation
            val cosLatVal = cos(Math.toRadians(centerLat))

            // Mathematical projection converter for Canvas coordinate scope
            fun project(lat: Double, lng: Double): Offset {
                val dx = (lng - centerLng) * cosLatVal * zoom
                val dy = -(lat - centerLat) * zoom
                return Offset(
                    x = (drawWidth / 2f) + dx.toFloat() + animDragOffset.value.x,
                    y = (drawHeight / 2f) + dy.toFloat() + animDragOffset.value.y
                )
            }

            // Estimate current visible coordinate ranges
            val latHalfSpanVal = (drawHeight / 2f) / zoom
            val lngHalfSpanVal = (drawWidth / 2f) / (zoom * cosLatVal)
            
            val minLatVal = centerLat - latHalfSpanVal - animDragOffset.value.y / zoom
            val maxLatVal = centerLat + latHalfSpanVal - animDragOffset.value.y / zoom
            val minLngVal = centerLng - lngHalfSpanVal - animDragOffset.value.x / (zoom * cosLatVal)
            val maxLngVal = centerLng + lngHalfSpanVal - animDragOffset.value.x / (zoom * cosLatVal)

            // Dynamic view center
            val viewCenterLat = centerLat - animDragOffset.value.y / zoom
            val viewCenterLng = centerLng + animDragOffset.value.x / (zoom * cosLatVal)

            // Check if coordinates lie near Shonan or Hakone regions
            val isNearShonan = centerLat in 35.25..35.38 && centerLng in 139.35..139.60
            val isNearHakone = centerLat in 35.15..35.25 && centerLng in 138.95..139.10

            // DRAWING LAYERS
            // ==========================================
            // LAYER 3: PRESET RAILWAYS (Enoden & Hakone Line)
            // ==========================================
                if (zoom > 25000f) {
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
                }

                // ==========================================
                // LAYER 4: LATITUDE / LONGITUDE RADAR GRID
                // ==========================================
                val gridStep = when {
                    zoom > 350000 -> 0.001
                    zoom > 150000 -> 0.002
                    zoom > 50000  -> 0.005
                    zoom > 15000  -> 0.02
                    zoom > 5000   -> 0.1
                    zoom > 1500   -> 0.5
                    zoom > 500    -> 2.0
                    else          -> 5.0
                }

                val startGridLat = Math.floor(minLatVal / gridStep) * gridStep
                val endGridLat = Math.ceil(maxLatVal / gridStep) * gridStep
                val startGridLng = Math.floor(minLngVal / gridStep) * gridStep
                val endGridLng = Math.ceil(maxLngVal / gridStep) * gridStep

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
                            drawHeight - 35f,
                            gridLabelPaint
                        )
                    }
                    curGridLng += gridStep
                }

                // Latitude dashed grid lines
                var curGridLat = startGridLat
                while (curGridLat <= endGridLat) {
                    val pL = project(curGridLat, minLngVal)
                    val pR = project(curGridLat, maxLngVal)
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
                                val prev = project(road.nodes[idx - 1].latitude, road.nodes[idx - 1].longitude)
                                val next = project(road.nodes[idx].latitude, road.nodes[idx].longitude)
                                // Smoothly interpolate points based on zoom
                                val kSteps = (zoom / 500f).toInt().coerceIn(1, 100)
                                for (k in 1..kSteps) {
                                    val fraction = k.toFloat() / (kSteps + 1).toFloat()
                                    val lerpPt = androidx.compose.ui.geometry.lerp(prev, next, fraction)
                                    lineTo(lerpPt.x, lerpPt.y)
                                }
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
                                color = Color(0xFFFBBF24), // Selected: bright yellow stripe
                                style = Stroke(
                                    width = 1.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
                                )
                            )
                        } else {
                            drawPath(
                                path = rPath,
                                color = Color.White.copy(alpha = 0.5f), // Unselected: subtle white dashed center dividing line
                                style = Stroke(
                                    width = 1.0.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 12f), 0f)
                                )
                            )
                        }

                        if (isSelected) {

                            // 4. Render Tunnel tubes (Overlay dark mountain tubes with orange caution lines)
                            road.tunnelRanges.forEach { tRange ->
                                val rStart = tRange.first.coerceIn(0, road.nodes.size - 1)
                                val rEnd = tRange.last.coerceIn(0, road.nodes.size - 1)
                                if (rStart < rEnd) {
                                    val tPath = Path().apply {
                                        val start = project(road.nodes[rStart].latitude, road.nodes[rStart].longitude)
                                        moveTo(start.x, start.y)
                                        for (i in rStart + 1..rEnd) {
                                            val prev = project(road.nodes[i - 1].latitude, road.nodes[i - 1].longitude)
                                            val next = project(road.nodes[i].latitude, road.nodes[i].longitude)
                                            // Add intermediate points
                                            val kSteps = (zoom / 500f).toInt().coerceIn(1, 100)
                                            for (k in 1..kSteps) {
                                                val fraction = k.toFloat() / (kSteps + 1).toFloat()
                                                val lerpPt = androidx.compose.ui.geometry.lerp(prev, next, fraction)
                                                lineTo(lerpPt.x, lerpPt.y)
                                            }
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
                // LAYER 7: RECORDED GPS ACCUMULATED TRAIL (Blue Ribbon)
                // ==========================================
                if (recordedPath.isNotEmpty()) {
                    val trailPath = Path().apply {
                        val origin = project(recordedPath[0].latitude, recordedPath[0].longitude)
                        moveTo(origin.x, origin.y)
                        for (i in 1 until recordedPath.size) {
                            val prev = project(recordedPath[i - 1].latitude, recordedPath[i - 1].longitude)
                            val next = project(recordedPath[i].latitude, recordedPath[i].longitude)
                            // Add intermediate points to smoothen angles
                            val kSteps = (zoom / 500f).toInt().coerceIn(1, 100)
                            for (k in 1..kSteps) {
                                val fraction = k.toFloat() / (kSteps + 1).toFloat()
                                val lerpPt = androidx.compose.ui.geometry.lerp(prev, next, fraction)
                                lineTo(lerpPt.x, lerpPt.y)
                            }
                            lineTo(next.x, next.y)
                        }
                    }

                    // Wide soft tracking glow
                    drawPath(
                        path = trailPath,
                        color = routeColor.copy(alpha = 0.25f),
                        style = Stroke(
                            width = 12.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Strong visible royal blue alignment core ribbon
                    drawPath(
                        path = trailPath,
                        color = routeColor,
                        style = Stroke(
                            width = 5.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
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
                // LAYER 9: LANDMARKS AND POI FLAGMARK LABELS / MAJOR CITIES
                // ==========================================
                if (zoom > 30000f) {
                    landmarks.forEach { lm ->
                        val lmPt = project(lm.lat, lm.lng)
                        
                        // Only draw if within bounds for optimized processing
                        if (lmPt.x in -100f..(drawWidth + 100f) && lmPt.y in -100f..(drawHeight + 100f)) {
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
                } else {
                    // Render major Japanese cities at national scale
                    JAPAN_MAJOR_CITIES.forEach { city ->
                        val cityPt = project(city.lat, city.lng)
                        if (cityPt.x in -100f..(drawWidth + 100f) && cityPt.y in -100f..(drawHeight + 100f)) {
                            if (city.isCapital) {
                                // Gold star outline with crimson core representing Tokyo Capital
                                drawCircle(
                                    color = Color(0xFFF59E0B),
                                    radius = 7.dp.toPx(),
                                    center = cityPt
                                )
                                drawCircle(
                                    color = Color(0xFFEF4444),
                                    radius = 4.dp.toPx(),
                                    center = cityPt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 1.5.dp.toPx(),
                                    center = cityPt
                                )
                            } else {
                                // High-contrast clean city marker dot
                                drawCircle(
                                    color = Color(0xFF1E293B),
                                    radius = 4.5.dp.toPx(),
                                    center = cityPt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = cityPt
                                )
                            }

                            // Render city name and regional info underneath
                            drawIntoCanvas { canvas ->
                                val namePaint = Paint().apply {
                                    color = android.graphics.Color.parseColor("#1E293B")
                                    textSize = 11.dp.toPx()
                                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                    textAlign = Paint.Align.CENTER
                                }
                                val subPaint = Paint().apply {
                                    color = android.graphics.Color.parseColor("#64748B")
                                    textSize = 8.5.dp.toPx()
                                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                                    textAlign = Paint.Align.CENTER
                                }
                                canvas.nativeCanvas.drawText(city.name, cityPt.x, cityPt.y - 8.dp.toPx(), namePaint)
                                canvas.nativeCanvas.drawText(city.description, cityPt.x, cityPt.y + 11.dp.toPx(), subPaint)
                            }
                        }
                    }
                }
            }
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

            // Dynamic GPS beacon circle (Google Map style)
            // Outer semi-transparent ring
            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                radius = 12.dp.toPx(),
                center = currentBeaconPt
            )

            // Inner solid blue circle
            drawCircle(
                color = Color(0xFF3B82F6),
                radius = 8.dp.toPx(),
                center = currentBeaconPt
            )

            // White center-line
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = currentBeaconPt
            )
        }
    }

    // ==========================================
    // COGNITIVE METRICS OVERLAYS & CONTROLS HUD
    // ==========================================

    // 1. Prominent START OVERLAY when in STOPPED state
    if (gpsStatus == GpsStatus.STOPPED) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 260.dp) // Offset above the bottom console
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)), // Emerald
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .wrapContentSize()
                    .clickable { onStartRecording?.invoke() } 
                    .graphicsLayer {
                        // Subtle pulsating effect could go here
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Start",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "ここから開始",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

        // Coordinates Panel Card (Top-right) - Safely offset vertically to stop clashing with top controls
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEBFFFFFF)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 185.dp, end = 12.dp)
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

        // Adaptive Map Scale HUD indicator (Top-Start corner below header, safe from bottom clutters)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 185.dp)
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

        // Float Re-centering Button action - Repositioned horizontally on Top Center under status row for zero overlaps
        if (!autoCenter) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 185.dp)
                    .border(1.5.dp, Color(0xFF1D4ED8), RoundedCornerShape(12.dp))
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

        // ==========================================
        // COMPASS TOGGLE BUTTON (Google Maps Style)
        // ==========================================
        // Positioned Top-Right below the zoom/settings column in MainActivity (which ends around 490dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 505.dp, end = 16.dp)
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0x3364748B), androidx.compose.foundation.shape.CircleShape)
                .clickable { 
                    if (isHeadingUp || manualRotation != 0f) {
                        isHeadingUp = false
                        manualRotation = 0f
                    } else {
                        isHeadingUp = true
                    }
                    onReCenter?.invoke()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val r = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                
                // Compass Bearing: Point to North
                val needleRotation = effectiveRotation
                
                rotate(needleRotation) {
                    // Needle Shadow
                    drawPath(
                        path = Path().apply {
                            moveTo(center.x, center.y - r)
                            lineTo(center.x - 3.dp.toPx(), center.y)
                            lineTo(center.x + 3.dp.toPx(), center.y)
                            close()
                        },
                        color = Color(0xFFEF4444) // North (Red)
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(center.x, center.y + r)
                            lineTo(center.x - 3.dp.toPx(), center.y)
                            lineTo(center.x + 3.dp.toPx(), center.y)
                            close()
                        },
                        color = Color(0xFF94A3B8) // South (Grey)
                    )
                }
                
                // Center dot
                drawCircle(color = Color(0xFF1E293B), radius = 1.5.dp.toPx(), center = center)
            }
            
            // Indicator text for Heading-Up mode
            if (isHeadingUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .size(3.dp)
                        .background(Color(0xFF3B82F6), androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}
}
