# Requirements Document

## Introduction

This feature replaces the current Web Speech API-based transcription in the TranscribeCare web app with a local Gemma 4 open source E2B (Edge-to-Browser) model running via llama.cpp's WebAssembly build. This enables fully offline, privacy-preserving audio transcription directly in the browser without relying on cloud services or browser-specific speech recognition APIs.

## Glossary

- **Transcription_Engine**: The module responsible for converting audio input into text output using the Gemma 4 model via llama.cpp WASM
- **Model_Loader**: The component that downloads, caches, and initializes the Gemma 4 GGUF model weights in the browser
- **WASM_Runtime**: The llama.cpp WebAssembly runtime that performs model inference in the browser
- **Audio_Processor**: The component that captures raw audio from the microphone and converts it into the format required by the Transcription_Engine
- **Model_Cache**: The browser-based storage (IndexedDB/Cache API) used to persist downloaded model weights across sessions
- **Transcript_Segment**: A discrete unit of transcribed text produced by the Transcription_Engine, containing an id, text content, and type classification

## Requirements

### Requirement 1: Model Loading and Initialization

**User Story:** As a patient, I want the transcription model to load and initialize in my browser, so that I can transcribe medical conversations without an internet connection after the initial download.

#### Acceptance Criteria

1. WHEN the user navigates to the web app for the first time, THE Model_Loader SHALL download the Gemma 4 GGUF model weights and store them in the Model_Cache
2. WHEN the model weights exist in the Model_Cache, THE Model_Loader SHALL load the model from the Model_Cache without re-downloading
3. WHILE the model is downloading, THE Model_Loader SHALL display a progress indicator showing the percentage of bytes downloaded
4. WHEN the model download completes, THE Model_Loader SHALL initialize the WASM_Runtime with the loaded model weights
5. IF the model download fails due to a network error, THEN THE Model_Loader SHALL display an error message and provide a retry option
6. IF the Model_Cache storage quota is exceeded, THEN THE Model_Loader SHALL inform the user that insufficient storage is available and suggest clearing browser data

### Requirement 2: Audio Capture and Preprocessing

**User Story:** As a patient, I want the app to capture my microphone audio and prepare it for the local model, so that I get accurate transcriptions of my medical conversations.

#### Acceptance Criteria

1. WHEN the user starts a recording session, THE Audio_Processor SHALL capture audio from the microphone using the MediaRecorder API
2. THE Audio_Processor SHALL convert captured audio into 16kHz mono PCM float32 format for the Transcription_Engine
3. WHILE a recording session is active, THE Audio_Processor SHALL buffer audio in chunks suitable for incremental transcription processing
4. IF the microphone permission is denied, THEN THE Audio_Processor SHALL display an accessible error message explaining how to grant permission

### Requirement 3: Local Transcription via llama.cpp WASM

**User Story:** As a patient, I want my speech to be transcribed locally in the browser using the Gemma 4 model, so that my medical conversations remain private and do not leave my device.

#### Acceptance Criteria

1. WHEN an audio chunk is available from the Audio_Processor, THE Transcription_Engine SHALL process the audio through the Gemma 4 model via the WASM_Runtime and produce text output
2. THE Transcription_Engine SHALL run inference in a Web Worker to avoid blocking the main UI thread
3. WHILE a recording session is active, THE Transcription_Engine SHALL produce interim transcription results as audio chunks are processed
4. WHEN the Transcription_Engine produces a finalized transcript segment, THE Transcription_Engine SHALL emit a Transcript_Segment with type "current"
5. IF the WASM_Runtime encounters an out-of-memory error, THEN THE Transcription_Engine SHALL notify the user that the device has insufficient memory for local transcription

### Requirement 4: Replace Web Speech API Integration

**User Story:** As a developer, I want the Gemma 4 local transcription to replace the Web Speech API, so that the app uses a single consistent transcription backend that works across all browsers.

#### Acceptance Criteria

1. THE Transcription_Engine SHALL produce Transcript_Segment objects compatible with the existing TranscriptView component interface (id, text, type fields)
2. WHEN a recording session starts, THE Transcription_Engine SHALL begin producing transcript segments in the same sequence as the previous Web Speech API implementation (interim text followed by finalized segments)
3. WHEN a recording session stops, THE Transcription_Engine SHALL finalize any remaining buffered audio and produce a final Transcript_Segment
4. THE Transcription_Engine SHALL support the existing recording session lifecycle: start, produce segments, stop, and save to session history

### Requirement 5: Transcription Performance

**User Story:** As a patient, I want the transcription to appear with minimal delay, so that I can follow along with the conversation in near real-time.

#### Acceptance Criteria

1. THE Transcription_Engine SHALL produce interim transcription results within 3 seconds of audio being spoken
2. WHILE a recording session is active, THE Transcription_Engine SHALL maintain continuous transcription without dropping audio chunks
3. THE WASM_Runtime SHALL utilize WebAssembly SIMD instructions when the browser supports them, to improve inference speed

### Requirement 6: Model Readiness State Management

**User Story:** As a patient, I want to know when the model is ready before I start recording, so that I do not miss the beginning of my medical conversation.

#### Acceptance Criteria

1. WHILE the model is loading or initializing, THE Transcription_Engine SHALL disable the "Start Recording" button
2. WHEN the model is fully loaded and initialized, THE Transcription_Engine SHALL enable the "Start Recording" button
3. THE Model_Loader SHALL display the current model status (downloading, initializing, ready, error) in an accessible status indicator
4. WHEN the model transitions to the "ready" state, THE Transcription_Engine SHALL announce the state change to assistive technologies via a live region

### Requirement 7: Offline Capability

**User Story:** As a patient, I want to use the transcription feature without an internet connection after the initial model download, so that I can rely on it during medical visits regardless of connectivity.

#### Acceptance Criteria

1. WHEN the model weights are present in the Model_Cache, THE Transcription_Engine SHALL function without any network connectivity
2. WHILE the device is offline and the model is cached, THE Model_Loader SHALL load and initialize the model from the Model_Cache
3. IF the device is offline and the model is not cached, THEN THE Model_Loader SHALL display a message indicating that an internet connection is required for the initial model download

### Requirement 8: Audio Recording Preservation

**User Story:** As a patient, I want my audio recordings to continue being saved alongside transcriptions, so that I can play them back later for review.

#### Acceptance Criteria

1. WHILE a recording session is active, THE Audio_Processor SHALL simultaneously feed audio to the Transcription_Engine and record the full audio stream via MediaRecorder
2. WHEN a recording session stops, THE Audio_Processor SHALL produce an audio blob that is saved to the session history alongside the transcript segments
3. THE Audio_Processor SHALL maintain the existing audio format (WebM) for playback compatibility with the AudioPlayer component
