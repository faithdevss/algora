# Phase 3 — Interactive Simulation Library

Status: DONE — data-structure sims + gate solver + ClassifierPlayground + PlaybackTransport + recursion-tree (factorial/fibonacci/hanoi/n-queens) + DP grid (8 problems) + sorting visualizer (8 algorithms) + search visualizer (5 algorithms) + tree visualizer (7) + pathfinding grid (3) + hashing visualizer (3) + RL grid-world (4) + bandit explorer (4) + graph algorithm player (6); slider-explorer subsumed. 63 of 176 authored topics now ship a lab.
Depends on: Phase 2

## Goal
Build the reusable simulation widget types referenced across the taxonomy, then attach them to the topics that need them.

## Rough scope
- Linked list / stack / queue visualizer (canvas, operation buttons)
- Graph builder + animated BFS/DFS traversal (state legend: Idle/Current/Visited/Queue/Active)
- Recursion tree / call-stack visualizer w/ playback transport (reset, step-back, play/pause, step-forward, speed slider)
- DP tabulation grid — animated cell-by-cell fill + traceback highlighting
- Generic slider-driven parameter explorer (reused later for e.g. linear regression)
- Classifier "gate solver" (AND/OR/XOR) w/ weight/bias sliders and pass/fail detection
- Wire each sim into its target topics from Phase 2's placeholders

## What actually got built (this session)

Research confirmed only linked list, graph+BFS/DFS, and a slider-driven explorer (regression — an
AI-mode widget) are actually specified anywhere (mock or `docs/features.md`); recursion-tree/DP
grid/gate solver have zero spec and no current DSA topic needs them. Scoped this session to the
4 widgets that are mock-specified (or a close extension of the existing `ArraySimulationSection`
pattern) **and** have a real, already-authored Phase 2 topic waiting for them:

- `LinkedListSimulationSection.kt` — mirrors the mock's `simLinked` exactly: box-chain visual,
  violet/amber/green node states, animated 420ms/step linear search walk. Wired to
  `singly_linked_list`.
- `StackSimulationSection.kt` / `QueueSimulationSection.kt` — not mock-specified (mock never built
  a Stack/Queue topic), designed to match the same visual language (box gradients, `SimColors`
  palette). Wired to `stack` / `queue`.
- `GraphSimulationSection.kt` — mirrors the mock's `simGraph`: circular node layout (not
  force-directed), Add Node/Link controls, BFS/DFS as a precomputed snapshot sequence played back
  at 600ms/tick, 4-swatch legend. Wired to both `graph` and `bfs` (BFS topic reuses the same
  graph-builder-plus-traversal widget rather than getting a separate single-purpose one).

`core/ui/theme/Color.kt` gained a `SimColors` object consolidating the `btn*` palette that was
previously duplicated as private vals in `ArraySimulationSection.kt` — all 4 new widgets and Array
now share it. `SimulationType` (in `TopicContent.kt`) is a sealed interface with one `data object`
per widget; `TopicDetailScreen.kt`'s simulation branch is an exhaustive `when`, so a future widget
type is a compiler-enforced reminder to wire it in.

All 5 wired topics (Array, Singly Linked List, Stack, Queue, Graph, BFS) were manually verified on
an emulator: insert/delete/search on the linked list (including the animated found-highlight), and
add-node/link/BFS/DFS on the graph (confirmed node colors animate through queue→current→visited
and status/counters update correctly, ending at "Complete").

## Deferred to a later session
Recursion-tree/call-stack player (needs full playback transport — reset/step-back/play/pause/
step-forward/speed-slider — richer than anything currently built), DP tabulation grid (needs a
concrete DP problem chosen to visualize), and the generic slider-driven parameter explorer. None
of these have a current DSA topic to attach to; build them once their target topics exist or their
design is worked out from scratch, since the mock provides no reference.

> **Stale note.** The "no current topic to attach to" claim is no longer true — see the build plan
> at the bottom of this file. Recursion and DP topics already exist on `NotYetAvailable`.

## Gate solver — status correction (this note supersedes the deferral above)

The **AND/OR/XOR gate solver is built and shipped.** Phase 5 implemented it as
`PerceptronSimulationSection.kt` — its own header reads *"the classifier-gate solver from the Phase
3 scope, applied to The Perceptron."* It has the full gate selector, w₁/w₂/bias sliders,
decision-boundary canvas, truth table, and pass/fail detection (`correct/4` + XOR-not-separable
message). Wired as `SimulationType.PerceptronVisualizer` → attached to the `perceptron` topic
(`PerceptronContent.kt`). On-device verified in Phase 5.

What's **not** done is the Phase 3 *intent*: a **reusable** widget (its own `SimulationType`)
attachable to multiple topics. It's currently perceptron-specific, and ~150 topics still sit on
`SimulationType.NotYetAvailable`. Plan below generalizes it.

## Generalization plan — reusable ClassifierPlayground

Goal: extract the gate solver into a config-driven `ClassifierPlaygroundSection`, then reuse it for
other linear-classifier topics. Perceptron's current on-screen view must stay byte-identical (it's
already verified — don't regress it).

### Step 0 — Extract the core
- Pull the gate/weights/bias/canvas/truth-table logic out of `PerceptronSimulationSection` into a
  new `feature/topics/ClassifierPlaygroundSection.kt`.
- Config = data class: dataset (list of points + target labels), classifier fn (params → predicted
  label), slider specs (label + range), axis ranges, success message, optional teaching note.
- Rewrite `PerceptronSimulationSection()` as a thin wrapper that calls `ClassifierPlaygroundSection`
  with the AND/OR/XOR gate config. Zero visual change to the perceptron screen.
- Reuse the existing `Surface`(18dp radius, `outline` border) chrome + `SimColors` palette already
  in that file.

### Step 1 — New SimulationType + exhaustive wiring
- Add `data object ClassifierPlayground : SimulationType` to the sealed interface (`TopicContent.kt`).
- `TopicDetailScreen.kt`'s `when` is exhaustive → compiler forces the new branch.
- Add the label to `SimulationsScreen.kt`'s map (e.g. "Classifier playground").

### Step 2 — Config resolution (the one architectural choice)
`SimulationType` entries are param-free `data object`s, so the type can't carry per-topic config.
Resolve config by **topicId**, mirroring `AnalysisToolRegistry`'s id→composable pattern: a
`classifierConfigFor(topicId): ClassifierConfig` map in the section file. Keeps `SimulationType`
param-free and consistent with how every other sim resolves (no args). (Alternative — make it
`data class ClassifierPlayground(val configId: String)` — is rejected: it breaks the param-free
convention and the `data object` equality the `when` relies on.)

### Step 3 — Configs + attach to reuse targets
- **Logistic Regression** (`logistic_regression`): two gaussian blobs, sigmoid boundary, weight/bias
  sliders; success = accuracy over a threshold.
- **SVM** (`svm`): two blobs, linear boundary + margin lines; w/b sliders; surface the margin width.
- **Perceptron** (`perceptron`): existing gate config, unchanged.
- Flip these topics' `simulation =` from `NotYetAvailable` to `ClassifierPlayground`.
- **KNN / Decision Trees are a poor fit** (non-linear / non-parametric — no single tunable straight
  boundary). Skip them for this widget; they'd need a separate sim. Don't force them in.

### Step 4 — Verify
- `./gradlew assembleDebug`.
- Emulator: perceptron screen pixel-unchanged (regression check); logistic_regression + svm render
  their blobs with a tunable decision boundary and live accuracy/margin readout.

---

## Build plan — remaining sims (recursion tree + DP grid)

The two remaining sim widgets **do** have target topics now (the deferral note above is stale, same
pattern as the gate solver). Confirmed ids on `NotYetAvailable`:

- **Recursion tree / call-stack:** `factorial`, `fibonacci_recursive`, `tower_of_hanoi`, `n_queens`
- **DP tabulation grid:** `fibonacci_dp`, `coin_change`, `knapsack_01`, `edit_distance`,
  `longest_common_subsequence`, `rod_cutting`, `matrix_chain_multiplication`,
  `longest_increasing_subsequence`

Two reusable widgets light up ~12 dead topics. Both follow the conventions already in the codebase:
**precomputed-snapshot playback** (like `GraphSimulationSection`'s BFS/DFS sequence) and
**config-resolved-by-topicId** (like `ClassifierPlayground` / `AnalysisToolRegistry`). Reuse the
`Surface`(18dp radius / `outline` border) chrome and `SimColors`.

### Step 0 — Shared playback transport (Phase 3 scope item, still unbuilt)
- Add `PlaybackTransport` to `SimComponents.kt`: reset ⏮ · step-back · play/pause · step-forward ⏭ +
  speed slider. Auto-advance driven by a `LaunchedEffect(playing, speed)` tick.
- Model: a sim is a `List<Step>` + a current index. `PlaybackTransport` owns index/playing state and
  calls back on change; the widget renders `steps[index]`. Both new widgets (and any future stepped
  sim) share it.

### Sub-phase A — Recursion tree / call-stack (`RecursionTreeVisualizer`)
- New `RecursionTreeSection.kt`. Config = `RecursionConfig(nRange, buildSteps: (n) -> List<RecStep>)`.
  A `RecStep` snapshots: active call, the call stack (list), and each tree node's state
  (pending / active / returned + return value).
- Canvas: tree laid out by depth × sibling order (precomputed, not force-directed — mirror the graph
  sim's fixed-layout choice); a call-stack column beside it; current return value.
- Ship **`factorial`** (linear chain) and **`fibonacci_recursive`** (binary tree, cap n ≤ 6 so the
  tree stays legible) first — clean, small trees.
- `tower_of_hanoi` / `n_queens` trees explode; defer to sub-phase C with a hard input cap (e.g. 3
  disks, 4×4 board) or attach later.

### Sub-phase B — DP tabulation grid (`DpGridVisualizer`)
- New `DpGridSection.kt`. Config = `DpConfig(rows, cols, axisLabels, buildFill: -> List<CellFill>,
  traceback: List<Cell>, cellLabel)`. `CellFill` = (r, c, value); fill order is the DP recurrence
  order.
- Canvas/grid: animate cell-by-cell fill via `PlaybackTransport`; after the last fill step, highlight
  the traceback path.
- Ship **`fibonacci_dp`** (1-D row) and **`edit_distance`** (classic 2-D with traceback) first; then
  `longest_common_subsequence`, `coin_change`, `knapsack_01` via added configs. 1-D problems render
  as a single row, 2-D as a matrix — same widget.

### Sub-phase C (stretch) — capped recursion trees
- `tower_of_hanoi` (3 disks), `n_queens` (4×4) with hard input caps so the backtracking tree stays
  renderable. Reuse sub-phase A's widget + transport.

### Wiring (each sub-phase)
- Add the `SimulationType` data object(s) — exhaustive `when` in `TopicDetailScreen` is
  compiler-enforced. Add `SimulationsScreen` labels.
- `recursionConfigFor(topicId)` / `dpConfigFor(topicId)` registries.
- Flip each shipped topic's `simulation =` off `NotYetAvailable`.

### Generic slider-explorer — close as subsumed
The third deferred item is **already covered** by `RegressionExplorer` and `ClassifierPlayground`
— both are slider-driven parameter explorers. No distinct target topic needs a separate generic
explorer. Recommend marking it done-by-reuse unless a non-classifier slider target appears; don't
build a fourth near-duplicate.

### Verify
- `./gradlew assembleDebug`.
- Emulator: factorial recursion plays through with working transport (reset / step / play / speed);
  edit_distance grid fills cell-by-cell then highlights the traceback.

## Sub-phase D/E/F — sorting + search visualizers, DP completion (this session)

Coverage pass: the widgets above only reached ~20 topics, leaving the two largest
Algorithms families (sorting, searching) on `NotYetAvailable` despite being the most
visual algorithms in the taxonomy. Both follow the established conventions —
precomputed frames + `PlaybackTransport`, config resolved by `topicId`.

- **`SortingVisualizerSection.kt`** (`SimulationType.SortingVisualizer`) — bar chart over 8 values;
  each frame carries the array plus role sets (compared / moved / sorted / range). Bar colours read
  those roles, so one renderer serves every algorithm. Configs: `bubble_sort`, `selection_sort`,
  `insertion_sort`, `merge_sort`, `quick_sort`, `heap_sort`, `counting_sort`, `radix_sort`. The
  `range` role is per-algorithm (merge window, quicksort partition, heap region, sorted prefix,
  digit-pass result) and its legend label comes from the config, so it is omitted where meaningless.
  Counting/radix use their own small-value inputs since their cost model is value-range driven.
- **`SearchVisualizerSection.kt`** (`SimulationType.SearchVisualizer`) — index-labelled cell strip;
  frames carry probe / live window / eliminated / found. Configs: `linear_search` (unsorted input,
  the one algorithm with no ordering precondition), `binary_search`, `jump_search`,
  `interpolation_search`, `exponential_search`.
- **DP grid completed** — added `rod_cutting`, `longest_increasing_subsequence` (1-D, no traceback),
  and `matrix_chain_multiplication` (interval DP: fills along diagonals, cells below the diagonal
  stay empty). All 8 DP topics listed in the build plan above now have a config.
- The DP legend previously showed "Traceback" for any 2-D table; it now derives from whether the
  frames actually contain traced cells (matrix chain has none).

Verified on-device: bubble sort plays through 51 frames with correct swap highlighting; binary
search plays to "Found 50 at index 6 — 4 probes"; matrix chain fills diagonally to dp[0][2] = 4500
(10·30·5 + 10·5·60).

**Remaining after this pass:** see sub-phase G below.

## Sub-phase G — tree visualizer (this session)

`TreeVisualizerSection.kt` (`SimulationType.TreeVisualizer`) covers every tree-shaped structure with
one player. The key difference from `RecursionTreeSection` (fixed node set, only states animate) is
that **each frame carries its own node list**, so insertions, AVL rotations and B-tree splits are
just different parent links between consecutive frames. A `TreeBuilder` mutates a live tree and
snapshots it per `frame()` call.

Layout reuses the recursion tree's leaf-slot algorithm (leaves take sequential x slots, parents
centre over their children), with two additions: siblings sort by an explicit `order` field so a
BST's left child stays left even when inserted after the right one, and row spacing is **fixed**
(capped to fit) rather than stretched to the canvas — otherwise a 2-level frame and a 4-level frame
of the same tree rendered at wildly different scales as nodes appeared. Nodes draw as rounded pills
sized to their text, so multi-key B-tree labels ("5 · 10") fit the same renderer as a single digit.

Configs: `binary_search_tree` (7 inserts + a search walk), `tree` (DFS then BFS over a directory
hierarchy), `heap` (array-backed insert with sift-up swaps), `trie` (three words sharing prefixes,
then a lookup), `avl_red_black_tree` (ascending inserts → left rotation), `segment_tree` (bottom-up
sum build → range query touching two nodes), `b_tree` (order-3 inserts through a median split).

`fenwick_tree` is deliberately **not** wired: its tree is implicit in index arithmetic, not in
parent links, so this widget would misrepresent it. It needs its own array-with-jump-arcs view.

Verified on-device: BST builds the correct shape and reports "Found 60 — 3 comparisons"; trie ends
with c→a→{t∎, r∎} plus d→o→g∎ and highlights "car"; B-tree splits to root 20 over leaves "5 · 10"
and "30 · 40".

**Remaining after that pass:** see sub-phase H below.

## Sub-phase H — pathfinding grid + hashing visualizer (this session)

- **`PathfindingGridSection.kt`** (`SimulationType.PathfindingGrid`) — a 6×8 walled grid with a
  single best-first search loop parameterised by heuristic: zero heuristic = Dijkstra, Manhattan
  distance = A*. One frame per expansion, then the path walk-back. The wall column has one gap, so
  Dijkstra visibly wastes expansions on the wrong side of it while A* aims straight at the goal —
  the frame counts in the closing status line are the comparison. `d_star_algorithm` chains three
  searches: plan, obstacle discovered on the committed path, repair.
- **`HashingVisualizerSection.kt`** (`SimulationType.HashingVisualizer`) — "array of slots plus a
  rule for which slot a key lands in" covers all three targets. `hash_table` (7 buckets, separate
  chaining, a real collision, a hit and a miss), `bloom_filter` (12 bits, two hashes per key,
  built so a never-added word lands on bits other keys already set — a genuine false positive), and
  `lru_cache` (capacity 4 by recency: promotion on hit, eviction on insert). Chained entries render
  as `a → b` in one row.
- Slot layout is configurable: chains and recency lists get one row each (they have contents to
  show), a bit array gets a single horizontal strip. The first build rendered the bloom filter as 12
  stacked rows, which ate the whole viewport for one bit of information each.

Also fixed: grid cells were sized `weight(1f).fillMaxWidth()`, giving them no height, so the first
build rendered an invisible grid — only the S/G text pills showed. Cells now `fillMaxHeight()` and
take their height from the grid's aspect ratio.

Verified on-device: Dijkstra expands 37 cells for a 9-step path with the wall gap correctly forcing
the detour; the bloom filter ends on "probably present" with the false-positive bits highlighted.

**Remaining after that pass:** 127 topics still resolved to `SimulationComingSoonCard`.

## Sub-phase I — RL grid-world + bandit explorer (`RlGridWorldSection.kt`)

`SimulationType.RlGridWorld` wired to `grid_world`, `mdp`, `q_learning`, `dyna_q` — an environment
loop rather than a data-structure animation, which is what the AI remainder needs.
`SimulationType.BanditExplorer` wired to `epsilon_greedy`, `ucb`, `thompson_sampling`,
`boltzmann_exploration` — arm pulls, running value estimates, exploration/exploitation split.
Both render via `RlGridWorldSection(topicId)` / `BanditSection(topicId)` from `TopicDetailScreen`.

## Sub-phase J — graph algorithm player (`GraphAlgorithmSection.kt`)

`SimulationType.GraphAlgorithmPlayer`, wired to six topics that all animate over a node-link diagram
but needed weights, direction, per-node badges and edge states that the Phase-3 `GraphVisualizer`
(an unweighted BFS/DFS *builder*) has no room for. One renderer, four fixed graphs, six frame
builders:

| Topic | Graph | What the frames show |
|---|---|---|
| `bellman_ford` | 5-node directed, negative edges | distance badges per node; only edges that actually relax get a frame; final sweep is the negative-cycle test (14 frames) |
| `floyd_warshall` | 4-node directed | the dist matrix under the graph, one round per intermediate node, improved cell in green with the k row/col tinted (17) |
| `kruskals_mst` | 6-node undirected weighted | edges in sorted order, accept vs. cycle-reject, nodes coloured by union-find component (8) |
| `prims_mst` | same graph as Kruskal | cut edges as candidates, then the cheapest one absorbed — ends at weight 17, same tree Kruskal builds (12) |
| `tarjans_algorithm` | 7-node directed, 3 SCCs | index/low-link badges, stack contents in the status line, component pops (16) |
| `kosarajus_algorithm` | same graph | finish-order badges, then the renderer flips every arrow for the transpose pass (13) |

Renderer details worth keeping: anti-parallel edge pairs (A→B alongside B→A) are nudged off the
centre line or their arrowheads and weights land on top of each other; `GroupColors[0]` is sky blue
rather than indigo so a node's first component assignment reads as a change against the idle violet;
Floyd is the only config that draws a matrix, which the frame carries as an optional field rather
than the section branching on topic id.

Verified on-device: Prim reaches weight 17 in 12 steps, Floyd's initial matrix matches the edge list
and `B→D via A = 15` highlights correctly, Kosaraju's second pass flips the arrows and pulls
{A, B, C} out as one component.

**Remaining (audited 2026-07-26):** **113 of 176** topics resolve to `SimulationComingSoonCard`;
63 ship a lab. By category:

| Category | pending | wired |
|---|---|---|
| Reinforcement Learning | 51 | 8 |
| Algorithms | 20 | 36 |
| Data Structures | 12 | 15 |
| Deep Learning | 10 | 1 |
| NLP | 8 | 0 |
| Machine Learning | 7 | 3 |
| Interview Prep | 5 | 0 |

RL is still the bulk (51) — the grid-world/bandit widgets cover tabular methods, but deep-RL topics
(DQN family, policy gradients, model-based, multi-agent) each want a training-curve or environment
loop that does not exist yet. Cheapest remaining wins reuse existing renderers: array-walk topics
(`prefix_sum`, `difference_array`,
`sliding_window`, `two_pointer`, `kadanes_algorithm`) plus all 5 Interview Prep pattern topics →
ArrayVisualizer; `subset_sum` → DP grid; `doubly_linked_list` → LinkedListVisualizer;
`priority_queue_adt` → TreeVisualizer heap. One new 2D-points clustering widget would cover
`kmeans` / `dbscan` / `knn` / `hierarchical_clustering` at once.
