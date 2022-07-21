package tracks.core.models

data class SearchOptions(
    val first: Int,
    val count: Int,
    val logSearch: Boolean = true
)
