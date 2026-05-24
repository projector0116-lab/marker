package com.example.data

/**
 * Represents a single coordinate point along a recorded route.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val timestamp: Long,
    val isInterpolated: Boolean = false // True if calculated via tunnel dead reckoning/extrapolation
) {
    companion object {
        /**
         * Serializes a list of RoutePoint objects to a simple, robust CSV-like string.
         * Saves dependency complexity and always compiles successfully.
         */
        fun serializeList(points: List<RoutePoint>): String {
            return points.joinToString(";") { point ->
                "${point.latitude},${point.longitude},${point.speedKmh},${point.timestamp},${if (point.isInterpolated) 1 else 0}"
            }
        }

        /**
         * Deserializes a CSV-like string back to a list of RoutePoint objects.
         */
        fun deserializeList(data: String): List<RoutePoint> {
            if (data.isBlank()) return emptyList()
            return try {
                data.split(";").mapNotNull { part ->
                    val tokens = part.split(",")
                    if (tokens.size >= 5) {
                        RoutePoint(
                            latitude = tokens[0].toDouble(),
                            longitude = tokens[1].toDouble(),
                            speedKmh = tokens[2].toDouble(),
                            timestamp = tokens[3].toLong(),
                            isInterpolated = tokens[4] == "1"
                        )
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
