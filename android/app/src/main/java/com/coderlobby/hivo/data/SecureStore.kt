package com.coderlobby.hivo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the refresh token encrypted with an AES-GCM key that lives in the Android Keystore
 * (the key cannot be exported from the device). The access token is never written to disk.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("hivo_secure", Context.MODE_PRIVATE)

    fun saveRefreshToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = b64(cipher.iv) + ":" + b64(encrypted)
        prefs.edit().putString(KEY_REFRESH, packed).apply()
    }

    fun readRefreshToken(): String? {
        val packed = prefs.getString(KEY_REFRESH, null) ?: return null
        return try {
            val (iv, data) = packed.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key lost or data corrupted (for example after a restore): treat as signed out.
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_REFRESH).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hivo_refresh_token_key"
        const val KEY_REFRESH = "refresh_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
