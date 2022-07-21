package tracks.indexer.models

import kotlinx.serialization.Serializable
import java.lang.Double.max
import java.lang.Double.min

@Serializable
data class DiagWorkspace(
    val chartData: List<DiagData>
)

@Serializable
data class DiagData(
    val name: String,
    val values: List<Double>,
    val minValue: Double,
    val maxValue: Double
) {
    companion object {
        fun fromPoints(name: String, points: List<Double>): DiagData {
            var minValue: Double? = null
            var maxValue: Double? = null
            points.forEach { value ->
                if (minValue == null) {
                    minValue = value
                } else {
                    minValue = min(minValue!!, value)
                }
                if (maxValue == null) {
                    maxValue = value
                } else {
                    maxValue = max(maxValue!!, value)
                }
            }
            return DiagData(name, points, minValue ?: 0.0, maxValue ?: 0.0)
        }
    }
}
