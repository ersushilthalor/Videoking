package com.example.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object LutGenerator {
    const val LUT_SIZE = 33

    fun generateLutBuffer(profile: VideoProfile): ByteBuffer {
        val totalBytes = LUT_SIZE * LUT_SIZE * LUT_SIZE * 4
        val buffer = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())

        for (bIdx in 0 until LUT_SIZE) {
            val b = bIdx.toFloat() / (LUT_SIZE - 1).toFloat()
            for (gIdx in 0 until LUT_SIZE) {
                val g = gIdx.toFloat() / (LUT_SIZE - 1).toFloat()
                for (rIdx in 0 until LUT_SIZE) {
                    val r = rIdx.toFloat() / (LUT_SIZE - 1).toFloat()

                    val (outR, outG, outB) = transformColor(r, g, b, profile)

                    val byteR = (clamp01(outR) * 255f).toInt().toByte()
                    val byteG = (clamp01(outG) * 255f).toInt().toByte()
                    val byteB = (clamp01(outB) * 255f).toInt().toByte()
                    val byteA = 255.toByte()

                    buffer.put(byteR)
                    buffer.put(byteG)
                    buffer.put(byteB)
                    buffer.put(byteA)
                }
            }
        }
        buffer.position(0)
        return buffer
    }

    private fun transformColor(r: Float, g: Float, b: Float, profile: VideoProfile): Triple<Float, Float, Float> {
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b

        return when (profile) {
            VideoProfile.IPHONE_NATURAL -> {
                // Natural iPhone rendering:
                // Gentle film toe, smooth highlight compression, clean organic shadows
                val tr = applyiPhoneCurve(r)
                val tg = applyiPhoneCurve(g)
                val tb = applyiPhoneCurve(b)
                // Slight warm highlight roll-off and natural skin tone harmony
                val hlFactor = smoothstep(0.70f, 1.0f, lum)
                Triple(
                    tr * (1.0f + 0.015f * hlFactor),
                    tg * (1.0f + 0.005f * hlFactor),
                    tb * (1.0f - 0.012f * hlFactor)
                )
            }

            VideoProfile.SAMSUNG -> {
                // Samsung vivid tone:
                // Punchier contrast, vibrant cyan/blue skies and rich foliage greens
                val tr = applySCurve(r, 1.22f)
                val tg = applySCurve(g, 1.20f) * 1.04f
                val tb = applySCurve(b, 1.25f) * 1.06f
                Triple(tr, tg, tb)
            }

            VideoProfile.OPPO -> {
                // Oppo portrait brightness:
                // Elevated shadows and midtones, flattering warm undertones
                val tr = (r.pow(0.92f)) * 1.03f
                val tg = (g.pow(0.94f)) * 1.01f
                val tb = (b.pow(0.98f)) * 0.98f
                Triple(tr, tg, tb)
            }

            VideoProfile.VIVO -> {
                // Vivo cinematic tone:
                // Rich red-orange saturation, controlled highlight falloff
                val tr = applySCurve(r, 1.15f) * 1.06f
                val tg = applySCurve(g, 1.12f)
                val tb = applySCurve(b, 1.10f) * 0.97f
                Triple(tr, tg, tb)
            }

            VideoProfile.CUSTOM -> {
                // Clean baseline identity LUT
                Triple(r, g, b)
            }
        }
    }

    private fun applyiPhoneCurve(x: Float): Float {
        // Controlled, natural S-curve with soft toe and soft shoulder
        val toe = 0.08f * (1.0f - (1.0f - x).pow(2.0f))
        val mid = x.pow(0.97f)
        val shoulder = 1.0f - (1.0f - x).pow(1.10f)
        return clamp01(mid * 0.7f + toe * 0.15f + shoulder * 0.15f)
    }

    private fun applySCurve(x: Float, power: Float): Float {
        return if (x < 0.5f) {
            0.5f * (2.0f * x).pow(power)
        } else {
            1.0f - 0.5f * (2.0f * (1.0f - x)).pow(power)
        }
    }

    private fun clamp01(v: Float) = max(0f, min(1f, v))

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / (edge1 - edge0))
        return t * t * (3.0f - 2.0f * t)
    }
}
