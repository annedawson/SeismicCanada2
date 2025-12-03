package net.annedawson.seismiccanada2

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SeismicRepository {
    private val client = OkHttpClient()

    // Using USGS (US Geological Survey) feed as a reliable JSON source.
    // It aggregates data including from CNSN -
    // Canadian National Seismograph Network CNSN
    // Separately, Natural Resources Canada - Earthquake Early Warning
    // report sending data to USGS
    // Changed to 30 days (1.0+) to include smaller earthquakes
    private val url = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/1.0_month.geojson"

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

            // Filter for British Columbia, Canada
            // Approximate bounding box for BC: Lat 48.0 to 60.0, Lon -139.0 to -114.0
            // Also check if place string explicitly mentions British Columbia
            
            val isInBcCoordinates = lat in 48.0..60.0 && lon in -139.0..-114.0
            val isBcString = place.contains("British Columbia", ignoreCase = true) || 
                             place.contains("BC, Canada", ignoreCase = true)

            if (isInBcCoordinates || isBcString) {
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
