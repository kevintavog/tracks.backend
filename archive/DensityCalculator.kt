package tracks.indexer.processors

import tracks.core.models.*
import tracks.indexer.models.GpsStop
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.StopReason

object DensityCalculator {
    fun process(workspace: GpxWorkspace) {
        workspace.allSegments.forEach { segment ->
            assignDensity(segment.points)

            val cloudPoints = mutableListOf<GpsTrackPoint>()
            var pointsSinceCloudEnd = 0
            var firstCloudIndex = 0
            for (idx in segment.points.indices) {
                val pt = segment.points[idx]
                if (pt.countInRadius >= AnalyzerSettings.densityMinimum) {
                    if (cloudPoints.isEmpty()) {
                        firstCloudIndex = idx
                    }
                    cloudPoints.add(pt)
                    pointsSinceCloudEnd = 0
                } else if (cloudPoints.isNotEmpty()) {
                    if (pointsSinceCloudEnd <= AnalyzerSettings.densityMinPointsBetweenClouds) {
                        pointsSinceCloudEnd += 1
                    } else {
                        val sequenceLength = idx - firstCloudIndex
                        val sequenceSeconds = cloudPoints.first().durationSeconds(cloudPoints.last())
                        val sequenceRatio = cloudPoints.size.toDouble() / sequenceLength.toDouble()
                        if (sequenceLength > AnalyzerSettings.densityMinimumCloudLength &&
                            sequenceSeconds > AnalyzerSettings.densityMinimumCloudSeconds &&
                            sequenceRatio > AnalyzerSettings.densityMinimumRatio) {
                            workspace.stops.add(GpsStop(
                                cloudPoints.first(),
                                cloudPoints.last(),
                                StopReason.Stop,
                                "High density")
                            )
                        }

                        cloudPoints.clear()
                        firstCloudIndex = 0
                        pointsSinceCloudEnd = 0
                    }
                }
            }

            if (cloudPoints.isNotEmpty()) {
                val sequenceLength = segment.points.size - firstCloudIndex
                val sequenceSeconds = cloudPoints.first().durationSeconds(cloudPoints.last())
                val sequencrRatio = cloudPoints.size.toDouble() / sequenceLength.toDouble()
                if (sequenceLength > AnalyzerSettings.densityMinimumCloudLength &&
                    sequenceSeconds > AnalyzerSettings.densityMinimumCloudSeconds &&
                    sequencrRatio > AnalyzerSettings.densityMinimumRatio) {
                    workspace.stops.add(GpsStop(
                        cloudPoints.first(),
                        cloudPoints.last(),
                        StopReason.Stop,
                    "High density")
                    )
                }
            }

        }
    }

    private fun assignDensity(points: List<GpsTrackPoint>) {
        for (idx in 1 until points.size) {
            val current = points[idx]
            val minIndex = 0.coerceAtLeast(idx - AnalyzerSettings.densityPointsDelta)
            val maxIndex = (points.size - 1).coerceAtMost(idx + AnalyzerSettings.densityPointsDelta)

            var countInRadius = 0
            for (windex in minIndex..maxIndex) {
                val wPoint = points[windex]
                val distance = current.distanceKm(wPoint) * 1000.0
                if (distance <= AnalyzerSettings.densityRadiusMeters) {
                    countInRadius += 1
                }
            }

            current.countInRadius = countInRadius
        }
    }
//
//    fun distance(pt1: GpsTrackPoint, pt2: GpsTrackPoint): Double {
//        val latDiff = pt2.lat!! - pt1.lat!!
//        val lonDiff = pt2.lon!! - pt1.lon!!
//        return sqrt((latDiff * latDiff) + (lonDiff * lonDiff))
//    }
}
