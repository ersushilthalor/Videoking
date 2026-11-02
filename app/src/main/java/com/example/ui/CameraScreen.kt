package com.example.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.LensInfo
import com.example.camera.LensType
import com.example.recorder.VideoResolution
import java.util.Locale

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_screen_root")
    ) {
        // 1. Camera Viewfinder (Aspect Ratio 9:16)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val viewWidth = constraints.maxWidth
            val viewHeight = constraints.maxHeight

            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                viewModel.initializeRenderer(width, height)
                                viewModel.setPreviewSurface(surface, width, height)
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                viewModel.setPreviewSurface(surface, width, height)
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                viewModel.setPreviewSurface(null, 0, 0)
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                // Handled via GL frame listener
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(9f / 16f)
                    .testTag("camera_viewfinder_texture_view")
            )

            // Split Screen Slider Overlay (if enabled)
            if (uiState.isSplitScreen) {
                SplitScreenSlider(
                    splitPosition = uiState.splitPosition,
                    onPositionChanged = { viewModel.setSplitPosition(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Top Toolbar
        TopToolbar(
            uiState = uiState,
            onToggleTorch = { viewModel.toggleTorch() },
            onToggleSplit = { viewModel.toggleSplitScreen() },
            onToggleDiagnostics = { viewModel.toggleDiagnostics() },
            onOpenSettings = { viewModel.toggleSettings() },
            onCycleResolution = {
                val next = when (uiState.selectedResolution) {
                    VideoResolution.RES_720P -> VideoResolution.RES_1080P
                    VideoResolution.RES_1080P -> VideoResolution.RES_2K
                    VideoResolution.RES_2K -> VideoResolution.RES_4K
                    VideoResolution.RES_4K -> VideoResolution.RES_720P
                }
                viewModel.setResolution(next)
            },
            onCycleFps = {
                val nextFps = if (uiState.selectedFps == 30) 60 else 30
                viewModel.setFps(nextFps)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 3. Viewfinder Metadata Badge (Lens & Profile Info)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 64.dp, start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            uiState.activeLens?.let { lens ->
                Box(
                    modifier = Modifier
                        .background(Color(0x99111318), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = lens.lensDescription,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(Color(0xCCFFD54F), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = uiState.activeProfile.title.uppercase(),
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 4. Live Diagnostics Overlay (if enabled)
        if (uiState.isDiagnosticsOpen) {
            DiagnosticsOverlay(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp)
                    .width(260.dp)
            )
        }

        // 5. Bottom HUD Controls
        BottomHud(
            uiState = uiState,
            onSelectLens = { viewModel.switchLens(it) },
            onZoomChanged = { viewModel.setZoom(it) },
            onToggleRecord = { viewModel.toggleRecording() },
            onPauseResume = { viewModel.pauseResumeRecording() },
            onOpenProfilePicker = { viewModel.toggleSettings() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )

        // 6. Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp)
        )

        // 7. Settings Modal Bottom Sheet
        if (uiState.isSettingsOpen) {
            SettingsSheet(
                uiState = uiState,
                onDismiss = { viewModel.toggleSettings() },
                onSelectProfile = { viewModel.setProfile(it) },
                onUpdateParam = { viewModel.updateCustomParams(it) },
                onSelectResolution = { viewModel.setResolution(it) },
                onSelectFps = { viewModel.setFps(it) },
                onSelectCodec = { viewModel.setCodec(it) },
                onToggleClipping = { viewModel.toggleClippingOverlay() },
                onToggleSplitScreen = { viewModel.toggleSplitScreen() }
            )
        }
    }
}

@Composable
private fun TopToolbar(
    uiState: CameraUiState,
    onToggleTorch: () -> Unit,
    onToggleSplit: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleResolution: () -> Unit,
    onCycleFps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x88000000), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Resolution & FPS Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Resolution pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x55FFFFFF))
                    .clickable { onCycleResolution() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("resolution_picker_badge")
            ) {
                Text(
                    text = uiState.selectedResolution.label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // FPS pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x55FFFFFF))
                    .clickable { onCycleFps() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("fps_picker_badge")
            ) {
                Text(
                    text = "${uiState.selectedFps}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Action Icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Torch
            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("torch_toggle_button")
            ) {
                Icon(
                    imageVector = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Torch",
                    tint = if (uiState.isTorchOn) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Split Screen A/B Comparison
            IconButton(
                onClick = onToggleSplit,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("split_screen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Compare,
                    contentDescription = "Split Screen Comparison",
                    tint = if (uiState.isSplitScreen) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Diagnostics & Histogram
            IconButton(
                onClick = onToggleDiagnostics,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Diagnostics & Waveform",
                    tint = if (uiState.isDiagnosticsOpen) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomHud(
    uiState: CameraUiState,
    onSelectLens: (LensInfo) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onToggleRecord: () -> Unit,
    onPauseResume: () -> Unit,
    onOpenProfilePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Recording Status & Timer Indicator (if recording)
        AnimatedVisibility(
            visible = uiState.isRecording,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            RecordingTimerPill(
                durationSec = uiState.recordingDurationSec,
                isPaused = uiState.isPaused,
                resolution = uiState.selectedResolution
            )
        }

        // Lens Selection Pills (.5x, 1x, 3x, Macro, Front)
        if (uiState.availableLenses.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .background(Color(0x77000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.availableLenses.take(5).forEach { lens ->
                    val isSelected = uiState.activeLens?.cameraId == lens.cameraId &&
                            uiState.activeLens?.physicalId == lens.physicalId
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFFFFD54F) else Color.Transparent)
                            .clickable { onSelectLens(lens) }
                            .testTag("lens_pill_${lens.lensType.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lens.lensType.badge,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Smooth Zoom Slider
        uiState.activeLens?.let { lens ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(Color(0x55000000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f×", uiState.zoomLevel),
                    color = Color(0xFFFFD54F),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )
                Slider(
                    value = uiState.zoomLevel,
                    onValueChange = onZoomChanged,
                    valueRange = lens.zoomRange.lower..lens.zoomRange.upper.coerceAtMost(10.0f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD54F),
                        activeTrackColor = Color(0xFFFFD54F),
                        inactiveTrackColor = Color(0x66FFFFFF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("zoom_slider")
                )
            }
        }

        // Shutter & Recording Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Pause/Resume button if recording, else Quick Profile button
            if (uiState.isRecording) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x88222630), CircleShape)
                        .border(1.dp, Color(0x44FFFFFF), CircleShape)
                        .clickable { onPauseResume() }
                        .testTag("pause_resume_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (uiState.isPaused) "Resume Recording" else "Pause Recording",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x88222630), CircleShape)
                        .border(1.dp, Color(0x44FFFFFF), CircleShape)
                        .clickable { onOpenProfilePicker() }
                        .testTag("profile_quick_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Profile Presets",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Center: Big Shutter Recording Button
            ShutterButton(
                isRecording = uiState.isRecording,
                isPaused = uiState.isPaused,
                onClick = onToggleRecord
            )

            // Right: Spacer or Placeholder to balance row
            Box(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun ShutterButton(
    isRecording: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording && !isPaused) 1.12f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("recording_button"),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            // Red rounded square when recording
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(pulseScale)
                    .background(Color(0xFFFF3B30), RoundedCornerShape(6.dp))
            )
        } else {
            // Full red circle when ready to record video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF3B30), CircleShape)
            )
        }
    }
}

@Composable
private fun RecordingTimerPill(
    durationSec: Long,
    isPaused: Boolean,
    resolution: VideoResolution
) {
    val hours = durationSec / 3600
    val minutes = (durationSec % 3600) / 60
    val seconds = durationSec % 60
    val formattedTime = if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    Row(
        modifier = Modifier
            .background(Color(0xCC000000), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x33FF3B30), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Blinking red recording dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (isPaused) Color(0xFFFFCA28) else Color(0xFFFF3B30), CircleShape)
        )
        Text(
            text = if (isPaused) "PAUSED $formattedTime" else "REC $formattedTime",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "• ${resolution.label}",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
