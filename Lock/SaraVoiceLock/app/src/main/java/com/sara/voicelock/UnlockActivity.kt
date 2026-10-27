package com.sara.voicelock

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager

/**
 * Transparent activity shown over the lock screen. It asks Android to dismiss the lock screen.
 * - Lock type "None" or "Swipe": the phone unlocks fully.
 * - PIN / pattern / password / fingerprint: Android refuses to skip it, so the phone wakes
 *   and shows the normal unlock prompt instead. This is a security rule, not something an app can bypass.
 */
class UnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) {
            finish()
            return
        }

        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }

            override fun onDismissCancelled() {
                finish()
            }

            override fun onDismissError() {
                finish()
            }
        })
    }
}
