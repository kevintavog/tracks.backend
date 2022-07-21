package tracks.indexer.models

import java.time.ZonedDateTime

    enum class MovementCategory {
    WALKING,
    GAP_WALKING,
    STOPPED_WALKING,
    VEHICLE,
    GAP_VEHICLE,
    STOPPED_VEHICLE,
}

data class MovementItem(
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val type: MovementCategory
) {
    override fun toString(): String {
        return "$type ${startTime.hour}:${startTime.minute}:${startTime.second}-${endTime.hour}:${endTime.minute}:${endTime.second}"
    }
}
