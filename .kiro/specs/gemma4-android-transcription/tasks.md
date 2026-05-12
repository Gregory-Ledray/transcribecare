# Implementation Plan: Gemma 4 On-Device Transcription

## Overview

Replace the cloud-based `SpeechRecognitionConsumer` with on-device transcription using the Gemma 4 E2B model via LiteRT-LM. Implementation proceeds bottom-up: core data structures first, then the engine wrapper, then the consumer, then ViewModel integration, and finally UI updates. Each phase builds on the previous one, with property-based tests validating correctness properties alongside implementation.

## Tasks

- [x] 1. Add LiteRT-LM dependency and ModelState sealed class
  - [x] 1.1 Add the `com.google.ai.edge.litertlm` dependency to `android/app/build.gradle.kts`
    - Add implementation dependency for LiteRT-LM library
    - Sync Gradle to verify resolution
    - _Requirements: 2.1_

  - [x] 1.2 Create `ModelState.kt` sealed class in `android/app/src/main/java/com/transcribecare/app/service/`
    - Define `ModelState` with variants: `Idle`, `Loading`, `Ready`, `Error(message: String)`
    - _Requirements: 1.3, 1.4, 1.5, 10.1, 10.2, 10.3_

- [x] 2. Implement AudioChunkBuffer
  - [x] 2.1 Create `AudioChunkBuffer.kt` in `android/app/src/main/java/com/transcribecare/app/service/`
    - Implement thread-safe bounded buffer with `ReentrantLock`
    - Constructor accepts `sampleRate: Int` and `maxDurationSeconds: Float = 30.0f`
    - Implement `append(frame: ShortArray, frameSize: Int)` — lock-free-ish short critical section, drops oldest samples when capacity exceeded
    - Implement `drain(): ShortArray?` — atomically swaps buffer contents, returns accumulated samples or null if empty
    - Implement `durationSeconds(): Float` — returns current accumulated duration
    - Implement `clear()` and `isEmpty()`
    - Expose `sampleCount` and `maxSamples` properties
    - Max capacity: `sampleRate * maxDurationSeconds` samples (1,323,000 at 44100 Hz × 30s)
    - Log warning when oldest samples are dropped
    - _Requirements: 4.1, 4.4, 4.5, 8.3, 9.1, 9.3_

  - [ ]* 2.2 Write property test: Frame Accumulation Preserves Order
    - **Property 2: Frame Accumulation Preserves Order**
    - **Validates: Requirements 4.1**
    - Create test file at `android/app/src/test/java/com/transcribecare/app/AudioChunkBufferPropertyTest.kt`
    - Generate random sequences of `ShortArray` frames, append all, drain, verify order matches input concatenation

  - [ ]* 2.3 Write property test: Bounded Buffer with Oldest-Drop Policy
    - **Property 5: Bounded Buffer with Oldest-Drop Policy**
    - **Validates: Requirements 4.5, 8.3**
    - Generate frame sequences exceeding max capacity, verify buffer never exceeds `maxSamples` and retained samples are the most recent

  - [ ]* 2.4 Write property test: Non-Blocking Frame Acceptance
    - **Property 4: Non-Blocking Frame Acceptance**
    - **Validates: Requirements 4.4, 5.3, 9.1**
    - Append frames concurrently with drain operations on separate coroutines, verify no frames are lost (up to capacity) and no deadlocks occur within a timeout

- [x] 3. Implement GemmaEngineWrapper
  - [x] 3.1 Create `GemmaEngineWrapper.kt` in `android/app/src/main/java/com/transcribecare/app/service/`
    - Implement as singleton with double-checked locking (`companion object` with `@Volatile instance`)
    - Accept `Context` for model path and cache directory access
    - Define internal `InitState` sealed class: `Uninitialized`, `Initializing`, `Ready`, `Failed(reason: String)`
    - Expose `initState: StateFlow<InitState>`
    - Implement `suspend fun initialize(): Result<Unit>`:
      - Call `ModelFileLoader.loadModel(context)` to get assembled model path
      - Create `EngineConfig` with model path, `Backend.CPU()`, and `context.cacheDir`
      - Call `engine.initialize()` on Dispatchers.IO
      - Create initial `Conversation` instance
      - Update `initState` to `Ready` or `Failed`
    - Implement `suspend fun sendMessage(prompt: String): Result<String>`:
      - Delegate to `conversation.sendMessage(prompt)`
      - Wrap exceptions in `Result.failure`
    - Implement `fun createNewConversation(): Result<Unit>`:
      - Close existing conversation, create new one from engine
    - Implement `fun release()`:
      - Close conversation, release engine, null out instance
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 8.1_

  - [ ]* 3.2 Write unit tests for GemmaEngineWrapper
    - Test singleton behavior (same instance returned)
    - Test `InitState` transitions (Uninitialized → Initializing → Ready)
    - Test `InitState` failure path (Uninitialized → Initializing → Failed)
    - Test `release()` cleans up resources
    - Test `createNewConversation()` success and failure paths
    - Use mock/fake Engine and Conversation
    - _Requirements: 2.1, 2.3, 2.4, 2.6, 8.1_

- [x] 4. Checkpoint - Verify core components compile
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement GemmaTranscriptionConsumer
  - [x] 5.1 Create `GemmaTranscriptionConsumer.kt` in `android/app/src/main/java/com/transcribecare/app/service/`
    - Implement `AudioConsumer` interface
    - Constructor accepts: `engine: GemmaEngineWrapper`, `onPartialResult: (String) -> Unit`, `onFinalResult: (String) -> Unit`, `onError: (String) -> Unit`, `chunkDurationSeconds: Float = 3.0f`, `coroutineScope: CoroutineScope`
    - `prepare()`: Initialize `AudioChunkBuffer` with provided sample rate, set `isActive = true`
    - `onAudioFrame()`: Append frame to buffer (non-blocking), check if `buffer.durationSeconds() >= chunkDurationSeconds`, if so launch inference coroutine. Invoke `onPartialResult` with accumulation status (e.g., "Listening..." or accumulated duration)
    - Inference coroutine: Drain buffer, encode as prompt, call `engine.sendMessage()` on Dispatchers.IO, deliver result via `onFinalResult`, then clear partial with `onPartialResult("")`
    - Enforce mutual exclusion: use `AtomicBoolean` or `Mutex` to ensure only one inference runs at a time; queue/skip if busy
    - `release()`: Set `isActive = false`, flush remaining buffer if non-empty (submit final chunk), clear buffer, cancel scope
    - Implement conversation recovery: on `sendMessage()` failure, call `engine.createNewConversation()`, retry once, if still fails invoke `onError` and cease inference
    - Invoke `onPartialResult("Transcribing...")` when submitting a chunk
    - _Requirements: 3.1, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 7.1, 7.2, 7.3, 7.4, 9.1, 9.2, 9.3, 9.4_

  - [ ]* 5.2 Write property test: Chunk Submission Triggers at Threshold
    - **Property 3: Chunk Submission Triggers at Threshold**
    - **Validates: Requirements 4.2**
    - Create test file at `android/app/src/test/java/com/transcribecare/app/GemmaTranscriptionConsumerPropertyTest.kt`
    - Use a mock engine, generate frame sequences crossing the threshold, verify exactly one inference submission occurs at the crossing point

  - [ ]* 5.3 Write property test: Non-Empty Inference Response Delivers Final Result
    - **Property 6: Non-Empty Inference Response Delivers Final Result**
    - **Validates: Requirements 5.2**
    - Mock engine returns random non-empty strings, verify `onFinalResult` is invoked exactly once with that string

  - [ ]* 5.4 Write property test: Partial Result Reflects Accumulation Progress
    - **Property 7: Partial Result Reflects Accumulation Progress**
    - **Validates: Requirements 6.1**
    - Generate sub-threshold frame sequences, verify `onPartialResult` is invoked with a non-empty status string reflecting accumulation

  - [ ]* 5.5 Write property test: Final Result Clears Partial Result
    - **Property 8: Final Result Clears Partial Result**
    - **Validates: Requirements 6.3**
    - After inference completes with a result, verify `onPartialResult("")` is called after `onFinalResult`

  - [ ]* 5.6 Write property test: Error Resilience — Inference Failure Does Not Halt Processing
    - **Property 9: Error Resilience — Inference Failure Does Not Halt Processing**
    - **Validates: Requirements 7.1**
    - Mock engine throws random exceptions, verify `onError` is invoked AND consumer continues accepting subsequent frames

  - [ ]* 5.7 Write property test: Mutual Exclusion — Single Concurrent Inference
    - **Property 10: Mutual Exclusion — Single Concurrent Inference**
    - **Validates: Requirements 9.4**
    - Submit rapid chunk sequences, verify at most one `sendMessage()` call is in progress at any time (use atomic counter)

- [x] 6. Checkpoint - Verify consumer and property tests
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Integrate into HomeViewModel
  - [x] 7.1 Add `ModelState` StateFlow and engine initialization to `HomeViewModel`
    - Add `private val _modelState = MutableStateFlow<ModelState>(ModelState.Idle)` and public `modelState: StateFlow<ModelState>`
    - Add `init { initializeEngine() }` block
    - Implement `private fun initializeEngine()`: set `ModelState.Loading`, launch on `Dispatchers.IO`, call `ModelFileLoader.loadModel()`, then `GemmaEngineWrapper.getInstance(context).initialize()`, update `_modelState` to `Ready` or `Error` on `Dispatchers.Main`
    - Verify free storage ≥ `AudioConfig.MIN_STORAGE_BYTES` before assembly
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 8.5, 9.5_

  - [x] 7.2 Replace `SpeechRecognitionConsumer` with `GemmaTranscriptionConsumer` in `startRecording()`
    - Guard: if `_modelState.value != ModelState.Ready`, set error and return early
    - Remove `SpeechRecognitionConsumer` creation
    - Create `GemmaTranscriptionConsumer` with engine instance, callbacks (`onInterimResult`, `onFinalResult`, `onError`), and `viewModelScope`
    - Register both `GemmaTranscriptionConsumer` and `FileRecordingConsumer` with `UnifiedAudioCaptureService`
    - Update `stopRecording()` to reference new consumer type
    - _Requirements: 3.1, 3.2, 3.5, 5.5, 11.1, 11.2, 11.3_

  - [x] 7.3 Update `onCleared()` to release engine resources
    - Call `GemmaEngineWrapper.getInstance(context).release()` when ViewModel is cleared
    - _Requirements: 2.6, 8.2_

  - [ ]* 7.4 Write unit tests for HomeViewModel model integration
    - Test `startRecording()` guard when `ModelState` is not `Ready`
    - Test `initializeEngine()` transitions `ModelState` correctly (Loading → Ready, Loading → Error)
    - Test both consumers are registered on `startRecording()`
    - Test `onCleared()` releases engine
    - _Requirements: 1.3, 1.4, 1.5, 3.1, 3.2, 8.2_

- [x] 8. Update UI for model loading state
  - [x] 8.1 Add model loading indicator to `HomeScreen.kt`
    - Collect `modelState` from `HomeViewModel` using `collectAsStateWithLifecycle()`
    - When `ModelState.Loading`: show `CircularProgressIndicator` with `contentDescription = "Preparing transcription model"` and minimum 48x48dp size
    - When `ModelState.Error`: show error text with sufficient contrast (4.5:1), announce via `LocalAccessibilityManager`
    - When `ModelState.Ready`: announce "Transcription model ready" via accessibility announcement
    - Disable "Start Recording" button when `ModelState` is not `Ready`
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [ ]* 8.2 Write unit tests for UI state rendering
    - Test loading indicator is displayed when `ModelState.Loading`
    - Test error message is displayed when `ModelState.Error`
    - Test start button is disabled when model not ready
    - _Requirements: 10.1, 10.2, 10.3_

- [x] 9. Model Assembly idempotence verification
  - [ ]* 9.1 Write property test: Model Assembly Idempotence
    - **Property 1: Model Assembly Idempotence**
    - **Validates: Requirements 1.2**
    - Create test file at `android/app/src/test/java/com/transcribecare/app/ModelFileLoaderPropertyTest.kt`
    - Use a mock filesystem/context, generate random file sizes > 0, verify calling `loadModel()` when file exists returns same path without modification

- [x] 10. Remove unused SpeechRecognition imports and clean up
  - [x] 10.1 Remove `SpeechRecognitionConsumer` import from `HomeViewModel.kt`
    - Remove the import statement for `SpeechRecognitionConsumer`
    - Remove the `speechConsumer` field reference
    - Verify no remaining references to the old cloud-based consumer in the ViewModel
    - _Requirements: 3.5_

  - [x] 10.2 Update `SpeechRecognitionConsumer.kt` with deprecation notice
    - Add `@Deprecated` annotation indicating replacement by `GemmaTranscriptionConsumer`
    - Keep file for reference but mark as unused
    - _Requirements: 3.5_

- [x] 11. Final checkpoint - Full test suite verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation between major phases
- Property tests validate the 10 universal correctness properties from the design document
- Unit tests validate specific examples, state transitions, and edge cases
- The LiteRT-LM SDK API surface (Engine, EngineConfig, Conversation, Backend) will need to be confirmed against the actual library documentation during implementation of task 3.1
- All new files go in `android/app/src/main/java/com/transcribecare/app/service/` (service layer) consistent with existing project structure
- Tests go in `android/app/src/test/java/com/transcribecare/app/` using Kotest property testing and JUnit 5 platform
