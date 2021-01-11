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

package com.example.android.camera2.video

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.util.Log
import com.google.gson.Gson
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties


data class DefogParams(
        val enable: Byte,
        val algo_type: Int,
        val algo_decision_mode: Int,
        val strength: Float,
        val convergence_speed: Int,
        val strength_range: List<Float>,
        val convergence_speed_range: List<Int>,
        val lp_color_comp_gain: Float,
        val lp_color_comp_gain_range: List<Float>,
        val abc_en: Byte,
        val acc_en: Byte,
        val afsd_en: Byte,
        val afsd_2a_en: Byte,
        val defog_dark_thres: Int,
        val defog_dark_thres_range: List<Int>,
        val defog_bright_thres: Int,
        val defog_bright_thres_range: List<Int>,
        val abc_gain: Float,
        val abc_gain_range: List<Float>,
        val acc_max_dark_str: Float,
        val acc_max_dark_str_range: List<Float>,
        val acc_max_bright_str: Float,
        val acc_max_bright_str_range: List<Float>,
        val dark_limit: Int,
        val dark_limit_range: List<Int>,
        val bright_limit: Int,
        val bright_limit_range: List<Int>,
        val dark_preserve: Int,
        val dark_preserve_range: List<Int>,
        val bright_preserve: Int,
        val bright_preserve_range: List<Int>,
        val dnr_trigparam_start_range: List<Float>,
        val dnr_trigparam_end_range: List<Float>,
        val dnr_trigparam_fog_range: List<Int>,
        val lux_trigparam_start_range: List<Float>,
        val lux_trigparam_end_range: List<Float>,
        val lux_trigparam_fog_range: List<Int>,
        val cct_trigparam_start_range: List<Float>,
        val cct_trigparam_end_range: List<Float>,
        val cct_trigparam_fog_range: List<Int>,
        val ce_trigparam_start_range: List<Float>,
        val ce_trigparam_end_range: List<Float>,
        val ce_trigparam_fog_range: List<Int>,
        val drc_trigparam_start_range: List<Float>,
        val drc_trigparam_end_range: List<Float>,
        val drc_trigparam_fog_range: List<Int>,
        val hdr_trigparam_start_range: List<Float>,
        val hdr_trigparam_end_range: List<Float>,
        val hdr_trigparam_fog_range: List<Int>,
        val isSettled: Int,
        val algo_fog_scene_probability: Int,
        val ce_en: Byte,
        val convergence_mode: Int,
        val guc_en: Byte,
        val dcc_en: Byte,
        val guc_str: Float,
        val dcc_dark_str: Float,
        val dcc_bright_str: Float
)

data class ExposureTable(
        val isValid: Byte,
        val sensitivityCorrectionFactor: Float,
        val kneeCount: Int,
        val gainKneeEntries: List<Float>,
        val expTimeKneeEntries: List<Long>,
        val incrementPriorityKneeEntries: List<Int>,
        val expIndexKneeEntries: List<Float>,
        val thresAntiBandingMinExpTimePct: Float
)

data class ANRTable(
        val anr_intensity: Float,
        val anr_motion_sensitivity: Float,
        val anr_tuning_range: List<Float>
)

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

    fun setDefog(builder: CaptureRequest.Builder, defogData: String) {
        GenericSetVendorEntries(builder, defogData, "Defog", "org.quic.camera.defog.", DefogParams::class.java, DefogParams::class.memberProperties).setVendorEntries()
    }

    fun setExposureTable(builder: CaptureRequest.Builder, exposureData: String) {
        GenericSetVendorEntries(builder, exposureData, "Exposure Table", "org.codeaurora.qcamera3.exposuretable.", ExposureTable::class.java, ExposureTable::class.memberProperties).setVendorEntries()
    }

    fun setANRTable(builder: CaptureRequest.Builder, ANRData: String) {
        GenericSetVendorEntries(builder, ANRData, "ANR Table", "org.quic.camera.anr_tuning.", ANRTable::class.java, ANRTable::class.memberProperties).setVendorEntries()
    }

    class GenericSetVendorEntries<T> (private val builder: CaptureRequest.Builder, private val jsonString: String, private val type: String, private val keyText: String, private val f1: Class<T>, private val f2: Collection<KProperty1<T, *>>){
        fun setVendorEntries() {
            Log.d(TAG, "Setting $type Table")
            if(jsonString=="{\"enable\" : 0}") {
                val key = CaptureRequest.Key(keyText + "enable", Byte::class.java)
                if (isSupported(builder, key)) builder.set(key, 0)
            } else if(jsonString=="{\"isValid\" : 0}") {
                val key = CaptureRequest.Key(keyText + "isValid", Byte::class.java)
                if (isSupported(builder, key)) builder.set(key, 0)
            } else {
                val tableParam = Gson().fromJson(jsonString, f1)
                for (prop in f2) {
                    if (prop.call(tableParam) != null) {
                        if (prop.returnType.toString() == "kotlin.Byte") {
                            val key = CaptureRequest.Key(keyText + prop.name, Byte::class.java)
                            if (isSupported(builder, key)) builder.set(key, prop.call(tableParam) as Byte)
                        } else if (prop.returnType.toString() == "kotlin.Float") {
                            val key = CaptureRequest.Key(keyText + prop.name, Float::class.java)
                            if (isSupported(builder, key)) builder.set(key, prop.call(tableParam) as Float)
                        } else if (prop.returnType.toString() == "kotlin.Boolean") {
                            val key = CaptureRequest.Key(keyText + prop.name, Byte::class.java)
                            if (isSupported(builder, key)) builder.set(key, if (prop.call(tableParam) as Boolean) 1 else 0)
                        } else if (prop.returnType.toString() == "kotlin.Double") {
                            val key = CaptureRequest.Key(keyText + prop.name, Float::class.java)
                            if (isSupported(builder, key)) builder.set(key, prop.call(tableParam).toString().toFloat())
                        } else if (prop.returnType.toString() == "kotlin.Int") {
                            val key = CaptureRequest.Key(keyText + prop.name, Int::class.java)
                            if (isSupported(builder, key)) builder.set(key, prop.call(tableParam) as Int)
                        } else if (prop.returnType.toString() == "kotlin.collections.List<kotlin.Float>") {
                            val key = CaptureRequest.Key(keyText + prop.name, FloatArray::class.java)
                            val indexEntries = FloatArray((prop.call(tableParam) as List<*>).size)
                            var index = 0
                            for (item in (prop.call(tableParam) as List<*>)) {
                                indexEntries[index++] = item as Float
                            }
                            if (isSupported(builder, key)) builder.set(key, indexEntries)
                        } else if (prop.returnType.toString() == "kotlin.collections.List<kotlin.Double>") {
                            val key = CaptureRequest.Key(keyText + prop.name, FloatArray::class.java)
                            val indexEntries = FloatArray((prop.call(tableParam) as List<*>).size)
                            var index = 0
                            for (item in prop.call(tableParam) as List<*>) {
                                indexEntries[index++] = item.toString().toFloat()
                            }
                            if (isSupported(builder, key)) builder.set(key, indexEntries)
                        } else if (prop.returnType.toString() == "kotlin.collections.List<kotlin.Int>") {
                            val key = CaptureRequest.Key(keyText + prop.name, IntArray::class.java)
                            val indexEntries = IntArray((prop.call(tableParam) as List<*>).size)
                            var index = 0
                            for (item in (prop.call(tableParam) as List<*>)) {
                                indexEntries[index++] = item as Int
                            }
                            if (isSupported(builder, key)) builder.set(key, indexEntries)
                        } else if (prop.returnType.toString() == "kotlin.collections.List<kotlin.Long>") {
                            val key = CaptureRequest.Key(keyText + prop.name, LongArray::class.java)
                            val indexEntries = LongArray((prop.call(tableParam) as List<*>).size)
                            var index = 0
                            for (item in prop.call(tableParam) as List<*>) {
                                indexEntries[index++] = item as Long
                            }
                            if (isSupported(builder, key)) builder.set(key, indexEntries)
                        } else {
                            Log.e(TAG, "New Type found - need to handle ${prop.returnType}")
                        }
                    }
                }
            }
        }
    }
}
