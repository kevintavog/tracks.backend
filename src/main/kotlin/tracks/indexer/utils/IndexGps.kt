package tracks.indexer.utils

import kotlinx.serialization.json.Json
import org.apache.http.HttpHost
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import tracks.core.elasticsearch.ElasticClient
import tracks.core.models.*
import tracks.core.services.GpxRepository
import tracks.core.utils.TrackConfiguration
import tracks.indexer.models.GpxWorkspace
import java.time.ZoneId
import java.time.ZonedDateTime

object IndexGps {
    private val esClient: RestHighLevelClient by lazy {
        RestHighLevelClient(
            RestClient.builder(
                HttpHost(TrackConfiguration.elasticSearchHost, TrackConfiguration.elasticSearchPort)
            )
        )
    }

    fun exists(id: String): Boolean {
        return esClient.exists(GetRequest(ElasticClient.tracksIndex).id(id), RequestOptions.DEFAULT)
    }

    fun index(fullPath: String, workspace: GpxWorkspace) {
        val gps = workspace.processedGps!!
        val startDate = gps.tracks.first().segments.first().points.first().time!!
        val endDate = gps.tracks.last().segments.last().points.last().time!!

        val localZone = ZoneId.of(workspace.timezoneInfo.id)
        val localDate = ZonedDateTime.parse(startDate).withZoneSameInstant(localZone).toOffsetDateTime()
        val localStartDate = localDate.toString()
        val localEndDate = ZonedDateTime.parse(endDate).withZoneSameInstant(localZone).toOffsetDateTime().toString()

        val dto = GpsDto(
            GpxRepository.toId(fullPath, gps),
            fullPath,

            workspace.timezoneInfo,
            startDate,
            endDate,
            localStartDate,
            localEndDate,

            localDate.year,
            localDate.month.name,
            localDate.dayOfWeek.name,
            localDate.dayOfMonth,

            GpsBoundsDto(
                GeoPoint(gps.calculatedBounds.minlat!!, gps.calculatedBounds.minlon!!),
                GeoPoint(gps.calculatedBounds.maxlat!!, gps.calculatedBounds.maxlon!!)
            ),
            gps.seconds,
            gps.kilometers,

            // Location name
            workspace.countries,
            workspace.states,
            workspace.cities,
            workspace.sites
        )

        esClient.index(IndexRequest(ElasticClient.tracksIndex)
            .id(dto.id)
            .source(Json.encodeToString(GpsDto.serializer(), dto), XContentType.JSON),
            RequestOptions.DEFAULT
        )
    }
}
