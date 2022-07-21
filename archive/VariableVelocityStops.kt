package tracks.indexer.processors

import tracks.core.models.GpsTrackPoint
import tracks.core.models.durationSeconds
import tracks.indexer.models.GpsStop
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.StopReason
import kotlin.math.absoluteValue

object VariableVelocityStops {
    fun process(workspace: GpxWorkspace) {
        workspace.currentSegments.forEach { segment ->
println("Examining velocity for ${segment.points.first().time} - ${segment.points.last().time}")
            val newStops = mutableListOf<GpsStop>()
            var countBadSpeed = 0
            var firstIndex = -1
            var lastIndex = -1
            for (idx in 1 until segment.points.size) {
                val prev = segment.points[idx - 1]
                val current = segment.points[idx]
                val speedDelta = (current.calculatedKmh - prev.calculatedKmh).absoluteValue

                if (speedDelta > 4.0) {
                    if (firstIndex == -1) {
                        firstIndex = idx
                    }
                    countBadSpeed += 1
                    lastIndex = idx
                } else if (firstIndex != -1) {
                    if (possibleStop(segment.points, firstIndex, lastIndex, countBadSpeed, newStops)) {
                        firstIndex = -1
                        lastIndex = -1
                        countBadSpeed = 0
                    }
                }
            }

            if (firstIndex != -1) {
                possibleStop(segment.points, firstIndex, segment.points.size - 1, countBadSpeed, newStops)
            }

            workspace.stops.addAll(newStops)
        }
    }

    private fun possibleStop(points: List<GpsTrackPoint>, firstIndex: Int, lastIndex: Int,
                             countBadSpeed: Int,
                             newStops: MutableList<GpsStop>): Boolean {
        val first = points[firstIndex]
        val last = points[lastIndex]
        val numberPoints = lastIndex - firstIndex + 1
        val durationSeconds = first.durationSeconds(last)
        if (durationSeconds >= 20.0 && numberPoints >= 5) {
            val speedRatio = (100.0 * countBadSpeed.toDouble() / numberPoints.toDouble()).toInt()

println(
    "VS: ${first.time} - ${last.time}, "
            + "$numberPoints points (${durationSeconds.toInt()} secs), "
            + "$countBadSpeed speed; $speedRatio%"
)

            if (speedRatio >= 40) {
                newStops.add(GpsStop(first, last, StopReason.Noise, "Noisy data - variable speed"))
            }

            return true
        }
        return false
    }
}
