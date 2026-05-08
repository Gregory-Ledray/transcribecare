/**
 * IndexedDB persistence layer for TranscribeCare sessions and audio.
 *
 * Database: "transcribecare"
 * Object stores:
 *   - "sessions": Stores RecordingSession metadata (keyed by session id)
 *   - "audio": Stores audio Blobs (keyed by session id)
 */

const DB_NAME = 'transcribecare';
const DB_VERSION = 1;
const SESSIONS_STORE = 'sessions';
const AUDIO_STORE = 'audio';

interface TranscriptSegment {
  id: string;
  text: string;
  type: 'past' | 'recent' | 'current';
}

interface StoredSession {
  id: string;
  title: string;
  date: string;
  time: string;
  duration: string;
  segments: TranscriptSegment[];
  statusLabel: string;
}

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(SESSIONS_STORE)) {
        db.createObjectStore(SESSIONS_STORE, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(AUDIO_STORE)) {
        db.createObjectStore(AUDIO_STORE);
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/** Save a session's metadata to IndexedDB. */
export async function saveSession(session: StoredSession): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(SESSIONS_STORE, 'readwrite');
    tx.objectStore(SESSIONS_STORE).put(session);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

/** Save an audio blob associated with a session id. */
export async function saveAudio(sessionId: string, blob: Blob): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(AUDIO_STORE, 'readwrite');
    tx.objectStore(AUDIO_STORE).put(blob, sessionId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

/** Load all sessions from IndexedDB, ordered newest first. */
export async function loadSessions(): Promise<StoredSession[]> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(SESSIONS_STORE, 'readonly');
    const request = tx.objectStore(SESSIONS_STORE).getAll();
    request.onsuccess = () => {
      const sessions = request.result as StoredSession[];
      // Sort newest first by id (which is a timestamp string)
      sessions.sort((a, b) => Number(b.id) - Number(a.id));
      resolve(sessions);
    };
    request.onerror = () => reject(request.error);
  });
}

/** Load an audio blob for a given session id. Returns null if not found. */
export async function loadAudio(sessionId: string): Promise<Blob | null> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(AUDIO_STORE, 'readonly');
    const request = tx.objectStore(AUDIO_STORE).get(sessionId);
    request.onsuccess = () => resolve(request.result ?? null);
    request.onerror = () => reject(request.error);
  });
}

/** Delete a session and its associated audio from IndexedDB. */
export async function deleteSession(sessionId: string): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([SESSIONS_STORE, AUDIO_STORE], 'readwrite');
    tx.objectStore(SESSIONS_STORE).delete(sessionId);
    tx.objectStore(AUDIO_STORE).delete(sessionId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
