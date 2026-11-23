package kz.duken.mobile.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kz.duken.mobile.R

object Notifications {
    private const val CHANNEL = "orders"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Уведомление об оплате. Без разрешения молча пропускается — оплата от этого не зависит. */
    fun orderPaid(context: Context, orderId: Int, total: Long) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notifications_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.payment_success))
            .setContentText(context.getString(R.string.notification_paid, orderId, formatTenge(total)))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(orderId, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом
        }
    }
}
