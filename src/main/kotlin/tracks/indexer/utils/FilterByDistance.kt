package tracks.indexer.utils

import tracks.core.models.*

object FilterByDistance {
    fun process(points: List<GpsTrackPoint>, minMeters: Double): List<GpsTrackPoint> {
        val result = mutableListOf<GpsTrackPoint>()
        if (points.isEmpty()) {
            return result
        }

        var previous = points.first()
        result.add(previous)
        for (idx in 1 until points.size) {
            val current = points[idx]
            if (current.distanceKm(previous) * 1000.0 >= minMeters) {
                result.add(current)
                previous = current
            }
        }

        return result
    }
}