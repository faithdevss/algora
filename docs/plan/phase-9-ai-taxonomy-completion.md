# Phase 9 — AI Taxonomy Completion

Status: In progress — Track A complete (RL at 100% of the doc), B1–B8, C1–C6 and D1–D5 done. 164 of
226 topics authored. AI sections now: **ML 84**, DL 63, NLP 57, RL 74 — 278 AI topics, and 400
browsable topics across the app *counted from the section lists themselves*. (The running "taxonomy at N"
tallies in earlier revisions of this file drifted from those lists; the measured number is the one to
trust, and `ContentCoverageTest` is what enforces it.)
**Doc coverage is measured, not estimated: 293 of the doc's 357 entries (82%).**
Next: B9 — the 17 evaluation metrics, the phase's largest thin-topic batch, which also takes
`model_evaluation` off `ml_supervised` as its landing topic.
Track B was interrupted after B7 by a deliberate jump to Track C; B8 has now landed, **B9 and B10
remain unbuilt**, and `ml_supervised` therefore still holds the four topics they were to redistribute.

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

## Where the phase stands (last updated after B8)

**164 of 226 topics authored; 62 remain, in three open blocks.**

| Open block | Topics | What it needs |
|---|---|---|
| B9–B10 | 20 | Evaluation metrics, then neural-network foundations and the RL view. Track B is not finished until `ml_supervised` is empty — it still holds `perceptron`, `bias_variance`, `regularization`, `model_evaluation` |
| C7–C9 | 26 | Generative, optimizers/regularization, specialized. `dl_fundamentals` is still standing and should end empty; `dl_architectures` is down to three topics, all of which C7 takes |
| D6 | 16 | Fine-tuning + beyond-transformers + metrics. The phase's other oversized thin-topic batch, and the last of Track D |

Track A is closed (RL is 71/71 against the doc). Track D is 5 of 6. Every landed batch has updated
`expectedCoveredCount` in the same change, so the coverage number below is a test result rather than
a count: **293 of 357**.

**`nlp_modeling` is gone.** C5 took its last topic, which makes NLP the second section (after ML's
`ml_unsupervised` in B6) to finish hollowing out one of the mock's two generic buckets.

Nothing is blocked. The one net-new widget the phase planned (`FeatureMapPlayer`) was built in C3,
and every batch since has run on widgets that already existed — C4, D1–D5, C5, C6 and B8, nine in a
row, each adding only math files. B8 is the first of those to reuse *three* widgets at once. Before C3 the run was seven (B3–B7, C1, C2). The widget cost really is
front-loaded per *family* rather than per batch, as the risks section predicted after B7.

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
| B8 · Data Preprocessing | 9 | **Done** | New `ml_preprocessing`; `one_hot_encoding` cross-listed into `nlp_embeddings`. No new widget — three existing ones. `PreprocessMath.kt`; three of the nine rules came out conditional |
| B9–B10 | 20 | **Still open** | `ml_supervised` awaits them |
| C1 · NN Basics | 4 | **Done** | No new widget. New `NeuralNetPlayer` frame guard found 4 live bugs |
| C2 · Activation Functions | 10 | **Done** | No new widget. `autoPlot` retires hand-picked axes |
| C3 · CNN Mechanics + Architectures | 12 | **Done** | New `FeatureMapPlayer` + `CnnMath.kt`. Plan said 11; the doc lists 12 |
| C4 · Object Detection & Segmentation | 9 | **Done** | `FeatureMapPlayer` gained a box/mask scene; new `DetectionMath.kt` |
| C5 · RNN Mechanics | 4 | **Done** | New `dl_rnn` + `nlp_rnn`, which retires `nlp_modeling`. No new widget; new `RnnMath.kt` trains every model on screen. `RnnMathTest` overturned the batch's planned headline |
| C6 · Transformers & Pre-trained Models | 8 | **Done** | New `dl_transformers`; cross-listed into `nlp_transformer` and `nlp_pretrained`. No new widget; new `AttentionMath.kt` + `PretrainMath.kt`. The cross-attention model closes C5's open claim, and the multi-head rank story in the plan was wrong |
| C7–C9 | 26 | Planned | |
| D1 · Preprocessing + Statistical NLP | 8 | **Done** | New `nlp_statistical`; no new widget. TokenStrip frame guard + `D1MathTest`, which caught two live errors |
| D2 · Syntactic & Semantic Analysis | 6 | **Done** | New `nlp_syntax`; no new widget. Taggers and parsers run and scored in `SyntaxMath.kt`; `D2MathTest` |
| D3 · Word Embeddings | 5 | **Done** | New `nlp_embeddings`; no new widget. Models trained for real in `EmbeddingMath.kt`; `D3MathTest` |
| D4 · Transformer Internals + Pre-trained LMs | 8 | **Done** | New `nlp_transformer` + `nlp_pretrained`; no new widget. `TransformerMath.kt`; `D4MathTest` |
| D5 · Modern LLM Techniques | 7 | **Done** | New `nlp_modern_llm`; no new widget. `ModernLlmMath.kt` runs a real Game-of-24 search, a real index and a real retriever; `D5MathTest` overturned four planned claims |
| D6 | 16 | Planned | |
| **Total** | **226** | **164 done** | 400 browsable topics, counted from the section lists |

Sections against the doc, measured by `AiTaxonomyCoverageTest` rather than counted by hand:
**RL 71/71**, **ML 93/113**, DL 69/96, NLP 60/77 — **293 of 357 overall**. ML is 84 topics of its
eventual ~100; DL is at 63; NLP is at 57. Browsable topics across the app: **400**, which
is what `DataStructuresTopics + AlgorithmsTopics + the four AI sections` actually sum to — the
"taxonomy 200 → N of 426" running tally this table used to carry had drifted from the lists by two
dozen, so it has been dropped in favour of the measured figure. (The per-section figures in earlier
revisions of this paragraph had drifted too — NLP was recorded as 43/77 while the map already said
50. These come from the map now, counted by script, and the total is what the test asserts.)

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
`ml_bayesian`, `ml_ensemble`, `ml_clustering`, `ml_dimreduction`, `ml_association`, `ml_timeseries`,
`ml_preprocessing`. Still to come: `ml_nn_foundations`, `ml_metrics`, `ml_rl_fundamentals`.

`ml_supervised` and `ml_unsupervised` were hollowed out a batch at a time rather than deleted up
front, so that no category was ever empty mid-phase. **`ml_unsupervised` is now gone** — B5 took its
clustering topics and B6 took `pca`, its last one. `ml_supervised` still holds `perceptron`,
`bias_variance`, `regularization` and `model_evaluation`; B9 takes the evaluation and practice
topics and B10 takes `perceptron`. It should be gone by the end of Track B, and if it still exists
then, something was missed. `CategoryIntegrityTest` is what makes that safe to do incrementally.

**Deep Learning — 11 categories.** Built so far: `dl_basics`, `dl_activations`, `dl_cnn`,
`dl_detection`, `dl_rnn`, `dl_transformers`. The two
generic buckets are being hollowed out the same way ML's were — `dl_architectures` lost `cnn` to
`dl_cnn`, then `rnn` and `lstm_gru` to `dl_rnn`, then `transformers` to `dl_transformers`, and is
down to `autoencoders`, `gans` and `diffusion_models`, which C7 takes. Full list (`dl_basics`, `dl_activations`,
`dl_cnn`, `dl_detection`,
`dl_rnn`, `dl_transformers`, `dl_generative`, `dl_deep_rl`, `dl_optimizers`, `dl_regularization`,
`dl_specialized`)

**NLP — 11 categories.** Built so far: `nlp_preprocessing` (kept, renamed "Text Preprocessing"),
`nlp_statistical` (D1, which also took `bow_tfidf` off Preprocessing), `nlp_syntax` (D2, which took
`ner` off Modeling), `nlp_embeddings` (D3, which took the `word_embeddings` umbrella off Modeling and
kept it as the category's landing topic), and `nlp_transformer` + `nlp_pretrained` (D4, which took
`attention`, `transformers` and `llms` off Modeling — leaving it holding only `rnn_lstm` and `rag`).
`nlp_modern_llm` (D5, which took `rag` off Modeling), and `nlp_rnn` (C5, which took `rnn_lstm` — the
last topic in Modeling, so **`nlp_modeling` is retired**). Full list
(`nlp_preprocessing` (kept), `nlp_statistical`, `nlp_syntax`,
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

**B8 · Data Preprocessing** — 9 topics into new `ml_preprocessing` — **Done**
`missing_value_imputation` · `outlier_detection` · `label_encoding` · `one_hot_encoding` ·
`min_max_normalization` · `z_score_standardization` · `smote` · `chi_square_selection` · `rfe`

New category `ml_preprocessing` ("Data Preprocessing", icon `browser` — a data table is what every
topic in it transforms). Ordered as a pipeline runs rather than as the doc lists it: fix the rows
(missing values, outliers), then the columns (encoding, scaling), then the class balance, and only
then decide which features to keep. `one_hot_encoding` is cross-listed into `nlp_embeddings`, where
the doc lists it as the baseline the word-embedding block improves on. Four free, five premium; the
doc has no markers on this block, so the free four are the ones that are definitional. Ten doc
entries closed (9 ML + 1 NLP): 283 → 293.

*Sims:* three existing widgets and no new one — `PointCloudPlayer` for the three geometric topics
(both scalers and SMOTE), `ArrayWalkPlayer` for the five column transforms, and `TokenStripPlayer`
for `one_hot_encoding`, whose subject is a *matrix* and which the heat grid draws directly. The
plan's suggestion was right about the first two widgets. One display decision worth recording: the
raw-units frames divide both features by one shared constant rather than normalising each, because
the canvas maps [0,1] and normalising per axis would perform the very transform the topic is about.

New `PreprocessMath.kt` with `PreprocessMathTest` (20 tests). Preprocessing is where a plan is most
likely to have copied a rule of thumb, so every rule is scored against a model — k-NN for the
distance claims, least squares and a depth-limited tree for the encoding claims, honest against leaky
cross-validation for SMOTE. **Three of the nine rules came out conditional rather than true:**

- **"Scale your features" — priced.** Unscaled, income supplies **99.996%** of the k-NN squared
  distance and age 0.004%: the classifier has two features and uses one. Scaling moves the same
  model, untouched, from 0.738 to **0.900** (min-max) and 0.888 (z-score), with the distance split
  becoming 44/56 and 51/49. On clean data the two scalers are a point apart, so the choice between
  them is not about accuracy — fit either on a column containing one 5,000,000 value and min-max
  squeezes every real point into **0.026** of its range against z-score's 0.289.
- **"Never label-encode" — half right, and the other half is what every boosting library does.** On
  a non-monotone target a least-squares fit scores MSE **11.26** on the label code against one-hot's
  **0.280** — 40× — because it must pass one line through four unordered levels. A tree grown on the
  same column reaches **0.280 at depth 2**, one-hot's number to three decimals, because two splits
  separate four categories. The rule is a property of the (data, model) pair, not of the encoding.
- **Neither feature selector finds an interaction, for different reasons.** On data where the label
  is exactly xorA ⊕ xorB, chi-square ranks **noise first at 3.79** and the two features that *are*
  the signal at 2.62 and 1.98 — each is individually independent of the label, so no univariate score
  can rank them. RFE with a linear model fails too, dropping `useful` first, because a linear model
  cannot express XOR and gives both halves a coefficient near zero. Chi-square is blind because it
  looks one at a time; RFE is blind because its estimator is. On the ordinary data RFE does what
  chi-square cannot: it drops the 90%-identical copy that chi-square ranked *second*, because with
  the original present its coefficient collapses to −0.015.

Two more measured results, both of which replace a hedge with a number:

- **Imputation is not a fill, it is an edit.** At 30% missing, mean imputation takes the column's
  variance from 8.09 to 5.89 — ratio **0.728** against the **0.70** the missing rate alone predicts —
  and its correlation with another column from 0.982 to **0.829**. The damage crosses columns and it
  is silent. Dropping the rows keeps both statistics intact and costs 60 of 200 rows, which is the
  actual decision. On a skewed column the mean is 43.3 and the median 5.3.
- **SMOTE, and the leak it invites.** Minority recall 0.667 → **1.000** on an untouched real test
  split, with precision 0.400 → **0.333** and accuracy 0.879 → 0.818: recall was bought, not found.
  Resampling *before* the split then reports **0.954** against the honest pipeline's **0.908** over
  five folds at 1-NN — 4.6 points of score that will not reproduce, because a synthetic point built
  from a validation-fold neighbour sits next to it.

**The outlier topic's two failures are both solved for rather than asserted.** Masking is usually
described with one dramatic value; measured, a single extreme *cannot* mask itself — its z-score here
is 7.68. What masks is a group, and the lab adds extremes until the largest z falls under the
threshold: **7 of 67 values, 10.4% of the sample**, at which point the z-score rule flags **zero**
while IQR and MAD flag all seven. Separately, a single point in a sample of n can never exceed
(n−1)/√n standard deviations, so **below n = 11 a threshold of 3 can never fire** — the rule is not
strict on small samples, it is inert. Both failures are one property (the estimator is inside the
contamination), which is what the breakdown-point table then names: 0% for mean/σ, 25% for quartiles,
50% for median/MAD.

One bug the probe caught, of the recurring kind: the distance-share measurement compared *scaled*
reference points against *raw* queries, so it dutifully reported the raw 0/100 split for every
scaler. The frames read perfectly well and were narrating a transform that had not happened.

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

**C2 · Activation Functions** — 10 topics — **Done**
`sigmoid` · `tanh` · `relu` · `leaky_relu` · `prelu` · `elu` · `selu` · `swish` · `gelu` · `softmax`

New category `dl_activations` (icon `trend`), led by the existing `activation_functions` umbrella
moved over from `dl_fundamentals`, which keeps its remaining four for C8 and C9. `sigmoid` and
`relu` are free; the rest premium. Ordered as the history went: the two saturating functions, the
rectifier family and the repairs to its one failure, the two smooth self-gated functions that won
the transformer era, and softmax last because it is an output layer rather than a hidden
non-linearity.

**No new widget.** The plan proposed `RegressionExplorer` here, which is the same mistake B7 caught
— it has no `topicId` parameter and cannot take configs. All ten went to `NeuralNetPlayer`, whose
`CurvePlot` with explicit axes is exactly the right shape for a function and its derivative. New
math file `ActivationMath.kt` defines each activation once with its derivative and measures every
claim by running signal through a real stack.

**A guard change worth keeping.** C1's frame guard failed immediately on seven of the ten new labs —
identity-like curves over x ∈ [−6, 6] reach 6, and hand-picked y ranges of `-1f..4f` clipped them.
Rather than widen each by hand, `autoPlot` now derives the y range from the curves. Hand-picked axes
caused three of C1's four found bugs; this removes the category rather than the seven instances.

**Measurements the copy is built on**, all computed at build time:
- σ′ ≤ 0.25 exactly, against tanh's 1.0. Mean output 0.497 against tanh's −0.009.
- Saturation is a property of *scale*, not of the function: sigmoid's flat-tail fraction goes
  0.000 → 0.777 as the pre-activation standard deviation goes 1 → 16, while **ReLU's stays at 0.50
  throughout** — a fixed feature of the shape rather than a failure that worsens with training.
- **Dying ReLU, run rather than described.** One layer, plain SGD, 120 steps: 0% dead at lr 1,
  12.5% at 30, 78.1% at 60, 100% at 100 — and every death happens inside the first ten steps, with
  the fraction flat for the remaining hundred and ten. Leaky ReLU loses nothing at any rate.
- PReLU's α trained from 0 to 0.2500 against a true 0.25.
- ELU's mean output 0.151 against ReLU's 0.394 — while **Leaky ReLU's is 0.390**, essentially
  unchanged. The two are often said to solve the same problem and measurably do not.
- **SELU self-normalises**: over 20 layers at LeCun init, layer 20 measures mean −0.014, std 0.971,
  holding across three seeds — against ReLU's 0.090 and tanh's 0.148. Under He init instead it
  breaks completely: mean 2.00, std 4.81. The initialisation is part of the method.
- Swish min −0.2785 at −1.2785, GELU min −0.1700 at −0.752, derivatives peaking at 1.0998 and
  1.1289 — both above 1, unlike every earlier activation in the category.
- GELU's tanh approximation within 0.00047 of exact everywhere; softmax naive on [1000, 1001, 1002]
  returns NaN while the shifted form returns [0.090, 0.245, 0.665]; temperature entropy 0.095 → 1.368
  nats; every Jacobian row sums to zero.

New guard `ActivationMathTest` (16 tests). Most are exact, because an activation is a formula; the
depth-propagation and dead-unit ones are orderings checked across seeds, since a result holding only
for the configuration on screen would not be a property of the activation.

> **Note on new widgets.** The phase was planned assuming `FeatureMapPlayer` would be the only
> net-new simulation widget. B1 and B2 each needed one as well (`RegressionLab`, `DecisionSurface`),
> because the existing labs were bound to a single model shape. Both are config-driven and expected to
> serve later batches — `DecisionSurface` in particular should cover B3 and B4 — so the remaining
> batches should be checked against them before assuming more widget work. That check has now paid
> off for seven consecutive batches (B3–B7, C1–C2), and twice the *plan's own* widget suggestion was
> the wrong one: it named `RegressionExplorer` for both B7 and C2, and that widget takes no
> `topicId` at all. Read the widget before trusting the plan's line about it.
>
> C3 is the one batch where the check came out the other way. `NeuralNetPlayer` already draws a small
> matrix with a highlighted window — its `cnn` lab does exactly the sliding-kernel picture — but it
> has no layer table, no live controls and no way to price an architecture, which is most of what the
> twelve CNN topics are about. `FeatureMapPlayer` was built, as the original plan said it would be,
> and it is the phase's only net-new widget that the plan actually predicted.

**C3 · CNN Mechanics + Architectures** — 12 topics into new `dl_cnn` — **Done**
`conv_layers` · `pooling_layers` · `padding_strides` · `lenet5` · `alexnet` · `vgg` · `inception` ·
`resnet` · `densenet` · `mobilenet` · `efficientnet` · `vit`

The plan said 11; `docs/topics.ai.md` lists **12** entries under Convolutional Neural Networks, and
all twelve were authored, so DL doc coverage moved 36 → 48. New category `dl_cnn` ("Convolutional
Networks (CNN)", icon `stack` — `chip` already belongs to Architectures), and the `cnn` umbrella
moved into it from `dl_architectures` as the landing topic, which leaves `dl_architectures` holding
the six topics C5–C7 will redistribute. Only `conv_layers` and `pooling_layers` are free; the rest
are premium. Ordered as mechanics first, then the architectures in publication order, because each
one is an answer to a limit of the one before it.

*Sims:* **`FeatureMapPlayer`** (`FeatureMapSection.kt`) — the phase's one genuinely net-new widget,
built here rather than before C1 as the plan sequenced it, since C1/C2 turned out not to need it.
Four render parts mixed per frame: grids (input / kernel / feature map with the current window
outlined), a layer table with per-layer parameter share, bars, and a plot with optional log-y. The
three mechanics labs carry live **kernel / stride / padding** pickers and rebuild every frame from
them, so the geometry on screen is the geometry of the controls. It serves C4 unchanged.

New math file `CnnMath.kt` with `CnnMathTest` (20 tests). The architectures are layer *tables*, not
prose: LeNet-5's **61,706**, AlexNet's **62,378,344** and VGG-16's **138,357,544** are summed from
the definitions, and the split inside them falls out of the same table — AlexNet keeps 94.0% of its
parameters in three dense layers while doing ~95% of its arithmetic in the five convolutions, and
VGG-16 pushes that to 89.4% / 99.2%. Also measured rather than quoted: the inception 3a module at its
real widths (393,216 → 163,328 MACs per position, 9.7× on the 5×5 branch alone), depthwise separable
cost as exactly 1/N + 1/k² (0.1150 at 256 channels, k=3), a six-layer dense block's channel growth
and flat per-layer cost, EfficientNet's α·β²·γ² = 1.9203, and ViT's 197 tokens / 590,592 patch-embed
parameters / 38,809 attention pairs.

Two claims are measurements over a real run rather than arithmetic, and both are checked as
orderings across seeds and depths so they cannot be artefacts of the configuration on screen:
- **ResNet.** The same 30-layer stack, identical weights, differing only by the `+ x`: a unit
  gradient at the output arrives at layer 1 as **1.3×10⁻⁸** plain and **6.7×10³** residual. Note the
  direction — the residual gradient *grows* toward the input, which the lab says out loud and
  attributes to BN and zero-init γ rather than pretending the shortcut is free.
- **Pooling.** The invariance claim, priced: a 1-pixel shift moves the raw response by 100% of its
  own magnitude and the 2×2 max-pooled map by 50%; a 2-pixel shift moves the pooled map by 100%.
  Pooling halves the sensitivity, it does not remove it. The first draft measured this on the
  convolution lab's edge patch and got a meaningless answer — a response that repeats down every
  column is shift-invariant for reasons unrelated to pooling — so the lab uses a localised square
  instead. Worth recording as the same class of error as A1's POMDP sensor.

**C4 · Object Detection & Segmentation** — 9 topics into new `dl_detection` — **Done**
`rcnn` · `fast_rcnn` · `faster_rcnn` · `yolo` · `ssd` · `retinanet` · `unet` · `mask_rcnn` ·
`segmentation_types`

New category `dl_detection` ("Object Detection & Vision Tasks", icon `target`). Ordered as the
history went, because each architecture is an answer to the previous one's bottleneck: the two-stage
line from R-CNN to Faster R-CNN, then the one-stage detectors that removed the proposal step, then
the two segmentation architectures and the task distinction they turn on. Only `segmentation_types`
is free — it is the one topic in the batch that is a definition rather than an architecture.

*Sims:* `FeatureMapPlayer`, as planned, but it needed a fifth render part. Detection is the one
vision task whose *output* is geometry, and a lab that cannot draw a box cannot show what any of
these produce. `FmScene` adds a square image canvas with boxes (solid for predictions and ground
truth, faint for proposals and anchors), an optional S×S cell grid for YOLO, and an optional
per-pixel label mask for the segmentation topics. The frame guard grew with it: no box may fall
outside its own scene, and no mask may be ragged.

New math file `DetectionMath.kt` with `DetectionMathTest` (14 tests). The evaluation machinery is
run rather than described — IoU, greedy NMS, and the real AP matching procedure, in which a
detection is matched to the highest-IoU ground-truth box *that is still unclaimed*:

- **NMS is part of the metric.** On the lab's six boxes AP@0.5 is **0.833** with the duplicate left
  in and **1.000** after suppression, from identical features. The same detections score **0.848**
  under VOC2007's 11-point rule, which is the argument for always checking a paper's protocol.
- **Focal loss, priced on a stated population.** 100,000 background anchors at p = 0.9 against 10
  foreground at p = 0.1: cross-entropy gives 10,536 vs 23 — **99.8% of the gradient from
  already-correct examples** — and focal loss at γ = 2 gives 105 vs 19. The background:foreground
  ratio falls **458:1 → 5.6:1, an 81× rebalance**, with nothing discarded.
- **RoI pooling's two roundings, in image pixels.** A 145-pixel box at stride 16 is 9.0625 feature
  cells, snapped to 9 (1 px), then divided into 7 bins of 1.2857 snapped to 1 — so seven bins span
  seven cells rather than nine and **32 px of the box's far edge are never read**. A box that
  divides evenly costs zero, which is why the bug was tolerable for two years and fatal the moment
  Mask R-CNN asked for a pixel-accurate mask.
- **Anchor counts summed from level tables:** SSD300's **8,732** (5,776 of them from the finest map
  alone), the RPN's **21,600**, RetinaNet's "about 100k" measured at **120,087**. YOLO v1's whole
  output is **7×7×30 = 1,470** numbers and **98** boxes.
- **U-Net's geometry** walked stage by stage: 572 → bottleneck 28 → output **388**, with skips
  cropped **4, 16, 40, 88** px per side.
- **Semantic vs instance, counted.** Two touching sheep are **1 semantic region of 54 pixels** and
  **2 instances**; the lab's damaged prediction scores **mIoU 0.917** while a merged pair would
  score 1.000 under mIoU and lose an object outright under mask AP.

One correction found while writing the guard: the first `binShiftPixels` formula scaled the bin
rounding by the *feature* count rather than the bin count and reported 41 px where the honest figure
— seven bins of one cell against a nine-cell RoI — is 32. It read plausibly, which is the recurring
failure mode this phase keeps recording.

**C5 · RNN Mechanics** — 4 topics into new `dl_rnn`, cross-listed into new `nlp_rnn` — **Done**
`bptt` · `bidirectional_rnn` · `encoder_decoder` · `seq2seq`

Two categories, both "Recurrent Networks", because the doc lists this block under Deep Learning
*and* under NLP. `dl_rnn` ("Recurrent Networks (RNN)", icon `history` — the only icon in the set
that reads as time) takes `rnn` and `lstm_gru` off Architectures and adds all four new topics;
`nlp_rnn` ("Recurrent Models", same icon and colour, since it is the same subject read from the
other section) takes `rnn_lstm` and cross-lists three of the four, the way `perceptron` is
cross-listed between ML and DL. That empties Modeling, so **`nlp_modeling` is retired**. All four
premium, honouring the doc's markers where it has them. Seven doc entries closed: 262 → 269.

*Sims:* `NeuralNetPlayer` for `bptt`, `TokenStripPlayer` for the other three — as planned, and the
first time this phase's plan named the right widgets for a whole batch. No new widget; the seventh
consecutive batch that needed only a math file.

New `RnnMath.kt` with `RnnMathTest` (23 tests). There is one hand-written RNN cell — tanh state,
softmax output, gradients derived by hand, Adam — and the four labs are that cell arranged three
ways: a classifier whose gradient can be cut off at a chosen window, a tagger that reads one
direction or both, and an encoder-decoder decoded greedily or by beam. Every model on screen is
trained when the topic opens (310–580 ms each, against the 216 ms `word2vec_cbow` already costs).

**The batch's planned headline was wrong, and the replacement is better.** The plan assumed
truncated BPTT fails on a dependency longer than its window, "and no amount of training can invent
one". Measured on a 10-step task whose cue is at step 1:
- **A window that never reaches the cue solves the task on every seed.** k = 9 covers steps 2–10 and
  scores 1.00 on all three initializations; k = 7 scores 1.00, 0.60, 0.54. The window bounds credit
  assignment, not memory — W is *shared*, so the steps inside the window train the same matrix that
  carries the cue forward outside it.
- **Below that, whether it works is a property of the initialization.** k = 3 solves it on 1 seed of
  3, k = 5 on 0 of 3, k = 7 on 1 of 3 — and k = 5's mean is *below* k = 3's, so the sweep is not even
  monotone. "Truncation degrades gracefully with k" is not what this measures.
- **Gradient similarity predicts none of it.** The truncated gradient is cosine 0.943 with the full
  one at k = 1 and 0.990 at k = 3; at k = 3 it also carries **100.2%** of the full gradient's
  magnitude, because the dropped terms were partly cancelling the ones that remain. The lab says
  that out loud — otherwise "recovers 99% of the gradient" reads as if the truncated vector were a
  piece of the full one.
- Also measured rather than asserted: ‖∂L/∂h‖ falls 0.480 → 0.0085 over nine steps (56×), and step
  1 contributes **exactly zero** to ∂L/∂W because h₀ is the zero vector — the cue reaches the input
  matrix and the recurrent matrix never sees it.

**The bidirectional lab computes its own ceiling before training anything.** The corpus is twelve
sentences of garden-path minimal pairs ("the horse raced past the barn" / "…barn fell"), and 12 of
its 59 positions are ambiguous given only the words to the left, 0 given the whole sentence. That
enumeration fixes a ceiling of 53/59 = **0.8983** for any left-to-right tagger; the trained forward
tagger scores 0.8983 to four decimals, with an exactly 0.500/0.500 split at every disputed position,
and the bidirectional one scores 1.000. The cost is stated in the same frame: 903 → 1,799
parameters, two passes, and no output until the sequence ends — which is the reason BERT is
bidirectional and a decoder cannot be.

**The encoder-decoder lab reproduces Sutskever's reversal trick and explains it with a probe.** On a
copy task — the weakest possible demand, so every failure is the vector losing the source — exact
match runs 1.00 / 0.48 / 0.00 at lengths 1 / 2 / 4. Freezing the encoder and fitting a linear
read-out from the context vector to each source position shows *what* it kept: position 6 is
recoverable 85% of the time and position 1 only 34%. Feeding the same encoder the same sources
backwards flips that to 98% at position 1 and takes exact match **0.292 → 0.542** with no extra
parameters. The closing frame refuses the obvious over-claim: reversal moves the bottleneck, it does
not remove it — length 6 is near zero either way.

**The seq2seq lab's argument is the error split.** Widening the beam 1 → 10 raises mean
log-probability −2.284 → −1.994 and changes 45 of 120 outputs, and moves exact match by **one
sequence** (0.292 → 0.300). Scoring the gold sequence under the same model says why: at width 1 the
failures are 1 search error against **84 model errors**, and at width 10 the search errors are gone.
Nine tenths of the failures are outside what any beam width can reach. The same diagnostic prices
the alternative — feeding the encoder backwards converts 31 model errors, thirty times what beam
width bought. Two further knobs are reported as they measured: length normalization changes 10 of
120 outputs, lengthens them (3.23 → 3.32 symbols) and moves accuracy by 0.000, because the source
fixes the length here; and teacher forcing scores 0.495 next-symbol accuracy against 0.324
free-running, which is exposure bias in one number.

One process note worth keeping. The first draft of this batch used a *reversal* task, on which the
d-sweep (context width 2/4/8/16) came out non-monotone — bigger vectors scored worse at equal
budget, because optimization rather than capacity was binding. Rather than narrate a capacity story
the numbers did not support, the task became a copy and the capacity claim became the linear probe,
which measures the thing directly. The discarded sweep is the same class of error as C3's pooling
patch: a lab that would have read perfectly well and measured the wrong thing.

**C6 · Transformers & Pre-trained Models** — 8 topics into new `dl_transformers`, cross-listed into
`nlp_transformer` and `nlp_pretrained` — **Done**
`self_cross_attention` · `multi_head_attention` · `bert` · `gpt` · `t5` · `roberta` · `distilbert` ·
`hf_tokenizers`

New category `dl_transformers` ("Transformers & LLMs", icon `share` — the all-to-all graph attention
is; `chip` still belongs to Architectures). It takes `transformers` off Architectures and cross-lists
`attention` in from NLP as its two landing topics, so the category is the doc's "Transformers & LLMs"
block in full. The NLP side gets the same ids in the categories D4 built: the two attention topics
into `nlp_transformer`, the five model families into `nlp_pretrained`. `hf_tokenizers` is DL-only —
the doc lists it under Deep Learning and nowhere else. Gating follows the doc's markers exactly,
which means **`bert` is the batch's one free topic**. Fourteen doc entries closed (8 DL + 6 NLP):
269 → 283.

*Sims:* `TokenStripPlayer` for all eight — the first batch this phase where the plan named the right
widget for every topic. No new widget; two new math files, `AttentionMath.kt` and `PretrainMath.kt`,
with `AttentionMathTest` (11) and `PretrainMathTest` (23).

**The batch's centrepiece is the experiment C5 left owing.** The encoder-decoder topic ends on
"attention removes this bottleneck", which was a claim about work not yet done. `AttentionSeq2Seq` is
C5's model with cross-attention in place of the single handover — same task, same 120 pairs, same 100
epochs, gradients derived by hand. Results:
- **0.292 → 0.867 exact match**, and the per-length collapse is gone: 1.00 / 0.92 / 0.89 / 0.95 /
  0.93 / 0.47 against the fixed vector's 1.00 / 0.48 / 0 / 0 / 0 / 0.
- **The parameter objection is answered by a control, not by argument.** A fixed-vector model widened
  to 1,069 parameters — within 18 of the attention model's 1,087 — scores **0.300**, and still
  collapses at length 3. Capacity was never the missing thing.
- **The alignment is learned and it is the right one:** 0.942 of the attention mass lands on the
  position each decoder step should be copying, offset 0.087 positions, on held-out sources.
- The hand-derived attention gradients are **checked against finite differences** (worst relative
  error 1.7e-5). The first run of that check failed at 0.857, which was the loss averaged on one side
  and summed on the other — a scale bug. The distinction is now in the copy: a derivation error puts
  one parameter block wrong, a scale error puts every block wrong by the same factor.

**The plan's multi-head rationale was wrong, and the replacement is provable.** The plan (and most
textbooks) argue from rank: one head cannot represent as many patterns. At realistic widths that is
backwards — a single full-width head at d = 64 fits a rank-12 alignment target *exactly*. What one
head cannot do is read two positions at once: it emits one distribution, so two reads means splitting
the mass. Measured over 200 random value pairs, the best single head sits at α = 0.500 with **0.697**
relative error; two heads are exact. The rank ceiling does bite, at the other end — 13.4% error at 8
dimensions per head, 37.1% at 4 — so the two measurements jointly explain why published models hold
d/h ≈ 64 from BERT-base to GPT-3. And on the lab's own task, which has exactly one alignment to
learn, **4 heads score 0.842 against 1 head's 0.867**: reported as measured, with the
simultaneous-read result as the reason that is expected rather than a failure.

**The parameter tables are summed, and one of them contradicts its paper.** Same method as C3's CNN
tables, applied to five configs with the two conventions that move the totals stated (layer-norm
scale and bias count; a tied output embedding counts once):

| Model | Summed | Published |
|---|---|---|
| BERT-base | 109,482,240 | 110M |
| GPT-2 small | **124,439,808** | **117M (+6.4%)** |
| RoBERTa-base | 124,645,632 | 125M |
| DistilBERT | 66,362,880 | 66M |
| T5-base | 222,903,552 | 220M |

Getting GPT-2 to match the released checkpoint exactly took finding 1,536 parameters: the first
version gave it BERT's embedding layer norm, which it does not have. The gap to the *paper* is real
and stays in the copy. The tables then carry the rest of the batch's arguments rather than prose
doing it: two thirds of every layer is the feed-forward block; RoBERTa's 50,265-piece vocabulary is
+63.6% embedding parameters with the twelve layers byte-identical; T5's decoder is 113M against the
encoder's 85M because of the extra cross-attention block; and DistilBERT is **39.4% smaller at half
the layers** because the embedding table does not shrink — 36% of the student against 22% of the
teacher, which is the general rule about when to distil depth rather than width.

**Objectives are counted on a corpus rather than described.** Causal LM extracts 64 targets from the
lab's 64-token corpus and masked LM extracts 10 — **6.4× less signal per pass** — in exchange for
exactly **2× the context per prediction** (5.44 tokens against 2.72). The 80/10/10 rule is priced by
the mismatch it patches: [MASK] covers 12% of pre-training positions and 0% of fine-tuning ones.
RoBERTa's cheapest change is one line of probability: 0.85¹⁰ = **19.7%** of tokens never predicted
under ten static masks, 0.85⁴⁰ = **0.15%** under forty dynamic ones. T5's span corruption is priced
at the length it runs — 512 tokens, 77 corrupted in 26 spans, encoder input 461, decoder target 104,
which is **4.9× fewer decoder steps** than a masked model's 512 output positions.

**The tokenizer lab trains all three algorithms and caught its own bug by fertility.** BPE, WordPiece
and Unigram on one corpus to one budget: fertility 1.667 / 1.667 / 1.500 pieces per word on held-out
text, with Unigram shortest because it decodes by Viterbi and can revise an early choice. On an unseen
word, BPE and Unigram fall back to small pieces and **WordPiece emits [UNK] and loses the word**.
Two bugs surfaced here, both caught by the measurement rather than by a crash:
- The first WordPiece merge concatenated raw strings, so "s" + "##m" entered the vocabulary as "sm"
  and the working set as "s##m". Fertility came out at 4.67 — *worse than characters* — which is what
  flagged it.
- With that fixed it was still 4.33, and this one is not a bug: freq(ab)/(freq(a)·freq(b)) is maximal
  at exactly 1 when both halves occur once, so on a small corpus the criterion spends its budget on
  one-off letter pairs. The fix is the minimum-frequency floor real trainers ship, and the unfloored
  run is **kept as a lab frame**, because it is the measured justification for a hyper-parameter the
  copy notes nobody reads.

Cost note: `self_cross_attention` is the phase's most expensive lab at ~1.24 s cold, because it
trains four models to make its comparison honest (C5's two, the widened control, and the attention
model). That is ~6× `word2vec_cbow`'s 216 ms. The 100-epoch budget was kept deliberately even though
40 epochs scores *higher* (0.892), because matching C5's budget is what makes the comparison a
comparison.

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

**D1 · Preprocessing + Statistical NLP** — 8 topics into `nlp_preprocessing` and a new
`nlp_statistical` — **Done**
`stop_words` · `text_cleaning` · `regex_nlp` · `n_grams` (Preprocessing) · `hmm` · `pcfg` ·
`cosine_similarity` · `jaccard_similarity` (Statistical)

New category `nlp_statistical` ("Statistical NLP", icon `chart`, unused elsewhere in the section).
`bow_tfidf` **moved into it** from Preprocessing — the doc lists BoW and TF-IDF under Statistical
NLP, and they are the representation the rest of that category scores — so Preprocessing is now
ordered as a reading path (clean → tokenize → regex → stop words → stem → lemmatize → n-grams → BPE)
rather than alphabetically. Gating honours the doc's `🔒` markers verbatim: `stop_words` and
`text_cleaning` free, the other six premium. Edit Distance and KMP/Rabin-Karp are cross-linked from
the DSA taxonomy rather than duplicated, as planned.

**No new widget.** Six topics went to `TokenStripPlayer`, `pcfg` to `TreeVisualizer` and
`cosine_similarity` to `PointCloudPlayer` — and the plan's own suggestion (PointCloud for *both*
similarity topics) turned out to be half wrong: Jaccard is a set-overlap story that reads far better
as chips and bars than as a scatter, so only cosine needed the geometry. That is the same
check-the-widget-against-what-exists lesson B7 recorded, in the opposite direction.

Two math files carry every number the copy quotes — `TextPreprocessingMath.kt` (stop-list removal
rates, the cleaning pipeline's per-stage vocabulary, a real regex tokenizer plus an explicit
backtracking counter, an n-gram model with add-k perplexity) and `StatisticalNlpMath.kt` (forward,
Viterbi, greedy tagging, probabilistic CYK, cosine/Jaccard/MinHash) — and `D1MathTest` pins the
properties the topics rest on.

**Two guards were built this batch, and both immediately paid.** `SimulationFrameTest` gained
**TokenStrip** coverage — the widget carries a third of the AI section's labs and had none — checking
bar captions that do not line up with their values and heat grids whose labels do not match their
matrix; it caught `pcfg` rendering a frame with a link to a node that had been removed (removing a
node in `TreeBuilder` orphans its children, which the rebuild between the two attachment readings did
twice). `D1MathTest` caught the second: the plan's claim that reordering the tokenizer's alternation
branches "returns the naive result" is false — it gives 16 tokens, not 21, because the digit-initial
branches still fire, and the e-mail comes back as `.smith@x.co`, a token that *looks* like the
pattern worked. The copy now says what the pattern actually does.

Content notes worth keeping: the HMM lab needed a sentence where greedy tagging and Viterbi genuinely
disagree, which is not automatic — "book that flight" was tuned until NN wins the first word locally
(0.0105 vs 0.0100) and loses the sentence globally (1.89e-6 vs 2.70e-6, a 1.43× gap), and the test
pins both halves so a re-tune cannot quietly make greedy right. The add-k frame reports a real
optimum (k=1 → 5.20, k=0.1 → 3.42, k=0.01 → 4.34) rather than asserting that smoothing helps, and it
also prices what smoothing costs on a *seen* sentence (1.86 → 4.51).

**D2 · Syntactic & Semantic Analysis** — 6 topics into a new `nlp_syntax` — **Done**
`pos_tagging` · `chunking` · `dependency_parsing` · `constituency_parsing` · `coreference` ·
`sentiment_lexicon`

New category `nlp_syntax` ("Syntactic & Semantic Analysis", icon `browser`). `ner` moved into it from
Modeling — the doc lists it under this heading — so the category reads as a pipeline: tag, chunk,
recognise entities, parse, resolve reference, then score sentiment. `pos_tagging` is free (the doc
carries no lock on it), the rest premium. Sims exactly as planned: `TokenStripPlayer` for tagging,
chunking, coreference and sentiment; `TreeVisualizer` for the two parsers.

`SyntaxMath.kt` runs and scores everything rather than describing it: a most-frequent-tag baseline
and a bigram HMM estimated from the same mini treebank, an arc-standard oracle parser with the full
transition sequence, evalb over labelled spans, a head-percolation converter, a regex chunker with
span-level scoring, an agreement-based coreference resolver, and a lexicon scorer with and without
negation rules.

**Four things the probe changed before the copy was written:**
- **The HMM tagger tied the baseline** (12/14 each) and made a new error of its own, because no
  training sentence contained a VBP → RB transition — so the held-out "the dogs walk slowly" was
  tagged DT NN VBZ RB. Two training sentences supply that evidence and it now scores 14/14. Without
  the probe, the topic's central comparison would have been asserted and false.
- **Coreference chains were being read off immediate links.** "her" resolves to "She", not to Ada
  Lovelace, so the resolver looked 67% accurate; adding the transitive closure the task actually
  asks for takes it to 100%. Both numbers are now in the lab, because the gap *is* the lesson.
- **LAS could not differ from UAS** — the planted wrong parse had only a head error, so both scores
  were 0.83 and the reason for having two metrics was invisible. A label error was added: 0.83 UAS
  against 0.67 LAS.
- **One narration idea did not survive measurement.** The chunking frame was going to say span
  scoring is stricter than token accuracy; on this sentence they are 0.80 and 0.78, so the claim is
  not supported. What *is* demonstrable is the thing that matters: the mis-bounded span overlaps
  gold by two of three tokens and scores exactly zero.

**D3 · Word Embeddings** — 5 topics into a new `nlp_embeddings` — **Done**
`word2vec_cbow` · `word2vec_skipgram` · `glove` · `fasttext` · `elmo`

New category `nlp_embeddings` ("Word Embeddings", icon `map`). The `word_embeddings` umbrella moved
into it from Modeling and stays as the landing topic, which is what the coverage test's umbrella note
anticipated. All five are premium, per the doc's markers. Sims split as the plan suggested:
`TokenStripPlayer` for CBOW, skip-gram and FastText; `PointCloudPlayer` for GloVe and ELMo.

**The labs train for real.** `EmbeddingMath.kt` runs SGNS (both objectives), a weighted-least-squares
GloVe fit, subword composition and a contextual-vector stand-in on a 20-sentence corpus, with
deterministic seeds and lazily cached models so opening three labs trains each model once. No
hand-set vectors anywhere — which is the difference from the older `word_embeddings` umbrella lab,
whose four dimensions are labelled by hand so the analogy lands exactly.

**Three things the probe found before any copy was written**, all of which would have shipped as
plausible-sounding falsehoods:
- **A 2-D GloVe fit is degenerate on this corpus.** The biases absorb most of log X, every vector
  landed on one line, and *every* cosine came out 1.00 — nearest-neighbour lists were meaningless.
  Fixed by training at 8 dimensions and projecting to 2 with a power-iteration PCA, which is also
  what people actually do to look at embeddings.
- **ELMo's sense separation did not hold** with the first four sentences: the two money sentences
  scored 0.583 against each other, *below* several cross-sense pairs. The corpus was rewritten so the
  sentences within a sense share content words, and now every same-sense pair (0.953, 0.954) beats
  every cross-sense pair — which is what `D3MathTest` asserts, rather than the gap's size.
- **The rare-word folklore inverted.** The plan and every textbook say skip-gram beats CBOW on rare
  words; on 102 tokens CBOW places `monarch` nearer `king` (0.89) than skip-gram does (0.60). The
  copy reports the measurement and explains why the published claim is a statement about billions of
  tokens — and the test pins the direction, so a re-tune cannot quietly make the narration false.

Also measured rather than asserted: the analogy. king − man + woman lands on `queen` at 0.93 under
skip-gram and 0.77 under the GloVe fit, both on vectors these labs train; the copy says explicitly
that 20 sentences cannot rank the two methods. The 3/4-power noise distribution is shown moving
"the" from 26.5% of draws to 18.0% and `monarch` from 2.0% to 2.5%.

**D4 · Transformer Internals + Pre-trained LMs** — 8 topics into two new categories — **Done**
`positional_encodings` · `feed_forward` (→ `nlp_transformer`) · `bart` · `xlnet` · `gpt3_gpt4` ·
`llama_vicuna` · `mistral_mixtral` · `claude_gemini` (→ `nlp_pretrained`)

Two categories at once, because the doc's two headings split cleanly: `nlp_transformer` ("The
Transformer Architecture", icon `chip`) takes the `attention` and `transformers` umbrellas off
Modeling, and `nlp_pretrained` ("Pre-trained Language Models", icon `crown`) takes `llms`. Modeling
is now down to `rnn_lstm` and `rag`, and C5–C6 will empty it. All eight premium, per the doc.

Sims as planned — `TokenStripPlayer` for positional encodings, BART, XLNet, MoE and the frontier
families; `NeuralNetPlayer` for the FFN block, the scaling laws and the LLaMA argument. No new widget.

`TransformerMath.kt` computes the sinusoidal table and its offset invariance, RoPE's rotation
identity, ALiBi slopes, the FFN parameter split, BART's five corruptions, XLNet's permutation
objective and independence gap, a top-2 MoE router with its load-balancing loss, the published
scaling-law table, and the attention/KV-cache cost of a long context.

**Two claims the probe overturned before the copy was written:**
- **Sinusoidal similarity does not decay monotonically.** The offset profile falls to offset 3 and
  then rises again at 4, 5, 6, 11 and 12 — it is a sum of cosines at different frequencies. Every
  textbook diagram draws a smooth decay and the actual table does not have one. The frame now shows
  the profile and names the offsets where it rises, and `D4MathTest` asserts the non-monotonicity so
  a re-tune cannot quietly restore the false version.
- **Two of BART's five corruptions were byte-identical.** With the rotation pivot at a sentence
  boundary, document rotation produced exactly the same string as sentence permutation on a
  two-sentence document, so the lab showed five frames of four distinct ideas. The pivot moved
  mid-sentence and a test now pins that all five outputs differ. The same probe run also killed the
  planned "fraction preserved" metric — position-wise similarity scores a rotation near zero even
  though it loses nothing — replaced by the 2×2 that actually separates them: are tokens gone, and
  is the order changed.

The `claude_gemini` topic is deliberately written against the volatile/durable split: architecture,
context cost and alignment method in the copy; a closing frame that says outright that scores and
prices are stale within months and the deciding factors are latency, context, tool reliability,
region and price. Model facts were taken from the `claude-api` skill rather than from memory.

**D5 · Modern LLM Techniques** — 7 topics into a new `nlp_modern_llm` — **Done**
`prompt_engineering` · `chain_of_thought` · `tree_of_thoughts` · `vector_databases` · `react` ·
`ai_agents` · `hallucination_mitigation`

New category `nlp_modern_llm` ("Modern LLM Techniques", icon `robot` — the agent loop these
techniques are built around, and unused in this section). `rag` **moved into it** from Modeling: the
doc lists it under this heading, and it is the technique the other six are built around. Modeling is
now down to `rnn_lstm` alone, which C5 will take. Ordered as the stack is assembled — shape the
prompt, decompose the reasoning, search over the decomposition, then retrieve, act, and check.
Gating honours the doc's markers verbatim: only `prompt_engineering` is free.

**No new widget, and the plan's own suggestion was wrong again.** It named `GameSearchPlayer` for
Tree of Thoughts as "the MCTS-family widget" — that widget renders a tic-tac-toe board and has no
tree at all, so it cannot draw a frontier or a pruned branch. `TreeVisualizer` took it. Otherwise:
three topics on `TokenStripPlayer`, two on `NeuralNetPlayer` (both are curve-and-bar arguments), one
on `PointCloudPlayer`. That is the same read-the-widget-before-trusting-the-plan lesson B7, C2 and
D1 each recorded, now for the third distinct widget.

`ModernLlmMath.kt` refuses to simulate a language model anywhere. What it runs instead is *the
problem each technique solves*: an enumerable hypothesis space for prompting, an exact multinomial
plurality calculation for self-consistency, a real search over Game of 24, a real IVF and a real
graph index over real distances, and a real TF-IDF retriever over a real corpus. Where a model's
behaviour has to be stood in for it is stood in for by a stated parameter — a per-step accuracy, a
number of distinct wrong answers — rather than a number typed in to make the story work, and the
copy says so.

**The probe overturned four claims before any copy existed, and one of them is the batch's headline:**
- **Self-consistency confidence does not detect hallucinations — and the control run says why.** The
  planned frame was "threshold on confidence and convert errors into refusals at a known rate".
  Measured, that mechanism fails: on the lab's population the least-confident *supported* question
  sits at 0.712 and the most-confident *unsupported* one at 0.883, so the signal does not separate
  the groups at all (ECE 0.225). The best threshold that still answers half the questions buys
  selective accuracy 0.668; grounding buys **0.959 at the same 60% coverage**. Rather than assert the
  reason, the lab reruns the identical population with the errors *scattered* over four wrong answers
  instead of one — and then confidence separates cleanly and thresholding scores 0.959 too. One
  property flipped, opposite conclusion. Self-consistency measures conviction, not correctness.
- **Tree of Thoughts: the better evaluator is the *more* expensive lever here.** The cheap
  one-operation evaluator turned out to be far worse than expected — it ranks the best genuinely
  solvable first move 8th of 36 and none of its top five can reach 24 — so beam width 1, 2, 3 and 5
  all fail and width 8 is the first that works. Giving the evaluator one more operation of lookahead
  lets width 1 solve it, but costs 702 evaluator calls against 180 for the weak evaluator at width 8.
  The frame is now the measured trade (width and evaluator quality are substitutes, and which is
  cheaper is a measurement) rather than the planned "a better evaluator lets you search less".
- **A plain k-NN graph is disconnected, which is the real HNSW motivation.** The first graph lab was
  built as "greedy search gets stuck in local minima" and measured recall 0.0 at every `ef` — because
  the 6-NN graph over this corpus comes apart into **3 components**, so from 33% of entry points
  there is no path to the answer and no candidate list can create one. Adding two random long-range
  links per node collapses it to one component and recovers recall 1.00 in 3 hops from the same
  stranded start. That is a better frame than the one planned, and it is why HNSW is not a k-NN graph.
- **Self-consistency voting can make accuracy worse, exactly.** Enumerated over the multinomial
  rather than sampled: at p = 0.40 with wrong answers spread over 4 values, voting over 9 chains
  lifts accuracy to 0.590; with every wrong chain landing on the *same* value, the identical vote
  drives it down to 0.267 — below the single chain it started from.

Two smaller corrections came out of the same run. The prompt pool was reordered so the reading path
is the interesting one: the first three words a person would reach for leave the rule underdetermined
(5 → 4 → 3 → 2 survivors, with a genuine 50/50 tie at three demonstrations), and `level` resolves it
at four. And the agent lab's "retry overhead" was originally just step 4's own tokens, which is
mislabelled — it now measures what the failure actually cost by billing the trajectory twice, with
and without the failed call and its reissue: **647 tokens, 49%**, because everything after them is
resent with them attached.

Measurements the copy is built on: the ReAct question's answer passage ranks **5 of 8** under the
question as asked and **1** under the query the second thought writes; the agent trajectory bills
1,970 tokens against a 700-token final context (**2.81×**); IVF at a cell boundary returns recall
0.60 for 28 comparisons at nprobe 1 and 1.00 for 35 at nprobe 2, against brute force's 180;
quantization at 16 levels imposes a 0.067 grid on neighbours 0.0089 apart, and recall only returns at
64 levels; contrast falls 34.6 → 0.35 from 2 to 128 dimensions; and chain-of-thought decomposition at
p = 0.92 beats a 0.55 direct answer up to **7** steps and loses at 8.

New guard `D5MathTest` (23 tests). Four of them are written as "if this ever flips, the topic's
central claim is false" — the non-separating confidence signal, the systematic-error voting curve,
the greedy Game-of-24 failure, and the disconnected k-NN graph — because each one is a frame that
would read perfectly well in the wrong direction.

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

**Shipping and green (288 tests):**
- `ContentCoverageTest` — every browsable topic has content, nothing resolves to `NotYetAvailable`,
  and every prerequisite and cross-link id resolves. It has caught real breakage every batch and is
  run (with `--rerun-tasks`) at the end of each one.
- `CategoryIntegrityTest` (phase 10) — generic over all eight sections: every `categoryId` resolves
  within its own section's `all` list, no category is empty, ids are globally unique. This is what
  made deleting `ml_unsupervised` in B6 a safe edit rather than a careful one.
- `SimulationFrameTest` (phase 10) — runs every frame builder on the JVM and asserts point-cloud
  frames stay inside the unit square. Caught B6's `unevenClusters` overflow. **C1 added neural-net
  coverage** (declared plot axes, bar caption arity) and it found four live bugs on its first run,
  two of them frames rendering the wrong data entirely; **C2 then failed on seven of its own ten new
  labs** before they shipped, which `autoPlot` now prevents structurally. **D1 added TokenStrip**
  (bar caption arity, heat-grid label/matrix agreement) and caught a dangling tree link in its own
  new `pcfg` lab. `RegressionLab` and `DecisionSurface` are still unguarded.
- `AiTaxonomyCoverageTest` (B6) — the doc as code. 357 entries nested by section and heading, each
  mapped to the topic ids serving it or to an explicit gap; pins the entry count, id resolution,
  section membership, and the covered count. Its failure message prints the remaining backlog.
- `DimReductionMathTest` (B6), `B7MathTest` (B7), `DeepNetMathTest` (C1), `ActivationMathTest` (C2),
  `CnnMathTest` (C3), `DetectionMathTest` (C4), `D1MathTest`, `D2MathTest`, `D3MathTest`,
  `D4MathTest` and `D5MathTest` — pin the properties each
  batch's copy leans on, so a re-tune that makes a topic pointless fails instead of shipping. Worth
  continuing per batch; `B7MathTest` caught a wrong claim that had already been written, and
  `D1MathTest` caught one this plan document had asserted.

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
- ~~**`FeatureMapPlayer` is still net-new** and blocks C3 and C4.~~ Built with C3, not before it —
  C1 and C2 never needed it, and building it against real content rather than in advance is what
  gave it its live kernel/stride/padding controls and its frame guard.
- **Verification keeps finding real defects, not wording problems.** Across nine batches: an
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
  is carrying unknown bugs of that shape. C2 then showed the guard's second use — it failed on seven
  of ten new labs during authoring, which is far cheaper than finding them after release, and the fix
  (`autoPlot`, deriving axes from the data) removed the whole class rather than the seven instances.
  D1 extended the same guard to `TokenStrip` and it found a live bug in the batch's own new tree lab
  (a frame linking to a removed node), while `D1MathTest` falsified a claim this plan document had
  itself asserted about the regex tokenizer. D5 is the first batch where the widget guard found
  nothing — its seven labs were written against math that had already been probed and re-probed,
  which is the order this phase keeps arriving at. Two widgets remain unguarded: `RegressionLab` and
  `DecisionSurface`. D3 is the strongest case yet for the probe-before-copy rule: its labs train
  rather than replay fixed numbers, and the first run produced a degenerate GloVe geometry, an ELMo
  sense separation that did not exist, and a rare-word comparison pointing the opposite way to the
  literature. All three read as perfectly good copy if written before looking. D2 then found four
  more of the same kind in one batch — a contextual tagger that did not beat its baseline, a
  coreference score computed on the wrong unit, two parser metrics that could not differ, and a
  comparison between metrics that the numbers did not support. D4 caught a textbook claim: the
  sinusoidal encoding's similarity is *not* monotone in distance, which every diagram of it implies
  and the table refutes. D5 produced the largest single reversal so far: its planned frame was that
  thresholding on self-consistency confidence converts hallucinations into refusals, and measured, the
  signal does not separate supported questions from hallucinated ones at all — it is *highest* on the
  questions the model is systematically wrong about. The batch also found that a better Tree-of-Thoughts
  evaluator was the more expensive lever rather than the cheaper one, and that the k-NN graph its
  vector-search lab was built on is disconnected. All three read as perfectly good copy if written
  before looking, and all three now have a test whose failure message says the claim has inverted.
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

~~A1 → A2~~ → ~~B1 → B2 → B3 → B4 → B5~~ → ~~guards~~ → ~~B6 → B7 → B8~~ → **B9** → B10 → ~~C1 → C2~~
→ ~~**`FeatureMapPlayer`** → C3 → C4 → C5 → C6~~ → C7 → C8 → C9 → ~~D1~~ → ~~D2~~ → ~~D3~~ → ~~D4~~ → ~~D5~~ → D6.

Struck-through batches are done. The guards step landed with B6 rather than before it, in the same
session. The order was then broken deliberately twice, both times at the user's direction and both
times harmlessly: C1 came before B8–B10, and within Track D the run order was **D1 → D3 → D2 → D4**
rather than D1 → D2 → D3. The tracks are independent, and the only real intra-track dependency the
phase had — `FeatureMapPlayer` before C3 — was resolved by C3 building it.

That D3-before-D2 swap did cost something small and worth recording: `word_embeddings` moved into
`nlp_embeddings` in D3 while `ner` was still sitting in `nlp_modeling`, so Modeling was left holding
an odd mix for one batch until D2 and D4 emptied it. Moving umbrella topics out of a generic bucket
is cheap to do per batch but only reads coherently once the whole track lands.

B8–B10 stay open, and Track B is not finished until `ml_supervised` is empty.

## Verification

Per batch: `./gradlew testDebugUnitTest` (coverage + taxonomy + category guards) then
`./gradlew assembleDebug` and a manual walk of the touched category screen — list renders, every row
opens, sim plays, premium rows gate.

**Outstanding:** the automated half has run green on every batch (288 unit tests at D5, plus
`assembleDebug`), but the **manual emulator walk has not been done for D1–D5**. Those five batches
added seven NLP categories, moved six topics between categories, and added thirty-four detail
screens — all of it guarded against broken content, resolvable links, and frame builders that throw,
none of it actually looked at on a device. This is now the phase's largest unverified surface and it
grows every batch. Worth one session with the emulator before Track D finishes: open each new
category, confirm the reading-path ordering reads correctly, and play at least one lab per widget
(TokenStrip, TreeVisualizer, PointCloud, NeuralNet all gained configs).
