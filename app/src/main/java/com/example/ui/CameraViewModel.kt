package com.example.ui

import android.app.Application
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraDeviceManager
import com.example.camera.CameraDiscovery
import com.example.camera.LensInfo
import com.example.camera.LensType
import com.example.gl.CameraPreviewRenderer
import com.example.gl.ProfileParams
import com.example.gl.VideoProfile
import com.example.recorder.EncoderConfig
import com.example.recorder.VideoCodec
import com.example.recorder.VideoRecorder
import com.example.recorder.VideoResolution
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CameraUiState(
    val availableLenses: List<LensInfo> = emptyList(),
    val activeLens: LensInfo? = null,
    val zoomLevel: Float = 1.0f,
    val isTorchOn: Boolean = false,
    val selectedResolution: VideoResolution = VideoResolution.RES_1080P,
    val selectedFps: Int = 30,
    val selectedCodec: VideoCodec = VideoCodec.H264,
    val activeProfile: VideoProfile = VideoProfile.IPHONE_NATURAL,
    val profileParams: ProfileParams = ProfileParams.createDefault(VideoProfile.IPHONE_NATURAL),
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDurationSec: Long = 0L,
    val lastRecordedUri: Uri? = null,
    val isSplitScreen: Boolean = false,
    val splitPosition: Float = 0.5f,
    val isClippingOverlayOn: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isDiagnosticsOpen: Boolean = false,
    // Live Diagnostics
    val currentFps: Float = 0.0f,
    val gpuRenderTimeMs: Float = 0.0f,
    val droppedFrames: Long = 0L,
    val histogram: IntArray = IntArray(16),
    val halInfo: String = "",
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val cameraDiscovery = CameraDiscovery(application)
    private var cameraManager: CameraDeviceManager? = null
    private var previewRenderer: CameraPreviewRenderer? = null
    private var videoRecorder: VideoRecorder? = null

    private var timerJob: Job? = null
    private var recordingStartTime = 0L
    private var accumulatedPauseTime = 0L
    private var lastPauseTime = 0L

    init {
        loadLenses()
    }

    private fun loadLenses() {
        val lenses = cameraDiscovery.discoverAvailableLenses()
        val defaultLens = lenses.firstOrNull { it.lensType == LensType.WIDE }
            ?: lenses.firstOrNull { it.lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
            ?: lenses.firstOrNull()

        _uiState.update {
            it.copy(
                availableLenses = lenses,
                activeLens = defaultLens
            )
        }
    }

    fun initializeRenderer(previewWidth: Int, previewHeight: Int) {
        if (previewRenderer != null) return

        val resolution = _uiState.value.selectedResolution
        previewRenderer = CameraPreviewRenderer(
            videoWidth = resolution.width,
            videoHeight = resolution.height,
            onInitialized = { surfaceTexture ->
                openCamera(surfaceTexture)
            },
            onFrameStats = { fps, gpuMs, drops, hist ->
                _uiState.update {
                    it.copy(
                        currentFps = fps,
                        gpuRenderTimeMs = gpuMs,
                        droppedFrames = drops,
                        histogram = hist.clone()
                    )
                }
            }
        )
    }

    private fun openCamera(surfaceTexture: SurfaceTexture) {
        val activeLens = _uiState.value.activeLens ?: return
        val resolution = _uiState.value.selectedResolution

        cameraManager = CameraDeviceManager(getApplication()) { error ->
            _uiState.update { it.copy(errorMessage = error) }
        }

        cameraManager?.openCamera(
            lens = activeLens,
            texture = surfaceTexture,
            previewWidth = resolution.width,
            previewHeight = resolution.height
        )

        updateHalInfo()
    }

    private fun updateHalInfo() {
        val hal = cameraManager?.halCapabilities
        val halText = if (hal != null) {
            "ToneMap: ${hal.toneMapMode ?: "Default"} | Edge: ${hal.edgeMode ?: "Default"} | NR: ${hal.noiseReductionMode ?: "Default"}"
        } else {
            "Custom GPU Engine Active"
        }
        _uiState.update { it.copy(halInfo = halText) }
    }

    fun setPreviewSurface(surface: Any?, width: Int, height: Int) {
        previewRenderer?.setPreviewSurface(surface, width, height)
    }

    fun switchLens(lens: LensInfo) {
        if (_uiState.value.isRecording) {
            _uiState.update { it.copy(infoMessage = "Lens switching disabled during active recording.") }
            return
        }
        _uiState.update { it.copy(activeLens = lens, zoomLevel = 1.0f) }
        val resolution = _uiState.value.selectedResolution
        cameraManager?.switchLens(lens, resolution.width, resolution.height)
        previewRenderer?.processingEngine?.resetTemporalHistory()
        updateHalInfo()
    }

    fun setZoom(zoom: Float) {
        cameraManager?.setZoom(zoom)
        _uiState.update { it.copy(zoomLevel = cameraManager?.currentZoom ?: zoom) }
    }

    fun toggleTorch() {
        val newState = !_uiState.value.isTorchOn
        cameraManager?.setTorch(newState)
        _uiState.update { it.copy(isTorchOn = newState) }
    }

    fun setResolution(res: VideoResolution) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(selectedResolution = res) }
        previewRenderer?.setResolution(res.width, res.height)

        val activeLens = _uiState.value.activeLens
        val surfaceTex = previewRenderer?.processingEngine?.surfaceTexture
        if (activeLens != null && surfaceTex != null) {
            cameraManager?.closeCamera()
            cameraManager?.openCamera(activeLens, surfaceTex, res.width, res.height)
        }
    }

    fun setFps(fps: Int) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(selectedFps = fps) }
    }

    fun setCodec(codec: VideoCodec) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(selectedCodec = codec) }
    }

    fun setProfile(profile: VideoProfile) {
        val newParams = ProfileParams.createDefault(profile)
        _uiState.update { it.copy(activeProfile = profile, profileParams = newParams) }
        previewRenderer?.setProfile(newParams)
    }

    fun updateCustomParams(update: (ProfileParams) -> Unit) {
        val current = _uiState.value.profileParams
        update(current)
        _uiState.update { it.copy(profileParams = current, activeProfile = VideoProfile.CUSTOM) }
        previewRenderer?.setProfile(current)
    }

    fun toggleSplitScreen() {
        val newState = !_uiState.value.isSplitScreen
        _uiState.update { it.copy(isSplitScreen = newState) }
        previewRenderer?.setSplitScreen(newState, _uiState.value.splitPosition)
    }

    fun setSplitPosition(pos: Float) {
        _uiState.update { it.copy(splitPosition = pos) }
        previewRenderer?.setSplitScreen(_uiState.value.isSplitScreen, pos)
    }

    fun toggleClippingOverlay() {
        val newState = !_uiState.value.isClippingOverlayOn
        _uiState.update { it.copy(isClippingOverlayOn = newState) }
        previewRenderer?.setClippingOverlay(newState)
    }

    fun toggleSettings() {
        _uiState.update { it.copy(isSettingsOpen = !it.isSettingsOpen) }
    }

    fun toggleDiagnostics() {
        _uiState.update { it.copy(isDiagnosticsOpen = !it.isDiagnosticsOpen) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val state = _uiState.value
        val config = EncoderConfig(
            resolution = state.selectedResolution,
            fps = state.selectedFps,
            codec = state.selectedCodec,
            bitrate = state.selectedResolution.defaultBitrate
        )

        val recorder = VideoRecorder(
            context = getApplication(),
            config = config,
            onRecordingStarted = {
                recordingStartTime = SystemClock.elapsedRealtime()
                accumulatedPauseTime = 0L
                _uiState.update { it.copy(isRecording = true, isPaused = false, recordingDurationSec = 0L) }
                startTimer()
            },
            onRecordingStopped = { uri, durationMs ->
                stopTimer()
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        isPaused = false,
                        recordingDurationSec = 0L,
                        lastRecordedUri = uri,
                        infoMessage = "Saved ${String.format("%.1f", durationMs / 1000f)}s video to Movies/ProCam"
                    )
                }
            },
            onError = { err ->
                stopTimer()
                _uiState.update { it.copy(isRecording = false, isPaused = false, errorMessage = err) }
            }
        )
        videoRecorder = recorder
        recorder.start()
        previewRenderer?.startRecording(recorder)
    }

    fun pauseResumeRecording() {
        val recorder = videoRecorder ?: return
        if (recorder.isPaused.get()) {
            recorder.resume()
            accumulatedPauseTime += SystemClock.elapsedRealtime() - lastPauseTime
            _uiState.update { it.copy(isPaused = false) }
        } else {
            recorder.pause()
            lastPauseTime = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    private fun stopRecording() {
        previewRenderer?.stopRecording()
        videoRecorder?.stop()
        videoRecorder = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.isPaused) {
                    val elapsed = SystemClock.elapsedRealtime() - recordingStartTime - accumulatedPauseTime
                    _uiState.update { it.copy(recordingDurationSec = (elapsed / 1000).coerceAtLeast(0L)) }
                }
                delay(500)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        stopRecording()
        cameraManager?.release()
        previewRenderer?.release()
    }
}
