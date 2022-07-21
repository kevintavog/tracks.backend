package tracks.indexer.processors

object AnalyzerSettings {
    const val maxSecondsBetweenPoints = 10.0
    const val minSecondsLowMovementForStop = 30

    const val movementMinSpeedMetersSecond: Double = 0.320
    const val movementMinSpeedKmh: Double = 1.2
//    const val movementMaxSpeedKmh: Double = 380.0

    const val maxNormalTurn = 60

    const val zeroSpeedKmh: Double = 0.60
    const val zeroSpeedMinSeconds: Double = 10.0

    const val speedGaussKernelWidth = 5

    const val speedSmoothingSeconds = 6

    const val gaussKernelWidth = 7
    const val smoothPointsDelta = gaussKernelWidth * 2 / 3

    const val densityPointsDelta = 20
    const val densityRadiusMeters = 15
    const val densityMinimum = 30
    const val densityMinPointsBetweenClouds = 30
    const val densityMinimumCloudLength = 10
    const val densityMinimumCloudSeconds = 60
    const val densityMinimumRatio = 0.66

    const val sharpTurnDuration = 5 * 60.0
    const val sharpTurnMinSecondsBetween = 1 * 60.0
    const val sharpTurnThreshold = 45

//    const val vectorOverlapDuration = 20 * 60.0
//    const val vectorMinimumScore = 3
//    const val vectorMinBetweenClouds = 6

//    const val noisyBearingDelta = 30
//    const val noisyAbruptBearing = 60

    const val minSecondsBetweenStops = 2 * 60.0

    const val maxMetersBetweenPlacenames = 50.0
    const val maxSecondsBetweenPlacenames = 5 * 60.0
}
