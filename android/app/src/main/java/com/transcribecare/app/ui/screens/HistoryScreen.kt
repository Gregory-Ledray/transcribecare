package com.transcribecare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcribecare.app.model.RecordingSession
import com.transcribecare.app.service.AudioPlayerState
import com.transcribecare.app.viewmodel.HistoryViewModel

/**
 * History screen composable displaying a searchable, scrollable list of recording sessions.
 * Each session card contains integrated playback controls (play/pause toggle, progress bar,
 * and speed selector) rather than a separate playback panel.
 *
 * @param viewModel ViewModel managing session list, search filtering, and playback state.
 * @param audioPlayerState Observable state of the audio player (position, duration, speed, playing).
 * @param currentPlaybackSessionId The ID of the session currently being played, or null.
 * @param largeTextMode Whether large text mode is enabled for accessibility.
 * @param onSessionClick Callback invoked when "View Transcript" is tapped, passing the session ID.
 * @param onShareClick Callback invoked when the share button is tapped for a session.
 * @param onPlayPauseClick Callback invoked when the play/pause button is tapped on the active session.
 * @param onSpeedChange Callback invoked when a new playback speed is selected (applies globally).
 * @param onPlaySession Callback invoked when play is tapped to start playback for a session.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    audioPlayerState: AudioPlayerState = AudioPlayerState(),
    currentPlaybackSessionId: String? = null,
    largeTextMode: Boolean = false,
    onSessionClick: (String) -> Unit = {},
    onShareClick: (RecordingSession) -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    onPlaySession: (RecordingSession) -> Unit = {},
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredSessions by viewModel.filteredSessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            largeTextMode = largeTextMode,
        ) { viewModel.search(it) }

        Spacer(modifier = Modifier.height(12.dp))

        // Session list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Session history list"
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredSessions, key = { it.id }) { session ->
                val isActivePlayback = currentPlaybackSessionId == session.id

                SessionCard(
                    session = session,
                    isActivePlayback = isActivePlayback,
                    audioPlayerState = audioPlayerState,
                    largeTextMode = largeTextMode,
                    onSessionClick = { onSessionClick(session.id) },
                    onShareClick = { onShareClick(session) },
                    onPlayPauseClick = {
                        if (isActivePlayback) {
                            // Toggle play/pause on the already-active session
                            onPlayPauseClick()
                        } else {
                            // Start playback for this session
                            onPlaySession(session)
                        }
                    },
                    onSpeedChange = onSpeedChange
                )
            }
        }
    }
}

/**
 * Search bar with real-time filtering and clear button.
 * Meets 48dp minimum touch target requirement.
 */
@Composable
private fun SearchBar(
    query: String,
    largeTextMode: Boolean = false,
    onQueryChange: (String) -> Unit
) {
    val textSize = if (largeTextMode) 24.sp else 16.sp

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .semantics {
                contentDescription = "Search sessions"
            },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = textSize),
        placeholder = {
            Text(
                text = "Search sessions...",
                fontSize = textSize
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Clear search"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * A single session card displaying title, date, time, duration, status label,
 * and integrated playback controls.
 *
 * The Play button toggles to Pause when this session is actively playing.
 * Progress bar and speed selector are shown directly in the card (no inner card).
 * Speed changes apply globally across all cards.
 *
 * @param session The recording session to display.
 * @param isActivePlayback Whether this session is the one currently playing.
 * @param audioPlayerState The current global audio player state (used for speed display on all cards).
 * @param largeTextMode Whether large text mode is enabled for accessibility.
 * @param onSessionClick Callback for "View Transcript" action.
 * @param onShareClick Callback for the share button.
 * @param onPlayPauseClick Callback to play this session or toggle pause on the active session.
 * @param onSpeedChange Callback to change playback speed (applies globally).
 */
@Composable
private fun SessionCard(
    session: RecordingSession,
    isActivePlayback: Boolean,
    audioPlayerState: AudioPlayerState,
    largeTextMode: Boolean = false,
    onSessionClick: () -> Unit,
    onShareClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    val hasTranscript = session.segments.isNotEmpty()
    val hasAudio = session.audioFilePath != null
    val isCurrentlyPlaying = isActivePlayback && audioPlayerState.isPlaying

    val titleSize = if (largeTextMode) 24.sp else 14.sp
    val bodySize = if (largeTextMode) 20.sp else 12.sp
    val buttonTextSize = if (largeTextMode) 20.sp else 14.sp
    val badgeSize = if (largeTextMode) 16.sp else 11.sp
    val controlTextSize = if (largeTextMode) 20.sp else 12.sp
    val speedTextSize = if (largeTextMode) 20.sp else 14.sp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .semantics {
                contentDescription = "Session: ${session.title}, recorded on ${session.date} at ${session.time}, duration ${session.duration}, status ${session.statusLabel}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row: session info + share button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Title row with status badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.title,
                            fontSize = titleSize,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        StatusBadge(label = session.statusLabel, fontSize = badgeSize)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Date, time, and duration
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = session.date,
                            fontSize = bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = session.time,
                            fontSize = bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = session.duration,
                            fontSize = bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Share button
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Share session: ${session.title}"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Progress bar (visible only when this session is actively playing)
            if (isActivePlayback) {
                Spacer(modifier = Modifier.height(8.dp))

                val progress = if (audioPlayerState.totalDuration > 0) {
                    audioPlayerState.currentPosition.toFloat() / audioPlayerState.totalDuration.toFloat()
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .semantics {
                            contentDescription = "Audio playback progress bar"
                        },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Progress time text
                Text(
                    text = "${formatDuration(audioPlayerState.currentPosition)} / ${formatDuration(audioPlayerState.totalDuration)}",
                    fontSize = controlTextSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Playback progress: ${formatDuration(audioPlayerState.currentPosition)} of ${formatDuration(audioPlayerState.totalDuration)}"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // View Transcript button (if applicable)
            if (hasTranscript) {
                TextButton(
                    onClick = onSessionClick,
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "View transcript for ${session.title}"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "View Transcript", fontSize = buttonTextSize)
                }
            }

            // Audio controls row: Play/Pause on left, Speed selector on right
            if (hasAudio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Play/Pause toggle button (left side)
                    TextButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .semantics {
                                contentDescription = if (isCurrentlyPlaying) {
                                    "Pause audio for ${session.title}"
                                } else {
                                    "Play audio for ${session.title}"
                                }
                            }
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCurrentlyPlaying) "Pause" else "Play",
                            fontSize = buttonTextSize
                        )
                    }

                    // Speed selector (right side, applies globally)
                    SpeedSelector(
                        currentSpeed = audioPlayerState.currentSpeed,
                        fontSize = speedTextSize,
                        onSpeedChange = onSpeedChange
                    )
                }
            }
        }
    }
}

/**
 * Playback speed selector dropdown. Changing speed on any card applies globally.
 */
@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    onSpeedChange: (Float) -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)

    Box {
        TextButton(
            onClick = { showSpeedMenu = true },
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                    contentDescription = "Playback speed: ${formatSpeed(currentSpeed)}. Tap to change."
                }
        ) {
            Text(
                text = formatSpeed(currentSpeed),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = showSpeedMenu,
            onDismissRequest = { showSpeedMenu = false }
        ) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSpeed(speed),
                            fontSize = fontSize,
                            fontWeight = if (speed == currentSpeed) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    onClick = {
                        onSpeedChange(speed)
                        showSpeedMenu = false
                    },
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Set playback speed to ${formatSpeed(speed)}"
                        }
                )
            }
        }
    }
}

/**
 * A styled status badge/chip displaying the session's relative time label
 * (e.g., "TODAY", "YESTERDAY", "THIS WEEK").
 */
@Composable
private fun StatusBadge(label: String, fontSize: androidx.compose.ui.unit.TextUnit = 11.sp) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Formats a duration in milliseconds to a "MM:SS" display string.
 */
private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Formats a playback speed float to a display string (e.g., "1x", "1.25x").
 */
private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}
