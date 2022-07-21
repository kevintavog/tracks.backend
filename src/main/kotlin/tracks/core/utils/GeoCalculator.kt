package tracks.core.utils

import kotlin.math.*
import tracks.core.models.*


object GeoCalculator {
    val radiusEarthKm = 6371.3
//    val oneDegreeLatitudeMeters = 111111.0
    private const val POINT_EPSILON = 0.000001

    // Returns the bearing in degrees: 0-359, with 0 as north and 90 as east
    // From https://www.movable-type.co.uk/scripts/latlong.html
    //      https://github.com/chrisveness/geodesy/blob/master/latlon-spherical.js
    fun bearing(pt1: GpsTrackPoint, pt2: GpsTrackPoint): Int {
        return bearing(pt1.lat!!, pt1.lon!!, pt2.lat!!, pt2.lon!!)
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val rLat1 = toRadians(lat1)
        val rLat2 = toRadians(lat2)
        val rLonDelta = toRadians(lon2 - lon1)

        val y = sin(rLonDelta) * cos(rLat2)
        val x = (cos(rLat1) * sin(rLat2)) - (sin(rLat1) * cos(rLat2) * cos(rLonDelta))

        return (toDegrees(atan2(y, x)).toInt() + 360) % 360
    }

    fun bearingDelta(alpha: Int, beta: Int): Int {
        val theta = max(alpha, beta) - min(alpha, beta)
        return ((theta + 180) % 360) - 180
//        val normalizedAlpha = if (alpha > 180) (360-alpha) else alpha
//        val normalizedBeta = if (beta > 180) (360-beta) else beta
//        val theta = (normalizedBeta - normalizedAlpha) % 360
//        return if (theta > 180) (360 - theta) else theta
//        val phi = abs(beta - alpha) % 360
//        return if (phi > 180) (360 - phi) else phi
    }

    // Use the small distance calculation (Pythagorus' theorem)
    // See the 'Equirectangular approximation' section of http://www.movable-type.co.uk/scripts/latlong.html
    // The distance returned is in kilometers
    fun distanceKm(pt1: GpsPoint, pt2: GpsPoint): Double {
        return distanceKm(pt1.lat!!, pt1.lon!!, pt2.lat!!, pt2.lon!!)
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = toRadians(lat1)
        val rLon1 = toRadians(lon1)
        val rLat2 = toRadians(lat2)
        val rLon2 = toRadians(lon2)

        val x = (rLon2 - rLon1) * cos((rLat1 + rLat2) / 2)
        val y = rLat2 - rLat1
        return sqrt((x * x) + (y * y)) * radiusEarthKm
    }

    fun toRadians(degrees: Double): Double {
        return degrees * PI / 180.0
    }

    fun toDegrees(radians: Double): Double {
        return radians * 180.0 / PI
    }

    // Based off: https://github.com/MartinThoma/algorithms/blob/master/crossingLineCheck/Geometry/src/Geometry.java
    fun doVectorsOverlap(first: GpsVector, second: GpsVector): Boolean {
        val firstBB = GpsRectangle(first.start, first.end)
        val secondBB = GpsRectangle(second.start, second.end)
        return (doBoundingBoxesIntersect(firstBB, secondBB)
                && lineSegmentTouchesOrCrossesLine(first, second)
                && lineSegmentTouchesOrCrossesLine(second, first))
    }

    private fun doBoundingBoxesIntersect(first: GpsRectangle, second: GpsRectangle): Boolean {
        return first.lowerLeft.lat!! <= second.upperRight.lat!! &&
                first.upperRight.lat!! >= second.lowerLeft.lat!! &&
                first.lowerLeft.lon!! <= second.upperRight.lon!!
                && first.upperRight.lon!! >= second.lowerLeft.lon!!
    }

    private fun lineSegmentTouchesOrCrossesLine(a: GpsVector, b: GpsVector): Boolean {
        return (isPointOnLine(a, b.start)
                || isPointOnLine(a, b.end)
                || isPointRightOfLine(a, b.start) xor isPointRightOfLine(a, b.end))
    }

    private fun isPointOnLine(a: GpsVector, b: GpsPoint): Boolean {
        // Move the image, so that a.start is at (0, 0)
        val aTmp = GpsVector(
            GpsPointImpl(0.0, 0.0, a.start.time),
            GpsPointImpl(
                a.end.lat!! - a.start.lat!!,
                a.end.lon!! - a.start.lon!!,
                a.start.time
            ),
            0
        )
        val bTmp = GpsPointImpl(b.lat!! - a.start.lat!!, b.lon!! - a.start.lon!!, a.start.time)
        val r: Double = crossProduct(aTmp.end, bTmp)
        return abs(r) < POINT_EPSILON
    }

    private fun isPointRightOfLine(a: GpsVector, b: GpsPoint): Boolean {
        // Move the image, so that a.first is on (0|0)
        val aTmp = GpsVector(
            GpsPointImpl(0.0, 0.0, a.start.time),
            GpsPointImpl(
                a.end.lat!! - a.start.lat!!,
                a.end.lon!! - a.start.lon!!,
                a.start.time
            ),
            0
        )
        val bTmp = GpsPointImpl(b.lat!! - a.start.lat!!, b.lon!! - a.start.lon!!, a.start.time)
        return crossProduct(aTmp.end, bTmp) < 0
    }

    private fun crossProduct(a: GpsPoint, b: GpsPoint): Double {
        return a.lat!! * b.lon!! - b.lat!! * a.lon!!
    }
}
