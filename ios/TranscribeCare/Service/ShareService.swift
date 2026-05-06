import Foundation

/// Service responsible for formatting and preparing recording session data
/// for sharing via the iOS native share sheet (UIActivityViewController).
struct ShareService {

    /// Formats the session data into a human-readable share text string.
    ///
    /// The formatted text includes:
    /// - Session title
    /// - Session date
    /// - All transcript segment text in order
    ///
    /// - Parameter session: The recording session to format.
    /// - Returns: A formatted string suitable for sharing.
    static func formatShareText(session: RecordingSession) -> String {
        var lines: [String] = []
        lines.append(session.title)
        lines.append(session.date)
        lines.append("")

        for segment in session.segments {
            lines.append(segment.text)
        }

        return lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Prepares the share items for a given recording session.
    ///
    /// Returns an array of items to share via UIActivityViewController.
    /// Includes the formatted text and optionally the audio file URL
    /// if `audioFilePath` is not nil and the file exists on disk.
    ///
    /// - Parameter session: The recording session to share.
    /// - Returns: An array of share items (String and/or URL).
    static func shareItems(for session: RecordingSession) -> [Any] {
        var items: [Any] = []

        let shareText = formatShareText(session: session)
        items.append(shareText)

        if let audioFilePath = session.audioFilePath {
            let fileURL = URL(fileURLWithPath: audioFilePath)
            if FileManager.default.fileExists(atPath: fileURL.path) {
                items.append(fileURL)
            }
        }

        return items
    }
}
