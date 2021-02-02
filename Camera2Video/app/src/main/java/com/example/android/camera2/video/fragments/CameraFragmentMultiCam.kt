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
import android.hardware.camera2.*
import android.media.ExifInterface
import android.media.MediaActionSound
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.getDisplaySmartSize
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.*
import com.example.android.camera2.video.CameraActivity.Companion.printAppVersion
import com.example.android.camera2.video.MediaCodecRecorder.Companion.MIN_REQUIRED_RECORDING_TIME_MILLIS
import com.example.android.camera2.video.overlay.VideoOverlay
import kotlinx.android.synthetic.main.fragment_camera_multicam.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraFragmentMultiCam : Fragment() {
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private lateinit var cameraBase0: CameraBase
    private lateinit var cameraBase1: CameraBase
    private lateinit var cameraBase2: CameraBase

    private lateinit var characteristics0: CameraCharacteristics
    private lateinit var characteristics1: CameraCharacteristics
    private lateinit var characteristics2: CameraCharacteristics

    private lateinit var viewFinder0: AutoFitSurfaceView
    private lateinit var viewFinder1: AutoFitSurfaceView

    private lateinit var overlay: View
    private lateinit var settings: CameraSettings
    private lateinit var relativeOrientation0: OrientationLiveData
    private lateinit var relativeOrientation1: OrientationLiveData

    private lateinit var previewSize0: Size
    private lateinit var previewSize1: Size

    private val videoOverlayList = mutableListOf<VideoOverlay>()

    private val camera0Id = "0"
    private val camera1Id = "1"
    private val camera2Id = "2"

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_multicam, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        printAppVersion(requireContext().applicationContext)
        cameraBase0 = CameraBase(requireContext().applicationContext)
        cameraBase1 = CameraBase(requireContext().applicationContext)
        settings = CameraSettingsUtil.getCameraSettings(requireContext().applicationContext)

        // If there is not recording stream, disable recording button.
        if (settings.recorderInfo.isEmpty()) recorder_button.visibility = View.INVISIBLE

        characteristics0 = cameraManager.getCameraCharacteristics(camera0Id)
        characteristics1 = cameraManager.getCameraCharacteristics(camera1Id)

        if (settings.threeCamUse) {
            cameraBase2 = CameraBase(requireContext().applicationContext)
            characteristics2 = cameraManager.getCameraCharacteristics(camera2Id)
        }

        overlay = view.findViewById(R.id.overlay)
        viewFinder0 = view.findViewById(R.id.view_finder)
        viewFinder1 = view.findViewById(R.id.view_finder1)

        viewFinder0.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                previewSize0 = getPreviewOutputSize(
                        viewFinder0.display, characteristics0, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${viewFinder0.width} x ${viewFinder0.height}")
                Log.d(TAG, "Selected preview size: $previewSize0")
                viewFinder0.setAspectRatio(previewSize0.width, previewSize0.height)
            }
        })

        viewFinder1.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                previewSize1 = getPreviewOutputSize(
                       viewFinder1.display, characteristics1, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${viewFinder1.width} x ${viewFinder1.height}")
                Log.d(TAG, "Selected preview size: $previewSize1")
                viewFinder1.setAspectRatio(previewSize1.width, previewSize1.height)
                viewFinder1.post { initializeCamera() }
            }
        })

        val cameraMenu = CameraMenu(this.context, view)
        cameraMenu.setOnCameraMenuListener(object: CameraMenu.OnCameraMenuListener {
            override fun onAELock(value: Boolean) {
                cameraBase0.setAELock(value)
                cameraBase1.setAELock(value)
                Log.d(TAG, "AE Lock: $value")
            }
            override fun onAWBLock(value: Boolean) {
                cameraBase0.setAWBLock(value)
                cameraBase1.setAWBLock(value)
                Log.d(TAG, "AWB Lock: $value")
            }
            override fun onEffectMode(value: Int) {
                cameraBase0.setEffectMode(value)
                cameraBase1.setEffectMode(value)
                Log.d(TAG, "Effect mode: $value")
            }
            override fun onNRMode(value: Int) {
                cameraBase0.setNRMode(value)
                cameraBase1.setNRMode(value)
                Log.d(TAG, "NR mode: $value")
            }

            override fun onAntiBandingMode(value: Int) {
                cameraBase0.setAntiBandingMode(value)
                cameraBase1.setAntiBandingMode(value)
                Log.d(TAG, "Antibanding mode: $value")
            }

            override fun onAEMode(value: Int) {
                cameraBase0.setAEMode(value)
                cameraBase1.setAEMode(value)
                Log.d(TAG, "AE mode: $value")
            }

            override fun onAWBMode(value: Int) {
                cameraBase0.setAWBMode(value)
                cameraBase1.setAWBMode(value)
                Log.d(TAG, "AWB mode: $value")
            }

            override fun onAFMode(value: Int) {
                cameraBase0.setAFMode(value)
                cameraBase1.setAFMode(value)
                Log.d(TAG, "AF mode: $value")
            }

            override fun onIRMode(value: Int) {
                cameraBase0.setIRMode(value)
                cameraBase1.setIRMode(value)
                Log.d(TAG, "IR mode: $value")
            }

            override fun onADRCMode(value: Byte) {
                cameraBase0.setADRCMode(value)
                cameraBase1.setADRCMode(value)
                Log.d(TAG, "ADRC mode: $value")
            }

            override fun onExpMeteringMode(value: Int) {
                cameraBase0.setExpMeteringMode(value)
                cameraBase1.setExpMeteringMode(value)
                Log.d(TAG, "Exp Metering mode: $value")
            }

            override fun onISOMode(value: Long) {
                cameraBase0.setISOMode(value)
                cameraBase1.setISOMode(value)
                Log.d(TAG, "ISO mode: $value")
            }

            override fun onSetZoom(value: Int) {
                cameraBase0.setZoom(value)
                cameraBase1.setZoom(value)
                Log.d(TAG, "Zoom value: $value")
            }
        })
        view.setOnClickListener() {
            cameraMenu.show()
        }

        // Used to rotate the output media to match device orientation
        relativeOrientation0 = OrientationLiveData(requireContext(), characteristics0).apply {
            observe(viewLifecycleOwner, Observer {
                orientation -> Log.d(CameraFragmentVideo.TAG, "Orientation changed: $orientation")
                val sensorOrientationDegrees0 =
                        characteristics0.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                val sign = if (characteristics0.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
                val requiredOrientation = ((orientation - sensorOrientationDegrees0)*sign).toFloat()
                capture_button.rotation = requiredOrientation
                recorder_button.rotation = requiredOrientation
                chronometer_dual.rotation = requiredOrientation
                thumbnailButton3.rotation = requiredOrientation
            })
        }
        // Used to rotate the output media to match device orientation
        relativeOrientation1 = OrientationLiveData(requireContext(), characteristics1).apply {
            observe(viewLifecycleOwner, Observer {
                orientation -> Log.d(CameraFragmentVideo.TAG, "Orientation changed: $orientation")
            })
        }
    }


    private fun startChronometer() {
        chronometer_dual.base = SystemClock.elapsedRealtime()
        chronometer_dual.visibility = View.VISIBLE;
        chronometer_dual.start()
    }

    private fun stopChronometer() {
        chronometer_dual.visibility = View.INVISIBLE;
        chronometer_dual.stop()
    }

    private fun addCameraStreams(camBase: CameraBase, settings: CameraSettings, previewSurface: Surface, previewSize: Size) {
        var availableCameraStreams = MAX_CAMERA_STREAMS

        if (settings.displayOn) {
            if (settings.previewInfo.overlayEnable) {
                val previewOverlay = VideoOverlay(previewSurface, previewSize.width, previewSize.height, settings.previewInfo.fps.toFloat(),0.0f)
                previewOverlay.setTextOverlay("Preview overlay", 0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
                videoOverlayList.add(previewOverlay)
                camBase.addPreviewStream(previewOverlay.getInputSurface())
            } else {
                camBase.addPreviewStream(previewSurface)
            }
            availableCameraStreams--
        }

        camBase.addSnapshotStream(settings.snapshotInfo)
        availableCameraStreams--

        val sharedStreamSurfaces = mutableListOf<Surface>()
        var sharedStreamsSize: Size = Size(0,0)

        // Search for best resolution for shared streams
        for ((streamCount, stream) in settings.recorderInfo.withIndex()) {
            if (availableCameraStreams - streamCount <= 1) {
                if (sharedStreamsSize.width < stream.width) {
                    sharedStreamsSize = Size(stream.width, stream.height)
                }
            }
        }

        for ((streamCount, stream) in settings.recorderInfo.withIndex()) {
            val recorder = VideoRecorderFactory(requireContext().applicationContext, stream, stream.videoRecorderType)
            camBase.addVideoRecorder(recorder)
            if (stream.overlayEnable) {
                lateinit var videoOverlay: VideoOverlay
                if (availableCameraStreams > 1) {
                    videoOverlay = VideoOverlay(recorder.getRecorderSurface(), stream.width, stream.height, stream.fps.toFloat(), camBase.getSensorOrientation().toFloat())
                    camBase.addStream(videoOverlay.getInputSurface())
                } else {
                    videoOverlay = VideoOverlay(recorder.getRecorderSurface(), sharedStreamsSize.width, sharedStreamsSize.height, stream.fps.toFloat(), camBase.getSensorOrientation().toFloat())
                    sharedStreamSurfaces.add(videoOverlay.getInputSurface())
                }
                videoOverlay.setTextOverlay("Stream $streamCount overlay",
                        0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
                videoOverlayList.add(videoOverlay)
            } else {
                if (availableCameraStreams > 1) {
                    camBase.addStream(recorder.getRecorderSurface())
                } else {
                    sharedStreamSurfaces.add(recorder.getRecorderSurface())
                }
            }
            availableCameraStreams--
        }
        if (sharedStreamSurfaces.isNotEmpty()) {
            camBase.addSharedStream(sharedStreamSurfaces)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        cameraBase0.openCamera(camera0Id)

        cameraBase0.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase0.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase0.setSHDREnable(settings.cameraParams.shdr_enable)
        cameraBase0.setFramerate(settings.previewInfo.fps)

        // Supports max two streams per camera. Remove the excess.
        if (settings.recorderInfo.size > 2) {
            for (i in 2 until settings.recorderInfo.size) {
                settings.recorderInfo.removeAt(i)
            }
        }
        addCameraStreams(cameraBase0, settings, viewFinder0.holder.surface, previewSize0)

        cameraBase1.openCamera(camera1Id)

        cameraBase1.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase1.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase1.setSHDREnable(settings.cameraParams.shdr_enable)
        cameraBase1.setFramerate(settings.previewInfo.fps)

        addCameraStreams(cameraBase1, settings, viewFinder1.holder.surface, previewSize1)

        if (settings.threeCamUse) {
            cameraBase2.openCamera(camera2Id)
            val rawSnap = StreamInfo(0,0,0, "RAW")
            cameraBase2.addSnapshotStream(rawSnap)
        }

        cameraBase0.startCamera()
        cameraBase1.startCamera()

        if (settings.threeCamUse) cameraBase2.startCamera()

        val sound = MediaActionSound()

        if (settings.recorderInfo.isNotEmpty()) {
            recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
            recorder_button.setOnClickListener {
                if (recording) {
                    if (SystemClock.elapsedRealtime() - chronometer_dual.base > MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                        cameraBase1.stopRecording()
                        cameraBase0.stopRecording()
                        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                        recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
                        recording = false
                        stopChronometer()
                    } else {
                        Log.d(CameraFragmentVideo.TAG, "Cannot record a video less than $MIN_REQUIRED_RECORDING_TIME_MILLIS ms")
                    }
                } else {
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    cameraBase0.startRecording(relativeOrientation0.value)
                    cameraBase1.startRecording(relativeOrientation1.value)
                    recorder_button.setBackgroundResource(android.R.drawable.presence_video_busy)
                    startChronometer()
                    recording = true
                }
            }
        }
        capture_button.setOnClickListener {
            it.isEnabled = false
            var snapshot0Flag = false
            var snapshot1Flag = false
            var snapshot2Flag = false

            if (!settings.threeCamUse) snapshot2Flag = true

            lifecycleScope.launch(Dispatchers.IO) {
                cameraBase0.takeSnapshot(relativeOrientation0.value).use { result ->
                    Log.d(TAG, "Result received: $result")
                    val outputFilePath = cameraBase0.saveResult(result)

                    // If the result is a JPEG file, update EXIF metadata with orientation info
                    if (outputFilePath?.substring(outputFilePath!!.lastIndexOf(".")) == ".jpg") {
                        val exif = ExifInterface(outputFilePath)
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                        exif.saveAttributes()
                        Log.d(TAG, "EXIF metadata saved: $outputFilePath")
                    }
                }
                snapshot0Flag = true
                if (snapshot0Flag and snapshot1Flag and snapshot2Flag) {
                    it.post { it.isEnabled = true }
                }
            }
            lifecycleScope.launch(Dispatchers.IO) {
                cameraBase1.takeSnapshot(relativeOrientation1.value).use { result ->
                    Log.d(TAG, "Result received: $result")
                    val outputFilePath = cameraBase1.saveResult(result)

                    // If the result is a JPEG file, update EXIF metadata with orientation info
                    if (outputFilePath?.substring(outputFilePath!!.lastIndexOf(".")) == ".jpg") {
                        val exif = ExifInterface(outputFilePath)
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                        exif.saveAttributes()
                        Log.d(TAG, "EXIF metadata saved: $outputFilePath")
                    }
                }
                snapshot1Flag = true
                if (snapshot0Flag and snapshot1Flag and snapshot2Flag) {
                    it.post { it.isEnabled = true }
                }
            }
            if (settings.threeCamUse) {
                lifecycleScope.launch(Dispatchers.IO) {
                    cameraBase2.takeSnapshot(0).use { result ->
                        Log.d(TAG, "Result received: $result")
                        val outputFilePath = cameraBase2.saveResult(result)
                    }
                    snapshot2Flag = true
                    if (snapshot0Flag and snapshot1Flag and snapshot2Flag) {
                        it.post { it.isEnabled = true }
                    }
                }
            }
            sound.play(MediaActionSound.SHUTTER_CLICK)
        }

        thumbnailButton3.setOnClickListener {
            Log.d(TAG, "Thumbnail icon pressed")
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.type = "image/*"
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    override fun onPause() {
        if (recording) {
            cameraBase1.stopRecording()
            cameraBase0.stopRecording()
            recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
            recording = false
            stopChronometer()
        }
        try {
            cameraBase0.close()
            cameraBase1.close()
            if (settings.threeCamUse) cameraBase2.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
        for (overlay in videoOverlayList) {
            overlay.release()
        }
        super.onPause()
    }

    companion object {
        private val TAG = CameraFragmentMultiCam::class.java.simpleName
        var recording = false
        const val MAX_CAMERA_STREAMS = 3
    }
}
