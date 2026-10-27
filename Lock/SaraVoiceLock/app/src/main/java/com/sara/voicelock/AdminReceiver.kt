package com.sara.voicelock

import android.app.admin.DeviceAdminReceiver

/** Lets the app call DevicePolicyManager.lockNow(). Needs the user to enable it once. */
class AdminReceiver : DeviceAdminReceiver()
