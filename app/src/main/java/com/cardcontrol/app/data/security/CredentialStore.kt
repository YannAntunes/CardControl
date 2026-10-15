package com.cardcontrol.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "card_control_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSmtpCredentials(senderEmail: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_EMAIL, senderEmail.trim())
            .putString(KEY_PASSWORD, appPassword.replace(" ", ""))
            .apply()
    }

    fun getSenderEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""
    fun getAppPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""
    fun isConfigured(): Boolean = getSenderEmail().isNotBlank() && getAppPassword().isNotBlank()

    companion object {
        private const val KEY_EMAIL = "smtp_sender_email"
        private const val KEY_PASSWORD = "smtp_app_password"
    }
}
