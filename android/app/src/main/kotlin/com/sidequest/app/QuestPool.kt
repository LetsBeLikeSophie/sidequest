package com.sidequest.app

import android.content.Context
import org.json.JSONArray

/**
 * Loads the quest content from assets/quests.json instead of hardcoding it,
 * so growing the pool (기획문서 3.1: LLM 생성 + 사람 검수 배치) never needs a
 * code change — just editing that file. Each entry also carries the five
 * category axes from 3.2 (place/energy/social/duration/mood) for whenever
 * the weighting-by-rejected-tags personalization in that section gets built;
 * only `tag` and `text` are actually read today.
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
            Quest(tag = obj.getString("tag"), text = obj.getString("text"))
        }
    }
}
