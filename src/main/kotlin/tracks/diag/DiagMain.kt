package tracks.diag

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.Json
import tracks.core.models.*
import tracks.core.services.GpxRepository
import tracks.core.utils.TrackConfiguration
import tracks.indexer.TrackParser
import tracks.indexer.models.DiagData
import tracks.indexer.models.DiagWorkspace
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.processors.GpsAnalyzer
import tracks.indexer.processors.TrajectoryByCourse
import tracks.indexer.utils.PointCalculator
import java.io.File
import java.nio.file.*
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.system.exitProcess

class DiagMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                Diagnostics().main(args)
                exitProcess(0)
            } catch (t: Throwable) {
                t.printStackTrace()
                exitProcess(-1)
            }
        }
    }
}

data class ProcessingSettings(
    var gpxFile: String? = null,
    var startTime: String? = null,
    var endTime: String? = null,
    var tzId: String? = null,
    var transitions: String = "",
    var runs: List<ProcessSettingsRun>? = null
) {
    companion object {
        val hourMinuteFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    fun startDate(firstDate: ZonedDateTime): ZonedDateTime {
        return convertTime(firstDate, startTime!!)
    }

    fun endDate(firstDate: ZonedDateTime): ZonedDateTime {
        return convertTime(firstDate, endTime!!)
    }

    fun isValid(): Boolean {
        return gpxFile != null && startTime != null && endTime != null || tzId == null
    }

    fun transitionList(firstDate: ZonedDateTime): List<ZonedDateTime> {
        val eachTransition = transitions.split(" ").filter { it.trim().isNotEmpty() }
        if (eachTransition.isEmpty()) {
            return emptyList()
        }

        val list = mutableListOf<ZonedDateTime>()
        list.add(startDate(firstDate))
        list.addAll(eachTransition.map { convertTime(firstDate, it) })
        list.add(endDate(firstDate))
        return list
    }

    fun convertTime(firstDate: ZonedDateTime, timeOnly: String): ZonedDateTime {
        val theTime = LocalTime.parse(timeOnly, hourMinuteFormatter)!!
        return ZonedDateTime.of(firstDate.toLocalDate(), theTime, ZoneId.of(tzId))
    }
}

data class ProcessSettingsRun(
    var name: String? = null,
    var type: String? = null,
    var startTime: String? = null
)

data class ConvertedRun(
    var name: String = "",
    var type: String = "",
    var startTime: ZonedDateTime = ZonedDateTime.now()
)

class Diagnostics: CliktCommand() {
    private val filename: String? by option(
        "-f",
        "--file",
        help="The file to process.")
    private val settingsFilename: String? by option(
        "-s",
        "--settings",
        help="The settings to use for processing.")
    private val nameLookupUrl: String by option(
        "-n",
        "--nameLookupUrl",
        help="The URl for looking up names (OsmPoi service)")
        .required()
    private val outputFolder: String by option(
        "-o",
        "--outputFolder",
        help="The output folder, where the files will be written")
        .required()

    override fun run() {
        TrackConfiguration.nameLookupUrl = nameLookupUrl

        if (filename == null && settingsFilename == null) {
            throw Exception("Either a GPF filename (-f) or a settings file (-s) needs to be provided")
        }

        var extraMessage = ""
        var settings: ProcessingSettings? = null
        if (settingsFilename != null) {
            ObjectMapper(YAMLFactory()).registerKotlinModule()
            val mapper: ObjectMapper = YAMLMapper()
            settings = mapper.readValue(File(settingsFilename).readText(Charsets.UTF_8), ProcessingSettings::class.java)

            if (!settings.isValid()) {
                throw Exception("'$settingsFilename' is missing some properties: $settings")
            }

            extraMessage = "from ${settings.startTime}-${settings.endTime}, tz=${settings.tzId}, transitions=${settings.transitions}"
        }

        val gpxFilename = filename ?: settings?.gpxFile!!

        // Save the original tracks & waypoints, with the calculated extensions added
        println("Processing GPX file: $gpxFilename $extraMessage")
        var originalGps = TrackParser().parse(gpxFilename)
        val firstDate = originalGps.tracks.firstOrNull()?.segments?.firstOrNull()?.points?.firstOrNull()
        if (settings != null && firstDate != null) {
            val startDate = settings.startDate(firstDate.dateTime())
            val endDate = settings.endDate(firstDate.dateTime())

            val tracks = mutableListOf<GpsTrack>()
            originalGps.tracks.forEach { track ->
                val segments = mutableListOf<GpsTrackSegment>()
                track.segments.forEach { segment ->
                    val points = PointCalculator.process(segment.points.filter {
                        val ptTime = it.dateTime()
                        ptTime >= startDate && ptTime <= endDate
                    })
                    if (points.isNotEmpty()) {
                        segments.add(GpsTrackSegment(points))
                    }
                }
                tracks.add(GpsTrack(track.name, track.desc, segments))
            }
            originalGps = Gps(
                originalGps.time,
                originalGps.bounds,
                tracks,
                originalGps.waypoints
            )
        }

        val originalWorkspace = GpxWorkspace.create(originalGps)
        val timeBasedSegments = mutableListOf<GpsTrackSegment>()
        originalWorkspace.allSegments.forEach { segment ->
            timeBasedSegments.add(segment)
//            timeBasedSegments.addAll(listOf(GpsTrackSegment(PointCalculator.process(FilterPoints.byTime(segment.points, 2 * 60.0)))))
        }

        val trajectories = TrajectoryByCourse.process(GpxWorkspace.segmentsByDistance(originalWorkspace.allSegments, 2.0))

        originalWorkspace.processedGps = Gps(
            originalWorkspace.gps.time, originalWorkspace.gps.bounds,
            listOf(GpsTrack("", "", originalWorkspace.allSegments)),
            originalWorkspace.gps.waypoints,
            countryNames = originalWorkspace.countries,
            stateNames = originalWorkspace.states,
            cityNames = originalWorkspace.cities,
            sites = originalWorkspace.sites)
        GpxRepository.saveAs(originalWorkspace.processedGps!!, File("$outputFolder/original.gpx"))

        val workspace = GpsAnalyzer.process(originalGps)
        val waypoints = mutableListOf<GpsWaypoint>()
        waypoints.addAll(originalGps.waypoints)
        workspace.stops.sortedBy { it.start.time!! }.forEach {
            val midLat = (it.start.lat!! + it.end.lat!!) / 2
            val midLon = (it.start.lon!! + it.end.lon!!) / 2
            val wpt = GpsWaypoint(midLat, midLon, time = it.start.time, name = "wpt")
            wpt.rangicDescription = it.description
            wpt.cmt = it.reason.toString()
            wpt.rangicStart = it.start
            wpt.rangicEnd = it.end
            val distanceKm = it.start.distanceKm(it.end)
            wpt.rangicDistanceKm = distanceKm
            val seconds = it.start.durationSeconds(it.end)
            wpt.rangicDurationSeconds = seconds
            wpt.rangicSpeedKmh = distanceKm / (60.0 * 60.0 * seconds)

            waypoints.add(wpt)
        }
        waypoints.sortBy { it.time }

        DiagAnalyzer.process(workspace, originalWorkspace.allSegments)
//        DiagAnalyzer.process(workspace, GpxWorkspace.segmentsByDistance(originalWorkspace.allSegments, 0.5))

//        val trajectoryVectors = workspace.trajectories.map { Vector(it.meters(), it.bearing.toDouble()) }
//        trajectoryVectors.forEachIndexed { index, v ->
//            println("${workspace.trajectories[index].start.timeOnly()} - ${workspace.trajectories[index].end.timeOnly()}: c=${v.course.toInt()} s=${v.speed.toInt()}")
//        }


// use the vectors as segments to verify vector correctness

        val transitionList = if (settings != null && firstDate != null) settings.transitionList(firstDate.dateTime()) else emptyList()
        val convertedRuns = settings?.runs?.
            filter { it.name != null }?.
            map {
                val time = if (it.startTime == null) ZonedDateTime.now() else settings.convertTime(firstDate!!.dateTime(), it.startTime ?: "")
                ConvertedRun(it.name ?: "", it.type ?: "", time)
        }
        val longTrajectories = DiagAnalyzer.prune(trajectories, transitionList, convertedRuns ?: emptyList())
//val trajectorySegments = oneMeterTrajectories.map { v ->
val trajectorySegments = longTrajectories.map { v ->
    GpsTrackSegment(listOf(v.start as GpsTrackPoint, v.end as GpsTrackPoint))
}
//        val trajectorySegments = workspace.trajectories.map { v ->
//            GpsTrackSegment(listOf(v.start as GpsTrackPoint, v.end as GpsTrackPoint))
//        }
val trajectoryGps = Gps(
    workspace.processedGps!!.time,
    workspace.processedGps!!.bounds,
    listOf(GpsTrack("", "", trajectorySegments)),
    waypoints
)
GpxRepository.saveAs(trajectoryGps, File("$outputFolder/trajectories.gpx"))


val timeBasedGps = Gps(
    workspace.processedGps!!.time,
    workspace.processedGps!!.bounds,
    listOf(GpsTrack("", "", timeBasedSegments)),
    waypoints
)
GpxRepository.saveAs(timeBasedGps, File("$outputFolder/processed.gpx"))

//        GpxRepository.saveAs(workspace.processedGps!!, File("$outputFolder/processed.gpx"))

        val calculatedSpeed = mutableListOf<Double>()
        val calculatedCourse = mutableListOf<Double>()
//        val angVel = mutableListOf<Double>()
//        val accel = mutableListOf<Double>()

//        val zeroSpeed = mutableListOf<Double>()
//        val lowSpeed = mutableListOf<Double>()
//        workspace.currentSegments.forEach { segment ->
        timeBasedSegments.forEach { segment ->
            segment.points.forEach { point ->
//println("${point.timeOnly()}: c=${point.smoothedVector.course.toInt()} m=${point.smoothedVector.meters}")
//                acceleration.add(
//                    SpeedChangeDetector.gradeAcceleration(
//                        point.calculatedKmh, point.calculatedSeconds, point.calculatedAcceleration))
//                deviceSpeed.add(speed)
//                deviceElevation.add(point.ele ?: -1.0)
                var mps = point.smoothedMeters / point.calculatedSeconds
                if (mps.isInfinite()) { mps = 0.0 }
                if (mps.isNaN()) { mps = 0.0 }
                calculatedSpeed.add(mps)
                calculatedCourse.add(point.deltaCourse.toDouble().absoluteValue)
//                calculatedCourse.add(point.smoothedDeltaCourse.toDouble())
//                angVel.add(point.calculatedAngularVelocity)
//                accel.add(point.calculatedAcceleration)

//                courseDiff.add(point.deltaCourse.toDouble())

//                val speedDev = point.speed ?: -1.0
//                val speedCalc = point.calculatedMps
//                val courseDev = point.course ?: -1.0
//                val courseCalc = point.calculatedCourse
//                courseDiff.add(GeoCalculator.bearingDelta(courseCalc, courseDev.toInt()).absoluteValue.toDouble())
//                zeroSpeed.add(if (point.smoothedKmh <= AnalyzerSettings.zeroSpeedKmh) 1.0 else 0.0)
//                lowSpeed.add(if (point.smoothedKmh <= AnalyzerSettings.movementMinSpeedKmh) 1.0 else 0.0)
            }
        }

        // gps speed appears to be useful for detecting non-moving stops (eating in a restaurant, for instance)
        //  Be sure to differentiate no data in the track versus speeds of 0
        //          NYC-stop-lunch.gpx
        val diag = DiagWorkspace(listOf(
//            DiagData.fromPoints("course diff", courseDiff.toList()),
//            DiagData.fromPoints("ele", deviceElevation.toList()),
            DiagData.fromPoints("speed", calculatedSpeed.toList()),
            DiagData.fromPoints("course", calculatedCourse.toList()),
//            DiagData.fromPoints("ang vel", angVel),
//            DiagData.fromPoints("accel", accel.toList()),
        ))

        Files.writeString(
            Path.of(outputFolder, "diag.json"),
            Json.encodeToString(DiagWorkspace.serializer(), diag),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    fun diff(a: Double?, b: Double?): Double {
        val aVal = if (a == null) 0.0 else if (a.isNaN()) 0.0 else a
        val bVal = if (b == null) 0.0 else if (b.isNaN()) 0.0 else b
        return abs(aVal - bVal)
    }
}
