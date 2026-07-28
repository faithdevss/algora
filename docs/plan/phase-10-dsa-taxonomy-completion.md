# Phase 10 — DSA Taxonomy Completion

Status: Planned — not started.
Depends on: Phase 2 (topic browser + content template), Phase 3 (simulation widgets), Phase 8 (premium gating).

## Goal

Close the gap between `docs/topics.algo.md` — the DSA/algorithms taxonomy source list — and what the
Algorithms section actually ships. The doc lists **92 entries across 11 headings**; the app carries
**67 algorithm topics** in 9 categories, and covers most of the doc through those plus 29 Data
Structures topics. What is missing is not scattered stragglers: **four entire doc headings have no
app representation at all** (Math & Number Theory, Bit Manipulation, Computational Geometry, and
String Algorithms as a browsable group).

This phase authors **27 new topics** and adds **4 categories**, taking Algorithms from 67 to 94
topics and the whole app from 257 to 284.

## Baseline coverage (measured 2026-07-28)

| Doc heading | Doc entries | Covered today | Covered by | Missing |
|---|---|---|---|---|
| Sorting | 9 | 9 | `algo_sorting` | **0** |
| Searching | 5 | 5 | `algo_searching` | **0** |
| Recursion & Backtracking | 6 | 6 | `algo_recursion` | **0** |
| Divide and Conquer | 6 | 6 | `algo_divide_conquer` (+ sorting, searching) | **0** |
| Greedy Algorithms | 8 | 6 | `algo_greedy` | **2** |
| Dynamic Programming | 11 | 10 | `algo_dp` (+ `algo_graph` for the two shortest-path entries) | **1** |
| Graph Algorithms | 16 | 14 | `algo_graph`, `algo_greedy`, `algo_pathfinding`, `ds_advanced` | **2** |
| Math & Number Theory | 7 | 0 | — | **7** |
| String Algorithms | 11 | 6 | `algo_misc` (KMP, Rabin-Karp, Manacher), `ds_nonlinear`/`ds_advanced` (Trie, Suffix Tree) | **5** |
| Bit Manipulation | 4 | 0 | — | **4** |
| Computational Geometry | 5 | 1 | `algo_divide_conquer` (Closest Pair) | **4** |
| Graph Search & Pathfinding | 4 | 2 | `algo_pathfinding` | **2** |
| **Total** | **92** | **65** | — | **27** |

"Covered" counts a doc entry satisfied by *any* app topic, including cross-section reuse — the doc's
graph block leans on `disjoint_set` and `suffix_tree` from Data Structures, and its Divide-and-Conquer
block is half-satisfied by Sorting and Searching topics.

The app is a **superset** everywhere else: 17 algorithm topics ship that the doc never lists
(`quickselect`, `median_of_medians`, `tower_of_hanoi`, `max_flow`, `articulation_points`, `lca`,
`bitmask_dp`, `tree_dp`, and the whole `algo_misc` group). Nothing in this phase removes them.

### Two entries deliberately scored as already covered

- **Kahn's Algorithm** — no standalone topic, but `topological_sort`'s simulation *is* Kahn's
  in-degree sweep (`GraphAlgorithmSection`, added in the 2026-07-28 expansion) and its content names
  it against the DFS alternative. A second topic would duplicate one page.
- **Longest Palindromic Substring** — the doc lists it beside Manacher's. `manacher` covers the
  linear-time answer, but not the expand-around-centre / DP framing a learner meets first, so this
  phase **does** add it. Scored missing in the table above.

## Decisions taken before planning

**1. Four new categories, restoring the mock's own collapsed numbering.** `docs/design/Algora.dc.html`
labels the last algorithms group **`'9–11 · Miscellaneous & Advanced'`** — the mock itself records
that it folded three taxonomy groups into one bucket rather than that those groups do not exist.
Adding them back is following the mock's numbering, not departing from it. This is the same
precedent Phase 9 set for ML/DL/NLP, where the doc's bold headings became the category list.

New categories, numbered into the existing ramp:

| id | Name | Icon | Color |
|---|---|---|---|
| `algo_math` | 9 · Math & Number Theory | `chip` | `#F59E0B` |
| `algo_strings` | 10 · String Algorithms | `browser` | `#EC4899` |
| `algo_bits` | 11 · Bit Manipulation | `stack` | `#10B981` |
| `algo_geometry` | 12 · Computational Geometry | `globe` | `#06B6D4` |

`algo_misc` keeps its id and topics but its display name becomes **`13 · Miscellaneous & Advanced`**.
Icons are existing `resolveIcon()` names — no new assets. Colors continue the mock's `cats()` ramp
cycling.

**2. `kmp`, `rabin_karp` and `manacher` move from `algo_misc` to `algo_strings`.** Ids do not change,
so no content file, cross-link, prerequisite, problem-bank reference or `SOLVED_PROBLEM_IDS` entry
breaks. Only `categoryId` changes. `algo_misc` drops from 12 topics to 9 and stops being the place
string algorithms hide.

**3. Every new topic meets the existing content bar.** Full 7-section `TopicContent` (whatIsIt ×3
paragraphs, 5–6 `StepCard`s, `formulas` + `notationKey`, 1–2 `CodeBlock`s, `applications`,
`takeaways`, `crossLinks`, `prerequisites`) plus a **bespoke simulation config** — no
`NotYetAvailable`, no shared-config shortcuts. `ContentCoverageTest` stays green throughout, so no
batch may land a topic row without its content file in the same change.

**4. One new widget, not four.** Three of the four new categories map onto widgets that already
exist (see each batch). Only Bit Manipulation has no usable host — a bit-level view needs per-bit
cells, and stretching `ArrayWalkPlayer` to render 32 columns of 0/1 would fight its row layout.

**5. Gating follows the established split.** One foundational free topic per new category
(`euclid_gcd`, `naive_string_search`, `bit_basics`, `polygon_area`), the rest premium. Same rule
inside existing categories: the gap-fill topics are premium, matching their neighbours.

## Batches

Five batches, ordered cheapest-first so the early ones need no new widget and no category work.

### D1 · Existing-category gap-fill — 7 topics

No new categories, no new widgets. Closes every doc gap outside the four missing headings.

| Topic id | Name | Category | Simulation host |
|---|---|---|---|
| `activity_selection` | Activity Selection | `algo_greedy` | `ArrayWalkPlayer` — intervals sorted by finish time, each kept or rejected |
| `coin_change_greedy` | Coin Change (Greedy) | `algo_greedy` | `ArrayWalkPlayer` — runs greedy and DP on the same `{1,3,4}` / target 6 case so greedy's failure is *shown*, not asserted |
| `partition_problem` | Partition Problem | `algo_dp` | `DpGridVisualizer` — subset-sum table over half the total |
| `eulerian_path` | Eulerian Path & Circuit | `algo_graph` | `GraphAlgorithmPlayer` — Hierholzer's, with the degree parity check as the gate |
| `hamiltonian_path` | Hamiltonian Path & Circuit | `algo_graph` | `GraphAlgorithmPlayer` — backtracking search, cross-linked to `bitmask_dp` (Held-Karp) |
| `uniform_cost_search` | Uniform Cost Search | `algo_pathfinding` | `PathfindingGrid` — already narrates UCS in `PathfindingGridSection`; this promotes it to a topic and contrasts it with Dijkstra |
| `ida_star` | IDA* | `algo_pathfinding` | `PathfindingGrid` — f-cost threshold per iteration, node counts against A*'s memory |

**Verify before writing:** the greedy coin-change counterexample and the UCS-vs-Dijkstra distinction
(same algorithm, different framing — UCS is goal-directed and lazily generated) must be stated
accurately, not hand-waved.

### D2 · Computational Geometry — 4 topics, new category `algo_geometry`

`PointCloudPlayer` already renders 2D points with frame-by-frame overlays (built in Phase 3, used by
the AI clustering topics). Every topic here is points plus drawn segments — no new widget.

`convex_hull` (Graham scan and Jarvis march as two configs of one topic) · `line_intersection`
(orientation/cross-product test, collinear and touching cases) · `rotating_calipers` (diameter of the
hull, stepping the antipodal pair) · `polygon_area` (shoelace + perimeter — **free**)

`closest_pair_of_points` stays in `algo_divide_conquer` (the doc lists it under both headings) and
gains a cross-link to this category.

**Frame builders needed on `PointCloudSection`:** hull-under-construction with the discarded-turn
highlight, a segment layer, and the antipodal-pair pointer.

### D3 · String Algorithms — 5 new topics, new category `algo_strings`

`naive_string_search` (**free**, and the baseline every other topic here beats) · `z_algorithm` ·
`longest_common_substring` · `longest_palindromic_substring` · `aho_corasick`

Plus the three moves (`kmp`, `rabin_karp`, `manacher`) from D2's decision above.

Hosts: `ArrayWalkPlayer` for the four scanning/table topics (it already drives KMP's LPS build,
Rabin-Karp's rolling hash and Manacher's radii, so the idiom is established);
`longest_common_substring` uses `DpGridVisualizer`; `aho_corasick` uses `TreeVisualizer` — the trie
with failure links drawn as back-edges is the whole idea, and a linear strip cannot show it.

**Risk:** `TreeVisualizer` may not support cross-tree edges. If it does not, the failure links go on
as a frame-level overlay list rather than a change to the tree model — check before committing to the
batch size.

### D4 · Math & Number Theory — 7 topics, new category `algo_math`

`euclid_gcd` (GCD/LCM — **free**) · `modular_arithmetic` · `fast_power` · `modular_exponentiation` ·
`sieve_of_eratosthenes` · `fermats_little_theorem` · `chinese_remainder_theorem`

The doc lists "Modular Exponentiation" and "Fast Power / Binary Exponentiation" separately and this
plan keeps them separate: `fast_power` is the halving-the-exponent idea (and its matrix-power use,
cross-linked to `fibonacci_dp`), `modular_exponentiation` is that idea under a modulus plus the
modular inverse it enables — which is what `fermats_little_theorem` then needs.

Hosts: `ArrayWalkPlayer` for `sieve_of_eratosthenes` (composite marking pass by pass) and
`euclid_gcd` (the (a, b) pair reducing per step); `RecursionTreeVisualizer` for `fast_power` and
`modular_exponentiation` (the halving tree is the complexity argument);
`ArrayWalkPlayer` residue rows for `modular_arithmetic`, `fermats_little_theorem` and
`chinese_remainder_theorem` (CRT's simultaneous congruences as one row per modulus, the solution
lighting up where they agree).

**Verify before writing:** every worked example computed and checked, not written from memory —
Phase 9's B3/B4 batches each caught arithmetic errors this way.

### D5 · Bit Manipulation — 4 topics, new category `algo_bits`, **new widget**

`bit_basics` (odd/even, power of 2, the `n & (n-1)` trick — **free**) · `count_set_bits` (Brian
Kernighan's, against naive shifting) · `subsets_bitmask` · `xor_tricks` (single number, swap, XOR
prefix ranges)

**New simulation type `BitBoardPlayer`.** Renders one or two integers as fixed-width bit cells with
per-bit highlighting, a running decimal readout, and a frame caption for the operation applied. Same
config-driven shape as `RegressionLab` and `DecisionSurface` from Phase 9 — a `SimulationType` data
object plus a config map, not a bespoke screen. `subsets_bitmask` cross-links to `bitmask_dp`, which
keeps its `DpGridVisualizer` host.

Last batch deliberately: it is the only one that can be blocked by widget work.

## Guards

**`AlgoTaxonomyCoverageTest` (new).** A checked-in map from every `docs/topics.algo.md` entry to the
topic id(s) that satisfy it, asserting each maps to a real `TopicRegistry` entry and that the map
covers all 92 entries. This is the guard that makes the coverage table above executable instead of a
claim that rots.

**Category integrity (new).** Assert no `Category` in `AlgorithmsCategories.all` is empty and every
topic's `categoryId` resolves — this catches a mistyped id in a moved topic, which is exactly D3's
failure mode.

Both are the same two guards **Phase 9 specified and still has not built after five batches**. Build
them once here, generic over section, and wire the AI taxonomy into the same harness — the phase-9
doc flags their absence as its largest open risk, and this phase's four category moves make the
category guard load-bearing rather than nice-to-have.

**Existing guards that must stay green every batch:** `ContentCoverageTest` (authored content, no
`NotYetAvailable`, prerequisite and cross-link targets resolve), `ProblemBankTest`, `QuizBankTest`.

## Progress

| Batch | Topics | Status | Notes |
|---|---|---|---|
| D1 · Existing-category gap-fill | 7 | **Done** | Plus `SimulationFrameTest` — see below |
| D2 · Computational Geometry | 4 | **Done** | Needed no new `CloudFrame` fields; found a live `mcmc` bug |
| D3 · String Algorithms | 5 (+3 moved) | **Done** | `TreeVisualizer` did need failure-link support; added |
| D4 · Math & Number Theory | 7 | **Done** | Every worked example pinned by a `require` in its builder |
| D5 · Bit Manipulation | 4 | Next | New `BitBoardPlayer` widget |
| Guards | — | Planned | Taxonomy + category guards land at the end, not with D1 — see below |
| **Total** | **27** | **23 done** | Algorithms 67 → 94; app taxonomy 257 → 284 |

### Deviations from the plan as written

**The two specified guards land at the end of the phase, not with D1.** `AlgoTaxonomyCoverageTest`
asserts its map covers all 92 doc entries and that each maps to a real `TopicRegistry` id. Landing it
with D1 would mean checking in a test that fails until D5 — a red guard teaches nothing. All five
batches ship in one session, so the guards go in once every topic they reference exists.

**A third guard was added that the plan did not specify: `SimulationFrameTest`.** Every simulation is
a config plus a pure, Compose-free frame builder, and nothing was calling those builders outside the
app. A builder that threw — an edge index off the end of a graph's edge list, a DP cell key outside
the declared table — surfaced only by opening that one topic. The test runs every configured builder
on the JVM and asserts the frames stay inside the shape the config declares. This phase adds 27 new
configs against four widgets plus a fifth that does not exist yet, which is what made it worth
building first rather than last. Each section exposes `internal val <section>TopicIds` and
`internal fun <section>FrameCount(topicId)`; extend both when a new widget lands.

### D1, as built

Topic rows in `AlgorithmsTopics.kt`, content files, sim configs and prerequisite edges, all premium
to match their neighbours:

- `activity_selection`, `coin_change_greedy` → `ArrayWalkPlayer`. Activity selection's frames run
  earliest-finish-first, then replay earliest-start on the same eight intervals (3 → 2) and
  shortest-duration on a three-interval set built to break it, so the wrong sort keys are watched
  losing. Coin change runs greedy and the DP table on {1,3,4}/6 side by side: 4+1+1 against 3+3.
- `partition_problem` → `DpGridVisualizer`, a 5×12 subset-sum table over {1,5,11,5} at target 11,
  with the traceback recovering {11} and {1,5,5}.
- `eulerian_path`, `hamiltonian_path` → `GraphAlgorithmPlayer`, on two new graph defs. The Eulerian
  graph is two triangles hinged at C so Hierholzer has to splice; the Hamiltonian graph has a circuit
  (A-B-D-F-E-C-A) that alphabetical neighbour order only reaches after three dead ends.
- `uniform_cost_search`, `ida_star` → `PathfindingGrid`. UCS needed a per-edge cost function rather
  than the shared unit-cost `searchFrames`: the two cells touching the goal charge a toll of 9, so
  the goal is *generated* at cost 17 and *popped* at 11 — which is the goal-test-on-pop rule made
  visible instead of asserted. IDA* runs its own f-bounded DFS, emitting a frame per expansion and
  folding pruned branches into the parent's status; on this map it takes two iterations, threshold
  7 then 9.

### D2, as built

Category `algo_geometry` (`12 · Computational Geometry`, `globe`, `#06B6D4`) with `polygon_area`
free and the other three premium. `closest_pair_of_points` stayed in `algo_divide_conquer` and gained
cross-links to `convex_hull` and `rotating_calipers`, as planned.

**The plan predicted three new `PointCloudSection` frame-builder features; none were needed.**
`Segment` already carries the hull under construction and the caliper, `Emphasis.QUERY` is the vertex
under test, and `Emphasis.FADED` is a point the scan discarded. `CloudFrame` was not touched.

- `convex_hull` runs a real Graham scan over twelve points positioned so the five pops are exactly
  the five interior points, then states Jarvis march's O(n·h) against it on the same input.
- `rotating_calipers` builds the hull, then walks the two-pointer antipodal sweep, one frame per hull
  edge, closing on the measured diameter.
- `polygon_area` uses a *non-convex* polygon and draws each shoelace term's triangle back to the
  origin, so the negative terms are watched cancelling instead of being asserted to cancel.
- `line_intersection` runs five segment pairs through the same four cross products: proper crossing,
  clear miss, endpoint touch, collinear overlap, collinear miss. The last three are the ones a
  straddle-only test gets wrong.

**`SimulationFrameTest` found a live bug in an existing topic on its first extension.** The
point-cloud check asserts every plotted point lies in the unit square, because `ScatterCanvas` maps
`[0,1]` onto the plot area with no autoscaling. `mcmc` (Phase 9) works in its target's own
coordinates — roughly x ∈ [−3.4, 3.4], y ∈ [−2.6, 3.4] — so all but a sliver of that lab had been
rendering off-canvas since it landed. Fixed by routing every dot and segment in `mcmcFrames` through
an `mcmcPlot()` mapping. This is exactly the class of defect the test was added for: it is invisible
in a build, invisible in a review, and only visible by opening that one topic and knowing what the
picture was supposed to look like.

### D3, as built

Category `algo_strings` (`10 · String Algorithms`, `browser`, `#EC4899`) with `naive_string_search`
free and the rest premium. `kmp`, `rabin_karp` and `manacher` moved over from `algo_misc` by changing
`categoryId` only — ids unchanged, so no content file, cross-link, prerequisite or problem-bank
reference moved with them. `algo_misc` is down from 12 topics to 9.

**The flagged risk was real: `TreeVisualizer` had no cross-tree edge support.** Its canvas derives
every edge from `parent`, so a failure link — which by definition points into a different branch —
could not be drawn at all. Resolved the way the plan's fallback proposed: `TreeFrame` gained a
`links: List<TreeLink>` field and `TreeConfig` an optional `linkLabel` for the extra legend chip,
with the links rendered as dashed quadratic arcs beneath the node pills. Purely additive; every other
tree config leaves the list empty.

- `naive_string_search` → `ArrayWalkPlayer`, with the pattern drawn as the aux row where it currently
  sits, so the shift-by-one and the re-comparison it forces are movements on screen. Closes by
  counting the pathological case (`AAAAAAAAAB` / `AAAB`) at 28 comparisons for a 10-character text.
- `z_algorithm` → `ArrayWalkPlayer`. The `[l, r)` window is the pointer row, and the readout tracks
  positions copied from a mirror against characters actually compared — the linearity argument.
- `longest_palindromic_substring` → `ArrayWalkPlayer`, expanding around both kinds of centre.
- `longest_common_substring` → `DpGridVisualizer`, a 7×7 table where the traceback starts at the
  maximum cell rather than the corner.
- `aho_corasick` → `TreeVisualizer`, on {he, she, his, hers} scanning "ushers".

**A bug in the Aho-Corasick scan survived a green test run and was caught by re-reading.** The node
label used for display (`"root"` for the root) was the same string used as the trie-lookup key, so
the first character looked up `"root" + c`, found nothing, and the automaton could never leave the
root. `SimulationFrameTest` did not catch it because the frames were still structurally valid — only
the narration was wrong. Fixed by separating `prefixOf` (the lookup key, empty at the root) from the
display label, and the builder now ends with a `require(found == [she, he, hers])`, which puts the
claim the frames make under the test that runs them. "he" is reachable only through the output link
off "she", so it is the first thing a wrong transition drops.

### D4, as built

Category `algo_math` (`9 · Math & Number Theory`, `chip`, `#F59E0B`) with `euclid_gcd` free and the
other six premium. The doc's separate listing of "Fast Power / Binary Exponentiation" and "Modular
Exponentiation" is kept: `fast_power` is the halving idea and its matrix-power use, and
`modular_exponentiation` is that idea under a modulus plus the inverse it enables — which is what
`fermats_little_theorem` then needs.

Hosts went as planned. `fast_power` and `modular_exponentiation` are `RecursionTreeVisualizer`
traces on a live exponent slider (1–20, default 13), because the halving tree *is* the complexity
argument. The other five are `ArrayWalkPlayer`: the gcd remainder chain as a growing row followed by
the Bézout back-substitution, residue rows for modular arithmetic where whether `1` appears in the
`a·k mod m` row is exactly whether `a` is invertible, the sieve across two rows so 2…30 stays
readable, powers of 3 mod 7 arriving at 1 at exponent 6, and CRT as three successive filters over
0…31 with the closed-form construction shown afterwards.

**Every worked example was computed and checked before it was written, as the plan required** — and
then pinned in code. Each builder that states a number now ends with a `require`: `252·(−2) +
105·5 = 21` for Bézout, the exact prime list for the sieve, and `23` for CRT from *both* the filter
and the construction independently. This is the D3 lesson applied ahead of the failure rather than
after it: a wrong constant leaves the frames structurally valid and every caption plausible, so
structure checks cannot catch it. During writing, the extended-Euclid unwind did initially drop its
last division step, which produces coefficients for the wrong pair; the pin is what would have
caught it.

## Out of scope

- Data Structures. The doc's structures are fully covered by the 29 `DataStructuresTopics` entries.
- The 17 app-only algorithm topics the doc does not list. They stay.
- Multi-language code snippets for the new topics (Phase 7 deferred these app-wide; new topics ship
  Kotlin like their neighbours).
- Problem-bank and quiz entries for the new topics. Worth a follow-up, not a blocker — `ProblemBankTest`
  only guards the references that exist.
