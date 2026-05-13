package com.transcribecare.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcribecare.app.model.TranscriptSegment

/**
 * Shared composable that renders transcript segments as a single continuous
 * block of text without line breaks between segments.
 *
 * The text is vertically scrollable and automatically scrolls to the bottom
 * when new content is appended, keeping the most recent transcription in view.
 *
 * Applies appropriate line height (1.5x font size) to prevent overlapping
 * lines when text wraps, especially in large text mode.
 *
 * @param segments The list of finalized transcript segments to display.
 * @param interimText Optional interim (partial) recognition text appended at the end.
 * @param largeTextMode Whether large text accessibility mode is enabled.
 * @param modifier Modifier applied to the outer container.
 * @param accessibilityLabel Content description for the transcript container.
 */
@Composable
fun TranscriptText(
    segments: List<TranscriptSegment>,
    interimText: String = "",
    largeTextMode: Boolean,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = "Transcript display"
) {
    val baseFontSize = if (largeTextMode) 44.sp else 20.sp
    val lineHeight = if (largeTextMode) 66.sp else 30.sp

    val fullTranscript = buildString {
        segments.forEachIndexed { index, segment ->
            if (index > 0) append(" ")
            append(segment.text)
        }
        if (interimText.isNotEmpty()) {
            if (segments.isNotEmpty()) append(" ")
            append(interimText)
        }
    }

    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when transcript content changes
    LaunchedEffect(fullTranscript) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .semantics {
                contentDescription = accessibilityLabel
            }
    ) {
        Text(
            text = fullTranscript,
            fontSize = baseFontSize,
            lineHeight = lineHeight,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }
}
