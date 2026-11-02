package com.example.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ProcessingEngine(
    private var videoWidth: Int = 1080,
    private var videoHeight: Int = 1920
) {
    // Shaders and Programs
    private var mainProgram: Int = 0
    private var downscaleProgram: Int = 0
    private var blitProgram: Int = 0

    // Vertex buffer for full-screen quad
    private val vertexBuffer: FloatBuffer = GlUtil.createFloatBuffer(GlUtil.FULL_SCREEN_VERTICES)

    // Camera OES Input Texture & SurfaceTexture
    var cameraTextureId: Int = 0
        private set
    var surfaceTexture: SurfaceTexture? = null
        private set

    // Textures & FBOs
    // 1. Processed Shared Output FBO (preview and encoder read from this texture)
    var processedTextureId: Int = 0
        private set
    var processedFboId: Int = 0
        private set

    // 2. Downscaled Luminance Map for Local Tone Mapping (~64x36)
    private val ltmWidth = 64
    private val ltmHeight = 36
    private var ltmTextureId: Int = 0
    private var ltmFboId: Int = 0

    // 3. Ping-Pong Textures for Motion-Adaptive Temporal Noise Reduction (TNR)
    private var tnrTextureA: Int = 0
    private var tnrTextureB: Int = 0
    private var tnrFboA: Int = 0
    private var tnrFboB: Int = 0
    private var useTnrTextureAAsSource = true
    private var isFirstFrame = true

    // 4. 3D LUT Texture (33x33x33)
    private var lutTextureId: Int = 0
    private var currentLutProfile: VideoProfile? = null

    // Uniform locations for Main Processing Program
    private var uTexMatrixLoc = 0
    private var uCameraTextureLoc = 0
    private var uLtmTextureLoc = 0
    private var uPrevFrameTextureLoc = 0
    private var uLutTextureLoc = 0
    private var uExposureEvLoc = 0
    private var uContrastLoc = 0
    private var uShadowLiftLoc = 0
    private var uLtmStrengthLoc = 0
    private var uHighlightKneeLoc = 0
    private var uHighlightMaxLoc = 0
    private var uSaturationLoc = 0
    private var uVibranceLoc = 0
    private var uWbTempLoc = 0
    private var uWbTintLoc = 0
    private var uSkinProtectionLoc = 0
    private var uColorGainsLoc = 0
    private var uTnrStrengthLoc = 0
    private var uSharpnessLoc = 0
    private var uTexelSizeLoc = 0
    private var uSplitScreenLoc = 0
    private var uSplitPositionLoc = 0
    private var uClippingOverlayLoc = 0
    private var uIsFirstFrameLoc = 0

    // Downscale program uniforms
    private var uDownscaleCameraTexLoc = 0
    private var uDownscaleTexMatrixLoc = 0

    // Blit program uniforms
    private var uBlitTextureLoc = 0

    // Transformation matrices
    val transformMatrix = FloatArray(16)

    // Current profile parameters
    var activeParams: ProfileParams = ProfileParams.createDefault(VideoProfile.IPHONE_NATURAL)
        private set

    // Diagnostics & comparison toggles
    var splitScreenEnabled: Boolean = false
    var splitPosition: Float = 0.5f // 0.0 to 1.0
    var clippingOverlayEnabled: Boolean = false

    // Real-time diagnostics
    var lastGpuTimeMs: Float = 0.0f
        private set
    var frameCounter: Long = 0
        private set
    var droppedFrames: Long = 0
        private set

    // Histogram buffer (downsampled 16-bin luminance)
    val histogramBins = IntArray(16)
    private val histogramPixelBuffer = ByteBuffer.allocateDirect(ltmWidth * ltmHeight * 4).order(ByteOrder.nativeOrder())

    init {
        Matrix.setIdentityM(transformMatrix, 0)
    }

    fun initialize() {
        createPrograms()
        createTexturesAndFBOs()
        updateLutTexture(activeParams.profile)
    }

    private fun createPrograms() {
        // Main processing shader: Linear-light pipeline + LTM + TNR + Color Science + 3D LUT + Gamma Output
        mainProgram = GlUtil.createProgram(MAIN_VERTEX_SHADER, MAIN_FRAGMENT_SHADER)
        uTexMatrixLoc = GLES30.glGetUniformLocation(mainProgram, "uTexMatrix")
        uCameraTextureLoc = GLES30.glGetUniformLocation(mainProgram, "uCameraTexture")
        uLtmTextureLoc = GLES30.glGetUniformLocation(mainProgram, "uLtmTexture")
        uPrevFrameTextureLoc = GLES30.glGetUniformLocation(mainProgram, "uPrevFrameTexture")
        uLutTextureLoc = GLES30.glGetUniformLocation(mainProgram, "uLutTexture")
        uExposureEvLoc = GLES30.glGetUniformLocation(mainProgram, "uExposureEv")
        uContrastLoc = GLES30.glGetUniformLocation(mainProgram, "uContrast")
        uShadowLiftLoc = GLES30.glGetUniformLocation(mainProgram, "uShadowLift")
        uLtmStrengthLoc = GLES30.glGetUniformLocation(mainProgram, "uLtmStrength")
        uHighlightKneeLoc = GLES30.glGetUniformLocation(mainProgram, "uHighlightKnee")
        uHighlightMaxLoc = GLES30.glGetUniformLocation(mainProgram, "uHighlightMax")
        uSaturationLoc = GLES30.glGetUniformLocation(mainProgram, "uSaturation")
        uVibranceLoc = GLES30.glGetUniformLocation(mainProgram, "uVibrance")
        uWbTempLoc = GLES30.glGetUniformLocation(mainProgram, "uWbTemp")
        uWbTintLoc = GLES30.glGetUniformLocation(mainProgram, "uWbTint")
        uSkinProtectionLoc = GLES30.glGetUniformLocation(mainProgram, "uSkinProtection")
        uColorGainsLoc = GLES30.glGetUniformLocation(mainProgram, "uColorGains")
        uTnrStrengthLoc = GLES30.glGetUniformLocation(mainProgram, "uTnrStrength")
        uSharpnessLoc = GLES30.glGetUniformLocation(mainProgram, "uSharpness")
        uTexelSizeLoc = GLES30.glGetUniformLocation(mainProgram, "uTexelSize")
        uSplitScreenLoc = GLES30.glGetUniformLocation(mainProgram, "uSplitScreen")
        uSplitPositionLoc = GLES30.glGetUniformLocation(mainProgram, "uSplitPosition")
        uClippingOverlayLoc = GLES30.glGetUniformLocation(mainProgram, "uClippingOverlay")
        uIsFirstFrameLoc = GLES30.glGetUniformLocation(mainProgram, "uIsFirstFrame")

        // Downscale shader for LTM luminance map
        downscaleProgram = GlUtil.createProgram(DOWNSCALE_VERTEX_SHADER, DOWNSCALE_FRAGMENT_SHADER)
        uDownscaleCameraTexLoc = GLES30.glGetUniformLocation(downscaleProgram, "uCameraTexture")
        uDownscaleTexMatrixLoc = GLES30.glGetUniformLocation(downscaleProgram, "uTexMatrix")

        // Blit shader (from processed FBO to target surface)
        blitProgram = GlUtil.createProgram(BLIT_VERTEX_SHADER, BLIT_FRAGMENT_SHADER)
        uBlitTextureLoc = GLES30.glGetUniformLocation(blitProgram, "uTexture")
    }

    private fun createTexturesAndFBOs() {
        // Camera OES texture
        cameraTextureId = GlUtil.createOESTexture()
        surfaceTexture = SurfaceTexture(cameraTextureId)

        // Processed output texture & FBO
        processedTextureId = GlUtil.createTexture2D(videoWidth, videoHeight)
        processedFboId = GlUtil.createFBO(processedTextureId)

        // LTM luminance map texture & FBO
        ltmTextureId = GlUtil.createTexture2D(ltmWidth, ltmHeight)
        ltmFboId = GlUtil.createFBO(ltmTextureId)

        // Ping-pong TNR textures & FBOs
        tnrTextureA = GlUtil.createTexture2D(videoWidth, videoHeight)
        tnrFboA = GlUtil.createFBO(tnrTextureA)
        tnrTextureB = GlUtil.createTexture2D(videoWidth, videoHeight)
        tnrFboB = GlUtil.createFBO(tnrTextureB)

        // 3D LUT Texture (33x33x33)
        val initialLut = LutGenerator.generateLutBuffer(VideoProfile.IPHONE_NATURAL)
        lutTextureId = GlUtil.createTexture3D(LutGenerator.LUT_SIZE, initialLut)
        currentLutProfile = VideoProfile.IPHONE_NATURAL
    }

    fun updateProfile(newParams: ProfileParams) {
        activeParams = newParams
        if (currentLutProfile != newParams.profile) {
            updateLutTexture(newParams.profile)
            currentLutProfile = newParams.profile
        }
    }

    private fun updateLutTexture(profile: VideoProfile) {
        val buffer = LutGenerator.generateLutBuffer(profile)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glTexSubImage3D(
            GLES30.GL_TEXTURE_3D, 0, 0, 0, 0,
            LutGenerator.LUT_SIZE, LutGenerator.LUT_SIZE, LutGenerator.LUT_SIZE,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    fun resize(width: Int, height: Int) {
        if (width == videoWidth && height == videoHeight) return
        videoWidth = width
        videoHeight = height

        // Reallocate processed and TNR textures
        GLES30.glDeleteTextures(3, intArrayOf(processedTextureId, tnrTextureA, tnrTextureB), 0)
        GLES30.glDeleteFramebuffers(3, intArrayOf(processedFboId, tnrFboA, tnrFboB), 0)

        processedTextureId = GlUtil.createTexture2D(videoWidth, videoHeight)
        processedFboId = GlUtil.createFBO(processedTextureId)

        tnrTextureA = GlUtil.createTexture2D(videoWidth, videoHeight)
        tnrFboA = GlUtil.createFBO(tnrTextureA)
        tnrTextureB = GlUtil.createTexture2D(videoWidth, videoHeight)
        tnrFboB = GlUtil.createFBO(tnrTextureB)

        isFirstFrame = true
    }

    fun resetTemporalHistory() {
        isFirstFrame = true
    }

    /**
     * Executes the custom GPU video pipeline on the latest Camera2 frame.
     * Renders the processed result into [processedFboId].
     */
    fun processFrame(): Long {
        val startTime = System.nanoTime()

        // 1. Update camera texture
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(transformMatrix)
        val timestamp = surfaceTexture?.timestamp ?: 0L

        // PASS 1: Generate Downscaled Luminance Map (64x36) for Local Tone Mapping
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ltmFboId)
        GLES30.glViewport(0, 0, ltmWidth, ltmHeight)
        GLES30.glUseProgram(downscaleProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glUniform1i(uDownscaleCameraTexLoc, 0)
        GLES30.glUniformMatrix4fv(uDownscaleTexMatrixLoc, 1, false, transformMatrix, 0)

        renderQuad(downscaleProgram)

        // Readback luminance map for lightweight CPU histogram calculation (only 64x36 = 2304 pixels)
        extractHistogram()

        // Determine ping-pong targets for Temporal Noise Reduction
        val prevTexture = if (useTnrTextureAAsSource) tnrTextureA else tnrTextureB
        val targetTnrFbo = if (useTnrTextureAAsSource) tnrFboB else tnrFboA

        // PASS 2: Main Linear-Light Video Engine Pass
        // Renders to both the TNR FBO (for temporal history) and Processed FBO
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetTnrFbo)
        GLES30.glViewport(0, 0, videoWidth, videoHeight)
        GLES30.glUseProgram(mainProgram)

        // Bind Textures
        // 0: Camera OES
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glUniform1i(uCameraTextureLoc, 0)

        // 1: Downscaled LTM luminance map
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ltmTextureId)
        GLES30.glUniform1i(uLtmTextureLoc, 1)

        // 2: Previous frame texture for TNR
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevTexture)
        GLES30.glUniform1i(uPrevFrameTextureLoc, 2)

        // 3: 3D LUT Texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glUniform1i(uLutTextureLoc, 3)

        // Upload Profile Parameters
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, transformMatrix, 0)
        GLES30.glUniform1f(uExposureEvLoc, activeParams.exposureEv)
        GLES30.glUniform1f(uContrastLoc, activeParams.contrast)
        GLES30.glUniform1f(uShadowLiftLoc, activeParams.shadowLift)
        GLES30.glUniform1f(uLtmStrengthLoc, activeParams.ltmStrength)
        GLES30.glUniform1f(uHighlightKneeLoc, activeParams.highlightKnee)
        GLES30.glUniform1f(uHighlightMaxLoc, activeParams.highlightMax)
        GLES30.glUniform1f(uSaturationLoc, activeParams.saturation)
        GLES30.glUniform1f(uVibranceLoc, activeParams.vibrance)
        GLES30.glUniform1f(uWbTempLoc, activeParams.wbTemperature)
        GLES30.glUniform1f(uWbTintLoc, activeParams.wbTint)
        GLES30.glUniform1f(uSkinProtectionLoc, activeParams.skinProtection)
        GLES30.glUniform3fv(uColorGainsLoc, 1, activeParams.colorGains, 0)
        GLES30.glUniform1f(uTnrStrengthLoc, activeParams.tnrStrength)
        GLES30.glUniform1f(uSharpnessLoc, activeParams.sharpness)
        GLES30.glUniform2f(uTexelSizeLoc, 1.0f / videoWidth, 1.0f / videoHeight)
        GLES30.glUniform1i(uSplitScreenLoc, if (splitScreenEnabled) 1 else 0)
        GLES30.glUniform1f(uSplitPositionLoc, splitPosition)
        GLES30.glUniform1i(uClippingOverlayLoc, if (clippingOverlayEnabled) 1 else 0)
        GLES30.glUniform1i(uIsFirstFrameLoc, if (isFirstFrame) 1 else 0)

        renderQuad(mainProgram)

        // PASS 3: Copy to Processed FBO (single shared texture for preview and recorder)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, targetTnrFbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, processedFboId)
        GLES30.glBlitFramebuffer(
            0, 0, videoWidth, videoHeight,
            0, 0, videoWidth, videoHeight,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
        )

        // Toggle ping-pong
        useTnrTextureAAsSource = !useTnrTextureAAsSource
        isFirstFrame = false

        // Measure GPU execution time
        val endTime = System.nanoTime()
        lastGpuTimeMs = (endTime - startTime) / 1_000_000.0f
        frameCounter++

        return timestamp
    }

    /**
     * Renders the processed frame from [processedTextureId] into whatever EGLSurface
     * is currently bound (Preview SurfaceView or MediaCodec InputSurface).
     */
    fun blitToCurrentSurface(targetWidth: Int, targetHeight: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, targetWidth, targetHeight)
        GLES30.glUseProgram(blitProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, processedTextureId)
        GLES30.glUniform1i(uBlitTextureLoc, 0)

        renderQuad(blitProgram)
    }

    private fun extractHistogram() {
        try {
            histogramPixelBuffer.position(0)
            GLES30.glReadPixels(0, 0, ltmWidth, ltmHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, histogramPixelBuffer)
            histogramBins.fill(0)
            val totalPixels = ltmWidth * ltmHeight
            for (i in 0 until totalPixels) {
                val r = histogramPixelBuffer.get(i * 4).toInt() and 0xFF
                val g = histogramPixelBuffer.get(i * 4 + 1).toInt() and 0xFF
                val b = histogramPixelBuffer.get(i * 4 + 2).toInt() and 0xFF
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt()
                val bin = (lum * 16 / 256).coerceIn(0, 15)
                histogramBins[bin]++
            }
        } catch (e: Exception) {
            // Non-critical diagnostic read
        }
    }

    private fun renderQuad(program: Int) {
        val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")

        GLES30.glEnableVertexAttribArray(posLoc)
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

        if (texLoc >= 0) {
            GLES30.glEnableVertexAttribArray(texLoc)
            vertexBuffer.position(2)
            GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        }

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        if (texLoc >= 0) GLES30.glDisableVertexAttribArray(texLoc)
    }

    fun release() {
        GLES30.glDeleteTextures(5, intArrayOf(cameraTextureId, processedTextureId, ltmTextureId, tnrTextureA, tnrTextureB), 0)
        GLES30.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
        GLES30.glDeleteFramebuffers(4, intArrayOf(processedFboId, ltmFboId, tnrFboA, tnrFboB), 0)
        GLES30.glDeleteProgram(mainProgram)
        GLES30.glDeleteProgram(downscaleProgram)
        GLES30.glDeleteProgram(blitProgram)
        surfaceTexture?.release()
        surfaceTexture = null
    }

    companion object {
        private const val TAG = "ProcessingEngine"

        // ------------------ SHADERS ------------------

        private const val MAIN_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uTexMatrix;
out vec2 vTexCoord;
out vec2 vCameraCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoord;
    vCameraCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
"""

        private const val MAIN_FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
precision highp sampler3D;

in vec2 vTexCoord;
in vec2 vCameraCoord;

uniform samplerExternalOES uCameraTexture;
uniform sampler2D uLtmTexture;
uniform sampler2D uPrevFrameTexture;
uniform sampler3D uLutTexture;

// Profile Parameters
uniform float uExposureEv;
uniform float uContrast;
uniform float uShadowLift;
uniform float uLtmStrength;
uniform float uHighlightKnee;
uniform float uHighlightMax;
uniform float uSaturation;
uniform float uVibrance;
uniform float uWbTemp;
uniform float uWbTint;
uniform float uSkinProtection;
uniform vec3 uColorGains;
uniform float uTnrStrength;
uniform float uSharpness;
uniform vec2 uTexelSize;

// Diagnostics
uniform int uSplitScreen;
uniform float uSplitPosition;
uniform int uClippingOverlay;
uniform int uIsFirstFrame;

out vec4 fragColor;

// Convert sRGB / Rec.709 gamma to Linear-Light working space
vec3 sRgbToLinear(vec3 c) {
    c = max(c, vec3(0.0));
    return mix(
        c / 12.92,
        pow((c + 0.055) / 1.055, vec3(2.4)),
        step(0.04045, c)
    );
}

// Convert Linear-Light working space to SDR BT.709 Output Gamma
vec3 linearToSDR(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(
        c * 12.92,
        1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055,
        step(0.0031308, c)
    );
}

// RGB to HSV for hue and skin tone detection
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV back to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Smooth cinematic S-curve for contrast
vec3 applySCurve(vec3 c, float contrast) {
    vec3 toe = pow(c, vec3(contrast));
    vec3 shoulder = 1.0 - pow(1.0 - c, vec3(contrast));
    return mix(toe, shoulder, smoothstep(0.3, 0.7, c));
}

void main() {
    // Check split-screen: left side raw camera passthrough, right side custom engine
    if (uSplitScreen == 1 && vTexCoord.x < uSplitPosition) {
        // Subtle divider line
        if (abs(vTexCoord.x - uSplitPosition) < 0.002) {
            fragColor = vec4(1.0, 0.84, 0.0, 1.0); // Gold divider
            return;
        }
        vec4 rawCam = texture(uCameraTexture, vCameraCoord);
        fragColor = rawCam;
        return;
    }

    // STAGE 1 & 2: Camera Input and Linear-Light Working Space Conversion
    vec4 rawColor = texture(uCameraTexture, vCameraCoord);
    vec3 linearRgb = sRgbToLinear(rawColor.rgb);

    // STAGE 3: White Balance & Tint in Linear Space
    vec3 wbGains = vec3(
        1.0 + uWbTemp * 0.35 + uWbTint * 0.15,
        1.0 - uWbTint * 0.25,
        1.0 - uWbTemp * 0.35 - uWbTint * 0.10
    );
    linearRgb *= wbGains;

    // STAGE 4: Exposure Adjustment (Linear EV Scaling)
    linearRgb *= exp2(uExposureEv);

    // STAGE 5: Local Tone Mapping (LTM) for Backlit Dynamic Range Recovery
    float pixelLum = dot(linearRgb, vec3(0.2126, 0.7152, 0.0722));
    float localLum = texture(uLtmTexture, vTexCoord).r;

    // Intelligent shadow recovery: active when pixel is in shadows and surrounded by high contrast
    float shadowMask = clamp(1.0 - smoothstep(0.01, 0.35, pixelLum), 0.0, 1.0);
    float backlitAdapt = clamp(smoothstep(0.15, 0.85, localLum), 0.0, 1.0);
    // Lift shadows without lifting true black level (preserves rich blacks)
    float trueBlackGuard = smoothstep(0.002, 0.04, pixelLum);
    float dynamicLift = (uShadowLift + uLtmStrength * backlitAdapt * 0.7) * shadowMask * trueBlackGuard;
    linearRgb += linearRgb * dynamicLift;

    // STAGE 6: Smooth Highlight Roll-Off Shoulder (prevents harsh digital clipping in sky)
    float knee = uHighlightKnee;
    float maxVal = uHighlightMax;
    vec3 hlDelta = max(linearRgb - vec3(knee), vec3(0.0));
    vec3 compressedHl = knee + (maxVal - knee) * (hlDelta / (hlDelta + vec3(maxVal - knee + 0.001)));
    linearRgb = mix(linearRgb, compressedHl, step(vec3(knee), linearRgb));

    // STAGE 7: Global Tone Curve (S-Curve)
    linearRgb = clamp(linearRgb, 0.0, 1.0);
    linearRgb = applySCurve(linearRgb, uContrast);

    // STAGE 8: Selective Hue & Color Matrix
    linearRgb *= uColorGains;

    // STAGE 9: Vibrance & Saturation with Skin Tone Protection
    vec3 hsv = rgb2hsv(linearRgb);
    float hueDeg = hsv.x * 360.0;

    // Detect skin hue: around 10° to 42° in HSV space
    float skinWeight = smoothstep(8.0, 18.0, hueDeg) * (1.0 - smoothstep(38.0, 50.0, hueDeg));
    skinWeight *= smoothstep(0.12, 0.30, hsv.y); // Must have some saturation
    skinWeight *= smoothstep(0.10, 0.35, hsv.z); // Must have some luminance

    // Protect skin tones from oversaturation and unnatural shifts
    float effectiveSat = mix(uSaturation, 1.0 + (uSaturation - 1.0) * 0.25, skinWeight * uSkinProtection);
    float currentSat = hsv.y;
    // Vibrance: boost less-saturated colors more than already saturated colors
    float vibranceDelta = (1.0 - currentSat) * (uVibrance - 1.0) * 0.5;
    hsv.y = clamp(currentSat * effectiveSat + vibranceDelta * (1.0 - skinWeight * 0.8), 0.0, 1.0);

    linearRgb = hsv2rgb(hsv);

    // STAGE 10: 3D LUT Color Grading (33x33x33)
    vec3 lutColor = texture(uLutTexture, clamp(linearRgb, 0.0, 1.0)).rgb;
    linearRgb = mix(linearRgb, lutColor, 0.85);

    // STAGE 11: Subtle GPU Sharpening in Linear Space
    if (uSharpness > 0.05) {
        vec3 center = linearRgb;
        vec3 up    = sRgbToLinear(texture(uCameraTexture, vCameraCoord + vec2(0.0, uTexelSize.y)).rgb);
        vec3 down  = sRgbToLinear(texture(uCameraTexture, vCameraCoord - vec2(0.0, uTexelSize.y)).rgb);
        vec3 left  = sRgbToLinear(texture(uCameraTexture, vCameraCoord - vec2(uTexelSize.x, 0.0)).rgb);
        vec3 right = sRgbToLinear(texture(uCameraTexture, vCameraCoord + vec2(uTexelSize.x, 0.0)).rgb);
        vec3 laplacian = (up + down + left + right) * 0.25;
        linearRgb = clamp(center + (center - laplacian) * (uSharpness * 0.8), 0.0, 1.0);
    }

    // STAGE 12: Motion-Adaptive Temporal Noise Reduction (TNR)
    if (uIsFirstFrame == 0 && uTnrStrength > 0.05) {
        vec4 prevSample = texture(uPrevFrameTexture, vTexCoord);
        vec3 prevLinear = sRgbToLinear(prevSample.rgb);
        float motionDelta = length(linearRgb - prevLinear);
        // Motion sensitivity: high delta reduces blend to eliminate ghosting; low delta increases blend for clean shadows
        float motionFactor = clamp(1.0 - motionDelta * 12.0, 0.0, 1.0);
        float tnrBlend = uTnrStrength * 0.45 * motionFactor;
        linearRgb = mix(linearRgb, prevLinear, tnrBlend);
    }

    // STAGE 13: SDR BT.709 Output Gamma Encoding
    vec3 sdrOutput = linearToSDR(linearRgb);

    // OPTIONAL: Zebra & Shadow Clipping Diagnostic Overlay
    if (uClippingOverlay == 1) {
        float outLum = dot(sdrOutput, vec3(0.2126, 0.7152, 0.0722));
        // Highlight clipping zebra (> 96%)
        if (outLum > 0.96) {
            float stripe = mod(gl_FragCoord.x + gl_FragCoord.y, 16.0);
            if (stripe < 8.0) {
                sdrOutput = vec3(1.0, 0.1, 0.1); // Red zebra stripe
            }
        }
        // Shadow underexposure clipping (< 3%)
        else if (outLum < 0.03) {
            sdrOutput = vec3(0.1, 0.3, 1.0); // Blue shadow crush indicator
        }
    }

    fragColor = vec4(sdrOutput, 1.0);
}
"""

        private const val DOWNSCALE_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uTexMatrix;
out vec2 vCameraCoord;

void main() {
    gl_Position = aPosition;
    vCameraCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
"""

        private const val DOWNSCALE_FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

in vec2 vCameraCoord;
uniform samplerExternalOES uCameraTexture;
out vec4 fragColor;

void main() {
    vec4 c = texture(uCameraTexture, vCameraCoord);
    // Linear luminance approximation
    float lum = dot(pow(c.rgb, vec3(2.2)), vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(lum, lum, lum, 1.0);
}
"""

        private const val BLIT_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoord;
}
"""

        private const val BLIT_FRAGMENT_SHADER = """#version 300 es
precision mediump float;
in vec2 vTexCoord;
uniform sampler2D uTexture;
out vec4 fragColor;

void main() {
    fragColor = texture(uTexture, vTexCoord);
}
"""
    }
}
