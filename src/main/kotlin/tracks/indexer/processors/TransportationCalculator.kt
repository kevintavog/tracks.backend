package tracks.indexer.processors

import tracks.core.models.GpsTrackPoint
import tracks.core.models.GpsTrackSegment
import tracks.core.models.TransportationMode
import tracks.core.models.TransportationType

data class SpeedProfile(
    val absoluteMinimum: Double,
    val nominalMinimum: Double,
    val nominalMaximum: Double,
    val absoluteMaximum: Double,
    val mode: TransportationMode
)

object TransportationCalculator {

    // Consider using acceleration 95th percentile as a tie-breaker for a segment,
    // along with median speed & 95th percentile of speed
    // See ProcessingRawGPSData.pdf

    // Acceleration (from BicycleGpsData.pdf)
    // They suggest using the 10% and 90% for mode recognition
    // There is a suggestion that calculating speed & acceleration is best *after* gaussian smoothing
    // Walk:        +/- 0.75 m/s/s
    // Bicycle:     +/- 1.25 m/s/s
    // Car:         +/- 2.50 m/s/s/
    // Train:       +/- 2.50 m/s/s/


    private val speedProfiles = listOf(
        SpeedProfile( 0.0,   1.0,    6.6,    7.4, TransportationMode.foot),
        SpeedProfile( 6.6,   12.0,  34.0,   41.0, TransportationMode.bicycle),
        SpeedProfile(16.0,   25.0, 128.0,  160.0, TransportationMode.car),
        SpeedProfile(90.0,  100.0, 320.0,  370.0, TransportationMode.train),
        SpeedProfile(100.0, 160.0, 800.0, 1000.0, TransportationMode.airplane)
    )

    fun process(points: List<GpsTrackPoint>) {
        points.forEach {
            it.transportationTypes = get(it)
        }
    }

    fun process(segment: GpsTrackSegment): List<TransportationType> {
        val tt = mutableMapOf<TransportationMode, Double>().withDefault { 0.0 }
        segment.points.forEach { point ->
            point.transportationTypes.forEach { pttt ->
                tt[pttt.mode] = tt.getValue(pttt.mode) + pttt.probability
            }
        }
        return tt
            .map { kv -> TransportationType(kv.value / segment.points.size, kv.key) }
            .sortedByDescending { it.probability }
    }

     fun get(point: GpsTrackPoint): List<TransportationType> {
        return get(point.calculatedKmh)
    }

    fun get(kmh: Double): List<TransportationType> {
        val types = mutableListOf<TransportationType>()
        speedProfiles.forEach { profile ->
            var probability: Double? = null
            if (kmh >= profile.nominalMinimum && kmh <= profile.nominalMaximum) {
                probability = 1.0
            } else if (kmh >= profile.absoluteMinimum && kmh < profile.nominalMinimum) {
                probability = probability(kmh, profile.absoluteMinimum, profile.nominalMinimum)
            } else if (kmh >= profile.nominalMaximum && kmh <= profile.absoluteMaximum) {
                probability = probability(kmh, profile.absoluteMaximum, profile.nominalMaximum)
            }

            probability?.let {
                types.add(TransportationType(it, profile.mode))
            }
        }

        if (types.isEmpty()) {
            return listOf(TransportationType(1.0, TransportationMode.unknown))
        }

        return types.sortedByDescending { it.probability }.take(2)
    }

    private fun probability(value: Double, absolute: Double, nominal: Double): Double {
        return 0.01.coerceAtLeast((value - absolute) / (nominal - absolute))
    }
}
