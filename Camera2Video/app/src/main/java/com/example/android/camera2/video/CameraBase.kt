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

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraBase(val context: Context): CameraModule {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    private val cameraHandler = Handler(cameraThread.looper)

    private lateinit var characteristics: CameraCharacteristics

    private lateinit var camera: CameraDevice

    lateinit var session: CameraCaptureSession

    lateinit var previewRequest: CaptureRequest.Builder

    lateinit var captureRequest: CaptureRequest.Builder

    private val streamSurfaceList = mutableListOf<Surface>()

    private val snapshotSurfaceList = mutableListOf<Surface>()

    private val recorderList = mutableListOf<VideoRecorder>()

    private var previewFps = 30

    private lateinit var imageReader: ImageReader

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    private val imageReaderHandler = Handler(imageReaderThread.looper)

    override fun getAvailableCameras(): Array<String> = cameraManager.cameraIdList

    override suspend fun openCamera(cameraId: String) {
        Log.d(TAG, "openCamera")
        camera = suspendCoroutine { cont ->
            val callback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cont.resume(camera)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    throw Exception("Fail to open Camera: $cameraId")
                    cont.resume(camera)
                }
            }
            cameraManager.openCamera(cameraId, callback, cameraHandler)
        }
        previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        characteristics = cameraManager.getCameraCharacteristics(cameraId)
        Log.d(TAG, "openCamera done")
    }

    private fun createSession(targets: List<Surface>) {

        val outConfigurations = mutableListOf<OutputConfiguration>()
        for (surface in targets) {
            outConfigurations.add(OutputConfiguration(surface))
            previewRequest.addTarget(surface)
        }
        for (surface in snapshotSurfaceList) {
            outConfigurations.add(OutputConfiguration(surface))
        }

        previewRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(previewFps, previewFps))

        val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outConfigurations, HandlerExecutor(cameraHandler),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        Log.d(TAG, "onConfigured session")
                        session = s
                        session.setRepeatingRequest(previewRequest.build(), null, cameraHandler)
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) =
                            s.device.close()
                })

        config.sessionParameters = previewRequest.build()
        camera.createCaptureSession(config)
    }
    override fun setFramerate(fps: Int) {previewFps = fps}
    override fun addPreviewStream(surface: Surface) {
        streamSurfaceList.add(surface)
    }
    override fun addStream(surface: Surface)  {
        streamSurfaceList.add(surface)
    }
    override fun addRecorderStream(stream: StreamInfo)  {
        val recorder = MediaCodecRecorder(context, stream.width, stream.height, stream.fps, stream.encoding, stream.audioEnc)
        streamSurfaceList.add(recorder.getRecorderSurface())
        recorderList.add(recorder)
    }

    @SuppressLint("Range")
    override fun addSnapshotStream(stream: StreamInfo)  {
        if (!::imageReader.isInitialized) {
            val format = when(stream.encoding) {
                "JPEG" -> ImageFormat.JPEG
                "RAW" -> ImageFormat.RAW_SENSOR
                else -> {
                    throw Exception("Unsupported image format: ${stream.encoding}")
                }
            }
            imageReader = ImageReader.newInstance(
                    stream.width, stream.height, format, IMAGE_BUFFER_SIZE)
            snapshotSurfaceList.add(imageReader.surface)
            captureRequest = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        }
    }

    override fun startCamera() {
        createSession(streamSurfaceList)
    }

    override fun startRecording()  {
        for (recorder in recorderList) {
            recorder.start()
        }
    }
    override fun isRecording() = false

    override fun stopRecording()  {
        for (recorder in recorderList) {
            recorder.stop()
        }
    }

    override suspend fun takeSnapshot():
        CombinedCaptureResult = suspendCoroutine { cont ->
            @Suppress("ControlFlowWithEmptyBody")
            while (imageReader.acquireNextImage() != null) {}

            val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                Log.d(TAG, "Image available in queue: ${image.timestamp}")
                imageQueue.add(image)
            }, imageReaderHandler)

            session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureStarted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        timestamp: Long,
                        frameNumber: Long) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                }

                override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                    Log.d(TAG, "Capture result received: $resultTimestamp")

                    val exc = TimeoutException("Image dequeuing took too long")
                    val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                    imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                    @Suppress("BlockingMethodInNonBlockingContext")
                    GlobalScope.launch(cont.context) {
                        while (true) {
                            val image = imageQueue.take()

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    image.format != ImageFormat.DEPTH_JPEG &&
                                    image.timestamp != resultTimestamp) continue
                            Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                            imageReaderHandler.removeCallbacks(timeoutRunnable)
                            imageReader.setOnImageAvailableListener(null, null)

                            while (imageQueue.size > 0) {
                                imageQueue.take().close()
                            }

                            cont.resume(CombinedCaptureResult(
                                    image, result, 0, imageReader.imageFormat))
                        }
                    }
                }
            }, cameraHandler)
    }

    suspend fun saveResult(result: CombinedCaptureResult): Boolean = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val output = createFile(context, "jpg")
                    val values = ContentValues()
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, output.absolutePath)
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    val imageOutStream = uri?.let { context.contentResolver.openOutputStream(it) };
                    imageOutStream?.write(bytes)
                    cont.resume(true)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val output = createFile(context, "dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    cont.resume(true)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    override fun close() {
        camera.close()
    }

    fun updateRepeatingRequest() {
        if (::session.isInitialized) {
            session.setRepeatingRequest(previewRequest.build(), null, cameraHandler)
        }
    }

    override fun setEISEnable(value: Byte) {
        VendorTagUtil.setEISEnable(previewRequest, value)
        updateRepeatingRequest()
    }

    override fun setLDCEnable(value: Byte) {
        VendorTagUtil.setLDCEnable(previewRequest, value)
        updateRepeatingRequest()
    }

    override fun setTNREnable(value: Byte) {
        VendorTagUtil.setTNREnable(previewRequest, value)
        updateRepeatingRequest()
    }

    override fun setEffectMode(value: Int) {
        Log.d(TAG, "Effect mode: $value")
        previewRequest.set(CaptureRequest.CONTROL_EFFECT_MODE, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_EFFECT_MODE, value)
        }
        updateRepeatingRequest()
    }

    override fun setAntiBandingMode(value: Int) {
        Log.d(TAG, "Antibanding mode: $value")
        previewRequest.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, value)
        }
        updateRepeatingRequest()
    }

    override fun setAEMode(value: Int) {
        Log.d(TAG, "AE mode: $value")
        previewRequest.set(CaptureRequest.CONTROL_AE_MODE, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_MODE, value)
        }
        updateRepeatingRequest()
    }

    override fun setAWBMode(value: Int) {
        Log.d(TAG, "AWB mode: $value")
        previewRequest.set(CaptureRequest.CONTROL_AWB_MODE, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AWB_MODE, value)
        }
        updateRepeatingRequest()
    }

    override fun setAFMode(value: Int) {
        Log.d(TAG, "AF mode: $value")
        previewRequest.set(CaptureRequest.CONTROL_AF_MODE, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AF_MODE, value)
        }
        updateRepeatingRequest()
    }

    override fun setIRMode(value: Int) {
        Log.d(TAG, "IR mode: $value")
        VendorTagUtil.setIRLED(previewRequest, value)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setIRLED(captureRequest, value)
        }
        updateRepeatingRequest()
    }

    override fun setADRCMode(value: Byte) {
        Log.d(TAG, "ADRC mode: $value")
        VendorTagUtil.setADRC(previewRequest, value)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setADRC(captureRequest, value)
        }
        updateRepeatingRequest()
    }

    override fun setExpMeteringMode(value: Int) {
        Log.d(TAG, "Exp Metering mode: $value")
        VendorTagUtil.setExposureMetering(previewRequest, value)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setExposureMetering(captureRequest, value)
        }
        updateRepeatingRequest()
    }

    override fun setISOMode(value: Long) {
        Log.d(TAG, "ISO mode: $value")
        VendorTagUtil.setIsoExpPrioritySelectPriority(previewRequest, 0)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setIsoExpPrioritySelectPriority(captureRequest, 0)
        }

        VendorTagUtil.setIsoExpPriority(previewRequest, value)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setIsoExpPriority(captureRequest, value)
        }

        updateRepeatingRequest()
    }

    override fun setNRMode(value: Int) {
        previewRequest.set(CaptureRequest.NOISE_REDUCTION_MODE, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.NOISE_REDUCTION_MODE, value)
        }
        updateRepeatingRequest()
    }

    override fun setSHDREnable(value: Byte) {
        VendorTagUtil.setSHDREnable(previewRequest, value)
        updateRepeatingRequest()
    }

    override fun setAELock(value: Boolean) {
        previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, value)
        updateRepeatingRequest()
    }

    override fun setAWBLock(value: Boolean) {
        previewRequest.set(CaptureRequest.CONTROL_AWB_LOCK, value)
        updateRepeatingRequest()
    }

    companion object {
        private val TAG = CameraBase::class.simpleName

        data class CombinedCaptureResult(
                val image: Image,
                val metadata: CaptureResult,
                val orientation: Int,
                val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }
    }
}

private class HandlerExecutor(private val handler: Handler?) : Executor {
    override fun execute(command: Runnable) {
        handler?.post(command)
    }
}