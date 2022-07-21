package tracks.indexer.utils

import tracks.core.models.durationSeconds
import tracks.indexer.models.*

/*

    Combine breaks
        Always combine like types if separated by less than 10 seconds

        LARGE_GAP (> ~20 seconds) is missing data.
            If preceded or followed by jumping points, extend the gap to include the jumping points
        SMALL_GAP

 */

object BreakPointConsolidation {
    fun processCourse(workspace: GpxWorkspace) {
        if (workspace.breaks.isEmpty()) { return }

        // First pass: combine like types
        val joined = mutableListOf<GpsBreak>()
        var prevBrk: GpsBreak? = null
        workspace.breaks
            .sortedBy { it.start.time }
            .forEach { brk ->
            prevBrk?.let {
                val secondsBetween = it.end.durationSeconds(brk.start)
                if (it.reason == brk.reason && secondsBetween < maxTimeBetween(brk.reason)) {
                    it.end = brk.end
                    it.totalDistanceMeters = null
                    it.totalDurationSeconds = null
                } else {
//println("brk ${it.start.time} ${it.end.time} ${brk.reason} ($it)")
                    joined.add(it)
                    prevBrk = brk
                }
            } ?: run {
                prevBrk = brk
            }
        }
        if (prevBrk != null && prevBrk != joined.lastOrNull()) {
//println("last brk ${prevBrk?.start?.time} ${prevBrk?.end?.time} ${prevBrk?.reason}")
            joined.add(prevBrk!!)
        }

        // Consolidate overlapping breaks of different types
        // Some break types have priority

        val overlapped = mutableListOf<GpsBreak>()
        var prev = joined[0]
        for (idx in joined.indices) {
            if (idx == 0) { continue }
            val current = joined[idx]
            if (prev.end.time!! >= current.start.time!!) {
                if (current.reason == BreakReason.HighDensityJumpingCourse &&
                        prev.reason != BreakReason.HighDensityJumpingCourse) {
                    current.start = prev.start
                    prev = current
                }
//println("OVERLAP: ${prev.end.time} (${prev.reason}) && ${current.start.time} (${current.reason})")
            } else {
                overlapped.add(prev)
                prev = current
            }
        }

        if (overlapped.isNotEmpty() && overlapped.last() != prev) {
            overlapped.add(prev)
        }

//        for (idx in joined.indices) {
//            val current = joined[idx]
//            if (current.reason == BreakReason.LargeGap) {
//
//            }
//        }

//println("JOINED")
//joined.forEach { brk ->
//    println("j: ${brk.start.time} - ${brk.end.time}, ${brk.reason}")
//}
//println("Joined - went from ${workspace.breaks.size} to ${joined.size} breaks")
//
//println("OVERLAPPED")
//overlapped.forEach { brk ->
//    println("ol: ${brk.start.time} - ${brk.end.time}, ${brk.reason}")
//}
//
//println("Overlapped - went from ${joined.size} to ${overlapped.size} breaks")

//workspace.breaks = joined

        overlapped.forEach { brk ->
            when(brk.reason) {
                BreakReason.HighDensityJumpingCourse -> {
                    workspace.stops.add(GpsStop(brk.start, brk.end, StopReason.Noise, "Noisy data"))
                }
                BreakReason.LowMovement -> {
                    if (brk.durationSeconds >= 5 * 60) {
                        workspace.stops.add(GpsStop(brk.start, brk.end, StopReason.Dwell, "Low movement"))
                    }
                }
                else -> { }
            }
        }
        workspace.breaks = overlapped
    }

    private fun maxTimeBetween(reason: BreakReason): Int {
        return when(reason) {
            BreakReason.JumpingCourse, BreakReason.JumpingSpeed -> { 2 }
            else -> { 11 }
        }
    }
}
