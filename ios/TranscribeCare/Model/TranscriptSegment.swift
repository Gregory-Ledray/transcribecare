import Foundation

/// A discrete unit of transcribed text with a classification indicating its recency.
struct TranscriptSegment: Identifiable, Codable, Equatable {
    /// Unique identifier for the segment.
    let id: String

    /// The transcribed text content.
    var text: String

    /// Classification of the segment (past, recent, or current).
    var type: SegmentType

    /// Timestamp when the segment was finalized.
    var timestamp: Date

    /// Creates a new transcript segment with a generated UUID.
    ///
    /// - Parameters:
    ///   - id: Unique identifier. Defaults to a new UUID string.
    ///   - text: The transcribed text content.
    ///   - type: Classification of the segment.
    ///   - timestamp: When the segment was finalized. Defaults to the current date.
    init(
        id: String = UUID().uuidString,
        text: String,
        type: SegmentType,
        timestamp: Date = Date()
    ) {
        self.id = id
        self.text = text
        self.type = type
        self.timestamp = timestamp
    }
}
