package com.tajertrack.phone.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.tajertrack.phone.data.AppPreferences

/**
 * Restores the SMS relay only when the merchant explicitly left it enabled
 * before a real device reboot. It never launches the app's UI.
 *
 * Android 15+ may also redeliver BOOT_COMPLETED when a force-stopped app is
 * first opened. AppPreferences guards that same-boot case with BOOT_COUNT.
 */
class SmsGatewayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return

        val preferences = AppPreferences(context)
        if (!preferences.relayEnabled) {
            Log.i(TAG, "Skipping automatic start: the SMS relay was disabled by the user.")
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED && !preferences.claimBootAutoStart()) {
            Log.i(TAG, "Skipping BOOT_COMPLETED: no new device boot since the relay was enabled.")
            return
        }

        val permissionsGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if (!permissionsGranted) {
            preferences.relayConnectionState = "attention"
            preferences.relayStatusMessage =
                "لم يبدأ الإرسال بعد تشغيل الهاتف لأن أحد أذونات SMS أو الشرائح غير متاح. افتح التطبيق وامنح الإذن ثم شغّل الإرسال."
            preferences.relayStatusUpdatedAt = System.currentTimeMillis()
            Log.w(TAG, "Relay was enabled, but required SMS permissions are missing after boot.")
            return
        }

        preferences.relayConnectionState = "connecting"
        preferences.relayStatusMessage = "جارٍ استعادة خدمة الإرسال بعد تشغيل الهاتف..."
        preferences.relayStatusUpdatedAt = System.currentTimeMillis()

        try {
            val serviceIntent = Intent(context, SmsGatewayService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "Restoring the previously enabled SMS relay after $action.")
        } catch (error: SecurityException) {
            recordUserActionRequired(
                preferences,
                "منع Android استعادة خدمة الإرسال تلقائياً. افتح التطبيق واضغط تشغيل الإرسال."
            )
            Log.w(TAG, "Android denied the SMS relay foreground service start.", error)
        } catch (error: IllegalStateException) {
            recordUserActionRequired(
                preferences,
                "تعذر استعادة خدمة الإرسال تلقائياً. افتح التطبيق واضغط تشغيل الإرسال."
            )
            Log.w(TAG, "The SMS relay could not be restarted from the system broadcast.", error)
        } catch (error: Exception) {
            recordUserActionRequired(
                preferences,
                "لم يتمكن الهاتف من استعادة خدمة الإرسال. افتح التطبيق للتحقق من الحالة."
            )
            Log.w(TAG, "Unexpected SMS relay boot-recovery failure.", error)
        }
    }

    private fun recordUserActionRequired(preferences: AppPreferences, message: String) {
        preferences.relayConnectionState = "needs_user_start"
        preferences.relayStatusMessage = message
        preferences.relayStatusUpdatedAt = System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "TajerTrackBootReceiver"
    }
}
