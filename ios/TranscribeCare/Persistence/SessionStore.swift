import Foundation
import SwiftData

/// Manages SwiftData persistence for recording sessions.
///
/// Provides CRUD operations, sorted fetch, and search functionality.
/// Uses `@Observable` so SwiftUI views can react to changes.
@Observable
class SessionStore {
    private let modelContainer: ModelContainer
    private let modelContext: ModelContext

    /// Initializes the SessionStore with a SwiftData ModelContainer.
    ///
    /// - Parameter modelContainer: An optional pre-configured container (useful for testing).
    ///   If nil, a default container is created for SessionModel and SegmentModel.
    init(modelContainer: ModelContainer? = nil) {
        if let container = modelContainer {
            self.modelContainer = container
        } else {
            let schema = Schema([SessionModel.self, SegmentModel.self])
            let configuration = ModelConfiguration(schema: schema)
            do {
                self.modelContainer = try ModelContainer(for: schema, configurations: [configuration])
            } catch {
                fatalError("Failed to create ModelContainer: \(error.localizedDescription)")
            }
        }
        self.modelContext = ModelContext(self.modelContainer)
    }

    /// Inserts a recording session into the database.
    ///
    /// Converts the domain `RecordingSession` model into SwiftData models and persists them.
    ///
    /// - Parameter session: The domain recording session to persist.
    func insertSession(session: RecordingSession) {
        let segmentModels = session.segments.enumerated().map { index, segment in
            SegmentModel(
                id: segment.id,
                text: segment.text,
                type: segment.type.rawValue,
                timestamp: segment.timestamp,
                orderIndex: index
            )
        }

        let sessionModel = SessionModel(
            id: session.id,
            title: session.title,
            date: session.date,
            time: session.time,
            createdAt: session.createdAt,
            duration: session.duration,
            audioFilePath: session.audioFilePath,
            statusLabel: session.statusLabel,
            segments: segmentModels
        )

        modelContext.insert(sessionModel)

        do {
            try modelContext.save()
        } catch {
            print("Failed to save session: \(error.localizedDescription)")
        }
    }

    /// Fetches all sessions sorted by creation date in descending order (most recent first).
    ///
    /// - Returns: An array of domain `RecordingSession` objects.
    func fetchAllSessions() -> [RecordingSession] {
        let descriptor = FetchDescriptor<SessionModel>(
            sortBy: [SortDescriptor(\.createdAt, order: .reverse)]
        )

        do {
            let models = try modelContext.fetch(descriptor)
            return models.map { toDomainModel($0) }
        } catch {
            print("Failed to fetch sessions: \(error.localizedDescription)")
            return []
        }
    }

    /// Fetches a single session by its unique identifier.
    ///
    /// - Parameter id: The session's unique identifier.
    /// - Returns: The matching domain `RecordingSession`, or nil if not found.
    func fetchSessionById(id: String) -> RecordingSession? {
        let descriptor = FetchDescriptor<SessionModel>(
            predicate: #Predicate { $0.id == id }
        )

        do {
            let models = try modelContext.fetch(descriptor)
            return models.first.map { toDomainModel($0) }
        } catch {
            print("Failed to fetch session by ID: \(error.localizedDescription)")
            return nil
        }
    }

    /// Searches sessions by case-insensitive substring match on title or segment text.
    ///
    /// - Parameter query: The search query string.
    /// - Returns: An array of matching domain `RecordingSession` objects sorted by creation date descending.
    func searchSessions(query: String) -> [RecordingSession] {
        if query.isEmpty {
            return fetchAllSessions()
        }

        let lowercasedQuery = query.lowercased()

        let descriptor = FetchDescriptor<SessionModel>(
            sortBy: [SortDescriptor(\.createdAt, order: .reverse)]
        )

        do {
            let allModels = try modelContext.fetch(descriptor)
            let filtered = allModels.filter { session in
                if session.title.lowercased().contains(lowercasedQuery) {
                    return true
                }
                return session.segments.contains { segment in
                    segment.text.lowercased().contains(lowercasedQuery)
                }
            }
            return filtered.map { toDomainModel($0) }
        } catch {
            print("Failed to search sessions: \(error.localizedDescription)")
            return []
        }
    }

    /// Deletes a session and its associated segments (cascade delete).
    ///
    /// - Parameter id: The unique identifier of the session to delete.
    func deleteSession(id: String) {
        let descriptor = FetchDescriptor<SessionModel>(
            predicate: #Predicate { $0.id == id }
        )

        do {
            let models = try modelContext.fetch(descriptor)
            if let model = models.first {
                modelContext.delete(model)
                try modelContext.save()
            }
        } catch {
            print("Failed to delete session: \(error.localizedDescription)")
        }
    }

    // MARK: - Private Helpers

    /// Converts a SwiftData `SessionModel` to the domain `RecordingSession` struct.
    private func toDomainModel(_ model: SessionModel) -> RecordingSession {
        let segments = model.segments
            .sorted { $0.orderIndex < $1.orderIndex }
            .map { segmentModel in
                TranscriptSegment(
                    id: segmentModel.id,
                    text: segmentModel.text,
                    type: SegmentType(rawValue: segmentModel.type) ?? .past,
                    timestamp: segmentModel.timestamp
                )
            }

        return RecordingSession(
            id: model.id,
            title: model.title,
            date: model.date,
            time: model.time,
            createdAt: model.createdAt,
            duration: model.duration,
            audioFilePath: model.audioFilePath,
            segments: segments,
            statusLabel: model.statusLabel
        )
    }
}
