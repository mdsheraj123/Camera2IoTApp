/*
# Changes from Qualcomm Innovation Center, Inc. are provided under the following license:

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

/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.video

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MotionEventCompat
import androidx.fragment.app.*
import androidx.preference.PreferenceManager
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.example.android.camera2.video.fragments.*
import com.google.android.material.tabs.TabLayout
import java.lang.ref.WeakReference


class CameraActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    var lastNonSettingTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        mActivity = WeakReference(this)
        setContentView(R.layout.activity_camera)
        container = findViewById(R.id.fragment_container)
        if (savedInstanceState == null) {
            switchToLaunchFragment()
        }
        val tabLayout = findViewById<TabLayout>(R.id.tabs_menu)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.i(TAG, "onTabSelected")
                tabUpdate(tab)
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                Log.i(TAG, "onTabReselected")
                tabUpdate(tab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
        })
    }

    fun switchToLaunchFragment() {
        Log.i(TAG, "switchToLaunchFragment")
        if (PermissionsFragment.hasPermissions(applicationContext)) {
            if(PreferenceManager.getDefaultSharedPreferences(this).getString("camera_fps", null)==null) {
                supportFragmentManager.commit {
                    Log.i(TAG, "replace<CameraFragmentSettings>")
                    replace<CameraFragmentSettings>(R.id.fragment_container, null, null)
                }
            }
            supportFragmentManager.commit {
                Log.i(TAG, "replace<CameraFragmentVideo>")
                replace<CameraFragmentVideo>(R.id.fragment_container, null, null)
            }
        } else {
            supportFragmentManager.commit {
                Log.i(TAG, "replace<PermissionsFragment>")
                replace<PermissionsFragment>(R.id.fragment_container, null, null)
            }
        }
    }

    fun tabUpdate(tab: TabLayout.Tab?) {
        Log.i(TAG, "tabUpdate ${tab!!.position}")
        when (tab!!.position) {
            0 -> supportFragmentManager.commit {
                Log.i(TAG, "replace<CameraFragmentVideo>")
                replace<CameraFragmentVideo>(R.id.fragment_container, null, null)
                lastNonSettingTab = 0
            }
            1 -> supportFragmentManager.commit {
                Log.i(TAG, "replace<CameraFragmentMultiCam>")
                replace<CameraFragmentMultiCam>(R.id.fragment_container, null, null)
                lastNonSettingTab = 1
            }
            2 -> supportFragmentManager.commit {
                Log.i(TAG, "replace<CameraFragmentSettings>")
                replace<CameraFragmentSettings>(R.id.fragment_container, null, null)
            }
        }
    }

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        val tabLayout = findViewById<TabLayout>(R.id.tabs_menu)
        if (tabLayout.selectedTabPosition == 2) {
            tabLayout.getTabAt(lastNonSettingTab)?.select()
        } else {
            super.onBackPressed()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        return when (MotionEventCompat.getActionMasked(event)) {
            MotionEvent.ACTION_UP -> {
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        container.postDelayed({
            container.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        mActivity?.clear()
        super.onDestroy()
    }

    companion object {
        var mActivity: WeakReference<Activity>? = null
        /** Combination of all flags required to put activity into immersive mode */
        const val FLAGS_FULLSCREEN =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        /** Milliseconds used for UI animations */
        const val ANIMATION_FAST_MILLIS = 50L
        const val ANIMATION_SLOW_MILLIS = 100L
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L
        private val TAG = CameraActivity::class.simpleName

        fun printAppVersion(context: Context) {
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val version = pInfo.versionName
                Log.i(TAG, "Camera2Video App version: ${version.toString()}")
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
    }
}
