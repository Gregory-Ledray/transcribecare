# Implementation Plan: Gemma 4 Local Transcription

## Overview

Replace the Web Speech API-based transcription in the TranscribeCare web app with a fully local, on-device speech-to-text pipeline powered by Google's Gemma 4 E2B model running via LiteRT.js. The implementation introduces a Model Loader, Audio Processor (AudioWorklet + ring buffer), Inference Worker (Web Worker), and a Transcription Engine hook that integrates with the existing App.tsx UI.

## Tasks

- [x] 1. Set up project dependencies and test infrastructure
  - [x] 1.1 Install runtime and dev dependencies
    - Add `@litertjs/core` to dependencies
    - Add `vitest`, `@vitest/coverage-v8`, and `fast-check` to devDependencies
    - Add `jsdom` to devDependencies for DOM testing
    - Add test scripts to package.json (`"test": "vitest --run"`, `"test:watch": "vitest"`)
    - Create `vitest.config.ts` with jsdom environment
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 1.2 Create directory structure and type definitions
    - Create `web/src/transcription/` directory for all new modules
    - Create `web/src/transcription/types.ts` with shared interfaces: `ModelLoaderConfig`, `ModelLoadProgress`, `AudioProcessorConfig`, `AudioChunkMessage`, `WorkerInMessage`, `WorkerOutMessage`, `ModelCacheMetadata`, `TranscriptionState`, `UseLocalTranscriptionReturn`
    - Ensure `TranscriptSegment` interface remains in App.tsx and is exported/importable
    - _Requirements: 4.3, 8.4_

- [x] 2. Implement Model Loader with caching
  - [x] 2.1 Implement core model loader functions
    - Create `web/src/transcription/modelLoader.ts`
    - Implement `computeProgress(received: number, total: number): number` that returns bounded 0–100 percentage
    - Implement `computeSha256(data: Uint8Array): Promise<string>` using Web Crypto API
    - Implement `validateIntegrity(data: Uint8Array, expectedHash: string): Promise<boolean>`
    - Implement `loadModel(config: ModelLoaderConfig): Promise<Uint8Array>` with fetch + progress reporting via callback
    - Implement cache-first strategy: check Cache API → validate integrity → return cached or re-download
    - Store `ModelCacheMetadata` (version, sha256, downloadedAt, sizeBytes) as a JSON response in the same cache namespace
    - Handle network errors with descriptive messages and expose retry capability
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.3, 6.4_

  - [ ]* 2.2 Write property test: Progress percentage is always bounded
    - **Property 1: Progress percentage is always bounded**
    - Use `fc.nat()` for total and received values
    - Assert `computeProgress(received, total)` always returns a value in [0, 100]
    - **Validates: Requirements 1.2**

  - [ ]* 2.3 Write property test: Cache integrity validation round-trip
    - **Property 2: Cache integrity validation round-trip**
    - Use `fc.uint8Array()` to generate arbitrary model byte arrays
    - Assert computing SHA-256 then validating against same bytes returns true
    - Assert mutating any single byte causes validation to return false
    - **Validates: Requirements 1.5**

  - [ ]* 2.4 Write unit tests for model loader
    - Mock `fetch` and Cache API
    - Test successful download flow with progress callbacks
    - Test cache hit path (no network request)
    - Test cache miss with re-download
    - Test version mismatch triggers re-download
    - Test network failure produces descriptive error
    - Test integrity check failure deletes corrupt cache entry
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.3, 6.4_

- [x] 3. Implement Audio Processor with AudioWorklet and ring buffer
  - [x] 3.1 Implement ring buffer
    - Create `web/src/transcription/ringBuffer.ts`
    - Implement a fixed-capacity ring buffer for Float32Array samples
    - Support `write(samples: Float32Array): void` — overwrites oldest data when full
    - Support `read(count: number): Float32Array` — reads and advances read pointer
    - Support `availableSamples(): number` — returns number of unread samples
    - Enforce maximum size configured at construction (bounded memory)
    - _Requirements: 2.5, 9.4_

  - [ ]* 3.2 Write property test: Ring buffer is bounded and lossless
    - **Property 5: Ring buffer is bounded and lossless**
    - Use `fc.array(fc.float32Array())` to generate write sequences
    - Assert total samples read equals total samples written when writes don't exceed capacity
    - Assert internal storage never exceeds configured maximum size
    - **Validates: Requirements 2.5, 9.4**

  - [x] 3.3 Implement AudioWorklet processor
    - Create `web/src/transcription/audioWorklet.ts` (AudioWorkletProcessor subclass)
    - Receive raw PCM frames in `process()` method
    - Resample from device sample rate to 16kHz mono using linear interpolation
    - Write resampled samples into ring buffer
    - When buffer reaches `chunkDurationMs` worth of samples, post chunk to main thread via `MessagePort`
    - _Requirements: 2.1, 2.2, 2.3, 2.5_

  - [x] 3.4 Implement audio processor handle
    - Create `web/src/transcription/audioProcessor.ts`
    - Implement `createAudioProcessor(config: AudioProcessorConfig): AudioProcessorHandle`
    - `start()`: call `getUserMedia({ audio: true })`, create `AudioContext`, register AudioWorklet, connect nodes
    - `stop()`: disconnect nodes, close AudioContext, stop media tracks
    - `onChunk` callback invoked when AudioWorklet posts a chunk
    - Handle microphone permission denied with accessible error message
    - _Requirements: 2.1, 2.4, 2.5_

  - [ ]* 3.5 Write property test: Audio resampling produces correct output length
    - **Property 3: Audio resampling produces correct output length**
    - Use `fc.float32Array()` for input buffers and `fc.constantFrom(44100, 48000)` for source sample rates
    - Assert output length equals `floor(N × 16000 / S)`
    - Assert all output values are in [-1.0, 1.0]
    - **Validates: Requirements 2.2**

  - [ ]* 3.6 Write property test: Audio chunking produces fixed-size chunks
    - **Property 4: Audio chunking produces fixed-size chunks**
    - Use `fc.float32Array()` for stream data and `fc.integer({min: 1600, max: 48000})` for chunk size
    - Assert `floor(L / C)` complete chunks emitted, each of exactly C samples
    - Assert no samples lost or duplicated
    - **Validates: Requirements 2.3**

- [x] 4. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Inference Worker
  - [x] 5.1 Implement the Web Worker script
    - Create `web/src/transcription/inferenceWorker.ts`
    - Handle `'init'` message: call model loader, report progress, initialize LiteRT runtime
    - Implement acceleration fallback: attempt WebGPU → WASM SIMD → standard WASM
    - Report active backend via `'backend'` message
    - Handle `'infer'` message: run inference on received Float32Array chunk, post `'partial'` or `'final'` result
    - Handle `'finalize'` message: flush any pending inference state, emit final text
    - Handle `'release'` message: dispose model and free memory
    - Catch inference errors, post `'error'` message, continue processing subsequent chunks
    - _Requirements: 3.1, 3.4, 3.5, 5.1, 5.2, 5.3, 5.4, 5.5, 9.1, 9.2_

  - [ ]* 5.2 Write unit tests for inference worker message handling
    - Test `'init'` message triggers model loading and reports progress
    - Test acceleration fallback chain (WebGPU unavailable → WASM SIMD → WASM)
    - Test `'infer'` message returns partial/final text
    - Test `'error'` message on inference failure does not crash worker
    - Test `'release'` message frees resources
    - _Requirements: 3.5, 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 6. Implement Transcription Engine Hook
  - [x] 6.1 Implement `useLocalTranscription` hook
    - Create `web/src/transcription/useLocalTranscription.ts`
    - Manage state: `status`, `loadProgress`, `segments`, `interimText`, `backend`, `error`
    - Implement `initialize()`: spawn Web Worker, send `'init'` message, track progress
    - Implement `start()`: create audio processor, begin feeding chunks to worker via `postMessage` (transferable)
    - Implement `stop()`: send `'finalize'` to worker, stop audio processor, convert pending interimText to final segment, release resources within 1 second
    - Implement `retry()`: reset error state, re-attempt initialization
    - Handle worker messages: update `interimText` on `'partial'`, append `TranscriptSegment` on `'final'`, promote previous `'current'` to `'recent'`
    - On error messages from worker: log error, remain in `'recording'` state, continue processing
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 8.1, 8.2, 8.3, 8.4, 9.3_

  - [ ]* 6.2 Write property test: Segment state machine correctness
    - **Property 6: Segment state machine correctness**
    - Use `fc.array(fc.oneof(partialMsg, finalMsg))` to generate message sequences
    - Assert: (a) interimText updates to latest partial, (b) new TranscriptSegment appended on final with type 'current', (c) previous 'current' promoted to 'recent', (d) interimText cleared on final
    - **Validates: Requirements 3.2, 3.3, 4.4, 4.5, 8.4**

  - [ ]* 6.3 Write property test: Error resilience preserves recording state
    - **Property 7: Error resilience preserves recording state**
    - Use `fc.array(fc.oneof(partialMsg, finalMsg, errorMsg))` to generate message sequences with errors
    - Assert engine remains in 'recording' status after error messages
    - Assert subsequent partial/final messages are still processed correctly
    - **Validates: Requirements 3.5**

  - [ ]* 6.4 Write property test: Stop finalization captures pending interim text
    - **Property 8: Stop finalization captures pending interim text**
    - Use `fc.string({minLength: 1})` for non-empty interim text
    - Assert calling stop with non-empty interimText produces a finalized TranscriptSegment
    - Assert interimText is cleared to empty string after stop
    - **Validates: Requirements 4.2**

- [x] 7. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Integrate with App.tsx and remove Web Speech API
  - [x] 8.1 Replace Web Speech API with useLocalTranscription hook
    - Remove `SpeechRecognition` initialization useEffect block from App.tsx
    - Remove `recognitionRef` and all references to it
    - Add `const transcription = useLocalTranscription();` in the App component
    - Call `transcription.initialize()` on mount (useEffect)
    - Update `handleToggleRecording`: call `transcription.start()` on start, `transcription.stop()` on stop
    - Wire `transcription.segments` and `transcription.interimText` to `TranscriptView`
    - Maintain existing `MediaRecorder` logic for audio playback recording (unchanged)
    - Ensure `TranscriptSegment` format (id, text, type) is preserved for session storage
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 8.1, 8.2, 8.3, 8.4_

  - [x] 8.2 Implement ModelLoadingOverlay component
    - Create `ModelLoadingOverlay` component in App.tsx (following single-file architecture)
    - Display progress bar with percentage text during model download
    - Use `aria-live="polite"` region to announce progress updates to screen readers
    - Announce readiness via `aria-live` when model finishes loading
    - On error: display error message with retry button, announce via `role="alert"`
    - Ensure all text meets WCAG 2.1 AA contrast (4.5:1 for text, 3:1 for interactive elements)
    - Show overlay when `transcription.status === 'loading'`
    - Show error state when `transcription.status === 'error'`
    - Hide overlay when `transcription.status === 'ready'` or `'recording'`
    - _Requirements: 1.2, 1.3, 7.1, 7.2, 7.3, 7.4_

  - [ ]* 8.3 Write unit tests for ModelLoadingOverlay
    - Test progress bar renders correct percentage
    - Test aria-live region contains progress text
    - Test error state renders retry button with role="alert"
    - Test overlay hides when status is 'ready'
    - Verify ARIA attributes are correctly applied
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [x] 9. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 10. Write integration tests
  - [ ]* 10.1 Write integration test for end-to-end recording flow
    - Mock LiteRT runtime and Web Worker
    - Test start recording → audio chunks processed → segments appear → stop recording → segments finalized
    - Verify session storage receives correct TranscriptSegment array
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 10.2 Write integration test for offline model loading
    - Pre-populate Cache API with model artifact and metadata
    - Test model loads from cache without network requests
    - Test app functions correctly in offline mode after cache is populated
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ]* 10.3 Write integration test for acceleration fallback
    - Mock WebGPU as unavailable
    - Verify fallback to WASM SIMD, then to standard WASM
    - Verify backend is correctly reported
    - _Requirements: 5.1, 5.2, 5.3, 5.5_

- [x] 11. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate the 8 universal correctness properties defined in the design document
- Unit tests validate specific examples and edge cases
- The existing `MediaRecorder` audio capture for playback remains unchanged
- All new modules go in `web/src/transcription/` to keep the codebase organized
- The `ModelLoadingOverlay` component lives in App.tsx per the single-file architecture convention
