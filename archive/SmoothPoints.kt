package tracks.indexer.processors

import tracks.core.models.*
import kotlin.math.pow

object SmoothPoints {
    // A weighted average to smooth out the coordinates, including elevation.
    fun process(points: List<GpsTrackPoint>) {
        val endIndex = 30
        for (idx in 1 until points.size) {
            val current = points[idx]
            val minIndex = Math.max(0, idx - AnalyzerSettings.smoothPointsDelta)
            val maxIndex = Math.min(points.size - 1, idx + AnalyzerSettings.smoothPointsDelta)

            var weightSum = 0.0
            var latWeightSum = 0.0
            var lonWeightSum = 0.0
            var eleWeightSum = 0.0

            for (windex in minIndex..maxIndex) {
                val wPoint = points[windex]
                val seconds = current.durationSeconds(wPoint)
// Math.exp(-Math.pow((currentCoord.getTimestamp().getTimeInMillis()-coord.getTimestamp().getTimeInMillis())/(double)1000, 2)/(2*Math.pow(this.kernelWidth/(double)2, 2)));
// Math.exp(-Math.pow(seconds, 2)/(2*Math.pow(this.kernelWidth/(double)2, 2)));
                val weight = -seconds.pow(2.0) / (2 * (AnalyzerSettings.gaussKernelWidth / 2.0).pow(2.0))

                latWeightSum += weight * wPoint.lat!!
                lonWeightSum += weight * wPoint.lon!!
                eleWeightSum += weight * wPoint.ele!!
                weightSum += weight
            }

            val smoothedLat = latWeightSum / weightSum
            val smoothedLon = lonWeightSum / weightSum
            val smoothedElevation = eleWeightSum / weightSum

            if (idx == endIndex) {
                println("Check for $idx from $minIndex to $maxIndex")
                println(" -> $current")
                println(" -> $smoothedLat, $smoothedLon @$smoothedElevation ($weightSum)")
            }

            current.lat = smoothedLat
            current.lon = smoothedLon
            current.ele = smoothedElevation
        }
    }
}
