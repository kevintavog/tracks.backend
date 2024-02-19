package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.Converter
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.utils.PointCalculator

/*

    Noisy stops are typically caused by stopping in one place for an extended period (at least a few minutes?)
    and are compounded by being inside a building or amongst trees and/or buildings.
    Behaviorally, the pattern observed is:
        - Many speed changes, occasionally extreme and often dramatic
        - Many course changes
        - Mostly contained in a small area (100-200 meters, though extreme speed changes exceed that)
        - Occasional gaps in data

    Some devices automatically disable collection when another system, such as a gyroscope, indicates there is no
    movement.

    Potential attributes:
        - Most stops have foot traffic both before and after
        - (I think?) All stops have foot traffic at least before or after

 */
object NoisyStopDetector {
    const val minDistanceMeters = 100.0
    const val minSeconds = 10 * 60.0
    const val minKmhThreshold = 0.75

    const val maxOverlapMeters = 120.0

    fun process(workspace: GpxWorkspace) {
// IDEALLY: This would search for runs that head one direction and within a minute or so head another direction.
// Often with many unrealistic speed changes
        // Scan for short-distances that take an unreasonable amount of time
        val stopSegments = findStops(workspace.allSegments)

        stopSegments.forEach { segment ->
            var gapCount = 0
            var gapsDuration = 0.0
            var badAccelerationCount = 0
            var badCourseCount = 0

            segment.points.forEach { point ->
                // get the count (& durations) of data gaps
                if (point.calculatedSeconds > AnalyzerSettings.maxSecondsBetweenPoints) {
                    gapCount += 1
                    gapsDuration += point.calculatedSeconds
                }

                // get the count of unrealistic speed changes
                if (point.accelerationGrade > 3) {
                    badAccelerationCount += 1
                }
            }

            val trajectories = TrajectoryByCourse.process(GpxWorkspace.segmentsByDistance(listOf(segment), 0.5))
            var prevTr:GpsTrajectory? = null
            trajectories.forEach { tr ->
                prevTr?.let { prev ->
                    val deltaCourse = GeoCalculator.bearingDelta(prev.bearing, tr.bearing)
                    if (deltaCourse > 60) {
                        badCourseCount += 1
                    }
                }
                prevTr = tr
            }

//            println("Segment $segment - gc=$gapCount gd=${gapsDuration.toInt()} bac=$badAccelerationCount bcc=$badCourseCount")
        }
            // get the slow moving stops (consider if these should be turned into gaps)
    }

    private fun findStops(segments: List<GpsTrackSegment>): List<GpsTrackSegment> {
        val allTrajectories = TrajectoryByCourse.process(GpxWorkspace.segmentsByDistance(segments, 0.5))
        val stopSegments = mutableListOf<GpsTrackSegment>()
        var startIndex = 0
        while (startIndex < allTrajectories.size) {
            var curIndex = startIndex + 1
            val startSegment = allTrajectories[startIndex]
            while (curIndex < allTrajectories.size) {
                val endSegment = allTrajectories[curIndex]
                if ((startSegment.end.distanceKm(endSegment.start) * 1000.0) < minDistanceMeters) {
                    curIndex += 1
                } else {
                    val prevSegment = allTrajectories[curIndex - 1]
                    val duration = startSegment.end.durationSeconds(prevSegment.start)
                    val meters = startSegment.end.distanceKm(prevSegment.start) * 1000.0
                    val kmh = Converter.metersPerSecondToKilometersPerHour(meters / duration)
                    if (kmh < minKmhThreshold) {
                        val stopPoints = segments.flatMap { it.points }.filter { pt ->
                            pt.time!! >= startSegment.start.time!! && pt.time!! <= prevSegment.start.time!!
                        }
                        stopSegments.add(GpsTrackSegment(PointCalculator.process(stopPoints)))
                    }
                    startIndex = curIndex
                    break
                }
            }
            startIndex += 1
        }
        return stopSegments
    }

    fun process3(workspace: GpxWorkspace) {
        // First, find the number of overlapping trajectories over the next short distance
        val oneMeterTrajectories = TrajectoryByCourse.process(workspace.oneMeterSegments)
        for ((startIndex, startTr) in oneMeterTrajectories.withIndex()) {
            var curIndex = startIndex + 2
            var countOverlap = 0
            var countSeconds = 0
            var metersBetween = 0.0
val debugIt = startTr.start.timeOnly() == "13:44:32"
if (debugIt) {
    println("Debugging ${startTr.start.timeOnly()}-${startTr.end.timeOnly()}")
}
            while (curIndex < oneMeterTrajectories.size && metersBetween < maxOverlapMeters) {
                val curTr = oneMeterTrajectories[curIndex]
//if (curTr.start.timeOnly().startsWith("13:48:")) {
//    println(" Checking ${curTr.start.timeOnly()}-${curTr.end.timeOnly()}")
//}
                metersBetween = startTr.end.distanceKm(curTr.start) * 1000.0
                if (metersBetween >= maxOverlapMeters) {
                    break
                }

                countSeconds = startTr.end.durationSeconds(curTr.start).toInt()
                if (GeoCalculator.doTrajectoriesOverlap(startTr, curTr)) {
if (debugIt) {
    println("  Overlap with ${curTr.start.timeOnly()}-${curTr.end.timeOnly()}")
}
                    countOverlap += 1
                }
                curIndex += 1
            }

            println("${startTr.start.timeOnly()}-${startTr.end.timeOnly()} has $countOverlap/${curIndex - startIndex} overlaps in $countSeconds seconds")
        }
    }

    fun process2(workspace: GpxWorkspace) {
        // From the starting point, check for a minimum of the next N meters / X minutes
        // ahead for abrupt speed and/or course changes
        workspace.allSegments.forEach { segment ->
            if (segment.points.isNotEmpty()) {
                // Analyze each segment independently
                var checkPoint = segment.points.first()
                var countSpeedChange = 0
                var countCourseChange = 0
                segment.points.drop(1).forEach { pt ->
                    val meters = checkPoint.distanceKm(pt) * 1000
                    val seconds = checkPoint.durationSeconds(pt)
                    if (meters > minDistanceMeters && seconds > minSeconds) {
println("${checkPoint.timeOnly()}: $countSpeedChange $countCourseChange (${meters.toInt()} && ${seconds.toInt()})")
                        countSpeedChange = 0
                        countCourseChange = 0
                        checkPoint = pt
                    } else {
                        if (pt.deviceAccelerationGrade > 1.0) {
                            countSpeedChange += 1
                        }
                        if (pt.deltaCourse > 45) {
                            countCourseChange += 1
                        }
                    }
                }

                if (checkPoint != segment.points.last()) {
println("${checkPoint.timeOnly()} (last): $countSpeedChange $countCourseChange")
                }
            }
        }
    }
}