package com.sidequest.app

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Drives "완전 랜덤, 하루 최대 N회, 최소 0회여도 됨" (기획문서 2.1) using a
 * self-perpetuating chain of one-time WorkManager jobs rather than a fixed
 * periodic schedule — each delivered notification schedules the next one at
 * a fresh random delay, so there's no fixed cadence to predict.
 */
object NotificationScheduler {
    private const val PREFS = "side_quest_notify_state"
    private const val KEY_DATE = "date"
    private const val KEY_COUNT = "count"
    private const val UNIQUE_WORK_NAME = "side_quest_notify_chain"

    const val MAX_PER_DAY = 2
    private const val MIN_DELAY_MINUTES = 30L
    private const val MAX_DELAY_MINUTES = 240L

    /** Call when there's reason to believe the chain might not be running yet
     *  (e.g. the first widget was just placed) — starts it if nothing is pending. */
    fun ensureScheduled(context: Context) {
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
            .get()
        val alreadyPending = infos.any { !it.state.isFinished }
        if (!alreadyPending) scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val prefs = prefs(context)
        resetIfNewDay(prefs)
        val sentToday = prefs.getInt(KEY_COUNT, 0)

        val delayMillis = if (sentToday >= MAX_PER_DAY) {
            millisUntilNextMidnight() // cap reached — next attempt only after the day rolls over
        } else {
            TimeUnit.MINUTES.toMillis(Random.nextLong(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES))
        }

        val request = OneTimeWorkRequestBuilder<SideQuestNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun canSendMoreToday(context: Context): Boolean {
        val prefs = prefs(context)
        resetIfNewDay(prefs)
        return prefs.getInt(KEY_COUNT, 0) < MAX_PER_DAY
    }

    fun recordSent(context: Context) {
        val prefs = prefs(context)
        resetIfNewDay(prefs)
        prefs.edit().putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun resetIfNewDay(prefs: SharedPreferences) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(KEY_DATE, null) != today) {
            prefs.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, 0).apply()
        }
    }

    private fun millisUntilNextMidnight(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
    }
}
