package tracks.app.feature

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import org.slf4j.*

class RangicLogger private constructor() {
    val logger = LoggerFactory.getLogger(RangicLogger::class.java)

    class Configuration {
    }

    companion object Feature : ApplicationFeature<Application, Configuration, RangicLogger> {
        private val measureKey = AttributeKey<CallMeasure>("rangicLogging")
        override val key: AttributeKey<RangicLogger> = AttributeKey("Rangic Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RangicLogger {
            val loggingPhase = PipelinePhase("Logging")
            Configuration().apply(configure)
            val feature = RangicLogger()

            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, loggingPhase)
            pipeline.intercept(loggingPhase) {
                feature.before(call)
                proceed()
            }

            val postSendPhase = PipelinePhase("RangicLoggingPostSend")
            pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.After, postSendPhase)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) {
                try {
                    proceed()
                } finally {
                    feature.after(call)
                }
            }

            return feature
        }
    }

    private fun before(call: ApplicationCall) {
        call.attributes.put(measureKey, CallMeasure(System.nanoTime()))
    }

    private fun after(call: ApplicationCall) {
        var milliSeconds = -1L
        call.attributes.getOrNull(measureKey)?.let {
            milliSeconds = (System.nanoTime() - it.startTime) / 1000L / 1000L
        }

        val statusCode = call.response.status()?.value ?: -1
        val status = call.response.status()?.description ?: "Unknown"
        val method = call.request.local.method.value
        val path = call.request.path()
        val query = call.request.queryString()
        val bytesOut = call.response.headers["Content-Length"] ?: "Unspecified"

        val message = "statusCode=$statusCode status=$status method=$method path=$path " +
                "query=$query bytesOut=$bytesOut durationMs=$milliSeconds"
        if (statusCode >= 400) {
            logger.warn(message)
//            println("reading body...")
//            GlobalScope.async {
//                val x = call.receiveTextWithCorrectEncoding()
//                println("failed request body: '$x'")
//            }
//            val buffer = ByteBuffer.allocate(1024)
//            val numRead = call.request.receiveChannel().readAvailable(buffer)
//            if (numRead > 0) {
//                println("Read $numRead bytes")
//            }
//            val x = call.receiveStream().use {
//                it.readBytes().toString()
//            }
//                println("failed request body: '$x'")
//            println("failed request body: '${call.receive<ErrorResponse>()}'")
//            println("failed request body: '${call.receiveText()}'")
//                println("failed request body: '${call.request.receiveChannel().readAvailable()}'")
        } else {
            if (!path.startsWith("/files/thumbs/", true)) {
                logger.info(message)
            }
        }
    }
}

private data class CallMeasure(
    val startTime: Long
)

/**
 * Receive the request as String.
 * If there is no Content-Type in the HTTP header specified use ISO_8859_1 as default charset, see https://www.w3.org/International/articles/http-charset/index#charset.
 * But use UTF-8 as default charset for application/json, see https://tools.ietf.org/html/rfc4627#section-3
 */
//private suspend fun ApplicationCall.receiveTextWithCorrectEncoding(): String {
//    fun ContentType.defaultCharset(): Charset = when (this) {
//        ContentType.Application.Json -> Charsets.UTF_8
//        else -> Charsets.ISO_8859_1
//    }
//
//    val contentType = request.contentType()
//    val suitableCharset = contentType.charset() ?: contentType.defaultCharset()
//    return receiveStream().bufferedReader(charset = suitableCharset).readText()
//}
