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
import android.media.MediaCodec
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.*


class MediaRecorderRecorder(val context: Context,
                    val width: Int,
                    val height: Int,
                    val fps: Int,
                    private val video_encoder: String?,
                    private val audio_encoder: String) : VideoRecorder {

    private var outputFile = createFile(context, "mp4")
    private lateinit var recorder: MediaRecorder
    private val surface: Surface by lazy {

        val recorderSurface = MediaCodec.createPersistentInputSurface()
        createRecorder(recorderSurface, FileDescriptor()).apply {
            prepare()
            release()
        }

        recorderSurface
    }

    private fun createRecorder(srfs: Surface, fd: FileDescriptor) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if (fd.valid()) {
            setOutputFile(fd)
        } else {
            setOutputFile(outputFile.absolutePath)
        }
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        if (fps > 0) setVideoFrameRate(fps)
        setVideoSize(width, height)
        when (video_encoder) {
            "H264" -> setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            "H265" -> setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            else -> {
                throw Exception("Unsupported video format: $video_encoder")
            }
        }

        var audioFormat = when (audio_encoder) {
            "AAC" -> {
                MediaRecorder.AudioEncoder.AAC
            }
            "AAC_ELD" -> {
                MediaRecorder.AudioEncoder.AAC_ELD
            }
            "AMR_NB" -> {
                MediaRecorder.AudioEncoder.AMR_NB
            }
            "AMR_WB" -> {
                MediaRecorder.AudioEncoder.AMR_WB
            }
            "DEFAULT" -> {
                MediaRecorder.AudioEncoder.DEFAULT
            }
            "HE_AAC" -> {
                MediaRecorder.AudioEncoder.HE_AAC
            }
            "OPUS" -> {
                MediaRecorder.AudioEncoder.OPUS
            }
            else -> {
                throw Exception("Unsupported audio format: $audio_encoder")
            }
        }
        setAudioEncoder(audioFormat)
        setInputSurface(srfs)
    }

    override fun start() {
        recorder = createVideoFile()?.let { createRecorder(surface, it) }!!
        recorder.prepare()
        recorder.start()
    }

    override fun stop() {
        recorder.stop()
        recorder.release()
    }

    override fun destroy() {
        surface.release()
    }

    override fun getRecorderSurface(): Surface {
        return surface
    }

    private fun createFile(context: Context, extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
    }

    private fun createVideoFile(): FileDescriptor? {
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
        val file = uri?.let { context.contentResolver.openFileDescriptor(it, "w") };

        if (file != null) {
            return file.fileDescriptor
        } else {
            throw Exception("Fail to create media file.")
        }
    }

    companion object {
        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L
        private val TAG = VideoRecorder::class.simpleName
    }
}