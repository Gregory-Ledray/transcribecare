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

struct HomeView_Previews: PreviewProvider {
    class PreviewViewModel: HomeViewModel {
        override func save(title: String) {}
    }
    
    static var previews: some View {
        HomeView(viewModel: PreviewViewModel(), largeTextMode: true)
    }
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

struct HistoryView_Previews: PreviewProvider {
    
    class PreviewSession: Session {
        let id = UUID()
        let title = "Example Session"
        let date = Date()
        let audioURL = URL(string: "https://example.com/audio.mp3")
    }
    
    class PreviewViewModel: HistoryViewModel {
        override var sessions: [Session] {
            [PreviewSession()]
        }
    }
    
    class PreviewPlayerService: AudioPlayerService {
        override func play(url: URL) {}
    }
    
    static var previews: some View {
        HistoryView(viewModel: PreviewViewModel(), playerService: PreviewPlayerService())
    }
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

struct SettingsView_Previews: PreviewProvider {
    class PreviewViewModel: SettingsViewModel {
        @Published var largeTextMode: Bool = true
    }
    
    static var previews: some View {
        SettingsView(viewModel: PreviewViewModel())
    }
}
