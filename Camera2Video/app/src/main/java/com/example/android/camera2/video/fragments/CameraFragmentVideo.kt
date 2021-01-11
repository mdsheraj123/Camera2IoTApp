/*
# Copyright (c) 2020-2021 Qualcomm Innovation Center, Inc.
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

package com.example.android.camera2.video.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.webkit.MimeTypeMap
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.*
import com.example.android.camera2.video.CameraSettingsUtil.getCameraSettings
import com.example.android.camera2.video.MediaCodecRecorder.Companion.MIN_REQUIRED_RECORDING_TIME_MILLIS
import com.example.android.camera2.video.overlay.VideoOverlay
import kotlinx.android.synthetic.main.fragment_camera_video.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CameraFragmentVideo : Fragment() {
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private lateinit var cameraBase: CameraBase

    private lateinit var characteristics: CameraCharacteristics

    private lateinit var viewFinder: AutoFitSurfaceView

    private lateinit var overlay: View

    private lateinit var settings: CameraSettings

    private lateinit var relativeOrientation: OrientationLiveData

    private lateinit var previewSize: Size

    private val videoOverlayList = mutableListOf<VideoOverlay>()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_video, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraBase = CameraBase(requireContext().applicationContext)
        settings = getCameraSettings(requireContext().applicationContext)

        // Make Video Record button invisible if there is no encoder stream
        if (settings.recorderInfo.isEmpty()) recorder_button.visibility = View.INVISIBLE

        characteristics = cameraManager.getCameraCharacteristics(settings.cameraId)

        overlay = view.findViewById(R.id.overlay)
        viewFinder = view.findViewById(R.id.view_finder)

        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                previewSize = getPreviewOutputSize(
                        viewFinder.display, characteristics, SurfaceHolder::class.java)

                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                viewFinder.setAspectRatio(previewSize.width, previewSize.height)
                viewFinder.post { initializeCamera() }
            }
        })

        val cameraMenu = CameraMenu(this.context, view)
        cameraMenu.setOnCameraMenuListener(object: CameraMenu.OnCameraMenuListener {
            override fun onAELock(value: Boolean) {
                cameraBase.setAELock(value)
                Log.d(TAG, "AE Lock: $value")
            }
            override fun onAWBLock(value: Boolean) {
                cameraBase.setAWBLock(value)
                Log.d(TAG, "AWB Lock: $value")
            }
            override fun onEffectMode(value: Int) {
                cameraBase.setEffectMode(value)
                Log.d(TAG, "Effect mode: $value")
            }
            override fun onNRMode(value: Int) {
                cameraBase.setNRMode(value)
                Log.d(TAG, "NR mode: $value")
            }
            override fun onAntiBandingMode(value: Int) {
                cameraBase.setAntiBandingMode(value)
                Log.d(TAG, "Antibanding mode: $value")
            }

            override fun onAEMode(value: Int) {
                cameraBase.setAEMode(value)
                Log.d(TAG, "AE mode: $value")
            }

            override fun onAWBMode(value: Int) {
                cameraBase.setAWBMode(value)
                Log.d(TAG, "AWB mode: $value")
            }

            override fun onAFMode(value: Int) {
                cameraBase.setAFMode(value)
                Log.d(TAG, "AF mode: $value")
            }

            override fun onIRMode(value: Int) {
                cameraBase.setIRMode(value)
                Log.d(TAG, "IR mode: $value")
            }

            override fun onADRCMode(value: Byte) {
                cameraBase.setADRCMode(value)
                Log.d(TAG, "ADRC mode: $value")
            }

            override fun onExpMeteringMode(value: Int) {
                cameraBase.setExpMeteringMode(value)
                Log.d(TAG, "Exp Metering mode: $value")
            }

            override fun onISOMode(value: Long) {
                cameraBase.setISOMode(value)
                Log.d(TAG, "ISO mode: $value")
            }

            override fun onSetZoom(value: Int) {
                cameraBase.setZoom(value)
                Log.d(TAG, "Zoom value: $value")
            }
        })
        view.setOnClickListener() {
            cameraMenu.show()
        }

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                orientation -> Log.d(TAG, "Orientation changed: $orientation")
                val sensorOrientationDegrees =
                        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
                val requiredOrientation = ((orientation - sensorOrientationDegrees)*sign).toFloat()
                recorder_button.rotation = requiredOrientation
                chronometer.rotation = requiredOrientation
                thumbnailButton.rotation = requiredOrientation
            })
        }
    }

    private fun startChronometer() {
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.visibility = View.VISIBLE;
        chronometer.start()
    }

    private fun stopChronometer() {
        chronometer.visibility = View.INVISIBLE;
        chronometer.stop()
    }

    private fun createVideoThumb() = ThumbnailUtils.createVideoThumbnail(cameraBase.getCurrentVideoFilePath(), MediaStore.Video.Thumbnails.MICRO_KIND)

    private fun createRoundThumb() : RoundedBitmapDrawable {
        val drawable = RoundedBitmapDrawableFactory.create(resources, createVideoThumb())
        drawable.isCircular = true
        return drawable
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        cameraBase.openCamera(settings.cameraId)

        cameraBase.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase.setSHDREnable(settings.cameraParams.shdr_enable)

        cameraBase.setFramerate(settings.previewInfo.fps)

        if (settings.displayOn) {
            if (settings.previewInfo.overlayEnable) {
                val previewOverlay = VideoOverlay(viewFinder.holder.surface, previewSize.width, previewSize.height, 0.0f)
                previewOverlay.setTextOverlay("Preview overlay", 0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
                videoOverlayList.add(previewOverlay)
                cameraBase.addPreviewStream(previewOverlay.getInputSurface())
            } else {
                cameraBase.addPreviewStream(viewFinder.holder.surface)
            }
        }

        for ((streamCount, stream) in settings.recorderInfo.withIndex()) {
            val recorder = VideoRecorderFactory(requireContext().applicationContext, stream, stream.videoRecorderType)
            cameraBase.addVideoRecorder(recorder)
            if (stream.overlayEnable) {
                val videoOverlay = VideoOverlay(recorder.getRecorderSurface(), stream.width, stream.height, sensorOrientation.toFloat())
                videoOverlay.setTextOverlay("Stream $streamCount overlay",
                        0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
                videoOverlayList.add(videoOverlay)
                cameraBase.addStream(videoOverlay.getInputSurface())
            } else {
                cameraBase.addStream(recorder.getRecorderSurface())
            }
        }

        cameraBase.startCamera()
        if (settings.recorderInfo.isNotEmpty()) {
            val sound = MediaActionSound()
            recorder_button.setOnClickListener {
                if (recording) {
                    if (SystemClock.elapsedRealtime() - chronometer.base > MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                        cameraBase.stopRecording()
                        broadcastFile()
                        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                        recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
                        if (settings.recorderInfo[0].storageEnable) thumbnailButton.setImageDrawable(createRoundThumb())
                        recording = false
                        stopChronometer()
                        Log.d(TAG, "Recorder stop")
                    } else {
                        Log.d(TAG, "Cannot record a video less than $MIN_REQUIRED_RECORDING_TIME_MILLIS ms")
                    }
                } else {
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    cameraBase.startRecording(relativeOrientation.value)
                    recorder_button.setBackgroundResource(android.R.drawable.presence_video_busy)
                    startChronometer()
                    recording = true
                    Log.d(TAG, "Recorder start")
                }
            }
        }
        thumbnailButton.setOnClickListener {
            Log.d(TAG, "Thumbnail icon pressed")
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.type = "image/*"
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun broadcastFile() {
        // Broadcasts the media file to the rest of the system 	219
        MediaScannerConnection.scanFile(
                view?.context, arrayOf(cameraBase.getCurrentVideoFilePath()), null, null)
    }

    override fun onStop() {
        if (recording) {
            cameraBase.stopRecording()
            broadcastFile()
            recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
            if (settings.recorderInfo[0].storageEnable) thumbnailButton.setImageDrawable(createRoundThumb())
            recording = false
            stopChronometer()
            Log.d(TAG, "Recorder stop")
        }
        try {
            cameraBase.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
        for (overlay in videoOverlayList) {
            overlay.release()
        }
        super.onStop()
    }

    companion object {
        val TAG = CameraFragmentVideo::class.java.simpleName
        var recording = false
    }
}

