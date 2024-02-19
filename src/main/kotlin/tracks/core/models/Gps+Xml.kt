package tracks.core.models

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import tracks.core.utils.Converter
import javax.xml.parsers.DocumentBuilderFactory

fun Gps.toXml(): Element {
    val dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    val root = dom.createElement("gpx")
    root.setAttribute("version", "1.1")
    root.setAttribute("creator", "Tracks")
    root.setAttribute("xmlns", "http://www.topografix.com/GPX/1/0")
    root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
    root.setAttribute("xmlns:rangic", "http://rangic.com/xmlschemas/GpxExtensionsV1")

    this.waypoints.forEach { waypoint ->
        root.appendChild(waypoint.toXml(dom))
    }

    this.tracks.forEach { track ->
        root.appendChild(track.toXml(dom))
    }

    val extension = dom.createElement("extensions")
    extension.appendChild(dom.rangicElement("kilometers", kilometers.toString()))
    extension.appendChild(dom.rangicElement("seconds", seconds.toString()))
    extension.appendChild(dom.rangicElement("kmh", Converter.metersPerSecondToKilometersPerHour(kilometers * 1000.0 / seconds).toString()))
    bounds?.let {
        extension.appendChild(it.toXml(dom))
    }

    extension.appendChild(dom.addArray("countryNames", "country", countryNames))
    extension.appendChild(dom.addArray("stateNames", "state", stateNames))
    extension.appendChild(dom.addArray("cityNames", "city", cityNames))
    extension.appendChild(sitesToXml(dom))
    root.appendChild(extension)
    return root
}

fun GpsBounds.toXml(dom: Document): Node {
    return dom.rangicElement("bounds", null, mapOf(
        "minLat" to minlat.toString(),
        "minLon" to minlon.toString(),
        "maxLat" to maxlat.toString(),
        "maxLon" to maxlon.toString()
    ))
}

fun GpsWaypoint.toXml(dom: Document): Node {
    val xml = dom.element("wpt", null, mapOf(
        "lat" to lat.toString(),
        "lon" to lon.toString()
    ))
    xml.appendChild(dom.element("time", time))
    xml.appendChild(dom.element("name", name))
    cmt?.let {
        xml.appendChild(dom.element("cmt", it))
    }
    desc?.let {
        xml.appendChild(dom.element("desc", it))
    }

    rangicEnd?.let {
        val extXml = dom.createElement("extensions")
        rangicStart?.let { start ->
            extXml.appendChild(
                dom.rangicElement(
                    "begin", null, mapOf(
                        "lat" to start.lat.toString(),
                        "lon" to start.lon.toString(),
                        "time" to start.time.toString()
            )))
        } ?: run {
            extXml.appendChild(
                dom.rangicElement(
                    "begin", null, mapOf(
                        "lat" to lat.toString(),
                        "lon" to lon.toString(),
                        "time" to time.toString()
            )))
        }

        extXml.appendChild(dom.rangicElement("finish", null, mapOf(
            "lat" to it.lat.toString(),
            "lon" to it.lon.toString(),
            "time" to it.time.toString()
        )))

        extXml.appendChild(dom.rangicElement("distanceKm", rangicDistanceKm.toString()))
        extXml.appendChild(dom.rangicElement("durationSeconds", rangicDurationSeconds.toString()))
        extXml.appendChild(dom.rangicElement("speedKmh", rangicSpeedKmh.toString()))
        extXml.appendChild(dom.rangicElement("description", rangicDescription))
        extXml.appendChild(dom.rangicElement("stopType", rangicStopType))

        xml.appendChild(extXml)
    }
    return xml
}

fun GpsTrack.toXml(dom: Document): Node {
    val xml = dom.createElement("trk")
    this.segments.forEach { segment ->
        xml.appendChild(segment.toXml(dom))
    }

    val extension = dom.createElement("extensions")
    extension.appendChild(dom.rangicElement("kilometers", kilometers.toString()))
    extension.appendChild(dom.rangicElement("seconds", seconds.toString()))
    extension.appendChild(dom.rangicElement("kmh", Converter.metersPerSecondToKilometersPerHour(kilometers * 1000.0 / seconds).toString()))
    extension.appendChild(bounds.toXml(dom))
    xml.appendChild(extension)

    return xml
}

fun GpsTrackSegment.toXml(dom: Document): Node {
    val xml = dom.createElement("trkseg")
    this.points.forEach { point ->
        xml.appendChild(point.toXml(dom))
    }

    val extension = dom.createElement("extensions")
    extension.appendChild(dom.rangicElement("kilometers", kilometers.toString()))
    extension.appendChild(dom.rangicElement("seconds", seconds.toString()))
    extension.appendChild(dom.rangicElement("kmh", speedKmh.toString()))
    extension.appendChild(dom.rangicElement("course", course.toString()))
    extension.appendChild(bounds.toXml(dom))
    if (transportationTypes.isNotEmpty()) {
        extension.appendChild(transportationTypesToXml(dom, transportationTypes))
    }
    xml.appendChild(extension)

    return xml
}

fun GpsTrackPoint.toXml(dom: Document): Node {
    val xml = dom.element("trkpt", null, mapOf(
        "lat" to lat.toString(),
        "lon" to lon.toString()
    ))
    xml.appendChild(dom.element("ele", ele.toString()))
    xml.appendChild(dom.element("time", time))
    xml.appendChild(dom.element("course", (course ?: 0.0).toInt().toString()))
    xml.appendChild(dom.element("speed", (speed ?: 0.0).toString()))

    fix?.let {
        xml.appendChild(dom.element("fix", it))
    }
    hdop?.let {
        xml.appendChild(dom.element("hdop", it.toString()))
    }
    pdop?.let {
        xml.appendChild(dom.element("pdop", it.toString()))
    }
    vdop?.let {
        xml.appendChild(dom.element("vdop", it.toString()))
    }

    val extension = dom.createElement("extensions")
    extension.appendChild(dom.rangicElement("calculatedMeters", calculatedMeters.toString()))
    extension.appendChild(dom.rangicElement("calculatedSeconds", calculatedSeconds.toString()))
    extension.appendChild(dom.rangicElement("calculatedKmh", calculatedKmh.toString()))
    extension.appendChild(dom.rangicElement("calculatedCourse", calculatedCourse.toString()))
    xml.appendChild(extension)

    return xml
}

fun Document.rangicElement(name: String, value: String?, attributes: Map<String, String>? = null): Element {
    val ele = this.createElement("rangic:$name")
    value?.let {
        ele.appendChild(this.createTextNode(it))
    }
    attributes?.forEach { (k, v) ->
        ele.setAttribute(k, v)
    }
    return ele
}

fun Document.element(name: String, value: String?, attributes: Map<String, String>? = null): Element {
    val ele = this.createElement(name)
    value?.let {
        ele.appendChild(this.createTextNode(it))
    }
    attributes?.forEach { (k, v) ->
        ele.setAttribute(k, v)
    }
    return ele
}

fun Document.addArray(parentName: String, itemName: String, items: List<String>): Element {
    val parent = createElement("rangic:$parentName")
    items.forEach {
        parent.appendChild(element(itemName, it))
    }
    return parent
}

fun transportationTypesToXml(dom: Document, ttList: List<TransportationType>): Element {
    val parent = dom.createElement("rangic:transportationTypes")
    ttList.forEach { tt ->
        val ttElement = dom.createElement("transportationType")
        ttElement.setAttribute("mode", tt.mode.toString())
        ttElement.setAttribute("probability", tt.probability.toString())
        parent.appendChild(ttElement)
    }
    return parent
}

fun Gps.sitesToXml(dom: Document): Element {
    val parent = dom.createElement("rangic:sites")
    sites.forEach { site ->
        val siteElement = dom.createElement("site")
        siteElement.appendChild(dom.element("lat", site.lat.toString()))
        siteElement.appendChild(dom.element("lon", site.lon.toString()))
        site.name.forEach { name ->
            siteElement.appendChild(dom.element("name", name))
        }
        parent.appendChild(siteElement)
    }
    return parent
}
