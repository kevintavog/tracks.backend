package tracks.app.utils

import io.ktor.http.*
import tracks.core.RangicBadRequestException
import tracks.core.models.SearchOptions

fun parseCommonSearchOptions(qp: Parameters): SearchOptions {
    val first: Int = (qp["first"] ?: "1").toInt() - 1
    val count: Int = (qp["count"] ?: "20").toInt()

    if (first < 0) {
        throw RangicBadRequestException("'first' must be at least 1")
    }

    return SearchOptions(first, count, true)
}
