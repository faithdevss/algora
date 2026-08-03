# Phase 11 — Figures for Data Structures & Algorithms

Status: **Shipped through F7** — all 122 topics carry a figure and `DsaFigureCoverageTest` asserts it
with no pending set. **F8 was not run**: the consistency sweep and the walk of all 122 pages are still
outstanding, deliberately skipped so the AI figure phase could start. Two deviations from the plan
below are worth knowing: F2 did change `FigureCard.kt` (tree and graph node radius now come from the
measured label width), and the AI phase added the three shapes this doc predicted — see
`phase-12-ai-figures.md`.
Depends on: the figure layer shipped with the pattern-library expansion (`core/data/model/Figure.kt`,
`feature/topics/FigureCard.kt`, `FigureCoverageTest`).

## Goal

Every topic page opens with prose and closes with an animated lab. Between them there is nothing a
reader can take in at a glance — the lab has to be *played* before it teaches anything, and the prose
has to be *read* before it does. The interview-prep pattern guides now answer that with a static
figure above the How-It-Works steps: one picture of the invariant, no playback.

That layer covers **57 of 505** topic pages. This phase extends it to the **122 Data Structures and
Algorithms topics** — the section a learner actually starts in — leaving the 326 AI topics for later
(see Non-goals).

No new renderer is needed. Every DSA topic maps onto one of the six shapes already built.

## Baseline

| Section | Topics | With a figure today |
|---|---|---|
| Interview Prep — Patterns | 57 | 57 |
| Data Structures | 29 | 0 |
| Algorithms | 93 | 0 |
| ML / DL / NLP / RL | 326 | 0 |
| **Total** | **505** | **57** |

## Shape mapping

The pattern set was authored against these six and needed no others; the DSA taxonomy is the same
material one level less abstract, so the mapping is direct.

| Shape | DSA categories it serves | Approx. topics |
|---|---|---|
| `Strip` | Array, string, sorting, searching, two-pointer/window algorithms, bit manipulation, math & number theory | ~45 |
| `Tree` | BST/AVL/B-tree/heap/trie/segment tree, recursion, divide & conquer, backtracking | ~30 |
| `Graph` | Graph algorithms, pathfinding, MST, flow, SCC, plus computational geometry (points and hull edges are nodes and edges) | ~25 |
| `Grid` | Dynamic programming, matrix problems, hash tables | ~15 |
| `Stacks` | Stack, queue, deque, linked-list surgery | ~5 |
| `Timeline` | Interval scheduling, greedy activity selection | ~2 |

**Geometry is the one judgement call.** Convex hull, closest pair, line intersection and sweep are
point-plane problems, and `Graph` is a hand-placed node set with optional edges — close enough that
inventing a `Plane` shape for four topics is not worth it. If the four figures come out unreadable,
that is the trigger to add one, not before.

## Batches

One commit each, category-aligned so a batch's figures share a visual vocabulary.

| Batch | Scope | Count |
|---|---|---|
| F1 | Data Structures — linear (array, string, linked lists, stack, queue, deque, hash table) | ~12 |
| F2 | Data Structures — non-linear & advanced (trees, heaps, tries, graphs, disjoint set, segment/Fenwick, suffix structures) | ~17 |
| F3 | Sorting + searching | ~14 |
| F4 | Recursion, backtracking, divide & conquer | ~14 |
| F5 | Graph algorithms + pathfinding | ~20 |
| F6 | Dynamic programming + greedy | ~19 |
| F7 | Strings, math & number theory, bits, geometry | ~20 |
| F8 | Consistency sweep — caption voice, tone roles, light/dark pass over all 122 | — |

## Files touched

- `app/src/main/java/com/algora/app/feature/topics/content/*.kt` — 122 files gain a `figure = …`
  argument. Same mechanical shape as the pattern batches: the argument sits after `topicId`, and the
  `Figure*` model imports are added alongside the existing ones.
- `app/src/test/java/…/FigureShapeTest.kt` — **new**, see below.
- `app/src/test/java/com/algora/app/feature/interviewprep/FigureCoverageTest.kt` — keeps the
  pattern-specific coverage assertions, drops the shape assertions once they move.
- No changes to `Figure.kt` or `FigureCard.kt` expected. If a batch does need one, that is a signal
  worth writing down in this doc rather than a routine edit.

## Guard changes

`FigureCoverageTest` currently does two jobs: "every pattern guide has a figure" (coverage) and "no
band runs past its strip" (shape conformance). Only the first is pattern-specific.

1. **Move the shape assertions** into a new `FigureShapeTest` under `feature/topics`, iterating
   **every** `TopicContent` that carries a figure rather than the pattern list — so a DSA figure is
   held to the same rules from its first batch. The eight existing checks port unchanged: cells and
   arrows address real positions, bands do not overlap, spans fit their axis, trees have one root
   with parents at smaller indices than their children, graph nodes sit inside 0..1, band labels get
   ~11 characters per cell they span, one-cell arrows carry no label, captions are non-blank.
2. **Add a coverage list** for DSA in the same shape the pattern batches used: a `pendingFigures` set
   that starts at 122 and shrinks per batch, with a companion test that fails if an id in it already
   has a figure. That is what kept the pattern batches honest about what was actually done.

## Verification

Per batch:
1. `./gradlew :app:testDebugUnitTest` — `FigureShapeTest`, `FigureCoverageTest`, `ContentCoverageTest`.
2. `./gradlew assembleDebug`.
3. Emulator spot-check of two pages from the batch, **light and dark**. This is not optional
   ceremony: the pattern work's two real defects — a muted tone unreadable on dark, and graph labels
   hanging outside their circles — were both invisible to the tests and obvious on the device.

At the end of F8, walk all 122 pages once.

## Non-goals

- **The 326 AI topics.** Their content is loss curves, layer stacks, attention matrices and
  probability plots; the six shapes do not fit, and doing it properly means new shapes (`Plot`,
  `LayerStack`, `Heatmap`). That is its own phase, and it should be planned after this one has shown
  what the layer costs per topic.
- **Replacing any simulation.** Figures sit above the steps; the labs are untouched.
- **New chrome.** `FigureCard` already borrows the card shell every other section uses — the mock has
  no diagram precedent, so a figure has to read as a card type that already exists.
