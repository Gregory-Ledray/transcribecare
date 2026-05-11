/**
 * Transcription Engine Hook — `useLocalTranscription`
 *
 * Orchestrates the local transcription pipeline: spawns the inference Web Worker,
 * manages audio capture via the AudioWorklet-based processor, and accumulates
 * transcript segments for the UI.
 *
 * Falls back to the native Web Speech API (SpeechRecognition) when the local
 * model is unavailable (e.g., model file not deployed yet).
 */

import { useState, useRef, useCallback, useEffect } from 'react';
import type { TranscriptSegment } from '../App';
import type {
  ModelLoaderConfig,
  ModelLoadProgress,
  AudioProcessorConfig,
  WorkerOutMessage,
  UseLocalTranscriptionReturn,
} from './types';
import { createAudioProcessor, type AudioProcessorHandle } from './audioProcessor';

// ---------------------------------------------------------------------------
// Default Configurations
// ---------------------------------------------------------------------------

const DEFAULT_MODEL_CONFIG: ModelLoaderConfig = {
  modelUrl: 'https://treatcost.com/models/gemma-4-E2B-it.litertlm',
  modelVersion: '1.0.0',
  cacheName: 'transcribecare-model-v1',
};

const DEFAULT_AUDIO_CONFIG: AudioProcessorConfig = {
  targetSampleRate: 16000,
  chunkDurationMs: 1500,
  ringBufferSizeMs: 10000,
};

// ---------------------------------------------------------------------------
// Hook Implementation
// ---------------------------------------------------------------------------

/**
 * React hook that manages the full local transcription lifecycle.
 * Attempts to use the local Gemma 4 model via LiteRT.js. If the model
 * fails to load, falls back to the native Web Speech API.
 *
 * @returns State and control functions for the transcription pipeline
 */
export function useLocalTranscription(): UseLocalTranscriptionReturn {
  // --- State ---
  const [status, setStatus] = useState<
    'idle' | 'loading' | 'ready' | 'recording' | 'error'
  >('idle');
  const [loadProgress, setLoadProgress] = useState<ModelLoadProgress>({
    phase: 'downloading',
    percent: 0,
  });
  const [segments, setSegments] = useState<TranscriptSegment[]>([]);
  const [interimText, setInterimText] = useState<string>('');
  const [backend, setBackend] = useState<'webgpu' | 'wasm-simd' | 'wasm' | null>(
    null
  );
  const [error, setError] = useState<string | null>(null);

  // --- Refs ---
  const workerRef = useRef<Worker | null>(null);
  const audioProcessorRef = useRef<AudioProcessorHandle | null>(null);
  const segmentCounterRef = useRef<number>(0);
  /** Tracks whether we're using the native Speech API fallback */
  const usingFallbackRef = useRef<boolean>(false);
  /** Reference to the native SpeechRecognition instance (fallback) */
  const recognitionRef = useRef<any>(null);
  /** Tracks user intent to keep recording (for SpeechRecognition auto-restart) */
  const isRecordingIntentRef = useRef<boolean>(false);

  // ---------------------------------------------------------------------------
  // Web Speech API Fallback
  // ---------------------------------------------------------------------------

  /**
   * Initializes the native SpeechRecognition API as a fallback.
   * Called automatically when the local model fails to load.
   */
  const initializeFallback = useCallback((): boolean => {
    const SpeechRecognition =
      (window as any).SpeechRecognition ||
      (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      console.debug('useLocalTranscription initializeFallback no SpeechRecognition API available')
      return false;
    }
    console.debug('useLocalTranscription initializeFallback setup beginning')

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = 'en-US';

    recognition.onresult = (event: any) => {
      let finalTranscript = '';
      let currentInterim = '';

      for (let i = event.resultIndex; i < event.results.length; ++i) {
        if (event.results[i].isFinal) {
          finalTranscript += event.results[i][0].transcript;
        } else {
          currentInterim += event.results[i][0].transcript;
        }
      }

      if (finalTranscript) {
        const newSegmentId = `seg-${++segmentCounterRef.current}-${Date.now()}`;
        setSegments((prev) => {
          const promoted = prev.map((seg) =>
            seg.type === 'current' ? { ...seg, type: 'recent' as const } : seg
          );
          return [
            ...promoted,
            { id: newSegmentId, text: finalTranscript.trim(), type: 'current' as const },
          ];
        });
      }
      setInterimText(currentInterim);
    };

    recognition.onerror = (event: any) => {
      console.error('[useLocalTranscription] SpeechRecognition error:', event.error);
      if (event.error !== 'no-speech' && event.error !== 'aborted') {
        setStatus('ready');
        isRecordingIntentRef.current = false;
      }
    };

    recognition.onend = () => {
      if (isRecordingIntentRef.current) {
        try {
          recognition.start();
        } catch (e) {
          console.error('[useLocalTranscription] Error restarting recognition:', e);
        }
      }
    };

    recognitionRef.current = recognition;
    usingFallbackRef.current = true;
    return true;
  }, []);

  // ---------------------------------------------------------------------------
  // Worker Message Handler
  // ---------------------------------------------------------------------------

  const handleWorkerMessage = useCallback((event: MessageEvent<WorkerOutMessage>) => {
    const message = event.data;

    console.debug(`useLocalTranscription handleWorkerMessage ${JSON.stringify(message)}`)
    switch (message.type) {
      case 'progress':
        setLoadProgress(message.progress);
        if (message.progress.phase === 'ready') {
          setStatus('ready');
        } else if (message.progress.phase === 'error') {
          // Model failed to load — fall back to native Speech API
          console.warn(
            '[useLocalTranscription] Local model unavailable, falling back to Web Speech API:',
            message.progress.error
          );
          // Terminate the failed worker
          if (workerRef.current) {
            workerRef.current.terminate();
            workerRef.current = null;
          }
          // Attempt fallback
          const fallbackOk = initializeFallback();
          if (fallbackOk) {
            setStatus('ready');
            setError(null);
            setBackend(null);
          } else {
            setStatus('error');
            setError(
              'Local model unavailable and Web Speech API not supported in this browser.'
            );
          }
        }
        break;

      case 'backend':
        setBackend(message.backend);
        break;

      case 'partial':
        setInterimText(message.text);
        break;

      case 'final': {
        const newSegmentId = `seg-${++segmentCounterRef.current}-${Date.now()}`;

        setSegments((prev) => {
          const promoted = prev.map((seg) =>
            seg.type === 'current' ? { ...seg, type: 'recent' as const } : seg
          );
          const newSegment: TranscriptSegment = {
            id: newSegmentId,
            text: message.text,
            type: 'current',
          };
          return [...promoted, newSegment];
        });

        setInterimText('');
        break;
      }

      case 'error':
        // Log error but remain in 'recording' state — continue processing
        console.error('[useLocalTranscription] Worker error:', message.message);
        break;
    }
  }, [initializeFallback]);

  // ---------------------------------------------------------------------------
  // initialize()
  // ---------------------------------------------------------------------------

  const initialize = useCallback(async (): Promise<void> => {
    if (workerRef.current || usingFallbackRef.current) {
      // Already initialized or in progress
      return;
    }

    console.debug(`useLocalTranscription initialize started`)

    setStatus('loading');
    setError(null);
    setLoadProgress({ phase: 'downloading', percent: 0 });

    // Spawn the inference Web Worker
    let worker: Worker;
    try {
      worker = new Worker(
        new URL('./inferenceWorker.ts', import.meta.url),
        { type: 'module' }
      );
    } catch (e) {
      // Worker failed to instantiate — fall back immediately
      console.warn('[useLocalTranscription] Worker instantiation failed, using fallback:', e);
      const fallbackOk = initializeFallback();
      if (fallbackOk) {
        setStatus('ready');
        setError(null);
      } else {
        setStatus('error');
        setError('Local model unavailable and Web Speech API not supported.');
      }
      return;
    }

    worker.onmessage = handleWorkerMessage;

    worker.onerror = (event) => {
      event.preventDefault();
      console.warn('[useLocalTranscription] Worker error, falling back to Web Speech API');
      // Terminate the broken worker
      worker.terminate();
      workerRef.current = null;
      // Attempt fallback
      const fallbackOk = initializeFallback();
      if (fallbackOk) {
        setStatus('ready');
        setError(null);
      } else {
        setStatus('error');
        setError('Local model unavailable and Web Speech API not supported.');
      }
    };

    workerRef.current = worker;

    // Send init message to begin model loading
    worker.postMessage({ type: 'init', config: DEFAULT_MODEL_CONFIG });
  }, [handleWorkerMessage, initializeFallback]);

  // ---------------------------------------------------------------------------
  // start()
  // ---------------------------------------------------------------------------

  const start = useCallback(async (): Promise<void> => {
    if (status !== 'ready') {
      throw new Error(`Cannot start recording: status is '${status}', expected 'ready'`);
    }

    setStatus('recording');

    // --- Fallback path: use native SpeechRecognition ---
    if (usingFallbackRef.current && recognitionRef.current) {
      console.debug(`useLocalTranscription start SpeechRecognition`)
      isRecordingIntentRef.current = true;
      // Move existing segments to 'past'
      setSegments((prev) =>
        prev.filter((s) => s.type !== 'past').map((s) => ({ ...s, type: 'past' as const }))
      );
      try {
        recognitionRef.current.start();
      } catch (e) {
        console.error('[useLocalTranscription] Error starting SpeechRecognition:', e);
        setStatus('ready');
        isRecordingIntentRef.current = false;
        throw new Error('Could not start speech recognition. Please try again.');
      }
      return;
    }

    // --- Local model path ---
    console.debug(`useLocalTranscription start Gemma4`)
    if (!workerRef.current) {
      throw new Error('Cannot start recording: model not initialized');
    }

    const processor = createAudioProcessor(DEFAULT_AUDIO_CONFIG);

    processor.onChunk = (chunk: Float32Array) => {
      if (workerRef.current) {
        workerRef.current.postMessage(
          { type: 'infer', chunk },
          [chunk.buffer]
        );
      }
    };

    audioProcessorRef.current = processor;
    await processor.start();
  }, [status]);

  // ---------------------------------------------------------------------------
  // stop()
  // ---------------------------------------------------------------------------

  const stop = useCallback((): void => {
    console.debug(`useLocalTranscription stop`)

    // --- Fallback path: stop native SpeechRecognition ---
    if (usingFallbackRef.current && recognitionRef.current) {
      isRecordingIntentRef.current = false;
      recognitionRef.current.stop();

      // Finalize interim text
      setInterimText((currentInterim) => {
        if (currentInterim.trim().length > 0) {
          const finalSegmentId = `seg-${++segmentCounterRef.current}-${Date.now()}`;
          setSegments((prev) => {
            const promoted = prev.map((seg) =>
              seg.type === 'current' ? { ...seg, type: 'recent' as const } : seg
            );
            return [
              ...promoted,
              { id: finalSegmentId, text: currentInterim.trim(), type: 'current' as const },
            ];
          });
        }
        return '';
      });

      setStatus('ready');
      return;
    }

    // --- Local model path ---
    if (workerRef.current) {
      workerRef.current.postMessage({ type: 'finalize' });
    }

    if (audioProcessorRef.current) {
      audioProcessorRef.current.stop();
      audioProcessorRef.current = null;
    }

    setInterimText((currentInterim) => {
      if (currentInterim.trim().length > 0) {
        const finalSegmentId = `seg-${++segmentCounterRef.current}-${Date.now()}`;
        const finalSegment: TranscriptSegment = {
          id: finalSegmentId,
          text: currentInterim.trim(),
          type: 'current',
        };

        setSegments((prev) => {
          const promoted = prev.map((seg) =>
            seg.type === 'current' ? { ...seg, type: 'recent' as const } : seg
          );
          return [...promoted, finalSegment];
        });
      } else {
        setSegments((prev) =>
          prev.map((seg) =>
            seg.type === 'current' ? { ...seg, type: 'recent' as const } : seg
          )
        );
      }

      return '';
    });

    setStatus('ready');
  }, []);

  // ---------------------------------------------------------------------------
  // retry()
  // ---------------------------------------------------------------------------

  const retry = useCallback((): void => {
    console.debug(`useLocalTranscription retry`)

    // Clean up existing worker if any
    if (workerRef.current) {
      workerRef.current.terminate();
      workerRef.current = null;
    }

    // Clean up fallback
    if (recognitionRef.current) {
      try { recognitionRef.current.abort(); } catch (_) {}
      recognitionRef.current = null;
    }
    usingFallbackRef.current = false;

    // Reset error state
    setError(null);
    setStatus('idle');

    // Re-attempt initialization
    initialize();
  }, [initialize]);

  // ---------------------------------------------------------------------------
  // Cleanup on unmount
  // ---------------------------------------------------------------------------

  useEffect(() => {
    return () => {
      console.debug(`useLocalTranscription unmount cleanup`)
      // Stop audio processor
      if (audioProcessorRef.current) {
        audioProcessorRef.current.stop();
        audioProcessorRef.current = null;
      }

      // Terminate worker
      if (workerRef.current) {
        workerRef.current.postMessage({ type: 'release' });
        workerRef.current.terminate();
        workerRef.current = null;
      }

      // Clean up fallback recognition
      if (recognitionRef.current) {
        isRecordingIntentRef.current = false;
        try { recognitionRef.current.abort(); } catch (_) {}
        recognitionRef.current = null;
      }
    };
  }, []);

  // ---------------------------------------------------------------------------
  // Return
  // ---------------------------------------------------------------------------

  return {
    status,
    loadProgress,
    segments,
    interimText,
    backend,
    error,
    initialize,
    start,
    stop,
    retry,
  };
}
