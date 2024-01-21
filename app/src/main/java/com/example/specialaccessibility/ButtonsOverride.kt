package com.example.specialaccessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.CountDownTimer
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent


class ButtonsOverride: AccessibilityService() {
    class SpecialDeviceAdminReceiver: DeviceAdminReceiver()
    private var backPressRepeatCount = 0
    private var deviceAdminReceiver: ComponentName? = null
    private var timer = object : CountDownTimer(300, 1000) {

        override fun onTick(millisUntilFinished: Long) {
        }

        override fun onFinish() {
            backPressRepeatCount = 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        deviceAdminReceiver = ComponentName(this, SpecialDeviceAdminReceiver::class.java)
        Log.i("Buttons Override", "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("Buttons Override", "Service connected")
        val formatted = serviceInfo.toString()
            .replace(", ", ",\n   ")
        Log.i("Buttons Override", "ServiceInfo {\n   %s\n}".format(formatted))

//        val intentFilterACSD = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
//
//        val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
//                    //do what you want here
//                    val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
//
//                    val taskId = activityManager.getRecentTasks(1, 0)[0].id
//                    activityManager.moveTaskToFront(taskId, 0)
//                }
//            }
//        }
//        this.registerReceiver(broadcastReceiver, intentFilterACSD)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.className == null)
                return

            val recentsNames = arrayOf(
                "com.android.internal.policy.impl.RecentApplicationsDialog",
                "com.android.systemui.recent.RecentsActivity",
                "com.android.systemui.recents.RecentsActivity")

            if (event.className.toString() in recentsNames) {
                Log.i("Buttons Override", "Found matching activity")
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager

                val taskId = activityManager.getRecentTasks(1,0)[0].id
                activityManager.moveTaskToFront(taskId, 0)

            } else {
                Log.i("Buttons Override", "Found no matching activities")
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isInteractive) {
                    val dpm = this.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    if (!dpm.isAdminActive(deviceAdminReceiver!!)) {
                        Log.i("Buttons Override", "Not admin")
                    } else {
                        val startMain = Intent(Intent.ACTION_MAIN)
                        startMain.addCategory(Intent.CATEGORY_HOME)
                        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(startMain)

                        dpm.lockNow()
                    }
                }
                Log.i("ButtonsOverride","HOME button pressed!")
                return super.onKeyEvent(event)
            }
            KeyEvent.KEYCODE_RECENT_APPS -> {
                Log.i("ButtonsOverride","RECENT APPS button pressed!")
                return true
            }
            KeyEvent.KEYCODE_APP_SWITCH -> {
                Log.i("ButtonsOverride","APP SWITCH button pressed!")

                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                timer.cancel()
                backPressRepeatCount += 1
                timer.start()
                return if (backPressRepeatCount < 3) {
                    Log.i("ButtonsOverride","BACK button pressed!")
                    true
                } else {
                    Log.i("ButtonsOverride","BACK button pressed three times in succession!")
                    super.onKeyEvent(event)
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Log.i("ButtonsOverride","VOLUME DOWN button pressed!")
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                Log.i("ButtonsOverride","VOLUME UP button pressed!")
                return true
            }
            else -> {
                return super.onKeyEvent(event)
            }
        }
    }
}