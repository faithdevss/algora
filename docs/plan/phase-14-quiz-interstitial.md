# Phase 14 — Interstitial after a finished quiz

Status: **Done.** All of the below is built and on `main`: the gate and its counters
(`InterstitialGateTest`, `InterstitialCounterTest`, 626 tests green), the single exit funnel with its
`BackHandler`, the `MobileAdsInit` extraction, the third ad id, and every doc listed at the bottom.
Three deviations from the plan as written are recorded in "What changed while building".

## Goal

Add the app's **second** ad surface: one interstitial shown when a free user leaves a finished quiz's
results screen. Everything else stays as it is — no banners, nothing on flashcards, nothing on the
daily drill, nothing mid-task.

Scope is deliberately one placement. The flashcard/review surface was considered and **cut** (see
"Rejected placements").

---

## Why

Two problems the current setup has:

1. **Free tier earns almost nothing.** The only ad surface is rewarded video, and it is entirely
   opt-in — a free user who never taps "watch an ad to unlock" and never banks a streak freeze sees
   zero ads for the life of the install. Monetization is a *lifetime* IAP (no subscription), so a
   non-buyer is currently worth ~£0 forever.
2. **The paywall's headline promise is empty.** `LockedTopicBody.kt:180` sells premium as "removes
   ads and unlocks every locked topic" — but a free user has no ad load to remove. Half the pitch
   describes a benefit they cannot feel.

An interstitial at a *natural completion boundary* fixes both: it earns from non-buyers, and it makes
"removes ads" a real, felt benefit that pushes toward the IAP.

The counter-risk is real and drives every cap below: the free user who finishes quizzes is also the
user closest to buying. The placement must annoy enough to be worth removing, and no more.

---

## Placement decision

**Fires:** on **exit from a completed full quiz's results screen**, for a non-premium user, subject to
the gate.

**Does not fire:**

- Before the score is shown. The results transition (`finished = true`) is the classic "level
  complete" slot and gets the most impressions, but it taxes the exact moment the learner is waiting
  for their number. The ad goes on the way *out*, not the way *in*.
- On a **"Retry missed"** or **"Retry all"** run. Retrying is continued study; taxing it punishes the
  behaviour the app exists to produce. `QuizScreen` already knows this — `isFullQuiz`
  (`QuizScreen.kt:88`) is exactly the discriminator, and a missed-only run already declines to record
  an attempt.
- On the **daily drill** (`DailyDrillScreen.kt:63`). The drill is a once-a-day habit loop, the same
  shape as flashcards; see "Rejected placements".
- On a quiz **abandoned** before completion (back out mid-run). No completion, no ad.

### The exit must be single-routed

Today the results screen leaves by three paths — the header back arrow, the "Back to Prep" button
(both `onBack`), and the system back gesture/predictive-back, which never reaches `onBack` at all.
An interstitial wired to only the first two is trivially bypassed and the impression rate would be
noise.

So the phase adds one `BackHandler` inside the results phase of `QuizRunner`, and funnels the header
arrow, the button and system back through a single `exitResults()`. That function asks the gate,
shows the ad if eligible, and calls `onBack` from the dismissal callback (and immediately if the ad
is skipped or fails).

---

## The gate

A pure, unit-tested eligibility function — no Compose, no SDK — plus the counters it reads.

`core/ads/InterstitialGate.kt`:

```kotlin
data class InterstitialState(
    val isPremium: Boolean,
    val quizzesFinished: Int,     // lifetime, full runs only
    val daysSinceInstall: Long,
    val shownToday: Int,
    val lastShownAtMs: Long,
    val reviewPromptedToday: Boolean,
)

fun shouldShowInterstitial(state: InterstitialState, nowMs: Long): Boolean
```

Rules, all of which must pass:

| Rule | Value | Why |
|---|---|---|
| Not premium | `isPremium == false` | The purchase's stated benefit. Non-negotiable. |
| Install grace | `daysSinceInstall >= 2` | A day-0 user has not decided whether the app is worth anything yet. Ads before that raise uninstall, and an uninstall is worth less than any eCPM. |
| Warm-up | `quizzesFinished > INTERSTITIAL_FREE_QUIZZES` (3) | Same reason, measured in engagement rather than calendar time. Both must pass — a user who installs and does nothing for a week still gets three untaxed finishes, and the fourth is the first that may show an ad. |
| Spacing | `nowMs - lastShownAtMs >= 3 min` | Stops a back-to-back quiz session from becoming an ad session. |
| Daily cap | `shownToday < 3` | Hard ceiling. Three interstitials is a noticeable-but-tolerable daily load; more is where quiz apps start bleeding retention. |
| Not colliding with the Play review prompt | `reviewPromptedToday == false` | `core/playreview/AppReviewPrompt.kt` is one-shot and also full-screen. Two modals stacked on one exit reads as broken, and it would poison the review ask — which is worth more than one impression. |

An ad that is **not loaded** is treated as "skip and continue", never "wait". The user is leaving a
screen; blocking that exit on a network load is worse than losing the impression. Preload is kicked
off for the next time in the same breath.

Note the gate deliberately does **not** consult `adUnlocks`. A rewarded per-topic unlock buys 6h of
one topic, not an ad-free app; conflating the two would let one rewarded view suppress interstitials
for the rest of the day.

---

## Architecture

Everything mirrors the existing rewarded trio one-for-one, so there is one ad pattern in the codebase
rather than two.

### `core/ads/` (new files)

| File | Role |
|---|---|
| `InterstitialAds.kt` | Interface — `isReady: StateFlow<Boolean>`, `overlay: StateFlow<String?>`, `preload(context)`, `show(activity, onDismiss, onUnavailable)`. Same shape as `RewardedAds`, minus the reward callback: an interstitial has no earn condition, so the caller only needs "it is over, carry on". |
| `AdMobInterstitialAds.kt` | Real implementation. One ad kept warm, reloaded on dismissal, same `MAX_AD_CONTENT_RATING_T` request configuration. |
| `MobileAdsInit.kt` | SDK bootstrap, **extracted out of `AdMobRewardedAds`'s `init` block**. See below — this is a prerequisite, not a tidy-up. |
| `InterstitialGate.kt` | The pure gate above. |
| `src/debug/.../FakeInterstitialAds.kt` | 3s countdown overlay, mirroring `FakeRewardedAds`, so the flow is walkable on an emulator with no AdMob account. |

#### The SDK bootstrap has to move first

`MobileAds.setRequestConfiguration()` + `MobileAds.initialize()` currently live in
`AdMobRewardedAds`'s `init` block, and that object is only ever constructed by `AdsProvider.get()`,
whose sole caller is `LockedTopicBody`. So today the SDK initializes the first time a free user opens
a **locked topic** — and for a user who never does, it never initializes at all.

That is fine while rewarded video is the only surface. It breaks the moment an interstitial exists:
a user can plainly finish three quizzes without ever opening a locked topic, and the first
interstitial request would then fire against an uninitialized SDK.

So the init block moves into `core/ads/MobileAdsInit.kt` — one idempotent `ensureInitialized(context)`
guarded by an `AtomicBoolean`, still doing its work on `Dispatchers.IO` (Google requires it off the
main thread; it does disk and network I/O). Both `AdMobRewardedAds` and `AdMobInterstitialAds` call
it from their own init, and the configuration is set before `initialize()` so the first request of
either kind honours the rating cap. The debug fakes call nothing, as now.

### Changed

| File | Change |
|---|---|
| `core/ads/AdsProvider.kt` | Second `@Volatile` singleton + `getInterstitial(context)`. |
| `src/debug/.../AdsFactory.kt`, `src/release/.../AdsFactory.kt` | Each gains `createInterstitial(app)`. The source-set split is what keeps the fake out of the shipped APK; do not add a `BuildConfig.DEBUG` branch instead. |
| `core/ads/RewardedAds.kt` | `AdIds` gains `interstitialUnit`. The file comment currently asserts "Rewarded video is the only ad surface in the app… There are no banners or interstitials anywhere" — it must be rewritten, not left to rot. |
| `core/data/settings/SettingsDataStore.kt` | Four new keys: `FIRST_OPEN_DAY` (long), `QUIZZES_FINISHED` (int), `INTERSTITIAL_DAY` (long) + `INTERSTITIAL_COUNT` (int) as a rolling daily pair, and `INTERSTITIAL_LAST_MS` (long). The day/count pair copies `NEW_CARDS_DAY`/`NEW_CARDS_COUNT` exactly — same rollover semantics, same read-side "if the stored day is not today, the count is 0" so a stale write can never grant extra impressions. |
| `core/data/settings/SettingsRepository.kt` | `interstitialState(nowMs): Flow<InterstitialState>` (combining the above with `EntitlementRepository.isPremium` at the call site, not here — settings must not depend on entitlement), `recordInterstitialShown()`, `recordQuizFinished()`, and `ensureFirstOpenDay()` called once from `MainActivity`. `ACTIVE_DAYS` is a *trimmed* rolling window, so it cannot answer "how old is this install" — hence the explicit key. |
| `feature/interviewprep/quiz/QuizScreen.kt` | New `adsEnabled: Boolean = true` parameter; the `exitResults()` funnel and `BackHandler` described above; `recordQuizFinished()` alongside the existing `recordQuizAttempt` in `onComplete`. |
| `feature/practice/daily/DailyDrillScreen.kt` | Passes `adsEnabled = false`. |
| `feature/topics/TopicDetailScreen.kt` | No change — it takes the default. This is the path the Quiz catalog and all 19 topic quizzes already route through. |
| `app/build.gradle.kts` | `testAdMobInterstitialUnitId = "ca-app-pub-3940256099942544/1033173712"`, an `admob.interstitialUnitId` / `ADMOB_INTERSTITIAL_UNIT_ID` lookup through the existing `adMobId()` helper, `buildConfigField("ADMOB_INTERSTITIAL_UNIT_ID")` in both `defaultConfig` (test id, always) and `release`, and the existing "using Google test ids" warning extended to name the third id. |

### UI

One addition beyond the ad itself: a single line at the foot of the results screen, for non-premium
users only — *"Studying ad-free? Unlock everything, one payment"* — routing to the existing paywall.
The interstitial creates the itch; this gives it somewhere to go on the very next screen. Without it
the ad is pure friction with no conversion path and the placement is much harder to justify.

---

## Rejected placements

**After a flashcard/review session** (`feature/review/ReviewScreen.kt:168`). Cut. SM-2 review is a
daily short loop — sometimes three cards — and "Study ahead" means a user can finish several sessions
in one sitting. An ad per session would fire repeatedly per day at exactly the moment the daily habit
is most fragile, and the habit is what produces the streak, the retention and eventually the
purchase. The daily drill's inline quiz is cut for the same reason.

**On the results transition** rather than the exit — taxes the score reveal. See "Placement decision".

**Banner on the quiz catalog / topic lists.** Permanently degrades the reading surface for a low
eCPM, and would contradict the mock's chrome everywhere at once.

**App-open ads.** Play policy risk and the worst possible first impression.

---

## Play policy notes

The placement is compliant as specified, but the specifics matter:

- **Natural break only.** The ad fires between screens, never over the quiz, never over a results
  screen the user is still reading, never on app open or app exit.
- **No unexpected full-screen ads.** The user's action (leaving results) initiates the transition the
  ad occupies.
- **Close control is the SDK's.** Do not draw over it, do not add a timer of our own.
- **Content rating cap stays `MAX_AD_CONTENT_RATING_T`**, set once in `AdMobRewardedAds`'s init and
  inherited by every request including interstitials. The 13+ target audience means no child-directed
  or under-age-of-consent tagging, unchanged from Phase 8.

---

## Docs that must change (all currently assert the opposite)

| File | Current claim |
|---|---|
| `docs/privacy-policy.md:32-33` | "optional rewarded video ads… **Only shown if you tap 'watch an ad'; never automatic.**" — flatly false once this ships. Needs an interstitial bullet and the "never automatic" sentence scoped to rewarded only. |
| `docs/admob-setup.md` | "Rewarded video is the app's only ad surface… There are no banners or interstitials." Plus a third row in the id table, the third `local.properties` line, and the console path (Ad units → **Interstitial**). |
| `docs/store/listing.md:243` | Data-safety form notes — reconfirm the answers still hold with a second ad format. The "Contains ads" declaration was already required by the rewarded surface, so that does not change. |
| `docs/plan/ROADMAP.md` | Phase 14 row + blurb. The status table is currently stale (no rows for 12 or 13) — add those while here. |
| `core/ads/RewardedAds.kt` header comment | As above. |

Also note the existing **24h vs 6h** inconsistency found while surveying: `ROADMAP.md` and
`privacy-policy.md:32` both say a rewarded unlock lasts 24h; `EntitlementRepository.grantAdUnlock`'s
comment and `LockedTopicBody.kt:54` say 6h. Resolve against `AD_UNLOCK_DURATION_MS` — the code is the
answer — and fix the docs in the same pass. The privacy policy being wrong about this is the one that
actually matters.

---

## Verification

- **`InterstitialGateTest`** (new, JVM) — the gate is a pure function, so every rule gets a case:
  premium never shows; day-1 install never shows; the third finished quiz never shows and the fourth
  does; two shows 30s apart yield one; the fourth show in a day is refused; the count rolls over at
  the day boundary; a stale `INTERSTITIAL_DAY` reads as 0 rather than as yesterday's count; a review
  prompt today suppresses it. Built on `TemporaryFolder` + `PreferenceDataStoreFactory` like
  `NewCardCounterTest`.
- **Manual, debug build** — the fake interstitial makes the whole flow walkable with no AdMob
  account. Walk: finish a topic quiz → results render with no ad → leave by button, by header arrow,
  and by system back, and confirm all three route through the gate → retry-missed run exits with no
  ad → daily drill exits with no ad → premium account (debug billing stub) never sees one.
- **Init ordering** — confirm on a release smoke build that an interstitial is the *first* ad surface
  touched (finish quizzes without ever opening a locked topic) and still serves. This is the failure
  the `MobileAdsInit` extraction exists to prevent, and the only one the JVM tests cannot see.
- **`./gradlew test`** — the existing suite must stay green; `QuizScreen`'s new parameter has a
  default so no call site outside the two named above changes.
- **Release smoke** — build release with ids unset so the Google test interstitial serves through the
  real SDK. Do **not** tap live ads from a release build; the invalid-traffic warning in
  `docs/admob-setup.md` applies identically to this unit.

---

## Known limits (accepted)

- **No frequency tuning by cohort or measurement.** The 2-day / 3-quiz / 3-per-day / 3-minute numbers
  are judgement, not data — there is no analytics backend (and none is planned), so they cannot be
  A/B tested. They are chosen conservative on purpose; the cheap iteration is to loosen them later if
  revenue is too low, which is a much safer direction to move than tightening after churn.
- **Impressions are inherently low.** Gated to completed full quizzes by warmed-up free users, this
  will not be a large revenue line. That is the intended trade: the placement exists as much to make
  the IAP's "removes ads" promise real as to earn directly.
- **Entitlement stays device-local**, so a rooted device can spoof premium and suppress ads. Same
  accepted limit as Phase 8, unchanged.

---

## What changed while building

1. **The warm-up constant is named for the promise, not the comparison.** The plan said
   `quizzesFinished >= 3` and then described "three free finishes" — those are different things (the
   third finish would have shown an ad). The constant shipped as `INTERSTITIAL_FREE_QUIZZES = 3` with
   a `>` test, so the name states the guarantee and the fourth completed quiz is the first that may
   be taxed. `InterstitialGateTest` pins both sides of that boundary.

2. **The impression is recorded on an app-lifetime scope, not the screen's.** The ad's dismissal
   callback fires *after* the caller has navigated away, so a `rememberCoroutineScope()` write would
   be cancelled with the departing composition and the impression would never be charged against the
   daily cap — the cap would leak. `QuizExitInterstitial.kt` keeps one small `AdWriteScope` for that
   single write.

3. **The 6h/24h contradiction was resolved as 6h and fixed everywhere.** `AD_UNLOCK_DURATION_MS` is
   `6L * 60 * 60 * 1000`, so the code was right and five places were wrong: `docs/privacy-policy.md`
   (the one that actually mattered — it told users an unlock lasted four times as long as it does),
   `ROADMAP.md`'s Phase 8 row and blurb, `docs/plan/phase-8-monetization.md` (3 occurrences), and a
   comment each in `AdUnlockableLockIcon.kt` and `TopicDetailScreen.kt`'s unlock banner.

### Found but not changed

`docs/store/listing.md` claimed `assembleRelease`/`bundleRelease` "fail loudly" when an ad id falls
back to Google's test id. **They do not** — the build only logs a warning at configuration time, and
that was already true before this phase. With a third id now able to fall back independently, the
risk of shipping a release that earns nothing on one surface is higher. The doc has been corrected to
say what the build actually does; adding a real hard failure was left alone deliberately, because
`docs/admob-setup.md` recommends "build release with the ids unset" as the *safe* way to test the
real SDK, and a bare hard-fail would break that documented path. Doing it properly needs an
`admob.allowTestIds` escape hatch — worth doing, out of scope here.
