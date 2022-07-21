package tracks.indexer.models

import tracks.core.models.GpsPoint
import tracks.core.models.distanceKm
import tracks.core.models.durationSeconds

enum class BreakReason(val reason: String) {
    LargeGap("LargeGap"),
    SmallGap("SmallGap"),
    LowMovement("LowMovement"),
    JumpingCourse("JumpingCourse"),
    JumpingSpeed("JumpingSpeed"),
    HighDensityJumpingCourse("HighDensityJumpingCourse"),
    Overlapping("Overlapping")
}

data class GpsBreak(
    var start: GpsPoint,
    var end: GpsPoint,
    var reason: BreakReason,
    var totalDurationSeconds: Double? = null,
    var totalDistanceMeters: Double? = null
) {
    val durationSeconds: Double by lazy { start.durationSeconds(end) }
    val distanceMeters: Double by lazy { start.distanceKm(end) * 1000.0 }
    val speedMps: Double by lazy { distanceMeters / durationSeconds }

    val totalSpeedMps: Double? by lazy {
        if (totalDurationSeconds == null || totalDistanceMeters == null) {
            null
        } else {
            totalDistanceMeters!! / totalDurationSeconds!!
        }
    }

    override fun toString(): String {
        var message = "${durationSeconds.toInt()} sec, ${fmt(distanceMeters)} meters, ${fmt(speedMps)} mps"
        totalSpeedMps?.let {
             message = "total: ${totalDurationSeconds!!.toInt()} sec, ${fmt(totalDistanceMeters!!)} meters, " +
                "${fmt(it)} mps (${fmt(totalSpeedMps!!)})"
        }
        return "${start.time}-${end.time} $reason; $message"
    }

    private fun fmt(value: Double): String {
        return String.format("%.3f", value)
    }
}
