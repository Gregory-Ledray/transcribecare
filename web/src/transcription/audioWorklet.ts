/**
 * AudioWorklet processor for real-time audio capture and resampling.
 *
 * Runs on the audio rendering thread. Receives raw PCM frames from the
 * microphone, resamples from device sample rate to 16kHz mono using
 * linear interpolation, buffers samples in a ring buffer, and posts
 * fixed-duration chunks to the main thread via MessagePort.
 *
 * This file is loaded as an AudioWorklet module via
 * `audioContext.audioWorklet.addModule()`. It cannot use standard ES
 * module imports, so the ring buffer logic is self-contained.
 */

/**
 * Resample an audio buffer from a source sample rate to a target sample rate
 * using linear interpolation.
 *
 * @param input - Source audio samples (Float32Array)
 * @param sourceRate - Source sample rate in Hz (e.g., 44100 or 48000)
 * @param targetRate - Target sample rate in Hz (e.g., 16000)
 * @returns Resampled audio buffer at the target sample rate
 */
export function resample(
  input: Float32Array,
  sourceRate: number,
  targetRate: number
): Float32Array {
  if (input.length === 0) {
    return new Float32Array(0);
  }

  if (sourceRate === targetRate) {
    return new Float32Array(input);
  }

  const ratio = sourceRate / targetRate;
  const outputLength = Math.floor(input.length * targetRate / sourceRate);
  const output = new Float32Array(outputLength);

  for (let i = 0; i < outputLength; i++) {
    const srcIndex = i * ratio;
    const indexFloor = Math.floor(srcIndex);
    const indexCeil = Math.min(indexFloor + 1, input.length - 1);
    const fraction = srcIndex - indexFloor;

    // Linear interpolation between adjacent samples
    const sample = input[indexFloor] * (1 - fraction) + input[indexCeil] * fraction;

    // Clamp output to [-1.0, 1.0]
    output[i] = Math.max(-1.0, Math.min(1.0, sample));
  }

  return output;
}

/**
 * Minimal ring buffer for use within the AudioWorklet context.
 * Self-contained since AudioWorklet modules cannot import from other files.
 */
class WorkletRingBuffer {
  private readonly buffer: Float32Array;
  private readonly capacity: number;
  private readPos: number = 0;
  private writePos: number = 0;
  private count: number = 0;

  constructor(capacity: number) {
    this.capacity = capacity;
    this.buffer = new Float32Array(capacity);
  }

  /** Write samples into the buffer, overwriting oldest data if full. */
  write(samples: Float32Array): void {
    const len = samples.length;
    if (len === 0) return;

    if (len >= this.capacity) {
      // Only keep the last `capacity` samples
      const offset = len - this.capacity;
      this.buffer.set(samples.subarray(offset));
      this.writePos = 0;
      this.readPos = 0;
      this.count = this.capacity;
      return;
    }

    const overflow = (this.count + len) - this.capacity;
    if (overflow > 0) {
      this.readPos = (this.readPos + overflow) % this.capacity;
      this.count -= overflow;
    }

    const spaceToEnd = this.capacity - this.writePos;
    if (len <= spaceToEnd) {
      this.buffer.set(samples, this.writePos);
    } else {
      this.buffer.set(samples.subarray(0, spaceToEnd), this.writePos);
      this.buffer.set(samples.subarray(spaceToEnd), 0);
    }

    this.writePos = (this.writePos + len) % this.capacity;
    this.count += len;
  }

  /** Read and consume samples from the buffer. */
  read(requestedCount: number): Float32Array {
    if (requestedCount > this.count) {
      throw new Error(
        `Cannot read ${requestedCount} samples: only ${this.count} available`
      );
    }

    const result = new Float32Array(requestedCount);
    const spaceToEnd = this.capacity - this.readPos;

    if (requestedCount <= spaceToEnd) {
      result.set(this.buffer.subarray(this.readPos, this.readPos + requestedCount));
    } else {
      result.set(this.buffer.subarray(this.readPos, this.capacity));
      result.set(this.buffer.subarray(0, requestedCount - spaceToEnd), spaceToEnd);
    }

    this.readPos = (this.readPos + requestedCount) % this.capacity;
    this.count -= requestedCount;
    return result;
  }

  /** Returns the number of unread samples in the buffer. */
  availableSamples(): number {
    return this.count;
  }
}

/**
 * AudioWorklet processor that captures microphone audio, resamples to 16kHz
 * mono, and posts fixed-duration chunks to the main thread.
 *
 * Processor options (passed via `processorOptions` in AudioWorkletNode constructor):
 * - `sourceSampleRate`: Device sample rate (e.g., 44100 or 48000)
 * - `targetSampleRate`: Target sample rate (always 16000)
 * - `chunkDurationMs`: Duration of each chunk in milliseconds (e.g., 1500)
 * - `ringBufferCapacity`: Maximum number of samples the ring buffer can hold
 */
class TranscriptionProcessor extends AudioWorkletProcessor {
  private readonly sourceSampleRate: number;
  private readonly targetSampleRate: number;
  private readonly chunkSamples: number;
  private readonly ringBuffer: WorkletRingBuffer;

  constructor(options: AudioWorkletNodeOptions) {
    super();

    const processorOptions = options.processorOptions as {
      sourceSampleRate: number;
      targetSampleRate: number;
      chunkDurationMs: number;
      ringBufferCapacity: number;
    };

    this.sourceSampleRate = processorOptions.sourceSampleRate;
    this.targetSampleRate = processorOptions.targetSampleRate;
    this.chunkSamples = Math.floor(
      processorOptions.targetSampleRate * processorOptions.chunkDurationMs / 1000
    );
    this.ringBuffer = new WorkletRingBuffer(processorOptions.ringBufferCapacity);
  }

  /**
   * Process incoming audio frames. Called by the audio rendering thread
   * with 128-sample blocks.
   */
  process(inputs: Float32Array[][]): boolean {
    const input = inputs[0];
    if (!input || input.length === 0 || !input[0] || input[0].length === 0) {
      return true;
    }

    // Take the first channel (mono)
    const monoInput = input[0];

    // Resample from device sample rate to target (16kHz)
    const resampled = resample(monoInput, this.sourceSampleRate, this.targetSampleRate);

    // Write resampled samples into the ring buffer
    this.ringBuffer.write(resampled);

    // When we have enough samples for a chunk, post it to the main thread
    while (this.ringBuffer.availableSamples() >= this.chunkSamples) {
      const chunk = this.ringBuffer.read(this.chunkSamples);
      this.port.postMessage(
        {
          type: 'chunk',
          data: chunk,
          timestamp: currentTime,
          sampleRate: this.targetSampleRate,
        },
        [chunk.buffer]
      );
    }

    // Return true to keep the processor alive
    return true;
  }
}

registerProcessor('transcription-processor', TranscriptionProcessor);
