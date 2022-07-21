package tracks.indexer.processors

import tracks.core.models.*
import kotlin.math.pow

object SmoothSpeed {
    // A weighted average to smooth out speed
    fun process(points: List<GpsTrackPoint>) {
        for (idx in 1 until points.size) {
            val current = points[idx]
            val minIndex = 0.coerceAtLeast(idx - AnalyzerSettings.smoothPointsDelta)
            val maxIndex = (points.size - 1).coerceAtMost(idx + AnalyzerSettings.smoothPointsDelta)

            var calculatedSpeedWeightSum = 0.0
            var speedWeightSum = 0.0
            var weightSum = 0.0
            for (windex in minIndex..maxIndex) {
                val wPoint = points[windex]
                val seconds = current.durationSeconds(wPoint)
                val weight = -seconds.pow(2.0) / (2 * (AnalyzerSettings.speedGaussKernelWidth / 2.0).pow(2.0))

                calculatedSpeedWeightSum += weight * wPoint.calculatedKmh
                speedWeightSum += weight * (wPoint.speed ?: 0.0)
                weightSum += weight
            }

//            current.calculatedKmh = calculatedSpeedWeightSum / weightSum
//            current.smoothedSpeed = speedWeigthSum / weightSum
        }
    }
}
