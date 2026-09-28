import SwiftUI
import UIKit

// Space Grotesk and IBM Plex Sans ship as variable fonts, so each weight is instantiated through
// the "wght" axis (as Typography.kt does with FontVariation) rather than by bundling one file per
// weight. IBM Plex Mono ships as two static files.

enum AppFont {
    private static let wghtTag = 0x7767_6874 // 'wght'

    static func grotesk(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        variable("SpaceGrotesk-Light", size: size, weight: weight)
    }

    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        variable("IBMPlexSans-Regular", size: size, weight: weight)
    }

    static func mono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(weight == .medium || weight == .semibold || weight == .bold ? "IBMPlexMono-Medium" : "IBMPlexMono-Regular", fixedSize: size)
    }

    private static func variable(_ postScriptName: String, size: CGFloat, weight: Font.Weight) -> Font {
        guard let base = UIFont(name: postScriptName, size: size) else { return .system(size: size, weight: weight) }
        let attribute = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
        let descriptor = base.fontDescriptor.addingAttributes([attribute: [wghtTag: weightValue(weight)]])
        return Font(UIFont(descriptor: descriptor, size: size))
    }

    private static func weightValue(_ weight: Font.Weight) -> Int {
        switch weight {
        case .ultraLight, .thin, .light: 300
        case .medium: 500
        case .semibold: 600
        case .bold, .heavy, .black: 700
        default: 400
        }
    }
}

/// The Material type scale from Typography.kt.
extension Font {
    static let headlineLarge = AppFont.grotesk(32, .bold)
    static let headlineMedium = AppFont.grotesk(28, .semibold)
    static let titleLarge = AppFont.grotesk(22, .semibold)
    static let titleMedium = AppFont.grotesk(18, .medium)
    static let bodyLarge = AppFont.sans(16)
    static let bodyMedium = AppFont.sans(14)
    static let labelLarge = AppFont.sans(14, .medium)
    static let code = AppFont.mono(14)
}
