package tracks.indexer.processors

import tracks.core.models.*
import tracks.indexer.models.BreakReason
import tracks.indexer.models.GpsBreak
import tracks.indexer.models.GpxWorkspace

object LowSpeed {
    private const val RollingWindowSize = 10

    fun processSequence(workspace: GpxWorkspace) {
        workspace.currentSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countAboveThreshold = 0
            var meters = 0.0

            for (idx in 1 until segment.points.size) {
                val current = segment.points[idx]

                // Is there an active sequence?
                if (firstIndex == -1) {
                    if (current.calculatedMps < AnalyzerSettings.movementMinSpeedMetersSecond) {
                        if (idx == 1) {
                            firstIndex = 0
                            meters = current.calculatedMeters
                        } else {
                            firstIndex = idx
                            meters = 0.0
                        }
                        countAboveThreshold = 0
                    }
                } else {
                    // Is this active sequence still below the threshold?
                    meters += current.calculatedMeters
                    val seconds = segment.points[firstIndex].durationSeconds(current)
                    val speed = meters / seconds
                    if (speed < AnalyzerSettings.movementMinSpeedMetersSecond) {
                        lastIndex = idx
                    } else {
                        ++countAboveThreshold
                        if (countAboveThreshold > 5) {
                            if (lastIndex - firstIndex > 2) {
                                workspace.breaks.add(
                                    GpsBreak(segment.points[firstIndex], segment.points[lastIndex], BreakReason.LowMovement)
                                )
                            }

                            firstIndex = -1
                            lastIndex = -1
                        }
                    }
                }
            }
        }
    }

    fun process(workspace: GpxWorkspace) {
        workspace.currentSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countAboveZero = 0

            for (idx in 1 until segment.points.size) {
                val current = segment.points[idx]
                if (current.calculatedMps < AnalyzerSettings.movementMinSpeedMetersSecond) {
                    if (firstIndex == -1) {
                        firstIndex = idx - 1
                        countAboveZero = 0
                    }
                    lastIndex = idx
                } else if (firstIndex != -1) {
                    countAboveZero += 1
                    if (countAboveZero > 0) {
                        if (lastIndex - firstIndex > 3) {
                            val duration = segment.points[firstIndex].durationSeconds(segment.points[lastIndex])
                            val meters: Double = segment.points
                                .subList(firstIndex + 1, lastIndex)
                                .map { it.calculatedMeters }
                                .reduce { acc, it -> acc + it }
                            workspace.breaks.add(
                                GpsBreak(
                                    segment.points[firstIndex],
                                    segment.points[lastIndex],
                                    BreakReason.LowMovement,
                                    duration,
                                    meters
                                )
                            )
                        }
                        firstIndex = -1
                        lastIndex = -1
                    }
                }
            }
        }
    }

    fun processBy10(workspace: GpxWorkspace) {
        // For every N second window, if it's too slow, add a break
        workspace.currentSegments.forEach { segment ->
            var idx = 0
            while (idx < segment.points.size) {
                val endIdx = (idx + RollingWindowSize).coerceAtMost(segment.points.size - 1)
                if (calculateSpeedMps(segment.points, idx, endIdx) < AnalyzerSettings.movementMinSpeedMetersSecond) {
                    workspace.breaks.add(GpsBreak(segment.points[idx], segment.points[endIdx], BreakReason.LowMovement))
                }
                idx += RollingWindowSize
//                idx += 1
            }
        }
    }

    fun calculateSpeedMps(points: List<GpsTrackPoint>, startIndex: Int, endIndex: Int): Double {
        var meters = 0.0
        for (idx in startIndex until endIndex) {
            meters += points[startIndex].calculatedMeters
        }
        return meters / points[startIndex].durationSeconds(points[endIndex])
    }

    fun processByPoint(workspace: GpxWorkspace) {
        val updatedSegments = mutableListOf<GpsTrackSegment>()
        workspace.currentSegments.forEach { segment ->
            var firstIndex = -1
            var lastIndex = -1
            var countZeroSpeed = 0
            var countFromLastZeroSpeed = 0
            var indexSegmentStart = 0

            for (idx in segment.points.indices) {
                val point = segment.points[idx]

                val isZero = point.speed?.let {
                    it.isNaN() || it <= 0.179
                } ?: run {
                    true
                }

                if (isZero) {
                    if (firstIndex == -1) {
                        firstIndex = idx
                        countZeroSpeed = 0
                    }
                    lastIndex = idx
                    countZeroSpeed += 1
                    countFromLastZeroSpeed = 0
                } else if (firstIndex != -1) {
                    if (countFromLastZeroSpeed < 20) {
                        countFromLastZeroSpeed += 1
                    } else {
                        if (possibleSlow(segment.points, firstIndex, lastIndex, countZeroSpeed, indexSegmentStart, updatedSegments)) {
                            indexSegmentStart = lastIndex + 1
                        }
                        firstIndex = -1
                        lastIndex = -1
                        countZeroSpeed = 0
                        countFromLastZeroSpeed = 0
                    }
                }
            }

            if (firstIndex != -1) {
                if (possibleSlow(segment.points, firstIndex, lastIndex, countZeroSpeed, indexSegmentStart, updatedSegments)) {
                    indexSegmentStart = lastIndex + 1
                }
            }

            if (indexSegmentStart < segment.points.size) {
//                val firstGood = segment.points[indexSegmentStart]
//                val lastGood = segment.points.last()
//                val durationGood = firstGood.durationSeconds(lastGood)
//println("last GoodSpeed: ${firstGood.time} - ${lastGood.time}; $durationGood seconds")
                updatedSegments.add(GpsTrackSegment(
                    segment.points.subList(indexSegmentStart, segment.points.size - 1)))
            }
        }

        workspace.currentSegments = updatedSegments.filter { it.points.isNotEmpty() }
    }

    private fun possibleSlow(points: List<GpsTrackPoint>, firstIndex: Int, lastIndex: Int, countZeroSpeed: Int,
                             indexSegmentStart: Int, newSegments: MutableList<GpsTrackSegment>): Boolean {
        val first = points[firstIndex]
        val last = points[lastIndex]
        val durationSeconds = first.durationSeconds(last)
        val numPoints = lastIndex - firstIndex + 1
        val ratio = (100.0 * countZeroSpeed.toDouble() / numPoints.toDouble()).toInt()

        if (numPoints > 1 && ratio >= 40 && durationSeconds >= 60.0) {
            if (indexSegmentStart < firstIndex) {
//                val firstGood = points[indexSegmentStart]
//                val lastGood = points[firstIndex - 1]
//                val durationGood = firstGood.durationSeconds(lastGood)
//                println("GoodSpeed: ${firstGood.time} - ${lastGood.time}; $durationGood seconds")
                newSegments.add(GpsTrackSegment(points.subList(indexSegmentStart, firstIndex - 1)))
            }
            newSegments.add(GpsTrackSegment(points.subList(firstIndex, lastIndex)))
println("ZeroSpeed ${first.time} - ${last.time}: $countZeroSpeed of $numPoints ($ratio); $durationSeconds seconds")
            return true
        }
        return false
    }
}
