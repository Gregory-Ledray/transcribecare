import Foundation

/// ViewModel for the Home screen managing recording state and transcript segments.
/// Wires together SpeechRecognitionService, AudioRecorderService, and SessionStore
/// to provide the end-to-end recording flow.
///
/// - Requirements: 4.1, 4.3, 4.5, 5.1, 5.2, 6.1
@Observable
class HomeViewModel {

    /// Whether the app is currently recording audio and transcribing speech.
    var isRecording: Bool = false

    /// The list of finalized transcript segments for the current session.
    var segments: [TranscriptSegment] = []

    /// Interim (partial) text from the speech recognizer, displayed while recognition is in progress.
    var interimText: String = ""

    /// Error message to display to the user, or nil if no error.
    var error: String?

    // MARK: - Services

    private var speechRecognitionService: SpeechRecognitionService?
    private var audioRecorderService: AudioRecorderService?
    private let sessionStore: SessionStore

    // MARK: - Recording State

    /// Tracks when the current recording session started.
    private var recordingStartTime: Date?

    /// The audio file URL for the current recording session.
    private var currentAudioURL: URL?

    // MARK: - Initialization

    /// Creates a HomeViewModel with the given SessionStore.
    ///
    /// - Parameter sessionStore: The persistence store for saving recording sessions.
    init(sessionStore: SessionStore = SessionStore()) {
        self.sessionStore = sessionStore
    }

    // MARK: - Public Methods

    /// Starts recording: creates and configures services, begins speech recognition
    /// and audio capture, and tracks the recording start time.
    func startRecording() {
        // Reset any previous error
        error = nil

        // Create and configure SpeechRecognitionService with callbacks
        let speechService = SpeechRecognitionService()
        speechService.onFinalResult = { [weak self] text in
            self?.onFinalResult(text: text)
        }
        speechService.onPartialResult = { [weak self] text in
            self?.onInterimResult(text: text)
        }
        speechService.onError = { [weak self] errorMessage in
            self?.handleSpeechError(errorMessage)
        }
        self.speechRecognitionService = speechService

        // Create and configure AudioRecorderService with error callback
        let audioService = AudioRecorderService()
        audioService.onError = { [weak self] errorMessage in
            self?.handleAudioError(errorMessage)
        }
        self.audioRecorderService = audioService

        // Start speech recognition
        speechService.startListening()

        // Start audio recording
        currentAudioURL = audioService.startRecording()

        // Track recording start time
        recordingStartTime = Date()

        // Set recording state
        isRecording = true
    }

    /// Stops recording: stops speech recognition and audio capture, creates a
    /// RecordingSession from the current segments and audio file, saves it to
    /// SessionStore, and resets state for the next session.
    func stopRecording() {
        // Stop speech recognition
        speechRecognitionService?.stopListening()

        // Stop audio recording
        let audioURL = audioRecorderService?.stopRecording()
        let finalAudioURL = audioURL ?? currentAudioURL

        // Create and save the session if we have segments
        if !segments.isEmpty {
            let session = createSession(audioFileURL: finalAudioURL)
            sessionStore.insertSession(session: session)
        }

        // Reset state for next session
        segments = []
        interimText = ""
        isRecording = false
        recordingStartTime = nil
        currentAudioURL = nil

        // Release services
        speechRecognitionService?.destroy()
        speechRecognitionService = nil
        audioRecorderService?.release()
        audioRecorderService = nil
    }

    /// Called when a finalized transcript result arrives from the speech recognizer.
    /// Reclassifies existing segments and appends the new text as the current segment.
    func onFinalResult(text: String) {
        segments = Self.reclassifyAndAppend(segments: segments, newText: text)
        interimText = ""
    }

    /// Called when interim (partial) text arrives from the speech recognizer.
    func onInterimResult(text: String) {
        interimText = text
    }

    // MARK: - Static Reclassification (Pure, Testable)

    /// Pure function that reclassifies transcript segments and appends a new current segment.
    ///
    /// Order of operations:
    /// 1. Reclassify all "recent" segments → "past"
    /// 2. Reclassify all "current" segments → "recent"
    /// 3. Append a new segment with type "current" and the given text
    ///
    /// This ordering prevents cascading (current→recent→past in one step).
    ///
    /// - Parameters:
    ///   - segments: The existing list of transcript segments.
    ///   - newText: The finalized text for the new current segment.
    /// - Returns: A new list with reclassified segments and the new current segment appended.
    static func reclassifyAndAppend(
        segments: [TranscriptSegment],
        newText: String
    ) -> [TranscriptSegment] {
        let reclassified = segments.map { segment -> TranscriptSegment in
            switch segment.type {
            case .recent:
                return TranscriptSegment(
                    id: segment.id,
                    text: segment.text,
                    type: .past,
                    timestamp: segment.timestamp
                )
            case .current:
                return TranscriptSegment(
                    id: segment.id,
                    text: segment.text,
                    type: .recent,
                    timestamp: segment.timestamp
                )
            case .past:
                return segment
            }
        }

        let newSegment = TranscriptSegment(
            text: newText,
            type: .current
        )

        return reclassified + [newSegment]
    }

    // MARK: - Private Helpers

    /// Creates a RecordingSession from the current recording state.
    ///
    /// Generates session metadata including:
    /// - Title derived from the first segment's text (truncated to 50 characters)
    /// - Formatted date and time strings
    /// - Duration calculated from recording start time
    /// - Status label based on the current date
    private func createSession(audioFileURL: URL?) -> RecordingSession {
        let metadata = generateSessionMetadata()

        return RecordingSession(
            title: metadata.title,
            date: metadata.date,
            time: metadata.time,
            createdAt: Date(),
            duration: metadata.duration,
            audioFilePath: audioFileURL?.path,
            segments: segments,
            statusLabel: metadata.statusLabel
        )
    }

    /// Generates session metadata from the current recording state.
    ///
    /// - Returns: A tuple containing title, date, time, duration, and status label.
    func generateSessionMetadata() -> (title: String, date: String, time: String, duration: String, statusLabel: String) {
        // Title: first segment text, truncated to 50 characters
        let title: String
        if let firstSegment = segments.first {
            let text = firstSegment.text
            title = text.count > 50 ? String(text.prefix(50)) + "..." : text
        } else {
            title = "Recording Session"
        }

        // Date and time formatting
        let now = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "MMM dd, yyyy"
        let date = dateFormatter.string(from: now)

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "hh:mm a"
        let time = timeFormatter.string(from: now)

        // Duration calculation
        let duration: String
        if let startTime = recordingStartTime {
            let elapsed = now.timeIntervalSince(startTime)
            let minutes = Int(elapsed) / 60
            let seconds = Int(elapsed) % 60
            duration = String(format: "%02d:%02d", minutes, seconds)
        } else {
            duration = "00:00"
        }

        // Status label
        let statusLabel = "TODAY"

        return (title: title, date: date, time: time, duration: duration, statusLabel: statusLabel)
    }

    /// Handles speech recognition errors by setting the error property
    /// and stopping the recording if needed.
    private func handleSpeechError(_ message: String) {
        error = message
        // If we get a critical error, stop recording
        if isRecording && !(speechRecognitionService?.isListening ?? false) {
            stopRecording()
        }
    }

    /// Handles audio recording errors by setting the error property.
    private func handleAudioError(_ message: String) {
        error = message
    }
}
