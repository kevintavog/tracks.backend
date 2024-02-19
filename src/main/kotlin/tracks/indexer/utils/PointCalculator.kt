package tracks.indexer.utils

import tracks.core.models.*
import tracks.core.utils.Converter
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.Vector
import tracks.indexer.processors.AnalyzerSettings
import tracks.indexer.processors.SpeedChangeDetector
import tracks.indexer.processors.TransportationCalculator
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.pow

object PointCalculator {
    private val vectorWeights = listOf(0.6, 0.2, 0.1, 0.1)

    fun process(gps: Gps): List<GpsTrackPoint> {
        val allPoints = mutableListOf<GpsTrackPoint>()

        gps.tracks.forEach { track ->
            track.segments.forEach { segment ->
                allPoints.addAll(segment.points)
            }
        }

        return process(allPoints)
    }

    fun process(points: List<GpsTrackPoint>): List<GpsTrackPoint> {
        val allPoints = mutableListOf<GpsTrackPoint>()

        // In the first pass, assign values that depend on this point and the point after it
        for (idx in 0 until points.size-1) {
            val current = points[idx]
            val next = points[idx + 1]
            populateDirect(current, next)
            allPoints.add(current)
        }

        // Fill out basic values for the last point, which is skipped in the loop above
        if (points.isNotEmpty()) {
            points.last().calculatedSeconds = 0.0
            points.last().calculatedCourse = 0
            points.last().calculatedMeters = 0.0
            points.last().calculatedKmh = 0.0
            points.last().calculatedAngularVelocity = 0.0
            allPoints.add(points.last())
        }

        // These calculations depend on nearby points (forward & backward), hence a second pass is needed
        for (idx in 0 until points.size-1) {
            val current = points[idx]
            val next = points[idx + 1]
            val prev = if (idx > 0) points[idx - 1] else null
            populateDifferentials(current, prev, next)

            current.smoothedMeters = smoothValues(idx, points, 5) { pt -> pt.calculatedMeters }
//            current.smoothedKmh = points[idx].calculatedKmh
            current.smoothedKmh = smoothValues(idx, points, 5) { pt -> pt.calculatedKmh }
            current.smoothedCourse = smoothValues(idx, points, 3) { pt -> pt.calculatedCourse.toDouble() }.toInt()
            current.smoothedMps = smoothValues(idx, points, 5) { pt -> pt.calculatedMps }

            current.calculatedAngularVelocity = 0.0
            prev?.let {
                val delta = GeoCalculator.bearingDelta(current.calculatedCourse, it.calculatedCourse).absoluteValue.toDouble()
                current.calculatedAngularVelocity = delta / current.calculatedSeconds
//                current.calculatedAngularVelocity = (current.smoothedCourse - it.smoothedCourse).absoluteValue.toDouble() / current.calculatedSeconds
            }
        }

        // Finally, this last set of values depends upon the smooth calculations
        for (idx in 0 until points.size-1) {
            val current = points[idx]
            val next = points[idx + 1]
            current.smoothedDeltaCourse = GeoCalculator.bearingDelta(current.smoothedCourse, next.smoothedCourse)
        }

        for (idx in 0 until points.size-1) {
            val current = points[idx]
//            current.calculatedTao = (current.smoothedKmh * current.smoothedMeters) / current.smoothedDeltaCourse.toDouble()
            current.recentCourseDeltas = sumValues(idx, points, 7) { pt -> pt.smoothedDeltaCourse }
//            val dc = if (current.deltaCourse == 0) 1.0 else current.deltaCourse.toDouble()
//            current.calculatedTao = (current.smoothedKmh * current.smoothedMeters) / dc
            val deltaCourse = if (current.recentCourseDeltas == 0) 1.0 else current.recentCourseDeltas.toDouble()
            current.calculatedTao = (current.smoothedKmh * current.smoothedMeters) / deltaCourse
        }


        TransportationCalculator.process(allPoints)
        return allPoints
    }

    fun populateOnePoint(current: GpsTrackPoint, previous: GpsTrackPoint?, next: GpsTrackPoint) {
        populateDirect(current, next)
        populateDifferentials(current, previous, next)
    }

    private fun populateDirect(current: GpsTrackPoint, next: GpsTrackPoint) {
        current.calculatedCourse = GeoCalculator.bearing(current, next)
        current.calculatedMeters = GeoCalculator.distanceKm(current, next) * 1000.0
        current.calculatedSeconds = current.durationSeconds(next)

        // Under peculiar conditions, consecutive points can have the same timestamps
        if (current.calculatedSeconds <= 0.001) {
            current.calculatedKmh = 0.0
            current.calculatedMps = 0.0
        } else {
            current.calculatedMps = current.calculatedMeters / current.calculatedSeconds
            current.calculatedKmh = Converter.metersPerSecondToKilometersPerHour(current.calculatedMps)
        }
    }

    private fun populateDifferentials(current: GpsTrackPoint, previous: GpsTrackPoint?, next: GpsTrackPoint) {
        current.deltaCourse = GeoCalculator.bearingDelta(current.calculatedCourse, next.calculatedCourse)

        previous?.let { prev ->
            current.deviceAcceleration = ((current.speed ?: 0.0) - (prev.speed ?: 0.0)) / current.calculatedSeconds
            current.calculatedAcceleration = (current.calculatedMps - prev.calculatedMps) / current.calculatedSeconds
            if (current.calculatedAcceleration.isNaN()) {
                current.calculatedAcceleration = 0.0
            }
            current.accelerationGrade = SpeedChangeDetector.gradeAcceleration(prev.calculatedKmh, current.calculatedAcceleration)
            current.deviceAccelerationGrade = SpeedChangeDetector.gradeAcceleration(prev.speed ?: 0.0, current.deviceAcceleration)
        } ?: run {
            current.deviceAcceleration = 0.0
            current.calculatedAcceleration = 0.0
            current.accelerationGrade = 0.0
            current.deviceAccelerationGrade = 0.0
        }
    }

    private fun smoothValues(index: Int, points: List<GpsTrackPoint>, numPoints: Int?, toValue: (pt: GpsTrackPoint) -> Double): Double {
        val current = points[0.coerceAtLeast(index)]
        val pointsDelta = if (numPoints != null) numPoints / 2 else (AnalyzerSettings.speedSmoothingSeconds * 2 / 3)
        val minIndex = 0.coerceAtLeast(index - pointsDelta)
        val maxIndex = (points.size - 1).coerceAtMost(index + pointsDelta)

        var weight = 0.0
        var sum = 0.0

        for (windex in minIndex..maxIndex) {
            val wPoint = points[windex]

            val secondsDiff = wPoint.durationSeconds(current)
            if (secondsDiff.absoluteValue <= AnalyzerSettings.speedSmoothingSeconds) {
                val pointWeight = exp(-(secondsDiff.pow(2) / (2 * (AnalyzerSettings.speedSmoothingSeconds / 2.0).pow(2))))
                sum += pointWeight
                weight += toValue(wPoint) * pointWeight
            }
        }

        return weight / sum
    }

    private fun sumValues(index: Int, points: List<GpsTrackPoint>, numPoints: Int, toValue: (pt: GpsTrackPoint) -> Int): Int {
        val minIndex = 0.coerceAtLeast(index - numPoints)
        val maxIndex = (points.size - 1).coerceAtMost(index)

        var sum = 0

        for (windex in minIndex..maxIndex) {
            val wPoint = points[windex]
            sum += toValue(wPoint)
        }

        return sum
    }

//    private fun weightedVectors(index: Int, points: List<GpsTrackPoint>): Vector {
//        val minIndex = 0.coerceAtLeast(index - 3)
//        val maxIndex = (points.size - 1).coerceAtMost(index)
//
//        var vector = Vector(0.0, 0.0)
//        for (windex in minIndex..maxIndex) {
//            val weight = vectorWeights[index - windex]
//            vector += points[windex].vector.fraction(weight)
//        }
//
//        return vector
//    }

//    private fun weightedVectors(index: Int, points: List<GpsTrackPoint>, count: Int): Vector {
//        val isForward = count > 0
//        val minIndex = 0.coerceAtLeast(if (isForward) index else index + count)
//        val maxIndex = (points.size - 1).coerceAtMost(if (isForward) index + count else index)
//
//        var vector = Vector(0.0, 0.0)
//        for (windex in minIndex..maxIndex) {
//            val weight = vectorWeights[if (isForward) windex - index else index - windex]
//            vector += points[windex].vector.fraction(weight)
//        }
//
//        return vector
//    }
//
//    private fun vectorChangeCalculator(first: Vector, second: Vector): Int {
//        var change = 0
//        val courseDelta = GeoCalculator.bearingDelta(first.course.toInt(), second.course.toInt())
//        change += ((courseDelta / 30.0) * 10.0).toInt()
//        val minSpeed = first.speed.coerceAtMost(second.speed)
//        val maxSpeed = first.speed.coerceAtLeast(second.speed)
//        val speedDelta = maxSpeed - minSpeed
//        if (minSpeed < 8) {
//            change += ((speedDelta / 4.0) * 20.0).toInt()
////        } else if (minSpeed < 20) {
////            change += ((speedDelta / 10.0) * 30.0).toInt()
//        } else {
//            change += ((speedDelta / 10.0) * 20.0).toInt()
//        }
////println("ch=$change cd=$courseDelta sd=${speedDelta.toInt()} min=${minSpeed.toInt()}")
//        return change
//    }
}
