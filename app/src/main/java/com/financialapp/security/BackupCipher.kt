package com.financialapp.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface BackupCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(cipherText: String): String
}

class AndroidSecurePreferences(context: Context) {
    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        "finance_secure_backup_prefs",
        "finance_secure_backup_master_key",
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? = preferences.getString(key, null)
}

class AesGcmBackupCipher(
    private val securePreferences: AndroidSecurePreferences,
) : BackupCipher {
    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val secretKey = getOrCreateKey()
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secretKey, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun decrypt(cipherText: String): String {
        val decoded = try {
            Base64.getDecoder().decode(cipherText)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Encrypted backup payload is invalid", exception)
        }

        require(decoded.size > 12) { "Encrypted backup payload is invalid" }
        val iv = decoded.copyOfRange(0, 12)
        val encrypted = decoded.copyOfRange(12, decoded.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(getOrCreateKey(), "AES"), GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(encrypted)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun getOrCreateKey(): ByteArray {
        val key = securePreferences.getString(KEY_NAME)
        if (key != null) return Base64.getDecoder().decode(key)

        val generated = ByteArray(32)
        secureRandom.nextBytes(generated)
        val encoded = Base64.getEncoder().encodeToString(generated)
        securePreferences.putString(KEY_NAME, encoded)
        return generated
    }

    private companion object {
        private const val KEY_NAME = "backup_encryption_key"
    }
}

class PlaintextBackupCipher : BackupCipher {
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(cipherText: String): String = cipherText
}
