package com.taskpulse.ai.recorder

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

class AudioRecorderManager(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var outputFile: File? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun startRecording(onAmplitudeUpdate: (Int) -> Unit): File? {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e("AudioRecorderManager", "Invalid AudioRecord buffer size")
            return null
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )

            val dir = File(context.cacheDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            outputFile = File(dir, "android_meeting_${System.currentTimeMillis()}.wav")

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                writeWavHeader(outputFile!!, sampleRate, 1, 16)
                val buffer = ShortArray(bufferSize)
                val fileStream = FileOutputStream(outputFile, true)

                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val byteBuffer = ByteArray(read * 2)
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i]
                            byteBuffer[i * 2] = (sample.toInt() and 0x00FF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() and 0xFF00) shr 8).toByte()
                            sum += (sample * sample).toDouble()
                        }
                        fileStream.write(byteBuffer)

                        val rms = sqrt(sum / read)
                        val normRms = (rms / 32767.0).coerceAtLeast(1e-5)
                        val db = 20.0 * Math.log10(normRms)
                        val level = (((db + 60.0) / 60.0) * 100.0).coerceIn(0.0, 100.0).toInt()
                        withContext(Dispatchers.Main) {
                            onAmplitudeUpdate(level)
                        }
                    }
                }
                fileStream.close()
                updateWavHeaderSize(outputFile!!)
            }

            return outputFile
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error starting recording: ${e.message}")
            return null
        }
    }

    fun stopRecording(): File? {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error stopping recording: ${e.message}")
        }
        return outputFile
    }

    private fun writeWavHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val stream = FileOutputStream(file)
        val header = ByteArray(44)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = 0; header[5] = 0; header[6] = 0; header[7] = 0
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size (16 for PCM)
        header[20] = 1; header[21] = 0 // AudioFormat (1 for PCM)
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = blockAlign.toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = 0; header[41] = 0; header[42] = 0; header[43] = 0

        stream.write(header)
        stream.close()
    }

    private fun updateWavHeaderSize(file: File) {
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val raf = RandomAccessFile(file, "rw")
        raf.seek(4)
        raf.write(intToByteArray(totalDataLen.toInt()))
        raf.seek(40)
        raf.write(intToByteArray(totalAudioLen.toInt()))
        raf.close()
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            (value shr 8 and 0xff).toByte(),
            (value shr 16 and 0xff).toByte(),
            (value shr 24 and 0xff).toByte()
        )
    }
}
