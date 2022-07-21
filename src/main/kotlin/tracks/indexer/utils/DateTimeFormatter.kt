package tracks.indexer.utils

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateTimeFormatter {
    private val utcZoneId = ZoneId.of("UTC")
    private val iso8601Formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
    private val iso8601TimeOnlyFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    fun parse(value: String): ZonedDateTime {
        val zoned = ZonedDateTime.parse(value, iso8601Formatter).toInstant()
        return zoned.atZone(utcZoneId)
    }

    fun formatTime(dateTime: ZonedDateTime): String {
        return dateTime.format(iso8601TimeOnlyFormatter)
    }
}
