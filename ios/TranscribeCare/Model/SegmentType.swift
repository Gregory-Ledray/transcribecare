import Foundation

/// Represents the classification of a transcript segment based on its recency.
///
/// When a new finalized transcript result arrives:
/// - Previous `.current` segments become `.recent`
/// - Previous `.recent` segments become `.past`
/// - The new segment is classified as `.current`
enum SegmentType: String, Codable, CaseIterable {
    /// Previously finalized segments from earlier in the session.
    case past

    /// Recently finalized segment (was "current", now superseded).
    case recent

    /// Most recently finalized segment.
    case current
}
