package tracks.app.utils

import org.slf4j.LoggerFactory
import tracks.core.RangicInternalServerException
import tracks.core.utils.ProcessInvoker
import tracks.core.utils.TrackConfiguration

object RunIndexer {
    val logger = LoggerFactory.getLogger(RunIndexer::class.java)
    var indexerJarPath = ""
    fun run(force: Boolean) {
        if (indexerJarPath.isEmpty()) {
            throw RangicInternalServerException("The Indexer jar path has not been set.")
        }

        val arguments = mutableListOf(
            "-cp",
            indexerJarPath,
            "tracks.indexer.IndexerMain",
            "-e",
            TrackConfiguration.elasticSearchUrl,
            "-n",
            TrackConfiguration.nameLookupUrl,
            "-p",
            TrackConfiguration.processedTracksFolder,
            "-t",
            TrackConfiguration.tracksFolder,
            "-z",
            TrackConfiguration.timezoneUrl
        )
        if (force) {
            arguments.add("-f")
        }

        logger.info("Starting indexing: $arguments")
        val process = ProcessInvoker.run("java", arguments)
        if (process.exitCode != 0) {
            logger.error("ERROR: Indexing failed:")
            logger.error("exit code: ${process.exitCode}")
            logger.error("output: [${process.output}]")
            logger.error("error: [${process.error}]")
        } else {
            logger.info("Indexer completed successfully")
        }
    }
}
