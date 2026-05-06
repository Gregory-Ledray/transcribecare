import Foundation

/// ViewModel for the Settings screen managing user preferences.
/// Persists the Large Text Mode preference using UserDefaults.
@Observable
class SettingsViewModel {

    /// UserDefaults key for the Large Text Mode preference.
    static let largeTextModeKey = "largeTextModeEnabled"

    /// Whether Large Text Mode is enabled (36pt minimum for transcript text).
    /// Backed by UserDefaults for persistence across app launches.
    var largeTextMode: Bool {
        didSet {
            UserDefaults.standard.set(largeTextMode, forKey: Self.largeTextModeKey)
        }
    }

    init() {
        self.largeTextMode = UserDefaults.standard.bool(forKey: Self.largeTextModeKey)
    }

    /// Toggles the Large Text Mode preference.
    func toggleLargeTextMode() {
        largeTextMode.toggle()
    }
}
