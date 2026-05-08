package com.transcribecare.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Settings screen managing user preferences.
 * Persists settings to SharedPreferences so they survive app restarts.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _largeTextMode = MutableStateFlow(
        prefs.getBoolean(KEY_LARGE_TEXT_MODE, true)
    )
    val largeTextMode: StateFlow<Boolean> = _largeTextMode.asStateFlow()

    /**
     * Toggles the large text mode preference and persists the new value.
     */
    fun toggleLargeTextMode() {
        val newValue = !_largeTextMode.value
        _largeTextMode.value = newValue
        prefs.edit().putBoolean(KEY_LARGE_TEXT_MODE, newValue).apply()
    }

    /**
     * Sets the large text mode preference to a specific value and persists it.
     */
    fun setLargeTextMode(enabled: Boolean) {
        _largeTextMode.value = enabled
        prefs.edit().putBoolean(KEY_LARGE_TEXT_MODE, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "transcribecare_settings"
        private const val KEY_LARGE_TEXT_MODE = "large_text_mode"
    }
}
