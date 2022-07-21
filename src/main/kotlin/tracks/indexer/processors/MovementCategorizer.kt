package tracks.indexer.processors

import tracks.core.models.Gps
import tracks.core.models.TransportationMode
import tracks.core.models.dateTime
import tracks.indexer.models.MovementCategory
import tracks.indexer.models.MovementItem
import tracks.indexer.utils.DateTimeFormatter
import java.time.temporal.ChronoUnit

object MovementCategorizer {
    fun process(gpx: Gps): List<MovementItem> {
        val segments = gpx.tracks.flatMap { it.segments }.sortedBy { it.points.first().time }
        val stops = gpx.waypoints.sortedBy { it.time }

        var segmentIndex = 0
        var stopIndex = 0
        val movementList = mutableListOf<MovementItem>()
        var lastSegmentCategory: MovementCategory? = null
        do {
            // Determine which is first
            val segment = if (segmentIndex >= segments.size) null else segments[segmentIndex]
            val stop = if (stopIndex >= stops.size) null else stops[stopIndex]
            if (segment == null && stop == null) {
                break
            }

            if (stop != null && (segment == null || stop.time!! < segment.points.first().time!!)) {
                val category = if (lastSegmentCategory != null && lastSegmentCategory == MovementCategory.VEHICLE)
                    MovementCategory.STOPPED_VEHICLE else MovementCategory.STOPPED_WALKING
                if (stop.rangicStart != null && stop.rangicEnd != null) {
                    movementList.add(MovementItem(stop.rangicStart!!.dateTime(), stop.rangicEnd!!.dateTime(), category))
                } else {
                    val time = DateTimeFormatter.parse(stop.time!!)
                    movementList.add(MovementItem(time, time, category))
                }
                stopIndex += 1
            } else if (segment != null) {
                // Look for gaps between segments and categorize them as such
                if (movementList.isNotEmpty()) {
                    val diff = movementList.last().endTime.until(segment.points.first().dateTime(), ChronoUnit.SECONDS)
                    if (diff >= 15) {
                        movementList.add(MovementItem(
                            movementList.last().endTime.plusSeconds(1),
                            segment.points.first().dateTime().minusSeconds(1),
                            MovementCategory.GAP_VEHICLE
                        ))
                    }
                }

                val segmentCategory = segmentCategory(segment.speedKmh)
                movementList.add(MovementItem(segment.points.first().dateTime(), segment.points.last().dateTime(), segmentCategory))
                segmentIndex += 1
                lastSegmentCategory = segmentCategory
            }
        } while (segmentIndex < segments.size || stopIndex < stops.size)

        return movementList
    }

    private fun segmentCategory(speedKmh: Double): MovementCategory {
        val transportTypes = TransportationCalculator.get(speedKmh)
        return when(transportTypes.size) {
            0 -> { throw Exception("No transportation types for $speedKmh") }
            else -> {
                val first = transportTypes.first()
                if (speedKmh <= 0.01 || first.mode == TransportationMode.foot && first.probability >= 0.65) {
                    MovementCategory.WALKING
                } else {
                    MovementCategory.VEHICLE
                }
            }
        }
    }
}
