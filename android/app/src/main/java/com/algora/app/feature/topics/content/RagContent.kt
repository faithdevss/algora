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

internal val ragContent = TopicContent(
    topicId = "rag",
    figure = Figure(
        caption = "The page's lab: \"What is the refund window?\" asked about a private policy. " +
            "Closed-book, the model answers fluently that refunds are accepted within 30 days — " +
            "specific, unsourced and wrong. With retrieval, the query is embedded (here as a TF-IDF " +
            "vector; production systems use a dense embedding model) and compared by cosine with " +
            "every chunk in a five-document store. The policy chunk scores 0.51; a chunk about " +
            "damaged items shares only the word \"refund\" and scores 0.22; the rest share nothing. " +
            "The top two go into the prompt with an instruction to answer only from them, and the " +
            "answer becomes 45 days, with a citation. If retrieval had missed the first row, no " +
            "amount of generation quality would have recovered it — RAG is bounded by its search.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("The refund window is 45 days…", "0.51"),
                listOf("Damaged items … full refund…", "0.22"),
                listOf("Shipping is free on orders…", "0.00"),
                listOf("Gift cards cannot be refunded…", "0.00"),
                listOf("Support is available by chat…", "0.00"),
            ),
            colHeaders = listOf("stored chunk", "cosine"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(1, 1, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Retrieval-augmented generation answers from documents rather than from memory. The question is embedded as a vector, the most similar chunks of a document store are retrieved, and they are pasted into the prompt with an instruction to answer only from them. The model's weights supply language and reasoning; the documents supply the facts, which can be private, recent, and cited.",
        "The lab asks \"What is the refund window?\" about a private, changeable policy. Closed-book, the model answers fluently and specifically — \"Refunds are accepted within 30 days\" — with no source and no way to know it is out of date. With retrieval, the query is turned into a TF-IDF vector over a five-document store (production systems use a dense embedding model) and compared by cosine: the policy chunk, \"The refund window is 45 days from delivery for unused items\", scores 0.51; a chunk about damaged items shares only the word \"refund\" and scores 0.22; the rest score 0. The top two go into the prompt, and the answer becomes 45 days, cited.",
        "RAG's quality is bounded by its retrieval. If the right chunk is not retrieved, the model either refuses or falls back on its weights; if a misleading chunk is retrieved, it will faithfully repeat it. So most of the engineering is in chunking, the embedding model, hybrid keyword-plus-vector search and re-ranking — and in evaluating retrieval separately from generation.",
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
        "In the lab the closed-book answer is a confident 30 days; retrieval finds the policy chunk (cosine 0.51) and the answer becomes the correct 45 days.",
    ),
    crossLinks = listOf(
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("llms", "LLMs"),
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
