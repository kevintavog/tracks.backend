package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.*
import kotlin.math.max

object DwellDetector {
    // Move these to AnalyzerSettings once they prove out
    // The amount of time ahead to find the dwell exit
    const val DWELL_SECONDS = 5 * 60
    // The minimum distance needed to travel to be considered exiting the dwell
    const val DWELL_METERS = (DWELL_SECONDS * AnalyzerSettings.movementMinSpeedMetersSecond).toInt()

    const val DWELL_MAX_DURATION_SECONDS = 60
    const val DWELL_MAX_DISTANCE_METERS = 40
    const val DWELL_MIN_NOTABLE_LOW_QUALITY = 7

    fun process(workspace: GpxWorkspace) {
        val newStops = mutableListOf<GpsStop>()
        val remainingRuns = mutableListOf<LowQualityRun>()
        val cluster = mutableListOf<LowQualityRun>()
        var prevNotableLQR: LowQualityRun? = null
        workspace.lowQualityRuns.sortedBy { it.start.time!! }.forEach { lqr ->
            prevNotableLQR?.let { prev ->
                val prevMeters = (prev.midPoint.distanceKm(lqr.start) * 1000)
                val prevSeconds = max(0, prev.end.durationSeconds(lqr.start).toInt())
                if (prevMeters < DWELL_MAX_DISTANCE_METERS || prevSeconds < DWELL_MAX_DURATION_SECONDS) {
                    if (cluster.isEmpty()) {
                        cluster.add(prev)
                    }
                    cluster.add(lqr)
//                    println("${lqr.start.timeOnly()}: ${lqr.type}, ${prevMeters.toInt()}m, ${prevSeconds}s; ${lqr.description}")
                } else {
                    if (cluster.isNotEmpty()) {
                        val notableCount = cluster.count { it.isNotable() }
                        if (notableCount >= DWELL_MIN_NOTABLE_LOW_QUALITY) {
                            if (areVectorsNoisy(workspace, cluster.first().start, cluster.last().end)) {
//println("Cluster: ${cluster.first().start.timeOnly()} - ${cluster.last().end.timeOnly()} ($notableCount)")
                                newStops.add(
                                    GpsStop(
                                        cluster.first().start,
                                        cluster.last().end,
                                        StopReason.Dwell,
                                        "Dwell"
                                    )
                                )
                            }
                        } else {
                            remainingRuns.addAll(cluster)
                        }
                        cluster.clear()
                    } else {
                        remainingRuns.add(lqr)
                    }
                }
            }

            if (lqr.isNotable()) {
                if (prevNotableLQR == null || prevNotableLQR!!.end.dateTime() < lqr.end.dateTime()) {
                    prevNotableLQR = lqr
                }
            }
        }

        workspace.lowQualityRuns = remainingRuns

        if (newStops.isNotEmpty()) {
//            applyDwell(workspace, newStops)
//            workspace.stops.addAll(newStops)
//            workspace.stops.sortBy { it.start.time }
        }
    }

    fun areVectorsNoisy(workspace: GpxWorkspace, start: GpsTrackPoint, end: GpsTrackPoint): Boolean {
        val vectors = workspace.vectors.filter { it.end.time!! >= start.time!! && it.start.time!! <= end.time!! }
        var countOverlap = 0
        for (currentIndex in vectors.indices) {
            val current = vectors[currentIndex]
            // Check for overlaps between the current vector two later. Skip the next one because the overlap
            // algorithm always includes it.
            for (laterIndex in (currentIndex+2) until vectors.size) {
                val later = vectors[laterIndex]
                if (GeoCalculator.doVectorsOverlap(current, later)) {
                    countOverlap += 1
                }
            }
        }
//        println("${start.timeOnly()}: $countOverlap overlapping vectors (of ${vectors.count()})")
        return countOverlap >= vectors.size
    }

    // For each low quality run, determine if it's part of a dwell time - if so, convert to a stop
    fun processOld(workspace: GpxWorkspace) {
        val sortedRuns = workspace.lowQualityRuns.sortedBy { it.start.dateTime() }
        var endRun: GpsTrackPoint? = null
        val stops = mutableListOf<GpsStop>()
        for (idx in sortedRuns.indices) {
            val current = sortedRuns[idx]
            if (endRun != null && endRun.timeOnly() >= current.start.timeOnly()) {
                continue
            }
            endRun = null

            findDwellExit(workspace, sortedRuns, current)?.let { end ->
                if (end.dateTime() > current.start.dateTime()) {
                    stops.add(GpsStop(current.start, end, StopReason.Dwell, "Dwell"))
                    endRun = end
                }
            }
        }

        applyDwell(workspace, stops)
    }

    private fun applyDwell(workspace: GpxWorkspace, dwellStops: List<GpsStop>) {
        // Remove all other stops that are a subset of these stops
        val newStops = mutableListOf<GpsStop>()
        workspace.stops.forEach { oldStop ->
            val overlapped = dwellStops.filter { ds ->
                (ds.start.dateTime() <= oldStop.start.dateTime() &&
                    ds.end.dateTime() >= oldStop.start.dateTime()) ||
                (ds.start.dateTime() <= oldStop.end.dateTime() &&
                    ds.end.dateTime() >= oldStop.end.dateTime())
            }
            if (overlapped.isEmpty()) {
                newStops.add(oldStop)
            }
        }
        newStops.addAll(dwellStops)

        // Remove all points that are a subset of these stops
        val newSegments = mutableListOf<GpsTrackSegment>()
        workspace.currentSegments.forEach { oldSegment ->
            val newPoints = oldSegment.points.filter { oldPt ->
                val overlapped = dwellStops.filter { ds ->
                    ds.start.dateTime() <= oldPt.dateTime() && ds.end.dateTime() >= oldPt.dateTime()
                }
                overlapped.isEmpty()
            }

            newSegments.add(
                GpsTrackSegment(newPoints)
            )
        }

        workspace.currentSegments = newSegments
    }

    private fun findDwellExit(workspace: GpxWorkspace, runlist: List<LowQualityRun>, run: LowQualityRun): GpsTrackPoint? {
        // Find the vector matching the run
        var vectorIndex = 0
        while (vectorIndex < workspace.vectors.size) {
            val vector = workspace.vectors[vectorIndex]
            if (vector.start.dateTime() >= run.start.dateTime() && vector.start.dateTime() <= run.end.dateTime()) {
                break
            }
            vectorIndex += 1
        }
        if (vectorIndex >= workspace.vectors.size) {
            println("ERROR: unable to find vector for $run")
            return run.start
        }

        val vector = workspace.vectors[vectorIndex]
        vectorIndex += 1

        val showDebug = run.start.timeOnly() == "14:27:51"
        if (showDebug) { println("DBG: Starting with vector ${vector.start.timeOnly()}") }
        while (vectorIndex < workspace.vectors.size) {
            val curVector = workspace.vectors[vectorIndex]
            val meters = (vector.end.distanceKm(curVector.end) * 1000.0).toInt()
            val seconds = vector.end.durationSeconds(curVector.end).toInt()
            if (showDebug) { println("DBG: Checking against ${curVector.start.timeOnly()}: $meters & $seconds") }
            if (meters > DWELL_METERS) {
                val countRuns = runlist.count { r ->
                    r.start.dateTime() >= run.start.dateTime() && r.end.dateTime() <= curVector.end.dateTime()
                }
                println("  Found dwell exit: ${curVector.end.timeOnly()} ($meters meters and $seconds seconds) [$countRuns]")
                if (countRuns > 4) {
                    return curVector.end as GpsTrackPoint?
                }
            }

            if (seconds > DWELL_SECONDS) {
                break
            }
            vectorIndex += 1
        }

//        println("  NO DWELL EXIT for ${run.start.timeOnly()}")
        return null
    }
}
