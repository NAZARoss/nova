package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdminAuthManager(private val context: Context) {

    private val keystoreManager = KeystoreManager(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("nova_secure_auth", Context.MODE_PRIVATE)

    private val _isAdminLoggedIn = MutableStateFlow(false)
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()

    private var sessionToken: String? = null
    private var sessionExpiresAt: Long = 0L

    init {
        initializeDefaultPinIfNeeded()
    }

    private fun initializeDefaultPinIfNeeded() {
        if (!prefs.contains(KEY_ADMIN_PIN_HASH)) {
            // Default initial PIN is 2468 (or 1337)
            val salt = keystoreManager.generateSalt()
            val hash = keystoreManager.hashPin("2468", salt)
            prefs.edit()
                .putString(KEY_ADMIN_SALT, salt)
                .putString(KEY_ADMIN_PIN_HASH, hash)
                .apply()
        }
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_ADMIN_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: return false
        val calculatedHash = keystoreManager.hashPin(pin, salt)

        if (storedHash == calculatedHash) {
            // Success: generate temporary session
            sessionToken = keystoreManager.generateSalt()
            sessionExpiresAt = System.currentTimeMillis() + SESSION_DURATION_MS
            _isAdminLoggedIn.value = true
            return true
        }
        return false
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        val newSalt = keystoreManager.generateSalt()
        val newHash = keystoreManager.hashPin(newPin, newSalt)
        prefs.edit()
            .putString(KEY_ADMIN_SALT, newSalt)
            .putString(KEY_ADMIN_PIN_HASH, newHash)
            .apply()
        return true
    }

    fun logout() {
        sessionToken = null
        sessionExpiresAt = 0L
        _isAdminLoggedIn.value = false
    }

    fun isSessionValid(): Boolean {
        val valid = _isAdminLoggedIn.value && System.currentTimeMillis() < sessionExpiresAt
        if (!valid && _isAdminLoggedIn.value) {
            logout()
        }
        return valid
    }

    companion object {
        private const val KEY_ADMIN_PIN_HASH = "sec_admin_pin_hash"
        private const val KEY_ADMIN_SALT = "sec_admin_salt"
        private const val SESSION_DURATION_MS = 60 * 60 * 1000L // 1 hour session
    }
}
