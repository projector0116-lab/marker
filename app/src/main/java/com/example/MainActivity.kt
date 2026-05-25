package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.StrokeCap
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.RouteDatabase
import com.example.data.RouteRepository
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.util.GeoPoint
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RouteTrackerApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RouteTrackerApp(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Instantiate simple database and setup the ViewModel
    val database = remember { RouteDatabase.getDatabase(context) }
    val repository = remember { RouteRepository(database.routeDao()) }
    val viewModel: RouteViewModel = viewModel(
        factory = RouteViewModelFactory(repository)
    )

    // Observe state metrics reactively from our ViewModel
    val currentLocation by viewModel.currentLocation.collectAsState()
    val recordedPath by viewModel.recordedPath.collectAsState()
    val currentSpeedKmh by viewModel.currentSpeedKmh.collectAsState()
    val totalDistanceKm by viewModel.totalDistanceKm.collectAsState()
    val averageSpeedKmh by viewModel.averageSpeedKmh.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()
    val gpsStatus by viewModel.gpsStatus.collectAsState()
    val selectedRoad by viewModel.selectedRoad.collectAsState()
    val isSnapped by viewModel.isSnapped.collectAsState()
    val isAutoTunnelEnabled by viewModel.isAutoTunnelEnabled.collectAsState()
    val savedRoutes by viewModel.savedRoutesHistory.collectAsState()
    val overlayRoute by viewModel.overlayRoute.collectAsState()
    val isWalkingMode by viewModel.isWalkingMode.collectAsState()
    val stepCount by viewModel.stepCount.collectAsState()

    // Local configuration controllers
    val sharedPrefs = remember { context.getSharedPreferences("route_tracker_settings", android.content.Context.MODE_PRIVATE) }
    
    var routeColorChoice by remember {
        mutableStateOf(sharedPrefs.getString("route_color_choice", "blue") ?: "blue")
    }
    var showSpeedometer by remember {
        mutableStateOf(sharedPrefs.getBoolean("show_speedometer", true))
    }
    var showDistanceDashboard by remember {
        mutableStateOf(sharedPrefs.getBoolean("show_distance_dashboard", true))
    }
    var layoutPresetChoices by remember {
        mutableStateOf(sharedPrefs.getString("layout_preset", "unified_top") ?: "unified_top")
    }
    var showSettingsDialog by remember { mutableStateOf(false) }

    fun updateRouteColorChoice(color: String) {
        routeColorChoice = color
        sharedPrefs.edit().putString("route_color_choice", color).apply()
    }
    fun updateShowSpeedometer(show: Boolean) {
        showSpeedometer = show
        sharedPrefs.edit().putBoolean("show_speedometer", show).apply()
    }
    fun updateShowDistanceDashboard(show: Boolean) {
        showDistanceDashboard = show
        sharedPrefs.edit().putBoolean("show_distance_dashboard", show).apply()
    }
    fun updateLayoutPreset(preset: String) {
        layoutPresetChoices = preset
        sharedPrefs.edit().putString("layout_preset", preset).apply()
    }

    val resolvedRouteColor = when (routeColorChoice) {
        "green" -> Color(0xFF10B981)
        "red" -> Color(0xFFEF4444)
        "purple" -> Color(0xFF8B5CF6)
        "orange" -> Color(0xFFF97316)
        else -> Color(0xFF1D4ED8) // "blue"
    }

    var zoomScale by remember { mutableStateOf(240000f) }
    var autoCenterMap by remember { mutableStateOf(true) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var customSaveName by remember { mutableStateOf("") }
    var showRoadSelectorDialog by remember { mutableStateOf(false) }

    var hasGpsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Live GPS runtime permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasGpsPermission = fineGranted || coarseGranted
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "GPS 測位を開始しました", Toast.LENGTH_SHORT).show()
            viewModel.fetchCurrentLocationOnce(context)
            viewModel.startTracking(context, useLiveGps = true)
        } else {
            Toast.makeText(context, "リアルGPS軌跡を記録するには、位置情報の許可が必要です。", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-align location on launch
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasGpsPermission = hasFine || hasCoarse
        if (hasFine || hasCoarse) {
            viewModel.fetchCurrentLocationOnce(context)
        } else {
            val arr = if (android.os.Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    "android.permission.POST_NOTIFICATIONS"
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            locationPermissionLauncher.launch(arr)
        }
    }

    // Function to initiate tracking (always uses real GPS)
    val initiateTracking: () -> Unit = {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.startTracking(context, useLiveGps = true)
        } else {
            val arr = if (android.os.Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    "android.permission.POST_NOTIFICATIONS"
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            locationPermissionLauncher.launch(arr)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F3F0))
    ) {
        // --------------------------------------------------------------------
        // Component 1: Scenic Vector Panning Canvas Map (fills full screen)
        // --------------------------------------------------------------------
        ScenicMapView(
            currentLocation = currentLocation,
            recordedPath = recordedPath,
            selectedRoad = selectedRoad,
            gpsStatus = gpsStatus,
            overlayRoutePoints = overlayRoute?.getPoints(),
            zoomScale = zoomScale,
            autoCenter = autoCenterMap,
            routeColor = if (isWalkingMode) Color(0xFF10B981) else resolvedRouteColor,
            onMapDragged = { autoCenterMap = false },
            onReCenter = { autoCenterMap = true },
            modifier = Modifier.fillMaxSize()
        )

        // --------------------------------------------------------------------
        // Component 2: Unified Header Dashboard (Fulfills no-Toyota, no-map-select, no-voice-guidance and no-overlap requests)
        // --------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val hasAnyHudVisible = showSpeedometer || showDistanceDashboard
            if (layoutPresetChoices == "unified_top" && hasAnyHudVisible) {
                // Unified Premium Dashboard Card (Speedometer + TRIP Metrics)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Deep premium dark theme
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .testTag("dashboard_hud")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = if (showSpeedometer && showDistanceDashboard) Arrangement.SpaceBetween else Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showSpeedometer) {
                            // Left Side: compact speedometer and ECO lamp
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                if (!isWalkingMode) {
                                    CircularProgressIndicator(
                                        progress = { 1f },
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color(0xFF1E293B),
                                        strokeWidth = 5.dp
                                    )
                                    val progressValue = (currentSpeedKmh / 120.0).coerceIn(0.0, 1.0).toFloat()
                                    val arcColor = when {
                                        currentSpeedKmh > 80f -> Color(0xFFEF4444) // Red alerts
                                        currentSpeedKmh > 50f -> Color(0xFFF59E0B) // Amber
                                        else -> Color(0xFF10B981) // Crisp Hybrid Green
                                    }
                                    CircularProgressIndicator(
                                        progress = { progressValue },
                                        modifier = Modifier.fillMaxSize(),
                                        color = arcColor,
                                        strokeWidth = 5.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%.0f", currentSpeedKmh),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 20.sp
                                        )
                                        Text(
                                            text = "km/h",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsWalk,
                                        contentDescription = "Walking",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                }

                                // ECO Drive Badge or WALK Badge
                                val badgeColor = if (isWalkingMode) Color(0xFF10B981) else when {
                                    currentSpeedKmh <= 0.1 -> Color(0xFF64748B) // Idle Grey
                                    currentSpeedKmh in 1.0..60.0 -> Color(0xFF10B981) // Vivid Emerald ECO
                                    else -> Color(0xFFF59E0B) // Amber Power
                                }
                                val badgeText = if (isWalkingMode) "WALK" else when {
                                    currentSpeedKmh <= 0.1 -> "READY"
                                    currentSpeedKmh in 1.0..60.0 -> "ECO"
                                    else -> "POWER"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = badgeColor
                                    )
                                }
                            }
                        }

                        // Divider
                        if (showSpeedometer && showDistanceDashboard) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(Color(0xFF334155))
                            )
                        }

                        if (showDistanceDashboard) {
                            // Right Side: Metrics (TRIP, TIME, AVG)
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Trip distance
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "走行距離 (TRIP)", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = String.format("%.2f", totalDistanceKm),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF10B981),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(text = " km", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    }
                                }

                                // Elapsed Timer
                                val minutes = durationSeconds / 60
                                val seconds = durationSeconds % 60
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "計測時間 (TIME)", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = String.format("%02d:%02d", minutes, seconds),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Avg Speed or Steps
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (!isWalkingMode) {
                                        Text(text = "平均速度 (AVG)", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = String.format("%.1f", averageSpeedKmh),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(text = " km/h", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        }
                                    } else {
                                        Text(text = "歩数 (STEPS)", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "$stepCount",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(text = " 歩", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lower Status & History trigger bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Tracker Status Indicator
                val statusBg = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> Color(0xFFDCFCE7) // Soft Green background
                    GpsStatus.RAW_ONLY -> Color(0xFFDBEAFE)  // Soft Blue background
                    GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFFFFEDD5) // Soft Orange warning background
                    GpsStatus.PAUSED -> Color(0xFFF1F5F9) // Soft Grey background
                    GpsStatus.STOPPED -> Color(0xFFF8FAFC)
                }
                val statusBorder = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> Color(0xFF86EFAC)
                    GpsStatus.RAW_ONLY -> Color(0xFF93C5FD)
                    GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFFFDBA74)
                    GpsStatus.PAUSED -> Color(0xFFCBD5E1)
                    GpsStatus.STOPPED -> Color(0xFFE2E8F0)
                }
                val statusText = if (isWalkingMode) {
                    when (gpsStatus) {
                        GpsStatus.STOPPED -> "待機中 - 歩行移動を自動で検知します"
                        GpsStatus.PAUSED -> "歩行記録一時停止中"
                        else -> "歩行中 - ルートと歩数を記録しています"
                    }
                } else {
                    when (gpsStatus) {
                        GpsStatus.EXCELLENT -> "車道中央ロック追従中"
                        GpsStatus.RAW_ONLY -> "GPS生座標トレース (補正なし)"
                        GpsStatus.EXTRAPOLATING_TUNNEL -> "トンネル自律補完（GPS補正）🛰️"
                        GpsStatus.PAUSED -> "記録一時停止中"
                        GpsStatus.STOPPED -> "待機中 - 端末のGPSで自動追従します"
                    }
                }
                val statusColor = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> Color(0xFF15803D)
                    GpsStatus.RAW_ONLY -> Color(0xFF1D4ED8)
                    GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFFC2410C)
                    GpsStatus.PAUSED -> Color(0xFF475569)
                    GpsStatus.STOPPED -> Color(0xFF64748B)
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = statusBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .height(38.dp)
                        .border(1.dp, statusBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "beacon")
                        val beaconAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (gpsStatus != GpsStatus.STOPPED && gpsStatus != GpsStatus.PAUSED) {
                                        statusColor.copy(alpha = beaconAlpha)
                                    } else {
                                        Color.Gray
                                    }
                                )
                        )

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Saved Route History Button trigger
                Button(
                    onClick = { showHistorySheet = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFAFFFFFF)),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .testTag("history_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "History",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "履歴 (${savedRoutes.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                // Custom settings configure button right next to history
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFAFFFFFF), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // If historic overlay tracker is active, show overlay name dismiss key
                if (overlayRoute != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        modifier = Modifier
                            .height(38.dp)
                            .clickable { viewModel.selectOverlayRoute(null) }
                            .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF15803D), modifier = Modifier.size(12.dp))
                            Text(
                                text = "履歴表示OFF",
                                color = Color(0xFF15803D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Check & request location permission banner if permission is missing
            if (!hasGpsPermission) {
                Spacer(modifier = Modifier.size(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), // Soft red highlight
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFFFCA5A5), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Denied", tint = Color(0xFFDC2626))
                            Text(
                                text = "位置情報(GPS)の権限がありません",
                                color = Color(0xFF991B1B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "リアルタイムな走行軌跡を計測するには、位置情報の利用許可が必要です。「位置情報の利用を許可する」ボタンを押して設定してください。",
                            color = Color(0xFF7F1D1D),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text(
                                text = "位置情報の利用を許可する",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // Floating map utility buttons (zoom + autoCenter) in the right middle (offset vertically to prevent HUD overlap)
        // --------------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 295.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recenter Lock anchor
            FloatingActionButton(
                onClick = { autoCenterMap = !autoCenterMap },
                containerColor = if (autoCenterMap) Color(0xFF1A73E8) else Color.White,
                contentColor = if (autoCenterMap) Color.White else Color(0xFF0F172A),
                shape = CircleShape,
                modifier = Modifier
                    .size(46.dp)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
            ) {
                Icon(
                    imageVector = if (autoCenterMap) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                    contentDescription = "Recenter Map",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom In
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(500000f) },
                containerColor = Color.White,
                contentColor = Color(0xFF0F172A),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(40f) },
                containerColor = Color.White,
                contentColor = Color(0xFF0F172A),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            // Settings/Customizer Panel Gear
            FloatingActionButton(
                onClick = { showSettingsDialog = true },
                containerColor = Color.White,
                contentColor = Color(0xFF1E293B),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    .testTag("settings_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings Option")
            }
        }

        // ==========================================
        // DYNAMIC CUSTOMIZABLE HUD PLACEMENTS (Bottom Left, Bottom Right, Split Layouts)
        // ==========================================
        if (layoutPresetChoices == "bottom_left" && (showSpeedometer || showDistanceDashboard)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 240.dp) // Hover elegantly above control sheet
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .wrapContentSize()
                        .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .testTag("dashboard_hud_bottom_left")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showSpeedometer) {
                            SpeedometerHUD(currentSpeedKmh = currentSpeedKmh)
                        }
                        if (showSpeedometer && showDistanceDashboard) {
                            HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
                        }
                        if (showDistanceDashboard) {
                            MetricsHUD(
                                totalDistanceKm = totalDistanceKm,
                                durationSeconds = durationSeconds,
                                averageSpeedKmh = averageSpeedKmh
                            )
                        }
                    }
                }
            }
        }

        if (layoutPresetChoices == "bottom_right" && (showSpeedometer || showDistanceDashboard)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 240.dp) // Hover elegantly above control sheet
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .wrapContentSize()
                        .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .testTag("dashboard_hud_bottom_right")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showSpeedometer) {
                            SpeedometerHUD(currentSpeedKmh = currentSpeedKmh)
                        }
                        if (showSpeedometer && showDistanceDashboard) {
                            HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
                        }
                        if (showDistanceDashboard) {
                            MetricsHUD(
                                totalDistanceKm = totalDistanceKm,
                                durationSeconds = durationSeconds,
                                averageSpeedKmh = averageSpeedKmh
                            )
                        }
                    }
                }
            }
        }

        if (layoutPresetChoices == "split") {
            // Speedometer HUD on Left
            if (showSpeedometer) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 240.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier
                            .wrapContentSize()
                            .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                            .testTag("dashboard_hud_split_speed")
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            SpeedometerHUD(currentSpeedKmh = currentSpeedKmh)
                        }
                    }
                }
            }

            // Metrics/Distance HUD on Right
            if (showDistanceDashboard) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 240.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier
                            .wrapContentSize()
                            .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                            .testTag("dashboard_hud_split_metrics")
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            MetricsHUD(
                                totalDistanceKm = totalDistanceKm,
                                durationSeconds = durationSeconds,
                                averageSpeedKmh = averageSpeedKmh
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // --------------------------------------------------------------------
        // Component 5: Bottom Cockpit Console Box (Pedal, Snaps, Tunnels)
        // --------------------------------------------------------------------
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tracking Controls (Start / Pause / Save Stop)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (gpsStatus) {
                        GpsStatus.STOPPED -> {
                            // Modes: Drive vs Walk toggle before start
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Driving Mode Toggle
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!isWalkingMode) Color(0xFFF1F5F9) else Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clickable { if (isWalkingMode) viewModel.toggleWalkingMode() }
                                        .border(
                                            1.5.dp,
                                            if (!isWalkingMode) Color(0xFF1D4ED8) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = "Driving",
                                            tint = if (!isWalkingMode) Color(0xFF1D4ED8) else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "ドライブ",
                                            color = if (!isWalkingMode) Color(0xFF1D4ED8) else Color(0xFF64748B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Walking Mode Toggle
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isWalkingMode) Color(0xFFF1F5F9) else Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clickable { if (!isWalkingMode) viewModel.toggleWalkingMode() }
                                        .border(
                                            1.5.dp,
                                            if (isWalkingMode) Color(0xFF10B981) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsWalk,
                                            contentDescription = "Walking",
                                            tint = if (isWalkingMode) Color(0xFF10B981) else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "歩行",
                                            color = if (isWalkingMode) Color(0xFF10B981) else Color(0xFF64748B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Start Button
                            Button(
                                onClick = initiateTracking,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC20505)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("start_recording_button")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "GPS",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = "リアルGPS計測を開始する",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "スマートGPS補完・車道中央ロック付きで追従が働きます",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            // Active tracking mode layout (Pause / Tunnel / Save Stop keys)
                            // 1. Manual Tunnel simulation trigger
                            // Crucial to demonstrate GPS補完 instantly!
                            Button(
                                onClick = { viewModel.toggleTunnelSimulation() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color(0xFFEA580C) else Color(0xFFF1F5F9)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                                    .border(
                                        1.dp,
                                        if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color(0xFFEA580C) else Color(0xFFCBD5E1),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .testTag("tunnel_simulation_toggle")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Icons.Default.DirectionsTransit else Icons.Outlined.Terrain,
                                        contentDescription = "Tunnel trigger",
                                        tint = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color.White else Color(0xFF0F172A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) "トンネルを出る" else "トンネル進入 (GPS切断)",
                                        fontSize = 11.sp,
                                        color = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color.White else Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Pause trigger
                            IconButton(
                                onClick = { viewModel.pauseTracking() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF1F5F9), CircleShape)
                                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (gpsStatus == GpsStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = Color(0xFF0F172A)
                                )
                            }

                            // 3. Save & Stop trigger
                            Button(
                                onClick = {
                                    // Automatically save route details and end session to fully fulfill automated saving requirement
                                    viewModel.stopTracking(saveAutomatically = true)
                                    Toast.makeText(context, "走行データを自動保存しました！(履歴から再生・マップ重ね合わせ可能です)", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("stop_recording_button")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop & Auto Save", tint = Color.White)
                                    Text("記録終了 (自動保存)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Accessory Cockpit switches: Snapping and Tunnel Automatic Detection Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock-to-road Snapping Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.toggleSnapping() }
                            .padding(end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Switch(
                            checked = isSnapped,
                            onCheckedChange = { viewModel.toggleSnapping() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1A73E8),
                                checkedTrackColor = Color(0xFFDBEAFE),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.scale(0.8f).testTag("snapping_switch")
                        )
                        Column {
                            Text(
                                text = "道路ロック",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isSnapped) "車道中央吸着" else "GPS実測値",
                                fontSize = 8.sp,
                                color = if (isSnapped) Color(0xFF16A34A) else Color(0xFF64748B)
                            )
                        }
                    }

                    // Automatic Tunnel Identification Switch
                    // Fully addresses "とんねるもーどのぼたんおーとにして"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.toggleAutoTunnel() }
                            .padding(start = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Switch(
                            checked = isAutoTunnelEnabled,
                            onCheckedChange = { viewModel.toggleAutoTunnel() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1A73E8),
                                checkedTrackColor = Color(0xFFDBEAFE),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.scale(0.8f).testTag("autotunnel_switch")
                        )
                        Column {
                            Text(
                                text = "トンネル判定",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isAutoTunnelEnabled) "オート（自動検知）" else "マニュアル（手動）",
                                fontSize = 8.sp,
                                color = if (isAutoTunnelEnabled) Color(0xFF1A73E8) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // Segment 2B: Customized Speedometer & Dashboard Float placements (Bottom Left/Right or Compact Left/Right views)
        // --------------------------------------------------------------------
        if (layoutPresetChoices == "split_bottom") {
            if (showSpeedometer) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Deep premium dark theme
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 240.dp) // Offset above pedal/simulation tray
                        .width(115.dp)
                        .border(2.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                        .testTag("speedometer")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(64.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF1E293B),
                                strokeWidth = 4.5.dp
                            )
                            val progressValue = (currentSpeedKmh / 120.0).coerceIn(0.0, 1.0).toFloat()
                            val arcColor = when {
                                currentSpeedKmh > 80f -> Color(0xFFEF4444)
                                currentSpeedKmh > 50f -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                            CircularProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxSize(),
                                color = arcColor,
                                strokeWidth = 4.5.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format("%.0f", currentSpeedKmh),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    text = "km/h",
                                    fontSize = 7.1.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                        // ECO badge
                        val ecoColor = when {
                            currentSpeedKmh <= 0.1 -> Color(0xFF64748B)
                            currentSpeedKmh in 1.0..60.0 -> Color(0xFF10B981)
                            else -> Color(0xFFF59E0B)
                        }
                        val ecoText = when {
                            currentSpeedKmh <= 0.1 -> "READY"
                            currentSpeedKmh in 1.0..60.0 -> "ECO"
                            else -> "POWER"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ecoColor.copy(alpha = 0.15f))
                                .border(1.dp, ecoColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(ecoText, fontSize = 7.sp, fontWeight = FontWeight.Black, color = ecoColor)
                        }
                    }
                }
            }

            if (showDistanceDashboard) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 240.dp)
                        .width(160.dp)
                        .border(2.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                        .testTag("dashboard_hud")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = "Dist", tint = Color(0xFFEA580C), modifier = Modifier.size(12.dp))
                            Text("メーター", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.White)
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                        // TRIP
                        Column {
                            Text("走行距離", fontSize = 7.sp, color = Color(0xFF94A3B8))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%.2f", totalDistanceKm),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp
                                )
                                Text(" km", fontSize = 8.sp, color = Color(0xFF94A3B8))
                            }
                        }
                        // AVG Speed & TIME
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("平均速度", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text(String.format("%.1f", averageSpeedKmh) + "km/h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            val min = durationSeconds / 60
                            val sec = durationSeconds % 60
                            Column {
                                Text("計測時間", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text(String.format("%d:%02d", min, sec), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        if (layoutPresetChoices == "compact_left" && (showSpeedometer || showDistanceDashboard)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 240.dp)
                    .width(180.dp)
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showSpeedometer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                                CircularProgressIndicator(
                                    progress = { (currentSpeedKmh / 120.0).coerceIn(0.0, 1.0).toFloat() },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFF10B981),
                                    strokeWidth = 3.5.dp
                                )
                                Text(String.format("%.0f", currentSpeedKmh), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            }
                            Column {
                                Text("現在の速度", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text("km/h測位中", fontSize = 9.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }
                    if (showDistanceDashboard) {
                        Column {
                            Text("走行距離 (TRIP)", fontSize = 7.sp, color = Color(0xFF94A3B8))
                            Text(String.format("%.2f km", totalDistanceKm), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("平均速度", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text(String.format("%.1f", averageSpeedKmh) + "km/h", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            val min = durationSeconds / 60
                            val sec = durationSeconds % 60
                            Column {
                                Text("経過時間", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text(String.format("%02d:%02d", min, sec), fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        if (layoutPresetChoices == "compact_right" && (showSpeedometer || showDistanceDashboard)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 240.dp)
                    .width(180.dp)
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showSpeedometer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                                CircularProgressIndicator(
                                    progress = { (currentSpeedKmh / 120.0).coerceIn(0.0, 1.0).toFloat() },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFF10B981),
                                    strokeWidth = 3.5.dp
                                )
                                Text(String.format("%.0f", currentSpeedKmh), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            }
                            Column {
                                Text("現在の速度", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text("km/h測位中", fontSize = 9.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }
                    if (showDistanceDashboard) {
                        Column {
                            Text("走行距離 (TRIP)", fontSize = 7.sp, color = Color(0xFF94A3B8))
                            Text(String.format("%.2f km", totalDistanceKm), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("平均速度", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text(String.format("%.1f", averageSpeedKmh) + "km/h", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            val min = durationSeconds / 60
                            val sec = durationSeconds % 60
                            Column {
                                Text("経過時間", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text(String.format("%02d:%02d", min, sec), fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // User Customization Settings Dialog
        // --------------------------------------------------------------------
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF1D4ED8)
                        )
                        Text(
                            text = "HUD表示とルート記録設定",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SECTION 1: Route traced colors
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "🎨 軌跡の記録コードカラー",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "地図上に青色などで記録される軌跡ルート線の表示色を変更できます。",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val colorOptions = listOf(
                                    Triple("blue", "ロイヤル青", Color(0xFF1D4ED8)),
                                    Triple("green", "ECO緑", Color(0xFF10B981)),
                                    Triple("red", "スポーツ赤", Color(0xFFEF4444)),
                                    Triple("purple", "高精細紫", Color(0xFF8B5CF6)),
                                    Triple("orange", "補正オレンジ", Color(0xFFF97316))
                                )
                                colorOptions.forEach { (choiceKey, label, colVal) ->
                                    val isSelected = routeColorChoice == choiceKey
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { updateRouteColorChoice(choiceKey) }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(colVal)
                                                .border(
                                                    width = if (isSelected) 3.dp else 0.dp,
                                                    color = Color(0xFF0F172A),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Current",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.size(2.dp))
                                        Text(label, fontSize = 8.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        // SECTION 2: HUD item ON/OFF Toggles
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "🔘 HUD表示項目のオン/オフ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )

                            // 1. Show speedometer toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { updateShowSpeedometer(!showSpeedometer) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                                    Column {
                                        Text("スピードメーター表示", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text("現在の時速メーターを画面上に表示します", fontSize = 8.1.sp, color = Color(0xFF64748B))
                                    }
                                }
                                Switch(
                                    checked = showSpeedometer,
                                    onCheckedChange = { updateShowSpeedometer(it) },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }

                            // 2. Show distance dashboard toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { updateShowDistanceDashboard(!showDistanceDashboard) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = "Trip", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                                    Column {
                                        Text("ダッシュボード表示", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text("走行距離(TRIP)・経過時間・平均速度を表示します", fontSize = 8.1.sp, color = Color(0xFF64748B))
                                    }
                                }
                                Switch(
                                    checked = showDistanceDashboard,
                                    onCheckedChange = { updateShowDistanceDashboard(it) },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        // SECTION 3: Layout presets position setting
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "📏 画面表示位置のカスタマイズ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "表示位置のプリセットを選択して、画面の配置をカスタマイズします。",
                                fontSize = 9.sp,
                                color = Color(0xFF64748B)
                            )

                            val placementOptions = listOf(
                                "unified_top" to "統合ヘッダー (上部)",
                                "split_bottom" to "左右に分割 (下部)",
                                "compact_left" to "左下コンパクト",
                                "compact_right" to "右下コンパクト"
                            )

                            placementOptions.forEach { (presetKey, label) ->
                                val isPresetSelected = layoutPresetChoices == presetKey
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isPresetSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { updateLayoutPreset(presetKey) }
                                        .border(
                                            width = if (isPresetSelected) 1.5.dp else 1.dp,
                                            color = if (isPresetSelected) Color(0xFF1D4ED8) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isPresetSelected) Color(0xFF1D4ED8) else Color(0xFF0F172A)
                                            )
                                            val descStr = when (presetKey) {
                                                "unified_top" -> "視野が広がる上部の一体型プレミアムHUD"
                                                "split_bottom" -> "左下に速度計、右下にTRIP計を分けて配置"
                                                "compact_left" -> "左下のミニカードにコンパクトに全集約"
                                                else -> "右下のミニカードにコンパクトに全集約"
                                            }
                                            Text(descStr, fontSize = 8.sp, color = Color(0xFF64748B))
                                        }
                                        RadioButton(
                                            selected = isPresetSelected,
                                            onClick = { updateLayoutPreset(presetKey) },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1D4ED8))
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("保存して閉じる", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
            )
        }

        // --------------------------------------------------------------------
        // Saved Track History Overlay Bottom Sheet
        // --------------------------------------------------------------------
        if (showHistorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showHistorySheet = false },
                containerColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📜 保存済みの通過ルート一覧",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(onClick = { showHistorySheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF0F172A))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (savedRoutes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = "None", tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                                Text(
                                    text = "保存されたルートはまだありません",
                                    color = Color(0xFF475569),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "測定を開始して、終了するとルートが自動保存されます",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedRoutes, key = { it.id }) { route ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = route.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF0F172A)
                                                )
                                                val sdf = remember { SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()) }
                                                Text(
                                                    text = sdf.format(Date(route.timestamp)),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            // Delete key
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteRoute(route.id)
                                                    Toast.makeText(context, "ルートを削除しました", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Telemetries recap row
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Column {
                                                    Text("距離", fontSize = 10.sp, color = Color(0xFF64748B))
                                                    Text(String.format("%.2f km", route.distanceKm), color = Color(0xFF16A34A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column {
                                                    Text("平均速度", fontSize = 10.sp, color = Color(0xFF64748B))
                                                    Text(String.format("%.1f km/h", route.averageSpeedKmh), color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column {
                                                    Text("時間", fontSize = 10.sp, color = Color(0xFF64748B))
                                                    val m = route.durationSec / 60
                                                    val s = route.durationSec % 60
                                                    Text(String.format("%d分%02d秒", m, s), color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Load visual overlay onto layout
                                            Button(
                                                onClick = {
                                                    viewModel.selectOverlayRoute(route)
                                                    showHistorySheet = false
                                                    Toast.makeText(context, "ルート履歴を緑色でマップに重ねて表示中", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (overlayRoute?.id == route.id) Color(0xFF10B981) else Color(0xFF1A73E8)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.ShowChart, contentDescription = "Show", tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.size(4.dp))
                                                Text(text = if (overlayRoute?.id == route.id) "表示中" else "マップ表示", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // --------------------------------------------------------------------
        // Save Confirmation Route popup
        // --------------------------------------------------------------------
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("軌跡記録の保存", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "今回の軌跡レコードを保存します。ルート名を入力してください (空白の場合は自動命名されます)。",
                            color = Color(0xFF475569),
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = customSaveName,
                            onValueChange = { customSaveName = it },
                            label = { Text("ルート名称") },
                            placeholder = { Text("いつもの散歩、通勤ドライブなど") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedLabelColor = Color(0xFF1A73E8),
                                focusedBorderColor = Color(0xFF1A73E8),
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("route_save_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveRecordedRoute(if (customSaveName.isNotBlank()) customSaveName else null)
                            viewModel.stopTracking(false)
                            showSaveDialog = false
                            Toast.makeText(context, "ルートを保存しました", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_save_button")
                    ) {
                        Text("保存", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.stopTracking(false)
                            showSaveDialog = false
                        }
                    ) {
                        Text("保存せず終了", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
            )
        }

        // --------------------------------------------------------------------
        // Scenic Map Course Selector Dialog
        // --------------------------------------------------------------------
        if (showRoadSelectorDialog) {
            AlertDialog(
                onDismissRequest = { showRoadSelectorDialog = false },
                title = { Text("🗺️ ドライブコース選択", color = Color(0xFF0F172A), fontWeight = FontWeight.Black) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "計測を行う走行コースを選んでください。コース変更によりマップが自動的にその走行ポイントに再配置されます。",
                            color = Color(0xFF475569),
                            fontSize = 12.sp
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            items(viewModel.availableRoads) { road ->
                                val isSelected = selectedRoad.id == road.id
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectRoad(road)
                                            showRoadSelectorDialog = false
                                            Toast.makeText(context, "${road.name.substringBefore(" (")} コースに切り替えました", Toast.LENGTH_SHORT).show()
                                        }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val iconEmoji = when {
                                            road.id.contains("coast") -> "🌊"
                                            road.id.contains("hakone") -> "🗻"
                                            road.id.contains("tunnel") -> "🚇"
                                            else -> "🚗"
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(if (isSelected) Color(0xFFDBEAFE) else Color(0xFFE2E8F0), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(iconEmoji, fontSize = 18.sp)
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = road.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF0F172A)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${road.nodes.size}地点",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                                if (road.tunnelRanges.isNotEmpty()) {
                                                    Text(
                                                        text = "トンネル: ${road.tunnelRanges.size}箇所",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFEA580C),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF1D4ED8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRoadSelectorDialog = false }) {
                        Text("閉じる", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
            )
        }
    }
}

// ============================================================================
// MODULAR REUSABLE CUSTOMIZABLE HUD COMPONENTS
// ============================================================================
@Composable
fun SpeedometerHUD(
    currentSpeedKmh: Double,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(60.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF1E293B),
                strokeWidth = 5.dp
            )
            val progressValue = (currentSpeedKmh / 120.0).coerceIn(0.0, 1.0).toFloat()
            val arcColor = when {
                currentSpeedKmh > 80f -> Color(0xFFEF4444) // Red alerts
                currentSpeedKmh > 50f -> Color(0xFFF59E0B) // Amber
                else -> Color(0xFF10B981) // Crisp Hybrid Green
            }
            CircularProgressIndicator(
                progress = { progressValue },
                modifier = Modifier.fillMaxSize(),
                color = arcColor,
                strokeWidth = 5.dp,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.0f", currentSpeedKmh),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 19.sp
                )
                Text(
                    text = "km/h",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // ECO Drop Badge
        val ecoColor = when {
            currentSpeedKmh <= 0.1 -> Color(0xFF64748B) // Idle Grey
            currentSpeedKmh in 1.0..60.0 -> Color(0xFF10B981) // Vivid Emerald ECO
            else -> Color(0xFFF59E0B) // Amber Power
        }
        val ecoText = when {
            currentSpeedKmh <= 0.1 -> "READY"
            currentSpeedKmh in 1.0..60.0 -> "ECO"
            else -> "POWER"
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ecoColor.copy(alpha = 0.15f))
                .border(1.dp, ecoColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = ecoText,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = ecoColor
            )
        }
    }
}

@Composable
fun MetricsHUD(
    totalDistanceKm: Double,
    durationSeconds: Long,
    averageSpeedKmh: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Trip distance
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "走行距離 (TRIP)", fontSize = 8.sp, color = Color(0xFF94A3B8))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.2f", totalDistanceKm),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF10B981),
                    fontFamily = FontFamily.Monospace
                )
                Text(text = " km", fontSize = 8.sp, color = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Elapsed Timer
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "計測時間 (TIME)", fontSize = 8.sp, color = Color(0xFF94A3B8))
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Avg Speed
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "平均速度 (AVG)", fontSize = 8.sp, color = Color(0xFF94A3B8))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", averageSpeedKmh),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Text(text = " km/h", fontSize = 8.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}
