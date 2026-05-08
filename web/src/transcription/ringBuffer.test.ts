import { describe, it, expect } from 'vitest';
import { RingBuffer } from './ringBuffer';

describe('RingBuffer', () => {
  describe('constructor', () => {
    it('creates a buffer with the specified capacity', () => {
      const buf = new RingBuffer(1024);
      expect(buf.availableSamples()).toBe(0);
    });

    it('throws on non-positive capacity', () => {
      expect(() => new RingBuffer(0)).toThrow();
      expect(() => new RingBuffer(-1)).toThrow();
    });

    it('throws on non-integer capacity', () => {
      expect(() => new RingBuffer(1.5)).toThrow();
    });
  });

  describe('write and read', () => {
    it('writes and reads back samples correctly', () => {
      const buf = new RingBuffer(10);
      const data = new Float32Array([1, 2, 3, 4, 5]);
      buf.write(data);

      expect(buf.availableSamples()).toBe(5);
      const result = buf.read(5);
      expect(Array.from(result)).toEqual([1, 2, 3, 4, 5]);
      expect(buf.availableSamples()).toBe(0);
    });

    it('handles multiple writes followed by a single read', () => {
      const buf = new RingBuffer(10);
      buf.write(new Float32Array([1, 2, 3]));
      buf.write(new Float32Array([4, 5, 6]));

      const result = buf.read(6);
      expect(Array.from(result)).toEqual([1, 2, 3, 4, 5, 6]);
    });

    it('handles interleaved writes and reads', () => {
      const buf = new RingBuffer(8);
      buf.write(new Float32Array([1, 2, 3]));
      expect(Array.from(buf.read(2))).toEqual([1, 2]);

      buf.write(new Float32Array([4, 5]));
      expect(buf.availableSamples()).toBe(3);
      expect(Array.from(buf.read(3))).toEqual([3, 4, 5]);
    });

    it('handles wrap-around correctly', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([1, 2, 3]));
      buf.read(3); // readPos now at 3

      buf.write(new Float32Array([4, 5, 6])); // wraps around
      const result = buf.read(3);
      expect(Array.from(result)).toEqual([4, 5, 6]);
    });

    it('returns empty array for read(0)', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([1, 2]));
      const result = buf.read(0);
      expect(result.length).toBe(0);
      expect(buf.availableSamples()).toBe(2);
    });
  });

  describe('overwrite behavior', () => {
    it('overwrites oldest data when buffer is full', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([1, 2, 3, 4]));
      buf.write(new Float32Array([5, 6]));

      // Oldest samples (1, 2) should be overwritten
      expect(buf.availableSamples()).toBe(4);
      const result = buf.read(4);
      expect(Array.from(result)).toEqual([3, 4, 5, 6]);
    });

    it('handles write larger than capacity', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([1, 2, 3, 4, 5, 6, 7]));

      // Only the last 4 samples should be kept
      expect(buf.availableSamples()).toBe(4);
      const result = buf.read(4);
      expect(Array.from(result)).toEqual([4, 5, 6, 7]);
    });

    it('handles write exactly equal to capacity', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([10, 20, 30, 40]));

      expect(buf.availableSamples()).toBe(4);
      const result = buf.read(4);
      expect(Array.from(result)).toEqual([10, 20, 30, 40]);
    });
  });

  describe('availableSamples', () => {
    it('returns 0 for empty buffer', () => {
      const buf = new RingBuffer(10);
      expect(buf.availableSamples()).toBe(0);
    });

    it('tracks available samples after writes and reads', () => {
      const buf = new RingBuffer(10);
      buf.write(new Float32Array([1, 2, 3, 4, 5]));
      expect(buf.availableSamples()).toBe(5);

      buf.read(2);
      expect(buf.availableSamples()).toBe(3);

      buf.write(new Float32Array([6, 7]));
      expect(buf.availableSamples()).toBe(5);
    });

    it('never exceeds capacity', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([1, 2, 3, 4]));
      buf.write(new Float32Array([5, 6, 7, 8]));
      expect(buf.availableSamples()).toBe(4);
    });
  });

  describe('clear', () => {
    it('resets the buffer to empty state', () => {
      const buf = new RingBuffer(10);
      buf.write(new Float32Array([1, 2, 3, 4, 5]));
      expect(buf.availableSamples()).toBe(5);

      buf.clear();
      expect(buf.availableSamples()).toBe(0);
    });

    it('allows writing after clear', () => {
      const buf = new RingBuffer(4);
      buf.write(new Float32Array([1, 2, 3, 4]));
      buf.clear();
      buf.write(new Float32Array([5, 6]));

      expect(buf.availableSamples()).toBe(2);
      expect(Array.from(buf.read(2))).toEqual([5, 6]);
    });
  });

  describe('error handling', () => {
    it('throws when reading more samples than available', () => {
      const buf = new RingBuffer(10);
      buf.write(new Float32Array([1, 2, 3]));

      expect(() => buf.read(5)).toThrow('Cannot read 5 samples: only 3 available');
    });

    it('throws on negative read count', () => {
      const buf = new RingBuffer(10);
      expect(() => buf.read(-1)).toThrow();
    });

    it('handles empty write gracefully', () => {
      const buf = new RingBuffer(10);
      buf.write(new Float32Array(0));
      expect(buf.availableSamples()).toBe(0);
    });
  });
});
