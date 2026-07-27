# Phase 8 — Monetization (Premium IAP + Rewarded-Ad Unlock)

Status: Done (fakes wired; real Play Console / AdMob ids still to be swapped in)
Depends on: Phases 0–7 (gates existing content, adds one new screen)

## Goal

Turn the cosmetic `Topic.isPremium` flag (143 topics across the 8 category files) into a real gate,
with exactly two ways past it:

1. **Buy premium** — one-time lifetime IAP, unlocks everything forever, no ads.
2. **Watch a rewarded ad** — unlocks *that one topic* for 24 hours, repeatable.

No banners, no interstitials: rewarded video is the only ad surface. Local-only, per the roadmap's
"no backend" decision — there is no server-side receipt validation.

## Architecture

### Entitlement — `core/data/entitlement/`
Own DataStore (`algora_entitlement`) so clearing settings or progress can never grant or revoke
premium. Two keys: `premium_owned` (a cache of Play's answer) and `ad_unlocks`
(`"topicId|expiryEpochMillis"` string set, same encoding convention as the SRS state).

`TopicAccess` = `Open | Owned | AdUnlocked(expiresAt) | Locked`, resolved by the pure
`accessOf(...)` function (unit-tested). Expired unlocks are filtered **on read**, so a stale entry
can never grant access even if the pruning write is missed; `grantAdUnlock()` prunes on write too.

### Billing — `core/billing/`
`PremiumBilling` interface (price / status / event flow / `refresh()` / `launchPurchase()`); the
implementations write the result into `EntitlementRepository`, which is what the UI observes.

- `PlayPremiumBilling` — Play Billing 8, product `algora_premium_lifetime` (INAPP, non-consumable).
  `enablePendingPurchases(...)` is mandatory since Billing 7.x. Purchases are **acknowledged
  immediately** (Google auto-refunds anything unacknowledged for 3 days).
  `queryPurchasesAsync` is the source of truth and runs on every connect, so refunds, reinstalls and
  account switches all converge; it doubles as "Restore purchase".
- `FakePremiumBilling` — debug stand-in, price `$14.99`, grants after a short delay.
- `BillingProvider` picks by `BuildConfig.DEBUG` (needs `buildConfig = true`, now enabled).

### Ads — `core/ads/`
`RewardedAds` interface (`isReady` / `overlay` / `preload` / `show`). `AdMobRewardedAds` keeps one
rewarded ad warm and reloads after each dismissal; `MobileAds.initialize()` runs on an IO dispatcher
inside the impl, so `MainActivity` stays clean. `FakeRewardedAds` renders a 5s countdown via the
`overlay` flow (debug builds never touch the AdMob SDK). Ad ids live only in `AdIds.kt`; the AdMob
app id lives only in `AndroidManifest.xml` — both currently Google's public test values.

### UI
- `feature/premium/PremiumScreen.kt` — the mock's `isPremium` block (`docs/design/Algora.dc.html`
  lines 307–342): sticky "Go Premium" header, three-stop gradient hero, six feature rows, the
  `planCard()` selected-state card (lifetime instead of monthly/yearly), gradient CTA, restore link.
  Route `premium`. Its Scaffold passes `WindowInsets(0)` — the app-level Scaffold already insets.
- `feature/premium/LockedTopicBody.kt` — the paywall itself: amber lock tile, "Unlock Premium"
  (→ paywall) and "Watch ad · 24h access" (→ rewarded ad → `grantAdUnlock`).
- Home gains the mock's line-82 "Unlock Premium" gradient button, hidden once owned.
- `TopicDetailScreen` was split: the public composable is now the **gate** and the old body became
  private `TopicDetailContent`. The gate sits before the quiz / behavioral / system-design /
  analysis-tool branches, all of which serve premium topics (every Analysis tool is premium).
  Ad-unlocked topics get an amber "Unlocked until Tue 5:11 PM" banner above the header.
- `core/ui/components/AdUnlockableLockIcon.kt` — the row/gate lock mark. A plain padlock reads
  "buy to open", which understates the model, so the padlock body carries a knocked-out play
  triangle: locked, but a video is also a way in. Built as a Compose composite (padlock icon +
  Canvas triangle in the background color) because `Icon()` tints a whole ImageVector one color.
- `TopicRow`'s `isPremium` param became `isLocked`; `CategoryBrowserScreen` collects entitlement
  itself (rather than threading it through eight category screens) so locks vanish the moment
  premium is bought or an unlock is granted.

## Known limits (accepted)

- Entitlement is cached locally with no server validation → spoofable on a rooted device. The
  roadmap defers a backend; `queryPurchasesAsync` on every launch keeps honest devices correct.
- Debug builds always use the fakes, so the real billing/ads paths are compile-checked but not
  exercised until a release build with real ids.

## Verification

- `./gradlew test` — `EntitlementAccessTest` (9 tests): free topic open, premium locked, unlock live
  at +23h / dead at +25h, no leak to other topics, ownership wins, 24h grant, read-filtering,
  re-grant dedupe, `setPremium` both directions.
- On-device (emulator, debug build): Analysis list shows locks → "Growth Curve Chart" opens the
  paywall → fake ad countdown → content renders with the expiry banner → Home upsell → fake purchase
  → "You're Premium ✓", every lock icon gone app-wide, upsell button gone.

## To go live

1. Create the `algora_premium_lifetime` managed product in Play Console; create the AdMob app +
   rewarded unit.
2. Replace the ids in `core/ads/AdIds.kt` and `AndroidManifest.xml`'s `APPLICATION_ID` meta-data.
3. Test with a licence-tester account on an internal-testing track (release build → real
   `PlayPremiumBilling` + `AdMobRewardedAds`).
