package tracks.app.models

import java.lang.Exception

data class ErrorResponse(val error: String)

fun toErrorResponse(exception: Exception) = ErrorResponse(exception.message ?: exception.localizedMessage)
