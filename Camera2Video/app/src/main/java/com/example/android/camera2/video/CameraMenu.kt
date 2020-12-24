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

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView

class CameraMenu(context: Context?, view: View) {
    private val popupAnchor = view.findViewById<TextView>(R.id.popup_anchor)
    private val popup = PopupMenu(context, popupAnchor)
    private var cameraMenuListener: OnCameraMenuListener? = null

    interface OnCameraMenuListener {
        fun onAELock(value: Boolean)
        fun onAWBLock(value: Boolean)
        fun onEffectMode(value: Int)
        fun onNRmode(value: Int)
    }

    fun setOnCameraMenuListener(listener: OnCameraMenuListener) {
        cameraMenuListener = listener
    }

    init {
        popup.inflate(R.menu.camera_menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ae_lock -> {
                    item.isChecked = !item.isChecked
                    cameraMenuListener?.onAELock(item.isChecked);
                    true
                }
                R.id.awb_lock -> {
                    item.isChecked = !item.isChecked
                    cameraMenuListener?.onAWBLock(item.isChecked);
                    true
                }
                R.id.EFFECT_MODE_OFF -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_OFF)
                    true
                }
                R.id.EFFECT_MODE_MONO -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_MONO)
                    true
                }
                R.id.EFFECT_MODE_NEGATIVE -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE)
                    true
                }
                R.id.EFFECT_MODE_SOLARIZE -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE)
                    true
                }
                R.id.EFFECT_MODE_SEPIA -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
                    true
                }
                R.id.EFFECT_MODE_POSTERIZE -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE)
                    true
                }
                R.id.EFFECT_MODE_WHITEBOARD -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD)
                    true
                }
                R.id.EFFECT_MODE_BLACKBOARD -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD)
                    true
                }
                R.id.EFFECT_MODE_AQUA -> {
                    item.isChecked = true
                    cameraMenuListener?.onEffectMode(CameraMetadata.CONTROL_EFFECT_MODE_AQUA)
                    true
                }
                R.id.nr_mode_off -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRmode(CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                    true
                }
                R.id.nr_mode_fast -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRmode(CameraMetadata.NOISE_REDUCTION_MODE_FAST)
                    true
                }
                R.id.nr_mode_hq -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRmode(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    true
                }
                R.id.nr_mode_minimal -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRmode(CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)
                    true
                }
                R.id.nr_mode_zsl -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRmode(CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)
                    true
                }
                else -> false
            }
        }
    }

    fun show() {
        popup.show()
    }
}