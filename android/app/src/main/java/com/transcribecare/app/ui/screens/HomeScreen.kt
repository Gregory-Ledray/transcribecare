package com.transcribecare.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import com.transcribecare.app.model.SegmentType
import com.transcribecare.app.model.TranscriptSegment
import com.transcribecare.app.viewmodel.HomeViewModel
import com.transcribecare.app.viewmodel.SettingsViewModel


/**
 * Home screen composable displaying recording controls, live transcript,
 * and recording status banner.
 *
 * @param homeViewModel ViewModel managing recording state and transcript segments.
 * @param settingsViewModel ViewModel providing user preferences like large text mode.
 */
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val isRecording by homeViewModel.isRecording.collectAsState()
    val segments by homeViewModel.segments.collectAsState()
    val interimText by homeViewModel.interimText.collectAsState()
    val largeTextMode by settingsViewModel.largeTextMode.collectAsState()

    val context = LocalContext.current

    // Permission state management
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showDeniedDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            homeViewModel.startRecording()
        } else {
            showDeniedDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Recording Status Banner
        RecordingStatusBanner(isVisible = isRecording)

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recording control button
            RecordingButton(
                isRecording = isRecording,
            ) {
                if (isRecording) {
                    homeViewModel.stopRecording()
                } else {
                        // Permission flow: check -> rationale -> request -> handle denial
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                homeViewModel.startRecording()
                            } else if (androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity,
                                    Manifest.permission.RECORD_AUDIO
                                )
                            ) {
                                showRationaleDialog = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }

            Spacer(modifier = Modifier.height(24.dp))

            // Live transcript display
            TranscriptDisplay(
                segments = segments,
                interimText = interimText,
                largeTextMode = largeTextMode,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Rationale dialog
    if (showRationaleDialog) {
        PermissionRationaleDialog(
            onConfirm = {
                showRationaleDialog = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { showRationaleDialog = false }
        )
    }

    // Permission denied dialog with Settings guidance
    if (showDeniedDialog) {
        PermissionDeniedDialog(
            onOpenSettings = {
                showDeniedDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onDismiss = { showDeniedDialog = false }
        )
    }
}


/**
 * Animated recording status banner displayed at the top of the screen
 * when recording is active.
 */
@Composable
fun RecordingStatusBanner(isVisible: Boolean) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics {
                    contentDescription = "Recording is active"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Animated recording dot
            val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "recording_dot_alpha"
            )

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )

            Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = "RECORDING ACTIVE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Start/Stop recording button with 48dp minimum touch target
 * and content descriptions for TalkBack support.
 */
@Composable
fun RecordingButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val buttonText = if (isRecording) "Stop Recording" else "Start Recording"
    val buttonDescription = if (isRecording) {
        "Stop recording. Double tap to stop the current recording session."
    } else {
        "Start recording. Double tap to begin a new recording session."
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .widthIn(min = 200.dp)
            .semantics {
                contentDescription = buttonDescription
            },
        colors = if (isRecording) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) {
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * Displays the live transcript with interim text and finalized segments.
 * Segments are color-coded by type (PAST, RECENT, CURRENT).
 * Supports Large Text Mode (36sp minimum when enabled).
 */
@Composable
fun TranscriptDisplay(
    segments: List<TranscriptSegment>,
    interimText: String,
    largeTextMode: Boolean,
    modifier: Modifier = Modifier
) {
    val baseFontSize = if (largeTextMode) 36.sp else 16.sp

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Live transcript display"
            }
    ) {
        // Finalized segments
        items(segments, key = { it.id }) { segment ->
            TranscriptSegmentItem(
                segment = segment,
                fontSize = baseFontSize,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Interim text (partial recognition result)
        if (interimText.isNotEmpty()) {
            item(key = "interim") {
                Text(
                    text = interimText,
                    fontSize = baseFontSize,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics {
                            contentDescription = "Interim transcript: $interimText"
                        }
                )
            }
        }
    }
}

/**
 * A single transcript segment rendered with color-coding based on its type.
 *
 * - CURRENT: Primary color (most recent finalized segment)
 * - RECENT: Secondary color (previously current)
 * - PAST: Muted on-background color (older segments)
 */
@Composable
fun TranscriptSegmentItem(
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

/**
 * Dialog explaining why microphone permission is needed.
 * Shown when the system indicates a rationale should be displayed.
 */
@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Microphone Permission Required")
        },
        text = {
            Text(
                text = "TranscribeCare needs access to your microphone to record and " +
                    "transcribe medical conversations in real time. Your audio is processed " +
                    "on-device and never sent to external servers without your consent."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Not Now")
            }
        }
    )
}

/**
 * Dialog shown when the user has denied microphone permission.
 * Provides guidance to enable it in system Settings.
 */
@Composable
fun PermissionDeniedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Permission Denied")
        },
        text = {
            Text(
                text = "Recording requires microphone access. You can enable it in your " +
                    "device settings. Go to Settings > Apps > TranscribeCare > Permissions " +
                    "and enable the Microphone permission."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
