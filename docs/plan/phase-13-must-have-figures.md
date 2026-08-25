# Phase 13 — The figures that must exist

Status: **In progress — M1 to M6 shipped, 31 of 41.** Everything else in `phase-12-figure-scope.md`'s
Tier A becomes optional and stays unbuilt unless a page proves it needs one.

Depends on: Phase 12's figure layer, which is finished as a *capability* — nine shapes, a renderer
that has survived an emulator pass, and 108 topics already drawn. Nothing in this phase needs new
code.

## Why this phase exists and Phase 12 does not continue

Phase 12 was written to cover the AI sections and its batches were sized by what fit in a commit, not
by what a reader needs. That produced 108 good figures and a backlog of 143 more, and the backlog was
never asked to justify itself topic by topic. `phase-12-figure-scope.md` did the first cut —
217 remaining topics, 58 too thin to draw on, 16 that should never have one. This phase does the
second: of the 143 that *could* have one, **41 are load-bearing and 102 are decoration.**

A figure is load-bearing when one of these is true:

1. **The claim is a shape.** ROC is a curve, a reachability plot is the output of OPTICS, an FP-tree
   is the whole method, focal loss is one factor's effect on a curve. The prose can name these and
   cannot show them, so the page is measurably worse without the picture.
2. **The claim is a comparison across numbers the reader has to hold at once.** R-CNN's 47 seconds
   against Fast R-CNN's, a residual connection against a plain stack, the beam width that fixes one
   class of error and not the other.
3. **The page is a hub.** Counted, not guessed: `bias_variance`, `vanishing_gradient`, `resnet`,
   `ppo`, `value_function`, `arima` and `encoder_decoder` are each pointed at by four or more other
   topics' cross-links, so their figure is seen by readers who arrived from somewhere else and needs
   to work cold.

Everything that fails all three is a figure that would be *nice*. A nice figure costs a commit, adds
a caption to maintain, and competes for the reader's attention with the lab underneath it. The
honest default for those is not to build them.

## The batches

Each line says what the figure has to show. If a batch's figures cannot be made to differ from each
other, the batch is cut rather than shipped — that rule already retired six activation curves and a
second dendrogram in Phase 12's scope pass.

**M1 — Classification metrics** (5) — **shipped.** Every one is a quantity read against a threshold,
which is the case `Plot` was added for. All five pages share one lab (1,000 cases, 88 positive, 912
negative), so the batch could be checked against itself: κ = 0.546 and MCC = 0.613 were recomputed
from the four cells before the figure was written, and both match the page. `log_loss` deliberately
does *not* redraw the −ln p curve `cross_entropy_loss` already carries — it draws the distortion test
instead, where AUC does not move and the two scoring rules disagree by 64% against 27%.

| Topic | The figure has to show |
|---|---|
| `roc_curve` | The curve itself, with the operating points that a single accuracy number hides. |
| `f1_score` | The threshold sweep the page measures — 0.569 to 0.776 on one model. |
| `log_loss` | One confident error costing 13× a hesitant one. |
| `cohens_kappa` | Accuracy 0.912 sitting beside kappa 0.000 on the same predictions. |
| `mae` | The two objectives picking different lines through the same points. |

**M2 — Fit, capacity and the two likelihoods** (5) — **shipped.** Three of these came out of a lab
that was already in the app — `RegressionMetricsLab` and the decision-surface blobs were executed and
their numbers read off rather than reproduced in NumPy — and the other two were computed from
scratch. The batch's own check is `r_squared`: the least-absolute-deviations line has the slope
nearer the truth *and* the worse R², which is only visible because both fits were scored against the
same SS total.

| Topic | The figure has to show |
|---|---|
| `r_squared` | What "better than predicting the mean" is measured against. |
| `bias_variance` | The U-curve. The most-cited page in the AI sections has no picture of the thing it is about. |
| `outlier_detection` | The sample size below which the z-rule cannot fire at all. |
| `gaussian_nb` | One likelihood curve per feature per class — the shape the name promises. |
| `multinomial_nb` | Counts, and why the text classifier refuses to die. |

`bias_variance` is the one figure in the batch with no lab behind it: the U-curve is 20,000 refits of
degrees 1–12 on 15 points of sin(2πx) + N(0, 0.30²), scored on 101 test points. Everything but the
degree-12 variance is stable to three decimals across seeds; that one moves ±2%, so the caption
quotes it to two.

**M3 — Outputs that are shapes** (5) — **shipped.** Every number here came out of a lab already in
the app: `optics()` and the mutual-reachability MST over `varyingDensity`, `buildFpTree()`, and
`retailSeries(4)` through `difference`, `lag1Autocorrelation` and `holtWinters`. Two shapes get their
first AI use — `Timeline` for HDBSCAN's persistence intervals and `Tree` for the FP-tree.

One brief was changed on contact. `exponential_smoothing` was scoped as "level, trend and season as
three curves that sum to the fit", and on the lab's numbers the trend is under 1.7 a month against a
level of 108–146: drawn on a shared axis it is indistinguishable from the level line, and drawn on
its own axis it is three panels pretending to be one. The figure draws level and fit, so the seasonal
component is the visible gap, and the caption does the addition exactly — 146.187 + 0.441 + 0.499 =
147.126, the first forecast month. A component too small to see is a fact about the series, and the
figure says so rather than rescaling until it looks important.


| Topic | The figure has to show |
|---|---|
| `optics` | The reachability plot. It *is* the algorithm's output; the page currently describes a picture. |
| `hdbscan` | Cluster persistence across ε, which is the one thing DBSCAN's figure cannot say. |
| `fp_growth` | The FP-tree, on the `Tree` shape, with the compression counted. |
| `arima` | Differencing turning a wandering series stationary. |
| `exponential_smoothing` | Level, trend and season as three curves that sum to the fit. |

`arima` also moved: "differencing turning a wandering series stationary" is drawn as the lag-1..12
autocorrelation at d = 0, 1 and 2 rather than as the raw and differenced series, which have no shared
scale — the series spans 55 units and its differences span 21 around zero, so one axis flattens the
differences into a line. The ACF is the diagnostic the page's own readout quotes, and all three
curves live on the same −1..+1 axis: 0.884 decaying slowly, 0.063 flat, −0.469 over-differenced.

**M4 — Gradients and what each CNN changed** (6) — **shipped.** `vanishing_gradient` is the only one
that needed a lab run — `deepGradients()` at four init scales — and every other number in the batch
was already stated on its page and was re-derived before being drawn: 192×32×25 = 153,600 and the
whole 3a module both ways, 25C² against 18C², 1/N + 1/k² swept over eight widths, and the 7×7
read-count map. `resnet` uses `Graph` rather than the `LayerStack` the brief named, because a skip
connection is an edge and `LayerStack` has no way to draw one — the addition is the figure, so the
shape that can draw the addition wins.


| Topic | The figure has to show |
|---|---|
| `vanishing_gradient` | Per-layer gradient magnitude, measured — the page says "measured, not asserted". |
| `padding_strides` | The output-size formula as a picture, including the border pixels nobody reads. |
| `resnet` | The addition, on `LayerStack` with the skip drawn. One edge is the whole contribution. |
| `inception` | The 1×1 bottleneck's arithmetic, as a cost comparison. |
| `vgg` | Two 3×3s against one 5×5: same receptive field, fewer parameters. |
| `mobilenet` | Depthwise separable convolution costing 8× less, as the division it is. |

The batch's six figures land on five shapes — Plot twice (a log-scale gradient sweep and a cost
curve), Grid twice (a 7×7 read-count map and a five-row cost table), plus Graph and a bar chart — so
the differ-from-each-other rule is satisfied on content as well as on shape. `mobilenet` and
`inception` are both cost arguments and were checked against each other for that reason: one is a
curve with a floor, the other a table where a single row carries the saving.

**M5 — Detection** (5) — **shipped.** The one block where the numbers only mean something side by
side, and the one batch where two figures had to reach onto neighbouring pages for the other half of
their comparison: `rcnn`'s 47 seconds is only a number next to Fast R-CNN's 2.3 and Faster R-CNN's
0.2, both of which live on their own pages and were taken from there rather than restated. Every
figure in the batch is on a different shape — Grid, Heatmap, Plot, Graph, Strip — which was not a
target but is a fair sign the five claims really are five different claims.

`unet` follows `resnet` off `LayerStack` for the same reason: the skips are the content and only
`Graph` can draw an edge. The four crops fall out of the arithmetic, so the figure is checkable
against itself — 568 − 2·88 = 392, 280 − 2·40 = 200, 136 − 2·16 = 104, 64 − 2·4 = 56.

`yolo` is the one figure in the phase so far whose layout is illustrative rather than measured: the
counts and the per-cell budget are the architecture's, but which cells hold the centres is drawn to
put the constraint in one picture. The caption says so.


| Topic | The figure has to show |
|---|---|
| `rcnn` | Where the 47 seconds per image goes. |
| `yolo` | One grid, 98 boxes, and what the grid costs at the cell boundary. |
| `retinanet` | Focal loss against cross-entropy — one factor, drawn as two curves. |
| `unet` | Contract, expand, and the concatenated skips, on `LayerStack`. |
| `mask_rcnn` | The RoIPool quantisation the masks exposed, and what RoIAlign does instead. |

**M6 — Sequence models** (5) — **shipped.** The batch where four of the five figures are the
same claim at different scales: a fixed channel between two things, and what the channel drops.
`encoder_decoder` measures it directly — a probe of the frozen context vector, 0.34 at position 1
against 0.85 at position 6, and the flip to 0.98/0.33 when the source is fed backwards. `bptt` is
the same picture in time rather than in width: ten steps, and a gradient that arrives 56× smaller
nine steps back. `gpt`'s triangle is the one channel that was narrowed deliberately.

`seq2seq` earns bars rather than a table because the point is that one of the three quantities does
not move: 84 model errors at width 1, 84 at width 10, with the correct count going 35 → 36 beside
them. `kl_divergence` is the batch's second Plot and the only figure here that is a pair of fitted
curves — checked against `seq2seq` for redundancy, and they share nothing but the axis.

Both KL fits were re-derived before being drawn rather than copied off the page: reverse KL(Q‖P)
reproduces 0.6906 exactly, and forward KL(P‖Q) comes to 0.4684 against the page's 0.4685 on a
[−6, 6] grid at step 0.05, which is what fixed the discretisation the lab must have used — a
continuous integral gives 0.5139 and would have drawn a different argument.


| Topic | The figure has to show |
|---|---|
| `encoder_decoder` | The single vector between the two networks, and what it drops. |
| `seq2seq` | Greedy against beam, and the error class only one of them fixes. |
| `gpt` | The triangular mask, on `Heatmap`. Everything on the page follows from it. |
| `bptt` | The unrolled window, and what the truncation really bounds. |
| `kl_divergence` | Forward against reverse on the same target — mode-covering beside mode-seeking. |

**M7 — NLP where the structure is the point** (5).

| Topic | The figure has to show |
|---|---|
| `hmm` | The Viterbi trellis, on `Grid`, with the surviving path marked. |
| `n_grams` | The count table and the sparsity that kills it as n grows. |
| `cosine_similarity` | Angle against length, so the reason length stops mattering is visible. |
| `word2vec_skipgram` | The training pairs a window generates, counted. |
| `dependency_parsing` | The labelled arcs, on `Graph`. A dependency parse is a drawing. |

**M8 — The four that are hubs elsewhere** (5).

| Topic | The figure has to show |
|---|---|
| `bleu` | Clipping, the brevity penalty, and the good paraphrase that scores exactly 0. |
| `lora_qlora` | Rank against quality, with the point the theorem says it stops paying. |
| `ppo` | The clipped objective against the unclipped one, for positive and negative advantage. |
| `value_iteration` | Values converging sweep by sweep on a small grid. |
| `value_function` | What V actually holds, on the same grid, before any algorithm touches it. |

## Method, unchanged from Phase 12

Every number in a figure is computed before the figure is written — NumPy in a scratch file, or the
page's own lab executed. sklearn is not installed in this environment, so anything needing a fitted
model is either written directly (A14's 1-NN and closed-form LDA) or replaced by a closed-form
quantity that makes the same point (A13's kernel decay for the γ-C grid). A figure that cannot be
computed honestly is cut, not estimated.

## Verification

Per batch: `./gradlew :app:testDebugUnitTest` (`FigureShapeTest`, `AiFigureCoverageTest`,
`DsaFigureCoverageTest`, `FigureCoverageTest`, `ContentCoverageTest`) then `./gradlew assembleDebug`.

**One emulator pass at the end of the phase, not per batch** — and it has a backlog to clear from
Phase 12 already: `knn`'s five labelled edges off one node, the eleven-node dendrogram in
`hierarchical_clustering`, `catboost`'s and `factor_analysis`'s narrow grids, `qda`'s 7×7 heatmap,
and `extra_trees`' full-width aux row. M7's trellis joins that list on arrival, M6 has added `bptt`'s ten-cell strip — two bands, two pointers and a ten-cell aux row, the narrowest cells in the app — plus `kl_divergence`'s three 25-point series, where the reverse fit's spike and the target's near-identical left mode overlap for a third of the axis, M5 has added `unet`'s nine-node U — the widest graph in the app and the one most likely to collide with itself on a narrow screen — plus `mask_rcnn`'s ten-cell strip with two bands and a pointer, M4 added `padding_strides`' 7×7 grid and `resnet`'s five-node graph, M3 added the
reachability profile it was promised plus `hdbscan`'s three-span timeline and `fp_growth`'s
nine-node tree, and M2 added two: `bias_variance`'s four series on one axis, and
`gaussian_nb`'s four bells, where each class's two curves nearly coincide and the solid/dashed
pairing is the only thing telling them apart.

## Non-goals

- **The other 102 Tier-A topics.** Not scheduled. The trigger to build one is evidence that a
  specific page reads badly without it, not the fact that its neighbours have figures.
- **The 58 Tier-B topics.** Content first — see `phase-12-figure-scope.md`. Forty-two are RL, and
  deepening those pages is worth its own phase on merits that have nothing to do with figures.
- **The 16 Tier-C topics.** Never.
- **New shapes.** Nine cover everything in this list. If a figure here seems to need a tenth, the
  figure is wrong before the renderer is.
