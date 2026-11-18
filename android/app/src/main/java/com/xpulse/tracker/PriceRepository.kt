package com.xpulse.tracker

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads price_history.json straight from the GitHub repo. No API key, no SDK —
 * just a plain HTTPS GET and org.json (bundled with Android).
 */
object PriceRepository {

    fun fetchHistory(): List<PricePoint> {
        val conn = (URL(Config.RAW_HISTORY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "XpulsePriceTracker")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Server returned HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<PricePoint> {
        val arr = JSONArray(body)
        val out = ArrayList<PricePoint>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val onRoad = o.optInt("on_road", 0)
            if (onRoad <= 0) continue
            out += PricePoint(
                date = o.optString("date"),
                onRoad = onRoad,
                exShowroom = o.optInt("ex_showroom", 0),
                city = o.optString("city", ""),
                variant = o.optString("variant", ""),
            )
        }
        return out
    }
}
