package tracks.app.routes

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import tracks.core.RangicBadRequestException
import tracks.core.RangicInternalServerException
import tracks.core.RangicNotFoundException
import tracks.core.elasticsearch.ElasticClient
import tracks.core.models.GpsDto
import tracks.core.models.SearchOptions
import tracks.core.services.GpxRepository
import tracks.core.utils.TrackConfiguration
import java.io.File

suspend fun getFile(call: ApplicationCall, handler: suspend (id: String, track: GpsDto) -> Unit) {
    val id = call.parameters["id"] ?: throw RangicBadRequestException("Missing 'id'")
    val results = ElasticClient().use {
        it.getId(id, SearchOptions(0, 10, true))
    }
    return when(results.totalMatches) {
        0 -> { throw RangicNotFoundException("No track with id: $id")}
        1 -> { handler(id, results.items.first()) }
        else -> { throw RangicInternalServerException("More than one track with id: $id")}
    }
}

fun Route.tracks() {
    get("/{id}") {
        getFile(call) { _, track ->
            context.respond(track)
        }
    }

    get("/gpx/{id}") {
        getFile(call) { id, _ ->
            context.respondFile(File(GpxRepository.pathFromId(id)))
        }
    }

    get("/original/{id}") {
        getFile(call) { _, track ->
            context.respondFile(File(TrackConfiguration.tracksFolder, track.path))
        }
    }
}
