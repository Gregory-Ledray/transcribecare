import Foundation
import AVFoundation

/// Wrapper around AVAudioPlayer that provides audio playback with variable
/// speed support, progress tracking, and end-of-playback handling.
///
/// Supports playback speeds of 1x, 1.25x, 1.5x, and 2x via the AVAudioPlayer
/// `rate` property. Exposes observable state for current position, total duration,
/// playing status, and current speed.
///
/// - Requirements: 5.3, 5.4, 5.5
@Observable
class AudioPlayerService: NSObject, AVAudioPlayerDelegate {

    // MARK: - Public State

    /// Current playback position in seconds.
    private(set) var currentPosition: TimeInterval = 0

    /// Total duration of the loaded audio file in seconds.
    private(set) var totalDuration: TimeInterval = 0

    /// Whether audio is currently playing.
    private(set) var isPlaying: Bool = false

    /// The current playback speed (1.0, 1.25, 1.5, or 2.0).
    private(set) var currentSpeed: Float = 1.0

    /// User-facing error message, or nil if no error.
    private(set) var error: String?

    // MARK: - Callbacks

    /// Called with a user-facing error message when playback encounters a problem.
    var onError: ((String) -> Void)?

    // MARK: - Constants

    /// Supported playback speed values.
    static let supportedSpeeds: [Float] = [1.0, 1.25, 1.5, 2.0]

    /// Interval between progress updates in seconds.
    private static let progressUpdateInterval: TimeInterval = 0.25

    // MARK: - Private Properties

    private var audioPlayer: AVAudioPlayer?
    private var progressTimer: Timer?

    // MARK: - Public Methods

    /// Starts playback of the audio file at the given URL.
    ///
    /// If audio is already playing, it will be stopped before starting the new file.
    /// Resets speed to 1x on new file load.
    ///
    /// - Parameter url: The file URL of the audio to play.
    func play(url: URL) {
        // Validate file exists
        guard FileManager.default.fileExists(atPath: url.path) else {
            let message = "Audio file not found."
            error = message
            onError?(message)
            return
        }

        // Release any existing player
        stopProgressUpdates()
        audioPlayer?.stop()
        audioPlayer = nil

        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.delegate = self
            player.enableRate = true
            player.rate = 1.0
            player.prepareToPlay()

            audioPlayer = player
            totalDuration = player.duration
            currentPosition = 0
            currentSpeed = 1.0
            error = nil

            guard player.play() else {
                let message = "Failed to start audio playback."
                error = message
                onError?(message)
                return
            }

            isPlaying = true
            startProgressUpdates()
        } catch let nsError as NSError {
            handlePlayerError(nsError)
        }
    }

    /// Pauses the current playback. Has no effect if not currently playing.
    func pause() {
        guard let player = audioPlayer, player.isPlaying else { return }

        player.pause()
        isPlaying = false
        currentPosition = player.currentTime
        stopProgressUpdates()
    }

    /// Resumes playback from the current position. Has no effect if already playing.
    func resume() {
        guard let player = audioPlayer, !player.isPlaying else { return }

        player.rate = currentSpeed
        guard player.play() else {
            let message = "Failed to resume playback."
            error = message
            onError?(message)
            return
        }

        isPlaying = true
        error = nil
        startProgressUpdates()
    }

    /// Stops playback and resets position to the beginning.
    func stop() {
        guard let player = audioPlayer else { return }

        player.stop()
        player.currentTime = 0
        isPlaying = false
        currentPosition = 0
        stopProgressUpdates()
    }

    /// Sets the playback speed. Only values in `supportedSpeeds` are accepted.
    ///
    /// The speed change takes effect immediately if audio is currently playing.
    ///
    /// - Parameter speed: The desired playback speed (1.0, 1.25, 1.5, or 2.0).
    func setSpeed(speed: Float) {
        guard Self.supportedSpeeds.contains(speed) else { return }

        currentSpeed = speed

        if let player = audioPlayer, player.isPlaying {
            player.rate = speed
        }
    }

    /// Releases all resources held by the audio player.
    /// Should be called when the service is no longer needed.
    func release() {
        stopProgressUpdates()
        audioPlayer?.stop()
        audioPlayer?.delegate = nil
        audioPlayer = nil
        isPlaying = false
        currentPosition = 0
        totalDuration = 0
        currentSpeed = 1.0
        error = nil
    }

    /// Clears the current error state.
    func clearError() {
        error = nil
    }

    // MARK: - AVAudioPlayerDelegate

    /// Called when audio playback finishes. Resets to beginning and stops.
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        player.currentTime = 0
        isPlaying = false
        currentPosition = 0
        stopProgressUpdates()
    }

    /// Called when a decode error occurs during playback.
    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error decodeError: (any Error)?) {
        let message = "Audio format not supported."
        self.error = message
        onError?(message)
        isPlaying = false
        currentPosition = 0
        stopProgressUpdates()
    }

    // MARK: - Private Methods

    /// Starts a repeating timer that updates the current playback position.
    private func startProgressUpdates() {
        stopProgressUpdates()
        progressTimer = Timer.scheduledTimer(
            withTimeInterval: Self.progressUpdateInterval,
            repeats: true
        ) { [weak self] _ in
            self?.updateProgress()
        }
    }

    /// Stops the progress update timer.
    private func stopProgressUpdates() {
        progressTimer?.invalidate()
        progressTimer = nil
    }

    /// Updates the current position from the audio player.
    private func updateProgress() {
        guard let player = audioPlayer, player.isPlaying else { return }
        currentPosition = player.currentTime
    }

    /// Handles errors from AVAudioPlayer initialization.
    private func handlePlayerError(_ nsError: NSError) {
        let message: String

        switch nsError.domain {
        case NSOSStatusErrorDomain:
            message = "Audio format not supported."
        case NSCocoaErrorDomain where nsError.code == NSFileReadNoSuchFileError:
            message = "Audio file not found."
        case NSCocoaErrorDomain where nsError.code == NSFileReadCorruptFileError:
            message = "Audio file is corrupted or in an unsupported format."
        default:
            message = "Unable to open audio file: \(nsError.localizedDescription)"
        }

        error = message
        onError?(message)
        isPlaying = false
        currentPosition = 0
        totalDuration = 0
    }
}
