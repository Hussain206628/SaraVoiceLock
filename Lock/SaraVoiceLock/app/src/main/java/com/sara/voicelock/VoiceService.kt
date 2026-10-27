package com.sara.voicelock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Foreground service that keeps listening for "Hey Sara, lock/unlock my phone".
 * It restarts Android's speech recogniser in a loop so listening is continuous.
 */
class VoiceService : Service() {

    companion object {
        const val ACTION_STOP = "com.sara.voicelock.STOP"
        private const val CH_LISTEN = "sara_listen"
        private const val CH_ALERT = "sara_alert"
        private const val ID_LISTEN = 1
        private const val ID_ALERT = 2

        @Volatile var isRunning = false
        @Volatile var lastHeard = ""
        @Volatile var lastAction = ""

        /** Returns false if device admin has not been enabled yet. */
        fun lockNow(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, AdminReceiver::class.java)
            if (!dpm.isAdminActive(admin)) return false
            dpm.lockNow()
            return true
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var lastActionAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LISTEN, "Sara listening", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Sara unlock", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        isRunning = true
        if (!listening) {
            listening = true
            startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        listening = false
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_LISTEN)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Sara is listening")
            .setContentText("Say: \"Hey Sara, lock my phone\"")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_LISTEN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(ID_LISTEN, n)
        }
    }

    // ---- Speech recognition loop -------------------------------------------------------

    private fun startListening() {
        if (!listening) return
        handler.post {
            recognizer?.destroy()
            recognizer = null
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                lastHeard = "Speech recognition is not available on this phone"
                return@post
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(listener)
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(i)
        }
    }

    private fun restartSoon(delayMs: Long) {
        if (!listening) return
        handler.postDelayed({ startListening() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            handleResults(partialResults)
        }

        override fun onResults(results: Bundle?) {
            handleResults(results)
            restartSoon(200)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    lastHeard = "Microphone permission missing"
                    stopSelf()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon(1000)
                else -> restartSoon(300) // no match / timeout are normal, just listen again
            }
        }
    }

    private fun handleResults(bundle: Bundle?) {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (list.isNotEmpty()) lastHeard = list[0]

        for (text in list) {
            val cmd = CommandParser.parse(text) ?: continue
            val now = System.currentTimeMillis()
            if (now - lastActionAt < 4000) return // ignore repeats of the same sentence
            lastActionAt = now

            when (cmd) {
                Command.LOCK -> {
                    lastAction = if (lockNow(this)) "Locked" else "Lock failed: enable Device Admin in the app"
                }
                Command.UNLOCK -> {
                    unlockPhone()
                    lastAction = "Unlock requested"
                }
            }
            return
        }
    }

    // ---- Unlock ------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun unlockPhone() {
        // 1. Turn the screen on.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "saravoicelock:wake"
        )
        wl.acquire(10_000)

        // 2. Ask Android to dismiss the lock screen (UnlockActivity does the work).
        val intent = Intent(this, UnlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Background activity starts can be blocked; the full-screen notification below is the fallback.
        }

        val pi = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Unlocking")
            .setContentText("Tap if the phone does not unlock")
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }
}
