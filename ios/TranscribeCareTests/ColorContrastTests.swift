import XCTest
import Foundation

/// Feature: monorepo-native-apps
/// Property 6: Color Contrast Compliance
///
/// **Validates: Requirements 8.3, 8.4**
///
/// Enumerates all text/background color pairs from the iOS color system,
/// computes WCAG 2.0 contrast ratio, and verifies ≥ 4.5:1.
final class ColorContrastTests: XCTestCase {

    // MARK: - Color Definitions

    /// Represents an RGB color with components in 0-255 range.
    struct RGBColor {
        let name: String
        let red: Int
        let green: Int
        let blue: Int

        var hex: String {
            String(format: "#%02X%02X%02X", red, green, blue)
        }
    }

    /// Represents a text/background color pair to test for contrast compliance.
    struct ColorPair {
        let description: String
        let textColor: RGBColor
        let backgroundColor: RGBColor
    }

    // iOS color system colors from Assets.xcassets
    static let primary = RGBColor(name: "Primary", red: 4, green: 22, blue: 39)
    static let secondary = RGBColor(name: "Secondary", red: 148, green: 74, blue: 0)
    static let background = RGBColor(name: "Background", red: 251, green: 249, blue: 250)
    static let white = RGBColor(name: "White", red: 255, green: 255, blue: 255)
    static let errorRed = RGBColor(name: "Error Red", red: 179, green: 38, blue: 30)

    /// All text/background color pairs used in the app.
    static let colorPairs: [ColorPair] = [
        ColorPair(
            description: "Primary text on Background",
            textColor: primary,
            backgroundColor: background
        ),
        ColorPair(
            description: "Secondary text on Background",
            textColor: secondary,
            backgroundColor: background
        ),
        ColorPair(
            description: "White text on Primary",
            textColor: white,
            backgroundColor: primary
        ),
        ColorPair(
            description: "White text on Secondary",
            textColor: white,
            backgroundColor: secondary
        ),
        ColorPair(
            description: "Primary text on White",
            textColor: primary,
            backgroundColor: white
        ),
        ColorPair(
            description: "White text on Error Red",
            textColor: white,
            backgroundColor: errorRed
        )
    ]

    // MARK: - WCAG Contrast Ratio Computation

    /// Linearize an sRGB channel value (0-255) to linear RGB.
    /// Per WCAG 2.0: if sRGB <= 0.03928 then linear = sRGB / 12.92
    /// else linear = ((sRGB + 0.055) / 1.055) ^ 2.4
    private func linearize(_ channel: Int) -> Double {
        let srgb = Double(channel) / 255.0
        if srgb <= 0.03928 {
            return srgb / 12.92
        } else {
            return pow((srgb + 0.055) / 1.055, 2.4)
        }
    }

    /// Compute relative luminance per WCAG 2.0.
    /// L = 0.2126 * R + 0.7152 * G + 0.0722 * B
    private func relativeLuminance(_ color: RGBColor) -> Double {
        let r = linearize(color.red)
        let g = linearize(color.green)
        let b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /// Compute WCAG 2.0 contrast ratio.
    /// Ratio = (L1 + 0.05) / (L2 + 0.05) where L1 is the lighter luminance.
    private func contrastRatio(_ color1: RGBColor, _ color2: RGBColor) -> Double {
        let l1 = relativeLuminance(color1)
        let l2 = relativeLuminance(color2)
        let lighter = max(l1, l2)
        let darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // MARK: - Property Test

    /// Property 6: Color Contrast Compliance
    /// All text/background color pairs SHALL have a WCAG contrast ratio ≥ 4.5:1.
    func testColorContrastCompliance() {
        let minimumRatio = 4.5

        for pair in Self.colorPairs {
            let ratio = contrastRatio(pair.textColor, pair.backgroundColor)

            XCTAssertGreaterThanOrEqual(
                ratio,
                minimumRatio,
                """
                WCAG contrast ratio violation:
                  Pair: \(pair.description)
                  Text: \(pair.textColor.name) (\(pair.textColor.hex))
                  Background: \(pair.backgroundColor.name) (\(pair.backgroundColor.hex))
                  Computed ratio: \(String(format: "%.2f", ratio)):1
                  Required minimum: \(minimumRatio):1
                """
            )
        }
    }

    /// Verify each individual color pair reports its ratio for documentation purposes.
    func testColorContrastRatiosAreDocumented() {
        for pair in Self.colorPairs {
            let ratio = contrastRatio(pair.textColor, pair.backgroundColor)
            // Log the ratio for each pair (visible in test output)
            print("[\(pair.description)] Contrast ratio: \(String(format: "%.2f", ratio)):1")
            // All pairs must meet the minimum
            XCTAssertGreaterThanOrEqual(ratio, 4.5)
        }
    }
}
