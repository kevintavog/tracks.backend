package tracks.diag

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import tracks.core.models.*
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.processors.AnalyzerSettings
import tracks.indexer.processors.TrajectoryByCourse
import tracks.indexer.utils.*
import java.time.ZonedDateTime
import kotlin.math.absoluteValue

object DiagAnalyzer {
    fun prune(trajectories: List<GpsTrajectory>, transitions: List<ZonedDateTime>, runs: List<ConvertedRun>): List<GpsTrajectory> {
        println("Runs: ${runs.map { "${it.name}, ${it.type}, ${it.startTime}" } } ")
        val response = mutableListOf<GpsTrajectory>()
        if (transitions.isEmpty()) {
            println("time, del bearing, del meters, del seconds bearing, meters, kmh")
            trajectories.forEach { tj ->
                if (tj.meters() > 6.0) {
                    val diffBearing =
                        if (response.isEmpty()) 0.0 else GeoCalculator.bearingDelta(tj.bearing, response.last().bearing)
                    val distanceMetersDiff =
                        if (response.isEmpty()) 0.0 else tj.start.distanceKm(response.last().end) * 1000.0
                    val secondsDiff = if (response.isEmpty()) 0.0 else response.last().end.durationSeconds(tj.start)
                    response.add(tj)
//println("${tj.start.timeOnly()}: ${diffBearing.toInt().leadSpaces(4)}°Δ  ${distanceMetersDiff.toInt().leadSpaces(2)}mΔ  " +
//        "${secondsDiff.toInt().leadSpaces(3)}sΔ  " +
//        "${tj.bearing.leadSpaces(3)}° ${tj.meters().leadSpaces(5, 1)}m  ${tj.kmh().leadSpaces(6, 2)}kmh")
                    println(
                        "${tj.start.timeOnly()}, ${diffBearing.toInt()}, ${distanceMetersDiff.toInt()}, " +
                                "${secondsDiff.toInt()}, " +
                                "${tj.bearing}, ${tj.meters()}, ${tj.kmh()}"
                    )
                }
            }
        } else {
            val percents = listOf(10.0, 20.0, 70.0, 80.0, 90.0)
            println("time, ${emitHeadingDetail("bearing", percents)}, ${emitHeadingDetail("meters", percents)}, ${emitHeadingDetail("seconds", percents)}, ${emitHeadingDetail("kmh", percents)}")

            // Find the first trajectory that starts no earlier than the first transition
            val longVectors = trajectories //.filter { it.meters() > 6.0}
            response.addAll(longVectors)
            var vectoryIndex = 0
            while (vectoryIndex < longVectors.size && longVectors[vectoryIndex].start.dateTime() < transitions[0]) {
                vectoryIndex += 1
            }
//println("First: ${transitions[0]} matched to #${trajectoryIndex + 1} ${trajectories[trajectoryIndex].start.timeOnly()}")
            var transitionIndex = 1
            val collected = mutableListOf<GpsTrajectory>()
            while (vectoryIndex < longVectors.size && transitionIndex < transitions.size) {
                // Collect trajectories until the start of a transition, then emit trajectory stats
//println("Comparing ${trajectories[trajectoryIndex].start.timeOnly()} to ${transitions[transitionIndex]}")
                if (longVectors[vectoryIndex].start.dateTime() >= transitions[transitionIndex]) {
                    emitTrajectoryStats(percents, collected)
                    collected.clear()
                    transitionIndex += 1
                }
                collected.add(longVectors[vectoryIndex])
                vectoryIndex += 1
            }

            emitTrajectoryStats(percents, collected)
        }
        return response
    }

    fun emitTrajectoryStats(percents: List<Double>, list: List<GpsTrajectory>) {
        if (list.isEmpty()) { return }
        val diffBearing = DescriptiveStatistics()
        val diffMeters = DescriptiveStatistics()
        val diffSeconds = DescriptiveStatistics()
        val kmh = DescriptiveStatistics()
        list.forEachIndexed() { index, tj ->
            kmh.addValue(tj.kmh())
            if (index <= 0) {
                diffBearing.addValue(0.0)
                diffMeters.addValue(0.0)
                diffSeconds.addValue(0.0)
            } else {
                val prev = list[index - 1]
                diffBearing.addValue(GeoCalculator.bearingDelta(prev.bearing, tj.bearing).toDouble())
                diffMeters.addValue(tj.start.distanceKm(prev.end) * 1000.0)
                diffSeconds.addValue(prev.end.durationSeconds(tj.start))

            }
        }

        val bearingDetails = emitDetails(percents, diffBearing)
        val metersDetails = emitDetails(percents, diffMeters)
        val secondsDetails = emitDetails(percents, diffSeconds)
        val kmhDetails = emitDetails(percents, kmh)
        println("${list.first().start.timeOnly()}, $bearingDetails, $metersDetails, $secondsDetails, $kmhDetails")
    }

    private fun emitHeadingDetail(name: String, percents: List<Double>): String {
        return percents.joinToString(", ") { "$name ${it.toInt()}" }
    }

    private fun emitDetails(percents: List<Double>, stats: DescriptiveStatistics): String {
        return percents.joinToString(", ") { stats.getPercentile(it).toString() }
    }

    fun process(workspace: GpxWorkspace, segments: List<GpsTrackSegment>) {
        // Collect stats for: speed, acceleration, distance, time delta, bearing, bearing delta
        val oneMeterTrajectories = TrajectoryByCourse.process(workspace.oneMeterSegments)
        val allPoints = oneMeterTrajectories.map { it.start as GpsTrackPoint }
        if (allPoints.isEmpty()) { return }
//        val allPoints = segments.flatMap { it.points }
//        if (allPoints.isEmpty()) {
//            println("There are no points to process")
//            return
//        }

        val allFiveSecondPoints = allPoints // PointCalculator.process(FilterPoints.byTime(allPoints, 5.0))
//        val allFiveSecondPoints = PointCalculator.process(FilterPoints.byDistance(allPoints, 1.0))
        val firstPoint = allFiveSecondPoints.first()
        val lastPoint = allFiveSecondPoints.last()
        val totalSeconds = firstPoint.durationSeconds(lastPoint)
        println("There are ${segments.size} segments, ${allFiveSecondPoints.size} points, $totalSeconds seconds ranging from ${firstPoint.timeOnly()} - ${lastPoint.timeOnly()}")
//        workspace.breaks.forEach {
//            println("Break ${it.start.timeOnly()} ${it.end.timeOnly()}")
//        }
//        workspace.stops.forEach {
//            println("Stop ${it.start.timeOnly()} - ${it.end.timeOnly()}")
//        }
//        findVisits(allPoints)

//        val calcSpeed = allFiveSecondPoints.map { it.calculatedKmh }.descriptiveStatistics
//        val calcAcceleration = allFiveSecondPoints.map { it.calculatedAcceleration.absoluteValue }.descriptiveStatistics
//        val timeDelta = allFiveSecondPoints.map { it.calculatedSeconds }.descriptiveStatistics
//
//        val calcCourseDelta = allFiveSecondPoints.map { it.deltaCourse.absoluteValue }.descriptiveStatistics
//        val smoothCourseDelta = allFiveSecondPoints.map { it.smoothedDeltaCourse.absoluteValue }.descriptiveStatistics
//        val angVel = allFiveSecondPoints.map { it.calculatedAngularVelocity }.descriptiveStatistics
//
//        val smoothSpeed = allFiveSecondPoints.map { it.smoothedKmh }.descriptiveStatistics
//        val smoothMeters = allFiveSecondPoints.map { it.smoothedMeters }.descriptiveStatistics
//        val calcMeters = allFiveSecondPoints.map { it.calculatedMeters }.descriptiveStatistics

//        val deviceSpeed = allPoints.map { it.speed }.filterNotNull().descriptiveStatistics
//        val deviceAcceleration = allPoints.map { it.deviceAcceleration }.descriptiveStatistics
//        val hdop = allPoints.map { it.hdop }.filterNotNull().descriptiveStatistics

//        println(details("course delta ", calcCourseDelta))
//        println(details("smooth course", smoothCourseDelta))
//        println(details("tao          ", tao))
//        println(details("angulr veloci", angVel))
//
//        println(details("calc speed   ", calcSpeed))
//        println(details("calc accel   ", calcAcceleration))
//        println(details("smooth speed ", smoothSpeed))
//        println(details("smooth meters", smoothMeters))
//        println(details("device speed ", deviceSpeed))
//        println(details("device accel ", deviceAcceleration))
//        println(details("hdop        ", hdop))

//        println()
//        printTableHeader()
//        printTableRow("Time Delta", timeDelta)
//        printTableRow("Course Delta", calcCourseDelta)
//        printTableRow("Smooth Course", smoothCourseDelta)
//        printTableRow("Angular Velocity", angVel)
//        printTableRow("Calc Speed", calcSpeed)
//        printTableRow("Calc Acceleration", calcAcceleration)
//        printTableRow("Smooth Speed", smoothSpeed)
//        printTableRow("Smooth Meters", smoothMeters)
//        printTableRow("Calc Meters", calcMeters)

        val windowSeconds = 5 * 60
        val slideSeconds = 60

//        val thePoints = segments.flatMap { it.points }
//        var index = 0
//        while (index < thePoints.size) {
//            if (grade(thePoints, index, windowSeconds)) {
//                break
//            }
//
//            val start = thePoints[index]
//            while (index < thePoints.size && start.durationSeconds(thePoints[index]).toInt() < slideSeconds) {
//                index += 1
//            }
//        }


//        println("time,diff course,kmh,mps")
//        thePoints.forEach {
//            println("${it.timeOnly()},${it.deltaCourse},${readable(it.calculatedKmh)},${readable(it.calculatedMps)}")
//        }
//        println()
//        printFullTable("14:33:03", "14:43:22", thePoints)
//        println()
//        printFullTable("14:43:23", "14:58:18", thePoints)
//        println()
//        printFullTable("14:58:23", "15:01:59", thePoints)
//        println()
//        printFullTable("15:02:00", "15:16:40", thePoints)

        println()
//        printSynopsisTable(
//            listOf("Walk dog", "Outside stop", "Walk", "Inside stop"),
//            listOf("14:33:03", "14:43:23", "14:58:23", "15:02:00"),
//            listOf("14:43:22", "14:58:18", "15:01:59", "15:16:40"),
//            thePoints)
    }

    private fun printSynopsisTable(names: List<String>, starts: List<String>, ends: List<String>, points: List<GpsTrackPoint>) {
        println(",${names.joinToString(",")}")

        val statsAccel = mutableListOf<DescriptiveStatistics>()
        val statsNZAccel = mutableListOf<DescriptiveStatistics>()
        val statsNZCourseDiffs = mutableListOf<DescriptiveStatistics>()
        val statsCourseDiffs = mutableListOf<DescriptiveStatistics>()
        val statsKmh = mutableListOf<DescriptiveStatistics>()
        val statsNZKmh = mutableListOf<DescriptiveStatistics>()
        for (setIndex in starts.indices) {
            val startTime = starts[setIndex]
            val endTime = ends[setIndex]
            val stAccel = DescriptiveStatistics()
            val stNZAccel = DescriptiveStatistics()
            val stCourse = DescriptiveStatistics()
            val stNZCourse = DescriptiveStatistics()
            val stKmh = DescriptiveStatistics()
            val stNZKmh = DescriptiveStatistics()
            points.forEach { pt ->
                if (pt.timeOnly() in startTime..endTime) {
                    stAccel.addValue(pt.calculatedAcceleration.absoluteValue)
                    stCourse.addValue(pt.deltaCourse.absoluteValue.toDouble())
                    stKmh.addValue(pt.calculatedKmh)
                    if (pt.calculatedKmh > AnalyzerSettings.movementMinSpeedKmh) {
                        stNZAccel.addValue(pt.calculatedAcceleration.absoluteValue)
                        stNZKmh.addValue(pt.calculatedKmh)
                        stNZCourse.addValue((pt.deltaCourse.absoluteValue.toDouble()))
                    }
                }
            }
            statsAccel.add(stAccel)
            statsNZAccel.add(stNZAccel)
            statsCourseDiffs.add(stCourse)
            statsNZCourseDiffs.add(stNZCourse)
            statsKmh.add(stKmh)
            statsNZKmh.add(stNZKmh)
        }

        println("p80 course,${statsCourseDiffs.joinToString(",") { readable(it.getPercentile(80.0)) }}")
        println("p80 NZ course,${statsNZCourseDiffs.joinToString(",") { readable(it.getPercentile(80.0)) }}")
        println("p20 course,${statsCourseDiffs.joinToString(",") { readable(it.getPercentile(20.0)) }}")
        println("p20 NZ course,${statsNZCourseDiffs.joinToString(",") { readable(it.getPercentile(20.0)) }}")
        println("p80 kmh,${statsKmh.joinToString(",") { readable(it.getPercentile(80.0)) }}")
        println("p80 NZ kmh,${statsNZKmh.joinToString(",") { readable(it.getPercentile(80.0)) }}")
        println("p20 kmh,${statsKmh.joinToString(",") { readable(it.getPercentile(20.0)) }}")
        println("p20 NZ kmh,${statsNZKmh.joinToString(",") { readable(it.getPercentile(20.0)) }}")
        println("p80 accel,${statsAccel.joinToString(",") { readable(it.getPercentile(80.0)) }}")
        println("p80 NZ accel,${statsNZAccel.joinToString(",") { readable(it.getPercentile(80.0)) }}")
        println("p20 accel,${statsAccel.joinToString(",") { readable(it.getPercentile(20.0)) }}")
        println("p20 NZ accel,${statsNZAccel.joinToString(",") { readable(it.getPercentile(20.0)) }}")
    }

    private fun printFullTable(start: String, end: String, points: List<GpsTrackPoint>) {
        println("time,diff course,kmh,mps")
        points
            .filter { it.timeOnly() in start..end }
            .forEach {
                println("${it.timeOnly()},${it.deltaCourse.absoluteValue},${readable(it.calculatedKmh)},${readable(it.calculatedMps)}")
            }

    }

    // Given the raw points, determine what the window is
    private fun grade(points: List<GpsTrackPoint>, startIndex: Int, windowSeconds: Int): Boolean {
        // Use trajectories to get percentiles for course
        val oneMeter = PointCalculator.process(FilterPoints.byDistance(points.drop((startIndex - 1).coerceAtLeast(0)), 1.0))
        val trajectories = TrajectoryByCourse.process(GpsTrackSegment(oneMeter))
        val trajectoryPoints = trajectories.map { it.start as GpsTrackPoint } + listOf(trajectories.last().end as GpsTrackPoint)

        val trajCalcCourseDelta = DescriptiveStatistics()
        val trajSmoothCourseDelta = DescriptiveStatistics()
//        var startPoint = trajectoryPoints[startIndex]
//        var index = startIndex
//        if (startIndex < trajectoryPoints.size) {
//            var current = trajectoryPoints[index]
//
//            while (index < trajectoryPoints.size && startPoint.durationSeconds(current).toInt() < windowSeconds) {
//                current = trajectoryPoints[index]
//                trajCalcCourseDelta.addValue(current.deltaCourse.absoluteValue.toDouble())
//                trajSmoothCourseDelta.addValue(current.smoothedDeltaCourse.absoluteValue.toDouble())
//
//                index += 1
//            }
//        }

        val allCalcCourseDelta = DescriptiveStatistics()
//        val allSmoothCourseDelta = DescriptiveStatistics()
        val allCalcSpeed = DescriptiveStatistics()
        val startPoint = points[startIndex]
        var index = startIndex
        var current = points[index]

        while (index < points.size && startPoint.durationSeconds(current).toInt() < windowSeconds) {
            current = points[index]
            allCalcCourseDelta.addValue(current.deltaCourse.absoluteValue.toDouble())
//            allSmoothCourseDelta.addValue(current.smoothedDeltaCourse.absoluteValue.toDouble())
            allCalcSpeed.addValue(current.calculatedMps)

            index += 1
        }

//        val isStop = trajCalcCourseDelta.getPercentile(80.0) >= 50.0 && trajSmoothCourseDelta.getPercentile(80.0) >= 50.0
        val isStop = allCalcCourseDelta.getPercentile(80.0) >= 50.0
        println(
            "${startPoint.timeOnly()} - ${current.timeOnly()}: stop=$isStop " +
//                "tcCourse=${percentile(trajCalcCourseDelta,80.0)} tsCourse=${percentile(trajSmoothCourseDelta, 80.0)} " +
                "calcCourse=${percentile(allCalcCourseDelta,80.0)} " +
//                "smoothCourse=${percentile(allSmoothCourseDelta,80.0)} " +
                "speedMS=aCalc=${percentile(allCalcSpeed,80.0)}")

        return current == points.last()
    }

    private fun printTableHeader() {
        println(",5%,10%,15%,20%,25%,30%,35%,40%,45%,50%,55%,60%,65%,70%,75%,80%,85%,90%,95%,100%")
    }

    private fun printTableRow(name: String, descriptives: StatsDescriptives) {
        println(
            "$name,${percentile(descriptives, 5.0)},${percentile(descriptives, 10.0)}," +
                    "${percentile(descriptives, 15.0)},${percentile(descriptives, 20.0)},${
                        percentile(
                            descriptives,
                            25.0
                        )
                    }," +
                    "${percentile(descriptives, 30.0)},${percentile(descriptives, 35.0)},${
                        percentile(
                            descriptives,
                            40.0
                        )
                    }," +
                    "${percentile(descriptives, 45.0)},${percentile(descriptives, 50.0)},${
                        percentile(
                            descriptives,
                            55.0
                        )
                    }," +
                    "${percentile(descriptives, 60.0)},${percentile(descriptives, 65.0)},${
                        percentile(
                            descriptives,
                            70.0
                        )
                    }," +
                    "${percentile(descriptives, 75.0)},${percentile(descriptives, 80.0)},${
                        percentile(
                            descriptives,
                            85.0
                        )
                    }," +
                    "${percentile(descriptives, 90.0)},${percentile(descriptives, 95.0)},${
                        percentile(
                            descriptives,
                            100.0
                        )
                    }"
        )
    }

    private fun findVisits(points: List<GpsTrackPoint>) {
        // Find gaps in original data
        // Find big course changes off derivative data (perhaps trajectories of 0.5 meter movement?)
        for (current in points) {
            if (current.calculatedSeconds > AnalyzerSettings.maxSecondsBetweenPoints) {
                println("${current.timeOnly()} gap=${current.calculatedSeconds.toInt()} meters=${current.calculatedMeters.toInt()} kmh=${current.calculatedMps}")
//            } else if (current.smoothedDeltaCourse > 45) {
//                println("${current.timeOnly()} delta course=${current.smoothedDeltaCourse} meters=${current.calculatedMeters.toInt()} kmh=${current.calculatedMps}")
            }
        }
        // Find slow/no movement spots (& remove them)
        // With remaining points, use a sliding window to calculate (smoothed) course & speed (maybe angular velocity?)
        //      changes. Split when the ratios increase/decrease between reasonable/unreasonable
        // When adding points, consider skipping the first point after a gap (which may have wacko values)
    }

    private fun details(name: String, descriptives: StatsDescriptives): String {
        if (descriptives.size == 0L) {
            return "$name - no data"
        }

        return "$name " +
                "10%=${percentile(descriptives, 10.0)} " +
                "20%=${percentile(descriptives, 20.0)} " +
                "30%=${percentile(descriptives, 30.0)} " +
                "70%=${percentile(descriptives, 70.0)} " +
                "80%=${percentile(descriptives, 80.0)} " +
                "90%=${percentile(descriptives, 90.0)} " +
//                "kurtosis=${readable(descriptives.kurtosis)} " +
                "count=${descriptives.size} " +
//                "stddev=${readable(descriptives.standardDeviation)} " +
//                "variance=${readable(descriptives.variance)} " +
//                "skew=${readable(descriptives.skewness)} " +
                "min=${readable(descriptives.min)} " +
                "max=${readable(descriptives.max)} "
    }

    private fun percentile(descriptives: StatsDescriptives, percent: Double): String {
        return readable(descriptives.percentile(percent))
    }

    private fun percentile(descriptives: DescriptiveStatistics, percent: Double): String {
        return readable(descriptives.getPercentile(percent))
    }

    private fun readable(x: Double): String {
        return "%.${3}f".format(x)
    }
}
