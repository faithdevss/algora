# AdMob setup

Ad ids are **not** committed. The build reads them at configuration time and injects them; the repo
only ever contains Google's public test ids as a fallback.

The app has two ad surfaces, both full-screen, both for non-premium users only, both in `core/ads/`:

- **Rewarded video** — always opt-in. A free user taps it to unlock one premium topic for 6 hours,
  or to bank a streak freeze (`feature/premium/`). Nothing shows it automatically.
- **Interstitial** — the one non-opt-in surface: shown on the way *out* of a finished quiz, behind
  the caps in `core/ads/InterstitialGate.kt` (not premium, past a 2-day and 3-quiz warm-up, at most
  3 a day, at least 3 minutes apart, never stacked on the Play review prompt).

There are no banners anywhere, and nothing renders an ad over content the user is still reading.

---

## Set your ids

Add to `android/local.properties` (already gitignored, never commit it):

```properties
admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
admob.rewardedUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
admob.interstitialUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW
```

Note the punctuation — the **app id uses `~`**, the **unit id uses `/`**. Swapping them is the most
common mistake and produces a "no ad config" failure at runtime rather than a build error.

Where they come from in the AdMob console:

| Id | Console location |
|---|---|
| App id | Apps → your app → **App settings** |
| Rewarded unit id | Apps → your app → **Ad units** → Add ad unit → **Rewarded** |
| Interstitial unit id | Apps → your app → **Ad units** → Add ad unit → **Interstitial** |

Then rebuild. Nothing else needs editing.

### Other ways to pass them

Resolved in this order, first hit wins:

1. `local.properties` — `admob.appId` / `admob.rewardedUnitId` / `admob.interstitialUnitId`
2. Gradle properties — `./gradlew assembleRelease -Padmob.appId=… -Padmob.rewardedUnitId=…
   -Padmob.interstitialUnitId=…`
3. Environment — `ADMOB_APP_ID` / `ADMOB_REWARDED_UNIT_ID` / `ADMOB_INTERSTITIAL_UNIT_ID`
4. Google's test ids

So a workstation uses the untracked file and CI uses secrets, with no branching in the build script.

---

## What debug does

**Debug builds can never touch your AdMob account.** Two independent guards:

- `AdsFactory` exists once in `src/debug` and once in `src/release`, so the debug build compiles the
  fakes and never links the real implementations at all — the AdMob SDK is not even called.
- `defaultConfig` pins the test app id and both test unit ids for debug regardless of what you
  configured, so a debug build that somehow reached the SDK would still hit Google's sample
  inventory.

Real ids apply to **release builds only**. If you configure your ids and see nothing change, you are
almost certainly running a debug build.

---

## Testing the real path safely

> **Never tap your own live ads.** Self-clicked impressions from a release build are invalid traffic
> and are a standard cause of AdMob account suspension. Google does not treat "I was only testing"
> as an exception.

Two safe options:

1. Build release while leaving the ids unset — the test unit serves a real rewarded ad from Google's
   sample inventory, through the real SDK, with no account impact.
2. Register the device as a test device in the AdMob console (Settings → Test devices) and keep your
   own ids. Ads then render as test ads on that device.

While you are there, walk the one case the JVM tests cannot see: finish four quizzes on a two-day-old
install **without ever opening a locked topic**, and confirm the interstitial serves. That path makes
the interstitial the first ad surface the SDK is asked for, which is why `MobileAdsInit` exists.

---

## Where this lives in the code

| File | Role |
|---|---|
| `android/app/build.gradle.kts` | Reads the ids, sets `manifestPlaceholders["admobAppId"]` and both `buildConfigField`s per build type |
| `android/app/src/main/AndroidManifest.xml` | `com.google.android.gms.ads.APPLICATION_ID` = `${admobAppId}` — required, `MobileAds.initialize()` crashes at startup without it |
| `core/ads/RewardedAds.kt` | `AdIds` reads both unit ids out of `BuildConfig` |
| `core/ads/AdsProvider.kt` | App-lifetime singleton per format; the implementation comes from the source-set `AdsFactory` |
| `core/ads/MobileAdsInit.kt` | Idempotent SDK bootstrap + the content-rating cap, shared by both formats |
| `core/ads/AdMobRewardedAds.kt` | Real rewarded implementation — one ad kept warm, reloaded after each dismissal |
| `core/ads/AdMobInterstitialAds.kt` | Real interstitial implementation, same warm-one-ad shape |
| `core/ads/InterstitialGate.kt` | Pure eligibility policy for the quiz-exit interstitial (`InterstitialGateTest`) |
| `core/ads/QuizExitInterstitial.kt` | Wires the gate to the results screen's single exit funnel |

Every Gradle configuration run logs which ids are in play when the test fallback is still active:

```
AdMob: using Google test ids for release builds — set admob.appId,
admob.rewardedUnitId and admob.interstitialUnitId in local.properties before
uploading (docs/admob-setup.md).
```

A release built with that line still in the log is a release that ships test ads and earns nothing.

---

## Related

- `docs/release-signing.md` — the keystore, read through the same lookup order as these ids
- `docs/store/listing.md` — Play listing copy and the rest of the pre-upload checklist
- Play billing ids (the lifetime premium purchase) are still debug stubs too — see `core/billing/`.
  They are **not** covered by this file yet.
