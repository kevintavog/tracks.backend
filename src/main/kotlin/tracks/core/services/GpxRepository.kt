package tracks.core.services

import tracks.core.models.Gps
import tracks.core.models.dateTime
import tracks.core.models.toXml
import tracks.core.utils.TrackConfiguration
import tracks.indexer.utils.DateTimeFormatter
import java.io.FileOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.File

object GpxRepository {
    fun toId(originalFilename: String, time: String): String {
        val originalFile = File(originalFilename)
        val date = DateTimeFormatter.parse(time)
        val datePrefix = String.format("%04d-%02d-%02d", date.year, date.month.value, date.dayOfMonth)
        return if (originalFile.name.startsWith(datePrefix)) {
            originalFile.name
        } else {
            "$datePrefix-${originalFile.name}"
        }
    }

    fun toId(originalFilename: String, gps: Gps): String {
        return toId(originalFilename, gps.tracks.first().segments.first().points.first().time!!)
    }

    fun pathFromId(id: String): String {
        // The id is the date as YYYY-MM-DD
        val year = id.subSequence(0, 4)
        return "${TrackConfiguration.processedTracksFolder}/$year/$id"
    }

    fun save(originalFilename: String, gps: Gps) {
        if (gps.tracks.isEmpty() || gps.tracks.first().segments.isEmpty()) {
            println("WARNING: Not saving $originalFilename (No tracks or segments)")
            return
        }

        val name = toId(originalFilename, gps)
        val date = gps.tracks.first().segments.first().points.first().dateTime()
        val filename = "${TrackConfiguration.processedTracksFolder}/${date.year}/$name"
        saveAs(gps, File(filename))
    }

    fun saveAs(gps: Gps, file: File) {
        file.parentFile.mkdirs()

        val xml = gps.toXml()

        val tr = TransformerFactory.newInstance().newTransformer()
        tr.setOutputProperty(OutputKeys.INDENT, "yes")
        tr.setOutputProperty(OutputKeys.METHOD, "xml")
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8")

        tr.transform(
            DOMSource(xml),
            StreamResult(FileOutputStream(file.absolutePath))
        )
    }
}
