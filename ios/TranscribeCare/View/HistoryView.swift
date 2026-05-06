import SwiftUI

/// The history view displaying a searchable, scrollable list of past recording sessions
/// sorted by date descending, with audio playback controls and sharing capabilities.
///
/// - Requirements: 6.3, 6.4, 6.6, 5.3, 5.4, 5.5, 7.1, 8.6, 8.8
struct HistoryView: View {

    /// The view model managing session list, search, and filtering.
    @Bindable var viewModel: HistoryViewModel

    /// The audio player service for playback controls.
    @Bindable var playerService: AudioPlayerService

    /// Controls display of the share sheet.
    @State private var shareItems: [Any] = []
    @State private var showShareSheet: Bool = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Audio player (shown when a session has audio loaded)
                if playerService.totalDuration > 0 {
                    AudioPlayerView(playerService: playerService)
                        .padding(.horizontal)
                        .padding(.top, 8)
                }

                // Session list
                sessionList
            }
            .navigationTitle("History")
            .searchable(
                text: Binding(
                    get: { viewModel.searchQuery },
                    set: { viewModel.search(query: $0) }
                ),
                prompt: "Search sessions"
            )
            .sheet(isPresented: $showShareSheet) {
                ShareSheet(items: shareItems)
            }
        }
    }

    // MARK: - Subviews

    /// Scrollable list of session rows.
    private var sessionList: some View {
        List {
            ForEach(viewModel.filteredSessions) { session in
                NavigationLink(destination: SessionDetailView(session: session)) {
                    sessionRow(session)
                }
                .frame(minHeight: 44)
                .accessibilityElement(children: .combine)
                .accessibilityLabel(sessionAccessibilityLabel(session))
                .accessibilityHint("Double tap to view session details")
            }
        }
        .listStyle(.plain)
        .overlay {
            if viewModel.filteredSessions.isEmpty {
                emptyStateView
            }
        }
    }

    /// A single session row displaying title, date, time, duration, status badge, and share button.
    @ViewBuilder
    private func sessionRow(_ session: RecordingSession) -> some View {
        HStack(spacing: 12) {
            // Session info
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(session.title)
                        .font(.headline)
                        .lineLimit(1)

                    statusBadge(session.statusLabel)
                }

                HStack(spacing: 12) {
                    Label(session.date, systemImage: "calendar")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Label(session.time, systemImage: "clock")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Label(session.duration, systemImage: "timer")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            // Play button (shown when session has audio)
            if session.audioFilePath != nil {
                playButton(for: session)
            }

            // Share button
            shareButton(for: session)
        }
        .padding(.vertical, 4)
    }

    /// Status badge displaying the session's relative time label.
    private func statusBadge(_ label: String) -> some View {
        Text(label)
            .font(.caption2)
            .fontWeight(.semibold)
            .textCase(.uppercase)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Color("Secondary").opacity(0.15))
            .foregroundStyle(Color("Secondary"))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    /// Play button for a session with 44pt minimum touch target.
    private func playButton(for session: RecordingSession) -> some View {
        Button(action: { playSession(session) }) {
            Image(systemName: isSessionPlaying(session) ? "stop.circle.fill" : "play.circle.fill")
                .font(.title2)
                .foregroundStyle(Color("Primary"))
        }
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel(isSessionPlaying(session) ? "Stop playback" : "Play session audio")
        .accessibilityHint(
            isSessionPlaying(session)
                ? "Double tap to stop audio playback"
                : "Double tap to play audio for \(session.title)"
        )
        .buttonStyle(.plain)
    }

    /// Share button for a session with 44pt minimum touch target.
    private func shareButton(for session: RecordingSession) -> some View {
        Button(action: { shareSession(session) }) {
            Image(systemName: "square.and.arrow.up")
                .font(.body)
                .foregroundStyle(Color("Primary"))
        }
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel("Share session")
        .accessibilityHint("Double tap to share \(session.title)")
        .buttonStyle(.plain)
    }

    /// Empty state view when no sessions match the search.
    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.largeTitle)
                .foregroundStyle(.secondary)

            if viewModel.searchQuery.isEmpty {
                Text("No Sessions")
                    .font(.headline)
                Text("Your recording sessions will appear here.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                Text("No Results")
                    .font(.headline)
                Text("No sessions match your search.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: - Helpers

    /// Starts playback of the session's audio file via the AudioPlayerService.
    private func playSession(_ session: RecordingSession) {
        if isSessionPlaying(session) {
            playerService.stop()
            viewModel.currentPlaybackSessionId = nil
            viewModel.isPlaying = false
        } else {
            guard let audioFilePath = session.audioFilePath else { return }
            let fileURL = URL(fileURLWithPath: audioFilePath)
            playerService.play(url: fileURL)
            viewModel.currentPlaybackSessionId = session.id
            viewModel.isPlaying = true
        }
    }

    /// Returns true if the given session is currently being played.
    private func isSessionPlaying(_ session: RecordingSession) -> Bool {
        viewModel.currentPlaybackSessionId == session.id && playerService.isPlaying
    }

    /// Invokes the share service for a session and presents the share sheet.
    private func shareSession(_ session: RecordingSession) {
        shareItems = ShareService.shareItems(for: session)
        showShareSheet = true
    }

    /// Builds an accessibility label for a session row.
    private func sessionAccessibilityLabel(_ session: RecordingSession) -> String {
        "\(session.title), \(session.statusLabel), \(session.date), \(session.time), duration \(session.duration)"
    }
}

// MARK: - ShareSheet

/// UIViewControllerRepresentable wrapper for UIActivityViewController.
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

#Preview {
    HistoryView(
        viewModel: HistoryViewModel(),
        playerService: AudioPlayerService()
    )
}
