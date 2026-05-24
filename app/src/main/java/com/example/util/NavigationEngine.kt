package com.example.util

import kotlin.math.*

/**
 * Geometric helper class for Map points.
 */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    /**
     * Calculates distance to another geo point in kilometers using Haversine formula.
     */
    fun distanceTo(other: GeoPoint): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(other.latitude - this.latitude)
        val dLng = Math.toRadians(other.longitude - this.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(this.latitude)) * cos(Math.toRadians(other.latitude)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

/**
 * Defines a structural road containing nodes, tunnel locations, and snapping capabilities.
 */
data class MockRoad(
    val id: String,
    val name: String,
    val nodes: List<GeoPoint>,
    val tunnelRanges: List<IntRange> // List of node index ranges representing tunnels
) {
    /**
     * Checks if a point index (or location near index) lies in any defined tunnel ranges.
     */
    fun isNodeIndexInTunnel(index: Int): Boolean {
        return tunnelRanges.any { index in it }
    }

    /**
     * Snaps a raw coordinate point to the nearest point on this road's segments.
     * Prevents wander and ensures drawing on "道路のまんなか" (middle of the road).
     */
    fun snapPoint(raw: GeoPoint): GeoPoint {
        if (nodes.isEmpty()) return raw
        if (nodes.size == 1) return nodes[0]

        var bestSnap = nodes[0]
        var minDistance = Double.MAX_VALUE

        for (i in 0 until nodes.size - 1) {
            val a = nodes[i]
            val b = nodes[i + 1]

            val snapped = getClosestPointOnSegment(raw, a, b)
            val dist = raw.distanceTo(snapped)
            if (dist < minDistance) {
                minDistance = dist
                bestSnap = snapped
            }
        }
        return bestSnap
    }

    private fun getClosestPointOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): GeoPoint {
        val latA = a.latitude
        val lngA = a.longitude
        val latB = b.latitude
        val lngB = b.longitude
        val latP = p.latitude
        val lngP = p.longitude

        val dx = latB - latA
        val dy = lngB - lngA

        if (dx == 0.0 && dy == 0.0) return a

        // Scalar projection factor t
        var t = ((latP - latA) * dx + (lngP - lngA) * dy) / (dx * dx + dy * dy)
        t = t.coerceIn(0.0, 1.0)

        return GeoPoint(
            latitude = latA + t * dx,
            longitude = lngA + t * dy
        )
    }
}

/**
 * NavigationEngine: Holds the curated routes and simulation math.
 */
object NavigationEngine {

    // Curve interpolation to make road render seamlessly
    private fun interpolatePoints(points: List<GeoPoint>, factor: Int = 10): List<GeoPoint> {
        if (points.size < 2) return points
        val result = mutableListOf<GeoPoint>()
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            for (j in 0 until factor) {
                val ratio = j.toDouble() / factor
                result.add(
                    GeoPoint(
                        latitude = start.latitude + (end.latitude - start.latitude) * ratio,
                        longitude = start.longitude + (end.longitude - start.longitude) * ratio
                    )
                )
            }
        }
        result.add(points.last())
        return result
    }

    // Beautiful Preset Routes in Shonan/Kamakura/Hakone regions
    val PRESET_ROADS = listOf(
        MockRoad(
            id = "coast_kamakura",
            name = "鎌倉134号湘南シーサイドロード (Coastal Route)",
            nodes = interpolatePoints(listOf(
                GeoPoint(35.3090, 139.5286), // Enoshima coast start
                GeoPoint(35.3075, 139.5350), // Shichirigahama curve
                GeoPoint(35.3060, 139.5420),
                GeoPoint(35.3065, 139.5480), // Cliff entrance (Undercliff Tunnel)
                GeoPoint(35.3080, 139.5530), // Tunnel mid-point
                GeoPoint(35.3100, 139.5580), // Cliff exit
                GeoPoint(35.3115, 139.5640), // Yuigahama approach
                GeoPoint(35.3108, 139.5710), // Yuigahama Coast
                GeoPoint(35.3088, 139.5790)  // Zaimokuza coast end
            ), factor = 15),
            tunnelRanges = listOf(40..66) // Clear segment designated as the cliffside coastal tunnel
        ),
        MockRoad(
            id = "hakone_pass",
            name = "箱根旧街道パノラマライン (Hakone Curve Mountain Pass)",
            nodes = interpolatePoints(listOf(
                GeoPoint(35.1950, 139.0230), // Mountain entrance
                GeoPoint(35.1980, 139.0270), // Sharp hairpins begin
                GeoPoint(35.2020, 139.0240),
                GeoPoint(35.2050, 139.0300), // First Summit
                GeoPoint(35.2030, 139.0360), // Hakone Pass Tunnel Entry
                GeoPoint(35.2000, 139.0410), // Inside Mountain
                GeoPoint(35.1985, 139.0460), // Mountain Tunnel Exit
                GeoPoint(35.2020, 139.0520), // Lakeside downhill
                GeoPoint(35.2050, 139.0580), // Hakone Checkpoint end
                GeoPoint(35.2080, 139.0620)
            ), factor = 20),
            tunnelRanges = listOf(75..110) // Hakone mountain ridge long tunnel
        ),
        MockRoad(
            id = "expressway_tunnels",
            name = "新湘南トンネルバイパス (Twin-Tunnel Expressway)",
            nodes = interpolatePoints(listOf(
                GeoPoint(35.3520, 139.4100), // Bypass Tollgate start
                GeoPoint(35.3535, 139.4200),
                GeoPoint(35.3550, 139.4300), // First Tunnel entry
                GeoPoint(35.3562, 139.4420), // Inside Tunnel 1
                GeoPoint(35.3570, 139.4530), // First Tunnel escape
                GeoPoint(35.3585, 139.4650), // High bridge span
                GeoPoint(35.3598, 139.4750), // Second Tunnel entry (Underpass)
                GeoPoint(35.3610, 139.4880), // Inside Tunnel 2
                GeoPoint(35.3625, 139.5000), // Second Tunnel escape
                GeoPoint(35.3615, 139.5100)  // Interchange termination
            ), factor = 18),
            // Two separate tunnels
            tunnelRanges = listOf(30..60, 100..140)
        )
    )
}
