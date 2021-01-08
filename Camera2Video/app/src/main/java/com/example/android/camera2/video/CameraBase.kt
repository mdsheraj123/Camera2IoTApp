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

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import com.example.android.camera.utils.OrientationLiveData.Companion.getOrientationValueForRotation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

    private var streamConfigOpMode: Int = 0x00
    private var isEISEnabled: Boolean = false
    private var isLDCEnabled: Boolean = false
    private var isSHDREnabled: Boolean = false

    private lateinit var imageReader: ImageReader

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    private val imageReaderHandler = Handler(imageReaderThread.looper)

    var currentSnapshotFilePath: String? = null

    override fun getAvailableCameras(): Array<String> = cameraManager.cameraIdList

    override suspend fun openCamera(cameraId: String) {
        Log.d(TAG, "openCamera")
        camera = suspendCancellableCoroutine { cont ->
            val callback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cont.resume(camera)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    val msg = when(error) {
                        ERROR_CAMERA_DEVICE -> "Fatal (device)"
                        ERROR_CAMERA_DISABLED -> "Device policy"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Fatal (service)"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                        else -> "Unknown"
                    }
                    val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                    Log.e(TAG, exc.message, exc)
                    if (cont.isActive) cont.resumeWithException(exc)
                }

                override fun onClosed(camera: CameraDevice) {
                    super.onClosed(camera)
                    clearStreams()
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

        // Set Opmode
        if (isEISEnabled) streamConfigOpMode = streamConfigOpMode or STREAM_CONFIG_EIS_MODE
        if (isSHDREnabled) streamConfigOpMode = streamConfigOpMode or STREAM_CONFIG_ZZHDR_MODE
        if (isLDCEnabled) streamConfigOpMode = streamConfigOpMode or STREAM_CONFIG_LDC_MODE

        Log.d(TAG, "Operation Mode: $streamConfigOpMode")

        val config = SessionConfiguration(
                streamConfigOpMode, outConfigurations, HandlerExecutor(cameraHandler),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        Log.d(TAG, "onConfigured session")
                        session = s
                        // if there is no active surface, do not set setRepeatingRequest.
                        if (streamSurfaceList.isNotEmpty()) session.setRepeatingRequest(previewRequest.build(), null, cameraHandler)
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
        val recorder = MediaCodecRecorder(context, stream)
        streamSurfaceList.add(recorder.getRecorderSurface())
        recorderList.add(recorder)
    }

    override fun addVideoRecorder(recorder: VideoRecorder) {
        recorderList.add(recorder)
    }

    @SuppressLint("Range")
    override fun addSnapshotStream(stream: StreamInfo)  {
        if (!::imageReader.isInitialized) {
            val format = when(stream.encoding) {
                "JPEG" -> ImageFormat.JPEG
                "RAW" -> ImageFormat.RAW10
                else -> {
                    throw Exception("Unsupported image format: ${stream.encoding}")
                }
            }
            if (format == ImageFormat.JPEG) {
                imageReader = ImageReader.newInstance(
                        stream.width, stream.height, format, IMAGE_BUFFER_SIZE)
            } else if (format == ImageFormat.RAW10) {
                val size = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                        .getOutputSizes(format).maxByOrNull { it.height * it.width }!!
                imageReader = ImageReader.newInstance(
                        size.width, size.height, format, IMAGE_BUFFER_SIZE)
            } else {
                throw Exception("Unsupported image format: ${stream.encoding}")
            }
            snapshotSurfaceList.add(imageReader.surface)
            captureRequest = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        }
    }

    override fun startCamera() {
        createSession(streamSurfaceList)
    }

    private fun clearStreams() {
        streamSurfaceList.clear()
        recorderList.clear()
    }

    override fun startRecording(orientation: Int?) {
        for (recorder in recorderList) {
            recorder.start(orientation)
        }
    }
    override fun isRecording() = false

    override fun stopRecording()  {
        for (recorder in recorderList) {
            recorder.stop()
        }
    }

    override suspend fun takeSnapshot(orientation: Int?):
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

                            // Compute EXIF orientation metadata
                            val exifOrientation = getOrientationValueForRotation(orientation?:0)

                            cont.resume(CombinedCaptureResult(
                                    image, result, exifOrientation, imageReader.imageFormat))
                        }
                    }
                }
            }, cameraHandler)
    }

    suspend fun saveResult(result: CombinedCaptureResult): String? = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val filename = "${createFileName()}.jpg"
                    val values = ContentValues()
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    currentSnapshotFilePath = "/storage/emulated/0/DCIM/Camera/$filename"
                    val imageOutStream = uri?.let { context.contentResolver.openOutputStream(it) };
                    imageOutStream?.write(bytes)
                    cont.resume(currentSnapshotFilePath)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            ImageFormat.RAW10 -> {
                try {
                    val output = createFile(context, "raw")
                    currentSnapshotFilePath = output.absolutePath
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    val out = FileOutputStream(output)
                    out.write(bytes)
                    out.close()
                    cont.resume(currentSnapshotFilePath)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write raw image to file", exc)
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

    fun getCurrentVideoFilePath():String? {
        for (recorder in recorderList) {
            if(recorder.getCurrentVideoFilePath()!=null) {
                return recorder.getCurrentVideoFilePath()
            }
        }
        return null
    }

    override fun close() {
        if (::session.isInitialized) {
            session.stopRepeating()
            session.abortCaptures()
        }
        camera.close()
        streamConfigOpMode = 0x00
    }

    private fun updateRepeatingRequest() {
        if (::session.isInitialized) {
            session.setRepeatingRequest(previewRequest.build(), null, cameraHandler)
        }
    }

    override fun setEISEnable(value: Boolean) {
        isEISEnabled = value
    }

    override fun setLDCEnable(value: Boolean) {
        isLDCEnabled = value
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

    override fun setSHDREnable(value: Boolean) {
        isSHDREnabled = value
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

        // Opmode
        private const val STREAM_CONFIG_ZZHDR_MODE: Int = 0xF002
        private const val STREAM_CONFIG_EIS_MODE: Int = 0xF200
        private const val STREAM_CONFIG_LDC_MODE: Int = 0xF800

        private fun createFile(context: Context, extension: String): File {
            val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM), "Camera")
            return File.createTempFile(createFileName(),".$extension",dir)
        }

        private fun createFileName(): String {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return "IMG_${sdf.format(Date())}"
        }
    }
}

private class HandlerExecutor(private val handler: Handler?) : Executor {
    override fun execute(command: Runnable) {
        handler?.post(command)
    }
}
