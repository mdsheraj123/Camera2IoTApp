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
import android.view.Surface
import androidx.preference.PreferenceManager

object CameraSettingsUtil {

    fun ParseWidth(res: String?) = res!!.split("x")[0].toInt()
    fun ParseHeight(res: String?) = res!!.split("x")[1].toInt()

    fun getCameraSettings(context: Context): CameraSettings {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)

        val previewInfo = StreamInfo(
                ParseWidth(sharedPref.getString("preview_resolution", null)),
                ParseHeight(sharedPref.getString("preview_resolution", null)))

        val streamInfo0 = StreamInfo(
                ParseWidth(sharedPref.getString("video_stream0_resolution", null)),
                ParseHeight(sharedPref.getString("video_stream0_resolution", null)),
                sharedPref.getString("video_framerate",null)!!.toInt(),
                sharedPref.getString("video_stream0_format", null)!!,
                sharedPref.getString("video_stream0_audio_format", null)!!
        )

        val streamInfo1 = StreamInfo(
                ParseWidth(sharedPref.getString("video_stream1_resolution", null)),
                ParseHeight(sharedPref.getString("video_stream1_resolution", null)),
                sharedPref.getString("video_framerate","")!!.toInt(),
                sharedPref.getString("video_stream1_format", "")!!,
                sharedPref.getString("video_stream1_audio_format", null)!!
        )

        val snapshotInfo = StreamInfo(
                ParseWidth(sharedPref.getString("snapshot_resolution", null)),
                ParseHeight(sharedPref.getString("snapshot_resolution", null)),
                sharedPref.getString("video_framerate",null)!!.toInt(),
                sharedPref.getString("snapshot_format", null))

        val recorderStreams = mutableListOf<StreamInfo>()

        recorderStreams.add(streamInfo0)

        if (sharedPref.getBoolean("video_stream1_enable",false)) {
            recorderStreams.add(streamInfo1)
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
                sharedPref.getString("camera_id", null)!!
        )
    }
}