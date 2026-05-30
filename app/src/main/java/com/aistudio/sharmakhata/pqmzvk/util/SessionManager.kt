package com.aistudio.sharmakhata.pqmzvk.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Manages persistent session data using EncryptedSharedPreferences with
 * automatic fallback to regular SharedPreferences when encryption is unavailable.
 *
 * IMPORTANT: Always call [load] before reading any field. Call [saveStoreInfo]
 * after registration and [setToken] after OTP verification.
 */
object SessionManager {
    private const val PREFS = "grahbook_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_STORE_ID = "store_id"
    private const val KEY_PHONE = "phone"

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var storeId: String? = null
        private set

    @Volatile
    var phone: String? = null
        private set

    private var prefs: SharedPreferences? = null

    /**
     * Load session from secure storage. MUST be called before reading fields.
     */
    fun load(context: Context) {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: GeneralSecurityException | IOException | RuntimeException) {
            // Fallback to regular SharedPreferences if encryption setup fails
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }

        token = prefs?.getString(KEY_TOKEN, null)
        storeId = prefs?.getString(KEY_STORE_ID, null)
        phone = prefs?.getString(KEY_PHONE, null)
    }

    /**
     * Reload fields from persistent storage (useful after registration from another screen).
     */
    fun reload(context: Context) {
        load(context)
    }

    /**
     * Save JWT token after successful OTP verification.
     */
    fun setToken(context: Context, value: String?) {
        if (prefs == null) load(context)
        edit { putString(KEY_TOKEN, value) }
        token = value
    }

    /**
     * Save store ID and phone after registration (or when recovering existing store).
     * Use [reload] after this to ensure in-memory values match.
     */
    fun saveStoreInfo(context: Context, storeIdValue: String?, phoneValue: String?) {
        if (prefs == null) load(context)
        edit {
            putString(KEY_STORE_ID, storeIdValue)
            putString(KEY_PHONE, phoneValue)
        }
        storeId = storeIdValue
        phone = phoneValue
    }

    /**
     * Clear all session data (logout).
     */
    fun clear(context: Context) {
        prefs?.edit()?.clear()?.apply()
        token = null
        storeId = null
        phone = null
    }

    /**
     * Clear in-memory token only (used by OkHttp 401 interceptor).
     * Persistent storage is cleared when user navigates to login.
     */
    fun clearToken() {
        token = null
    }

    /** Returns true when a session token is present and non-blank. */
    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    /** Returns true when a storeId is present and non-blank. */
    fun hasStoreId(): Boolean = !storeId.isNullOrBlank()

    /** Returns storeId or empty string (for safe JSON serialization). */
    fun getStoreIdSafe(): String = storeId ?: ""

    /** Returns phone or empty string. */
    fun getPhoneSafe(): String = phone ?: ""

    // ── helpers ──────────────────────────────────────────────────────────

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply { block(); apply() }
    }
}
