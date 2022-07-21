package tracks.indexer.processors

import tracks.core.models.GpsTrackPoint
import tracks.core.models.GpsTrackSegment
import tracks.core.models.dateTime
import tracks.core.models.durationSeconds
import tracks.indexer.models.GpsStop
import tracks.indexer.models.GpxWorkspace
import java.time.ZonedDateTime

object ConsolidateStops {
    fun process(workspace: GpxWorkspace) {
//workspace.stops.forEach {
//    println("Stop: ${it.start.time} - ${it.end.time} - ${it.reason}")
//}
        val combined = process(workspace.stops.sortedBy { it.start.time }.toMutableList() )
        workspace.stops = combined //.filter { it.start.durationSeconds(it.end) > 120.0 }.toMutableList()
//println("Combined")
//combined.forEach {
//    println("Stop: ${it.start.time} - ${it.end.time} - ${it.reason}")
//}
        removePointsInStop(workspace)
    }

    fun process(rawStops: MutableList<GpsStop>): MutableList<GpsStop> {
        if (rawStops.size < 1) {
            return rawStops
        }

        val stops = rawStops.sortedBy { it.start.time }

        val combined = mutableListOf<GpsStop>()
        var recentStop = stops.first()
        var start = recentStop.start
        var end = recentStop.end
        stops.forEach {
            val stopSeconds = start.durationSeconds(end)
            val secondsBetween = end.durationSeconds(it.start)
            var minSecondsBetween = AnalyzerSettings.zeroSpeedMinSeconds

//            var minSecondsBetween = AnalyzerSettings.minSecondsBetweenStops
//
//            // Short stops should only be combined if the nearby stops are VERY close
//            if (stopSeconds < AnalyzerSettings.minSecondsBetweenStops) {
//                minSecondsBetween = AnalyzerSettings.zeroSpeedMinSeconds
//            }

            if (it.start.time!! > end.time!! && secondsBetween > minSecondsBetween) {
                combined.add(GpsStop(start, end, recentStop.reason, it.description))
                start = it.start
                end = it.end
                recentStop = it
            } else {
                if (it.end.time!! > end.time!!) {
                    end.time = it.end.time
                }
            }
        }

        if (combined.size == 0 || start.time!! > combined.last().start.time!!) {
            combined.add(GpsStop(start, end, recentStop.reason, recentStop.description))
        }

        return combined
    }

    private fun removePointsInStop(workspace: GpxWorkspace) {
        val newSegments = mutableListOf<GpsTrackSegment>()
        workspace.currentSegments.forEach { segment ->
            val newPoints = mutableListOf<GpsTrackPoint>()
            segment.points.forEach { point ->
                if (!isInStop(workspace, point.dateTime())) {
                    newPoints.add(point)
                } else {
                    if (newPoints.isNotEmpty()) {
                        newSegments.add(GpsTrackSegment(newPoints.toList()))
                        newPoints.clear()
                    }
                }
            }
            newSegments.add(GpsTrackSegment(newPoints))
        }
        workspace.currentSegments = newSegments.filter { it.points.size > 3 }
    }

    private fun isInStop(workspace: GpxWorkspace, time: ZonedDateTime): Boolean {
        workspace.stops.forEach {
            if (time >= it.start.dateTime() && time <= it.end.dateTime()) {
                return true
            }
        }
        return false
    }
}
