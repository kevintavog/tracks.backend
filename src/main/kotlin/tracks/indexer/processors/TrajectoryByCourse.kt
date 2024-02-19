package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpxWorkspace
import kotlin.math.absoluteValue

object TrajectoryByCourse {
    // Split the points into trajectory
    fun process(segments: List<GpsTrackSegment>): List<GpsTrajectory> {
        val trajectories = mutableListOf<GpsTrajectory>()
        segments.forEach { segment ->
            trajectories.addAll(process(segment))
        }
        return trajectories
    }

    fun process(segment: GpsTrackSegment): List<GpsTrajectory> {
        return twoPassProcess(segment.points)
    }

    // Two pass trajectory creation.
    //   The first pass uses very conservative values to determine trajectories.
    //   The second pass combines consecutive trajectories that are similar enough
    private fun twoPassProcess(points: List<GpsTrackPoint>): List<GpsTrajectory> {
        val firstTrajectories = mutableListOf<GpsTrajectory>()
        var trajectoryStart = 0
        var initialBearing: Int? = null
        for (idx in points.indices) {
            val current = points[idx]
            val pointsTrajectory = idx - trajectoryStart
            if (pointsTrajectory > 0) {
                val deltaBearing = GeoCalculator.bearingDelta(points[idx - 1].smoothedCourse, current.smoothedCourse).absoluteValue

                if (initialBearing == null) {
                    val meters = points[trajectoryStart].distanceKm(current) * 1000
                    if (meters >= 2) {
                        initialBearing = GeoCalculator.bearing(points[trajectoryStart], current)
                    }
                }


                var deltaFromInitial = 0
                initialBearing?.let {
                    deltaFromInitial = GeoCalculator.bearingDelta(it, current.smoothedCourse).absoluteValue
                }

                // Split due to time gap
                if (deltaBearing >= 10 || deltaFromInitial >= 10) {
                    val start = points[trajectoryStart]

                    // Ignore trajectories that start and end in the same place
                    if (start.lat != current.lat && start.lon != current.lon) {
                        firstTrajectories.add(GpsTrajectory(start, current, GeoCalculator.bearing(start, current)))
                    }
                    trajectoryStart = idx
                    initialBearing = null
                }
            }
        }

        if (points.isNotEmpty() && trajectoryStart != points.size-1) {
            firstTrajectories.add(GpsTrajectory(points[trajectoryStart], points.last(), GeoCalculator.bearing(points[trajectoryStart], points.last())))
        }

        // Second pass - combine consecutive trajectories that have a similar course
        val trajectories = mutableListOf<GpsTrajectory>()
        if (firstTrajectories.isNotEmpty()) {
            var prevTrajectory = firstTrajectories.first()
            for (idx in 1 until firstTrajectories.size) {
                val current = firstTrajectories[idx]
                val deltaBearing = GeoCalculator.bearingDelta(prevTrajectory.bearing, current.bearing).absoluteValue
                prevTrajectory = if (deltaBearing <= 20) {
                    GpsTrajectory(prevTrajectory.start, current.end, GeoCalculator.bearing(prevTrajectory.start as GpsTrackPoint, current.end as GpsTrackPoint))
                } else {
                    trajectories.add(prevTrajectory)
                    current
                }
            }

            trajectories.add(prevTrajectory)
        }

        return trajectories
    }
}
