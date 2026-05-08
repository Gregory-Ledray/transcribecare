/**
 * TranscriptionEngine orchestrates the full transcription pipeline:
 * model loading → audio capture → inference → transcript segments.
 *
 * This is the high-level API consumed by App.tsx, bridging the AudioProcessor
 * (microphone capture + resampling) with the InferenceWorker (wllama-based
 * Gemma 4 inference running in a Web Worker).
 */

import { AudioProcessor } from './audio-processor';
import type {
  AudioProcessorConfig,
  ModelLoaderConfig,
  ModelProgress,
  ModelStatus,
  TranscriptSegment,
  TranscriptionEngineConfig,
  WorkerInMessage,
  WorkerOutMessage,
} from './types';

/** Error message displayed when offline and model is not cached. */
const OFFLINE_NO_CACHE_MESSAGE =
  'An internet connection is required for the initial model download. Please connect to the internet and try again.';

/** Default model configuration for Gemma 4 E2B Q4_K_M quantization. */
const DEFAULT_MODEL_CONFIG: ModelLoaderConfig = {
  modelRepo: 'ggml-org/gemma-3-4b-it-GGUF',
  modelFile: 'gemma-3-4b-it-Q4_K_M.gguf',
  mmprojFile: 'mmproj-BF16.gguf',
};

/** Default audio processing configuration. */
const DEFAULT_AUDIO_CONFIG: AudioProcessorConfig = {
  targetSampleRate: 16000,
  chunkDurationSec: 5,
};

/** Maximum number of worker crash restarts before giving up. */
const MAX_WORKER_CRASHES = 3;

/** Inference timeout per chunk in milliseconds (10 seconds). */
const INFERENCE_TIMEOUT_MS = 10000;

/**
 * TranscriptionEngine manages the full lifecycle of local transcription:
 * model initialization, audio capture, inference, and segment emission.
 */
export class TranscriptionEngine {
  private modelConfig: ModelLoaderConfig;
  private audioConfig: AudioProcessorConfig;

  private worker: Worker | null = null;
  private audioProcessor: AudioProcessor | null = null;

  private modelStatus: ModelStatus = 'idle';
  private segmentCallbacks: Array<(segment: TranscriptSegment) => void> = [];
  private modelStatusCallbacks: Array<(progress: ModelProgress) => void> = [];
  private errorCallbacks: Array<(error: string) => void> = [];

  private sessionSegments: TranscriptSegment[] = [];
  private sessionActive = false;

  /** Promise resolve/reject for loadModel() awaiting 'ready' or 'error' status. */
  private loadModelResolve: (() => void) | null = null;
  private loadModelReject: ((error: Error) => void) | null = null;

  /** Promise resolve for stopSession() awaiting the final segment after finalize. */
  private finalizeResolve: (() => void) | null = null;
  private finalizeTimer: ReturnType<typeof setTimeout> | null = null;

  /** Promise resolve for isModelCached() awaiting 'cache-status' response. */
  private cacheStatusResolve: ((cached: boolean) => void) | null = null;

  /** Whether the last error was due to being offline without a cached model. */
  private offlineErrorPending = false;

  /** Bound handler references for online/offline events (for cleanup). */
  private handleOnline: (() => void) | null = null;
  private handleOffline: (() => void) | null = null;

  /** Number of times the worker has crashed during the current engine lifecycle. */
  private workerCrashCount = 0;

  /** Timer for inference timeout — cleared when a transcript response arrives. */
  private inferenceTimer: ReturnType<typeof setTimeout> | null = null;

  /** Whether the model was successfully initialized (for restart purposes). */
  private modelInitialized = false;

  constructor(config: Partial<TranscriptionEngineConfig> = {}) {
    this.modelConfig = config.modelConfig ?? DEFAULT_MODEL_CONFIG;
    this.audioConfig = config.audioConfig ?? DEFAULT_AUDIO_CONFIG;
    this.setupConnectivityListeners();
  }

  /**
   * Initialize the transcription model by spawning the inference worker
   * and downloading/loading the model. Resolves when the model is ready
   * for inference, or rejects if loading fails.
   *
   * Handles offline scenarios:
   * - If offline and model is cached: loads from cache (wllama allowOffline)
   * - If offline and model is NOT cached: emits error with offline message
   * - If online: proceeds with normal download/load
   */
  async loadModel(): Promise<void> {
    // Spawn the inference worker using Vite's Web Worker import syntax
    this.worker = new Worker(
      new URL('./inference-worker.ts', import.meta.url),
      { type: 'module' }
    );

    // Set up message routing from the worker
    this.worker.onmessage = (event: MessageEvent<WorkerOutMessage>) => {
      this.handleWorkerMessage(event.data);
    };

    this.worker.onerror = (event: ErrorEvent) => {
      this.handleWorkerCrash(event);
    };

    // Check offline status before attempting to load
    if (!navigator.onLine) {
      const cached = await this.isModelCached();
      if (!cached) {
        // Offline and no cache — cannot proceed
        this.offlineErrorPending = true;
        this.modelStatus = 'error';
        const progress: ModelProgress = {
          status: 'error',
          downloadPercent: 0,
          errorMessage: OFFLINE_NO_CACHE_MESSAGE,
        };
        this.notifyModelStatus(progress);
        return Promise.reject(new Error(OFFLINE_NO_CACHE_MESSAGE));
      }
      // Offline but cached — proceed, wllama's allowOffline will load from cache
    }

    this.offlineErrorPending = false;

    // Send init message to the worker and wait for ready/error
    return new Promise<void>((resolve, reject) => {
      this.loadModelResolve = resolve;
      this.loadModelReject = reject;

      const message: WorkerInMessage = { type: 'init', config: this.modelConfig };
      this.worker!.postMessage(message);
    });
  }

  /**
   * Start a transcription session. Creates an AudioProcessor, begins
   * microphone capture, and forwards resampled audio chunks to the
   * inference worker for transcription.
   */
  async startSession(): Promise<void> {
    if (!this.worker || this.modelStatus !== 'ready') {
      throw new Error('Model is not loaded. Call loadModel() first.');
    }

    this.sessionSegments = [];
    this.sessionActive = true;

    // Create and configure the AudioProcessor
    this.audioProcessor = new AudioProcessor(this.audioConfig);

    // Forward audio chunks to the inference worker with timeout
    this.audioProcessor.onChunk((chunk) => {
      if (this.worker && this.sessionActive) {
        const message: WorkerInMessage = { type: 'transcribe', chunk };
        this.worker.postMessage(message);
        this.startInferenceTimeout();
      }
    });

    // Start audio capture
    await this.audioProcessor.start();
  }

  /**
   * Stop the current transcription session. Stops audio capture, sends
   * a finalize message to the worker to process remaining buffered audio,
   * and returns the collected audio blob and transcript segments.
   *
   * If the worker has crashed, still stops the AudioProcessor and returns
   * whatever audio was captured — recording is never lost even if inference fails.
   */
  async stopSession(): Promise<{ audioBlob: Blob; segments: TranscriptSegment[] }> {
    if (!this.audioProcessor || !this.sessionActive) {
      throw new Error('No active session to stop.');
    }

    this.sessionActive = false;

    // Clear any pending inference timeout
    this.clearInferenceTimeout();

    // Stop the AudioProcessor and get the WebM audio blob.
    // This always succeeds — MediaRecorder runs independently of the worker.
    const { audioBlob } = await this.audioProcessor.stop();
    this.audioProcessor = null;

    // Send finalize to the worker and wait briefly for the final segment.
    // If the worker is dead (crashed and not restarted), skip finalization.
    if (this.worker) {
      await this.waitForFinalize();
    }

    const segments = [...this.sessionSegments];
    return { audioBlob, segments };
  }

  /**
   * Subscribe to transcript segment emissions. Each segment is delivered
   * as it is produced by the inference worker during an active session.
   */
  onSegment(callback: (segment: TranscriptSegment) => void): void {
    this.segmentCallbacks.push(callback);
  }

  /**
   * Subscribe to model status/progress updates. Receives progress during
   * download, initialization, and status transitions.
   */
  onModelStatus(callback: (progress: ModelProgress) => void): void {
    this.modelStatusCallbacks.push(callback);
  }

  /**
   * Subscribe to runtime error notifications. Receives error messages
   * for OOM, worker crashes, and other runtime failures that the UI
   * should surface to the user.
   */
  onError(callback: (error: string) => void): void {
    this.errorCallbacks.push(callback);
  }

  /**
   * Get the current model status.
   */
  getModelStatus(): ModelStatus {
    return this.modelStatus;
  }

  /**
   * Query the worker to determine if the model is already cached.
   * Sends an `is-cached` message and waits for the `cache-status` response.
   */
  async isModelCached(): Promise<boolean> {
    if (!this.worker) {
      // Worker not yet spawned — spawn a temporary one for the cache check
      this.worker = new Worker(
        new URL('./inference-worker.ts', import.meta.url),
        { type: 'module' }
      );
      this.worker.onmessage = (event: MessageEvent<WorkerOutMessage>) => {
        this.handleWorkerMessage(event.data);
      };
    }

    return new Promise<boolean>((resolve) => {
      this.cacheStatusResolve = resolve;
      const message: WorkerInMessage = { type: 'is-cached', config: this.modelConfig };
      this.worker!.postMessage(message);
    });
  }

  /**
   * Route incoming worker messages to the appropriate handler.
   */
  private handleWorkerMessage(message: WorkerOutMessage): void {
    switch (message.type) {
      case 'model-progress':
        this.handleModelProgress(message.progress);
        break;

      case 'transcript':
        this.handleTranscriptSegment(message.segment);
        break;

      case 'error':
        this.handleWorkerError(message.message);
        break;

      case 'cache-status':
        this.handleCacheStatus(message.cached);
        break;
    }
  }

  /**
   * Handle model progress updates from the worker.
   * Updates internal status and notifies subscribers.
   * Resolves or rejects the loadModel() promise on terminal states.
   */
  private handleModelProgress(progress: ModelProgress): void {
    this.modelStatus = progress.status;
    this.notifyModelStatus(progress);

    if (progress.status === 'ready' && this.loadModelResolve) {
      this.modelInitialized = true;
      this.loadModelResolve();
      this.loadModelResolve = null;
      this.loadModelReject = null;
    }

    if (progress.status === 'error' && this.loadModelReject) {
      this.loadModelReject(
        new Error(progress.errorMessage ?? 'Model loading failed.')
      );
      this.loadModelResolve = null;
      this.loadModelReject = null;
    }
  }

  /**
   * Handle a transcript segment from the worker.
   * Assigns a unique timestamp-based ID, tracks it in the session,
   * and notifies all segment subscribers. Clears the inference timeout
   * since a response was received.
   */
  private handleTranscriptSegment(segment: TranscriptSegment): void {
    // Clear inference timeout — response arrived in time
    this.clearInferenceTimeout();

    // Generate a unique timestamp-based ID for the segment
    const segmentWithId: TranscriptSegment = {
      ...segment,
      id: generateSegmentId(),
    };

    this.sessionSegments.push(segmentWithId);
    this.notifySegment(segmentWithId);

    // If we're waiting for finalize, resolve immediately on receiving a segment
    if (this.finalizeResolve) {
      this.finalizeResolve();
      this.finalizeResolve = null;
      if (this.finalizeTimer) {
        clearTimeout(this.finalizeTimer);
        this.finalizeTimer = null;
      }
    }
  }

  /**
   * Handle error messages from the worker.
   * Detects OOM/memory errors and surfaces them to the UI via onError callbacks.
   */
  private handleWorkerError(errorMessage: string): void {
    console.error('[TranscriptionEngine] Worker error:', errorMessage);

    // Detect memory-related errors and surface to UI
    const lowerMessage = errorMessage.toLowerCase();
    if (
      lowerMessage.includes('memory') ||
      lowerMessage.includes('oom') ||
      lowerMessage.includes('insufficient memory') ||
      lowerMessage.includes('allocation failed')
    ) {
      this.notifyError(errorMessage);
      this.modelStatus = 'error';
      this.notifyModelStatus({
        status: 'error',
        downloadPercent: 0,
        errorMessage,
      });
    }
  }

  /**
   * Handle the cache-status response from the worker.
   * Resolves the pending isModelCached() promise.
   */
  private handleCacheStatus(cached: boolean): void {
    if (this.cacheStatusResolve) {
      this.cacheStatusResolve(cached);
      this.cacheStatusResolve = null;
    }
  }

  /**
   * Set up online/offline event listeners to handle connectivity changes.
   * When coming back online after an offline error, automatically retries loadModel().
   */
  private setupConnectivityListeners(): void {
    this.handleOnline = () => {
      // If the model failed to load because we were offline, retry now
      if (this.offlineErrorPending && this.modelStatus === 'error') {
        this.offlineErrorPending = false;
        this.loadModel().catch(() => {
          // Error is surfaced via onModelStatus callback
        });
      }
    };

    this.handleOffline = () => {
      // If model is already loaded ('ready'), no action needed.
      // The model runs entirely locally once initialized.
    };

    window.addEventListener('online', this.handleOnline);
    window.addEventListener('offline', this.handleOffline);
  }

  /**
   * Send a finalize message to the worker and wait briefly for the
   * final transcript segment. Resolves after receiving a segment or
   * after a timeout (to avoid hanging indefinitely).
   */
  private waitForFinalize(): Promise<void> {
    return new Promise<void>((resolve) => {
      this.finalizeResolve = resolve;

      const message: WorkerInMessage = { type: 'finalize' };
      this.worker!.postMessage(message);

      // Timeout after 5 seconds to avoid hanging if no final segment arrives
      this.finalizeTimer = setTimeout(() => {
        if (this.finalizeResolve) {
          this.finalizeResolve();
          this.finalizeResolve = null;
          this.finalizeTimer = null;
        }
      }, 5000);
    });
  }

  /**
   * Notify all model status subscribers.
   */
  private notifyModelStatus(progress: ModelProgress): void {
    for (const callback of this.modelStatusCallbacks) {
      callback(progress);
    }
  }

  /**
   * Notify all segment subscribers.
   */
  private notifySegment(segment: TranscriptSegment): void {
    for (const callback of this.segmentCallbacks) {
      callback(segment);
    }
  }

  /**
   * Notify all error subscribers.
   */
  private notifyError(error: string): void {
    for (const callback of this.errorCallbacks) {
      callback(error);
    }
  }

  /**
   * Handle a worker crash (onerror event). Attempts to restart the worker
   * up to MAX_WORKER_CRASHES times. If the limit is exceeded, sets status
   * to error and notifies subscribers.
   *
   * The AudioProcessor (MediaRecorder) continues running independently —
   * only inference is lost during a crash.
   */
  private handleWorkerCrash(event: ErrorEvent): void {
    this.workerCrashCount++;
    const errorMessage = `Worker crashed: ${event.message}`;
    console.error(`[TranscriptionEngine] ${errorMessage} (crash ${this.workerCrashCount}/${MAX_WORKER_CRASHES})`);

    // Clear any pending inference timeout
    this.clearInferenceTimeout();

    // Terminate the crashed worker
    if (this.worker) {
      this.worker.terminate();
      this.worker = null;
    }

    // If we're still in the initial loadModel phase, reject the promise
    if (this.loadModelReject) {
      this.loadModelReject(new Error(errorMessage));
      this.loadModelReject = null;
      this.loadModelResolve = null;
    }

    if (this.workerCrashCount >= MAX_WORKER_CRASHES) {
      // Too many crashes — give up
      const fatalMessage =
        'The transcription engine has crashed repeatedly. Please reload the page.';
      this.modelStatus = 'error';
      this.notifyModelStatus({
        status: 'error',
        downloadPercent: 0,
        errorMessage: fatalMessage,
      });
      this.notifyError(fatalMessage);
    } else if (this.modelInitialized) {
      // Attempt to respawn the worker and re-initialize the model
      this.attemptWorkerRestart();
    }
  }

  /**
   * Attempt to respawn the inference worker and re-initialize the model
   * after a crash. The AudioProcessor continues running during this process.
   */
  private attemptWorkerRestart(): void {
    console.warn('[TranscriptionEngine] Attempting worker restart...');

    this.worker = new Worker(
      new URL('./inference-worker.ts', import.meta.url),
      { type: 'module' }
    );

    this.worker.onmessage = (event: MessageEvent<WorkerOutMessage>) => {
      this.handleWorkerMessage(event.data);
    };

    this.worker.onerror = (event: ErrorEvent) => {
      this.handleWorkerCrash(event);
    };

    // Re-initialize the model in the new worker
    const message: WorkerInMessage = { type: 'init', config: this.modelConfig };
    this.worker.postMessage(message);
  }

  /**
   * Start the inference timeout timer. If no transcript response arrives
   * within INFERENCE_TIMEOUT_MS, the chunk is skipped and the session continues.
   */
  private startInferenceTimeout(): void {
    // Clear any existing timer (in case chunks arrive faster than inference)
    this.clearInferenceTimeout();

    this.inferenceTimer = setTimeout(() => {
      console.warn(
        '[TranscriptionEngine] Inference timeout: chunk skipped after',
        INFERENCE_TIMEOUT_MS,
        'ms'
      );
      this.inferenceTimer = null;
      // Don't stop the session — just skip this chunk and continue
    }, INFERENCE_TIMEOUT_MS);
  }

  /**
   * Clear the inference timeout timer (called when a transcript arrives).
   */
  private clearInferenceTimeout(): void {
    if (this.inferenceTimer) {
      clearTimeout(this.inferenceTimer);
      this.inferenceTimer = null;
    }
  }
}

/** Counter for ensuring unique segment IDs within the same millisecond. */
let segmentIdCounter = 0;

/**
 * Generate a unique timestamp-based ID for a TranscriptSegment.
 * Format: `ts-{timestamp}-{counter}` to guarantee uniqueness.
 */
function generateSegmentId(): string {
  return `ts-${Date.now()}-${segmentIdCounter++}`;
}
