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

import android.content.Context
import android.view.Surface
import androidx.preference.PreferenceManager

object CameraSettingsUtil {

    private fun ParseWidth(res: String?) = res!!.split("x")[0].toInt()
    private fun ParseHeight(res: String?) = res!!.split("x")[1].toInt()

    fun getCameraSettings(context: Context): CameraSettings {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)

        val previewInfo = StreamInfo(
                0,0,
                sharedPref.getString("camera_fps", null)!!.toInt(),
                overlayEnable = sharedPref.getBoolean("preview_overlay",false)
        )

        val streamInfo0 = StreamInfo(
                ParseWidth(sharedPref.getString("vid_0_size", null)),
                ParseHeight(sharedPref.getString("vid_0_size", null)),
                sharedPref.getString("vid_0_fps", null)!!.toInt(),
                sharedPref.getString("vid_0_format", null)!!,
                sharedPref.getString("vid_0_audio_format", null)!!,
                sharedPref.getString("vid_0_bitrate", null)!!.toInt(),
                sharedPref.getString("vid_0_rate_control", null)!!.toInt(),
                sharedPref.getString("vid_0_i_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_0_i_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_0_b_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_0_b_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_0_p_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_0_p_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_0_i_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_0_b_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_0_p_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_0_iframe_interval", null)!!.toInt(),
                sharedPref.getBoolean("video_storage", true),
                videoRecorderType = sharedPref.getString("video_recorder_type", null)!!.toInt(),
                overlayEnable = sharedPref.getBoolean("vid_0_overlay",false)
        )

        val streamInfo1 = StreamInfo(
                ParseWidth(sharedPref.getString("vid_1_size", null)),
                ParseHeight(sharedPref.getString("vid_1_size", null)),
                sharedPref.getString("vid_1_fps","")!!.toInt(),
                sharedPref.getString("vid_1_format", "")!!,
                sharedPref.getString("vid_1_audio_format", null)!!,
                sharedPref.getString("vid_1_bitrate",null)!!.toInt(),
                sharedPref.getString("vid_1_rate_control", null)!!.toInt(),
                sharedPref.getString("vid_1_i_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_1_i_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_1_b_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_1_b_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_1_p_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_1_p_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_1_i_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_1_b_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_1_p_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_1_iframe_interval", null)!!.toInt(),
                sharedPref.getBoolean("video_storage", true),
                videoRecorderType = sharedPref.getString("video_recorder_type", null)!!.toInt(),
                overlayEnable = sharedPref.getBoolean("vid_1_overlay",false)
        )

        val streamInfo2 = StreamInfo(
                ParseWidth(sharedPref.getString("vid_2_size", null)),
                ParseHeight(sharedPref.getString("vid_2_size", null)),
                sharedPref.getString("vid_2_fps", null)!!.toInt(),
                sharedPref.getString("vid_2_format", null)!!,
                sharedPref.getString("vid_2_audio_format", null)!!,
                sharedPref.getString("vid_2_bitrate", null)!!.toInt(),
                sharedPref.getString("vid_2_rate_control", null)!!.toInt(),
                sharedPref.getString("vid_2_i_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_2_i_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_2_b_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_2_b_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_2_p_min_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_2_p_max_qp_range", null)!!.toInt(),
                sharedPref.getString("vid_2_i_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_2_b_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_2_p_init_qp", null)!!.toInt(),
                sharedPref.getString("vid_2_iframe_interval", null)!!.toInt(),
                sharedPref.getBoolean("video_storage", true),
                videoRecorderType = sharedPref.getString("video_recorder_type", null)!!.toInt(),
                overlayEnable = sharedPref.getBoolean("vid_2_overlay",false)
        )

        val snapshotInfo = StreamInfo(
                ParseWidth(sharedPref.getString("snapshot_size", null)),
                ParseHeight(sharedPref.getString("snapshot_size", null)),
                sharedPref.getString("camera_fps",null)!!.toInt(),
                sharedPref.getString("snapshot_format", null))

        val recorderStreams = mutableListOf<StreamInfo>()

        if (sharedPref.getBoolean("vid_0_enable",false)) {
            recorderStreams.add(streamInfo0)
        }

        if (sharedPref.getBoolean("vid_1_enable",false)) {
            recorderStreams.add(streamInfo1)
        }
        if (sharedPref.getBoolean("vid_2_enable", false)) {
            recorderStreams.add(streamInfo2)
        }

        return CameraSettings(
                previewInfo,
                recorderStreams,
                snapshotInfo,
                CameraParameters(
                        sharedPref.getBoolean("eis_enable",false),
                        sharedPref.getBoolean("ldc_enable",false),
                        sharedPref.getBoolean("shdr_enable",false)
                ),
                sharedPref.getString("camera_id", null)!!,
                sharedPref.getBoolean("display_enable",false),
                sharedPref.getBoolean("snapshot_enable",false),
                sharedPref.getBoolean("three_camera",false)
        )
    }
}
