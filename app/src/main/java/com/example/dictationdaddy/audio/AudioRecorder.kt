package com.example.dictationdaddy.audio

import android.media.MediaRecorder
import java.io.File

// Recorder Logic
class AudioRecorder(val outputFile: File) {
    private var recorder: MediaRecorder? = null

    fun start() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            setOutputFile(outputFile.absolutePath)

            prepare()
            start()
        }
    }

    fun stop() {
        recorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            release()
        }
        recorder = null
    }

    fun cancel() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {

        } finally {
            outputFile.delete()
            recorder = null
        }
    }

    fun getAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    val file: File
        get() = outputFile
}
