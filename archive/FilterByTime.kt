package tracks.indexer.utils

import tracks.core.models.*

object FilterByTime {
    fun process(points: List<GpsTrackPoint>, minSeconds: Double): List<GpsTrackPoint> {
        val result = mutableListOf<GpsTrackPoint>()
        if (points.isEmpty()) {
            return result
        }

        var previous = points.first()
        result.add(previous)
        for (idx in 1 until points.size) {
            val current = points[idx]
            if (previous.durationSeconds(current) >= minSeconds) {
                result.add(current)
                previous = current
            }
        }

        return result

    }
}