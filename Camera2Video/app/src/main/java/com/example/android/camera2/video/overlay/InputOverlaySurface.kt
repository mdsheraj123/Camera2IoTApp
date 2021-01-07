/*
# Copyright (c) 2021 Qualcomm Innovation Center, Inc.
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

package com.example.android.camera2.video.overlay

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface

class InputOverlaySurface : SurfaceTexture.OnFrameAvailableListener {

    private val surfaceTexture : SurfaceTexture

    private val surface : Surface

    private val frameSyncObject = Object()
    private var frameAvailable = false
    private var running = true

    constructor (texName: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            throw Exception("Invalid surface resolution")
        }

        surfaceTexture = SurfaceTexture(texName)
        surfaceTexture.setDefaultBufferSize(width, height)
        surfaceTexture.setOnFrameAvailableListener(this)
        surface = Surface(surfaceTexture)
    }

    fun getSurfaceTexture(): SurfaceTexture {
        return surfaceTexture
    }

    fun getSurface(): Surface {
        return surface
    }

    fun awaitFrame() : Boolean {
        synchronized(frameSyncObject) {
            while ((!frameAvailable) && running) {
                try {
                    frameSyncObject.wait(FRAME_TIMEOUT_MS)
                    if (!frameAvailable) {
                        Log.d(TAG,"Surface frame time out")
                        return false
                    }
                } catch (e: InterruptedException) {
                    return false
                }
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
        if (!running) return false
        return true
    }

    fun getTimestamp(): Long {
        return surfaceTexture.timestamp
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(frameSyncObject) {
            if (frameAvailable) {
                throw Exception("frameAvailable unexpected state");
            }
            frameAvailable = true;
            frameSyncObject.notifyAll()
        }
    }

    fun release() {
        running = false
        frameAvailable = true
        synchronized(frameSyncObject) {
            frameSyncObject.notifyAll()
        }
    }

    companion object {
        private val TAG = this::class.simpleName
        const val FRAME_TIMEOUT_MS = 2000L
    }
}