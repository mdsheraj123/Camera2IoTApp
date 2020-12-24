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

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.util.Log

object VendorTagUtil {
    private const val TAG = "VendorTagUtil"

    private val TNREnableKey = CaptureRequest.Key("org.codeaurora.qcamera3.temporal_denoise.enable",
            Byte::class.java)
    private val EISEnableKey = CaptureRequest.Key("org.codeaurora.qcamera3.EISLDC.EISenable",
            Byte::class.java)
    private val LDCEnableKey = CaptureRequest.Key("org.codeaurora.qcamera3.EISLDC.LDCenable",
            Byte::class.java)
    private val SHDREnableKey = CaptureRequest.Key("org.codeaurora.qcamera3.shdr.enable",
            Byte::class.java)
    private val CdsModeKey = CaptureRequest.Key("org.codeaurora.qcamera3.CDS.cds_mode",
            Int::class.java)
    private val JpegCropEnableKey = CaptureRequest.Key("org.codeaurora.qcamera3.jpeg_encode_crop.enable",
            Byte::class.java)
    private val JpegCropRectKey = CaptureRequest.Key("org.codeaurora.qcamera3.jpeg_encode_crop.rect",
            IntArray::class.java)
    private val JpegRoiRectKey = CaptureRequest.Key("org.codeaurora.qcamera3.jpeg_encode_crop.roi",
            IntArray::class.java)
    private val SELECT_PRIORITY = CaptureRequest.Key("org.codeaurora.qcamera3.iso_exp_priority.select_priority",
            Int::class.java)
    private val ISO_EXP = CaptureRequest.Key("org.codeaurora.qcamera3.iso_exp_priority.use_iso_exp_priority",
            Long::class.java)
    private val USE_ISO_VALUE = CaptureRequest.Key("org.codeaurora.qcamera3.iso_exp_priority.use_iso_value",
            Int::class.java)
    private val WB_COLOR_TEMPERATURE = CaptureRequest.Key("org.codeaurora.qcamera3.manualWB.color_temperature",
            Int::class.java)
    private val IRLEDKey = CaptureRequest.Key("org.codeaurora.qcamera3.ir_led.mode",
            Int::class.java)
    private  val ADRCKey = CaptureRequest.Key("org.codeaurora.qcamera3.adrc.disable",
            Byte::class.java)
    private val EXPOSER_METERING_KEY = CaptureRequest.Key("org.codeaurora.qcamera3.exposure_metering.exposure_metering_mode",
            Int::class.java)
    private val MANUAL_WB_GAINS = CaptureRequest.Key("org.codeaurora.qcamera3.manualWB.gains", FloatArray::class.java)
    private val PARTIAL_MANUAL_WB_MODE = CaptureRequest.Key("org.codeaurora.qcamera3.manualWB.partial_mwb_mode", Int::class.java)
    private val HDRVideoMode = CaptureRequest.Key("org.quic.camera2.streamconfigs.HDRVideoMode", Byte::class.java)
    private const val MANUAL_WB_DISABLE_MODE = 0
    private const val MANUAL_WB_CCT_MODE = 1
    private const val MANUAL_WB_GAINS_MODE = 2

    private fun isSupported(builder: CaptureRequest.Builder,
                            key: CaptureRequest.Key<*>): Boolean {
        var supported = true
        try {
            builder[key]
        } catch (exception: IllegalArgumentException) {
            supported = false
            Log.d(TAG, "vendor tag " + key.name + " is not supported")
            exception.printStackTrace()
        }
        if (supported) {
            Log.d(TAG, "vendor tag " + key.name + " is supported")
        }
        return supported
    }

    // value=0:OFF
    // value=1:ON
    // value=2:AUTO
    fun setCdsMode(builder: CaptureRequest.Builder, value: Int) {
        if (isCdsModeSupported(builder)) {
            builder.set(CdsModeKey, value)
        }
    }

    private fun isCdsModeSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, CdsModeKey)
    }

    fun setJpegCropEnable(builder: CaptureRequest.Builder, value: Byte) {
        if (isJpegCropEnableSupported(builder)) {
            builder.set(JpegCropEnableKey, value)
        }
    }

    private fun isJpegCropEnableSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, JpegCropEnableKey)
    }

    fun setJpegCropRect(builder: CaptureRequest.Builder, value: IntArray) {
        if (isJpegCropRectSupported(builder)) {
            builder.set(JpegCropRectKey, value)
        }
    }

    private fun isJpegCropRectSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, JpegCropRectKey)
    }

    fun setJpegRoiRect(builder: CaptureRequest.Builder, value: IntArray) {
        if (isJpegRoiRectSupported(builder)) {
            builder.set(JpegRoiRectKey, value)
        }
    }

    private fun isJpegRoiRectSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, JpegRoiRectKey)
    }

    fun setIsoExpPrioritySelectPriority(builder: CaptureRequest.Builder,
                                        value: Int) {
        if (isIsoExpPrioritySelectPrioritySupported(builder)) {
            builder.set(SELECT_PRIORITY, value)
        }
    }

    private fun isIsoExpPrioritySelectPrioritySupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, SELECT_PRIORITY)
    }

    fun setIsoExpPriority(builder: CaptureRequest.Builder, value: Long) {
        if (isIsoExpPrioritySupported(builder)) {
            builder.set(ISO_EXP, value)
        }
    }

    fun setUseIsoValues(builder: CaptureRequest.Builder, value: Int) {
        if (isUseIsoValueSupported(builder)) {
            builder.set(USE_ISO_VALUE, value)
        }
    }

    fun setIRLED(builder: CaptureRequest.Builder, value: Int) {
        if (isIRLEDSupported(builder)) {
            builder.set(IRLEDKey, value)
        }
    }

    fun setADRC(builder: CaptureRequest.Builder, value: Byte) {
        if (isADRCSupported(builder)) {
            builder.set(ADRCKey, value)
        }
    }

    fun setExposureMetering(builder: CaptureRequest.Builder, value: Int) {
        if (isExposureMeteringSupported(builder)) {
            builder.set(EXPOSER_METERING_KEY, value)
        }
    }

    private fun isExposureMeteringSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, EXPOSER_METERING_KEY)
    }

    private fun isADRCSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, ADRCKey)
    }

    private fun isIRLEDSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, IRLEDKey)
    }

    private fun isIsoExpPrioritySupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, ISO_EXP)
    }

    private fun isUseIsoValueSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, USE_ISO_VALUE)
    }

    private fun isPartialWBModeSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, PARTIAL_MANUAL_WB_MODE)
    }

    private fun isWBTemperatureSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, WB_COLOR_TEMPERATURE)
    }

    private fun isMWBGainsSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, MANUAL_WB_GAINS)
    }

    fun setWbColorTemperatureValue(builder: CaptureRequest.Builder, value: Int) {
        if (isPartialWBModeSupported(builder)) {
            builder.set(PARTIAL_MANUAL_WB_MODE, MANUAL_WB_CCT_MODE)
            if (isWBTemperatureSupported(builder)) {
                builder.set(WB_COLOR_TEMPERATURE, value)
            }
        }
    }

    fun setMWBGainsValue(builder: CaptureRequest.Builder, gains: FloatArray) {
        if (isPartialWBModeSupported(builder)) {
            builder.set(PARTIAL_MANUAL_WB_MODE, MANUAL_WB_GAINS_MODE)
            if (isMWBGainsSupported(builder)) {
                builder.set(MANUAL_WB_GAINS, gains)
            }
        }
    }

    fun setMWBDisableMode(builder: CaptureRequest.Builder) {
        if (isPartialWBModeSupported(builder)) {
            builder.set(PARTIAL_MANUAL_WB_MODE, MANUAL_WB_DISABLE_MODE)
        }
    }

    fun setHDRVideoMode(builder: CaptureRequest.Builder, mode: Byte) {
        if (isHDRVideoModeSupported(builder)) {
            builder.set(HDRVideoMode, mode)
        }
    }

    private fun isHDRVideoModeSupported(builder: CaptureRequest.Builder): Boolean {
        return isSupported(builder, HDRVideoMode)
    }

    fun isHDRVideoModeSupported(camera: CameraDevice): Boolean {
        return try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            isHDRVideoModeSupported(builder)
        } catch (exception: CameraAccessException) {
            exception.printStackTrace()
            false
        }
    }

    private fun isSHDREnable(builder: CaptureRequest.Builder) : Boolean {
        return isSupported(builder, SHDREnableKey)
    }

    fun setSHDREnable(builder: CaptureRequest.Builder, value: Byte) {
        if (isSHDREnable(builder)) {
            builder.set(SHDREnableKey, value)
        }
    }

    private fun isLDCEnable(builder: CaptureRequest.Builder) : Boolean {
        return isSupported(builder, LDCEnableKey)
    }

    fun setLDCEnable(builder: CaptureRequest.Builder, value: Byte) {
        if (isLDCEnable(builder)) {
            builder.set(LDCEnableKey, value)
        }
    }

    private fun isTNREnable(builder: CaptureRequest.Builder) : Boolean {
        return isSupported(builder, TNREnableKey)
    }

    fun setTNREnable(builder: CaptureRequest.Builder, value: Byte) {
        if (isTNREnable(builder)) {
            builder.set(TNREnableKey, value)
        }
    }

    private fun isEISEnable(builder: CaptureRequest.Builder) : Boolean {
        return isSupported(builder, EISEnableKey)
    }

    fun setEISEnable(builder: CaptureRequest.Builder, value: Byte) {
        if (isEISEnable(builder)) {
            builder.set(EISEnableKey, value)
        }
    }

}
