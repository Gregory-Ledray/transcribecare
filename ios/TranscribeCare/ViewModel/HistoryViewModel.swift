import Foundation

/// ViewModel for the History screen managing session list, search filtering,
/// and audio playback state.
@Observable
class HistoryViewModel {

    /// The complete list of recording sessions loaded from persistence.
    var sessions: [RecordingSession] = []

    /// The current search query entered by the user.
    var searchQuery: String = ""

    /// The filtered list of sessions based on the current search query.
    var filteredSessions: [RecordingSession] = []

    // MARK: - Audio Playback State (stubs — will be wired to AudioPlayerService later)

    /// Whether audio is currently playing.
    var isPlaying: Bool = false

    /// The session ID of the currently playing audio, or nil if nothing is playing.
    var currentPlaybackSessionId: String? = nil

    // MARK: - Public Methods

    /// Updates the search query and recomputes the filtered session list.
    ///
    /// When the query is empty, all sessions are returned.
    /// Otherwise, sessions are filtered by case-insensitive substring match
    /// on the session title OR any segment's text.
    ///
    /// - Parameter query: The search query string.
    func search(query: String) {
        searchQuery = query
        filteredSessions = Self.filterSessions(sessions: sessions, query: query)
    }

    /// Sets the full list of sessions (e.g., loaded from the database)
    /// and recomputes the filtered list based on the current query.
    ///
    /// - Parameter sessions: The complete list of recording sessions.
    func setSessions(_ sessions: [RecordingSession]) {
        self.sessions = sessions
        filteredSessions = Self.filterSessions(sessions: sessions, query: searchQuery)
    }

    // MARK: - Static Filter (for testability)

    /// Pure function that filters sessions by case-insensitive substring match.
    ///
    /// A session matches if:
    /// - Its title contains the query (case-insensitive), OR
    /// - At least one of its segments' text contains the query (case-insensitive)
    ///
    /// Returns all sessions when the query is empty.
    ///
    /// - Parameters:
    ///   - sessions: The list of sessions to filter.
    ///   - query: The search query string.
    /// - Returns: The filtered list of sessions matching the query.
    static func filterSessions(
        sessions: [RecordingSession],
        query: String
    ) -> [RecordingSession] {
        if query.isEmpty {
            return sessions
        }

        let lowerQuery = query.lowercased()

        return sessions.filter { session in
            session.title.lowercased().contains(lowerQuery) ||
            session.segments.contains { segment in
                segment.text.lowercased().contains(lowerQuery)
            }
        }
    }
}
