package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bowTfidfContent = TopicContent(
    topicId = "bow_tfidf",
    figure = Figure(
        caption = "The page's lab: two six-word sentences — \"the cat sat on the mat\" and \"the dog " +
            "sat on the log\" — as bag-of-words count vectors over their shared 7-word vocabulary, " +
            "with each word's inverse document frequency underneath. Order is gone: any reordering " +
            "of a sentence gives the same row. By raw counts the two rows have a cosine similarity of " +
            "0.75, mostly from \"the\", which each uses twice. IDF, ln((1 + N)/(1 + df)) + 1, gives a " +
            "word that appears in both documents the floor of 1.00 and a word that singles one out " +
            "1.41, so multiplying counts by IDF makes cat, mat, dog and log stand out, and the " +
            "similarity drops to 0.60 — closer to how different the sentences actually are.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2", "1", "1", "1", "1", "0", "0"),
                listOf("2", "0", "1", "1", "0", "1", "1"),
                listOf("1.00", "1.41", "1.00", "1.00", "1.41", "1.41", "1.41"),
            ),
            rowHeaders = listOf("doc 1", "doc 2", "idf"),
            colHeaders = listOf("the", "cat", "sat", "on", "mat", "dog", "log"),
            marks = listOf(
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(2, 4, FigureTone.Accent),
                FigureCell(2, 5, FigureTone.Accent),
                FigureCell(2, 6, FigureTone.Accent),
                FigureCell(2, 0, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Bag of words represents a document as counts of the words in it, with one column per vocabulary word and the order thrown away. TF-IDF reweights those counts: term frequency times inverse document frequency, so a word that appears in every document counts for little and a word that singles out a document counts for more.",
        "The lab builds both from two six-word sentences that share four words, giving a vocabulary of 7. Document 1 becomes [2, 1, 1, 1, 1, 0, 0] — \"the\" twice — and any reordering of its words gives the same vector. By raw counts the two documents have a cosine similarity of 0.75, mostly because of \"the\". IDF fixes the emphasis: idf = ln((1 + N)/(1 + df)) + 1 gives a word in both documents the floor of 1.00 and a word in only one 1.41, so \"cat\" and \"mat\" now stand out and the similarity drops to 0.60.",
        "The representation's limits are as clear as its strengths. It forgets order entirely — \"dog bites man\" and \"man bites dog\" are the same vector — and it has one dimension per vocabulary word, around 10⁵ in a real corpus, almost all zero. It remains a fast, interpretable and strong baseline for search and classification; word embeddings fix the sparsity and sequence models fix the order.",
    ),
    steps = listOf(
        StepCard(1, "Build a Vocabulary", "Collect every unique term across the corpus; each becomes a vector dimension.", 0xFF818CF8),
        StepCard(2, "Count (Term Frequency)", "For each document, count how often each term appears.", 0xFF60A5FA),
        StepCard(3, "Weight by Rarity (IDF)", "Scale each count by how rare the term is across all documents.", 0xFF10B981),
        StepCard(4, "Vectorize", "The document becomes a sparse TF-IDF vector, ready for a model.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("TF", "count(t, d) / |d|", "How often term t appears in document d."),
        FormulaEntry("IDF", "log(N / dfₜ)", "Rarity across N documents; dfₜ = docs containing t."),
        FormulaEntry("TF-IDF", "TF · IDF", "High for terms frequent here but rare overall."),
    ),
    notationKey = listOf(
        NotationEntry("TF", "term frequency within a document"),
        NotationEntry("IDF", "inverse document frequency (rarity)"),
        NotationEntry("sparse vector", "mostly zeros — most terms absent"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "TF-IDF vectors (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.feature_extraction.text import TfidfVectorizer

                docs = ["the cat sat", "the dog ran", "cat and dog"]
                vec = TfidfVectorizer()
                X = vec.fit_transform(docs)   # sparse TF-IDF matrix
                print(vec.get_feature_names_out())
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF818CF8, "Search Ranking", "TF-IDF (and BM25) scores how well a document matches a query."),
        ApplicationCard("chart", 0xFF60A5FA, "Text Classification", "Sparse TF-IDF features feed Naive Bayes and linear SVMs effectively."),
        ApplicationCard("book", 0xFF10B981, "Keyword Extraction", "High-TF-IDF terms surface a document's distinctive keywords."),
    ),
    takeaways = listOf(
        "Bag-of-Words vectorizes text as word counts, ignoring order.",
        "TF-IDF reweights counts by rarity so distinctive words matter most.",
        "The vectors are sparse and high-dimensional — a fit for linear models.",
        "It captures no meaning or order; word embeddings address that.",
        "In the lab two sentences sharing four words have cosine 0.75 by raw counts and 0.60 after TF-IDF down-weights the shared words.",
    ),
    crossLinks = listOf(
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
