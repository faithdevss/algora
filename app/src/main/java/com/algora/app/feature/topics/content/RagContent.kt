package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ragContent = TopicContent(
    topicId = "rag",
    whatIsIt = listOf(
        "Retrieval-augmented generation answers a question by first fetching relevant passages from a corpus and then generating an answer conditioned on them, rather than relying on what the model memorized during training.",
        "It fixes three specific problems: knowledge frozen at the training cutoff, hallucination when the model does not know, and the impossibility of putting a private corpus into the weights. The corpus becomes a swappable index, so updating knowledge means re-indexing rather than retraining.",
    ),
    steps = listOf(
        StepCard(1, "Chunk the Corpus", "Split documents into passages of a few hundred tokens with a little overlap so context is not cut mid-thought.", 0xFF3B82F6),
        StepCard(2, "Embed and Index", "Encode each chunk into a vector and store it in an approximate-nearest-neighbour index.", 0xFF10B981),
        StepCard(3, "Retrieve Top-k", "Embed the query, pull the k nearest chunks — often blended with BM25 keyword scores as hybrid search.", 0xFFF59E0B),
        StepCard(4, "Rerank", "A cross-encoder rescores the shortlist by reading query and passage together, which is slower but far more accurate.", 0xFF8B5CF6),
        StepCard(5, "Generate With Citations", "The prompt carries the passages and an instruction to answer only from them and cite the source.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Similarity", "cos(q, d) = (q·d) / (‖q‖‖d‖)", "The dense retrieval score."),
        FormulaEntry("Hybrid fusion", "score = α·dense + (1−α)·BM25", "Or reciprocal rank fusion: Σ 1/(k + rank)."),
        FormulaEntry("Bi- vs cross-encoder", "sim(f(q), f(d))  vs  g(q ⊕ d)", "One is precomputable, the other reads the pair jointly."),
        FormulaEntry("Context budget", "k · chunk_size < context window", "The hard constraint on how much can be retrieved."),
    ),
    notationKey = listOf(
        NotationEntry("chunk", "a passage-sized unit of the corpus, embedded and indexed"),
        NotationEntry("ANN", "approximate nearest neighbour — HNSW, IVF-PQ, ScaNN"),
        NotationEntry("BM25", "classic sparse keyword relevance score"),
        NotationEntry("grounding", "requiring the answer to be supported by retrieved text"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A minimal RAG pipeline",
            accentColor = 0xFF6366F1,
            code = """
                class RagPipeline(
                    private val embed: (String) -> DoubleArray,
                    private val index: VectorIndex,
                    private val rerank: ((String, List<Chunk>) -> List<Chunk>)? = null,
                    private val llm: (String) -> String,
                ) {
                    fun ingest(documents: List<String>, chunkSize: Int = 300, overlap: Int = 50) {
                        for (doc in documents) {
                            val words = doc.split(" ")
                            var start = 0
                            while (start < words.size) {
                                val end = minOf(start + chunkSize, words.size)
                                val text = words.subList(start, end).joinToString(" ")
                                index.add(Chunk(text), embed(text))
                                if (end == words.size) break
                                start += chunkSize - overlap        // overlap keeps context across the seam
                            }
                        }
                    }

                    fun answer(question: String, k: Int = 5): String {
                        val candidates = index.search(embed(question), k = k * 4)
                        val passages = (rerank?.invoke(question, candidates) ?: candidates).take(k)

                        val context = passages.mapIndexed { i, c -> "[${'$'}{i + 1}] ${'$'}{c.text}" }
                            .joinToString("\n\n")

                        // Grounding instruction: refuse rather than invent.
                        val prompt = buildString {
                            appendLine("Answer the question using ONLY the sources below.")
                            appendLine("If they do not contain the answer, say so. Cite sources as [n].")
                            appendLine()
                            appendLine(context)
                            appendLine()
                            append("Question: ").append(question)
                        }
                        return llm(prompt)
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF3B82F6, "Documentation Assistants", "Support bots answer from the current handbook instead of a stale snapshot in the weights."),
        ApplicationCard("search", 0xFF10B981, "Enterprise Search", "Private corpora stay in an index the model reads at query time, never in its parameters."),
        ApplicationCard("target", 0xFFF59E0B, "Grounded Q&A", "Citations let a reader verify each claim, which is what makes the output auditable."),
    ),
    takeaways = listOf(
        "RAG separates knowledge from parameters — update the index, not the model.",
        "Retrieval quality caps answer quality: if the right passage is not in the top-k, generation cannot recover.",
        "Chunking and overlap are quietly the highest-leverage knobs in the whole pipeline.",
        "Hybrid retrieval plus a cross-encoder reranker beats pure vector search on almost every real corpus.",
    ),
    crossLinks = listOf(
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("llms", "LLMs"),
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
