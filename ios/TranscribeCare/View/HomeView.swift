import SwiftUI
import AVFoundation

/// The main home view for recording and live transcription display.
///
/// Displays recording controls, a live transcript with interim and finalized
/// segments (color-coded by type), and handles microphone permission requests.
struct HomeView: View {

    /// The view model managing recording state and transcript segments.
    @Bindable var viewModel: HomeViewModel

    /// Whether Large Text Mode is enabled (36pt minimum for transcript text).
    var largeTextMode: Bool

    /// Controls display of the permission-denied alert.
    @State private var showPermissionDeniedAlert: Bool = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Recording status banner
                if viewModel.isRecording {
                    RecordingStatusBanner()
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }

                // Live transcript area
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 8) {
                            ForEach(viewModel.segments) { segment in
                                transcriptSegmentView(segment)
                            }

                            // Interim text (partial recognition result)
                            if !viewModel.interimText.isEmpty {
                                Text(viewModel.interimText)
                                    .font(transcriptFont)
                                    .italic()
                                    .foregroundStyle(.secondary)
                                    .id("interim")
                            }
                        }
                        .padding()
                    }
                    .onChange(of: viewModel.segments.count) {
                        if let lastSegment = viewModel.segments.last {
                            withAnimation {
                                proxy.scrollTo(lastSegment.id, anchor: .bottom)
                            }
                        }
                    }
                    .onChange(of: viewModel.interimText) {
                        if !viewModel.interimText.isEmpty {
                            withAnimation {
                                proxy.scrollTo("interim", anchor: .bottom)
                            }
                        }
                    }
                }

                Divider()

                // Recording control button
                recordingButton
                    .padding(.vertical, 16)
            }
            .navigationTitle("TranscribeCare")
            .animation(.default, value: viewModel.isRecording)
            .alert(
                "Microphone Access Required",
                isPresented: $showPermissionDeniedAlert
            ) {
                Button("Open Settings") {
                    openAppSettings()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Recording requires microphone access. Please enable it in Settings > Privacy & Security > Microphone.")
            }
        }
    }

    // MARK: - Subviews

    /// Displays a single transcript segment with color coding based on type.
    @ViewBuilder
    private func transcriptSegmentView(_ segment: TranscriptSegment) -> some View {
        Text(segment.text)
            .font(transcriptFont)
            .foregroundStyle(colorForSegmentType(segment.type))
            .id(segment.id)
    }

    /// The Start/Stop recording button with 44pt minimum touch target.
    private var recordingButton: some View {
        Button(action: handleRecordingToggle) {
            HStack(spacing: 8) {
                Image(systemName: viewModel.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                    .font(.title2)
                Text(viewModel.isRecording ? "Stop Recording" : "Start Recording")
                    .fontWeight(.semibold)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(viewModel.isRecording ? Color.red : Color("Primary"))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel(viewModel.isRecording ? "Stop recording" : "Start recording")
        .accessibilityHint(
            viewModel.isRecording
                ? "Double tap to stop the current recording session"
                : "Double tap to start a new recording session"
        )
    }

    // MARK: - Helpers

    /// The font used for transcript text, respecting Large Text Mode.
    private var transcriptFont: Font {
        largeTextMode ? .system(size: 36) : .body
    }

    /// Returns the appropriate color for a segment type.
    private func colorForSegmentType(_ type: SegmentType) -> Color {
        switch type {
        case .past:
            return .secondary
        case .recent:
            return .primary
        case .current:
            return Color("Primary")
        }
    }

    /// Handles the recording toggle action, including permission checks.
    private func handleRecordingToggle() {
        if viewModel.isRecording {
            viewModel.stopRecording()
        } else {
            requestMicrophonePermission()
        }
    }

    /// Requests microphone permission via AVAudioSession and starts recording if granted.
    private func requestMicrophonePermission() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async {
                if granted {
                    viewModel.startRecording()
                } else {
                    showPermissionDeniedAlert = true
                }
            }
        }
    }

    /// Opens the app's settings page in the system Settings app.
    private func openAppSettings() {
        guard let settingsURL = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        UIApplication.shared.open(settingsURL)
    }
}

#Preview {
    HomeView(
        viewModel: HomeViewModel(),
        largeTextMode: false
    )
}
