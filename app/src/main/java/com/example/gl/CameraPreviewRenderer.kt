package com.example.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.recorder.VideoRecorder

class CameraPreviewRenderer(
    private var videoWidth: Int = 1080,
    private var videoHeight: Int = 1920,
    private val onInitialized: (SurfaceTexture) -> Unit,
    private val onFrameStats: (Float, Float, Long, IntArray) -> Unit // (fps, gpuMs, dropped, histogram)
) : SurfaceTexture.OnFrameAvailableListener {

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var eglCore: EglCore? = null
    private var previewEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var offscreenEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    var processingEngine: ProcessingEngine? = null
        private set

    // Preview surface target (TextureView or SurfaceView)
    private var previewSurface: Any? = null
    private var previewWidth = 1080
    private var previewHeight = 1920

    // Video recording target
    private var videoRecorder: VideoRecorder? = null
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // Frame timing & FPS tracking
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var renderedFrameCount = 0
    private var currentFps = 0.0f
    private var droppedFrames = 0L

    init {
        startRenderThread()
    }

    private fun startRenderThread() {
        val thread = HandlerThread("GLRenderThread").apply { start() }
        renderThread = thread
        renderHandler = Handler(thread.looper)
        renderHandler?.post {
            initGL()
        }
    }

    private fun initGL() {
        try {
            val core = EglCore()
            eglCore = core
            offscreenEglSurface = core.createOffscreenSurface(videoWidth, videoHeight)
            core.makeCurrent(offscreenEglSurface)

            val engine = ProcessingEngine(videoWidth, videoHeight)
            engine.initialize()
            processingEngine = engine

            val surfaceTex = engine.surfaceTexture
            if (surfaceTex != null) {
                surfaceTex.setOnFrameAvailableListener(this, renderHandler)
                onInitialized(surfaceTex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GL: ${e.message}", e)
        }
    }

    fun setPreviewSurface(surface: Any?, width: Int, height: Int) {
        renderHandler?.post {
            previewWidth = width
            previewHeight = height
            previewSurface = surface

            val core = eglCore ?: return@post
            if (previewEglSurface !== EGL14.EGL_NO_SURFACE) {
                core.destroySurface(previewEglSurface)
                previewEglSurface = EGL14.EGL_NO_SURFACE
            }

            if (surface != null) {
                try {
                    previewEglSurface = core.createWindowSurface(surface)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create preview EGLSurface: ${e.message}")
                }
            }
        }
    }

    fun setResolution(width: Int, height: Int) {
        renderHandler?.post {
            videoWidth = width
            videoHeight = height
            processingEngine?.resize(width, height)
        }
    }

    fun setProfile(params: ProfileParams) {
        renderHandler?.post {
            processingEngine?.updateProfile(params)
        }
    }

    fun setSplitScreen(enabled: Boolean, position: Float = 0.5f) {
        renderHandler?.post {
            processingEngine?.splitScreenEnabled = enabled
            processingEngine?.splitPosition = position
        }
    }

    fun setClippingOverlay(enabled: Boolean) {
        renderHandler?.post {
            processingEngine?.clippingOverlayEnabled = enabled
        }
    }

    fun startRecording(recorder: VideoRecorder) {
        renderHandler?.post {
            videoRecorder = recorder
            val inputSurface = recorder.inputSurface ?: return@post
            val core = eglCore ?: return@post

            try {
                if (encoderEglSurface !== EGL14.EGL_NO_SURFACE) {
                    core.destroySurface(encoderEglSurface)
                }
                encoderEglSurface = core.createWindowSurface(inputSurface)
                Log.i(TAG, "Encoder EGL surface bound successfully for shared rendering.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind encoder surface: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        renderHandler?.post {
            val core = eglCore
            if (core != null && encoderEglSurface !== EGL14.EGL_NO_SURFACE) {
                core.destroySurface(encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
            }
            videoRecorder = null
            Log.i(TAG, "Encoder EGL surface unbound.")
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        renderHandler?.post {
            drawFrame()
        }
    }

    private fun drawFrame() {
        val core = eglCore ?: return
        val engine = processingEngine ?: return

        // 1. Process camera frame into shared Processed FBO
        // Use offscreen surface or preview surface to bind context
        val activeContextSurface = if (previewEglSurface !== EGL14.EGL_NO_SURFACE) previewEglSurface else offscreenEglSurface
        core.makeCurrent(activeContextSurface)

        val timestampNs = engine.processFrame()

        // 2. Render processed texture to Preview Surface
        if (previewEglSurface !== EGL14.EGL_NO_SURFACE) {
            core.makeCurrent(previewEglSurface)
            engine.blitToCurrentSurface(previewWidth, previewHeight)
            core.swapBuffers(previewEglSurface)
        }

        // 3. Render exact same processed texture to MediaCodec Encoder Surface if recording!
        val recorder = videoRecorder
        if (recorder != null && recorder.isRecording.get() && encoderEglSurface !== EGL14.EGL_NO_SURFACE) {
            if (!recorder.isPaused.get()) {
                core.makeCurrent(encoderEglSurface)
                engine.blitToCurrentSurface(videoWidth, videoHeight)
                core.setPresentationTime(encoderEglSurface, timestampNs)
                core.swapBuffers(encoderEglSurface)
                recorder.onVideoFrameRendered(timestampNs)
            }
        }

        // 4. Update frame statistics & histogram
        renderedFrameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTimestamp >= 1000) {
            currentFps = (renderedFrameCount * 1000f) / (now - lastFpsTimestamp)
            renderedFrameCount = 0
            lastFpsTimestamp = now
            onFrameStats(currentFps, engine.lastGpuTimeMs, droppedFrames, engine.histogramBins)
        }
    }

    fun release() {
        renderHandler?.post {
            val core = eglCore
            if (core != null) {
                if (previewEglSurface !== EGL14.EGL_NO_SURFACE) core.destroySurface(previewEglSurface)
                if (encoderEglSurface !== EGL14.EGL_NO_SURFACE) core.destroySurface(encoderEglSurface)
                if (offscreenEglSurface !== EGL14.EGL_NO_SURFACE) core.destroySurface(offscreenEglSurface)
                processingEngine?.release()
                core.release()
            }
            eglCore = null
            processingEngine = null
        }
        renderThread?.quitSafely()
        try {
            renderThread?.join(1000)
            renderThread = null
            renderHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping render thread: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CameraPreviewRenderer"
    }
}
