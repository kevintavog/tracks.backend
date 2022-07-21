package tracks.core.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateFormatter {
    private val utcZoneId = ZoneId.of("UTC")
    private val recordDateFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
    }
    private val dateOnlyFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
    private val iso8601Formatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZZZZZ")
    }

    fun parseDateOnly(value: String): LocalDate {
        return LocalDate.parse(value, dateOnlyFormatter)
    }

    fun parseUTC(value: String): ZonedDateTime {
        val zoned = ZonedDateTime.parse(value, recordDateFormatter).toInstant()
        return zoned.atZone(utcZoneId)
    }

    fun parseISO8601(value: String): ZonedDateTime {
        val zoned = ZonedDateTime.parse(value, iso8601Formatter).toInstant()
        return zoned.atZone(utcZoneId)
    }

    fun formatISO8601(time: ZonedDateTime): String {
        return time.format(iso8601Formatter)
    }

    fun keyUTC(date: ZonedDateTime): String {
        return directoriesUTC(date).joinToString("-")
    }

    fun directoriesUTC(date: ZonedDateTime): Array<String> {
        return listOf(
            String.format("%04d", date.year),
            String.format("%02d", date.monthValue),
            String.format("%02d", date.dayOfMonth)
        ).toTypedArray()
    }
}
