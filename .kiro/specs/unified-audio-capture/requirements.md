# Requirements Document

## Introduction

The Unified Audio Capture feature replaces the current dual-service recording approach in the Android app. Today, `HomeViewModel.startRecording()` initializes both `SpeechRecognitionService` and `AudioRecorderService` independently, each attempting to acquire the microphone via `AudioSource.VOICE_RECOGNITION`. Because Android's `AudioRecord` only allows a single consumer at a time, this architecture is fundamentally broken — one service will fail to acquire the audio source.

The solution introduces a single `UnifiedAudioCaptureService` that owns the `AudioRecord` instance, continuously reads raw PCM data into a buffer on a background thread, duplicates each audio frame, and dispatches one copy to a refactored `SpeechRecognitionService` (which processes raw audio into recognized text) and another copy to a refactored `AudioRecorderService` (which encodes raw audio into a file). This fan-out architecture ensures both consumers receive identical audio data without contention.

## Glossary

- **Unified_Audio_Capture_Service**: A new service that owns the `AudioRecord` instance, reads raw PCM data on a background thread, and distributes duplicated audio frames to registered consumers.
- **Audio_Consumer**: An interface representing any component that can receive raw PCM audio frames (as `ShortArray`) for processing.
- **Speech_Recognition_Consumer**: A refactored version of `SpeechRecognitionService` that implements `Audio_Consumer` and feeds raw PCM frames to Android's speech recognition API to produce transcribed text.
- **File_Recording_Consumer**: A refactored version of `AudioRecorderService` that implements `Audio_Consumer` and encodes raw PCM frames into an audio file (e.g., WAV or M4A).
- **PCM_Frame**: A `ShortArray` containing one read-cycle's worth of raw 16-bit audio samples from `AudioRecord`.
- **Audio_Buffer**: The internal buffer used by `Unified_Audio_Capture_Service` to hold PCM data between `AudioRecord.read()` calls and consumer dispatch.
- **Home_ViewModel**: The ViewModel coordinating recording lifecycle, replacing direct service instantiation with `Unified_Audio_Capture_Service` orchestration.

## Requirements

### Requirement 1: Unified Audio Capture Initialization

**User Story:** As a developer, I want a single service to own the microphone, so that both speech recognition and file recording can operate simultaneously without audio source contention.

#### Acceptance Criteria

1. WHEN `startCapture()` is called, THE Unified_Audio_Capture_Service SHALL create an `AudioRecord` instance configured with `AudioSource.VOICE_RECOGNITION`, 16-bit PCM encoding, and a sample rate of 44100 Hz.
2. WHEN `startCapture()` is called, THE Unified_Audio_Capture_Service SHALL allocate an Audio_Buffer sized to at least the minimum buffer size reported by `AudioRecord.getMinBufferSize()`.
3. IF `AudioRecord` initialization fails, THEN THE Unified_Audio_Capture_Service SHALL invoke the error callback with a descriptive message and remain in the stopped state.
4. IF the `AudioRecord` instance reports a state other than `STATE_INITIALIZED` after creation, THEN THE Unified_Audio_Capture_Service SHALL release the instance and invoke the error callback.

### Requirement 2: Background Audio Reading

**User Story:** As a developer, I want audio data to be read continuously on a background thread, so that the main thread remains responsive during recording.

#### Acceptance Criteria

1. WHEN capture is active, THE Unified_Audio_Capture_Service SHALL spawn a dedicated background thread that continuously calls `audioRecord.read()` to pull raw PCM data into a ShortArray.
2. WHILE capture is active, THE Unified_Audio_Capture_Service SHALL read PCM_Frames at a cadence that prevents AudioRecord buffer overflow.
3. WHEN `stopCapture()` is called, THE Unified_Audio_Capture_Service SHALL signal the background thread to terminate and wait for it to complete before releasing the `AudioRecord` instance.
4. IF `audioRecord.read()` returns an error code, THEN THE Unified_Audio_Capture_Service SHALL invoke the error callback and stop capture.

### Requirement 3: Audio Frame Duplication and Dispatch

**User Story:** As a developer, I want each audio frame to be duplicated and sent to multiple consumers, so that speech recognition and file recording each receive their own independent copy of the audio data.

#### Acceptance Criteria

1. WHEN a PCM_Frame is read from AudioRecord, THE Unified_Audio_Capture_Service SHALL create an independent copy of the ShortArray for each registered Audio_Consumer.
2. WHEN a PCM_Frame is read from AudioRecord, THE Unified_Audio_Capture_Service SHALL dispatch the copied frame to each registered Audio_Consumer by calling the consumer's `onAudioFrame()` method.
3. THE Unified_Audio_Capture_Service SHALL support registering at least two Audio_Consumer instances before capture begins.
4. IF no Audio_Consumer instances are registered when `startCapture()` is called, THEN THE Unified_Audio_Capture_Service SHALL invoke the error callback and remain in the stopped state.

### Requirement 4: Audio Consumer Interface

**User Story:** As a developer, I want a common interface for audio consumers, so that new audio processing components can be added without modifying the capture service.

#### Acceptance Criteria

1. THE Audio_Consumer interface SHALL define an `onAudioFrame(frame: ShortArray, frameSize: Int)` method that consumers implement to receive PCM data.
2. THE Audio_Consumer interface SHALL define a `prepare(sampleRate: Int, channelCount: Int, encoding: Int)` method that consumers implement to configure themselves before capture begins.
3. THE Audio_Consumer interface SHALL define a `release()` method that consumers implement to free resources when capture ends.

### Requirement 5: Speech Recognition Consumer

**User Story:** As a patient, I want live speech-to-text transcription from the shared audio stream, so that I can see what is being said during my medical visit in real time.

#### Acceptance Criteria

1. WHEN `onAudioFrame()` is called, THE Speech_Recognition_Consumer SHALL feed the raw PCM data to Android's speech recognition API for transcription.
2. WHEN the speech recognition API produces a partial result, THE Speech_Recognition_Consumer SHALL invoke the `onPartialResult` callback with the interim text.
3. WHEN the speech recognition API produces a final result, THE Speech_Recognition_Consumer SHALL invoke the `onFinalResult` callback with the finalized text.
4. IF the speech recognition API returns an error, THEN THE Speech_Recognition_Consumer SHALL invoke the `onError` callback with a descriptive message.
5. WHILE capture is active, THE Speech_Recognition_Consumer SHALL automatically restart recognition sessions that end (due to silence timeout or session limits) to maintain continuous transcription.

### Requirement 6: File Recording Consumer

**User Story:** As a patient, I want the audio from my medical visit saved to a file, so that I can replay it later for review.

#### Acceptance Criteria

1. WHEN `prepare()` is called, THE File_Recording_Consumer SHALL create an output file in the app's recordings directory with a timestamped filename.
2. WHEN `onAudioFrame()` is called, THE File_Recording_Consumer SHALL write the raw PCM data to the output file, encoding it into a playable audio format.
3. WHEN `release()` is called, THE File_Recording_Consumer SHALL finalize and close the output file.
4. THE File_Recording_Consumer SHALL expose the output file path so that the Home_ViewModel can associate it with the recording session.
5. IF writing to the output file fails, THEN THE File_Recording_Consumer SHALL invoke the `onError` callback with a descriptive message.
6. IF available storage is below 10 MB when `prepare()` is called, THEN THE File_Recording_Consumer SHALL invoke the `onError` callback and report insufficient storage.

### Requirement 7: HomeViewModel Integration

**User Story:** As a developer, I want the HomeViewModel to use the unified capture service instead of separate services, so that the recording lifecycle is managed through a single coordinated entry point.

#### Acceptance Criteria

1. WHEN `startRecording()` is called on Home_ViewModel, THE Home_ViewModel SHALL create a Unified_Audio_Capture_Service, register both Speech_Recognition_Consumer and File_Recording_Consumer, and call `startCapture()`.
2. WHEN `stopRecording()` is called on Home_ViewModel, THE Home_ViewModel SHALL call `stopCapture()` on the Unified_Audio_Capture_Service, which stops the background thread and calls `release()` on each Audio_Consumer.
3. WHEN the Home_ViewModel is cleared, THE Home_ViewModel SHALL call `destroy()` on the Unified_Audio_Capture_Service to release all resources.
4. THE Home_ViewModel SHALL remove direct references to the legacy `SpeechRecognitionService` and `AudioRecorderService` classes.

### Requirement 8: Resource Lifecycle Management

**User Story:** As a developer, I want all audio resources to be properly released when recording stops or the app is backgrounded, so that the microphone is freed for other applications.

#### Acceptance Criteria

1. WHEN `stopCapture()` is called, THE Unified_Audio_Capture_Service SHALL release the `AudioRecord` instance and free the Audio_Buffer.
2. WHEN `destroy()` is called, THE Unified_Audio_Capture_Service SHALL call `release()` on each registered Audio_Consumer and then release the `AudioRecord` instance.
3. IF capture is active when `destroy()` is called, THEN THE Unified_Audio_Capture_Service SHALL stop capture before releasing resources.
4. THE Unified_Audio_Capture_Service SHALL ensure the background reading thread is terminated before releasing the `AudioRecord` instance to prevent use-after-free conditions.

### Requirement 9: Frame Duplication Correctness

**User Story:** As a developer, I want to verify that frame duplication produces independent copies, so that one consumer's modifications to its frame do not affect the other consumer's data.

#### Acceptance Criteria

1. FOR ALL PCM_Frames dispatched to consumers, THE Unified_Audio_Capture_Service SHALL guarantee that each consumer receives a distinct ShortArray instance (not a shared reference).
2. FOR ALL PCM_Frames of arbitrary content, copying then dispatching SHALL produce arrays where modifying one consumer's copy does not alter the other consumer's copy (independence property).
3. FOR ALL PCM_Frames, the copied array dispatched to each consumer SHALL contain identical values to the original frame read from AudioRecord (content preservation property).
