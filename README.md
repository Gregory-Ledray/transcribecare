# TranscribeCare

Overcome hearing loss with real time, offline conversation transcriptions you can share with family. Great for family care coordination



## Repository Structure

```
transcribecare/
├── web/        # React/Vite web application
└── android/    # Native Android app (Kotlin + Jetpack Compose)
```

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

## License

MIT License
