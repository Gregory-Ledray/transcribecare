import Foundation
import AVFoundation

/// Wrapper around AVAudioRecorder that provides M4A/AAC audio capture
/// with file path management and graceful error handling.
///
/// Recordings are stored in the app's documents directory using timestamped
/// filenames for uniqueness.
///
/// - Requirements: 5.1, 5.2, 5.6
@Observable
class AudioRecorderService {

    // MARK: - Callbacks

    /// Called with a user-facing error message when recording encounters a problem.
    var onError: ((String) -> Void)?

    // MARK: - Public State

    /// Whether a recording is currently in progress.
    private(set) var isRecording: Bool = false

    /// The file URL of the current or most recent recording.
    private(set) var currentRecordingURL: URL?

    // MARK: - Private Properties

    private var audioRecorder: AVAudioRecorder?

    // MARK: - Constants

    private static let recordingsDirectory = "Recordings"
    private static let filePrefix = "recording_"
    private static let fileExtension = "m4a"
    private static let minimumStorageBytes: UInt64 = 10 * 1024 * 1024 // 10 MB

    // MARK: - Audio Settings

    /// AAC encoding settings for M4A audio capture.
    private var recordingSettings: [String: Any] {
        [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100.0,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
            AVEncoderBitRateKey: 128000
        ]
    }

    // MARK: - Public Methods

    /// Starts audio recording in M4A/AAC format.
    ///
    /// Configures the audio session for recording, creates a unique output file,
    /// and begins capture. Returns the file URL on success, or nil on failure.
    ///
    /// - Returns: The URL of the recording file, or nil if recording could not start.
    func startRecording() -> URL? {
        if isRecording {
            return currentRecordingURL
        }

        // Configure audio session
        guard configureAudioSession() else {
            return nil
        }

        // Create output file URL
        guard let outputURL = createOutputFileURL() else {
            return nil
        }

        // Check available storage
        guard hasAvailableStorage() else {
            onError?("Not enough storage space to record audio.")
            return nil
        }

        // Create and start the recorder
        do {
            let recorder = try AVAudioRecorder(url: outputURL, settings: recordingSettings)
            recorder.prepareToRecord()

            guard recorder.record() else {
                onError?("Failed to start recording. Please try again.")
                return nil
            }

            audioRecorder = recorder
            currentRecordingURL = outputURL
            isRecording = true

            return outputURL
        } catch let error as NSError {
            handleRecorderInitError(error, fileURL: outputURL)
            return nil
        }
    }

    /// Stops the current recording and finalizes the audio file.
    ///
    /// - Returns: The URL of the completed recording, or nil if no recording was active.
    func stopRecording() -> URL? {
        guard isRecording, let recorder = audioRecorder else {
            return nil
        }

        recorder.stop()
        isRecording = false

        let fileURL = currentRecordingURL

        // Verify the file was actually written
        if let url = fileURL {
            let fileManager = FileManager.default
            if !fileManager.fileExists(atPath: url.path) {
                onError?("Recording failed. No audio data was captured.")
                cleanup()
                return nil
            }
        }

        // Deactivate audio session
        deactivateAudioSession()

        audioRecorder = nil
        return fileURL
    }

    /// Releases all resources held by the audio recorder.
    /// Should be called when the service is no longer needed.
    func release() {
        if isRecording {
            audioRecorder?.stop()
        }
        cleanup()
        deactivateAudioSession()
    }

    // MARK: - Private Methods

    /// Configures the AVAudioSession for recording.
    /// - Returns: `true` if configuration succeeded, `false` otherwise.
    private func configureAudioSession() -> Bool {
        let session = AVAudioSession.sharedInstance()

        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            return true
        } catch let error as NSError {
            if error.code == AVAudioSession.ErrorCode.insufficientPriority.rawValue ||
               error.domain == NSOSStatusErrorDomain {
                onError?("Microphone is in use by another app.")
            } else {
                onError?("Failed to configure audio session: \(error.localizedDescription)")
            }
            return false
        }
    }

    /// Deactivates the audio session after recording completes.
    private func deactivateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            // Non-critical — session will be deactivated when app backgrounds
        }
    }

    /// Creates a unique output file URL in the app's recordings directory.
    /// - Returns: A URL for the new recording file, or nil if the directory could not be created.
    private func createOutputFileURL() -> URL? {
        let fileManager = FileManager.default

        guard let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            onError?("Unable to access storage for recording.")
            return nil
        }

        let recordingsDir = documentsDirectory.appendingPathComponent(Self.recordingsDirectory)

        // Create recordings directory if needed
        if !fileManager.fileExists(atPath: recordingsDir.path) {
            do {
                try fileManager.createDirectory(at: recordingsDir, withIntermediateDirectories: true)
            } catch {
                onError?("Unable to access storage for recording.")
                return nil
            }
        }

        // Generate unique filename using timestamp
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        let timestamp = formatter.string(from: Date())
        let fileName = "\(Self.filePrefix)\(timestamp).\(Self.fileExtension)"

        return recordingsDir.appendingPathComponent(fileName)
    }

    /// Checks whether sufficient storage is available for recording.
    /// - Returns: `true` if at least the minimum storage threshold is available.
    private func hasAvailableStorage() -> Bool {
        let fileManager = FileManager.default

        guard let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return false
        }

        do {
            let attributes = try fileManager.attributesOfFileSystem(forPath: documentsDirectory.path)
            if let freeSpace = attributes[.systemFreeSize] as? UInt64 {
                return freeSpace > Self.minimumStorageBytes
            }
            return false
        } catch {
            return false
        }
    }

    /// Handles errors from AVAudioRecorder initialization.
    private func handleRecorderInitError(_ error: NSError, fileURL: URL) {
        // Clean up the file that was created
        try? FileManager.default.removeItem(at: fileURL)

        if error.domain == NSOSStatusErrorDomain {
            // OS-level audio errors often indicate hardware issues
            onError?("Microphone is in use by another app.")
        } else {
            onError?("Failed to initialize audio recorder: \(error.localizedDescription)")
        }
    }

    /// Cleans up recorder resources and resets state.
    private func cleanup() {
        audioRecorder?.stop()
        audioRecorder = nil
        isRecording = false
    }
}
