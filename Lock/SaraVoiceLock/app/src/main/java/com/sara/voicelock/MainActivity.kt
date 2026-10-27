package com.sara.voicelock

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Sara Voice Lock"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Say: \"Hey Sara, lock my phone\" or \"Hey Sara, unlock my phone\".\nDo steps 1 to 3 once, in order."
            setPadding(0, pad / 2, 0, pad / 2)
        })

        statusView = TextView(this).apply {
            textSize = 15f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(statusView)

        fun addButton(label: String, onClick: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            })
        }

        addButton("1. Allow microphone and notifications") { askPermissions() }
        addButton("2. Enable Device Admin (needed to lock)") { askAdmin() }
        addButton("3. Start listening for \"Hey Sara\"") { startVoice() }
        addButton("Stop listening") { stopVoice() }
        addButton("Test: lock now") {
            if (!VoiceService.lockNow(this)) toast("Enable Device Admin first (step 2)")
        }
        addButton("Optional: allow background running (battery)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isAdmin(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
    }

    private fun refresh() {
        statusView.text = buildString {
            append("Microphone permission: ").append(if (hasMic()) "OK" else "missing").append('\n')
            append("Device Admin (lock): ").append(if (isAdmin()) "OK" else "not enabled").append('\n')
            append("Listening: ").append(if (VoiceService.isRunning) "ON" else "off").append('\n')
            append("Last heard: ").append(VoiceService.lastHeard.ifEmpty { "-" }).append('\n')
            append("Last action: ").append(VoiceService.lastAction.ifEmpty { "-" })
        }
    }

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun askAdmin() {
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Needed so Sara can lock the screen when you ask. It cannot read or erase your data."
            )
        startActivity(i)
    }

    private fun startVoice() {
        if (!hasMic()) {
            toast("Allow the microphone first (step 1)")
            return
        }
        startForegroundService(Intent(this, VoiceService::class.java))
    }

    private fun stopVoice() {
        startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_STOP))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
