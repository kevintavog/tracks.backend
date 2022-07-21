package tracks.app.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import tracks.app.utils.RunIndexer

fun Route.index() {
    post("/index/reindex") {
        val force = (call.parameters["force"] ?: "false").toBooleanStrict()
        // Don't wait for the index to finish
        GlobalScope.async {
            RunIndexer.run(force)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
