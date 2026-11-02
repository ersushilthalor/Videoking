package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraDeviceManager(
    private val context: Context,
    private val onCameraError: (String) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var currentLens: LensInfo? = null
    private var previewSurface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null

    private val cameraOpenCloseLock = Semaphore(1)

    var currentZoom: Float = 1.0f
        private set

    var isTorchOn: Boolean = false
        private set

    data class HalCapabilities(
        val toneMapMode: Int?,
        val edgeMode: Int?,
        val noiseReductionMode: Int?,
        val supportedZoomRange: Range<Float>,
        val exposureCompensationRange: Range<Int>,
        val exposureStep: Float
    )

    var halCapabilities: HalCapabilities? = null
        private set

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (cameraThread == null) {
            cameraThread = HandlerThread("CameraBackground").apply {
                start()
                cameraHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join(1000)
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping camera thread: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(lens: LensInfo, texture: SurfaceTexture, previewWidth: Int, previewHeight: Int) {
        startBackgroundThread()
        currentLens = lens
        surfaceTexture = texture
        texture.setDefaultBufferSize(previewWidth, previewHeight)
        previewSurface = Surface(texture)

        cameraHandler?.post {
            try {
                if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw RuntimeException("Time out waiting to lock camera opening.")
                }

                inspectHalCapabilities(lens.cameraId)

                cameraManager.openCamera(lens.cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraOpenCloseLock.release()
                        cameraDevice = camera
                        createCaptureSession(lens)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cameraOpenCloseLock.release()
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        cameraOpenCloseLock.release()
                        camera.close()
                        cameraDevice = null
                        onCameraError("Camera error code: $error")
                    }
                }, cameraHandler)
            } catch (e: Exception) {
                cameraOpenCloseLock.release()
                Log.e(TAG, "Failed to open camera: ${e.message}", e)
                onCameraError("Failed to open camera: ${e.message}")
            }
        }
    }

    private fun inspectHalCapabilities(cameraId: String) {
        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)

            // Tone mapping modes
            val toneMapModes = chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
            val chosenToneMap = when {
                toneMapModes.contains(CaptureRequest.TONEMAP_MODE_FAST) -> CaptureRequest.TONEMAP_MODE_FAST
                toneMapModes.contains(CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE) -> CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE
                toneMapModes.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY) -> CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
                else -> null
            }

            // Edge / sharpening modes
            val edgeModes = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: intArrayOf()
            val chosenEdge = when {
                edgeModes.contains(CaptureRequest.EDGE_MODE_FAST) -> CaptureRequest.EDGE_MODE_FAST
                edgeModes.contains(CaptureRequest.EDGE_MODE_OFF) -> CaptureRequest.EDGE_MODE_OFF
                edgeModes.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) -> CaptureRequest.EDGE_MODE_HIGH_QUALITY
                else -> null
            }

            // Noise reduction modes
            val nrModes = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: intArrayOf()
            val chosenNr = when {
                nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST) -> CaptureRequest.NOISE_REDUCTION_MODE_FAST
                nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) -> CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) -> CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                else -> null
            }

            val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1.0f, 10.0f)
            } else {
                val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 10.0f
                Range(1.0f, maxZoom)
            }

            val aeRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)
            val aeStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.0f

            halCapabilities = HalCapabilities(
                toneMapMode = chosenToneMap,
                edgeMode = chosenEdge,
                noiseReductionMode = chosenNr,
                supportedZoomRange = zoomRange,
                exposureCompensationRange = aeRange,
                exposureStep = aeStep
            )
            Log.i(TAG, "HAL capabilities inspected: $halCapabilities")
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting HAL capabilities: ${e.message}")
        }
    }

    private fun createCaptureSession(lens: LensInfo) {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)

                // Continuous video autofocus
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

                // Flash / torch
                if (lens.hasFlash) {
                    set(CaptureRequest.FLASH_MODE, if (isTorchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                }

                // Prevent aggressive HAL double processing
                halCapabilities?.toneMapMode?.let { mode ->
                    set(CaptureRequest.TONEMAP_MODE, mode)
                }
                halCapabilities?.edgeMode?.let { mode ->
                    set(CaptureRequest.EDGE_MODE, mode)
                }
                halCapabilities?.noiseReductionMode?.let { mode ->
                    set(CaptureRequest.NOISE_REDUCTION_MODE, mode)
                }

                // Apply initial zoom
                applyZoom(this, currentZoom, lens)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfig = OutputConfiguration(surface)
                if (lens.physicalId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    outputConfig.setPhysicalCameraId(lens.physicalId)
                }

                val executor = Executor { command -> cameraHandler?.post(command) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfig),
                    executor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            updatePreview()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onCameraError("Camera session configuration failed.")
                        }
                    }
                )
                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            updatePreview()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onCameraError("Camera session configuration failed.")
                        }
                    },
                    cameraHandler
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session: ${e.message}", e)
            onCameraError("Failed to create capture session: ${e.message}")
        }
    }

    fun setZoom(zoom: Float) {
        val lens = currentLens ?: return
        val clampedZoom = zoom.coerceIn(lens.zoomRange.lower, lens.zoomRange.upper)
        currentZoom = clampedZoom
        previewRequestBuilder?.let { builder ->
            applyZoom(builder, clampedZoom, lens)
            updatePreview()
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder, zoom: Float, lens: LensInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        } else {
            val activeRect = lens.activeArraySize ?: return
            val cropW = (activeRect.width() / zoom).toInt()
            val cropH = (activeRect.height() / zoom).toInt()
            val cropX = activeRect.centerX() - cropW / 2
            val cropY = activeRect.centerY() - cropH / 2
            val cropRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
        }
    }

    fun setTorch(enabled: Boolean) {
        isTorchOn = enabled
        val lens = currentLens ?: return
        if (!lens.hasFlash) return

        previewRequestBuilder?.let { builder ->
            builder.set(CaptureRequest.FLASH_MODE, if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            updatePreview()
        }
    }

    fun setExposureCompensation(evSteps: Int) {
        val caps = halCapabilities ?: return
        val clamped = evSteps.coerceIn(caps.exposureCompensationRange.lower, caps.exposureCompensationRange.upper)
        previewRequestBuilder?.let { builder ->
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clamped)
            updatePreview()
        }
    }

    private fun updatePreview() {
        val session = captureSession ?: return
        val request = previewRequestBuilder?.build() ?: return
        try {
            session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    // Frame metadata updated
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set repeating request: ${e.message}")
        }
    }

    fun switchLens(newLens: LensInfo, previewWidth: Int, previewHeight: Int) {
        val current = currentLens
        // If switching between physical cameras of the same logical device, we can reconfigure smoothly
        closeCamera()
        val texture = surfaceTexture ?: return
        openCamera(newLens, texture, previewWidth, previewHeight)
    }

    fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera: ${e.message}")
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
        previewSurface?.release()
        previewSurface = null
        surfaceTexture = null
    }

    companion object {
        private const val TAG = "CameraDeviceManager"
    }
}
