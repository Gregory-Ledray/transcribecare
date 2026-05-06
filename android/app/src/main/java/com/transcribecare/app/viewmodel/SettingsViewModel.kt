package com.transcribecare.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Settings screen managing user preferences.
 * Stub implementation — persistence will be wired later.
 */
class SettingsViewModel : ViewModel() {

    private val _largeTextMode = MutableStateFlow(false)
    val largeTextMode: StateFlow<Boolean> = _largeTextMode.asStateFlow()

    /**
     * Toggles the large text mode preference.
     */
    fun toggleLargeTextMode() {
        _largeTextMode.value = !_largeTextMode.value
    }

    /**
     * Sets the large text mode preference to a specific value.
     */
    fun setLargeTextMode(enabled: Boolean) {
        _largeTextMode.value = enabled
    }
}
