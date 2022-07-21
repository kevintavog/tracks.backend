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

            current.smoothedMeters = smoothValues(idx, points, null) { pt -> pt.calculatedMeters }
            current.smoothedKmh = points[idx].calculatedKmh
            current.smoothedCourse = smoothValues(idx, points, 3) { pt -> pt.calculatedCourse.toDouble() }.toInt()

//if (current.timeOnly() >= "08:03:00" && current.timeOnly() <= "08:05:00") {
//    val seconds = current.calculatedSeconds.toInt()
//    val meters = Converter.readableSpeed(current.calculatedMeters)
//    val kmh = Converter.readableSpeed(current.calculatedKmh)
//    val mss = Converter.readableSpeed(current.calculatedAcceleration)
//    val grade = Converter.readableSpeed(current.accelerationGrade)
//    println("[calc] ${current.timeOnly()}: ${seconds}s, ${meters}m, ${kmh}kmh, ${mss}mss, $grade")
//}
        }

//        for (idx in points.indices) {
//            calculateTao(idx, points)
//        }

//        for (idx in 2 until allPoints.size) {
//            val prev = allPoints[idx - 2]
//            val current = allPoints[idx - 1]
//            val next = allPoints[idx]
//
//            val prevToNextSeconds = prev.durationSeconds(next)
//            current.latAcceleration = (next.latVelocity - prev.latVelocity) / prevToNextSeconds
//            current.lonAcceleration = (next.lonVelocity - prev.lonVelocity) / prevToNextSeconds
//            current.eleAcceleration = (next.eleVelocity - prev.eleVelocity) / prevToNextSeconds
//
//            current.acceleration =
//                ((current.latAcceleration * current.latVelocity) +
//                (current.lonAcceleration * current.lonVelocity) +
//                (current.eleAcceleration * current.eleVelocity)) / current.velocity
//        }

        TransportationCalculator.process(allPoints)
        return allPoints
    }

    fun populateOnePoint(current: GpsTrackPoint, previous: GpsTrackPoint?, next: GpsTrackPoint) {
//println("[calc before] ${current.timeOnly()}: ${current.calculatedSeconds.toInt()}s, ${current.calculatedMeters.toInt()}m, ${current.calculatedKmh}kmh, ${current.accelerationGrade}")

        populateDirect(current, next)
        populateDifferentials(current, previous, next)

//println("[calc after] ${current.timeOnly()}: ${current.calculatedSeconds.toInt()}s, ${current.calculatedMeters.toInt()}m, ${current.calculatedKmh}kmh, ${current.accelerationGrade}")
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

    // Ensure we have moved a few feet before calculating the course - to avoid false negative course changes
    private fun calcShortDistanceCourse(points: List<GpsTrackPoint>, from: Int): Int? {
        val point = points[from]
        for (idx in from+1 until points.size-1) {
            val meters = point.distanceKm(points[idx]) * 1000.0
            if (meters >= 0.6) {
                return GeoCalculator.bearing(point, points[idx])
            }
        }

        return null
    }

//    private fun smoothSpeed(index: Int, points: List<GpsTrackPoint>) {
//        val current = points[index]
//        val minIndex = 0.coerceAtLeast(index - (AnalyzerSettings.speedSmoothingSeconds * 2 / 3))
//        val maxIndex = (points.size - 1).coerceAtMost(index + (AnalyzerSettings.speedSmoothingSeconds * 2 / 3))
//
//        var speedWeight = 0.0
//        var sumWeight = 0.0
//
//        for (windex in minIndex..maxIndex) {
//            val wPoint = points[windex]
//
//            val secondsDiff = wPoint.durationSeconds(current)
//            if (secondsDiff.absoluteValue <= AnalyzerSettings.speedSmoothingSeconds) {
//                val weight = exp(-(secondsDiff.pow(2) / (2 * (AnalyzerSettings.speedSmoothingSeconds / 2.0).pow(2))))
//
////if (index == 1501) {
////    println("for $index: $windex -> $secondsDiff -> $weight")
////}
//                sumWeight += weight
//                speedWeight += wPoint.calculatedKmh * weight
//            }
//        }
//
//        current.smoothedKmh = speedWeight / sumWeight
//        current.smoothedMps = Converter.kmhToMetersPerSecond(current.smoothedKmh)
//    }

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

//if (index == 1501) {
//    println("for $index: $windex -> $secondsDiff -> $weight")
//}
                sumWeight += weight
                speedWeight += toValue(wPoint) * weight
            }
        }

        return speedWeight / sumWeight
    }

    private fun funnyHeading(course: Int): Int {
        return when(course % 360) {
            in 0..180 -> { course }
            in 181..360 -> { 360 - course }
            else -> {
println("WTF: funnyHeading: $course")
                0
            }
        }
    }

    private fun calculateCourse(index: Int, points: List<GpsTrackPoint>): Int {
        val current = points[index]

        var sumCourse = 0
        var countPoints = 0

        val smoothingSeconds = 7
        val minIndex = 0.coerceAtLeast(index - smoothingSeconds)
        val maxIndex = (points.size - 1).coerceAtMost(index + smoothingSeconds)

        for (windex in minIndex until maxIndex) {
            val wPoint = points[windex]
            val secondsDiff = wPoint.durationSeconds(current)
            if (secondsDiff.absoluteValue <= 60) {
                countPoints += 1
                sumCourse += wPoint.deltaCourse.absoluteValue
            }
        }

        return (sumCourse.toDouble() / countPoints.toDouble()).toInt()
    }

    private fun calculateTao(index: Int, points: List<GpsTrackPoint>) {
        val current = points[index]

        var sumTao = 0
        var countPoints = 0

        val smoothingSeconds = 7
        val minIndex = 0.coerceAtLeast(index - smoothingSeconds - 1)
        val maxIndex = (points.size - 1).coerceAtMost(index + smoothingSeconds)

        for (windex in minIndex until maxIndex) {
            val prevWPoint = points[windex]
            val wPoint = points[windex + 1]
            val secondsDiff = wPoint.durationSeconds(current)
            if (secondsDiff.absoluteValue <= 60) {
                countPoints += 1
                val prevHeading = funnyHeading(prevWPoint.calculatedCourse)
                val heading = funnyHeading(wPoint.calculatedCourse)

                val delta = (heading - prevHeading).absoluteValue
                val angle = when(delta) {
                    in 0..90 -> { 90 - heading }
                    in 90..180 -> { 360 - (heading - 90) }
                    else -> { sqrt((heading - 90).toDouble().pow(2)).toInt() }
                }
                sumTao += angle.absoluteValue
            }
        }
    }

    private fun fmt(value: Double): String {
        return String.format("%.2f", value)
    }
}
