# Build and Test

## Web (./web)

### Prerequisites
- Node.js 18+
- npm 9+

### Setup
```bash
cd web
npm install
cp .env.example .env
```

### Commands
| Command | Purpose |
|---------|---------|
| `npm run dev` | Start dev server on port 3000 |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview production build |
| `npm run lint` | Type-check with `tsc --noEmit` |
| `npm run clean` | Remove `dist/` directory |

### Verification
- Run `npm run lint` to type-check before committing
- Run `npm run build` to verify production build succeeds

## Android (./android)

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34

### Commands
```bash
cd android
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests (Kotest + JUnit)
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew lint                   # Run Android lint
```

### Verification
- Run `./gradlew test` to execute unit tests
- Tests use JUnit 5 platform (configured via `useJUnitPlatform()`)
- Property-based tests use Kotest

## iOS (./ios)

### Prerequisites
- Xcode 15+
- macOS Sonoma or later
- iOS 17 simulator or device

### Commands
```bash
cd ios
swift build                      # Build the package
swift test                       # Run tests (SwiftCheck property-based)
```

Or use Xcode:
- Open `ios/` directory or `Package.swift` in Xcode
- Build: Cmd+B
- Test: Cmd+U

### Verification
- Run `swift build` to verify compilation
- Run `swift test` to execute property-based tests with SwiftCheck

## CI Considerations

- Web: `npm run lint && npm run build` covers type-checking and build verification
- Android: `./gradlew test` runs all unit tests
- iOS: `swift test` runs all package tests
- All platforms should pass their respective checks before merging
