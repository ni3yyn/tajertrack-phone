package com.tajertrack.phone.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the per-device bearer credential encrypted by a non-exportable Android Keystore key. */
class DeviceTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("tajertrack_device", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_TOKEN_DATA, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun readToken(): String? {
        val iv = preferences.getString(KEY_TOKEN_IV, null) ?: return null
        val data = preferences.getString(KEY_TOKEN_DATA, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_TOKEN_IV)
            .remove(KEY_TOKEN_DATA)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_STORE_NAME)
            .apply()
    }

    fun saveDevice(deviceId: String, storeName: String) {
        preferences.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_STORE_NAME, storeName)
            .apply()
    }

    fun deviceId(): String = preferences.getString(KEY_DEVICE_ID, "").orEmpty()
    fun storeName(): String = preferences.getString(KEY_STORE_NAME, "").orEmpty()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "tajertrack-phone-device-token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_TOKEN_IV = "device_token_iv"
        private const val KEY_TOKEN_DATA = "device_token_data"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_STORE_NAME = "store_name"
    }
}
