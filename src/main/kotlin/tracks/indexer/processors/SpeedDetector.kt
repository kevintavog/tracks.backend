package tracks.indexer.processors

import tracks.core.models.*
import tracks.core.utils.Converter
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.LowQualityRun
import tracks.indexer.models.LowQualityType

object SpeedDetector {
    // When both stops are of short duration
    //    They must be quite close by either distance or time
    const val shortMaxMetersByDistance = 5
    const val shortMaxSecondsByDistance = 60
    const val shortMaxMetersByTime = 20
    const val shortMaxSecondsByTime = 15


    // When at least one stop is of long duration, the duration & distance are increased
    const val minLongDuration = 4 * 60
    const val longMaxMeters = 20
    const val longMaxSeconds = 5 * 60

    fun isMoving(kmh: Double) = kmh > AnalyzerSettings.movementMinSpeedKmh

    fun processLowSpeed(workspace: GpxWorkspace) {
        process(workspace, AnalyzerSettings.movementMinSpeedMetersSecond, 15, LowQualityType.LOW_MOVEMENT) {
            pt -> pt.calculatedMps
        }
    }

    /**
     * Remove data from areas identified as low movement runs; if there are low movement runs, this will create
     * new segments between them.
     */
    fun trimLowSpeedRuns(workspace: GpxWorkspace) {
        val lowSpeedRunList = workspace.lowQualityRuns
            .filter { it.type == LowQualityType.LOW_MOVEMENT || it.type == LowQualityType.NO_MOVEMENT }
            .filter { it.duration() >= AnalyzerSettings.minSecondsLowMovementForStop }
            .sortedBy { it.start.time }
        if (lowSpeedRunList.isEmpty()) {
            return
        }

        val lqrIterator = lowSpeedRunList.iterator()
        var currentLqr = lqrIterator.next()
        val newSegments = mutableListOf<GpsTrackSegment>()
        workspace.allSegments.forEach { segment ->
            var includedPoints = mutableListOf<GpsTrackPoint>()
            segment.points.forEach { point ->
                if (point.time!! < currentLqr.start.time!!) {
                    includedPoints.add(point)
                } else if (point.time!! > currentLqr.end.time!!) {
                    if (lqrIterator.hasNext()) {
                        currentLqr = lqrIterator.next()
                    }
                    includedPoints.add(point)
                } else {
                    if  (includedPoints.isNotEmpty()) {
                        newSegments.add(GpsTrackSegment(includedPoints))
                        includedPoints = mutableListOf()
                    }
                }
            }
            if (includedPoints.isNotEmpty()) {
                newSegments.add(GpsTrackSegment(includedPoints))
            }
        }

        workspace.allSegments = newSegments
    }

    fun process(workspace: GpxWorkspace, minSpeedMps: Double, minDuration: Int, type: LowQualityType, toValue: (pt: GpsTrackPoint) -> Double?) {
        var prevSpeed: LowQualityRun? = null
        workspace.allSegments.forEach { segment ->
            val matchingPoints = mutableListOf<GpsTrackPoint>()
            segment.points.forEach { point ->
                toValue(point)?.let {
                    if (it <= minSpeedMps) {
                        matchingPoints.add(point)
                    } else {
                        if (matchingPoints.isNotEmpty()) {
                            val duration = matchingPoints.first().durationSeconds(matchingPoints.last())
                            if (duration >= minDuration) {
                                var createNewItem = true
                                prevSpeed?.let { prev ->
                                    val prevSeconds = prev.end.durationSeconds(matchingPoints.first()).toInt()
                                    val prevMeters = (prev.end.distanceKm(matchingPoints.first()) * 1000).toInt()
                                    val useLongCheck = prev.duration() >= minLongDuration || duration.toInt() > minLongDuration
                                    val combineStops = if (useLongCheck) {
                                        prevMeters <= longMaxMeters && prevSeconds <= longMaxSeconds
                                    } else {
                                        (prevMeters <= shortMaxMetersByDistance && prevSeconds <= shortMaxSecondsByDistance) ||
                                                (prevMeters <= shortMaxMetersByTime && prevSeconds <= shortMaxSecondsByTime)
                                    }
                                    if (combineStops) {
                                        createNewItem = false
                                        prev.end = matchingPoints.last()

                                        val seconds = prev.start.durationSeconds(prev.end).toInt()
                                        val speed = Converter.readableSpeed(prev.start.speedKmh(prev.end))
                                        prev.description = "Low speed is $speed kmh for $seconds seconds"
                                    }
                                }

                                if (createNewItem) {
                                    val meters = matchingPoints.map { p -> p.calculatedMeters }.reduce { acc, meters -> acc + meters }
                                    val seconds = matchingPoints.first().durationSeconds(matchingPoints.last()).toInt()
                                    val speed =
                                        Converter.readableSpeed(matchingPoints.first().speedKmh(matchingPoints.last()))
                                    val lqr = LowQualityRun(
                                        matchingPoints.first(),
                                        matchingPoints.last(),
                                        type,
                                        "Low speed is $speed kmh for $seconds seconds",
                                        meters / 1000.0
                                    )
                                    workspace.lowQualityRuns.add(lqr)
                                    prevSpeed = lqr
                                }
                            }
                            matchingPoints.clear()
                        }
                    }
                }
            }

            if (matchingPoints.isNotEmpty()) {
                val duration = matchingPoints.first().durationSeconds(matchingPoints.last())
                if (duration >= minDuration) {
                    val seconds = matchingPoints.first().durationSeconds(matchingPoints.last()).toInt()
                    val speed = Converter.readableSpeed(matchingPoints.first().speedKmh(matchingPoints.last()))
                    workspace.lowQualityRuns.add(LowQualityRun(
                        matchingPoints.first(),
                        matchingPoints.last(),
                        type,
                    "Low speed is $speed kmh for $seconds seconds"))
                }
                matchingPoints.clear()
            }
        }
    }
}
