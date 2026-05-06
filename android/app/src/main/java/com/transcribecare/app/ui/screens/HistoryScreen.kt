package com.transcribecare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.transcribecare.app.model.RecordingSession
import com.transcribecare.app.service.AudioPlayerState
import com.transcribecare.app.viewmodel.HistoryViewModel

/**
 * History screen composable displaying a searchable, scrollable list of recording sessions
 * with audio playback controls and sharing capabilities.
 *
 * @param viewModel ViewModel managing session list, search filtering, and playback state.
 * @param audioPlayerState Observable state of the audio player (position, duration, speed, playing).
 * @param onSessionClick Callback invoked when a session card is tapped, passing the session ID.
 * @param onShareClick Callback invoked when the share button is tapped for a session.
 * @param onPlayPauseClick Callback invoked when the play/pause button is tapped.
 * @param onSpeedChange Callback invoked when a new playback speed is selected.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    audioPlayerState: AudioPlayerState = AudioPlayerState(),
    onSessionClick: (String) -> Unit = {},
    onShareClick: (RecordingSession) -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
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
        ) { viewModel.search(it) }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio playback controls
        AudioPlaybackControls(
            audioPlayerState = audioPlayerState,
            onPlayPauseClick = onPlayPauseClick,
            onSpeedChange = onSpeedChange
        )

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
                SessionCard(
                    session = session,
                    onSessionClick = { onSessionClick(session.id) },
                    onShareClick = { onShareClick(session) }
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
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .semantics {
                contentDescription = "Search sessions"
            },
        placeholder = {
            Text(text = "Search sessions...")
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
 * Audio playback controls section with play/pause button, speed selector,
 * and progress indicator.
 */
@Composable
private fun AudioPlaybackControls(
    audioPlayerState: AudioPlayerState,
    onPlayPauseClick: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play/Pause button
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = if (audioPlayerState.isPlaying) {
                                "Pause audio playback"
                            } else {
                                "Play audio playback"
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (audioPlayerState.isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Progress text
                Text(
                    text = "${formatDuration(audioPlayerState.currentPosition)} / ${formatDuration(audioPlayerState.totalDuration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Playback progress: ${formatDuration(audioPlayerState.currentPosition)} of ${formatDuration(audioPlayerState.totalDuration)}"
                    }
                )

                // Speed selector
                Box {
                    TextButton(
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = "Playback speed: ${formatSpeed(audioPlayerState.currentSpeed)}. Tap to change."
                            }
                    ) {
                        Text(
                            text = formatSpeed(audioPlayerState.currentSpeed),
                            style = MaterialTheme.typography.labelLarge,
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
                                        fontWeight = if (speed == audioPlayerState.currentSpeed) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Progress indicator
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
        }
    }
}

/**
 * A single session card displaying title, date, time, duration, status label,
 * and a share button. Tapping the card navigates to session detail.
 */
@Composable
private fun SessionCard(
    session: RecordingSession,
    onSessionClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(
                onClick = onSessionClick,
                onClickLabel = "View session details for ${session.title}"
            )
            .semantics {
                contentDescription = "Session: ${session.title}, recorded on ${session.date} at ${session.time}, duration ${session.duration}, status ${session.statusLabel}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Session info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title row with status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status label badge
                    StatusBadge(label = session.statusLabel)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Date, time, and duration
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = session.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session.duration,
                        style = MaterialTheme.typography.bodySmall,
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
    }
}

/**
 * A styled status badge/chip displaying the session's relative time label
 * (e.g., "TODAY", "YESTERDAY", "THIS WEEK").
 */
@Composable
private fun StatusBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
