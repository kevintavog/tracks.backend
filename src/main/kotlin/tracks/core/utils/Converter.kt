package tracks.core.utils

import kotlin.math.absoluteValue

object Converter {
    fun kmhToMetersPerSecond(kmh: Double): Double {
        return kmh * 1000 / 3600.0
    }

    fun metersPerSecondToKilometersPerHour(metersSecond: Double): Double {
        return metersSecond * (3600.0 / 1000.0)
    }

    fun speedKph(seconds: Double, kilometers: Double): Double {
        val time = (seconds / 3600.0)
        if (time < 0.000001) {
            return 0.0
        }
        return kilometers / time
    }

    fun readableSpeed(speed: Double): String {
        if (speed.absoluteValue < 0.0001) {
            return "0"
        }
        return ((speed * 100).toInt() / 100.0).toString()
    }
}
