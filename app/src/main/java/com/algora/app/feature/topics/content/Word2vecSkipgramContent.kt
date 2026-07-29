package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val word2vecSkipgramContent = TopicContent(
    topicId = "word2vec_skipgram",
    whatIsIt = listOf(
        "Skip-gram inverts CBOW: instead of predicting the centre word from its context, it takes the centre word and predicts each surrounding word separately. One window of four neighbours becomes four training examples rather than one, so the lab's 102-token corpus yields 288 (centre, context) pairs. Each occurrence of a word therefore gets its own gradient instead of being averaged into a group, which is the mechanism behind skip-gram's reputation on rare words and small corpora.",
        "Training it exactly would require a softmax over the entire vocabulary for every pair, which is hopeless at |V| in the millions. Negative sampling replaces that with k+1 logistic decisions: pull the true context word's vector toward the centre's, push k noise words away. The noise distribution is unigram counts raised to the power 3/4 — the paper's one unexplained constant, and it matters: on the lab's corpus it takes \"the\" from 26.5% of draws down to 18.0% and lifts \"monarch\" from 2.0% to 2.5%. Cost per example falls from |V|·d to (k+1)·d — 416 to 96 multiply-adds here, and 300 million to 1,800 at a million-word vocabulary with 300 dimensions.",
        "What comes out is the result the paper is remembered for. Trained on the lab's twenty sentences, king − man + woman lands nearest \"queen\" at cosine 0.93, and king·queen is 0.99 against king·dog's 0.31 — relations end up as directions in the space, and nobody put them there. The ceiling is equally clear: a type gets exactly one vector, so \"bank\" carries the river and the money senses in the same 300 numbers however it was used. Removing that limitation is what ELMo and then BERT were built to do.",
    ),
    steps = listOf(
        StepCard(1, "Emit Pairs", "For each centre word, one (centre, context) pair per neighbour in the window.", 0xFF6366F1),
        StepCard(2, "Subsample Frequent Words", "Drop a token with probability 1 − √(t/f) — this also widens the effective window.", 0xFF8B5CF6),
        StepCard(3, "Score the Positive", "σ(v_c · u_o) for the real pair, pulled toward 1.", 0xFF3B82F6),
        StepCard(4, "Draw Negatives", "k words from unigram^0.75, each pushed toward 0.", 0xFF06B6D4),
        StepCard(5, "Update Both Matrices", "One SGD step per pair — no softmax, no normalisation constant.", 0xFF14B8A6),
        StepCard(6, "Check the Geometry", "Nearest neighbours and analogies, measured on the trained vectors.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "maximise Σ_t Σ_{−c ≤ j ≤ c, j≠0} log P(w_{t+j} | w_t)", "Each neighbour is its own prediction."),
        FormulaEntry("Negative sampling", "log σ(v_c·u_o) + Σ_{i=1..k} E_{w~Pn} log σ(−v_c·u_i)", "The softmax replaced by k+1 binary classifications."),
        FormulaEntry("Noise distribution", "Pn(w) ∝ count(w)^0.75", "\"the\" 26.5% → 18.0%, \"monarch\" 2.0% → 2.5% on the lab's corpus."),
        FormulaEntry("Training pairs", "102 tokens, window ±2 → 288 pairs", "2.8× the updates CBOW makes on the same text."),
        FormulaEntry("Cost per example", "(k+1)·d = 96 vs |V|·d = 416", "4.3× here; 166,000× at |V| = 1M, d = 300."),
        FormulaEntry("Analogy, measured", "king − man + woman → queen at 0.93", "Computed on the vectors the lab trains, not asserted."),
    ),
    notationKey = listOf(
        NotationEntry("v_c / u_o", "centre (input) and context (output) vectors — the two matrices SGNS trains"),
        NotationEntry("k", "negatives per positive: 5–20 for small corpora, 2–5 for large ones"),
        NotationEntry("subsampling threshold t", "typically 1e-5; the second frequency correction after the 3/4 power"),
        NotationEntry("SGNS", "skip-gram with negative sampling — the variant everyone actually trains"),
        NotationEntry("hierarchical softmax", "the alternative: a Huffman tree over the vocabulary, log₂|V| decisions"),
        NotationEntry("analogy task", "a:b :: c:? evaluated by nearest neighbour to b − a + c"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Skip-gram with negative sampling, minus the plumbing",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                # The 3/4 power is not cosmetic: it decides which words act as negatives.
                counts = np.array([corpus.count(w) for w in vocab], dtype=float)
                noise  = counts ** 0.75
                noise /= noise.sum()

                def train_pair(centre, context, k=5, lr=0.05):
                    v = W_in[centre]
                    grad = np.zeros_like(v)
                    targets = [(context, 1.0)] + [(np.random.choice(V, p=noise), 0.0) for _ in range(k)]
                    for word, label in targets:
                        p = 1 / (1 + np.exp(-v @ W_out[word]))
                        g = (label - p) * lr
                        grad        += g * W_out[word]
                        W_out[word] += g * v
                    W_in[centre] += grad          # one update per PAIR, not per window

                # Every (centre, context) pair in the corpus is one call:
                for sent in corpus:
                    for i, w in enumerate(sent):
                        for j in range(max(0, i - 2), min(len(sent), i + 3)):
                            if i != j:
                                train_pair(idx[w], idx[sent[j]])
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Reading the space back out",
            accentColor = 0xFFEC4899,
            code = """
                from gensim.models import Word2Vec

                model = Word2Vec(sentences, sg=1, negative=5, window=2, vector_size=16, epochs=300)
                wv = model.wv

                print(wv.similarity("king", "queen"))     # 0.99 on the lab corpus
                print(wv.similarity("king", "dog"))       # 0.31
                print(wv.most_similar(positive=["king", "woman"], negative=["man"])[0])
                # ('queen', 0.93)

                # Two cautions the analogy demo usually skips:
                #  1. most_similar EXCLUDES the three input words by default. Without that exclusion
                #     the nearest vector to king - man + woman is frequently "king" itself.
                #  2. The same geometry encodes the corpus's biases as directions -- doctor - man +
                #     woman lands on "nurse" in vectors trained on news text. Debiasing projects the
                #     gender direction out; it reduces the effect and does not remove it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF6366F1, "Query Expansion", "Nearest neighbours in the space give related terms without a thesaurus."),
        ApplicationCard("users", 0xFF8B5CF6, "Sequence Embeddings", "node2vec and item2vec are skip-gram over random walks and sessions — same objective, other data."),
        ApplicationCard("flask", 0xFF3B82F6, "Bioinformatics", "Protein and gene embeddings from sequence context, trained exactly this way."),
        ApplicationCard("help", 0xFFEC4899, "Bias Auditing", "The analogy trick that shows relations also exposes the corpus's stereotypes as directions."),
    ),
    takeaways = listOf(
        "One centre word predicts each neighbour separately: 288 training pairs from 102 tokens, 2.8× CBOW's updates.",
        "Negative sampling turns a |V|-way softmax into k+1 logistic decisions — 96 multiply-adds instead of 416 here.",
        "The noise distribution is unigram^0.75, which flattens \"the\" from 26.5% to 18.0% of draws and lifts rare words.",
        "On the lab's own trained vectors, king − man + woman → queen at 0.93 and king·dog is 0.31.",
        "One vector per type is the hard ceiling — senses collapse, and that is exactly what contextual models remove.",
    ),
    crossLinks = listOf(
        CrossLink("word2vec_cbow", "Word2Vec (CBOW)"),
        CrossLink("glove", "GloVe"),
        CrossLink("fasttext", "FastText"),
        CrossLink("elmo", "ELMo"),
    ),
)
