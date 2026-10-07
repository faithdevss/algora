# Phase 16 — Fifty more figures, and pages aligned to their labs

Status: **Done — 50 figures built (Q1–Q5).** AI figure coverage 189 → 239 pilot topics, 418 figures
app-wide, iOS export included. No new shapes, no renderer changes.

## Scope

The remaining 62 Tier-A topics minus twelve low-value ones (deepfakes, complement/categorical NB,
k-modes, BIRCH, affinity propagation, incremental PCA, METEOR, KAN, neural ODEs, capsule networks,
SELU). Tier B (stub pages) and Tier C stay out, as before.

| Batch | Topics |
|---|---|
| Q1 — RL + evaluation (6) | dynamic_programming, exploration_exploitation, pomdp, prioritized_replay, adjusted_r_squared, davies_bouldin |
| Q2 — classic ML (10) | bernoulli_nb, rfe, chi_square_selection, eclat, autoregression, sarima, prophet, mcmc, restricted_boltzmann_machines, deep_belief_networks |
| Q3 — deep learning (13) | swish, efficientnet, densenet, group_normalization, gcn, gat, dcgan, cyclegan, stylegan, stable_diffusion, ssd, mamba, rwkv |
| Q4 — NLP (11) | chunking, constituency_parsing, coreference, pcfg, jaccard_similarity, regex_nlp, sentiment_lexicon, word2vec_cbow, fasttext, elmo, hf_tokenizers |
| Q5 — pretrained families, eval, agents (10) | bart, distilbert, roberta, t5, xlnet, gpt3_gpt4, llama_vicuna, mmlu, tree_of_thoughts, ai_agents |

## The rule that changed: figures follow the lab the reader actually sees

`SimulationHost` routes most AI topics to the newer storyboard labs (`rlBoardTopicIds`,
`evalStoryTopicIds`, `deepStoryTopicIds`, …) **before** the older per-type sections. Several older
sections (`RlGridWorldSection`, `ClusterMetricsLab`, `RegressionMetricsLab`) still compute numbers but
are no longer what those pages show. Phase 15 sourced some figures from them by mistake.

So every figure here was drawn from the visible lab, read through a throwaway reflective JVM dumper
(every string each lab frame draws, across tabs and sliders; deleted afterwards), and **page text that
contradicted the visible lab was rewritten** in the same pass. Where a page's number came from its own
self-contained code example rather than a lab, the sentence now says so instead.

### Phase 15 figures corrected

- **Wrong lab used:** `td_learning`, `monte_carlo_rl`, `q_function` (now from the slippery-grid RL board
  labs), `sarsa` (now the 4×6 cliff in `RlStoryLabs`, 11-step detour vs 7, returns −21 vs −26),
  `silhouette_score` (now the storyboard eval data: 0.805 at k = 3; rings 0.361 → 0.477 at k = 6). The TD
  prose sentence Phase 15 added was wrong for the visible lab and is removed.
- **Page and lab disagreed; both now follow the lab:** `smote` (6:60 → 30:60, recall 0.40 → 0.90,
  precision 0.67 → 0.56), `dpo` (4 responses, 7 pairs, β sweep), `rouge` (11-word reference: summary F1
  0.842, whole doc 0.524), `glove` (16-token corpus, x_max = 3), `auc` (0.9446 over 80,256 pairs), `ssm`
  (Ā = 0.95/0.9/0.8/0.6), `quantization` (41.0 dB 8-bit, 15.8 dB 4-bit, 14.0/7.2/3.7 GB; NF4 and
  outlier figures recomputed), `faster_rcnn` (lab's 3,600 anchors added), `chain_of_thought` (lab's
  p = 0.6 → 0.92 added; the page's p = 0.4 voting math was verified correct).

### Phase 16 pages whose prose was rewritten to the lab

adjusted_r_squared (20 noise columns: R² 0.698 → 0.808, adjusted 0.690 → 0.625), davies_bouldin,
rfe and chi_square_selection (one dataset: χ² 214.7 / 177.7 / 6.0 / 0.8 / 0.0), autoregression (p = 8
is the first order to beat seasonal naive, not 9), sarima, prophet (seasonal order dominates;
changepoints barely matter), gat (node-3 weight 0.021 → 0.114), mamba (7-filler selective-copy),
constituency_parsing, coreference, sentiment_lexicon, word2vec_cbow, fasttext, elmo, jaccard_similarity,
hf_tokenizers, mmlu.

### Worth knowing, not fixed

- `dpo`'s lab captions describe β backwards ("β = 0.1 … stays near the reference" while its KL is the
  largest, 1.08 nats; "β = 2 moves far … fast" while it barely moves). The page text now follows the
  numbers; the lab captions (Android and the iOS port) still need correcting.
- The drift is wider than these pages: the storyboard redesign replaced many labs without updating
  their topic text. Pages outside the 90 figure topics were not audited.

## Verification

`./gradlew :app:testDebugUnitTest` (401 tests; `AiFigureCoverageTest` pilot count now 239,
`FigureShapeTest` over every figure), `./gradlew installDebug`, the iOS export and an iOS simulator
build. Emulator pass over a sample (tree, graph, strips with aux rows, multi-series plots, wide grids):
it caught three layout problems that were fixed — a cramped span label inside tree nodes, a strip
whose 11 narrow cells wrapped words mid-token, and a plot whose interesting region was squashed by its
y-range.
