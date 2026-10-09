package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val wordEmbeddingsContent = TopicContent(
    topicId = "word_embeddings",
    figure = Figure(
        caption = "The lab's word vectors placed on two of their five dimensions: royal runs across, " +
            "female − male runs up. The two highlighted arrows, man → woman and king → queen, are " +
            "the same vector, woman − man: female up 0.85, male down 0.85, " +
            "royal and human unchanged. That is why king − man + woman lands exactly on queen in " +
            "the lab, at cosine 1.00. Apple has no royalty and no gender, so it sits on neither " +
            "axis; its weight is on fruit, which this view leaves out, and its cosine to king is " +
            "0.03. Against king, man (0.83) scores higher than queen (0.69) — on these dimensions " +
            "gender counts as much as royalty. Trained embeddings have hundreds of unnamed " +
            "dimensions and only roughly parallel offsets.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("man", 0.30f, 0.88f),
                FigureGraphNode("woman", 0.30f, 0.12f),
                FigureGraphNode("king", 0.86f, 0.88f, FigureTone.Primary),
                FigureGraphNode("queen", 0.86f, 0.12f, FigureTone.Accent),
                FigureGraphNode("apple", 0.04f, 0.50f),
            ),
            edges = listOf(
                FigureEdge(0, 1, directed = true, tone = FigureTone.Accent),
                FigureEdge(2, 3, directed = true, tone = FigureTone.Accent),
                FigureEdge(0, 2, "+ royal", directed = true),
                FigureEdge(1, 3, "+ royal", directed = true),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Word embeddings represent each word as a dense vector in a continuous space where semantically similar words sit close together.",
        "Learned from context — 'you shall know a word by the company it keeps' — they capture meaning and analogies that sparse bag-of-words vectors can't.",
        "The lab uses five hand-set dimensions — royal, male, female, human, fruit — so the geometry can be read directly. King is (0.95, 0.90, 0.05, 0.80, 0.00), queen (0.95, 0.05, 0.90, 0.80, 0.00), man (0.10, 0.90, 0.05, 0.90, 0.00), woman (0.10, 0.05, 0.90, 0.90, 0.00) and apple (0.00, 0.00, 0.00, 0.05, 0.95). Against king, man scores 0.83, queen 0.69, woman 0.46 and apple 0.03; as one-hot vectors every pair would score 0. The step from man to woman is (0, −0.85, +0.85, 0, 0), the same as the step from king to queen, so king − man + woman comes out as (0.95, 0.05, 0.90, 0.80, 0.00), which is queen's vector exactly, at cosine 1.00.",
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
