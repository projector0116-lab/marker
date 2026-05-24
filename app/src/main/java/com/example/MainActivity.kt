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
    val savedRoutes by viewModel.savedRoutesHistory.collectAsState()
    val overlayRoute by viewModel.overlayRoute.collectAsState()

    // Local configuration controllers
    var isLiveGpsSelected by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableStateOf(240000f) }
    var autoCenterMap by remember { mutableStateOf(true) }
    var selectRoadDropdownExpanded by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var customSaveName by remember { mutableStateOf("") }

    // Live GPS runtime permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "GPS 測位を開始しました", Toast.LENGTH_SHORT).show()
            viewModel.startTracking(context, useLiveGps = true)
        } else {
            isLiveGpsSelected = false
            Toast.makeText(context, "GPS権限が拒否されました。シミュレーション走行を開始します。", Toast.LENGTH_LONG).show()
            viewModel.startTracking(context, useLiveGps = false)
        }
    }

    // Function to initiate tracking
    val initiateTracking: () -> Unit = {
        if (isLiveGpsSelected) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                viewModel.startTracking(context, useLiveGps = true)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            viewModel.startTracking(context, useLiveGps = false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
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
            modifier = Modifier.fillMaxSize()
        )

        // --------------------------------------------------------------------
        // Component 2: Header Overlay Panel (Status & Road Selection)
        // --------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route Selection Dropdown Trigger Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF01E293B)),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .testTag("route_selector")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectRoadDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = "Map Select", tint = Color(0xFF00E5FF))
                            Column {
                                Text(
                                    text = "選択中のマップ",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = selectedRoad.name.substringBefore(" ("),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand", tint = Color.White)
                        }

                        // Preset Roads Dropdown Menu
                        DropdownMenu(
                            expanded = selectRoadDropdownExpanded,
                            onDismissRequest = { selectRoadDropdownExpanded = false },
                            modifier = Modifier
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                        ) {
                            viewModel.availableRoads.forEach { road ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = road.name,
                                            color = if (road.id == selectedRoad.id) Color(0xFF00E5FF) else Color.White,
                                            fontWeight = if (road.id == selectedRoad.id) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectRoad(road)
                                        selectRoadDropdownExpanded = false
                                        Toast.makeText(context, "${road.name.substringBefore(" (")} コースを選択", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                // Saved Route History Button trigger
                Button(
                    onClick = { showHistorySheet = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xF00F172A)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .testTag("history_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = "History", tint = Color(0xFF00E676))
                        Text(
                            text = "履歴 (${savedRoutes.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Active State Status Tag bar (Exchanges between locks, wiggles or tunnel supplemental extrapolation!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main Tracker Status Indicator
                val statusBg = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> Color(0xFF14532D) // Deep Green
                    GpsStatus.RAW_ONLY -> Color(0xFF1E3A8A)  // Deep Blue
                    GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFF7C2D12) // Glowing orange warning tint
                    GpsStatus.PAUSED -> Color(0xFF334155) // Grey
                    GpsStatus.STOPPED -> Color(0xFF0F172A)
                }
                val statusBorder = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> Color(0xFF22C55E)
                    GpsStatus.RAW_ONLY -> Color(0xFF3B82F6)
                    GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFFEA580C)
                    GpsStatus.PAUSED -> Color(0xFF64748B)
                    GpsStatus.STOPPED -> Color(0xFF1E293B)
                }
                val statusText = when (gpsStatus) {
                    GpsStatus.EXCELLENT -> "GPS 良好 (道路ロック中)"
                    GpsStatus.RAW_ONLY -> "GPS 良好 (吸着 OFF - 生座標)"
                    GpsStatus.EXTRAPOLATING_TUNNEL -> "トンネル進入！自律補完（GPS補完）稼働中 🛰️"
                    GpsStatus.PAUSED -> "記録一時停止中"
                    GpsStatus.STOPPED -> "ログ待機中 - ドライブを開始してください"
                }
                val statusColor = when (gpsStatus) {
                    GpsStatus.EXTRAPOLATING_TUNNEL -> Color(0xFFFFB74D)
                    GpsStatus.EXCELLENT -> Color(0xFF4ADE80)
                    GpsStatus.RAW_ONLY -> Color(0xFF60A5FA)
                    else -> Color.White
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = statusBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, statusBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsating or stateful beacon dot
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
                                .size(8.dp)
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // If historic overlay tracker is active, show overlay name dismiss key
                if (overlayRoute != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xE015803D)),
                        modifier = Modifier
                            .clickable { viewModel.selectOverlayRoute(null) }
                            .border(1.dp, Color(0xFF4ADE80), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Active", tint = Color.White, modifier = Modifier.size(12.dp))
                            Text(
                                text = "履歴表示OFF",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // Component 3: Left floating panel Speedometer & Compass Ring
        // Fulfills "左下に速度がでるようにしてください" (Speedometer on the bottom-left)
        // --------------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 120.dp) // Offset above pedal/simulation tray
                .width(135.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Speedometer HUD Dial Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF50B132B)),
                modifier = Modifier
                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .testTag("speedometer")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        // Background circle gauge tracker
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF1E293B),
                            strokeWidth = 6.dp
                        )

                        // Glowing speed arc
                        val progressValue = (currentSpeedKmh / 120.0).coerceIn(0.0, 1.0).toFloat()
                        val arcColor = when {
                            currentSpeedKmh > 80f -> Color(0xFFEF4444) // Red for speeds > 80kmh
                            currentSpeedKmh > 50f -> Color(0xFFF97316) // Orange
                            else -> Color(0xFF00E5FF) // Glowing cyan
                        }

                        CircularProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier.fillMaxSize(),
                            color = arcColor,
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round
                        )

                        // Speed Readout Digits
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format("%.0f", currentSpeedKmh),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 34.sp
                            )
                            Text(
                                text = "km/h",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(6.dp))

                    Text(
                        text = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) "補完速度ロック" else "実測スピード",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color(0xFFF97316) else Color(0xFF94A3B8)
                    )
                }
            }
        }

        // --------------------------------------------------------------------
        // Component 4: Dashboard Panel (走行距離 details metrics)
        // Fulfills "走行距離も併せて表示するダッシュボード機能も追加してほしい"
        // --------------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xED0F172A)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 120.dp)
                .width(200.dp)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                .testTag("dashboard_hud")
        ) {
            Column(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = "Distance", tint = Color(0xFFFF9100), modifier = Modifier.size(16.dp))
                    Text(
                        text = "ダッシュボード",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // 2.1 走行距離 Distance
                // Glowing numbers with customized visual layouts
                Column {
                    Text(text = "走行距離 (TRIP)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = String.format("%.2f", totalDistanceKm),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FF88),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 24.sp
                        )
                        Text(text = "km", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    }
                }

                // 2.2 平均速度 Average speed
                Column {
                    Text(text = "平均速度 (AVG)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = String.format("%.1f", averageSpeedKmh),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(text = "km/h", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                }

                // 2.3 経過時間 Elapsed timer
                val minutes = durationSeconds / 60
                val seconds = durationSeconds % 60
                Column {
                    Text(text = "計測時間 (TIME)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Timer", tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // Floating map utility buttons (zoom + autoCenter) in the right middle
        // --------------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recenter Lock anchor
            FloatingActionButton(
                onClick = { autoCenterMap = !autoCenterMap },
                containerColor = if (autoCenterMap) Color(0xFF00E5FF) else Color(0xDD1E293B),
                contentColor = if (autoCenterMap) Color.Black else Color.White,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
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
                containerColor = Color(0xDD1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(80000f) },
                containerColor = Color(0xDD1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }

        // --------------------------------------------------------------------
        // Component 5: Bottom Cockpit Console Box (Pedal, Snaps, Tunnels)
        // --------------------------------------------------------------------
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xF50B0F19)),
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
                            // Tracking configuration mode selector (Live GPS vs Simulated Drive)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { isLiveGpsSelected = false },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isLiveGpsSelected) Color(0xFF334155) else Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Computer, contentDescription = "Sim", modifier = Modifier.size(15.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("シミュレーション走行", fontSize = 10.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { isLiveGpsSelected = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isLiveGpsSelected) Color(0xFF334155) else Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.GpsFixed, contentDescription = "Sim", modifier = Modifier.size(15.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("リアルGPS測位", fontSize = 10.sp, color = Color.White)
                                }
                            }

                            // Start Recording trigger
                            Button(
                                onClick = initiateTracking,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("start_recording_button")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.Black)
                                    Text("走行開始", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                    containerColor = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color(0xFFEA580C) else Color(0xDD3E2723)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .border(
                                        1.dp,
                                        if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Color(0xFFF97316) else Color(0x60D7CCC8),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .testTag("tunnel_simulation_toggle")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) Icons.Default.DirectionsTransit else Icons.Outlined.Terrain,
                                        contentDescription = "Tunnel trigger",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (gpsStatus == GpsStatus.EXTRAPOLATING_TUNNEL) "トンネルを出る" else "トンネルに突入",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Pause trigger
                            IconButton(
                                onClick = { viewModel.pauseTracking() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                                    .border(1.dp, Color(0xFF334155), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (gpsStatus == GpsStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = Color.White
                                )
                            }

                            // 3. Save & Stop trigger
                            Button(
                                onClick = {
                                    customSaveName = ""
                                    showSaveDialog = true
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
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                                    Text("保存して終了", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Accessory Cockpit switches: Snapping and Simulator Pedals
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock-to-road Snapping Switch
                    // Fully addresses "道路のまんなかをしっかり線が記録されるようにして"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.toggleSnapping() }
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Switch(
                            checked = isSnapped,
                            onCheckedChange = { viewModel.toggleSnapping() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF0891B2),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.scale(0.8f).testTag("snapping_switch")
                        )
                        Column {
                            Text(
                                text = "道路ロック (車道中央)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isSnapped) "中央線に強制吸着中" else "GPS実計測生プロット",
                                fontSize = 8.sp,
                                color = if (isSnapped) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Accelerator Speed Regulator Pedal for Simulations
                    if (gpsStatus != GpsStatus.STOPPED) {
                        var speedSliderValue by remember { mutableStateOf(50f) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = "Speed Pedal", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            Slider(
                                value = speedSliderValue,
                                onValueChange = {
                                    speedSliderValue = it
                                    viewModel.setTargetSpeed(it.toDouble())
                                },
                                valueRange = 10f..115f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF00E5FF),
                                    inactiveTrackColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            )
                            Text(
                                text = "${speedSliderValue.toInt()} km/h",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // Saved Track History Overlay Bottom Sheet
        // --------------------------------------------------------------------
        if (showHistorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showHistorySheet = false },
                containerColor = Color(0xFF0F172A),
                scrimColor = Color.Black.copy(alpha = 0.6f),
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
                            text = "📜 自動保存済みの通過ルート一覧",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = { showHistorySheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

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
                                Icon(Icons.Outlined.History, contentDescription = "None", tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                                Text(
                                    text = "保存されたルートはまだありません",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "走行を開始して、終了するとルートが保存されます",
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
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
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
                                                    color = Color.White
                                                )
                                                val sdf = remember { SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()) }
                                                Text(
                                                    text = sdf.format(Date(route.timestamp)),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF94A3B8)
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
                                                    Text("距離", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                                    Text(String.format("%.2f km", route.distanceKm), color = Color(0xFF00FF88), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column {
                                                    Text("平均速度", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                                    Text(String.format("%.1f km/h", route.averageSpeedKmh), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column {
                                                    Text("時間", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                                    val m = route.durationSec / 60
                                                    val s = route.durationSec % 60
                                                    Text(String.format("%d分%02d秒", m, s), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                                    containerColor = if (overlayRoute?.id == route.id) Color(0xFF10B981) else Color(0xFF0284C7)
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
                title = { Text("ドライブの保存", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "今回のドライブ記録を保存します。ルート名を入力してください (空白の場合は自動命名されます)。",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = customSaveName,
                            onValueChange = { customSaveName = it },
                            label = { Text("ルート名称") },
                            placeholder = { Text("鎌倉海岸ドライブ、箱根峠攻めなど") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF00E5FF),
                                focusedBorderColor = Color(0xFF00E5FF),
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_save_button")
                    ) {
                        Text("保存", color = Color.Black, fontWeight = FontWeight.Bold)
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
                containerColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            )
        }
    }
}
