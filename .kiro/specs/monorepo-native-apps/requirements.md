# Requirements Document

## Introduction

This document specifies the requirements for restructuring the TranscribeCare project into a monorepo layout and creating native Android and iOS applications that closely replicate the existing web application's functionality. The monorepo will house the existing web app under a dedicated subdirectory and introduce platform-native implementations for Android (Kotlin) and iOS (Swift) that leverage native speech recognition, audio recording, and accessibility APIs for improved performance and platform-specific user experiences.

## Glossary

- **Monorepo**: A single repository containing multiple related projects (web, Android, iOS) organized in subdirectories
- **Web_App**: The existing TranscribeCare React/Vite web application served at app.transcribecare.com
- **Android_App**: The native Android application written in Kotlin using Jetpack Compose
- **iOS_App**: The native iOS application written in Swift using SwiftUI
- **Speech_Recognizer**: The platform-specific speech-to-text engine (Android SpeechRecognizer API or iOS SFSpeechRecognizer)
- **Audio_Recorder**: The platform-specific audio recording component (Android MediaRecorder or iOS AVAudioRecorder)
- **Audio_Player**: The platform-specific audio playback component with variable speed support
- **Session_Store**: The local persistence layer for recording sessions and transcripts
- **Share_Service**: The platform-specific sharing mechanism (Android Intent system or iOS UIActivityViewController)
- **Transcript_Segment**: A discrete unit of transcribed text classified as past, recent, or current
- **Recording_Session**: A complete recording event containing audio data, transcript segments, and metadata

## Requirements

### Requirement 1: Monorepo Directory Structure

**User Story:** As a developer, I want the repository restructured into a monorepo layout, so that the web, Android, and iOS projects coexist in a single repository with clear separation.

#### Acceptance Criteria

1. THE Monorepo SHALL contain a `web/` directory housing all existing web application files (src/, index.html, package.json, vite.config.ts, tsconfig.json, metadata.json, .env.example, README.md)
2. THE Monorepo SHALL contain an `android/` directory housing the native Android application project
3. THE Monorepo SHALL contain an `ios/` directory housing the native iOS application project
4. THE Monorepo SHALL contain a root-level README.md documenting the repository structure and setup instructions for each platform
5. THE Web_App SHALL remain fully functional after relocation to the `web/` subdirectory without changes to its build or runtime behavior
6. THE Monorepo SHALL contain a root-level `.gitignore` that covers build artifacts for all three platforms (web dist/, Android build/, iOS DerivedData/)

### Requirement 2: Native Android Project Setup

**User Story:** As a developer, I want a properly structured native Android project, so that the Android app can be built and developed using standard Android tooling.

#### Acceptance Criteria

1. THE Android_App SHALL use Kotlin as the primary programming language
2. THE Android_App SHALL use Jetpack Compose for the UI framework
3. THE Android_App SHALL target a minimum SDK version of 26 (Android 8.0) to support the required speech and audio APIs
4. THE Android_App SHALL use Gradle with Kotlin DSL for build configuration
5. THE Android_App SHALL declare microphone and speech recognition permissions in the AndroidManifest.xml
6. THE Android_App SHALL follow the standard Android project structure (app/src/main/java, app/src/main/res, etc.)

### Requirement 3: Native iOS Project Setup

**User Story:** As a developer, I want a properly structured native iOS project, so that the iOS app can be built and developed using standard Apple tooling.

#### Acceptance Criteria

1. THE iOS_App SHALL use Swift as the primary programming language
2. THE iOS_App SHALL use SwiftUI for the UI framework
3. THE iOS_App SHALL target iOS 16.0 as the minimum deployment version to support required speech and audio APIs
4. THE iOS_App SHALL use Swift Package Manager for dependency management
5. THE iOS_App SHALL declare microphone and speech recognition usage descriptions in Info.plist
6. THE iOS_App SHALL follow the standard Xcode project structure with an .xcodeproj file

### Requirement 4: Real-Time Speech-to-Text Transcription

**User Story:** As a patient or caregiver, I want live speech-to-text transcription during medical visits, so that I have an accurate written record of the conversation.

#### Acceptance Criteria

1. WHEN the user taps the "Start Recording" button, THE Speech_Recognizer SHALL begin continuous speech recognition and display interim results in real time
2. WHEN the Speech_Recognizer produces a finalized transcript result, THE Android_App SHALL append a new Transcript_Segment with type "current" and reclassify previous "current" segments as "recent"
3. WHEN the Speech_Recognizer produces a finalized transcript result, THE iOS_App SHALL append a new Transcript_Segment with type "current" and reclassify previous "current" segments as "recent"
4. WHILE recording is active, THE Speech_Recognizer SHALL display interim (non-final) text to indicate active listening
5. WHEN the user taps the "Stop Recording" button, THE Speech_Recognizer SHALL cease recognition and finalize any remaining interim text
6. IF the Speech_Recognizer encounters an error, THEN THE Android_App SHALL stop recording and display an informative error message to the user
7. IF the Speech_Recognizer encounters an error, THEN THE iOS_App SHALL stop recording and display an informative error message to the user
8. WHEN the Speech_Recognizer session ends unexpectedly while recording intent is active, THE Speech_Recognizer SHALL automatically restart recognition to maintain continuous transcription

### Requirement 5: Audio Recording and Playback

**User Story:** As a patient, I want to record audio during my medical visit and play it back later at different speeds, so that I can review what was said at my own pace.

#### Acceptance Criteria

1. WHEN the user taps "Start Recording", THE Audio_Recorder SHALL simultaneously capture audio alongside speech recognition
2. WHEN the user taps "Stop Recording", THE Audio_Recorder SHALL save the recorded audio to local storage and associate it with the Recording_Session
3. THE Audio_Player SHALL support playback at speeds of 1x, 1.25x, 1.5x, and 2x
4. WHILE audio is playing, THE Audio_Player SHALL display a progress indicator showing current position and total duration
5. WHEN audio playback reaches the end, THE Audio_Player SHALL reset to the beginning position and stop playback
6. THE Audio_Recorder SHALL record in a format supported natively by the platform (AAC on iOS, WebM or AAC on Android)

### Requirement 6: Session History and Search

**User Story:** As a patient, I want to browse and search my past recording sessions, so that I can find specific medical conversations when I need them.

#### Acceptance Criteria

1. THE Session_Store SHALL persist all Recording_Sessions locally on the device using platform-appropriate storage (Room database on Android, Core Data or SwiftData on iOS)
2. THE Android_App SHALL display a scrollable list of past Recording_Sessions sorted by date in descending order
3. THE iOS_App SHALL display a scrollable list of past Recording_Sessions sorted by date in descending order
4. WHEN the user enters a search query, THE Session_Store SHALL filter sessions by matching against session titles and transcript segment text (case-insensitive)
5. WHEN the user taps on a Recording_Session, THE Android_App SHALL navigate to a detail view showing the full transcript
6. WHEN the user taps on a Recording_Session, THE iOS_App SHALL navigate to a detail view showing the full transcript
7. THE Session_Store SHALL store session metadata including title, date, time, duration, and a status label (e.g., "TODAY", "YESTERDAY")

### Requirement 7: Family Sharing

**User Story:** As a caregiver, I want to share transcription sessions with family members, so that everyone involved in the patient's care stays informed.

#### Acceptance Criteria

1. WHEN the user taps the share button on a Recording_Session, THE Share_Service SHALL present the platform's native share sheet with the transcript text
2. WHERE the platform supports file sharing, THE Share_Service SHALL include the audio recording file in the share payload
3. THE Share_Service SHALL format the shared content to include the session title, date, and full transcript text
4. THE Android_App SHALL use the Android Intent system (ACTION_SEND) to invoke the native share sheet
5. THE iOS_App SHALL use UIActivityViewController to invoke the native share sheet

### Requirement 8: Accessibility and Visual Design

**User Story:** As an elderly or visually impaired user, I want large text and high-contrast visuals, so that I can easily read the transcription during my medical visit.

#### Acceptance Criteria

1. THE Android_App SHALL provide a "Large Text Mode" toggle in Settings that increases transcription text to a minimum of 36sp
2. THE iOS_App SHALL provide a "Large Text Mode" toggle in Settings that increases transcription text to a minimum of 36pt
3. THE Android_App SHALL use a high-contrast color scheme with a minimum contrast ratio of 4.5:1 for body text against background colors
4. THE iOS_App SHALL use a high-contrast color scheme with a minimum contrast ratio of 4.5:1 for body text against background colors
5. THE Android_App SHALL ensure all interactive elements have a minimum touch target size of 48dp × 48dp
6. THE iOS_App SHALL ensure all interactive elements have a minimum touch target size of 44pt × 44pt
7. THE Android_App SHALL support the system-level TalkBack screen reader by providing content descriptions for all interactive elements
8. THE iOS_App SHALL support VoiceOver by providing accessibility labels for all interactive elements
9. THE Android_App SHALL use the Material Design 3 color system matching the web app's palette (primary: #041627, secondary: #944a00, background: #fbf9fa)
10. THE iOS_App SHALL use a color system matching the web app's palette (primary: #041627, secondary: #944a00, background: #fbf9fa)

### Requirement 9: Tab-Based Navigation

**User Story:** As a user, I want simple tab-based navigation between Home, History, and Settings, so that I can easily find the feature I need.

#### Acceptance Criteria

1. THE Android_App SHALL display a bottom navigation bar with three tabs: Home, History, and Settings
2. THE iOS_App SHALL display a bottom tab bar with three tabs: Home, History, and Settings
3. WHEN the user taps a navigation tab, THE Android_App SHALL switch to the corresponding screen without losing state in other tabs
4. WHEN the user taps a navigation tab, THE iOS_App SHALL switch to the corresponding screen without losing state in other tabs
5. THE Android_App SHALL visually indicate the currently active tab using the primary color
6. THE iOS_App SHALL visually indicate the currently active tab using the primary color

### Requirement 10: Recording Status Indicator

**User Story:** As a user, I want a clear visual indicator when recording is active, so that I always know when my conversation is being captured.

#### Acceptance Criteria

1. WHILE recording is active, THE Android_App SHALL display a persistent status banner below the header with an animated recording indicator
2. WHILE recording is active, THE iOS_App SHALL display a persistent status banner below the header with an animated recording indicator
3. THE Android_App SHALL display the text "Recording Active" in the status banner with uppercase styling
4. THE iOS_App SHALL display the text "Recording Active" in the status banner with uppercase styling

### Requirement 11: Microphone Permission Handling

**User Story:** As a user, I want the app to properly request and handle microphone permissions, so that recording works reliably and I understand why the permission is needed.

#### Acceptance Criteria

1. WHEN the user first attempts to start recording, THE Android_App SHALL request the RECORD_AUDIO permission with a rationale explaining its purpose
2. WHEN the user first attempts to start recording, THE iOS_App SHALL trigger the system permission dialog (configured via NSMicrophoneUsageDescription in Info.plist)
3. IF the user denies microphone permission, THEN THE Android_App SHALL display a message explaining that recording requires microphone access and provide guidance to enable it in system settings
4. IF the user denies microphone permission, THEN THE iOS_App SHALL display a message explaining that recording requires microphone access and provide guidance to enable it in system settings
5. WHEN the user first attempts to start recording, THE Android_App SHALL request the speech recognition permission (RECORD_AUDIO covers this on Android)
6. WHEN the user first attempts to start recording, THE iOS_App SHALL request speech recognition permission via SFSpeechRecognizer.requestAuthorization()
