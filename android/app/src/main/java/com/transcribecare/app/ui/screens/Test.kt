//package com.transcribecare.app.ui.screens
//
//import android.app.Activity
//import android.media.AudioFormat
//import android.media.AudioRecord
//import android.media.MediaRecorder
//import android.os.Bundle
//import android.widget.Button
//import androidx.compose.runtime.Composable
//import java.io.FileNotFoundException
//import java.io.FileOutputStream
//import java.io.IOException
//
//class Audio_Record : Activity() {
//    private val RECORDER_SAMPLERATE = 8000
//    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
//    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
//    private var recorder: AudioRecord? = null
//    private var recordingThread: Thread? = null
//    private var isRecording = false
//
//    private val BufferElements2Rec = 1024 // want to play 2048 (2K) since 2 bytes we use only 1024
//    private val BytesPerElement = 2 // 2 bytes in 16bit format
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(this.layout.main)
//
//        setButtonHandlers()
//        enableButtons(false)
//
//        val bufferSize = AudioRecord.getMinBufferSize(
//            RECORDER_SAMPLERATE,
//            RECORDER_CHANNELS,
//            RECORDER_AUDIO_ENCODING
//        )
//    }
//
//    private fun setButtonHandlers() {
//        findViewById<Button>(R.id.btnStart).setOnClickListener(btnClick)
//        findViewById<Button>(R.id.btnStop).setOnClickListener(btnClick)
//    }
//
//    private fun enableButton(id: Int, isEnable: Boolean) {
//        findViewById<Button>(id).isEnabled = isEnable
//    }
//
//    private fun enableButtons(isRecording: Boolean) {
//        enableButton(R.id.btnStart, !isRecording)
//        enableButton(R.id.btnStop, isRecording)
//    }
//
//    private val btnClick = { v: android.view.View ->
//        when (v.id) {
//            R.id.btnStart -> {
//                startRecording()
//                enableButtons(true)
//            }
//            R.id.btnStop -> {
//                stopRecording()
//                enableButtons(false)
//            }
//        }
//    }
//
//    private fun startRecording() {
//        recorder = AudioRecord(
//            MediaRecorder.AudioSource.MIC,
//            RECORDER_SAMPLERATE,
//            RECORDER_CHANNELS,
//            RECORDER_AUDIO_ENCODING,
//            BufferElements2Rec * BytesPerElement
//        )
//
//        recorder?.startRecording()
//        isRecording = true
//        recordingThread = Thread({
//            writeAudioDataToFile()
//        }, "AudioRecorder Thread")
//        recordingThread?.start()
//    }
//
//    // convert short to byte
//    private fun short2byte(sData: ShortArray): ByteArray {
//        val bytes = ByteArray(sData.size * 2)
//        for (i in sData.indices) {
//            bytes[i * 2] = (sData[i].toInt() and 0x00FF).toByte()
//            bytes[i * 2 + 1] = (sData[i].toInt() shr 8).toByte()
//            sData[i] = 0
//        }
//        return bytes
//    }
//
//    private fun writeAudioDataToFile() {
//        val filePath = "/sdcard/voice8K16bitmono.pcm"
//        val sData = ShortArray(BufferElements2Rec)
//
//        var os: FileOutputStream? = null
//        try {
//            os = FileOutputStream(filePath)
//        } catch (e: FileNotFoundException) {
//            e.printStackTrace()
//        }
//
//        while (isRecording) {
//            recorder?.read(sData, 0, BufferElements2Rec)
//            println("Short writing to file ${sData.contentToString()}")
//            try {
//                val bData = short2byte(sData)
//                os?.write(bData, 0, BufferElements2Rec * BytesPerElement)
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//        }
//        try {
//            os?.close()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun stopRecording() {
//        recorder?.let {
//            isRecording = false
//            it.stop()
//            it.release()
//            recorder = null
//            recordingThread = null
//        }
//    }
//
//    @Composable
//    fun RecordingButton(
//        isRecording: Boolean,
//        onClick: () -> Unit
//    ) {
//        if (isRecording) {
//            enableButtons(true)
//            startRecording()
//        } else {
//            enableButtons(false)
//            stopRecording()
//        }
//    }
//}
//
