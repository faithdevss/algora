# Phase 5 — AI Mode

Status: In progress
Depends on: Phase 3 (reuses sim widgets)

## Goal
Add the second app mode (AI: ML/DL/NLP/RL) alongside DSA mode, sharing the same topic template and nav pattern.

## Rough scope
- DSA/AI mode switch
- AI bottom nav: Machine Learning · Deep Learning · Home · NLP · Reinforcement Learning
- Topic content for ML, DL, NLP, RL taxonomies (see `docs/features.md` §2.4-2.7) via the same 7-section template
- Reuse Phase 3 sim widgets where applicable (e.g. gate solver → perceptron, slider explorer → regression)
- Cross-links between DSA and AI topics (e.g. Graph traversal ↔ MCTS, Gradient Descent ↔ backprop)

## Detail plan (this session)

### Mode infrastructure
- `AppMode` enum (DSA, AI) hoisted in `AlgoraApp` (MainActivity). Bottom bar swaps `dsaTabs` ⇄ `aiTabs`.
- `Screen` gains `MachineLearning`, `DeepLearning`, `Nlp`, `ReinforcementLearning` (short labels ML/DL/NLP/RL to fit a 5-tab bar). `aiTabs = [ML, DL, Home, NLP, RL]`.
- `NavGraph` registers the four AI category routes and passes `mode`/`onModeChange` into Home; `TopicDetailScreen` gains `onTopicClick` for cross-links.

### Home dashboard (now shared by both modes — hosts the switch)
Ported from the mock's `isHome` block: gradient topbar (DSA gradient / AI pink→orange gradient) with the DSA / AI Simulation segmented switch, a Featured Lab card (DSA → Singly Linked List, AI → Linear Regression), and a 2×2 Quick Access card grid that deep-links into the four category tabs of the active mode.

### AI content (mirrors DSA feature packages)
`feature/{machinelearning,deeplearning,nlp,reinforcementlearning}/` each with `*Categories`, `*Topics`, `*Screen`. Categories = the mock's `cats()` sub-sections; topic rows + free/premium flags taken verbatim from the mock. `TopicRegistry` aggregates all AI topics so any id resolves.

### Simulations (Phase 3 widgets, built here since deferred earlier)
- `RegressionSimulationSection` — full port of the mock's regression lab (slope/intercept sliders, scatter + best-fit line on Canvas, live MSE, New Data / Best Fit buttons via least-squares).
- `PerceptronSimulationSection` — logic-gate solver (AND/OR/XOR), adjustable weights + bias, live truth-table classification, decision-boundary Canvas, XOR-not-linearly-separable note.

### Flagship content
`linear_regression` (verbatim from the mock's `regression` topic) and `perceptron` (authored) get full 7-section content + their sims. Remaining AI topics ship as browser rows → ComingSoon, matching the Phase 2/3/4 "flagship few, rest deferred" precedent.

### Cross-links
`TopicContent.crossLinks: List<CrossLink>` (optional). Rendered as tappable chips under Key Takeaways. Seeded: Linear Regression ↔ The Perceptron, Graph → Monte Carlo Tree Search.

## Deferred
Full 7-section content for the remaining AI topics; perceptron multi-layer / backprop sim; per-mode theming of the whole shell (only the Home topbar gradient switches today).

> **Status update (2026-07-27).** Content is complete for all 87 AI topics, and the multi-layer /
> backprop sim shipped in Phase 3 sub-phase N as `NeuralNetPlayer` — `neural_network_basics` runs a
> real 2-3-1 forward pass and `backpropagation` runs the chain rule back through it. Per-mode
> theming is the only Phase 5 item still open: `AppMode` reaches the nav graph, Home and Progress,
> but `Theme.kt` still builds one scheme, so the shell does not recolour with the mode.

Progress: the whole Machine Learning family is now authored — Linear Regression, Logistic
Regression, Decision Trees, SVM, k-NN, Naive Bayes, K-Means, Hierarchical Clustering, PCA, DBSCAN
(plus the Perceptron). Content follows the same `content/<Topic>Content.kt` + `TopicContentProvider`
pattern as the DSA topics, but uses ML math notation, Python (scikit-learn) code blocks, and the AI
indigo/blue accent palette, with cross-links back into DSA where relevant (k-NN → K-D Tree, Decision
Trees → Tree, etc.). The Deep Learning family is now authored too — Neural Network Basics,
Backpropagation, Activation Functions, Gradient Descent Variants, CNNs, RNNs, LSTMs/GRUs,
Autoencoders, GANs, Transformers (PyTorch code blocks). Note: the `transformers` id is shared by the
Deep Learning and NLP taxonomies, so its single content entry serves both browser rows. The NLP
family is now authored too — Tokenization, Stemming, Lemmatization, Bag-of-Words/TF-IDF, Word
Embeddings, RNN/LSTM, Attention, LLMs (plus the shared Transformers entry).

Machine Learning, Deep Learning, and NLP are all fully authored and verified registered in
`TopicContentProvider`. Reinforcement Learning is now in progress: the value-based family is done —
MDP, Q-Learning, DQN, Experience Replay, Target Networks, Double DQN, Dueling DQN, Prioritized
Replay, Noisy Nets, C51 (Distributional), Rainbow DQN. The policy-gradient / actor-critic family is
done too — REINFORCE, Actor-Critic, A2C, A3C, GAE, TRPO, PPO, DPG, DDPG, TD3, SAC, Maximum Entropy
RL. The model-based family is done too — Dyna-Q, MCTS, AlphaGo, AlphaZero, MuZero, World Models,
Dreamer, MBPO (this also resolves the Graph → Monte Carlo Tree Search cross-link seeded in the
vertical slice). The exploration family is done too — Epsilon-Greedy, UCB, Thompson Sampling,
Boltzmann Exploration, Intrinsic Motivation, ICM, RND. The multi-agent family is done too — Minimax,
Independent Q-Learning, VDN, QMIX, MADDPG, Self-Play. The imitation / offline family is done too —
Imitation Learning, IRL, GAIL, Offline RL, CQL, Decision Transformer, Meta-RL, RLHF (RLHF resolves
the PPO → RLHF and LLMs → RLHF cross-links). The benchmark environments are done too — Grid World,
CartPole, Mountain Car, Atari, MuJoCo, StarCraft II, Dota 2.

**All AI-mode topic content is now authored: every Machine Learning, Deep Learning, NLP, and
Reinforcement Learning topic across all four taxonomies has full 7-section content, verified
registered in `TopicContentProvider` (87 AI rows).** Nothing in the AI browser falls through to
ComingSoon. The remaining Phase 5 items are non-content: the perceptron multi-layer / backprop sim,
and per-mode theming of the whole shell. (The first of those is now done — see the status update
above; per-mode theming remains.)
