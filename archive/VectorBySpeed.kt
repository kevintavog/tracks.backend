package tracks.indexer.processors

import tracks.core.models.GpsTrackSegment
import tracks.core.models.GpsVector
import tracks.indexer.models.GpxWorkspace
import kotlin.math.absoluteValue

object VectorBySpeed {
    fun process(workspace: GpxWorkspace): List<GpsVector> {
        val vectors = mutableListOf<GpsVector>()
        workspace.currentSegments.forEach { segment ->

        }
        return vectors
    }

    fun process(segment: GpsTrackSegment): List<GpsVector> {
        var vectorStart = 0
        var initialSpeed: Double? = null
        val vectors = mutableListOf<GpsVector>()
        for (idx in segment.points.indices) {
            val current = segment.points[idx]
            val pointsInVector = idx - vectorStart
            if (pointsInVector > 0) {
                val deltaSpeed = (segment.points[idx - 1].smoothedKmh - current.smoothedKmh).absoluteValue

            }
        }

        return vectors
    }
}