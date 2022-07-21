package tracks.indexer.processors

import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.LowQualityRun
import tracks.indexer.models.LowQualityType

object MissingDataDetector {
    fun process(workspace: GpxWorkspace) {
        // Find any time gap caused by missing data for a period of time.
        workspace.allSegments.forEach { segment ->
            for (idx in 0 until segment.points.size-1) {
                val current = segment.points[idx]
                val next = segment.points[idx + 1]
                if (current.calculatedSeconds > 3) { // AnalyzerSettings.maxSecondsBetweenPoints) {
                    val distance = "${current.calculatedMeters.toInt()} meters"
                    workspace.lowQualityRuns.add(LowQualityRun(
                        current,
                        next,
                        LowQualityType.MISSING_DATA,
                    "Missing data for ${current.calculatedSeconds} seconds and $distance"))
                }
            }
        }
    }
}
