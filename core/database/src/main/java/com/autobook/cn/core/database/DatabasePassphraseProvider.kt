package com.autobook.cn.core.database

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class DatabasePassphraseProvider(
    private val context: Context,
) {
    fun getOrCreate(): ByteArray {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = preferences.getString(KEY_ENCRYPTED, null)
        val iv = preferences.getString(KEY_IV, null)
        if (encrypted != null && iv != null) {
            return decrypt(encrypted, iv)
        }

        val generated = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val encryptedBytes = cipher.doFinal(generated)
            preferences.edit()
                .putString(KEY_ENCRYPTED, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
            generated
        }.getOrElse {
            preferences.edit()
                .putString(KEY_LEGACY, Base64.encodeToString(generated, Base64.NO_WRAP))
                .apply()
            generated
        }
    }

    private fun decrypt(
        encrypted: String,
        iv: String,
    ): ByteArray {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                javax.crypto.spec.GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
        }.getOrElse {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            preferences.getString(KEY_LEGACY, null)?.let { legacy ->
                Base64.decode(legacy, Base64.NO_WRAP)
            } ?: ByteArray(32).also { bytes ->
                java.security.SecureRandom().nextBytes(bytes)
            }
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "autobook_db_security"
        private const val KEY_ALIAS = "autobook_db_passphrase"
        private const val KEY_ENCRYPTED = "encrypted_passphrase"
        private const val KEY_IV = "encrypted_passphrase_iv"
        private const val KEY_LEGACY = "legacy_plaintext_passphrase"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
