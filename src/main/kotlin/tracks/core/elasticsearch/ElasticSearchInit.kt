package tracks.core.elasticsearch

import org.apache.http.HttpHost

import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.xcontent.XContentType
import tracks.core.utils.TrackConfiguration

object ElasticSearchInit {
    var version = ""

    fun run() {
        RestHighLevelClient(RestClient.builder(
            HttpHost(TrackConfiguration.elasticSearchHost, TrackConfiguration.elasticSearchPort))
        ).use {
            val main = it.info(RequestOptions.DEFAULT)
            version = main.version.number

            if (!doesIndexExist(it, ElasticClient.tracksIndex)) {
                createIndex(it, ElasticClient.tracksIndex, IndexInit.tracksMappings)
            }
        }
    }

    private fun createIndex(client: RestHighLevelClient, index: String, mappings: String) {
        println("Creating ElasticSearch index '$index'")
        val request = CreateIndexRequest(index)
            .settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("number_of_replicas", 0)
                .put("max_result_window", 100000)
            )
            .mapping(mappings, XContentType.JSON)
        client.indices().create(request, RequestOptions.DEFAULT)
    }

    private fun doesIndexExist(client: RestHighLevelClient, index: String): Boolean {
        return client.indices().exists(GetIndexRequest(index), RequestOptions.DEFAULT)
    }
}
