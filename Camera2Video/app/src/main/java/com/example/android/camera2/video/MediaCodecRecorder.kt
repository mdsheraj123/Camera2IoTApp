/*
# Copyright (c) 2020 Qualcomm Innovation Center, Inc.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted (subject to the limitations in the
# disclaimer below) provided that the following conditions are met:
#
#    * Redistributions of source code must retain the above copyright
#      notice, this list of conditions and the following disclaimer.
#
#    * Redistributions in binary form must reproduce the above
#      copyright notice, this list of conditions and the following
#      disclaimer in the documentation and/or other materials provided
#      with the distribution.
#
#    * Neither the name Qualcomm Innovation Center nor the names of its
#      contributors may be used to endorse or promote products derived
#      from this software without specific prior written permission.
#
# NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
# GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
# HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
# IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
# ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
# IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
# OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
# IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.example.android.camera2.video

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class MediaCodecRecorder(private val context: Context,
                         private val width: Int,
                         private val height: Int,
                         private val fps: Int,
                         private val video_encoder: String?,
                         private val audio_encoder: String) : VideoRecorder {

    private var surface: Surface = MediaCodec.createPersistentInputSurface()
    private var videoEncoder: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var videoEncoderRunning = false
    private var audioEncoderRunning = false
    private var audioRecorderRunning = false
    private var muxerCreated = false
    private var muxerStarted = false
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var videoFormat: MediaFormat
    private var audioRecorder: AudioRecord
    private var audioFormat: MediaFormat
    lateinit var audioEncoder: MediaCodec
    private var muxerTrackCount = 0
    val muxerLock = Mutex()

    private val videoStopSyncObject = Object()
    private val audioStopSyncObject = Object()

    private var videoMimeType: String = when (video_encoder) {
        "H264" -> "video/avc"
        "H265" -> "video/hvc"
        else -> {
            throw Exception("Unsupported video format: $video_encoder")
        }
    }

    private val audioMimeType = AUDIO_MIME_TYPE

    init {
        videoFormat = MediaFormat.createVideoFormat(videoMimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, RECORDER_VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        }

        videoEncoder = createVideoEncoder()
        videoEncoder.start()
        GlobalScope.launch {
            videoEncoderHandler(true)
        }

        val minBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)

        var bufferSize = AUDIO_SAMPLES_PER_FRAME * 10
        if (bufferSize < minBufferSize) bufferSize = (minBufferSize / AUDIO_SAMPLES_PER_FRAME + 1) * AUDIO_SAMPLES_PER_FRAME * 2

        audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize)

        audioFormat = MediaFormat()
        audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, AUDIO_SAMPLE_RATE)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
    }

    private fun createVideoEncoder(): MediaCodec {
        return MediaCodec.createEncoderByType(videoMimeType).apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            setInputSurface(surface)
        }
    }

    private fun createAudoioEncoder(): MediaCodec {
        return MediaCodec.createEncoderByType(audioMimeType).apply {
            configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private suspend fun videoEncoderHandler(endOfStream: Boolean) {
        videoEncoderRunning = true
        val encoderOutputBuffers: Array<ByteBuffer> = videoEncoder.getOutputBuffers()
        while (videoEncoderRunning) {
            val bufferInfo = MediaCodec.BufferInfo()
            val encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxerLock.withLock {
                    if (muxerCreated) {
                        muxerTrackCount++
                        videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                        if (muxerTrackCount == 2 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }
            } else if (encoderStatus < 0) {

            } else {
                if (endOfStream) {
                    break
                }

                val encodedData = encoderOutputBuffers[encoderStatus]
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG !== 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size !== 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxerLock.withLock {
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                }
                videoEncoder.releaseOutputBuffer(encoderStatus, false);

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM !== 0) {
                    break
                }
            }
        }
        releaseVideoEncoder()
        synchronized(videoStopSyncObject) {
            videoStopSyncObject.notifyAll()
        }
    }

    private suspend fun audioEncoderHandler(endOfStream: Boolean) {
        audioEncoderRunning = true
        val encoderOutputBuffers: Array<ByteBuffer> = audioEncoder.getOutputBuffers()
        var oldTimeStampUs = 0L
        while (audioEncoderRunning) {
            val bufferInfo = MediaCodec.BufferInfo()
            val encoderStatus = audioEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxerLock.withLock {
                    if (muxerCreated) {
                        muxerTrackCount++
                        audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                        if (muxerTrackCount == 2 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }
            } else if (encoderStatus < 0) {

            } else {
                if (endOfStream) {
                    break
                }

                val encodedData = encoderOutputBuffers[encoderStatus]
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG !== 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size !== 0 && muxerStarted && (oldTimeStampUs < bufferInfo.presentationTimeUs)) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxerLock.withLock {
                        muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                    }
                    oldTimeStampUs = bufferInfo.presentationTimeUs
                }
                audioEncoder.releaseOutputBuffer(encoderStatus, false);

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM !== 0) {
                    break
                }
            }
        }
        releaseAudioEncoder()
        synchronized(audioStopSyncObject) {
            audioStopSyncObject.notifyAll()
        }
    }

    private suspend fun audioRecorderHandler(endOfStream: Boolean) {
        audioRecorderRunning = true
        val encoderInputBuffers: Array<ByteBuffer> = audioEncoder.getInputBuffers()
        while (audioRecorderRunning) {
            val inputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoderInputBuffers[inputBufferIndex]
                inputBuffer.clear()
                val audioTimestamp = AudioTimestamp()
                var inputLength = 0
                inputLength = audioRecorder.read(inputBuffer, AUDIO_SAMPLES_PER_FRAME, AudioRecord.READ_BLOCKING)

                val status = audioRecorder.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
                if (status != AudioRecord.SUCCESS) {
                    throw Exception("Invalid audio timestamp")
                }

                val presentationTimeUs = audioTimestamp.nanoTime / 1000

                if (endOfStream) {
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                } else {
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, 0)
                }
            }
        }
        audioRecorder.stop()
        audioEncoderRunning = false
    }

    private fun createMuxer(): MediaMuxer {
        muxerCreated = true
        return  MediaMuxer(createVideoFile(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun releaseVideoEncoder() {
        videoEncoderRunning = false
        videoEncoder.stop()
        videoEncoder.release()
    }

    private fun releaseAudioEncoder() {
        audioEncoderRunning = false
        audioEncoder.stop()
        audioEncoder.release()
    }

    private fun releaseMuxer() {
        if(muxerStarted) {
            muxer.stop()
        }
        if (muxerCreated) {
            muxer.release()
        }
        muxerCreated = false
        muxerStarted = false
        muxerTrackCount = 0
    }

    override fun start() {
        muxer = createMuxer()
        videoEncoder = createVideoEncoder()
        audioEncoder = createAudoioEncoder()
        videoEncoder.start()
        GlobalScope.launch {
            videoEncoderHandler(false)
        }
        audioEncoder.start()
        GlobalScope.launch {
            audioEncoderHandler(false)
        }
        audioRecorder.startRecording()
        GlobalScope.launch {
            audioRecorderHandler(false)
        }
    }

    override fun stop() {
        videoEncoderRunning = false
        audioRecorderRunning = false

        synchronized(videoStopSyncObject) {
            videoStopSyncObject.wait()
        }

        synchronized(audioStopSyncObject) {
            audioStopSyncObject.wait()
        }

        releaseMuxer()
    }

    override fun destroy() {
        surface.release()
    }

    override fun getRecorderSurface(): Surface {
        return surface
    }

    private fun createVideoFile(): FileDescriptor {
        val dateTaken = System.currentTimeMillis()
        val filename = "VID_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US).format(Date())}.mp4"

        Log.d(TAG, "Recorder output file: $filename")

        val values = ContentValues()
        values.put(MediaStore.Video.Media.TITLE, filename)
        values.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        values.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        val file = uri?.let { context.contentResolver.openFileDescriptor(it, "w") }

        if (file != null) {
            return file.fileDescriptor
        } else {
            throw Exception("Fail to create media file.")
        }
    }

    companion object {
        private const val RECORDER_VIDEO_BITRATE = 10_000_000
        private const val IFRAME_INTERVAL = 0
        private const val TIMEOUT_USEC = 10000L
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_SAMPLES_PER_FRAME = 1024
        private const val AUDIO_BITRATE = 128000
        private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"
        private val TAG = VideoRecorder::class.simpleName
    }
}