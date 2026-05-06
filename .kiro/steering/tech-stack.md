# Technology Stack

TranscribeCare is a multi-platform application with three targets sharing the same feature set.

## Web (./web)

- **Framework:** React 19 with TypeScript
- **Build Tool:** Vite 6
- **Styling:** Tailwind CSS 4
- **Icons:** lucide-react
- **Animations:** motion (Framer Motion)
- **AI Integration:** @google/genai (Gemini API)
- **Architecture:** Single-file component-based (App.tsx), functional components with hooks
- **Speech:** Web Speech API (SpeechRecognition)
- **Audio:** MediaRecorder API + HTMLAudioElement
- **Sharing:** Web Share API with clipboard fallback

## Android (./android)

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Navigation:** Navigation Compose
- **Persistence:** Room Database with KSP annotation processing
- **Architecture:** MVVM (ViewModel + StateFlow + Compose)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **Testing:** JUnit 4 + Kotest (property-based testing)
- **Build:** Gradle with Kotlin DSL

## iOS (./ios)

- **Language:** Swift 5.9
- **UI Framework:** SwiftUI
- **Persistence:** SwiftData
- **Speech:** Speech framework
- **Architecture:** MVVM (ObservableObject ViewModels + SwiftUI Views)
- **Min Target:** iOS 17
- **Testing:** SwiftCheck (property-based testing)
- **Package Manager:** Swift Package Manager

## Shared Patterns Across Platforms

- MVVM architecture with clear separation: Model → ViewModel → View
- Service layer for audio recording, playback, speech recognition, and sharing
- Local persistence for session history
- Tab-based navigation (Home, History, Settings)
