package tracks.indexer.processors

import tracks.core.models.distanceKm
import tracks.core.models.durationSeconds
import tracks.indexer.models.BreakReason
import tracks.indexer.models.GpsBreak
import tracks.indexer.models.GpxWorkspace
import kotlin.math.absoluteValue

object JumpingPoints {
    fun processCourse(workspace: GpxWorkspace) {
        val breaks = mutableListOf<GpsBreak>()
        workspace.oneMeterSegments.forEach { segment ->
            segment.points.forEach { point ->
                if (point.deltaCourse.absoluteValue >= 75) {
println("${point.time}: ${point.deltaCourse} (${point.speed}, ${point.calculatedKmh}, ${point.deltaKmh}) -- ${point.transportationTypes}")
                    val brk = GpsBreak(
                        point,
                        point,
                        BreakReason.JumpingCourse,
                        point.calculatedSeconds,
                        point.calculatedMeters
                    )
                    breaks.add(brk)
                    workspace.breaks.add(brk)
                }
//if (point.time!! >= "2018-06-25T13:43:13Z" && point.time!! <= "2018-06-25T13:43:19Z") {
//    println("$point")
//}
            }
        }

        detectDenseCourse(workspace, breaks)
    }

    const val maxMatersBetweenBreaks = 200
    const val maxSecondsBetweenBreaks = 5 * 60
    const val minJumpingCourseCount = 7
    const val minDensityCount = 4
    private fun detectDenseCourse(workspace: GpxWorkspace, breaks: List<GpsBreak>) {
        var firstIndex = -1
        var lastIndex = -1
        var countBelowThreshold = 0

        for (idx in breaks.indices) {
            val current = breaks[idx]
            var prevIdx = idx - 1
            while (prevIdx >= 0) {
                val prev = breaks[prevIdx]
                val seconds = prev.end.durationSeconds(current.start)
                val meters = 1000 * prev.end.distanceKm(current.start)
                if (seconds > maxSecondsBetweenBreaks || meters > maxMatersBetweenBreaks) {
                    break
                }
                prevIdx -= 1
            }
            val reverseCount = idx - prevIdx - 1

            var nextIdx = idx + 1
            while (nextIdx < breaks.size) {
                val next = breaks[nextIdx]
                val seconds = current.end.durationSeconds(next.start)
                val meters = 1000 * current.end.distanceKm(next.start)
                if (seconds > maxSecondsBetweenBreaks || meters > maxMatersBetweenBreaks) {
                    break
                }
                nextIdx += 1
            }
            val forwardCount = nextIdx - idx - 1

//println("counts: ${current.start.time}: $reverseCount : $forwardCount")

            if (reverseCount >= minJumpingCourseCount || forwardCount >= minJumpingCourseCount) {
                if (firstIndex == -1) {
                    firstIndex = idx
                    countBelowThreshold = 0
                }
                lastIndex = idx
            } else if (firstIndex != -1) {
                countBelowThreshold += 1
                if (countBelowThreshold > 4) {
                    val densityCount = lastIndex - firstIndex + 1
                    if (densityCount > minDensityCount) {
//println("hd: ${breaks[firstIndex].start.time} - ${breaks[lastIndex].end.time}")
                        workspace.breaks.add(GpsBreak(
                            breaks[firstIndex].start,
                            breaks[lastIndex].end,
                            BreakReason.HighDensityJumpingCourse
                        ))
                    }
                    firstIndex = -1
                    lastIndex = -1
                }
            }
        }

        if (firstIndex != -1) {
            val densityCount = lastIndex - firstIndex + 1
            if (densityCount > minDensityCount) {
//println("l hd: ${breaks[firstIndex].start.time} - ${breaks[lastIndex].end.time}")
                workspace.breaks.add(GpsBreak(
                    breaks[firstIndex].start,
                    breaks[lastIndex].end,
                    BreakReason.HighDensityJumpingCourse
                ))
            }
        }
    }

    fun processSpeed(workspace: GpxWorkspace) {
        workspace.oneMeterSegments.forEach { segment ->
            segment.points.forEach { point ->
                if (point.deltaKmh .absoluteValue >= 9) {
println("speed: ${point.time}: ${point.deltaKmh} (${point.smoothedKmh}, ${point.deltaCourse}) -- ${point.transportationTypes}")
                    workspace.breaks.add(
                        GpsBreak(
                            point,
                            point,
                            BreakReason.JumpingSpeed,
                            point.calculatedSeconds,
                            point.calculatedMeters
                        )
                    )
                }
//if (point.time!! >= "2018-06-25T13:43:13Z" && point.time!! <= "2018-06-25T13:43:19Z") {
//    println("$point")
//}
            }
        }
    }
}
