package tracks.filter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import tracks.core.models.*
import tracks.indexer.TrackParser
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.exitProcess

class FilterMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Filter().main(args)
        }
    }
}

class Filter: CliktCommand() {
    private val inputFile: String by option(
        "-i",
        "--input",
        help = "The GPX source file."
    ).required()
    private val outputFile: String by option(
        "-o",
        "--output",
        help = "The output file."
    ).required()
    private val endArg: String by option(
        "-e",
        "--end",
        help = "The UTC end time, in HH:MM form"
    ).required()
    private val startArg: String by option(
        "-s",
        "--start",
        help = "The UTC start time, in HH:MM form"
    ).required()

    override fun run() {
        val hourMinuteFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val startTime = LocalTime.parse(startArg, hourMinuteFormatter)!!
        val endTime = LocalTime.parse(endArg, hourMinuteFormatter)!!
        val endIsNextDay = startTime > endTime

        println("Parsing $inputFile from $startTime to $endTime, filtering between $startTime && $endTime (end is next day: $endIsNextDay)")
        val gps = TrackParser().parse(inputFile)
        val first = gps.tracks.firstOrNull()?.segments?.firstOrNull()?.points?.firstOrNull()
        val last = gps.tracks.lastOrNull()?.segments?.lastOrNull()?.points?.lastOrNull()
        first?.let {
            println("  -> which starts at ${first.time} and ends ${last?.time}")
        }
        val points = mutableListOf<GpsTrackPoint>()
        gps.tracks.forEach { track ->
            track.segments.forEach { segment ->
                points.addAll(segment.points.filter { isIncluded(first!!.dateTime(), it.dateTime(), startTime, endTime, endIsNextDay) })
            }
        }

        println("Saving ${points.size} points to $outputFile")
        val filteredGps = Gps(null, null,
            listOf(GpsTrack("", "", listOf(GpsTrackSegment(points)))))
        TrackParser().save(filteredGps, outputFile)
        exitProcess(0)
    }

    private fun isIncluded(first: ZonedDateTime, pointTime: ZonedDateTime, startTime: LocalTime, endTime: LocalTime, endIsNextDay: Boolean): Boolean {
        if (endIsNextDay) {
            if (pointTime.dayOfYear == first.dayOfYear) {
                if (pointTime.hour < startTime.hour ||
                    pointTime.hour == startTime.hour && pointTime.minute < startTime.minute) {
                    return false
                }
                return true
            }
            if (pointTime.hour > endTime.hour ||
                pointTime.hour == endTime.hour && pointTime.minute > endTime.minute) {
                return false
            }
        } else {
            if (pointTime.hour < startTime.hour ||
                pointTime.hour == startTime.hour && pointTime.minute < startTime.minute) {
                return false
            }

            if (pointTime.hour > endTime.hour ||
                pointTime.hour == endTime.hour && pointTime.minute > endTime.minute) {
                return false
            }
        }

        return true
    }
}
