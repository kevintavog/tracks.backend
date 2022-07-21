package tracks.indexer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import tracks.core.models.Gps
import tracks.core.models.GpsBadElfPointExtension
import tracks.core.models.toXml
import java.io.File
import java.io.FileOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


class TrackParser {
    private val xmlMapper = XmlMapper(JacksonXmlModule().apply {
        setDefaultUseWrapper(false)
    }).registerKotlinModule()
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun parse(filename: String): Gps {
        val gps: Gps = xmlMapper.readValue(File(filename))

        // The BadElf unit exports GPX with an extension containing the speed AND the normal speed
        // field is null - this code patches up the normal speed field in that case.
        gps.tracks.forEach { track ->
            track.segments.forEach { segment ->
                segment.points.forEach { point ->
                    point.extensions?.forEach { extension ->
                        if (extension is GpsBadElfPointExtension && extension.speed != null) {
                            point.speed = extension.speed
                        }
                    }
                }
            }
        }
        return gps
    }

    fun save(gps: Gps, filename: String) {
        val xml = gps.toXml()

        val tr = TransformerFactory.newInstance().newTransformer()
        tr.setOutputProperty(OutputKeys.INDENT, "yes")
        tr.setOutputProperty(OutputKeys.METHOD, "xml")
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8")

        tr.transform(
            DOMSource(xml),
            StreamResult(FileOutputStream(filename))
        )
    }
}
