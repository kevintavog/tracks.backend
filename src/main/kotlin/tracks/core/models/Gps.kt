package tracks.core.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import kotlinx.serialization.Serializable
import tracks.core.utils.Converter
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.SiteResponse
import tracks.indexer.processors.TransportationCalculator
import tracks.indexer.utils.DateTimeFormatter
import java.time.Duration
import java.time.ZonedDateTime

@JsonRootName("gpx")
data class Gps(
    var time: String? = null,
    var bounds: GpsBounds? = null,

    @set:JsonProperty("trk")
    var tracks: List<GpsTrack> = ArrayList(),

    @set:JsonProperty("wpt")
    var waypoints: List<GpsWaypoint> = ArrayList(),

    var countryNames: List<String> = listOf(),
    var stateNames: List<String> = listOf(),
    var cityNames: List<String> = listOf(),
    var sites: List<SiteResponse> = listOf(),
    var hierarchicalNames: List<LocationNames> = listOf()
) {
    val kilometers: Double by lazy { tracks.map { it.kilometers }.reduce { acc, v -> acc + v } }
    val seconds: Double by lazy { tracks.first().segments.first().points.first().durationSeconds(
        tracks.last().segments.last().points.last()) }
    val calculatedBounds: GpsBounds by lazy {
        val bounds = tracks.first().bounds
        tracks.forEach {
            bounds.minlat = (bounds.minlat!!).coerceAtMost(it.bounds.minlat!!)
            bounds.minlon = (bounds.minlon!!).coerceAtMost(it.bounds.minlon!!)
            bounds.maxlat = (bounds.maxlat!!).coerceAtLeast(it.bounds.maxlat!!)
            bounds.maxlon = (bounds.maxlon!!).coerceAtLeast(it.bounds.maxlon!!)
        }
        bounds
    }
}

@Serializable
data class LocationNames(
    val countryName: String?,
    val countryCode: String?,
    val stateName: String?,
    val cityName: String?,
    val sites: MutableList<LocationNamesSite>
)

@Serializable
data class LocationNamesSite(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val children: MutableList<LocationNamesSite>
) {
    override fun toString(): String {
        return "$name $children"
    }
}

@JsonRootName("bounds")
data class GpsBounds(
    var minlon: Double?,
    var maxlon: Double?,
    var minlat: Double?,
    var maxlat: Double?
)

@JsonRootName("wpt")
data class GpsWaypoint(
    var lat: Double?,
    var lon: Double?,
    var time: String?,
    var ele: Double? = null,
    var name: String? = null,
    var desc: String? = null,
    var cmt: String? = null,
    var fix: String? = null,
    var hdop: Double? = null,
    var vdop: Double? = null,
    var pdop: Double? = null,

    var rangicDescription: String? = null,
    var rangicStart: GpsPoint? = null,
    var rangicEnd: GpsPoint? = null,
    var rangicSpeedKmh: Double? = null,
    var rangicDistanceKm: Double? = null,
    var rangicDurationSeconds: Double? = null,
    var rangicStopType: String? = null
)

// waypoint
//  .name  => Auto-detected stop #YYY
//  .src   => Rangic Tracks
//         => Rangic Tracks
//  .desc => Density detector
//         => Overlapping paths
//  .sym   (optional)
//         => Emoji? for type of stop?
//  .type  => Waypoint, Stop, Dwell(?), ...
// extensions:
//      duration (assuming time is start, this could be end?)
//

@JsonRootName("trk")
data class GpsTrack(
    var name: String?,
    var desc: String?,

    @set:JsonProperty("trkseg")
    var segments: List<GpsTrackSegment> = ArrayList()
) {
    val kilometers: Double by lazy { segments.map { it.kilometers }.reduce { acc, v -> acc + v } }
    val seconds: Double by lazy { segments.first().points.first().durationSeconds(segments.last().points.last()) }
    val bounds: GpsBounds by lazy {
        val bounds = segments.first().bounds
        segments.forEach {
            bounds.minlat = (bounds.minlat!!).coerceAtMost(it.bounds.minlat!!)
            bounds.minlon = (bounds.minlon!!).coerceAtMost(it.bounds.minlon!!)
            bounds.maxlat = (bounds.maxlat!!).coerceAtLeast(it.bounds.maxlat!!)
            bounds.maxlon = (bounds.maxlon!!).coerceAtLeast(it.bounds.maxlon!!)
        }
        bounds
    }
}

fun List<GpsTrackSegment>.containingSegment(start: String, end: String): GpsTrackSegment? {
    this.forEach { segment ->
        if (segment.contains(start) && segment.contains(end)) {
            return segment
        }
    }
    return null
}

@JsonRootName("trkseg")
data class GpsTrackSegment(
    @set:JsonProperty("trkpt")
    var points: List<GpsTrackPoint> = ArrayList()
) {
    val kilometers: Double by lazy {
        var distanceKm = 0.0
        for (idx in 1 until points.size) {
            distanceKm += points[idx - 1].distanceKm(points[idx])
        }
        distanceKm
    }
    val seconds: Double by lazy { points.first().durationSeconds(points.last()) }
    val course: Int by lazy { GeoCalculator.bearing(points.first(), points.last()) }
    val speedKmh: Double by lazy { Converter.speedKph(seconds, kilometers) }
    val bounds: GpsBounds by lazy {
        val bounds = GpsBounds(points.first().lat, points.first().lon, points.first().lat, points.first().lon)
        points.forEach {
            bounds.minlat = (bounds.minlat!!).coerceAtMost(it.lat!!)
            bounds.minlon = (bounds.minlon!!).coerceAtMost(it.lon!!)
            bounds.maxlat = (bounds.maxlat!!).coerceAtLeast(it.lat!!)
            bounds.maxlon = (bounds.maxlon!!).coerceAtLeast(it.lon!!)
        }
        bounds
    }
    val transportationTypes: List<TransportationType> by lazy {
        TransportationCalculator.process((this))
    }

    fun contains(time: String): Boolean {
        if (points.isEmpty()) { return false }
        val first = points.first().time
        val last = points.last().time
        if (first == null || last == null) { return false }
        return time >= first && time <= last
    }

    override fun toString(): String {
        if (points.isEmpty()) { return "Empty" }
        return "${points.first().timeOnly()} - ${points.last().timeOnly()}: $kilometers ${speedKmh}kmh"
    }
}

@JsonRootName("trkpt")
data class GpsTrackPoint(
    override var lat: Double?,
    override var lon: Double?,
    override var time: String?,
    // Meters
    var ele: Double?,
    // Degrees
    var course: Double?,
    // Meters/second
    var speed: Double?,
    var fix: String?,
    var hdop: Double?,
    var vdop: Double?,
    var pdop: Double?,
    var extensions: List<GpsBadElfPointExtension?>?,

    var calculatedCourse: Int = 0,
    var calculatedMeters: Double = 0.0,
    var calculatedSeconds: Double = 0.0,
    var calculatedMps: Double = 0.0,
    var calculatedKmh: Double = 0.0,
//    var calculatedTao: Double = 0.0,

    // Acceleration units: meters/s/s
    var deviceAcceleration: Double = 0.0,
    var calculatedAcceleration: Double = 0.0,
    var accelerationGrade: Double = 0.0,

    var transportationTypes: List<TransportationType> = listOf(),

    // Deltas are between the previous point and this point
    var deltaCourse: Int = 0,
    var deltaKmh: Double = 0.0,
    var deltaElevation: Double = 0.0,

    var smoothedCourse: Int = 0,
//    var smoothedKmh: Double = 0.0,
//    var smoothedMeters: Double = 0.0,

//    var velocity: Double = 0.0,
//    var latVelocity: Double = 0.0,
//    var lonVelocity: Double = 0.0,
//    var eleVelocity: Double = 0.0,
): GpsPoint

data class GpsBadElfPointExtension(
    var speed: Double?
)

interface GpsPoint {
    var lat: Double?
    var lon: Double?
    var time: String?
}

fun GpsPoint.timeOnly(): String {
    return DateTimeFormatter.formatTime(DateTimeFormatter.parse(time!!))
}

fun GpsPoint.dateTime(): ZonedDateTime {
    return DateTimeFormatter.parse(time!!)
}

fun GpsPoint.durationSeconds(other: GpsPoint): Double {
    return Duration.between(dateTime(), other.dateTime()).seconds.toDouble()
}

fun GpsPoint.distanceKm(other: GpsPoint): Double {
    return GeoCalculator.distanceKm(this, other)
}

fun GpsPoint.speedKmh(other: GpsPoint): Double {
    return Converter.speedKph(durationSeconds(other), distanceKm(other))
}

fun GpsPoint.compareTime(other: GpsPoint): Int {
    return this.dateTime().compareTo(other.dateTime())
}

data class GpsPointImpl(
    override var lat: Double?,
    override var lon: Double?,
    override var time: String?) : GpsPoint

class GpsRectangle(one: GpsPoint, two: GpsPoint) {
    val lowerLeft: GpsPoint
    val upperRight: GpsPoint

    init {
        lowerLeft = GpsPointImpl((one.lat!!).coerceAtMost(two.lat!!), (one.lon!!).coerceAtMost(two.lon!!), one.time)
        upperRight = GpsPointImpl((one.lat!!).coerceAtLeast(two.lat!!), (one.lon!!).coerceAtLeast(two.lon!!), two.time)
    }
}

data class GpsVector(
    val start: GpsPoint,
    val end: GpsPoint,
    val bearing: Int,
    var backwardOverlapCount: Int = 0,
    var forwardOverlapCount: Int = 0
) {
    fun kmh(): Double {
        return Converter.metersPerSecondToKilometersPerHour(meters() / durationSeconds().toDouble())
    }
    fun meters(): Double {
        return start.distanceKm(end) * 1000.0
    }
    fun durationSeconds(): Int {
        return start.durationSeconds(end).toInt()
    }
    override fun toString(): String = "${start.timeOnly()}: ${(start.distanceKm(end) * 1000.0).toLong()}m; ${start.durationSeconds(end).toInt()} sec; @$bearing"
}
