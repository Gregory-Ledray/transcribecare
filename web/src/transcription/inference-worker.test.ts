/**
 * Unit tests for inference-worker helper functions.
 * Tests the error classification, retry eligibility, and quota detection logic.
 */

import { describe, it, expect } from 'vitest';
import {
  classifyAndFormatError,
  isTransientError,
  isQuotaExceededError,
} from './inference-worker';

describe('classifyAndFormatError', () => {
  it('returns OOM message for out of memory errors', () => {
    const error = new Error('Out of memory');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Insufficient memory');
  });

  it('returns OOM message for allocation failed errors', () => {
    const error = new Error('allocation failed: cannot allocate');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Insufficient memory');
  });

  it('returns network message for fetch errors', () => {
    const error = new Error('Failed to fetch');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Network error');
  });

  it('returns network message for network errors', () => {
    const error = new Error('NetworkError when attempting to fetch resource');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Network error');
  });

  it('returns storage message for quota exceeded errors', () => {
    const error = new Error('QuotaExceededError: storage quota reached');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Insufficient storage space');
    expect(result).toContain('3 GB');
  });

  it('returns storage message for DOMException QuotaExceededError', () => {
    const error = new DOMException('Quota exceeded', 'QuotaExceededError');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Insufficient storage space');
    expect(result).toContain('3 GB');
  });

  it('returns WASM message for WebAssembly errors', () => {
    const error = new Error('WebAssembly.compile(): expected magic word');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Failed to initialize the transcription engine');
  });

  it('returns generic message for unknown Error instances', () => {
    const error = new Error('Something unexpected happened');
    const result = classifyAndFormatError(error);
    expect(result).toContain('Transcription error:');
    expect(result).toContain('Something unexpected happened');
  });

  it('returns fallback message for non-Error values', () => {
    const result = classifyAndFormatError('string error');
    expect(result).toBe('An unexpected error occurred during transcription.');
  });

  it('returns fallback message for null', () => {
    const result = classifyAndFormatError(null);
    expect(result).toBe('An unexpected error occurred during transcription.');
  });
});

describe('isTransientError', () => {
  it('returns true for network errors', () => {
    const error = new Error('NetworkError when attempting to fetch');
    expect(isTransientError(error)).toBe(true);
  });

  it('returns true for failed to fetch errors', () => {
    const error = new Error('Failed to fetch');
    expect(isTransientError(error)).toBe(true);
  });

  it('returns true for timeout errors', () => {
    const error = new Error('Request timeout exceeded');
    expect(isTransientError(error)).toBe(true);
  });

  it('returns true for aborted errors', () => {
    const error = new Error('The request was aborted');
    expect(isTransientError(error)).toBe(true);
  });

  it('returns false for OOM errors', () => {
    const error = new Error('Out of memory');
    expect(isTransientError(error)).toBe(false);
  });

  it('returns false for quota exceeded errors', () => {
    const error = new Error('QuotaExceededError');
    expect(isTransientError(error)).toBe(false);
  });

  it('returns false for DOMException QuotaExceededError', () => {
    const error = new DOMException('Quota exceeded', 'QuotaExceededError');
    expect(isTransientError(error)).toBe(false);
  });

  it('returns false for WASM errors', () => {
    const error = new Error('WebAssembly compilation failed');
    expect(isTransientError(error)).toBe(false);
  });

  it('returns false for unknown errors', () => {
    const error = new Error('Something else went wrong');
    expect(isTransientError(error)).toBe(false);
  });

  it('returns false for non-Error values', () => {
    expect(isTransientError('string')).toBe(false);
    expect(isTransientError(null)).toBe(false);
    expect(isTransientError(undefined)).toBe(false);
  });
});

describe('isQuotaExceededError', () => {
  it('returns true for DOMException with QuotaExceededError name', () => {
    const error = new DOMException('Storage quota exceeded', 'QuotaExceededError');
    expect(isQuotaExceededError(error)).toBe(true);
  });

  it('returns true for Error with quotaexceedederror in message', () => {
    const error = new Error('QuotaExceededError: not enough space');
    expect(isQuotaExceededError(error)).toBe(true);
  });

  it('returns true for Error with quota exceeded in message', () => {
    const error = new Error('The quota has been exceeded for this origin');
    expect(isQuotaExceededError(error)).toBe(true);
  });

  it('returns false for regular network errors', () => {
    const error = new Error('Failed to fetch');
    expect(isQuotaExceededError(error)).toBe(false);
  });

  it('returns false for OOM errors', () => {
    const error = new Error('Out of memory');
    expect(isQuotaExceededError(error)).toBe(false);
  });

  it('returns false for non-Error values', () => {
    expect(isQuotaExceededError('string')).toBe(false);
    expect(isQuotaExceededError(null)).toBe(false);
    expect(isQuotaExceededError(42)).toBe(false);
  });
});
