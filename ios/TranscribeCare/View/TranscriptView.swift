import SwiftUI

/// A reusable view that renders a list of transcript segments with color-coding by type.
///
/// Segments are color-coded as follows:
/// - `.past`: secondary color (previously finalized segments)
/// - `.recent`: primary color (recently finalized)
/// - `.current`: accent/primary asset color (most recent)
///
/// This view is used in both HomeView (live transcript) and SessionDetailView (full transcript).
///
/// - Requirements: 6.6, 8.2
struct TranscriptView: View {

    /// The transcript segments to display.
    let segments: [TranscriptSegment]

    /// Whether Large Text Mode is enabled (36pt minimum).
    var largeTextMode: Bool

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 8) {
            ForEach(segments) { segment in
                Text(segment.text)
                    .font(transcriptFont)
                    .foregroundStyle(colorForSegmentType(segment.type))
                    .accessibilityLabel(accessibilityLabel(for: segment))
            }
        }
    }

    // MARK: - Helpers

    /// The font used for transcript text, respecting Large Text Mode.
    private var transcriptFont: Font {
        largeTextMode ? .system(size: 36) : .body
    }

    /// Returns the appropriate color for a segment type.
    private func colorForSegmentType(_ type: SegmentType) -> Color {
        switch type {
        case .past:
            return .secondary
        case .recent:
            return .primary
        case .current:
            return Color("Primary")
        }
    }

    /// Builds an accessibility label for a segment including its type.
    private func accessibilityLabel(for segment: TranscriptSegment) -> String {
        let typeLabel: String
        switch segment.type {
        case .past:
            typeLabel = "Past segment"
        case .recent:
            typeLabel = "Recent segment"
        case .current:
            typeLabel = "Current segment"
        }
        return "\(typeLabel): \(segment.text)"
    }
}

#Preview {
    ScrollView {
        TranscriptView(
            segments: [
                TranscriptSegment(text: "Hello, how are you feeling today?", type: .past),
                TranscriptSegment(text: "I've been having some headaches lately.", type: .recent),
                TranscriptSegment(text: "Let me check your blood pressure.", type: .current)
            ],
            largeTextMode: false
        )
        .padding()
    }
}
