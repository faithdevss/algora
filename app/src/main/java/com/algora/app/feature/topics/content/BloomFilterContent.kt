package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bloomFilterContent = TopicContent(
    topicId = "bloom_filter",
    whatIsIt = listOf(
        "A Bloom filter is a space-efficient probabilistic set that tests membership with one guarantee: 'definitely not present' is always correct, but 'possibly present' may be a false positive.",
        "It stores no elements — just a bit array flipped by several hash functions — so it uses a tiny fraction of the memory a real set would, at the cost of a tunable error rate and no deletions.",
    ),
    steps = listOf(
        StepCard(1, "One Bit Array", "Start with m bits all zero and k independent hash functions.", 0xFF10B981),
        StepCard(2, "Insert", "Hash the item k ways and set all k resulting bit positions to 1.", 0xFF3B82F6),
        StepCard(3, "Query", "Hash the item the same k ways; if any of those bits is 0, it's definitely absent.", 0xFFF59E0B),
        StepCard(4, "Accept False Positives", "If all k bits are 1, it's probably present — collisions can lie, but never in the negative direction.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("False-positive rate", "≈ (1 − e^(−kn/m))ᵏ", "Grows as the filter fills."),
        FormulaEntry("Optimal hashes", "k = (m/n)·ln 2", "Minimizes the error for given size."),
        FormulaEntry("No false negatives", "'absent' is always true", "And elements can't be removed from a plain filter."),
    ),
    notationKey = listOf(
        NotationEntry("m", "number of bits in the array"),
        NotationEntry("k", "number of hash functions"),
        NotationEntry("n", "number of inserted elements"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bloom filter core",
            accentColor = 0xFF6366F1,
            code = """
                class BloomFilter(private val m: Int, private val k: Int) {
                    private val bits = BooleanArray(m)

                    private fun hashes(item: String) = (0 until k).map {
                        ((item.hashCode() * 31 + it * 0x9E3779B1.toInt()) and 0x7FFFFFFF) % m
                    }

                    fun add(item: String) { for (h in hashes(item)) bits[h] = true }

                    // false = definitely absent; true = possibly present.
                    fun mightContain(item: String) = hashes(item).all { bits[it] }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.HashingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Database Read Skipping", "LSM-tree stores (Cassandra, RocksDB) use Bloom filters to skip disk lookups for absent keys."),
        ApplicationCard("globe", 0xFF3B82F6, "Caches & CDNs", "Web caches check a Bloom filter before an expensive origin fetch to avoid one-hit-wonders."),
        ApplicationCard("lock", 0xFFF59E0B, "Safe Browsing", "Browsers test URLs against a Bloom filter of malicious sites before a full check."),
    ),
    takeaways = listOf(
        "A Bloom filter answers membership in tiny space, with false positives but never false negatives.",
        "Tuning m and k trades memory against the false-positive rate.",
        "A plain Bloom filter can't delete; counting Bloom filters add that at extra cost.",
        "Use it as a cheap pre-filter to avoid expensive lookups for items that are absent.",
    ),
    crossLinks = listOf(
        CrossLink("hash_table", "Hash Table / Hash Map"),
        CrossLink("lru_cache", "LRU Cache"),
    ),
)
