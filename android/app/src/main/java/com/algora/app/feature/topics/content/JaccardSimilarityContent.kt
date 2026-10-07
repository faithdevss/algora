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

internal val jaccardSimilarityContent = TopicContent(
    topicId = "jaccard_similarity",
    figure = Figure(
        caption = "Four comparisons from the page's lab, each a ratio of shared to total. Two " +
            "sentences as word sets share 5 of 7 distinct words: J = 0.714, and the repeated " +
            "\"data\" in one of them counts once, by design. Cosine on the same pair says 0.849, " +
            "higher, because a count vector still sees the repeat — the two measures agree on the " +
            "ranking but not the scale. Two sentences that mean the same thing in different words " +
            "(\"the model learns\" / \"a network is trained\") score exactly 0: Jaccard sees only " +
            "surface overlap. Split words into character 3-grams instead and \"learning\" vs " +
            "\"learner\" shares 3 of 8, J = 0.375 — partial credit for spelling variants, which " +
            "is the basis of fuzzy matching and, with longer shingles and MinHash, of web-scale " +
            "near-duplicate detection.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("5", "7", "0.714"),
                listOf("—", "—", "0.849"),
                listOf("0", "7", "0.000"),
                listOf("3", "8", "0.375"),
            ),
            rowHeaders = listOf("word sets", "cosine, counts", "paraphrase", "3-grams"),
            colHeaders = listOf("shared", "union", "score"),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Warn),
                FigureCell(3, 2, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Jaccard similarity is set overlap: |A ∩ B| / |A ∪ B|. Applied to text it throws away counts, weights and order and keeps only membership — a document becomes the set of things in it. On the lab's pair, five shared terms over a union of seven gives 0.714, and the fact that \"data\" appears twice in one document and once in the other changes nothing. That is the design, not an oversight: for \"how much of this material is also in that\", repetition is noise.",
        "Cosine over the same two documents gives 0.849, higher, because it still sees the repeated term aligning the two vectors. Neither number is more correct — they answer different questions. Cosine asks how similar the emphasis is and is the right choice over TF-IDF weights and embeddings; Jaccard asks how much of the material is shared and is the right choice for deduplication, plagiarism, permission sets, tags and shingles, where a count would mean nothing anyway.",
        "Near-duplicate detection does not use word sets at all — it uses character k-shingles, which preserve local word order: at k = 5 the two documents in the code below have 35 and 22 shingles and score 0.629. Comparing those sets exactly is O(|A| + |B|) per pair and quadratic across a corpus, which is why MinHash exists. Hash every shingle under k independent permutations and keep the minimum under each; the probability that two sets' minima agree is *exactly* their Jaccard similarity, so the fraction of matching signature positions estimates it, with error shrinking as 1/√k. With one hash family the estimates run 0.688 at k = 16, 0.641 at 64 and 0.598 at 256 against a true 0.629 — other hash functions (such as the md5 code below) give different draws around the same value. Fixed-width signatures then feed locality-sensitive hashing, which finds candidate near-duplicates in a web-scale corpus without comparing all pairs.",
    ),
    steps = listOf(
        StepCard(1, "Choose the Element", "Words for topical overlap, character k-shingles for near-duplicates, tags or IDs for sets.", 0xFF8B5CF6),
        StepCard(2, "Build Sets", "Membership only — no counts, no order, no weights.", 0xFF6366F1),
        StepCard(3, "Intersect and Union", "|A ∩ B| / |A ∪ B|; 1.0 is identical sets, 0.0 is disjoint.", 0xFF3B82F6),
        StepCard(4, "Shingle for Duplicates", "k-grams of characters keep local order, so word-order changes are detectable.", 0xFF06B6D4),
        StepCard(5, "MinHash the Sets", "k hash permutations, keep each minimum — a fixed-width signature that estimates Jaccard.", 0xFF14B8A6),
        StepCard(6, "Band for LSH", "Split signatures into bands; matching bands become candidate pairs, avoiding all-pairs comparison.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Jaccard index", "J(A,B) = |A ∩ B| / |A ∪ B| = 5/7 = 0.714", "Measured on the lab's document pair."),
        FormulaEntry("Jaccard distance", "d = 1 − J", "Unlike cosine distance this *is* a metric — it satisfies the triangle inequality."),
        FormulaEntry("Against cosine", "J = 0.714 vs cos = 0.849", "Same pair; cosine still sees the repeated term, Jaccard cannot."),
        FormulaEntry("Shingled", "J₅ = 0.629 over 35 and 22 shingles", "Character 5-shingles, which keep local word order."),
        FormulaEntry("MinHash identity", "P(min_h(A) = min_h(B)) = J(A,B)", "Exact, not approximate — the estimator is unbiased by construction."),
        FormulaEntry("Estimator error", "σ ≈ √(J(1−J)/k)", "One hash family gives 0.688 at k=16, 0.641 at k=64, 0.598 at k=256 against a true 0.629; other hashes give different draws."),
    ),
    notationKey = listOf(
        NotationEntry("k-shingle", "a contiguous window of k characters (or words); the unit near-duplicate detection compares"),
        NotationEntry("MinHash", "a signature of per-permutation minima whose agreement rate estimates Jaccard"),
        NotationEntry("LSH", "locality-sensitive hashing — banding signatures so similar items collide on purpose"),
        NotationEntry("candidate pair", "a pair LSH surfaces for exact scoring, instead of scoring all n²/2"),
        NotationEntry("weighted Jaccard", "Σ min(aᵢ,bᵢ) / Σ max(aᵢ,bᵢ) — the version that does look at counts"),
        NotationEntry("containment", "|A ∩ B| / |A| — the right measure when one document is much shorter"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Sets, shingles, and the two metrics side by side",
            accentColor = 0xFF8B5CF6,
            code = """
                a = "the model learns from data and more data"
                b = "the model learns from data"

                jaccard = lambda x, y: len(x & y) / len(x | y)
                print(jaccard(set(a.split()), set(b.split())))     # 0.714  (5 shared / 7 union)

                # Cosine on raw counts, same pair -- higher, because the repeated "data" still counts.
                from collections import Counter
                import math
                ca, cb = Counter(a.split()), Counter(b.split())
                dot = sum(ca[t] * cb[t] for t in ca.keys() | cb.keys())
                print(dot / (math.hypot(*ca.values()) * math.hypot(*cb.values())))   # 0.849

                # Near-duplicate detection uses character shingles, which keep local word order:
                shingle = lambda s, k=5: {s[i:i + k] for i in range(len(s) - k + 1)}
                print(jaccard(shingle(a), shingle(b)))             # 0.629 over 35 and 22 shingles
            """.trimIndent(),
        ),
        CodeBlock(
            title = "MinHash: fixed-width signatures that estimate the same number",
            accentColor = 0xFF14B8A6,
            code = """
                import hashlib

                def signature(s: set[str], k: int) -> list[int]:
                    # k independent hash functions; keep the minimum hash of the set under each.
                    return [min(int(hashlib.md5(f"{seed}:{x}".encode()).hexdigest()[:8], 16) for x in s)
                            for seed in range(k)]

                def estimate(a: set[str], b: set[str], k: int) -> float:
                    sa, sb = signature(a, k), signature(b, k)
                    return sum(x == y for x, y in zip(sa, sb)) / k

                A, B = shingle(a), shingle(b)
                for k in (16, 64, 256):
                    print(k, round(estimate(A, B, k), 3))   # -> 0.629 as k grows; error ~ 1/sqrt(k)

                # Why this matters: comparing the sets exactly costs O(|A| + |B|) per pair and
                # O(n^2) pairs. Signatures are fixed-width, and LSH banding turns "find near
                # duplicates in a billion documents" into hashing each document a few times --
                # which is how search engines have deduplicated crawls since 1997.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF8B5CF6, "Near-Duplicate Detection", "Crawl deduplication, plagiarism checking and training-set decontamination all run shingles + MinHash."),
        ApplicationCard("users", 0xFF6366F1, "Recommenders", "Overlap of item sets between users, where a purchase either happened or did not."),
        ApplicationCard("lock", 0xFF3B82F6, "Permission & Tag Sets", "Comparing role, label or keyword sets, where counts have no meaning at all."),
        ApplicationCard("flask", 0xFF14B8A6, "Genomics & Malware", "k-mer set similarity between genomes, and opcode sets between binaries — same index, different alphabet."),
    ),
    takeaways = listOf(
        "J = |A ∩ B| / |A ∪ B| — membership only: 0.714 on the lab's pair, where cosine says 0.849.",
        "Jaccard distance is a true metric; cosine distance is not, which matters for indexing.",
        "Character k-shingles keep local word order — the real unit of near-duplicate detection (J₅ = 0.629 here).",
        "MinHash is exact in expectation: P(minima agree) = J, so k permutations estimate it with error ~1/√k.",
        "Use it for deduplication, sets and tags; use cosine when counts and weights carry the meaning.",
    ),
    crossLinks = listOf(
        CrossLink("cosine_similarity", "Cosine Similarity"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("bloom_filter", "Bloom Filter"),
        CrossLink("rabin_karp", "Rabin-Karp"),
    ),
)
