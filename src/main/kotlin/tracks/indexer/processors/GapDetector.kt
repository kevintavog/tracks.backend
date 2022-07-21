package tracks.indexer.processors

import tracks.core.models.GpsTrackPoint
import tracks.core.models.GpsTrackSegment
import tracks.core.models.timeOnly
import tracks.indexer.models.BreakReason
import tracks.indexer.models.GpsBreak
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.utils.PointCalculator

object GapDetector {
    fun process(workspace: GpxWorkspace) {
        // Find any large time gap caused by missing data for a period of time. Split
        // segments to ease later processing.
        val newSegments = mutableListOf<GpsTrackSegment>()
        workspace.allSegments.forEach { segment ->
            newSegments.addAll(process(segment.points, workspace.breaks))
        }
        workspace.currentSegments = newSegments
        // Now find small time-gaps; these will add a break, but won't split the segment
        workspace.currentSegments.forEach {
            detectSmallGaps(it, workspace.breaks)
        }
    }

    fun process(points: List<GpsTrackPoint>, breaks: MutableList<GpsBreak>): List<GpsTrackSegment> {
        // Find any time gap caused by missing data for a period of time. Split
        // segments to ease later processing.
        val newSegments = mutableListOf<GpsTrackSegment>()

        val accumulated = mutableListOf<GpsTrackPoint>()
//        points.firstOrNull()?.let {
//            accumulated.add(it)
//        }

        for (current in points) {
            accumulated.add(current)
            if (current.calculatedSeconds > AnalyzerSettings.maxSecondsBetweenPoints) {
                if (accumulated.isNotEmpty()) {
                    breaks.add(GpsBreak(accumulated.last(), current, BreakReason.LargeGap))
//println("Splitting due to gap: ${accumulated.first().timeOnly()} - ${accumulated.last().timeOnly()} (@${current.timeOnly()} - ${current.calculatedSeconds})")
                    newSegments.add(GpsTrackSegment(PointCalculator.process(accumulated.map { it })))
                    accumulated.clear()
                }
            }
        }

        if (accumulated.isNotEmpty()) {
//println("Adding last bit of segment: ${accumulated.first().timeOnly()} - ${accumulated.last().timeOnly()}")
            newSegments.add(GpsTrackSegment(PointCalculator.process(accumulated)))
        }

        return newSegments
    }

    fun detectSmallGaps(segment: GpsTrackSegment, breaks: MutableList<GpsBreak>) {
        // Expect a point every second; add breaks for consecutive misses
        for (idx in 1 until segment.points.size) {
            if (segment.points[idx].calculatedSeconds > 1.0) {
//println("Adding break: @${segment.points[idx - 1].time} - ${segment.points[idx].calculatedSeconds}")
                breaks.add(GpsBreak(segment.points[idx - 1], segment.points[idx], BreakReason.SmallGap))
            }
        }
    }
}
