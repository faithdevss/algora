package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gloveContent = TopicContent(
    topicId = "glove",
    figure = Figure(
        caption = "GloVe's weighting function, f(x) = min(1, (x / x_max)^0.75), at the lab's x_max = 3, " +
            "with the co-occurrence values its toy corpus actually produces marked on it. Every " +
            "non-zero cell of X is one regression target — make wᵢ·w̃ⱼ plus two biases match " +
            "log Xᵢⱼ — and its squared error is multiplied by this curve. A zero count gets weight " +
            "zero, so empty cells cost nothing and training scales with the 24 cells that are " +
            "filled. A single adjacent pair, X(king, rules) = 1.00, gets 0.44; a pair half as " +
            "strong, two words apart, gets less; and from 3 upward the curve is flat, so the " +
            "handful of very frequent pairs involving \"the\" cannot own every gradient. The " +
            "exponent 0.75 keeps the growth sub-linear below the cap: 1.5 counts earn 0.59 of the " +
            "weight, not half of it.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "f(x), x_max = 3",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.0625f, 0.155f), FigurePoint(0.125f, 0.261f),
                        FigurePoint(0.25f, 0.439f), FigurePoint(0.375f, 0.595f), FigurePoint(0.5f, 0.738f),
                        FigurePoint(0.625f, 0.872f), FigurePoint(0.75f, 1f), FigurePoint(1f, 1f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.25f, 0.439f, "X = 1.00 → 0.44"),
                FigurePoint(0.375f, 0.595f, "1.50 → 0.59", FigureTone.Muted),
                FigurePoint(0.75f, 1f, "x_max = 3", FigureTone.Warn),
            ),
            xLabel = "co-occurrence Xᵢⱼ, 0 → 4",
            yLabel = "weight f(X), 0 to 1",
        ),
    ),
    whatIsIt = listOf(
        "GloVe — global vectors — begins from a complaint about word2vec: it learns from local windows one at a time and never uses the corpus-wide co-occurrence statistics directly, even though they were computed implicitly and thrown away. GloVe computes the co-occurrence matrix X once, where Xᵢⱼ counts how often j appears in i's window, and then fits vectors to it. There is no sliding window at training time and no sampling — the corpus was reduced to a matrix, and the matrix is the training set.",
        "The paper's insight is that meaning lives in *ratios* of co-occurrence probabilities, not in the probabilities themselves. Its own worked example: P(solid|ice)/P(solid|steam) = 8.9 and P(gas|ice)/P(gas|steam) = 0.085 (the paper's Table 1), while words related to both or to neither sit near 1 — water at 1.36, fashion at 0.96. The raw probabilities for solid and gas are both tiny and say nothing; the ratio discriminates cleanly. Working backwards from \"the model's output should be that ratio\" gives the log-bilinear form wᵢ·w̃ⱼ + bᵢ + b̃ⱼ = log Xᵢⱼ, which is a weighted least-squares problem rather than a classification one.",
        "The weighting f(X) = (X/x_max)^0.75, capped at 1, is what makes it work in practice: rare pairs are noisy and shouldn't dominate, and the handful of enormous \"the\" counts would otherwise own every gradient. The lab shows the whole pipeline on a toy corpus — four sentences, 16 tokens, 8 word types, a window of 2 with 1/distance weighting, so a neighbour one word away adds 1 and two words away adds ½. That leaves 24 non-zero cells; f uses x_max = 3, so X(king, rules) = 1.00 gets weight 0.44 while any pair counted 3 or more gets the full 1. Zero cells contribute nothing at all. Fitted by SGD for 400 epochs on 4-dimensional vectors, the loss falls from 1.3e−1 to 1.0e−5 — the paper uses x_max = 100 and billions of tokens, and twenty words are far too few to rank GloVe against word2vec.",
    ),
    steps = listOf(
        StepCard(1, "Count Co-occurrences", "One sweep with a ±5 to ±10 window, incrementing by 1/distance so near neighbours weigh more.", 0xFF8B5CF6),
        StepCard(2, "Keep Only Non-Zeros", "The matrix is mostly empty — 71% here — and only filled cells are ever visited.", 0xFF6366F1),
        StepCard(3, "Take Logs", "The target is log Xᵢⱼ, which turns ratios of probabilities into differences of dot products.", 0xFF3B82F6),
        StepCard(4, "Weight Each Cell", "f(X) = (X/x_max)^0.75 capped at 1 — rare pairs damped, frequent pairs capped.", 0xFF06B6D4),
        StepCard(5, "Fit by AdaGrad", "Least squares over the non-zero entries; embarrassingly parallel, deterministic.", 0xFF14B8A6),
        StepCard(6, "Sum the Two Matrices", "w + w̃ is the released vector — the paper measures that it beats either alone.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "J = Σᵢⱼ f(Xᵢⱼ)(wᵢ·w̃ⱼ + bᵢ + b̃ⱼ − log Xᵢⱼ)²", "Weighted least squares on log counts."),
        FormulaEntry("The ratio argument", "P(solid|ice)/P(solid|steam) = 8.9; gas → 0.085", "From the paper's Table 1; water 1.36 and fashion 0.96 sit at ≈1."),
        FormulaEntry("Weighting", "f(x) = min(1, (x/x_max)^0.75)", "x_max = 3 in the lab: X(king, rules) = 1.00 → 0.44; the paper uses 100."),
        FormulaEntry("Sparsity", "24 non-zero cells", "The lab's 8-word, 16-token corpus; real matrices are far sparser."),
        FormulaEntry("Cost", "O(non-zeros), not O(|V|²) or O(corpus)", "The matrix is built once and reused for every training run."),
        FormulaEntry("Fitted result", "loss 1.3e−1 → 1.0e−5", "4-d vectors, 400 epochs of SGD over the non-zero cells."),
    ),
    notationKey = listOf(
        NotationEntry("Xᵢⱼ", "how often word j occurs in word i's window, distance-weighted"),
        NotationEntry("w / w̃", "the two vector sets — target and context; the release sums them"),
        NotationEntry("bᵢ / b̃ⱼ", "bias terms that absorb each word's overall frequency"),
        NotationEntry("x_max", "the count above which the weight saturates; 3 in the lab, 100 in the paper"),
        NotationEntry("log-bilinear", "the model form the ratio requirement forces: a dot product equal to a log count"),
        NotationEntry("count-based vs predictive", "the distinction GloVe was written to collapse — it is both"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Counts, then a least-squares fit",
            accentColor = 0xFF8B5CF6,
            code = """
                from collections import defaultdict
                import numpy as np

                X = defaultdict(float)
                for sent in corpus:
                    toks = sent.split()
                    for i, w in enumerate(toks):
                        for j in range(max(0, i - 5), min(len(toks), i + 6)):
                            if i != j:
                                X[(idx[w], idx[toks[j]])] += 1.0 / abs(i - j)   # distance weighting

                # the paper uses xmax=100; the quoted weights (0.62, 0.08) imply xmax=10
                f = lambda x, xmax=10, a=0.75: (x / xmax) ** a if x < xmax else 1.0

                def epoch(W, Wt, b, bt, lr=0.05):
                    for (i, j), x in X.items():                 # ONLY non-zero cells
                        diff = W[i] @ Wt[j] + b[i] + bt[j] - np.log(x)
                        g = f(x) * diff * lr
                        W[i], Wt[j] = W[i] - g * Wt[j], Wt[j] - g * W[i]
                        b[i] -= g
                        bt[j] -= g

                vectors = W + Wt        # the paper's final step, worth ~1% on the analogy task
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the ratio, not the probability",
            accentColor = 0xFF14B8A6,
            code = """
                # The paper's Table 1 (probabilities from a 6B-token corpus). Its printed ratios are
                # solid 8.9, gas 8.5e-2, water 1.36, fashion 0.96; recomputing from the rounded
                # probabilities below gives slightly different values:
                p_ice   = {"solid": 1.9e-4, "gas": 6.6e-5, "water": 3.0e-3, "fashion": 1.7e-5}
                p_steam = {"solid": 2.2e-5, "gas": 7.8e-4, "water": 2.2e-3, "fashion": 1.8e-5}

                for w in p_ice:
                    print(w, round(p_ice[w] / p_steam[w], 2))
                # solid 8.64   gas 0.08   water 1.36   fashion 0.94
                #
                # Read the raw probabilities and solid (1.9e-4) is only 11x fashion (1.7e-5) next to
                # ice -- not a clean signal. Read the ratios and solid is ~9, fashion ~1: the ratio
                # cancels whatever makes a word common in general, leaving only what makes it
                # specific to one of the two.
                #
                # Requiring F(w_i - w_j, w~_k) = P_ik / P_jk, plus symmetry between the two roles a
                # word plays, forces F = exp and hence w_i . w~_k = log P_ik. The objective is that
                # identity, made least-squares and weighted.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF8B5CF6, "Pretrained Vectors", "The 6B/42B/840B-token releases were the default embedding initialiser for years."),
        ApplicationCard("chip", 0xFF6366F1, "Reproducible Pipelines", "No sampling means the same corpus gives the same vectors — useful when a result must be defended."),
        ApplicationCard("chart", 0xFF3B82F6, "Corpus Analysis", "The co-occurrence matrix is a reusable artefact in its own right, independent of the vectors."),
        ApplicationCard("flask", 0xFF14B8A6, "Specialised Domains", "Counting is cheap and parallel, so a domain corpus can be re-fitted repeatedly at different dimensions."),
    ),
    takeaways = listOf(
        "GloVe fits vectors to a co-occurrence matrix computed once, rather than to sampled windows.",
        "Its derivation starts from ratios: P(solid|ice)/P(solid|steam) = 8.9 vs 0.085 for gas, with unrelated words at ≈1.",
        "f(X) = (X/x_max)^0.75 damps rare pairs and caps frequent ones — 0.44 for X(king, rules) = 1.00 at the lab's x_max = 3.",
        "Training touches only non-zero cells (24 in the lab's toy corpus), which makes it parallel and deterministic.",
        "On the lab's 16 tokens the fit drives the loss from 1.3e−1 to 1.0e−5 — far too small a corpus to rank GloVe against skip-gram.",
    ),
    crossLinks = listOf(
        CrossLink("word2vec_skipgram", "Word2Vec (Skip-Gram)"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("pca", "Principal Component Analysis"),
        CrossLink("word_embeddings", "Word Embeddings"),
    ),
)
