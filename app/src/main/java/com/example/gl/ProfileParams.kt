package com.example.gl

enum class VideoProfile(val id: String, val title: String, val subtitle: String) {
    IPHONE_NATURAL("iphone_natural", "Natural / iPhone", "Lower controlled contrast, bright balanced exposure, rich natural skin tones"),
    SAMSUNG("samsung", "Samsung Vibrant", "Punchy S-curve, dynamic blues and greens, high vibrance"),
    OPPO("oppo", "Oppo Soft Bright", "Elevated shadows and midtones, warm portrait skin tones"),
    VIVO("vivo", "Vivo Cinematic", "Richer reds and oranges, strong highlight protection, deep dynamic range"),
    CUSTOM("custom", "Custom Profile", "User-customized processing parameters with live adjustments")
}

data class ProfileParams(
    val profile: VideoProfile = VideoProfile.IPHONE_NATURAL,
    // Exposure & Brightness (linear EV offset, e.g. -1.0 to +1.0)
    var exposureEv: Float = 0.12f,
    // Contrast S-curve steepness (0.8 to 1.6)
    var contrast: Float = 1.05f,
    // Shadow lift in linear working space (0.0 to 1.0)
    var shadowLift: Float = 0.36f,
    // Adaptive Local Tone Mapping strength for backlit dynamic range recovery (0.0 to 1.0)
    var ltmStrength: Float = 0.45f,
    // Highlight shoulder roll-off knee (0.5 to 0.95)
    var highlightKnee: Float = 0.78f,
    // Highlight shoulder maximum expansion (1.0 to 3.0)
    var highlightMax: Float = 1.85f,
    // Saturation multiplier (0.5 to 2.0)
    var saturation: Float = 1.07f,
    // Vibrance - selectively boost lower-saturated colors (0.0 to 2.0)
    var vibrance: Float = 1.12f,
    // White Balance temperature shift (-0.5 cool blue to +0.5 warm amber)
    var wbTemperature: Float = 0.0f,
    // White Balance tint shift (-0.5 green to +0.5 magenta)
    var wbTint: Float = 0.0f,
    // Skin Tone Protection strength (0.0 none to 1.0 full protection)
    var skinProtection: Float = 0.92f,
    // Selective hue gains: [Red, Green, Blue]
    var colorGains: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f),
    // Temporal Noise Reduction strength (0.0 to 1.0)
    var tnrStrength: Float = 0.40f,
    // Sharpening and detail enhancement (0.0 to 1.0)
    var sharpness: Float = 0.25f
) {
    companion object {
        fun createDefault(profile: VideoProfile): ProfileParams {
            return when (profile) {
                VideoProfile.IPHONE_NATURAL -> ProfileParams(
                    profile = VideoProfile.IPHONE_NATURAL,
                    exposureEv = 0.14f,
                    contrast = 1.05f,
                    shadowLift = 0.38f,
                    ltmStrength = 0.50f,
                    highlightKnee = 0.76f,
                    highlightMax = 1.90f,
                    saturation = 1.07f, // Natural 7% boost
                    vibrance = 1.10f,
                    wbTemperature = 0.0f,
                    wbTint = 0.0f,
                    skinProtection = 0.92f,
                    colorGains = floatArrayOf(1.02f, 1.03f, 1.01f),
                    tnrStrength = 0.45f,
                    sharpness = 0.22f
                )

                VideoProfile.SAMSUNG -> ProfileParams(
                    profile = VideoProfile.SAMSUNG,
                    exposureEv = 0.08f,
                    contrast = 1.24f, // Punchier S-curve
                    shadowLift = 0.26f,
                    ltmStrength = 0.38f,
                    highlightKnee = 0.82f,
                    highlightMax = 1.70f,
                    saturation = 1.16f,
                    vibrance = 1.30f, // Stronger blues and greens
                    wbTemperature = -0.04f, // Slightly crisper
                    wbTint = 0.02f,
                    skinProtection = 0.78f,
                    colorGains = floatArrayOf(0.98f, 1.15f, 1.22f), // Strong blues & greens
                    tnrStrength = 0.35f,
                    sharpness = 0.45f
                )

                VideoProfile.OPPO -> ProfileParams(
                    profile = VideoProfile.OPPO,
                    exposureEv = 0.22f, // Bright, flattering exposure
                    contrast = 0.98f, // Softer contrast
                    shadowLift = 0.48f, // Elevated shadows & midtones
                    ltmStrength = 0.55f,
                    highlightKnee = 0.80f,
                    highlightMax = 1.95f,
                    saturation = 1.05f,
                    vibrance = 1.12f,
                    wbTemperature = 0.12f, // Warm flattering skin rendering
                    wbTint = 0.05f,
                    skinProtection = 0.88f,
                    colorGains = floatArrayOf(1.08f, 1.02f, 0.96f),
                    tnrStrength = 0.50f,
                    sharpness = 0.18f
                )

                VideoProfile.VIVO -> ProfileParams(
                    profile = VideoProfile.VIVO,
                    exposureEv = 0.10f,
                    contrast = 1.15f,
                    shadowLift = 0.32f,
                    ltmStrength = 0.42f,
                    highlightKnee = 0.72f, // Strong highlight protection
                    highlightMax = 2.10f,
                    saturation = 1.12f,
                    vibrance = 1.20f,
                    wbTemperature = 0.04f,
                    wbTint = 0.02f,
                    skinProtection = 0.85f,
                    colorGains = floatArrayOf(1.22f, 1.02f, 0.98f), // Richer reds and oranges
                    tnrStrength = 0.40f,
                    sharpness = 0.30f
                )

                VideoProfile.CUSTOM -> ProfileParams(
                    profile = VideoProfile.CUSTOM,
                    exposureEv = 0.12f,
                    contrast = 1.08f,
                    shadowLift = 0.35f,
                    ltmStrength = 0.45f,
                    highlightKnee = 0.78f,
                    highlightMax = 1.85f,
                    saturation = 1.08f,
                    vibrance = 1.15f,
                    wbTemperature = 0.0f,
                    wbTint = 0.0f,
                    skinProtection = 0.90f,
                    colorGains = floatArrayOf(1.0f, 1.0f, 1.0f),
                    tnrStrength = 0.40f,
                    sharpness = 0.25f
                )
            }
        }
    }
}
