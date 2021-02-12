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

import android.view.Surface
import com.example.android.camera2.video.CameraBase.Companion.CombinedCaptureResult

data class StreamInfo(
        val width: Int = 0,
        val height: Int = 0,
        val fps: Int = 0,
        val encoding: String? = "",
        val audioEnc: String = "",
        val bitrate: Int = 0,
        val rcmode: Int = 0,
        val minqp_i_frame: Int = 0,
        val maxqp_i_frame: Int = 0,
        val minqp_b_frame: Int = 0,
        val maxqp_b_frame: Int = 0,
        val minqp_p_frame: Int = 0,
        val maxqp_p_frame: Int = 0,
        val initqp_i_frame: Int = 0,
        val initqp_b_frame: Int = 0,
        val initqp_p_frame: Int = 0,
        val interval_iframe: Int = 0,
        val storageEnable: Boolean = true,
        val videoRecorderType: Int = 0,
        val overlayEnable: Boolean = false
)

data class CameraParameters(val eis_enable: Boolean = false,
                            val ldc_enable: Boolean = false,
                            val shdr_enable: Boolean = false)

data class CameraSettings(var previewInfo: StreamInfo,
                          var recorderInfo: MutableList<StreamInfo>,
                          var snapshotInfo: StreamInfo,
                          var cameraParams: CameraParameters,
                          var cameraId: String,
                          var displayOn: Boolean,
                          var snapshotOn: Boolean,
                          var threeCamUse: Boolean)

interface CameraModule {
    fun getAvailableCameras(): Array<String>
    fun getSensorOrientation(): Int
    suspend fun openCamera(cameraId: String)
    fun setFramerate(fps: Int)
    fun addPreviewStream(surface: Surface)
    fun addStream(surface: Surface)
    fun addRecorderStream(stream: StreamInfo)
    fun addSharedStream(surfaceList: List<Surface>)
    fun addSnapshotStream(stream: StreamInfo)
    fun addVideoRecorder(recorder: VideoRecorder)
    fun startCamera()
    fun startRecording(value: Int?)
    fun isRecording() : Boolean
    fun stopRecording()
    suspend fun takeSnapshot(value: Int?): CombinedCaptureResult
    fun close()
    fun setEISEnable(value: Boolean)
    fun setLDCEnable(value: Boolean)
    fun setTNREnable(value: Byte)
    fun setNRMode(value: Int)
    fun setSHDREnable(value: Boolean)
    fun setAELock(value: Boolean)
    fun setAWBLock(value: Boolean)
    fun setAntiBandingMode(value: Int)
    fun setAEMode(value: Int)
    fun setAWBMode(value: Int)
    fun setAFMode(value: Int)
    fun setIRMode(value: Int)
    fun setADRCMode(value: Byte)
    fun setExpMeteringMode(value: Int)
    fun setISOMode(value: Long)
    fun setZoom(value: Int)
    fun setDefog(value: Boolean)
    fun setExposureTable(value: Boolean)
    fun setANRTable(value: Boolean)
    fun setSaturationLevel(value: Int)
    fun setSharpnessLevel(value: Int)
}
