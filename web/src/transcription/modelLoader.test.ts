/**
 * Unit tests for the core model loader functions.
 * Tests computeProgress, computeSha256, and validateIntegrity.
 */

import { describe, it, expect } from 'vitest';
import { computeProgress, computeSha256, validateIntegrity } from './modelLoader';

describe('computeProgress', () => {
  it('returns 0 when total is 0', () => {
    expect(computeProgress(50, 0)).toBe(0);
  });

  it('returns 0 when received is 0', () => {
    expect(computeProgress(0, 100)).toBe(0);
  });

  it('returns 50 when half received', () => {
    expect(computeProgress(50, 100)).toBe(50);
  });

  it('returns 100 when fully received', () => {
    expect(computeProgress(100, 100)).toBe(100);
  });

  it('caps at 100 when received exceeds total', () => {
    expect(computeProgress(150, 100)).toBe(100);
  });

  it('returns 0 when total is negative', () => {
    expect(computeProgress(50, -10)).toBe(0);
  });

  it('returns 0 when received is negative and total is positive', () => {
    expect(computeProgress(-10, 100)).toBe(0);
  });
});

describe('computeSha256', () => {
  it('returns a 64-character hex string', async () => {
    const data = new Uint8Array([1, 2, 3, 4, 5]);
    const hash = await computeSha256(data);
    expect(hash).toHaveLength(64);
    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('returns consistent hash for same input', async () => {
    const data = new Uint8Array([10, 20, 30]);
    const hash1 = await computeSha256(data);
    const hash2 = await computeSha256(data);
    expect(hash1).toBe(hash2);
  });

  it('returns different hash for different input', async () => {
    const data1 = new Uint8Array([1, 2, 3]);
    const data2 = new Uint8Array([4, 5, 6]);
    const hash1 = await computeSha256(data1);
    const hash2 = await computeSha256(data2);
    expect(hash1).not.toBe(hash2);
  });

  it('handles empty array', async () => {
    const data = new Uint8Array([]);
    const hash = await computeSha256(data);
    // SHA-256 of empty input is a known constant
    expect(hash).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  });
});

describe('validateIntegrity', () => {
  it('returns true when hash matches', async () => {
    const data = new Uint8Array([1, 2, 3, 4, 5]);
    const hash = await computeSha256(data);
    const isValid = await validateIntegrity(data, hash);
    expect(isValid).toBe(true);
  });

  it('returns false when hash does not match', async () => {
    const data = new Uint8Array([1, 2, 3, 4, 5]);
    const wrongHash = 'a'.repeat(64);
    const isValid = await validateIntegrity(data, wrongHash);
    expect(isValid).toBe(false);
  });

  it('returns false when data is mutated', async () => {
    const data = new Uint8Array([1, 2, 3, 4, 5]);
    const hash = await computeSha256(data);
    const mutated = new Uint8Array([1, 2, 3, 4, 6]);
    const isValid = await validateIntegrity(mutated, hash);
    expect(isValid).toBe(false);
  });
});
