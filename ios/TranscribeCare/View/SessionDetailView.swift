import SwiftUI

/// Detail view for a selected recording session showing the full transcript,
/// session metadata, and a share button.
///
/// Displays:
/// - Session header with title, date, time, and duration
/// - Full transcript rendered via TranscriptView with color-coded segments
/// - Share button in the toolbar for sharing the session
///
/// Supports Large Text Mode (36pt minimum when enabled).
///
/// - Requirements: 6.6, 7.1, 8.2
struct SessionDetailView: View {

    /// The recording session to display.
    let session: RecordingSession

    /// Whether Large Text Mode is enabled (36pt minimum for transcript text).
    var largeTextMode: Bool = false

    /// Controls display of the share sheet.
    @State private var showShareSheet: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Session header
                sessionHeader

                Divider()

                // Full transcript
                TranscriptView(
                    segments: session.segments,
                    largeTextMode: largeTextMode
                )
            }
            .padding()
        }
        .navigationTitle(session.title)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                shareButton
            }
        }
        .sheet(isPresented: $showShareSheet) {
            SessionShareSheet(items: ShareService.shareItems(for: session))
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Session detail for \(session.title)")
    }

    // MARK: - Subviews

    /// Session metadata header displaying date, time, and duration.
    private var sessionHeader: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Label(session.date, systemImage: "calendar")
                    .font(headerFont)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Date: \(session.date)")

                Label(session.time, systemImage: "clock")
                    .font(headerFont)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Time: \(session.time)")
            }

            Label(session.duration, systemImage: "timer")
                .font(headerFont)
                .foregroundStyle(.secondary)
                .accessibilityLabel("Duration: \(session.duration)")

            if !session.statusLabel.isEmpty {
                Text(session.statusLabel)
                    .font(largeTextMode ? .system(size: 28, weight: .semibold) : .caption)
                    .fontWeight(.semibold)
                    .textCase(.uppercase)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color("Secondary").opacity(0.15))
                    .foregroundStyle(Color("Secondary"))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .accessibilityLabel("Status: \(session.statusLabel)")
            }
        }
    }

    /// Share button with 44pt minimum touch target.
    private var shareButton: some View {
        Button(action: { showShareSheet = true }) {
            Image(systemName: "square.and.arrow.up")
                .font(.body)
        }
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel("Share session")
        .accessibilityHint("Double tap to share \(session.title)")
    }

    // MARK: - Helpers

    /// Font for header metadata, respecting Large Text Mode.
    private var headerFont: Font {
        largeTextMode ? .system(size: 30) : .subheadline
    }
}

// MARK: - SessionShareSheet

/// UIViewControllerRepresentable wrapper for UIActivityViewController used in SessionDetailView.
private struct SessionShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

#Preview {
    NavigationStack {
        SessionDetailView(
            session: RecordingSession(
                title: "Doctor Visit",
                date: "Jun 15, 2025",
                time: "02:30 PM",
                duration: "05:32",
                segments: [
                    TranscriptSegment(text: "Hello, how are you feeling today?", type: .past),
                    TranscriptSegment(text: "I've been having some headaches lately.", type: .recent),
                    TranscriptSegment(text: "Let me check your blood pressure.", type: .current)
                ],
                statusLabel: "TODAY"
            ),
            largeTextMode: false
        )
    }
}
