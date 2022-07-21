package tracks.indexer.processors

import tracks.core.models.GpsTrackPoint
import tracks.core.models.GpsTrackSegment
import tracks.indexer.models.GpxWorkspace
import java.lang.Math.min
import kotlin.math.absoluteValue
import kotlin.time.seconds

// JumpingPointsFilter ?
object UnexpectedPhysicsFilter {
    fun process(workspace: GpxWorkspace) {
        val newSegments = mutableListOf<GpsTrackSegment>()
        workspace.currentSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countSinceLast = 0
            val removeIndices = mutableListOf<Pair<Int, Int>>()

            for (idx in 1 until segment.points.size) {
                val point = segment.points[idx]
                val speedDiff = (point.speed!! - point.calculatedMps).absoluteValue
                if (point.speed!! < 1.9 && speedDiff > 2.7) {
println("${point.time}: ${point.speed} & ${point.calculatedMps} [$speedDiff]")
                    if (firstIndex == -1) {
                        // If it's the second point, the first one with proper
                        // data, include the first point because it's possibly after
                        // a gap and likely has the same poor data
                        firstIndex = if (idx == 1) 0 else idx
                    }

                    lastIndex = idx
                    countSinceLast = 0
                } else if (firstIndex != -1) {
                    countSinceLast += 1
                    if (countSinceLast > 5) {
                        if (lastIndex - firstIndex > 4) {
                            removeIndices.add(Pair(firstIndex, lastIndex))
                        }
                        firstIndex = -1
                        lastIndex = -1
                    }
                }
            }

            var removeIndex = 0
            var pointIndex = 0

            while (removeIndex < removeIndices.size) {
                // Create a segment from for any points prior to the start of the removed points
                val removePair = removeIndices[removeIndex]
                val startIndex = pointIndex
                val endIndex = removePair.first - 1
                if (startIndex < endIndex) {
                    newSegments.add(GpsTrackSegment(segment.points.subList(startIndex, endIndex)))
                }
                pointIndex = removePair.second + 1
                removeIndex += 1
            }
        }

        workspace.currentSegments = newSegments
    }

    fun processByDeltas(workspace: GpxWorkspace) {
        val newSegments = mutableListOf<GpsTrackSegment>()
        workspace.currentSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countSinceLast = 0
            val removeIndices = mutableListOf<Pair<Int, Int>>()

            for (idx in 1 until segment.points.size) {
                val point = segment.points[idx]
                if (point.unexpectedSpeed || point.unexpectedCourse) {
                    if (firstIndex == -1) {
                        // If it's the second point, the first one with proper
                        // data, include the first point because it's possibly after
                        // a gap and likely has the same poor data
                        firstIndex = if (idx == 1) 0 else idx
                    }
                    lastIndex = idx
                    countSinceLast = 0
                } else if (firstIndex != -1) {
                    countSinceLast += 1
                    if (countSinceLast > 10) {
                        removeIndices.add(Pair(firstIndex, lastIndex))
                        firstIndex = -1
                        lastIndex = -1
                    }
                }
            }

            if (firstIndex != -1 && firstIndex != lastIndex) {
                removeIndices.add(Pair(firstIndex, lastIndex))
            }

            // Remove unexpected points
            val newPoints = mutableListOf<GpsTrackPoint>()
//            val newPoints = segment.points.toMutableList()
var countRemoved = 0

            var removeIndex = 0
            var pointIndex = 0
            while (removeIndex < removeIndices.size && pointIndex < segment.points.size) {
                val removePair = removeIndices[removeIndex]
                if (pointIndex <= removePair.first || pointIndex >= removePair.second) {
                    newPoints.add(segment.points[pointIndex])
                } else {
                    countRemoved += 1
                }
                pointIndex += 1

                if (pointIndex >= removePair.second) {
                    removeIndex += 1
                }
            }

//            removeIndices.reversed().forEach {
//                countRemoved += it.second - it.first
////println("Removing ${it.first} - ${it.second} of ${newPoints.size}")
//                for (idx in it.second-1..it.first) {
//                    newPoints.removeAt(idx)
//                }
//            }
println("${newPoints.first().time}: Started with ${segment.points.size}, removed $countRemoved, ended with ${newPoints.size}")
            newSegments.addAll(GapDetector.process(newPoints, workspace.breaks))
        }

        workspace.currentSegments = newSegments
    }
}
