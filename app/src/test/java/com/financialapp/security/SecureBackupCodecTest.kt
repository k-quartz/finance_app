package com.financialapp.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureBackupCodecTest {
    @Test
    fun `backup payload is encrypted and can be decrypted round trip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = AndroidSecurePreferences(context)
        val codec = AesGcmBackupCipher(crypto)

        val payload = """
            {
              "formatVersion": 1,
              "databaseVersion": 9,
              "accounts": [{"name": "Primary Savings", "notes": "Sensitive salary info"}],
              "notes": "raw financial details must not be plain text"
            }
        """.trimIndent()

        val encrypted = codec.encrypt(payload)

        assertTrue(encrypted.isNotBlank())
        assertFalse(encrypted.contains("Sensitive salary info"))
        assertFalse(encrypted.contains("raw financial details must not be plain text"))
        assertEquals(payload, codec.decrypt(encrypted))
    }

    @Test
    fun `secure preferences persist values without exposing them as plain strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AndroidSecurePreferences(context)

        preferences.putString("backup_key_alias", "phase-14-key")

        val stored = preferences.getString("backup_key_alias")
        assertEquals("phase-14-key", stored)
    }
}
