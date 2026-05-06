import Foundation
import SwiftData

/// SwiftData model representing a persisted recording session.
@Model
class SessionModel {
    @Attribute(.unique) var id: String
    var title: String
    var date: String
    var time: String
    var createdAt: Date
    var duration: String
    var audioFilePath: String?
    var statusLabel: String
    @Relationship(deleteRule: .cascade) var segments: [SegmentModel]

    /// Creates a new SessionModel instance.
    ///
    /// - Parameters:
    ///   - id: Unique identifier for the session.
    ///   - title: Display title for the session.
    ///   - date: Formatted display date.
    ///   - time: Formatted display time.
    ///   - createdAt: Creation date used for sorting.
    ///   - duration: Formatted duration string.
    ///   - audioFilePath: Optional local file path to the recorded audio.
    ///   - statusLabel: Relative time label for display.
    ///   - segments: Array of associated segment models.
    init(
        id: String,
        title: String,
        date: String,
        time: String,
        createdAt: Date,
        duration: String,
        audioFilePath: String? = nil,
        statusLabel: String,
        segments: [SegmentModel] = []
    ) {
        self.id = id
        self.title = title
        self.date = date
        self.time = time
        self.createdAt = createdAt
        self.duration = duration
        self.audioFilePath = audioFilePath
        self.statusLabel = statusLabel
        self.segments = segments
    }
}
