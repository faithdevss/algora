import Foundation
import Observation
import StoreKit

/// The two ways to hold Premium. Either grants the same entitlement: Monthly lapses with the
/// subscription, Lifetime never does. Same product ids as Android's `PremiumPlan`.
enum PremiumPlan: String, CaseIterable, Identifiable {
    /// Auto-renewing subscription. No free trial or introductory offer: the product is content.
    case monthly = "algora_premium_monthly"
    /// Non-consumable.
    case lifetime = "algora_premium_lifetime"

    var id: String { rawValue }
}

/// Counterpart of Android's `PremiumBilling` + `EntitlementRepository`, over StoreKit 2.
///
/// `Transaction.currentEntitlements` is the source of truth and runs on every launch, so refunds,
/// lapsed subscriptions, reinstalls and Apple ID switches all converge. The answer is cached in
/// UserDefaults only so the UI is right on the first frame and offline. There is no server-side
/// receipt validation (the app has no backend), the same trade-off as Android.
///
/// iOS has no ads, so there is no ad-unlock tier here: the topics Android lets a rewarded ad open
/// stay locked until Premium.
@MainActor
@Observable
final class PremiumStore {
    private static let ownedKey = "premium_owned"

    private(set) var isPremium: Bool
    private(set) var products: [PremiumPlan: Product] = [:]
    private(set) var isPurchasing = false
    /// A one-line result for the paywall to show; the screen clears it once shown.
    var message: String?

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var updatesTask: Task<Void, Never>?

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        isPremium = defaults.bool(forKey: Self.ownedKey)
    }

    /// Call once at launch: listens for transactions that finish outside the app (Ask to Buy,
    /// renewals, refunds), then loads prices and re-checks ownership.
    func start() {
        guard updatesTask == nil else { return }
        updatesTask = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.handle(result)
            }
        }
        Task {
            await loadProducts()
            await refresh()
        }
    }

    func loadProducts() async {
        do {
            let loaded = try await Product.products(for: PremiumPlan.allCases.map(\.rawValue))
            var byPlan: [PremiumPlan: Product] = [:]
            for product in loaded {
                if let plan = PremiumPlan(rawValue: product.id) { byPlan[plan] = product }
            }
            products = byPlan
        } catch {
            message = "Couldn't reach the App Store. Check your connection and try again."
        }
    }

    /// Re-reads what this Apple ID owns. Also runs silently every time the paywall opens.
    func refresh() async {
        var owned = false
        let now = Date()
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result,
                  PremiumPlan(rawValue: transaction.productID) != nil,
                  transaction.revocationDate == nil,
                  transaction.expirationDate.map({ $0 > now }) ?? true else { continue }
            owned = true
        }
        setOwned(owned)
    }

    func purchase(_ plan: PremiumPlan) async {
        guard let product = products[plan], !isPurchasing else {
            if products[plan] == nil { message = "The App Store isn't available right now." }
            return
        }
        isPurchasing = true
        defer { isPurchasing = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                await handle(result)
                if isPremium { message = "Premium unlocked — enjoy every topic." }
            case .pending:
                message = "Purchase pending — we'll unlock it once it clears."
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            message = error.localizedDescription
        }
    }

    /// "Restore purchase": asks the App Store to sync, then re-reads ownership.
    func restore() async {
        do {
            try await StoreKit.AppStore.sync()
        } catch {
            message = error.localizedDescription
            return
        }
        await refresh()
        if !isPremium { message = "No previous purchase found on this Apple ID." }
    }

    private func handle(_ result: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = result else { return }
        await transaction.finish()
        await refresh()
    }

    private func setOwned(_ owned: Bool) {
        isPremium = owned
        defaults.set(owned, forKey: Self.ownedKey)
    }
}
