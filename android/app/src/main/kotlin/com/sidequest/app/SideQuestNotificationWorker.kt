package com.sidequest.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

private const val CHANNEL_ID = "side_quest_default"
private const val NOTIFICATION_ID = 1

/**
 * The "불쑥 찾아옴" trigger (기획문서 2.1) — this is the push, not the widget.
 * Delivering it also advances the widget to a fresh quest so the two stay
 * in sync: tapping the widget right after the notification shows what the
 * notification announced, not something stale.
 */
class SideQuestNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext

        if (!NotificationScheduler.canSendMoreToday(context)) {
            NotificationScheduler.scheduleNext(context)
            return Result.success()
        }

        SideQuestWidgetProvider.advanceAndRefreshWidgets(context)
        val quest = SideQuestWidgetProvider.currentQuest(context)
        postNotification(context, quest)

        NotificationScheduler.recordSent(context)
        NotificationScheduler.scheduleNext(context)
        return Result.success()
    }

    private fun postNotification(context: Context, quest: Quest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "사이드퀘스트", NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // Tapping just brings the launcher forward — the widget is where the
        // actual 다음에/해볼래 decision happens, the notification only announces it.
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(quest.text)
            .setContentText(quest.tag)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // permission not granted — silently skip rather than crash
        }

        NotificationManagerCompatWrapper.notify(context, NOTIFICATION_ID, notification)
    }
}

/** Tiny indirection so we depend on androidx.core's NotificationManagerCompat in one place. */
private object NotificationManagerCompatWrapper {
    fun notify(context: Context, id: Int, notification: android.app.Notification) {
        androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
    }
}
