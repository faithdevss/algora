package com.algora.app.feature.analysis

import com.algora.app.feature.analysis.tools.AnalysisToolRegistry
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos
import com.algora.app.feature.analysis.tools.common.complexityCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The Analysis tools print their op counts to the user as measured fact ("not an estimate"), so the
 * counts are only as trustworthy as the instrumented code producing them. These lock the closed-form
 * bounds each tool's copy claims, and the agreement between the plotted curves and the real code.
 */
class AnalysisToolsTest {

    private fun sorted(n: Int) = IntArray(n) { it }
    private fun reversed(n: Int) = IntArray(n) { n - 1 - it }

    @Test
    fun `linear search counts one comparison per element until it hits the target`() {
        assertEquals(16, InstrumentedAlgos.linearSearch(sorted(16), -1))
        assertEquals(1, InstrumentedAlgos.linearSearch(sorted(16), 0))
        assertEquals(9, InstrumentedAlgos.linearSearch(sorted(16), 8))
    }

    @Test
    fun `binary search halves the range so an absent target costs log2 n`() {
        // OperationCounterTool's own input shape: sorted evens, target absent below the range.
        assertEquals(4, InstrumentedAlgos.binarySearch(IntArray(16) { it * 2 }, -1))
        assertEquals(5, InstrumentedAlgos.binarySearch(IntArray(32) { it * 2 }, -1))
        assertEquals(6, InstrumentedAlgos.binarySearch(IntArray(64) { it * 2 }, -1))
    }

    @Test
    fun `insertion sort hits its documented best and worst bounds`() {
        for (n in 2..32) {
            assertEquals("best case at n=$n", n - 1, InstrumentedAlgos.insertionSort(sorted(n)))
            assertEquals("worst case at n=$n", n * (n - 1) / 2, InstrumentedAlgos.insertionSort(reversed(n)))
        }
    }

    @Test
    fun `average case lands between the two bounds and near n squared over 4`() {
        // CaseAnalysisTool prints "≈ n² / 4" next to the measured count for shuffled input.
        val n = 64
        val counts = (1..40).map {
            InstrumentedAlgos.insertionSort((0 until n).shuffled(Random(it)).toIntArray())
        }
        val mean = counts.average()
        assertTrue("mean $mean below best case", mean > n - 1)
        assertTrue("mean $mean above worst case", mean < n * (n - 1) / 2.0)
        assertEquals(n * n / 4.0, mean, n * n / 16.0)
    }

    @Test
    fun `sorts actually sort`() {
        val expected = sorted(24).toList()
        assertEquals(expected, reversed(24).also { InstrumentedAlgos.insertionSort(it) }.toList())
        assertEquals(expected, reversed(24).also { InstrumentedAlgos.bubbleSort(it) }.toList())
        val shuffled = (0 until 24).shuffled(Random(7)).toIntArray()
        assertEquals(expected, shuffled.also { InstrumentedAlgos.quicksort(it) }.toList())
    }

    @Test
    fun `bubble sort always runs the full n choose 2 comparisons`() {
        for (n in 2..24) {
            assertEquals(n * (n - 1) / 2, InstrumentedAlgos.bubbleSort(reversed(n)))
            assertEquals(n * (n - 1) / 2, InstrumentedAlgos.bubbleSort(sorted(n)))
        }
    }

    @Test
    fun `Lomuto quicksort degrades to quadratic on already-sorted input`() {
        // SandboxTool offers quicksort + a "sorted" input shape; this is the pairing that surprises.
        for (n in 2..24) {
            assertEquals(n * (n - 1) / 2, InstrumentedAlgos.quicksort(sorted(n)))
        }
    }

    @Test
    fun `two-sum trades pair checks for memory`() {
        val n = 24
        val arr = sorted(n)
        assertEquals(n * (n - 1) / 2, InstrumentedAlgos.twoSumBrute(arr, -1))
        val (ops, mem) = InstrumentedAlgos.twoSumHash(arr, -1)
        assertEquals(n, ops)
        assertEquals(n, mem)
    }

    @Test
    fun `dynamic array pushes cost 1 except at power-of-two resizes and amortize to O(1)`() {
        val costs = InstrumentedAlgos.dynamicArrayPushCosts(16)
        val spikes = costs.indices.filter { costs[it] > 1 }
        assertEquals(listOf(1, 2, 4, 8), spikes)
        assertEquals(listOf(2, 3, 5, 9), spikes.map { costs[it] })
        // 16 writes + copies of 1+2+4+8 elements.
        assertEquals(31, costs.sum())
        // AmortizedAnalysisTool's claim: the per-push average stays under a small constant.
        for (pushes in listOf(16, 64, 256, 1024)) {
            val amortized = InstrumentedAlgos.dynamicArrayPushCosts(pushes).sum().toDouble() / pushes
            assertTrue("amortized $amortized at $pushes pushes", amortized < 3.0)
        }
    }

    @Test
    fun `complexity curves are base-2, matching the counts the tools measure`() {
        val fn = complexityCurves.associate { it.label to it.fn }
        assertEquals(1.0, fn.getValue("O(1)")(64.0), 1e-9)
        assertEquals(4.0, fn.getValue("O(log n)")(16.0), 1e-9)
        assertEquals(6.0, fn.getValue("O(log n)")(64.0), 1e-9)
        assertEquals(64.0, fn.getValue("O(n)")(64.0), 1e-9)
        assertEquals(24.0, fn.getValue("O(n log n)")(8.0), 1e-9)
        assertEquals(144.0, fn.getValue("O(n²)")(12.0), 1e-9)

        // The legend and the instrumented code must not disagree: the O(log n) curve at n is what
        // binary search really costs on a sorted array of that size.
        for (n in listOf(16, 32, 64)) {
            val curveValue = fn.getValue("O(log n)")(n.toDouble())
            assertEquals(
                "curve vs measured at n=$n",
                InstrumentedAlgos.binarySearch(IntArray(n) { it * 2 }, -1).toDouble(),
                curveValue,
                1e-9,
            )
        }
    }

    @Test
    fun `curves are ordered slowest-growing to fastest at a large n`() {
        val values = complexityCurves.map { it.fn(100.0) }
        assertEquals(values.sorted(), values)
    }

    @Test
    fun `every Analysis topic resolves to a built tool`() {
        val missing = AnalysisTopics.topics.filter { AnalysisToolRegistry.get(it.id) == null }
        assertEquals("topics with no registered tool", emptyList<String>(), missing.map { it.id })
        assertNotNull(AnalysisToolRegistry.get("operation_counter"))
        assertNull(AnalysisToolRegistry.get("not_a_tool"))
    }
}
