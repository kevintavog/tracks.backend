package tracks.diag

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.Json
import tracks.core.models.*
import tracks.core.services.GpxRepository
import tracks.core.utils.Converter
import tracks.core.utils.GeoCalculator
import tracks.core.utils.TrackConfiguration
import tracks.indexer.TrackParser
import tracks.indexer.models.DiagData
import tracks.indexer.models.DiagWorkspace
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.processors.AnalyzerSettings
import tracks.indexer.processors.GpsAnalyzer
import tracks.indexer.processors.SpeedChangeDetector
import java.io.File
import java.nio.file.*
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

class Diagnostics: CliktCommand() {
    val filename: String by option(
        "-f",
        "--file",
        help="The file to process.")
        .required()
    val nameLookupUrl: String by option(
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

//        Files.copy(Path.of(filename), Path.of(outputFolder, "original.gpx"), StandardCopyOption.REPLACE_EXISTING)
        // Save the original tracks & waypoints, with the calculated extensions added
        val originalGps = TrackParser().parse(filename)
        val originalWorkspace = GpxWorkspace.create(originalGps)
        originalWorkspace.processedGps = Gps(
            originalWorkspace.gps.time, originalWorkspace.gps.bounds,
            listOf(GpsTrack("", "", originalWorkspace.allSegments)),
            originalWorkspace.gps.waypoints,
            countryNames = originalWorkspace.countries,
            stateNames = originalWorkspace.states,
            cityNames = originalWorkspace.cities,
            sites = originalWorkspace.sites)
        GpxRepository.saveAs(originalWorkspace.processedGps!!, File("$outputFolder/original.gpx"))

        println("Processing $filename")
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

//println("Segment descriptions")
//SegmentDescriptions.process(workspace.currentSegments).forEach { d ->
//    println(d)
//}
//println("Low quality runs")
//var prevNotableLQR: LowQualityRun? = null
//workspace.lowQualityRuns.sortedBy { it.start.time!! }.forEach { lqr ->
//    var extra = ""
//    prevNotableLQR?.let {
//        val prevMeters = (it.midPoint.distanceKm(lqr.start) * 1000)
//        val prevSeconds = max(0, it.end.durationSeconds(lqr.start).toInt())
//        extra = "(prev = ${prevMeters.toInt()}m; ${prevSeconds}s; ${it.type})"
//    }
//    println("$lqr $extra")
//    if (lqr.type != LowQualityType.BIG_SPEED_CHANGES) {
//        if (prevNotableLQR == null || prevNotableLQR!!.end.dateTime() < lqr.end.dateTime()) {
//            prevNotableLQR = lqr
//        }
//    }
//}

//        for (idx in 0 until workspace.vectors.size-1) {
//            val current = workspace.vectors[idx]
//            val next = workspace.vectors[idx + 1]
//            val courseDiff = GeoCalculator.bearingDelta(current.bearing, next.bearing).absoluteValue
//            val bigTurn = courseDiff > 60
//            val bearingMessage = if (bigTurn) "BIG TURN" else ""
//            val moving = SpeedDetector.isMoving(current.kmh())
//            val movingMessage = if (moving) "" else "NO MOVEMENT"
//            val speedDiff = next.kmh() - current.kmh()
//            val bigSpeed = SpeedChangeDetector.isBigChange(current.kmh(), next.kmh())
//            val speedMessage = if (bigSpeed) "BIG SPEED" else ""
//            println("${current.start.timeOnly()}: @$courseDiff (${current.durationSeconds()}, ${Converter.readableSpeed(current.kmh())} kmh) $movingMessage $bearingMessage $speedMessage")
//        }

// use the vectors as segments to verify vector correctness
val vectorSegments = workspace.vectors.map { v ->
    GpsTrackSegment(listOf(v.start as GpsTrackPoint, v.end as GpsTrackPoint))
}
val vectorGps = Gps(
    workspace.processedGps!!.time,
    workspace.processedGps!!.bounds,
    listOf(GpsTrack("", "", vectorSegments)),
    waypoints
)
GpxRepository.saveAs(vectorGps, File("$outputFolder/vectors.gpx"))
//SegmentDescriptions.process(vectorSegments).forEach { d ->
//    println(d)
//}

//        workspace.processedGps!!.tracks.forEach { tr ->
//            println("${tr.segments.first().points.first().timeOnly()} - ${tr.segments.last().points.last().timeOnly()}")
//        }

        GpxRepository.saveAs(workspace.processedGps!!, File("$outputFolder/processed.gpx"))

        // Speed, acceleration, course delta
        val deviceSpeed = mutableListOf<Double>()
        val calculatedSpeed = mutableListOf<Double>()
//        val zeroSpeed = mutableListOf<Double>()
//        val lowSpeed = mutableListOf<Double>()
        workspace.currentSegments.forEach { segment ->
            segment.points.forEach { point ->
//                acceleration.add(
//                    SpeedChangeDetector.gradeAcceleration(
//                        point.calculatedKmh, point.calculatedSeconds, point.calculatedAcceleration))
                deviceSpeed.add(point.speed ?: -1.0)
                calculatedSpeed.add(point.calculatedMps)
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
            DiagData.fromPoints("device speed", deviceSpeed.toList()),
            DiagData.fromPoints("calculated speed", calculatedSpeed.toList()),
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
