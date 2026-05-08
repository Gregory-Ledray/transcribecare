# Implementation Plan: Gemma 4 Local Transcription

## Overview

Replace the Web Speech API transcription in the TranscribeCare web app with a local Gemma 4 E2B model running via llama.cpp's WebAssembly build (wllama). Implementation proceeds bottom-up: audio pipeline first, then inference worker, then orchestration engine, then UI integration.

## Tasks

- [x] 1. Set up project infrastructure and dependencies
  - [x] 1.1 Install dependencies and configure Vite for Web Workers and AudioWorklets
    - Install `@nicepkg/wllama` (wllama npm package), `fast-check`, and `vitest` with `@vitest/browser` or `jsdom` environment
    - Install `fake-indexeddb` as a dev dependency for testing
    - Update `vite.config.ts` to handle `.worker.ts` files and AudioWorklet module resolution
    - Add `"test": "vitest --run"` script to `package.json`
    - _Requirements: 3.2_

  - [x] 1.2 Define shared TypeScript interfaces and types
    - Create `web/src/transcription/types.ts` with all shared interfaces: `ModelStatus`, `ModelProgress`, `ModelLoaderConfig`, `AudioProcessorConfig`, `AudioChunk`, `TranscriptSegment`, `TranscriptionEngineConfig`, `WorkerInMessage`, `WorkerOutMessage`
    - Ensure `TranscriptSegment` matches the existing interface in App.tsx (id, text, type)
    - _Requirements: 4.1_

- [x] 2. Implement AudioResamplerWorklet
  - [x] 2.1 Create the AudioWorkletProcessor for real-time resampling
    - Create `web/src/transcription/audio-resampler-worklet.ts`
    - Implement `AudioResamplerProcessor` extending `AudioWorkletProcessor`
    - Accept `targetSampleRate` and `chunkSize` as processor options
    - Perform linear interpolation resampling from native sample rate to 16kHz mono
    - Buffer resampled samples and post `Float32Array` chunks via the port when buffer reaches `chunkSize`
    - Handle stereo-to-mono downmix (average channels)
    - _Requirements: 2.2, 2.3_

  - [ ]* 2.2 Write property test: Audio resampling produces correct output format
    - **Property 1: Audio resampling produces correct output format**
    - Generate random Float32Arrays at sample rates between 8kHz–96kHz with 1 or 2 channels
    - Verify output is mono, Float32, and sample count equals `inputSamples * (16000 / inputSampleRate)` within ±1
    - **Validates: Requirements 2.2**

  - [ ]* 2.3 Write property test: Chunk buffering preserves all audio samples
    - **Property 2: Chunk buffering preserves all audio samples**
    - Generate random-length sample streams, feed through chunking logic
    - Verify all chunks are exactly the configured size (except final), and concatenation equals original input
    - **Validates: Requirements 2.3**

- [x] 3. Implement AudioProcessor service
  - [x] 3.1 Create the AudioProcessor module
    - Create `web/src/transcription/audio-processor.ts`
    - Implement `AudioProcessor` class with `start()`, `stop()`, and `onChunk()` methods
    - On `start()`: call `getUserMedia({ audio: true })`, create `AudioContext`, register and connect `AudioResamplerWorklet`, start `MediaRecorder` for WebM capture
    - On `stop()`: disconnect worklet, stop MediaRecorder, return `{ audioBlob, chunks }`
    - Route worklet MessagePort messages to the `onChunk` callback
    - Handle `NotAllowedError` (permission denied) with accessible error message
    - Handle `NotFoundError` (no microphone) with appropriate message
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 8.1, 8.2, 8.3_

  - [ ]* 3.2 Write unit tests for AudioProcessor
    - Test that `start()` requests microphone permission
    - Test that `stop()` produces a WebM audio blob
    - Test error handling for permission denied and no microphone scenarios
    - _Requirements: 2.1, 2.4, 8.3_

- [x] 4. Implement InferenceWorker with wllama
  - [x] 4.1 Create the Web Worker hosting the wllama instance
    - Create `web/src/transcription/inference-worker.ts`
    - Handle `WorkerInMessage` types: `init`, `transcribe`, `finalize`
    - On `init`: instantiate wllama, download/cache model via wllama's built-in caching, post `model-progress` messages with download percentage and status transitions
    - On `transcribe`: run wllama inference on the audio chunk, post `transcript` message with resulting `TranscriptSegment`
    - On `finalize`: process any remaining buffered audio, post final `transcript` segment
    - Post `error` messages for OOM, network failures, and initialization errors
    - Configure wllama to use SIMD when available
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.2, 5.3_

  - [x] 4.2 Implement ModelLoader logic within the worker
    - Implement model download with progress reporting via wllama's `progressCallback`
    - Implement cache detection using wllama's `isModelCached()` or equivalent
    - Handle `QuotaExceededError` from IndexedDB and post appropriate error message
    - Implement retry logic with exponential backoff (max 3 attempts) for transient network errors
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 7.1, 7.2, 7.3_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement TranscriptionEngine orchestrator
  - [x] 6.1 Create the TranscriptionEngine class
    - Create `web/src/transcription/transcription-engine.ts`
    - Implement `TranscriptionEngine` class with `loadModel()`, `startSession()`, `stopSession()`, `onSegment()`, `onModelStatus()`, `getModelStatus()` methods
    - `loadModel()`: spawn InferenceWorker, send `init` message, relay `model-progress` messages to subscribers
    - `startSession()`: create AudioProcessor, start capture, forward audio chunks to worker via `transcribe` messages
    - `stopSession()`: stop AudioProcessor, send `finalize` to worker, wait for final segment, return `{ audioBlob, segments }`
    - Track all emitted segments during a session
    - Generate unique IDs (timestamp-based) for each TranscriptSegment
    - _Requirements: 3.1, 3.3, 3.4, 4.2, 4.3, 4.4, 5.1, 5.2_

  - [ ]* 6.2 Write property test: All submitted audio chunks are processed without loss
    - **Property 3: All submitted audio chunks are processed without loss**
    - Generate random sequences of N audio chunks, submit to TranscriptionEngine with mock worker
    - Verify all N chunks are forwarded to the worker and each produces at least one TranscriptSegment
    - **Validates: Requirements 3.3, 5.2**

  - [ ]* 6.3 Write property test: Produced TranscriptSegments have valid structure
    - **Property 4: Produced TranscriptSegments have valid structure**
    - Generate random inference outputs from mock worker
    - Verify every emitted TranscriptSegment has non-empty `id`, non-empty `text`, and `type === 'current'`
    - **Validates: Requirements 3.4, 4.1**

  - [ ]* 6.4 Write property test: Session stop finalizes all buffered audio
    - **Property 5: Session stop finalizes all buffered audio**
    - Generate random buffer states (non-empty remaining audio) at stop time
    - Verify `stopSession()` always produces at least one final TranscriptSegment from remaining buffer
    - **Validates: Requirements 4.3**

- [x] 7. Implement ModelStatusIndicator UI component
  - [x] 7.1 Create the ModelStatusIndicator React component
    - Create `web/src/transcription/model-status-indicator.tsx`
    - Render progress bar with percentage during `downloading` state
    - Render spinner during `initializing` state
    - Render success checkmark when `ready`
    - Render error message with retry button when `error`
    - Use `aria-live="polite"` region for screen reader announcements on state changes
    - Use Tailwind CSS for styling, lucide-react for icons
    - Ensure minimum touch target of 48x48px for retry button
    - _Requirements: 1.3, 1.5, 6.3, 6.4_

  - [ ]* 7.2 Write unit tests for ModelStatusIndicator
    - Test rendering for each ModelStatus state (downloading, initializing, ready, error)
    - Test that retry button calls `onRetry` callback
    - Test that aria-live region content updates on state transitions
    - _Requirements: 1.3, 1.5, 6.3, 6.4_

- [x] 8. Integrate TranscriptionEngine into App.tsx
  - [x] 8.1 Replace Web Speech API with TranscriptionEngine
    - Remove the `SpeechRecognition` initialization and event handlers from App.tsx
    - Instantiate `TranscriptionEngine` and call `loadModel()` on component mount
    - Subscribe to `onModelStatus()` to track model readiness
    - Update `handleToggleRecording`: on start, call `startSession()`; on stop, call `stopSession()` and save session
    - Subscribe to `onSegment()` to update `segments` state (replacing the `recognition.onresult` handler)
    - Maintain the existing segment type transitions: new segments as `'current'`, previous segments transition to `'recent'` then `'past'`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 8.2 Integrate ModelStatusIndicator and recording button state
    - Add `ModelStatusIndicator` component above the recording controls
    - Disable "Start Recording" button when `getModelStatus() !== 'ready'`
    - Enable "Start Recording" button when model transitions to `'ready'`
    - Ensure the existing `Controls` component respects the model readiness state
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 8.3 Preserve audio recording and session save flow
    - Ensure `stopSession()` returns the WebM audio blob from MediaRecorder
    - Save audio blob to IndexedDB using existing `saveAudio()` function
    - Save session metadata and segments using existing `saveSession()` function
    - Verify AudioPlayer playback still works with saved WebM blobs
    - _Requirements: 8.1, 8.2, 8.3_

- [x] 9. Implement offline capability and error states
  - [x] 9.1 Handle offline model loading
    - On app load, check if model is cached via worker `isModelCached` query
    - If cached and offline: load from cache, proceed normally
    - If not cached and offline: display message indicating internet required for initial download
    - Use `navigator.onLine` and `online`/`offline` events for connectivity detection
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 9.2 Handle runtime errors gracefully
    - Display OOM error message when worker reports memory exhaustion
    - On worker crash (`onerror`): attempt restart, show error after repeated failures
    - Ensure MediaRecorder continues capturing even if inference fails (recording preservation)
    - Implement inference timeout (10s per chunk): skip chunk and continue
    - _Requirements: 1.5, 1.6, 3.5, 5.2_

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Final integration and wiring validation
  - [x] 11.1 End-to-end wiring verification
    - Verify full flow: model load → start recording → audio capture → inference → segments displayed → stop → session saved
    - Verify existing session history, search, and playback features still work
    - Verify WhatsApp/text message mock integrations still fire on session stop
    - Remove any remaining Web Speech API references and dead code
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 8.1, 8.2, 8.3_

  - [ ]* 11.2 Write integration tests
    - Test full pipeline with mock wllama: microphone → worklet → worker → segments
    - Test offline mode: cached model loads without network
    - Test session persistence: audio blob + segments saved and retrievable from IndexedDB
    - _Requirements: 4.4, 7.1, 8.2_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using fast-check
- Unit tests validate specific examples and edge cases
- The wllama library handles WASM compilation, Web Worker isolation, and model caching internally — tasks focus on integration rather than low-level WASM management
- All code lives under `web/src/transcription/` to keep the feature modular
