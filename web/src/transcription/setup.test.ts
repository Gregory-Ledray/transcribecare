import { describe, it, expect } from 'vitest';

describe('Project setup', () => {
  it('vitest runs with jsdom environment', () => {
    expect(typeof document).toBe('object');
  });

  it('fast-check is available', async () => {
    const fc = await import('fast-check');
    expect(fc.integer).toBeDefined();
  });

  it('fake-indexeddb is available', () => {
    expect(typeof indexedDB).toBe('object');
    expect(indexedDB).not.toBeNull();
  });
});
