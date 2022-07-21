package tracks.indexer.utils

import tracks.core.models.*
import tracks.core.utils.Converter
import tracks.core.utils.GeoCalculator
import tracks.indexer.processors.AnalyzerSettings
import tracks.indexer.processors.SpeedChangeDetector
import tracks.indexer.processors.TransportationCalculator
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

object PointCalculator {
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
            allPoints.add(points.last())
        }

        // These calculations depend on nearby points (forward & backward), hence a second pass is needed
        for (idx in 0 until points.size-1) {
            val current = points[idx]
            val next = points[idx + 1]
            val prev = if (idx > 0) points[idx - 1] else null
            populateDifferentials(current, prev, next)

//            current.smoothedMeters = smoothValues(idx, points, null) { pt -> pt.calculatedMeters }
//            current.smoothedKmh = points[idx].calculatedKmh
            current.smoothedCourse = smoothValues(idx, points, 3) { pt -> pt.calculatedCourse.toDouble() }.toInt()
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
        previous?.let { prev ->
            current.calculatedAcceleration =
                (current.calculatedMps - prev.calculatedMps) / current.calculatedSeconds
            if (current.calculatedAcceleration.isNaN()) {
                current.calculatedAcceleration = 0.0
            }
            current.accelerationGrade = SpeedChangeDetector.gradeAcceleration(prev.calculatedKmh, current.calculatedAcceleration)
        } ?: run {
            current.calculatedAcceleration = 0.0
            current.accelerationGrade = 0.0
        }

        current.deviceAcceleration = ((next.speed ?: 0.0) - (current.speed ?: 0.0)) / current.calculatedSeconds
        current.deltaCourse = GeoCalculator.bearingDelta(current.calculatedCourse, next.calculatedCourse)
    }

    private fun smoothValues(index: Int, points: List<GpsTrackPoint>, numPoints: Int?, toValue: (pt: GpsTrackPoint) -> Double): Double {
        val current = points[index]
        val pointsDelta = if (numPoints != null) numPoints / 2 else (AnalyzerSettings.speedSmoothingSeconds * 2 / 3)
        val minIndex = 0.coerceAtLeast(index - pointsDelta)
        val maxIndex = (points.size - 1).coerceAtMost(index + pointsDelta)

        var speedWeight = 0.0
        var sumWeight = 0.0

        for (windex in minIndex..maxIndex) {
            val wPoint = points[windex]

            val secondsDiff = wPoint.durationSeconds(current)
            if (secondsDiff.absoluteValue <= AnalyzerSettings.speedSmoothingSeconds) {
                val weight = exp(-(secondsDiff.pow(2) / (2 * (AnalyzerSettings.speedSmoothingSeconds / 2.0).pow(2))))
                sumWeight += weight
                speedWeight += toValue(wPoint) * weight
            }
        }

        return speedWeight / sumWeight
    }
}
