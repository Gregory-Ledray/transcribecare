package com.transcribecare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.transcribecare.app.ui.theme.TranscribeCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranscribeCareTheme {
                TranscribeCareApp()
            }
        }
    }
}
