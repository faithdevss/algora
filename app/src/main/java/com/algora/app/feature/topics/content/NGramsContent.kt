package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val nGramsContent = TopicContent(
    topicId = "n_grams",
    figure = Figure(
        caption = "The same eight sentences counted four times, and the wall arriving. Padded, " +
            "the corpus is 11 unigram types over 45 occurrences — every type seen four times on " +
            "average, which is enough to divide with. One token of context later there are 19 " +
            "bigram types over 37 occurrences and 47% of them were seen exactly once; at n = 3 it " +
            "is 22 over 29 with 77% singletons, and at n = 4, 20 types over 21 occurrences with " +
            "19 of the 20 seen once. The bar is that ratio, and it is heading for 1.0, where " +
            "every count is 1 and the maximum-likelihood estimate for every context is a single " +
            "observation. The table grows as |V|ⁿ — 11, 121, 1,331, 14,641 possible cells here — " +
            "while the evidence per cell collapses, which is why add-k has an optimum (5.20 at " +
            "k = 1, 3.42 at 0.1, 4.34 at 0.01) rather than a safe default, and why a neural " +
            "language model that shares strength across similar contexts exists at all.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("n = 1", 0.244f),
                FigureBar("n = 2", 0.514f),
                FigureBar("n = 3", 0.759f, FigureTone.Warn),
                FigureBar("n = 4", 0.952f, FigureTone.Warn),
            ),
            xLabel = "11 / 45 · 19 / 37 · 22 / 29 · 20 / 21",
            yLabel = "distinct n-grams per occurrence",
        ),
    ),
    whatIsIt = listOf(
        "An n-gram is a contiguous window of n tokens. Slid across a sentence one position at a time, the windows become features that a bag of words cannot express — \"not good\" and \"good\" are different bigrams, so a bigram model can represent negation that a unigram model provably cannot. Counting them is also the whole of training an n-gram language model: P(w₂|w₁) = count(w₁w₂) / count(w₁), one division, no gradient descent anywhere.",
        "The model that falls out is a Markov chain of order n−1: the probability of the next word depends on the previous n−1 words and nothing before them. Sentences are padded with <s> and </s> markers, which are not decoration — without them there is nothing to condition the first word on, no way to end a sentence, and the probabilities do not sum to 1. The standard evaluation is perplexity, exp(−(1/N)Σ ln p), read as the average number of words the model was choosing between: lower is better, and a uniform model over a vocabulary of V has perplexity exactly V.",
        "Two facts kill the pure count model, and both are visible on a corpus of eight sentences. First, any unseen n-gram has probability zero, so a single novel bigram makes the whole sentence probability zero and perplexity infinite — the model cannot rank two sentences it has not seen. Add-k smoothing patches this by moving mass to the unseen: on the lab's held-out sentence, k = 1 gives perplexity 5.20, k = 0.1 gives 3.42 and k = 0.01 gives 4.34, so k has an optimum and is a hyperparameter, not a safety switch. Second, sparsity gets worse with n: at n = 4 the lab's corpus has 20 distinct 4-grams over 21 occurrences, so nearly every one was seen once. The table grows as |V|ⁿ while evidence per cell collapses, which is the wall neural language models were built to climb.",
    ),
    steps = listOf(
        StepCard(1, "Pad the Sentence", "<s> and </s> so the first and last positions have a context and the model is a distribution.", 0xFF14B8A6),
        StepCard(2, "Slide the Window", "Every n consecutive tokens, one step at a time — a sentence of L tokens yields L−n+1 n-grams.", 0xFF06B6D4),
        StepCard(3, "Count", "A hash map from n-gram to frequency. This is the entire training procedure.", 0xFF3B82F6),
        StepCard(4, "Normalise", "P(wₙ|w₁…wₙ₋₁) = count(full) / count(prefix) — the maximum-likelihood estimate.", 0xFF6366F1),
        StepCard(5, "Smooth", "Move mass to the unseen: add-k, then Good-Turing or Kneser-Ney for anything real.", 0xFF8B5CF6),
        StepCard(6, "Evaluate on Held-Out Text", "Perplexity on data the counts never saw — the only number that says whether n and k were right.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Chain rule + Markov", "P(w₁…w_L) ≈ Π P(wᵢ | wᵢ₋ₙ₊₁…wᵢ₋₁)", "The approximation that makes counting tractable."),
        FormulaEntry("MLE", "P(w₂|w₁) = c(w₁w₂) / c(w₁)", "P(like|i) = 0.60 and P(love|i) = 0.40 on the lab's corpus; every other word gets 0."),
        FormulaEntry("Perplexity", "PP = exp(−(1/N) Σ ln p(wᵢ | context))", "Average branching factor; one zero probability makes it infinite."),
        FormulaEntry("Add-k smoothing", "(c(w₁w₂) + k) / (c(w₁) + k·|V|)", "k=1 → PP 5.20, k=0.1 → 3.42, k=0.01 → 4.34 on the held-out sentence."),
        FormulaEntry("The cost of smoothing", "seen sentence: MLE 1.86 → add-1 4.51", "Mass given to the unseen is taken from the seen."),
        FormulaEntry("Sparsity", "|V|ⁿ possible, O(N) observed", "n=4 on this corpus: 20 types over 21 tokens — almost everything seen once."),
    ),
    notationKey = listOf(
        NotationEntry("n-gram", "a contiguous window of n tokens; unigram, bigram, trigram for n = 1, 2, 3"),
        NotationEntry("<s> / </s>", "sentence boundary markers, required for the model to be a proper distribution"),
        NotationEntry("MLE", "maximum-likelihood estimate — counts divided by counts, and zero where unseen"),
        NotationEntry("|V|", "vocabulary size; add-k spreads mass over all |V| continuations"),
        NotationEntry("perplexity", "exp of average negative log probability; equals |V| for a uniform model"),
        NotationEntry("backoff / interpolation", "use the (n−1)-gram estimate when the n-gram is unseen — what real models do instead of add-k"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole model: count, divide, score",
            accentColor = 0xFF14B8A6,
            code = """
                from collections import Counter
                import math

                corpus = ["i like nlp", "i like deep learning", "i love nlp", "i like machine learning",
                          "deep learning is fun", "machine learning is fun", "nlp is fun",
                          "i love machine learning"]

                pad = lambda s: ["<s>"] + s.split() + ["</s>"]
                uni = Counter(w for s in corpus for w in pad(s))
                bi  = Counter(t for s in corpus for t in zip(pad(s), pad(s)[1:]))
                V   = len(uni)                                  # 11

                mle = lambda a, b: bi[(a, b)] / uni[a]
                print(mle("i", "like"), mle("i", "love"))       # 0.6 0.4
                print(mle("love", "deep"))                       # 0.0  <- never seen

                def perplexity(sentence, k=0.0):
                    toks = pad(sentence)
                    logs = []
                    for a, b in zip(toks, toks[1:]):
                        p = (bi[(a, b)] + k) / (uni[a] + k * V) if k else mle(a, b)
                        if p == 0:
                            return float("inf")
                        logs.append(math.log(p))
                    return math.exp(-sum(logs) / len(logs))

                print(perplexity("i love deep learning"))         # inf   -- one unseen bigram
                print(perplexity("i love deep learning", k=1.0))  # 5.20
                print(perplexity("i love deep learning", k=0.1))  # 3.42  <- best here
                print(perplexity("i love deep learning", k=0.01)) # 4.34  <- too little mass
            """.trimIndent(),
        ),
        CodeBlock(
            title = "N-grams as features, and where the wall is",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.feature_extraction.text import TfidfVectorizer

                # Unigrams cannot represent negation; bigrams can. This one argument is why
                # ngram_range is the first thing to tune on a text classifier.
                vec = TfidfVectorizer(ngram_range=(1, 2))
                X = vec.fit_transform(["the movie was not good", "the movie was good"])
                print("not good" in vec.get_feature_names_out())   # True

                # The cost: vocabulary growth. On real corpora the feature count multiplies by
                # 10-50x going from (1,1) to (1,2), which is what HashingVectorizer and min_df exist
                # to control.
                #
                # And the wall: with n=4 on the lab corpus, 20 distinct 4-grams cover 21 occurrences.
                # Every extra token of context multiplies the table by |V| and divides the evidence
                # per cell -- so counting stops working long before the context is long enough.
                # A neural LM shares statistical strength across similar contexts instead of
                # counting each one separately, which is precisely the fix.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF14B8A6, "Text Classification Features", "Bigrams and trigrams over TF-IDF remain a strong, cheap baseline — and can express negation."),
        ApplicationCard("search", 0xFF3B82F6, "Autocomplete & Spelling", "Query completion and correction ranked by n-gram probability, still deployed at scale."),
        ApplicationCard("music", 0xFF8B5CF6, "Speech Recognition & MT", "N-gram LMs rescored ASR and translation hypotheses for two decades before neural LMs."),
        ApplicationCard("chart", 0xFFEC4899, "Evaluation Metrics", "BLEU and ROUGE are n-gram overlap counts — the same windows, used to score output."),
    ),
    takeaways = listOf(
        "An n-gram model is a count table plus one division; the Markov assumption is what makes it tractable.",
        "Padding with <s>/</s> is required, not cosmetic — without it the model is not a distribution.",
        "One unseen bigram sends perplexity to infinity, which is why smoothing is mandatory rather than optional.",
        "Add-k has an optimum: 5.20 at k=1, 3.42 at k=0.1, 4.34 at k=0.01 on the lab's held-out sentence.",
        "Sparsity beats you as n grows — 20 distinct 4-grams over 21 occurrences here — and that is why neural LMs exist.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("hmm", "Hidden Markov Models"),
        CrossLink("bpe", "Byte-Pair Encoding"),
        CrossLink("llms", "LLMs"),
    ),
)
