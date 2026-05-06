import Foundation
import Speech
import AVFoundation

/// Wrapper around SFSpeechRecognizer that provides continuous on-device speech recognition
/// with interim and final results, auto-restart on unexpected session end, and graceful
/// error handling with on-device fallback.
///
/// Uses AVAudioEngine to tap the audio input node and feed buffers to the recognition request.
///
/// - Requirements: 4.1, 4.4, 4.5, 4.7, 4.8
@Observable
class SpeechRecognitionService {

    // MARK: - Callbacks

    /// Called with interim (non-final) recognition text while the user is speaking.
    var onPartialResult: ((String) -> Void)?

    /// Called with finalized recognition text when the recognizer commits a result.
    var onFinalResult: ((String) -> Void)?

    /// Called with a user-facing error message when recognition encounters a problem.
    var onError: ((String) -> Void)?

    // MARK: - Public State

    /// Whether the service is actively listening for speech.
    private(set) var isListening: Bool = false

    // MARK: - Private Properties

    private let speechRecognizer: SFSpeechRecognizer?
    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?

    /// Tracks whether the user intends to be recording. Used to determine
    /// if auto-restart should occur when a session ends unexpectedly.
    private var isRecordingIntent: Bool = false

    // MARK: - Initialization

    /// Creates a new SpeechRecognitionService with the default locale recognizer.
    init() {
        self.speechRecognizer = SFSpeechRecognizer(locale: Locale.current)
    }

    // MARK: - Public Methods

    /// Starts continuous speech recognition.
    ///
    /// Configures the audio session, sets up the audio engine input tap,
    /// creates a recognition request, and begins processing speech.
    func startListening() {
        // Check authorization status
        guard checkAuthorization() else { return }

        // Verify recognizer is available
        guard let speechRecognizer = speechRecognizer, speechRecognizer.isAvailable else {
            onError?("Speech recognition is not available on this device.")
            return
        }

        isRecordingIntent = true
        isListening = true

        do {
            try startRecognitionSession()
        } catch {
            isListening = false
            isRecordingIntent = false
            onError?("Failed to start speech recognition: \(error.localizedDescription)")
        }
    }

    /// Stops speech recognition and releases resources.
    /// Prevents auto-restart by clearing the recording intent flag.
    func stopListening() {
        isRecordingIntent = false
        isListening = false
        stopRecognitionSession()
    }

    /// Releases all resources. Call when the service is no longer needed.
    func destroy() {
        isRecordingIntent = false
        isListening = false
        stopRecognitionSession()
    }

    // MARK: - Private Methods

    /// Checks speech recognition authorization and reports errors for denied states.
    /// - Returns: `true` if authorized, `false` otherwise.
    private func checkAuthorization() -> Bool {
        let status = SFSpeechRecognizer.authorizationStatus()
        switch status {
        case .authorized:
            return true
        case .denied:
            onError?("Speech recognition permission denied. Please enable it in Settings > Privacy > Speech Recognition.")
            return false
        case .restricted:
            onError?("Speech recognition is restricted on this device.")
            return false
        case .notDetermined:
            // Request authorization and report result asynchronously
            requestAuthorization()
            return false
        @unknown default:
            onError?("Speech recognition authorization status unknown.")
            return false
        }
    }

    /// Requests speech recognition authorization from the user.
    private func requestAuthorization() {
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            DispatchQueue.main.async {
                switch status {
                case .authorized:
                    self?.startListening()
                case .denied:
                    self?.onError?("Speech recognition permission denied. Please enable it in Settings > Privacy > Speech Recognition.")
                case .restricted:
                    self?.onError?("Speech recognition is restricted on this device.")
                case .notDetermined:
                    self?.onError?("Speech recognition authorization not determined.")
                @unknown default:
                    self?.onError?("Speech recognition authorization status unknown.")
                }
            }
        }
    }

    /// Configures and starts a recognition session with audio engine input.
    private func startRecognitionSession() throws {
        // Cancel any existing task
        stopRecognitionSession()

        // Configure audio session for recording
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

        // Create recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else {
            throw SpeechRecognitionError.requestCreationFailed
        }

        // Enable partial results for interim text display
        recognitionRequest.shouldReportPartialResults = true

        // Set on-device recognition as fallback for network issues (Requirement 4.7)
        if #available(iOS 13, *) {
            recognitionRequest.requiresOnDeviceRecognition = true
        }

        // Install audio tap on the input node
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
            self?.recognitionRequest?.append(buffer)
        }

        // Start audio engine
        audioEngine.prepare()
        try audioEngine.start()

        // Start recognition task
        guard let speechRecognizer = speechRecognizer else {
            throw SpeechRecognitionError.recognizerUnavailable
        }

        recognitionTask = speechRecognizer.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            self?.handleRecognitionResult(result: result, error: error)
        }
    }

    /// Handles recognition results and errors from the recognition task.
    private func handleRecognitionResult(result: SFSpeechRecognitionResult?, error: Error?) {
        var isFinal = false

        if let result = result {
            let transcription = result.bestTranscription.formattedString
            isFinal = result.isFinal

            if isFinal {
                onFinalResult?(transcription)
            } else {
                onPartialResult?(transcription)
            }
        }

        if let error = error {
            handleRecognitionError(error)
            return
        }

        // If the result is final, the session has ended — auto-restart if intent is active
        if isFinal {
            restartIfActive()
        }
    }

    /// Handles recognition errors with appropriate recovery strategies.
    private func handleRecognitionError(_ error: Error) {
        let nsError = error as NSError

        // Check for specific error conditions
        if nsError.domain == "kAFAssistantErrorDomain" {
            switch nsError.code {
            case 1: // Recognition request was canceled
                restartIfActive()
                return
            case 4: // No speech detected / timeout
                restartIfActive()
                return
            default:
                break
            }
        }

        // Network-related errors: attempt on-device fallback
        if nsError.code == 2 || nsError.localizedDescription.lowercased().contains("network") {
            onError?("Network unavailable. Using on-device recognition.")
            restartIfActive()
            return
        }

        // For other errors, stop and report
        stopRecognitionSession()
        isListening = false

        if isRecordingIntent {
            // Unexpected end — attempt restart
            isRecordingIntent = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                self?.restartIfActive()
            }
        } else {
            onError?("Speech recognition error: \(error.localizedDescription)")
        }
    }

    /// Stops the current recognition session and cleans up resources.
    private func stopRecognitionSession() {
        // Stop audio engine and remove tap
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }

        // End recognition request
        recognitionRequest?.endAudio()
        recognitionRequest = nil

        // Cancel recognition task
        recognitionTask?.cancel()
        recognitionTask = nil
    }

    /// Restarts recognition if the user's recording intent is still active.
    /// This enables continuous transcription across recognition sessions (Requirement 4.8).
    private func restartIfActive() {
        guard isRecordingIntent else { return }

        stopRecognitionSession()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self, self.isRecordingIntent else { return }
            do {
                try self.startRecognitionSession()
            } catch {
                self.isListening = false
                self.isRecordingIntent = false
                self.onError?("Failed to restart speech recognition: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: - Error Types

/// Internal errors for the speech recognition service.
private enum SpeechRecognitionError: LocalizedError {
    case requestCreationFailed
    case recognizerUnavailable

    var errorDescription: String? {
        switch self {
        case .requestCreationFailed:
            return "Failed to create speech recognition request."
        case .recognizerUnavailable:
            return "Speech recognizer is not available."
        }
    }
}
