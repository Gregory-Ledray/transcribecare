import Foundation

/// Represents a complete recording session containing audio data, transcript segments, and metadata.
struct RecordingSession: Identifiable, Codable, Equatable {
    /// Unique identifier for the session.
    let id: String

    /// Display title for the session.
    var title: String

    /// Formatted display date (e.g., "Jun 15, 2025").
    var date: String

    /// Formatted display time (e.g., "02:30 PM").
    var time: String

    /// Date when the session was created, used for sorting.
    var createdAt: Date

    /// Formatted duration string (e.g., "05:32").
    var duration: String

    /// Local file path to the recorded audio, or nil if no audio was saved.
    var audioFilePath: String?

    /// List of transcript segments captured during the session.
    var segments: [TranscriptSegment]

    /// Relative time label for display (e.g., "TODAY", "YESTERDAY", "THIS WEEK").
    var statusLabel: String

    /// Creates a new recording session with a generated UUID.
    ///
    /// - Parameters:
    ///   - id: Unique identifier. Defaults to a new UUID string.
    ///   - title: Display title for the session.
    ///   - date: Formatted display date.
    ///   - time: Formatted display time.
    ///   - createdAt: Creation date used for sorting. Defaults to the current date.
    ///   - duration: Formatted duration string.
    ///   - audioFilePath: Optional local file path to the recorded audio.
    ///   - segments: List of transcript segments. Defaults to an empty array.
    ///   - statusLabel: Relative time label for display.
    init(
        id: String = UUID().uuidString,
        title: String,
        date: String,
        time: String,
        createdAt: Date = Date(),
        duration: String,
        audioFilePath: String? = nil,
        segments: [TranscriptSegment] = [],
        statusLabel: String
    ) {
        self.id = id
        self.title = title
        self.date = date
        self.time = time
        self.createdAt = createdAt
        self.duration = duration
        self.audioFilePath = audioFilePath
        self.segments = segments
        self.statusLabel = statusLabel
    }
}
