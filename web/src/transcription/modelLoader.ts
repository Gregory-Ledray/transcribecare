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
 * For buffers exceeding the SubtleCrypto size limit (~2 GB), computes
 * the hash incrementally in chunks using a WASM-free approach: hash
 * fixed-size blocks and then hash the concatenated block hashes.
 *
 * Note: For very large buffers, this produces a "chunked hash" that is
 * NOT the same as a standard SHA-256 of the full file. However, it is
 * deterministic and sufficient for cache integrity validation (detecting
 * corruption or partial writes).
 *
 * @param data - The binary data to hash
 * @returns Hex-encoded SHA-256 hash string
 */
export async function computeSha256(data: Uint8Array): Promise<string> {
  // SubtleCrypto.digest() fails for buffers > ~2 GB in Chrome.
  // Use chunked hashing for large buffers.
  const MAX_DIGEST_SIZE = 1.5 * 1024 * 1024 * 1024; // 1.5 GB safe limit

  if (data.byteLength <= MAX_DIGEST_SIZE) {
    const hashBuffer = await crypto.subtle.digest('SHA-256', data as unknown as ArrayBuffer);
    const hashArray = new Uint8Array(hashBuffer);
    return Array.from(hashArray)
      .map((byte) => byte.toString(16).padStart(2, '0'))
      .join('');
  }

  // Chunked hashing: hash each chunk, then hash the concatenated hashes.
  const CHUNK_SIZE = 512 * 1024 * 1024; // 512 MB per chunk
  const chunkHashes: Uint8Array[] = [];

  for (let pos = 0; pos < data.byteLength; pos += CHUNK_SIZE) {
    const end = Math.min(pos + CHUNK_SIZE, data.byteLength);
    const chunk = data.subarray(pos, end);
    const chunkHash = await crypto.subtle.digest('SHA-256', chunk as unknown as ArrayBuffer);
    chunkHashes.push(new Uint8Array(chunkHash));
  }

  // Concatenate all chunk hashes and hash the result
  const combined = new Uint8Array(chunkHashes.length * 32);
  for (let i = 0; i < chunkHashes.length; i++) {
    combined.set(chunkHashes[i], i * 32);
  }

  const finalHash = await crypto.subtle.digest('SHA-256', combined.buffer);
  const finalArray = new Uint8Array(finalHash);
  return Array.from(finalArray)
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
        // Stream from cache into a pre-allocated buffer to avoid the
        // double-allocation that .arrayBuffer() causes on large responses.
        const cachedBody = cachedResponse.body;
        if (!cachedBody) {
          throw new Error('Cached response has no readable body');
        }

        const cachedSize = metadata.sizeBytes;

        // Allocate with WebAssembly.Memory fallback for large models
        const wasmPageSize = 65536;
        const pages = Math.ceil(cachedSize / wasmPageSize);
        let cachedData: Uint8Array;
        try {
          cachedData = new Uint8Array(cachedSize);
        } catch {
          const mem = new WebAssembly.Memory({ initial: pages, maximum: pages });
          cachedData = new Uint8Array(mem.buffer, 0, cachedSize);
        }

        let cachedOffset = 0;
        const cachedReader = cachedBody.getReader();

        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await cachedReader.read();
          if (done) break;
          cachedData.set(value, cachedOffset);
          cachedOffset += value.length;
        }

        reportProgress({ phase: 'validating', percent: 50 });

        const isValid = await validateIntegrity(cachedData, metadata.sha256);
        const isLargeEnoughToBeTheModel = cachedData.length > 10_000;

        if (isValid && isLargeEnoughToBeTheModel) {
          reportProgress({ phase: 'ready', percent: 100 });
          console.debug(`modelLoader cachedData ${cachedData.length}`)
          return cachedData;
        }
      }
    }
    
    // Cache is corrupt or version mismatch — delete stale entries
    await cache.delete(modelUrl);
    await cache.delete(metadataKey(modelUrl));

    // Download from network — stream directly into a pre-allocated buffer.
    reportProgress({ phase: 'downloading', percent: 0 });

    // Use a streaming fetch. We read headers first to get content-length,
    // then allocate our buffer, then stream the body into it.
    const response = await fetch(modelUrl);

    if (!response.ok) {
      console.debug(`modelLoader cachedData modelDownloadFailure`)
      throw new Error(
        `Failed to download model: HTTP ${response.status} ${response.statusText}`
      );
    }

    const contentLength = Number(response.headers.get('content-length') || '0');
    if (contentLength < 10_000) {
      console.debug(`modelLoader modelLengthWasTooSmall`)
      throw new Error('Model not found')
    }

    if (!response.body) {
      console.debug(`modelLoader cachedData modelReadError`)
      throw new Error('Failed to read model response: no readable stream');
    }

    // Allocate the model buffer. For files near the V8 heap limit (~4 GB
    // for workers), we use WebAssembly.Memory which allocates outside the
    // normal JS heap and can handle larger contiguous buffers.
    const wasmPageSize = 65536; // 64 KiB per WebAssembly page
    const pagesNeeded = Math.ceil(contentLength / wasmPageSize);
    let modelData: Uint8Array;

    try {
      // First try normal allocation (works if enough heap is available)
      modelData = new Uint8Array(contentLength);
    } catch {
      // Fall back to WebAssembly.Memory which allocates outside V8 heap
      const memory = new WebAssembly.Memory({
        initial: pagesNeeded,
        maximum: pagesNeeded,
      });
      modelData = new Uint8Array(memory.buffer, 0, contentLength);
    }

    let offset = 0;

    // Stream the fetch body directly into our pre-allocated buffer.
    // Each chunk is small (~64 KB from the network layer) so memory
    // pressure during streaming is minimal.
    const reader = response.body.getReader();

    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      modelData.set(value, offset);
      offset += value.length;
      reportProgress({
        phase: 'downloading',
        percent: computeProgress(offset, contentLength),
      });
    }

    reportProgress({ phase: 'validating', percent: 0 });

    // Write the model to cache from our buffer for future loads.
    // We create a ReadableStream that reads from our buffer in chunks
    // to avoid passing the full ArrayBuffer (which would copy it).
    const CACHE_CHUNK_SIZE = 4 * 1024 * 1024; // 4 MB chunks
    const cacheBody = new ReadableStream<Uint8Array>({
      start(controller) {
        let pos = 0;
        function push() {
          if (pos >= contentLength) {
            controller.close();
            return;
          }
          const end = Math.min(pos + CACHE_CHUNK_SIZE, contentLength);
          controller.enqueue(modelData.slice(pos, end));
          pos = end;
          // Yield to avoid blocking
          setTimeout(push, 0);
        }
        push();
      },
    });

    await cache.put(
      modelUrl,
      new Response(cacheBody, {
        headers: {
          'Content-Type': 'application/octet-stream',
          'Content-Length': String(contentLength),
        },
      })
    );

    reportProgress({ phase: 'validating', percent: 50 });

    const sha256 = await computeSha256(modelData);

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
    console.debug(`modelLoader downloadedAndReady ${modelData.length}`)
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
