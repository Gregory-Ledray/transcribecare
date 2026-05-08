/**
 * Model Loader for the Gemma 4 E2B transcription model.
 *
 * Responsible for downloading, caching, validating integrity,
 * and reporting progress during model initialization.
 */

import type {
  ModelLoaderConfig,
  ModelLoadProgress,
  ModelCacheMetadata,
} from './types';

/**
 * Computes a bounded progress percentage from received and total bytes.
 *
 * @param received - Number of bytes received so far
 * @param total - Total number of bytes expected
 * @returns A number in the range [0, 100]
 */
export function computeProgress(received: number, total: number): number {
  if (total <= 0) {
    return 0;
  }
  const percent = (received / total) * 100;
  return Math.min(Math.max(percent, 0), 100);
}

/**
 * Computes the SHA-256 hash of a Uint8Array using the Web Crypto API.
 *
 * @param data - The binary data to hash
 * @returns Hex-encoded SHA-256 hash string
 */
export async function computeSha256(data: Uint8Array): Promise<string> {
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = new Uint8Array(hashBuffer);
  return Array.from(hashArray)
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Validates the integrity of binary data against an expected SHA-256 hash.
 *
 * @param data - The binary data to validate
 * @param expectedHash - The expected hex-encoded SHA-256 hash
 * @returns true if the computed hash matches the expected hash
 */
export async function validateIntegrity(
  data: Uint8Array,
  expectedHash: string
): Promise<boolean> {
  const computedHash = await computeSha256(data);
  return computedHash === expectedHash;
}

/** Key suffix for storing model cache metadata alongside the model binary. */
const METADATA_SUFFIX = '__metadata';

/**
 * Builds the metadata cache key for a given model URL.
 */
function metadataKey(modelUrl: string): string {
  return `${modelUrl}${METADATA_SUFFIX}`;
}

/**
 * Loads the Gemma 4 model artifact with a cache-first strategy.
 *
 * Strategy:
 * 1. Check Cache API for existing model binary and metadata
 * 2. If cached: validate integrity via stored SHA-256 hash
 * 3. If valid and version matches: return cached data
 * 4. If invalid, version mismatch, or not cached: download from network
 * 5. Store downloaded model and metadata in cache
 *
 * @param config - Model loader configuration (URL, version, cache name)
 * @param onProgress - Optional callback for progress reporting
 * @returns The model binary as a Uint8Array
 * @throws Error with descriptive message on network or integrity failures
 */
export async function loadModel(
  config: ModelLoaderConfig,
  onProgress?: (progress: ModelLoadProgress) => void
): Promise<Uint8Array> {
  const { modelUrl, modelVersion, cacheName } = config;

  const reportProgress = (progress: ModelLoadProgress): void => {
    if (onProgress) {
      onProgress(progress);
    }
  };

  try {
    const cache = await caches.open(cacheName);

    // Attempt to load from cache
    const cachedResponse = await cache.match(modelUrl);
    const cachedMetaResponse = await cache.match(metadataKey(modelUrl));

    if (cachedResponse && cachedMetaResponse) {
      reportProgress({ phase: 'validating', percent: 0 });

      const metadata: ModelCacheMetadata = await cachedMetaResponse.json();

      // Check version match
      if (metadata.version === modelVersion) {
        const cachedData = new Uint8Array(await cachedResponse.arrayBuffer());

        reportProgress({ phase: 'validating', percent: 50 });

        const isValid = await validateIntegrity(cachedData, metadata.sha256);

        if (isValid) {
          reportProgress({ phase: 'ready', percent: 100 });
          return cachedData;
        }
      }

      // Cache is corrupt or version mismatch — delete stale entries
      await cache.delete(modelUrl);
      await cache.delete(metadataKey(modelUrl));
    }

    // Download from network
    reportProgress({ phase: 'downloading', percent: 0 });

    const response = await fetch(modelUrl);

    if (!response.ok) {
      throw new Error(
        `Failed to download model: HTTP ${response.status} ${response.statusText}`
      );
    }

    const contentLength = Number(response.headers.get('content-length') || '0');
    const reader = response.body?.getReader();

    if (!reader) {
      throw new Error('Failed to read model response: no readable stream');
    }

    const chunks: Uint8Array[] = [];
    let received = 0;

    while (true) {
      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      chunks.push(value);
      received += value.length;

      reportProgress({
        phase: 'downloading',
        percent: computeProgress(received, contentLength),
      });
    }

    // Combine chunks into a single Uint8Array
    const modelData = new Uint8Array(received);
    let offset = 0;
    for (const chunk of chunks) {
      modelData.set(chunk, offset);
      offset += chunk.length;
    }

    // Validate and store in cache
    reportProgress({ phase: 'validating', percent: 0 });

    const sha256 = await computeSha256(modelData);

    reportProgress({ phase: 'validating', percent: 50 });

    // Store model binary in cache
    await cache.put(
      modelUrl,
      new Response(modelData, {
        headers: { 'Content-Type': 'application/octet-stream' },
      })
    );

    // Store metadata in cache
    const cacheMetadata: ModelCacheMetadata = {
      version: modelVersion,
      sha256,
      downloadedAt: new Date().toISOString(),
      sizeBytes: modelData.length,
    };

    await cache.put(
      metadataKey(modelUrl),
      new Response(JSON.stringify(cacheMetadata), {
        headers: { 'Content-Type': 'application/json' },
      })
    );

    reportProgress({ phase: 'ready', percent: 100 });
    return modelData;
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : 'An unknown error occurred while loading the model';

    reportProgress({ phase: 'error', percent: 0, error: message });
    throw new Error(`Model loading failed: ${message}`);
  }
}
