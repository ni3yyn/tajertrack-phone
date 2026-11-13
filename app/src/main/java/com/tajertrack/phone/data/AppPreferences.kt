package com.tajertrack.phone.data

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("tajertrack_phone_preferences", Context.MODE_PRIVATE)

    var selectedSubscriptionId: Int
        get() = preferences.getInt(KEY_SUBSCRIPTION_ID, -1)
        set(value) = preferences.edit().putInt(KEY_SUBSCRIPTION_ID, value).apply()

    var relayEnabled: Boolean
        get() = preferences.getBoolean(KEY_RELAY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_RELAY_ENABLED, value).apply()

    var smsSetupUnlocked: Boolean
        get() = preferences.getBoolean(KEY_SMS_SETUP_UNLOCKED, false)
        set(value) = preferences.edit().putBoolean(KEY_SMS_SETUP_UNLOCKED, value).apply()

    companion object {
        private const val KEY_SUBSCRIPTION_ID = "selected_subscription_id"
        private const val KEY_RELAY_ENABLED = "relay_enabled"
        private const val KEY_SMS_SETUP_UNLOCKED = "sms_setup_unlocked"
    }
}
