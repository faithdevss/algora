# AdMob setup

Ad ids are **not** committed. The build reads them at configuration time and injects them; the repo
only ever contains Google's public test ids as a fallback.

Rewarded video is the app's only ad surface — it appears when a free user chooses to unlock a
premium topic for 6 hours (`core/ads/`, `feature/premium/`). There are no banners or interstitials.

---

## Set your ids

Add to `local.properties` (already gitignored, never commit it):

```properties
admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
admob.rewardedUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

Note the punctuation — the **app id uses `~`**, the **unit id uses `/`**. Swapping them is the most
common mistake and produces a "no ad config" failure at runtime rather than a build error.

Where they come from in the AdMob console:

| Id | Console location |
|---|---|
| App id | Apps → your app → **App settings** |
| Rewarded unit id | Apps → your app → **Ad units** → Add ad unit → **Rewarded** |

Then rebuild. Nothing else needs editing.

### Other ways to pass them

Resolved in this order, first hit wins:

1. `local.properties` — `admob.appId` / `admob.rewardedUnitId`
2. Gradle properties — `./gradlew assembleRelease -Padmob.appId=… -Padmob.rewardedUnitId=…`
3. Environment — `ADMOB_APP_ID` / `ADMOB_REWARDED_UNIT_ID`
4. Google's test ids

So a workstation uses the untracked file and CI uses secrets, with no branching in the build script.

---

## What debug does

**Debug builds can never touch your AdMob account.** Two independent guards:

- `AdsProvider` returns `FakeRewardedAds` whenever `BuildConfig.DEBUG`, so the AdMob SDK is not even
  called.
- `defaultConfig` pins the test app id and test unit id for debug regardless of what you configured,
  so a debug build that somehow reached the SDK would still hit Google's sample inventory.

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

---

## Where this lives in the code

| File | Role |
|---|---|
| `app/build.gradle.kts` | Reads the ids, sets `manifestPlaceholders["admobAppId"]` and `buildConfigField("ADMOB_REWARDED_UNIT_ID")` per build type |
| `app/src/main/AndroidManifest.xml` | `com.google.android.gms.ads.APPLICATION_ID` = `${admobAppId}` — required, `MobileAds.initialize()` crashes at startup without it |
| `core/ads/RewardedAds.kt` | `AdIds.rewardedUnit` reads `BuildConfig.ADMOB_REWARDED_UNIT_ID` |
| `core/ads/AdsProvider.kt` | Picks `FakeRewardedAds` (debug) or `AdMobRewardedAds` (release) |
| `core/ads/AdMobRewardedAds.kt` | Real implementation — one ad kept warm, reloaded after each dismissal |

Every Gradle configuration run logs which ids are in play when the test fallback is still active:

```
AdMob: using Google test ids for release builds — set admob.appId and
admob.rewardedUnitId in local.properties before uploading (docs/admob-setup.md).
```

A release built with that line still in the log is a release that ships test ads and earns nothing.

---

## Related

- `docs/release-signing.md` — the keystore, read through the same lookup order as these ids
- `docs/store/listing.md` — Play listing copy and the rest of the pre-upload checklist
- Play billing ids (the lifetime premium purchase) are still debug stubs too — see `core/billing/`.
  They are **not** covered by this file yet.
