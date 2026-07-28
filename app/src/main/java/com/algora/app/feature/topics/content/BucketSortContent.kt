package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bucketSortContent = TopicContent(
    topicId = "bucket_sort",
    whatIsIt = listOf(
        "Bucket sort scatters values into a set of ordered buckets by range, sorts each bucket with a simple algorithm, then concatenates the buckets in order.",
        "It is not a comparison sort at the top level, so it escapes the O(n log n) bound — but only when the input is spread evenly. If every value lands in one bucket the algorithm degenerates to whatever sorts that bucket, typically O(n²).",
    ),
    steps = listOf(
        StepCard(1, "Choose the Buckets", "Split the value range into k intervals, usually k = n so each holds about one element.", 0xFF3B82F6),
        StepCard(2, "Scatter", "Send each value to bucket ⌊k·(v − min) / (max − min + 1)⌋ in one linear pass.", 0xFF10B981),
        StepCard(3, "Sort Each Bucket", "Insertion sort is the usual choice — buckets are tiny and nearly sorted.", 0xFFF59E0B),
        StepCard(4, "Concatenate", "Because the buckets are already in value order, appending them yields the sorted array.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Bucket index", "b(v) = ⌊k · (v − min) / (max − min + 1)⌋", "Linear mapping from value to bucket."),
        FormulaEntry("Average time", "O(n + k)", "Uniform input, O(1) elements per bucket."),
        FormulaEntry("Worst time", "O(n²)", "All values in one bucket, sorted by insertion sort."),
        FormulaEntry("Space", "O(n + k)", "The buckets themselves."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of buckets"),
        NotationEntry("min, max", "extremes of the input, needed to scale the mapping"),
        NotationEntry("stable", "equal elements keep their order if the per-bucket sort is stable"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bucket sort with insertion sort per bucket",
            accentColor = 0xFF6366F1,
            code = """
                fun bucketSort(a: IntArray): IntArray {
                    if (a.size <= 1) return a
                    val min = a.min()
                    val max = a.max()
                    if (min == max) return a                       // all equal, nothing to do

                    val k = a.size
                    val buckets = Array(k) { mutableListOf<Int>() }
                    for (v in a) {
                        // Scale into [0, k): the span+1 avoids putting max out of range.
                        val index = ((v - min).toLong() * k / (max - min + 1L)).toInt()
                        buckets[index] += v
                    }

                    val out = IntArray(a.size)
                    var write = 0
                    for (bucket in buckets) {
                        insertionSort(bucket)                      // small and nearly sorted
                        for (v in bucket) out[write++] = v
                    }
                    return out
                }

                private fun insertionSort(list: MutableList<Int>) {
                    for (i in 1 until list.size) {
                        val value = list[i]
                        var j = i - 1
                        while (j >= 0 && list[j] > value) {
                            list[j + 1] = list[j]
                            j--
                        }
                        list[j + 1] = value
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Histogram & Percentile Work", "Data already binned for a histogram is one scatter pass away from being sorted."),
        ApplicationCard("chip", 0xFF10B981, "External & Parallel Sorting", "Buckets map cleanly onto files or worker threads, each sorted independently."),
        ApplicationCard("globe", 0xFFF59E0B, "Uniform Float Keys", "Normalized scores and random keys in [0, 1) are the textbook happy case."),
    ),
    takeaways = listOf(
        "Bucket sort is linear only when the input spreads evenly across buckets.",
        "Skewed data collapses it into the per-bucket sort's complexity — usually quadratic.",
        "It needs to know the value range, unlike a comparison sort.",
        "Radix sort is the related idea applied digit by digit, with a guaranteed bound.",
    ),
    crossLinks = listOf(
        CrossLink("counting_sort", "Counting Sort"),
        CrossLink("radix_sort", "Radix Sort"),
        CrossLink("insertion_sort", "Insertion Sort"),
    ),
)
