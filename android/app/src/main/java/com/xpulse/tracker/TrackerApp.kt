package com.xpulse.tracker

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        schedulePeriodicCheck()
    }

    private fun schedulePeriodicCheck() {
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(
            Config.CHECK_INTERVAL_HOURS, TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "xpulse_price_check",
            ExistingPeriodicWorkPolicy.KEEP, // keep the existing schedule across launches
            request,
        )
    }
}
