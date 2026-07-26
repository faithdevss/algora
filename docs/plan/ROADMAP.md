# Algora — Phased Build Roadmap

## Context

Repo greenfield (only `docs/` — `docs/features.md` spec + static HTML design mock `docs/design/Algora.dc.html`, no app code yet). Target: **native Android app in Kotlin** (Jetpack Compose), **local-only storage** for v1 (no backend — content bundled, progress via DataStore, no server-side paywall). Content scope large (DSA taxonomy + AI/ML/DL/NLP/RL taxonomy, each topic follows 7-section detail template w/ interactive simulations) — too large to plan/implement in one pass.

Each phase gets planned in detail in its own session: `/clear`, load this file + the relevant `docs/plan/phase-N-*.md` stub, expand into a real implementation plan, execute, mark done here.

**Design fidelity (applies to every phase, not just the ones that first touch a screen type):** `docs/design/Algora.dc.html` is the presentation-style source of truth — exact radii, spacing, color-alpha treatment, icon-tile shapes, button color-coding, fixed-appearance code/takeaway blocks, etc. Pull real values from its markup (`sc-if value="{{ is<Screen> }}"` blocks) and paired `renderVals()` style objects before building any new screen or section — don't improvise chrome from scratch even for content the mock doesn't cover (e.g. topics it never fleshed out). Topic **content** itself (copy, steps, code samples) is free to author — the mock only fully specifies 3 example topics (Singly Linked List, Graph, Linear Regression) as content references, not mandates. Phase 1 initially got this wrong (extracted only colors/fonts, invented layout) and was reworked — see `phase-1-vertical-slice.md`'s "Design fidelity rework" note for the corrected reference values now already ported into Compose.

**Bottom-nav realignment (post-Phase-7):** Phases 0 and 5 originally shipped a *mode-dependent category* bottom bar (DSA: Interview Prep/Data Structures/Home/Algorithms/Analysis; AI: ML/DL/Home/NLP/RL). A design-fidelity pass replaced it with the mock's actual `navDefs` — a **fixed four-destination bar identical in both modes**: Learning (→ Home) · Simulations (→ mode's flagship sim topic: `graph`/`linear_regression`) · Practice (→ Interview Prep, forces DSA) · Progress. Icons use the mock's SVG names (`book`/`flask`/`target`/`chart`) via `resolveIcon()`. Category screens are now reached from Home's Quick Access grid, not the nav bar. The mock's **Progress** dashboard (`isProgress` block: overall completion ring + streak + per-category bars) was also built (`feature/progress/ProgressScreen.kt`). `resolveIcon()` now covers the full mock icon set so authored content never falls back to a generic circle.

## Phase Status

| Phase | Name | Status |
|---|---|---|
| 0 | Foundation & App Shell | Done |
| 1 | Vertical Slice | Done |
| 2 | Topic Browser & Content Scale-out | Done — all 81 DSA topics authored, nothing falls through to ComingSoon |
| 3 | Interactive Simulation Library | Widgets done, wiring ongoing — 25 widget types built (DS sims, recursion tree, DP grid, sorting, search, tree, pathfinding, hashing, classifier playground, graph algorithms, array walks, 2D point cloud, token strip, neural net, RL grid-world/bandit/DQN/policy-gradient/game-search, shared PlaybackTransport). **129 of 176 topics wired; 47 still ComingSoon** (RL 21, Algorithms 14, Data Structures 12 — Interview Prep, ML, NLP and DL complete). Per-widget inventory in the phase-3 doc |
| 4 | Algorithm Analysis Module | Done — all 17 Analysis tool ids built (11 distinct tools; see phase-4 doc) |
| 5 | AI Mode | Done — mode switch, AI nav, and full 7-section content for all 87 ML/DL/NLP/RL topics. The multi-layer/backprop sim it deferred now ships via `NeuralNetPlayer` (`neural_network_basics`, `backpropagation`). Still open: per-mode theming of the whole shell — `AppMode` reaches nav/Home/Progress, but `Theme.kt` has no per-mode scheme, so only the Home topbar gradient switches |
| 6 | Interview Prep Module | Done — timed quiz mode + 4 quizzes + all 5 pattern guides + behavioral bank (STAR) + system design primer; all 11 topics have content |
| 7 | Engagement & Polish | Persisted dark mode + streaks + bookmarks/continue + flashcards + difficulty chips + multi-language code toggle + SM-2 spaced repetition + prerequisite-graph UI done; multi-language snippets for remaining topics deferred |

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

**Explicitly deferred (not a phase yet):** real auth/cloud sync/server-side paywall — only revisit if static/local turns out insufficient.

## Verification

- Per phase: standard Android build/run (`./gradlew assembleDebug` or emulator) + manual walkthrough of that phase's screens.
