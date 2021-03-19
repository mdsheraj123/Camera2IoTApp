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
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.ExifInterface
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.*
import com.example.android.camera2.video.CameraActivity.Companion.printAppVersion
import com.example.android.camera2.video.CameraSettingsUtil.getCameraSettings
import com.example.android.camera2.video.MediaCodecRecorder.Companion.MIN_REQUIRED_RECORDING_TIME_MILLIS
import com.example.android.camera2.video.overlay.VideoOverlay
import kotlinx.android.synthetic.main.fragment_camera_video.*
import kotlinx.android.synthetic.main.fragment_camera_video.capture_button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CameraFragmentVideo : Fragment(),CameraReadyListener {
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
        Log.i(TAG, "onViewCreated")
        super.onViewCreated(view, savedInstanceState)
        printAppVersion(requireContext().applicationContext)
        cameraBase = CameraBase(requireContext().applicationContext)
        cameraBase.listeners.add(this)
        settings = getCameraSettings(requireContext().applicationContext)

        // Make Snapshot button invisible if there is no snapshot stream
        if (!settings.snapshotOn) capture_button.visibility = View.INVISIBLE
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
                previewSize = if (settings.previewInfo.fps > 30 && settings.cameraId != "4") {
                    Size(1280, 720)
                } else if (settings.cameraId == "4") {
                    // For logical camera keep w X h into 2:1.
                    Size(1440, 720)
                } else {
                    getPreviewOutputSize(
                            viewFinder.display, characteristics, SurfaceHolder::class.java)
                }
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
            override fun onDefog(value: Boolean): Boolean {
                Log.d(TAG, "Defog value: $value")
                return cameraBase.setDefog(value)
            }

            override fun onExposureTable(value: Boolean): Boolean {
                Log.d(TAG, "Exposure value: $value")
                return cameraBase.setExposureTable(value)
            }

            override fun onANRTable(value: Boolean): Boolean {
                Log.d(TAG, "ANR value: $value")
                return cameraBase.setANRTable(value)
            }

            override fun onLTMTable(value: Boolean): Boolean {
                Log.d(TAG, "LTM value: $value")
                return cameraBase.setLTMTable(value)
            }

            override fun onSaturationLevel(value: Int) {
                cameraBase.setSaturationLevel(value)
                Log.d(TAG, "Saturation Level: $value")
            }

            override fun onSharpnessLevel(value: Int) {
                cameraBase.setSharpnessLevel(value)
                Log.d(TAG, "Sharpness Level: $value")
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

    private fun createThumb(path: String?,type: Int): Bitmap? {
        return if(type== THUMBNAIL_TYPE_IMAGE) {
            path?.let { ThumbnailUtils.createImageThumbnail(it, MediaStore.Images.Thumbnails.MICRO_KIND) }
        } else {
            path?.let { ThumbnailUtils.createVideoThumbnail(it, MediaStore.Video.Thumbnails.MICRO_KIND) }
        }
    }

    private fun createRoundThumb(path: String?, type: Int) : RoundedBitmapDrawable {
        Log.i(TAG, "createRoundThumb path=$path type=$type")
        val drawable = RoundedBitmapDrawableFactory.create(resources, createThumb(path,type))
        drawable.isCircular = true
        return drawable
    }

    private fun addCameraStreams(camBase: CameraBase, settings: CameraSettings) {
        Log.i(TAG, "addCameraStreams start")
        var availableCameraStreams = MAX_CAMERA_STREAMS

        if (settings.displayOn) {
            if (settings.previewInfo.overlayEnable) {
                val previewOverlay = VideoOverlay(viewFinder.holder.surface, previewSize.width, previewSize.height, settings.previewInfo.fps.toFloat(),0.0f)
                previewOverlay.setTextOverlay("Preview overlay", 0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
                videoOverlayList.add(previewOverlay)
                camBase.addPreviewStream(previewOverlay.getInputSurface())
                Log.i(TAG, "addCameraStreams preview ${settings.previewInfo}")
            } else {
                camBase.addPreviewStream(viewFinder.holder.surface)
                Log.i(TAG, "addCameraStreams preview ${settings.previewInfo}")
            }
            availableCameraStreams--
        }

        if (settings.snapshotOn) {
            camBase.addSnapshotStream(settings.snapshotInfo)
            availableCameraStreams--
        }

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
                    Log.i(TAG, "addCameraStreams encoded stream$streamCount $stream")
                    availableCameraStreams--
                } else {
                    videoOverlay = VideoOverlay(recorder.getRecorderSurface(), sharedStreamsSize.width, sharedStreamsSize.height, stream.fps.toFloat(), camBase.getSensorOrientation().toFloat())
                    sharedStreamSurfaces.add(videoOverlay.getInputSurface())
                    Log.i(TAG, "addCameraStreams encoded stream$streamCount $stream")
                }
                videoOverlay.setTextOverlay("Stream $streamCount overlay",
                        0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
                videoOverlayList.add(videoOverlay)
            } else {
                if (availableCameraStreams > 1) {
                    camBase.addStream(recorder.getRecorderSurface())
                    Log.i(TAG, "addCameraStreams encoded stream$streamCount $stream")
                    availableCameraStreams--
                } else {
                    sharedStreamSurfaces.add(recorder.getRecorderSurface())
                    Log.i(TAG, "addCameraStreams encoded stream$streamCount $stream")
                }
            }
        }
        if (sharedStreamSurfaces.isNotEmpty()) {
            camBase.addSharedStream(sharedStreamSurfaces)
            availableCameraStreams--
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        Log.i(TAG, "initializeCamera")
        cameraBase.openCamera(settings.cameraId)

        cameraBase.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase.setSHDREnable(settings.cameraParams.shdr_enable)
        cameraBase.setFramerate(settings.previewInfo.fps)

        addCameraStreams(cameraBase, settings)
        cameraBase.setExposureValue(settings.cameraParams.exposure_value)
        cameraBase.setZSL(settings.cameraParams.hal_zsl_enable)

        cameraBase.startCamera()

        val sound = MediaActionSound()
        if(settings.snapshotOn) {
            capture_button.setOnClickListener {
                Log.i(TAG, "capture_button pressed")
                if(settings.mjpegOn) {
                    if(recordingMJPEG) {
                        if(SystemClock.elapsedRealtime() - chronometer.base > MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                            cameraBase.takeMJPEG(false)
                            capture_button.background = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_shutter)
                            stopChronometer()
                            sound.play(MediaActionSound.SHUTTER_CLICK)
                            recordingMJPEG = false
                            Log.i(TAG, "recordingMJPEG stopped")
                        } else {
                            Log.d(TAG, "Cannot record mjpeg less than $MIN_REQUIRED_RECORDING_TIME_MILLIS ms")
                        }
                    } else {
                        Log.i(TAG, "recordingMJPEG started")
                        recordingMJPEG = true
                        sound.play(MediaActionSound.SHUTTER_CLICK)
                        capture_button.background = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_shutter_mjpeg)
                        cameraBase.takeMJPEG(true)
                        startChronometer()
                    }
                } else {
                    sound.play(MediaActionSound.SHUTTER_CLICK)
                    it.isEnabled = false
                    Log.i(TAG, "capture_button disabled")
                    lifecycleScope.launch(Dispatchers.IO) {
                        cameraBase.takeSnapshot(relativeOrientation.value).use { result ->
                            Log.d(TAG, "Result received: $result")
                            val outputFilePath = cameraBase.saveResult(result)

                            // If the result is a JPEG file, update EXIF metadata with orientation info
                            if (outputFilePath?.substring(outputFilePath!!.lastIndexOf(".")) == ".jpg") {
                                val exif = ExifInterface(outputFilePath)
                                exif.setAttribute(ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                                exif.saveAttributes()
                                Log.d(TAG, "EXIF metadata saved: $outputFilePath")
                            }
                        }
                        it.post {
                            if (settings.snapshotInfo.encoding == "JPEG") {
                                broadcastFile(cameraBase.currentSnapshotFilePath)
                                thumbnailButton.setImageDrawable(createRoundThumb(cameraBase.currentSnapshotFilePath, THUMBNAIL_TYPE_IMAGE))
                            }
                            it.isEnabled = true
                            Log.i(TAG, "capture_button enabled")
                        }
                    }
                }
            }
        }
        if (settings.recorderInfo.isNotEmpty()) {
            recorder_button.setOnClickListener {
                if (recording) {
                    if (SystemClock.elapsedRealtime() - chronometer.base > MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                        Log.i(TAG, "stopRecording enter")
                        cameraBase.stopRecording()
                        broadcastFile(cameraBase.getCurrentVideoFilePath())
                        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                        recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
                        if (settings.recorderInfo[0].storageEnable) thumbnailButton.setImageDrawable(createRoundThumb(cameraBase.getCurrentVideoFilePath(), THUMBNAIL_TYPE_VIDEO))
                        recording = false
                        stopChronometer()
                        Log.i(TAG, "stopRecording exit")
                    } else {
                        Log.d(TAG, "Cannot record a video less than $MIN_REQUIRED_RECORDING_TIME_MILLIS ms")
                    }
                } else {
                    Log.i(TAG, "startRecording enter")
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    cameraBase.startRecording(relativeOrientation.value)
                    recorder_button.setBackgroundResource(android.R.drawable.presence_video_busy)
                    startChronometer()
                    recording = true
                    Log.i(TAG, "startRecording exit")
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

    private fun broadcastFile(path: String?) {
        // Broadcasts the media file to the rest of the system
        MediaScannerConnection.scanFile(
                view?.context, arrayOf(path), null, null)
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        if (recordingMJPEG) {
            cameraBase.takeMJPEG(false)
            capture_button.background = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_shutter)
            stopChronometer()
            recordingMJPEG = false
            Log.i(TAG, "recordingMJPEG stopped")
        }
        if (recording) {
            Log.i(TAG, "stopRecording enter")
            cameraBase.stopRecording()
            broadcastFile(cameraBase.getCurrentVideoFilePath())
            recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
            if (settings.recorderInfo[0].storageEnable) thumbnailButton.setImageDrawable(createRoundThumb(cameraBase.getCurrentVideoFilePath(),THUMBNAIL_TYPE_VIDEO))
            recording = false
            stopChronometer()
            Log.i(TAG, "stopRecording exit")
        }
        try {
            cameraBase.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
        for (overlay in videoOverlayList) {
            overlay.release()
        }
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onIsCameraReadyUpdated(oldIsCameraReady: Boolean, newIsCameraReady: Boolean) {
        Log.i(TAG, "onIsCameraReadyUpdated $oldIsCameraReady to $newIsCameraReady")
        (CameraActivity.mActivity?.get() as CameraActivity).enableTabs()
    }

    companion object {
        const val THUMBNAIL_TYPE_IMAGE = 1
        const val THUMBNAIL_TYPE_VIDEO = 2
        private val TAG = CameraFragmentVideo::class.java.simpleName
        var recording = false
        var recordingMJPEG = false
        const val MAX_CAMERA_STREAMS = 3
    }
}

