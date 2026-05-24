package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    zoomScale: Float = 240000f,
    autoCenter: Boolean = true
) {
    // マニュアルドラッグオフセット
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // autoCenter が true になったら、直ちにドラッグ位置をリセットする
    LaunchedEffect(autoCenter, currentLocation) {
        if (autoCenter) {
            dragOffset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)) // 美しいライトグレー背景
            .pointerInput(autoCenter) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // 投影用基準緯度に合わせたcos補正
            val latRad = Math.toRadians(currentLocation.latitude)
            val cosLat = cos(latRad).toFloat()

            // 緯度経度からキャンバス座標へのマッピング関数
            fun toCanvasOffset(lat: Double, lng: Double): Offset {
                val latDiff = lat - currentLocation.latitude
                val lngDiff = lng - currentLocation.longitude
                
                val x = center.x + (lngDiff * zoomScale * cosLat) + dragOffset.x
                val y = center.y - (latDiff * zoomScale) + dragOffset.y
                return Offset(x.toFloat(), y.toFloat())
            }

            // 1. 背景グリッドの描画 (100ピクセル間隔、緯度経度の目安として)
            val gridSpacing = 120f
            val startX = (dragOffset.x % gridSpacing)
            val startY = (dragOffset.y % gridSpacing)
            
            // 縦グリッド
            var xG = startX
            while (xG < size.width) {
                if (xG > 0) {
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(xG, 0f),
                        end = Offset(xG, size.height),
                        strokeWidth = 1f
                    )
                }
                xG += gridSpacing
            }
            // 横グリッド
            var yG = startY
            while (yG < size.height) {
                if (yG > 0) {
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(0f, yG),
                        end = Offset(size.width, yG),
                        strokeWidth = 1f
                    )
                }
                yG += gridSpacing
            }

            // 2. 選択されたコース道路の描画 (selectedRoad)
            if (selectedRoad.nodes.isNotEmpty()) {
                val roadPath = Path()
                selectedRoad.nodes.forEachIndexed { index, node ->
                    val offset = toCanvasOffset(node.latitude, node.longitude)
                    if (index == 0) {
                        roadPath.moveTo(offset.x, offset.y)
                    } else {
                        roadPath.lineTo(offset.x, offset.y)
                    }
                }
                
                // 道路の下地（太めのアスファルト縁取り）
                drawPath(
                    path = roadPath,
                    color = Color(0xFFCBD5E1),
                    style = Stroke(width = 32f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
                // 道路の中心（白色のアスファルト）
                drawPath(
                    path = roadPath,
                    color = Color.White,
                    style = Stroke(width = 20f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )

                // トンネル区間の描画（点線などで表現）
                selectedRoad.tunnelRanges.forEach { range ->
                    val tunnelPath = Path()
                    var first = true
                    for (i in range.first..range.last) {
                        if (i < selectedRoad.nodes.size) {
                            val node = selectedRoad.nodes[i]
                            val offset = toCanvasOffset(node.latitude, node.longitude)
                            if (first) {
                                tunnelPath.moveTo(offset.x, offset.y)
                                first = false
                            } else {
                                tunnelPath.lineTo(offset.x, offset.y)
                            }
                        }
                    }
                    // トンネルオーバーレイ
                    drawPath(
                        path = tunnelPath,
                        color = Color(0xFF475569), // ダークグレーのトンネル
                        style = Stroke(width = 24f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                    drawPath(
                        path = tunnelPath,
                        color = Color(0xFF1E293B), // インナー
                        style = Stroke(width = 14f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                }
            }

            // 3. 過去保存トレイル（保存済みの通過ルート）緑色
            if (!overlayRoutePoints.isNullOrEmpty() && overlayRoutePoints.size > 1) {
                val overlayPath = Path()
                overlayRoutePoints.forEachIndexed { index, pt ->
                    val offset = toCanvasOffset(pt.latitude, pt.longitude)
                    if (index == 0) {
                        overlayPath.moveTo(offset.x, offset.y)
                    } else {
                        overlayPath.lineTo(offset.x, offset.y)
                    }
                }
                drawPath(
                    path = overlayPath,
                    color = Color(0xFF10B981).copy(alpha = 0.4f),
                    style = Stroke(width = 24f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
                drawPath(
                    path = overlayPath,
                    color = Color(0xFF10B981),
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
            }

            // 4. 今回リアルタイム計測中の走行軌跡 (recordedPath) 青色
            if (recordedPath.size > 1) {
                val trackPath = Path()
                recordedPath.forEachIndexed { index, pt ->
                    val offset = toCanvasOffset(pt.latitude, pt.longitude)
                    if (index == 0) {
                        trackPath.moveTo(offset.x, offset.y)
                    } else {
                        trackPath.lineTo(offset.x, offset.y)
                    }
                }
                
                // 軌跡のブルー影
                drawPath(
                    path = trackPath,
                    color = Color(0xFF1A73E8).copy(alpha = 0.35f),
                    style = Stroke(width = 24f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
                // 軌跡の実線
                drawPath(
                    path = trackPath,
                    color = Color(0xFF1A73E8),
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )

                // 補間計測地点（トンネル自律推測などの点）をオレンジの丸で描画
                recordedPath.forEach { pt ->
                    if (pt.isInterpolated) {
                        val offset = toCanvasOffset(pt.latitude, pt.longitude)
                        drawCircle(
                            color = Color(0xFFEA580C),
                            radius = 12f,
                            center = offset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = offset
                        )
                    }
                }
            }

            // 5. 現在地自車マーカーの描画
            // 現在地は常に (center + dragOffset)
            val carCenter = center + dragOffset

            // 進行方向（ベアリング）の計算
            val bearingDegrees = if (recordedPath.size >= 2) {
                val last = recordedPath.last()
                val prev = recordedPath[recordedPath.size - 2]
                val latD = last.latitude - prev.latitude
                val lngD = (last.longitude - prev.longitude) * cosLat
                val rad = Math.atan2(lngD, latD)
                Math.toDegrees(rad).toFloat()
            } else if (selectedRoad.nodes.size >= 2) {
                val first = selectedRoad.nodes[0]
                val second = selectedRoad.nodes[1]
                val latD = second.latitude - first.latitude
                val lngD = (second.longitude - first.longitude) * cosLat
                val rad = Math.atan2(lngD, latD)
                Math.toDegrees(rad).toFloat()
            } else {
                0f
            }

            // 回転した自車アイコン（三角矢印と波紋）
            // 波紋サークル
            drawCircle(
                color = Color(0xFF1A73E8).copy(alpha = 0.18f),
                radius = 64f,
                center = carCenter
            )
            drawCircle(
                color = Color(0xFF1A73E8).copy(alpha = 0.35f),
                radius = 42f,
                center = carCenter
            )

            // 三角形の進行方向アロー
            rotate(degrees = bearingDegrees, pivot = carCenter) {
                val arrowPath = Path().apply {
                    moveTo(carCenter.x, carCenter.y - 22f)
                    lineTo(carCenter.x - 14f, carCenter.y + 16f)
                    lineTo(carCenter.x, carCenter.y + 8f)
                    lineTo(carCenter.x + 14f, carCenter.y + 16f)
                    close()
                }
                
                // 縁取り
                drawPath(
                    path = arrowPath,
                    color = Color.White,
                    style = Stroke(width = 6f)
                )
                // 塗りつぶし
                drawPath(
                    path = arrowPath,
                    color = Color(0xFF1A73E8)
                )
            }
        }

        // マップ上の補助インジケータ（オートセンター無効時に再オンにするボタン等）
        if (dragOffset != Offset.Zero && !autoCenter) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "現在地からずれています",
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
