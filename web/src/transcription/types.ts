/**
 * Shared TypeScript interfaces and types for the Gemma 4 local transcription feature.
 * These types define the contracts between the audio pipeline, inference worker,
 * transcription engine, and UI components.
 */

/** Model loading states representing the lifecycle of model initialization. */
export type ModelStatus = 'idle' | 'downloading' | 'initializing' | 'ready' | 'error';

/** Progress information emitted during model download and initialization. */
export interface ModelProgress {
  /** Current status of the model loading process. */
  status: ModelStatus;
  /** Download progress from 0 to 100, only meaningful during 'downloading' status. */
  downloadPercent: number;
  /** Human-readable error message when status is 'error'. */
  errorMessage?: string;
}

/** Configuration for the model loader specifying which model files to download. */
export interface ModelLoaderConfig {
  /** HuggingFace repository identifier for the GGUF model. */
  modelRepo: string;
  /** Filename of the main model GGUF (or first split file). */
  modelFile: string;
  /** Filename of the multimodal projector GGUF. */
  mmprojFile: string;
}

/** Configuration for the audio capture and preprocessing pipeline. */
export interface AudioProcessorConfig {
  /** Target sample rate for transcription input (typically 16000 Hz). */
  targetSampleRate: number;
  /** Duration of each audio chunk in seconds for incremental processing. */
  chunkDurationSec: number;
}

/** A chunk of processed audio ready for inference. */
export interface AudioChunk {
  /** PCM float32 samples at the configured target sample rate, mono channel. */
  samples: Float32Array;
  /** Timestamp of chunk start relative to recording start, in milliseconds. */
  timestampMs: number;
}

/**
 * A discrete unit of transcribed text produced by the transcription engine.
 * Compatible with the existing TranscriptView component interface in App.tsx.
 */
export interface TranscriptSegment {
  /** Unique identifier for this segment (timestamp-based). */
  id: string;
  /** Transcribed text content. */
  text: string;
  /** Display classification for visual styling in the transcript view. */
  type: 'past' | 'recent' | 'current';
}

/** Combined configuration for the transcription engine. */
export interface TranscriptionEngineConfig {
  /** Model download and initialization configuration. */
  modelConfig: ModelLoaderConfig;
  /** Audio capture and preprocessing configuration. */
  audioConfig: AudioProcessorConfig;
}

/** Messages sent from the main thread to the inference worker. */
export type WorkerInMessage =
  | { type: 'init'; config: ModelLoaderConfig }
  | { type: 'transcribe'; chunk: AudioChunk }
  | { type: 'finalize' }
  | { type: 'is-cached'; config: ModelLoaderConfig };

/** Messages sent from the inference worker back to the main thread. */
export type WorkerOutMessage =
  | { type: 'model-progress'; progress: ModelProgress }
  | { type: 'transcript'; segment: TranscriptSegment }
  | { type: 'error'; message: string }
  | { type: 'cache-status'; cached: boolean };
