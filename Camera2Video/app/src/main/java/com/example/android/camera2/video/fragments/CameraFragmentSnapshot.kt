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
import android.media.ExifInterface
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
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
import com.example.android.camera2.video.CameraActivity.Companion.printAppVersion
import com.example.android.camera2.video.overlay.VideoOverlay
import kotlinx.android.synthetic.main.fragment_camera_snapshot.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraFragmentSnapshot : Fragment() {
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private lateinit var cameraBase: CameraBase

    private lateinit var characteristics: CameraCharacteristics

    private lateinit var viewFinder: AutoFitSurfaceView

    private lateinit var overlay: View

    private lateinit var settings: CameraSettings

    private lateinit var previewSize: Size

    private val videoOverlayList = mutableListOf<VideoOverlay>()

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_snapshot, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        printAppVersion(requireContext().applicationContext)
        cameraBase = CameraBase(requireContext().applicationContext)
        settings = CameraSettingsUtil.getCameraSettings(requireContext().applicationContext)
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
                Log.d(TAG, "Zoom Value: $value")
            }
            override fun onDefog(value: Boolean) {
                cameraBase.setDefog(value)
                Log.d(TAG, "Defog value: $value")
            }

            override fun onExposureTable(value: Boolean) {
                cameraBase.setExposureTable(value)
                Log.d(TAG, "ExposureTable : $value")
            }

            override fun onANRTable(value: Boolean) {
                cameraBase.setANRTable(value)
                Log.d(TAG, "ANRTable : $value")
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
                capture_button.rotation = requiredOrientation
                thumbnailButton2.rotation = requiredOrientation
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        cameraBase.openCamera(settings.cameraId)

        cameraBase.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase.setSHDREnable(settings.cameraParams.shdr_enable)

        cameraBase.setFramerate(settings.previewInfo.fps)

        if (settings.previewInfo.overlayEnable) {
            val previewOverlay = VideoOverlay(viewFinder.holder.surface, previewSize.width, previewSize.height, settings.previewInfo.fps.toFloat(),0.0f)
            previewOverlay.setTextOverlay("Preview overlay", 0.0f, 100.0f, 100.0f, Color.WHITE, 0.5f)
            videoOverlayList.add(previewOverlay)
            cameraBase.addPreviewStream(previewOverlay.getInputSurface())
        } else {
            cameraBase.addPreviewStream(viewFinder.holder.surface)
        }

        cameraBase.addSnapshotStream(settings.snapshotInfo)
        cameraBase.startCamera()

        val sound = MediaActionSound()
        capture_button.setOnClickListener {
            it.isEnabled = false
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
                        broadcastFile()
                        thumbnailButton2.setImageDrawable(createRoundThumb())
                    }
                    it.isEnabled = true
                }
            }
            sound.play(MediaActionSound.SHUTTER_CLICK)
        }

        if(cameraBase.currentSnapshotFilePath !=null) {
            thumbnailButton2.setImageDrawable(createRoundThumb())
        }
        thumbnailButton2.setOnClickListener {
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
                view?.context, arrayOf(cameraBase.currentSnapshotFilePath), null, null)
    }

    private fun createImageThumb() = cameraBase.currentSnapshotFilePath?.let { ThumbnailUtils.createImageThumbnail(it, MediaStore.Images.Thumbnails.MICRO_KIND) }

    private fun createRoundThumb() : RoundedBitmapDrawable {
        val drawable = RoundedBitmapDrawableFactory.create(resources, createImageThumb())
        drawable.isCircular = true
        return drawable
    }

    override fun onPause() {
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
        super.onDestroy()
    }

    companion object {
        private val TAG = CameraFragmentSnapshot::class.java.simpleName
    }
}
