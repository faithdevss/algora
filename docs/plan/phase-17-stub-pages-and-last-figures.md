# Phase 17 — The stub pages, and the last useful figures

Status: **Done — 70 figures built (R1–R5), 58 stub pages expanded.** AI figure coverage goes from 239 to
309 pilot topics, and from 418 to 488 figures app-wide. The iOS export is included. No new shapes and
no renderer changes.

## Scope

This phase covers every remaining AI topic worth a figure. That is the 58 Tier-B stub pages, which
Phase 12 had judged too thin to draw on, plus the 12 low-value pages Phase 16 skipped. The 16 Tier-C
pages are still out, for the reasons given in `phase-12-figure-scope.md`.

| Batch | Topics |
|---|---|
| R1 — deep RL: value methods and policy gradients (16) | dqn, double_dqn, dueling_dqn, experience_replay, target_networks, noisy_nets, c51, rainbow_dqn, reinforce, a2c, a3c, gae, trpo, td3, dpg, max_entropy_rl |
| R2 — exploration, model-based and multi-agent RL (21) | epsilon_greedy, ucb, thompson_sampling, boltzmann_exploration, intrinsic_motivation, icm, rnd, dyna_q, world_models, dreamer, mbpo, mcts, minimax, alphago, alphazero, muzero, self_play, maddpg, qmix, vdn, meta_rl |
| R3 — offline, imitation and RLHF (8) | offline_rl, cql, iql, decision_transformer, imitation_learning, irl, gail, rlhf |
| R4 — ML, DL and NLP stubs (13) | rnn, lstm_gru, neural_network_basics, autoencoders, diffusion_models, regularization, model_evaluation, naive_bayes, bow_tfidf, lemmatization, ner, rag, bpe |
| R5 — the twelve Phase 16 skipped (12) | deepfakes, complement_nb, categorical_nb, k_modes, birch, affinity_propagation, incremental_pca, meteor, kan, neural_odes, capsule_networks, selu |

## Expanding the stubs

A figure follows the page's measured numbers, and the stub pages stated none. So each of the 58 got
the following, all taken from its visible lab:

- a three-paragraph `whatIsIt`: the idea, what the lab measures, and the trade-off;
- one takeaway quoting the lab's numbers;
- the figure itself.

The numbers were read with the same throwaway reflective dumper Phase 16 used, which was deleted
afterwards.

## Prose that disagreed with its lab (rewritten)

Phase 16's rule applies: the figure follows the lab the reader actually sees, and so does the text.

- `kan`: the page described a separate 16-parameter SGD run (MSE 0.166 vs 0.0030, "55×"). The lab
  compares a 1-6-1 MLP with 19 weights (RMSE 0.069–0.131 over three seeds) against one 20-knot spline
  edge (RMSE 0.024 every time, 2.9× closer). The prose, steps, formulas and takeaways now follow the
  lab, and the code block is labelled as the separate run.
- `selu`: the page said layer 20 had std 0.971 and that ReLU and tanh ended at std 0.090 and 0.148.
  The lab shows variance 1.01 at layer 5 and 0.90 at layer 20, against tanh's 0.10 and 0.02.
- `capsule_networks`: the page described a single-parent run (0.59 / 0.64 / 0.41 after 3 rounds).
  The lab splits each vote between parents A and B: 0.76 / 0.90 / 0.21, and |v_A| goes 0.48 → 0.73.
- `meteor`: the page said a shuffle makes 8 chunks and scores 0.500. In the lab's shuffle one pair
  stays adjacent, giving 7 chunks, a 0.335 penalty and a score of 0.665. The 0.500 ceiling is now
  described as the no-adjacent-pair case, which is what the code block shows.
- `neural_odes`: added the lab's own run (4 Euler steps reach 0.0625 against exact 0.1353, 54% off;
  RK4 is off by 0.00021). The page's step-doubling series is noted as starting at 5 steps.
- From R1–R4: `dpg` (curve rescaled to the lab's values) and `decision_transformer` (rebuilt from the
  lab's exact target/achieved pairs; a −0.5 target achieves −1.25, and the text now says so).

## Lab captions that contradicted their own numbers (fixed, Android and iOS)

- `dpo`: β was described backwards. The caption now says a small β is a weak leash, so the policy
  moves furthest from the reference, and a large β holds it close. It quotes the run's KL.
- `glove`: the caption said king and queen "end up close" beside a cosine of −0.24. Their rows of X
  are identical, but each row gives only 2 targets for 4 dimensions plus a bias, so many different
  vectors fit exactly. The caption now says that.
- `rainbow_dqn`: Noisy Nets' row was "goal reached 94% → 33%", listed as a fix although it is a drop.
  It now shows what per-episode noise does fix in that lab: steps to goal 35.9 → 6.0.
- `alphago`: the captions claimed a better evaluator gives a sharper search, but random playouts (54%)
  beat every value net (47 / 45 / 42%). They now report each share, keep the true ordering (less noise
  means more visits on the best move), and say why playouts win on a 3×3 board.
- `meteor`: the shuffle caption hard-coded "8 chunks" beside the computed 7. It now prints the run's
  chunk and match counts.

`complement_nb` was listed here at first, but on re-reading the lab it is correct. The −4.61 is
multinomial's politics score; complement's politics score is −3.58 and the lowest score wins. The
mistake was in this phase's own figure, which has been redrawn with all four scores.

## Figure ↔ lab agreement test (new)

`FigureTextAgreementTest` sweeps every figure whose caption cites the lab. Each decimal or percentage
the figure shows must appear in the lesson text or in a string the lab draws. Lab strings come from
`LabText` (test source), which follows `SimulationHost`'s routing and walks every tab and parameter
setting of the story labs, plus the older config-driven sections. Axis-extent labels are skipped.
Numbers a figure computes with the lab's own functions, which the lab draws as shapes rather than
text, are listed in the test's `derived` map, each with the arithmetic or function that produces it.
Figures that don't cite the lab are self-contained worked examples and are not swept.

Running it against the existing figures found eight that had drifted from their redesigned labs. All
were recomputed from the lab code, and where the page text was stale it was rewritten too:

- `arima`: ACF from the lab's series (lag 1: 0.851 → 0.084 → −0.488; season +0.250 at lag 12).
- `exponential_smoothing`: Holt-Winters on the lab's series (level 43.55 → 61.96; first forecast
  63.055). The old figure was drawn from a different series that ran from 108 to 146.
- `r_squared`: the lab's 44 points (SS total 1,802.4; least squares 0.638; bad line −0.604). Page
  prose and the noise-column run were recomputed (0.6384 → 0.6827).
- `roc_curve`: the lab's traced curve (t = 0.5 at (0.007, 0.602), t = 0.2 at (0.150, 0.886),
  AUC 0.945). Page prose was rewritten too.
- `multinomial_nb`: the lab's counts (−4.451 vs −8.030, posterior 0.973).
- `optics`: the lab's 22 points (it had 36), and `hdbscan`, which shares the data (MST at
  minPts = 4; λ-spans 2.12 vs 0.47).
- `early_stopping`: the lab's 40-feature run (validation minimum 4.89 at step 583, 5.47 at the end;
  patience 50 stops at 633). The page now follows the lab; the degree-9 true-risk run stays as the
  labelled code example.
- `policy_iteration`: −0.399 → −0.400.
- `value_function`: the figure showed a uniform-random policy that the lab never evaluates. It is
  redrawn from the lab's two fixed policies ("up, then right" +0.427 at the start, "right, then up"
  −0.794). The arithmetic of the old figure was right; it was just not this lab.
- `value_iteration`: checked against the current lab, and every number matches. No change.

The test was mutation-checked: one wrong number planted in a figure made it fail.

## Verification

- `./gradlew :app:testDebugUnitTest`: 402 tests, 0 failures. This includes `FigureShapeTest` over every
  figure and `AiFigureCoverageTest`, whose pilot count is now 309.
- The iOS export, which writes 488 topics with figures, and an iOS simulator build (`BUILD SUCCEEDED`).
- `./gradlew installDebug`, then an emulator spot check of the riskiest layouts: the 12-cell strip with
  an aux row (birch), the 6-column grid (categorical_nb), and a plot with an off-curve marker and a
  dashed baseline (deepfakes). All three render without clipping or wrapping.
