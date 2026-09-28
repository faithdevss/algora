package com.algora.app.feature.analysis.tools.common

// Real instrumented implementations. Every function returns the actual count of the operation it
// increments (comparisons, unless noted) — what the loop below executed, not a formula estimate.
// Callers pass a fresh/cloned array; sorts mutate their input.
object InstrumentedAlgos {

    fun linearSearch(arr: IntArray, target: Int): Int {
        var comparisons = 0
        for (value in arr) {
            comparisons++
            if (value == target) break
        }
        return comparisons
    }

    fun binarySearch(arr: IntArray, target: Int): Int {
        var comparisons = 0
        var lo = 0
        var hi = arr.size - 1
        while (lo <= hi) {
            val mid = lo + (hi - lo) / 2
            comparisons++
            when {
                arr[mid] == target -> break
                arr[mid] < target -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return comparisons
    }

    // Counts every evaluation of the inner `arr[j] > key` guard, including the terminal one that
    // ends the shift. Sorted input → n-1 comparisons; reverse-sorted → n(n-1)/2 (true worst case).
    fun insertionSort(arr: IntArray): Int {
        var comparisons = 0
        for (i in 1 until arr.size) {
            val key = arr[i]
            var j = i - 1
            while (j >= 0) {
                comparisons++
                if (arr[j] <= key) break
                arr[j + 1] = arr[j]
                j--
            }
            arr[j + 1] = key
        }
        return comparisons
    }

    fun bubbleSort(arr: IntArray): Int {
        var comparisons = 0
        for (i in 0 until arr.size - 1) {
            for (j in 0 until arr.size - 1 - i) {
                comparisons++
                if (arr[j] > arr[j + 1]) {
                    val tmp = arr[j]; arr[j] = arr[j + 1]; arr[j + 1] = tmp
                }
            }
        }
        return comparisons
    }

    fun quicksort(arr: IntArray): Int {
        var comparisons = 0
        fun sort(lo: Int, hi: Int) {
            if (lo >= hi) return
            val pivot = arr[hi]
            var i = lo
            for (j in lo until hi) {
                comparisons++
                if (arr[j] < pivot) {
                    val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
                    i++
                }
            }
            val tmp = arr[i]; arr[i] = arr[hi]; arr[hi] = tmp
            sort(lo, i - 1)
            sort(i + 1, hi)
        }
        sort(0, arr.size - 1)
        return comparisons
    }

    // O(n²) two-sum — returns the number of pair checks performed.
    fun twoSumBrute(arr: IntArray, target: Int): Int {
        var ops = 0
        for (i in arr.indices) {
            for (j in i + 1 until arr.size) {
                ops++
                if (arr[i] + arr[j] == target) return ops
            }
        }
        return ops
    }

    // O(n) two-sum — returns ops (lookups+inserts) and the peak size of the seen-set (extra memory).
    fun twoSumHash(arr: IntArray, target: Int): Pair<Int, Int> {
        var ops = 0
        val seen = HashSet<Int>()
        for (value in arr) {
            ops++
            if (target - value in seen) return ops to seen.size
            seen += value
        }
        return ops to seen.size
    }

    // Dynamic array (doubling) push. Returns the per-push cost: 1 for a plain write, or 1 + old
    // capacity when a resize copies existing elements. Amortizes to O(1) across the sequence.
    fun dynamicArrayPushCosts(pushes: Int): IntArray {
        val costs = IntArray(pushes)
        var capacity = 1
        var size = 0
        for (i in 0 until pushes) {
            var cost = 1
            if (size == capacity) {
                cost += capacity // copy existing elements into the new backing array
                capacity *= 2
            }
            size++
            costs[i] = cost
        }
        return costs
    }
}
