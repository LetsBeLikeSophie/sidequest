package com.sidequest.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat

/**
 * The callback sheet — reached only by tapping a CallbackNotificationWorker
 * notification, well after "하트" was pressed on the widget. It never asks
 * 지우기/하트 again; it only asks the one question that quest queued up
 * (e.g. "하늘은 어땠나요?"), because by now there's actually been time to
 * have done the thing. Hearting itself already advanced the widget — this
 * screen's only job is to record (or skip) an answer.
 *
 * launchMode="singleTask": repeated callback taps must reuse this one
 * instance (via onNewIntent) instead of stacking a fresh MainActivity
 * underneath each time — a stack of activities each running their own
 * idle-close timer made it look like state was changing on its own.
 */
class MainActivity : Activity() {

    private val idleHandler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var finished = false
    private lateinit var tag: String
    private lateinit var questText: String
    private lateinit var question: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // The dialog theme gives us reliable EditText focus/IME behavior; size it
        // to fill the screen ourselves so our own scrim + bottom card do the layout.
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setupSheet()
        ensureNotificationPermission()
    }

    // 알림 권한 요청 문구/타이밍은 기획문서에서도 "실제 화면 나온 후 UX 피드백으로 확정"이라 아직
    // 보류 상태 — 지금은 시스템 다이얼로그만 뜨게 해서 알림이 실제로 도착하는지부터 검증한다.
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupSheet()
    }

    private fun setupSheet() {
        finished = false
        idleRunnable?.let { idleHandler.removeCallbacks(it) }

        // Only reachable via a callback notification, so these extras are always present.
        tag = intent.getStringExtra(CALLBACK_QUEST_TAG) ?: ""
        questText = intent.getStringExtra(CALLBACK_QUEST_TEXT) ?: ""
        question = intent.getStringExtra(CALLBACK_QUEST_QUESTION) ?: ""

        findViewById<TextView>(R.id.sheet_eyebrow).text = questText
        findViewById<TextView>(R.id.sheet_quest_text).text = question

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

    private fun finishWith(rawAnswer: String?) {
        if (finished) return
        finished = true
        idleRunnable?.let { idleHandler.removeCallbacks(it) }

        val answer = rawAnswer?.trim()?.takeIf { it.isNotEmpty() }
        QuestArchive.save(this, tag, questText, question, answer)

        // The web prototype's "whisper" (3초짜리, 못 찾아보게 사라지는 반응) maps to a
        // plain Toast here — it's tied to the application, not this activity, so it
        // still shows over the home screen after finish() below closes the sheet.
        if (answer != null) {
            val appContext = applicationContext

            // Joins the "다른 사람 한마디" pool (기획문서 2.5) — server-side moderation
            // decides if it's ever shown to anyone; this call doesn't wait for that.
            CommunityClient.submitAnswer(questText, question, answer)

            ReactionClient.fetchReaction(answer) { reaction ->
                if (reaction != null) {
                    Toast.makeText(appContext, reaction, Toast.LENGTH_LONG).show()
                }
            }

            // Plain-toast wiring for now — the card-flip/gacha treatment for this
            // is a UI project of its own, planned separately.
            CommunityClient.fetchRandomAnswer(questText) { communityAnswer ->
                if (communityAnswer != null) {
                    Toast.makeText(appContext, "누군가는: $communityAnswer", Toast.LENGTH_LONG).show()
                }
            }
        }

        finish()
    }

    override fun onDestroy() {
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
