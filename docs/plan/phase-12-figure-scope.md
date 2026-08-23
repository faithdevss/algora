# Phase 12 — which of the remaining AI topics get a figure

Companion to `phase-12-ai-figures.md`, which records what shipped. This one is the decision about
what is left: **108 of 325 AI topics carry a figure; of the 217 that do not, 143 should get one,
58 should not get one yet, and 16 should never get one.** Batches A18 onward build the 143 in the
order below.

## Why this list exists

A16 and A17 were built on the assumption that every AI topic eventually gets a figure. That
assumption does not survive contact with the pages. Measuring the prose length of all 217
remaining topics produces a split with almost nothing in the middle: 148 pages carry 1,100–2,600
characters of prose with six formulas and a worked code block, and 69 carry 270–560 characters
with three formulas and a snippet. The second group is not a smaller version of the first — it is
the taxonomy-completion bar from Phase 9 rather than the rewritten bar, and drawing a figure above
300 characters of prose makes the figure the page.

That matters because of the rule the pilot established: **the figure follows the page's measured
numbers.** A page that states no numbers gives a figure nothing to be honest about, so the figure
would have to introduce its own — teaching material the prose never claims, above prose that
cannot support it. The fix for those pages is content, not a picture, and the figure is cheap once
the content exists.

## The three tiers

| Tier | Count | Rule |
|---|---|---|
| **A — build** | 143 | The page states a mechanism that is a quantity against a scale, a structure, a matrix or a sequence, and carries enough prose that the figure supports it rather than replaces it. |
| **B — content first** | 58 | The mechanism is real but the page is at the Phase 9 bar. Revisit after a content pass; the figure is 30 minutes' work once there is something to draw from. |
| **C — never** | 16 | Nothing to draw that a neighbouring figure does not already say, or the page describes an environment, a product or a roster rather than a mechanism. |

## Tier A — the build order

Batches are 4–6 topics, grouped so each batch is one argument and the figures inside it have to
differ from each other by construction. Sections in front are the ones where a figure carries the
most: a metric is a quantity against a threshold, which is the case the `Plot` shape exists for.

**A18 — ML metrics I — the classification metrics** (6)  
`f1_score`, `roc_curve`, `auc`, `log_loss`, `cohens_kappa`, `mae`

**A19 — ML metrics II — regression and clustering scores** (5)  
`r_squared`, `adjusted_r_squared`, `gini_impurity`, `silhouette_score`, `davies_bouldin`

**A20 — Naive Bayes family — one likelihood shape each** (5)  
`gaussian_nb`, `multinomial_nb`, `bernoulli_nb`, `complement_nb`, `categorical_nb`

**A21 — ML preprocessing — the failure each method has** (5)  
`outlier_detection`, `z_score_standardization`, `smote`, `chi_square_selection`, `rfe`

**A22 — ML foundations left over** (6)  
`bias_variance`, `bayesian_networks`, `mcmc`, `restricted_boltzmann_machines`, `deep_belief_networks`, `sarsa`

**A23 — Clustering — the rest of the block** (5)  
`k_modes`, `hdbscan`, `optics`, `birch`, `affinity_propagation`

**A24 — Association rules + the two PCA variants** (4)  
`eclat`, `fp_growth`, `kernel_pca`, `incremental_pca`

**A25 — Time series** (5)  
`exponential_smoothing`, `autoregression`, `arima`, `sarima`, `prophet`

**A26 — DL gradients and the three activations that differ** (5)  
`vanishing_gradient`, `exploding_gradient`, `gelu`, `selu`, `swish`

**A27 — CNN architectures I — what each one changed** (4)  
`resnet`, `inception`, `mobilenet`, `vgg`

**A28 — CNN architectures II** (5)  
`efficientnet`, `vit`, `alexnet`, `densenet`, `padding_strides`

**A29 — Detection I — the R-CNN timing story** (4)  
`rcnn`, `fast_rcnn`, `faster_rcnn`, `yolo`

**A30 — Detection II** (4)  
`ssd`, `retinanet`, `unet`, `mask_rcnn`

**A31 — Recurrent and sequence-to-sequence** (4)  
`bptt`, `bidirectional_rnn`, `encoder_decoder`, `seq2seq`

**A32 — Transformer variants** (6)  
`gpt`, `t5`, `roberta`, `distilbert`, `hf_tokenizers`, `feed_forward`

**A33 — Optimizers and normalization** (4)  
`lr_schedulers`, `kl_divergence`, `layer_normalization`, `group_normalization`

**A34 — Specialized architectures** (5)  
`gcn`, `gat`, `capsule_networks`, `neural_odes`, `kan`

**A35 — Generative models** (5)  
`dcgan`, `cyclegan`, `stylegan`, `stable_diffusion`, `deepfakes`

**A36 — NLP preprocessing and statistical** (6)  
`regex_nlp`, `n_grams`, `cosine_similarity`, `jaccard_similarity`, `hmm`, `pcfg`

**A37 — NLP syntax** (5)  
`chunking`, `dependency_parsing`, `constituency_parsing`, `coreference`, `sentiment_lexicon`

**A38 — Embeddings** (5)  
`word2vec_cbow`, `word2vec_skipgram`, `glove`, `fasttext`, `elmo`

**A39 — Pretrained families** (5)  
`bart`, `xlnet`, `gpt3_gpt4`, `llama_vicuna`, `mistral_mixtral`

**A40 — Modern LLM patterns** (6)  
`chain_of_thought`, `tree_of_thoughts`, `vector_databases`, `react`, `ai_agents`, `hallucination_mitigation`

**A41 — Fine-tuning and efficiency** (6)  
`fine_tuning_full`, `dpo`, `peft`, `lora_qlora`, `quantization`, `flash_attention`

**A42 — Beyond the transformer** (4)  
`ssm`, `mamba`, `rwkv`, `long_context`

**A43 — NLP evaluation metrics** (4)  
`bleu`, `rouge`, `meteor`, `mmlu`

**A44 — RL — the tabular block that has real pages** (5)  
`dynamic_programming`, `policy_iteration`, `value_iteration`, `monte_carlo_rl`, `td_learning`

**A45 — RL foundations that have real pages** (5)  
`value_function`, `q_function`, `discount_factor`, `exploration_exploitation`, `pomdp`

**A46 — RL algorithms whose pages state an exact mechanism** (5)  
`ppo`, `actor_critic`, `prioritized_replay`, `ddpg`, `sac`

## Tier B — content first, then a figure

These 58 pages are stubs: two or three sentences, three formulas, a short snippet. Forty-two of
them are RL, which is the section Phase 9 finished last and at the lowest depth — the DQN,
policy-gradient, model-based, exploration, MARL and advanced blocks are almost entirely at this
bar. Every one of them has a mechanism worth drawing (a clipped ratio, a target-network delay, a
replay priority, a tree search); none of them has a page that could carry the drawing today.

**RL** (42) — `a2c`, `a3c`, `alphago`, `alphazero`, `boltzmann_exploration`, `c51`, `cql`, `decision_transformer`, `double_dqn`, `dpg`, `dqn`, `dreamer`, `dueling_dqn`, `dyna_q`, `epsilon_greedy`, `experience_replay`, `gae`, `gail`, `icm`, `imitation_learning`, `intrinsic_motivation`, `iql`, `irl`, `maddpg`, `max_entropy_rl`, `mbpo`, `mcts`, `meta_rl`, `minimax`, `muzero`, `noisy_nets`, `offline_rl`, `qmix`, `rainbow_dqn`, `reinforce`, `rnd`, `self_play`, `target_networks`, `td3`, `trpo`, `vdn`, `world_models`

**ML** (6) — `autoencoders`, `model_evaluation`, `naive_bayes`, `regularization`, `thompson_sampling`, `ucb`

**NLP** (6) — `bow_tfidf`, `bpe`, `lemmatization`, `ner`, `rag`, `rlhf`

**DL** (4) — `diffusion_models`, `lstm_gru`, `neural_network_basics`, `rnn`

## Tier C — no figure

| Topic | Why not |
|---|---|
| `atari` | environment description, not a mechanism |
| `cartpole` | environment description, not a mechanism |
| `claude_gemini` | product comparison; any figure is a table that ages |
| `dota2` | environment description, not a mechanism |
| `elu` | curve already legible on activation_functions (A2) |
| `grid_world` | environment description, not a mechanism |
| `hierarchical_divisive` | same dendrogram as hierarchical_clustering (A16), built downward |
| `lenet5` | a parameter table; alexnet carries the era's figure |
| `llms` | roster page — the mechanism lives on transformers/gpt |
| `mountain_car` | environment description, not a mechanism |
| `mujoco` | environment description, not a mechanism |
| `prelu` | curve already legible on activation_functions (A2) |
| `rnn_lstm` | duplicate of rnn + lstm_gru |
| `starcraft` | environment description, not a mechanism |
| `tanh` | curve already legible on activation_functions (A2) |
| `word_embeddings` | duplicate of the word2vec pair |

The three activations and the divisive-clustering entry are the pruning that matters most for
what comes next: six more activation curves and a second dendrogram would each be a figure that
says what the reader already saw two topics ago. A figure earns its place against its neighbours,
not only against its own page.

## What would change this list

- **A content pass on RL.** It moves 42 topics from B to A and is the single largest change
  available. Worth doing on its own merits — those pages are the thinnest in the app.
- **A batch that turns out duplicative in practice.** The Naive Bayes family (A20) and the CNN
  architectures (A27–A28) are the two at risk: if three of five figures come out saying the same
  thing, the honest move is to cut the batch rather than ship near-duplicates.
- **Emulator evidence.** The outstanding walk-through in `phase-12-ai-figures.md` could still
  demote a shape. `knn`'s five labelled edges and the eleven-node dendrogram are the two cells
  most likely to fail it.
