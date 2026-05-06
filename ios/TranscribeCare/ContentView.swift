import SwiftUI

/// Root view with TabView navigation providing three tabs: Home, History, Settings.
///
/// Creates and owns ViewModels at this level so they survive tab switches,
/// preserving state across navigation. The active tab is highlighted with
/// the primary color.
///
/// A shared SessionStore is used by both HomeViewModel (to save sessions)
/// and the History tab (to load and display sessions).
///
/// - Requirements: 9.2, 9.4, 9.6
struct ContentView: View {

    // MARK: - Tab Selection

    /// The currently selected tab, preserved via @State across tab switches.
    @State private var selectedTab: Tab = .home

    // MARK: - Shared Services

    @State private var sessionStore = SessionStore()

    // MARK: - ViewModels (owned here to survive tab switches)

    @State private var homeViewModel: HomeViewModel?
    @State private var historyViewModel = HistoryViewModel()
    @State private var settingsViewModel = SettingsViewModel()
    @State private var audioPlayerService = AudioPlayerService()

    // MARK: - Tab Enum

    enum Tab {
        case home
        case history
        case settings
    }

    // MARK: - Body

    var body: some View {
        if #available(iOS 17.0, *) {
            TabView(selection: $selectedTab) {
                HomeView(
                    viewModel: resolvedHomeViewModel,
                    largeTextMode: settingsViewModel.largeTextMode
                )
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }
                .tag(Tab.home)
                
                HistoryView(
                    viewModel: historyViewModel,
                    playerService: audioPlayerService
                )
                .tabItem {
                    Label("History", systemImage: "clock.fill")
                }
                .tag(Tab.history)
                
                SettingsView(viewModel: settingsViewModel)
                    .tabItem {
                        Label("Settings", systemImage: "gearshape.fill")
                    }
                    .tag(Tab.settings)
            }
            .tint(Color("Primary"))
            .onChange(of: selectedTab) {
                if selectedTab == .history {
                    loadHistorySessions()
                }
            }
            .onAppear {
                loadHistorySessions()
            }
        } else {
            // Fallback on earlier versions
        }
    }

    // MARK: - Helpers

    /// Lazily creates the HomeViewModel with the shared SessionStore.
    private var resolvedHomeViewModel: HomeViewModel {
        if let vm = homeViewModel {
            return vm
        }
        let vm = HomeViewModel(sessionStore: sessionStore)
        DispatchQueue.main.async {
            homeViewModel = vm
        }
        return vm
    }

    /// Loads sessions from the SessionStore into the HistoryViewModel.
    private func loadHistorySessions() {
        let sessions = sessionStore.fetchAllSessions()
        historyViewModel.setSessions(sessions)
    }
}

#Preview {
    ContentView()
}
