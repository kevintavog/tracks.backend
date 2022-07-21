package tracks.indexer.processors

import tracks.core.models.*
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.LowQualityRun
import tracks.indexer.models.LowQualityType

object GpsAnalyzer {
    fun process(gps: Gps): GpxWorkspace {
        val workspace = GpxWorkspace.create(gps)

        SpeedDetector.processLowSpeed(workspace)
        SpeedDetector.trimLowSpeedRuns(workspace)

        SpeedChangeDetector.process(workspace)
        MissingDataDetector.process(workspace)

        GapDetector.process(workspace)
        SpeedChangeDetector.removeHighAcceleration(workspace)
        CourseChangeDetector.processVectors(workspace)

        val waypoints = mutableListOf<GpsWaypoint>()
        waypoints.addAll(gps.waypoints)
        val lqrToRemove = mutableListOf<LowQualityRun>()
        workspace.lowQualityRuns
            .filter { it.type == LowQualityType.LOW_MOVEMENT || it.type == LowQualityType.NO_MOVEMENT }
            .filter { it.duration() >= AnalyzerSettings.minSecondsLowMovementForStop }
            .forEach {
                lqrToRemove.add(it)
                val midLat = (it.start.lat!! + it.end.lat!!) / 2
                val midLon = (it.start.lon!! + it.end.lon!!) / 2
                val wpt = GpsWaypoint(midLat, midLon, time = it.start.time, name = "wpt")
                wpt.rangicDescription = it.description
                wpt.cmt = it.description
                wpt.rangicStart = it.start
                wpt.rangicEnd = it.end
                val distanceKm = it.start.distanceKm(it.end)
                wpt.rangicDistanceKm = distanceKm
                wpt.rangicStopType = it.type.toString()
                val seconds = it.start.durationSeconds(it.end)
                wpt.rangicDurationSeconds = seconds
                wpt.rangicSpeedKmh = distanceKm / (60.0 * 60.0 * seconds)

                waypoints.add(wpt)
            }
        waypoints.sortBy { it.time }
        workspace.lowQualityRuns.removeAll(lqrToRemove)

        // A last check for splitting segments - if there's a transition to or from foot, split the segment
        workspace.currentSegments = SpeedChangeDetector.splitSegmentsByFoot(workspace.currentSegments)

        // Honor the track boundaries in the original file
        val tracks = mutableListOf<GpsTrack>()
        val segments = mutableListOf<GpsTrackSegment>()
        var trackIndex = 0
        workspace.currentSegments.forEach { seg ->
            seg.points.firstOrNull()?.let { first ->
                first.time?.let { firstTime ->
                    if (firstTime >= workspace.tracksStartEnd[trackIndex].end) {
                        val filtered = segments.filter { isGoodSegment(it) }
                        if (filtered.isNotEmpty()) {
                            tracks.add(GpsTrack("", "", filtered.map { it } ))
                        }
                        segments.clear()
                        trackIndex += 1
                    }
                }
            }
            segments.add(seg)
        }
        val filtered = segments.filter { isGoodSegment(it) }
        if (filtered.isNotEmpty()) {
            tracks.add(GpsTrack("", "", filtered.map { it }))
        }

        workspace.processedGps = Gps(
            gps.time,
            gps.bounds,
            tracks,
            waypoints
        )

        workspace.processedGps?.let {
            MovementCategorizer.process(it)
        }

        NameResolver.process(workspace)
        workspace.processedGps?.let {
            it.countryNames = workspace.countries
            it.stateNames = workspace.states
            it.cityNames = workspace.cities
            it.sites = workspace.sites
        }

//        println(
//            "Input has ${gps.tracks.size} tracks and ${
//                gps.tracks.map { it.segments.size }.reduce { sum, count -> sum + count }
//            } segments and ${gps.waypoints.size} waypoints"
//        )
//        println(
//            "Output has ${workspace.processedGps!!.tracks.size} tracks and ${
//                workspace.processedGps!!.tracks.map { it.segments.size }.reduce { sum, count -> sum + count }
//            } segments and ${waypoints.size} waypoints")

        return workspace
    }

    private fun isGoodSegment(segment: GpsTrackSegment): Boolean {
        return segment.points.isNotEmpty() && segment.seconds > 5
    }

    private fun splitSegmentsByTransportation(workspace: GpxWorkspace, segmentList: List<GpsTrackSegment>): List<GpsTrackSegment> {
        val slowList = workspace.lowQualityRuns
            .filter { it.type == LowQualityType.LOW_MOVEMENT || it.type == LowQualityType.NO_MOVEMENT }
            .filter { it.duration() < AnalyzerSettings.minSecondsLowMovementForStop }
            .sortedBy { it.start.time }

segmentList.forEach { seg ->
    println("Original: ${seg.points.first().timeOnly()} - ${seg.points.last().timeOnly()}")
}
        val newSegments = mutableListOf<GpsTrackSegment>()
        segmentList.forEach { seg ->
            val slowBits = slowList.filter { seg.points.first().compareTime(it.start) < 0 && seg.points.last().compareTime(it.end) > 0 }
            val splitPoints = mutableListOf<List<GpsTrackPoint>>()
            val allPoints = seg.points.map { it }.toMutableList()
            slowBits.forEach { lqr ->
//                val points = allPoints.filter { pt -> pt.compareTime(lqr.start) >= 0 && pt.compareTime(lqr.end) <= 0 }
                val points = allPoints.filter { pt -> pt.compareTime(lqr.end) <= 0 }
                if (points.isNotEmpty()) {
                    splitPoints.add(points.map { it })
                    allPoints.removeAll { pt -> pt.compareTime(lqr.end) <= 0 }
                }
            }

            if (splitPoints.size > 0) {
                if (allPoints.isNotEmpty()) {
                    splitPoints.add(allPoints.map { it })
                    allPoints.clear()
                }

println("Segment ${seg.points.first().timeOnly()} - ${seg.points.last().timeOnly()} split into ${splitPoints.size}")
                var lastOnFoot = transportationTypes(splitPoints.first()).first().mode == TransportationMode.foot
                val outstandingPoints = mutableListOf<GpsTrackPoint>()
                outstandingPoints.addAll(splitPoints.first())
//println("  => first split segment ${outstandingPoints.first().timeOnly()} - ${outstandingPoints.last().timeOnly()} => $lastTransportationMode")
                for (idx in 1 until splitPoints.size) {
                    val pointList = splitPoints[idx]
                    val tt = transportationTypes(pointList)
                    val splitOnFoot = tt.first().mode == TransportationMode.foot
                    if (splitOnFoot != lastOnFoot) {
println(" ==> mode changed from $lastOnFoot to $splitOnFoot, added ${outstandingPoints.first().timeOnly()} - ${outstandingPoints.last().timeOnly()}")
                        newSegments.add(GpsTrackSegment(outstandingPoints.map { it }))
                        outstandingPoints.clear()
                        lastOnFoot = splitOnFoot
                    }
                    outstandingPoints.addAll(pointList)
//println("  => split segment ${pointList.first().timeOnly()} - ${pointList.last().timeOnly()} => $tt")
                }
                if (outstandingPoints.isNotEmpty()) {
                    println("last check, added ${outstandingPoints.first().timeOnly()} - ${outstandingPoints.last().timeOnly()}")
                    newSegments.add(GpsTrackSegment(outstandingPoints.map { it }))
                }
            } else {
                newSegments.add(seg)
            }
        }
println("From ${segmentList.size} to ${newSegments.size}")
newSegments.forEach { seg ->
    println("New: ${seg.points.first().timeOnly()} - ${seg.points.last().timeOnly()}")
}

        return newSegments
    }

    fun findSlowdowns(points: List<GpsTrackPoint>) {
        if (points.isEmpty()) { return }
        points.forEach { pt ->
            // TODO: Make this number, '2.0', a setting
            if (pt.calculatedKmh < 2.0) {
                println("${pt.timeOnly()}: ${pt.calculatedKmh} over ${pt.calculatedSeconds} seconds")
            }
        }
    }

    fun transportationTypes(points: List<GpsTrackPoint>): List<TransportationType> {
        val tt = mutableMapOf<TransportationMode, Double>().withDefault { 0.0 }
        points.forEach { point ->
            point.transportationTypes.forEach { pttt ->
                tt[pttt.mode] = tt.getValue(pttt.mode) + pttt.probability
            }
        }
        return tt
            .map { kv -> TransportationType(kv.value / points.size, kv.key) }
            .sortedByDescending { it.probability }
    }
}
