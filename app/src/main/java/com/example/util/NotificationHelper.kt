package com.example.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Task

interface TaskNotificationDispatcher {
    fun ensureChannelCreated()
    fun showAssignedTaskNotification(task: Task): Boolean
}

class NotificationHelper(
    private val context: Context
) : TaskNotificationDispatcher {

    private val appContext: Context = context.applicationContext

    override fun ensureChannelCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            enableLights(true)
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }

    override fun showAssignedTaskNotification(task: Task): Boolean {
        if (!hasPostNotificationPermission()) return false
        ensureChannelCreated()

        val notificationId = notificationIdForTask(task.id)
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            action = "$ACTION_OPEN_TASK_PREFIX${task.id}"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messageSummary = "[${task.ticketCode}] ${task.title}"
        val reportedLabel = DateEpochUtil.formatReportedTime(task.createdAtMillis)
        val expandedDetails = buildString {
            append(messageSummary)
            if (task.location.isNotBlank()) {
                append("\nLokasi: ")
                append(task.location)
            }
            append("\nWaktu Lapor: ")
            append(reportedLabel)
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pingdulu)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(messageSummary)
            .setSubText(task.location.takeIf { it.isNotBlank() })
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedDetails))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setSound(soundUri)
            .setVibrate(VIBRATION_PATTERN)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    fun hasPostNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val CHANNEL_ID = "pingdulu_assigned_tasks"
        const val CHANNEL_NAME = "Penugasan Tiket IT"
        const val CHANNEL_DESCRIPTION =
            "Notifikasi bergetar dan bersuara saat teknisi menerima delegasi tiket baru dari admin atau rekan."
        const val NOTIFICATION_TITLE = "🔔 Tugas Baru Didelegasikan!"
        const val EXTRA_TASK_ID = "extra_pingdulu_task_id"
        private const val ACTION_OPEN_TASK_PREFIX = "com.example.ACTION_OPEN_ASSIGNED_TASK_"
        val VIBRATION_PATTERN = longArrayOf(0L, 280L, 140L, 320L)

        fun notificationIdForTask(taskId: Long): Int {
            val positive = taskId.hashCode() and 0x7FFFFFFF
            return if (positive < 1000) positive + 1000 else positive
        }
    }
}
