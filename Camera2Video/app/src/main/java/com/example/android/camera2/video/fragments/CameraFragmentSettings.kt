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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
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
        val cameraPreference = screen.findPreference<DropDownPreference>("camera_id");
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
        val snapshotResPreference = screen.findPreference<DropDownPreference>("snapshot_size");
        snapshotResPreference?.entries = pictureSizes.toTypedArray()
        snapshotResPreference?.entryValues = pictureSizes.toTypedArray()

        val videoResPreference0 = screen.findPreference<DropDownPreference>("vid_0_size");
        videoResPreference0?.entries = videoSizes.toTypedArray()
        videoResPreference0?.entryValues = videoSizes.toTypedArray()

        val videoResPreference1 = screen.findPreference<DropDownPreference>("vid_1_size");
        videoResPreference1?.entries = videoSizes.toTypedArray()
        videoResPreference1?.entryValues = videoSizes.toTypedArray()

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
