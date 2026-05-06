import SwiftUI

/// Settings view displaying user preferences with accessibility support.
///
/// Provides a "Large Text Mode" toggle that persists via @AppStorage
/// and applies across the app for improved readability.
struct SettingsView: View {

    /// The view model managing settings state.
    @Bindable var viewModel: SettingsViewModel

    /// Persisted preference for Large Text Mode using @AppStorage.
    @AppStorage(SettingsViewModel.largeTextModeKey) private var largeTextModeStored: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle(isOn: $viewModel.largeTextMode) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Large Text Mode")
                                .font(.body)
                                .fontWeight(.medium)
                                .foregroundStyle(Color("Primary"))

                            Text("Increases transcript text to a minimum of 36pt for improved readability")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(minHeight: 44)
                    .accessibilityLabel("Large Text Mode")
                    .accessibilityValue(viewModel.largeTextMode ? "Enabled" : "Disabled")
                    .accessibilityHint("Double tap to toggle large text mode for improved readability")
                } header: {
                    Text("Accessibility")
                }
            }
            .navigationTitle("Settings")
            .onChange(of: viewModel.largeTextMode) { _, newValue in
                largeTextModeStored = newValue
            }
            .onAppear {
                // Sync ViewModel with persisted @AppStorage value on appear
                if viewModel.largeTextMode != largeTextModeStored {
                    viewModel.largeTextMode = largeTextModeStored
                }
            }
        }
    }
}

#Preview {
    SettingsView(viewModel: SettingsViewModel())
}
