package tracks.indexer

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import tracks.core.elasticsearch.ElasticSearchInit
import tracks.core.services.GpxRepository
import tracks.core.utils.TrackConfiguration
import tracks.indexer.processors.GpsAnalyzer
import tracks.indexer.utils.EnumerateFolder
import tracks.indexer.utils.IndexGps
import kotlin.system.exitProcess

class IndexerMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                Indexer().main(args)
                exitProcess(0)
            } catch (t: Throwable) {
                t.printStackTrace()
                exitProcess(-1)
            }
        }
    }
}

class Indexer: CliktCommand() {
    val elasticUrl: String by option(
        "-e",
        "--elasticUrl",
        help="The URl for ElasticSearch")
        .required()
    val forceIndex: Boolean? by option(
        "-f",
        "--forceIndex",
        help="Force the index to be updated")
        .flag(default=false)
    val nameLookupUrl: String by option(
        "-n",
        "--nameLookupUrl",
        help="The URl for looking up names (ReverseNameLookup service)")
        .required()
    val processedFolder: String by option(
        "-p",
        "--processedFolder",
        help="The folder which the processed tracks will be written to")
        .required()
    val singleFile: String? by option(
        "-s",
        "--singleFile",
        help="The single file to process.")
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
        TrackConfiguration.timezoneUrl = timezoneUrl

        ElasticSearchInit.run()

        val fileList = mutableListOf<String>()
        singleFile?.let {
            fileList.add(it)
        } ?: run {
            println("Enumerating $tracksFolder")
            fileList.addAll(EnumerateFolder.at(tracksFolder, listOf("GPX")))
        }

        println("Found ${fileList.count()} files")
        fileList.forEach { filename ->
            val originalGps = TrackParser().parse(filename)
            val hasPoints = originalGps.tracks
                .map {
                    it.segments.isNotEmpty() && it.segments
                        .map { seg -> seg.points.isNotEmpty() }
                        .reduce { acc, b -> acc && b }
                }
                .reduce { acc, b -> acc && b }
            if (hasPoints) {
                val id = GpxRepository.toId(filename, originalGps.tracks.first().segments.first().points.first().time!!)
                if (forceIndex == true || singleFile != null || !IndexGps.exists(id)) {
                    println("Processing $filename")
                    val relativePath = if (filename.startsWith(tracksFolder))
                        filename.drop(tracksFolder.length) else filename
                    val workspace = GpsAnalyzer.process(originalGps)
                    GpxRepository.save(filename, workspace.processedGps!!)
//println("DID NOT INDEX!!!")
                    IndexGps.index(relativePath, workspace)
                }
            }
        }
    }
}
