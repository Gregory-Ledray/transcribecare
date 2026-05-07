package com.transcribecare.app.service

/**
 * Interface for components that consume raw PCM audio frames
 * from the UnifiedAudioCaptureService.
 */
interface AudioConsumer {

    /**
     * Called once before capture begins. Consumers should allocate
     * resources and configure themselves for the given audio format.
     *
     * @param sampleRate The audio sample rate in Hz (e.g., 44100)
     * @param channelCount The number of audio channels (e.g., 1 for mono)
     * @param encoding The audio encoding format (e.g., AudioFormat.ENCODING_PCM_16BIT)
     */
    fun prepare(sampleRate: Int, channelCount: Int, encoding: Int)

    /**
     * Called for each PCM frame read from AudioRecord.
     * Each consumer receives its own independent copy of the frame.
     *
     * @param frame ShortArray containing PCM samples (independent copy)
     * @param frameSize Number of valid samples in the frame
     */
    fun onAudioFrame(frame: ShortArray, frameSize: Int)

    /**
     * Called when capture ends. Consumers should finalize and free resources.
     */
    fun release()
}
