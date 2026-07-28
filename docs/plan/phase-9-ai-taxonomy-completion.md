# Phase 9 — AI Taxonomy Completion

Status: In progress — Track A complete (RL at 100% of the doc), B1–B5 done. 57 of 226 topics
authored; taxonomy at 257, target 426. AI sections now: ML 58, DL 15, NLP 12, RL 74.
Next: B6 (Dimensionality Reduction, 8 topics).

**Outstanding, and slipping:** both guards this plan specifies as "New" — `AiTaxonomyCoverageTest`
and the category-integrity assertion — are still unbuilt after five batches. See the Guards section.
Depends on: Phase 5 (AI mode shell + content template), Phase 3 (simulation widgets), Phase 8 (premium gating)

## Goal

Close the gap between `docs/topics.ai.md` — the full AI taxonomy, 357 listed entries / 339 unique —
and what the app actually ships. Today the four AI sections carry **102 topics**; the taxonomy calls
for **~328 distinct topics** once cross-listed entries (RLHF ×3, MDP ×2, LSTM ×3, Perceptron ×2) are
collapsed. This phase authors the missing **226**, taking the whole app from 200 to **426 topics**.

## Baseline coverage (measured 2026-07-28, before any batch landed)

| Section | Doc entries | App topics | Covered | New topics needed |
|---|---|---|---|---|
| Machine Learning | 113 | 16 | ~24 | **88** |
| Deep Learning | 96 | 15 | ~26 | **73** |
| NLP | 77 | 12 | ~18 | **50** |
| Reinforcement Learning | 71 | 59 | 56 | **15** |
| **Total** | **357** | **102** | — | **226** |

"Covered" counts doc entries satisfied by *any* app topic including cross-section reuse (e.g. the
doc's ML → NN Foundations block is largely satisfied by Deep Learning topics; the doc's DL → Deep RL
block is fully satisfied by the RL section).

RL was the only near-complete section at that point: everything from **DQN Family** downward
shipped, plus three topics the doc does not list (VDN, QMIX, MADDPG). Its two gaps were its two most
foundational sub-sections, and Track A closed both.

This table is the starting snapshot and is deliberately left unedited. Live status is the table
below.

## Progress

| Batch | Topics | Status | Notes |
|---|---|---|---|
| A1 · RL Core Concepts | 8 | **Done** | POMDP sensor replaced after verification |
| A2 · RL Tabular Methods | 7 | **Done** | `RlGridWorldSection` gained a world parameter for the cliff walk |
| B1 · Regression | 11 | **Done** | New `RegressionLab` sim type |
| B2 · Classification | 5 | **Done** | New `DecisionSurface` sim type |
| B3 · Bayesian Algorithms | 7 | **Done** | No new widget; CNB sign bug caught |
| B4 · Ensemble Methods | 9 | **Done** | No new widget; XGBoost and LightGBM arithmetic corrected |
| B5 · Clustering | 10 | **Done** | No new widget; `twoMoons` geometry replaced |
| B6 · Dimensionality Reduction | 8 | Next | |
| B7–B10 | 38 | Planned | |
| Track C (C1–C9) | 73 | Planned | `FeatureMapPlayer` still outstanding |
| Track D (D1–D6) | 50 | Planned | |
| **Total** | **226** | **57 done** | Taxonomy 200 → 257 of 426 |

Sections against the doc: **RL 100%**, ML 58 topics of its eventual ~100, DL and NLP untouched at 15
and 12.

## Decisions taken before planning

**1. Categories expand to match the doc's sub-sections.** ML, DL and NLP each carry only two
categories today (`ml_supervised`/`ml_unsupervised`, `dl_fundamentals`/`dl_architectures`,
`nlp_preprocessing`/`nlp_modeling`) because the design mock's `cats()` never fleshed those sections
out. 88 ML topics across two buckets is unbrowsable. The mock's **RL** taxonomy — the one AI section
it *did* detail — uses nine categories, so expanding is following the mock's own most-developed
precedent rather than departing from it. `docs/topics.ai.md`'s bold headings become the category
list. This is a deliberate, recorded departure from the ROADMAP's "no new categories were invented"
note, which was scoped to the 2026-07-28 content expansion.

**2. Every new topic meets the existing content bar.** Full 7-section `TopicContent` (whatIsIt ×3
paragraphs, 5–6 `StepCard`s, `formulas` + `notationKey`, 1–2 `CodeBlock`s, `applications`,
`takeaways`, `crossLinks`, `prerequisites`) plus a **bespoke simulation config** — no
`NotYetAvailable`, no shared-config shortcuts. `ContentCoverageTest` stays green throughout, which
means no batch may land a topic row without its content file in the same change.

## New category structure

Existing categories are kept where a doc heading matches; the two generic ML/DL/NLP buckets are
retired once their topics are redistributed.

**Machine Learning — 12 categories.** Built so far: `ml_regression`, `ml_classification`,
`ml_bayesian`, `ml_ensemble`, `ml_clustering`. Still to come: `ml_dimreduction`,
`ml_nn_foundations`, `ml_association`, `ml_timeseries`, `ml_preprocessing`, `ml_metrics`,
`ml_rl_fundamentals`.

`ml_supervised` and `ml_unsupervised` are being hollowed out a batch at a time rather than deleted up
front, so that no category is ever empty mid-phase. They currently hold the leftovers —
`ml_supervised`: `perceptron`, `bias_variance`, `regularization`, `model_evaluation`;
`ml_unsupervised`: `pca`. B6 takes `pca`, B9 takes the evaluation and practice topics, and B10 takes
`perceptron`. Both buckets should be gone by the end of Track B, and if either still exists then,
something was missed.

**Deep Learning — 11 categories** (`dl_basics`, `dl_activations`, `dl_cnn`, `dl_detection`,
`dl_rnn`, `dl_transformers`, `dl_generative`, `dl_deep_rl`, `dl_optimizers`, `dl_regularization`,
`dl_specialized`)

**NLP — 11 categories** (`nlp_preprocessing` (kept), `nlp_statistical`, `nlp_syntax`,
`nlp_embeddings`, `nlp_rnn`, `nlp_transformer`, `nlp_pretrained`, `nlp_modern_llm`,
`nlp_finetuning`, `nlp_beyond`, `nlp_metrics`)

**Reinforcement Learning — 10 categories** (existing 9 + new `rl_tabular`; `rl_foundations` absorbs
the doc's Core Concepts block)

Colors and icons follow the established convention — pull the palette from the mock's `cats()['rl']`
ramp and reuse `resolveIcon()` names already in the icon set. No new icon assets.

## Batches

Twenty-seven batches (A×2, B×10, C×9, D×6), each one or two categories, sized so a batch is a single
session's work and lands green. Order is dependency-first: foundations before the topics that
cross-link to them.

### Track A — Reinforcement Learning (15 topics, closes the section)

**A1 · RL Core Concepts** — 8 topics into `rl_foundations` — **Done**
`agent_environment` · `state_action_reward` · `policy` · `value_function` · `q_function` ·
`discount_factor` · `exploration_exploitation` · `pomdp`

All eight authored with full 7-section content and a bespoke simulation. The `rl_foundations` list is
now ordered as a reading path rather than alphabetically, with `mdp` and `q_learning` slotted into
their real positions in it. Gating: the first three are free, the rest premium.

Seven of the eight run on `RlGridWorldSection`, which gained two additive fields on `RlFrame` and the
frame builders to use them:
- `qTable` — renders all four action values around the cell edges in ↑↓←→ order with the largest
  highlighted, so `V(s) = max Q(s,a)` and `π(s) = argmax Q(s,a)` are legible rather than asserted.
- `fog` — cells the agent cannot distinguish, drawn as "?".

New builders: `agentEnvironmentFrames` (one rollout split into agent-side and environment-side
half-steps), `stateActionRewardFrames` (same rollout as (s,a,r,s′) tuples with the discounted return
accumulating), `policyFrames` (random / hand-written / optimal π, each with the Vπ it actually
achieves — the hand-written "right then up" rule walks into the pit, which is the point),
`valueFunctionFrames` (iterative policy evaluation of that fixed policy, then one greedy improvement
step), `qFunctionFrames` (Q-learning with the full table visible), `discountFactorFrames` (the same
MDP solved at γ = 0, 0.5, 0.9, 0.99 so the horizon is visible as how far value spreads), and
`pomdpFrames` (an exact Bayes filter).

`exploration_exploitation` went to `BanditSection` instead as a deliberately broken pure-greedy
strategy: one forced sample per arm, then permanent commitment. The pull counts show the agent
pouring every remaining pull into an arm it has no evidence is best — the lock-in is the argument.

**Correction worth recording:** the POMDP sim first used "which of my four moves are blocked" as the
observation. That sensor turns out to identify almost every cell of this 4×4 grid uniquely, so the
aliasing narration would have been false. It was replaced with a coarser bump counter — *how many*
sides are blocked, not which — verified to give 7 candidate states at the start, narrowing to 2 then
1 over two moves. All the counts in the status text are computed from the filter's actual support, so
the narration cannot drift from what the code did.

**A2 · RL Tabular Methods** — 7 topics into new `rl_tabular` — **Done**
`bellman_equation` · `dynamic_programming` · `policy_iteration` · `value_iteration` ·
`monte_carlo_rl` · `td_learning` · `sarsa`

New category `rl_tabular` ("Tabular Methods (Classical)"), slotted between Foundations and the DQN
family. Icon is `map`, not `grid` — `grid` is not in `resolveIcon`'s set and would have fallen back to
a generic circle. `bellman_equation` is free, the rest premium. Note the id: RL's Monte Carlo is
`monte_carlo_rl` because `monte_carlo_method` already belongs to the Algorithms taxonomy (randomized
sampling); the two cross-link to each other.

**`RlGridWorldSection` gained a world parameter.** The grid was a set of file-level constants, which
was fine until SARSA — whose entire behaviour is invisible unless the layout punishes walking beside a
hazard, and the default 4×4 does not. It is now an `RlWorld` (rows, cols, start, goal, pits, walls,
step cost, terminal rewards) carrying `step`/`rewardFor`/`backup`/`greedyPolicy` as methods, with
`DefaultWorld` plus top-level shims so every previously-written builder reads exactly as it did.
`RlFrame` carries its world, and the grid and legend render from it — the legend labels its rewards
from the frame's own world rather than the hardcoded "+1/−1".

`CliffWorld` is Sutton & Barto's cliff walk compressed to 4×4: start bottom-left, goal bottom-right,
cliff between them, −1 per step and −100 to fall in (the textbook's 100:1 ratio, which is what makes
the two algorithms disagree).

New builders: `bellmanEquationFrames` (one cell's backup, one frame per action, showing the
arithmetic then the max), `dynamicProgrammingFrames` (sweeps with a running backup count, framed
around the model being a precondition), `policyIterationFrames` (evaluate-to-convergence then improve,
reporting states changed per round and stopping when stable), `valueIterationDetailFrames` (tracks Δ
*and* the sweep where the greedy policy stops changing — which arrives several sweeps before the
values settle), `predictionFrames(useTd)` (MC and TD(0) predicting the same fixed policy, both scored
as max error against the exact Vπ from policy evaluation, so the comparison is measured rather than
asserted), and `sarsaFrames`.

**The SARSA result was verified before it was written.** A standalone replication over 5 seeds
confirmed the separation holds on a 4×4: Q-learning learns the 5-step cliff-edge path on every seed,
SARSA a longer detour (7–9 steps), and scored with exploration still on, SARSA fell in ~22 times per
500 episodes against Q-learning's ~39. The sim runs both from the same seed and reports the measured
falls and path lengths in its status text.

**RL is now complete** — all 71 `docs/topics.ai.md` RL entries covered, plus VDN/QMIX/MADDPG which the
doc does not list. Section total 74 topics.

### Track B — Machine Learning (88 topics)

**B1 · Regression** — 11 topics — **Done**
`polynomial_regression` · `ridge_regression` · `lasso_regression` · `elasticnet_regression` ·
`stepwise_regression` · `robust_regression` · `quantile_regression` · `bayesian_ridge` ·
`poisson_regression` · `isotonic_regression` · `lars`

First batch of the ML restructure. New category `ml_regression`, with the existing `linear_regression`
moved into it; `ml_supervised` keeps the rest until their own batches, so no category is ever empty
mid-phase. `polynomial_regression` is free, the other ten premium.

**New simulation type `RegressionLab`.** The mock's regression lab (`RegressionSimulationSection`) is
a hand-tuned port bound to Linear Regression and was left untouched for design fidelity. `RegressionLab`
is the config-driven sibling — same chrome (canvas, readout row, sliders, action button) with a
per-topic dataset, estimator, readouts and note. Two new files:
- `RegressionLabMath.kt` — the estimators, all real: ridge/OLS via Gaussian elimination on the normal
  equations, coordinate descent with soft-thresholding for lasso and ElasticNet, RANSAC with a
  consensus refit, pinball-loss subgradient descent for quantiles, a Bayesian posterior with matrix
  inversion for the predictive band, IRLS for Poisson, PAVA for isotonic, and a LARS path.
- `RegressionLabSection.kt` — the Compose widget plus the eleven configs.

Datasets are shaped per topic rather than shared: wavy data for the penalty topics, a six-point
contaminating population for RANSAC, fan-shaped heteroscedastic data for quantiles, sampled Poisson
counts, a monotone-with-plateaus series for isotonic, and a deliberate x-gap for Bayesian ridge so the
predictive band has somewhere to widen.

**Behaviours verified before the copy was written**, over 5 seeds each, so the notes describe what the
lab does rather than what the textbook says:
- Polynomial: held-out MSE bottoms at degree 4–7 and is 10–100× worse at degree 9, while train MSE
  keeps falling (0.018–0.062). The overfitting note is gated on the measured ratio.
- RANSAC: OLS slope lands at 0.74–0.76 against a true 0.85; RANSAC recovers 0.87–0.88 from 26–27
  inliers of 32.
- Poisson: the OLS line predicts negative counts below x ≈ 1.1–1.4 on every seed, so the note's
  "impossible for the data it is modelling" branch always fires.

The canvas clamps a curve's contribution to the y-range: an unpenalized degree-9 fit can excurse to
±1e4 between points, and letting that set the scale would flatten the data to a line. Off-scale
segments are skipped rather than clipped, since a clipped spike would read as a real feature.

**B2 · Classification** — 5 topics — **Done**
`svm_rbf` · `nu_svc` · `lda` · `qda` · `passive_aggressive`

New category `ml_classification`, now holding the doc's Classification block in full: the four that
already existed (`logistic_regression`, `knn`, `decision_trees`, `svm`) moved over from
`ml_supervised`, plus these five. All premium. `svm` was renamed to "Support Vector Machines (Linear)"
so the pair with `svm_rbf` reads correctly.

**New simulation type `DecisionSurface`.** The plan assumed `ClassifierPlayground` would cover this
batch; it will not. That widget's model *is* a straight line (`w₁x + w₂y + b`), so it cannot express a
kernel boundary, QDA's conic, class-conditional ellipses, or support vectors. `DecisionSurface`
renders an arbitrary decision function by sampling it on a 42×42 grid and tinting each cell by
predicted class with alpha from confidence — the boundary emerges as the seam where the tint flips, so
no contour tracer is needed. It also draws covariance ellipses, rings support vectors in amber and
errors in red, and washes out the tint inside a margin band so the ±1 corridor reads as a pale strip.
Two new files:
- `DecisionSurfaceMath.kt` — a working simplified SMO for the C-SVM dual (genuinely sparse support
  vectors, not a least-squares stand-in), linear and RBF kernels, closed-form LDA/QDA with 2×2
  eigendecomposition for the ellipses, and the real Passive-Aggressive online update with all three
  variants.
- `DecisionSurfaceSection.kt` — the widget plus the five configs.

This widget should carry B3 (Bayesian classifiers) and B4 (ensembles) as well, so the cost is
amortized across three batches rather than one.

`nu_svc` needed a judgment call: implementing a second QP for the ν formulation was not worth it, so
the lab searches C over a log-spaced sweep for the value whose support-vector fraction best matches
the requested ν — which is the known ν↔C equivalence — and then *reports the achieved fractions* so
the two-sided bound is checked on screen rather than asserted.

**Verified before the copy was written**, over 6 seeds:
- RBF SVM separates the concentric rings at 100% with ~19 of 90 points as support vectors, so the
  "closed curve no linear model can produce" claim is what the lab actually shows.
- ν-SVC: at ν = 0.1/0.3/0.5 the measured margin-error fractions came out 0.100/0.312/0.500 with
  support-vector fractions 0.113/0.325/0.500 — the sandwich holds, with the small finite-sample
  overshoot the note already accounts for.
- **A correction came out of this.** QDA beats LDA on 5 of 6 seeds of the unequal-covariance dataset,
  not all 6, but the QDA note asserted the win unconditionally. Both the LDA and QDA notes now compute
  the comparison and describe whichever way it actually went — including the case where QDA loses,
  which is the more instructive outcome and states the reason.

**B3 · Bayesian Algorithms** — 7 topics — **Done**
`gaussian_nb` · `multinomial_nb` · `bernoulli_nb` · `complement_nb` · `categorical_nb` ·
`bayesian_networks` · `mcmc`

New category `ml_bayesian`, led by the existing generic `naive_bayes` moved over from
`ml_supervised`. All premium.

**Zero new widgets** — the check the note above asks for paid off. Configs went to four existing
labs: `DecisionSurface` (`gaussian_nb`), `TokenStripPlayer` (the four discrete variants),
`GraphAlgorithmPlayer` (`bayesian_networks`), `PointCloudPlayer` (`mcmc`). One new support file,
`NaiveBayesFrames.kt`, holds the shared corpus and the four fitted models so the frame builders
compute their arithmetic rather than hard-coding it.

`gaussian_nb` reuses the LDA/QDA dataset and fits a diagonal covariance, so the independence
assumption is visible as ellipses that cannot tilt next to QDA's that can. The four discrete
variants share one corpus and one test document, so what changes between them is only the
likelihood — Bernoulli's frames step through the whole vocabulary to show absent terms contributing,
which is the thing that actually distinguishes it from multinomial.

**Two corrections came out of verification, both real bugs rather than wording:**
- **Complement NB had the sign inverted.** I wrote the weight as −log(θ̃) and kept Rennie's argmin
  decision rule, which is self-cancelling: the lab confidently picked the *wrong* class on the
  imbalanced corpus (politics for a document reading "goal match"). Rennie's weight is log(θ̃) with
  argmin; fixed, and the lab now scores sports at −0.502 against politics at −0.212.
- **The MCMC proposal scales were wrong.** Acceptance was measured against the actual target rather
  than assumed: the "tuned" chain at half-width 0.55 was accepting 66%, and the "too large" chain at
  3.2 was accepting 18% — which is the *well-tuned* regime, not a failure. Now 3.0 (24%, the
  random-walk rule of thumb), 0.06 (95%) and 8.0 (2%), with the measured values recorded in a
  comment.

Also softened two overclaims: the complement frame now states which model was right rather than
assuming multinomial fails, and notes that the two scores are on different scales and not comparable.
The Bayesian-network explaining-away numbers were verified by exact enumeration — P(Rain) 0.500 →
0.708 on wet grass → 0.320 once the sprinkler explains it.

**B4 · Ensemble Methods** — 9 topics — **Done**
`bagging` · `extra_trees` · `voting` · `stacking` · `adaboost` · `xgboost` · `lightgbm` ·
`catboost` · `isolation_forest`

New category `ml_ensemble`, holding the doc's Ensemble block in full — the existing `random_forest`
and `gradient_boosting` moved over from `ml_supervised` and were renamed to the doc's titles
("Random Forests", "Gradient Boosting Machines (GBM)"). All premium. `ml_supervised` is now down to
`perceptron`, `bias_variance`, `regularization` and `model_evaluation`, which B9 and B10 will
redistribute.

**No new widgets again.** Six configs went to `PointCloudPlayer` and three to `TreeVisualizer`.
A shared `Stump` type plus an exhaustive `bestStump` search now sits in `PointCloudSection`, so the
boosting and bagging builders show the genuinely optimal split under the current weights rather than
a chosen one. AdaBoost runs the real algorithm — weighted error, α = ½ln((1−ε)/ε), exponential
re-weighting — and reports its own numbers.

The three GBM implementations went to `TreeVisualizer` because what distinguishes them in practice is
how they decide to grow, which is a tree-shape story: XGBoost's level-wise growth with a
second-order gain and γ-pruning, LightGBM's leaf-wise growth, and CatBoost's oblivious trees where
every node at a level shares one condition.

**Two arithmetic errors caught before the copy shipped:**
- **XGBoost's split gain was negative where the frame claimed it positive.** The G/H values I first
  picked (−5/5 and −1/3) give a gain of −0.29, so the frame narrated taking a split the formula
  rejects. Replaced with −6/4 and −1/4, verified: gain +0.478, leaves 1.20 and 0.20, and a second
  split at −0.513 that γ correctly prunes.
- **LightGBM's leaf counts and gain totals did not follow from the drawing.** The frames said "six
  leaves" while rendering four, and the totals (8.5 vs 10.3) summed gains from splits that were never
  taken. Rewritten to an exactly checkable comparison: three splits and four leaves either way,
  level-wise realizing 4.0 + 0.3 = 4.3 and leaf-wise 4.0 + 2.1 = 6.1, because leaf-wise declines to
  spend a budgeted split on the 0.3 node.

**B5 · Clustering** — 10 topics — **Done**
`k_medians` · `k_modes` · `hierarchical_divisive` · `hdbscan` · `optics` · `mean_shift` · `birch` ·
`affinity_propagation` · `spectral_clustering` · `gmm`

New category `ml_clustering`, holding the doc's Clustering block in full — `kmeans`,
`hierarchical_clustering` and `dbscan` moved over from `ml_unsupervised`, with the hierarchical one
renamed to "Hierarchical (Agglomerative)" to pair with the new divisive topic. All premium.
`ml_unsupervised` is down to `pca` alone, which B6 will absorb.

**Still no new widget, fourth batch running.** Eight configs on `PointCloudPlayer`, one on
`TokenStripPlayer` (k-modes, which is categorical and has no scatter plot), one on `TreeVisualizer`
(divisive, which is a dendrogram). `CloudFrame` gained two additive fields, in the same spirit as
`RlFrame`'s earlier ones: `profile`, a bar strip under the scatter — OPTICS' reachability plot is the
algorithm's actual output and cannot be read off a scatter — and `ellipses`, because rings draw only
circles and a Gaussian mixture's whole advantage over k-means is that its components are not
circular.

New file `ClusteringMath.kt` holds the algorithms: Lloyd's loop parameterized by mean-vs-median,
mean-shift kernel steps, an OPTICS priority walk, complete-linkage agglomerative and diameter-driven
divisive, EM for a full-covariance Gaussian mixture, affinity propagation's two message updates, and
a Laplacian Fiedler vector.

**One outright failure caught by verification, and it invalidated the topic's premise.** The
`twoMoons` generator used hand-picked arc centres and radii, and the moons came out too weakly
interleaved: spectral clustering scored 0.67 against k-means' 0.65, so the entire reason the topic
exists — that spectral succeeds where k-means cannot — was simply not visible in the lab. Replaced
with scikit-learn's actual `make_moons` construction mapped into the unit square, which measures 0.98
against 0.85.

Two smaller fixes fell out of the same check:
- The Fiedler power iteration was under-converged. Because `c` must exceed λ_max to keep (cI − L)
  positive definite, the ratio (c−λ₃)/(c−λ₂) sits near 1 and convergence is slow by construction.
  Checked against a full eigendecomposition at n = 60: 400 and 2000 iterations agree on 95% of signs,
  6000 agrees exactly. Raised to 6000, with the measurement recorded in a comment.
- The mean-shift builder still contained drafting debris — a `while (…&& false) break`, a
  `repeat(0)` and a self-assignment — which made the iteration schedule meaningless. Rewritten to a
  real 1/2/4/8 snapshot schedule that also reports how far the furthest seed has travelled.

Both spectral frames now report the measured accuracy for k-means and for spectral rather than
asserting either. Verified separately: k-medians' centre sits 0.083 away from k-means' on the
outlier dataset, and mean shift finds exactly the 3 modes the data was built with.

**B6 · Dimensionality Reduction** — 8 topics
Kernel PCA · Incremental PCA · t-SNE · UMAP · SVD · ICA · Factor Analysis · LLE
*Sims:* `PointCloudPlayer` (projection onto components, neighbour-preservation animation for
t-SNE/UMAP, ICA unmixing two sources).

**B7 · Association Rules + Time Series** — 9 topics
Apriori · Eclat · FP-Growth · Moving Average · Autoregression · ARIMA · SARIMA ·
Exponential Smoothing · Prophet
*Sims:* `ArrayWalkPlayer` (candidate pruning per Apriori pass; rolling-window mean sliding a series),
`TreeVisualizer` (FP-tree), `RegressionExplorer` (AR coefficients, trend/seasonal decomposition).

**B8 · Data Preprocessing** — 9 topics
Min-Max Normalization · Z-Score Standardization · Label Encoding · One-Hot Encoding ·
Missing Value Imputation · SMOTE · Outlier Detection (IQR/Z) · Chi-Square Feature Selection · RFE
*Sims:* `ArrayWalkPlayer` (per-column transforms in place), `PointCloudPlayer` (SMOTE synthesizing
minority points along neighbour segments; IQR fences).

**B9 · Evaluation Metrics** — 17 topics
Confusion Matrix · Accuracy · MSE · RMSE · MAE · R² · Adjusted R² · Precision & Recall · F1 ·
ROC Curve · AUC · Log Loss · Gini Impurity · Hinge Loss · Cohen's Kappa · Silhouette · Davies-Bouldin
*Sims:* `PointCloudPlayer` threshold sweep with live cells/metrics (the `model_evaluation` frame
builder already does this — extend it per metric), `RegressionExplorer` (error metrics reacting to a
dragged outlier — the MAE-vs-MSE robustness payoff).
*Note:* the existing `model_evaluation` topic stays as the umbrella overview and becomes the
category's landing/prerequisite topic.

**B10 · NN Foundations + RL Fundamentals (ML view)** — 3 topics
Restricted Boltzmann Machines · Deep Belief Networks · Multi-Armed Bandit
The rest of both blocks is satisfied by cross-listing existing DL and RL topics into the new
`ml_nn_foundations` / `ml_rl_fundamentals` categories — no duplicate content files.
*Sims:* `NeuralNetPlayer` (contrastive-divergence up/down passes, layer-wise pretraining),
`BanditExplorer`.

### Track C — Deep Learning (73 topics)

**C1 · NN Basics** — 4 topics
The Biological Neuron · Multi-Layer Perceptron · Vanishing Gradient · Exploding Gradient
*Sims:* `NeuralNetPlayer` (gradient magnitudes per layer shrinking/blowing up across depth — makes
both problems visible rather than asserted).

**C2 · Activation Functions** — 10 topics
Sigmoid · Tanh · ReLU · Leaky ReLU · PReLU · ELU · SELU · Swish · GELU · Softmax
*Sims:* `RegressionExplorer` (function + derivative curves, saturation regions shaded) and
`NeuralNetPlayer` (dead-ReLU count per activation).

> **Note on new widgets.** The phase was planned assuming `FeatureMapPlayer` would be the only
> net-new simulation widget. B1 and B2 each needed one as well (`RegressionLab`, `DecisionSurface`),
> because the existing labs were bound to a single model shape. Both are config-driven and expected to
> serve later batches — `DecisionSurface` in particular should cover B3 and B4 — so the remaining
> batches should be checked against them before assuming more widget work.

**C3 · CNN Mechanics + Architectures** — 11 topics
Convolution Layers · Pooling · Padding & Strides · LeNet-5 · AlexNet · VGG · Inception · ResNet ·
DenseNet · MobileNet · EfficientNet · ViT
*Sims:* `FeatureMapPlayer` — an input grid, a sliding kernel, and the resulting feature map, with
stride/padding/kernel-size controls and a per-architecture layer stack. It also serves C4. (The
original plan called this the phase's only new widget; that turned out to be wrong — see the note
above.)

**C4 · Object Detection & Segmentation** — 9 topics
R-CNN · Fast R-CNN · Faster R-CNN · YOLO (V1–V8) · SSD · RetinaNet · U-Net · Mask R-CNN ·
Semantic vs Instance Segmentation
*Sims:* `FeatureMapPlayer` (region proposals → RoI pooling → boxes; YOLO's grid-cell prediction;
U-Net's contract/expand path with skip connections).

**C5 · RNN Mechanics** — 4 topics
BPTT · Bidirectional RNNs · Encoder-Decoder Architecture · Seq2Seq
*Sims:* `NeuralNetPlayer` (unrolled graph, gradient flowing back through time),
`TokenStripPlayer` (encoder consuming, decoder emitting).

**C6 · Transformers & Pre-trained Models** — 8 topics
Self- vs Cross-Attention · Multi-Head Attention · BERT · GPT · T5 · RoBERTa · DistilBERT ·
Hugging Face Tokenizers
*Sims:* `TokenStripPlayer` (attention weights per head, masked-LM vs causal masking side by side,
text-to-text framing).

**C7 · Generative Deep Learning** — 7 topics
VAE · DCGAN · CycleGAN · StyleGAN · Stable Diffusion Architecture · Neural Style Transfer · DeepFakes
*Sims:* `PointCloudPlayer` (latent-space sampling and interpolation; the existing diffusion
ring→noise→ring frame builder extends to the latent/conditioning story), `NeuralNetPlayer`
(generator/discriminator alternation, cycle-consistency loop).

**C8 · Optimizers & Losses** — 10 topics
Momentum · AdaGrad · RMSprop · Adam · AdamW · LR Schedulers · Cross-Entropy · BCE · Hinge · KL
*Sims:* `RegressionExplorer` (optimizer paths racing across the same loss surface — the single most
legible comparison in the set), plus loss-curve plots per loss function.

**C9 · Regularization + Specialized/Graph Networks** — 10 topics
Data Augmentation · Early Stopping · Layer Normalization · Group Normalization · Siamese Networks ·
GCN · GAT · Capsule Networks · Neural ODEs · KAN
*Sims:* `NeuralNetPlayer` (normalization axes; validation curve turning up at the early-stopping
point), `GraphVisualizer` / `GraphAlgorithmPlayer` (message passing per GCN layer, attention weights
on edges for GAT).
*Cross-links:* GCN/GAT ↔ the DSA `graph` and `graph_variants` topics.

### Track D — NLP (50 topics)

**D1 · Preprocessing + Statistical NLP** — 8 topics
Stop Word Removal · Lowercasing & Cleaning · RegEx · N-Grams · HMM · PCFG · Cosine Similarity ·
Jaccard Similarity
*Sims:* `TokenStripPlayer` (each cleaning stage mutating the strip; n-gram windows; Viterbi over HMM
states — the `ner` frame builder already does Viterbi), `PointCloudPlayer` (cosine vs Jaccard on the
same pair of documents).
*Cross-links:* Edit Distance and KMP/Rabin-Karp already exist in the DSA taxonomy — link, don't
duplicate.

**D2 · Syntactic & Semantic Analysis** — 6 topics
POS Tagging · Dependency Parsing · Constituency Parsing · Chunking · Coreference Resolution ·
Sentiment Analysis (Lexicon)
*Sims:* `TokenStripPlayer` (tag assignment, chunk spans, coref chains linking mentions),
`TreeVisualizer` (dependency arcs and constituency trees).

**D3 · Word Embeddings** — 5 topics
Word2Vec (CBOW) · Word2Vec (Skip-Gram) · GloVe · FastText · ELMo
*Sims:* `PointCloudPlayer` (2-D embedding space with the analogy-vector payoff),
`TokenStripPlayer` (context window sliding, subword decomposition for FastText).

**D4 · Transformer Internals + Pre-trained LMs** — 8 topics
Positional Encodings · Feed-Forward Networks · BART · XLNet · GPT-3 & GPT-4 · LLaMA & Vicuna ·
Mistral & Mixtral (MoE) · Claude & Gemini
*Sims:* `TokenStripPlayer` (sinusoidal position signal added to embeddings; MoE router sending tokens
to different experts), `NeuralNetPlayer` (the FFN block).

**D5 · Modern LLM Techniques** — 7 topics
Prompt Engineering · Chain of Thought · Tree of Thoughts · Vector Databases · ReAct ·
AI Agents & Tool Use · Hallucination Mitigation
*Sims:* `TokenStripPlayer` (the existing `rag` pipeline frame builder generalizes to ReAct's
thought/action/observation loop), `GameSearchPlayer` (Tree of Thoughts branching and pruning — reuses
the MCTS-family widget), `PointCloudPlayer` (vector-DB nearest-neighbour retrieval).

**D6 · Fine-Tuning + Beyond Transformers + Metrics** — 16 topics
Fine-Tuning (Full) · DPO · PEFT · LoRA & QLoRA · Quantization · Flash Attention · SSMs · Mamba ·
RWKV · Long Context Windows · Perplexity · WER · BLEU · ROUGE · METEOR · MMLU
*Sims:* `NeuralNetPlayer` (frozen vs trainable parameter counts; a low-rank adapter beside the full
weight matrix; weight histograms before/after quantization), `TokenStripPlayer` (n-gram overlap
scoring for BLEU/ROUGE/METEOR; per-token surprisal for perplexity), `ArrayWalkPlayer` (attention
tiling for Flash Attention; the recurrent state scan for SSM/Mamba/RWKV).
*Note:* this batch is oversized on purpose — the metrics topics are short and share one frame
builder. Split at the session boundary if it runs long.

## Per-topic implementation checklist

Unchanged from the Phase 5 / 2026-07-28 expansion precedent:

1. Topic row in the owning `feature/<section>/<Section>Topics.kt` — id, name, category, tagline,
   `isPremium`, `difficulty`.
2. `feature/topics/content/<Topic>Content.kt` — full 7-section `TopicContent`, ML/DL math notation,
   Python (scikit-learn / PyTorch) code blocks, `crossLinks` and `prerequisites` pointing at real ids.
3. Register the id in `TopicContentProvider.byId`.
4. Bespoke `SimulationType` config + frame builder entry in the relevant `*Section` widget.
5. Category registered in the section's `*Categories.kt` and included in its `all` list.

## Gating

Follow the established split — foundational free, advanced premium. Concretely: each new category's
one or two entry-level topics stay free (Bellman Equation, Convolution Layers, Stop Word Removal,
Confusion Matrix, Sigmoid, Momentum, …); everything else is `isPremium = true`. The NLP doc already
carries explicit `🔒` markers for 61 entries — honour those verbatim. ML/DL/RL carry no markers, so
apply the rule above. This roughly triples the premium pool, which is the point of the phase from a
monetization angle.

## Guards

**Shipping and green (46 tests):**
- `ContentCoverageTest` — every browsable topic has content, nothing resolves to `NotYetAvailable`,
  and every prerequisite and cross-link id resolves. It has caught real breakage every batch and is
  run (with `--rerun-tasks`) at the end of each one.

**Specified here, and still not built after five batches:**
- `AiTaxonomyCoverageTest` + a checked-in `AiTaxonomyMap.kt` mapping each `docs/topics.ai.md` entry
  to the topic id serving it. Without it, "how much of the doc is covered?" is still a manual recount
  — which is exactly how the ~24 / ~26 / ~18 "covered" figures in the baseline table were produced,
  and they are estimates rather than measurements.
- The category-integrity assertion: every `categoryId` resolves within its section's `all` list, and
  no category is empty.

**This slip is the plan's largest open risk, and it compounds.** Five batches have restructured ML's
categories by hand with nothing checking the invariant, and `ml_supervised`/`ml_unsupervised` are
mid-migration precisely where an empty or orphaned category would appear. The taxonomy map also gets
more expensive to write the longer it waits, since every batch adds entries to backfill.

**Recommendation: build both before B6**, not at the end of the phase. They are perhaps an hour of
work, the category assertion is a dozen lines, and the taxonomy map turns the phase's headline
question into a test result. Deferring them again should be a deliberate decision rather than a
default.

## Risks / open items

- **Widget estimation was wrong, and by a lot.** The phase was planned around one net-new simulation
  widget. Three batches in, two config-driven sim types had shipped (`RegressionLab`,
  `DecisionSurface`) plus additive fields on `RlFrame` and `CloudFrame`. The pattern that emerged:
  existing widgets were each bound to one model shape, so a batch either fits an existing one exactly
  or needs a new config-driven sibling. B3, B4 and B5 then needed nothing new, so the cost is
  front-loaded per *family* rather than per batch. Check the remaining batches against what now
  exists before assuming more.
- **`FeatureMapPlayer` is still net-new** and blocks C3 and C4. Build it while C1/C2 land.
- **Verification keeps finding real defects, not wording problems.** Across five batches: an
  inverted Complement NB sign that produced confidently wrong classifications, MCMC step sizes whose
  "too large" case sat in the well-tuned regime, an XGBoost split gain that was negative where the
  narration took it, LightGBM leaf counts that contradicted the drawing, and a `twoMoons` geometry
  that made spectral clustering score 0.67 against k-means' 0.65 — erasing the reason that topic
  exists. Every one of these reads plausibly and is wrong. **Numeric claims in a lab must be measured
  before the copy around them is written**, and notes that could go either way should compute the
  comparison rather than assert it.
- **Topic-id collisions.** `TopicRegistry` collapses cross-listed ids first-wins (`perceptron`,
  `transformers`). Tracks C and D cross-list far more (backprop, LSTM, attention, RLHF, all seven
  Deep RL entries). Without `AiTaxonomyMap` these stay accidental rather than reviewable.
- **Browse-list length.** 426 topics changes the character of the category screens, and ML alone is
  heading for 12 categories. Per-category search exists (Phase 2); scroll performance and whether
  Home's Quick Access grid is still a sensible sole entry point are both unchecked.
- **Batch B9 (17 metrics) and D6 (16)** remain the thin-topic batches, and the most likely to read as
  filler if authored mechanically. Each metric needs its own "when this one misleads you" angle.

## Suggested order

~~A1 → A2~~ → ~~B1 → B2 → B3 → B4 → B5~~ → **guards** → B6 → B7 → B8 → B9 → B10 → C1 → C2 →
`FeatureMapPlayer` → C3 → C4 → C5 → C6 → C7 → C8 → C9 → D1 → D2 → D3 → D4 → D5 → D6.

Struck-through batches are done. The `guards` step is inserted deliberately: see the Guards section
for why it should not slip past B6.

## Verification

Per batch: `./gradlew testDebugUnitTest` (coverage + taxonomy + category guards) then
`./gradlew assembleDebug` and a manual walk of the touched category screen — list renders, every row
opens, sim plays, premium rows gate.
