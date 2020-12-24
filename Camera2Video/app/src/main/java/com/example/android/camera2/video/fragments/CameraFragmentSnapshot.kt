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

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaActionSound
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.*
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

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_snapshot, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                val previewSize = getPreviewOutputSize(
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
            override fun onNRmode(value: Int) {
                cameraBase.setNRmode(value)
                Log.d(TAG, "NR mode: $value")
            }
        })
        view.setOnClickListener() {
            cameraMenu.show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        cameraBase.openCamera(settings.cameraId)
        cameraBase.setFramerate(settings.previewInfo.fps)
        cameraBase.addPreviewStream(viewFinder.holder.surface)
        cameraBase.addSnapshotStream(settings.snapshotInfo)
        cameraBase.startCamera()

        val sound = MediaActionSound()
        capture_button.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                cameraBase.takeSnapshot().use { result ->
                    Log.d(TAG, "Result received: $result")
                    cameraBase.saveResult(result)
                }
                it.post { it.isEnabled = true }
            }
            sound.play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            cameraBase.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private val TAG = CameraFragmentSnapshot::class.java.simpleName
    }
}
