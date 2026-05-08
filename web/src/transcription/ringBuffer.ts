/**
 * Fixed-capacity ring buffer for Float32Array audio samples.
 *
 * Used in the AudioWorklet to store resampled 16kHz mono PCM samples
 * with bounded memory. When the buffer is full, new writes overwrite
 * the oldest data by advancing the read pointer.
 */
export class RingBuffer {
  private readonly buffer: Float32Array;
  private readonly capacity: number;
  private readPos: number = 0;
  private writePos: number = 0;
  private count: number = 0;

  /**
   * Create a ring buffer with a fixed maximum capacity.
   * @param capacity Maximum number of Float32 samples the buffer can hold.
   */
  constructor(capacity: number) {
    if (capacity <= 0 || !Number.isInteger(capacity)) {
      throw new Error('Ring buffer capacity must be a positive integer');
    }
    this.capacity = capacity;
    this.buffer = new Float32Array(capacity);
  }

  /**
   * Write samples into the buffer. If writing would exceed capacity,
   * the oldest unread samples are overwritten (read pointer advances).
   */
  write(samples: Float32Array): void {
    const len = samples.length;

    if (len === 0) {
      return;
    }

    if (len >= this.capacity) {
      // If incoming data is larger than or equal to capacity, only keep the last `capacity` samples
      const offset = len - this.capacity;
      this.buffer.set(samples.subarray(offset));
      this.writePos = 0;
      this.readPos = 0;
      this.count = this.capacity;
      return;
    }

    // Calculate how many samples would overflow
    const overflow = (this.count + len) - this.capacity;
    if (overflow > 0) {
      // Advance read pointer past the overwritten samples
      this.readPos = (this.readPos + overflow) % this.capacity;
      this.count -= overflow;
    }

    // Write samples, handling wrap-around
    const spaceToEnd = this.capacity - this.writePos;
    if (len <= spaceToEnd) {
      this.buffer.set(samples, this.writePos);
    } else {
      // Split write across the wrap boundary
      this.buffer.set(samples.subarray(0, spaceToEnd), this.writePos);
      this.buffer.set(samples.subarray(spaceToEnd), 0);
    }

    this.writePos = (this.writePos + len) % this.capacity;
    this.count += len;
  }

  /**
   * Read and consume up to `count` samples from the buffer.
   * @param count Number of samples to read.
   * @returns A new Float32Array containing the requested samples.
   * @throws Error if requesting more samples than are available.
   */
  read(count: number): Float32Array {
    if (count < 0 || !Number.isInteger(count)) {
      throw new Error('Read count must be a non-negative integer');
    }

    if (count === 0) {
      return new Float32Array(0);
    }

    if (count > this.count) {
      throw new Error(
        `Cannot read ${count} samples: only ${this.count} available`
      );
    }

    const result = new Float32Array(count);
    const spaceToEnd = this.capacity - this.readPos;

    if (count <= spaceToEnd) {
      result.set(this.buffer.subarray(this.readPos, this.readPos + count));
    } else {
      // Split read across the wrap boundary
      result.set(this.buffer.subarray(this.readPos, this.capacity));
      result.set(this.buffer.subarray(0, count - spaceToEnd), spaceToEnd);
    }

    this.readPos = (this.readPos + count) % this.capacity;
    this.count -= count;
    return result;
  }

  /** Returns the number of unread samples currently in the buffer. */
  availableSamples(): number {
    return this.count;
  }

  /** Reset the buffer state, discarding all unread samples. */
  clear(): void {
    this.readPos = 0;
    this.writePos = 0;
    this.count = 0;
  }
}
