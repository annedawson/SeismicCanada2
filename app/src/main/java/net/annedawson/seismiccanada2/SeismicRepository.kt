package net.annedawson.seismiccanada2

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SeismicRepository {
    private val client = OkHttpClient()

    // Using USGS feed as a reliable JSON source. It aggregates data including from CNSN.
    private val url = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"

    suspend fun fetchEarthquakes(): List<Earthquake> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseData = response.body?.string() ?: return@use emptyList()
            parseJson(responseData)
        }
    }

    private fun parseJson(jsonString: String): List<Earthquake> {
        val earthquakes = mutableListOf<Earthquake>()
        val jsonObject = JSONObject(jsonString)
        val features = jsonObject.getJSONArray("features")

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            val id = feature.getString("id")
            val place = properties.optString("place", "Unknown location")
            val magnitude = properties.optDouble("mag", 0.0)
            val time = properties.optLong("time", 0L)
            val lon = coordinates.getDouble(0)
            val lat = coordinates.getDouble(1)
            val depth = coordinates.getDouble(2)

            // Filter for Canada approx bounding box
            // Lat: 41.0 to 83.0, Lon: -141.0 to -50.0
            // Also include some buffer or just check if string contains "Canada" or "CA"
            // The 'place' string usually looks like "54km S of Whites City, New Mexico" or "10km NW of Ottawa, Canada"
            
            val isCanadaLocation = (lat in 41.0..85.0 && lon in -142.0..-50.0) || place.contains("Canada", ignoreCase = true)

            if (isCanadaLocation) {
                earthquakes.add(
                    Earthquake(
                        id = id,
                        place = place,
                        magnitude = magnitude,
                        time = time,
                        lat = lat,
                        lon = lon,
                        depth = depth
                    )
                )
            }
        }
        return earthquakes
    }
}
