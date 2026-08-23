# Phase 12 — Figures for AI topics (pilot)

Status: **Pilot shipped, extension in progress.** 59 of 323 AI topics carry a figure — the A1–A5
pilot plus A6–A9, batched by free-tier priority from here on (see *Batches* below). The remaining
~264 continue in the same batch size — see *What the pilot was for* below.

Depends on: the figure layer (`core/data/model/Figure.kt`, `feature/topics/FigureCard.kt`,
`FigureShapeTest`) and Phase 11, which took it to 122/122 Data Structures and Algorithms topics.

Free-tier topics still without one: **18**, after A9 took the five RL foundations. What is left of
the free tier is `bert`, `perplexity`, `prompt_engineering`, `transfer_learning`, `wer` (NLP);
`biological_neuron`, `conv_layers`, `pooling_layers`, `data_augmentation`, `early_stopping`,
`neural_style_transfer`, `perceptron`, `segmentation_types`, `siamese_networks` (DL); and `apriori`,
`moving_average`, `multi_armed_bandit`, `svd` (ML). Everything after that is premium.

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
| ML / DL / NLP / RL | 323 unique (356 entries, 33 cross-listed) | **59** |

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
| A6 | `Plot` — regression | `linear_regression`, `polynomial_regression`, `ridge_regression`, `lasso_regression`, `elasticnet_regression`, `stepwise_regression`, `robust_regression`, `quantile_regression`, `bayesian_ridge`, `poisson_regression`, `isotonic_regression`, `lars` |
| A7 | Free-tier priority — data preprocessing & model evaluation, `Plot` + `Grid` | `missing_value_imputation`, `label_encoding`, `one_hot_encoding`, `min_max_normalization`, `confusion_matrix` (Grid), `accuracy`, `precision_recall`, `rmse` |
| A8 | Free-tier priority — NLP preprocessing, on the existing `Strip` shape | `pos_tagging`, `stemming`, `stop_words`, `text_cleaning` |
| A9 | Free-tier priority — RL foundations, one topic per shape | `agent_environment` (LayerStack), `state_action_reward` (Strip), `policy` (Heatmap), `mdp` (Graph), `bellman_equation` (Tree) |

Each batch's shape work landed in the same commit as its first figures — a shape with no caller is
unreviewable.

## Files touched

- `core/data/model/Figure.kt` — three shape variants plus `FigureSeries`, `FigurePoint`, `FigureBar`,
  `FigureLayer`. `FigureCell` is reused by `Heatmap.marks`.
- `feature/topics/FigureCard.kt` — `PlotFigure`, `LayerStackFigure`, `HeatmapFigure`, three `when`
  branches. Also dropped a dead `widestNode` local left in `GraphFigure` by F2.
- `feature/topics/content/*.kt` — 30 files gained a `figure = Figure(…)` argument after `topicId`.
  A6–A9 added 29 more the same way; no renderer change since A4.
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
- **And the guards still could not see the one real defect.** Horizontal `LayerStack` at five blocks
  rendered "discriminator" as "discri / minat / or" and left the row a ragged staircase of four
  different heights. Every unit test passed. See Verification.

**Cost, for planning the full phase:** roughly 4–8 topics per commit, with the constraint being how
much of each page's prose has to be read before its figure is honest — not the spec, which is 15–40
lines. A shape-only batch (A1, A3, A4) costs about one extra hour for renderer plus tests.

## Verification

Per batch: `./gradlew :app:testDebugUnitTest` (`FigureShapeTest`, `AiFigureCoverageTest`,
`DsaFigureCoverageTest`, `FigureCoverageTest`, `ContentCoverageTest`) then `./gradlew assembleDebug`.
Both green for A1–A9.

**Emulator pass — partial, and it earned its keep.** Nine of the 30 pages walked on a 1080×2400
emulator, chosen to hit every shape at its densest: MLP and CNNs (vertical `LayerStack` at three and
six blocks), GANs (horizontal at five), Backpropagation (`backwardLabel`), Sigmoid and Momentum
(`Plot` at two and three series), Leaky ReLU (`Plot` at six bars), Multi-Head Attention (4×8
`Heatmap`) and K-Means (nine-node `Graph`). Dark for all nine; light for GANs, Multi-Head Attention
and Sigmoid.

One defect, and it was the predicted one: **horizontal `LayerStack` at five blocks**. Fixed in
`5e4bbce` — equal block heights via `IntrinsicSize.Min`, 10sp labels in horizontal mode, and the
guard's cap dropped from five blocks to four. Re-verified in both themes.

Two risks named above turned out not to be real: the heatmap ramp is legible at 0.10 intensity in
both themes, and the plot's axis labels do not crowd at three series.

**Still outstanding:** the other 21 pages, and light mode for six of the nine walked. A9 is
unwalked — its two riskiest cells are the `mdp` graph, whose six hand-placed nodes push to y = 0.04
and 0.96, and `policy`'s heatmap row labels, which get 16% of the card's width at 9sp.

## Non-goals

- **The remaining ~293 AI topics.** Plan against the cost note above.
- **Phase 11's F8 sweep.** Skipped by decision; `feat/dsa-figures` still ends at F7.
- **Replacing any simulation.** Figures sit above the steps; the labs are untouched.
