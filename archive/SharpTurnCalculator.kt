package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpsStop
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.StopReason
import tracks.indexer.utils.FilterByDistance
import tracks.indexer.utils.PointCalculator
import kotlin.math.absoluteValue

object SharpTurnCalculator {
    fun process(workspace: GpxWorkspace) {
        workspace.currentSegments.forEach { segment ->
            val sampledPoints = PointCalculator.process(FilterByDistance.process(segment.points, 0.5))
            val newStops = mutableListOf<GpsStop>()
//            val sampledPoints = segment.points
            var countSharpTurns = 0
//            var countBadSpeed = 0
            var firstIndex = -1
            var lastIndex = -1
            for (idx in 1 until sampledPoints.size) {
                val prev = sampledPoints[idx - 1]
                val current = sampledPoints[idx]

                // These segments are likely on foot; there shouldn't be large jumps in speed or turns
                val courseDelta = GeoCalculator.bearingDelta(prev.calculatedCourse, current.calculatedCourse).absoluteValue
//                val speedDelta = (current.calculatedKmh - prev.calculatedKmh).absoluteValue
                if (courseDelta > 40) {
                    countSharpTurns += 1
//                    println("${current.time} - "
//                        + "$courseDelta (${prev.calculatedCourse} & ${current.calculatedCourse}) &"
//                        + " $speedDelta (${prev.calculatedKmh} & ${current.calculatedKmh} -- ${current.calculatedMeters})"
//                    )
                }
//                if (speedDelta > 4.0) {
//                    countBadSpeed += 1
//                }

                if (courseDelta > 30) { // || speedDelta > 4.0) {
                    if (firstIndex == -1) {
                        firstIndex = idx
                    }
                    lastIndex = idx
                } else if (firstIndex != -1) {
                    if (possibleStop(sampledPoints, firstIndex, lastIndex, countSharpTurns, newStops)) {
                        firstIndex = -1
                        lastIndex = -1
//                        countBadSpeed = 0
                        countSharpTurns = 0
                    }
                }
            }

            if (firstIndex != -1) {
                possibleStop(sampledPoints, firstIndex, sampledPoints.size - 1, countSharpTurns, newStops)
            }

println("Segment ${segment.points.first().time} - ${segment.points.last().time} ")
newStops.forEach { println("STOP: ${it.start.time} - ${it.end.time}") }
            workspace.stops.addAll(newStops)
        }
    }

    private fun possibleStop(points: List<GpsTrackPoint>, firstIndex: Int, lastIndex: Int,
                             countSharpTurns: Int, // countBadSpeed: Int,
                             newStops: MutableList<GpsStop>): Boolean {
        val first = points[firstIndex]
        val last = points[lastIndex]
        val numberPoints = lastIndex - firstIndex + 1
        val durationSeconds = first.durationSeconds(last)
        if (durationSeconds >= 45.0 && numberPoints >= 5) {
            val courseRatio = (100.0 * countSharpTurns.toDouble() / numberPoints.toDouble()).toInt()
//            val speedRatio = (100.0 * countBadSpeed.toDouble() / numberPoints.toDouble()).toInt()
println(
    "PS: ${first.time} - ${last.time}, "
            + "$numberPoints points (${durationSeconds.toInt()} secs), "
            + "$countSharpTurns sharp turns; $courseRatio%, "
//            + "$countBadSpeed speed; $speedRatio%"
)
            if (courseRatio >= 10) { // && speedRatio >= 20) {
                newStops.add(GpsStop(first, last, StopReason.Noise, "Noisy data"))
            }

            return true
        }

        return false
    }

    fun processWithVectors(workspace: GpxWorkspace) {
        workspace.currentSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countSharpTurns = 0

println("Start vector at ${segment.points.first().time} - ${segment.points.last().time}")
            val vectors = VectorByCourse.process(segment)
            for (idx in 1 until vectors.size) {
                val prev = vectors[idx - 1]
                val current = vectors[idx]
                val delta = GeoCalculator.bearingDelta(prev.bearing, current.bearing)
                if (delta > AnalyzerSettings.sharpTurnThreshold) {
                    if (firstIndex == -1) {
                        firstIndex = idx
                    }
                    lastIndex = idx
                    countSharpTurns += 1
                } else if (firstIndex != -1) {
                    if (possibleStop2(firstIndex, lastIndex, countSharpTurns, idx, vectors)) {
                        firstIndex = -1
                        lastIndex = -1
                        countSharpTurns = 0
                    }
                }
            }

            if (firstIndex != -1) {
                possibleStop2(firstIndex, lastIndex, -1, countSharpTurns, vectors)
            }
        }
    }

    fun possibleStop2(firstIndex: Int, lastIndex: Int, currentIndex: Int,
                     countSharpTurns: Int, vectors: List<GpsVector>): Boolean {
        val first = vectors[firstIndex]
        val last = vectors[lastIndex]

        val sharpTurnDuration = first.end.durationSeconds(last.start)
        val secondsFromEnd = if (currentIndex >= 0) last.start.durationSeconds(vectors[currentIndex].end) else -1.0

        if (secondsFromEnd > 1 * 60.0 || currentIndex < 0) {
            val countVectors = lastIndex - firstIndex + 1
            val ratio = countSharpTurns.toDouble() / countVectors.toDouble()
            if (countVectors >= 3 && ratio >= 0.15) {
                processOverlappingVectors(vectors, firstIndex, lastIndex)
                println(
                    "Possible stop from ${first.start.time} - ${last.end.time}: "
                            + "$ratio ($countSharpTurns of $countVectors), "
                            + "duration: $sharpTurnDuration; since: $secondsFromEnd"
                )
            }
            return true
        }

        return false
    }

    fun processOverlappingVectors(vectors: List<GpsVector>, firstIndex: Int, lastIndex: Int) {
//        for (vectorIndex in firstIndex..lastIndex) {
//            NoisyData.assignOverlapCounts(vectorIndex, vectors)
//
//            val vector = vectors[vectorIndex]
////            println("${vector.start.time}: ${vector.backwardOverlapCount} & ${vector.forwardOverlapCount}")
//        }
    }

    fun process1Meter(workspace: GpxWorkspace) {
//        workspace.allSegments.forEach { segment ->
        workspace.oneMeterSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countSharpTurns = 0

            for (idx in 1 until segment.points.size) {
                val current = segment.points[idx]
                if (current.deltaCourse > AnalyzerSettings.sharpTurnThreshold) {
                    if (firstIndex == -1) {
                        firstIndex = idx
                    }
                    lastIndex = idx
                    countSharpTurns += 1
                } else if (firstIndex != -1) {
                    val secondsSinceLast = segment.points[lastIndex].durationSeconds(current)
                    if (secondsSinceLast > AnalyzerSettings.sharpTurnMinSecondsBetween) {
                        // This sharp turn sequence has ended
                        val firstPoint = segment.points[firstIndex]
                        val lastPoint = segment.points[lastIndex]
                        val secondsDuration = firstPoint.durationSeconds(lastPoint)
                        val ratio = (lastIndex - firstIndex + 1).toDouble() / countSharpTurns.toDouble()
println("ST: ${firstPoint.time} - ${lastPoint.time}: $secondsDuration seconds, $ratio")

                        countSharpTurns = 0
                        firstIndex = -1
                        lastIndex = -1
                    }
                }
            }

            if (firstIndex != -1) {
//                val secondsSinceLast = segment.points[lastIndex].durationSeconds(segment.points.last())
//                if (secondsSinceLast > AnalyzerSettings.sharpTurnMinSecondsBetween) {
                    // This sharp turn sequence has ended
                    val firstPoint = segment.points[firstIndex]
                    val lastPoint = segment.points[lastIndex]
                    val secondsDuration = firstPoint.durationSeconds(lastPoint)
                    val ratio = (lastIndex - firstIndex + 1).toDouble() / countSharpTurns.toDouble()
println("ST: ${firstPoint.time} - ${lastPoint.time}: $secondsDuration seconds, $ratio")
//                }
            }
        }
    }

    fun processMultiScan(workspace: GpxWorkspace) {
        workspace.oneMeterSegments.forEach { segment ->
            val sharpPoints = mutableListOf<GpsTrackPoint>()
            var firstSharpPoint = 0
            var pointsSinceEnd = 0

            for (idx in segment.points.indices) {
                assignStats(idx, segment.points)
                val current = segment.points[idx]
if (current.backwardSharpTurns > 2 && current.forwardSharpTurns > 2) {
    if (sharpPoints.isEmpty()) {
        firstSharpPoint = idx
    }
    sharpPoints.add(current)
} else if (sharpPoints.isNotEmpty()) {
    // TODO: Use seconds instead
    if (pointsSinceEnd < 10) {
        pointsSinceEnd += 1
    } else {
        println("Sharp: ${sharpPoints.first().time} - ${sharpPoints.last().time}")
//        examineQuality(sharpPoints)
        sharpPoints.clear()
        pointsSinceEnd = 0
        firstSharpPoint = 0
    }
}
            }

//examineQuality(segment.points)
        }
    }

    private fun examineQuality(points: List<GpsTrackPoint>) {
        points.forEach { point ->
            println("${point.time}: ${fmt(point.calculatedKmh)} kmh, ${point.ele} ele, "
                + "<L${point.backwardSharpTurns}, >L${point.forwardSharpTurns}, ${fmt(point.deltaKmh)} Δ kmh "
                + "(<-: ${point.backwardSpeedDecreases}, <+: ${point.backwardSpeedIncreases}, "
                + ">-: ${point.forwardSpeedDecreases}, >+: ${point.forwardSpeedIncreases}), "
                + "<${point.backwardElevationDelta}, >${point.forwardElevationDelta} Δ ele, "
                + " ${point.transportationTypes}")
        }
    }

    private fun assignStats(index: Int, points: List<GpsTrackPoint>) {
        val current = points[index]

        var checkIndex = index - 1
        while (checkIndex >= 0) {
            val check = points[checkIndex]
            val secondsDiff = check.durationSeconds(current)
            if (secondsDiff > AnalyzerSettings.sharpTurnDuration) {
                break
            }

            if (secondsDiff.toInt() == 60) {
                current.backwardElevationDelta = (current.ele ?: 0.0) - (check.ele ?: 0.0)
            }

            if (secondsDiff.toInt() < 10) {
                when {
                    check.deltaKmh <= -2.0 -> { current.backwardSpeedDecreases += 1 }
                    check.deltaKmh >= 2.0 -> { current.backwardSpeedIncreases += 1 }
                }
            }

            if (check.deltaCourse >= AnalyzerSettings.sharpTurnThreshold) {
                current.backwardSharpTurns += 1
            }
            checkIndex -= 1
        }

        checkIndex = index + 1
        while (checkIndex < points.size) {
            val check = points[checkIndex]
            val secondsDiff = check.durationSeconds(current).absoluteValue
            if (secondsDiff > AnalyzerSettings.sharpTurnDuration) {
                break
            }

            if (secondsDiff.toInt() == 60) {
                current.forwardElevationDelta = (current.ele ?: 0.0) - (check.ele ?: 0.0)
            }

            if (secondsDiff.toInt() < 10) {
                when {
                    check.deltaKmh <= -2.0 -> { current.forwardSpeedDecreases += 1 }
                    check.deltaKmh >= 2.0 -> { current.forwardSpeedIncreases += 1 }
                }
            }

            if (check.deltaCourse >= AnalyzerSettings.sharpTurnThreshold) {
                current.forwardSharpTurns += 1
            }
            checkIndex += 1
        }
    }

    private fun fmt(value: Double): String {
        return String.format("%.2f", value)
    }
}
