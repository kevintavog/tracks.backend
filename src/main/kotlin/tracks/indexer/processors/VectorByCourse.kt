package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpxWorkspace
import kotlin.math.abs
import kotlin.math.absoluteValue

object VectorByCourse {
    // Split the points into vectors
    fun process(workspace: GpxWorkspace): List<GpsVector> {
        val vectors = mutableListOf<GpsVector>()
//        workspace.oneMeterSegments.forEach { segment ->
//            vectors.addAll(process(segment))
//        }
//        workspace.allSegments.forEach { segment ->
//            vectors.addAll(process(segment))
//        }
        workspace.currentSegments.forEach { segment ->
            vectors.addAll(process(segment))
        }
        return vectors
    }

    fun process(segment: GpsTrackSegment): List<GpsVector> {
        return twoPassProcess(segment.points)
    }

    // Look ahead vector creation
    // When a point is encountered that significantly differs from the current course, look ahead a bit to
    // determine if it impacts a couple of points or indicates a real change
//    private fun lookAheadProcess(points: List<GpsTrackPoint>): List<GpsVector> {
//        val vectors = mutableListOf<GpsVector>()
//        return vectors
//    }

    // Two pass vector creation.
    //   The first pass uses very conservative values to determine vectors.
    //   The second pass combines consecutive vectors that are similar enough
    private fun twoPassProcess(points: List<GpsTrackPoint>): List<GpsVector> {
        val firstVectors = mutableListOf<GpsVector>()
        var vectorStart = 0
        var initialBearing: Int? = null
        for (idx in points.indices) {
            val current = points[idx]
            val pointsInVector = idx - vectorStart
            if (pointsInVector > 0) {
                val deltaBearing = GeoCalculator.bearingDelta(points[idx - 1].smoothedCourse, current.smoothedCourse).absoluteValue

                if (initialBearing == null) {
                    val meters = points[vectorStart].distanceKm(current) * 1000
                    if (meters >= 2) {
                        initialBearing = GeoCalculator.bearing(points[vectorStart], current)
                    }
                }


                var deltaFromInitial = 0
                initialBearing?.let {
                    deltaFromInitial = GeoCalculator.bearingDelta(it, current.smoothedCourse).absoluteValue
                }

                // Split due to time gap
                if (deltaBearing >= 10 || deltaFromInitial >= 10) {
                    val start = points[vectorStart]

                    // Ignore vectors that start and end in the same place
                    if (start.lat != current.lat && start.lon != current.lon) {
                        firstVectors.add(GpsVector(start, current, GeoCalculator.bearing(start, current)))
                    }
                    vectorStart = idx
                    initialBearing = null
                }
            }
        }

        if (vectorStart != points.size-1) {
            firstVectors.add(GpsVector(points[vectorStart], points.last(), GeoCalculator.bearing(points[vectorStart], points.last())))
        }

        // Second pass - combine consecutive vectors that have a similar course
        val vectors = mutableListOf<GpsVector>()
        if (firstVectors.isNotEmpty()) {
            var prevVector = firstVectors.first()
            for (idx in 1 until firstVectors.size) {
                val current = firstVectors[idx]
                val deltaBearing = GeoCalculator.bearingDelta(prevVector.bearing, current.bearing).absoluteValue
                prevVector = if (deltaBearing <= 20) {
                    GpsVector(prevVector.start, current.end, GeoCalculator.bearing(prevVector.start as GpsTrackPoint, current.end as GpsTrackPoint))
                } else {
                    vectors.add(prevVector)
                    current
                }
            }

            vectors.add(prevVector)
        }

//        if (firstVectors.isNotEmpty() && firstVectors.last().end.time != points.last().time) {
//            println("Remaining items at end? $vectorStart (${points.size})")
//        }
        return vectors
    }

    private fun oldProcess(points: List<GpsTrackPoint>): List<GpsVector> {
        var vectorStart = 0
        val vectors = mutableListOf<GpsVector>()
        var currentDelta = 0
        for (idx in points.indices) {
            val current = points[idx]
            val deltaBearing = current.deltaCourse
//println("${current.timeOnly()} ($idx): $deltaBearing, $idx, ${current.calculatedSeconds.toInt()}")
            if (idx > 0 && (current.calculatedSeconds >= 20 ||
                deltaBearing.absoluteValue >= 60 || (currentDelta + deltaBearing).absoluteValue > 45)) {
                val start = points[vectorStart]
                val end = points[idx - 1]
                vectors.add(GpsVector(start, current, GeoCalculator.bearing(start, end)))
println("Vector ${start.timeOnly()} - ${end.timeOnly()} ($vectorStart - (${idx-1}): ${vectors.last()}; b: $deltaBearing && db: ${(currentDelta + deltaBearing).absoluteValue}, s: ${current.calculatedSeconds.toInt()}")
//println("Vector ${start.timeOnly()} - ${end.timeOnly()}: ${vectors.last()}")

                vectorStart = idx
                currentDelta = 0
            } else {
                currentDelta += deltaBearing
            }
        }

        if (vectorStart < points.size - 1) {
            val start = points[vectorStart]
            val end = points.last()
            vectors.add(GpsVector(start, end, GeoCalculator.bearing(start, end)))
        }

        return vectors
    }
}
