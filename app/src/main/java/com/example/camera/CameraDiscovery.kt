package com.example.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Size

class CameraDiscovery(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun discoverAvailableLenses(): List<LensInfo> {
        val lenses = mutableListOf<LensInfo>()
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue

                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.25f)
                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf(1.8f)
                val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f
                val sensorPhysicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val sensorPixelSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                val zoomRange: Range<Float> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1.0f, 10.0f)
                } else {
                    val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 10.0f
                    Range(1.0f, maxZoom)
                }

                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: listOf(
                    Size(1920, 1080), Size(1280, 720)
                )
                val videoSizes = map?.getOutputSizes(android.media.MediaRecorder::class.java)?.toList() ?: previewSizes

                val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    chars.physicalCameraIds
                } else {
                    emptySet()
                }

                val isLogical = physicalIds.isNotEmpty()

                // Determine lens type based on facing, focal length, and focus distance
                val lensType = classifyLens(facing, focalLengths, minFocusDist)

                val mainLens = LensInfo(
                    cameraId = id,
                    physicalId = null,
                    lensFacing = facing,
                    lensType = lensType,
                    focalLengths = focalLengths,
                    apertures = apertures,
                    minFocusDistance = minFocusDist,
                    sensorSize = sensorPixelSize,
                    activeArraySize = activeArray,
                    zoomRange = zoomRange,
                    supportedPreviewSizes = previewSizes,
                    supportedVideoSizes = videoSizes,
                    hasFlash = flashAvailable,
                    isLogicalMultiCamera = isLogical,
                    physicalCameraIds = physicalIds
                )
                lenses.add(mainLens)

                // Also inspect accessible physical cameras if exposed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalIds.isNotEmpty()) {
                    for (physId in physicalIds) {
                        try {
                            val physChars = cameraManager.getCameraCharacteristics(physId)
                            val physFocal = physChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: focalLengths
                            val physApertures = physChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: apertures
                            val physMinFocus = physChars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: minFocusDist
                            val physType = classifyLens(facing, physFocal, physMinFocus)

                            // Only add if distinct type or distinct focal length
                            val alreadyHasSimilar = lenses.any { it.physicalId == physId || (it.lensType == physType && it.primaryFocalLength == physFocal.firstOrNull()) }
                            if (!alreadyHasSimilar) {
                                lenses.add(
                                    LensInfo(
                                        cameraId = id,
                                        physicalId = physId,
                                        lensFacing = facing,
                                        lensType = physType,
                                        focalLengths = physFocal,
                                        apertures = physApertures,
                                        minFocusDistance = physMinFocus,
                                        sensorSize = physChars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: sensorPixelSize,
                                        activeArraySize = physChars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: activeArray,
                                        zoomRange = zoomRange,
                                        supportedPreviewSizes = previewSizes,
                                        supportedVideoSizes = videoSizes,
                                        hasFlash = flashAvailable,
                                        isLogicalMultiCamera = false,
                                        physicalCameraIds = emptySet()
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error querying physical camera $physId: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating cameras: ${e.message}", e)
        }

        // Sort lenses: Rear UltraWide, Wide, Telephoto, Periscope, Macro, then Front
        return lenses.sortedWith(compareBy({ it.lensFacing }, { it.lensType.ordinal }, { it.primaryFocalLength }))
    }

    private fun classifyLens(facing: Int, focalLengths: FloatArray, minFocusDist: Float): LensType {
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return LensType.FRONT
        }
        val focal = focalLengths.firstOrNull() ?: 4.25f

        // Macro lens check: high minimum focus distance (e.g. 10 diopters = 10cm focus or closer)
        if (minFocusDist >= 10.0f && focal in 2.0f..6.0f) {
            return LensType.MACRO
        }

        return when {
            focal < 2.8f -> LensType.ULTRA_WIDE
            focal in 2.8f..5.5f -> LensType.WIDE
            focal in 5.6f..12.0f -> LensType.TELEPHOTO
            focal > 12.0f -> LensType.PERISCOPE
            else -> LensType.WIDE
        }
    }

    companion object {
        private const val TAG = "CameraDiscovery"
    }
}
