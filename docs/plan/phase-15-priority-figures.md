# Phase 15 — Forty priority figures

> **Superseded in part by Phase 16:** several figures below were drawn from labs that are no longer
> the ones the pages show, and some of the page numbers they quoted were stale. See
> `phase-16-figures-and-lab-alignment.md` for what was corrected.

Status: **Done — 40 built, P1–P4.** AI figure coverage goes from 149 to 189 pilot topics (368 figures
app-wide, iOS export included). No new shapes, no renderer changes.

## How the forty were chosen

Of the 102 Tier-A topics Phase 13 left unbuilt (`phase-12-figure-scope.md`), these are the ones a reader
is most likely to reach and least likely to understand without a picture: ranked by inbound cross-links
first (`actor_critic` has 8, the most of any figureless page), then by curriculum weight — the RL tabular
methods, transformer internals and the LLM practice pages are interview staples. Tier B (58 stub pages)
and Tier C (16) stay out for the reasons the scope doc gives.

| Batch | Topics |
|---|---|
| P1 — RL core (9) | `actor_critic`, `sac`, `ddpg`, `td_learning`, `monte_carlo_rl`, `policy_iteration`, `q_function`, `sarsa`, `discount_factor` |
| P2 — Deep learning (11) | `feed_forward`, `layer_normalization`, `flash_attention`, `vit`, `gelu`, `exploding_gradient`, `lr_schedulers`, `alexnet`, `bidirectional_rnn`, `fast_rcnn`, `faster_rcnn` |
| P3 — LLM practice (11) | `chain_of_thought`, `hallucination_mitigation`, `react`, `peft`, `quantization`, `fine_tuning_full`, `dpo`, `vector_databases`, `mistral_mixtral`, `long_context`, `ssm` |
| P4 — ML + NLP (9) | `auc`, `silhouette_score`, `z_score_standardization`, `smote`, `gini_impurity`, `kernel_pca`, `bayesian_networks`, `rouge`, `glove` |

## Where the numbers came from

- **Executed from the app's own labs** (via a throwaway reflective JVM probe, deleted afterwards):
  the six gridworld figures (`RlGridWorldSection.kt` — policy iteration's 9/6/4/2/0 changes, the TD vs MC
  error checkpoints, the Q-table, the cliff routes and the four-γ value curves), `exploding_gradient`
  (`deepRun` at σw = 1.5 and He), `lr_schedulers` (the schedule functions in `OptimStoryFrames.kt`) and
  `silhouette_score` (`ClusterMetricsLab` sweeps — they reproduce the page's 0.8452 / 0.3632 / 0.4629).
- **Computed from the page's formulas**: SAC's tanh-squash density, GELU, the online-softmax walk,
  0.92ⁿ, the attention FLOP share (crossover 24,704 = the page's), SSM decay curves, NF4 levels,
  LayerNorm at batch 1, impurity curves, GloVe's weighting, AlexNet's per-layer parameters, anchor sizes.
- **Quoted from the page's prose** where no lab reproduces them (PEFT, quantization MSEs, DPO, ROUGE,
  SMOTE, z-score, vector DB). `fine_tuning_full` was redrawn from its lab once the prose was fixed.
- **Illustrative and said so in the caption**: `auc` (a 3×4 pairwise grid), `kernel_pca` (rings computed
  in NumPy), `bayesian_networks` (the textbook sprinkler tables).

## What the probe found — prose that disagreed with its own lab

The storyboard lab redesign had changed five labs without updating the page text. All five are now
rewritten against what the lab actually shows (numbers re-measured from the lab code):

- `exploding_gradient` — activations 2.2 → 11.3M (was 1.4 → 5.7M); gradient sentence now quotes the
  lab's 1.4 × 10⁷ layer-1 ratio and ≈2.7 × 10⁸ weight-gradient norm (was "10¹⁵").
- `lr_schedulers` — the lab runs constant 0.03, step decay halving every 15, cosine and warmup+cosine
  from (−8, 1): finals 0.827 / 0.171 / 0.109 / 0.099, so decay wins; prose, steps, formulas, takeaways
  and the first code block now say so. The noise-floor demo stays as the second code block (re-run:
  0.000223 vs 0.0000366, 6.09×).
- `fine_tuning_full` — the lab is a 16-point circle task: scratch 55%, head-only 95%, full 82% test
  accuracy. Prose, formulas, takeaways and the figure now use those; the 253× forgetting number (from a
  lab that no longer exists) is gone and forgetting is described without a number.
- `bidirectional_rnn` — the lab walks "the horse raced past the barn fell" with 128-dim states per
  direction; the trained-corpus numbers (0.8983, 1,799 vs 903) are gone.
- `td_learning` — added why the lab's deterministic world lets Monte Carlo win.

## Verification

`./gradlew :app:testDebugUnitTest` (401 tests, including `FigureShapeTest` and `AiFigureCoverageTest`,
whose pilot count is now 189), `./gradlew assembleDebug`, and the iOS export
(`EXPORT_IOS_CONTENT=1 … IosContentExportTest`). **No emulator pass yet** — Phase 13's showed that
graph spacing and pointer length are the two things the tests cannot fully see.
