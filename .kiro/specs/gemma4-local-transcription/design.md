# Design Document: Gemma 4 Local Transcription

## Overview

This design replaces the Web Speech API-based transcription in the TranscribeCare web app with a fully local, on-device speech-to-text pipeline powered by Google's Gemma 4 E2B model running via LiteRT.js (formerly TensorFlow Lite for Web).

The architecture introduces four new logical components into the existing single-file React app:

1. **Model Loader** — Downloads, caches, validates, and initializes the Gemma 4 E2B `.tflite` model artifact using the Cache API and LiteRT.js runtime.
2. **Audio Processor** — Captures microphone audio via the Web Audio API (AudioWorklet), resamples to 16kHz mono PCM, and segments into fixed-duration chunks via a ring buffer.
3. **Inference Worker** — Runs LiteRT.js model inference in a dedicated Web Worker to keep the main thread responsive, with WebGPU → WASM SIMD → WASM fallback.
4. **Transcription Engine** — Orchestrates the pipeline: feeds audio chunks to the worker, accumulates partial/final transcript segments, and exposes the same `TranscriptSegment` interface consumed by the existing `TranscriptView` component.

The Web Speech API (`SpeechRecognition`) is fully removed. The existing `MediaRecorder` audio capture for playback remains unchanged.

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Web Worker for inference | Keeps main thread at <16ms frame time (Req 9.2) |
| AudioWorklet for capture | Low-latency, glitch-free audio on the audio rendering thread |
| Ring buffer in AudioWorklet | Bounded memory, no unbounded accumulation (Req 9.4) |
| Cache API for model storage | Persistent, works offline, supports integrity checks (Req 6) |
| Progressive acceleration fallback | WebGPU → WASM SIMD → WASM ensures broadest device support (Req 5) |
| Single custom hook (`useLocalTranscription`) | Clean integration point replacing `recognitionRef` usage in App.tsx |

## Architecture

```mermaid
graph TD
    subgraph Main Thread
        A[App.tsx] -->|controls| B[useLocalTranscription Hook]
        B -->|segments, status| A
        B -->|start/stop| C[Audio Processor]
        B -->|messages| D[Inference Worker]
    end

    subgraph Audio Thread - AudioWorklet
        C -->|getUserMedia| E[Microphone]
        C -->|AudioWorkletNode| F[PCM Ring Buffer]
        F -->|16kHz mono chunks| B
    end

    subgraph Web Worker
        D -->|loadModel| G[Model Loader]
        G -->|Cache API| H[(Browser Cache)]
        G -->|fetch| I[CDN / Origin]
        D -->|inference| J[LiteRT.js Runtime]
        J -->|WebGPU or WASM| K[Gemma 4 E2B Model]
    end

    B -->|TranscriptSegment[]| L[TranscriptView]
```

### Data Flow

1. User taps "Start Recording" → `useLocalTranscription.start()` is called
2. AudioWorklet captures mic audio, resamples to 16kHz mono, fills ring buffer
3. Every chunk interval (~1–2s), the hook transfers a `Float32Array` chunk to the Web Worker via `postMessage` (transferable)
4. Worker runs LiteRT.js inference on the chunk, returns partial/final text
5. Hook updates `segments` state → React re-renders `TranscriptView`
6. User taps "Stop Recording" → pipeline drains, final segment emitted, resources released

## Components and Interfaces

### 1. Model Loader (`modelLoader.ts`)

Responsible for downloading, caching, validating, and reporting progress.

```typescript
interface ModelLoaderConfig {
  modelUrl: string;           // CDN URL for the .tflite artifact
  modelVersion: string;       // Semantic version for cache invalidation
  cacheName: string;          // Cache API storage name
}

interface ModelLoadProgress {
  phase: 'downloading' | 'validating' | 'initializing' | 'ready' | 'error';
  percent: number;            // 0–100
  error?: string;
}

// Functions exposed to the Web Worker
async function loadModel(config: ModelLoaderConfig): Promise<Uint8Array>;
function reportProgress(progress: ModelLoadProgress): void;
```

**Caching strategy:**
- On first load: fetch model from `modelUrl`, store in Cache API with version metadata header.
- On subsequent loads: check cache, validate integrity via stored SHA-256 hash, load from cache.
- If cache is corrupt or version mismatch: re-download.

### 2. Audio Processor (`audioProcessor.ts` + `audioWorklet.ts`)

```typescript
interface AudioProcessorConfig {
  targetSampleRate: 16000;
  chunkDurationMs: number;    // e.g., 1500ms
  ringBufferSizeMs: number;   // e.g., 10000ms (bounded)
}

interface AudioProcessorHandle {
  start(): Promise<void>;
  stop(): void;
  onChunk: (chunk: Float32Array) => void;
}
```

**AudioWorklet processor (`audioWorklet.ts`):**
- Runs on the audio rendering thread
- Receives raw PCM frames from the microphone source
- Resamples from device sample rate (typically 44.1kHz or 48kHz) to 16kHz using linear interpolation
- Writes resampled samples into a fixed-size ring buffer
- When buffer reaches `chunkDurationMs` worth of samples, posts the chunk to the main thread via `MessagePort`

### 3. Inference Worker (`inferenceWorker.ts`)

```typescript
// Messages from main thread → worker
type WorkerInMessage =
  | { type: 'init'; config: ModelLoaderConfig }
  | { type: 'infer'; chunk: Float32Array }  // transferred
  | { type: 'finalize' }
  | { type: 'release' };

// Messages from worker → main thread
type WorkerOutMessage =
  | { type: 'progress'; progress: ModelLoadProgress }
  | { type: 'backend'; backend: 'webgpu' | 'wasm-simd' | 'wasm' }
  | { type: 'partial'; text: string }
  | { type: 'final'; text: string }
  | { type: 'error'; message: string };
```

**Acceleration fallback logic:**
1. Attempt `loadAndCompile(modelBytes, { accelerator: 'webgpu' })`
2. If WebGPU unavailable or fails → try `{ accelerator: 'wasm' }` (XNNPack with SIMD auto-detected)
3. Report active backend via `'backend'` message

### 4. Transcription Engine Hook (`useLocalTranscription.ts`)

```typescript
interface TranscriptionState {
  status: 'idle' | 'loading' | 'ready' | 'recording' | 'error';
  loadProgress: ModelLoadProgress;
  segments: TranscriptSegment[];
  interimText: string;
  backend: 'webgpu' | 'wasm-simd' | 'wasm' | null;
  error: string | null;
}

interface UseLocalTranscriptionReturn extends TranscriptionState {
  initialize(): Promise<void>;
  start(): Promise<void>;
  stop(): void;
  retry(): void;
}

function useLocalTranscription(): UseLocalTranscriptionReturn;
```

**Segment management:**
- Partial inference results update `interimText`
- When a final result arrives, a new `TranscriptSegment` is appended with `type: 'current'`
- Previous `'current'` segments are promoted to `'recent'`
- On stop, all segments are finalized for session storage

### 5. Integration with App.tsx

The hook replaces the existing `recognitionRef`-based speech recognition:

```typescript
// Before (Web Speech API)
const recognitionRef = useRef<any>(null);

// After (Local Gemma 4)
const transcription = useLocalTranscription();
```

The `handleToggleRecording` function calls `transcription.start()` / `transcription.stop()` instead of `recognitionRef.current.start()` / `.stop()`.

A new `ModelLoadingOverlay` component displays progress with accessible `aria-live` announcements during initial model download.

## Data Models

### TranscriptSegment (unchanged)

```typescript
interface TranscriptSegment {
  id: string;
  text: string;
  type: 'past' | 'recent' | 'current';
}
```

### Model Cache Metadata

```typescript
interface ModelCacheMetadata {
  version: string;
  sha256: string;
  downloadedAt: string;       // ISO 8601
  sizeBytes: number;
}
```

Stored as a JSON entry in the same Cache API namespace alongside the model binary.

### Audio Chunk Transfer

```typescript
interface AudioChunkMessage {
  type: 'chunk';
  data: Float32Array;         // Transferred (zero-copy)
  timestamp: number;          // Performance.now() at capture time
  sampleRate: 16000;
}
```

### Worker State

```typescript
interface InferenceWorkerState {
  modelLoaded: boolean;
  backend: 'webgpu' | 'wasm-simd' | 'wasm' | null;
  inferenceCount: number;
  lastInferenceMs: number;    // Latency tracking
}
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Progress percentage is always bounded

*For any* total model size greater than zero and any number of bytes received (0 ≤ received ≤ total), the computed loading progress percentage SHALL always be a number in the range [0, 100] inclusive.

**Validates: Requirements 1.2**

### Property 2: Cache integrity validation round-trip

*For any* byte array representing a model artifact, computing its SHA-256 hash and then validating the same byte array against that hash SHALL return true. Mutating any single byte in the array SHALL cause validation to return false.

**Validates: Requirements 1.5**

### Property 3: Audio resampling produces correct output length

*For any* input audio buffer of length N samples at a source sample rate S (where S > 0), resampling to 16kHz SHALL produce an output buffer of length floor(N × 16000 / S), and all output sample values SHALL be in the range [-1.0, 1.0].

**Validates: Requirements 2.2**

### Property 4: Audio chunking produces fixed-size chunks

*For any* continuous stream of 16kHz mono PCM samples of total length L, the chunking function with chunk size C SHALL emit floor(L / C) complete chunks each of exactly C samples, with no samples lost or duplicated across chunks.

**Validates: Requirements 2.3**

### Property 5: Ring buffer is bounded and lossless

*For any* sequence of write operations to the ring buffer that does not exceed the buffer's capacity, the total samples read out SHALL equal the total samples written in (lossless). Additionally, *for any* sequence of writes, the buffer's internal storage SHALL never exceed its configured maximum size in bytes (bounded).

**Validates: Requirements 2.5, 9.4**

### Property 6: Segment state machine correctness

*For any* sequence of worker messages containing partial and final results, the transcription engine SHALL: (a) update `interimText` to the latest partial text on each partial message, (b) append a new `TranscriptSegment` with `type: 'current'` and the correct text on each final message, (c) promote previous `'current'` segments to `'recent'` when a new final arrives, and (d) clear `interimText` when a final message is received.

**Validates: Requirements 3.2, 3.3, 4.4, 4.5, 8.4**

### Property 7: Error resilience preserves recording state

*For any* sequence of worker messages that includes error messages interleaved with valid partial/final results, the transcription engine SHALL remain in the `'recording'` status and continue processing subsequent messages. No error message SHALL cause the engine to transition to a terminal state or drop subsequent results.

**Validates: Requirements 3.5**

### Property 8: Stop finalization captures pending interim text

*For any* non-empty interim text present when `stop()` is called, the transcription engine SHALL convert that interim text into a finalized `TranscriptSegment` appended to the segments array, and `interimText` SHALL be cleared to an empty string.

**Validates: Requirements 4.2**

## Error Handling

### Model Loading Errors

| Error Condition | Handling | User Impact |
|----------------|----------|-------------|
| Network fetch fails | Set state to `'error'`, store descriptive message, expose `retry()` | Error overlay with retry button, announced via `aria-live` |
| Cache integrity check fails | Delete corrupt cache entry, attempt re-download | Transparent to user if online; error if offline |
| LiteRT initialization fails | Report error with browser/device context | Error message suggesting browser update |
| Insufficient memory | Catch OOM, report resource constraint | Message suggesting closing other tabs |

### Audio Processing Errors

| Error Condition | Handling | User Impact |
|----------------|----------|-------------|
| Microphone permission denied | Set error state with permission instructions | Accessible error message with platform-specific guidance |
| AudioContext creation fails | Fallback error state | Message indicating browser incompatibility |
| AudioWorklet registration fails | Fallback to ScriptProcessorNode (deprecated but wider support) | Transparent fallback, log warning |

### Inference Errors

| Error Condition | Handling | User Impact |
|----------------|----------|-------------|
| Single chunk inference fails | Log error, skip chunk, continue with next | Brief gap in transcription, no crash |
| Worker crashes | Detect via `onerror`, attempt worker restart | Brief pause, auto-recovery |
| Repeated inference failures (>5 consecutive) | Stop recording, report persistent error | Error message with suggestion to reload |

### Resource Cleanup

On `stop()`:
1. Close `AudioWorkletNode` and disconnect from `AudioContext`
2. Stop all `MediaStreamTrack`s from `getUserMedia`
3. Send `'release'` message to worker (frees model memory in worker)
4. Clear internal buffers and reset state to `'ready'`

Cleanup MUST complete within 1 second of `stop()` being called (Req 9.3).

## Testing Strategy

### Property-Based Tests (fast-check)

The project will use [fast-check](https://github.com/dubzzz/fast-check) for property-based testing, integrated with Vitest as the test runner.

**Configuration:**
- Minimum 100 iterations per property test
- Each test tagged with: `Feature: gemma4-local-transcription, Property {N}: {title}`

**Properties to implement:**

| Property | Module Under Test | Key Generators |
|----------|------------------|----------------|
| 1: Progress bounds | `modelLoader.ts` → `computeProgress()` | `fc.nat()` for total/received |
| 2: Integrity round-trip | `modelLoader.ts` → `validateIntegrity()` | `fc.uint8Array()` |
| 3: Resampling length | `audioProcessor.ts` → `resample()` | `fc.float32Array()`, `fc.constantFrom(44100, 48000)` |
| 4: Chunking fixed-size | `audioProcessor.ts` → `chunkStream()` | `fc.float32Array()`, `fc.integer({min:1600, max:48000})` |
| 5: Ring buffer | `ringBuffer.ts` | `fc.array(fc.float32Array())` |
| 6: Segment state machine | `useLocalTranscription.ts` → reducer | `fc.array(fc.oneof(partialMsg, finalMsg))` |
| 7: Error resilience | `useLocalTranscription.ts` → reducer | `fc.array(fc.oneof(partialMsg, finalMsg, errorMsg))` |
| 8: Stop finalization | `useLocalTranscription.ts` → reducer | `fc.string()` for interim text |

### Unit Tests (Vitest)

- Model loader: mock fetch/Cache API, test download flow, cache hit/miss, version mismatch
- Audio processor: test AudioWorklet registration, start/stop lifecycle
- Worker message handling: test each message type in isolation
- UI components: `ModelLoadingOverlay` renders correct ARIA attributes
- Accessibility: verify `aria-live`, `role="alert"`, contrast compliance

### Integration Tests

- End-to-end recording flow with mocked LiteRT runtime
- Offline model loading from pre-populated cache
- Acceleration fallback chain (WebGPU → WASM SIMD → WASM)
- Session persistence after recording stop

### Dependencies to Add

```json
{
  "devDependencies": {
    "fast-check": "^3.x",
    "vitest": "^3.x",
    "@vitest/coverage-v8": "^3.x"
  },
  "dependencies": {
    "@litertjs/core": "^latest"
  }
}
```

### Test File Structure

```
web/src/__tests__/
├── properties/
│   ├── modelLoader.property.test.ts
│   ├── audioProcessor.property.test.ts
│   ├── ringBuffer.property.test.ts
│   └── transcriptionEngine.property.test.ts
├── unit/
│   ├── modelLoader.test.ts
│   ├── audioProcessor.test.ts
│   ├── inferenceWorker.test.ts
│   └── ModelLoadingOverlay.test.ts
└── integration/
    ├── recordingFlow.test.ts
    └── offlineMode.test.ts
```
