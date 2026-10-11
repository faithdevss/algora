import SwiftUI
import UIKit

// Mirrors android/app/src/main/java/com/algora/app/core/ui/theme/ — Color.kt, Theme.kt,
// and core/data/settings/AppTheme.kt. Keep the two platforms in sync.

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

/// The app themes from docs/Icon and Themes (standalone).html, minus Violet (Ink is the default): a
/// palette *and* the alternate app icon that goes with it. Mirrors AppTheme.kt.
enum AppTheme: String, CaseIterable, Identifiable {
    case ink, paper, mint, ocean, sky

    var id: String { rawValue }
    var label: String { rawValue.capitalized }

    static let `default` = AppTheme.ink

    /// The mode the doc shows this theme in; picking the theme moves the mode there.
    var preferredMode: ThemeMode { self == .paper ? .light : .dark }

    struct Colors {
        var background, surface, primary, accent, text, muted, onPrimary: Color
    }

    /// The doc's palette, except Paper: warm cream and brick red so it reads apart from Ink's violet.
    var dark: Colors {
        switch self {
        case .ink: Colors(background: Color(hex: 0x0C0E13), surface: Color(hex: 0x171A22), primary: Color(hex: 0xB3ABFF), accent: Color(hex: 0x8F84FF), text: Color(hex: 0xF2F3F7), muted: Color(hex: 0x8A90A0), onPrimary: Color(hex: 0x12151C))
        case .paper: Colors(background: Color(hex: 0x14100D), surface: Color(hex: 0x211A15), primary: Color(hex: 0xF0766F), accent: Color(hex: 0xF4A37F), text: Color(hex: 0xF7F0EA), muted: Color(hex: 0xA8998E), onPrimary: Color(hex: 0x1D1714))
        case .mint: Colors(background: Color(hex: 0x0B1A16), surface: Color(hex: 0x13261F), primary: Color(hex: 0x27C486), accent: Color(hex: 0x7FE0B6), text: Color(hex: 0xEEFAF4), muted: Color(hex: 0x8FB3A5), onPrimary: Color(hex: 0x06221A))
        case .ocean: Colors(background: Color(hex: 0x0B1324), surface: Color(hex: 0x142036), primary: Color(hex: 0x4F94FF), accent: Color(hex: 0x93BCFF), text: Color(hex: 0xEEF3FF), muted: Color(hex: 0x91A0BD), onPrimary: Color(hex: 0x06132B))
        case .sky: Colors(background: Color(hex: 0x08161F), surface: Color(hex: 0x10242F), primary: Color(hex: 0x1BA1E6), accent: Color(hex: 0x7CD0F7), text: Color(hex: 0xEEF8FD), muted: Color(hex: 0x8EAEBF), onPrimary: Color(hex: 0x04131C))
        }
    }

    /// Only Paper is designed as light; the others keep the app's neutral light surfaces and swap in
    /// a primary dark enough to carry white text.
    var light: Colors {
        func neutral(_ primary: UInt32, _ accent: UInt32) -> Colors {
            Colors(background: Color(hex: 0xF7F8FA), surface: .white, primary: Color(hex: primary), accent: Color(hex: accent), text: Color(hex: 0x161A22), muted: Color(hex: 0x6B7280), onPrimary: .white)
        }
        return switch self {
        case .ink: neutral(0x5B4DF0, 0x8B3FF0)
        case .paper: Colors(background: Color(hex: 0xF8F4EE), surface: .white, primary: Color(hex: 0xD63A3F), accent: Color(hex: 0xF08A5D), text: Color(hex: 0x1D1714), muted: Color(hex: 0x6B615A), onPrimary: .white)
        case .mint: neutral(0x0F9C68, 0x27C486)
        case .ocean: neutral(0x2563EB, 0x4F94FF)
        case .sky: neutral(0x1497DB, 0x3DB3F0)
        }
    }

    /// Home header ramp (white text sits on it): the icon gradient where one reads well.
    var headerStart: Color {
        switch self {
        case .ink: Color(hex: 0x5B4DF0)
        case .paper: Color(hex: 0xD63A3F)
        case .mint: Color(hex: 0x27C486)
        case .ocean: Color(hex: 0x4F94FF)
        case .sky: Color(hex: 0x3DB3F0)
        }
    }

    var headerEnd: Color {
        switch self {
        case .ink: Color(hex: 0x8B3FF0)
        case .paper: Color(hex: 0xF08A5D)
        case .mint: Color(hex: 0x0F9C68)
        case .ocean: Color(hex: 0x2563EB)
        case .sky: Color(hex: 0x1497DB)
        }
    }

    /// Home Featured Lab card ramp: a deeper cut of the theme so it reads apart from the header.
    var featured: [Color] {
        switch self {
        case .paper: [Color(hex: 0xD63A3F), Color(hex: 0x8E2328)]
        case .ink: [Color(hex: 0x4C3FD6), Color(hex: 0x231E5C)]
        case .mint: [Color(hex: 0x0F9C68), Color(hex: 0x065F46)]
        case .ocean: [Color(hex: 0x2563EB), Color(hex: 0x1E3A8A)]
        case .sky: [Color(hex: 0x1497DB), Color(hex: 0x075985)]
        }
    }

    /// The launcher tile, drawn in Settings so the preview is the icon that will be installed.
    var iconBackground: [Color] {
        switch self {
        case .ink: [Color(hex: 0x1C202B), Color(hex: 0x0C0E13)]
        case .paper: [.white, Color(hex: 0xF3E9E1)]
        case .mint: [Color(hex: 0x27C486), Color(hex: 0x0F9C68)]
        case .ocean: [Color(hex: 0x4F94FF), Color(hex: 0x2563EB)]
        case .sky: [Color(hex: 0x3DB3F0), Color(hex: 0x1497DB)]
        }
    }

    var iconGlyph: Color {
        switch self {
        case .ink: Color(hex: 0xB3ABFF)
        case .paper: Color(hex: 0xD63A3F)
        default: .white
        }
    }

    /// `setAlternateIconName` argument; nil is the primary icon (Ink).
    var alternateIconName: String? { self == .ink ? nil : "AppIcon-\(label)" }
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
    /// The paywall's CTA and hero, from the mock's isPremium block.
    static let premiumCta = [Color(hex: 0x4F46E5), Color(hex: 0x7C3AED)]
    static let premiumHero = [Color(hex: 0x4F46E5), Color(hex: 0x7C3AED), Color(hex: 0xC026D3)]
}

/// The resolved colour scheme (Material roles on Android), injected through the environment.
struct Palette: Equatable {
    var dark: Bool
    var theme: AppTheme

    private var colors: AppTheme.Colors { dark ? theme.dark : theme.light }

    var primary: Color { colors.primary }
    var tertiary: Color { colors.accent }
    var secondary: Color { Brand.blue }
    var background: Color { colors.background }
    var surface: Color { colors.surface }
    var onSurface: Color { colors.text }
    var onPrimary: Color { colors.onPrimary }
    var muted: Color { colors.muted }
    /// Hairlines are the surface nudged toward the text colour, so each theme's borders stay tinted
    /// to its own surfaces.
    var outline: Color { colors.surface.blended(toward: colors.text, dark ? 0.10 : 0.08) }
    var outlineVariant: Color { colors.surface.blended(toward: colors.text, dark ? 0.10 : 0.05) }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette(dark: false, theme: .default)
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

extension Color {
    /// This colour blended toward `other` by `amount` (0 = unchanged, 1 = `other`).
    func blended(toward other: Color, _ amount: Double) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        UIColor(other).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let t = CGFloat(amount)
        return Color(red: Double(r + (r2 - r) * t), green: Double(g + (g2 - g) * t), blue: Double(b + (b2 - b) * t), opacity: Double(a))
    }
}
