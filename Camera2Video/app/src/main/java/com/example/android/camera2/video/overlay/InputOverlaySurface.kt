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
import java.util.concurrent.atomic.AtomicLong

class FrameSync(size: Int) {
    private val sync = Object()
    private var isReleased = false
    private var count = AtomicLong(0)
    private var size = size
    private val syncTimeout = 10L
    fun notifyFrame(): Boolean {
        if (!isReleased) {
            while (count.get() >= size) {
                synchronized(sync) {
                    sync.wait(syncTimeout)
                }
            }
            count.incrementAndGet()
            synchronized(sync) {
                sync.notifyAll()
            }
        }
        return !isReleased
    }

    fun waitFrame(): Long {
        var cnt = 0L
        if (!isReleased) {
            while (count.get() <= 0) {
                synchronized(sync) {
                    sync.wait(syncTimeout)
                }
            }
            cnt = count.decrementAndGet()
            synchronized(sync) {
                sync.notifyAll()
            }
        }
        return if (isReleased) -1 else cnt
    }

    fun release() {
        isReleased = true
        count.set(1)
        synchronized(sync) {
            sync.notifyAll()
        }
    }
}

class InputOverlaySurface : SurfaceTexture.OnFrameAvailableListener {

    private val surfaceTexture : SurfaceTexture
    private val surface : Surface
    private val frameSync = FrameSync(10)
    private var prvTimestamp: Long = 0
    private var frameNumber = 0L
    private var frameRateArray = Array(FRAMERATE_ARRAY_SIZE) {0.0f}
    private var frameRate: Float = 0.0f

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
        var count = 0L
        do {
            count = frameSync.waitFrame()
            surfaceTexture.updateTexImage()
        } while (count > 0)

        if (count < 0) {
            return false
        }
        return true
    }

    fun getTimestamp(): Long {
        return surfaceTexture.timestamp
    }

    fun gerFrameRate(): Float {
        return frameRate
    }

    fun getFrameNumber(): Long {
        return frameNumber
    }

    private fun measureFrameRate() {
        val timestamp = getTimestamp()
        if (prvTimestamp > 0) {
            frameRateArray[frameNumber.toInt() % FRAMERATE_ARRAY_SIZE] =  1000000000.0f / ((timestamp - prvTimestamp).toFloat())
            if (frameNumber > FRAMERATE_ARRAY_SIZE) {
                var fps = 0.0f
                for (i in 0 until FRAMERATE_ARRAY_SIZE) {
                    fps += frameRateArray[i]
                }
                frameRate = fps / FRAMERATE_ARRAY_SIZE
            }
        }
        prvTimestamp = timestamp
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameSync.notifyFrame()
        frameNumber++
        measureFrameRate()
    }

    fun release() {
        frameSync.release()
    }

    companion object {
        private val TAG = this::class.simpleName
        const val FRAMERATE_ARRAY_SIZE = 4
    }
}