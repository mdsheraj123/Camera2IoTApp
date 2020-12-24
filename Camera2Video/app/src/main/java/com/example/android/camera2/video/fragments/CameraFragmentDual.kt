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

package com.example.android.camera2.video.fragments

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.annotation.SuppressLint
import android.hardware.camera2.*
import android.media.MediaActionSound
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.getDisplaySmartSize
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.*
import kotlinx.android.synthetic.main.fragment_camera_dual.capture_button
import kotlinx.android.synthetic.main.fragment_camera_dual.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraFragmentDual : Fragment() {
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private lateinit var cameraBase0: CameraBase
    private lateinit var cameraBase1: CameraBase

    private lateinit var characteristics0: CameraCharacteristics
    private lateinit var characteristics1: CameraCharacteristics

    private lateinit var viewFinder: AutoFitSurfaceView
    private lateinit var viewFinder1: AutoFitSurfaceView

    private lateinit var overlay: View
    private lateinit var settings: CameraSettings

    private val camera0Id = "0"
    private val camera1Id = "1"

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_dual, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraBase0 = CameraBase(requireContext().applicationContext)
        cameraBase1 = CameraBase(requireContext().applicationContext)
        settings = CameraSettingsUtil.getCameraSettings(requireContext().applicationContext)
        characteristics0 = cameraManager.getCameraCharacteristics(camera0Id)
        characteristics1 = cameraManager.getCameraCharacteristics(camera1Id)

        overlay = view.findViewById(R.id.overlay)
        viewFinder = view.findViewById(R.id.view_finder)
        viewFinder1 = view.findViewById(R.id.view_finder1)

        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val screenSize = getDisplaySmartSize(viewFinder.display)
                val previewSize = getPreviewOutputSize(
                        viewFinder.display, characteristics0, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                viewFinder.setAspectRatio(previewSize.width, previewSize.height)
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
                val previewSize = getPreviewOutputSize(
                       viewFinder1.display, characteristics1, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${viewFinder1.width} x ${viewFinder1.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                viewFinder1.setAspectRatio(previewSize.width, previewSize.height)
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
        })
        view.setOnClickListener() {
            cameraMenu.show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        cameraBase0.openCamera(camera0Id)

        cameraBase0.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase0.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase0.setSHDREnable(settings.cameraParams.shdr_enable)

        cameraBase0.setFramerate(settings.previewInfo.fps)
        cameraBase0.addPreviewStream(viewFinder.holder.surface)
        cameraBase0.addSnapshotStream(settings.snapshotInfo)

        // With Dual cam add only one encoding stream if enabled
        if (settings.recorderInfo.isNotEmpty()) {
            cameraBase0.addRecorderStream(settings.recorderInfo[0])
        }

        cameraBase1.openCamera(camera1Id)

        cameraBase1.setEISEnable(settings.cameraParams.eis_enable)
        cameraBase1.setLDCEnable(settings.cameraParams.ldc_enable)
        cameraBase1.setSHDREnable(settings.cameraParams.shdr_enable)

        cameraBase1.setFramerate(settings.previewInfo.fps)
        cameraBase1.addPreviewStream(viewFinder1.holder.surface)
        cameraBase1.addSnapshotStream(settings.snapshotInfo)

        // With Dual cam add only one encoding stream if enabled
        if (settings.recorderInfo.isNotEmpty()) {
            cameraBase1.addRecorderStream(settings.recorderInfo[0])
        }

        cameraBase0.startCamera()
        cameraBase1.startCamera()

        val sound = MediaActionSound()

        var recording = false
        recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
        recorder_button.setOnClickListener {
            if (recording) {
                cameraBase1.stopRecording()
                cameraBase0.stopRecording()
                sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                recorder_button.setBackgroundResource(android.R.drawable.presence_video_online)
                recording = false
            } else {
                sound.play(MediaActionSound.START_VIDEO_RECORDING)
                cameraBase0.startRecording()
                cameraBase1.startRecording()
                recorder_button.setBackgroundResource(android.R.drawable.presence_video_busy)
                recording = true
            }
        }

        capture_button.setOnClickListener {
            it.isEnabled = false
            var snapshot0Flag = false
            var snapshot1Flag = false
            lifecycleScope.launch(Dispatchers.IO) {
                cameraBase0.takeSnapshot().use { result ->
                    Log.d(TAG, "Result received: $result")
                    cameraBase0.saveResult(result)
                }
                snapshot0Flag = true
                if (snapshot0Flag and snapshot1Flag) {
                    it.post { it.isEnabled = true }
                }
            }
            lifecycleScope.launch(Dispatchers.IO) {
                cameraBase1.takeSnapshot().use { result ->
                    Log.d(TAG, "Result received: $result")
                    cameraBase1.saveResult(result)
                }
                snapshot1Flag = true
                if (snapshot0Flag and snapshot1Flag) {
                    it.post { it.isEnabled = true }
                }
            }
            sound.play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            cameraBase0.close()
            cameraBase1.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    companion object {
        private val TAG = CameraFragmentDual::class.java.simpleName
    }
}
