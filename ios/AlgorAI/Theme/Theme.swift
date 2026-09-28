import SwiftUI
import UIKit

// Mirrors android/app/src/main/java/com/algora/app/core/ui/theme/ — Color.kt, Theme.kt,
// ModeAccent.kt and core/data/settings/AccentColor.kt. Keep the two platforms in sync.

extension Color {
    /// 0xRRGGBB.
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// Android's 0xAARRGGBB longs, as the content JSON carries them.
    init(argb: Int64) {
        let value = UInt64(bitPattern: argb)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: Double((value >> 24) & 0xFF) / 255
        )
    }
}

/// User-selectable accent. `gradientEnd` is the mock's accent2 role.
enum AccentColor: String, CaseIterable, Identifiable {
    case indigo, violet, sky, emerald, pink

    var id: String { rawValue }

    var label: String { rawValue.capitalized }

    var color: Color {
        switch self {
        case .indigo: Color(hex: 0x4F46E5)
        case .violet: Color(hex: 0x7C3AED)
        case .sky: Color(hex: 0x0EA5E9)
        case .emerald: Color(hex: 0x059669)
        case .pink: Color(hex: 0xDB2777)
        }
    }

    var gradientEnd: Color {
        switch self {
        case .indigo: Color(hex: 0x7C3AED)
        case .violet: Color(hex: 0xA78BFA)
        case .sky: Color(hex: 0x4F46E5)
        case .emerald: Color(hex: 0x34D399)
        case .pink: Color(hex: 0xF97316)
        }
    }

    var topbarStart: Color { color }

    var topbarEnd: Color { self == .indigo ? Color(hex: 0x6D28D9) : gradientEnd }
}

extension AppMode {
    /// The accent a mode wears while the accent setting is on Auto.
    var accent: AccentColor { self == .dsa ? .indigo : .pink }
}

enum Brand {
    static let indigo = Color(hex: 0x4F46E5)
    static let blue = Color(hex: 0x3B82F6)
    static let violet = Color(hex: 0x7C3AED)
}

enum CategoryAccents {
    static let purple = Color(hex: 0x8B5CF6)
    static let green = Color(hex: 0x10B981)
    static let amber = Color(hex: 0xF59E0B)
    static let pink = Color(hex: 0xEC4899)
    static let darkGreen = Color(hex: 0x16A34A)
    static let orange = Color(hex: 0xF97316)
    static let blue = Color(hex: 0x6366F1)
    static let lightBlue = Color(hex: 0x3B82F6)
}

enum SimColors {
    static let blue = Color(hex: 0x3B82F6)
    static let violet = Color(hex: 0x8B5CF6)
    static let red = Color(hex: 0xEF4444)
    static let amber = Color(hex: 0xF59E0B)
    static let grey = Color(hex: 0x94A3B8)
    /// "Done" in every lab.
    static let green = Color(hex: 0x22A06B)
    /// "The thing being looked at right now" in every lab.
    static let active = Color(hex: 0xF5C542)
    /// "The answer" in every lab.
    static let answer = Color(hex: 0x7C5CFF)
    static let idle = Color(hex: 0xCBD0DA)
    static let wall = Color(hex: 0x39414F)
    /// iOS tertiary fill: the neutral behind secondary buttons, tracks and value tokens.
    static let tint = Color(hex: 0x767680, opacity: 0.24)
}

enum Gradients {
    static let brand = [Color(hex: 0x6366F1), Color(hex: 0x7C3AED), Color(hex: 0xC026D3)]
    static let green = [Color(hex: 0x34D399), Color(hex: 0x059669)]
    static let blue = [Color(hex: 0x60A5FA), Color(hex: 0x2563EB)]
    static let amber = [Color(hex: 0xFBBF24), Color(hex: 0xF97316)]
    static let violet = [Color(hex: 0xC084FC), Color(hex: 0x7C3AED)]
    static let indigo = [Color(hex: 0x818CF8), Color(hex: 0x4F46E5)]
    static let pink = [Color(hex: 0xF472B6), Color(hex: 0xDB2777)]
    static let teal = [Color(hex: 0x5EEAD4), Color(hex: 0x0D9488)]
    static let orange = [Color(hex: 0xFDBA74), Color(hex: 0xEA580C)]
    static let featuredDsa = [Color(hex: 0x7C3AED), Color(hex: 0x4338CA)]
    static let featuredAi = [Color(hex: 0x4F46E5), Color(hex: 0x0EA5E9)]
}

/// The resolved colour scheme (Material roles on Android), injected through the environment.
struct Palette: Equatable {
    var dark: Bool
    var accent: AccentColor

    var primary: Color { accent.color }
    var tertiary: Color { accent.gradientEnd }
    var secondary: Color { Brand.blue }
    var background: Color { dark ? Color(hex: 0x161A22) : Color(hex: 0xF7F8FA) }
    var surface: Color { dark ? Color(hex: 0x1C212B) : .white }
    var onSurface: Color { dark ? .white : Color(hex: 0x161A22) }
    var onPrimary: Color { dark ? Color(hex: 0x161A22) : .white }
    var muted: Color { dark ? Color(hex: 0x9AA1B1) : Color(hex: 0x6B7280) }
    var outline: Color { dark ? Color(hex: 0x262B36) : Color(hex: 0xE8EAEF) }
    var outlineVariant: Color { dark ? Color(hex: 0x262B36) : Color(hex: 0xEEF0F4) }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette(dark: false, accent: .indigo)
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

/// Horizontal gutter every screen's scrolling body sits in.
let screenGutter: CGFloat = 18
/// Bottom inset for a scrolling body.
let screenBottomInset: CGFloat = 24
