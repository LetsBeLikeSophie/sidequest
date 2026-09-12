package com.sidequest.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Picks which quest is "current" — a shuffle-bag over QuestPool, not a
 * fixed cycle. A plain +1 index would replay the exact same order every
 * lap, which is the opposite of 기획문서's "완전 랜덤": after a day or two
 * of use you'd be able to name the next quest before it appeared. Each lap
 * through the full pool is shuffled once, so nothing repeats until every
 * other quest has been shown, and unlucky back-to-back laps can't hand you
 * the same quest twice in a row.
 */
object QuestSequencer {
    private const val PREFS = "side_quest_widget_state"
    private const val KEY_ORDER = "quest_order"
    private const val KEY_POSITION = "quest_position"

    fun current(context: Context, pool: List<Quest>): Quest {
        val prefs = prefs(context)
        val order = loadOrCreateOrder(prefs, pool.size)
        val position = prefs.getInt(KEY_POSITION, 0).coerceIn(0, order.lastIndex)
        return pool[order[position]]
    }

    fun advance(context: Context, pool: List<Quest>) {
        val prefs = prefs(context)
        var order = loadOrCreateOrder(prefs, pool.size)
        var position = prefs.getInt(KEY_POSITION, 0) + 1

        if (position >= order.size) {
            val justPlayed = order.last()
            order = newShuffledOrder(pool.size, avoidFirst = justPlayed)
            position = 0
            prefs.edit().putString(KEY_ORDER, encode(order)).apply()
        }
        prefs.edit().putInt(KEY_POSITION, position).apply()
    }

    private fun loadOrCreateOrder(prefs: SharedPreferences, size: Int): List<Int> {
        val stored = prefs.getString(KEY_ORDER, null)?.let(::decode)
        if (stored != null && stored.size == size) return stored

        // missing, corrupt, or the pool size changed (content update) — start a fresh lap
        val fresh = newShuffledOrder(size, avoidFirst = null)
        prefs.edit().putString(KEY_ORDER, encode(fresh)).putInt(KEY_POSITION, 0).apply()
        return fresh
    }

    private fun newShuffledOrder(size: Int, avoidFirst: Int?): List<Int> {
        val order = (0 until size).shuffled().toMutableList()
        if (avoidFirst != null && size > 1 && order.first() == avoidFirst) {
            order[0] = order[1].also { order[1] = order[0] }
        }
        return order
    }

    private fun encode(order: List<Int>) = order.joinToString(",")
    private fun decode(raw: String) = raw.split(",").mapNotNull { it.toIntOrNull() }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
