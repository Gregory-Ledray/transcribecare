# Requirements Document

## Introduction

This feature replaces the cloud-based Android `SpeechRecognizer` with on-device transcription powered by the Gemma 4 E2B model via the LiteRT-LM library. The model is pre-packaged as 25 split asset files, assembled at runtime into a single model file, and loaded into a LiteRT-LM `Engine`. A new `AudioConsumer` implementation receives raw PCM frames from the existing `UnifiedAudioCaptureService` and feeds them to the model for local inference, producing partial and final transcript results without network connectivity.

## Glossary

- **Model_Assembler**: The existing `ModelFileLoader` object responsible for concatenating split asset files (.aa through .ay) into a single model file in internal storage.
- **LiteRT_Engine**: The `com.google.ai.edge.litertlm` `Engine` instance that loads the assembled model and provides inference capabilities.
- **Engine_Config**: The `EngineConfig` data class specifying model path, backend, and cache directory for LiteRT_Engine initialization.
- **Conversation**: A stateful LiteRT-LM session created from LiteRT_Engine, used to send prompts and receive generated text responses.
- **Gemma_Consumer**: The new `AudioConsumer` implementation that buffers PCM audio frames and submits them to the LiteRT_Engine for transcription.
- **Unified_Capture_Service**: The existing `UnifiedAudioCaptureService` that owns the `AudioRecord`, reads PCM data, and dispatches frame copies to registered `AudioConsumer` instances.
- **Home_ViewModel**: The `HomeViewModel` that orchestrates recording state, registers consumers, and exposes transcript segments to the UI.
- **Audio_Frame**: A `ShortArray` containing PCM 16-bit mono samples at 44100 Hz, dispatched by Unified_Capture_Service.
- **Transcript_Segment**: A finalized piece of transcribed text with a type classification (current, recent, past) and timestamp.
- **Model_State**: An observable state representing the lifecycle of the model: Idle, Loading, Ready, or Error.

## Requirements

### Requirement 1: Model Assembly on App Startup

**User Story:** As a user, I want the app to prepare the transcription model automatically when I open it, so that transcription is ready when I need it without manual setup.

#### Acceptance Criteria

1. WHEN the app launches for the first time, THE Model_Assembler SHALL concatenate the 25 split asset files (gemma-4-E2B-it.litertlm.aa through .ay) into a single file at `context.filesDir/gemma-4-E2B-it.litertlm`.
2. WHEN the assembled model file already exists with non-zero size, THE Model_Assembler SHALL skip reassembly and return the existing file path.
3. WHILE the Model_Assembler is concatenating files, THE Home_ViewModel SHALL expose a Model_State of Loading to the UI.
4. WHEN model assembly completes successfully, THE Home_ViewModel SHALL transition Model_State to Ready.
5. IF model assembly fails due to an I/O error, THEN THE Home_ViewModel SHALL transition Model_State to Error and expose a descriptive error message.
6. THE Model_Assembler SHALL perform file concatenation on a background coroutine (Dispatchers.IO) to avoid blocking the main thread.

### Requirement 2: LiteRT-LM Engine Initialization

**User Story:** As a user, I want the transcription engine to initialize reliably after model assembly, so that I can start recording without delays.

#### Acceptance Criteria

1. WHEN model assembly completes successfully, THE LiteRT_Engine SHALL be initialized with an Engine_Config specifying the assembled model path, `Backend.CPU()`, and `context.cacheDir` as the cache directory.
2. THE LiteRT_Engine SHALL call `engine.initialize()` on a background coroutine (Dispatchers.IO) to avoid blocking the main thread.
3. WHEN engine initialization completes successfully, THE Home_ViewModel SHALL transition Model_State to Ready.
4. IF engine initialization fails, THEN THE Home_ViewModel SHALL transition Model_State to Error and expose the failure reason as a user-facing message.
5. THE LiteRT_Engine SHALL create exactly one Conversation instance for use during the recording session.
6. WHEN the app process is destroyed, THE LiteRT_Engine SHALL close the Conversation and release engine resources.

### Requirement 3: Gemma Consumer Registration and Lifecycle

**User Story:** As a user, I want the on-device transcription to integrate seamlessly with the existing recording flow, so that I press the same "Start Recording" button and get local transcription.

#### Acceptance Criteria

1. WHEN the user presses "Start Recording" and Model_State is Ready, THE Home_ViewModel SHALL create a Gemma_Consumer instance and register it with the Unified_Capture_Service.
2. WHEN the user presses "Start Recording" and Model_State is not Ready, THE Home_ViewModel SHALL display an error message indicating the model is not available.
3. WHEN `prepare()` is called on Gemma_Consumer, THE Gemma_Consumer SHALL initialize its internal audio buffer and configure itself for the provided sample rate, channel count, and encoding.
4. WHEN `release()` is called on Gemma_Consumer, THE Gemma_Consumer SHALL flush any buffered audio, release internal resources, and signal that no further frames will be processed.
5. THE Gemma_Consumer SHALL replace the SpeechRecognitionConsumer as the transcription consumer registered with Unified_Capture_Service.

### Requirement 4: Audio Frame Processing and Buffering

**User Story:** As a user, I want my speech to be captured and processed locally in real time, so that I see transcription results appearing as I speak.

#### Acceptance Criteria

1. WHEN `onAudioFrame()` is called, THE Gemma_Consumer SHALL append the PCM samples to an internal accumulation buffer.
2. WHEN the accumulation buffer reaches a configurable chunk duration threshold, THE Gemma_Consumer SHALL submit the buffered audio to the LiteRT_Engine for transcription on a background coroutine (Dispatchers.IO).
3. THE Gemma_Consumer SHALL use a chunk duration threshold between 2 and 5 seconds to balance latency against inference quality.
4. WHILE audio is being accumulated, THE Gemma_Consumer SHALL accept new frames without blocking the Unified_Capture_Service read thread.
5. IF the accumulation buffer exceeds a maximum size limit, THEN THE Gemma_Consumer SHALL drop the oldest samples and log a warning.

### Requirement 5: Transcription Inference and Result Delivery

**User Story:** As a user, I want to see my spoken words appear on screen as text, so that I have a readable record of the conversation.

#### Acceptance Criteria

1. WHEN a buffered audio chunk is submitted to the LiteRT_Engine, THE Gemma_Consumer SHALL send a transcription prompt containing the audio data to the Conversation via `sendMessage()`.
2. WHEN `sendMessage()` returns a non-empty response, THE Gemma_Consumer SHALL invoke the `onFinalResult` callback with the transcribed text.
3. WHILE inference is in progress for a chunk, THE Gemma_Consumer SHALL continue buffering new incoming audio frames for the next submission.
4. THE Gemma_Consumer SHALL invoke `sendMessage()` on Dispatchers.IO to avoid blocking the main thread or the audio capture thread.
5. WHEN the Gemma_Consumer receives a transcription result, THE Home_ViewModel SHALL create a new Transcript_Segment with type CURRENT and reclassify existing segments.

### Requirement 6: Partial Result Feedback

**User Story:** As a user, I want visual feedback that my speech is being heard while waiting for full transcription, so that I know the system is working.

#### Acceptance Criteria

1. WHILE audio frames are being accumulated toward the chunk threshold, THE Gemma_Consumer SHALL invoke the `onPartialResult` callback with a status indicator (e.g., accumulated duration or "Listening...").
2. WHEN a new audio chunk submission begins, THE Gemma_Consumer SHALL invoke the `onPartialResult` callback with a processing indicator (e.g., "Transcribing...").
3. WHEN a final transcription result is delivered, THE Gemma_Consumer SHALL clear the partial result by invoking `onPartialResult` with an empty string.

### Requirement 7: Error Handling During Inference

**User Story:** As a user, I want the app to handle transcription errors gracefully, so that a single failure does not stop my entire recording session.

#### Acceptance Criteria

1. IF `sendMessage()` throws an exception during inference, THEN THE Gemma_Consumer SHALL invoke the `onError` callback with a descriptive message and continue processing subsequent audio chunks.
2. IF the Conversation becomes invalid or unresponsive, THEN THE Gemma_Consumer SHALL attempt to create a new Conversation from the LiteRT_Engine and resume processing.
3. IF Conversation recreation fails, THEN THE Gemma_Consumer SHALL invoke the `onError` callback indicating transcription is unavailable and cease further inference attempts for the current session.
4. THE Gemma_Consumer SHALL log all inference errors with sufficient detail for debugging without exposing internal details to the user.

### Requirement 8: Resource and Memory Management

**User Story:** As a user, I want the app to manage device resources responsibly, so that transcription does not cause crashes or excessive battery drain.

#### Acceptance Criteria

1. THE LiteRT_Engine SHALL be initialized as a singleton scoped to the application lifecycle, reused across recording sessions without re-loading the model.
2. WHEN the ViewModel is cleared (app backgrounded or destroyed), THE Home_ViewModel SHALL close the active Conversation and release engine resources.
3. THE Gemma_Consumer SHALL limit its internal audio buffer to a maximum of 30 seconds of PCM data (44100 samples/sec × 2 bytes × 30 sec = approximately 2.6 MB).
4. WHEN recording stops, THE Gemma_Consumer SHALL release its audio buffer memory promptly during the `release()` call.
5. THE Model_Assembler SHALL verify that at least 10 MB of free storage is available before beginning file concatenation, consistent with `AudioConfig.MIN_STORAGE_BYTES`.

### Requirement 9: Threading and Concurrency

**User Story:** As a developer, I want clear threading boundaries, so that the audio pipeline remains responsive and free of race conditions.

#### Acceptance Criteria

1. THE Gemma_Consumer SHALL accept `onAudioFrame()` calls on the Unified_Capture_Service read thread without blocking.
2. THE Gemma_Consumer SHALL perform all LiteRT_Engine inference calls on Dispatchers.IO.
3. THE Gemma_Consumer SHALL use a thread-safe mechanism (e.g., a concurrent queue or mutex-protected buffer) to transfer audio data from the capture thread to the inference coroutine.
4. THE Gemma_Consumer SHALL ensure that only one inference call is in progress at a time, queuing subsequent chunks until the current call completes.
5. THE Home_ViewModel SHALL dispatch Model_State updates and transcript callbacks on the main thread for safe UI consumption.

### Requirement 10: Accessibility for Model Loading State

**User Story:** As a visually impaired user, I want to be informed about the model loading status through my screen reader, so that I know when the app is ready for transcription.

#### Acceptance Criteria

1. WHILE Model_State is Loading, THE Home screen SHALL display a loading indicator with a content description of "Preparing transcription model" accessible to TalkBack.
2. WHEN Model_State transitions to Ready, THE Home screen SHALL announce "Transcription model ready" via an accessibility announcement.
3. WHEN Model_State transitions to Error, THE Home screen SHALL announce the error message via an accessibility announcement and display it with sufficient contrast (minimum 4.5:1 ratio).
4. THE loading indicator SHALL have a minimum touch target size of 48x48dp, consistent with accessibility requirements.

### Requirement 11: Coexistence with File Recording Consumer

**User Story:** As a user, I want my audio to still be saved as a file while transcription happens locally, so that I can play back recordings later.

#### Acceptance Criteria

1. WHEN recording starts, THE Home_ViewModel SHALL register both the Gemma_Consumer and the FileRecordingConsumer with the Unified_Capture_Service.
2. THE Unified_Capture_Service SHALL dispatch independent frame copies to both consumers concurrently, as per the existing fan-out architecture.
3. WHEN recording stops, THE Home_ViewModel SHALL retrieve the audio file path from FileRecordingConsumer and save the session with both the transcript and the audio file reference.
