package tracks.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tracks.indexer.models.SiteResponse

@Serializable
data class GeoPoint(
    @SerialName("lat")
    val latitude: Double,
    @SerialName("lon")
    val longitude: Double
)

@Serializable
data class GpsDto(
    val id: String,
    val path: String,
    val timezoneInfo: TimezoneInfo,
    val startTime: String,          // 2009-06-15T20:45:30Z
    val endTime: String,            // 2009-06-15T20:45:30Z
    val startTimeLocal: String,     // 2009-06-15T13:45:30
    val endTimeLocal: String,       // 2009-06-15T13:45:30

    // These are local times, not UTC
    val year: Int,
    val month: String,
    val dayOfWeek: String,
    val dayOfMonth: Int,

    val bounds: GpsBoundsDto,
    val seconds: Double,
    val kilometers: Double,

    // Location name
    val countries: List<String>,
    val states: List<String>,
    val cities: List<String>,
    val sites: List<SiteResponse>,
    val hierarchicalNames: List<LocationNames>,
)

@Serializable
data class GpsBoundsDto(
    val min: GeoPoint,
    val max: GeoPoint
)

@Serializable
data class TimezoneInfo(
    val id: String,     // America/Los_Angeles
    val tag: String     // PDT
)
