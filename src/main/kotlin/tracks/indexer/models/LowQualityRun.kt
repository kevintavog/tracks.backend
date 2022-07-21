package tracks.indexer.models

import tracks.core.models.GpsTrackPoint
import tracks.core.models.dateTime
import tracks.core.models.durationSeconds
import tracks.core.models.timeOnly
import tracks.core.utils.DateFormatter

enum class LowQualityType {
    NO_MOVEMENT,
    LOW_MOVEMENT,
    MISSING_DATA,
    BIG_COURSE_CHANGES,
    BIG_SPEED_CHANGES
}

data class LowQualityRun(
    val start: GpsTrackPoint, var end: GpsTrackPoint, val type: LowQualityType, var description: String,
    val distanceKm: Double = 0.0) {

    override fun toString(): String {
        return "${start.timeOnly()}, ${duration()}s: $type; $description"
    }

    fun duration() = start.durationSeconds(end).toInt()
}
