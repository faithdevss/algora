# Phase 9 — AI Taxonomy Completion

Status: In progress — Track A complete (RL at 100% of the doc), B1–B7 and C1 done. 78 of 226 topics
authored; taxonomy at 278, target 426. AI sections now: ML 75, DL 19, NLP 12, RL 74.
**Doc coverage is measured, not estimated: 197 of the doc's 357 entries (55%).**
Next: C2 (Activation Functions, 10 topics) — or back to B8, which is still open. Track B was
interrupted after B7 by a deliberate jump to Track C; **B8, B9 and B10 remain unbuilt**, and
`ml_supervised` therefore still holds the four topics they were to redistribute.

**Both guards are now built.** Phase 10 landed the category-integrity assertion
(`CategoryIntegrityTest`, generic over all eight sections), and B6 landed
`AiTaxonomyCoverageTest` — the piece this plan specified five times and deferred five times.

It maps every one of `docs/topics.ai.md`'s 357 entries, nested by the doc's own `#` section and
`**bold**` heading, to the topic id or ids serving it or to an explicit `emptyList()`. The entry
count checked out at exactly 357, so the plan's headline figure was right where phase 10's "92 doc
entries" claim was wrong by three. Four things are now pinned: the entry count, that every mapped id
resolves in `TopicRegistry`, that every mapped id belongs to an AI section (with one recorded DSA
exception, `edit_distance`), and the covered-entry count — which every batch has to move, and whose
failure message prints the remaining backlog straight from the doc.

The rule it applies is strict, and it lowered the headline number on purpose. An entry counts as
covered only when a topic exists whose *subject is that entry*. Six app topics are umbrellas written
before the doc was broken into sub-sections — `activation_functions`, `cnn`, `llms`,
`word_embeddings`, `model_evaluation`, `naive_bayes` — and each stands in front of a whole block.
They count as covering nothing. The baseline table's "~24 / ~26 / ~18" figures were produced by
counting umbrellas as their blocks, which is exactly the estimate this test exists to replace.
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
| B6 · Dimensionality Reduction | 8 | **Done** | New `DimReductionMath.kt`; no new widget. `ml_unsupervised` retired |
| — · Guards | — | **Done** | `AiTaxonomyCoverageTest` + `DimReductionMathTest` |
| B7 · Association Rules + Time Series | 9 | **Done** | No new widget; one additive `LabCanvas` fix. `B7MathTest` added |
| B8–B10 | 29 | **Skipped for now** | Deliberate jump to Track C; `ml_supervised` still awaits them |
| C1 · NN Basics | 4 | **Done** | No new widget. New `NeuralNetPlayer` frame guard found 4 live bugs |
| C2–C9 | 69 | Planned | `FeatureMapPlayer` still outstanding, blocks C3/C4 |
| Track D (D1–D6) | 50 | Planned | |
| **Total** | **226** | **78 done** | Taxonomy 200 → 278 of 426 |

Sections against the doc, measured by `AiTaxonomyCoverageTest` rather than counted by hand:
**RL 71/71**, ML 84/113, DL 26/96, NLP 16/77 — **197 of 357 overall**. ML is 75 topics of its
eventual ~100; DL has started at 19; NLP is untouched at 12. Browsable topics across the app: 301.

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
`ml_bayesian`, `ml_ensemble`, `ml_clustering`, `ml_dimreduction`, `ml_association`, `ml_timeseries`.
Still to come: `ml_nn_foundations`, `ml_preprocessing`, `ml_metrics`, `ml_rl_fundamentals`.

`ml_supervised` and `ml_unsupervised` were hollowed out a batch at a time rather than deleted up
front, so that no category was ever empty mid-phase. **`ml_unsupervised` is now gone** — B5 took its
clustering topics and B6 took `pca`, its last one. `ml_supervised` still holds `perceptron`,
`bias_variance`, `regularization` and `model_evaluation`; B9 takes the evaluation and practice
topics and B10 takes `perceptron`. It should be gone by the end of Track B, and if it still exists
then, something was missed. `CategoryIntegrityTest` is what makes that safe to do incrementally.

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

**B6 · Dimensionality Reduction** — 8 topics — **Done**
`svd` · `kernel_pca` · `incremental_pca` · `ica` · `factor_analysis` · `tsne` · `umap` · `lle`

New category `ml_dimreduction` ("Dimensionality Reduction", icon `chart` — the scree plot, since
`stack` already belongs to Ensemble Methods and would render the same glyph twice in one browser),
led by the existing `pca` moved over from `ml_unsupervised`, which is now deleted. `svd` is free
(it is the linear algebra the first four topics are special cases of); the rest premium. Ordered as
the two families the block really contains: the linear factorizations first, then the three manifold
methods, which answer a different question and are not substitutes for them.

**No new widget, fifth batch running** — all eight configs went to `PointCloudPlayer`, and
`CloudFrame` needed no new fields either (`profile`, added in B5, carries factor analysis's loadings
and uniquenesses). One new support file, `DimReductionMath.kt`, holds the estimators: a cyclic
Jacobi eigensolver (chosen because both kernel PCA and LLE need a full spectrum, from opposite ends
of it), kernel PCA with the double-centred Gram matrix, an exact streaming-covariance incremental
PCA, SVD via XᵀX for the n×2 case, FastICA with the tanh fixed point after whitening, closed-form
one-factor analysis, a real t-SNE (per-point perplexity binary search, early exaggeration,
Student-t Q, gradient descent on KL), UMAP's fuzzy simplicial set plus its layout with the repulsive
term computed exactly rather than negative-sampled, and LLE's constrained reconstruction weights
into the bottom eigenvectors of (I−W)ᵀ(I−W).

**Verification found three problems before any copy was written, and one of them killed a premise.**
- **Kernel PCA did not separate the rings at all** — 61–77% at every γ tried, against linear PCA's
  75%. Two causes, both real. The ring dataset sampled angles at *even* spacing, making it exactly
  rotationally symmetric, so the leading kernel components were the angular harmonics (visible as
  near-degenerate eigenvalue pairs: 6.76 / 6.69) and carried no radius information. And the γ sweep
  was calibrated to the wrong scale. Rebuilt on scikit-learn's `make_circles(factor=0.35,
  noise=0.05)` geometry in its own coordinates with sampled angles: γ = 4 now separates at 100%
  against linear PCA's 75%, and γ = 0.5 and γ = 16 land at 73% and 83%, which is what makes the
  "the bandwidth is the model" frame a measurement rather than a claim.
- **The factor-analysis story inverted.** The intended frame was "PCA absorbs noise into the
  component, factor analysis does not". Measured, FA estimated the noisy variable's loading at 0.36
  against a true 0.55, while PCA landed on 0.58 — *closer*. Writing the intended frame would have
  been false. The lab now scores both on the thing they are both modelling, the off-diagonal
  correlations: FA reproduces them with residual 0.0000 (exactly identified at p = 3, one factor)
  against PCA's 0.85. And the last frame states the caveat outright — an exactly-identified solution
  divides small correlations by each other and is a high-variance estimator, so being the right
  *model* did not make it the better estimate of every parameter on this sample.
- **`unevenClusters` escaped the unit square.** `SimulationFrameTest` caught it: the loose cluster's
  Gaussian tail reached past 1.0, and unlike the embeddings this dataset is drawn directly. Fitted
  at definition, which preserves aspect ratio so every ratio the labs report is unchanged.

Two more measurements the copy is built on rather than around. t-SNE's distortion is shown as
numbers: the two tight clusters go from mean spreads of 0.040 and 0.032 to 0.200 and 0.144 while the
loose one *shrinks* from 0.117 to 0.101, and the far/near centroid-gap ratio collapses from 6.0× to
1.2×. UMAP beats it on the same points — 80% of 5-NN preserved against 67%, separation ratio 6.55
against 2.10 — and the closing frame says plainly that it does not preserve relative cluster size
either. LLE recovers the spiral's parameter at ρ = 0.99 (k = 4) against PCA's 0.47, and collapses to
ρ = 0.01 at k = 6, where 18 neighbour links short-circuit across turns instead of 2.

New guard `DimReductionMathTest` (11 tests) pins all of it — the Eckart–Young identity, the ICA
recovery to within 5°, the FA residual, the t-SNE size inversion, UMAP beating t-SNE on both
metrics, the LLE k = 6 failure — so a future re-tune that quietly makes a topic pointless fails
rather than ships.

**B7 · Association Rules + Time Series** — 9 topics — **Done**
`apriori` · `eclat` · `fp_growth` · `moving_average` · `exponential_smoothing` · `autoregression` ·
`arima` · `sarima` · `prophet`

Two new categories, `ml_association` (icon `link`) and `ml_timeseries` (icon `history`) — the last
two glyphs still unused in this section, so no ML category shares one. `apriori` and
`moving_average` are free, the rest premium.

**No new widget, sixth batch running.** The plan proposed `RegressionExplorer` for the time-series
half; that is the mock's hand-tuned lab with no `topicId` parameter, so it cannot take per-topic
configs. `RegressionLab` — B1's config-driven sibling — took all six instead, and the only change it
needed was one additive line in `LabCanvas`: the x-range now includes curve points, not just data
points. Every regression topic samples its curve across the data's own range so none of them noticed,
but a forecast is by definition drawn past the last observation, and without it the entire forecast
fell off the right edge of the canvas. Association went to `ArrayWalkPlayer` (Apriori's candidate
rows, Eclat's tid-lists) and `TreeVisualizer` (the FP-tree, using the `TreeLink` field Aho-Corasick
added for failure links to draw the header chain).

Two new math files. `AssociationMath.kt` holds one basket database and all three algorithms, so the
labs can show that they return the *same* frequent itemsets by different searches. The database is
built to exercise each claim rather than illustrate it: one item below minimum support, one 3-itemset
(BCE) eliminated by downward closure without a counting pass, and one frequent rule with high
confidence and lift below 1. `TimeSeriesMath.kt` holds the six estimators on one shared series —
least squares on the lagged design for AR, **Hannan–Rissanen** two-stage for ARIMA's MA terms (a real
estimator, not a stand-in), the three Holt-Winters recursions, and Prophet's actual model form:
piecewise-linear trend on changepoint basis functions plus a Fourier seasonality, fitted as one
least-squares problem with the penalty applied to the changepoint slopes alone — which is Prophet's
sparse prior on δ.

**Every lab scores against a held-out year and against seasonal-naive**, and that discipline caught
the batch's one wrong claim. The `autoregression` copy was drafted saying AR loses to the benchmark
at every order the slider offers. Measured, that is true only up to p = 8 (RMSE 8.7–11.0 against the
benchmark's 5.90); at p = 9 it reaches the annual lag and wins at 3.80. The paragraph was rewritten
to the more interesting true version — AR *can* represent seasonality, at a price of nine to twelve
parameters estimated from thirty-six usable rows, which is what one seasonal difference buys in a
single subtraction. Measured ordering across the six: Prophet 3.77 < Holt-Winters 4.72 < SARIMA 5.54
< seasonal-naive 5.90 < ARIMA ≈ AR(1–8) ≈ 8.7–10.9.

New guard `B7MathTest` (13 tests). The association assertions are exact, because the database is
hand-checkable; the time-series ones are orderings — which method beats which, which diagnostic moves
which way — because those orderings *are* the copy. A re-tune that flipped one would leave every frame
internally consistent (all numbers are computed) and teaching the opposite of what it says.

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

**C1 · NN Basics** — 4 topics — **Done**
`biological_neuron` · `mlp` · `vanishing_gradient` · `exploding_gradient`

First Track C batch, and the first new DL category: `dl_basics` ("Neural Network Basics", icon
`robot`), holding the doc's block in full — the four new topics plus `perceptron`,
`neural_network_basics` and `backpropagation` moved over from `dl_fundamentals`, which keeps its
other five until C2, C8 and C9 claim them. `neural_network_basics` was renamed to the doc's title,
**"Feedforward Networks"**, since "Neural Network Basics" is now the category. `biological_neuron`
and `mlp` are free; the two gradient topics premium.

**No new widget** — all four on `NeuralNetPlayer`, whose `NetFrame` already carried everything
needed (layer diagram, curve plot with explicit axes, matrix grid, bars). New math file
`DeepNetMath.kt`: a leaky integrate-and-fire neuron, a real 2-3-1 network trained on XOR by
backpropagation, and a 12-layer dense stack whose per-layer gradient norms come from an actual
backward pass rather than a decay formula.

**The batch's most useful output was a guard, not a topic.** `NeuralNetPlayer` had no entry in
`SimulationFrameTest` at all — the phase's most-used widget from here on (C1–C9 all lean on it) was
the only major one unguarded. The new check builds every config and asserts that a plot's curves stay
inside the axis range the frame declared and that a bar's captions match its values. It failed
immediately on **four live bugs in already-shipped labs**:
- **`rnn` and `lstm_gru` were rendering the wrong data.** Both passed a `mutableListOf` straight into
  `NetBar`, so every frame held the same growing list by reference — each early frame drew the
  *final* four (or six) bars while its captions had been snapshotted at build time. Fixed with
  `.toList()`.
- **`gradient_descent_variants` had a hand-picked plot box that did not contain its paths.** Thirteen
  points fell outside it, and `PlotCanvas` clamps rather than skips, so momentum's overshoot was
  drawn flat along the boundary and read as a deliberate slide. The box is now derived from the paths.
- **`dropout` and `rnn` each had an axis ceiling one point too low** — 1.11 against a declared 1.1,
  and 1.3⁸ = 8.16 against a declared 8, the latter in a frame whose entire subject is that the curve
  does not stay flat.

**Verification changed two of the four labs before any copy was written.** The first XOR run used
2 hidden units and did not converge — final loss 0.347, having learned x₁ alone. A seed and width
sweep showed why and became a frame: 2 units solve 2 of 5 seeds, 3 solve 4, 4 solve 5. The failures
are local minima, so width bought *trainability*, not expressiveness — a better point than the one
originally planned. And the first deep-stack run used width 8, where n is too small for the
variance argument to hold; widened to 16 and checked across five seeds, sigmoid vanishes at every
init scale tried (ratios 5.7×10¹ to 3.1×10⁷) while ReLU with He init stays within a factor of ~2.

Measured numbers the frames quote: rheobase exactly 15 with 0 Hz below it and 30/54/94/154/228 Hz
above; linear-hidden XOR stuck at 0.6931 = ln 2 with every output exactly 0.500; sigmoid's first
layer ~570× weaker than its last over 12 layers; ReLU at init scale 1.5 taking mean activation from
1.4 to 5.7 × 10⁶ (≈3.6× per layer) and a gradient norm of 3.7 × 10¹⁵, clipped to exactly 5.0 by a
uniform factor of 1.3 × 10⁻¹⁵.

New guard `DeepNetMathTest` (12 tests), including that clipping preserves each layer's *share* of
the gradient — the property that distinguishes global-norm clipping from per-parameter clipping and
the one the frame's claim rests on.

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

**Shipping and green (107 tests):**
- `ContentCoverageTest` — every browsable topic has content, nothing resolves to `NotYetAvailable`,
  and every prerequisite and cross-link id resolves. It has caught real breakage every batch and is
  run (with `--rerun-tasks`) at the end of each one.
- `CategoryIntegrityTest` (phase 10) — generic over all eight sections: every `categoryId` resolves
  within its own section's `all` list, no category is empty, ids are globally unique. This is what
  made deleting `ml_unsupervised` in B6 a safe edit rather than a careful one.
- `SimulationFrameTest` (phase 10) — runs every frame builder on the JVM and asserts point-cloud
  frames stay inside the unit square. Caught B6's `unevenClusters` overflow.
- `AiTaxonomyCoverageTest` (B6) — the doc as code. 357 entries nested by section and heading, each
  mapped to the topic ids serving it or to an explicit gap; pins the entry count, id resolution,
  section membership, and the covered count. Its failure message prints the remaining backlog.
- `DimReductionMathTest` (B6), `B7MathTest` (B7) and `DeepNetMathTest` (C1) — pin the properties each
  batch's copy leans on, so a re-tune that makes a topic pointless fails instead of shipping. Worth
  continuing per batch; `B7MathTest` caught a wrong claim that had already been written.

**Nothing specified here is now unbuilt.** The lesson from the five-batch slip is worth keeping:
the taxonomy map cost about an hour, would have cost the same at any point, and immediately
corrected the "~24 / ~26 / ~18 covered" estimates it replaced. Each remaining batch should update
`expectedCoveredCount` in the same change that lands its topics — a batch that does not move that
number closed no doc entry, which is worth noticing at the time rather than at the end.

## Risks / open items

- **Widget estimation was wrong, and by a lot.** The phase was planned around one net-new simulation
  widget. Three batches in, two config-driven sim types had shipped (`RegressionLab`,
  `DecisionSurface`) plus additive fields on `RlFrame` and `CloudFrame`. The pattern that emerged:
  existing widgets were each bound to one model shape, so a batch either fits an existing one exactly
  or needs a new config-driven sibling. B3 through B7 and C1 then needed nothing new — six
  consecutive batches on existing widgets — so the cost is front-loaded per *family* rather than per batch. B7
  also showed the second-order version of the same mistake: the plan named `RegressionExplorer` for
  its time-series half, which cannot take a config at all, while `RegressionLab` took all six topics
  after a one-line canvas change. Check the remaining batches against what now exists, and check
  *which* of two similar widgets is the config-driven one.
- **`FeatureMapPlayer` is still net-new** and blocks C3 and C4. Build it while C1/C2 land.
- **Verification keeps finding real defects, not wording problems.** Across eight batches: an
  inverted Complement NB sign that produced confidently wrong classifications, MCMC step sizes whose
  "too large" case sat in the well-tuned regime, an XGBoost split gain that was negative where the
  narration took it, LightGBM leaf counts that contradicted the drawing, and a `twoMoons` geometry
  that made spectral clustering score 0.67 against k-means' 0.65 — erasing the reason that topic
  exists. B6 added two more: a ring dataset whose even angular spacing made kernel PCA no better
  than linear PCA, and a factor-analysis frame whose intended claim was measurably backwards. Every
  one of these reads plausibly and is wrong. **Numeric claims in a lab must be measured before the
  copy around them is written**, and notes that could go either way should compute the comparison
  rather than assert it. B7 added one more — an `autoregression` paragraph asserting the model loses
  to the seasonal benchmark at every order, when it wins from p = 9 — and it was the batch's own guard
  test that caught it, not the probe. Both steps earn their place: the probe before the copy, the
  guard after it. C1 added a third kind: a *widget* guard, which found four defects in labs that had
  shipped batches earlier and were rendering the wrong data the whole time. Every widget without one
  is carrying unknown bugs of that shape.
- **Topic-id collisions.** `TopicRegistry` collapses cross-listed ids first-wins (`perceptron`,
  `transformers`). Tracks C and D cross-list far more (backprop, LSTM, attention, RLHF, all seven
  Deep RL entries). `AiTaxonomyCoverageTest` now makes these reviewable — a cross-listed entry
  appears under both doc sections with the same id, visibly — but it does not decide which section a
  duplicate id should live in. Tracks C and D still have to make that call per topic.
- **Browse-list length.** 426 topics changes the character of the category screens, and ML alone is
  heading for 12 categories. Per-category search exists (Phase 2); scroll performance and whether
  Home's Quick Access grid is still a sensible sole entry point are both unchecked.
- **Batch B9 (17 metrics) and D6 (16)** remain the thin-topic batches, and the most likely to read as
  filler if authored mechanically. Each metric needs its own "when this one misleads you" angle.

## Suggested order

~~A1 → A2~~ → ~~B1 → B2 → B3 → B4 → B5~~ → ~~guards~~ → ~~B6 → B7~~ → **B8** → B9 → B10 → ~~C1~~ →
**C2** → `FeatureMapPlayer` → C3 → C4 → C5 → C6 → C7 → C8 → C9 → D1 → D2 → D3 → D4 → D5 → D6.

Struck-through batches are done. The guards step landed with B6 rather than before it, in the same
session. The order was then broken deliberately: C1 was built before B8–B10 at the user's direction,
which is fine — the tracks are independent, and Track C's only real dependency is `FeatureMapPlayer`
before C3. B8–B10 stay open, and Track B is not finished until `ml_supervised` is empty.

## Verification

Per batch: `./gradlew testDebugUnitTest` (coverage + taxonomy + category guards) then
`./gradlew assembleDebug` and a manual walk of the touched category screen — list renders, every row
opens, sim plays, premium rows gate.
