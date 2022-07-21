package tracks.core.elasticsearch

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.http.HttpHost
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.common.geo.GeoPoint
import org.elasticsearch.common.unit.DistanceUnit
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.RangeQueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.GeoDistanceSortBuilder
import org.elasticsearch.search.sort.SortMode
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import tracks.core.RangicInternalServerException
import tracks.core.models.GpsDto
import tracks.core.models.SearchOptions
import tracks.core.models.SearchResults
import tracks.core.utils.TrackConfiguration
import java.io.Closeable

class ElasticClient : Closeable {
    companion object {
        private val logger = LoggerFactory.getLogger(ElasticClient::class.java)
        private val defaultSearchFields = mapOf(
            "cities" to 1.0f,
            "countries" to 1.0f,
            "sites" to 1.0f,
            "sites.name" to 1.0f,
            "sites.tags.key" to 1.0f,
            "sites.tags.value" to 1.0f,
            "states" to 1.0f,
            "path" to 1.0f,
            "year" to 1.0f,
            "month" to 1.0f,
            "dayOfWeek" to 1.0f,
            "dayOfMonth" to 1.0f,
        )

        var tracksIndex = "tracks"

        fun toReadable(t: Throwable): String {
            var message = "${t.javaClass}: "
            if (t is ElasticsearchException && t.rootCause != null) {
                message += "type=${t.rootCause.javaClass} reason=${t.rootCause.message}; "
            } else {
                message += "${t.message} "
            }
            t.cause?.let {
                message += "(${toReadable(it)}) "
            }
            return message
        }

    }

    private val esClient: RestHighLevelClient by lazy {
        RestHighLevelClient(
            RestClient.builder(
                HttpHost(TrackConfiguration.elasticSearchHost, TrackConfiguration.elasticSearchPort)
            )
            .setRequestConfigCallback { config ->
                config.setConnectionRequestTimeout(3 * 60 * 1000)
            }
        )
    }

    override fun close() {
        esClient.close()
    }

    fun getId(id: String, options: SearchOptions): SearchResults {
        val builder = SearchSourceBuilder()
            .from(options.first)
            .size(options.count)
            .query(getTextQuery("id:${id}"))
            .trackTotalHits(true)

        return execute(builder, options)
    }

    fun query(queryText: String?, options: SearchOptions): SearchResults {
        val builder = SearchSourceBuilder()
            .from(options.first)
            .size(options.count)
            .sort("startTime", SortOrder.DESC)
            .query(getTextQuery(queryText))
            .trackTotalHits(true)

        return execute(builder, options)
    }

    fun dates(startDate: String?, endDate: String?, queryText: String?, options: SearchOptions): SearchResults {
        val textQuery = getTextQuery(queryText)
        val query = QueryBuilders.boolQuery().must(textQuery)

        startDate?.let {
            query.must(QueryBuilders.rangeQuery("startTime").gte(it))
        }
        endDate?.let {
            query.must(QueryBuilders.rangeQuery("endTime").lte(it))
        }

        val builder = SearchSourceBuilder()
            .from(options.first)
            .size(options.count)
            .sort("startTime", SortOrder.DESC)
            .query(query)
            .trackTotalHits(true)

        return execute(builder, options)
    }

    fun names(countries: List<String>, states: List<String>, cities: List<String>, sites: List<String>,
              queryText: String?, options: SearchOptions): SearchResults {
        val textQuery = getTextQuery(queryText)
        val query = QueryBuilders.boolQuery().must(textQuery)

        val locationQuery = QueryBuilders.boolQuery()
        countries.forEach { locationQuery.should(QueryBuilders.termQuery("countries.keyword", it)) }
        states.forEach { locationQuery.should(QueryBuilders.termQuery("states.keyword", it)) }
        cities.forEach { locationQuery.should(QueryBuilders.termQuery("cities.keyword", it)) }
        sites.forEach { locationQuery.should(QueryBuilders.termQuery("sites.keyword", it)) }
        query.must(locationQuery)

        val builder = SearchSourceBuilder()
            .from(options.first)
            .size(options.count)
            .sort("startTime", SortOrder.DESC)
            .query(query)
            .trackTotalHits(true)

        return execute(builder, options)
    }

    fun nearby(lat: Double, lon: Double, radius: Double, queryText: String?, options: SearchOptions): SearchResults {
        val textQuery = getTextQuery(queryText)
        val query = QueryBuilders.boolQuery().must(textQuery)

        val locationQuery = QueryBuilders.boolQuery()
        locationQuery.should(QueryBuilders.geoDistanceQuery("bounds.min")
            .point(lat, lon)
            .distance(radius, DistanceUnit.KILOMETERS))
        locationQuery.should(QueryBuilders.geoDistanceQuery("bounds.max")
            .point(lat, lon)
            .distance(radius, DistanceUnit.KILOMETERS))
        query.must(locationQuery)

        val builder = SearchSourceBuilder()
            .from(options.first)
            .size(options.count)
            .sort(
                GeoDistanceSortBuilder("bounds.min", GeoPoint(lat, lon))
                .order(SortOrder.ASC)
                .unit(DistanceUnit.KILOMETERS)
                .sortMode(SortMode.MIN))
            .query(query)
            .trackTotalHits(true)

        return execute(builder, options)
    }

    private fun execute(builder: SearchSourceBuilder, options: SearchOptions): SearchResults {
        if (options.logSearch) {
            logger.info("Elastic Request: $builder")
        }

        val request = SearchRequest()
            .indices(tracksIndex)
            .source(builder)

        val response = esClient.search(request, RequestOptions.DEFAULT)
        if (response.status().status >= 300) {
            throw RangicInternalServerException("ElasticSearch request failed: $response")
        }

        val hits = response.hits.hits.map {
            Json.decodeFromString<GpsDto>(it.sourceAsString)
        }

        return SearchResults(
            hits.size,
            (response.hits.totalHits?.value ?: 0L).toInt(),
            hits
        )
    }

    private fun getTextQuery(queryText: String?): QueryBuilder {
        return if (queryText.isNullOrBlank()) {
            QueryBuilders.matchAllQuery()
        } else {
            QueryBuilders.queryStringQuery(queryText).fields(defaultSearchFields)
        }
    }
}
