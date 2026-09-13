package com.sidequest.app

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random

const val CALLBACK_QUEST_TAG = "callback_quest_tag"
const val CALLBACK_QUEST_TEXT = "callback_quest_text"
const val CALLBACK_QUEST_QUESTION = "callback_quest_question"

/**
 * Books the "how did it go?" follow-up for a hearted quest. The delay is
 * keyed off how long the quest itself takes — asking about "하늘 색 3초만
 * 쳐다보기" only makes sense once there's actually been time to look, and a
 * 15분+ quest needs longer than that. These ranges are first-guess defaults;
 * they're intentionally isolated here so they're easy to revisit once real
 * response-rate data exists to tune them against. Each heart gets its own
 * independent callback (not a unique/replaced work name), since hearting
 * several quests in a row should queue up several honest callbacks, not
 * cancel each other.
 */
object CallbackScheduler {
    private val DELAY_RANGES_MINUTES = mapOf(
        "즉시" to (10L to 25L),
        "5분" to (30L to 60L),
        "15분+" to (90L to 150L)
    )
    private val DEFAULT_RANGE = 30L to 60L

    fun scheduleCallback(context: Context, quest: Quest) {
        val (min, max) = DELAY_RANGES_MINUTES[quest.duration] ?: DEFAULT_RANGE
        val delayMinutes = Random.nextLong(min, max + 1)

        val data = Data.Builder()
            .putString(CALLBACK_QUEST_TAG, quest.tag)
            .putString(CALLBACK_QUEST_TEXT, quest.text)
            .putString(CALLBACK_QUEST_QUESTION, quest.question)
            .build()

        val request = OneTimeWorkRequestBuilder<CallbackNotificationWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        // Deliberately not enqueueUniqueWork — several hearted quests in a
        // row should each get their own honest callback later, not clobber
        // one another the way the random-quest chain intentionally does.
        WorkManager.getInstance(context).enqueue(request)
    }
}
