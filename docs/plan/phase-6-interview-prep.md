# Phase 6 — Interview Prep Module

Status: DONE — every Interview Prep topic has content (20 Patterns guides after the expansion below)
Depends on: Phase 2 (links back to Algorithms topics)

## Goal
Build the Interview Prep tab: practice problems tagged by pattern and company, cross-linked to Algorithms content, plus a timed quiz mode.

## Rough scope
- Mock question sets
- Company tagging
- Pattern tagging (sliding window, two pointer, etc.)
- Cross-links back to relevant Algorithms topic pages
- Timed quiz mode

## Detail plan (this session)

### Pattern guides (reuse the 7-section template)
The `Patterns` category topics (Sliding Window, Two Pointer, Fast & Slow Pointers, …) are prose learning units — they fit the existing `TopicContent` template exactly. Author full content for the flagship few, each with `crossLinks` to the matching Algorithms topic (e.g. Sliding Window Pattern → `sliding_window`). Sim = NotYetAvailable (patterns aren't visualized). Register in `TopicContentProvider`.

### Timed quiz mode (new unit type)
- `quiz/QuizModels.kt` — `QuizQuestion` (prompt, options, correctIndex, `patternTag`, `companyTag?`, difficulty, explanation, optional cross-link to an Algorithms topic) and `Quiz` (title, description, `timeLimitSeconds`, questions).
- `quiz/QuizContent.kt` — the authored question banks.
- `quiz/QuizRegistry.kt` — maps a topic id → `Quiz` (same pattern as `AnalysisToolRegistry`).
- `quiz/QuizScreen.kt` — paginated one-question-at-a-time flow with a live countdown timer (auto-submits on expiry), selectable option cards, then a results view: score, per-question review (your answer vs correct + explanation), and a tappable cross-link chip to the related Algorithms topic. Retry resets. Marks the topic complete on finish.
- `TopicDetailScreen` resolves `QuizRegistry.get(id)` before the content/tool branches and hands off to `QuizScreen`.

### Flagship content shipped
- Pattern guides: **Sliding Window Pattern**, **Two Pointer Pattern**, **Fast & Slow Pointers** (full 7-section + cross-links).
- Quizzes: **Timed Mock Interview** (free, mixed patterns, 5 min) and **FAANG Set** (company-tagged). `timed_mock_interview` flipped to free so the flagship is cleanly reachable.

### Design fidelity
Quiz chrome reuses the mock's tokens — Surface radii, `SimColors` action buttons, accent tag chips, `TakeawayGreen` for correct answers, a detail-style back header + a timer pill.

## Deferred
A per-topic problem list separate from quizzes (nice-to-have; not blocking).

## System Design Primer (this session — closes Phase 6)
New `systemdesign/` package (models/content/registry/`SystemDesignScreen`) mirroring the behavioral
bank: a "Drive the round" 5-step framework card + expandable building-block cards (10 concepts, each
category-chipped with a "when to use it"). Resolved in `TopicDetailScreen`. All 11 Interview Prep
topics now have content. Verified on-device.

## Remaining pattern guides (this session)
`merge_intervals_pattern` (→ merge_sort, timed mock) and `top_k_pattern` (→ top_k_elements, heap,
FAANG set) authored on the standard 7-section template and registered in `TopicContentProvider`.
All five Patterns topics now have full content. Verified on-device.

## Patterns expansion — 15 more pattern guides (this session)

The Patterns category went from 5 topics to 20, all premium and all on the standard 7-section
template with cross-links into Algorithms/Data Structures. Every one ships a real lab rather than
`NotYetAvailable`, reusing whichever player fits the pattern:

- **ArrayWalkPlayer** (new builders in `ArrayWalkSection`): Prefix Sum (subarray-sum-to-k against a
  prefix map, with a negative value to show why a window fails), Binary Search on Answer (ship
  capacity, one repack per probe), Monotonic Stack (next greater element with the stack as the aux
  row), Cyclic Sort (swap-to-index, ending on the duplicate/missing mismatch), In-place Linked List
  Reversal (prev/cur/next), K-way Merge (size-k heap of list heads), Greedy Intervals
  (earliest-finish-first on the interval track).
- **RecursionTreeVisualizer**: Backtracking (combination sum, sorted-break pruning) and Subsets &
  Combinations (power set, `start` preventing duplicates) — both slider-driven like the others.
- **TreeVisualizer**: Tree BFS (level order with the queue size frozen per level) and Tree DFS
  (root-to-leaf path sum, un-choose on exit), sharing one `PatternTree`.
- **GraphAlgorithmPlayer**: Topological Sort (course schedule run twice — valid DAG, then a back
  edge, so the emitted count is the cycle check) and Union-Find (edges streaming in, badges are
  roots, the rejected edge is the cycle).
- **PathfindingGrid**: Matrix Islands — flood fill over a land/water grid; START/GOAL cells are
  deliberately water so the renderer's markers stay hidden.
- **BitBoardPlayer**: Bit Manipulation — the two-unpaired-values variant, split by `n & −n`.

`SimulationFrameTest` covers every new config; `ContentCoverageTest` covers the cross-links.

## Remaining quizzes + behavioral (this session)

- **Company quizzes** — `startupSet` (Stripe/Airbnb/DoorDash/Notion/Databricks) and
  `financeTradingSet` (Two Sigma/Jane Street/Jump/Citadel/HRT), five company-tagged questions each,
  cross-linked to Algorithms topics, added to `QuizRegistry`. Reuse the existing `QuizScreen`.
- **Behavioral bank** — `behavioral/` package: `BehavioralModels` (prompt + category + assesses +
  starTip), `BehavioralContent` (8 prompts across Conflict/Failure/Leadership/…), `BehavioralRegistry`,
  and `BehavioralScreen` — a STAR-framework primer card plus expandable question cards showing "what
  they're assessing" and "how to frame it". Resolved in `TopicDetailScreen` (new branch after the
  quiz branch); "Mark as reviewed" marks the topic complete. Verified on-device.

## Bank expansion — quizzes, flashcards, behavioral, system design (this session)

- **Quizzes 15 → 23.** `QuizContentTopicsExtra.kt` adds four DSA subject sets (Recursion &
  Backtracking, Bit Manipulation, String Matching, Math & Number Theory); `QuizContentAiExtra.kt`
  adds three AI sets (Unsupervised Learning, Computer Vision, Data Preprocessing) plus the
  premium **ML Engineer Set** company set, which mixes modelling with production concerns (leakage,
  serving, drift). Six questions each, same four-minute clock, every question cross-linked to a real
  topic id. Registered in `QuizRegistry` + `InterviewPrepTopics`; the Practice quiz catalog picks
  them up automatically.
- **Flashcards +71 curated cards.** New `review/FlashcardDecks.kt` holds five hand-authored decks
  (Complexity Cheat Sheet, Pattern Triggers, Data Structure Choices, ML Metrics & Concepts, System
  Design Recall). `ReviewCard` gained an optional `prompt`, so a deck card shows its own question on
  the front instead of the generic "recall a key takeaway", and keeps it visible above the answer
  once flipped. Keys are `deck_<id>#<index>`, which cannot collide with the `topicId#index`
  takeaway cards, so both kinds schedule through the same SM-2 queue.
- **Behavioral bank 8 → 20 prompts** — added Ownership, Judgment, Prioritization, Initiative,
  Mentorship, Ambiguity, Collaboration, Depth, Communication, Impact, Motivation and a
  disagree-and-commit Conflict prompt.
- **System Design Primer 10 → 18 concepts** (idempotency, circuit breaker, WAL, leader election,
  blob store + metadata DB, fan-out on write vs read, backpressure, Bloom filter) and **ML System
  Design 12 → 18** (label delay, class imbalance, embedding/ANN index, human-in-the-loop, guardrails
  and fallbacks, A/B experiment design).
- **Tests** — `QuizBankTest` threshold raised to 23 plus a duplicate-title guard; new
  `FlashcardDeckTest` pins key uniqueness, the key shape, deck size and that the queue holds both
  card kinds.
