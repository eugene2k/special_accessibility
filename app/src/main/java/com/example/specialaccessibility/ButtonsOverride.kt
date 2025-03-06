package com.example.specialaccessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

const val PROCESS_VIEW_CLICKED_EVENTS = false
const val LIMITED_MODE_DISABLE = "com.example.specialaccessibility.LIMITED_MODE_DISABLE"

class ActivityValidator {
    companion object {
        const val WHATSAPP_CALL = "com.whatsapp.voipcalling.VoipActivityV2"
        const val VIBER_CALL = "com.viber.voip.phone.PhoneFragmentActivity"
    }

    private val activitySequences: Array<Pair<String, Array<Int>>> = arrayOf(
        Pair("com.example.viberlauncher.LockedActivity", arrayOf()),
        Pair("android.widget.FrameLayout", arrayOf(0, 1)),
        Pair(VIBER_CALL, arrayOf(0, 1)),
        Pair(WHATSAPP_CALL, arrayOf(0, 1)),
        Pair("org.telegram.ui.LaunchActivity", arrayOf(0, 1)),
        Pair("com.viber.voip.HomeActivity", arrayOf(2)),
    )
    private val sequenceStarts = arrayOf(2, 3, 4, 5)
    private var sequenceCursor = -1
    fun isValid(activity: String): Boolean {
        sequenceCursor = if (sequenceCursor == -1) {
            sequenceStarts.find { activitySequences[it].first == activity } ?: -1
        } else {
            activitySequences[sequenceCursor].second.find { activitySequences[it].first == activity }
                ?: -1
        }
        return sequenceCursor != -1
    }

    fun reset() {
        sequenceCursor = -1
    }
}

val PACKAGE_INSTALLER_ACTIVITIES = arrayOf(
    "com.android.packageinstaller.permission.ui.GrantPermissionsActivity",
    "com.google.android.packageinstaller"
)

class ButtonsOverride : AccessibilityService() {
    class SpecialDeviceAdminReceiver : DeviceAdminReceiver()

    private var deviceAdminReceiver: ComponentName? = null
    private var limitedMode = false
    private val disableLimitedModeHandler = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            limitedMode = false
            Log.i(this.javaClass.name, "Limited mode disabled")
        }
    }

    private var activityValidator = ActivityValidator()

    private var wakeLock: PowerManager.WakeLock? = null
    private var lockDevice = false
    private var endCallButton: AccessibilityNodeInfo? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        deviceAdminReceiver = ComponentName(this, SpecialDeviceAdminReceiver::class.java)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            this.javaClass.name
        )
        registerReceiver(disableLimitedModeHandler, IntentFilter(LIMITED_MODE_DISABLE))

        Log.i(this.javaClass.name, "Service created")
    }


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(this.javaClass.name, "Service connected")
        val formatted = serviceInfo.toString()
            .replace(", ", ",\n   ")
        Log.i(this.javaClass.name, "ServiceInfo {\n   %s\n}".format(formatted))
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.i(this.javaClass.name, event.packageName?.toString() ?: "<null>")
                    Log.i(this.javaClass.name, event.className.toString())
                    Log.i(this.javaClass.name, "limitedMode=%b".format(limitedMode))
                    lockDevice = false
                    endCallButton = null
                    if (event.packageName !in PACKAGE_INSTALLER_ACTIVITIES) {
                        if (event.packageName =="com.example.viberlauncher") {
                            if (event.className =="com.example.viberlauncher.LockedActivity") {
                                activityValidator.reset()
                                if (wakeLock!!.isHeld)
                                    wakeLock!!.release()
                                lockDevice = true
                                limitedMode = true
                                Log.i(this.javaClass.name, "Limited mode enabled")
                            }
                        } else {
                            if (limitedMode) {
                                if (!activityValidator.isValid(event.className.toString())) {
                                    startViberLauncher()
                                } else {
                                    val phoneActivities = arrayOf(
                                        ActivityValidator.WHATSAPP_CALL,
                                        ActivityValidator.VIBER_CALL
                                    )
                                    val activity = event.className.toString()
                                    if (activity in phoneActivities) {
                                        lockDevice = true
                                        if (PROCESS_VIEW_CLICKED_EVENTS) {
                                            assert(event.source != null)
                                            val childCount = event.source!!.childCount
                                            for (i in 0..<childCount) {
                                                val child = event.source!!.getChild(i)
                                                Log.i(
                                                    this.javaClass.name,
                                                    "%d - %s".format(
                                                        i,
                                                        child.hashCode().toHexString()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    if (activity == ActivityValidator.WHATSAPP_CALL) {
                                        if (!wakeLock!!.isHeld) wakeLock!!.acquire(120 * 60 * 1000L /*120 minutes*/)
                                        val toggleSpeakerButton = event.source?.getChild(6)
                                        toggleSpeakerButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        endCallButton = event.source?.getChild(4)
                                    } else if (activity == ActivityValidator.VIBER_CALL) {
                                        val toggleSpeakerButton = event.source?.getChild(0)
                                        toggleSpeakerButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        endCallButton = event.source?.getChild(3)
                                    }
                                }
                            }
                        }
                    }
                }

                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    assert(event.source != null)
                    val name = event.source.hashCode().toHexString()
                    Log.i(this.javaClass.name, name)
                }

                else -> {
                    Log.i(this.javaClass.name, event.eventType.toHexString())
                }
            }
        }
    }

    private fun startViberLauncher() {
        val intent = Intent()
        intent.component =
            ComponentName(
                "com.example.viberlauncher",
                "com.example.viberlauncher.LockedActivity"
            )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    override fun onInterrupt() {
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Log.i(this.javaClass.name, "VOLUME DOWN button pressed!")
                return true
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                Log.i(this.javaClass.name, "VOLUME UP button pressed!")
                return true
            }

            KeyEvent.KEYCODE_HOME -> {
                Log.i(this.javaClass.name, "HOME button pressed")
                if (lockDevice) {
                    endCallButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val dpm =
                        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    dpm.lockNow()
                } else {
                    startViberLauncher()
                }
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                Log.i(this.javaClass.name, "BACK button pressed")
                return if (limitedMode) true
                else super.onKeyEvent(event)
            }

            else -> {
                return super.onKeyEvent(event)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(disableLimitedModeHandler)
    }
}