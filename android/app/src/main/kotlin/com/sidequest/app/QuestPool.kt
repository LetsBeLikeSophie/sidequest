package com.sidequest.app

import android.content.Context
import org.json.JSONArray

/**
 * Loads the quest content from assets/quests.json instead of hardcoding it,
 * so growing the pool (기획문서 3.1: LLM 생성 + 사람 검수 배치) never needs a
 * code change — just editing that file. `duration` drives the callback delay
 * (see CallbackScheduler) and `question` is what the callback actually asks;
 * the other three axes (place/energy/social/mood) are still just carried
 * for whenever the rejected-tag-weighted personalization in 3.2 gets built.
 */
object QuestPool {
    private var cached: List<Quest>? = null

    fun all(context: Context): List<Quest> {
        return cached ?: load(context).also { cached = it }
    }

    private fun load(context: Context): List<Quest> {
        val json = context.assets.open("quests.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Quest(
                tag = obj.getString("tag"),
                text = obj.getString("text"),
                duration = obj.getString("duration"),
                question = obj.getString("question")
            )
        }
    }
}
