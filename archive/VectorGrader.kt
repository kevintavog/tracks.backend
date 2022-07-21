package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import kotlin.math.absoluteValue

object VectorGrader {
    fun process(vectors: List<GpsVector>) {
        // For every vector with a big course change, scan forward to determine if the run leaves the area
        for (idx in 1 until vectors.size) {
            val prev = vectors[idx-1]
            val current = vectors[idx]
            val bearingDiff = GeoCalculator.bearingDelta(prev.bearing, current.bearing).absoluteValue
            if (bearingDiff >= AnalyzerSettings.sharpTurnThreshold) {
println("${current.start.timeOnly()}: ${prev.bearing} ${current.bearing} -- $bearingDiff")
                scan(idx, vectors)
            }
        }
    }

    private fun scan(start: Int, vectors: List<GpsVector>) {
        var startVector = vectors[start]
        var prevVector = vectors[start]
        val endScanTime = prevVector.end.dateTime().plusMinutes(5)
        var index = start+1
        while (index < vectors.size) {
//        while (index < vectors.size && index < start+2) {
            val current = vectors[index]
            if (current.start.dateTime() > endScanTime) {
                break
            }
            // Determine if this vector leads back to the starting vector
            //  Is it getting closer than the previous vector?
            //  Is it very close at all?
            //  Does it parallel the starting vector?

            // When we encounter another big turn, extend the time period we're scanning
            val deltaBearing = GeoCalculator.bearingDelta(prevVector.bearing, current.bearing).absoluteValue
            if (deltaBearing >= AnalyzerSettings.sharpTurnThreshold) {
                println("  delta ${current.start.timeOnly()}: $deltaBearing (${prevVector.bearing} & ${current.bearing})")
            }
            prevVector = current
            index += 1
        }
    }
}