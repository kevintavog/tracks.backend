package tracks.indexer.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.serialization.Serializable

// The ReverseNameLookup service
@Serializable
data class BulkNamesRequest(
    val items: List<NameRequest>,
    val radiusMeters: Double?
)

@Serializable
data class BulkNamesResponse(
    val items: List<NameResponse>,
    val hasErrors: Boolean
)

@Serializable
data class NameRequest(val lat: Double, val lon: Double)

@Serializable
data class NameResponse(
    val placename: Placename? = null,
    val error: String? = null
)

@Serializable
data class Placename(
    val fullDescription: String,
    val sites: List<String>? = null,
    val city: String? = null,
    val state: String? = null,
    val countryName: String? = null,
    val countryCode: String? = null,
    val location: PlacenameLocation? = null,
    val dateCreated: String? = null
)

@Serializable
data class PlacenameLocation(val lat: Double, val lon: Double)

@Serializable
data class SiteResponse(
    val name: List<String>,
    val lat: Double,
    val lon: Double,
    val tags: List<OsmTagValue>
)

// The OSM POI service
@Serializable
data class OsmPoiBulkLocationRequest(
    val items: List<NameRequest>? = null,
    val includeOrdinary: Boolean? = null,
    val poiRadiusMeters: Int? = null,
    val cityRadiusMeters: Int? = null
)

@Serializable
data class OsmPoiBulkLocationResponse(
    val items: List<OsmPoiLocationSearchResponse>
)

@Serializable
data class OsmPoiLocationResponse(
    val inside: List<OsmPoi>,
    val nearby: List<OsmPoi>,
    val countryCode: String?,
    val countryName: String?,
    val stateName: String?,
    val cityName: String?
)

@Serializable
data class OsmPoiLocationSearchResponse(
    val error: String? = null,
    val location: OsmPoiLocationResponse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@Serializable
data class OsmPoi(
    val id: String,
    var name: String,
    val point: NameRequest,
    var location: String? = null,
    var tags: List<OsmTagValue>,
    val area: Double? = null,
    var poiLevel: PoiLevel
)

@Serializable
data class OsmTagValue(val key: String = "", val value: String = "")

enum class PoiLevel {
    ORDINARY,
    NOTABLE,
    ADMIN,
    ZERO
}