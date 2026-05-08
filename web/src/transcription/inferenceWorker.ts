/**
 * Inference Worker for the Gemma 4 E2B transcription model.
 *
 * Runs LiteRT.js model inference in a dedicated Web Worker to keep
 * the main thread responsive. Implements WebGPU → WASM SIMD → WASM
 * acceleration fallback.
 */

import { loadModel } from './modelLoader';
import type {
  ModelLoaderConfig,
  ModelLoadProgress,
  WorkerInMessage,
  WorkerOutMessage,
} from './types';

// ---------------------------------------------------------------------------
// LiteRT Runtime Interfaces (placeholder until @litertjs/core is available)
// ---------------------------------------------------------------------------

/** Represents a compiled inference session capable of running the model. */
export interface LiteRTSession {
  /** Run inference on a Float32Array audio chunk, returning transcribed text. */
  infer(input: Float32Array): string;
  /** Dispose the session and free associated memory. */
  dispose(): void;
}

/** Options for loading and compiling a model with a specific accelerator. */
export interface LiteRTCompileOptions {
  accelerator: 'webgpu' | 'wasm';
}

/** The LiteRT runtime responsible for loading models and creating sessions. */
export interface LiteRTRuntime {
  /** Load model bytes and compile with the specified accelerator. */
  loadAndCompile(
    modelBytes: Uint8Array,
    options: LiteRTCompileOptions
  ): Promise<LiteRTSession>;
  /** Dispose the runtime and free all resources. */
  dispose(): void;
}

/**
 * Creates a LiteRT runtime instance.
 *
 * This is a placeholder that will be replaced with the real @litertjs/core
 * import when the package becomes available. The mock implementation allows
 * the worker message handling and fallback logic to be fully tested.
 */
export async function createLiteRTRuntime(): Promise<LiteRTRuntime> {
  // TODO: Replace with actual @litertjs/core import:
  // const { createRuntime } = await import('@litertjs/core');
  // return createRuntime();
  return {
    loadAndCompile: async (
      _modelBytes: Uint8Array,
      _options: LiteRTCompileOptions
    ): Promise<LiteRTSession> => {
      return {
        infer: (_input: Float32Array): string => '',
        dispose: () => {},
      };
    },
    dispose: () => {},
  };
}

// ---------------------------------------------------------------------------
// Worker State
// ---------------------------------------------------------------------------

let modelLoaded = false;
let session: LiteRTSession | null = null;
let runtime: LiteRTRuntime | null = null;
let backend: 'webgpu' | 'wasm-simd' | 'wasm' | null = null;
let pendingText = '';
let inferenceCount = 0;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Sentence boundary pattern: period, question mark, exclamation, or ellipsis followed by space or end. */
const SENTENCE_BOUNDARY = /[.?!…](?:\s|$)/;

/**
 * Posts a typed message back to the main thread.
 */
function postOutMessage(message: WorkerOutMessage): void {
  self.postMessage(message);
}

/**
 * Detects whether WebGPU is available in the current worker context.
 */
function isWebGPUAvailable(): boolean {
  return 'gpu' in self.navigator;
}

// ---------------------------------------------------------------------------
// Message Handlers
// ---------------------------------------------------------------------------

/**
 * Handles the 'init' message: loads the model, initializes the LiteRT runtime
 * with acceleration fallback, and reports the active backend.
 */
async function handleInit(config: ModelLoaderConfig): Promise<void> {
  try {
    // Load model bytes with progress reporting
    const modelBytes = await loadModel(config, (progress: ModelLoadProgress) => {
      postOutMessage({ type: 'progress', progress });
    });

    // Report initializing phase
    postOutMessage({
      type: 'progress',
      progress: { phase: 'initializing', percent: 0 },
    });

    // Create the LiteRT runtime
    runtime = await createLiteRTRuntime();

    // Acceleration fallback: WebGPU → WASM (SIMD auto-detected) → WASM
    session = await initializeWithFallback(runtime, modelBytes);

    modelLoaded = true;

    // Report active backend
    postOutMessage({ type: 'backend', backend: backend! });

    // Report ready
    postOutMessage({
      type: 'progress',
      progress: { phase: 'ready', percent: 100 },
    });
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : 'Failed to initialize inference runtime';
    postOutMessage({ type: 'error', message });
    postOutMessage({
      type: 'progress',
      progress: { phase: 'error', percent: 0, error: message },
    });
  }
}

/**
 * Attempts to initialize the model session with progressive fallback:
 * 1. WebGPU (if available)
 * 2. WASM with SIMD (auto-detected by the runtime)
 * 3. Standard WASM
 */
async function initializeWithFallback(
  rt: LiteRTRuntime,
  modelBytes: Uint8Array
): Promise<LiteRTSession> {
  // Attempt WebGPU
  if (isWebGPUAvailable()) {
    try {
      const sess = await rt.loadAndCompile(modelBytes, { accelerator: 'webgpu' });
      backend = 'webgpu';
      return sess;
    } catch {
      // WebGPU failed, fall through to WASM
    }
  }

  // Attempt WASM (XNNPack with SIMD auto-detected)
  try {
    const sess = await rt.loadAndCompile(modelBytes, { accelerator: 'wasm' });
    // Detect SIMD support to report the correct backend
    backend = detectSimdSupport() ? 'wasm-simd' : 'wasm';
    return sess;
  } catch {
    // WASM with SIMD failed, try without SIMD detection
  }

  // Final fallback: standard WASM
  try {
    const sess = await rt.loadAndCompile(modelBytes, { accelerator: 'wasm' });
    backend = 'wasm';
    return sess;
  } catch (error) {
    throw new Error(
      `All acceleration backends failed: ${error instanceof Error ? error.message : 'unknown error'}`
    );
  }
}

/**
 * Detects WebAssembly SIMD support by attempting to compile a minimal SIMD module.
 */
function detectSimdSupport(): boolean {
  try {
    // Minimal WASM module that uses a SIMD instruction (v128.const)
    const simdTest = new Uint8Array([
      0x00, 0x61, 0x73, 0x6d, // magic
      0x01, 0x00, 0x00, 0x00, // version
      0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7b, // type section: () -> v128
      0x03, 0x02, 0x01, 0x00, // function section
      0x0a, 0x0a, 0x01, 0x08, 0x00, 0xfd, 0x0c, // code section with v128.const
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x0b,
    ]);
    return WebAssembly.validate(simdTest);
  } catch {
    return false;
  }
}

/**
 * Handles the 'infer' message: runs inference on the audio chunk and posts
 * partial or final results based on sentence boundary detection.
 */
function handleInfer(chunk: Float32Array): void {
  if (!session || !modelLoaded) {
    postOutMessage({
      type: 'error',
      message: 'Cannot run inference: model not initialized',
    });
    return;
  }

  try {
    const text = session.infer(chunk);
    inferenceCount++;

    if (!text || text.trim().length === 0) {
      return;
    }

    // Accumulate text
    pendingText += text;

    // Check for sentence boundaries
    if (SENTENCE_BOUNDARY.test(pendingText)) {
      // Split at the last sentence boundary
      const lastBoundaryMatch = findLastSentenceBoundary(pendingText);

      if (lastBoundaryMatch !== -1) {
        const finalText = pendingText.slice(0, lastBoundaryMatch + 1).trim();
        pendingText = pendingText.slice(lastBoundaryMatch + 1).trimStart();

        if (finalText.length > 0) {
          postOutMessage({ type: 'final', text: finalText });
        }

        // Post remaining as partial if any
        if (pendingText.length > 0) {
          postOutMessage({ type: 'partial', text: pendingText });
        }
      }
    } else {
      // No sentence boundary yet, post as partial
      postOutMessage({ type: 'partial', text: pendingText });
    }
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : 'Inference error on audio chunk';
    postOutMessage({ type: 'error', message });
    // Continue processing — do not crash the worker
  }
}

/**
 * Finds the index of the last sentence boundary character in the text.
 * Returns -1 if no boundary is found.
 */
function findLastSentenceBoundary(text: string): number {
  let lastIndex = -1;
  for (let i = text.length - 1; i >= 0; i--) {
    const char = text[i];
    if (char === '.' || char === '?' || char === '!' || char === '…') {
      lastIndex = i;
      break;
    }
  }
  return lastIndex;
}

/**
 * Handles the 'finalize' message: flushes any pending inference state
 * and emits the remaining text as a final result.
 */
function handleFinalize(): void {
  if (pendingText.trim().length > 0) {
    postOutMessage({ type: 'final', text: pendingText.trim() });
  }
  pendingText = '';
}

/**
 * Handles the 'release' message: disposes the model session and runtime,
 * and clears all worker state.
 */
function handleRelease(): void {
  if (session) {
    session.dispose();
    session = null;
  }
  if (runtime) {
    runtime.dispose();
    runtime = null;
  }
  modelLoaded = false;
  backend = null;
  pendingText = '';
  inferenceCount = 0;
}

// ---------------------------------------------------------------------------
// Global Error Handlers (catch any unhandled errors in the worker)
// ---------------------------------------------------------------------------

self.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
  event.preventDefault();
  const message =
    event.reason instanceof Error
      ? event.reason.message
      : String(event.reason || 'Unhandled promise rejection in worker');
  postOutMessage({ type: 'error', message });
  postOutMessage({
    type: 'progress',
    progress: { phase: 'error', percent: 0, error: message },
  });
});

// ---------------------------------------------------------------------------
// Message Listener
// ---------------------------------------------------------------------------

self.onmessage = async (event: MessageEvent<WorkerInMessage>): Promise<void> => {
  const message = event.data;

  switch (message.type) {
    case 'init':
      await handleInit(message.config);
      break;

    case 'infer':
      handleInfer(message.chunk);
      break;

    case 'finalize':
      handleFinalize();
      break;

    case 'release':
      handleRelease();
      break;

    default:
      postOutMessage({
        type: 'error',
        message: `Unknown message type: ${(message as { type: string }).type}`,
      });
  }
};
