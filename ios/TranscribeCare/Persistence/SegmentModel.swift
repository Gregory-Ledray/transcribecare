import Foundation
import SwiftData

/// SwiftData model representing a persisted transcript segment.
@Model
class SegmentModel {
    @Attribute(.unique) var id: String
    var text: String
    var type: String // "past", "recent", "current"
    var timestamp: Date
    var orderIndex: Int

    /// Creates a new SegmentModel instance.
    ///
    /// - Parameters:
    ///   - id: Unique identifier for the segment.
    ///   - text: The transcribed text content.
    ///   - type: Classification string ("past", "recent", or "current").
    ///   - timestamp: When the segment was finalized.
    ///   - orderIndex: Position index for ordering segments within a session.
    init(
        id: String,
        text: String,
        type: String,
        timestamp: Date,
        orderIndex: Int
    ) {
        self.id = id
        self.text = text
        self.type = type
        self.timestamp = timestamp
        self.orderIndex = orderIndex
    }
}
