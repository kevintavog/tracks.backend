package tracks.diagserver.routes

import io.ktor.response.*
import io.ktor.routing.*
import tracks.diagserver.diagFilesFolder
import java.io.File

fun Route.files() {
    get("/original") {
        context.respondFile(File("$diagFilesFolder/original.gpx"))
    }

    get("/track") {
        context.respondFile(File("$diagFilesFolder/processed.gpx"))
    }

    get("/diag") {
        context.respondFile(File("$diagFilesFolder/diag.json"))
    }

    get("/vectors") {
        context.respondFile(File("$diagFilesFolder/vectors.gpx"))
    }

}
