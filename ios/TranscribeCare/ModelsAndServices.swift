import SwiftUI
import Foundation

// Placeholder class to manage session data
class SessionStore {
    init() {}
    
    // Returns all stored sessions (empty by default)
    func fetchAllSessions() -> [Session] {
        return []
    }
    
    // Saves a session (no-op)
    func save(session: Session) {}
    
    // Session model representing a recording session
    struct Session: Identifiable, Codable {
        var id: UUID
        var title: String
        var date: Date
        var audioURL: URL?
        
        init(id: UUID = UUID(), title: String = "", date: Date = Date(), audioURL: URL? = nil) {
            self.id = id
            self.title = title
            self.date = date
            self.audioURL = audioURL
        }
    }
}

// Placeholder audio player service used by HistoryView
class AudioPlayerService {
    var isPlaying: Bool = false
    
    // Plays audio from the given URL (no-op)
    func play(url: URL) {}
    
    // Stops audio playback (no-op)
    func stop() {}
}

// ViewModel for the HomeView, managing sessions
class HomeViewModel: ObservableObject {
    let sessionStore: SessionStore
    
    init(sessionStore: SessionStore) {
        self.sessionStore = sessionStore
    }
    
    // Starts a new session (no-op placeholder)
    func startNewSession() {}
    
    // Saves a new session with the given title
    func save(title: String) {
        let s = SessionStore.Session(title: title)
        sessionStore.save(session: s)
    }
}

// ViewModel for the HistoryView, managing session list
class HistoryViewModel: ObservableObject {
    @Published var sessions: [SessionStore.Session] = []
    
    // Sets the list of sessions
    func setSessions(_ new: [SessionStore.Session]) {
        self.sessions = new
    }
}

// ViewModel for the SettingsView, managing user settings
class SettingsViewModel: ObservableObject {
    @Published var largeTextMode: Bool = false
}
