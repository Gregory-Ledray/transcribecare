# TranscribeCare

An accessible healthcare transcription assistant that provides real-time speech-to-text during medical visits, with a focus on high-contrast visuals and large text for readability.

## Repository Structure

```
transcribecare/
├── web/        # React/Vite web application
├── android/    # Native Android app (Kotlin + Jetpack Compose)
└── ios/        # Native iOS app (Swift + SwiftUI)
```

## Web App

The web application is built with React 19, Vite 6, and Tailwind CSS 4.

### Prerequisites

- Node.js 18+
- npm 9+

### Setup

```bash
cd web
npm install
cp .env.example .env
npm run dev            # Starts dev server on port 3000
```

### Commands

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start development server |
| `npm run build` | Production build (outputs to `dist/`) |
| `npm run preview` | Preview production build |
| `npm run lint` | Type-check with `tsc --noEmit` |

## Android App

The Android app is built with Kotlin and Jetpack Compose, targeting Android 8.0+ (API 26).

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34

### Setup

```bash
cd android
./gradlew assembleDebug
```

Or open the `android/` directory in Android Studio and sync Gradle.

### Key Dependencies

- Jetpack Compose (UI)
- Room (local persistence)
- Navigation Compose (tab navigation)
- Kotest (property-based testing)

## iOS App

The iOS app is built with Swift and SwiftUI, targeting iOS 16.0+.

### Prerequisites

- Xcode 15+
- macOS Sonoma or later

### Setup

```bash
cd ios
open TranscribeCare.xcodeproj
```

Build and run from Xcode on a simulator or device.

### Key Dependencies

- SwiftUI (UI)
- SwiftData (local persistence)
- Speech framework (speech recognition)
- SwiftCheck (property-based testing, via Swift Package Manager)

## Core Features

- Live speech-to-text transcription during medical visits
- Audio recording with variable-speed playback (1x, 1.25x, 1.5x, 2x)
- Session history with search
- Family sharing via native share sheets
- Large text accessibility mode
- High-contrast color scheme (WCAG 4.5:1 minimum)

## License

Proprietary — All rights reserved.
