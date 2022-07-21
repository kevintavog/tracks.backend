package tracks.core.models

enum class TransportationMode(val mode: String) {
    unknown("Unknown"),
    foot("foot"),
    bicycle("bicycle"),
    car("car"),
    train("train"),
    airplane("airplane")
}

data class TransportationType(
    val probability: Double,
    val mode: TransportationMode
) {
    override fun toString(): String {
        return "$mode=${(probability * 100.0).toInt()}"
    }
}
