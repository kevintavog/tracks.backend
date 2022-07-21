package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.Converter
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.LowQualityRun
import tracks.indexer.models.LowQualityType
import tracks.indexer.utils.PointCalculator
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

object SpeedChangeDetector {
    fun splitSegmentsByFoot(segmentList: List<GpsTrackSegment>): List<GpsTrackSegment> {
        // For each segment, determine if the transportation is by foot or not - when a transition occurs, split the
        // segment
        val newSegments = mutableListOf<GpsTrackSegment>()
        val span = 60
        segmentList.forEach { segment ->
            if (segment.points.isNotEmpty()) {
                val transitionIndices = mutableListOf<Int>()
                var lastOnFoot: Boolean? = null
                var startIndex = 0
                for (index in 1 until segment.points.size) {
                    if (segment.points[startIndex].durationSeconds(segment.points[index]) > span) {
                        val isOnFoot = transportationTypes(segment.points, startIndex, index).first().mode == TransportationMode.foot
                        if (lastOnFoot == null) {
                            lastOnFoot = isOnFoot
                        } else if (isOnFoot != lastOnFoot) {
                            val foundIndex = findFootTransition(segment, startIndex, index)
                            if (foundIndex >= 0) {
                                transitionIndices.add(foundIndex)
                            }
//println("From $lastOnFoot to $isOnFoot ${segment.points[startIndex].timeOnly()}-${segment.points[index].timeOnly()} => ${pointTransition.timeOnly()}")
                            lastOnFoot = isOnFoot
                        }
                        startIndex = index
                    }
                }

                if (transitionIndices.isEmpty()) {
                    newSegments.add(segment)
                } else {
//println("For ${segment.points.size}")
                    var splitIndex = 0
                    transitionIndices.forEach { endIndex ->
//println("  => using $splitIndex and $endIndex")
                        newSegments.add(GpsTrackSegment(segment.points.subList(splitIndex, endIndex)))
                        splitIndex = endIndex
                    }
                    newSegments.add(GpsTrackSegment(segment.points.subList(splitIndex, segment.points.size)))
                }
            }
        }
        return newSegments
    }

    private fun findFootTransition(segment: GpsTrackSegment, startIndex: Int, endIndex: Int): Int {
        val isOnFoot = segment.points[startIndex].transportationTypes.first().mode == TransportationMode.foot
        var index = startIndex
        while (index < endIndex) {
            val thisOnFoot = segment.points[index].transportationTypes.first().mode == TransportationMode.foot
            if (isOnFoot != thisOnFoot) {
                return index - 1
            }
            index += 1
        }
        return -1
    }

    fun gradeAcceleration(speedKmh: Double, accelerationMss: Double): Double {
        val grade = _gradeAcceleration(speedKmh, accelerationMss)
//if (accelerationMss.absoluteValue < 1 && grade > 1) {
//    println("WTF: ${Converter.readableSpeed(speedKmh)}kmh, ${accelerationMss}mss => $grade")
//}
        if (grade.equals(Double.NaN)) {
            return 0.0
        }
        return grade
    }

    private fun _gradeAcceleration(speedKmh: Double, accelerationMss: Double): Double {
        // Acceleration (from BicycleGpsData.pdf)
        // Walk:        +/- 0.75 m/s/s
        // Bicycle:     +/- 1.25 m/s/s
        // Car:         +/- 2.50 m/s/s/
        // Train:       +/- 2.50 m/s/s/
//        val normalizedAcceleration = (accelerationMss / seconds).absoluteValue
        val normalizedAcceleration = accelerationMss.absoluteValue
        when {
            // Walking
            speedKmh <= 6.6 -> {
                if (normalizedAcceleration <= 1.3) {
                    return 0.0
                }
                return max(0.0, min(10.0, normalizedAcceleration / 1.3))
            }
            speedKmh <= 34.0 -> {
                if (normalizedAcceleration <= 1.25) {
                    return 0.0
                }
                return max(0.0, min(10.0, normalizedAcceleration / 1.25))
            }
            speedKmh <= 128.0 -> {
                if (normalizedAcceleration <= 2.50) {
                    return 0.0
                }
                return max(0.0, min(10.0, normalizedAcceleration / 2.50))
            }
            speedKmh <= 320.0 -> {
                if (normalizedAcceleration <= 5.0) {
                    return 0.0
                }
                return max(0.0, min(10.0, normalizedAcceleration / 5.0))
            }
        }
        return 0.0
    }

    fun isBigChange(firstKmh: Double, secondKmh: Double): Boolean {
        val speedDiff = (secondKmh - firstKmh).absoluteValue

        // If stopped, it's only big if it's more than walking
        return if (!SpeedDetector.isMoving(firstKmh)) {
            speedDiff > 5.0
        } else {
            when {
                firstKmh <= 6.4 -> speedDiff > 4.0
                firstKmh <= 34.0 -> speedDiff > 10.0
                firstKmh <= 128.0 -> speedDiff > 20.0
                else -> speedDiff > 20.0
            }
        }
    }

    fun removeHighAcceleration(workspace: GpxWorkspace) {
        val updatedSegments = mutableListOf<GpsTrackSegment>()
        workspace.currentSegments.forEach { segment ->
            val newPoints = segment.points.map { it.copy() }.toMutableList()

            var idx = 0
            while (idx < newPoints.size-1) {
                val current = newPoints[idx]
//                val debug = (current.timeOnly() >= "10:44:00" && current.timeOnly() <= "10:44:04") ||
//                            (current.timeOnly() >= "17:11:44" && current.timeOnly() <= "17:11:52")
//                val debug = current.timeOnly() >= "08:03:27" && current.timeOnly() <= "08:04:43"
val debug = false
                if (current.accelerationGrade < 6) {
                    if (debug) { println("${current.timeOnly()}: OK ${current.accelerationGrade} [${current.calculatedSeconds}s, ${current.calculatedKmh}kmh]") }
                    idx += 1
                } else {
                    // If the jump occurs early in a segment, it means poor data was collected initially - get rid of
                    // the data leading up to the jump (up to the current index).
                    if (idx < 10) {
                        // This is likely poor data from the start of the segment: get rid of it
//                        println("Clearing from ${newPoints.first().timeOnly()} - ${newPoints[idx].timeOnly()}")
                        while (idx >= 0) {
                            newPoints.removeFirst()
                            idx -= 1
                        }
                    } else  if (idx < (newPoints.size - 2)) {
                        // Distances & times are from the current point to the next point, and speeds & acceleration are
                        // calculated from those.
                        // This means when a point indicates the acceleration is unreasonable, it's not this point
                        // but the next point that needs to be removed
                        val removedIndex = idx + 1
                        val removed = newPoints[removedIndex]
                        if (debug) {
                            println("${current.timeOnly()}: Current @$idx ${current.accelerationGrade} [${current.calculatedSeconds.toInt()}s, ${current.calculatedMeters.toInt()}, ${current.calculatedKmh}kmh]")
                            println("${removed.timeOnly()}: REMOVE ${removed.calculatedSeconds.toInt()}s, ${removed.calculatedMeters.toInt()}, ${removed.calculatedKmh}kmh")
                        }
                        newPoints.removeAt(removedIndex)
                        val previous = newPoints.getOrNull(idx - 1)
                        newPoints.getOrNull(idx + 1)?.let { next ->
                            PointCalculator.populateOnePoint(current, previous, next)
                            idx -= 1
                        }
                    }
                    idx += 1

//                    // This is being removed, update the previous point calculations
//                    val indexToRemove = idx
//                    if (idx > 0) {
//
//                        if (debug) { println("${current.timeOnly()}: REMOVE ${current.accelerationGrade} [${current.calculatedSeconds.toInt()}s, ${current.calculatedKmh}kmh]") }
//                        val newPrevious = if (idx > 1) newPoints[idx - 2] else null
//                        val newCurrent = newPoints[idx - 1]
//if (debug) { println("${newCurrent.timeOnly()} before update: ${newCurrent.accelerationGrade} & ${newCurrent.calculatedSeconds}s, ${newCurrent.calculatedKmh}kmh") }
//                        val next = newPoints[idx + 1]
//                        PointCalculator.populateOnePoint(newCurrent, newPrevious, next)
//
//println("${newCurrent.timeOnly()} updated: ${newCurrent.accelerationGrade} & ${newCurrent.calculatedSeconds}s, ${newCurrent.calculatedKmh}kmh")
//                    }
//println("REMOVING ${newPoints[indexToRemove].timeOnly()} ${newPoints[indexToRemove].accelerationGrade} [${newPoints[indexToRemove].calculatedSeconds.toInt()}s, ${newPoints[indexToRemove].calculatedKmh}kmh]")
//                    newPoints.removeAt(indexToRemove)
//println("  --> at same index after: ${newPoints[indexToRemove].timeOnly()} ${newPoints[indexToRemove].accelerationGrade} [${newPoints[indexToRemove].calculatedSeconds.toInt()}s, ${newPoints[indexToRemove].calculatedKmh}kmh]")
                }
            }

            updatedSegments.add(GpsTrackSegment(newPoints))
        }

        workspace.currentSegments = updatedSegments
    }

    private fun emitPointInfo(points: List<GpsTrackPoint>) {
        for (idx in points.indices) {
            val current = points[idx]
            if (current.accelerationGrade >= 7) {
                println("High acceleration at ${current.timeOnly()}")
                val start = (idx - 5).coerceAtLeast(0)
                val end = (idx + 5).coerceAtMost(points.size - 1)
                for (specIndex in start until end) {
                    val pt = points[specIndex]
                    val grade = pt.accelerationGrade // gradeAcceleration(pt.calculatedKmh, pt.calculatedAcceleration)
                    val kmh = Converter.readableSpeed(pt.calculatedKmh)
                    val accel = Converter.readableSpeed(pt.calculatedAcceleration)
                    val meters = pt.calculatedMeters.toInt()
                    val mps = Converter.readableSpeed(pt.calculatedMps)
                    println("  ${pt.timeOnly()}: ${kmh}kmh, ${accel}mss ($meters -- $mps) => ${grade.toInt()}")
                }
            }
        }
    }

    fun process(workspace: GpxWorkspace) {
        workspace.allSegments.forEach { segment ->
            var startingPoint: GpsTrackPoint? = null
            var endingPoint: GpsTrackPoint? = null

            for (current in segment.points) {
                val grade = gradeAcceleration(current.calculatedKmh, current.calculatedAcceleration)
//println("${current.timeOnly()}: ${Converter.readableSpeed(current.calculatedKmh)}kmh, ${
//    Converter.readableSpeed(current.calculatedAcceleration)}mss => ${Converter.readableSpeed(grade)}")
                if (grade >= 2) {
                    if (startingPoint == null) {
                        startingPoint = current
                    }
                    endingPoint = current
//if (grade > 8) {
//println("${current.timeOnly()}: ${Converter.readableSpeed(current.calculatedKmh)}kmh, ${
//    Converter.readableSpeed(current.calculatedAcceleration)}mss => ${
//    Converter.readableSpeed(grade)}")
//}
                } else {
                    startingPoint?.let { sp ->
                        val startingGrade = gradeAcceleration(sp.calculatedKmh, sp.calculatedAcceleration)
                        val kmh = Converter.readableSpeed(sp.calculatedKmh)
                        val acceleration = Converter.readableSpeed(sp.calculatedAcceleration)
                        workspace.lowQualityRuns.add(LowQualityRun(
                            sp,
                            endingPoint!!,
                            LowQualityType.BIG_SPEED_CHANGES,
                        "Speed changed from ${kmh}kmh; ${acceleration}mss => ${Converter.readableSpeed(startingGrade)}"))

//if (startingGrade > 8) {
//    println("Added speed lqr: ${workspace.lowQualityRuns.last()}")
//}
                        startingPoint = null
                        endingPoint = null
                    }
                }
            }
        }
    }

    private fun transportationTypes(points: List<GpsTrackPoint>, startIndex: Int, endIndex: Int): List<TransportationType> {
        val tt = mutableMapOf<TransportationMode, Double>().withDefault { 0.0 }
        for (index in startIndex until endIndex) {
            val point = points[index]
            point.transportationTypes.forEach { pttt ->
                tt[pttt.mode] = tt.getValue(pttt.mode) + pttt.probability
            }
        }
        return tt
            .map { kv -> TransportationType(kv.value / points.size, kv.key) }
            .sortedByDescending { it.probability }
    }
}
