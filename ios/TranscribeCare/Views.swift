import SwiftUI

// MARK: - HomeView

struct HomeView: View {
    let viewModel: HomeViewModel
    let largeTextMode: Bool
    
    init(viewModel: HomeViewModel, largeTextMode: Bool) {
        self.viewModel = viewModel
        self.largeTextMode = largeTextMode
    }
    
    var body: some View {
        VStack {
            Text("Home")
            Button("Save Demo Session") {
                viewModel.save(title: "Demo")
            }
        }
        .font(largeTextMode ? .largeTitle : .body)
    }
}

#Preview("HomeView") {
    HomeView(viewModel: HomeViewModel(sessionStore: SessionStore()), largeTextMode: true)
}

// MARK: - HistoryView

struct HistoryView: View {
    let viewModel: HistoryViewModel
    let playerService: AudioPlayerService
    
    init(viewModel: HistoryViewModel, playerService: AudioPlayerService) {
        self.viewModel = viewModel
        self.playerService = playerService
    }
    
    var body: some View {
        List(viewModel.sessions, id: \.id) { session in
            VStack(alignment: .leading) {
                Text(session.title)
                Text(session.date.formatted())
            }
            .onTapGesture {
                if let url = session.audioURL {
                    playerService.play(url: url)
                }
            }
        }
    }
}

#Preview("HistoryView") {
    HistoryView(
        viewModel: {
            let vm = HistoryViewModel()
            // Preview without sample data to avoid cross-file type references.
            vm.setSessions([])
            return vm
        }(),
        playerService: AudioPlayerService()
    )
}

// MARK: - SettingsView

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel
    
    init(viewModel: SettingsViewModel) {
        self.viewModel = viewModel
    }
    
    var body: some View {
        Form {
            Toggle("Large Text", isOn: $viewModel.largeTextMode)
        }
    }
}

#Preview("SettingsView") {
    SettingsView(viewModel: {
        let vm = SettingsViewModel()
        vm.largeTextMode = true
        return vm
    }())
}
