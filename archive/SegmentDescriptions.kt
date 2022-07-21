package tracks.indexer.processors

import tracks.core.models.GpsTrackSegment
import tracks.core.models.durationSeconds
import tracks.core.models.timeOnly
import tracks.core.utils.Converter
import tracks.core.utils.GeoCalculator
import java.math.RoundingMode
import kotlin.math.absoluteValue

object SegmentDescriptions {
    fun process(segments: List<GpsTrackSegment>): List<String> {
        val descriptions = mutableListOf<String>()
        var prev: GpsTrackSegment? = null
        segments.forEach { current ->
            val meters = (current.kilometers * 1000).toInt()
            val seconds = current.seconds.toInt()
            val kmh = current.speedKmh.toBigDecimal().setScale(1, RoundingMode.HALF_UP).toDouble()
            val course = current.course
            var desc = "${current.points.first().timeOnly()}: "
            prev?.let {
                val courseDelta = GeoCalculator.bearingDelta(course, it.course).absoluteValue
//println("${current.points.first().timeOnly()}: ${it.course} & $course => $courseDelta")
                var extra = false
                if (courseDelta > 65) {
                    desc += "COURSE $courseDelta; "
                    extra = true
                }
//                val speedDelta = ((kmh - it.speedKmh).absoluteValue).toBigDecimal().setScale(1, RoundingMode.HALF_UP).toDouble()
                val prevMps = Converter.kmhToMetersPerSecond(it.speedKmh)
                val curMps = Converter.kmhToMetersPerSecond(current.speedKmh)
                val acceleration = (curMps - prevMps) / current.seconds
                val speedGrade = SpeedChangeDetector.gradeAcceleration(it.speedKmh, acceleration).toInt()
//println("${current.points.first().timeOnly()}: ($curMps - $prevMps / ${it.seconds}) = $acceleration -> $speedGrade")
                // NOTE: This is likely naive. The delta is dependent on the total speed
                if (speedGrade > 1) {
                    desc += "SPEED: $speedGrade; "
                    extra = true
                }

                val gapSeconds = it.points.last().durationSeconds(current.points.first()).toInt().absoluteValue
                if (gapSeconds > 2) {
                    desc += "GAP: $gapSeconds; "
                    extra = true
                }
                if (!extra) {
                    desc += "       "
                }
            } ?: run {
                desc += "Start,"
            }
            desc += " $seconds secs, ${meters}m @$course: $kmh kmh"

            descriptions.add(desc)
            prev = current
        }
        return descriptions
    }
}