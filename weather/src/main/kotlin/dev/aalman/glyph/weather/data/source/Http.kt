package dev.aalman.glyph.weather.data.source

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Status code and body of an HTTP response. */
class HttpResult(val code: Int, val body: String)

/** Performs a request: GET when [body] is null, otherwise POST with a JSON body. */
fun interface Http {
    fun request(url: String, body: String?): HttpResult

    companion object {
        val Default = Http { url, body ->
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            try {
                if (body != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } catch (e: IOException) {
                throw SourceException(SourceException.Kind.NETWORK, "Request failed: ${e.message}", e)
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** Throws the [SourceException] matching a non-200 [result] from [service]. */
internal fun checkStatus(service: String, result: HttpResult) {
    val kind = when (result.code) {
        200 -> return
        401, 403 -> SourceException.Kind.BAD_KEY
        429 -> SourceException.Kind.RATE_LIMITED
        else -> SourceException.Kind.NETWORK
    }
    throw SourceException(kind, "$service returned HTTP ${result.code}: ${result.body.take(200)}")
}
