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
        fun onNRMode(value: Int)
        fun onAntiBandingMode(value: Int)
        fun onAEMode(value: Int)
        fun onAWBMode(value: Int)
        fun onAFMode(value: Int)
        fun onIRMode(value: Int)
        fun onADRCMode(value: Byte)
        fun onExpMeteringMode(value: Int)
        fun onISOMode(value: Long)
        fun onSetZoom(value: Int)
        fun onDefog(value: Boolean): Boolean
        fun onExposureTable(value: Boolean): Boolean
        fun onANRTable(value: Boolean): Boolean
        fun onLTMTable(value: Boolean): Boolean
        fun onSaturationLevel(value: Int)
        fun onSharpnessLevel(value: Int)
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
                R.id.nr_mode_off -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRMode(CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                    true
                }
                R.id.nr_mode_fast -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRMode(CameraMetadata.NOISE_REDUCTION_MODE_FAST)
                    true
                }
                R.id.nr_mode_hq -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRMode(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    true
                }
                R.id.nr_mode_minimal -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRMode(CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)
                    true
                }
                R.id.nr_mode_zsl -> {
                    item.isChecked = true
                    cameraMenuListener?.onNRMode(CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)
                    true
                }
                R.id.ANTIBANDING_MODE_OFF -> {
                    item.isChecked = true
                    cameraMenuListener?.onAntiBandingMode(CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF)
                    true
                }
                R.id.ANTIBANDING_MODE_50HZ -> {
                    item.isChecked = true
                    cameraMenuListener?.onAntiBandingMode(CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)
                    true
                }
                R.id.ANTIBANDING_MODE_60HZ -> {
                    item.isChecked = true
                    cameraMenuListener?.onAntiBandingMode(CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ)
                    true
                }
                R.id.ANTIBANDING_MODE_AUTO -> {
                    item.isChecked = true
                    cameraMenuListener?.onAntiBandingMode(CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                    true
                }
                R.id.AE_OFF -> {
                    item.isChecked = true
                    cameraMenuListener?.onAEMode(CameraMetadata.CONTROL_AE_MODE_OFF)
                    true
                }
                R.id.AE_ON -> {
                    item.isChecked = true
                    cameraMenuListener?.onAEMode(CameraMetadata.CONTROL_AE_MODE_ON)
                    true
                }
                R.id.AWB_MODE_AUTO -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_AUTO)
                    true
                }
                R.id.AWB_MODE_CLOUDY_DAYLIGHT -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                    true
                }
                R.id.AWB_MODE_DAYLIGHT -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
                    true
                }
                R.id.AWB_MODE_FLUORESCENT -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
                    true
                }
                R.id.AWB_MODE_SHADE -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_SHADE)
                    true
                }
                R.id.AWB_MODE_TWILIGHT -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_TWILIGHT)
                    true
                }
                R.id.AWB_MODE_WARM_FLUORESCENT -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT)
                    true
                }
                R.id.AWB_MODE_INCANDESCENT -> {
                    item.isChecked = true
                    cameraMenuListener?.onAWBMode(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
                    true
                }
                R.id.AF_MODE_OFF -> {
                    item.isChecked = true
                    cameraMenuListener?.onAFMode(CameraMetadata.CONTROL_AF_MODE_OFF)
                    true
                }
                R.id.AF_MODE_MACRO -> {
                    item.isChecked = true
                    cameraMenuListener?.onAFMode(CameraMetadata.CONTROL_AF_MODE_MACRO)
                    true
                }
                R.id.AF_MODE_CONTINUOUS_VIDEO -> {
                    item.isChecked = true
                    cameraMenuListener?.onAFMode(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    true
                }
                R.id.AF_MODE_CONTINUOUS_PICTURE -> {
                    item.isChecked = true
                    cameraMenuListener?.onAFMode(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    true
                }
                R.id.AF_MODE_EDOF -> {
                    item.isChecked = true
                    cameraMenuListener?.onAFMode(CameraMetadata.CONTROL_AF_MODE_EDOF)
                    true
                }
                R.id.ir_mode_off -> {
                    item.isChecked = true
                    cameraMenuListener?.onIRMode(0)
                    true
                }
                R.id.ir_mode_on -> {
                    item.isChecked = true
                    cameraMenuListener?.onIRMode(1)
                    true
                }
                R.id.adrc_mode -> {
                    item.isChecked = !item.isChecked
                    cameraMenuListener?.onADRCMode(if (item.isChecked) 1 else 0);
                    true
                }
                R.id.ae_exp_mode_avg -> {
                    item.isChecked = true
                    cameraMenuListener?.onExpMeteringMode(0)
                    true
                }
                R.id.ae_exp_mode_center_weight -> {
                    item.isChecked = true
                    cameraMenuListener?.onExpMeteringMode(1)
                    true
                }
                R.id.ae_exp_mode_spot -> {
                    item.isChecked = true
                    cameraMenuListener?.onExpMeteringMode(2)
                    true
                }
                R.id.ae_exp_mode_custom -> {
                    item.isChecked = true
                    cameraMenuListener?.onExpMeteringMode(3)
                    true
                }
                R.id.iso_mode_auto -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(0)
                    true
                }
                R.id.iso_mode_deblur -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(1)
                    true
                }
                R.id.iso_mode_100 -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(2)
                    true
                }
                R.id.iso_mode_200 -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(3)
                    true
                }
                R.id.iso_mode_400 -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(4)
                    true
                }
                R.id.iso_mode_800 -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(5)
                    true
                }
                R.id.iso_mode_1600 -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(6)
                    true
                }
                R.id.iso_mode_3200 -> {
                    item.isChecked = true
                    cameraMenuListener?.onISOMode(7)
                    true
                }
                R.id.zoom_off -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(0)
                    true
                }
                R.id.zoom_1x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(1)
                    true
                }
                R.id.zoom_2x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(2)
                    true
                }
                R.id.zoom_3x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(3)
                    true
                }
                R.id.zoom_4x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(4)
                    true
                }
                R.id.zoom_5x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(5)
                    true
                }
                R.id.zoom_6x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(6)
                    true
                }
                R.id.zoom_7x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(7)
                    true
                }
                R.id.zoom_8x -> {
                    item.isChecked = true
                    cameraMenuListener?.onSetZoom(8)
                    true
                }
                R.id.defog -> {
                    val status = cameraMenuListener?.onDefog(!item.isChecked)
                    if (status!!) item.isChecked = !item.isChecked
                    true
                }
                R.id.exp_table -> {
                    val status = cameraMenuListener?.onExposureTable(!item.isChecked)
                    if (status!!) item.isChecked = !item.isChecked
                    true
                }
                R.id.ANR_table -> {
                    val status = cameraMenuListener?.onANRTable(!item.isChecked)
                    if (status!!) item.isChecked = !item.isChecked
                    true
                }
                R.id.LTM_table -> {
                    val status = cameraMenuListener?.onLTMTable(!item.isChecked)
                    if (status!!) item.isChecked = !item.isChecked
                    true
                }
                R.id.Sat_Level_0 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(0)
                    true
                }
                R.id.Sat_Level_1 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(1)
                    true
                }
                R.id.Sat_Level_2 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(2)
                    true
                }
                R.id.Sat_Level_3 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(3)
                    true
                }
                R.id.Sat_Level_4 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(4)
                    true
                }
                R.id.Sat_Level_5 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(5)
                    true
                }
                R.id.Sat_Level_6 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(6)
                    true
                }
                R.id.Sat_Level_7 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(7)
                    true
                }
                R.id.Sat_Level_8 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(8)
                    true
                }
                R.id.Sat_Level_9 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(9)
                    true
                }
                R.id.Sat_Level_10 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSaturationLevel(10)
                    true
                }
                R.id.Sharpness_Level_0 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(0)
                    true
                }
                R.id.Sharpness_Level_1 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(1)
                    true
                }
                R.id.Sharpness_Level_2 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(2)
                    true
                }
                R.id.Sharpness_Level_3 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(3)
                    true
                }
                R.id.Sharpness_Level_4 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(4)
                    true
                }
                R.id.Sharpness_Level_5 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(5)
                    true
                }
                R.id.Sharpness_Level_6 -> {
                    item.isChecked = true
                    cameraMenuListener?.onSharpnessLevel(6)
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
