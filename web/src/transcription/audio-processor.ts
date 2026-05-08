/**
 * AudioProcessor manages the audio capture pipeline for real-time transcription.
 *
 * Responsibilities:
 * - Captures microphone audio via getUserMedia
 * - Routes audio through an AudioWorklet for 16kHz mono resampling
 * - Simultaneously records WebM audio via MediaRecorder for playback
 * - Delivers resampled PCM chunks to subscribers via onChunk callback
 */

import type { AudioChunk, AudioProcessorConfig } from './types';

/** Default configuration for audio processing. */
const DEFAULT_CONFIG: AudioProcessorConfig = {
  targetSampleRate: 16000,
  chunkDurationSec: 5,
};

/**
 * AudioProcessor captures microphone audio and produces both:
 * 1. Resampled PCM Float32 chunks (via AudioWorklet) for transcription
 * 2. A WebM audio blob (via MediaRecorder) for playback/storage
 */
export class AudioProcessor {
  private config: AudioProcessorConfig;
  private chunkCallback: ((chunk: AudioChunk) => void) | null = null;
  private mediaStream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private workletNode: AudioWorkletNode | null = null;
  private mediaRecorder: MediaRecorder | null = null;
  private recordedBlobs: Blob[] = [];
  private chunks: AudioChunk[] = [];
  private recordingStartTime: number = 0;
  private chunkIndex: number = 0;

  constructor(config: Partial<AudioProcessorConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Register a callback to receive audio chunks as they are produced
   * by the AudioWorklet resampler.
   */
  onChunk(callback: (chunk: AudioChunk) => void): void {
    this.chunkCallback = callback;
  }

  /**
   * Start the audio capture pipeline:
   * 1. Request microphone access
   * 2. Create AudioContext and register the resampler worklet
   * 3. Connect the microphone source to the worklet
   * 4. Start MediaRecorder for WebM capture
   *
   * @throws Error with accessible message for permission denied or no microphone
   */
  async start(): Promise<void> {
    let stream: MediaStream;

    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error: unknown) {
      if (error instanceof DOMException) {
        if (error.name === 'NotAllowedError') {
          throw new Error(
            'Microphone access was denied. Please allow microphone access in your browser settings to use transcription.'
          );
        }
        if (error.name === 'NotFoundError') {
          throw new Error(
            'No microphone detected. Please connect a microphone and try again.'
          );
        }
      }
      throw new Error(
        `Failed to access microphone: ${error instanceof Error ? error.message : String(error)}`
      );
    }

    this.mediaStream = stream;
    this.recordingStartTime = Date.now();
    this.chunks = [];
    this.chunkIndex = 0;
    this.recordedBlobs = [];

    // Create AudioContext
    this.audioContext = new AudioContext();

    // Register the AudioWorklet module (Vite resolves the URL)
    const workletUrl = new URL('./audio-resampler-worklet.ts', import.meta.url);
    await this.audioContext.audioWorklet.addModule(workletUrl);

    // Create the worklet node with resampling configuration
    const chunkSize = this.config.targetSampleRate * this.config.chunkDurationSec;
    this.workletNode = new AudioWorkletNode(this.audioContext, 'audio-resampler-worklet', {
      processorOptions: {
        targetSampleRate: this.config.targetSampleRate,
        chunkSize,
      },
    });

    // Listen for resampled audio chunks from the worklet
    this.workletNode.port.onmessage = (event: MessageEvent<Float32Array>) => {
      const samples = event.data;
      const timestampMs = (this.chunkIndex * this.config.chunkDurationSec * 1000);
      const chunk: AudioChunk = { samples, timestampMs };

      this.chunks.push(chunk);
      this.chunkIndex++;

      if (this.chunkCallback) {
        this.chunkCallback(chunk);
      }
    };

    // Connect microphone source → worklet
    const source = this.audioContext.createMediaStreamSource(stream);
    source.connect(this.workletNode);

    // Start MediaRecorder for WebM capture
    this.mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
    this.mediaRecorder.ondataavailable = (event: BlobEvent) => {
      if (event.data.size > 0) {
        this.recordedBlobs.push(event.data);
      }
    };
    this.mediaRecorder.start();
  }

  /**
   * Stop the audio capture pipeline and return the captured data.
   *
   * @returns Object containing the WebM audio blob and all collected audio chunks
   */
  async stop(): Promise<{ audioBlob: Blob; chunks: AudioChunk[] }> {
    // Disconnect the worklet node
    if (this.workletNode) {
      this.workletNode.disconnect();
      this.workletNode.port.onmessage = null;
      this.workletNode = null;
    }

    // Stop MediaRecorder and wait for final data
    const audioBlob = await this.stopMediaRecorder();

    // Stop all media stream tracks
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach((track) => track.stop());
      this.mediaStream = null;
    }

    // Close the AudioContext
    if (this.audioContext) {
      await this.audioContext.close();
      this.audioContext = null;
    }

    return { audioBlob, chunks: this.chunks };
  }

  /**
   * Stop the MediaRecorder and collect the final WebM blob.
   * Returns a promise that resolves once the recorder has flushed all data.
   */
  private stopMediaRecorder(): Promise<Blob> {
    return new Promise((resolve) => {
      if (!this.mediaRecorder || this.mediaRecorder.state === 'inactive') {
        resolve(new Blob(this.recordedBlobs, { type: 'audio/webm' }));
        return;
      }

      this.mediaRecorder.onstop = () => {
        const blob = new Blob(this.recordedBlobs, { type: 'audio/webm' });
        this.mediaRecorder = null;
        resolve(blob);
      };

      this.mediaRecorder.stop();
    });
  }
}
