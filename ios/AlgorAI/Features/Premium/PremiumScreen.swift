import StoreKit
import SwiftUI

// Port of feature/premium/PremiumScreen.kt and LockedTopicBody.kt: the mock's isPremium block with
// two plan cards (monthly subscription, one-time lifetime) and no free trial.

private let premiumFeatures = [
    "Unlock every topic & category",
    "All interactive labs & simulators",
    "Multi-language code snippets",
    "Spaced-repetition review mode",
    "Offline access to all content",
    "No ads",
]

struct PremiumScreen: View {
    @Environment(PremiumStore.self) private var premium
    @Environment(\.palette) private var palette
    @State private var selected: PremiumPlan = .monthly
    @State private var showManage = false
    @State private var toast: String?

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Go Premium")
            ScrollView {
                VStack(spacing: 0) {
                    hero
                    VStack(spacing: 12) {
                        ForEach(premiumFeatures, id: \.self) { featureRow($0) }
                    }
                    .padding(.top, 20)
                    plans.padding(.top, 20)
                    action.padding(.top, 18)
                    terms.padding(.top, 12)
                }
                .padding(.horizontal, screenGutter)
                .padding(.vertical, 16)
            }
        }
        .task {
            // Also the refund / account-switch check: the App Store is asked again every time this opens.
            if premium.products.isEmpty { await premium.loadProducts() }
            await premium.refresh()
        }
        .onChange(of: premium.message) { _, message in
            guard let message else { return }
            toast = message
            premium.message = nil
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(AppFont.sans(13, .semibold))
                    .foregroundStyle(palette.onPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(palette.onSurface.opacity(0.92), in: RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, screenGutter).padding(.bottom, 12)
                    .task {
                        try? await Task.sleep(for: .seconds(3.5))
                        self.toast = nil
                    }
            }
        }
        .manageSubscriptionsSheet(isPresented: $showManage)
    }

    private var hero: some View {
        VStack(spacing: 5) {
            Image(systemName: "rosette")
                .font(.system(size: 30))
                .foregroundStyle(.white)
                .frame(width: 64, height: 64)
                .background(.white.opacity(0.2), in: RoundedRectangle(cornerRadius: 20))
                .padding(.bottom, 9)
            Text("AlgorAI Premium").font(AppFont.grotesk(24, .bold)).foregroundStyle(.white)
            Text(premium.isPremium ? "Premium active.\nEvery topic and lab is unlocked." : "Every topic, every interactive lab.\nLearn without limits.")
                .font(AppFont.sans(14))
                .foregroundStyle(.white.opacity(0.85))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 20).padding(.vertical, 26)
        .background(LinearGradient(colors: Gradients.premiumHero, startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 22))
    }

    private func featureRow(_ text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(palette.primary)
            Text(text).font(AppFont.sans(14, .semibold))
            Spacer()
        }
        .padding(.horizontal, 15).padding(.vertical, 13)
        .card(radius: 14)
    }

    private var plans: some View {
        HStack(spacing: 12) {
            planCard(.monthly, title: "Monthly", note: "per month · cancel any time", badge: nil)
            planCard(.lifetime, title: "Lifetime", note: "one-time · yours forever", badge: "PAY ONCE")
        }
    }

    private func planCard(_ plan: PremiumPlan, title: String, note: String, badge: String?) -> some View {
        let isSelected = selected == plan && !premium.isPremium
        return Button { selected = plan } label: {
            VStack(spacing: 2) {
                Text(title).font(AppFont.sans(12, .semibold)).foregroundStyle(palette.muted)
                Text(premium.products[plan]?.displayPrice ?? "—").font(AppFont.grotesk(22, .bold))
                Text(note).font(AppFont.sans(11)).foregroundStyle(palette.muted).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12).padding(.vertical, 16)
            .background(palette.surface, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(isSelected ? palette.primary : palette.outline, lineWidth: isSelected ? 2 : 1))
            .padding(.top, 9)
            .overlay(alignment: .topTrailing) {
                if let badge {
                    Text(badge)
                        .font(AppFont.sans(10, .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(SimColors.green, in: Capsule())
                        .padding(.trailing, 10)
                }
            }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var action: some View {
        if premium.isPremium {
            Text("You're Premium ✓")
                .font(AppFont.grotesk(15.5, .bold)).foregroundStyle(palette.primary)
                .frame(maxWidth: .infinity).padding(16)
                .background(palette.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 15))
                .overlay(RoundedRectangle(cornerRadius: 15).stroke(palette.primary.opacity(0.35), lineWidth: 1))
        } else {
            Button { Task { await premium.purchase(selected) } } label: {
                Text(buttonTitle)
                    .font(AppFont.grotesk(15.5, .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(16)
                    .background(LinearGradient(colors: Gradients.premiumCta, startPoint: .leading, endPoint: .trailing),
                                in: RoundedRectangle(cornerRadius: 15))
                    .opacity(premium.isPurchasing ? 0.6 : 1)
            }
            .buttonStyle(.plain)
            .disabled(premium.isPurchasing || premium.products[selected] == nil)
        }
    }

    private var buttonTitle: String {
        guard let price = premium.products[selected]?.displayPrice else { return "Unlock Premium" }
        return selected == .monthly ? "Subscribe · \(price) / month" : "Unlock for life · \(price)"
    }

    /// Apple requires the renewal terms next to the price.
    private var terms: some View {
        VStack(spacing: 6) {
            Text(selected == .monthly
                 ? "Renews monthly until you cancel in your Apple ID subscriptions."
                 : "One-time purchase. No subscription.")
                .font(AppFont.sans(12)).foregroundStyle(palette.muted).multilineTextAlignment(.center)
            HStack(spacing: 14) {
                Button("Restore purchase") { Task { await premium.restore() } }
                Button("Manage subscription") { showManage = true }
            }
            .font(AppFont.sans(12, .semibold))
            .foregroundStyle(palette.primary)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Shown in place of a locked topic's content. Counterpart of LockedTopicBody.kt without the
/// rewarded-ad button: iOS has no ads, so Premium is the only way in.
struct LockedTopicView: View {
    let title: String
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: title)
            VStack(spacing: 14) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(Color(hex: 0xF97316))
                    .frame(width: 68, height: 68)
                    .background(Color(hex: 0xF97316).opacity(0.14), in: RoundedRectangle(cornerRadius: 22))
                Text("Premium topic").font(AppFont.grotesk(22, .bold))
                Text("This lesson is part of Premium — subscribe monthly or pay once, with no ads.")
                    .font(AppFont.sans(14)).foregroundStyle(palette.muted).multilineTextAlignment(.center)
                Button { router.push(.premium) } label: {
                    Text("Unlock Premium")
                        .font(AppFont.grotesk(15.5, .bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(16)
                        .background(LinearGradient(colors: Gradients.premiumCta, startPoint: .leading, endPoint: .trailing),
                                    in: RoundedRectangle(cornerRadius: 15))
                }
                .buttonStyle(.plain)
                .padding(.top, 6)
            }
            .padding(.horizontal, 28)
            .padding(.top, 48)
            Spacer()
        }
    }
}

/// Wraps a topic-keyed destination (lesson, lab, quiz) in the paywall.
struct PremiumGate<Content: View>: View {
    let topicId: String
    @ViewBuilder var content: () -> Content
    @Environment(PremiumStore.self) private var premium

    var body: some View {
        if let topic = ContentStore.shared.topic(topicId), topic.isPremium, !premium.isPremium {
            LockedTopicView(title: topic.name)
        } else {
            content()
        }
    }
}

/// Wraps a problem in the paywall: problem groups carry their own `isPremium`, separate from topics.
struct ProblemGate<Content: View>: View {
    let problemId: String
    @ViewBuilder var content: () -> Content
    @Environment(PremiumStore.self) private var premium

    var body: some View {
        let store = ContentStore.shared
        if let pattern = store.problem(problemId).flatMap({ store.pattern($0.patternId) }),
           pattern.isPremium, !premium.isPremium {
            LockedTopicView(title: pattern.name)
        } else {
            content()
        }
    }
}
