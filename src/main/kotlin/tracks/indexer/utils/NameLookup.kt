package tracks.indexer.utils

import io.ktor.utils.io.errors.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tracks.core.utils.TrackConfiguration
import tracks.indexer.models.*
import java.util.concurrent.TimeUnit

object NameLookup {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val radiusMeters = 10.0

    private val jsonCoder = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy { OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build() }

    fun resolveOsmPoi(items: List<NameRequest>): List<OsmPoiLocationResponse>? {
        if (items.isEmpty()) { return emptyList() }
        val path = "/pois"
        val requestBody = OsmPoiBulkLocationRequest(items, poiRadiusMeters = radiusMeters.toInt())

        val request = client.newCall(Request.Builder()
            .url("${TrackConfiguration.nameLookupUrl}$path")
            .post(Json.encodeToJsonElement(requestBody).toString().toRequestBody(JSON))
            .build())
        request.execute()
            .use { response ->
                if (!response.isSuccessful) {
                    println("OsmPoi call failed: ${response.code}; body='${response.body?.string()}'")
                    throw IOException("OsmPoi service failed with ${response.code} url=${TrackConfiguration.nameLookupUrl}$path")
                }
                if (response.body != null) {
                    val bodyAsString = response.body!!.string()
                    try {
                        return jsonCoder.decodeFromString<OsmPoiBulkLocationResponse>(bodyAsString).items
                            .mapNotNull { it.location }
                    } catch (t: Throwable) {
                        println("OsmPoi decoding exception: ${t.message}; body='$bodyAsString'")
                        throw t
                    }
                } else {
                    throw IOException("OsmPoi service did not return a body ${response.code}")
                }
            }
    }


/*
    fun processReverseName(workspace: GpxWorkspace) {
        // Lookup each waypoint (as they may be stops)
        // Lookup periodically along each segment
        val request = mutableListOf<NameRequest>()
        workspace.processedGps?.waypoints?.forEach { wpt ->
            request.add(NameRequest(wpt.lat!!, wpt.lon!!))
        }

        workspace.processedGps?.tracks?.forEach { track ->
            track.segments.forEach { segment ->
                request.add(NameRequest(segment.points.first().lat!!, segment.points.first().lon!!))
                request.add(NameRequest(segment.points.last().lat!!, segment.points.last().lon!!))
                var meters = 0.0
                var seconds = 0.0
                segment.points.forEach { pt ->
                    meters += pt.calculatedMeters
                    seconds += pt.calculatedSeconds
                    if (meters >= AnalyzerSettings.maxMetersBetweenPlacenames ||
                        seconds >= AnalyzerSettings.maxSecondsBetweenPlacenames) {
                        request.add(NameRequest(pt.lat!!, pt.lon!!))
                        meters = 0.0
                        seconds = 0.0
                    }
                }
            }
        }

        val names = resolveReverseName(request) ?: emptyList()
        workspace.countries = names.map { it.placename?.countryName }.distinct().filterNotNull()
        workspace.states = names.map { it.placename?.state }.distinct().filterNotNull()
        workspace.cities = names.map { it.placename?.city }.distinct().filterNotNull()
        workspace.sites = names
            .filter { it.placename?.sites?.isNotEmpty() ?: false }
            .map { SiteResponse(
                it.placename?.sites ?: emptyList(),
                it.placename?.location?.lat ?: 0.0,
                it.placename?.location?.lon ?: 0.0,
                emptyList()
            ) }
            .distinctBy { it.name.firstOrNull() ?: "" }
    }
*/

/*
    private fun resolveReverseName(items: List<NameRequest>): List<NameResponse>? {
        val path = "/cached-names"
        val requestBody = BulkNamesRequest(items, radiusMeters)

        val request = client.newCall(Request.Builder()
            .url("${TrackConfiguration.nameLookupUrl}$path")
            .post(Json.encodeToJsonElement(requestBody).toString().toRequestBody(JSON))
            .build())

        request.execute()
            .body?.let {
                return jsonCoder.decodeFromString<BulkNamesResponse>(it.string()).items
            }

        return null
    }
*/
}
