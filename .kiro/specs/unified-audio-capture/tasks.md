# Implementation Plan: Unified Audio Capture

## Overview

Replace the broken dual-service audio architecture with a single `UnifiedAudioCaptureService` that owns the `AudioRecord` instance and dispatches independent frame copies to registered `AudioConsumer` implementations. This eliminates microphone contention between speech recognition and file recording.

## Tasks

- [x] 1. Create AudioConsumer interface and AudioConfig constants
  - [x] 1.1 Create the `AudioConsumer` interface in `service/` package
    - Define `prepare(sampleRate: Int, channelCount: Int, encoding: Int)` method
    - Define `onAudioFrame(frame: ShortArray, frameSize: Int)` method
    - Define `release()` method
    - Add KDoc comments for each method
    - _Requirements: 4.1, 4.2, 4.3_
  - [x] 1.2 Create `AudioConfig` object with audio configuration constants
    - Define SAMPLE_RATE (44100), CHANNEL_CONFIG, CHANNEL_COUNT, AUDIO_FORMAT, AUDIO_SOURCE
    - Define MIN_STORAGE_BYTES (10 MB)
    - _Requirements: 1.1, 6.6_
  - [x] 1.3 Create `CaptureState` enum (IDLE, CAPTURING, STOPPING)
    - _Requirements: 1.3, 1.4_
  - [x] 1.4 Create `WavHeader` data class and `duplicateFrame` function
    - Implement WavHeader with sampleRate, channelCount, bitsPerSample, dataSize fields
    - Implement computed properties: byteRate, blockAlign, headerSize
    - Implement `duplicateFrame(frame, frameSize, consumerCount)` pure function
    - _Requirements: 3.1, 6.2_

- [x] 2. Implement UnifiedAudioCaptureService
  - [x] 2.1 Create `UnifiedAudioCaptureService` class in `service/` package
    - Implement `registerConsumer(consumer: AudioConsumer)` method
    - Implement `startCapture()` — validate consumers, create AudioRecord, call prepare() on consumers, spawn reading thread
    - Implement `stopCapture()` — signal thread stop, join, call release() on consumers, release AudioRecord
    - Implement `destroy()` — stop capture if active, clear consumer list
    - Manage CaptureState transitions (IDLE → CAPTURING → STOPPING → IDLE)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 8.1, 8.2, 8.3, 8.4_
  - [ ]* 2.2 Write property test for frame dispatch completeness
    - **Property 1: Frame Dispatch Completeness**
    - Generate random ShortArrays (size 1–8192) and consumer counts (1–10)
    - Verify `duplicateFrame` output list size equals consumer count
    - **Validates: Requirements 3.1, 3.2**
  - [ ]* 2.3 Write property test for copy independence
    - **Property 2: Copy Independence**
    - Generate random ShortArrays, duplicate for 2+ consumers
    - Mutate one copy at a random index, verify other copies unchanged
    - **Validates: Requirements 9.1, 9.2**
  - [ ]* 2.4 Write property test for content preservation
    - **Property 3: Content Preservation**
    - Generate random ShortArrays, duplicate, verify each copy is value-equal to original
    - **Validates: Requirements 9.3**
  - [ ]* 2.5 Write unit tests for UnifiedAudioCaptureService lifecycle
    - Test state transitions (IDLE → CAPTURING → STOPPING → IDLE)
    - Test error when no consumers registered at startCapture()
    - Test destroy() during active capture stops cleanly
    - Test consumer registration rejected during active capture
    - _Requirements: 1.3, 1.4, 3.4, 8.3_

- [x] 3. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement FileRecordingConsumer
  - [x] 4.1 Create `FileRecordingConsumer` class implementing `AudioConsumer`
    - Implement `prepare()` — check storage (≥10 MB), create timestamped WAV file, write WAV header placeholder
    - Implement `onAudioFrame()` — write PCM data to output file incrementally
    - Implement `release()` — patch WAV header with final data size, close file
    - Implement `getOutputFilePath()` accessor
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  - [ ]* 4.2 Write unit tests for FileRecordingConsumer
    - Test WAV header generation with known parameters
    - Test storage check rejects when below 10 MB
    - Test file path is exposed after prepare()
    - Test onError invoked on write failure
    - _Requirements: 6.1, 6.2, 6.5, 6.6_

- [x] 5. Implement SpeechRecognitionConsumer
  - [x] 5.1 Create `SpeechRecognitionConsumer` class implementing `AudioConsumer`
    - Implement `prepare()` — initialize SpeechRecognizer, configure for raw PCM input
    - Implement `onAudioFrame()` — feed PCM data to speech recognition API
    - Implement `release()` — destroy SpeechRecognizer, free resources
    - Implement auto-restart on silence timeout for continuous transcription
    - Propagate partial/final results and errors via callbacks
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  - [ ]* 5.2 Write unit tests for SpeechRecognitionConsumer
    - Test callback propagation for partial and final results
    - Test error callback invocation on recognizer error
    - Test auto-restart behavior on session end
    - _Requirements: 5.2, 5.3, 5.4, 5.5_

- [x] 6. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Integrate UnifiedAudioCaptureService into HomeViewModel
  - [x] 7.1 Refactor `HomeViewModel` to use `UnifiedAudioCaptureService`
    - Remove direct references to `SpeechRecognitionService` and `AudioRecorderService`
    - Add `captureService`, `speechConsumer`, and `fileConsumer` fields
    - Refactor `startRecording()` — create UnifiedAudioCaptureService, register both consumers, call startCapture()
    - Refactor `stopRecording()` — call stopCapture(), retrieve file path from fileConsumer, save session
    - Refactor `onCleared()` — call destroy() on captureService
    - Wire error callbacks from both consumers and capture service to `_error` StateFlow
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 8.1, 8.2, 8.3_
  - [ ]* 7.2 Write unit tests for HomeViewModel integration
    - Test startRecording() creates service and registers both consumers
    - Test stopRecording() calls stopCapture() and saves session with audio file path
    - Test onCleared() calls destroy() on capture service
    - Test error propagation from consumers to error StateFlow
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 8. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate the core `duplicateFrame` pure function (Properties 1–3 from design)
- Unit tests validate lifecycle, error handling, and integration behavior
- The existing `AudioRecorderService` and `SpeechRecognitionService` files can be kept for reference but are no longer used by HomeViewModel after task 7
