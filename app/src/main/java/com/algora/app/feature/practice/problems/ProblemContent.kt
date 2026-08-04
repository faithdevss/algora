package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// Authored problem bank. Each pattern carries three problems on an easy → hard ramp so the pattern
// is met, stretched, then pushed. Solutions are Kotlin and deliberately match the shape of the code
// in the corresponding Interview Prep pattern topic.

internal val interviewPatterns = listOf(
    ProblemPattern(
        id = "sliding_window",
        name = "Sliding Window",
        blurb = "A window that grows and shrinks over a sequence, reusing work instead of rescanning.",
        accentColor = 0xFF3B82F6,
        topicId = "sliding_window_pattern",
    ),
    ProblemPattern(
        id = "two_pointers",
        name = "Two Pointers",
        blurb = "Two indices converging or chasing, usually over sorted data.",
        accentColor = 0xFF8B5CF6,
        topicId = "two_pointer_pattern",
    ),
    ProblemPattern(
        id = "fast_slow",
        name = "Fast & Slow Pointers",
        blurb = "Different speeds over the same path — cycles, midpoints, and duplicates.",
        accentColor = 0xFF16A34A,
        topicId = "fast_slow_pointers",
        isPremium = true,
    ),
    ProblemPattern(
        id = "merge_intervals",
        name = "Merge Intervals",
        blurb = "Sort by start, then decide overlap versus gap in one sweep.",
        accentColor = 0xFFF59E0B,
        topicId = "merge_intervals_pattern",
        isPremium = true,
    ),
    ProblemPattern(
        id = "top_k",
        name = "Top-K Elements",
        blurb = "A size-k heap keeps the best k without sorting everything.",
        accentColor = 0xFFEF4444,
        topicId = "top_k_pattern",
        isPremium = true,
    ),
)

private val slidingWindowProblems = listOf(
    PracticeProblem(
        id = "max_sum_subarray_k",
        title = "Maximum Sum Subarray of Size K",
        patternId = "sliding_window",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given an array of integers and a number k, find the largest sum of any contiguous subarray of exactly k elements.",
        examples = listOf(
            ProblemExample("nums = [2, 1, 5, 1, 3, 2], k = 3", "9", "The subarray [5, 1, 3] sums to 9."),
            ProblemExample("nums = [2, 3, 4, 1, 5], k = 2", "7", "The subarray [3, 4] sums to 7."),
        ),
        constraints = listOf(
            "1 ≤ k ≤ nums.size",
            "Elements may be negative.",
        ),
        hints = listOf(
            "The brute force recomputes each window from scratch — n × k additions. What does window [i, i+k) share with window [i+1, i+k+1)?",
            "They share every element but two. Adding the entering element and subtracting the leaving one updates the sum in O(1).",
            "Build the first window's sum separately, then slide: sum += nums[end] - nums[end - k].",
        ),
        approach = listOf(
            "Sum the first k elements to seed the window and record it as the best so far.",
            "Move `end` from k to the last index. Each step, add nums[end] and subtract nums[end - k] — the element that just fell out of the window.",
            "Compare the running sum against the best after every slide.",
            "Because the window size is fixed, no shrink loop is needed — the window slides rather than breathes.",
        ),
        timeComplexity = "O(n) — each element enters and leaves the window exactly once",
        spaceComplexity = "O(1) — only the running sum and the best sum",
        solutionCode = """
            fun maxSumSubarray(nums: IntArray, k: Int): Int {
                var windowSum = 0
                for (i in 0 until k) windowSum += nums[i]

                var best = windowSum
                for (end in k until nums.size) {
                    // One element enters on the right, one leaves on the left.
                    windowSum += nums[end] - nums[end - k]
                    best = maxOf(best, windowSum)
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("array", "Array", "Contiguous indexing is what makes the O(1) slide possible."),
            ProblemPrereq("sliding_window", "Sliding Window", "The fixed-size window is this technique in its simplest form."),
        ),
        linkedTopicId = "sliding_window_pattern",
        linkedTopicLabel = "Sliding Window Pattern",
    ),
    PracticeProblem(
        id = "longest_substring_no_repeat",
        title = "Longest Substring Without Repeating Characters",
        patternId = "sliding_window",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a string, return the length of the longest substring that contains no repeated character.",
        examples = listOf(
            ProblemExample("s = \"abcabcbb\"", "3", "\"abc\" is the longest clean run."),
            ProblemExample("s = \"bbbbb\"", "1", "Only \"b\" qualifies."),
            ProblemExample("s = \"pwwkew\"", "3", "\"wke\" — note \"pwke\" is a subsequence, not a substring."),
        ),
        constraints = listOf(
            "0 ≤ s.length ≤ 5 × 10⁴",
            "Characters can be letters, digits, symbols, or spaces.",
        ),
        hints = listOf(
            "The window is valid while it holds no duplicate. What must happen the moment a duplicate enters?",
            "The left edge must jump past the previous occurrence of the offending character — not one step, all the way past it.",
            "Store the last index of every character in a map so that jump is O(1).",
        ),
        approach = listOf(
            "Keep a map from character to the last index it was seen at, plus a `start` marking the window's left edge.",
            "Extend `end` one character at a time. If the incoming character was last seen at or after `start`, it is inside the window — move `start` to lastSeen + 1.",
            "Record the character's new index, then update the best length with end - start + 1.",
            "The left edge only ever moves forward, so both pointers make a single pass.",
        ),
        timeComplexity = "O(n) — each index is visited by `end` once and `start` never rewinds",
        spaceComplexity = "O(min(n, alphabet)) for the last-seen map",
        solutionCode = """
            fun lengthOfLongestSubstring(s: String): Int {
                val lastSeen = HashMap<Char, Int>()
                var start = 0
                var best = 0

                for (end in s.indices) {
                    val c = s[end]
                    val seen = lastSeen[c]
                    // Only jump if the duplicate is inside the current window.
                    if (seen != null && seen >= start) start = seen + 1
                    lastSeen[c] = end
                    best = maxOf(best, end - start + 1)
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("string", "String", "Substrings are contiguous ranges — not to be confused with subsequences."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "The last-seen index lookup must be O(1) or the window scan degrades."),
        ),
        linkedTopicId = "sliding_window_pattern",
        linkedTopicLabel = "Sliding Window Pattern",
    ),
    PracticeProblem(
        id = "minimum_window_substring",
        title = "Minimum Window Substring",
        patternId = "sliding_window",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given strings s and t, return the shortest substring of s that contains every character of t including duplicates. Return \"\" if none exists.",
        examples = listOf(
            ProblemExample("s = \"ADOBECODEBANC\", t = \"ABC\"", "\"BANC\"", "Shortest window covering A, B and C."),
            ProblemExample("s = \"a\", t = \"aa\"", "\"\"", "s has only one 'a'; two are required."),
        ),
        constraints = listOf(
            "1 ≤ s.length, t.length ≤ 10⁵",
            "t may contain repeated characters — counts matter, not just presence.",
        ),
        hints = listOf(
            "This window breathes: grow until it is valid, then shrink while it stays valid.",
            "Track how many characters are still *missing* as one integer rather than comparing two maps each step.",
            "Let counts go negative for surplus characters. A count above zero means the window genuinely needs that character.",
        ),
        approach = listOf(
            "Build a need-map of t's character counts and set `missing` to t.length.",
            "Extend `end`. Decrement the incoming character's need; if its count was still positive it covered a real requirement, so decrement `missing`.",
            "While `missing` is 0 the window is valid — record it if it is the shortest so far, then shrink from the left, restoring the leaving character's need and bumping `missing` back if that character becomes required again.",
            "Surplus characters sit at negative counts, which is what keeps the shrink loop from breaking a still-valid window.",
        ),
        timeComplexity = "O(n + m) — every index is advanced by `end` and `start` at most once each",
        spaceComplexity = "O(m) for the need-map over t's distinct characters",
        solutionCode = """
            fun minWindow(s: String, t: String): String {
                if (t.isEmpty() || s.length < t.length) return ""

                val need = HashMap<Char, Int>()
                for (c in t) need[c] = (need[c] ?: 0) + 1

                var missing = t.length
                var start = 0
                var bestStart = 0
                var bestLen = Int.MAX_VALUE

                for (end in s.indices) {
                    val c = s[end]
                    val count = need[c] ?: 0
                    if (count > 0) missing--          // covered a real requirement
                    need[c] = count - 1               // surplus goes negative

                    while (missing == 0) {
                        if (end - start + 1 < bestLen) {
                            bestLen = end - start + 1
                            bestStart = start
                        }
                        val left = s[start]
                        val restored = (need[left] ?: 0) + 1
                        need[left] = restored
                        if (restored > 0) missing++    // window just broke
                        start++
                    }
                }

                return if (bestLen == Int.MAX_VALUE) "" else s.substring(bestStart, bestStart + bestLen)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Character need-counts are tracked in a map, including negative surplus."),
            ProblemPrereq("sliding_window", "Sliding Window", "This is the variable-size version: grow to valid, shrink while valid."),
        ),
        linkedTopicId = "sliding_window_pattern",
        linkedTopicLabel = "Sliding Window Pattern",
    ),
)

private val twoPointerProblems = listOf(
    PracticeProblem(
        id = "two_sum_sorted",
        title = "Two Sum in a Sorted Array",
        patternId = "two_pointers",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given an array sorted in non-decreasing order and a target, return the indices of the two numbers that add up to the target.",
        examples = listOf(
            ProblemExample("nums = [2, 7, 11, 15], target = 9", "[0, 1]", "2 + 7 = 9."),
            ProblemExample("nums = [1, 3, 4, 5, 7], target = 12", "[3, 4]", "5 + 7 = 12."),
        ),
        constraints = listOf(
            "The array is already sorted.",
            "Exactly one valid pair exists; solve in O(1) extra space.",
        ),
        hints = listOf(
            "A hash map solves it in O(n) time and O(n) space. Sortedness should buy you the space back.",
            "Start with the widest pair — first and last. That sum is the largest reachable with the left index fixed.",
            "If the sum is too big, only moving `hi` left can shrink it; if too small, only moving `lo` right can grow it.",
        ),
        approach = listOf(
            "Place `lo` at index 0 and `hi` at the last index.",
            "Compare nums[lo] + nums[hi] against the target.",
            "Too small → advance `lo`, because every remaining pair with the current `lo` is even smaller. Too large → retreat `hi` by the mirrored argument.",
            "Each move eliminates an entire row or column of the candidate matrix, so the pointers never need to back up.",
        ),
        timeComplexity = "O(n) — the pointers together traverse the array once",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun twoSumSorted(nums: IntArray, target: Int): IntArray {
                var lo = 0
                var hi = nums.size - 1

                while (lo < hi) {
                    val sum = nums[lo] + nums[hi]
                    when {
                        sum == target -> return intArrayOf(lo, hi)
                        sum < target -> lo++     // need a bigger sum
                        else -> hi--             // need a smaller sum
                    }
                }
                return intArrayOf(-1, -1)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("array", "Array", "Random access by index is what lets both ends be read in O(1)."),
            ProblemPrereq("two_pointer", "Two Pointer Technique", "Converging pointers over sorted data is the whole method."),
        ),
        linkedTopicId = "two_pointer_pattern",
        linkedTopicLabel = "Two Pointer Pattern",
    ),
    PracticeProblem(
        id = "three_sum",
        title = "3Sum",
        patternId = "two_pointers",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given an integer array, return every unique triplet [a, b, c] such that a + b + c == 0. The solution set must not contain duplicate triplets.",
        examples = listOf(
            ProblemExample("nums = [-1, 0, 1, 2, -1, -4]", "[[-1, -1, 2], [-1, 0, 1]]", "Two distinct triplets; the repeated -1 does not create a duplicate answer."),
            ProblemExample("nums = [0, 0, 0, 0]", "[[0, 0, 0]]", "Reported once, not four times."),
        ),
        constraints = listOf(
            "3 ≤ nums.size ≤ 3000",
            "Triplets are unordered — [-1, 0, 1] and [0, 1, -1] are the same answer.",
        ),
        hints = listOf(
            "Fix one number and the problem collapses into two-sum on the rest.",
            "Two-sum with converging pointers needs sorted input — sort first, and sorting also puts duplicates next to each other.",
            "Skip a fixed value equal to its predecessor, and after recording a hit, skip past repeated values on both pointers.",
        ),
        approach = listOf(
            "Sort the array so both the two-pointer scan and duplicate skipping become possible.",
            "For each index i, skip it if nums[i] equals nums[i-1] — that outer value was already fully explored. Break entirely once nums[i] > 0, since three positives cannot sum to zero.",
            "Run converging pointers over the suffix looking for -nums[i]: sum too small → lo++, too large → hi--.",
            "On a hit, record the triplet then advance both pointers past any repeats before continuing.",
        ),
        timeComplexity = "O(n²) — an O(n) scan for each of n fixed values, after an O(n log n) sort",
        spaceComplexity = "O(1) beyond the output (sorting in place)",
        solutionCode = """
            fun threeSum(nums: IntArray): List<List<Int>> {
                nums.sort()
                val out = mutableListOf<List<Int>>()

                for (i in nums.indices) {
                    if (nums[i] > 0) break                          // sorted: no zero-sum left
                    if (i > 0 && nums[i] == nums[i - 1]) continue   // duplicate anchor

                    var lo = i + 1
                    var hi = nums.size - 1
                    while (lo < hi) {
                        val sum = nums[i] + nums[lo] + nums[hi]
                        when {
                            sum < 0 -> lo++
                            sum > 0 -> hi--
                            else -> {
                                out += listOf(nums[i], nums[lo], nums[hi])
                                while (lo < hi && nums[lo] == nums[lo + 1]) lo++
                                while (lo < hi && nums[hi] == nums[hi - 1]) hi--
                                lo++
                                hi--
                            }
                        }
                    }
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("two_pointer", "Two Pointer Technique", "The inner loop is plain two-sum with converging pointers."),
            ProblemPrereq("quick_sort", "Quick Sort", "Sorting first is what enables both the scan and the duplicate skipping."),
        ),
        linkedTopicId = "two_pointer_pattern",
        linkedTopicLabel = "Two Pointer Pattern",
    ),
    PracticeProblem(
        id = "trapping_rain_water",
        title = "Trapping Rain Water",
        patternId = "two_pointers",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given an array where each element is the height of a bar of width 1, compute how much rainwater is trapped after it rains.",
        examples = listOf(
            ProblemExample("height = [0,1,0,2,1,0,1,3,2,1,2,1]", "6", "Six unit squares of water sit in the dips."),
            ProblemExample("height = [4,2,0,3,2,5]", "9", ""),
        ),
        constraints = listOf(
            "1 ≤ height.size ≤ 2 × 10⁴",
            "0 ≤ height[i] ≤ 10⁵",
        ),
        hints = listOf(
            "Think per column, not per pool: water above column i is min(maxLeft, maxRight) - height[i].",
            "Precomputing both prefix maxima works in O(n) time and O(n) space. Can you avoid the arrays?",
            "If the left bar is shorter than the right bar, the left side's answer is already decided by leftMax — whatever happens on the right cannot lower the minimum.",
        ),
        approach = listOf(
            "Place pointers at both ends and track leftMax and rightMax seen so far.",
            "Advance whichever side has the shorter bar. That side's limiting wall is known: it is its own running max.",
            "Add max(0, runningMax - height[pointer]) — with the running max updated first, that difference is never negative.",
            "Because the shorter side always moves, every column is settled exactly once with no lookahead.",
        ),
        timeComplexity = "O(n) — single pass",
        spaceComplexity = "O(1) — two maxima instead of two prefix arrays",
        solutionCode = """
            fun trap(height: IntArray): Int {
                var lo = 0
                var hi = height.size - 1
                var leftMax = 0
                var rightMax = 0
                var water = 0

                while (lo < hi) {
                    if (height[lo] < height[hi]) {
                        // Left is the shorter wall, so leftMax alone decides this column.
                        leftMax = maxOf(leftMax, height[lo])
                        water += leftMax - height[lo]
                        lo++
                    } else {
                        rightMax = maxOf(rightMax, height[hi])
                        water += rightMax - height[hi]
                        hi--
                    }
                }
                return water
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("two_pointer", "Two Pointer Technique", "The shorter-wall-moves rule is a two-pointer invariant."),
            ProblemPrereq("prefix_sum", "Prefix Sum", "The O(n) space solution is prefix/suffix maxima — worth seeing before the O(1) trick."),
        ),
        linkedTopicId = "two_pointer_pattern",
        linkedTopicLabel = "Two Pointer Pattern",
    ),
)

private val fastSlowProblems = listOf(
    PracticeProblem(
        id = "linked_list_cycle",
        title = "Linked List Cycle Detection",
        patternId = "fast_slow",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given the head of a singly linked list, determine whether the list contains a cycle, using O(1) extra space.",
        examples = listOf(
            ProblemExample("3 → 2 → 0 → -4 → (back to 2)", "true", "The tail links back to the second node."),
            ProblemExample("1 → 2 → null", "false", "Terminates normally."),
        ),
        constraints = listOf(
            "0 ≤ node count ≤ 10⁴",
            "O(1) extra space — a visited-set is not allowed.",
        ),
        hints = listOf(
            "Two runners on a circular track eventually meet, no matter where they started.",
            "Move one pointer one node per step and the other two nodes per step.",
            "The only way the fast pointer escapes is by reaching null — which is exactly the no-cycle case.",
        ),
        approach = listOf(
            "Start both pointers at the head.",
            "Each iteration, advance slow by one and fast by two, checking fast and fast.next for null before dereferencing.",
            "If they ever land on the same node, the fast pointer has lapped the slow one inside a loop — return true.",
            "Reaching a null means the list ends, so no cycle exists. Inside a cycle of length L the gap closes by one node per step, so they meet within L steps.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1)",
        solutionCode = """
            class Node(val value: Int) { var next: Node? = null }

            fun hasCycle(head: Node?): Boolean {
                var slow = head
                var fast = head

                while (fast?.next != null) {
                    slow = slow?.next
                    fast = fast.next?.next
                    if (slow === fast) return true   // identity, not equality
                }
                return false
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "You must be comfortable with next-pointer traversal and null termination."),
            ProblemPrereq("two_pointer", "Two Pointer Technique", "Fast/slow is the same-direction variant of two pointers."),
        ),
        linkedTopicId = "fast_slow_pointers",
        linkedTopicLabel = "Fast & Slow Pointers",
    ),
    PracticeProblem(
        id = "cycle_start_node",
        title = "Find the Start of the Cycle",
        patternId = "fast_slow",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a linked list that contains a cycle, return the node where the cycle begins.",
        examples = listOf(
            ProblemExample("3 → 2 → 0 → -4 → (back to 2)", "node with value 2", "Cycle entry is the second node."),
            ProblemExample("1 → 2 → (back to 1)", "node with value 1", ""),
        ),
        constraints = listOf(
            "A cycle is guaranteed to exist.",
            "O(1) extra space.",
        ),
        hints = listOf(
            "First detect the meeting point with the usual fast/slow scan, then do a second phase.",
            "Let F be the distance from head to the cycle entry and a the distance from entry to the meeting point. Write down how far each pointer has walked when they meet.",
            "Fast walked exactly twice slow's distance, and the algebra collapses to: the distance from head to entry equals the distance from meeting point to entry.",
        ),
        approach = listOf(
            "Phase 1: run fast/slow until they meet inside the cycle.",
            "Phase 2: reset one pointer to the head, leave the other at the meeting point.",
            "Advance both one node at a time. They meet at the cycle entry.",
            "Why: slow travelled F + a, fast travelled F + a + kL, and fast = 2 × slow, so F = kL - a — walking F from the head and F from the meeting point both land on the entry.",
        ),
        timeComplexity = "O(n) — both phases are linear",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun detectCycleStart(head: Node?): Node? {
                var slow = head
                var fast = head

                while (fast?.next != null) {
                    slow = slow?.next
                    fast = fast.next?.next
                    if (slow === fast) {
                        // Phase 2: equal-speed walk from head and meeting point.
                        var entry = head
                        while (entry !== slow) {
                            entry = entry?.next
                            slow = slow?.next
                        }
                        return entry
                    }
                }
                return null
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "Phase 2 walks two references through the same node chain."),
            ProblemPrereq("fast_slow_pointers", "Fast & Slow Pointers", "The F = kL - a argument is the pattern's core proof."),
        ),
        linkedTopicId = "fast_slow_pointers",
        linkedTopicLabel = "Fast & Slow Pointers",
    ),
    PracticeProblem(
        id = "find_duplicate_number",
        title = "Find the Duplicate Number",
        patternId = "fast_slow",
        difficulty = Difficulty.ADVANCED,
        prompt = "An array of n + 1 integers holds values in the range 1..n. Exactly one value repeats. Find it without modifying the array and in O(1) extra space.",
        examples = listOf(
            ProblemExample("nums = [1, 3, 4, 2, 2]", "2", ""),
            ProblemExample("nums = [3, 1, 3, 4, 2]", "3", ""),
        ),
        constraints = listOf(
            "The array is read-only — no sorting, no marking.",
            "O(1) extra space, so no seen-set.",
        ),
        hints = listOf(
            "There is no list here, but there is a successor function: from index i, go to index nums[i].",
            "Values are in 1..n and there are n + 1 slots, so following that function from index 0 must eventually revisit a node — the sequence has a cycle.",
            "Two indices point at the same value precisely when two nodes point at one successor. The cycle's entry node is the duplicate.",
        ),
        approach = listOf(
            "Read the array as a linked list where node i links to node nums[i].",
            "Run fast/slow over that implicit list until they meet — this is Floyd's cycle detection with no pointers allocated.",
            "Reset one runner to nums[0] and step both one at a time; the node they meet at is the cycle entry.",
            "Since the duplicate value is the one two indices point to, the entry node is exactly the repeated value.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) — the array itself is the graph",
        solutionCode = """
            fun findDuplicate(nums: IntArray): Int {
                // Phase 1: find a meeting point inside the cycle.
                var slow = nums[0]
                var fast = nums[0]
                do {
                    slow = nums[slow]
                    fast = nums[nums[fast]]
                } while (slow != fast)

                // Phase 2: walk from the start to the cycle entry.
                slow = nums[0]
                while (slow != fast) {
                    slow = nums[slow]
                    fast = nums[fast]
                }
                return slow
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("array", "Array", "The array is reinterpreted as an implicit successor function i → nums[i]."),
            ProblemPrereq("fast_slow_pointers", "Fast & Slow Pointers", "Floyd's algorithm runs over that implicit list."),
        ),
        linkedTopicId = "fast_slow_pointers",
        linkedTopicLabel = "Fast & Slow Pointers",
    ),
)

private val mergeIntervalProblems = listOf(
    PracticeProblem(
        id = "merge_overlapping_intervals",
        title = "Merge Overlapping Intervals",
        patternId = "merge_intervals",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given a collection of intervals, merge every set of overlapping intervals and return the non-overlapping result.",
        examples = listOf(
            ProblemExample("[[1,3], [2,6], [8,10], [15,18]]", "[[1,6], [8,10], [15,18]]", "[1,3] and [2,6] overlap."),
            ProblemExample("[[1,4], [4,5]]", "[[1,5]]", "Touching intervals count as overlapping."),
        ),
        constraints = listOf(
            "1 ≤ intervals.size ≤ 10⁴",
            "Intervals arrive in arbitrary order.",
        ),
        hints = listOf(
            "Unsorted input makes overlap a pairwise question. Sorting makes it a neighbour question.",
            "Sort by start. Then an interval can only overlap the one currently being built.",
            "Overlap test: current.start ≤ built.end. Merging means built.end = max(built.end, current.end) — the current interval may end sooner.",
        ),
        approach = listOf(
            "Sort intervals by start time.",
            "Seed the output with the first interval as the open merge candidate.",
            "For each subsequent interval, if it starts at or before the candidate's end, extend the candidate's end to the larger of the two ends.",
            "Otherwise there is a gap: close the candidate and open a new one from the current interval.",
        ),
        timeComplexity = "O(n log n) — dominated by the sort; the sweep itself is O(n)",
        spaceComplexity = "O(n) for the output",
        solutionCode = """
            fun merge(intervals: Array<IntArray>): List<IntArray> {
                if (intervals.isEmpty()) return emptyList()
                intervals.sortBy { it[0] }

                // Copy the first so the input array is never mutated through aliasing.
                val out = mutableListOf(intArrayOf(intervals[0][0], intervals[0][1]))
                for (i in 1 until intervals.size) {
                    val last = out.last()
                    val cur = intervals[i]
                    if (cur[0] <= last[1]) {
                        last[1] = maxOf(last[1], cur[1])   // overlap: extend
                    } else {
                        out += intArrayOf(cur[0], cur[1])  // gap: start fresh
                    }
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("merge_sort", "Merge Sort", "Sorting by start is the enabling step; any O(n log n) sort works."),
            ProblemPrereq("array", "Array", "Intervals are stored and swept as an indexed sequence."),
        ),
        linkedTopicId = "merge_intervals_pattern",
        linkedTopicLabel = "Merge Intervals",
    ),
    PracticeProblem(
        id = "insert_interval",
        title = "Insert Interval",
        patternId = "merge_intervals",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a list of non-overlapping intervals sorted by start, insert a new interval and merge where needed.",
        examples = listOf(
            ProblemExample("intervals = [[1,3], [6,9]], newInterval = [2,5]", "[[1,5], [6,9]]", ""),
            ProblemExample("intervals = [[1,2], [3,5], [6,7], [8,10], [12,16]], newInterval = [4,8]", "[[1,2], [3,10], [12,16]]", "Three intervals collapse into one."),
        ),
        constraints = listOf(
            "The input is already sorted and non-overlapping.",
            "Aim for a single O(n) pass — do not re-sort.",
        ),
        hints = listOf(
            "The input is already sorted, so the answer splits into three consecutive zones.",
            "Zone 1: intervals ending before the new one starts — copy verbatim. Zone 3: intervals starting after it ends — copy verbatim.",
            "Zone 2 is the overlap run: absorb it by taking the min of the starts and the max of the ends.",
        ),
        approach = listOf(
            "Copy every interval whose end is strictly before newInterval.start.",
            "Absorb every interval whose start is ≤ the running end: widen start to the min and end to the max.",
            "Append the merged interval once the overlap run is exhausted.",
            "Copy the remaining tail unchanged.",
        ),
        timeComplexity = "O(n) — one pass, no sort needed",
        spaceComplexity = "O(n) for the output",
        solutionCode = """
            fun insert(intervals: Array<IntArray>, newInterval: IntArray): List<IntArray> {
                val out = mutableListOf<IntArray>()
                val n = intervals.size
                var i = 0

                // 1. Everything strictly before the new interval.
                while (i < n && intervals[i][1] < newInterval[0]) out += intervals[i++]

                // 2. Absorb the overlap run.
                var start = newInterval[0]
                var end = newInterval[1]
                while (i < n && intervals[i][0] <= end) {
                    start = minOf(start, intervals[i][0])
                    end = maxOf(end, intervals[i][1])
                    i++
                }
                out += intArrayOf(start, end)

                // 3. Everything strictly after.
                while (i < n) out += intervals[i++]
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("merge_intervals_pattern", "Merge Intervals", "Overlap versus gap is decided exactly as in the merge problem."),
            ProblemPrereq("array", "Array", "Sortedness of the input array is what removes the sort step."),
        ),
        linkedTopicId = "merge_intervals_pattern",
        linkedTopicLabel = "Merge Intervals",
    ),
    PracticeProblem(
        id = "meeting_rooms_two",
        title = "Minimum Meeting Rooms",
        patternId = "merge_intervals",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given meeting intervals [start, end), return the minimum number of rooms required to hold all of them.",
        examples = listOf(
            ProblemExample("[[0,30], [5,10], [15,20]]", "2", "[5,10] and [15,20] can share a room; [0,30] needs its own."),
            ProblemExample("[[7,10], [2,4]]", "1", "They never overlap."),
        ),
        constraints = listOf(
            "0 ≤ intervals.size ≤ 10⁴",
            "Intervals are half-open: a meeting ending at 10 frees the room for one starting at 10.",
        ),
        hints = listOf(
            "The answer is the maximum number of meetings in progress at any instant, not the total count.",
            "Rooms only change at a start or an end event — so only those timestamps matter, and they can be decoupled.",
            "Sort the start times and end times into two separate lists and sweep them together like a merge.",
        ),
        approach = listOf(
            "Extract and sort all start times and all end times independently — which meeting an endpoint belongs to is irrelevant.",
            "Sweep the starts in order, keeping an index into the ends.",
            "Before each start, release every room whose end time is ≤ that start (half-open intervals share the boundary).",
            "Occupy a room and track the running maximum — that peak is the answer.",
        ),
        timeComplexity = "O(n log n) for the two sorts, O(n) for the sweep",
        spaceComplexity = "O(n) for the endpoint lists",
        solutionCode = """
            fun minMeetingRooms(intervals: Array<IntArray>): Int {
                if (intervals.isEmpty()) return 0

                val starts = intervals.map { it[0] }.sorted()
                val ends = intervals.map { it[1] }.sorted()

                var rooms = 0
                var peak = 0
                var e = 0

                for (s in starts) {
                    // Free every room whose meeting has already finished.
                    while (e < ends.size && ends[e] <= s) {
                        rooms--
                        e++
                    }
                    rooms++
                    peak = maxOf(peak, rooms)
                }
                return peak
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("merge_sort", "Merge Sort", "Both endpoint lists must be sorted before the sweep."),
            ProblemPrereq("priority_queue_adt", "Priority Queue", "The classic alternative keeps end times in a min-heap — worth comparing."),
        ),
        linkedTopicId = "merge_intervals_pattern",
        linkedTopicLabel = "Merge Intervals",
    ),
)

private val topKProblems = listOf(
    PracticeProblem(
        id = "kth_largest_element",
        title = "Kth Largest Element",
        patternId = "top_k",
        difficulty = Difficulty.BEGINNER,
        prompt = "Return the kth largest element in an unsorted array. It is the kth largest in sorted order, not the kth distinct value.",
        examples = listOf(
            ProblemExample("nums = [3, 2, 1, 5, 6, 4], k = 2", "5", ""),
            ProblemExample("nums = [3, 2, 3, 1, 2, 4, 5, 5, 6], k = 4", "4", "Duplicates each count."),
        ),
        constraints = listOf(
            "1 ≤ k ≤ nums.size",
            "Sorting is O(n log n) — aim for O(n log k).",
        ),
        hints = listOf(
            "Sorting answers far more than the question asks. You never need the order of the bottom n - k elements.",
            "Keep only the k largest seen so far. Which element is the one to evict when a better candidate arrives?",
            "The smallest of the k kept — so a min-heap of size k, whose root is the running answer.",
        ),
        approach = listOf(
            "Maintain a min-heap capped at size k.",
            "Push each element; if the heap exceeds k, poll the root, which is the smallest of the current best k.",
            "After the pass the heap holds exactly the k largest elements.",
            "Its root is the smallest of those — the kth largest overall.",
        ),
        timeComplexity = "O(n log k) — better than sorting when k ≪ n",
        spaceComplexity = "O(k) for the heap",
        solutionCode = """
            import java.util.PriorityQueue

            fun findKthLargest(nums: IntArray, k: Int): Int {
                val heap = PriorityQueue<Int>()   // natural order = min-heap

                for (n in nums) {
                    heap.add(n)
                    if (heap.size > k) heap.poll()   // drop the weakest candidate
                }
                return heap.peek()
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("heap", "Heap (Min / Max)", "A size-k min-heap is the data structure the whole method rests on."),
            ProblemPrereq("heap_sort", "Heap Sort", "Understanding sift-up/sift-down explains where the log k comes from."),
        ),
        linkedTopicId = "top_k_pattern",
        linkedTopicLabel = "Top-K Pattern",
    ),
    PracticeProblem(
        id = "top_k_frequent",
        title = "Top K Frequent Elements",
        patternId = "top_k",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given an integer array and a number k, return the k most frequent elements in any order.",
        examples = listOf(
            ProblemExample("nums = [1, 1, 1, 2, 2, 3], k = 2", "[1, 2]", "1 appears three times, 2 appears twice."),
            ProblemExample("nums = [1], k = 1", "[1]", ""),
        ),
        constraints = listOf(
            "1 ≤ k ≤ number of distinct elements",
            "The answer is guaranteed unique.",
        ),
        hints = listOf(
            "Two phases: count, then select. Only the second phase is the pattern.",
            "The heap should order by frequency, not by value — the comparator is the whole trick.",
            "Cap the heap at k entries so the poll always evicts the least frequent survivor.",
        ),
        approach = listOf(
            "Build a frequency map in one O(n) pass.",
            "Push each (value, count) entry into a min-heap ordered by count.",
            "Whenever the heap exceeds k, poll — that removes the least frequent of the current best.",
            "Read the keys out of the heap; ordering among them is not required.",
        ),
        timeComplexity = "O(n + d log k) where d is the number of distinct values",
        spaceComplexity = "O(d) for the counts plus O(k) for the heap",
        solutionCode = """
            import java.util.PriorityQueue

            fun topKFrequent(nums: IntArray, k: Int): List<Int> {
                val counts = HashMap<Int, Int>()
                for (n in nums) counts[n] = (counts[n] ?: 0) + 1

                // Min-heap on frequency: the root is the weakest of the current top k.
                val heap = PriorityQueue<Map.Entry<Int, Int>>(compareBy { it.value })
                for (entry in counts.entries) {
                    heap.add(entry)
                    if (heap.size > k) heap.poll()
                }
                return heap.map { it.key }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "The counting phase is a frequency map."),
            ProblemPrereq("heap", "Heap (Min / Max)", "Selection is a size-k heap ordered by count rather than value."),
        ),
        linkedTopicId = "top_k_pattern",
        linkedTopicLabel = "Top-K Pattern",
    ),
    PracticeProblem(
        id = "merge_k_sorted_lists",
        title = "Merge K Sorted Lists",
        patternId = "top_k",
        difficulty = Difficulty.ADVANCED,
        prompt = "Merge k sorted linked lists into one sorted linked list and return its head.",
        examples = listOf(
            ProblemExample("[[1,4,5], [1,3,4], [2,6]]", "[1,1,2,3,4,4,5,6]", ""),
            ProblemExample("[]", "null", "No lists at all."),
        ),
        constraints = listOf(
            "0 ≤ k ≤ 10⁴; total nodes up to 10⁴",
            "Each input list is already sorted ascending.",
        ),
        hints = listOf(
            "At every step the next output node is the smallest head among the k lists.",
            "Rescanning all k heads each time costs O(nk). A heap answers 'smallest of k' in O(log k).",
            "The heap holds at most one node per list — after popping a node, push its successor.",
        ),
        approach = listOf(
            "Seed a min-heap ordered by node value with the head of each non-empty list.",
            "Poll the smallest node, append it to the output tail, and push that node's next if it exists.",
            "The heap never exceeds k entries, so each of the n pops and pushes costs O(log k).",
            "Use a dummy head so appending needs no special case for the first node, and null the final tail's next to cut any stale link.",
        ),
        timeComplexity = "O(n log k) for n total nodes across k lists",
        spaceComplexity = "O(k) for the heap",
        solutionCode = """
            import java.util.PriorityQueue

            fun mergeKLists(lists: List<Node?>): Node? {
                val heap = PriorityQueue<Node>(compareBy { it.value })
                for (head in lists) if (head != null) heap.add(head)

                val dummy = Node(0)
                var tail = dummy
                while (heap.isNotEmpty()) {
                    val node = heap.poll()
                    tail.next = node
                    tail = node
                    node.next?.let { heap.add(it) }   // refill from the same list
                }
                tail.next = null                      // cut any stale link
                return dummy.next
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("heap", "Heap (Min / Max)", "'Smallest of k heads' in O(log k) is exactly what a heap answers."),
            ProblemPrereq("singly_linked_list", "Singly Linked List", "The output is stitched by relinking nodes, not copying values."),
            ProblemPrereq("merge_sort", "Merge Sort", "The two-list merge step generalises to k lists here."),
        ),
        linkedTopicId = "top_k_pattern",
        linkedTopicLabel = "Top-K Pattern",
    ),
)

internal val interviewPatternProblems: List<PracticeProblem> =
    slidingWindowProblems + twoPointerProblems + fastSlowProblems + mergeIntervalProblems + topKProblems
