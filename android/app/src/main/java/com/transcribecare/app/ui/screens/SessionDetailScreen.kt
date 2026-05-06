package com.transcribecare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcribecare.app.model.RecordingSession
import com.transcribecare.app.model.SegmentType
import com.transcribecare.app.model.TranscriptSegment
import com.transcribecare.app.viewmodel.SettingsViewModel

/**
 * Session detail screen displaying the full transcript of a recording session.
 * Supports Large Text Mode and provides a share button for the session.
 *
 * @param session The recording session to display.
 * @param settingsViewModel ViewModel providing user preferences like large text mode.
 * @param onShareClick Callback invoked when the user taps the share button.
 */
@Composable
fun SessionDetailScreen(
    session: RecordingSession,
    settingsViewModel: SettingsViewModel,
    onShareClick: () -> Unit,
) {
    val largeTextMode by settingsViewModel.largeTextMode.collectAsState()

    val titleFontSize = if (largeTextMode) 40.sp else 22.sp
    val metadataFontSize = if (largeTextMode) 36.sp else 14.sp
    val segmentFontSize = if (largeTextMode) 36.sp else 16.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics {
                contentDescription = "Session detail for ${session.title}"
            }
    ) {
        // Session header
        SessionDetailHeader(
            session = session,
            titleFontSize = titleFontSize,
            metadataFontSize = metadataFontSize,
            onShareClick = onShareClick
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Full transcript with all segments
        SessionTranscriptList(
            segments = session.segments,
            segmentFontSize = segmentFontSize,
            largeTextMode = largeTextMode,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Header section displaying session title, date, time, duration, and share button.
 */
@Composable
private fun SessionDetailHeader(
    session: RecordingSession,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    metadataFontSize: androidx.compose.ui.unit.TextUnit,
    onShareClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.title,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Session title: ${session.title}"
                    }
            )

            // Share button with 48dp minimum touch target
            IconButton(
                onClick = onShareClick,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Share session. Double tap to share this transcript."
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null, // Handled by parent semantics
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Date and time
        Text(
            text = "${session.date} at ${session.time}",
            fontSize = metadataFontSize,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.semantics {
                contentDescription = "Date: ${session.date}, Time: ${session.time}"
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Duration
        Text(
            text = "Duration: ${session.duration}",
            fontSize = metadataFontSize,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.semantics {
                contentDescription = "Duration: ${session.duration}"
            }
        )
    }
}

/**
 * Scrollable list displaying all transcript segments with color-coding by type.
 * Uses LazyColumn for efficient rendering of long transcripts.
 */
@Composable
private fun SessionTranscriptList(
    segments: List<TranscriptSegment>,
    segmentFontSize: androidx.compose.ui.unit.TextUnit,
    largeTextMode: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = "Full transcript with ${segments.size} segments"
            }
    ) {
        items(segments, key = { it.id }) { segment ->
            DetailTranscriptSegmentItem(
                segment = segment,
                fontSize = segmentFontSize,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * A single transcript segment in the detail view, color-coded by type.
 *
 * - CURRENT: Primary color (most recent finalized segment)
 * - RECENT: Secondary color (previously current)
 * - PAST: Muted on-background color (older segments)
 */
@Composable
private fun DetailTranscriptSegmentItem(
    segment: TranscriptSegment,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val textColor = when (segment.type) {
        SegmentType.CURRENT -> MaterialTheme.colorScheme.primary
        SegmentType.RECENT -> MaterialTheme.colorScheme.secondary
        SegmentType.PAST -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    }

    val typeLabel = when (segment.type) {
        SegmentType.CURRENT -> "Current segment"
        SegmentType.RECENT -> "Recent segment"
        SegmentType.PAST -> "Past segment"
    }

    Text(
        text = segment.text,
        fontSize = fontSize,
        color = textColor,
        fontWeight = if (segment.type == SegmentType.CURRENT) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "$typeLabel: ${segment.text}"
            }
    )
}
