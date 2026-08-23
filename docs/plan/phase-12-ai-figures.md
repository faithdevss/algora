# Phase 12 — Figures for AI topics (pilot)

Status: **Pilot shipped, free tier closed, premium extension in progress.** 108 of 325 AI topics
carry a figure — the A1–A5 pilot plus A6–A17. **The remaining 217 are not all getting one:**
`phase-12-figure-scope.md` measures every one of them and splits them 143 build / 58 content-first /
16 never, with A18 onward ordered there. (The "323" this line carried through A17 was an undercount —
two topics whose `topic(` call spans lines differently were missed by the tally; 325 is the measured
number, and `AiTaxonomyCoverageTest` is what enforces the section lists themselves.)

Depends on: the figure layer (`core/data/model/Figure.kt`, `feature/topics/FigureCard.kt`,
`FigureShapeTest`) and Phase 11, which took it to 122/122 Data Structures and Algorithms topics.

**Every free-tier AI topic now has a figure** (A9–A12 closed the last 18). Everything still
without one is premium, which is the right order: a reader who has not paid sees a picture on every
page they can open.

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
| ML / DL / NLP / RL | 325 unique (356 entries, 33 cross-listed) | **108** (all free-tier topics, plus A13–A17) |

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
| A10 | Free-tier DL foundations & CNN mechanics | `biological_neuron`, `perceptron`, `conv_layers`, `pooling_layers`, `early_stopping`, `data_augmentation` |
| A11 | Free-tier NLP | `bert`, `perplexity`, `prompt_engineering`, `wer`, `transfer_learning` |
| A12 | The last of the free tier | `neural_style_transfer`, `segmentation_types`, `siamese_networks`, `apriori`, `moving_average`, `multi_armed_bandit`, `svd` |
| A13 | First premium batch — ML classification | `logistic_regression`, `knn`, `svm`, `svm_rbf`, `nu_svc`, `lda`, `qda`, `passive_aggressive` |
| A14 | Premium — ensembles that average | `bagging`, `random_forest`, `extra_trees`, `voting`, `stacking`, `isolation_forest` |
| A15 | Premium — boosting | `adaboost`, `gradient_boosting`, `xgboost`, `lightgbm`, `catboost` |
| A16 | Premium — clustering beyond k-means | `dbscan`, `gmm`, `hierarchical_clustering`, `mean_shift`, `spectral_clustering`, `k_medians` |
| A17 | Premium — dimensionality reduction | `pca`, `ica`, `tsne`, `umap`, `lle`, `factor_analysis` |

Each batch's shape work landed in the same commit as its first figures — a shape with no caller is
unreviewable.

## Files touched

- `core/data/model/Figure.kt` — three shape variants plus `FigureSeries`, `FigurePoint`, `FigureBar`,
  `FigureLayer`. `FigureCell` is reused by `Heatmap.marks`.
- `feature/topics/FigureCard.kt` — `PlotFigure`, `LayerStackFigure`, `HeatmapFigure`, three `when`
  branches. Also dropped a dead `widestNode` local left in `GraphFigure` by F2.
- `feature/topics/content/*.kt` — 30 files gained a `figure = Figure(…)` argument after `topicId`.
  A6–A17 added 78 more the same way; no renderer change since A4.
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

**Scope, before cost:** `phase-12-figure-scope.md` is the list the remaining batches build from, and
its finding is that 74 of the 217 remaining topics should not get a figure now or at all. That
document was written after A17, which means A13–A17 were built on the wrong assumption — that every
topic eventually gets one. No figure already shipped is wrong because of it; what it changes is what
comes next.

**Cost, for planning the full phase:** roughly 4–8 topics per commit, with the constraint being how
much of each page's prose has to be read before its figure is honest — not the spec, which is 15–40
lines. A shape-only batch (A1, A3, A4) costs about one extra hour for renderer plus tests.

## Verification

Per batch: `./gradlew :app:testDebugUnitTest` (`FigureShapeTest`, `AiFigureCoverageTest`,
`DsaFigureCoverageTest`, `FigureCoverageTest`, `ContentCoverageTest`) then `./gradlew assembleDebug`.
Both green for A1–A17.

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

**Still outstanding:** the other 21 pilot pages, light mode for six of the nine walked, and all of
A9–A17. The riskiest cells there, in the order worth checking: `knn`'s graph, which hangs five
labelled edges off one node — the densest edge labelling in the app, and the only cell in A13 no unit
test can size; the `mdp` and `apriori` graphs, whose hand-placed nodes push to y = 0.04/0.96;
`hierarchical_clustering`'s dendrogram, the largest tree in the app at eleven nodes and six leaves —
every earlier one stopped at nine, and the radius is capped by slot width, so six leaves is where a
label starts to outgrow its circle; `qda`'s 7×7 heatmap at 182dp with nine outlined cells; `extra_trees`' seven-cell strip, the first to
carry a full-width aux row of five-character numbers; `catboost`'s 3×6 grid, whose six four-character
cells plus row headers are the narrowest columns any grid has asked for; `policy`'s heatmap row
labels, which get 16%
of the card's width at 9sp; `conv_layers`' 7×7 grid, the tallest in the app at 210dp; and `bert`'s
six-cell strip, where "[MASK]" has to fit a sixth of the width at labelMedium.

**A17's figures are mechanisms, not outputs — deliberately.** Every page in this batch produces a
scatter plot when it runs, and a scatter plot of a t-SNE map is the one thing the t-SNE page already
warns against reading. So each figure draws the machinery instead: the two kernels whose ratio is 9×
at d = 3 and 175× at d = 4, UMAP's two ρ-anchored weight curves at σ = 0.056 and σ = 0.310, and ICA's
kurtosis sweep, whose peaks at 82.5° and 172.5° hit 1.19 and 1.21 against source kurtoses of 1.19 and
1.21 — the unmixing lands on the sources to two decimal places, computed rather than claimed. `lle`
needed a substitution: its embedding quality against k came out non-monotone (an eigenvector
degeneracy in one dimension, not a property of the method), so the figure plots neighbourhood purity
and short-circuit count instead, both clean and both the actual failure mode. `factor_analysis`
reuses `catboost`'s narrow-grid shape to put PCA and FA side by side on the same six variables.

**A16 ran four of six algorithms outright.** Mean shift, single-linkage agglomeration, the DBSCAN
core/border/noise labelling and the graph Laplacian's spectrum were all executed in NumPy rather than
described — which is where the figures earned something the prose had not said. The bandwidth sweep
came out 11, 10, 5, 3, 3, 2, 2, 2, 1, so the window that recovers the true three clusters is two
values wide out of nine, a far narrower target than "sensitive to bandwidth" suggests. GMM's
responsibility curves were the opposite correction: the band where neither component exceeds 80% is
6% of the range, so "soft clustering" is hard almost everywhere, and the figure says which 6% it is
not. The 4-NN moons graph is the one figure that came out cleaner than expected — exactly two zero
eigenvalues, so the count is readable off the spectrum before any clustering runs.

**A15 closed the ensemble block, and two of its five figures are the page's own code output.**
`lightgbm` draws the level-wise-versus-leaf-wise example the page states in ASCII — 4.3 against 6.1
for the same three splits — as an actual tree with the forced 0.3 node marked, and `catboost` runs
both encodings over the page's six rows so the leak is visible as one number appearing twice
(city C, naive 1.00, label 1). `gradient_boosting` is the only figure in the phase so far that
required running the algorithm itself: 100 rounds of depth-1 stumps on residuals at ν = 0.1, RMSE
0.258 → 0.147 → 0.044, and the staircase in the plot is the real fitted function rather than a drawn
impression of one. `adaboost`'s α curve needed the only axis trick in the phase — α goes negative, so
the plot maps α = 0 to mid-axis and the caption says so.

**A14 wrote the models rather than importing them.** The batch's two most useful figures are
measurements, and sklearn is still not installed, so `stacking`'s leakage gap comes from a 1-NN and a
closed-form LDA implemented in NumPy over a 400-row synthetic set and scored twice — 1.000 in-sample
against 0.778 out-of-fold, versus 0.830 against 0.825 for the stable model. That second pair is the
part worth having: it shows leakage inflating members *unequally*, which is why the meta-learner ends
up trusting the worst-behaved one. `extra_trees` is the same move at smaller scale — real Gini gains
for all seven candidate cuts on one eight-value node, so the forest's 0.281 and the random draw's
0.040 are computed rather than asserted. The other four are exact arithmetic on the pages' own
formulas, and `bagging` and `random_forest` were deliberately drawn as one argument across two pages:
the variance floor is ρ, and √p feature sampling is what moves it.

**A13 computed every value in NumPy first.** The page's own numbers were the starting point but not
the figure: LDA's code block runs a symmetric Σ where w lands on the line between the means and the
lesson is invisible, so the figure runs the asymmetric Σ the block's comment only promises — w =
(1.94, 0.06), a 43.2° rotation off μ₁ − μ₀. `knn`'s seven points were placed until the k = 3 and
k = 5 votes actually disagree (B 2–1, then A 3–2 at distances 0.11 / 0.13 / 0.18 / 0.25 / 0.25), and
`qda`'s heatmap is the real posterior of N(0, 0.6²) against N(0, 1.5²), equal-density circle at
r = 0.886. sklearn is not installed here, which ruled out the two figures that wanted measured
accuracy — the γ×C grid and the LDA-vs-QDA crossover — so `svm_rbf` draws the kernel decay itself and
`nu_svc` draws the feasibility cap, both exact in closed form.

**A10–A12 ran the labs rather than reading the prose.** `EarlyStoppingLab`, `firingRate`,
`PerplexityLab` and `PromptLab` were each executed from a scratch test to get the plotted values —
which is how the f–I curve's 212 Hz at 80 units (the page's Python comment says "~215") and
perplexity's k = 0.01 optimum ended up on the axes as measured rather than approximate numbers.

## Non-goals

- **The 58 Tier-B topics**, until their pages are deepened — see `phase-12-figure-scope.md`. A figure
  over 300 characters of prose is the figure becoming the page, and there are no measured numbers on
  those pages for it to follow.
- **The 16 Tier-C topics**, permanently. Environments, product rosters and figures a neighbouring
  topic already draws.
- **Phase 11's F8 sweep.** Skipped by decision; `feat/dsa-figures` still ends at F7.
- **Replacing any simulation.** Figures sit above the steps; the labs are untouched.
