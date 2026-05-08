/**
 * Audio processor handle for managing microphone capture and AudioWorklet pipeline.
 *
 * Creates and manages the Web Audio API graph:
 * MediaStream (mic) → MediaStreamAudioSourceNode → AudioWorkletNode (resampling + chunking)
 *
 * The AudioWorklet processor resamples to 16kHz mono and posts fixed-duration
 * chunks back to the main thread via MessagePort.
 */

import type { AudioProcessorConfig, AudioChunkMessage } from './types';

/** Handle returned by createAudioProcessor for controlling the audio pipeline. */
export interface AudioProcessorHandle {
  /** Start capturing audio from the microphone. */
  start(): Promise<void>;
  /** Stop capturing and release all resources. */
  stop(): void;
  /** Callback invoked when the AudioWorklet posts a chunk of 16kHz mono PCM data. */
  onChunk: (chunk: Float32Array) => void;
}

/**
 * Create an audio processor that captures microphone input, resamples to 16kHz
 * mono via an AudioWorklet, and delivers fixed-duration chunks through the
 * `onChunk` callback.
 *
 * @param config - Audio processing configuration (sample rate, chunk duration, buffer size)
 * @returns An AudioProcessorHandle to start/stop capture and receive chunks
 */
export function createAudioProcessor(config: AudioProcessorConfig): AudioProcessorHandle {
  let audioContext: AudioContext | null = null;
  let mediaStream: MediaStream | null = null;
  let sourceNode: MediaStreamAudioSourceNode | null = null;
  let workletNode: AudioWorkletNode | null = null;

  const handle: AudioProcessorHandle = {
    onChunk: () => {},

    async start(): Promise<void> {
      // Request microphone access
      try {
        mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      } catch (err: unknown) {
        if (err instanceof DOMException && err.name === 'NotAllowedError') {
          throw new Error(
            'Microphone permission denied. Please allow microphone access in your browser settings to use transcription.'
          );
        }
        throw new Error(
          `Failed to access microphone: ${err instanceof Error ? err.message : 'Unknown error'}`
        );
      }

      // Create AudioContext
      audioContext = new AudioContext();

      // Register the AudioWorklet module
      const workletUrl = new URL('./audioWorklet.ts', import.meta.url).href;
      await audioContext.audioWorklet.addModule(workletUrl);

      // Create source node from the media stream
      sourceNode = audioContext.createMediaStreamSource(mediaStream);

      // Calculate ring buffer capacity in samples
      const ringBufferCapacity = Math.floor(
        config.targetSampleRate * config.ringBufferSizeMs / 1000
      );

      // Create AudioWorkletNode with processor options
      workletNode = new AudioWorkletNode(audioContext, 'transcription-processor', {
        processorOptions: {
          sourceSampleRate: audioContext.sampleRate,
          targetSampleRate: config.targetSampleRate,
          chunkDurationMs: config.chunkDurationMs,
          ringBufferCapacity,
        },
      });

      // Listen for chunk messages from the worklet
      workletNode.port.onmessage = (event: MessageEvent<AudioChunkMessage>) => {
        if (event.data && event.data.type === 'chunk') {
          handle.onChunk(event.data.data);
        }
      };

      // Connect the audio graph: mic source → worklet node
      sourceNode.connect(workletNode);
    },

    stop(): void {
      // Disconnect the worklet node
      if (workletNode) {
        workletNode.disconnect();
        workletNode.port.onmessage = null;
        workletNode = null;
      }

      // Disconnect source node
      if (sourceNode) {
        sourceNode.disconnect();
        sourceNode = null;
      }

      // Stop all media stream tracks
      if (mediaStream) {
        for (const track of mediaStream.getTracks()) {
          track.stop();
        }
        mediaStream = null;
      }

      // Close the AudioContext
      if (audioContext) {
        audioContext.close();
        audioContext = null;
      }
    },
  };

  return handle;
}
