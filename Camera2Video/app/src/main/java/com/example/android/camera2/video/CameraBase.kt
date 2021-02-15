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
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
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
import android.widget.Toast
import com.example.android.camera.utils.OrientationLiveData.Companion.getOrientationValueForRotation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlin.properties.Delegates

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

    private val sharedStreamSurfaceList = mutableListOf<List<Surface>>()

    private val recorderList = mutableListOf<VideoRecorder>()

    private var previewFps = 30

    private var streamConfigOpMode: Int = 0x00
    private var isEISEnabled: Boolean = false
    private var isLDCEnabled: Boolean = false
    private var isSHDREnabled: Boolean = false
    private var exposureValue = 0
    private var enableZSL = true

    private lateinit var imageReader: ImageReader

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    private val imageReaderHandler = Handler(imageReaderThread.looper)

    var currentSnapshotFilePath: String? = null

    val closeSync = Object()

    var listeners = mutableListOf<CameraReadyListener>()

    var isCameraReady: Boolean by Delegates.observable(false) { _, old, new ->
        listeners.forEach { it.onIsCameraReadyUpdated(old, new) }
    }

    override fun getAvailableCameras(): Array<String> = cameraManager.cameraIdList

    override fun getSensorOrientation(): Int {
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    override suspend fun openCamera(cameraId: String) {
        Log.i(TAG, "openCamera")
        camera = suspendCancellableCoroutine { cont ->
            val callback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "openCamera onOpened")
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera $cameraId has been disconnected")
                    CameraActivity().finish()
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
                    Log.i(TAG, "openCamera onClosed")
                    super.onClosed(camera)
                    clearStreams()
                    synchronized(closeSync) {
                        closeSync.notifyAll()
                    }
                }
            }
            cameraManager.openCamera(cameraId, callback, cameraHandler)
        }
        previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

        characteristics = cameraManager.getCameraCharacteristics(cameraId)
        Log.i(TAG, "openCamera done")
    }

    private fun createSession(targets: List<Surface>) {
        Log.i(TAG, "createSession enter")

        val outConfigurations = mutableListOf<OutputConfiguration>()
        for (surface in targets) {
            outConfigurations.add(OutputConfiguration(surface))
            previewRequest.addTarget(surface)
            captureRequest.addTarget(surface)
        }
        for (surface in snapshotSurfaceList) {
            outConfigurations.add(OutputConfiguration(surface))
            captureRequest.addTarget(surface)
        }

        for (sharedSurface in sharedStreamSurfaceList) {
            val sharedOutputConfig = OutputConfiguration(sharedSurface[0])
            sharedOutputConfig.enableSurfaceSharing()
            previewRequest.addTarget(sharedSurface[0])
            captureRequest.addTarget(sharedSurface[0])
            for (surface in sharedSurface.takeLast(sharedSurface.size - 1)) {
                sharedOutputConfig.addSurface(surface)
                previewRequest.addTarget(surface)
                captureRequest.addTarget(surface)
            }
            outConfigurations.add(sharedOutputConfig)
        }

        previewRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(previewFps, previewFps))
        captureRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(previewFps, previewFps))

        // Set ZSL Mode
        captureRequest.set(CaptureRequest.CONTROL_ENABLE_ZSL, enableZSL)

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
                        // Set Default Camera Param
                        setDefaultCameraParam()
                        // if there is no active surface, do not set setRepeatingRequest.
                        if (streamSurfaceList.isNotEmpty()) session.setRepeatingRequest(previewRequest.build(), null, cameraHandler)
                        isCameraReady = true
                        Log.i(TAG, "isCameraReady true")
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) =
                            s.device.close()
                })

        config.sessionParameters = previewRequest.build()
        camera.createCaptureSession(config)
        Log.i(TAG, "createSession exit")
    }

    private fun getJsonString(fileName: String): String? {
        val file = File(context.filesDir, fileName)
        var jsonString: String? = null
        try {
            jsonString = File(file.absolutePath).bufferedReader().use { it.readText() }
            Log.i(TAG, "Json String: $jsonString")
        } catch (ioException: IOException) {
            Log.e(TAG, "Not able to fetch Json String $ioException")
        }
        return jsonString
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

    override fun addSharedStream(surfaceList: List<Surface>) {
        sharedStreamSurfaceList.add(surfaceList)
    }

    override fun addVideoRecorder(recorder: VideoRecorder) {
        recorderList.add(recorder)
    }

    @SuppressLint("Range")
    override fun addSnapshotStream(stream: StreamInfo) {
        val format = when (stream.encoding) {
            "JPEG" -> ImageFormat.JPEG
            "RAW" -> ImageFormat.RAW10
            else -> {
                throw Exception("Unsupported image format: ${stream.encoding}")
            }
        }
        if (format == ImageFormat.JPEG) {
            imageReader = ImageReader.newInstance(
                    stream.width, stream.height, format, IMAGE_BUFFER_SIZE)
            // Set JPEG Quality
            captureRequest.set(CaptureRequest.JPEG_QUALITY, IMAGE_JPEG_QUALITY)
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
    }

    override fun startCamera() {
        createSession(streamSurfaceList)
    }

    private fun clearStreams() {
        Log.i(TAG, "clearStreams")
        streamSurfaceList.clear()
        recorderList.clear()
        snapshotSurfaceList.clear()
        sharedStreamSurfaceList.clear()
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
            Log.i(TAG, "takeSnapshot")
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
        Log.i(TAG, "saveResult")
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
        Log.i(TAG, "close enter")
        if (::session.isInitialized) {
            session.stopRepeating()
            session.abortCaptures()
        }
        if (::camera.isInitialized) {
            camera.close()
            synchronized(closeSync) {
                closeSync.wait(CLOSESYNC_TIMEOUT)
            }
        }
        streamConfigOpMode = 0x00
        Log.i(TAG, "close exit")
    }

    private fun setDefaultCameraParam() {
        Log.i(TAG, "setDefaultCameraParam")

        // Set AntiBanding to Auto
        previewRequest.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, 3)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, 3)
        }

        // Set AE Exposure Compensation to 0
        previewRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
        }

        // Set AE Mode to On
        previewRequest.set(CaptureRequest.CONTROL_AE_MODE, 1)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_MODE, 1)
        }

        // Set AE Lock to False
        previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, false)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_LOCK, false)
        }

        // Set AWB to Auto
        previewRequest.set(CaptureRequest.CONTROL_AWB_MODE, 1)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AWB_MODE, 1)
        }

        // Set AWB lock to False
        previewRequest.set(CaptureRequest.CONTROL_AWB_LOCK, false)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AWB_LOCK, false)
        }

        // Set AF Mode to Off.
        previewRequest.set(CaptureRequest.CONTROL_AF_MODE, 0)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AF_MODE, 0)
        }

        // Set Noise Reduction Mode to FAST
        previewRequest.set(CaptureRequest.NOISE_REDUCTION_MODE, 1)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.NOISE_REDUCTION_MODE, 1)
        }

        // Set ADRC to False
        VendorTagUtil.setADRC(previewRequest, 0)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setADRC(captureRequest, 0)
        }

        // Set IR LED to Off
        VendorTagUtil.setIRLED(previewRequest, 0)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setIRLED(captureRequest, 0)
        }

        // Set ISO Mode to Auto
        VendorTagUtil.setIsoExpPrioritySelectPriority(previewRequest, 0)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setIsoExpPrioritySelectPriority(captureRequest, 0)
        }

        VendorTagUtil.setIsoExpPriority(previewRequest, 0)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setIsoExpPriority(captureRequest, 0)
        }

        // Set Exposure Metering to Avg
        VendorTagUtil.setExposureMetering(previewRequest, 0)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setExposureMetering(captureRequest, 0)
        }

        // Set Saturation to Level 5
        VendorTagUtil.setSaturationLevel(previewRequest, 5)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setSaturationLevel(captureRequest, 5)
        }

        // Set Sharpness to Level 2
        VendorTagUtil.setSharpnessLevel(previewRequest, 2)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setSharpnessLevel(captureRequest, 2)
        }

        // Set Exposure Value
        previewRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION , exposureValue)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION , exposureValue)
        }
    }

    override fun setExposureValue(value: Int) {
        Log.d(TAG, "Exposure Value: $value")
        exposureValue = value
    }

    override fun setZSL(value: Boolean) {
        Log.d(TAG, "ZSL Value: $value")
        enableZSL = value
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

    override fun setZoom(zoomValue: Int) {
        Log.d(TAG, "Zoom Value: $zoomValue")
        val rect: Rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return

        var ratio: Float = when (zoomValue) {
            0 -> 1.toFloat()
            else -> 1.toFloat() / zoomValue
        }

        val croppedWidth: Int = rect.width() - (rect.width() * ratio).roundToInt()
        val croppedHeight: Int = rect.height() - (rect.height() * ratio).roundToInt()

        //Finally, zoom represents the zoomed visible area
        val zoom = Rect(croppedWidth / 2, croppedHeight / 2,
                rect.width() - croppedWidth / 2, rect.height() - croppedHeight / 2)

        previewRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom)
        }
        updateRepeatingRequest()
    }

    private fun showTextDialog(filename: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(CameraActivity.mActivity?.get())
        try {
            builder.setMessage(readFile(context.filesDir.absolutePath + filename))
                    .setCancelable(true)
                    .setPositiveButton("Okay") { dialog, _ -> dialog.cancel() }.show()
        } catch (e: IOException) {
            Toast.makeText(context, "Error in reading file", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    private fun readFile(path: String): String? {
        val stream = FileInputStream(File(path))
        return stream.use { stream ->
            val fc: FileChannel = stream.channel
            val bb: MappedByteBuffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size())
            Charset.defaultCharset().decode(bb).toString()
        }
    }

    override fun setDefog(value: Boolean) {
        Log.d(TAG, "Defog: $value")
        if (value) {
            val jsonString = getJsonString("Defog_Table.json")
            if (jsonString != null) {
                showTextDialog("/Defog_Table.json")
                VendorTagUtil.setDefog(previewRequest, jsonString)
                VendorTagUtil.setDefog(captureRequest, jsonString)
                updateRepeatingRequest()
            } else {
                Log.e(TAG, "Defog Data is not available")
                Toast.makeText(context, "Please push Defog_Table.json file to device.", Toast.LENGTH_SHORT).show()
            }
        } else {
            val jsonString = "{\"enable\" : 0}"
            VendorTagUtil.setDefog(previewRequest, jsonString)
            VendorTagUtil.setDefog(captureRequest, jsonString)
            updateRepeatingRequest()
        }
    }

    override fun setExposureTable(value: Boolean) {
        Log.d(TAG, "ExposureTable: $value")
        if (value) {
            val jsonString = getJsonString("Exposure_Table.json")
            if (jsonString != null) {
                showTextDialog("/Exposure_Table.json")
                VendorTagUtil.setExposureTable(previewRequest, jsonString)
                VendorTagUtil.setExposureTable(captureRequest, jsonString)
                updateRepeatingRequest()
            } else {
                Log.e(TAG, "Exposure Table is not available")
                Toast.makeText(context, "Please push Exposure_Table.json file to device.", Toast.LENGTH_SHORT).show()
            }
        } else {
            val jsonString = "{\"isValid\" : 0}"
            VendorTagUtil.setExposureTable(previewRequest, jsonString)
            VendorTagUtil.setExposureTable(captureRequest, jsonString)
            updateRepeatingRequest()
        }
    }

    override fun setANRTable(value: Boolean) {
        Log.d(TAG, "ANR: $value")
        if (value) {
            val jsonString = getJsonString("ANR_Table.json")
            if (jsonString != null) {
                showTextDialog("/ANR_Table.json")
                VendorTagUtil.setANRTable(previewRequest, jsonString)
                VendorTagUtil.setANRTable(captureRequest, jsonString)
                updateRepeatingRequest()
            } else {
                Log.e(TAG, "ANR Table is not available")
                Toast.makeText(context, "Please push ANR_Table.json file to device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun setSaturationLevel(value: Int) {
        Log.d(TAG, "Saturation Level: $value")
        VendorTagUtil.setSaturationLevel(previewRequest, value)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setSaturationLevel(captureRequest, value)
        }
        updateRepeatingRequest()
    }

    override fun setSharpnessLevel(value: Int) {
        Log.d(TAG, "Sharpness Level: $value")
        VendorTagUtil.setSharpnessLevel(previewRequest, value)
        if (::captureRequest.isInitialized) {
            VendorTagUtil.setSharpnessLevel(captureRequest, value)
        }
        updateRepeatingRequest()
    }

    override fun setNRMode(value: Int) {
        Log.d(TAG, "Noise Reduction mode: $value")
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
        Log.d(TAG, "AE Lock: $value")
        previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AE_LOCK, value)
        }
        updateRepeatingRequest()
    }

    override fun setAWBLock(value: Boolean) {
        Log.d(TAG, "AWB Lock: $value")
        previewRequest.set(CaptureRequest.CONTROL_AWB_LOCK, value)
        if (::captureRequest.isInitialized) {
            captureRequest.set(CaptureRequest.CONTROL_AWB_LOCK, value)
        }
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

        private const val CLOSESYNC_TIMEOUT = 1000L
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000
        private const val IMAGE_JPEG_QUALITY: Byte = 85

        // Opmode
        private const val STREAM_CONFIG_ZZHDR_MODE: Int = 0xF002
        private const val STREAM_CONFIG_EIS_MODE: Int = 0xF200
        private const val STREAM_CONFIG_LDC_MODE: Int = 0xF800

        private fun createFile(context: Context, extension: String): File {
            val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM), "Camera")
            return File.createTempFile(createFileName(), ".$extension", dir)
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
