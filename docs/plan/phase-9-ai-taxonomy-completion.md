# Phase 9 — AI Taxonomy Completion

Status: In progress — Track A complete (RL at 100% of the doc), B1 and B2 done. 31 of 226 topics
authored; taxonomy at 231, target 426. Next: B3 (Bayesian Algorithms, 7 topics)
Depends on: Phase 5 (AI mode shell + content template), Phase 3 (simulation widgets), Phase 8 (premium gating)

## Goal

Close the gap between `docs/topics.ai.md` — the full AI taxonomy, 357 listed entries / 339 unique —
and what the app actually ships. Today the four AI sections carry **102 topics**; the taxonomy calls
for **~328 distinct topics** once cross-listed entries (RLHF ×3, MDP ×2, LSTM ×3, Perceptron ×2) are
collapsed. This phase authors the missing **226**, taking the whole app from 200 to **426 topics**.

## Current coverage (measured 2026-07-28)

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

RL is the only near-complete section: everything from **DQN Family** downward ships, plus three
topics the doc does not list (VDN, QMIX, MADDPG). The two gaps are its two most foundational
sub-sections.

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

**Machine Learning — 12 categories** (`ml_regression`, `ml_classification`, `ml_bayesian`,
`ml_ensemble`, `ml_clustering`, `ml_dimreduction`, `ml_nn_foundations`, `ml_association`,
`ml_timeseries`, `ml_preprocessing`, `ml_metrics`, `ml_rl_fundamentals`)

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

Fourteen batches, each one or two categories, sized so a batch is a single session's work and lands
green. Order is dependency-first: foundations before the topics that cross-link to them.

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

**B3 · Bayesian Algorithms** — 7 topics
Gaussian NB · Multinomial NB · Bernoulli NB · Complement NB · Categorical NB · Bayesian Networks ·
MCMC
*Sims:* `ClassifierPlayground` (per-likelihood decision surfaces), `GraphVisualizer` (Bayes net
d-separation), `PointCloudPlayer` (MCMC chain walking a posterior).

**B4 · Ensemble Methods** — 9 topics
AdaBoost · XGBoost · LightGBM · CatBoost · Extra Trees · Voting · Stacking & Blending · Bagging ·
Isolation Forest
*Sims:* `PointCloudPlayer` (reweighting per boosting round, residual-fitting stumps — the existing
`gradient_boosting` frame builder generalizes), `ClassifierPlayground` (vote aggregation),
`TreeVisualizer` (histogram vs leaf-wise growth for LightGBM).

**B5 · Clustering** — 10 topics
K-Medians · K-Modes · Hierarchical (Divisive) · HDBSCAN · OPTICS · Mean Shift · BIRCH ·
Affinity Propagation · Spectral · GMM
*Sims:* `PointCloudPlayer` (centroid moves, reachability plots, mode-seeking kernel drift, soft GMM
responsibilities), `GraphVisualizer` (spectral's affinity graph → eigenvector cut).

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
*Sims:* **one new widget** — `FeatureMapPlayer`: an input grid, a sliding kernel, and the resulting
feature map, with stride/padding/kernel-size controls and a per-architecture layer stack. This is the
only new simulation widget the phase needs, and it also serves C4.

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

- `ContentCoverageTest` — already asserts every browsable topic has content, nothing resolves to
  `NotYetAvailable`, and every prerequisite/cross-link id resolves. Must stay green at the end of
  **every** batch, not just the phase.
- **New: `AiTaxonomyCoverageTest`** — a checked-in `AiTaxonomyMap.kt` maps each `docs/topics.ai.md`
  entry to the topic id that serves it (many-to-one where a doc entry is satisfied by a cross-listed
  topic). The test asserts every mapped id resolves through `TopicRegistry` and that no doc entry is
  unmapped. This is what makes "are we done?" a test result rather than a manual recount, and it
  catches drift if the doc is edited later.
- **New: category integrity assertion** — every topic's `categoryId` resolves to a category in its
  section's `all` list, and no category is empty. Cheap, and the 40-category restructure is exactly
  where that breaks silently.

## Risks / open items

- **Topic-id collisions.** `TopicRegistry` already collapses cross-listed ids first-wins
  (`perceptron`, `transformers`). This phase deliberately cross-lists far more (backprop, LSTM,
  attention, RLHF, all seven Deep RL entries). The `AiTaxonomyMap` makes each collision intentional
  and reviewable instead of accidental.
- **`FeatureMapPlayer` is net-new** and blocks batches C3 and C4. Build it first within Track C, or
  reorder Track C to start at C1/C2 while it lands.
- **Browse-list length.** 426 topics changes the character of the category screens. Per-category
  search already exists (Phase 2); worth re-checking scroll performance and whether Home's Quick
  Access grid still makes sense as the only route into a 12-category ML section.
- **Batch B9 (17 metrics) and D6 (16)** are the two thin-topic batches. They are the most likely to
  read as filler if authored mechanically — each metric needs its own honest "when this one misleads
  you" angle, not a formula restatement.

## Suggested order

A1 → A2 (RL hits 100%, smallest track, validates the new-category mechanics on a section that already
has nine) → B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8 → B9 → B10 → C1 → C2 → `FeatureMapPlayer` → C3 →
C4 → C5 → C6 → C7 → C8 → C9 → D1 → D2 → D3 → D4 → D5 → D6.

## Verification

Per batch: `./gradlew testDebugUnitTest` (coverage + taxonomy + category guards) then
`./gradlew assembleDebug` and a manual walk of the touched category screen — list renders, every row
opens, sim plays, premium rows gate.
