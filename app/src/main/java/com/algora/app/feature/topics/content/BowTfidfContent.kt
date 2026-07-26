package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bowTfidfContent = TopicContent(
    topicId = "bow_tfidf",
    whatIsIt = listOf(
        "Bag-of-Words represents a document as a vector of word counts, discarding order — just which words appear and how often.",
        "TF-IDF refines those counts by down-weighting words common across all documents and up-weighting words distinctive to one, so uninformative words like 'the' don't dominate.",
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
    ),
    crossLinks = listOf(
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
