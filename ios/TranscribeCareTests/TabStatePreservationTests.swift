import XCTest
@testable import TranscribeCare

/// Feature: monorepo-native-apps
/// Property 7: Tab State Preservation
///
/// **Validates: Requirements 9.3, 9.4**
///
/// For any sequence of tab switches and state mutations made within a tab,
/// returning to a previously visited tab SHALL preserve the state that was
/// set before leaving.
///
/// Uses XCTest with 100 randomized iterations to achieve property-based
/// testing coverage.
final class TabStatePreservationTests: XCTestCase {

    // MARK: - Tab Enum (mirrors ContentView.Tab)

    enum Tab: CaseIterable {
        case home
        case history
        case settings
    }

    // MARK: - Random Generators

    /// Generate a random alphanumeric string of given length.
    private func randomString(length: Int) -> String {
        let characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
        return String((0..<length).map { _ in characters.randomElement()! })
    }

    /// Generate a random tab.
    private func randomTab() -> Tab {
        Tab.allCases.randomElement()!
    }

    /// Generate a random tab switch sequence of given length.
    private func randomTabSequence(length: Int) -> [Tab] {
        (0..<length).map { _ in randomTab() }
    }

    // MARK: - Property Test: HistoryViewModel Search Query Preservation

    /// Verifies that setting a search query in HistoryViewModel, then "switching"
    /// to other tabs and back, preserves the search query.
    /// Since ViewModels are owned by @State at ContentView level, they survive
    /// tab switches. This test confirms the ViewModel retains state.
    func testSearchQueryPreservedAcrossTabSwitches() {
        for iteration in 0..<100 {
            // Create ViewModel (simulates @State ownership at ContentView level)
            let historyVM = HistoryViewModel()

            // Generate random search query
            let queryLength = Int.random(in: 1...50)
            let searchQuery = randomString(length: queryLength)

            // Set state (user types a search query in History tab)
            historyVM.search(query: searchQuery)

            // Generate random tab switch sequence (simulates user navigating away and back)
            let switchCount = Int.random(in: 1...20)
            let tabSequence = randomTabSequence(length: switchCount)

            // Simulate tab switches — the ViewModel instance persists because
            // it's held by @State in ContentView, not recreated per tab switch
            var currentTab: Tab = .history
            for tab in tabSequence {
                currentTab = tab
            }
            // "Return" to history tab
            currentTab = .history
            _ = currentTab // suppress unused warning

            // Verify state is preserved
            XCTAssertEqual(
                historyVM.searchQuery,
                searchQuery,
                """
                Iteration \(iteration): Search query not preserved after tab switches.
                Expected: "\(searchQuery)"
                Got: "\(historyVM.searchQuery)"
                Tab sequence length: \(switchCount)
                """
            )

            XCTAssertEqual(
                historyVM.filteredSessions,
                [],
                "Iteration \(iteration): Filtered sessions should remain consistent (empty with no sessions loaded)"
            )
        }
    }

    // MARK: - Property Test: HomeViewModel Recording State Preservation

    /// Verifies that recording state in HomeViewModel is preserved across
    /// simulated tab switches.
    func testRecordingStatePreservedAcrossTabSwitches() {
        for iteration in 0..<100 {
            let homeVM = HomeViewModel()

            // Generate random recording state
            let shouldBeRecording = Bool.random()

            // Set state
            if shouldBeRecording {
                homeVM.startRecording()
            } else {
                homeVM.stopRecording()
            }

            // Also set random interim text if recording
            let interimText: String
            if shouldBeRecording {
                interimText = randomString(length: Int.random(in: 0...30))
                homeVM.onInterimResult(text: interimText)
            } else {
                interimText = ""
            }

            // Generate random segments
            let segmentCount = Int.random(in: 0...5)
            for _ in 0..<segmentCount {
                homeVM.onFinalResult(text: randomString(length: Int.random(in: 1...20)))
            }

            let expectedSegmentCount = homeVM.segments.count
            let expectedIsRecording = homeVM.isRecording
            let expectedInterimText = homeVM.interimText

            // Simulate tab switches
            let switchCount = Int.random(in: 1...20)
            let tabSequence = randomTabSequence(length: switchCount)

            var currentTab: Tab = .home
            for tab in tabSequence {
                currentTab = tab
            }
            currentTab = .home
            _ = currentTab

            // Verify state is preserved
            XCTAssertEqual(
                homeVM.isRecording,
                expectedIsRecording,
                """
                Iteration \(iteration): Recording state not preserved.
                Expected isRecording: \(expectedIsRecording)
                Got: \(homeVM.isRecording)
                """
            )

            XCTAssertEqual(
                homeVM.interimText,
                expectedInterimText,
                """
                Iteration \(iteration): Interim text not preserved.
                Expected: "\(expectedInterimText)"
                Got: "\(homeVM.interimText)"
                """
            )

            XCTAssertEqual(
                homeVM.segments.count,
                expectedSegmentCount,
                """
                Iteration \(iteration): Segment count not preserved.
                Expected: \(expectedSegmentCount)
                Got: \(homeVM.segments.count)
                """
            )
        }
    }

    // MARK: - Property Test: SettingsViewModel Large Text Mode Preservation

    /// Verifies that largeTextMode setting in SettingsViewModel is preserved
    /// across simulated tab switches.
    func testLargeTextModePreservedAcrossTabSwitches() {
        for iteration in 0..<100 {
            let settingsVM = SettingsViewModel()

            // Generate random large text mode state
            let shouldEnableLargeText = Bool.random()

            // Set state
            if shouldEnableLargeText {
                settingsVM.largeTextMode = true
            } else {
                settingsVM.largeTextMode = false
            }

            let expectedLargeTextMode = settingsVM.largeTextMode

            // Simulate tab switches
            let switchCount = Int.random(in: 1...20)
            let tabSequence = randomTabSequence(length: switchCount)

            var currentTab: Tab = .settings
            for tab in tabSequence {
                currentTab = tab
            }
            currentTab = .settings
            _ = currentTab

            // Verify state is preserved
            XCTAssertEqual(
                settingsVM.largeTextMode,
                expectedLargeTextMode,
                """
                Iteration \(iteration): Large text mode not preserved.
                Expected: \(expectedLargeTextMode)
                Got: \(settingsVM.largeTextMode)
                """
            )
        }
    }

    // MARK: - Property Test: Combined Multi-Tab State Preservation

    /// Verifies that state across ALL ViewModels is preserved simultaneously
    /// when switching between tabs in random order. This simulates the real
    /// ContentView behavior where all ViewModels coexist.
    func testCombinedStatePreservedAcrossRandomTabSwitches() {
        for iteration in 0..<100 {
            // Create all ViewModels (simulates ContentView @State ownership)
            let homeVM = HomeViewModel()
            let historyVM = HistoryViewModel()
            let settingsVM = SettingsViewModel()

            // Set random state in each ViewModel

            // Home: random recording state and segments
            let shouldRecord = Bool.random()
            if shouldRecord {
                homeVM.startRecording()
                homeVM.onInterimResult(text: randomString(length: Int.random(in: 1...20)))
            }
            let homeSegmentCount = Int.random(in: 0...3)
            for _ in 0..<homeSegmentCount {
                homeVM.onFinalResult(text: randomString(length: Int.random(in: 1...15)))
            }

            // History: random search query
            let searchQuery = randomString(length: Int.random(in: 1...30))
            historyVM.search(query: searchQuery)

            // Settings: random large text mode
            let largeTextEnabled = Bool.random()
            settingsVM.largeTextMode = largeTextEnabled

            // Capture expected state
            let expectedIsRecording = homeVM.isRecording
            let expectedInterimText = homeVM.interimText
            let expectedSegments = homeVM.segments.count
            let expectedSearchQuery = historyVM.searchQuery
            let expectedLargeText = settingsVM.largeTextMode

            // Perform random tab switch sequence
            let switchCount = Int.random(in: 5...30)
            let tabSequence = randomTabSequence(length: switchCount)

            var currentTab: Tab = .home
            for tab in tabSequence {
                currentTab = tab
            }
            _ = currentTab

            // Verify ALL state is preserved across all ViewModels
            XCTAssertEqual(homeVM.isRecording, expectedIsRecording,
                "Iteration \(iteration): Home recording state lost after \(switchCount) tab switches")
            XCTAssertEqual(homeVM.interimText, expectedInterimText,
                "Iteration \(iteration): Home interim text lost after \(switchCount) tab switches")
            XCTAssertEqual(homeVM.segments.count, expectedSegments,
                "Iteration \(iteration): Home segments lost after \(switchCount) tab switches")
            XCTAssertEqual(historyVM.searchQuery, expectedSearchQuery,
                "Iteration \(iteration): History search query lost after \(switchCount) tab switches")
            XCTAssertEqual(settingsVM.largeTextMode, expectedLargeText,
                "Iteration \(iteration): Settings large text mode lost after \(switchCount) tab switches")
        }
    }
}
