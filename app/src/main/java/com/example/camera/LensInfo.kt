package com.example.camera

import android.graphics.Rect
import android.util.Range
import android.util.Size

enum class LensType(val displayName: String, val badge: String) {
    ULTRA_WIDE("Ultra Wide", "0.5×"),
    WIDE("Wide / Main", "1×"),
    TELEPHOTO("Telephoto", "3×"),
    PERISCOPE("Periscope", "5×"),
    MACRO("Macro", "MACRO"),
    FRONT("Front Camera", "FRONT")
}

data class LensInfo(
    val cameraId: String,
    val physicalId: String? = null,
    val lensFacing: Int, // CameraCharacteristics.LENS_FACING_BACK, LENS_FACING_FRONT, etc.
    val lensType: LensType,
    val focalLengths: FloatArray,
    val apertures: FloatArray,
    val minFocusDistance: Float, // diopters: 1/meters. Higher (>10) = macro
    val sensorSize: Size?,
    val zoomRange: Range<Float>,
    val supportedPreviewSizes: List<Size>,
    val supportedVideoSizes: List<Size>,
    val activeArraySize: Rect?,
    val hasFlash: Boolean,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String>
) {
    val primaryFocalLength: Float
        get() = focalLengths.firstOrNull() ?: 4.25f

    val primaryAperture: Float
        get() = apertures.firstOrNull() ?: 1.8f

    val isMacroCapable: Boolean
        get() = minFocusDistance >= 10.0f || lensType == LensType.MACRO

    val lensDescription: String
        get() = buildString {
            append(lensType.displayName)
            append(" • ")
            append(String.format("%.1fmm", primaryFocalLength))
            append(" ƒ/")
            append(String.format("%.1f", primaryAperture))
        }
}
