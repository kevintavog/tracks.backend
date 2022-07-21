package tracks.indexer.utils

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tracks.core.models.TimezoneInfo
import tracks.core.utils.TrackConfiguration
import java.util.concurrent.TimeUnit

object TimezoneLookup {
    private val jsonCoder = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy { OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build() }

    fun at(lat: Double, lon: Double): TimezoneInfo? {
        val response = client
            .newCall(
                Request.Builder()
                .url("${TrackConfiguration.timezoneUrl}/api/v1/timezone?lat=$lat&lon=$lon")
                .build())
            .execute()
        val body = response.body?.string()

        response.close()
        if (response.isSuccessful) {
            return jsonCoder.decodeFromString(body!!)
        }

        return null
    }
}
