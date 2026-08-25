# Phase 13 — The figures that must exist

Status: **Complete — all 41 built, M1 to M8 shipped, and the emulator pass done.** Six figures came back broken and were fixed; the rest of this file records what the pass found. Everything else in `phase-12-figure-scope.md`'s
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

**M7 — NLP where the structure is the point** (5) — **shipped.** The batch with the least
invention in it: four of the five figures are the page's own worked example drawn instead of
described. `hmm`'s trellis is filled by running the page's transition and emission tables, and the
drama survives the drawing — the DT cell at column 2 (7.50e−4) is *smaller* than the IN cell
(7.87e−4) and still wins, because the transition out of it is 0.90 against 0.60. Greedy's two
divergent cells are marked in the same grid rather than in a second figure.

`cosine_similarity` is the one place a Plot is used as a plane rather than as a graph of a function:
four documents as rays from the origin, with "long" drawn dashed because it lies exactly on top of
"short" — which is the claim. `dependency_parsing` uses height for depth and keeps reading order on
the x-axis, so the projectivity constraint the caption names is visible in the drawing.

`word2vec_skipgram`'s strip is generic on purpose. The lab's corpus is described by its totals (102
tokens, 20 sentences, 288 pairs) and never listed sentence by sentence, so the figure draws five
unlabelled positions and shows the count identity instead of inventing text: 4L − 6 per sentence,
which sums to 4 × 102 − 6 × 20 = 288 and reproduces the page's number exactly.

`n_grams` is the batch's second Plot and the only figure that had to be re-counted rather than
read off: types-per-occurrence at n = 1…4 is 0.244, 0.514, 0.759, 0.952 on the page's eight
sentences, with the hapax share going 0% → 47% → 77% → 95%. Only the n = 4 pair was on the page.


| Topic | The figure has to show |
|---|---|
| `hmm` | The Viterbi trellis, on `Grid`, with the surviving path marked. |
| `n_grams` | The count table and the sparsity that kills it as n grows. |
| `cosine_similarity` | Angle against length, so the reason length stops mattering is visible. |
| `word2vec_skipgram` | The training pairs a window generates, counted. |
| `dependency_parsing` | The labelled arcs, on `Graph`. A dependency parse is a drawing. |

**M8 — The four that are hubs elsewhere** (5) — **shipped.** The last batch, and the one where
two figures had to be computed from the app rather than from the page. `value_iteration` and
`value_function` both run this repo's own gridworld — `RlGridWorldSection.kt`'s 4×4 with γ = 0.9,
−0.04 a step, +1 at (0,3), −1 at (1,3) and a wall at (1,1) — re-implemented in Python against the
Kotlin backup, including the absorbing-terminal rule, so the figures agree with the simulation the
reader can press play on rather than with a textbook grid. Value iteration reproduces the page's
claim exactly: largest change per sweep 1.000, 0.900, 0.810, 0.729, 0.656, 0.590, 0.000, so the
values settle at sweep 6 while the greedy policy has been optimal since sweep 3. Policy evaluation
under the uniform-random policy gives the pair's punchline — (1,2) is worth −0.478 under random and
+0.860 under optimal, the same square differing by 1.34.

`bleu`'s grid is the phase's densest at 3 × 6, and it is the one place three rows carry the same
number on purpose: a stuck decoder, a three-word fragment and a correct paraphrase all score
0.0000. The unclipped 8/8 and the smoothed 0.1652/0.1667 stay in the caption because they are what
the cells are being compared against. Only p₃ = 1/1 on the three-word row is inferred rather than
stated — it follows from the page's own 2/2 bigrams, since a single occurrence of "revised" with
both its bigrams present makes the trigram contiguous in the reference.

`ppo` is drawn for both signs of the advantage on one axis because the asymmetry *is* the topic:
flat past 1 + ε for a good action, unbounded past it for a bad one. `lora_qlora` is the phase's
only figure whose second curve is a prediction rather than a measurement — the Eckart-Young floor
under the trained sweep, which the trained losses sit just above at all four ranks.


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

**The emulator pass, and what it found.** Twenty-five figures were opened on a Pixel 7 (API 36,
1080×2400): the whole of M6–M8, both M5 graphs, and every cell of the Phase 12 backlog. Nineteen
were right the first time. Six were not, and five of those trace to two renderer bugs rather than to
the specs:

- **Graph node circles used a flat 15% of the card height**, so `knn`, `unet`, `resnet` and
  `dependency_parsing` all drew overlapping blobs, and labels longer than the circle (`conv 3×3`,
  `chased`, `572→568`) hung outside it. Making the card taller cannot help — the radius is derived
  from the height it grows with — so the radius is now capped by the closest pair in the graph, and
  a label that still does not fit steps down to 8sp and then 7sp.
- **Edge labels were nudged 9px off their line**, about a third of what a 9sp text box needs, so
  `crop 4`, `F(x)` and all six dependency arcs came back struck through. The offset is now half the
  label's own height plus 6.
- **Heatmap marks ignored their tone** and drew every outline in the accent colour, which is why
  `value_function`'s goal, pit and wall — two of them blank cells meaning different things — were
  indistinguishable.
- `value_function` also anchored its ramp to the two terminals, flattening every walkable cell into
  the same pale blue. It is stretched over the walkable range now, −0.666 to +0.104, and the
  terminals clamp.
- `mask_rcnn`'s pointer wrapped across three lines under one cell of a ten-cell strip, splitting
  `true` mid-word. That number is a one-cell band above the strip now, and `bptt`'s two pointers were
  shortened for the same reason.

Two guards went in with the fixes: `FigureShapeTest` now checks that a graph's closest pair still
leaves a circle big enough for a label, and that a pointer's length fits the strip it hangs under
(six characters once a strip reaches ten cells, eleven below that).

`dbscan` was the one graph left alone deliberately — its ten points are drawn as a density cloud,
where circles touching is what "within ε of each other" looks like, and it reads correctly on the
device.

## Non-goals

- **The other 102 Tier-A topics.** Not scheduled. The trigger to build one is evidence that a
  specific page reads badly without it, not the fact that its neighbours have figures.
- **The 58 Tier-B topics.** Content first — see `phase-12-figure-scope.md`. Forty-two are RL, and
  deepening those pages is worth its own phase on merits that have nothing to do with figures.
- **The 16 Tier-C topics.** Never.
- **New shapes.** Nine cover everything in this list. If a figure here seems to need a tenth, the
  figure is wrong before the renderer is.
