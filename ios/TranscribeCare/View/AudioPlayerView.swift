import SwiftUI

/// Audio playback controls view with play/pause, speed selector,
/// progress indicator, and current position / total duration display.
///
/// All interactive elements have 44pt minimum touch targets and
/// accessibility labels for VoiceOver support.
///
/// - Requirements: 5.3, 5.4, 5.5, 8.6, 8.8
struct AudioPlayerView: View {

    /// The audio player service managing playback state.
    @Bindable var playerService: AudioPlayerService

    var body: some View {
        VStack(spacing: 12) {
            // Progress indicator
            progressBar

            // Time labels
            HStack {
                Text(formatTime(playerService.currentPosition))
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Current position \(formatTimeAccessible(playerService.currentPosition))")

                Spacer()

                Text(formatTime(playerService.totalDuration))
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Total duration \(formatTimeAccessible(playerService.totalDuration))")
            }

            // Controls row
            HStack(spacing: 24) {
                playPauseButton
                speedSelector
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Subviews

    /// Progress bar showing playback position relative to total duration.
    private var progressBar: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                // Background track
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color(.systemGray4))
                    .frame(height: 4)

                // Filled progress
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color("Primary"))
                    .frame(width: progressWidth(totalWidth: geometry.size.width), height: 4)
            }
        }
        .frame(height: 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Playback progress")
        .accessibilityValue("\(Int(progressPercentage))%")
    }

    /// Play/pause toggle button with 44pt minimum touch target.
    private var playPauseButton: some View {
        Button(action: togglePlayback) {
            Image(systemName: playerService.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                .font(.system(size: 36))
                .foregroundStyle(Color("Primary"))
        }
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel(playerService.isPlaying ? "Pause" : "Play")
        .accessibilityHint(
            playerService.isPlaying
                ? "Double tap to pause audio playback"
                : "Double tap to resume audio playback"
        )
    }

    /// Speed selector using a Menu for selecting playback speed.
    private var speedSelector: some View {
        Menu {
            ForEach(AudioPlayerService.supportedSpeeds, id: \.self) { speed in
                Button(action: { playerService.setSpeed(speed: speed) }) {
                    HStack {
                        Text(formatSpeed(speed))
                        if speed == playerService.currentSpeed {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            Text(formatSpeed(playerService.currentSpeed))
                .font(.subheadline)
                .fontWeight(.medium)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(.systemGray5))
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel("Playback speed")
        .accessibilityValue(formatSpeed(playerService.currentSpeed))
        .accessibilityHint("Double tap to change playback speed")
    }

    // MARK: - Helpers

    /// Toggles between play and pause states.
    private func togglePlayback() {
        if playerService.isPlaying {
            playerService.pause()
        } else {
            playerService.resume()
        }
    }

    /// Calculates the progress bar fill width.
    private func progressWidth(totalWidth: CGFloat) -> CGFloat {
        guard playerService.totalDuration > 0 else { return 0 }
        let ratio = playerService.currentPosition / playerService.totalDuration
        return totalWidth * CGFloat(min(max(ratio, 0), 1))
    }

    /// Progress as a percentage (0–100).
    private var progressPercentage: Double {
        guard playerService.totalDuration > 0 else { return 0 }
        return (playerService.currentPosition / playerService.totalDuration) * 100
    }

    /// Formats a time interval as "MM:SS".
    private func formatTime(_ time: TimeInterval) -> String {
        let totalSeconds = Int(max(time, 0))
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    /// Formats a time interval for accessibility (e.g., "2 minutes 30 seconds").
    private func formatTimeAccessible(_ time: TimeInterval) -> String {
        let totalSeconds = Int(max(time, 0))
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        if minutes > 0 {
            return "\(minutes) minutes \(seconds) seconds"
        }
        return "\(seconds) seconds"
    }

    /// Formats a speed value for display (e.g., "1.5x").
    private func formatSpeed(_ speed: Float) -> String {
        if speed == 1.0 {
            return "1x"
        } else if speed == 1.25 {
            return "1.25x"
        } else if speed == 1.5 {
            return "1.5x"
        } else if speed == 2.0 {
            return "2x"
        }
        return "\(speed)x"
    }
}

#Preview {
    AudioPlayerView(playerService: AudioPlayerService())
        .padding()
}
