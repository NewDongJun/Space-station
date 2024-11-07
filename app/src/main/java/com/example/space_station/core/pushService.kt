package com.example.space_station.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class NotificationApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val notificationChannel = NotificationChannel(
            "checkIn_reminder",
            "CheckIn reminder channel",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationChannel.description = "A notification channel for water reminders"

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
    }
}

class NotificationService(
    private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun showBasicNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(context, "checkIn_reminder")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            Random.nextInt(),
            notification
        )
    }

    private fun Context.bitmapFromResource(
        @DrawableRes resId: Int
    ) = BitmapFactory.decodeResource(
        resources,
        resId
    )
}