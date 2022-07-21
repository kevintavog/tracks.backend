package tracks.indexer.processors

import tracks.core.models.dateTime
import tracks.core.models.durationSeconds
import tracks.core.models.timeOnly
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.LowQualityRun

object NoisyData {
    private const val scanAheadSeconds = 2 * 60

    // Scans the low quality runs to find grouped areas likely indicating noisy data, which is converted to a stop
    fun process(workspace: GpxWorkspace) {
        // For each low quality run, scan ahead a given amount.
        val sortedRuns = workspace.lowQualityRuns.sortedBy { it.start.dateTime() }
        for (idx in sortedRuns.indices) {
            val current = sortedRuns[idx]
            val score = lookAhead(sortedRuns, idx, scanAheadSeconds)
            println("${current.start.timeOnly()}: ${current.type} ${current.description}:: $score")
        }
    }

    private fun lookAhead(runs: List<LowQualityRun>, startIndex: Int, maxSeconds: Int): Int {
        var score = 0
        for (idx in startIndex+1 until runs.size) {
           val current = runs[idx]
           val secondsFromStart = runs[startIndex].end.durationSeconds(current.start).toInt()
           if (secondsFromStart > maxSeconds) {
               break
           }
            score += 1
        }
        return score
    }
}

/*
import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpsStop
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.StopReason
import tracks.indexer.utils.FilterByDistance
import tracks.indexer.utils.PointCalculator
import kotlin.math.absoluteValue

object NoisyData {
    fun processVectors(workspace: GpxWorkspace) {
        val vectors = workspace.currentSegments
            .filter { it.quality != SegmentQuality.High }
            .map { VectorByCourse.process(PointCalculator.process(FilterByDistance.process(it.points, 0.5))) }
            .flatten()

        // Hunt for nearby vector overlaps
        for (idx in vectors.indices) {
            assignOverlapCounts(idx, vectors)
        }

        // To start a cloud
        //      1. Both counts must be >= 2
        //      2. Cloud ends with enough 1,1 counts (4?)
        //      3. A cloud must have high enough counts (a ratio of vector count)
        var firstCloudIndex = 0
        val cloudVectors = mutableListOf<GpsVector>()
        var vectorsSinceCloudEnd = 0
        for (idx in vectors.indices) {
            val current = vectors[idx]
            val score = scoreVector(current)
            if (score >= AnalyzerSettings.vectorMinimumScore) {
                if (cloudVectors.isEmpty()) {
                    firstCloudIndex = idx
                }
                cloudVectors.add(current)
                vectorsSinceCloudEnd = 0
            } else if (cloudVectors.isNotEmpty()) {
                if (vectorsSinceCloudEnd <= AnalyzerSettings.vectorMinBetweenClouds) {
                    vectorsSinceCloudEnd += 1
                } else {
                    val sequenceLength = idx - firstCloudIndex
                    val sequenceSeconds = cloudVectors.first().start.durationSeconds(cloudVectors.last().end)
                    val sequenceRatio = cloudVectors.size.toDouble() / sequenceLength.toDouble()
                    workspace.stops.add(GpsStop(
                        cloudVectors.first().start,
                        cloudVectors.last().end,
                        StopReason.Stop)
                    )

                    cloudVectors.clear()
                    firstCloudIndex = 0
                    vectorsSinceCloudEnd = 0
                }
            }
        }

        if (cloudVectors.isNotEmpty()) {
            val sequenceLength = vectors.size - firstCloudIndex
            val sequenceSeconds = cloudVectors.first().start.durationSeconds(cloudVectors.last().end)
            val sequenceRatio = cloudVectors.size.toDouble() / sequenceLength.toDouble()
            workspace.stops.add(GpsStop(
                cloudVectors.first().start,
                cloudVectors.last().end,
                StopReason.Stop)
            )
        }
    }

    private fun scoreVector(vector: GpsVector): Int {
        if (vector.backwardOverlapCount < 2 || vector.forwardOverlapCount < 2) {
            return 0
        }
        var score = 2
        score += (vector.backwardOverlapCount / 5)
        score += (vector.forwardOverlapCount / 5)
        return score
    }

    fun assignOverlapCounts(index: Int, workspace: GpxWorkspace) {
        assignOverlapCounts(index, workspace.vectors)
    }

    fun assignOverlapCounts(index: Int, vectors: List<GpsVector>) {
        val current = vectors[index]

        var checkIndex = index - 1
        while (checkIndex >= 0) {
//            val checkVector = vectors[checkIndex]
//            if (checkVector.end.durationSeconds(current.start) < AnalyzerSettings.vectorOverlapDuration) {
//                if (GeoCalculator.doVectorsOverlap(checkVector, current)) {
//                    current.backwardOverlapCount += 1
//                }
//                checkIndex -= 1
//            } else {
//                break
//            }
        }

        checkIndex = index + 1
        while (checkIndex < vectors.size) {
//            val checkVector = vectors[checkIndex]
//            if (current.end.durationSeconds(checkVector.start) < AnalyzerSettings.vectorOverlapDuration) {
//                if (GeoCalculator.doVectorsOverlap(checkVector, current)) {
//                    current.forwardOverlapCount += 1
//                }
//                checkIndex += 1
//            } else {
//                break
//            }
        }

//        println("${current.start.time}; $index -> -${current.backwardIntersectCount} && +${current.forwardIntersectCount}")
    }

    fun process(points: List<GpsTrackPoint>) {
        // 1. Detect frequent big changes in bearing
        //      a. Get ~1 meter points, check bearing
        // 2. Detect frequent changes in speed
        // 3. Consider elevation changes
        // 4. Consider density of points

//        val filteredByDistance = PointCalculator.process(FilterByDistance.process(points, 0.5))
        val filteredByDistance = points
//        SmoothSpeed.process(filteredByDistance)
        for (idx in filteredByDistance.indices) {
            val minIndex = 1 + 0.coerceAtLeast(idx - AnalyzerSettings.noisyBearingDelta)
            val maxIndex = (filteredByDistance.size - 1).coerceAtMost(idx + AnalyzerSettings.noisyBearingDelta)

            val underReview = filteredByDistance[idx]

            var abruptBearingCount = 0
            var unnaturalBearingCount = 0
            var abruptSpeedCount = 0
            var aboveWalkingCount = 0
            var highAccelerationCount = 0
            var bigElevationChangeCount = 0

            for (windex in minIndex..maxIndex) {
                val prev = filteredByDistance[windex - 1]
                val current = filteredByDistance[windex]
                val deltaSeconds = prev.durationSeconds(current)
                val delta = GeoCalculator.bearingDelta(prev.calculatedCourse, current.calculatedCourse)
                if (delta >= AnalyzerSettings.noisyAbruptBearing) {
                    abruptBearingCount += 1
                }
                if (delta >= 90) {
                    unnaturalBearingCount += 1
                }
                if (((prev.calculatedKmh - current.calculatedKmh).absoluteValue / deltaSeconds) >= 1.0) {
                    highAccelerationCount += 1
                }
                if ((prev.calculatedKmh - current.calculatedKmh).absoluteValue >= 4.0) {
                    abruptSpeedCount += 1
                }
                if (current.calculatedKmh > 10) {
                    aboveWalkingCount += 1
                }
                if ((underReview.ele!! - current.ele!!).absoluteValue >= 7.0) {
                    bigElevationChangeCount += 1
                }
            }

            println("${filteredByDistance[idx].time} -> be: $abruptBearingCount ($unnaturalBearingCount), de: ${underReview.countInRadius} "
                    + "sp: $abruptSpeedCount, "
                    + "wa: $aboveWalkingCount, ac: $highAccelerationCount, el: $bigElevationChangeCount")
        }
    }
}

 */