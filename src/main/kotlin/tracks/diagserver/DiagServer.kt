package tracks.diagserver

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
import tracks.app.models.toErrorResponse
import tracks.core.RangicBadRequestException
import tracks.core.RangicInternalServerException
import tracks.core.RangicNotFoundException
import tracks.diagserver.routes.files

var diagFilesFolder: String = ""

fun main(args: Array<String>) {
    DiagServer().main(args)
}

class DiagServer: CliktCommand() {
    private val filesFolder: String by option(
        "-f",
        "--filesFolder",
        help="The folder containing the diag files")
        .required()

    override fun run() {
        diagFilesFolder = filesFolder
        embeddedServer(Netty, port = 7000) {
            DiagServer().apply { main() }
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

        routing {
            files()
        }
    }
}
