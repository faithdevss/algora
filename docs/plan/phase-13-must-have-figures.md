# Phase 13 — The figures that must exist

Status: **Planned.** 41 topics in 8 batches (M1–M8). Everything else in
`phase-12-figure-scope.md`'s Tier A becomes optional and stays unbuilt unless a page proves it needs
one.

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

**M1 — Classification metrics** (5). Every one is a quantity read against a threshold, which is the
case `Plot` was added for.

| Topic | The figure has to show |
|---|---|
| `roc_curve` | The curve itself, with the operating points that a single accuracy number hides. |
| `f1_score` | The threshold sweep the page measures — 0.569 to 0.776 on one model. |
| `log_loss` | One confident error costing 13× a hesitant one. |
| `cohens_kappa` | Accuracy 0.912 sitting beside kappa 0.000 on the same predictions. |
| `mae` | The two objectives picking different lines through the same points. |

**M2 — Fit, capacity and the two likelihoods** (5).

| Topic | The figure has to show |
|---|---|
| `r_squared` | What "better than predicting the mean" is measured against. |
| `bias_variance` | The U-curve. The most-cited page in the AI sections has no picture of the thing it is about. |
| `outlier_detection` | The sample size below which the z-rule cannot fire at all. |
| `gaussian_nb` | One likelihood curve per feature per class — the shape the name promises. |
| `multinomial_nb` | Counts, and why the text classifier refuses to die. |

**M3 — Outputs that are shapes** (5).

| Topic | The figure has to show |
|---|---|
| `optics` | The reachability plot. It *is* the algorithm's output; the page currently describes a picture. |
| `hdbscan` | Cluster persistence across ε, which is the one thing DBSCAN's figure cannot say. |
| `fp_growth` | The FP-tree, on the `Tree` shape, with the compression counted. |
| `arima` | Differencing turning a wandering series stationary. |
| `exponential_smoothing` | Level, trend and season as three curves that sum to the fit. |

**M4 — Gradients and what each CNN changed** (6).

| Topic | The figure has to show |
|---|---|
| `vanishing_gradient` | Per-layer gradient magnitude, measured — the page says "measured, not asserted". |
| `padding_strides` | The output-size formula as a picture, including the border pixels nobody reads. |
| `resnet` | The addition, on `LayerStack` with the skip drawn. One edge is the whole contribution. |
| `inception` | The 1×1 bottleneck's arithmetic, as a cost comparison. |
| `vgg` | Two 3×3s against one 5×5: same receptive field, fewer parameters. |
| `mobilenet` | Depthwise separable convolution costing 8× less, as the division it is. |

**M5 — Detection** (5). The one block where the numbers only mean something side by side.

| Topic | The figure has to show |
|---|---|
| `rcnn` | Where the 47 seconds per image goes. |
| `yolo` | One grid, 98 boxes, and what the grid costs at the cell boundary. |
| `retinanet` | Focal loss against cross-entropy — one factor, drawn as two curves. |
| `unet` | Contract, expand, and the concatenated skips, on `LayerStack`. |
| `mask_rcnn` | The RoIPool quantisation the masks exposed, and what RoIAlign does instead. |

**M6 — Sequence models** (5).

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
and `extra_trees`' full-width aux row. M3's reachability plot and M7's trellis join that list on
arrival.

## Non-goals

- **The other 102 Tier-A topics.** Not scheduled. The trigger to build one is evidence that a
  specific page reads badly without it, not the fact that its neighbours have figures.
- **The 58 Tier-B topics.** Content first — see `phase-12-figure-scope.md`. Forty-two are RL, and
  deepening those pages is worth its own phase on merits that have nothing to do with figures.
- **The 16 Tier-C topics.** Never.
- **New shapes.** Nine cover everything in this list. If a figure here seems to need a tenth, the
  figure is wrong before the renderer is.
