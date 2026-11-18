package com.xpulse.tracker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs in the background (via WorkManager). Fetches the latest price, compares
 * it with the last price we saw, and raises a notification if it dropped.
 */
class PriceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val history = try {
            PriceRepository.fetchHistory()
        } catch (_: Exception) {
            return Result.retry()   // network hiccup — try again later
        }
        if (history.isEmpty()) return Result.retry()

        val latest = history.last()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getInt(KEY_LAST_SEEN, -1)

        if (lastSeen != -1 && latest.onRoad < lastSeen) {
            Notifier.showDrop(applicationContext, dropTo = latest.onRoad, from = lastSeen, point = latest)
        }
        // Always store the newest price as the baseline for the next comparison.
        prefs.edit().putInt(KEY_LAST_SEEN, latest.onRoad).apply()
        return Result.success()
    }

    companion object {
        const val PREFS = "xpulse_prefs"
        const val KEY_LAST_SEEN = "last_seen_onroad"
    }
}
