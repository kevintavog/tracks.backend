package tracks.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import tracks.app.feature.RangicLogger
import tracks.app.models.toErrorResponse
import tracks.app.routes.index
import tracks.app.routes.search
import tracks.app.routes.tracks
import tracks.app.utils.RunIndexer
import tracks.core.RangicBadRequestException
import tracks.core.RangicInternalServerException
import tracks.core.RangicNotFoundException
import tracks.core.elasticsearch.ElasticSearchInit
import tracks.core.utils.TrackConfiguration

fun main(args: Array<String>) {
    App().main(args)
}

class App: CliktCommand() {
    val elasticUrl: String by option(
        "-e",
        "--elasticUrl",
        help="The URl for ElasticSearch")
        .required()
    val indexerJar: String by option(
        "-i",
        "--indexerJar",
        help="The path to the jar used for indexing")
        .required()
    val processedFolder: String by option(
        "-p",
        "--processedFolder",
        help="The folder in which the processed files are written to by the indexer")
        .required()
    val nameLookupUrl: String by option(
        "-n",
        "--nameLookupUrl",
        help="The URl for looking up names (OSM POI service)")
        .required()
    val tracksFolder: String by option(
        "-t",
        "--tracksFolder",
        help="The folder containing the tracks")
        .required()
    val timezoneUrl: String by option(
        "-z",
        "--timezoneUrl",
        help="The URL for the TimezoneLookup service")
        .required()


    override fun run() {
        TrackConfiguration.elasticSearchUrl = elasticUrl
        TrackConfiguration.processedTracksFolder = processedFolder
        TrackConfiguration.nameLookupUrl = nameLookupUrl
        TrackConfiguration.tracksFolder = tracksFolder
        TrackConfiguration.timezoneUrl = timezoneUrl
        RunIndexer.indexerJarPath = indexerJar

        ElasticSearchInit.run()
        println("ElasticSearch ${ElasticSearchInit.version}; ${TrackConfiguration.elasticSearchUrl}")

        embeddedServer(Netty, port = 5000) {
            App().apply { main() }
        }.start(wait = true)
    }

    fun Application.main() {
        install(CORS) {
            method(HttpMethod.Delete)
            method(HttpMethod.Get)
            method(HttpMethod.Options)
            method(HttpMethod.Patch)
            method(HttpMethod.Post)
            method(HttpMethod.Put)

            anyHost()
            host("localhost:8080")
        }

        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
                setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                // setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            }
        }

        install(StatusPages) {
            exception<RangicBadRequestException> { cause ->
                context.respond(HttpStatusCode.BadRequest, toErrorResponse(cause))
            }
            exception<RangicInternalServerException> { cause ->
                context.respond(HttpStatusCode.InternalServerError, toErrorResponse(cause))
            }
            exception<RangicNotFoundException> { cause ->
                context.respond(HttpStatusCode.NotFound, toErrorResponse(cause))
            }
        }

        install(RangicLogger)

        routing {
            index()
            search()
            tracks()
        }
    }
}
