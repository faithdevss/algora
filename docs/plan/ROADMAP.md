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

**Explicitly deferred (not a phase yet):** real auth/cloud sync/server-side receipt validation — entitlement stays device-local (spoofable on rooted devices, accepted). Only revisit if static/local turns out insufficient.

## Verification

- Per phase: standard Android build/run (`./gradlew assembleDebug` or emulator) + manual walkthrough of that phase's screens.
