package tracks.core.utils

import java.net.URL

object TrackConfiguration {
    var elasticSearchUrl: String = ""
    var processedTracksFolder: String = ""
    var nameLookupUrl: String = ""
    var timezoneUrl: String = ""
    var tracksFolder: String = ""

    val elasticSearchHost: String by lazy { URL(elasticSearchUrl).host }
    val elasticSearchPort: Int by lazy { URL(elasticSearchUrl).port }
}
