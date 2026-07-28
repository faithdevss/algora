# Algora — Phased Build Roadmap

## Context

Repo greenfield (only `docs/` — `docs/features.md` spec + static HTML design mock `docs/design/Algora.dc.html`, no app code yet). Target: **native Android app in Kotlin** (Jetpack Compose), **local-only storage** for v1 (no backend — content bundled, progress via DataStore, no server-side paywall). Content scope large (DSA taxonomy + AI/ML/DL/NLP/RL taxonomy, each topic follows 7-section detail template w/ interactive simulations) — too large to plan/implement in one pass.

Each phase gets planned in detail in its own session: `/clear`, load this file + the relevant `docs/plan/phase-N-*.md` stub, expand into a real implementation plan, execute, mark done here.

**Design fidelity (applies to every phase, not just the ones that first touch a screen type):** `docs/design/Algora.dc.html` is the presentation-style source of truth — exact radii, spacing, color-alpha treatment, icon-tile shapes, button color-coding, fixed-appearance code/takeaway blocks, etc. Pull real values from its markup (`sc-if value="{{ is<Screen> }}"` blocks) and paired `renderVals()` style objects before building any new screen or section — don't improvise chrome from scratch even for content the mock doesn't cover (e.g. topics it never fleshed out). Topic **content** itself (copy, steps, code samples) is free to author — the mock only fully specifies 3 example topics (Singly Linked List, Graph, Linear Regression) as content references, not mandates. Phase 1 initially got this wrong (extracted only colors/fonts, invented layout) and was reworked — see `phase-1-vertical-slice.md`'s "Design fidelity rework" note for the corrected reference values now already ported into Compose.

**Bottom-nav realignment (post-Phase-7):** Phases 0 and 5 originally shipped a *mode-dependent category* bottom bar (DSA: Interview Prep/Data Structures/Home/Algorithms/Analysis; AI: ML/DL/Home/NLP/RL). A design-fidelity pass replaced it with the mock's actual `navDefs` — a **fixed four-destination bar identical in both modes**: Learning (→ Home) · Simulations (→ mode's flagship sim topic: `graph`/`linear_regression`) · Practice (→ Interview Prep, forces DSA) · Progress. Icons use the mock's SVG names (`book`/`flask`/`target`/`chart`) via `resolveIcon()`. Category screens are now reached from Home's Quick Access grid, not the nav bar. The mock's **Progress** dashboard (`isProgress` block: overall completion ring + streak + per-category bars) was also built (`feature/progress/ProgressScreen.kt`). `resolveIcon()` now covers the full mock icon set so authored content never falls back to a generic circle.

**Practice-tab restructure (post-Phase-8):** the Practice nav destination no longer jumps straight to Interview Prep. It now opens a hub (`feature/practice/PracticeScreen.kt`) listing the four things a learner *does*: Problem Solving · Quizzes · Flashcards · Interview Prep. Card review moved off Home (the Learning tab) — recall drills are practice, not reading — and the two duplicate decks were **merged into one**: the unscheduled `FlashcardScreen` (which looped every card forever) is deleted, and `ReviewRoute` is now the single flashcard surface, SM-2 scheduled so a card graded Good/Easy stops reappearing. Its queue is due cards then never-seen cards, with new cards capped at `DAILY_NEW_CARD_LIMIT` (20) per day so a first session cannot flood the schedule — the tally lives in `SettingsKeys.NEW_CARDS_DAY/COUNT` and rolls over daily (`NewCardCounterTest`). When nothing is due it offers "Study ahead" over the soonest-scheduled batch. The hub row shows live counts via `reviewCounts` (`ReviewCountsTest`). New surfaces: a quiz catalog over `QuizRegistry.all` (rows open the quiz topic, so premium gating stays in `TopicDetailScreen`), a **Topic Quizzes** Interview Prep category adding 14 subject quizzes — 9 DSA/analysis + 5 AI — plus an **AI Interview** category (AI/ML mock round + `mlSystemDesignPrimer`), for 19 quizzes / 111 questions total, guarded by `QuizBankTest`, and an authored **problem bank** (`feature/practice/problems/`) — 91 problems across 19 pattern groups (the five Interview Prep patterns plus binary search, prefix sums, hashing, monotonic stack, traversal, DP, backtracking, linked-list surgery, trees/BST, greedy, and four AI groups — ML foundations, neural networks, NLP pipeline and RL control), each with prompt, examples, constraints, a **"Knowledge you need"** block linking to the real `TopicRegistry` topics the problem depends on and why, progressively revealed hints, an approach + complexity reveal, and a collapsed Kotlin solution. Solved state persists via `ProgressKeys.SOLVED_PROBLEM_IDS`; `ProblemBankTest` guards that every prerequisite, cross-link and pattern id resolves to an existing topic. This intentionally departs from the mock's `navDefs`, whose Practice tap opens the Interview Prep browser directly.

## Phase Status

| Phase | Name | Status |
|---|---|---|
| 0 | Foundation & App Shell | Done |
| 1 | Vertical Slice | Done |
| 2 | Topic Browser & Content Scale-out | Done — all 81 DSA topics authored, nothing falls through to ComingSoon |
| 3 | Interactive Simulation Library | Done — 30 widget types built (DS sims, recursion tree, DP grid, sorting, search, tree, pathfinding, hashing, classifier playground, graph algorithms, array walks, 2D point cloud, token strip, neural net, RL grid-world/bandit/DQN/policy-gradient/game-search/offline-imitation/multi-agent/exploration, linked structures, environments, shared PlaybackTransport). **All 176 topics wired; nothing resolves to ComingSoon.** Per-widget inventory in the phase-3 doc |
| 4 | Algorithm Analysis Module | Done — all 17 Analysis tool ids built (11 distinct tools; see phase-4 doc) |
| 5 | AI Mode | Done — mode switch, AI nav, and full 7-section content for all 87 ML/DL/NLP/RL topics. The multi-layer/backprop sim it deferred now ships via `NeuralNetPlayer` (`neural_network_basics`, `backpropagation`). Per-mode shell theming is now done too (see the note below) |
| 6 | Interview Prep Module | Done — timed quiz mode + 4 quizzes + all 5 pattern guides + behavioral bank (STAR) + system design primer; all 11 topics have content |
| 7 | Engagement & Polish | Persisted dark mode + streaks + bookmarks/continue + flashcards + difficulty chips + multi-language code toggle + SM-2 spaced repetition + prerequisite-graph UI done; multi-language snippets for remaining topics deferred |
| 8 | Monetization | Done — lifetime IAP (Play Billing 8) + rewarded-ad 24h per-topic unlock gate the 143 `isPremium` topics; paywall screen built from the mock's `isPremium` block. Real Play Console / AdMob ids still to be swapped in (fakes active in debug) |
| 9 | AI Taxonomy Completion | In progress — author the 226 AI topics `docs/topics.ai.md` lists but the app lacks. Track A done (15 topics): **Reinforcement Learning covers 100% of the doc**, 74 topics. B1–B7 done (Regression 11, Classification 5, Bayesian 7, Ensembles 9, Clustering 10, Dimensionality Reduction 8, Association Rules + Time Series 9) — ML is restructured into eight of its twelve planned categories and carries 75 topics; **B8–B10 are still open**, so `ml_supervised` has not been emptied yet. Track C has started: **C1 (NN Basics, 4 topics)** created `dl_basics` and moved `perceptron`/`neural_network_basics`/`backpropagation` into it, taking DL to 19. Two config-driven simulation types were added early (`RegressionLab`, `DecisionSurface`); **B3–B7 and C1 needed no new widget**, only a math file each. **Both guard tests this phase specified are built** — `CategoryIntegrityTest` came with Phase 10, and B6 landed `AiTaxonomyCoverageTest`, which makes `docs/topics.ai.md` executable: all 357 entries mapped to the topic ids serving them or to an explicit gap, so **coverage is a measurement — 197 of 357 (55%)**. Per-batch math guards (`DimReductionMathTest`, `B7MathTest`, `DeepNetMathTest`) pin the numbers the labs quote, and C1 extended `SimulationFrameTest` to the neural-net widget, which immediately found four live bugs in labs shipped in earlier phases — two of them rendering the wrong data. 107 unit tests pass; 301 browsable topics. Detail in `docs/plan/phase-9-ai-taxonomy-completion.md` |
| 10 | DSA Taxonomy Completion | Done — authored the 27 algorithm topics `docs/topics.algo.md` listed and the app lacked (Algorithms 66 → 93; browsable topics 253 → 280). The four doc headings with **no** app representation now exist as categories: `algo_math` (7 topics), `algo_strings` (5 new, plus `kmp`/`rabin_karp`/`manacher` moved out of `algo_misc`), `algo_bits` (4, on a new `BitBoardPlayer` widget) and `algo_geometry` (4, reusing `PointCloudPlayer` unchanged); the remaining 7 filled gaps in existing categories. `algo_misc` renumbered to `13 · Miscellaneous & Advanced`. **Both guards Phase 9 specified and never landed are now built** — `AlgoTaxonomyCoverageTest` makes the doc executable (89 entries mapped to topic ids, not the 92 the plan claimed) and `CategoryIntegrityTest` covers all eight sections. A third, unplanned, `SimulationFrameTest` runs all 131 simulation frame builders on the JVM and found a live off-canvas bug in Phase 9's `mcmc` lab. Detail in `docs/plan/phase-10-dsa-taxonomy-completion.md` |

**Per-mode shell theming (closes the last Phase-5 item):** `mode` is hoisted out of `AlgoraApp` into
`MainActivity` (as `rememberSaveable`, so it survives rotation) and fed to `AlgoraTheme`, so the
whole M3 `colorScheme` — not just the Home header — recolours with DSA/AI. This is reconciled with
the user-selectable accent added in Phase 7 rather than overriding it: `SettingsRepository.accent`
is now `Flow<AccentColor?>` where **null = "Auto"** (stored as `AccentColor.AUTO_ID`, and the default
for a fresh install), resolved as `accentChoice ?: mode.accent` via the `AppMode.accent` extension
(`core/ui/theme/ModeAccent.kt` — DSA → Indigo, AI → Pink, chosen so Auto reproduces the mock's
`TopbarDsa`/`TopbarAi` ramps exactly). An explicit swatch wins in both modes. Settings gains a sixth
"Auto" swatch painted indigo→pink; the row now sizes swatches by weight so six fit a narrow screen.
The Home topbar reads `LocalAccent` instead of a hardcoded per-mode gradient, so it follows an
explicit accent too — the mock's ramps moved onto `AccentColor.topbarStart/topbarEnd` and
`Gradients.TopbarDsa`/`TopbarAi` are gone. Accent colours animate (320ms `tween`) so switching mode
slides rather than snaps.

**Content expansion (2026-07-28):** the taxonomy grew from 176 to **200 topics** — 12 DSA/algorithms
and 12 AI — all fully authored (7 sections, cross-links, prerequisites) and all with a *bespoke*
simulation config rather than a fallback. New DSA topics: `deque`, `sparse_table` (Data Structures);
`bucket_sort` (the one row the mock's `cats()` listed but the app never had); `kmp`, `rabin_karp`,
`manacher` (Misc & Advanced); `topological_sort`, `max_flow`, `articulation_points`, `lca` (Graph
Algorithms); `bitmask_dp`, `tree_dp` (DP). New AI topics: `random_forest`, `gradient_boosting`,
`bias_variance`, `regularization`, `model_evaluation` (ML); `batch_normalization`, `dropout`,
`transfer_learning`, `diffusion_models` (DL); `bpe`, `ner`, `rag` (NLP). **No new categories were
invented** — the mock's `cats()` is the authority on the category list, so every new topic slots into
an existing group. Gating follows the established split (foundational free, advanced premium); only
`deque` is free. Simulations reuse the existing widgets via new config entries + frame builders:
`ArrayWalkSection` (KMP's LPS build + scan, Rabin-Karp rolling hash *including a real collision*,
Manacher radii, sparse-table levels), `SortingVisualizerSection` (bucket scatter/sort/concatenate),
`GraphAlgorithmSection` (Kahn's in-degrees, Ford-Fulkerson over three augmenting paths where the
third exists only because of residual capacity, disc/low cut vertices), `TreeVisualizerSection` (two
LCA queries, tree-DP labels turning into "not-taken / taken"), `DpGridSection` (Held-Karp TSP with
mask rows), `LinkedStructureSection` (deque ends + the monotonic sliding-window-maximum payoff),
`PointCloudSection` (bootstrap/vote, residual-fitting stumps, bias-variance refits, λ sweep,
threshold sweep with live P/R/F1, ring → noise → ring diffusion), `NeuralNetSection` (batch stats,
dropout masks, freeze-then-fine-tune), `TokenStripSection` (BPE merges, BIO/Viterbi, RAG pipeline).
The problem bank gained a fifth content file (`ProblemContentAdvanced.kt`) with **4 new pattern
groups and 11 problems** — string matching, advanced graphs, state-compression DP, and model
evaluation/ensembles — taking it to 102 problems across 23 groups. Six thin RL/DL topics (`ppo`,
`sac`, `ddpg`, `actor_critic`, `prioritized_replay`, `gradient_descent_variants`) were deepened with
a third "why this exists" paragraph, extra steps/formulas/notation, and a second full code block
(PPO's whole update loop, SAC's tanh log-prob correction, DDPG's two losses + Polyak, n-step
actor-critic, a sum-tree buffer, Adam from scratch). New guard: `core/data/ContentCoverageTest.kt`
asserts every browsable topic has content, no topic resolves to `NotYetAvailable`, and every
prerequisite and cross-link id resolves — 46 unit tests pass.

**Cross-promo to the developer's other apps:** `core/ui/components/CrossPromo.kt` — a `CrossPromo`
descriptor plus `CrossPromoCard` (full width, end-of-content) and `CrossPromoRow` (compact, Settings)
that deep-link to Play via `market://details?id=…` with an `ActivityNotFoundException` fallback to
the web listing. First entry is **Systa: Learn System Design** (`com.saimum.systa`), shown at the
bottom of the general `system_design_primer` (after the "Mark as reviewed" CTA, so it never sits
between the reader and the content) and as a "More from the developer" Settings card. Not shown on
`ml_system_design_primer` — different subject. Play's ads policy is satisfied by the explicit
"MORE FROM THE DEVELOPER" label and the end-of-content, non-interstitial placement; no `<queries>`
manifest entry is needed because an implicit `ACTION_VIEW` is exempt from package-visibility
filtering.

**Target audience is 13+** (decided 2026-07-28), so Play's Families policy does not apply: ads stay
personalized, and outbound links need no parental gate. The one hardening that came out of the
review is in `core/ads/AdMobRewardedAds.kt` — `MobileAds.setRequestConfiguration()` now caps ad
content at `MAX_AD_CONTENT_RATING_T` before `initialize()`. That file's comment is the marker for
what would have to change if the audience is ever widened to include under-13s
(`setTagForChildDirectedTreatment` / `setTagForUnderAgeOfConsent`, plus a parental gate on every
external link).

## Phase Breakdown

**Phase 0 — Foundation & App Shell**
Android Studio/Gradle project setup, Kotlin + Jetpack Compose, package structure (feature-based), theming from design mock (Space Grotesk + IBM Plex Sans, light/dark base colors), bottom nav scaffold (DSA mode: Interview Prep/Data Structures/Home/Algorithms/Analysis), local data models (Topic/Category as Kotlin data classes), DataStore setup for progress persistence.

**Phase 1 — Vertical Slice (proves the template)**
One topic end-to-end: Array under Data Structures. All 7 detail-page sections (Hero, How It Works steps, Math w/ formula rendering, Technical Deep Dive w/ syntax-highlighted collapsible code, Interactive Simulation — Compose Canvas array visualizer with Insert/Delete/Search/Reset, Real-World Applications cards, Key Takeaways). Wired into nav: Home → Data Structures list → Array detail. Progress checkbox persisted.

**Phase 2 — Topic Browser & Content Scale-out**
Category list screens for all sections (grouped, icon badges, lock icons for premium — visual only, no enforcement), per-category search, per-category progress bar. Fill remaining Data Structures + Algorithms topics (text/math/code sections) reusing Phase 1's template composables. No new simulation types yet — topics needing unbuilt sim types get a placeholder.

**Phase 3 — Interactive Simulation Library**
Reusable simulation components: linked list/stack/queue visualizer, graph builder + animated BFS/DFS (state legend), recursion tree/call-stack player w/ playback transport, DP tabulation grid animator, generic slider-driven parameter explorer, classifier gate solver (AND/OR/XOR). Attach to topics from Phase 2 that need them.

**Phase 4 — Algorithm Analysis Module**
Operation counter tied to real code execution, cost/runtime profiler, parameterized input generator, growth-curve chart (log/linear/exponential overlay), best/worst-case comparison view, benchmark dashboard (real vs predicted), amortized analysis walkthrough, sandbox mode.

**Phase 5 — AI Mode**
DSA/AI mode switch, AI bottom nav (ML/DL/Home/NLP/RL), topic content for ML/DL/NLP/RL via same template, reuse Phase 3 sim widgets where applicable (e.g. gate solver → perceptron), cross-links between DSA and AI topics (e.g. Graph traversal ↔ MCTS).

**Phase 6 — Interview Prep Module**
Mock question sets, company tagging, pattern tagging (sliding window, two pointer, etc.), cross-links back to Algorithms topic pages, timed quiz mode.

**Phase 7 — Engagement & Polish**
Dark mode, bookmarks + "continue where left off," spaced-repetition review scheduler, quiz/flashcard mode auto-generated from Key Takeaways, difficulty tags + prerequisite graph UI, multi-language code snippet toggle (Python/Java/JS/C++), local streaks.

**Phase 8 — Monetization**
Premium gate over the existing `isPremium` topics: one-time lifetime IAP via Play Billing, or a rewarded ad for 24h access to a single topic. Entitlement lives in its own DataStore; paywall + locked-topic screens ported from the mock's `isPremium` block. Detail in `docs/plan/phase-8-monetization.md`.

**Phase 9 — AI Taxonomy Completion**
`docs/topics.ai.md` lists 357 entries (339 unique) across ML/DL/NLP/RL; the app ships 102 AI topics. RL is near-complete (56/71 — only Core Concepts and Tabular Methods are missing); ML/DL/NLP are 16/15/12 against 113/96/77. Author the missing **226** topics at the existing bar (full 7-section content + bespoke simulation + registry entry), in 28 category-sized batches. Two decisions taken up front: ML/DL/NLP **categories expand from two each to match the doc's sub-sections** (following the mock's own nine-category RL precedent — this supersedes the "no new categories were invented" note above, which was scoped to the 2026-07-28 expansion), and **no content shortcuts** — `ContentCoverageTest` stays green per batch. One new simulation widget (`FeatureMapPlayer`, for CNNs and object detection) is the phase's only net-new widget. Detail in `docs/plan/phase-9-ai-taxonomy-completion.md`.

**Explicitly deferred (not a phase yet):** real auth/cloud sync/server-side receipt validation — entitlement stays device-local (spoofable on rooted devices, accepted). Only revisit if static/local turns out insufficient.

## Verification

- Per phase: standard Android build/run (`./gradlew assembleDebug` or emulator) + manual walkthrough of that phase's screens.
