package tracks.indexer.models

import tracks.core.models.*
import tracks.indexer.utils.PointCalculator
import tracks.indexer.processors.TrajectoryByCourse
import tracks.indexer.utils.FilterPoints
import tracks.indexer.utils.TimezoneLookup

data class GpxWorkspace(val gps: Gps) {
    companion object {
        fun create(gps: Gps): GpxWorkspace {
            val trackStartEnd = mutableListOf<StartEnd>()
            val workspace = GpxWorkspace(gps)
            workspace.allSegments = gps.tracks.map { track ->
                if (track.segments.isNotEmpty()) {
                    var start = ""
                    var end = ""
                    track.segments.forEach { segment ->
                        segment.points.forEach { point ->
                            point.time?.let { ptTime ->
                                if (start.isEmpty() || ptTime < start) {
                                    start = ptTime
                                }
                                if (end.isEmpty() || ptTime > end) {
                                    end = ptTime
                                }
                            }
                        }
                    }
                    trackStartEnd.add(StartEnd(start, end))
                }
                track.segments.map { segment ->
                    GpsTrackSegment(PointCalculator.process(segment.points))
                }
            }.flatten()

            workspace.tracksStartEnd = trackStartEnd
            return workspace
        }

        fun segmentsByDistance(segments: List<GpsTrackSegment>, meters: Double): List<GpsTrackSegment> {
            return segments.map {
                val newPoints = it.points.map { point -> point.copy() }
                GpsTrackSegment(PointCalculator.process(FilterPoints.byDistance(newPoints, meters)))
            }
        }
    }

    lateinit var allSegments: List<GpsTrackSegment>
    lateinit var tracksStartEnd: List<StartEnd>

    val oneMeterSegments: List<GpsTrackSegment> by lazy {
        segmentsByDistance(allSegments, 1.0)
     }

    val trajectories: List<GpsTrajectory> by lazy {
        TrajectoryByCourse.process(this.currentSegments)
    }

    val timezoneInfo: TimezoneInfo by lazy {
        val firstPoint = allSegments.first().points.first()
        TimezoneLookup.at(firstPoint.lat!!, firstPoint.lon!!) ?: TimezoneInfo("", "")
    }

    var currentSegments = listOf<GpsTrackSegment>()

    var stops: MutableList<GpsStop> = mutableListOf()

    var breaks: MutableList<GpsBreak> = mutableListOf()

    var lowQualityRuns: MutableList<LowQualityRun> = mutableListOf()

    var processedGps: Gps? = null

    var countries: List<String> = listOf()
    var states: List<String> = listOf()
    var cities: List<String> = listOf()
    var sites: List<SiteResponse> = listOf()
    var hierarchicalNames: List<LocationNames> = listOf()

    override fun toString(): String = "trajectories: $trajectories; stops: $stops"
}
