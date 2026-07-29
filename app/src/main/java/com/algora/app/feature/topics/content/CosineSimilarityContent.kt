package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val cosineSimilarityContent = TopicContent(
    topicId = "cosine_similarity",
    whatIsIt = listOf(
        "Cosine similarity measures the angle between two vectors rather than the distance between them: cos θ = (a · b) / (‖a‖‖b‖). In a term space — one dimension per vocabulary entry, a document as its vector of weights — that means it compares the *mix* of terms and ignores how much text there is. It is the default similarity in information retrieval for that one reason, and it is the similarity every embedding model is trained and evaluated with.",
        "The lab makes the argument concrete. \"long\" is \"short\" with every sentence written twice: identical topic mix, double the counts. Euclidean distance calls them 3.16 apart — as far apart as \"short\" is from the origin — while cosine gives exactly 1.00, because scaling a vector multiplies both the dot product and the norm and the ratio is unchanged. Rank a query against the corpus by each metric and the orders differ: by cosine \"long\" is tied for first, by Euclidean distance it is last, purely for being long. A retrieval system that ranked by distance would bury the best match.",
        "Two properties are worth internalising. With non-negative counts every angle lies between 0° and 90°, so cosine over raw term counts is bounded to [0, 1]; embeddings have negative components and genuinely use [−1, 1]. And once vectors are L2-normalised, cosine *is* the dot product — one multiply-add per dimension, no square roots at query time — which is why vector databases store normalised embeddings and run inner-product search. The cost of the invariance is that length is sometimes the signal: a three-word note and a three-thousand-word report on the same terms are identical to cosine. On raw counts it also over-rewards frequent words, which is why the pairing is always TF-IDF weights *then* cosine, never counts then cosine.",
    ),
    steps = listOf(
        StepCard(1, "Vectorise", "One dimension per term, weighted by TF-IDF (or an embedding model, which does this implicitly).", 0xFF8B5CF6),
        StepCard(2, "Take the Dot Product", "Σ aᵢbᵢ — only terms present in both contribute, so sparse vectors are cheap.", 0xFF6366F1),
        StepCard(3, "Divide by the Norms", "‖a‖‖b‖ is what removes length; without it you have an unbounded overlap score.", 0xFF3B82F6),
        StepCard(4, "Read the Angle", "1.0 is the same direction, 0.0 is orthogonal — no terms in common for count vectors.", 0xFF06B6D4),
        StepCard(5, "Normalise Once, Reuse", "Pre-normalise the corpus and every query becomes a dot product.", 0xFF14B8A6),
        StepCard(6, "Check Length Isn't the Signal", "If document length carries meaning for your task, this is the wrong metric.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Cosine similarity", "cos θ = (a · b) / (‖a‖‖b‖)", "Bounded [0,1] for non-negative counts, [−1,1] for embeddings."),
        FormulaEntry("Scale invariance", "cos(a, ca) = 1 for any c > 0", "Measured: the lab's doubled document scores 1.00, at Euclidean distance 3.16."),
        FormulaEntry("Angle", "θ = arccos(0.60) = 53.1°", "The lab's opposite-emphasis pair, in degrees rather than as a ratio."),
        FormulaEntry("Normalised form", "cos(a, b) = â · b̂ where â = a/‖a‖", "Why vector DBs store unit vectors and use inner product."),
        FormulaEntry("Cosine distance", "d = 1 − cos θ", "Not a metric: it violates the triangle inequality, so it cannot index a metric tree."),
        FormulaEntry("Relation to Euclidean", "‖â − b̂‖² = 2(1 − cos θ)", "On unit vectors the two rankings agree exactly; only unnormalised vectors disagree."),
    ),
    notationKey = listOf(
        NotationEntry("term space", "one dimension per vocabulary entry; documents are sparse vectors in it"),
        NotationEntry("‖a‖", "the L2 norm, √Σaᵢ² — the document's length in the geometric sense"),
        NotationEntry("orthogonal", "cos = 0; for count vectors it means no shared terms at all"),
        NotationEntry("L2 normalisation", "dividing by the norm so every vector has length 1"),
        NotationEntry("inner-product search", "what a vector database runs once vectors are normalised"),
        NotationEntry("soft cosine", "a variant that credits related-but-different terms using a similarity matrix"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The metric, and the ranking flip it prevents",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np

                short  = np.array([3., 1.])      # "data" x3, "model" x1
                long   = short * 2               # same document, written twice as long
                theory = np.array([1., 3.])
                query  = np.array([2., 1.])

                cos = lambda a, b: a @ b / (np.linalg.norm(a) * np.linalg.norm(b))

                print(cos(short, long))                       # 1.0
                print(np.linalg.norm(short - long))           # 3.162  <- distance disagrees

                docs = {"short": short, "long": long, "theory": theory}
                print(sorted(docs, key=lambda d: -cos(query, docs[d])))
                # ['short', 'long', 'theory']       <- by angle
                print(sorted(docs, key=lambda d: np.linalg.norm(query - docs[d])))
                # ['short', 'theory', 'long']       <- by distance: the best match ranks LAST
            """.trimIndent(),
        ),
        CodeBlock(
            title = "TF-IDF then cosine, and the normalisation shortcut",
            accentColor = 0xFF14B8A6,
            code = """
                from sklearn.feature_extraction.text import TfidfVectorizer
                from sklearn.metrics.pairwise import cosine_similarity
                import numpy as np

                # TfidfVectorizer L2-normalises rows by default, which is not a detail: it means
                # the linear kernel below IS cosine similarity, at one multiply-add per dimension.
                X = TfidfVectorizer().fit_transform(corpus)
                print(np.allclose(np.linalg.norm(X.toarray(), axis=1), 1.0))   # True
                print(cosine_similarity(X[0], X[1]) == (X[0] @ X[1].T).toarray())  # True

                # The same identity is why FAISS, pgvector and every hosted vector store index
                # normalised embeddings with an inner-product metric rather than a cosine one --
                # and why forgetting to normalise on insert silently ranks by magnitude instead.
                emb = emb / np.linalg.norm(emb, axis=1, keepdims=True)
                index.add(emb)                                # IndexFlatIP == cosine, now
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF8B5CF6, "Document Retrieval", "The scoring function behind classic vector-space search, and the reason length normalisation exists."),
        ApplicationCard("chip", 0xFF6366F1, "Vector Databases & RAG", "Normalise on insert, inner-product on query — cosine by another name."),
        ApplicationCard("users", 0xFF3B82F6, "Recommenders", "User-item and item-item similarity, where one user rating far more items must not dominate."),
        ApplicationCard("flask", 0xFF14B8A6, "Embedding Evaluation", "Sentence-similarity benchmarks and contrastive losses are stated in cosine directly."),
    ),
    takeaways = listOf(
        "Cosine compares direction, not magnitude: a document and its doubled copy score exactly 1.00 while sitting 3.16 apart.",
        "Ranking by distance instead of angle demotes long documents — the lab's best match by topic ranks last by Euclidean.",
        "Non-negative counts bound it to [0,1]; embeddings use the full [−1,1].",
        "On L2-normalised vectors cosine is the dot product, which is what vector databases actually run.",
        "Pair it with TF-IDF weights, and reject it when document length is itself the signal.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("jaccard_similarity", "Jaccard Similarity"),
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("rag", "Retrieval-Augmented Generation"),
    ),
)
