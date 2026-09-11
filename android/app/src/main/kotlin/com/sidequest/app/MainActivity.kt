package com.sidequest.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

/**
 * The "한마디 남기기" sheet (기획문서 2.3). Reached only after "해볼래" was
 * already pressed on the widget — this screen never re-asks 다음에/해볼래,
 * it only offers an optional note before quietly moving the widget on.
 *
 * launchMode="singleTask": repeated widget taps must reuse this one instance
 * (via onNewIntent) instead of stacking a fresh MainActivity underneath each
 * time — a stack of activities each running their own idle-close timer made
 * it look like quests were advancing on their own when they finished in turn.
 */
class MainActivity : Activity() {

    private val idleHandler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var finished = false
    private lateinit var quest: Quest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // The dialog theme gives us reliable EditText focus/IME behavior; size it
        // to fill the screen ourselves so our own scrim + bottom card do the layout.
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setupSheet()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupSheet()
    }

    private fun setupSheet() {
        finished = false
        idleRunnable?.let { idleHandler.removeCallbacks(it) }

        quest = SideQuestWidgetProvider.currentQuest(this)
        findViewById<TextView>(R.id.sheet_quest_text).text = quest.text

        val noteInput = findViewById<EditText>(R.id.note_input)
        noteInput.setText("")

        // default is to skip (기본값은 생략) — closes itself if left untouched
        idleRunnable = Runnable { finishWith(null) }.also {
            idleHandler.postDelayed(it, 3200)
        }

        noteInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                idleRunnable?.let { idleHandler.removeCallbacks(it) }
            }
        })

        findViewById<android.view.View>(R.id.sheet_card).setOnClickListener {
            /* consume — don't let taps on the card fall through to the scrim */
        }
        findViewById<android.view.View>(R.id.root_scrim).setOnClickListener {
            finishWith(noteInput.text?.toString())
        }
        findViewById<android.view.View>(R.id.btn_close_note).setOnClickListener {
            finishWith(null)
        }
        findViewById<android.view.View>(R.id.btn_send_note).setOnClickListener {
            finishWith(noteInput.text?.toString())
        }
    }

    private fun finishWith(rawNote: String?) {
        if (finished) return
        finished = true
        idleRunnable?.let { idleHandler.removeCallbacks(it) }

        val note = rawNote?.trim()?.takeIf { it.isNotEmpty() }
        QuestArchive.save(this, quest, note)
        SideQuestWidgetProvider.advanceAndRefreshWidgets(this)
        finish()
    }

    override fun onDestroy() {
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
