/**
 * Shared type definitions for the local transcription pipeline.
 *
 * These interfaces define the contracts between the Model Loader,
 * Audio Processor, Inference Worker, and Transcription Engine modules.
 */

// Re-export TranscriptSegment from App.tsx for use within the transcription module
export type { TranscriptSegment } from '../App';

/** Configuration for downloading and caching the Gemma 4 E2B model artifact. */
export interface ModelLoaderConfig {
  /** CDN URL for the .tflite artifact */
  modelUrl: string;
  /** Semantic version for cache invalidation */
  modelVersion: string;
  /** Cache API storage name */
  cacheName: string;
}

/** Progress state during model loading phases. */
export interface ModelLoadProgress {
  phase: 'downloading' | 'validating' | 'initializing' | 'ready' | 'error';
  /** Percentage complete, bounded to 0–100 */
  percent: number;
  /** Error description when phase is 'error' */
  error?: string;
}

/** Configuration for the audio capture and processing pipeline. */
export interface AudioProcessorConfig {
  /** Target sample rate for model input (always 16kHz) */
  targetSampleRate: 16000;
  /** Duration of each audio chunk in milliseconds (e.g., 1500ms) */
  chunkDurationMs: number;
  /** Maximum ring buffer size in milliseconds (bounded memory, e.g., 10000ms) */
  ringBufferSizeMs: number;
}

/** Message format for audio chunks transferred from AudioWorklet to main thread. */
export interface AudioChunkMessage {
  type: 'chunk';
  /** PCM audio data transferred via zero-copy */
  data: Float32Array;
  /** Performance.now() timestamp at capture time */
  timestamp: number;
  /** Sample rate of the chunk data (always 16kHz) */
  sampleRate: 16000;
}

/** Messages sent from the main thread to the inference worker. */
export type WorkerInMessage =
  | { type: 'init'; config: ModelLoaderConfig }
  | { type: 'infer'; chunk: Float32Array }
  | { type: 'finalize' }
  | { type: 'release' };

/** Messages sent from the inference worker back to the main thread. */
export type WorkerOutMessage =
  | { type: 'progress'; progress: ModelLoadProgress }
  | { type: 'backend'; backend: 'webgpu' | 'wasm-simd' | 'wasm' }
  | { type: 'partial'; text: string }
  | { type: 'final'; text: string }
  | { type: 'error'; message: string };

/** Metadata stored alongside the cached model artifact for integrity and versioning. */
export interface ModelCacheMetadata {
  /** Semantic version of the cached model */
  version: string;
  /** SHA-256 hash of the model binary for integrity validation */
  sha256: string;
  /** ISO 8601 timestamp of when the model was downloaded */
  downloadedAt: string;
  /** Size of the model artifact in bytes */
  sizeBytes: number;
}

/** Current state of the local transcription pipeline. */
export interface TranscriptionState {
  /** Overall pipeline status */
  status: 'idle' | 'loading' | 'ready' | 'recording' | 'error';
  /** Model loading progress details */
  loadProgress: ModelLoadProgress;
  /** Accumulated transcript segments */
  segments: import('../App').TranscriptSegment[];
  /** Current interim (partial) transcription text */
  interimText: string;
  /** Active inference backend, null if not yet determined */
  backend: 'webgpu' | 'wasm-simd' | 'wasm' | null;
  /** Error message, null if no error */
  error: string | null;
}

/** Return type of the useLocalTranscription hook. */
export interface UseLocalTranscriptionReturn extends TranscriptionState {
  /** Initialize the model and inference worker */
  initialize(): Promise<void>;
  /** Start recording and transcribing */
  start(): Promise<void>;
  /** Stop recording and finalize pending text */
  stop(): void;
  /** Retry after an error */
  retry(): void;
}
