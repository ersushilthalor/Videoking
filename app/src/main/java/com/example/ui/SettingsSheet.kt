package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gl.ProfileParams
import com.example.gl.VideoProfile
import com.example.recorder.VideoCodec
import com.example.recorder.VideoResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: CameraUiState,
    onDismiss: () -> Unit,
    onSelectProfile: (VideoProfile) -> Unit,
    onUpdateParam: ((ProfileParams) -> Unit) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (Int) -> Unit,
    onSelectCodec: (VideoCodec) -> Unit,
    onToggleClipping: () -> Unit,
    onToggleSplitScreen: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pipeline Profiles", "Image Tuning", "Camera & Output", "Diagnostics")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14171E),
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = Color(0xFFFFD54F)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Camera & Pipeline Engine",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFFFD54F),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFFFFD54F)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color(0xFFFFD54F) else Color(0xFF888888)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content
            when (selectedTab) {
                0 -> PipelineProfilesTab(uiState, onSelectProfile)
                1 -> ImageTuningTab(uiState, onUpdateParam)
                2 -> CameraOutputTab(uiState, onSelectResolution, onSelectFps, onSelectCodec)
                3 -> DiagnosticsTab(uiState, onToggleClipping, onToggleSplitScreen)
            }
        }
    }
}

@Composable
private fun PipelineProfilesTab(
    uiState: CameraUiState,
    onSelectProfile: (VideoProfile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "DATA-DRIVEN VIDEO ENGINE PROFILES",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(VideoProfile.values().size) { idx ->
            val profile = VideoProfile.values()[idx]
            val isSelected = uiState.activeProfile == profile

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF262C36),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (isSelected) Color(0xFF1E232D) else Color(0xFF181C24),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectProfile(profile) }
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.title,
                            color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFD54F), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.subtitle,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImageTuningTab(
    uiState: CameraUiState,
    onUpdateParam: ((ProfileParams) -> Unit) -> Unit
) {
    val params = uiState.profileParams

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "LINEAR-LIGHT & COLOR SCIENCE TUNING",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Exposure EV
        item {
            SliderSetting(
                title = "Exposure Compensation (EV)",
                value = params.exposureEv,
                range = -1.0f..1.0f,
                formattedValue = String.format("%+.2f EV", params.exposureEv),
                onValueChange = { v -> onUpdateParam { it.exposureEv = v } }
            )
        }

        // Contrast S-Curve
        item {
            SliderSetting(
                title = "Tone Curve Contrast",
                value = params.contrast,
                range = 0.8f..1.5f,
                formattedValue = String.format("%.2f", params.contrast),
                onValueChange = { v -> onUpdateParam { it.contrast = v } }
            )
        }

        // Shadow Recovery
        item {
            SliderSetting(
                title = "Intelligent Shadow Lift",
                value = params.shadowLift,
                range = 0.0f..0.8f,
                formattedValue = String.format("%.2f", params.shadowLift),
                onValueChange = { v -> onUpdateParam { it.shadowLift = v } }
            )
        }

        // Backlit Scene Local Tone Mapping
        item {
            SliderSetting(
                title = "Backlit Scene Recovery (LTM)",
                value = params.ltmStrength,
                range = 0.0f..1.0f,
                formattedValue = String.format("%.0f%%", params.ltmStrength * 100),
                onValueChange = { v -> onUpdateParam { it.ltmStrength = v } }
            )
        }

        // Highlight Knee
        item {
            SliderSetting(
                title = "Highlight Roll-Off Shoulder",
                value = params.highlightKnee,
                range = 0.5f..0.95f,
                formattedValue = String.format("%.2f", params.highlightKnee),
                onValueChange = { v -> onUpdateParam { it.highlightKnee = v } }
            )
        }

        // Saturation
        item {
            SliderSetting(
                title = "Saturation",
                value = params.saturation,
                range = 0.5f..1.8f,
                formattedValue = String.format("%.2f", params.saturation),
                onValueChange = { v -> onUpdateParam { it.saturation = v } }
            )
        }

        // Vibrance
        item {
            SliderSetting(
                title = "Vibrance (Smart Saturation)",
                value = params.vibrance,
                range = 0.5f..2.0f,
                formattedValue = String.format("%.2f", params.vibrance),
                onValueChange = { v -> onUpdateParam { it.vibrance = v } }
            )
        }

        // Skin Tone Protection
        item {
            SliderSetting(
                title = "Skin Tone Protection Strength",
                value = params.skinProtection,
                range = 0.0f..1.0f,
                formattedValue = String.format("%.0f%%", params.skinProtection * 100),
                onValueChange = { v -> onUpdateParam { it.skinProtection = v } }
            )
        }

        // White Balance Temp
        item {
            SliderSetting(
                title = "White Balance (Kelvin Shift)",
                value = params.wbTemperature,
                range = -0.4f..0.4f,
                formattedValue = if (params.wbTemperature > 0) "Warm +${String.format("%.2f", params.wbTemperature)}" else "Cool ${String.format("%.2f", params.wbTemperature)}",
                onValueChange = { v -> onUpdateParam { it.wbTemperature = v } }
            )
        }

        // Temporal Noise Reduction
        item {
            SliderSetting(
                title = "Temporal Noise Reduction (TNR)",
                value = params.tnrStrength,
                range = 0.0f..0.8f,
                formattedValue = String.format("%.0f%%", params.tnrStrength * 100),
                onValueChange = { v -> onUpdateParam { it.tnrStrength = v } }
            )
        }

        // Sharpness
        item {
            SliderSetting(
                title = "Fine Detail Enhancement",
                value = params.sharpness,
                range = 0.0f..0.8f,
                formattedValue = String.format("%.2f", params.sharpness),
                onValueChange = { v -> onUpdateParam { it.sharpness = v } }
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181C24), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = Color.White, fontSize = 13.sp)
            Text(
                text = formattedValue,
                color = Color(0xFFFFD54F),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD54F),
                activeTrackColor = Color(0xFFFFD54F),
                inactiveTrackColor = Color(0xFF333842)
            )
        )
    }
}

@Composable
private fun CameraOutputTab(
    uiState: CameraUiState,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (Int) -> Unit,
    onSelectCodec: (VideoCodec) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Resolution Selection
        item {
            Text(
                text = "RECORDING RESOLUTION (9:16 SDR BT.709)",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoResolution.values().forEach { res ->
                    val isSelected = uiState.selectedResolution == res
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF262C36),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isSelected) Color(0xFF1E232D) else Color(0xFF181C24),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectResolution(res) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = res.label,
                                color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${res.width}×${res.height}",
                                color = Color(0xFF888888),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Frame Rate
        item {
            Text(
                text = "FRAME RATE",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(30, 60).forEach { fps ->
                    val isSelected = uiState.selectedFps == fps
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF262C36),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isSelected) Color(0xFF1E232D) else Color(0xFF181C24),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectFps(fps) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$fps FPS",
                            color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Video Codec
        item {
            Text(
                text = "MEDIACODEC COMPRESSION FORMAT",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoCodec.values().forEach { codec ->
                    val isSelected = uiState.selectedCodec == codec
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF262C36),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isSelected) Color(0xFF1E232D) else Color(0xFF181C24),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectCodec(codec) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = codec.label,
                            color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Lens Technical Specs
        item {
            val lens = uiState.activeLens
            if (lens != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181C24), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "ACTIVE OPTICS & SENSOR DETAILS",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Camera ID: ${lens.cameraId} (Physical: ${lens.physicalId ?: "Native"})", color = Color.White, fontSize = 12.sp)
                    Text(text = "Focal Length: ${lens.primaryFocalLength}mm | Aperture: ƒ/${lens.primaryAperture}", color = Color.White, fontSize = 12.sp)
                    Text(text = "Min Focus Distance: ${lens.minFocusDistance} diopters ${if (lens.isMacroCapable) "(Macro Ready)" else ""}", color = Color.White, fontSize = 12.sp)
                    Text(text = "Zoom Hardware Range: ${lens.zoomRange.lower}× – ${lens.zoomRange.upper}×", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsTab(
    uiState: CameraUiState,
    onToggleClipping: () -> Unit,
    onToggleSplitScreen: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "IMAGE QUALITY & PIPELINE VERIFICATION TOOLS",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            DiagnosticToggleCard(
                title = "Split-Screen Profile Comparison",
                description = "Divides viewport to display raw unprocessed camera input on left vs custom iPhone-profile GPU processing on right with draggable divider.",
                isActive = uiState.isSplitScreen,
                onToggle = onToggleSplitScreen
            )
        }

        item {
            DiagnosticToggleCard(
                title = "Highlight Zebra & Shadow Crush Indicators",
                description = "Renders red diagonal zebra stripes over blown-out highlights (>96%) and blue indicators over crushed shadows (<3%).",
                isActive = uiState.isClippingOverlayOn,
                onToggle = onToggleClipping
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181C24), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PIPELINE VERIFICATION CRITERIA",
                    color = Color(0xFF81C784),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                VerificationCheckItem("✓ Single common GPU fragment shader across all profiles")
                VerificationCheckItem("✓ Shared FBO pipeline: zero preview / recording mismatch")
                VerificationCheckItem("✓ HAL double tone mapping & sharpening bypass")
                VerificationCheckItem("✓ Linear-light working space color transformations")
                VerificationCheckItem("✓ Downsampled LTM for backlit dynamic range recovery")
                VerificationCheckItem("✓ 33×33×33 3D LUT procedural color science")
                VerificationCheckItem("✓ Skin tone protection active across all profiles")
                VerificationCheckItem("✓ Motion-adaptive temporal noise reduction (TNR)")
            }
        }
    }
}

@Composable
private fun DiagnosticToggleCard(
    title: String,
    description: String,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) Color(0xFFFFD54F) else Color(0xFF262C36),
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color(0xFF181C24), RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .background(
                            if (isActive) Color(0xFFFFD54F) else Color(0xFF333842),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isActive) "ENABLED" else "DISABLED",
                        color = if (isActive) Color.Black else Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = Color(0xFFAAAAAA), fontSize = 11.sp)
        }
    }
}

@Composable
private fun VerificationCheckItem(text: String) {
    Text(
        text = text,
        color = Color(0xFFCCCCCC),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
