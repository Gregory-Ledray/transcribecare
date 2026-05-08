/**
 * Web Worker hosting the wllama instance for local Gemma 4 inference.
 *
 * Handles message types from the main thread:
 * - `init`: Downloads/caches the model and initializes the WASM runtime
 * - `transcribe`: Runs inference on an audio chunk and emits transcript segments
 * - `finalize`: Processes any remaining buffered audio and emits a final segment
 * - `is-cached`: Checks if the model is already cached without triggering download
 *
 * Communicates back via WorkerOutMessage posts (model-progress, transcript, error, cache-status).
 */

// Polyfill: wllama's `absoluteUrl` utility references `document.baseURI` which
// doesn't exist in a Web Worker context. Provide a shim so URL resolution works.
if (typeof document === 'undefined') {
  (self as any).document = { baseURI: self.location?.href ?? '/' };
}

import { Wllama } from '@wllama/wllama';
import type {
  AudioChunk,
  ModelLoaderConfig,
  ModelProgress,
  TranscriptSegment,
  WorkerInMessage,
  WorkerOutMessage,
} from './types';

/** Wllama instance — initialized on `init` message. */
let wllama: Wllama | null = null;

/** Counter for generating unique segment IDs. */
let segmentCounter = 0;

/** Buffer for partial audio that hasn't been processed yet. */
let audioBuffer: Float32Array | null = null;

/** Whether the model is fully loaded and ready for inference. */
let modelReady = false;

/** Maximum number of retry attempts for transient network errors. */
const MAX_RETRY_ATTEMPTS = 3;

/** Base delay in milliseconds for exponential backoff (1 second). */
const BASE_RETRY_DELAY_MS = 1000;

/**
 * Post a typed message back to the main thread.
 */
function postOutMessage(message: WorkerOutMessage): void {
  self.postMessage(message);
}

/**
 * Generate a unique segment ID using timestamp and counter.
 */
function generateSegmentId(): string {
  return `seg-${Date.now()}-${segmentCounter++}`;
}

/**
 * Post a model progress update to the main thread.
 */
function postProgress(progress: ModelProgress): void {
  postOutMessage({ type: 'model-progress', progress });
}

/**
 * Post an error message to the main thread.
 */
function postError(message: string): void {
  postOutMessage({ type: 'error', message });
}

/**
 * Determine whether an error is transient (network-related) and eligible for retry.
 * Non-transient errors (OOM, quota exceeded, WASM failures) should not be retried.
 */
export function isTransientError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'QuotaExceededError') {
    return false;
  }

  if (error instanceof Error) {
    const message = error.message.toLowerCase();
    const name = error.name.toLowerCase();

    // Permanent errors — do NOT retry
    if (
      message.includes('out of memory') ||
      message.includes('oom') ||
      message.includes('allocation failed') ||
      message.includes('quota') ||
      message.includes('quotaexceedederror') ||
      message.includes('wasm') ||
      message.includes('webassembly') ||
      message.includes('compile') ||
      name === 'quotaexceedederror'
    ) {
      return false;
    }

    // Network-related errors are transient
    if (
      message.includes('network') ||
      message.includes('fetch') ||
      message.includes('failed to fetch') ||
      message.includes('networkerror') ||
      message.includes('timeout') ||
      message.includes('aborted') ||
      name === 'networkerror' ||
      (name === 'typeerror' && message.includes('fetch'))
    ) {
      return true;
    }
  }

  // Default: not transient (don't retry unknown errors)
  return false;
}

/**
 * Determine whether an error is a QuotaExceededError from IndexedDB/OPFS.
 */
export function isQuotaExceededError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'QuotaExceededError') {
    return true;
  }
  if (error instanceof Error) {
    const message = error.message.toLowerCase();
    const name = error.name.toLowerCase();
    if (
      name === 'quotaexceedederror' ||
      message.includes('quotaexceedederror') ||
      (message.includes('quota') && message.includes('exceeded'))
    ) {
      return true;
    }
  }
  return false;
}

/**
 * Sleep for a given number of milliseconds.
 */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Build the model URL from the loader config.
 */
function buildModelUrl(config: ModelLoaderConfig): string {
  return `https://huggingface.co/${config.modelRepo}/resolve/main/${config.modelFile}`;
}

/**
 * Check if the model is already cached in OPFS via wllama's ModelManager.
 * Uses the provided Wllama instance to avoid OPFS file handle conflicts
 * that occur when multiple Wllama instances access the same files.
 */
async function checkModelCached(config: ModelLoaderConfig, instance?: Wllama | null): Promise<boolean> {
  try {
    const wllamaToUse = instance ?? new Wllama({
      'single-thread/wllama.wasm': new URL(
        '../../node_modules/@wllama/wllama/esm/single-thread/wllama.wasm',
        import.meta.url
      ).toString(),
    });

    const modelUrl = buildModelUrl(config);
    const models = await wllamaToUse.modelManager.getModels();
    return models.some((m) => m.url === modelUrl);
  } catch {
    return false;
  }
}

/**
 * Handle the `is-cached` message: check if the model is cached and respond.
 * Uses the main wllama instance if available, otherwise creates a temporary one.
 */
async function handleIsCached(config: ModelLoaderConfig): Promise<void> {
  const cached = await checkModelCached(config, wllama);
  postOutMessage({ type: 'cache-status', cached });
}

/**
 * Initialize the wllama instance, download/cache the model, and report progress.
 *
 * Uses wllama's built-in OPFS caching — subsequent loads skip the download.
 * SIMD is handled internally by wllama based on browser capabilities.
 *
 * Implements:
 * - Cache detection: skips download progress if model is already cached
 * - Retry logic with exponential backoff for transient network errors (max 3 attempts)
 * - QuotaExceededError handling with clear user messaging
 */
async function handleInit(config: ModelLoaderConfig): Promise<void> {
  postProgress({ status: 'downloading', downloadPercent: 0 });

  try {
    // Configure WASM asset paths — wllama selects single-thread or multi-thread
    // based on SharedArrayBuffer availability (SIMD is handled internally).
    wllama = new Wllama(
      {
        'single-thread/wllama.wasm': new URL(
          '../../node_modules/@wllama/wllama/esm/single-thread/wllama.wasm',
          import.meta.url
        ).toString(),
        'multi-thread/wllama.wasm': new URL(
          '../../node_modules/@wllama/wllama/esm/multi-thread/wllama.wasm',
          import.meta.url
        ).toString(),
      },
      {
        allowOffline: true,
      }
    );

    const modelUrl = buildModelUrl(config);

    // Check if model is already cached — report full progress immediately if so.
    // Pass the main wllama instance to avoid OPFS file handle conflicts.
    const cached = await checkModelCached(config, wllama);
    if (cached) {
      postProgress({ status: 'downloading', downloadPercent: 100 });
    }

    // Attempt model loading with retry logic for transient network errors
    let lastError: unknown = null;

    for (let attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        await wllama.loadModelFromUrl(modelUrl, {
          n_ctx: 2048,
          n_batch: 512,
          progressCallback: ({ loaded, total }) => {
            const percent = total > 0 ? Math.round((loaded / total) * 100) : 0;
            postProgress({ status: 'downloading', downloadPercent: percent });
          },
        });

        // Success — break out of retry loop
        lastError = null;
        break;
      } catch (error: unknown) {
        lastError = error;

        // QuotaExceededError — fail immediately with specific message
        if (isQuotaExceededError(error)) {
          modelReady = false;
          const errorMessage =
            'Insufficient storage space for the transcription model. Please clear browser data or free up at least 3 GB of disk space.';
          postProgress({
            status: 'error',
            downloadPercent: 0,
            errorMessage,
          });
          postError(errorMessage);
          return;
        }

        // Non-transient error — fail immediately, no retry
        if (!isTransientError(error)) {
          break;
        }

        // Transient error — retry with exponential backoff if attempts remain
        if (attempt < MAX_RETRY_ATTEMPTS) {
          const delayMs = BASE_RETRY_DELAY_MS * Math.pow(2, attempt - 1);
          await sleep(delayMs);
        }
      }
    }

    // If we exhausted retries or hit a non-transient error, report failure
    if (lastError) {
      modelReady = false;
      const errorMessage = classifyAndFormatError(lastError);
      postProgress({
        status: 'error',
        downloadPercent: 0,
        errorMessage,
      });
      postError(errorMessage);
      return;
    }

    postProgress({ status: 'initializing', downloadPercent: 100 });

    // TODO: Load the multimodal projector (mmproj) for audio encoding.
    // The exact API for loading mmproj alongside the main model in wllama
    // needs to be confirmed once Gemma 4 E2B audio input format is finalized.
    // For now, the main text model is loaded and ready for text-based completion.
    // Audio chunks will be encoded as a text prompt describing the audio input.

    modelReady = true;
    postProgress({ status: 'ready', downloadPercent: 100 });
  } catch (error: unknown) {
    modelReady = false;

    // Handle QuotaExceededError at the top level as well
    if (isQuotaExceededError(error)) {
      const errorMessage =
        'Insufficient storage space for the transcription model. Please clear browser data or free up at least 3 GB of disk space.';
      postProgress({
        status: 'error',
        downloadPercent: 0,
        errorMessage,
      });
      postError(errorMessage);
      return;
    }

    const errorMessage = classifyAndFormatError(error);
    postProgress({
      status: 'error',
      downloadPercent: 0,
      errorMessage,
    });
    postError(errorMessage);
  }
}

/**
 * Run inference on an audio chunk and emit a transcript segment.
 *
 * TODO: Once the Gemma 4 E2B audio input format is confirmed, this function
 * should encode the PCM audio data into the model's expected multimodal input
 * format (likely via the mmproj encoder). Currently uses a text-based prompt
 * as a placeholder for the audio-to-text pipeline.
 */
async function handleTranscribe(chunk: AudioChunk): Promise<void> {
  if (!wllama || !modelReady) {
    postError('Model is not initialized. Call init before transcribe.');
    return;
  }

  try {
    // Buffer the incoming audio chunk
    if (audioBuffer) {
      const combined = new Float32Array(audioBuffer.length + chunk.samples.length);
      combined.set(audioBuffer);
      combined.set(chunk.samples, audioBuffer.length);
      audioBuffer = combined;
    } else {
      audioBuffer = new Float32Array(chunk.samples);
    }

    // Process the buffered audio through inference
    // TODO: Replace this text prompt with actual audio encoding via mmproj
    // once the Gemma 4 E2B multimodal audio API is confirmed.
    const prompt = buildTranscriptionPrompt(audioBuffer);

    const result = await wllama.createCompletion(prompt, {});

    const text = typeof result === 'string' ? result : '';

    if (text.trim().length > 0) {
      const segment: TranscriptSegment = {
        id: generateSegmentId(),
        text: text.trim(),
        type: 'current',
      };
      postOutMessage({ type: 'transcript', segment });
    }

    // Clear the buffer after successful processing
    audioBuffer = null;
  } catch (error: unknown) {
    const errorMessage = classifyAndFormatError(error);
    postError(errorMessage);
  }
}

/**
 * Process any remaining buffered audio and emit a final transcript segment.
 * Called when the recording session ends.
 */
async function handleFinalize(): Promise<void> {
  if (!wllama || !modelReady) {
    postError('Model is not initialized. Cannot finalize.');
    return;
  }

  try {
    if (audioBuffer && audioBuffer.length > 0) {
      // Process remaining buffered audio
      const prompt = buildTranscriptionPrompt(audioBuffer);

      const result = await wllama.createCompletion(prompt, {});

      const text = typeof result === 'string' ? result : '';

      if (text.trim().length > 0) {
        const segment: TranscriptSegment = {
          id: generateSegmentId(),
          text: text.trim(),
          type: 'current',
        };
        postOutMessage({ type: 'transcript', segment });
      }

      audioBuffer = null;
    }
  } catch (error: unknown) {
    const errorMessage = classifyAndFormatError(error);
    postError(errorMessage);
  }
}

/**
 * Build a transcription prompt from audio samples.
 *
 * TODO: This is a placeholder. Once the Gemma 4 E2B audio multimodal input
 * format is confirmed, this should encode the Float32Array PCM data into
 * the format expected by the model's audio encoder (via mmproj).
 * The actual implementation will likely involve passing raw audio bytes
 * to the model's multimodal input mechanism rather than a text prompt.
 */
function buildTranscriptionPrompt(samples: Float32Array): string {
  const durationSec = samples.length / 16000;
  return `<start_of_turn>user\nTranscribe the following ${durationSec.toFixed(1)} seconds of audio speech to text. Output only the transcription, nothing else.<end_of_turn>\n<start_of_turn>model\n`;
}

/**
 * Classify an error and return a user-friendly message.
 * Handles OOM, network failures, quota exceeded, and initialization errors.
 */
export function classifyAndFormatError(error: unknown): string {
  if (error instanceof DOMException && error.name === 'QuotaExceededError') {
    return 'Insufficient storage space for the transcription model. Please clear browser data or free up at least 3 GB of disk space.';
  }

  if (error instanceof Error) {
    const message = error.message.toLowerCase();

    // Out of memory errors
    if (
      message.includes('out of memory') ||
      message.includes('oom') ||
      message.includes('memory') ||
      message.includes('allocation failed')
    ) {
      return 'Insufficient memory for local transcription. Try closing other browser tabs to free up memory.';
    }

    // Network errors
    if (
      message.includes('network') ||
      message.includes('fetch') ||
      message.includes('failed to fetch') ||
      message.includes('networkerror') ||
      message.includes('offline')
    ) {
      return 'Network error while downloading the model. Please check your internet connection and try again.';
    }

    // Storage quota errors
    if (
      message.includes('quota') ||
      message.includes('quotaexceedederror') ||
      (message.includes('storage') && message.includes('exceeded'))
    ) {
      return 'Insufficient storage space for the transcription model. Please clear browser data or free up at least 3 GB of disk space.';
    }

    // WASM/initialization errors
    if (
      message.includes('wasm') ||
      message.includes('webassembly') ||
      message.includes('compile')
    ) {
      return 'Failed to initialize the transcription engine. Your browser may not support WebAssembly features required for local transcription.';
    }

    return `Transcription error: ${error.message}`;
  }

  return 'An unexpected error occurred during transcription.';
}

/**
 * Main message handler for the worker.
 * Routes incoming messages to the appropriate handler function.
 */
self.onmessage = async (event: MessageEvent<WorkerInMessage>) => {
  const message = event.data;

  switch (message.type) {
    case 'init':
      await handleInit(message.config);
      break;

    case 'transcribe':
      await handleTranscribe(message.chunk);
      break;

    case 'finalize':
      await handleFinalize();
      break;

    case 'is-cached':
      await handleIsCached(message.config);
      break;

    default:
      postError(`Unknown message type: ${(message as { type: string }).type}`);
  }
};
