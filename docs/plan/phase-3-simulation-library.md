# Phase 3 — Interactive Simulation Library

Status: **Widgets done, wiring ongoing.** 25 simulation widgets built; 129 of 176 authored topics
ship a runnable lab, 47 still show the ComingSoon card. Full inventory and remaining work in
"Current state" below.

Depends on: Phase 2

---

## Current state (audited 2026-07-27)

**129 of 176 topics ship a runnable lab; 47 still resolve to `SimulationComingSoonCard`.** Recount
it at any time by walking `TopicContentProvider`'s id→content map and reading each content file's
`simulation =` field — every number in this file comes from that walk, not from memory.

25 widget types, resolved by `topicId` inside each section file:

| Widget | Topics | What it covers |
|---|---|---|
| `PolicyGradientPlayer` | 12 | REINFORCE → PPO, plus continuous control (DPG/DDPG/TD3/SAC) |
| `ArrayWalkPlayer` | 11 | prefix/difference/window/two-pointer walks + all 5 Interview Prep patterns |
| `NeuralNetPlayer` | 9 | forward pass, backprop, activations, optimizers, CNN, autoencoder, GAN, RNN, LSTM |
| `GameSearchPlayer` | 9 | minimax/alpha-beta, MCTS, AlphaGo/Zero, MuZero, self-play, model-based RL |
| `TokenStripPlayer` | 9 | tokenization → attention → transformers → LLM decoding |
| `RlTrainingPlayer` | 9 | the DQN family, measured on a chain MDP |
| `SortingVisualizer` / `DpGridVisualizer` | 8 each | 8 sorts; 8 DP problems |
| `TreeVisualizer` | 7 | BST, heap, trie, AVL/red-black, B-tree, segment tree |
| `PointCloudPlayer` | 7 | k-means, DBSCAN, hierarchical, k-NN, naive Bayes, trees, PCA |
| `GraphAlgorithmPlayer` | 6 | Bellman-Ford, Floyd-Warshall, MSTs, Tarjan, Kosaraju |
| `SearchVisualizer` | 5 | linear → exponential search |
| `RecursionTreeVisualizer` / `BanditExplorer` / `RlGridWorld` | 4 each | recursion; bandits; tabular RL |
| `PathfindingGrid` / `GraphVisualizer` / `HashingVisualizer` | 3 each | Dijkstra/A*/D*; graph builder + BFS/DFS; hashing |
| `ClassifierPlayground` | 2 | logistic regression, SVM |
| Array / LinkedList / Stack / Queue / Regression / Perceptron | 1 each | the Phase 1–5 originals |

**Remaining 47, by category:**

| Category | pending | wired | What is left |
|---|---|---|---|
| Reinforcement Learning | 21 | 38 | multi-agent (3), offline + imitation (8), exploration + meta (4), environments (6) |
| Algorithms | 14 | 42 | selection/randomised (`quickselect`, `median_of_medians`, `reservoir_sampling`, `monte_carlo_method`, `mos_algorithm`), greedy (`huffman_coding`, `fractional_knapsack`, `job_sequencing`), divide-and-conquer (`karatsubas_algorithm`, `strassens_algorithm`, `closest_pair_of_points`), backtracking (`sudoku_solver`, `permutation_generation`, `subset_sum`) |
| Data Structures | 12 | 15 | `disjoint_set`, `doubly_linked_list`, `fenwick_tree`, `graph_variants`, `kd_tree`, `list_adt`, `map_adt`, `priority_queue_adt`, `set_adt`, `skip_list`, `string`, `suffix_tree` |
| Deep Learning / NLP / ML / Interview Prep | 0 | 34 | complete |

**How these labs are built.** Since sub-phase J the rule has been that a frame's narration may only
state numbers the frame's own experiment produced — the search runs, the optimizers step, the
gradients are sampled, the models are learned. That has caught a wrong claim in most batches
(centre-first move ordering, Adam beating tuned momentum, target networks "fixing" oscillation,
noisy nets exploring deeper, monotone GAE variance, MuZero degrading under model error). The
corrections are recorded in each sub-phase below rather than quietly patched.

**Next batch.** Offline + imitation RL (`offline_rl`, `cql`, `iql`, `decision_transformer`, `gail`,
`irl`, `imitation_learning`, `rlhf`): distribution shift, behaviour cloning's compounding error and
a conservative Q penalty are all measurable on the same chain MDP the other RL players use.

> Everything below this line is the historical record: the original scope, the sub-phases in the
> order they were built, and the reasoning (including the corrections) behind each one.

---

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

## Sub-phase K — array walk player (`ArrayWalkSection.kt`)

`SimulationType.ArrayWalkPlayer`, wired to 11 topics: six one-dimensional algorithms and the five
Interview Prep pattern guides, which are the same walks under interview names. One row-of-cells
renderer plus the extras individual algorithms need — a second row (the prefix / difference / heap
being maintained), pointer labels under the cells, a back-edge arc that turns the row into a linked
list with a cycle, and an interval track for merge-intervals. Each frame carries only the parts it
uses.

| Topic | Problem the frames walk |
|---|---|
| `prefix_sum` | build inclusive P, then answer a[2..5] as P[5] − P[1] |
| `difference_array` | three range updates as two writes each, then one prefix pass to rebuild |
| `sliding_window` | fixed k = 3 max sum; each slide subtracts what leaves, adds what enters |
| `two_pointer` | all pairs summing to 14 in a sorted array |
| `kadanes_algorithm` | running "best ending here", restarting when extending is worse |
| `top_k_elements` | top-3 over a stream via a size-3 min-heap |
| `sliding_window_pattern` | variable window: longest substring with no repeat |
| `two_pointer_pattern` | same-direction read/write pointers deduping in place |
| `fast_slow_pointers` | cycle detection, then the second walk that finds the entry node |
| `merge_intervals_pattern` | sort by start, extend or close the open interval |
| `top_k_pattern` | count first, then a size-k min-heap over the counts |

Pointer labels are one per cell, so two pointers landing on the same index collapse to `both` — the
first build wrapped `slow+fast` onto two lines and pushed the loop-back arc into the row above.

Verified on-device: the prefix-sum query frame reads `a[2..5] = P[5] − P[1] = 23 − 4 = 19` with the
two aux cells highlighted, and the fast/slow list meets at F then walks back to D, the true cycle
entry.

## Sub-phase L — point cloud player (`PointCloudSection.kt`)

`SimulationType.PointCloudPlayer`, wired to the 7 remaining ML topics, which are all "points on a
plane plus one overlay": clustering, the two geometric classifiers, the tree's axis-aligned splits,
and PCA's principal axis. Shared scatter renderer; each frame carries only the overlay it needs
(centroids, ε rings, split lines, shaded leaf regions, projection residuals).

| Topic | What the frames walk |
|---|---|
| `kmeans` | deliberately lopsided seeding, then assign/update until movement stops — converges in 4 iterations and recovers the 7/7/7 blobs |
| `dbscan` | ε = 0.22, minPts = 3: core vs border vs noise, cluster expansion by chaining — ends 2 clusters, 2 noise points |
| `hierarchical_clustering` | single-linkage merges, closest pair each step, merge order as the dendrogram |
| `knn` | k = 5, radius grows to the k-th neighbour, running vote tally |
| `naive_bayes` | per-class mean/variance, then a near-boundary query scored as log prior + log p(x) + log p(y) → 74%, not 100% |
| `decision_trees` | greedy gini splits to depth 2 → 3 leaves, with the chosen threshold and impurity drop in the status |
| `pca` | centre, covariance matrix, top eigenvector by the 2×2 closed form, projections and residuals — PC1 keeps 98% |

Datasets come from a fixed-seed LCG so the narration in each frame stays true across launches. The
decision-tree set is hand-placed instead: the LCG's jitter is too correlated to reliably produce a
layout where neither a single x cut nor a single y cut suffices, and without that the depth-2 story
would be a lie — the first version separated the classes in one cut.

## Sub-phase M — token strip player (`TokenStripSection.kt`)

`SimulationType.TokenStripPlayer`, wired to all 8 remaining NLP topics plus `transformers` (which
the registry resolves to its Deep Learning listing). Four shared building blocks — a chip row for
tokens with an optional sub-label, a signed bar chart for any vector, a token×token heat grid, and
label/value rows for the arithmetic — and each frame picks the ones it needs.

| Topic | What the frames walk |
|---|---|
| `tokenization` | string → whitespace words → subword pieces for the two OOV words → vocabulary ids (4 words → 9 tokens) |
| `stemming` | suffix rules in order; "studies" → "studi" is not a word, and "better" never reaches "good" |
| `lemmatization` | POS tag + dictionary; final frame puts stem and lemma side by side |
| `bow_tfidf` | counts → df → idf → tf-idf; "the" is in every document so its idf is 0 |
| `word_embeddings` | cosine king·queen 0.72 vs king·apple −0.74, then king − man + woman landing on queen at 1.00 |
| `rnn_lstm` | h = tanh(x + 0.6h) token by token, then 0.6⁵ = 0.08 decay against an LSTM forget gate's 0.77 |
| `attention` | q·k/√d scores → softmax (sat puts 71% on cat) → the full n² matrix as the architecture's cost |
| `transformers` | attention + FFN + residual, block 2 running on block 1's output with a sharper pattern |
| `llms` | logits → softmax (94% Paris) → temperature 0.5 vs 1.8 → append and repeat |

Every number in the narration is computed from the vectors on screen, which caught two frames that
would otherwise have lied: the attention softmax came out nearly uniform (29% on "cat") until the
q/k dot products were scaled — readable toy vectors have far smaller magnitudes than trained ones —
and "apple" scored a *higher* cosine with "king" than "queen" did, because every component of the
hand-set royalty vectors was positive. Apple now points away on two dimensions.

## Sub-phase N — neural net player (`NeuralNetSection.kt`)

`SimulationType.NeuralNetPlayer`, wired to the 9 remaining Deep Learning topics. Four render parts
mixed per frame: a layered node diagram, labelled curve plots on shared axes, small matrices with a
highlighted window, and signed bar vectors.

| Topic | What actually runs |
|---|---|
| `neural_network_basics` | a real 2-3-1 forward pass, unit by unit — one hidden unit lands negative and ReLU silences it |
| `backpropagation` | δ at the output, chain rule back through ReLU, one weight step, then a re-run showing loss 0.0346 → 0.0335 |
| `activation_functions` | σ, σ', ReLU, tanh, GELU sampled; the vanishing-gradient frame is 0.25¹⁰ |
| `gradient_descent_variants` | SGD / momentum / Adam run for 40 steps on a 50:1 ill-conditioned quadratic |
| `cnn` | a Sobel kernel convolved over a 6×6 edge image, then 2×2 max pooling |
| `autoencoders` | 6 → 2 → 6 through an orthonormal basis (a linear autoencoder's optimum *is* the PCA subspace), reconstruction error 0.076 |
| `gans` | generated histogram closing on the real one, with D and G losses crossing as it does |
| `rnn` | one shared weight over 4 timesteps, then u⁸ as the BPTT decay, vs u > 1 exploding |
| `lstm_gru` | gate values per step including one deliberate wipe, then 0.92ⁿ retention vs the RNN's 0.6ⁿ |

The optimizer lab was rewritten after the numbers came back: with a shared learning rate, plain SGD
*beat* both momentum and Adam, which is the opposite of what the first draft's narration claimed.
Each optimizer now runs at a rate it can actually use (SGD 0.09, just under its 2/L = 0.1 stability
limit; momentum 0.012, because accumulation multiplies the effective step by 1/(1−β) ≈ 10; Adam
0.2), and the closing frame states the honest outcome — tuned momentum wins on a clean quadratic,
Adam does not, and Adam's advantage lies in noisy, badly scaled, or untuned settings this problem
does not have.

## Sub-phase O — RL training player (`RlTrainingSection.kt`)

`SimulationType.RlTrainingPlayer`, wired to the 9 DQN-family topics. Every curve is produced by an
experiment run when the frames are built — tabular Q-learning on a six-state chain (sparse reward at
the far end), the classic overestimation MDP averaged over 40 runs, and replay buffers sampled
uniformly or by TD error.

| Topic | Measured result |
|---|---|
| `dqn` | online 12 episodes to converge → 4 with replay |
| `experience_replay` | same interactions, 4× the updates: 12 → 3 episodes |
| `target_networks` | see below — the honest result is that it *costs* speed here |
| `double_dqn` | peak overestimate 0.095 → 0.002; worthless action taken 45% → 7% of episodes |
| `dueling_dqn` | V spans 0.81–1.00 while |A| tops out at 0.049, and A must be zero-mean to be identifiable |
| `prioritized_replay` | uniform 5 → prioritized 3 episodes |
| `noisy_nets` | see below |
| `c51` | two actions, near-equal means, completely different return distributions |
| `rainbow_dqn` | components measured one at a time: 11 → 4 → 3 episodes |

Two labs were rewritten after the experiments contradicted the draft narration:

- **Target networks.** The first version claimed a moving target oscillates and a frozen one
  converges. It does not: the scalar bootstrap contracts, and every small linear variant tried
  (including an aliased two-state one) converges to the TD fixed point. What the chain actually
  shows is that a target network is *pure cost* on a tabular problem — 12 episodes with no target
  network, 18 at lag 20, 31 at lag 50. So the lab now says that, then demonstrates the real reason
  DQN needs one: give the chain a single shared weight and one update at s4 moves the predicted
  value of all six states, including its own target. It closes by naming the target network what it
  is — an empirical stabiliser for nonlinear approximation, not a convergence guarantee.
- **Noisy nets.** The draft claimed per-episode parameter noise explores deeper. Measured, it does
  not: ε-greedy reaches the goal in 60/60 episodes, parameter noise in 51/60 — but when it does
  reach, it takes 5.0 steps against ε-greedy's 6.9, and its start-state visit count balloons from 91
  to 411. The lab now tells that story (commitment is a bet: straighter runs, and whole episodes
  wasted on a bad draw) and puts the real argument where it belongs — the noise scale is learned,
  so there is no ε schedule to tune.

## Sub-phase P — policy gradient player (`PolicyGradientSection.kt`)

`SimulationType.PolicyGradientPlayer`, wired to the 12 policy-gradient and continuous-control
topics. The recurring measurement is the standard deviation of **one** gradient component across 300
independent rollouts of an unchanged policy — the quantity this entire family exists to shrink.
Continuous control runs on a 1-D action with a known reward curve, so the true optimum is available
to compare against.

| Topic | Measured |
|---|---|
| `reinforce` | sd 0.303 around a mean of 0.214 — the noise is 1.4× the signal |
| `actor_critic` | return 0.303 → baseline 0.249 → TD critic 0.120 |
| `a2c` | 1 / 8 / 16 actors → sd 0.298 / 0.112 / 0.080, against √8's predicted 0.105 |
| `a3c` | same reduction as A2C (it comes from averaging, not asynchrony) plus the staleness cost |
| `gae` | λ = 0 · 0.5 · 0.9 · 0.95 · 1 → sd 0.12 / 0.09 / 0.15 / 0.19 / 0.25 |
| `trpo` | lr 0.4 → max KL 0.03; lr 20 → max KL 5.45 and the return collapses to −0.26 |
| `ppo` | clipping holds max KL to 0.004 at lr 20 and still finishes at 0.63 |
| `dpg` | ∂Q/∂a climbs from a = −0.80 to the optimum with no action sampling at all |
| `ddpg` | the actor selects for critic noise: +0.16 bias at the chosen action |
| `td3` | min of twin critics cuts that bias to ≈0, deliberately erring low |
| `sac` | optimal σ per temperature solved exactly for a Gaussian policy |
| `max_entropy_rl` | Boltzmann policy at several α, and the reward each one gives up |

Three narrations were rewritten after the experiments disagreed with them:

- **GAE.** The draft said variance rises monotonically with λ. It does not here: λ = 0.5 has the
  *lowest* spread, below λ = 0. The textbook picture assumes an accurate critic, and this one is a
  Monte-Carlo estimate whose own error goes straight into the gradient at λ = 0. The lab now shows
  the measured curve and explains the discrepancy instead of hiding it.
- **TRPO / PPO.** With noise-free advantages, an over-large step did not hurt at all on this chain —
  it converged *faster* than the safe one, which would have made the whole trust-region argument a
  fiction. Advantage estimates are now noisy (as the earlier labs measured them to be), and at lr 20
  the policy genuinely collapses to −0.26 while clipping keeps it at 0.63.
- **Baseline unbiasedness.** "The mean is unchanged" became a statement about the expected gradient
  plus the measured 0.03 difference, explicitly labelled sampling noise over 300 rollouts.

## Sub-phase Q — game search player (`GameSearchSection.kt`)

`SimulationType.GameSearchPlayer`, wired to the 9 search and model-based topics. The search labs run
a real tic-tac-toe engine — minimax and alpha-beta count their own nodes, MCTS actually plays
rollouts — and the model-based labs learn a tabular model of the six-state chain from a limited
number of real transitions, so its errors are real errors.

| Topic | Measured |
|---|---|
| `minimax` | 186 nodes → 88 with alpha-beta → 28 with tactical ordering (and 120 with a *bad* ordering) |
| `mcts` | 30 / 120 / 600 simulations put 47% / 68% / 88% of the search on the move minimax proves best |
| `alphago` | plain 70% → +value net 72% → +policy prior 82% of the budget on the best move |
| `alphazero` | a 22% prior comes back out of search as a 91% visit distribution — search as policy improvement |
| `muzero` | corrupting 0/20/40/60/80% of evaluations → 100/100/97/90/80% correct move |
| `self_play` | fictitious play on RPS: latest strategy stays maximally exploitable, the average converges to Nash |
| `world_models` | 8 → 120 real steps take model coverage 50% → 100%; a sparse model is 3 states wrong after 5 imagined steps |
| `dreamer` | 20 imagined updates per real step: 11 → 2 episodes to converge |
| `mbpo` | compounding error by rollout length, and what more planning volume actually buys |

Three claims were corrected against the measurements:

- **Move ordering.** The draft assumed centre-first ordering would help. It makes pruning *worse*
  here — 120 nodes against 88 unordered — while ordering by immediate wins and blocks drops it to
  28. Both numbers are now in the lab, because "a wrong guess delays the cutoff instead of causing
  it" is the more useful lesson.
- **AlphaGo's value network.** On a 3×3 board it barely helps (70% → 72%), because random playouts
  are already a decent estimate at that size. The lab says so and points at where the gain actually
  comes from at full board size.
- **MuZero's model error.** At the drafted 120-simulation budget, search picked the right move 100%
  of the time at *every* corruption level — the degradation claim was unsupported. Corrupting
  terminal evaluations too and dropping to 30 simulations produces a real curve, and the frame now
  also states the honest surprise: search tolerates a lot of model noise before it breaks.

**Remaining (audited 2026-07-27):** **47 of 176** topics resolve to `SimulationComingSoonCard`;
129 ship a lab.

| Category | pending | wired |
|---|---|---|
| Reinforcement Learning | 21 | 38 |
| Algorithms | 14 | 42 |
| Data Structures | 12 | 15 |
| Deep Learning | 0 | 11 |
| NLP | 0 | 8 |
| Machine Learning | 0 | 10 |
| Interview Prep | 0 | 5 |

RL's remaining 21: multi-agent (`maddpg`, `qmix`, `vdn`), offline and imitation (`cql`, `iql`,
`offline_rl`, `decision_transformer`, `gail`, `irl`, `imitation_learning`, `rlhf`), exploration and
meta (`icm`, `rnd`, `intrinsic_motivation`, `meta_rl`), and the six environment topics (`cartpole`,
`mountain_car`, `atari`, `mujoco`, `dota2`, `starcraft`). Offline/imitation is the natural next
batch: distribution shift, behaviour cloning's compounding error and a conservative penalty are all
measurable on the chain the other RL players already use.

RL's remaining 42 split into policy-gradient/actor-critic (≈12), model-based and search (≈9),
multi-agent + offline + imitation (≈12), exploration and meta (≈5), and the environment topics
(cartpole, mountain_car, atari, mujoco, dota2, starcraft). The policy-gradient family is the natural
next batch: REINFORCE's variance, a baseline, GAE, and PPO's clipped ratio are all measurable on the
same kind of toy MDP this player already runs.

RL is still the bulk (51) — the grid-world/bandit widgets cover tabular methods, but deep-RL topics
(DQN family, policy gradients, model-based, multi-agent) each want a training-curve or environment
loop that does not exist yet. Cheapest remaining wins reuse existing renderers: `subset_sum` → DP
grid; `doubly_linked_list` → LinkedListVisualizer; `priority_queue_adt` → TreeVisualizer heap;
`quickselect` / `median_of_medians` / `mos_algorithm` / `reservoir_sampling` → array walk player.
RL's 21 are still the bulk of what is left: grid-world, bandit, DQN-family, policy-gradient and
game-search players now cover tabular methods, value-based deep RL, continuous control, search and
model-based, leaving multi-agent, offline/imitation, exploration and the environment topics. The 14 Algorithms and 12 Data
Structures stragglers are mostly one-off structures (`skip_list`, `suffix_tree`, `kd_tree`,
`fenwick_tree`) that each need their own renderer.
