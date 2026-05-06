import SwiftUI

/// An animated banner displayed when recording is active.
/// Shows a pulsing red dot and "RECORDING ACTIVE" text.
struct RecordingStatusBanner: View {

    /// Controls the pulsing animation state.
    @State private var isPulsing: Bool = false

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(Color.red)
                .frame(width: 10, height: 10)
                .opacity(isPulsing ? 0.3 : 1.0)
                .animation(
                    .easeInOut(duration: 0.8).repeatForever(autoreverses: true),
                    value: isPulsing
                )

            Text("RECORDING ACTIVE")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.red)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 16)
        .background(Color.red.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Recording is active")
        .onAppear {
            isPulsing = true
        }
    }
}

#Preview {
    RecordingStatusBanner()
}
