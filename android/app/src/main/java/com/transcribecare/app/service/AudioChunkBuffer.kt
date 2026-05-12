package com.transcribecare.app.service

import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe bounded buffer for accumulating PCM audio samples.
 *
 * Accepts frames from the audio capture thread and provides an atomic drain
 * operation for the inference coroutine. Uses a [ReentrantLock] with short
 * critical sections to minimize contention between the capture thread (append)
 * and the inference coroutine (drain).
 *
 * When the buffer reaches maximum capacity, the oldest samples are dropped
 * to make room for new frames, ensuring the most recent audio is always retained.
 *
 * @param sampleRate The audio sample rate in Hz (e.g., 44100).
 * @param maxDurationSeconds Maximum buffer duration in seconds before oldest samples are dropped.
 */
class AudioChunkBuffer(
    private val sampleRate: Int,
    private val maxDurationSeconds: Float = 30.0f
) {
    companion object {
        private const val TAG = "AudioChunkBuffer"
    }

    /** Maximum number of samples the buffer can hold. */
    val maxSamples: Int = (sampleRate * maxDurationSeconds).toInt()

    private val lock = ReentrantLock()
    private var buffer = ShortArray(maxSamples)
    private var writePosition = 0

    /** Current number of accumulated samples in the buffer. */
    val sampleCount: Int
        get() = lock.withLock { writePosition }

    /**
     * Appends audio samples to the buffer.
     *
     * If appending the frame would exceed [maxSamples], the oldest samples are
     * dropped to make room. The lock is held only for the duration of the array
     * copy, keeping the critical section short.
     *
     * @param frame The PCM sample data to append.
     * @param frameSize The number of valid samples in [frame] to copy.
     */
    fun append(frame: ShortArray, frameSize: Int) {
        val samplesToAppend = frameSize.coerceAtMost(frame.size)
        if (samplesToAppend <= 0) return

        lock.withLock {
            if (samplesToAppend >= maxSamples) {
                // Frame itself exceeds buffer capacity — keep only the tail
                Log.w(TAG, "Frame size ($samplesToAppend) exceeds max buffer capacity ($maxSamples). Dropping oldest samples.")
                frame.copyInto(buffer, 0, samplesToAppend - maxSamples, samplesToAppend)
                writePosition = maxSamples
                return
            }

            val availableSpace = maxSamples - writePosition
            if (samplesToAppend > availableSpace) {
                // Need to drop oldest samples to make room
                val samplesToDrop = samplesToAppend - availableSpace
                Log.w(TAG, "Buffer capacity exceeded. Dropping $samplesToDrop oldest samples.")
                // Shift remaining samples to the front
                buffer.copyInto(buffer, 0, samplesToDrop, writePosition)
                writePosition -= samplesToDrop
            }

            // Copy new frame into buffer
            frame.copyInto(buffer, writePosition, 0, samplesToAppend)
            writePosition += samplesToAppend
        }
    }

    /**
     * Atomically drains all accumulated samples from the buffer.
     *
     * Returns the accumulated samples as a new [ShortArray] and resets the
     * internal buffer. Returns null if the buffer is empty.
     *
     * @return A [ShortArray] containing all accumulated samples, or null if empty.
     */
    fun drain(): ShortArray? {
        lock.withLock {
            if (writePosition == 0) return null

            val result = buffer.copyOfRange(0, writePosition)
            writePosition = 0
            return result
        }
    }

    /**
     * Returns the current accumulated duration in seconds.
     *
     * @return Duration of buffered audio in seconds.
     */
    fun durationSeconds(): Float {
        lock.withLock {
            return writePosition.toFloat() / sampleRate
        }
    }

    /**
     * Clears all accumulated samples from the buffer.
     */
    fun clear() {
        lock.withLock {
            writePosition = 0
        }
    }

    /**
     * Returns whether the buffer contains no samples.
     *
     * @return true if the buffer is empty, false otherwise.
     */
    fun isEmpty(): Boolean {
        lock.withLock {
            return writePosition == 0
        }
    }
}
