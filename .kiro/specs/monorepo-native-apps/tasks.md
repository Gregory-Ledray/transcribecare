# Implementation Plan: Monorepo Native Apps

## Overview

This plan restructures the TranscribeCare project into a monorepo with three platform targets (web, Android, iOS) and implements native Android (Kotlin/Jetpack Compose) and iOS (Swift/SwiftUI) applications that replicate the web app's core functionality. Tasks are ordered to establish project structure first, then implement shared patterns (data models, services), followed by UI and integration.

## Tasks

- [x] 1. Restructure repository into monorepo layout
  - [x] 1.1 Move existing web app files into `web/` subdirectory
    - Move index.html, package.json, vite.config.ts, tsconfig.json, metadata.json, .env.example, README.md, and src/ into web/
    - Update vite.config.ts path alias to resolve correctly from new location
    - Verify web app builds and runs from the web/ subdirectory
    - _Requirements: 1.1, 1.5_

  - [x] 1.2 Create root-level monorepo files
    - Create root README.md documenting repository structure and setup instructions for web, Android, and iOS
    - Create root .gitignore covering web dist/, Android build/, iOS DerivedData/ and other platform artifacts
    - _Requirements: 1.4, 1.6_

- [x] 2. Set up Android project structure
  - [x] 2.1 Create Android project scaffolding with Gradle Kotlin DSL
    - Create android/ directory with build.gradle.kts, settings.gradle.kts, gradle.properties
    - Create app/build.gradle.kts with dependencies: Jetpack Compose, Room, Lifecycle ViewModel, Navigation Compose, Kotest
    - Set minSdk = 26, targetSdk = 34, Kotlin language version
    - Create gradle/wrapper/ with gradle-wrapper.properties
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 2.2 Create AndroidManifest.xml and resource files
    - Declare RECORD_AUDIO and INTERNET permissions
    - Create res/values/strings.xml, colors.xml, themes.xml with Material 3 color scheme (primary: #041627, secondary: #944a00, background: #fbf9fa)
    - _Requirements: 2.5, 2.6, 8.9_

  - [x] 2.3 Create MainActivity.kt and app entry point
    - Implement MainActivity with setContent using the app theme
    - Create TranscribeCareApp.kt composable as root with navigation setup
    - Create ui/theme/Color.kt, Theme.kt, Type.kt with high-contrast color definitions
    - _Requirements: 2.2, 8.3_

- [x] 3. Set up iOS project structure
  - [x] 3.1 Create iOS project scaffolding
    - Create ios/ directory with TranscribeCare.xcodeproj structure
    - Create Package.swift with dependencies: SwiftCheck for property testing
    - Set minimum deployment target to iOS 16.0
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6_

  - [x] 3.2 Create Info.plist and app entry point
    - Declare NSMicrophoneUsageDescription and NSSpeechRecognitionUsageDescription in Info.plist
    - Create TranscribeCareApp.swift with @main entry point
    - Create ContentView.swift with TabView navigation structure
    - Create Assets.xcassets with color definitions (primary: #041627, secondary: #944a00, background: #fbf9fa)
    - _Requirements: 3.5, 3.2, 8.10_

- [x] 4. Checkpoint - Verify project structure
  - Ensure all three project directories exist with valid build configurations
  - Ensure web app still builds from web/ subdirectory
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Android data models and persistence
  - [x] 5.1 Create data model classes
    - Implement SegmentType.kt enum (PAST, RECENT, CURRENT)
    - Implement TranscriptSegment.kt data class with id, text, type, timestamp
    - Implement RecordingSession.kt data class with all metadata fields
    - _Requirements: 4.2, 4.3, 6.7_

  - [x] 5.2 Implement Room database layer
    - Create SessionEntity.kt and SegmentEntity.kt with Room annotations and foreign key relationship
    - Create SessionDao.kt with insert, query all (sorted descending by createdAt), query by ID, search by title/text, and delete operations
    - Create AppDatabase.kt as Room database singleton with type converters
    - _Requirements: 6.1, 6.2, 6.7_

  - [ ]* 5.3 Write property test: Session Persistence Round-Trip (Android)
    - **Property 2: Session Persistence Round-Trip**
    - Generate random RecordingSession objects with arbitrary fields, persist to Room, retrieve by ID, verify all fields match
    - Use Kotest property testing with minimum 100 iterations
    - **Validates: Requirements 6.1, 6.7**

  - [ ]* 5.4 Write property test: Session Sort Order (Android)
    - **Property 3: Session Sort Order**
    - Generate random lists of sessions with distinct createdAt timestamps, insert all, query sorted list, verify strictly descending order
    - Use Kotest property testing with minimum 100 iterations
    - **Validates: Requirements 6.2, 6.3**

- [x] 6. Implement iOS data models and persistence
  - [x] 6.1 Create data model classes
    - Implement SegmentType.swift enum (past, recent, current)
    - Implement TranscriptSegment.swift struct with id, text, type, timestamp
    - Implement RecordingSession.swift struct with all metadata fields
    - _Requirements: 4.2, 4.3, 6.7_

  - [x] 6.2 Implement SwiftData persistence layer
    - Create SessionModel and SegmentModel as @Model classes with @Relationship for cascade delete
    - Create SessionStore.swift with ModelContainer setup, CRUD operations, sorted fetch (descending by createdAt), and search by title/text
    - _Requirements: 6.1, 6.2, 6.7_

  - [ ]* 6.3 Write property test: Session Persistence Round-Trip (iOS)
    - **Property 2: Session Persistence Round-Trip**
    - Generate random RecordingSession objects, persist to SwiftData, retrieve by ID, verify all fields preserved
    - Use SwiftCheck with minimum 100 iterations
    - **Validates: Requirements 6.1, 6.7**

  - [ ]* 6.4 Write property test: Session Sort Order (iOS)
    - **Property 3: Session Sort Order**
    - Generate random session lists with distinct createdAt dates, verify sorted fetch returns strictly descending order
    - Use SwiftCheck with minimum 100 iterations
    - **Validates: Requirements 6.2, 6.3**

- [x] 7. Implement segment reclassification logic
  - [x] 7.1 Implement Android segment reclassification
    - Create reclassifyAndAppend function in HomeViewModel: current→recent, recent→past, append new segment as current
    - Ensure function is pure (takes list + new text, returns new list) for testability
    - _Requirements: 4.2, 4.3_

  - [x] 7.2 Implement iOS segment reclassification
    - Create reclassifyAndAppend function in HomeViewModel: current→recent, recent→past, append new segment as current
    - Ensure function is pure for testability
    - _Requirements: 4.2, 4.3_

  - [ ]* 7.3 Write property test: Segment Reclassification Invariant (Android)
    - **Property 1: Segment Reclassification Invariant**
    - Generate random segment lists + new text, verify: exactly one "current" segment after reclassification, all previously "current" are now "recent", total count increases by exactly one
    - Use Kotest property testing with minimum 100 iterations
    - **Validates: Requirements 4.2, 4.3**

  - [ ]* 7.4 Write property test: Segment Reclassification Invariant (iOS)
    - **Property 1: Segment Reclassification Invariant**
    - Generate random segment lists + new text, verify same invariants as Android
    - Use SwiftCheck with minimum 100 iterations
    - **Validates: Requirements 4.2, 4.3**

- [x] 8. Checkpoint - Verify data layer and core logic
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement Android speech recognition service
  - [x] 9.1 Create SpeechRecognitionService.kt
    - Implement wrapper around android.speech.SpeechRecognizer with RecognitionListener
    - Handle onPartialResults (interim text) and onResults (final text) callbacks
    - Implement auto-restart on unexpected session end when recording intent is active
    - Handle error codes: ERROR_NO_MATCH (continue), ERROR_NETWORK (fallback), ERROR_RECOGNIZER_BUSY (retry)
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 4.8_

  - [x] 9.2 Create AudioRecorderService.kt
    - Implement wrapper around android.media.MediaRecorder for AAC audio capture
    - Handle start/stop recording with file path management
    - Handle errors: microphone in use, storage full, init failure
    - _Requirements: 5.1, 5.2, 5.6_

  - [x] 9.3 Create AudioPlayerService.kt
    - Implement wrapper around android.media.MediaPlayer
    - Support variable speed playback (1x, 1.25x, 1.5x, 2x) via setPlaybackParams()
    - Track progress position and total duration
    - Handle end-of-playback reset
    - _Requirements: 5.3, 5.4, 5.5_

- [x] 10. Implement iOS speech recognition service
  - [x] 10.1 Create SpeechRecognitionService.swift
    - Implement wrapper around SFSpeechRecognizer with SFSpeechAudioBufferRecognitionRequest
    - Handle partial and final recognition results via result handler
    - Implement auto-restart on unexpected session end
    - Set requiresOnDeviceRecognition = true as network fallback
    - _Requirements: 4.1, 4.4, 4.5, 4.7, 4.8_

  - [x] 10.2 Create AudioRecorderService.swift
    - Implement wrapper around AVAudioRecorder for M4A/AAC audio capture
    - Configure AVAudioSession for recording category
    - Handle start/stop recording with file path management
    - Handle errors: microphone in use, storage full, init failure
    - _Requirements: 5.1, 5.2, 5.6_

  - [x] 10.3 Create AudioPlayerService.swift
    - Implement wrapper around AVAudioPlayer
    - Support variable speed playback via rate property (1.0, 1.25, 1.5, 2.0)
    - Track progress position and total duration
    - Handle end-of-playback reset
    - _Requirements: 5.3, 5.4, 5.5_

- [x] 11. Implement Android search and share services
  - [x] 11.1 Implement search filtering in HistoryViewModel
    - Filter sessions by case-insensitive substring match on title and segment text
    - Return all sessions when query is empty
    - _Requirements: 6.4_

  - [x] 11.2 Create ShareService.kt
    - Format share text with session title, date, and all transcript segment text
    - Create Intent(ACTION_SEND) with formatted text and optional audio file URI
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 11.3 Write property test: Search Filter Correctness (Android)
    - **Property 4: Search Filter Correctness**
    - Generate random sessions and queries, verify filtered results contain exactly matching sessions (case-insensitive substring in title or segment text)
    - Use Kotest property testing with minimum 100 iterations
    - **Validates: Requirements 6.4**

  - [ ]* 11.4 Write property test: Share Content Formatting Completeness (Android)
    - **Property 5: Share Content Formatting Completeness**
    - Generate random sessions with non-empty title, date, and segments, verify formatted share text contains title, date, and all segment text
    - Use Kotest property testing with minimum 100 iterations
    - **Validates: Requirements 7.3**

- [x] 12. Implement iOS search and share services
  - [x] 12.1 Implement search filtering in HistoryViewModel
    - Filter sessions by case-insensitive substring match on title and segment text
    - Return all sessions when query is empty
    - _Requirements: 6.4_

  - [x] 12.2 Create ShareService.swift
    - Format share text with session title, date, and all transcript segment text
    - Present UIActivityViewController with formatted text and optional audio file URL
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

  - [ ]* 12.3 Write property test: Search Filter Correctness (iOS)
    - **Property 4: Search Filter Correctness**
    - Generate random sessions and queries, verify filtered results contain exactly matching sessions
    - Use SwiftCheck with minimum 100 iterations
    - **Validates: Requirements 6.4**

  - [ ]* 12.4 Write property test: Share Content Formatting Completeness (iOS)
    - **Property 5: Share Content Formatting Completeness**
    - Generate random sessions with non-empty fields, verify formatted share text contains title, date, and all segment text
    - Use SwiftCheck with minimum 100 iterations
    - **Validates: Requirements 7.3**

- [x] 13. Checkpoint - Verify services layer
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implement Android UI screens
  - [x] 14.1 Implement HomeScreen.kt with HomeViewModel
    - Create recording controls (Start/Stop button with 48dp minimum touch target)
    - Display live transcript with interim text and finalized segments (color-coded by type)
    - Display RecordingStatusBanner with animated indicator when recording is active
    - Wire permission request flow: check → rationale → request → handle denial with Settings guidance
    - Support Large Text Mode (36sp minimum for transcript text when enabled)
    - Add content descriptions for all interactive elements (TalkBack support)
    - _Requirements: 4.1, 4.4, 4.5, 5.1, 8.1, 8.5, 8.7, 10.1, 10.3, 11.1, 11.3_

  - [x] 14.2 Implement HistoryScreen.kt with HistoryViewModel
    - Display scrollable session list sorted by date descending with status labels
    - Implement search bar with real-time filtering
    - Add audio playback controls with speed selector and progress indicator
    - Navigate to SessionDetailScreen on session tap
    - Add share button per session invoking ShareService
    - Ensure 48dp touch targets and content descriptions
    - _Requirements: 6.2, 6.4, 6.5, 5.3, 5.4, 5.5, 7.1, 8.5, 8.7_

  - [x] 14.3 Implement SettingsScreen.kt with SettingsViewModel
    - Create Large Text Mode toggle switch
    - Persist preference and apply to transcript text across the app
    - Ensure 48dp touch targets and content descriptions
    - _Requirements: 8.1, 8.5, 8.7_

  - [x] 14.4 Implement SessionDetailScreen.kt
    - Display full transcript with all segments
    - Support Large Text Mode
    - Add share button for the session
    - _Requirements: 6.5, 7.1, 8.1_

  - [x] 14.5 Implement AppNavigation.kt with bottom navigation bar
    - Set up Navigation Compose with three destinations: Home, History, Settings
    - Highlight active tab with primary color
    - Preserve state across tab switches using SavedStateHandle
    - _Requirements: 9.1, 9.3, 9.5_

- [x] 15. Implement iOS UI views
  - [x] 15.1 Implement HomeView.swift with HomeViewModel
    - Create recording controls (Start/Stop button with 44pt minimum touch target)
    - Display live transcript with interim text and finalized segments (color-coded by type)
    - Display RecordingStatusBanner with animated indicator when recording is active
    - Wire permission request flow: request → handle denial with Settings guidance
    - Support Large Text Mode (36pt minimum for transcript text when enabled)
    - Add accessibility labels for all interactive elements (VoiceOver support)
    - _Requirements: 4.1, 4.4, 4.5, 5.1, 8.2, 8.6, 8.8, 10.2, 10.4, 11.2, 11.4_

  - [x] 15.2 Implement HistoryView.swift with HistoryViewModel
    - Display scrollable session list sorted by date descending with status labels
    - Implement search bar with real-time filtering
    - Add AudioPlayerView with speed selector and progress indicator
    - Navigate to SessionDetailView on session tap
    - Add share button per session invoking ShareService
    - Ensure 44pt touch targets and accessibility labels
    - _Requirements: 6.3, 6.4, 6.6, 5.3, 5.4, 5.5, 7.1, 8.6, 8.8_

  - [x] 15.3 Implement SettingsView.swift with SettingsViewModel
    - Create Large Text Mode toggle
    - Persist preference using @AppStorage and apply across the app
    - Ensure 44pt touch targets and accessibility labels
    - _Requirements: 8.2, 8.6, 8.8_

  - [x] 15.4 Implement SessionDetailView.swift
    - Display full transcript with all segments using TranscriptView
    - Support Large Text Mode
    - Add share button for the session
    - _Requirements: 6.6, 7.1, 8.2_

  - [x] 15.5 Wire ContentView.swift TabView navigation
    - Configure TabView with three tabs: Home, History, Settings
    - Highlight active tab with primary color
    - Preserve state across tab switches using @State
    - _Requirements: 9.2, 9.4, 9.6_

- [x] 16. Implement accessibility and theme compliance
  - [x] 16.1 Write property test: Color Contrast Compliance (Android)
    - **Property 6: Color Contrast Compliance**
    - Enumerate all text/background color pairs from Theme.kt, compute WCAG contrast ratio, verify ≥ 4.5:1
    - Use Kotest property testing
    - **Validates: Requirements 8.3, 8.4**

  - [x] 16.2 Write property test: Color Contrast Compliance (iOS)
    - **Property 6: Color Contrast Compliance**
    - Enumerate all text/background color pairs from the iOS color system, compute WCAG contrast ratio, verify ≥ 4.5:1
    - Use SwiftCheck or XCTest
    - **Validates: Requirements 8.3, 8.4**

  - [x] 16.3 Write property test: Tab State Preservation (Android)
    - **Property 7: Tab State Preservation**
    - Generate random tab switch sequences with state mutations (search query, recording state), verify returning to a tab preserves its state
    - Use Kotest property testing with minimum 100 iterations
    - **Validates: Requirements 9.3, 9.4**

  - [x] 16.4 Write property test: Tab State Preservation (iOS)
    - **Property 7: Tab State Preservation**
    - Generate random tab switch sequences with state mutations, verify returning to a tab preserves its state
    - Use SwiftCheck with minimum 100 iterations
    - **Validates: Requirements 9.3, 9.4**

- [x] 17. Checkpoint - Verify UI and accessibility
  - Ensure all tests pass, ask the user if questions arise.

- [x] 18. Integration and final wiring
  - [x] 18.1 Wire Android end-to-end recording flow
    - Connect HomeViewModel to SpeechRecognitionService, AudioRecorderService, and Room database
    - Verify: tap Start → permissions checked → speech + audio start → segments appear → tap Stop → session saved to database → appears in History
    - _Requirements: 4.1, 4.2, 4.5, 5.1, 5.2, 6.1_

  - [x] 18.2 Wire iOS end-to-end recording flow
    - Connect HomeViewModel to SpeechRecognitionService, AudioRecorderService, and SessionStore
    - Verify: tap Start → permissions checked → speech + audio start → segments appear → tap Stop → session saved → appears in History
    - _Requirements: 4.1, 4.3, 4.5, 5.1, 5.2, 6.1_

  - [x] 18.3 Wire Android history playback and sharing
    - Connect HistoryViewModel to AudioPlayerService and ShareService
    - Verify: session list loads from Room, search filters correctly, audio plays with speed control, share opens native sheet
    - _Requirements: 5.3, 6.2, 6.4, 7.1, 7.4_

  - [x] 18.4 Wire iOS history playback and sharing
    - Connect HistoryViewModel to AudioPlayerService and ShareService
    - Verify: session list loads from SwiftData, search filters correctly, audio plays with speed control, share opens native sheet
    - _Requirements: 5.3, 6.3, 6.4, 7.1, 7.5_

  - [ ]* 18.5 Write integration tests for Android
    - Test Room database CRUD operations end-to-end
    - Test permission flow triggers system dialog
    - Test audio file creation on disk after recording
    - _Requirements: 5.2, 6.1, 11.1_

  - [ ]* 18.6 Write integration tests for iOS
    - Test SwiftData CRUD operations end-to-end
    - Test permission flow triggers system dialog
    - Test audio file creation on disk after recording
    - _Requirements: 5.2, 6.1, 11.2_

- [x] 19. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation between major phases
- Property tests validate universal correctness properties from the design document (7 properties, tested on both platforms)
- The web app remains unchanged functionally; only its location in the repository changes
- Android uses Kotest for property-based testing; iOS uses SwiftCheck
