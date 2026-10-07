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

internal val vectorDatabasesContent = TopicContent(
    topicId = "vector_databases",
    figure = Figure(
        caption = "The page's 180-vector corpus, searched five ways, with recall against the " +
            "exact answer. Brute force compares the query to all 180 and is exact by construction. " +
            "IVF probing one k-means cell does 28 comparisons and returns 60% of the true " +
            "neighbours — the rest sit just across the cell boundary — and probing a second cell " +
            "costs 7 more comparisons and brings them all back. The graph rows fail differently: " +
            "a plain 6-nearest-neighbour graph over this corpus splits into 3 disconnected " +
            "components, so a walk that starts in the wrong one cannot reach the answer at any " +
            "candidate-list size, and recall is 0.00. Two random long-range links per vector join " +
            "it into one component and the same stranded start reaches 100% in three hops. Recall " +
            "is not a property of an index; it is a dial, and each row is a different price for a " +
            "notch of it.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("180 dist.", "1.00"),
                listOf("28 dist.", "0.60"),
                listOf("35 dist.", "1.00"),
                listOf("3 parts", "0.00"),
                listOf("1 part", "1.00"),
            ),
            rowHeaders = listOf("brute force", "IVF, 1 cell", "IVF, 2 cells", "6-NN graph", "+ long links"),
            colHeaders = listOf("cost", "recall"),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(3, 1, FigureTone.Warn),
                FigureCell(4, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A vector database answers exactly one question: which stored vectors are nearest this one. Everything else it offers — metadata filters, hybrid search, persistence, replication — is ordinary database work wrapped around that primitive. The honest baseline is to compare against every stored vector, which is exact by construction and linear in the corpus. Every index below is an approximation measured against it, and the number that matters is recall: what fraction of the true nearest neighbours it actually returned.",
        "Two families dominate, and they fail differently. IVF partitions the vectors into cells with k-means and scans only the cells a query probes, so its failure is geometric: a query near a cell boundary has true neighbours sitting just across it. In the lab, probing one cell returns 60% recall for 28 comparisons against the corpus's 180; probing two returns 100% for 35. That is the trade the whole field runs on — recall is not a property of the index, it is a dial with a price per notch.",
        "Graph methods navigate instead of partitioning: link each vector to its nearest neighbours, then greedily walk toward the query. The obvious version is broken in a way worth seeing, because it explains the real algorithm's shape. A plain 6-nearest-neighbour graph over this corpus comes apart into 3 components, so from a third of possible entry points there is no path to the answer and no amount of candidate list gets you one — recall is 0.00 at every `ef` tried. Add two random long-range links per vector and the graph collapses to one component; the same stranded start reaches 100% recall in three hops. HNSW gets its long links from a layer hierarchy rather than at random, but that is the job they do.",
    ),
    steps = listOf(
        StepCard(1, "Embed", "Text becomes a vector; retrieval quality is capped by the embedding model.", 0xFF10B981),
        StepCard(2, "Index", "Partition (IVF) or link (HNSW). This is where the approximation enters.", 0xFF14B8A6),
        StepCard(3, "Search", "Probe cells or walk the graph; scan far fewer than N vectors.", 0xFF3B82F6),
        StepCard(4, "Measure Recall", "Against brute force. An index without a recall number is unevaluated.", 0xFF6366F1),
        StepCard(5, "Compress if Needed", "Quantization saves memory and destroys rankings finer than its grid.", 0xFF8B5CF6),
        StepCard(6, "Rescore", "Re-rank the shortlist with full vectors — the fix for compression, not a better quantizer.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Recall@k", "|found ∩ true| / k", "The only honest score for an approximate index."),
        FormulaEntry("Brute force", "N distance computations", "180 here; exact by construction."),
        FormulaEntry("IVF cost", "nlist + (N · nprobe / nlist)", "Centroid scan plus the probed cells' contents."),
        FormulaEntry("IVF measured", "nprobe 1 → 0.60 @ 28 · nprobe 2 → 1.00 @ 35", "One extra cell, 7 more comparisons, the missing neighbours back."),
        FormulaEntry("Contrast", "(d_max − d_min) / d_min", "34.6 at d = 2, 0.35 at d = 128 — why high-dimensional search is hard."),
        FormulaEntry("Quantization step", "1 / (levels − 1)", "0.067 at 16 levels, against a 0.0089 neighbour spread."),
    ),
    notationKey = listOf(
        NotationEntry("recall@k", "fraction of the exact top-k that an approximate search returned"),
        NotationEntry("IVF", "inverted file index — k-means cells, scan only the probed ones"),
        NotationEntry("nprobe", "how many cells a query opens; the recall dial"),
        NotationEntry("HNSW", "hierarchical navigable small world — greedy graph walk with long-range links"),
        NotationEntry("ef", "candidate-list size during the graph walk; wider search, more comparisons"),
        NotationEntry("quantization", "storing components at reduced precision to cut memory"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Embed, index, query — and always measure recall",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np

                # A vector store is an index plus the two operations below. Whatever library
                # you use, these are the only calls that matter.
                vectors = np.load("corpus.npy")          # (N, d) float32, L2-normalised
                query = embed("In what year was C released?")

                # 1. The baseline you evaluate against. Exact, and O(N).
                exact = np.argsort(-(vectors @ query))[:5]

                # 2. The approximate index, whatever it is.
                approx = index.search(query, k=5, nprobe=2)

                # 3. The number the index is worth. Compute it on a held-out query set
                #    BEFORE tuning anything -- an index without a recall figure is
                #    unevaluated, not fast.
                recall = len(set(approx) & set(exact)) / 5
                print(f"recall@5 = {recall:.2f}")
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why HNSW is not a k-NN graph",
            accentColor = 0xFFEC4899,
            code = """
                # The obvious neighbour graph: link every vector to its 6 nearest.
                knn = [nearest_ids(i, k=6) for i in range(len(corpus))]
                print(component_count(knn))              # 3

                # Three components means a greedy walk that starts in the wrong one can
                # never reach the answer -- and no amount of candidate list fixes it,
                # because the path does not exist.
                stranded = entry_outside_answer_component(knn)
                for ef in (1, 2, 4, 8):
                    print(ef, recall(graph_search(ef, knn, entry=stranded)))
                # 1 0.0
                # 2 0.0
                # 4 0.0
                # 8 0.0        <- widening the search does nothing; it is a topology problem

                # Add two random long-range links per node -- the "small world" half.
                small_world = [near + random_ids(2) for near in knn]
                print(component_count(small_world))       # 1
                print(recall(graph_search(4, small_world, entry=stranded)))   # 1.0

                # HNSW draws its long links from a layer hierarchy rather than at random,
                # but this is the problem they exist to solve.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF10B981, "Retrieval for RAG", "The retrieval half of every RAG system is this index."),
        ApplicationCard("users", 0xFF3B82F6, "Recommendation", "Nearest-neighbour over user and item embeddings, at the same trade."),
        ApplicationCard("finance", 0xFF8B5CF6, "Memory Budgets", "int8 at 768 dims is 768 bytes against fp32's 3,072 — and needs rescoring."),
        ApplicationCard("help", 0xFFEC4899, "Recall Debt", "An index tuned for speed with no recall measurement is silently losing answers."),
    ),
    takeaways = listOf(
        "One primitive — nearest neighbours — and every index is an approximation of it, scored by recall.",
        "IVF fails geometrically: at a cell boundary, nprobe 1 gives 0.60 recall and nprobe 2 gives 1.00 for 7 more comparisons.",
        "A plain k-NN graph came apart into 3 components: from a third of entry points, recall is 0.00 at every ef.",
        "Two random long-range links per node made it one component and recovered full recall in 3 hops — that is what HNSW's layers buy.",
        "Quantization at 16 levels imposes a 0.067 grid on neighbours 0.0089 apart; rescore the shortlist rather than tuning the quantizer.",
    ),
    crossLinks = listOf(
        CrossLink("rag", "Retrieval-Augmented Generation"),
        CrossLink("cosine_similarity", "Cosine Similarity"),
        CrossLink("knn", "K-Nearest Neighbors"),
        CrossLink("word_embeddings", "Word Embeddings"),
    ),
)
