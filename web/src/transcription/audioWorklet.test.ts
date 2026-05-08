/**
 * Unit tests for the AudioWorklet processor's resample function.
 *
 * The AudioWorkletProcessor class itself cannot be tested in a standard
 * Node/jsdom environment (it requires the AudioWorklet API), but the
 * exported `resample` function can be tested independently.
 */
import { describe, it, expect, vi } from 'vitest';

// Polyfill AudioWorklet globals before importing the module
(globalThis as any).AudioWorkletProcessor = class AudioWorkletProcessor {
  port = { postMessage: vi.fn() };
  constructor() {}
};
(globalThis as any).registerProcessor = vi.fn();
(globalThis as any).currentTime = 0;

// Now import the module (polyfills must be set before this)
const { resample } = await import('./audioWorklet');

describe('resample', () => {
  it('returns empty array for empty input', () => {
    const result = resample(new Float32Array(0), 44100, 16000);
    expect(result.length).toBe(0);
  });

  it('returns a copy when source and target rates are equal', () => {
    const input = new Float32Array([0.1, 0.2, 0.3, 0.4, 0.5]);
    const result = resample(input, 16000, 16000);
    expect(result.length).toBe(5);
    expect(Array.from(result)).toEqual(Array.from(input));
  });

  it('produces correct output length when downsampling from 44100 to 16000', () => {
    const inputLength = 441; // 10ms at 44100Hz
    const input = new Float32Array(inputLength);
    for (let i = 0; i < inputLength; i++) {
      input[i] = Math.sin(2 * Math.PI * 440 * i / 44100);
    }

    const result = resample(input, 44100, 16000);
    const expectedLength = Math.floor(inputLength * 16000 / 44100);
    expect(result.length).toBe(expectedLength);
  });

  it('produces correct output length when downsampling from 48000 to 16000', () => {
    const inputLength = 480; // 10ms at 48000Hz
    const input = new Float32Array(inputLength);
    for (let i = 0; i < inputLength; i++) {
      input[i] = Math.sin(2 * Math.PI * 440 * i / 48000);
    }

    const result = resample(input, 48000, 16000);
    const expectedLength = Math.floor(inputLength * 16000 / 48000);
    expect(result.length).toBe(expectedLength);
  });

  it('clamps output values to [-1.0, 1.0]', () => {
    // Input with values exceeding [-1, 1] range
    const input = new Float32Array([1.5, -1.5, 0.5, -0.5, 2.0]);
    const result = resample(input, 44100, 16000);

    for (let i = 0; i < result.length; i++) {
      expect(result[i]).toBeGreaterThanOrEqual(-1.0);
      expect(result[i]).toBeLessThanOrEqual(1.0);
    }
  });

  it('preserves signal characteristics through resampling', () => {
    // A DC signal (constant value) should remain constant after resampling
    const input = new Float32Array(128).fill(0.75);
    const result = resample(input, 48000, 16000);

    for (let i = 0; i < result.length; i++) {
      expect(result[i]).toBeCloseTo(0.75, 5);
    }
  });

  it('handles single-sample input', () => {
    const input = new Float32Array([0.5]);
    // floor(1 * 16000 / 44100) = 0, so output should be empty
    const result = resample(input, 44100, 16000);
    expect(result.length).toBe(Math.floor(1 * 16000 / 44100));
  });

  it('handles 128-sample blocks (typical AudioWorklet frame size)', () => {
    const input = new Float32Array(128);
    for (let i = 0; i < 128; i++) {
      input[i] = Math.sin(2 * Math.PI * 1000 * i / 48000);
    }

    const result = resample(input, 48000, 16000);
    const expectedLength = Math.floor(128 * 16000 / 48000);
    expect(result.length).toBe(expectedLength);

    // All values should be in valid range
    for (let i = 0; i < result.length; i++) {
      expect(result[i]).toBeGreaterThanOrEqual(-1.0);
      expect(result[i]).toBeLessThanOrEqual(1.0);
    }
  });
});
