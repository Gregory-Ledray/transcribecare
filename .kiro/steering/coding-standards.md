# Coding Standards

## General Principles

- Keep code readable and accessible to contributors across all three platforms
- Prefer clarity over cleverness
- Use descriptive naming that reflects the healthcare domain
- All user-facing strings should be externalized for future localization

## Web (React/TypeScript)

- Functional components only, no class components
- Use React hooks for state and side effects
- TypeScript strict mode — no `any` types unless interfacing with browser APIs that lack typings
- Tailwind utility classes for styling; avoid inline style objects
- Component naming: PascalCase (e.g., `TranscriptView`, `AudioPlayer`)
- Event handlers: prefix with `handle` (e.g., `handleToggleRecording`)
- Use `useMemo` and `useRef` for performance-sensitive values
- Keep the single-file architecture in App.tsx — components are defined as functions within the file

## Android (Kotlin/Compose)

- Follow Kotlin coding conventions (https://kotlinlang.org/docs/coding-conventions.html)
- Composable functions: PascalCase (e.g., `HomeScreen`, `AppNavigation`)
- ViewModels: suffix with `ViewModel` (e.g., `HistoryViewModel`)
- Data classes for models and entities
- Use `StateFlow` for observable state in ViewModels
- Collect state in Compose with `collectAsStateWithLifecycle()`
- Room entities: suffix with `Entity` (e.g., `SessionEntity`)
- DAOs: suffix with `Dao` (e.g., `SessionDao`)
- Services: suffix with `Service` (e.g., `AudioRecorderService`)
- Use Material 3 components and theming exclusively
- Use Material Icons from `androidx.compose.material.icons`

## iOS (Swift/SwiftUI)

- Follow Swift API Design Guidelines
- Views: suffix with `View` (e.g., `HistoryView`, `HomeView`)
- ViewModels: suffix with `ViewModel` (e.g., `HomeViewModel`)
- Models: plain structs or SwiftData `@Model` classes
- Services: suffix with `Service` (e.g., `SpeechRecognitionService`)
- Use `@Published` properties in ViewModels with `@ObservableObject`
- Prefer value types (structs/enums) over reference types where possible
- Use Swift concurrency (async/await) for asynchronous operations

## File Organization

Each platform follows the same logical structure:
```
Model/       — Data types and domain objects
ViewModel/   — Business logic and state management
View/UI/     — Presentation layer
Service/     — Platform services (audio, speech, sharing)
Data/        — Persistence layer (Room/SwiftData)
```

## Comments and Documentation

- Add KDoc/DocC/JSDoc comments to public APIs and complex logic
- Use `/** */` block comments for type and function documentation
- Inline comments only for non-obvious logic
- Keep TODOs actionable with context (e.g., `// TODO: Handle network timeout in share flow`)
