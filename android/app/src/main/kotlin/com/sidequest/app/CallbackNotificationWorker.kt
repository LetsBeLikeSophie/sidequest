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
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

private const val CALLBACK_CHANNEL_ID = "side_quest_default"

/**
 * Fires once, well after a hearted quest, and asks the question that quest
 * itself queued up (e.g. "하늘은 어땠나요?") instead of a blank "아무 말이나".
 * Tapping it opens MainActivity to answer; ignoring it does nothing at all —
 * same "무해함" as every other notification here, just delayed.
 */
class CallbackNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val tag = inputData.getString(CALLBACK_QUEST_TAG) ?: return Result.failure()
        val text = inputData.getString(CALLBACK_QUEST_TEXT) ?: return Result.failure()
        val question = inputData.getString(CALLBACK_QUEST_QUESTION) ?: return Result.failure()

        postNotification(context, tag, text, question)
        return Result.success()
    }

    private fun postNotification(context: Context, tag: String, text: String, question: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALLBACK_CHANNEL_ID, "사이드퀘스트", NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(CALLBACK_QUEST_TAG, tag)
            putExtra(CALLBACK_QUEST_TEXT, text)
            putExtra(CALLBACK_QUEST_QUESTION, question)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, text.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALLBACK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(question)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(context).notify(text.hashCode(), notification)
    }
}
