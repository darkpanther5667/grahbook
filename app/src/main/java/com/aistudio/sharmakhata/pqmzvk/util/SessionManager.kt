package com.aistudio.sharmakhata.pqmzvk.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

object SessionManager {
    private const val PREFS = "grahbook_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_STORE_ID = "store_id"
    private const val KEY_PHONE = "phone"

    // Fallback to regular SharedPreferences if encryption fails
    private var fallbackPrefs: SharedPreferences? = null
    private var encryptedPrefs: SharedPreferences? = null

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var storeId: String? = null
        private set

    @Volatile
    var phone: String? = null
        private set

    /**
     * Load session from secure storage
     */
    fun load(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            token = encryptedPrefs?.getString(KEY_TOKEN, null)
            storeId = encryptedPrefs?.getString(KEY_STORE_ID, null)
            phone = encryptedPrefs?.getString(KEY_PHONE, null)

        } catch (e: GeneralSecurityException) {
            // Fallback to regular SharedPreferences if encryption fails
            fallbackPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            token = fallbackPrefs?.getString(KEY_TOKEN, null)
            storeId = fallbackPrefs?.getString(KEY_STORE_ID, null)
            phone = fallbackPrefs?.getString(KEY_PHONE, null)
        } catch (e: IOException) {
            // Fallback to regular SharedPreferences if encryption fails
            fallbackPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            token = fallbackPrefs?.getString(KEY_TOKEN, null)
            storeId = fallbackPrefs?.getString(KEY_STORE_ID, null)
            phone = fallbackPrefs?.getString(KEY_PHONE, null)
        }
    }

    /**
     * Save token securely
     */
    fun setToken(context: Context, value: String?) {
        try {
            if (encryptedPrefs != null) {
                encryptedPrefs?.edit()?.putString(KEY_TOKEN, value)?.apply()
            } else {
                // Fallback to regular SharedPreferences
                fallbackPrefs?.edit()?.putString(KEY_TOKEN, value)?.apply()
            }
            token = value
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences on error
            fallbackPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fallbackPrefs?.edit()?.putString(KEY_TOKEN, value)?.apply()
            token = value
        }
    }

    /**
     * Save store ID and phone after registration
     */
    fun saveStoreInfo(context: Context, storeIdValue: String?, phoneValue: String?) {
        try {
            if (encryptedPrefs != null) {
                encryptedPrefs?.edit()?.putString(KEY_STORE_ID, storeIdValue)?.putString(KEY_PHONE, phoneValue)?.apply()
            } else {
                fallbackPrefs?.edit()?.putString(KEY_STORE_ID, storeIdValue)?.putString(KEY_PHONE, phoneValue)?.apply()
            }
            storeId = storeIdValue
            phone = phoneValue
        } catch (e: Exception) {
            fallbackPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fallbackPrefs?.edit()?.putString(KEY_STORE_ID, storeIdValue)?.putString(KEY_PHONE, phoneValue)?.apply()
            storeId = storeIdValue
            phone = phoneValue
        }
    }

    /**
     * Clear session (logout)
     */
    fun clear(context: Context) {
        try {
            encryptedPrefs?.edit()?.clear()?.apply()
            fallbackPrefs?.edit()?.clear()?.apply()
            token = null
            storeId = null
            phone = null
        } catch (e: Exception) {
            fallbackPrefs?.edit()?.clear()?.apply()
            token = null
            storeId = null
            phone = null
        }
    }

    /**
     * Clear token in-memory only (for use from OkHttp interceptors without context).
     * This wipes the in-memory token so subsequent requests won't include it.
     * Persistent prefs are cleared later when the user navigates to login.
     */
    fun clearToken() {
        token = null
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return !token.isNullOrBlank()
    }
}

