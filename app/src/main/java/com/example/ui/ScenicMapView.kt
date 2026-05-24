package com.example.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.GeoPoint
import com.example.util.MockRoad

@SuppressLint("SetJavaScriptEnabled")
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
    val webViewInstance = remember { mutableStateOf<WebView?>(null) }
    val isPageLoaded = remember { mutableStateOf(false) }

    // Convert the project zoom scale to Leaflet OSM zoom level (11-18)
    val osmZoom = remember(zoomScale) {
        (12f + (zoomScale - 80000f) / (500000f - 80000f) * 6f).coerceIn(11f, 18f)
    }

    // Leaflet HTML with high contrast & accurate vector paths drawing
    val htmlContent = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
            <style>
                html, body, #map {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    background: #f1f5f9;
                }
                .leaflet-control-zoom {
                    display: none !important;
                }
                .leaflet-control-attribution {
                    font-size: 8px !important;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: true
                }).setView([${currentLocation.latitude}, ${currentLocation.longitude}], 15);

                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '© OpenStreetMap drivers'
                }).addTo(map);

                // Custom Location PIN with neon green inner core and pulsating ripple ring
                var curMarkerIcon = L.divIcon({
                    className: 'custom-div-icon',
                    html: `
                        <div style="position: relative; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;">
                            <div style="position: absolute; width: 28px; height: 28px; border-radius: 50%; background: rgba(16, 185, 129, 0.4); animation: pulse 1.6s infinite;"></div>
                            <div style="position: absolute; width: 16px; height: 16px; border-radius: 50%; background: #ffffff; border: 2px solid #1e3a8a; display: flex; align-items: center; justify-content: center; box-shadow: 0 1px 4px rgba(0,0,0,0.4);">
                                <div style="width: 8px; height: 8px; border-radius: 50%; background: #10b981;"></div>
                            </div>
                        </div>
                        <style>
                            @keyframes pulse {
                                0% { transform: scale(0.6); opacity: 1; }
                                100% { transform: scale(2.2); opacity: 0; }
                            }
                        </style>
                    `,
                    iconSize: [32, 32],
                    iconAnchor: [16, 16]
                });

                var currentMarker = L.marker([${currentLocation.latitude}, ${currentLocation.longitude}], { icon: curMarkerIcon }).addTo(map);

                // Blue trace line representing current active real GPS coordinates path
                var recordedPolyline = L.polyline([], {
                    color: '#1E3A8A',
                    weight: 5,
                    opacity: 0.9,
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);

                // Dark grey line showing selection road guidelines
                var roadPolyline = L.polyline([], {
                    color: '#64748B',
                    weight: 6,
                    opacity: 0.7,
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);

                // Emerald green comparison trail line showing database history overlay
                var overlayPolyline = L.polyline([], {
                    color: '#10B981',
                    weight: 4,
                    opacity: 0.95,
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);

                var isAutoCenterEnabled = true;

                // Monitor user panning to toggle AutoCenter flag off
                map.on('dragstart', function() {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onMapDragged();
                    }
                });

                function updateCurrentLocation(lat, lng) {
                    var newLatLng = new L.LatLng(lat, lng);
                    currentMarker.setLatLng(newLatLng);
                    if (isAutoCenterEnabled) {
                        map.panTo(newLatLng);
                    }
                }

                function setRecordedPath(coordsJson) {
                    try {
                        var coords = JSON.parse(coordsJson);
                        recordedPolyline.setLatLngs(coords);
                    } catch(e) {}
                }

                function setRoadPath(coordsJson) {
                    try {
                        var coords = JSON.parse(coordsJson);
                        roadPolyline.setLatLngs(coords);
                    } catch(e) {}
                }

                function setOverlayPath(coordsJson) {
                    try {
                        var coords = JSON.parse(coordsJson);
                        overlayPolyline.setLatLngs(coords);
                    } catch(e) {}
                }

                function setZoom(level) {
                    map.setZoom(level);
                }

                function setAutoCenter(enabled) {
                    isAutoCenterEnabled = enabled;
                    if (enabled) {
                        map.panTo(currentMarker.getLatLng());
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Synchronize data changes inside WebView
    LaunchedEffect(
        isPageLoaded.value,
        currentLocation,
        recordedPath,
        overlayRoutePoints,
        selectedRoad,
        autoCenter,
        osmZoom
    ) {
        if (isPageLoaded.value) {
            val webView = webViewInstance.value ?: return@LaunchedEffect

            webView.evaluateJavascript("setAutoCenter($autoCenter)", null)
            webView.evaluateJavascript("setZoom($osmZoom)", null)
            webView.evaluateJavascript("updateCurrentLocation(${currentLocation.latitude}, ${currentLocation.longitude})", null)

            val recordedJson = recordedPath.joinToString(",", prefix = "[", postfix = "]") { point ->
                "[${point.latitude},${point.longitude}]"
            }
            webView.evaluateJavascript("setRecordedPath('$recordedJson')", null)

            val overlayJson = overlayRoutePoints?.joinToString(",", prefix = "[", postfix = "]") { point ->
                "[${point.latitude},${point.longitude}]"
            } ?: "[]"
            webView.evaluateJavascript("setOverlayPath('$overlayJson')", null)

            val roadJson = selectedRoad.nodes.joinToString(",", prefix = "[", postfix = "]") { point ->
                "[${point.latitude},${point.longitude}]"
            }
            webView.evaluateJavascript("setRoadPath('$roadJson')", null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance.value = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    
                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onMapDragged() {
                            onMapDragged?.invoke()
                        }
                    }, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoaded.value = true
                        }
                    }

                    loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Coordinates Status Overlay HUD
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
                        text = "オフライン対応・OSM広域マップ",
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
                    GpsStatus.EXTRAPOLATING_TUNNEL -> "自律航空補完（GPS遮断）"
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
                        text = "地図非固定（スクロール中）",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "現在地に戻す",
                        color = Color(0xFF1A73E8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
