package com.andrew264.mocklocation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


suspend fun extract(input: String): List<String> {
    return if (input.startsWith("https://www.google.com/maps/"))
        extractFromGoogleMapsUrl(input)
    else if ("""-?\d+(\.\d+)?,\s*-?\d+(\.\d+)?""".toRegex().containsMatchIn(input))
        extractFromCsvFormat(input)
    else if (input.startsWith("https://maps.app.goo.gl/") || input.startsWith("https://goo.gl/maps/"))
        resolveShortUrl(input)
    else throw IllegalArgumentException("Invalid input format")
}


private fun extractFromGoogleMapsUrl(url: String): List<String> {
    val regex = "@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+),(-?\\d+\\.?\\d*)z?".toRegex()
    val matchResult = regex.find(url)

    if (matchResult != null && matchResult.groupValues.size >= 3) {
        val latitude = matchResult.groupValues[1]
        val longitude = matchResult.groupValues[2]
        var altitude = "0.0"
        if (matchResult.groupValues.size >= 4 && matchResult.groupValues[3].isNotEmpty()) {
            altitude = matchResult.groupValues[3]
        }
        return listOf(latitude, longitude, altitude)
    } else {
        throw IllegalArgumentException("Invalid Google Maps URL")
    }
}

private fun extractFromCsvFormat(csv: String): List<String> {
    try {
        val coordinates = csv.split(",").map { it.trim() }
        val latitude = coordinates[0]
        val longitude = coordinates[1]
        val altitude = coordinates.getOrNull(2)?.let {
            it.split("z")[0].takeIf { it.isNotEmpty() }
        } ?: "0.0"
        return listOf(latitude, longitude, altitude)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid coordinate format")
    }
}

private suspend fun resolveShortUrl(shortUrl: String): List<String> {
    return withContext(Dispatchers.IO) {
        try {
            val fullUrl = getRedirectedUrl(shortUrl)
            val regex = """!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""".toRegex()
            val matchResult = regex.find(fullUrl)

            if (matchResult != null) {
                val (latitude, longitude) = matchResult.destructured
                listOf(latitude, longitude, "0.0")
            } else {
                throw IllegalArgumentException("Coordinates not found in resolved URL")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to resolve shortened URL: ${e.message}")
        }
    }
}

private suspend fun getRedirectedUrl(shortUrl: String): String {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = shortUrl
            var redirects = 0
            val maxRedirects = 5

            while (redirects < maxRedirects) {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return@withContext currentUrl
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_SEE_OTHER -> {
                        val newUrl = connection.getHeaderField("Location")
                        if (newUrl == null || newUrl == currentUrl) {
                            break
                        }
                        currentUrl = newUrl
                        redirects++
                    }

                    else -> throw Exception("Unexpected response code: ${connection.responseCode}")
                }
                connection.disconnect()
            }
            throw Exception("Too many redirects")
        } finally {
            connection?.disconnect()
        }
    }
}