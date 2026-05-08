/**
 * Public API for the Gemma 4 local transcription module.
 *
 * Re-exports the TranscriptionEngine class and all shared types
 * needed by consuming components (App.tsx, ModelStatusIndicator, etc.).
 */

export { TranscriptionEngine } from './transcription-engine';
export { ModelStatusIndicator } from './model-status-indicator';
export type { ModelStatusIndicatorProps } from './model-status-indicator';
export type {
  AudioChunk,
  AudioProcessorConfig,
  ModelLoaderConfig,
  ModelProgress,
  ModelStatus,
  TranscriptSegment,
  TranscriptionEngineConfig,
  WorkerInMessage,
  WorkerOutMessage,
} from './types';
