package com.transcribecare.app

import androidx.compose.runtime.Composable
import com.transcribecare.app.ui.navigation.AppNavigation

/**
 * Root composable for the TranscribeCare application.
 * Delegates to AppNavigation which sets up the bottom navigation bar
 * and all screen destinations.
 */
@Composable
fun TranscribeCareApp() {
    AppNavigation()
}
