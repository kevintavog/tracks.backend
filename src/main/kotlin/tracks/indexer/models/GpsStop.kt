package tracks.indexer.models

import tracks.core.models.*

enum class StopReason(val reason: String) {
    Waypoint("Waypoint"),
    Dwell("Dwell"),
    Gap("Gap"),
    Noise("Noise"),
    Stop("Stop"),
}

data class GpsStop(
    val start: GpsPoint,
    var end: GpsPoint,
    val reason: StopReason,
    val description: String
)
