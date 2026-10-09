# iOS port (native SwiftUI)

## Scope

A native SwiftUI app in `ios/`, bundle id `com.saimum.algorai`, iPhone, iOS 17+. It ports every
Android feature **except ads**: no AdMob. Payments are in (Phase 18): StoreKit 2 with the same two
plans as Android — `algora_premium_monthly` (auto-renewing, no trial) and `algora_premium_lifetime`
(non-consumable). `Topic.isPremium` is exported from Android's `AccessTiers`, so the free/paid split
is identical. With no ads there is no ad-unlock tier: the 30 topics Android opens with a rewarded ad
stay locked on iOS until Premium.

Android stays the source of truth for content. `android/app/src/test/.../export/IosContentExportTest.kt`
serializes the Kotlin content objects to JSON in `ios/AlgorAI/Resources/Content/`:

| File | Source |
|---|---|
| `catalog.json` | `CategoryRegistry.all`, each section's `*Topics.topics`, `PrerequisiteGraph` |
| `topic_content.json` | `TopicContentProvider.all` (504 pages, 9 figure shapes, 34 simulation types) |
| `quizzes.json` | `QuizRegistry.all` (54 sets) |
| `problems.json` | `ProblemRegistry.patterns` + `.all` (23 groups, 102 problems) |
| `interview.json` | behavioral bank + both system-design primers |
| `flashcards.json` | `flashcardDecks` (5 curated decks) |
| `hashing_labs.json` | `hashingLabExports` — the hashing lab's frames (hash table, set, map, bloom filter, LRU cache, hash counting); iOS only draws them |
| `tokenizer_labs.json` | `tokenizerLabConfigs` — the tokenizer lab's frames (tokenization, bpe, hf_tokenizers), built from the real BPE / WordPiece / Unigram trainers so iOS needs no Swift port of them |

Re-export after any content change:

```
cd android && EXPORT_IOS_CONTENT=1 ./gradlew :app:testDebugUnitTest --tests '*IosContentExportTest*'
```

Sealed cases encode with a `"type"` field (`FigureShape`), payload-free cases as their bare name
(`SimulationType`), and absent optionals are omitted.

What does **not** export is code: the 34 simulation labs (~70k lines of Kotlin, each with per-topic
frame generators) and the 17 analysis tools. Those are hand-ported to Swift.

Design fidelity rules from `ROADMAP.md` apply unchanged: iOS matches the Android presentation
(same tokens, radii, spacing, fonts), not stock iOS chrome.

## Phases

| Phase | Scope | Status |
|---|---|---|
| I0 | Restructure into `android/` + `ios/`, xcodegen project, content export | Done |
| I1 | Foundation: theme tokens + accent/dark mode, fonts, icon map, settings/progress stores, tab shell + DSA/AI mode switch | Done |
| I2 | Browsing: Home, category browsers, patterns, topic detail (all static sections, code toggle, cross-links, prerequisites), FigureCard (9 shapes), bookmarks, completion | Done |
| I3 | Practice: hub, quiz catalog + QuizScreen (interview/learn), problems list/detail, daily drill, weak spots, SM-2 review, progress dashboard, streaks | Done |
| I4 | Interview prep extras (behavioral, system design) + the 17 analysis tools | Done |
| I5 | Simulation labs: all 34 `SimulationType` widgets + Simulations catalog, in batches (DS basics → algorithms → ML → DL/NLP → RL) | In progress: 30 of 33 labs wired in `SimulationHost.swift` |
| I6 | Settings, daily/study reminders (`UNUserNotificationCenter`), review prompt (`SKStoreReviewController`), share | Done |

Out of scope: `core/ads`, rewarded streak freezes, interstitials.
