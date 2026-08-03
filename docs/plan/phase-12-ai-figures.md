# Phase 12 — Figures for AI topics (pilot)

Status: **Pilot shipped.** 30 of 323 AI topics carry a figure, in batches A1–A5 on branch
`feat/ai-figures`. The remaining ~293 are not planned yet — see *What the pilot was for* below.
Depends on: the figure layer (`core/data/model/Figure.kt`, `feature/topics/FigureCard.kt`,
`FigureShapeTest`) and Phase 11, which took it to 122/122 Data Structures and Algorithms topics.

## Goal

An AI topic page has prose at the top and a lab at the bottom, and the lab has to be *played* before
it teaches anything. Every DSA and interview-prep page now answers that with a static figure above
the How-It-Works steps. The AI sections had none — 0 of 323 — which is where a picture pays most: a
loss curve, a layer stack and an attention matrix are each one image and several paragraphs of prose.

`phase-11-dsa-figures.md` deferred this explicitly, for a real reason: the six existing shapes do not
fit curves, network architectures or matrices, and it predicted three new ones would be needed. They
were, and they are exactly the three it named.

## Baseline

| Section | Topics | With a figure |
|---|---|---|
| Interview Prep — Patterns | 57 | 57 |
| Data Structures + Algorithms | 122 | 122 |
| ML / DL / NLP / RL | 323 unique (356 entries, 33 cross-listed) | **30** |

## New shapes

Three, added to `Figure.kt` and rendered in `FigureCard.kt`. All three sit in the same card shell and
use the same four `FigureTone` roles — no new chrome, per the design-fidelity rule.

**`Plot`** (A1) — axes with either curves or bars. `series` and `bars` are alternatives, not layers:
they answer different questions and one axis carrying both makes neither legible. The rule is a test,
not a type. Points are normalised `0f..1f` with **y measured up from the axis**; the renderer flips
it, because authoring a loss curve with y descending means writing every value upside down. The
legend and axis names are composables around the canvas rather than text inside it — a series label
drawn at the end of its own curve lands on top of the next curve the moment two of them converge.

**`LayerStack`** (A3) — ordered blocks with the signal running through them. `Stacks` was the near
miss: a call stack's entries are all the same kind of thing and a network's layers deliberately are
not. Vertical by default, `horizontal` for pipelines, and `backwardLabel` for the two figures where
the return path is the topic (backprop, GANs). Blocks are composables, so a layer's detail line
measures and wraps itself.

**`Heatmap`** (A4) — a matrix as intensity. `Grid` holds the same numbers but reads them one cell at
a time, and the question an attention matrix is asked is *where the mass is*. Values are `0f..1f`
intensities, not raw magnitudes: the ramp has no scale of its own, so the caption carries what 1.0
meant. Cells are square — an attention matrix drawn as a rectangle loses its diagonal.

## Batches

| Batch | Scope | Topics |
|---|---|---|
| A1 | `Plot` + optimisers | `adam`, `adamw`, `momentum`, `rmsprop`, `adagrad`, `gradient_descent_variants` |
| A2 | `Plot` — activations & losses | `relu`, `leaky_relu`, `sigmoid`, `softmax`, `activation_functions`, `cross_entropy_loss`, `mse`, `hinge_loss` |
| A3 | `LayerStack` + architectures | `mlp`, `cnn`, `transformers`, `backpropagation`, `batch_normalization`, `dropout`, `gans`, `vae` |
| A4 | `Heatmap` + attention | `attention`, `multi_head_attention`, `self_cross_attention`, `positional_encodings` |
| A5 | The existing six shapes, on AI content | `tokenization` (Strip), `decision_trees` (Tree), `kmeans` (Graph), `q_learning` (Grid) |

Each batch's shape work landed in the same commit as its first figures — a shape with no caller is
unreviewable.

## Files touched

- `core/data/model/Figure.kt` — three shape variants plus `FigureSeries`, `FigurePoint`, `FigureBar`,
  `FigureLayer`. `FigureCell` is reused by `Heatmap.marks`.
- `feature/topics/FigureCard.kt` — `PlotFigure`, `LayerStackFigure`, `HeatmapFigure`, three `when`
  branches. Also dropped a dead `widestNode` local left in `GraphFigure` by F2.
- `feature/topics/content/*.kt` — 30 files gained a `figure = Figure(…)` argument after `topicId`.
- `feature/topics/FigureShapeTest.kt` — three conformance tests; the enumeration already covered
  ML/DL/NLP/RL, so no wiring changed.
- `feature/topics/AiFigureCoverageTest.kt` — new.
- `core/data/model/TopicContent.kt` — the comment on `figure` was still claiming pattern guides only.

## What the pilot was for

The question was whether the remaining ~293 topics are a renderer problem or an authoring problem.
A5 answers it: classical ML, NLP preprocessing and tabular RL fit `Strip`, `Tree`, `Graph` and `Grid`
unchanged. Nine shapes cover the AI taxonomy, and no batch after A4 needed renderer work.

Two things the pilot showed that a plan could not have:

- **The figure follows the page's measured numbers, not its name.** Leaky ReLU's α = 0.01 is
  invisible on a plot of the curve, so its figure is the learning-rate sweep instead. Softmax's is
  the `[1000, 1001, 1002]` overflow example. Where a page had run an experiment, that experiment was
  a better figure than the textbook shape.
- **The DSA-era guards caught AI figures immediately.** A5 hit two `FigureShapeTest` failures on its
  first run — an over-long band label, and a one-cell arrow whose label lands on the value it points
  at. The second changed the figure rather than the caption.

**Cost, for planning the full phase:** roughly 4–8 topics per commit, with the constraint being how
much of each page's prose has to be read before its figure is honest — not the spec, which is 15–40
lines. A shape-only batch (A1, A3, A4) costs about one extra hour for renderer plus tests.

## Verification

Per batch: `./gradlew :app:testDebugUnitTest` (`FigureShapeTest`, `AiFigureCoverageTest`,
`DsaFigureCoverageTest`, `FigureCoverageTest`, `ContentCoverageTest`) then `./gradlew assembleDebug`.
Both green for A1–A5.

**Outstanding:** the emulator pass. All 30 pages, light and dark, has not been run. This is the step
that caught the pattern work's only two real defects — a muted tone unreadable on dark, and graph
labels hanging outside their circles — and the three new shapes raise the risk rather than lowering
it: the heatmap ramp's contrast at low intensity, the plot's axis-label crowding, and the horizontal
`LayerStack` at five blocks are all invisible to a unit test.

## Non-goals

- **The remaining ~293 AI topics.** Plan against the cost note above.
- **Phase 11's F8 sweep.** Skipped by decision; `feat/dsa-figures` still ends at F7.
- **Replacing any simulation.** Figures sit above the steps; the labs are untouched.
