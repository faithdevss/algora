package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// Second half of the problem bank: the core techniques that are not Interview Prep "patterns" but
// show up underneath most of them — binary search, prefix sums, hashing, monotonic stacks,
// traversal, DP and backtracking. Patterns here link to Algorithms/Data Structures topics.

internal val corePatterns = listOf(
    ProblemPattern(
        id = "binary_search",
        name = "Binary Search",
        blurb = "Halve the search space each step — over an index range, or over the answer itself.",
        accentColor = 0xFF0EA5E9,
        topicId = "binary_search",
        isPremium = true,
    ),
    ProblemPattern(
        id = "prefix_sum",
        name = "Prefix Sums",
        blurb = "Precompute cumulative totals so any range answer becomes one subtraction.",
        accentColor = 0xFF14B8A6,
        topicId = "prefix_sum",
        isPremium = true,
    ),
    ProblemPattern(
        id = "hashing",
        name = "Hashing & Frequency",
        blurb = "Trade memory for time: a map turns a nested scan into a single pass.",
        accentColor = 0xFFA855F7,
        topicId = "hash_table",
        isPremium = true,
    ),
    ProblemPattern(
        id = "monotonic_stack",
        name = "Stack & Monotonic Stack",
        blurb = "A stack that stays sorted answers \"next greater\" and \"span\" questions in one pass.",
        accentColor = 0xFFF97316,
        topicId = "stack",
        isPremium = true,
    ),
    ProblemPattern(
        id = "traversal",
        name = "Tree & Graph Traversal",
        blurb = "DFS goes deep with recursion, BFS goes wide with a queue — same graph, different order.",
        accentColor = 0xFF22C55E,
        topicId = "bfs",
        isPremium = true,
    ),
    ProblemPattern(
        id = "dynamic_programming",
        name = "Dynamic Programming",
        blurb = "Define a state, write its recurrence, then fill it once instead of recomputing.",
        accentColor = 0xFF6366F1,
        topicId = "fibonacci_dp",
        isPremium = true,
    ),
    ProblemPattern(
        id = "backtracking",
        name = "Backtracking",
        blurb = "Build a candidate, recurse, then undo the last choice and try the next.",
        accentColor = 0xFFEC4899,
        topicId = "n_queens",
        isPremium = true,
    ),
)

private val binarySearchProblems = listOf(
    PracticeProblem(
        id = "classic_binary_search",
        title = "Binary Search",
        patternId = "binary_search",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given a sorted array of distinct integers and a target, return the target's index or -1 if it is absent.",
        examples = listOf(
            ProblemExample("nums = [-1, 0, 3, 5, 9, 12], target = 9", "4", ""),
            ProblemExample("nums = [-1, 0, 3, 5, 9, 12], target = 2", "-1", "Not present."),
        ),
        constraints = listOf(
            "The array is sorted ascending with no duplicates.",
            "Required runtime is O(log n).",
        ),
        hints = listOf(
            "Compare against the middle element. What does the comparison tell you about *half* the array?",
            "If the middle is too small, every element left of it is too small as well — discard that whole side.",
            "Compute mid as lo + (hi - lo) / 2 rather than (lo + hi) / 2 to avoid overflow on large bounds.",
        ),
        approach = listOf(
            "Hold an inclusive range [lo, hi] that still might contain the target.",
            "Look at the middle. Equal → done. Too small → the answer is in [mid + 1, hi]. Too large → it is in [lo, mid - 1].",
            "Loop while lo ≤ hi; the range shrinks by half every iteration so it terminates in log₂ n steps.",
            "Falling out of the loop means the range emptied — the target is absent.",
        ),
        timeComplexity = "O(log n)",
        spaceComplexity = "O(1) iteratively",
        solutionCode = """
            fun search(nums: IntArray, target: Int): Int {
                var lo = 0
                var hi = nums.size - 1

                while (lo <= hi) {
                    val mid = lo + (hi - lo) / 2   // overflow-safe midpoint
                    when {
                        nums[mid] == target -> return mid
                        nums[mid] < target -> lo = mid + 1
                        else -> hi = mid - 1
                    }
                }
                return -1
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("array", "Array", "O(1) access by index is what makes jumping to the middle free."),
            ProblemPrereq("binary_search", "Binary Search", "The loop invariant and the mid computation are the topic itself."),
        ),
        linkedTopicId = "binary_search",
        linkedTopicLabel = "Binary Search",
    ),
    PracticeProblem(
        id = "search_rotated_array",
        title = "Search in a Rotated Sorted Array",
        patternId = "binary_search",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "A sorted array was rotated at an unknown pivot. Find a target's index in O(log n), or return -1.",
        examples = listOf(
            ProblemExample("nums = [4,5,6,7,0,1,2], target = 0", "4", "Rotated at index 4."),
            ProblemExample("nums = [4,5,6,7,0,1,2], target = 3", "-1", ""),
        ),
        constraints = listOf(
            "All values are distinct.",
            "The array is a rotation of an ascending sorted array.",
        ),
        hints = listOf(
            "The array is not sorted overall, but cut it at any midpoint and consider the two halves.",
            "At least one half is always fully sorted — compare nums[lo] with nums[mid] to find out which.",
            "Inside the sorted half you can range-check the target exactly; if it is not there, it must be in the other half.",
        ),
        approach = listOf(
            "Run a normal binary search loop, but before choosing a side, work out which half is sorted.",
            "If nums[lo] ≤ nums[mid], the left half is sorted: keep it only when nums[lo] ≤ target < nums[mid].",
            "Otherwise the right half is sorted: keep it only when nums[mid] < target ≤ nums[hi].",
            "Each iteration still discards half the range, preserving O(log n).",
        ),
        timeComplexity = "O(log n)",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun searchRotated(nums: IntArray, target: Int): Int {
                var lo = 0
                var hi = nums.size - 1

                while (lo <= hi) {
                    val mid = lo + (hi - lo) / 2
                    if (nums[mid] == target) return mid

                    if (nums[lo] <= nums[mid]) {
                        // Left half is sorted — can we range-check the target into it?
                        if (target >= nums[lo] && target < nums[mid]) hi = mid - 1 else lo = mid + 1
                    } else {
                        // Right half is sorted.
                        if (target > nums[mid] && target <= nums[hi]) lo = mid + 1 else hi = mid - 1
                    }
                }
                return -1
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search", "Binary Search", "This is binary search with an extra decision before halving."),
            ProblemPrereq("array", "Array", "Rotation is an index shift over a contiguous block."),
        ),
        linkedTopicId = "binary_search",
        linkedTopicLabel = "Binary Search",
    ),
    PracticeProblem(
        id = "min_eating_speed",
        title = "Minimum Eating Speed",
        patternId = "binary_search",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given piles of bananas and h hours, find the smallest integer eating speed k such that eating ⌈pile / k⌉ hours per pile finishes every pile within h hours.",
        examples = listOf(
            ProblemExample("piles = [3, 6, 7, 11], h = 8", "4", "Speed 4 takes 1 + 2 + 2 + 3 = 8 hours."),
            ProblemExample("piles = [30, 11, 23, 4, 20], h = 6", "23", ""),
        ),
        constraints = listOf(
            "1 ≤ piles.size ≤ h ≤ 10⁹",
            "Only one pile may be eaten per hour, even if the speed exceeds it.",
        ),
        hints = listOf(
            "There is nothing sorted to search — but list the candidate answers 1, 2, 3, … max(pile). What shape do they have?",
            "If speed k finishes in time, so does every speed above it. The feasibility test is monotone, which is all binary search needs.",
            "So binary search the answer range and use 'does k fit in h hours?' as the comparison.",
        ),
        approach = listOf(
            "Bound the answer: at least 1, at most the largest pile (any faster changes nothing).",
            "For a candidate k, compute total hours as the sum of ⌈pile / k⌉, written as (pile + k - 1) / k in integer math.",
            "Feasible → the answer is k or smaller, so move hi to mid. Infeasible → move lo to mid + 1.",
            "Loop while lo < hi and return lo — the smallest feasible speed. Sum in Long, since piles and h reach 10⁹.",
        ),
        timeComplexity = "O(n log m) where m is the largest pile",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun minEatingSpeed(piles: IntArray, h: Int): Int {
                var lo = 1
                var hi = piles.max()

                while (lo < hi) {
                    val mid = lo + (hi - lo) / 2
                    // Ceiling division without floating point; Long guards against overflow.
                    val hours = piles.sumOf { ((it + mid - 1) / mid).toLong() }
                    if (hours <= h) hi = mid else lo = mid + 1
                }
                return lo
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search", "Binary Search", "Binary searching the answer space, not an array — the key generalisation."),
            ProblemPrereq("linear_search", "Linear Search", "The O(m) baseline you are replacing makes the speedup concrete."),
        ),
        linkedTopicId = "binary_search",
        linkedTopicLabel = "Binary Search",
    ),
)

private val prefixSumProblems = listOf(
    PracticeProblem(
        id = "range_sum_query",
        title = "Range Sum Query (Immutable)",
        patternId = "prefix_sum",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given a fixed integer array, answer many queries of the form \"sum of elements from index left to right inclusive\".",
        examples = listOf(
            ProblemExample("nums = [-2, 0, 3, -5, 2, -1]; sumRange(0, 2)", "1", "-2 + 0 + 3."),
            ProblemExample("sumRange(2, 5)", "-1", "3 - 5 + 2 - 1."),
        ),
        constraints = listOf(
            "The array never changes after construction.",
            "Up to 10⁴ queries — each must be O(1).",
        ),
        hints = listOf(
            "Looping per query is O(n) each time. What can be computed once, up front?",
            "Store prefix[i] = sum of the first i elements. Then any range sum is a difference of two prefixes.",
            "Size the prefix array n + 1 with prefix[0] = 0, and the left = 0 case needs no special handling.",
        ),
        approach = listOf(
            "Build prefix of length n + 1 where prefix[i + 1] = prefix[i] + nums[i].",
            "Answer sumRange(left, right) as prefix[right + 1] - prefix[left].",
            "The subtraction cancels everything before `left`, leaving exactly the requested range.",
            "Construction is O(n) once; every query afterwards is O(1).",
        ),
        timeComplexity = "O(n) to build, O(1) per query",
        spaceComplexity = "O(n) for the prefix array",
        solutionCode = """
            class NumArray(nums: IntArray) {
                private val prefix = IntArray(nums.size + 1)

                init {
                    // prefix[i + 1] holds the sum of the first i + 1 elements.
                    for (i in nums.indices) prefix[i + 1] = prefix[i] + nums[i]
                }

                fun sumRange(left: Int, right: Int): Int = prefix[right + 1] - prefix[left]
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("prefix_sum", "Prefix Sum", "The technique in its purest form — one build, O(1) queries."),
            ProblemPrereq("array", "Array", "The prefix table is itself an array indexed in O(1)."),
        ),
        linkedTopicId = "prefix_sum",
        linkedTopicLabel = "Prefix Sum",
    ),
    PracticeProblem(
        id = "subarray_sum_equals_k",
        title = "Subarray Sum Equals K",
        patternId = "prefix_sum",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given an integer array and a target k, count how many contiguous subarrays sum to exactly k. Values may be negative.",
        examples = listOf(
            ProblemExample("nums = [1, 1, 1], k = 2", "2", "[1,1] at indices 0-1 and 1-2."),
            ProblemExample("nums = [1, 2, 3], k = 3", "2", "[1,2] and [3]."),
        ),
        constraints = listOf(
            "Negative numbers are allowed — a sliding window will not work.",
            "1 ≤ nums.size ≤ 2 × 10⁴",
        ),
        hints = listOf(
            "A subarray sum is a difference of two prefix sums: sum(i..j) = prefix[j] - prefix[i - 1].",
            "So at index j you are asking: how many earlier prefixes equal prefix[j] - k?",
            "Count prefixes in a hash map as you go, seeding it with prefix 0 seen once so subarrays starting at index 0 are counted.",
        ),
        approach = listOf(
            "Keep a running prefix sum and a map from prefix value to how many times it has occurred.",
            "Seed the map with 0 → 1, representing the empty prefix before the array starts.",
            "At each index, add map[running - k] to the answer — every earlier matching prefix closes a valid subarray here.",
            "Then record the current running sum. Negatives are handled automatically since nothing assumes monotonicity.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(n) for the prefix-count map",
        solutionCode = """
            fun subarraySum(nums: IntArray, k: Int): Int {
                val counts = HashMap<Int, Int>()
                counts[0] = 1          // empty prefix, so subarrays from index 0 count

                var running = 0
                var total = 0
                for (n in nums) {
                    running += n
                    total += counts[running - k] ?: 0
                    counts[running] = (counts[running] ?: 0) + 1
                }
                return total
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("prefix_sum", "Prefix Sum", "The identity sum(i..j) = prefix[j] - prefix[i-1] is the whole idea."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Counting how often each prefix occurred must be O(1)."),
        ),
        linkedTopicId = "prefix_sum",
        linkedTopicLabel = "Prefix Sum",
    ),
    PracticeProblem(
        id = "contiguous_array_balance",
        title = "Contiguous Array of Equal 0s and 1s",
        patternId = "prefix_sum",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a binary array, return the length of the longest contiguous subarray containing an equal number of 0s and 1s.",
        examples = listOf(
            ProblemExample("nums = [0, 1]", "2", "The whole array is balanced."),
            ProblemExample("nums = [0, 1, 0]", "2", "Either [0,1] or [1,0]."),
        ),
        constraints = listOf(
            "Elements are 0 or 1 only.",
            "1 ≤ nums.size ≤ 10⁵",
        ),
        hints = listOf(
            "\"Equal counts\" is awkward. Re-encode 0 as -1 and ask what a balanced subarray sums to.",
            "It sums to zero — so you want the longest range whose prefix sums are equal at both ends.",
            "Record the *first* index each running balance appeared at; a later repeat gives the longest span for that balance.",
        ),
        approach = listOf(
            "Walk the array keeping a balance that increments on 1 and decrements on 0.",
            "Store balance → first index seen, seeded with 0 → -1 so a prefix that balances on its own is measured correctly.",
            "When a balance repeats, the segment between the two occurrences nets to zero — its length is i - firstIndex[balance].",
            "Never overwrite an existing entry; the earliest occurrence always yields the longest candidate.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(n) for the balance map",
        solutionCode = """
            fun findMaxLength(nums: IntArray): Int {
                val firstIndex = HashMap<Int, Int>()
                firstIndex[0] = -1     // balance 0 "occurred" before the array

                var balance = 0
                var best = 0
                for (i in nums.indices) {
                    balance += if (nums[i] == 1) 1 else -1
                    val seen = firstIndex[balance]
                    if (seen != null) {
                        best = maxOf(best, i - seen)
                    } else {
                        firstIndex[balance] = i   // keep the earliest only
                    }
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("prefix_sum", "Prefix Sum", "Equal prefixes at two indices mean the range between them nets to zero."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "First-occurrence lookup per balance value."),
        ),
        linkedTopicId = "prefix_sum",
        linkedTopicLabel = "Prefix Sum",
    ),
)

private val hashingProblems = listOf(
    PracticeProblem(
        id = "two_sum_hash",
        title = "Two Sum",
        patternId = "hashing",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given an unsorted array and a target, return the indices of the two numbers that add up to the target.",
        examples = listOf(
            ProblemExample("nums = [2, 7, 11, 15], target = 9", "[0, 1]", ""),
            ProblemExample("nums = [3, 2, 4], target = 6", "[1, 2]", "Not [0, 0] — an element cannot be reused."),
        ),
        constraints = listOf(
            "Exactly one valid answer exists.",
            "The same element may not be used twice.",
        ),
        hints = listOf(
            "The nested loop asks \"is the complement anywhere else in the array?\" n times.",
            "That question is a lookup, not a scan — if you have already seen the complement, you are done.",
            "Store each value → index as you pass it, and check for the complement *before* inserting the current value.",
        ),
        approach = listOf(
            "Walk the array once, maintaining a map from value to the index it was seen at.",
            "At index i, compute need = target - nums[i] and look it up.",
            "A hit means the pair is (storedIndex, i) — return immediately.",
            "Otherwise record nums[i] → i. Checking before inserting is what prevents pairing an element with itself.",
        ),
        timeComplexity = "O(n) — one pass with O(1) lookups",
        spaceComplexity = "O(n) for the map",
        solutionCode = """
            fun twoSum(nums: IntArray, target: Int): IntArray {
                val seen = HashMap<Int, Int>()   // value -> index

                for (i in nums.indices) {
                    val need = target - nums[i]
                    seen[need]?.let { return intArrayOf(it, i) }
                    seen[nums[i]] = i            // insert after the check
                }
                return intArrayOf(-1, -1)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Average O(1) insert and lookup is what buys the speedup."),
            ProblemPrereq("array", "Array", "Indices are the answer, so index-order matters as you scan."),
        ),
        linkedTopicId = "hash_table",
        linkedTopicLabel = "Hash Table / Hash Map",
    ),
    PracticeProblem(
        id = "group_anagrams",
        title = "Group Anagrams",
        patternId = "hashing",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given an array of strings, group together all words that are anagrams of one another.",
        examples = listOf(
            ProblemExample("[\"eat\", \"tea\", \"tan\", \"ate\", \"nat\", \"bat\"]", "[[\"eat\",\"tea\",\"ate\"], [\"tan\",\"nat\"], [\"bat\"]]", "Group order does not matter."),
            ProblemExample("[\"\"]", "[[\"\"]]", ""),
        ),
        constraints = listOf(
            "Words are lowercase English letters.",
            "1 ≤ words.size ≤ 10⁴",
        ),
        hints = listOf(
            "Comparing every pair is O(n² · L). Instead, give each word a fingerprint that anagrams share.",
            "Sorted letters work: \"eat\" and \"tea\" both become \"aet\" — that costs O(L log L) per word.",
            "A 26-slot letter count is an O(L) fingerprint, faster when words are long.",
        ),
        approach = listOf(
            "Choose a canonical key that is identical for anagrams and different otherwise.",
            "Count letters into a 26-element array and stringify it — collisions are impossible for lowercase input.",
            "Append each word into the bucket for its key using getOrPut.",
            "The map's values are the groups; no sorting of the output is needed.",
        ),
        timeComplexity = "O(n · L) with counting keys, where L is the average word length",
        spaceComplexity = "O(n · L) for the grouped output",
        solutionCode = """
            fun groupAnagrams(words: Array<String>): List<List<String>> {
                val groups = HashMap<String, MutableList<String>>()

                for (w in words) {
                    val counts = IntArray(26)
                    for (c in w) counts[c - 'a']++
                    val key = counts.joinToString(",")   // O(L) fingerprint
                    groups.getOrPut(key) { mutableListOf() }.add(w)
                }
                return groups.values.toList()
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Bucketing by a derived key is the core move."),
            ProblemPrereq("string", "String", "The key is built by walking each word's characters."),
            ProblemPrereq("counting_sort", "Counting Sort", "The 26-slot letter tally is the same counting idea, reused as a fingerprint."),
        ),
        linkedTopicId = "hash_table",
        linkedTopicLabel = "Hash Table / Hash Map",
    ),
    PracticeProblem(
        id = "longest_consecutive_sequence",
        title = "Longest Consecutive Sequence",
        patternId = "hashing",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given an unsorted array of integers, return the length of the longest run of consecutive integers. Required runtime is O(n).",
        examples = listOf(
            ProblemExample("nums = [100, 4, 200, 1, 3, 2]", "4", "The run 1, 2, 3, 4."),
            ProblemExample("nums = [0,3,7,2,5,8,4,6,0,1]", "9", ""),
        ),
        constraints = listOf(
            "Duplicates may appear.",
            "O(n) required — sorting is explicitly too slow.",
        ),
        hints = listOf(
            "Sorting gives the answer in O(n log n). The O(n) bound rules it out, so membership must be a lookup.",
            "Put everything in a set. Now \"is n + 1 present?\" is O(1) and you can walk a run forward.",
            "Walking from every element is quadratic. Only start walking from an element that begins a run — one with no n - 1 in the set.",
        ),
        approach = listOf(
            "Load all values into a hash set, which also removes duplicates.",
            "For each value, skip it unless it is a run start (no predecessor in the set).",
            "From a run start, count forward while n + length stays in the set.",
            "Every element is visited by at most one forward walk, so the total work is linear despite the nested loop.",
        ),
        timeComplexity = "O(n) amortized — each value participates in exactly one run walk",
        spaceComplexity = "O(n) for the set",
        solutionCode = """
            fun longestConsecutive(nums: IntArray): Int {
                val set = nums.toHashSet()
                var best = 0

                for (n in set) {
                    if (set.contains(n - 1)) continue   // not the start of a run

                    var length = 1
                    while (set.contains(n + length)) length++
                    best = maxOf(best, length)
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "O(1) membership is what replaces sorting."),
            ProblemPrereq("set_adt", "Set", "The dedup-and-contains behaviour of a set is used directly."),
        ),
        linkedTopicId = "hash_table",
        linkedTopicLabel = "Hash Table / Hash Map",
    ),
)

private val stackProblems = listOf(
    PracticeProblem(
        id = "valid_parentheses",
        title = "Valid Parentheses",
        patternId = "monotonic_stack",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given a string of brackets (), [] and {}, decide whether every bracket is closed by the matching type in the correct order.",
        examples = listOf(
            ProblemExample("s = \"()[]{}\"", "true", ""),
            ProblemExample("s = \"([)]\"", "false", "Correct types, wrong nesting order."),
        ),
        constraints = listOf(
            "The string contains bracket characters only.",
            "1 ≤ s.length ≤ 10⁴",
        ),
        hints = listOf(
            "Counting brackets is not enough — \"([)]\" has balanced counts and is still invalid.",
            "Order matters, and the bracket that must close next is always the most recently opened one.",
            "\"Most recent first\" is exactly LIFO — a stack.",
        ),
        approach = listOf(
            "Push every opening bracket.",
            "On a closing bracket, pop and check the popped opener matches the expected type; a mismatch or an empty stack means invalid.",
            "After the scan, a non-empty stack means unclosed openers remain.",
            "One pass, and the stack never holds more than the current nesting depth.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(n) worst case, when the string is all openers",
        solutionCode = """
            fun isValid(s: String): Boolean {
                val stack = ArrayDeque<Char>()
                val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')

                for (c in s) {
                    val expectedOpener = pairs[c]
                    if (expectedOpener == null) {
                        stack.addLast(c)                                  // an opener
                    } else if (stack.removeLastOrNull() != expectedOpener) {
                        return false                                      // mismatch or nothing to close
                    }
                }
                return stack.isEmpty()
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("stack", "Stack", "LIFO order is exactly what nesting requires."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "The closer → opener table is a small map lookup."),
        ),
        linkedTopicId = "stack",
        linkedTopicLabel = "Stack",
    ),
    PracticeProblem(
        id = "daily_temperatures",
        title = "Daily Temperatures",
        patternId = "monotonic_stack",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "For each day, return how many days you must wait for a warmer temperature. Answer 0 for days with no warmer future day.",
        examples = listOf(
            ProblemExample("temps = [73,74,75,71,69,72,76,73]", "[1,1,4,2,1,1,0,0]", ""),
            ProblemExample("temps = [30, 40, 50, 60]", "[1,1,1,0]", "Strictly increasing."),
        ),
        constraints = listOf(
            "1 ≤ temps.size ≤ 10⁵",
            "The brute force is O(n²) — aim for O(n).",
        ),
        hints = listOf(
            "This is \"next greater element\" for every index at once.",
            "Days still waiting for a warmer day form a decreasing sequence — a warmer day resolves all of them at once.",
            "Keep those unresolved indices on a stack whose temperatures decrease from bottom to top.",
        ),
        approach = listOf(
            "Walk the days, keeping a stack of indices whose answers are still unknown.",
            "Before pushing day i, pop every index whose temperature is lower than temps[i] — day i is their answer, at distance i - j.",
            "Push i; the stack stays decreasing by construction.",
            "Indices left on the stack at the end never found a warmer day and keep their default 0.",
        ),
        timeComplexity = "O(n) — each index is pushed once and popped at most once",
        spaceComplexity = "O(n) for the stack",
        solutionCode = """
            fun dailyTemperatures(temps: IntArray): IntArray {
                val answer = IntArray(temps.size)
                val stack = ArrayDeque<Int>()   // indices, temperatures decreasing bottom → top

                for (i in temps.indices) {
                    while (stack.isNotEmpty() && temps[i] > temps[stack.last()]) {
                        val j = stack.removeLast()
                        answer[j] = i - j       // day i resolves day j
                    }
                    stack.addLast(i)
                }
                return answer                   // unresolved days stay 0
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("stack", "Stack", "The monotonic stack is a plain stack with an ordering invariant."),
            ProblemPrereq("array", "Array", "Answers are written back by index during the same pass."),
        ),
        linkedTopicId = "stack",
        linkedTopicLabel = "Stack",
    ),
    PracticeProblem(
        id = "largest_rectangle_histogram",
        title = "Largest Rectangle in a Histogram",
        patternId = "monotonic_stack",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given bar heights of a histogram where each bar has width 1, find the area of the largest rectangle that fits inside.",
        examples = listOf(
            ProblemExample("heights = [2,1,5,6,2,3]", "10", "Bars 5 and 6 give 5 × 2."),
            ProblemExample("heights = [2, 4]", "4", ""),
        ),
        constraints = listOf(
            "1 ≤ heights.size ≤ 10⁵",
            "0 ≤ height ≤ 10⁴",
        ),
        hints = listOf(
            "Every maximal rectangle is limited by some bar's full height. Fix that bar and ask how far it can extend.",
            "It extends until a strictly shorter bar appears on each side — so you need the previous and next smaller element.",
            "An increasing stack gives both: when a bar is popped, the incoming bar is its next-smaller and the new stack top is its previous-smaller.",
        ),
        approach = listOf(
            "Keep a stack of indices whose heights increase from bottom to top.",
            "When the incoming height is not larger, pop: the popped bar's rectangle spans from the element below it on the stack to the current index, exclusive at both ends.",
            "Width is i - left - 1 where left is the new stack top (or -1 when the stack empties) — this handles the popped bar extending back past several taller bars.",
            "Run one extra iteration with a virtual height of 0 to flush anything still on the stack.",
        ),
        timeComplexity = "O(n) — each index is pushed and popped once",
        spaceComplexity = "O(n)",
        solutionCode = """
            fun largestRectangleArea(heights: IntArray): Int {
                val stack = ArrayDeque<Int>()   // indices, heights increasing
                var best = 0

                // The extra i == size step uses height 0 to drain the stack.
                for (i in 0..heights.size) {
                    val h = if (i == heights.size) 0 else heights[i]

                    while (stack.isNotEmpty() && heights[stack.last()] >= h) {
                        val height = heights[stack.removeLast()]
                        val left = if (stack.isEmpty()) -1 else stack.last()
                        best = maxOf(best, height * (i - left - 1))
                    }
                    stack.addLast(i)
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("stack", "Stack", "Previous-smaller and next-smaller both fall out of one monotonic stack."),
            ProblemPrereq("array", "Array", "Widths are index arithmetic over a contiguous block."),
        ),
        linkedTopicId = "stack",
        linkedTopicLabel = "Stack",
    ),
)

private val traversalProblems = listOf(
    PracticeProblem(
        id = "max_depth_binary_tree",
        title = "Maximum Depth of a Binary Tree",
        patternId = "traversal",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given the root of a binary tree, return its maximum depth — the number of nodes on the longest root-to-leaf path.",
        examples = listOf(
            ProblemExample("root = [3, 9, 20, null, null, 15, 7]", "3", ""),
            ProblemExample("root = null", "0", "An empty tree has depth 0."),
        ),
        constraints = listOf(
            "0 ≤ node count ≤ 10⁴",
            "The tree is not necessarily balanced.",
        ),
        hints = listOf(
            "Depth of a tree is defined in terms of the depth of its subtrees — that is a recurrence.",
            "depth(node) = 1 + max(depth(left), depth(right)).",
            "The base case is the null child, whose depth is 0.",
        ),
        approach = listOf(
            "Recurse into both children and take the larger result.",
            "Add one for the current node.",
            "A null node returns 0, which terminates the recursion and makes leaves return 1.",
            "This is a post-order DFS: both children are resolved before the node's own answer exists.",
        ),
        timeComplexity = "O(n) — every node is visited once",
        spaceComplexity = "O(h) for the call stack, where h is the height (O(n) if degenerate)",
        solutionCode = """
            class TreeNode(val value: Int) {
                var left: TreeNode? = null
                var right: TreeNode? = null
            }

            fun maxDepth(root: TreeNode?): Int =
                if (root == null) 0
                else 1 + maxOf(maxDepth(root.left), maxDepth(root.right))
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("tree", "Tree", "Node/child structure and the definition of height."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Post-order recursion is DFS on a tree."),
        ),
        linkedTopicId = "dfs",
        linkedTopicLabel = "Depth-First Search (DFS)",
    ),
    PracticeProblem(
        id = "level_order_traversal",
        title = "Binary Tree Level Order Traversal",
        patternId = "traversal",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Return the node values of a binary tree grouped level by level, from the root downwards.",
        examples = listOf(
            ProblemExample("root = [3, 9, 20, null, null, 15, 7]", "[[3], [9, 20], [15, 7]]", ""),
            ProblemExample("root = null", "[]", ""),
        ),
        constraints = listOf(
            "0 ≤ node count ≤ 2000",
            "Each inner list must contain exactly one level.",
        ),
        hints = listOf(
            "Recursion goes deep first, which is the wrong order here — you need to finish a level before descending.",
            "A queue gives you that: enqueue children as you dequeue parents.",
            "To know where one level ends, snapshot the queue size before draining it.",
        ),
        approach = listOf(
            "Seed a queue with the root (returning early on an empty tree).",
            "Each round, read the current queue size — that count is exactly the current level's width.",
            "Dequeue that many nodes into one list, enqueuing each node's non-null children behind them.",
            "Repeat until the queue drains. The size snapshot is what separates levels.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(w) where w is the widest level",
        solutionCode = """
            fun levelOrder(root: TreeNode?): List<List<Int>> {
                if (root == null) return emptyList()

                val out = mutableListOf<List<Int>>()
                val queue = ArrayDeque<TreeNode>()
                queue.addLast(root)

                while (queue.isNotEmpty()) {
                    val levelSize = queue.size      // snapshot before draining
                    val level = ArrayList<Int>(levelSize)

                    repeat(levelSize) {
                        val node = queue.removeFirst()
                        level += node.value
                        node.left?.let { queue.addLast(it) }
                        node.right?.let { queue.addLast(it) }
                    }
                    out += level
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("queue", "Queue", "FIFO order is what produces level-by-level visiting."),
            ProblemPrereq("bfs", "Breadth-First Search (BFS)", "This is textbook BFS with a level-size snapshot."),
            ProblemPrereq("tree", "Tree", "Children are enqueued through the node structure."),
        ),
        linkedTopicId = "bfs",
        linkedTopicLabel = "Breadth-First Search (BFS)",
    ),
    PracticeProblem(
        id = "number_of_islands",
        title = "Number of Islands",
        patternId = "traversal",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a grid of '1' (land) and '0' (water), count the islands. An island is land connected horizontally or vertically.",
        examples = listOf(
            ProblemExample("[[1,1,0],[1,0,0],[0,0,1]]", "2", "The top-left blob and the bottom-right cell."),
            ProblemExample("[[1,1,1],[1,1,1]]", "1", "All connected."),
        ),
        constraints = listOf(
            "1 ≤ rows, cols ≤ 300",
            "Diagonal neighbours do not connect.",
        ),
        hints = listOf(
            "A grid is a graph: each land cell is a node, edges join orthogonal land neighbours.",
            "Counting islands is counting connected components — one traversal per unvisited component.",
            "Sinking visited land to '0' as you go doubles as the visited-set, costing no extra memory.",
        ),
        approach = listOf(
            "Scan every cell. Land that has not been consumed yet starts a new island — increment the count.",
            "Flood that island with DFS (or BFS), flipping each visited cell to '0'.",
            "The flood's bounds check plus the '1' test together stop recursion at edges and water.",
            "Because each cell is flipped once, total work is proportional to the grid size regardless of island shapes.",
        ),
        timeComplexity = "O(rows × cols) — each cell is visited at most twice",
        spaceComplexity = "O(rows × cols) worst case for the recursion stack on an all-land grid",
        solutionCode = """
            fun numIslands(grid: Array<CharArray>): Int {
                if (grid.isEmpty()) return 0
                var count = 0

                fun sink(r: Int, c: Int) {
                    if (r !in grid.indices || c !in grid[0].indices || grid[r][c] != '1') return
                    grid[r][c] = '0'          // mark visited in place
                    sink(r + 1, c)
                    sink(r - 1, c)
                    sink(r, c + 1)
                    sink(r, c - 1)
                }

                for (r in grid.indices) {
                    for (c in grid[0].indices) {
                        if (grid[r][c] == '1') {
                            count++
                            sink(r, c)
                        }
                    }
                }
                return count
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "The flood fill is DFS over an implicit graph."),
            ProblemPrereq("graph", "Graph", "Connected components is the concept being counted."),
            ProblemPrereq("disjoint_set", "Disjoint Set (Union-Find)", "The alternative solution unions adjacent land — useful contrast."),
        ),
        linkedTopicId = "dfs",
        linkedTopicLabel = "Depth-First Search (DFS)",
    ),
)

private val dpProblems = listOf(
    PracticeProblem(
        id = "climbing_stairs",
        title = "Climbing Stairs",
        patternId = "dynamic_programming",
        difficulty = Difficulty.BEGINNER,
        prompt = "You climb a staircase of n steps, taking 1 or 2 steps at a time. How many distinct ways can you reach the top?",
        examples = listOf(
            ProblemExample("n = 2", "2", "1+1 or 2."),
            ProblemExample("n = 3", "3", "1+1+1, 1+2, 2+1."),
        ),
        constraints = listOf(
            "1 ≤ n ≤ 45",
            "Plain recursion recomputes the same subproblems exponentially.",
        ),
        hints = listOf(
            "The last move onto step n came from step n-1 or step n-2, and nowhere else.",
            "So ways(n) = ways(n-1) + ways(n-2) — Fibonacci wearing a costume.",
            "Only two previous values are ever needed, so an array is unnecessary.",
        ),
        approach = listOf(
            "Identify the state: ways(i) = number of distinct ways to stand on step i.",
            "Write the recurrence from the last decision: arrive from i-1 with a 1-step, or from i-2 with a 2-step.",
            "Base cases: ways(0) = ways(1) = 1.",
            "Iterate upward carrying only the last two values, which collapses O(n) space to O(1).",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) with the rolling pair",
        solutionCode = """
            fun climbStairs(n: Int): Int {
                var prev = 1   // ways(i - 2)
                var cur = 1    // ways(i - 1)

                for (i in 2..n) {
                    val next = prev + cur
                    prev = cur
                    cur = next
                }
                return cur
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("fibonacci_dp", "Fibonacci (Dynamic Programming)", "Same recurrence, same memo-then-roll optimisation."),
            ProblemPrereq("fibonacci_recursive", "Fibonacci", "Seeing the exponential version first is what motivates DP."),
        ),
        linkedTopicId = "fibonacci_dp",
        linkedTopicLabel = "Fibonacci (Dynamic Programming)",
    ),
    PracticeProblem(
        id = "maximum_subarray",
        title = "Maximum Subarray",
        patternId = "dynamic_programming",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Find the contiguous subarray with the largest sum and return that sum. The array may be all negative.",
        examples = listOf(
            ProblemExample("nums = [-2,1,-3,4,-1,2,1,-5,4]", "6", "The subarray [4,-1,2,1]."),
            ProblemExample("nums = [-3, -1, -2]", "-1", "All negative — the best is the single largest element."),
        ),
        constraints = listOf(
            "1 ≤ nums.size ≤ 10⁵",
            "The subarray must be non-empty.",
        ),
        hints = listOf(
            "Define the state as \"best sum of a subarray ending exactly at index i\" rather than \"best overall so far\".",
            "At each index there are only two options: extend the previous best-ending-here, or start fresh at nums[i].",
            "Track the global best separately — the running value can dip while the answer stays high.",
        ),
        approach = listOf(
            "Seed both current and best with nums[0], which also handles the all-negative case.",
            "For each later element, current = max(nums[i], current + nums[i]) — the extend-or-restart decision.",
            "Update best after every step.",
            "Restarting is correct because a negative running sum can only hurt any subarray that extends through it.",
        ),
        timeComplexity = "O(n) — one pass",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun maxSubArray(nums: IntArray): Int {
                var current = nums[0]   // best sum of a subarray ending at i
                var best = nums[0]

                for (i in 1 until nums.size) {
                    current = maxOf(nums[i], current + nums[i])   // restart or extend
                    best = maxOf(best, current)
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("kadanes_algorithm", "Kadane's Algorithm", "This problem is Kadane's algorithm, stated as an interview question."),
            ProblemPrereq("prefix_sum", "Prefix Sum", "The O(n²) prefix-difference solution is the baseline this improves on."),
        ),
        linkedTopicId = "kadanes_algorithm",
        linkedTopicLabel = "Kadane's Algorithm",
    ),
    PracticeProblem(
        id = "house_robber",
        title = "House Robber",
        patternId = "dynamic_programming",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given house values along a street, take the largest total without ever taking two adjacent houses.",
        examples = listOf(
            ProblemExample("nums = [1, 2, 3, 1]", "4", "Houses 0 and 2."),
            ProblemExample("nums = [2, 7, 9, 3, 1]", "12", "Houses 0, 2 and 4."),
        ),
        constraints = listOf(
            "0 ≤ nums.size ≤ 100",
            "Greedy 'take every other house' is wrong — check [2, 1, 1, 2].",
        ),
        hints = listOf(
            "At each house you have exactly two choices: take it, or skip it.",
            "Taking house i forbids house i-1, so the total becomes nums[i] + best(i-2).",
            "Skipping it leaves best(i-1). The answer is the larger — and only two prior values are needed.",
        ),
        approach = listOf(
            "Define best(i) as the maximum take from the first i houses.",
            "Recurrence: best(i) = max(best(i-1), best(i-2) + nums[i]).",
            "Carry two rolling variables instead of an array.",
            "The empty prefix contributes 0, which handles the leading houses without special cases.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun rob(nums: IntArray): Int {
                var skip = 0   // best through house i - 2
                var take = 0   // best through house i - 1

                for (n in nums) {
                    val next = maxOf(take, skip + n)   // skip this house, or rob it
                    skip = take
                    take = next
                }
                return take
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("fibonacci_dp", "Fibonacci (Dynamic Programming)", "Same two-term rolling-state shape, different recurrence."),
            ProblemPrereq("knapsack_01", "0/1 Knapsack", "Take-or-skip with a constraint is the knapsack decision in miniature."),
        ),
        linkedTopicId = "fibonacci_dp",
        linkedTopicLabel = "Fibonacci (Dynamic Programming)",
    ),
    PracticeProblem(
        id = "coin_change_min",
        title = "Coin Change",
        patternId = "dynamic_programming",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given coin denominations and an amount, return the fewest coins that make that amount, or -1 if it cannot be made.",
        examples = listOf(
            ProblemExample("coins = [1, 2, 5], amount = 11", "3", "5 + 5 + 1."),
            ProblemExample("coins = [2], amount = 3", "-1", "Odd amounts are unreachable."),
        ),
        constraints = listOf(
            "Each coin may be used unlimited times.",
            "0 ≤ amount ≤ 10⁴",
        ),
        hints = listOf(
            "Greedy 'take the biggest coin' fails — try coins [1, 3, 4] and amount 6.",
            "Define dp[a] as the fewest coins making amount a, and think about the *last* coin used.",
            "If the last coin is c, the rest is dp[a - c], so dp[a] = 1 + min over all usable c.",
        ),
        approach = listOf(
            "Fill dp with an impossible sentinel (amount + 1) and set dp[0] = 0.",
            "For each amount ascending, try every coin that fits and take the best dp[a - c] + 1.",
            "Ascending order guarantees dp[a - c] is already final when it is read.",
            "A remaining sentinel at dp[amount] means unreachable — return -1.",
        ),
        timeComplexity = "O(amount × coins.size)",
        spaceComplexity = "O(amount)",
        solutionCode = """
            fun coinChange(coins: IntArray, amount: Int): Int {
                // amount + 1 is larger than any real answer, so it works as "impossible".
                val dp = IntArray(amount + 1) { amount + 1 }
                dp[0] = 0

                for (a in 1..amount) {
                    for (c in coins) {
                        if (c <= a) dp[a] = minOf(dp[a], dp[a - c] + 1)
                    }
                }
                return if (dp[amount] > amount) -1 else dp[amount]
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("coin_change", "Coin Change", "The topic covers this recurrence and the greedy counterexample."),
            ProblemPrereq("knapsack_01", "0/1 Knapsack", "This is the unbounded variant — same table, coins reusable."),
            ProblemPrereq("fractional_knapsack", "Fractional Knapsack", "Shows exactly which problems greedy *does* solve, and why this is not one."),
        ),
        linkedTopicId = "coin_change",
        linkedTopicLabel = "Coin Change",
    ),
)

private val backtrackingProblems = listOf(
    PracticeProblem(
        id = "subsets_powerset",
        title = "Subsets",
        patternId = "backtracking",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given an array of distinct integers, return every possible subset (the power set), without duplicates.",
        examples = listOf(
            ProblemExample("nums = [1, 2, 3]", "[[], [1], [1,2], [1,2,3], [1,3], [2], [2,3], [3]]", "2³ = 8 subsets; order is free."),
            ProblemExample("nums = [0]", "[[], [0]]", ""),
        ),
        constraints = listOf(
            "1 ≤ nums.size ≤ 10",
            "All elements are distinct.",
        ),
        hints = listOf(
            "Every element is either in or out — that is a binary decision tree of depth n.",
            "Every node of that tree is itself a valid subset, so record on entry rather than only at the leaves.",
            "Pass a start index so each element is only ever considered after the ones before it, which prevents permuted duplicates.",
        ),
        approach = listOf(
            "Maintain a mutable `path` holding the subset under construction.",
            "On entering the recursion, snapshot `path` into the output — no separate base case is needed.",
            "Loop i from `start`: append nums[i], recurse with i + 1, then remove the last element.",
            "That removal is the backtrack — it restores the state for the next sibling branch.",
        ),
        timeComplexity = "O(n × 2ⁿ) — 2ⁿ subsets, each copied out",
        spaceComplexity = "O(n) for the path and recursion depth, excluding output",
        solutionCode = """
            fun subsets(nums: IntArray): List<List<Int>> {
                val out = mutableListOf<List<Int>>()
                val path = mutableListOf<Int>()

                fun backtrack(start: Int) {
                    out += path.toList()          // every node is a valid subset
                    for (i in start until nums.size) {
                        path += nums[i]           // choose
                        backtrack(i + 1)          // explore
                        path.removeAt(path.lastIndex)  // un-choose
                    }
                }

                backtrack(0)
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("subset_sum", "Subset Sum", "Same include/exclude tree, with a target test at the leaves."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Backtracking is DFS over a decision tree with undo."),
        ),
        linkedTopicId = "subset_sum",
        linkedTopicLabel = "Subset Sum",
    ),
    PracticeProblem(
        id = "permutations_backtracking",
        title = "Permutations",
        patternId = "backtracking",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given an array of distinct integers, return all possible orderings of its elements.",
        examples = listOf(
            ProblemExample("nums = [1, 2, 3]", "[[1,2,3], [1,3,2], [2,1,3], [2,3,1], [3,1,2], [3,2,1]]", "3! = 6."),
            ProblemExample("nums = [1]", "[[1]]", ""),
        ),
        constraints = listOf(
            "1 ≤ nums.size ≤ 6",
            "All elements are distinct.",
        ),
        hints = listOf(
            "Unlike subsets, order matters — so there is no start index; every unused element is a candidate at every depth.",
            "Track which indices are already in the current path.",
            "A complete path of length n is a finished permutation; that is the only place to record.",
        ),
        approach = listOf(
            "Keep a `used` flag per index alongside the path being built.",
            "At each level, try every unused index: mark used, append, recurse, then undo both.",
            "Record only when the path reaches full length.",
            "Undoing both the flag and the append is what makes sibling branches see a clean state.",
        ),
        timeComplexity = "O(n × n!) — n! permutations, each of length n",
        spaceComplexity = "O(n) for the path, flags and recursion depth",
        solutionCode = """
            fun permute(nums: IntArray): List<List<Int>> {
                val out = mutableListOf<List<Int>>()
                val path = mutableListOf<Int>()
                val used = BooleanArray(nums.size)

                fun backtrack() {
                    if (path.size == nums.size) {
                        out += path.toList()
                        return
                    }
                    for (i in nums.indices) {
                        if (used[i]) continue
                        used[i] = true
                        path += nums[i]
                        backtrack()
                        path.removeAt(path.lastIndex)   // undo both parts
                        used[i] = false
                    }
                }

                backtrack()
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("permutation_generation", "Permutation Generation", "The topic covers this exact recursion and its alternatives."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "The recursion tree is traversed depth-first with undo on the way back up."),
        ),
        linkedTopicId = "permutation_generation",
        linkedTopicLabel = "Permutation Generation",
    ),
    PracticeProblem(
        id = "combination_sum",
        title = "Combination Sum",
        patternId = "backtracking",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given distinct candidate numbers and a target, return every unique combination summing to the target. A candidate may be reused unlimited times.",
        examples = listOf(
            ProblemExample("candidates = [2, 3, 6, 7], target = 7", "[[2,2,3], [7]]", ""),
            ProblemExample("candidates = [2, 3, 5], target = 8", "[[2,2,2,2], [2,3,3], [3,5]]", ""),
        ),
        constraints = listOf(
            "All candidates are distinct positive integers.",
            "Combinations are unordered — [2,3,3] and [3,2,3] are the same answer.",
        ),
        hints = listOf(
            "Reuse is allowed, so recursing on the same index (not index + 1) keeps a candidate available.",
            "Never going backwards past `start` is what stops [2,3,3] and [3,2,3] both appearing.",
            "Carry the remaining target down instead of recomputing the path's sum at each node.",
        ),
        approach = listOf(
            "Recurse with (start, remaining). Remaining hitting 0 means the path is a valid combination — record it.",
            "For each candidate from `start` onwards, skip any that exceeds the remaining target.",
            "Append it and recurse with the *same* index so it can be reused, and remaining - candidate.",
            "Remove it on the way back out. Since all candidates are positive, remaining strictly decreases and the recursion terminates.",
        ),
        timeComplexity = "Exponential in target/min(candidate) — bounded by the number of valid combinations",
        spaceComplexity = "O(target / min(candidate)) recursion depth",
        solutionCode = """
            fun combinationSum(candidates: IntArray, target: Int): List<List<Int>> {
                val out = mutableListOf<List<Int>>()
                val path = mutableListOf<Int>()

                fun backtrack(start: Int, remaining: Int) {
                    if (remaining == 0) {
                        out += path.toList()
                        return
                    }
                    for (i in start until candidates.size) {
                        val c = candidates[i]
                        if (c > remaining) continue
                        path += c
                        backtrack(i, remaining - c)     // same i — reuse allowed
                        path.removeAt(path.lastIndex)
                    }
                }

                backtrack(0, target)
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("subset_sum", "Subset Sum", "The bounded version of this search; compare the reuse rule."),
            ProblemPrereq("coin_change", "Coin Change", "Same structure counted with DP instead of enumerated — worth contrasting."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Pruning on `remaining` is a depth-first search with early cutoff."),
        ),
        linkedTopicId = "subset_sum",
        linkedTopicLabel = "Subset Sum",
    ),
)

internal val coreTechniqueProblems: List<PracticeProblem> =
    binarySearchProblems + prefixSumProblems + hashingProblems + stackProblems +
        traversalProblems + dpProblems + backtrackingProblems
