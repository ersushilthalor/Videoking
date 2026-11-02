package com.example.recorder

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class VideoRecorder(
    private val context: Context,
    val config: EncoderConfig = EncoderConfig(),
    private val onRecordingStarted: () -> Unit,
    private val onRecordingStopped: (Uri?, Long) -> Unit,
    private val onError: (String) -> Unit
) {
    // Video Encoder
    private var videoEncoder: MediaCodec? = null
    var inputSurface: Surface? = null
        private set
    private var videoTrackIndex = -1

    // Audio Encoder & Record
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrackIndex = -1

    // MediaMuxer
    private var mediaMuxer: MediaMuxer? = null
    private var isMuxerStarted = false
    private var pfd: ParcelFileDescriptor? = null
    private var outputFileUri: Uri? = null

    // State
    val isRecording = AtomicBoolean(false)
    val isPaused = AtomicBoolean(false)

    // Threads
    private var videoDrainThread: Thread? = null
    private var audioThread: Thread? = null

    // Timing & Pause synchronization
    private var recordingStartTimeMs = 0L
    private var pauseStartTimeMs = 0L
    private var totalPausedDurationMs = 0L
    private var videoFramesEncoded = 0L

    fun start() {
        if (isRecording.get()) return

        try {
            outputFileUri = createMediaStoreUri()
            val pfdLocal = context.contentResolver.openFileDescriptor(outputFileUri!!, "rw")
                ?: throw RuntimeException("Cannot open descriptor for $outputFileUri")
            pfd = pfdLocal

            mediaMuxer = MediaMuxer(pfdLocal.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            setupVideoEncoder()
            setupAudioEncoderAndRecord()

            isRecording.set(true)
            isPaused.set(false)
            recordingStartTimeMs = SystemClock.elapsedRealtime()
            totalPausedDurationMs = 0L

            startVideoDrainThread()
            startAudioRecordingThread()

            onRecordingStarted()
            Log.i(TAG, "Recording started successfully at ${config.resolution.label} ${config.fps}fps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            cleanup()
            onError("Failed to start recording: ${e.message}")
        }
    }

    private fun setupVideoEncoder() {
        val format = MediaFormat.createVideoFormat(
            config.codec.mimeType,
            config.resolution.width,
            config.resolution.height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // Explicit SDR BT.709 color metadata
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }

        val encoder = MediaCodec.createEncoderByType(config.codec.mimeType)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        encoder.start()
        videoEncoder = encoder
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioEncoderAndRecord() {
        val channelConfig = if (config.audioChannels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.audioSampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            config.audioSampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2
        )

        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            config.audioSampleRate,
            config.audioChannels
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 2)
        }

        val aEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        aEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        aEncoder.start()
        audioEncoder = aEncoder

        audioRecord?.startRecording()
    }

    fun pause() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true)
            pauseStartTimeMs = SystemClock.elapsedRealtime()
            Log.i(TAG, "Recording paused.")
        }
    }

    fun resume() {
        if (isRecording.get() && isPaused.get()) {
            val pausedDuration = SystemClock.elapsedRealtime() - pauseStartTimeMs
            totalPausedDurationMs += pausedDuration
            isPaused.set(false)
            Log.i(TAG, "Recording resumed (was paused for ${pausedDuration}ms).")
        }
    }

    fun onVideoFrameRendered(timestampNs: Long) {
        videoFramesEncoded++
    }

    private fun startVideoDrainThread() {
        videoDrainThread = Thread({
            val encoder = videoEncoder ?: return@Thread
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRecording.get() || !drainComplete) {
                val outputBufferIndex = try {
                    encoder.dequeueOutputBuffer(bufferInfo, 10000)
                } catch (e: Exception) {
                    break
                }

                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(this@VideoRecorder) {
                            if (videoTrackIndex == -1) {
                                val newFormat = encoder.outputFormat
                                videoTrackIndex = mediaMuxer!!.addTrack(newFormat)
                                checkStartMuxer()
                            }
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (drainComplete) break
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && bufferInfo.size > 0 && isMuxerStarted) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    // Adjust presentation time for pauses
                                    val pts = bufferInfo.presentationTimeUs - (totalPausedDurationMs * 1000)
                                    bufferInfo.presentationTimeUs = pts.coerceAtLeast(0L)
                                    synchronized(mediaMuxer!!) {
                                        mediaMuxer?.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                                    }
                                }
                            }
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }
        }, "VideoDrainThread").apply { start() }
    }

    private fun startAudioRecordingThread() {
        audioThread = Thread({
            val recorder = audioRecord ?: return@Thread
            val encoder = audioEncoder ?: return@Thread
            val bufferSize = 2048
            val audioBuffer = ByteArray(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRecording.get()) {
                if (isPaused.get()) {
                    SystemClock.sleep(50)
                    continue
                }

                val readBytes = recorder.read(audioBuffer, 0, bufferSize)
                if (readBytes > 0) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(audioBuffer, 0, readBytes)
                        val ptsUs = (SystemClock.elapsedRealtime() - recordingStartTimeMs - totalPausedDurationMs) * 1000
                        encoder.queueInputBuffer(inputBufferIndex, 0, readBytes, ptsUs.coerceAtLeast(0L), 0)
                    }
                }

                // Drain audio encoder output
                while (true) {
                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        synchronized(this@VideoRecorder) {
                            if (audioTrackIndex == -1) {
                                audioTrackIndex = mediaMuxer!!.addTrack(encoder.outputFormat)
                                checkStartMuxer()
                            }
                        }
                    } else if (outputBufferIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0 && isMuxerStarted) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                synchronized(mediaMuxer!!) {
                                    mediaMuxer?.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    } else {
                        break
                    }
                }
            }
        }, "AudioRecordThread").apply { start() }
    }

    @Volatile
    private var drainComplete = false

    private fun checkStartMuxer() {
        if (!isMuxerStarted && videoTrackIndex != -1 && audioTrackIndex != -1) {
            mediaMuxer?.start()
            isMuxerStarted = true
            Log.i(TAG, "MediaMuxer started with video track $videoTrackIndex and audio track $audioTrackIndex")
        }
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false)

        val durationMs = SystemClock.elapsedRealtime() - recordingStartTimeMs - totalPausedDurationMs

        Thread {
            try {
                // Signal end of stream to video encoder
                videoEncoder?.signalEndOfInputStream()
                drainComplete = true

                videoDrainThread?.join(2000)
                audioThread?.join(2000)

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                audioEncoder?.stop()
                audioEncoder?.release()
                audioEncoder = null

                videoEncoder?.stop()
                videoEncoder?.release()
                videoEncoder = null

                if (isMuxerStarted) {
                    try {
                        mediaMuxer?.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Exception stopping muxer: ${e.message}")
                    }
                }
                mediaMuxer?.release()
                mediaMuxer = null

                pfd?.close()
                pfd = null

                // Finalize MediaStore entry by clearing IS_PENDING
                finalizeMediaStoreUri(outputFileUri)

                onRecordingStopped(outputFileUri, durationMs)
                Log.i(TAG, "Recording successfully stopped and finalized. Duration: ${durationMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing recording: ${e.message}", e)
                onError("Error saving video: ${e.message}")
            } finally {
                cleanup()
            }
        }.start()
    }

    private fun cleanup() {
        isRecording.set(false)
        isPaused.set(false)
        isMuxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
        inputSurface = null
    }

    private fun createMediaStoreUri(): Uri {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "PROCAM_$dateFormat.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ProCam")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("Failed to create MediaStore entry")
    }

    private fun finalizeMediaStoreUri(uri: Uri?) {
        if (uri == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    companion object {
        private const val TAG = "VideoRecorder"
    }
}
