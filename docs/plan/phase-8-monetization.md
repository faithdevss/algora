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
- `FakePremiumBilling` — debug stand-in, price `$9.99` (the launch price, so the debug paywall reads
  like the shipped one), grants after a short delay. **Lives in `src/debug/`, not `src/main/`.**
- `BillingProvider` (main) holds the singleton and delegates construction to `BillingFactory`, which
  exists once in `src/debug/` and once in `src/release/`.

**The implementation is chosen by source set, not by `BuildConfig.DEBUG`.** It was originally an
`if (BuildConfig.DEBUG)` branch inside `BillingProvider`, and that is not the same guarantee:
`isMinifyEnabled = false` on the release build type means R8 never runs, so `FakePremiumBilling` —
whose `launchPurchase()` calls `setPremium(true)` with no payment at all — was being compiled into
the release APK and left reachable by reflection. Splitting the factory means the release compiler
never sees the class, so the guarantee holds regardless of shrinker settings. `FakeRewardedAds` and
`AdsFactory` are arranged the same way. Verify after any refactor with:

```
./gradlew compileReleaseKotlin
find app/build/intermediates/built_in_kotlinc/release -iname "Fake*"   # must print nothing
```

### Ads — `core/ads/`
`RewardedAds` interface (`isReady` / `overlay` / `preload` / `show`). `AdMobRewardedAds` keeps one
rewarded ad warm and reloads after each dismissal; `MobileAds.initialize()` runs on an IO dispatcher
inside the impl, so `MainActivity` stays clean. `FakeRewardedAds` renders a 5s countdown via the
`overlay` flow (debug builds never touch the AdMob SDK) and, like the billing fake, lives in
`src/debug/` with `AdsFactory` split across `src/debug/` and `src/release/`. Ad ids live only in
`AdIds.kt`; the AdMob app id lives only in `AndroidManifest.xml` — both currently Google's public
test values.

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

1. Create the `algora_premium_lifetime` managed product in Play Console at **$9.99 USD**; create the
   AdMob app + rewarded unit.
2. **Set regional prices by hand.** Play's automatic conversion prices South and Southeast Asia at
   near-full USD, which is far above local purchasing power and is where most of this app's audience
   is. Turn on Play's purchasing-power price recommendations, then override at least: BDT 299–399,
   ₹199–299, PKR 699–899, Rp 39–49k, R$ 14,90.
3. Replace the ids in `core/ads/AdIds.kt` and `AndroidManifest.xml`'s `APPLICATION_ID` meta-data.
4. Test with a licence-tester account on an internal-testing track (release build → real
   `PlayPremiumBilling` + `AdMobRewardedAds`).

**Why $9.99 and not more.** The rewarded-ad path opens any locked topic for 24h, so the ceiling on
the lifetime price is not the value of the content — it is the point at which grinding ads beats
paying. $9.99 also sits under the $10 mental threshold that separates an impulse buy from a
considered one, which matters for a first release with no brand behind it. Play permits raising the
price for new buyers later and existing lifetime entitlements survive it, so launching low and
raising is recoverable in a way that launching high and discounting is not.
