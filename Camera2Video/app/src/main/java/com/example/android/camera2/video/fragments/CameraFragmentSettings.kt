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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import androidx.preference.*
import com.example.android.camera2.video.CameraSettingsUtil.getCameraSettings
import com.example.android.camera2.video.R


class CameraFragmentSettings : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.camera_preferences, rootKey)
        val cameraManager =
                requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraList = enumerateVideoCameras(cameraManager)

        val detectedCameras = mutableListOf<String>()
        val detectedCameraIds = mutableListOf<String>()

        for (camera in cameraList) {
            detectedCameras.add("${camera.orientation} (${camera.cameraId})")
            detectedCameraIds.add(camera.cameraId)
        }

        val screen: PreferenceScreen = this.preferenceScreen
        val cameraPreference = screen.findPreference<ListPreference>("camera_id");
        cameraPreference?.entries = detectedCameras.toTypedArray()
        cameraPreference?.entryValues = detectedCameraIds.toTypedArray()

        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.versionName
            val versionPref = screen.findPreference<EditTextPreference>("version_info");
            if (versionPref != null) {
                versionPref.summary = version.toString()
                versionPref.isEnabled = false
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        updateCameraPreferences()
        updateEncodePreference()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navHeight = resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height", "dimen", "android"))
        if (navHeight > 0) {
            listView.setPadding(0, 0, 0, navHeight)
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "camera_id" -> updateCameraPreferences()
            "display_enable" -> {
                // If Display is on, then disable stream 2 encoding.
                val screen: PreferenceScreen = this.preferenceScreen
                val displayPreference = screen.findPreference<SwitchPreference>("display_enable")
                val stream2Preference = screen.findPreference<SwitchPreference>("vid_2_enable")
                if (displayPreference != null) {
                    if (displayPreference.isChecked) {
                        if (stream2Preference != null) {
                            stream2Preference.isChecked = false
                        }
                    } else {
                        if (stream2Preference != null) {
                            stream2Preference.isChecked = true
                        }
                    }
                }
            }
            "vid_2_enable" -> {
                // If Stream 2  is on, then disable display.
                val screen: PreferenceScreen = this.preferenceScreen
                val displayPreference = screen.findPreference<SwitchPreference>("display_enable")
                val stream2Preference = screen.findPreference<SwitchPreference>("vid_2_enable")
                if (stream2Preference != null) {
                    if (stream2Preference.isChecked) {
                        if (displayPreference != null) {
                            displayPreference.isChecked = false
                        }
                    } else {
                        if (displayPreference != null) {
                            displayPreference.isChecked = true
                        }
                    }
                }
            }
            "dual_camera" -> {
                val screen: PreferenceScreen = this.preferenceScreen
                val dualCam = screen.findPreference<SwitchPreference>("dual_camera")
                val threeCam = screen.findPreference<SwitchPreference>("three_camera")

                if (threeCam != null && dualCam != null) {
                    threeCam.isChecked = when (dualCam.isChecked) {
                        true -> false
                        false -> true
                    }
                }
            }
            "three_camera" -> {
                val screen: PreferenceScreen = this.preferenceScreen
                val dualCam = screen.findPreference<SwitchPreference>("dual_camera")
                val threeCam = screen.findPreference<SwitchPreference>("three_camera")

                if (threeCam != null && dualCam != null) {
                    dualCam.isChecked = when (threeCam.isChecked) {
                        true -> false
                        false -> true
                    }
                }
            }
            else -> updateEncodePreference()
        }
    }

    private fun updateCameraPreferences() {
        val settings = getCameraSettings(requireContext().applicationContext)

        val cameraManager =
                requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraList = enumerateVideoCameras(cameraManager)

        val selectedCamera = cameraList.find { it.cameraId == settings.cameraId }
        val pictureSizes = mutableListOf<String>()
        val videoSizes = mutableListOf<String>()
        val previewSizes = mutableListOf<String>()
        if (selectedCamera != null) {
            for (size in selectedCamera.pictureSizes) {
                pictureSizes.add("${size.width}x${size.height}")
            }
            for (size in selectedCamera.videoSizes) {
                videoSizes.add("${size.width}x${size.height}")
            }
        }

        val screen: PreferenceScreen = this.preferenceScreen
        val snapshotResPreference = screen.findPreference<ListPreference>("snapshot_size");
        snapshotResPreference?.entries = pictureSizes.toTypedArray()
        snapshotResPreference?.entryValues = pictureSizes.toTypedArray()

        val videoResPreference0 = screen.findPreference<ListPreference>("vid_0_size");
        videoResPreference0?.entries = videoSizes.toTypedArray()
        videoResPreference0?.entryValues = videoSizes.toTypedArray()

        val videoResPreference1 = screen.findPreference<ListPreference>("vid_1_size");
        videoResPreference1?.entries = videoSizes.toTypedArray()
        videoResPreference1?.entryValues = videoSizes.toTypedArray()

        val videoResPreference2 = screen.findPreference<ListPreference>("vid_2_size");
        videoResPreference2?.entries = videoSizes.toTypedArray()
        videoResPreference2?.entryValues = videoSizes.toTypedArray()

    }

    private fun updateEncodePreference() {
        val encoderKeys = listOf(
                "vid_0_i_min_qp_range",
                "vid_0_i_max_qp_range",
                "vid_0_b_min_qp_range",
                "vid_0_b_max_qp_range",
                "vid_0_p_min_qp_range",
                "vid_0_p_max_qp_range",
                "vid_0_i_init_qp",
                "vid_0_b_init_qp",
                "vid_0_p_init_qp",
                "vid_1_i_min_qp_range",
                "vid_1_i_max_qp_range",
                "vid_1_b_min_qp_range",
                "vid_1_b_max_qp_range",
                "vid_1_p_min_qp_range",
                "vid_1_p_max_qp_range",
                "vid_1_i_init_qp",
                "vid_1_b_init_qp",
                "vid_1_p_init_qp",
                "vid_2_i_min_qp_range",
                "vid_2_i_max_qp_range",
                "vid_2_b_min_qp_range",
                "vid_2_b_max_qp_range",
                "vid_2_p_min_qp_range",
                "vid_2_p_max_qp_range",
                "vid_2_i_init_qp",
                "vid_2_b_init_qp",
                "vid_2_p_init_qp",
        )
        val screen: PreferenceScreen = this.preferenceScreen
        for (key in encoderKeys) {
            val item = screen.findPreference<EditTextPreference>(key);
            if (item !=null) {
                item.summary = item.text
            }
        }
    }

    companion object {
        private data class CameraInfo(
                val orientation: String,
                val cameraId: String,
                val videoSizes: Array<Size>,
                val pictureSizes: Array<Size>,
        )

        /** Converts a lens orientation enum into a human-readable string */
        private fun lensOrientationString(value: Int) = when (value) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        /** Lists all video-capable cameras and supported resolution and FPS combinations */
        @SuppressLint("InlinedApi")
        private fun enumerateVideoCameras(cameraManager: CameraManager): List<CameraInfo> {
            val availableCameras: MutableList<CameraInfo> = mutableListOf()

            cameraManager.cameraIdList.forEach { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val cameraConfig = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                val orientation = lensOrientationString(
                        characteristics.get(CameraCharacteristics.LENS_FACING)!!)
                val videoSizes = cameraConfig.getOutputSizes(MediaRecorder::class.java)
                val pictureSizes = cameraConfig.getOutputSizes(ImageFormat.JPEG)

                availableCameras.add(CameraInfo(orientation, id, videoSizes, pictureSizes))
            }
            return availableCameras
        }
    }
}
