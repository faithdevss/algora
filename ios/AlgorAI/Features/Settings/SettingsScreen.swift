import StoreKit
import SwiftUI

// Port of feature/settings/SettingsScreen.kt: theme, accent, both reminders and sharing. The
// cross-promo card is Android-only (it links to a Play listing).
struct SettingsScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(PremiumStore.self) private var premium
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.requestReview) private var requestReview

    var body: some View {
        @Bindable var store = store
        ScrollView {
            VStack(spacing: 0) {
                header
                SettingsCard(title: "Premium", subtitle: premium.isPremium ? "Every topic and lab is unlocked" : "Monthly subscription or one-time lifetime") {
                    Button { router.push(.premium) } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "rosette").foregroundStyle(palette.primary)
                            Text(premium.isPremium ? "Manage Premium" : "Go Premium").font(AppFont.sans(14, .semibold)).foregroundStyle(palette.onSurface)
                            Spacer()
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.muted)
                        }
                    }
                    .buttonStyle(.plain)
                }
                SettingsCard(title: "Theme", subtitle: "How AlgorAI follows light and dark") {
                    HStack(spacing: 4) {
                        ForEach(ThemeMode.allCases, id: \.self) { mode in
                            let selected = store.themeMode == mode
                            Button { store.themeMode = mode } label: {
                                Text(mode.rawValue.capitalized)
                                    .font(AppFont.sans(13, .bold))
                                    .foregroundStyle(selected ? .white : palette.muted)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(selected ? palette.primary : .clear, in: RoundedRectangle(cornerRadius: 11))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(4)
                    .background(palette.background, in: RoundedRectangle(cornerRadius: 14))
                }
                SettingsCard(title: "App theme", subtitle: "Colours and app icon, together") {
                    VStack(spacing: 4) {
                        ForEach(AppTheme.allCases) { option in
                            themeRow(option)
                        }
                    }
                }
                SettingsCard(title: "Study reminders", subtitle: "One nudge after a week away — never more than one a week") {
                    toggleRow(on: $store.studyRemindersEnabled,
                              detail: store.studyRemindersEnabled ? "You'll hear from AlgorAI only if you go quiet for \(Reminders.inactiveDays) days."
                                  : "AlgorAI will never send you a notification.")
                }
                SettingsCard(title: "Daily reminder", subtitle: "One nudge at your time, only on a day you haven't opened the app") {
                    toggleRow(on: $store.dailyReminderEnabled,
                              detail: store.dailyReminderEnabled
                                  ? "Only when cards are due or the drill is unfinished — and never once you've been away more than \(Reminders.maxGapDays) days."
                                  : "No daily nudge. The weekly one above is unaffected.")
                    if store.dailyReminderEnabled {
                        HStack(spacing: 12) {
                            Image(systemName: "clock").foregroundStyle(palette.primary)
                            Text("Remind me at").font(AppFont.sans(14))
                            Spacer()
                            DatePicker("", selection: reminderTime, displayedComponents: .hourAndMinute)
                                .labelsHidden()
                                .environment(\.locale, Locale(identifier: "en_GB"))
                        }
                        .padding(.top, 10)
                    }
                }
                if let url = AppLinks.appStore {
                    SettingsCard(title: "Share AlgorAI", subtitle: "Send someone the install link") {
                        ShareLink(item: url, subject: Text("AlgorAI"), message: Text("AlgorAI\n\(AppLinks.pitch)")) {
                            HStack(spacing: 12) {
                                Image(systemName: "square.and.arrow.up").foregroundStyle(palette.primary)
                                Text("Share the app").font(AppFont.sans(14, .semibold)).foregroundStyle(palette.onSurface)
                                Spacer()
                            }
                        }
                    }
                }
                // The write-review page once the App Store id is filled in. Until then (before the first
                // release) fall back to Apple's rating sheet, which may show nothing outside TestFlight/dev.
                SettingsCard(title: "Rate AlgorAI", subtitle: "Tell others what you think on the App Store") {
                    Button {
                        if let url = AppLinks.writeReview { openURL(url) } else { requestReview() }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "star.fill").foregroundStyle(palette.primary)
                            Text("Rate the app").font(AppFont.sans(14, .semibold)).foregroundStyle(palette.onSurface)
                            Spacer()
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.bottom, 24)
        }
        .onChange(of: store.dailyReminderEnabled) { reschedule() }
        .onChange(of: store.studyRemindersEnabled) { reschedule() }
        .onChange(of: store.dailyReminderMinute) { reschedule() }
    }

    private func reschedule() { Task { await Reminders.reschedule(store: store) } }

    private var reminderTime: Binding<Date> {
        Binding(
            get: { Calendar.current.date(bySettingHour: store.dailyReminderMinute / 60, minute: store.dailyReminderMinute % 60, second: 0, of: Date()) ?? Date() },
            set: { date in
                let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                store.dailyReminderMinute = (c.hour ?? 20) * 60 + (c.minute ?? 0)
            }
        )
    }

    private var header: some View {
        HStack(spacing: 12) {
            // The arrow and the title together are the back control.
            Button { dismiss() } label: {
                HStack(spacing: 12) {
                    Image(systemName: "chevron.backward")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(.white.opacity(0.16), in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Preferences").font(AppFont.sans(12)).foregroundStyle(.white.opacity(0.7))
                        Text("Settings").font(AppFont.grotesk(22, .bold)).foregroundStyle(.white)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            Spacer()
        }
        .padding(18)
        // Dark on the left, light on the right, whichever of primary/accent is darker.
        .background(LinearGradient(colors: [palette.primary, palette.tertiary].sorted { $0.luminance < $1.luminance },
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 22))
        .padding(.top, 12)
    }

    private func toggleRow(on: Binding<Bool>, detail: String) -> some View {
        Toggle(isOn: on) {
            VStack(alignment: .leading, spacing: 2) {
                Text(on.wrappedValue ? "On" : "Off").font(AppFont.sans(14, .semibold))
                Text(detail).font(AppFont.sans(12)).foregroundStyle(palette.muted)
            }
        }
        .tint(palette.primary)
    }

    private func themeRow(_ option: AppTheme) -> some View {
        let selected = store.appTheme == option
        return Button { store.selectTheme(option) } label: {
            HStack(spacing: 14) {
                ThemeIconTile(theme: option).frame(width: 46, height: 46)
                VStack(alignment: .leading, spacing: 1) {
                    Text(option.label).font(AppFont.sans(15, .semibold)).foregroundStyle(palette.onSurface)
                    Text(option.preferredMode == .light ? "Light" : "Dark").font(AppFont.sans(12)).foregroundStyle(palette.muted)
                }
                Spacer()
                if selected {
                    Image(systemName: "checkmark").font(.system(size: 16, weight: .bold)).foregroundStyle(palette.primary)
                }
            }
            .padding(.vertical, 6).padding(.horizontal, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(selected ? "\(option.label), selected" : option.label)
    }
}

/// The launcher tile drawn from the theme's own icon colours, so the preview is the icon that will be installed.
private struct ThemeIconTile: View {
    let theme: AppTheme

    var body: some View {
        GeometryReader { geo in
            let u = min(geo.size.width, geo.size.height) / 100
            ZStack {
                LinearGradient(colors: theme.iconBackground, startPoint: .topLeading, endPoint: .bottomTrailing)
                Path { p in
                    p.move(to: CGPoint(x: 50 * u, y: 22 * u)); p.addLine(to: CGPoint(x: 34.8 * u, y: 55 * u)); p.addLine(to: CGPoint(x: 24 * u, y: 78 * u))
                    p.move(to: CGPoint(x: 50 * u, y: 22 * u)); p.addLine(to: CGPoint(x: 65.2 * u, y: 55 * u)); p.addLine(to: CGPoint(x: 76 * u, y: 78 * u))
                    p.move(to: CGPoint(x: 34.8 * u, y: 55 * u)); p.addLine(to: CGPoint(x: 65.2 * u, y: 55 * u))
                }
                .stroke(theme.iconGlyph, style: StrokeStyle(lineWidth: 5.5 * u, lineCap: .round, lineJoin: .round))
                ForEach([(34.8, 55.0, 7.0), (65.2, 55.0, 7.0), (24.0, 78.0, 7.0), (76.0, 78.0, 7.0), (50.0, 22.0, 8.0)], id: \.0) { node in
                    Circle().fill(theme.iconGlyph).frame(width: node.2 * 2 * u, height: node.2 * 2 * u)
                        .position(x: node.0 * u, y: node.1 * u)
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 46 * 0.2237))
        .overlay(RoundedRectangle(cornerRadius: 46 * 0.2237).stroke(.white.opacity(0.06), lineWidth: 1))
    }
}

private struct SettingsCard<Content: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder let content: () -> Content
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.grotesk(16, .bold))
            Text(subtitle).font(AppFont.sans(12.5)).foregroundStyle(palette.muted)
            content().padding(.top, 14)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 20)
        .padding(.top, 14)
    }
}

/// Store links. The App Store id does not exist until the app is first published, so sharing stays
/// hidden until it is filled in.
enum AppLinks {
    static let appStore: URL? = nil
    static var writeReview: URL? {
        guard let appStore, var c = URLComponents(url: appStore, resolvingAgainstBaseURL: false) else { return nil }
        c.queryItems = [URLQueryItem(name: "action", value: "write-review")]
        return c.url
    }
    static let pitch = "Learn DSA and AI with interactive labs, then practise coding and ML interviews."
}

private extension Color {
    /// Rec. 709 luma of the resolved sRGB colour; enough to tell the darker of two colours apart.
    var luminance: Double {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: nil)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
