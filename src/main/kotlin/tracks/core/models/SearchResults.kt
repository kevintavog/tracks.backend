package tracks.core.models

data class SearchResults(
    val resultCount: Int,
    val totalMatches: Int,
    val items: List<GpsDto>
)
