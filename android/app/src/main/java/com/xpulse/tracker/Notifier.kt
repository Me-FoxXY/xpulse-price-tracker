package com.xpulse.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.NumberFormat
import java.util.Locale

object Notifier {
    private const val CHANNEL_ID = "price_drops"
    private const val NOTIF_ID = 1001

    private val inr: NumberFormat = NumberFormat.getInstance(Locale("en", "IN"))
    private fun rupees(v: Int) = "₹" + inr.format(v)

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Price drops",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alerts when the Xpulse on-road price drops" }
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Fired by the worker when a drop is detected. No-ops if the user denied
     *  notification permission (Android 13+). */
    fun showDrop(ctx: Context, dropTo: Int, from: Int, point: PricePoint) {
        val saved = from - dropTo
        val openApp = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setContentTitle("Xpulse price dropped to ${rupees(dropTo)}")
            .setContentText("Down ${rupees(saved)} (was ${rupees(from)}) — on-road in ${point.city}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The Hero Xpulse 200 4V ${point.variant} on-road price in ${point.city} " +
                        "fell to ${rupees(dropTo)}, down ${rupees(saved)} from ${rupees(from)}. " +
                        "Good time to get a written quote from the dealer."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // Permission not granted yet; the next drop after the user allows
            // notifications will come through.
        }
    }
}
