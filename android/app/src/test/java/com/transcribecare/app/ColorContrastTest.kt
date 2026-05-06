package com.transcribecare.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlin.math.pow

/**
 * Feature: monorepo-native-apps
 * Property 6: Color Contrast Compliance
 *
 * Validates: Requirements 8.3, 8.4
 *
 * Enumerates all text/background color pairs from Theme.kt,
 * computes WCAG 2.0 contrast ratio, and verifies ≥ 4.5:1.
 */
class ColorContrastTest : FunSpec({

    // Color definitions from Color.kt (ARGB hex values)
    // Primary = 0xFF041627, OnPrimary = 0xFFFFFFFF
    // Secondary = 0xFF944A00, OnSecondary = 0xFFFFFFFF
    // Background = 0xFFFBF9FA, OnBackground = 0xFF041627
    // Surface = 0xFFFFFFFF, OnSurface = 0xFF041627
    // Error = 0xFFB3261E, OnError = 0xFFFFFFFF

    data class ColorPair(
        val name: String,
        val textColor: Long,
        val backgroundColor: Long
    )

    val colorPairs = listOf(
        ColorPair("Primary text (OnBackground) on Background", 0xFF041627, 0xFFFBF9FA),
        ColorPair("Secondary text on Background", 0xFF944A00, 0xFFFBF9FA),
        ColorPair("OnPrimary text on Primary", 0xFFFFFFFF, 0xFF041627),
        ColorPair("OnSecondary text on Secondary", 0xFFFFFFFF, 0xFF944A00),
        ColorPair("OnSurface text on Surface", 0xFF041627, 0xFFFFFFFF),
        ColorPair("OnError text on Error", 0xFFFFFFFF, 0xFFB3261E)
    )

    /**
     * Linearize an sRGB channel value (0-255) to linear RGB.
     * Per WCAG 2.0: if sRGB <= 0.03928 then linear = sRGB/12.92
     * else linear = ((sRGB + 0.055) / 1.055) ^ 2.4
     */
    fun linearize(channel: Int): Double {
        val srgb = channel / 255.0
        return if (srgb <= 0.03928) {
            srgb / 12.92
        } else {
            ((srgb + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * Compute relative luminance per WCAG 2.0.
     * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     */
    fun relativeLuminance(color: Long): Double {
        val r = ((color shr 16) and 0xFF).toInt()
        val g = ((color shr 8) and 0xFF).toInt()
        val b = (color and 0xFF).toInt()
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    }

    /**
     * Compute WCAG 2.0 contrast ratio.
     * Ratio = (L1 + 0.05) / (L2 + 0.05) where L1 >= L2
     */
    fun contrastRatio(color1: Long, color2: Long): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    test("Property 6: Color Contrast Compliance - all text/background pairs meet WCAG 4.5:1 ratio") {
        checkAll(Exhaustive.collection(colorPairs)) { pair ->
            val ratio = contrastRatio(pair.textColor, pair.backgroundColor)
            ratio shouldBeGreaterThanOrEqual 4.5
        }
    }
})
