import SwiftUI

// Port of feature/settings/SettingsScreen.kt: theme, accent, both reminders and sharing. The
// cross-promo card is Android-only (it links to a Play listing).
struct SettingsScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var store = store
        ScrollView {
            VStack(spacing: 0) {
                header
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
                SettingsCard(title: "Accent color", subtitle: "Tints buttons, progress and highlights") {
                    HStack(spacing: 10) {
                        swatch(fill: [AppMode.dsa.accent.color, AppMode.ai.accent.color], ring: palette.primary, selected: store.accent == nil) {
                            store.accent = nil
                        }
                        ForEach(AccentColor.allCases) { option in
                            swatch(fill: [option.color, option.gradientEnd], ring: option.color, selected: store.accent == option) {
                                store.accent = option
                            }
                        }
                    }
                    Text(store.accent?.label ?? "Auto — indigo in DSA mode, pink in AI mode")
                        .font(AppFont.sans(13, .semibold)).foregroundStyle(palette.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 12)
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
        .background(LinearGradient(colors: [palette.primary, palette.tertiary], startPoint: .topLeading, endPoint: .bottomTrailing),
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

    private func swatch(fill: [Color], ring: Color, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle()
                .fill(LinearGradient(colors: fill, startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay { if selected { Image(systemName: "checkmark").font(.system(size: 15, weight: .bold)).foregroundStyle(.white) } }
                .padding(4)
                .overlay(Circle().stroke(selected ? (palette.dark ? Color.white : ring) : .clear, lineWidth: 2))
                .aspectRatio(1, contentMode: .fit)
        }
        .buttonStyle(.plain)
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
    static let pitch = "Learn DSA and AI with interactive labs, then practise coding and ML interviews."
}
