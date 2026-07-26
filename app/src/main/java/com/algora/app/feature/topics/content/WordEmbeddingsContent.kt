package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val wordEmbeddingsContent = TopicContent(
    topicId = "word_embeddings",
    whatIsIt = listOf(
        "Word embeddings represent each word as a dense vector in a continuous space where semantically similar words sit close together.",
        "Learned from context — 'you shall know a word by the company it keeps' — they capture meaning and analogies that sparse bag-of-words vectors can't.",
    ),
    steps = listOf(
        StepCard(1, "Learn from Context", "Train so a word predicts (or is predicted by) its neighbors — word2vec's skip-gram/CBOW.", 0xFF818CF8),
        StepCard(2, "Dense Vectors", "Each word maps to a few hundred real numbers, not a huge sparse count vector.", 0xFF60A5FA),
        StepCard(3, "Geometry = Meaning", "Similar words cluster; directions encode relationships (king − man + woman ≈ queen).", 0xFF10B981),
        StepCard(4, "Reuse or Fine-Tune", "Pretrained embeddings seed downstream models; modern ones are contextual (per-sentence).", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Similarity", "cos(u, v) = u·v / (‖u‖‖v‖)", "Cosine measures semantic closeness."),
        FormulaEntry("Skip-gram", "maximize P(context | word)", "word2vec's training objective."),
        FormulaEntry("Analogy", "vec(king) − vec(man) + vec(woman)", "Lands near vec(queen)."),
    ),
    notationKey = listOf(
        NotationEntry("embedding", "a word's dense vector representation"),
        NotationEntry("cosine similarity", "angle-based closeness of two vectors"),
        NotationEntry("static vs contextual", "one vector per word vs per occurrence"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Using word2vec (gensim)",
            accentColor = 0xFF6366F1,
            code = """
                from gensim.models import Word2Vec

                model = Word2Vec(sentences, vector_size=100, window=5, min_count=2)
                print(model.wv.most_similar("king"))
                print(model.wv.similarity("cat", "dog"))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF818CF8, "Semantic Search", "Retrieving by meaning, not exact words, via nearest embeddings."),
        ApplicationCard("target", 0xFF60A5FA, "Recommendation", "Item and user embeddings place similar things nearby for matching."),
        ApplicationCard("robot", 0xFF10B981, "Model Input", "Embeddings are the standard first layer feeding text into neural networks."),
    ),
    takeaways = listOf(
        "Embeddings encode words as dense vectors where geometry reflects meaning.",
        "They're learned from context and capture similarity and analogies.",
        "word2vec/GloVe give static vectors; Transformers give contextual ones.",
        "Cosine similarity over embeddings powers modern semantic search and retrieval.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("transformers", "Transformers"),
    ),
)
