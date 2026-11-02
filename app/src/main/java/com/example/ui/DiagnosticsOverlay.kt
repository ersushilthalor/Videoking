package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiagnosticsOverlay(
    uiState: CameraUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("diagnostics_overlay")
            .background(Color(0xCC111318), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Engine & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPU PIPELINE ENGINE",
                    color = Color(0xFFFFD54F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${uiState.selectedResolution.label} @ ${uiState.selectedFps}fps",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "FPS", value = String.format("%.1f", uiState.currentFps))
                MetricItem(label = "GPU TIME", value = String.format("%.2f ms", uiState.gpuRenderTimeMs))
                MetricItem(label = "DROPS", value = "${uiState.droppedFrames}")
                MetricItem(label = "PROFILE", value = uiState.activeProfile.name.take(7))
            }

            // Real-time Luminance Histogram
            Column {
                Text(
                    text = "LUMINANCE HISTOGRAM (SHADOWS → HIGHLIGHTS)",
                    color = Color(0xFFAAAAAA),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                HistogramCanvas(
                    bins = uiState.histogram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )
            }

            // HAL ISP State
            Text(
                text = "HAL ISP BYPASS: ${uiState.halInfo}",
                color = Color(0xFF81C784),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun HistogramCanvas(
    bins: IntArray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val maxVal = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
        val barCount = bins.size
        val barWidth = size.width / barCount
        val barSpacing = 1.dp.toPx()

        for (i in 0 until barCount) {
            val count = bins[i]
            val normalizedHeight = (count.toFloat() / maxVal.toFloat()) * size.height
            val left = i * barWidth
            val top = size.height - normalizedHeight

            // Color gradient across luminance bins: deep blue (shadows) -> grey/green (midtones) -> amber/white (highlights)
            val barColor = when {
                i < 3 -> Color(0xFF42A5F5) // Shadows
                i in 3..12 -> Color(0xFFEEEEEE) // Midtones
                else -> Color(0xFFFFCA28) // Highlights
            }

            drawRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth - barSpacing, normalizedHeight)
            )
        }
    }
}
