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

/** A separate Keystore-encrypted credential for each carrier sender line. */
data class DeviceCredential(
    val deviceId: String,
    val token: String,
    val storeName: String,
    val operatorCode: String?
)

/**
 * Stores per-line bearer credentials encrypted with a non-exportable Android
 * Keystore key. The original single-token keys remain readable for upgrades.
 */
class DeviceTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("tajertrack_device", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val encrypted = encrypt(token)
        preferences.edit()
            .putString(KEY_TOKEN_IV, encrypted.first)
            .putString(KEY_TOKEN_DATA, encrypted.second)
            .apply()
    }

    fun readToken(): String? = readProfiles().firstOrNull()?.token ?: readLegacyToken()

    fun saveDevice(deviceId: String, storeName: String) {
        preferences.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_STORE_NAME, storeName)
            .apply()
    }

    /** Saves a new sender line without replacing any other carrier line on this phone. */
    fun saveProfile(profile: DeviceCredential): Boolean {
        if (profile.deviceId.isBlank() || profile.token.isBlank()) return false
        val encrypted = encrypt(profile.token)
        val ids = preferences.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty().toMutableSet()
        ids.add(profile.deviceId)
        return preferences.edit()
            .putString(profileKey(profile.deviceId, "iv"), encrypted.first)
            .putString(profileKey(profile.deviceId, "data"), encrypted.second)
            .putString(profileKey(profile.deviceId, "store"), profile.storeName.take(120))
            .putString(profileKey(profile.deviceId, "operator"), profile.operatorCode)
            .putStringSet(KEY_PROFILE_IDS, ids)
            .putString(KEY_DEVICE_ID, profile.deviceId)
            .putString(KEY_STORE_NAME, profile.storeName.take(120))
            .remove(KEY_TOKEN_IV)
            .remove(KEY_TOKEN_DATA)
            .commit()
    }

    /** Saves the manager credential and its first sender profile in one preference commit. */
    fun saveManagerPairing(
        managerDeviceId: String,
        managerToken: String,
        profile: DeviceCredential
    ): Boolean {
        if (managerDeviceId.isBlank() || managerToken.isBlank() ||
            profile.deviceId.isBlank() || profile.token.isBlank()
        ) return false

        val managerEncrypted = encrypt(managerToken)
        val senderEncrypted = encrypt(profile.token)
        val ids = preferences.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty().toMutableSet()
        val replacedIds = ids.filter {
            preferences.getString(profileKey(it, "operator"), null) == profile.operatorCode
        }
        replacedIds.forEach(ids::remove)
        ids.add(profile.deviceId)

        val editor = preferences.edit()
        replacedIds.forEach { id ->
            editor.remove(profileKey(id, "iv"))
                .remove(profileKey(id, "data"))
                .remove(profileKey(id, "store"))
                .remove(profileKey(id, "operator"))
        }

        return editor
            .putString(KEY_MANAGER_DEVICE_ID, managerDeviceId)
            .putString(KEY_MANAGER_TOKEN_IV, managerEncrypted.first)
            .putString(KEY_MANAGER_TOKEN_DATA, managerEncrypted.second)
            .putString(KEY_MANAGER_STORE_NAME, profile.storeName.take(120))
            .putString(profileKey(profile.deviceId, "iv"), senderEncrypted.first)
            .putString(profileKey(profile.deviceId, "data"), senderEncrypted.second)
            .putString(profileKey(profile.deviceId, "store"), profile.storeName.take(120))
            .putString(profileKey(profile.deviceId, "operator"), profile.operatorCode)
            .putStringSet(KEY_PROFILE_IDS, ids)
            .putString(KEY_DEVICE_ID, profile.deviceId)
            .putString(KEY_STORE_NAME, profile.storeName.take(120))
            .commit()
    }

    fun managerToken(): String? {
        val iv = preferences.getString(KEY_MANAGER_TOKEN_IV, null) ?: return null
        val data = preferences.getString(KEY_MANAGER_TOKEN_DATA, null) ?: return null
        return decrypt(iv, data)
    }

    fun managerDeviceId(): String =
        preferences.getString(KEY_MANAGER_DEVICE_ID, "").orEmpty()

    fun hasManagerCredential(): Boolean = !managerToken().isNullOrBlank()

    fun clearManagerCredential() {
        preferences.edit()
            .remove(KEY_MANAGER_DEVICE_ID)
            .remove(KEY_MANAGER_TOKEN_IV)
            .remove(KEY_MANAGER_TOKEN_DATA)
            .remove(KEY_MANAGER_STORE_NAME)
            .commit()
    }

    fun readProfiles(): List<DeviceCredential> {
        val profiles = mutableListOf<DeviceCredential>()
        val ids = preferences.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty().toList().sorted()
        ids.forEach { id ->
            val iv = preferences.getString(profileKey(id, "iv"), null)
            val data = preferences.getString(profileKey(id, "data"), null)
            if (!iv.isNullOrBlank() && !data.isNullOrBlank()) {
                val token = decrypt(iv, data)
                if (!token.isNullOrBlank()) {
                    profiles += DeviceCredential(
                        deviceId = id,
                        token = token,
                        storeName = preferences.getString(profileKey(id, "store"), "").orEmpty(),
                        operatorCode = preferences.getString(profileKey(id, "operator"), null)
                            ?.takeIf { it in SUPPORTED_OPERATORS }
                    )
                }
            }
        }

        // Existing single-device installations remain readable after upgrading.
        val legacyToken = readLegacyToken()
        val legacyDeviceId = preferences.getString(KEY_DEVICE_ID, "").orEmpty()
        if (!legacyToken.isNullOrBlank() && legacyDeviceId.isNotBlank() &&
            profiles.none { it.deviceId == legacyDeviceId }
        ) {
            profiles.add(
                0,
                DeviceCredential(
                    deviceId = legacyDeviceId,
                    token = legacyToken,
                    storeName = preferences.getString(KEY_STORE_NAME, "").orEmpty(),
                    operatorCode = null
                )
            )
        }
        return profiles
    }

    fun profile(deviceId: String): DeviceCredential? =
        readProfiles().firstOrNull { it.deviceId == deviceId }

    /** Removes only the revoked line; other operator lines on this phone survive. */
    fun removeProfile(deviceId: String) {
        val ids = preferences.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty().toMutableSet()
        ids.remove(deviceId)
        val editor = preferences.edit()
            .remove(profileKey(deviceId, "iv"))
            .remove(profileKey(deviceId, "data"))
            .remove(profileKey(deviceId, "store"))
            .remove(profileKey(deviceId, "operator"))
            .putStringSet(KEY_PROFILE_IDS, ids)

        if (preferences.getString(KEY_DEVICE_ID, null) == deviceId) {
            val next = ids.firstOrNull()
            editor.remove(KEY_TOKEN_IV).remove(KEY_TOKEN_DATA)
            if (next != null) {
                editor.putString(KEY_DEVICE_ID, next)
                    .putString(KEY_STORE_NAME, preferences.getString(profileKey(next, "store"), "").orEmpty())
            } else {
                editor.remove(KEY_DEVICE_ID).remove(KEY_STORE_NAME)
            }
        }
        editor.commit()
    }

    fun clear() {
        val ids = preferences.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty()
        val editor = preferences.edit()
        ids.forEach { id ->
            editor.remove(profileKey(id, "iv"))
                .remove(profileKey(id, "data"))
                .remove(profileKey(id, "store"))
                .remove(profileKey(id, "operator"))
        }
        editor.remove(KEY_PROFILE_IDS)
            .remove(KEY_TOKEN_IV)
            .remove(KEY_TOKEN_DATA)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_STORE_NAME)
            .remove(KEY_MANAGER_DEVICE_ID)
            .remove(KEY_MANAGER_TOKEN_IV)
            .remove(KEY_MANAGER_TOKEN_DATA)
            .remove(KEY_MANAGER_STORE_NAME)
            .apply()
    }

    fun deviceId(): String =
        readProfiles().firstOrNull()?.deviceId ?: preferences.getString(KEY_DEVICE_ID, "").orEmpty()

    fun storeName(): String =
        readProfiles().firstOrNull()?.storeName ?: preferences.getString(KEY_STORE_NAME, "").orEmpty()

    private fun readLegacyToken(): String? {
        val iv = preferences.getString(KEY_TOKEN_IV, null) ?: return null
        val data = preferences.getString(KEY_TOKEN_DATA, null) ?: return null
        return decrypt(iv, data)
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) to
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(iv: String, data: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun profileKey(deviceId: String, suffix: String): String = "line_${deviceId}_${suffix}"

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
        private const val KEY_PROFILE_IDS = "sender_line_profile_ids"
        private const val KEY_MANAGER_DEVICE_ID = "manager_device_id"
        private const val KEY_MANAGER_TOKEN_IV = "manager_token_iv"
        private const val KEY_MANAGER_TOKEN_DATA = "manager_token_data"
        private const val KEY_MANAGER_STORE_NAME = "manager_store_name"
        private val SUPPORTED_OPERATORS = setOf("djezzy", "mobilis", "ooredoo")
    }
}
