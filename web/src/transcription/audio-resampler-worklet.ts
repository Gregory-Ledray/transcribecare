/**
 * AudioWorkletProcessor that performs real-time resampling from the device's
 * native sample rate to 16kHz mono PCM float32 for the Gemma 4 transcription engine.
 *
 * Registered as 'audio-resampler-worklet'.
 *
 * This file runs in the AudioWorklet scope and must NOT import from other modules.
 */

/**
 * Processor options passed during AudioWorkletNode construction.
 */
interface ResamplerProcessorOptions {
  targetSampleRate?: number;
  chunkSize?: number;
}

/**
 * AudioResamplerProcessor performs:
 * 1. Stereo-to-mono downmix (averages left and right channels)
 * 2. Linear interpolation resampling from native sample rate to targetSampleRate
 * 3. Buffering of resampled samples into fixed-size chunks
 * 4. Posting Float32Array chunks to the main thread via MessagePort
 */
class AudioResamplerProcessor extends AudioWorkletProcessor {
  private targetSampleRate: number;
  private chunkSize: number;
  private buffer: Float32Array;
  private bufferOffset: number;
  private fractionalPosition: number;

  constructor(options?: AudioWorkletNodeOptions) {
    super();

    const processorOptions = (options?.processorOptions ?? {}) as ResamplerProcessorOptions;
    this.targetSampleRate = processorOptions.targetSampleRate ?? 16000;
    this.chunkSize = processorOptions.chunkSize ?? 16000 * 5; // Default: 5 seconds at 16kHz
    this.buffer = new Float32Array(this.chunkSize);
    this.bufferOffset = 0;
    this.fractionalPosition = 0;
  }

  /**
   * Process incoming audio frames. Called by the audio rendering thread.
   *
   * @param inputs - Array of inputs, each containing channels of Float32Array (128 samples each)
   * @returns true to keep the processor alive
   */
  process(inputs: Float32Array[][]): boolean {
    const input = inputs[0];
    if (!input || input.length === 0 || !input[0]) {
      return true;
    }

    // Downmix to mono: average all channels
    const monoSamples = this.downmixToMono(input);

    // Resample from native sample rate to target sample rate using linear interpolation
    const resampled = this.resample(monoSamples);

    // Buffer resampled samples and post chunks when full
    this.bufferSamples(resampled);

    return true;
  }

  /**
   * Downmix multi-channel audio to mono by averaging all channels.
   * If already mono, returns the single channel directly.
   */
  private downmixToMono(channels: Float32Array[]): Float32Array {
    if (channels.length === 1) {
      return channels[0];
    }

    // Average all channels for mono output
    const frameLength = channels[0].length;
    const mono = new Float32Array(frameLength);
    const channelCount = channels.length;

    for (let i = 0; i < frameLength; i++) {
      let sum = 0;
      for (let ch = 0; ch < channelCount; ch++) {
        sum += channels[ch][i];
      }
      mono[i] = sum / channelCount;
    }

    return mono;
  }

  /**
   * Resample audio from the native sample rate to the target sample rate
   * using linear interpolation. Maintains fractional position state between
   * calls for seamless continuity across process() invocations.
   */
  private resample(inputSamples: Float32Array): Float32Array {
    // sampleRate is a global in AudioWorkletGlobalScope representing the context's sample rate
    const nativeSampleRate = sampleRate;
    const ratio = nativeSampleRate / this.targetSampleRate;

    // Calculate the number of output samples for this input block
    const inputLength = inputSamples.length;
    const outputLength = Math.floor((inputLength - this.fractionalPosition) / ratio) + 1;

    if (outputLength <= 0) {
      // Not enough input samples to produce output; advance fractional position
      this.fractionalPosition -= inputLength;
      return new Float32Array(0);
    }

    const output = new Float32Array(outputLength);
    let outputIndex = 0;

    for (let i = 0; outputIndex < outputLength; i++) {
      const position = this.fractionalPosition + i * ratio;

      if (position >= inputLength) {
        break;
      }

      const index = Math.floor(position);
      const fraction = position - index;

      if (index + 1 < inputLength) {
        // Linear interpolation between adjacent samples
        output[outputIndex] = inputSamples[index] * (1 - fraction) + inputSamples[index + 1] * fraction;
      } else {
        // At the last sample, use it directly
        output[outputIndex] = inputSamples[index];
      }

      outputIndex++;
    }

    // Update fractional position for the next call
    // The next call starts where we left off relative to the next input block
    this.fractionalPosition = (this.fractionalPosition + outputIndex * ratio) - inputLength;

    // Return only the samples we actually produced
    return outputIndex < outputLength ? output.subarray(0, outputIndex) : output;
  }

  /**
   * Buffer resampled samples and post complete chunks to the main thread
   * via the MessagePort when the buffer reaches chunkSize.
   */
  private bufferSamples(samples: Float32Array): void {
    let samplesOffset = 0;

    while (samplesOffset < samples.length) {
      const remaining = this.chunkSize - this.bufferOffset;
      const available = samples.length - samplesOffset;
      const toCopy = Math.min(remaining, available);

      this.buffer.set(samples.subarray(samplesOffset, samplesOffset + toCopy), this.bufferOffset);
      this.bufferOffset += toCopy;
      samplesOffset += toCopy;

      if (this.bufferOffset >= this.chunkSize) {
        // Post a copy of the full chunk to the main thread
        const chunk = this.buffer.slice(0, this.chunkSize);
        this.port.postMessage(chunk, [chunk.buffer]);

        // Reset buffer for next chunk
        this.buffer = new Float32Array(this.chunkSize);
        this.bufferOffset = 0;
      }
    }
  }
}

registerProcessor('audio-resampler-worklet', AudioResamplerProcessor);
