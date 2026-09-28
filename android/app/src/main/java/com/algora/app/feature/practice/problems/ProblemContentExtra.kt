package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// Second pass over the twelve existing pattern groups: two more problems each, chosen to hit the
// variants the first three miss (validity windows, answer-space searches, in-place rewrites,
// topological order). ProblemRegistry.forPattern sorts by difficulty, so these interleave with the
// originals rather than tailing them.

private val moreSlidingWindow = listOf(
    PracticeProblem(
        id = "permutation_in_string",
        title = "Permutation in String",
        patternId = "sliding_window",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given strings s1 and s2, return true if s2 contains any permutation of s1 as a contiguous substring.",
        examples = listOf(
            ProblemExample("s1 = \"ab\", s2 = \"eidbaooo\"", "true", "s2 contains \"ba\"."),
            ProblemExample("s1 = \"ab\", s2 = \"eidboaoo\"", "false", "\"boa\" is not contiguous as a permutation."),
        ),
        constraints = listOf(
            "Lowercase English letters only.",
            "1 ≤ s1.length, s2.length ≤ 10⁴",
        ),
        hints = listOf(
            "A permutation of s1 has exactly s1.length characters, so the window size never changes.",
            "Two strings are permutations of each other exactly when their letter counts match.",
            "Slide a fixed window over s2, adding the entering letter and removing the leaving one, and compare counts.",
        ),
        approach = listOf(
            "Build a 26-slot count for s1 and an empty count for the window.",
            "Extend the window one character at a time; once it exceeds s1.length, drop the character that fell off the left.",
            "Compare the two count arrays after each step — equality means the window is a permutation.",
            "Comparing 26 slots is O(1), so the whole scan stays linear.",
        ),
        timeComplexity = "O(n) — 26-slot comparison per step is constant",
        spaceComplexity = "O(1) — two fixed 26-element arrays",
        solutionCode = """
            fun checkInclusion(s1: String, s2: String): Boolean {
                if (s1.length > s2.length) return false

                val need = IntArray(26)
                val window = IntArray(26)
                for (c in s1) need[c - 'a']++

                for (i in s2.indices) {
                    window[s2[i] - 'a']++
                    if (i >= s1.length) window[s2[i - s1.length] - 'a']--   // fixed size
                    if (need.contentEquals(window)) return true
                }
                return false
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("sliding_window", "Sliding Window", "Fixed-size window with an entering and a leaving element."),
            ProblemPrereq("counting_sort", "Counting Sort", "The 26-slot tally is the counting idea used as a signature."),
        ),
        linkedTopicId = "sliding_window_pattern",
        linkedTopicLabel = "Sliding Window Pattern",
    ),
    PracticeProblem(
        id = "longest_repeating_replacement",
        title = "Longest Repeating Character Replacement",
        patternId = "sliding_window",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a string and an integer k, you may replace at most k characters with any letter. Return the length of the longest substring of a single repeated letter you can produce.",
        examples = listOf(
            ProblemExample("s = \"ABAB\", k = 2", "4", "Replace both A's or both B's."),
            ProblemExample("s = \"AABABBA\", k = 1", "4", "Replace the middle A to get \"AABBBBA\" → \"BBBB\"."),
        ),
        constraints = listOf(
            "Uppercase English letters only.",
            "0 ≤ k ≤ s.length ≤ 10⁵",
        ),
        hints = listOf(
            "A window is achievable when the characters you must replace fit inside k.",
            "That count is windowLength - (occurrences of the most frequent letter in the window).",
            "The window only needs to shrink when that quantity exceeds k — and the answer never shrinks, so a single pass suffices.",
        ),
        approach = listOf(
            "Track letter counts inside the window plus the highest count seen so far.",
            "Extend the right edge, updating the count and the running max.",
            "If windowLength - maxCount > k the window is unachievable — advance the left edge and drop its letter.",
            "Record the window length after each step. The max count is deliberately never decreased: a stale max can only keep the window from shrinking, and a longer answer requires a genuinely higher count.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) — one 26-element count",
        solutionCode = """
            fun characterReplacement(s: String, k: Int): Int {
                val count = IntArray(26)
                var start = 0
                var maxCount = 0
                var best = 0

                for (end in s.indices) {
                    count[s[end] - 'A']++
                    maxCount = maxOf(maxCount, count[s[end] - 'A'])

                    // Characters needing replacement = window size - most common letter.
                    while (end - start + 1 - maxCount > k) {
                        count[s[start] - 'A']--
                        start++
                    }
                    best = maxOf(best, end - start + 1)
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("sliding_window", "Sliding Window", "A validity condition drives the shrink, not a fixed size."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Letter frequency inside the window is a count map."),
        ),
        linkedTopicId = "sliding_window_pattern",
        linkedTopicLabel = "Sliding Window Pattern",
    ),
)

private val moreTwoPointers = listOf(
    PracticeProblem(
        id = "valid_palindrome",
        title = "Valid Palindrome",
        patternId = "two_pointers",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given a string, decide whether it reads the same forwards and backwards, considering only alphanumeric characters and ignoring case.",
        examples = listOf(
            ProblemExample("s = \"A man, a plan, a canal: Panama\"", "true", ""),
            ProblemExample("s = \"race a car\"", "false", ""),
        ),
        constraints = listOf(
            "1 ≤ s.length ≤ 2 × 10⁵",
            "Solve without building a cleaned copy of the string.",
        ),
        hints = listOf(
            "A palindrome is defined by pairs: first with last, second with second-last.",
            "Walk two pointers inwards and compare as you go.",
            "Skipping non-alphanumerics happens inside the loop, so no separate cleaning pass is needed.",
        ),
        approach = listOf(
            "Start pointers at both ends.",
            "Advance each past any non-alphanumeric character before comparing.",
            "Compare lowercased characters; a mismatch ends it immediately.",
            "Meeting in the middle without a mismatch means the string is a palindrome.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) — no cleaned copy",
        solutionCode = """
            fun isPalindrome(s: String): Boolean {
                var lo = 0
                var hi = s.length - 1

                while (lo < hi) {
                    while (lo < hi && !s[lo].isLetterOrDigit()) lo++
                    while (lo < hi && !s[hi].isLetterOrDigit()) hi--
                    if (s[lo].lowercaseChar() != s[hi].lowercaseChar()) return false
                    lo++
                    hi--
                }
                return true
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("two_pointer", "Two Pointer Technique", "Converging pointers compare mirrored positions."),
            ProblemPrereq("string", "String", "Character classification and case folding are string operations."),
        ),
        linkedTopicId = "two_pointer_pattern",
        linkedTopicLabel = "Two Pointer Pattern",
    ),
    PracticeProblem(
        id = "container_with_most_water",
        title = "Container With Most Water",
        patternId = "two_pointers",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given heights of vertical lines, pick two that together with the x-axis hold the most water. Return that maximum area.",
        examples = listOf(
            ProblemExample("height = [1,8,6,2,5,4,8,3,7]", "49", "Lines at index 1 and 8: min(8,7) × 7."),
            ProblemExample("height = [1, 1]", "1", ""),
        ),
        constraints = listOf(
            "2 ≤ height.size ≤ 10⁵",
            "Containers do not slant — area is min(left, right) × distance.",
        ),
        hints = listOf(
            "Start with the widest possible container: the two ends.",
            "Any move inwards loses width, so it can only help if it gains height.",
            "Moving the taller line can never gain — the shorter one still caps the area. So move the shorter.",
        ),
        approach = listOf(
            "Place pointers at both ends and compute the area each step.",
            "Advance the pointer at the shorter line.",
            "That discards every container using the shorter line, all of which are bounded by the area just measured.",
            "Continue until the pointers meet, keeping the running maximum.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun maxArea(height: IntArray): Int {
                var lo = 0
                var hi = height.size - 1
                var best = 0

                while (lo < hi) {
                    best = maxOf(best, minOf(height[lo], height[hi]) * (hi - lo))
                    // The shorter line caps this pair — only moving it can improve.
                    if (height[lo] < height[hi]) lo++ else hi--
                }
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("two_pointer", "Two Pointer Technique", "The greedy shrink argument is a two-pointer invariant."),
            ProblemPrereq("array", "Array", "Widths are index differences over a contiguous block."),
        ),
        linkedTopicId = "two_pointer_pattern",
        linkedTopicLabel = "Two Pointer Pattern",
    ),
)

private val moreFastSlow = listOf(
    PracticeProblem(
        id = "middle_of_linked_list",
        title = "Middle of the Linked List",
        patternId = "fast_slow",
        difficulty = Difficulty.BEGINNER,
        prompt = "Return the middle node of a singly linked list. With an even count, return the second of the two middle nodes.",
        examples = listOf(
            ProblemExample("1 → 2 → 3 → 4 → 5", "node 3", ""),
            ProblemExample("1 → 2 → 3 → 4 → 5 → 6", "node 4", "Second middle."),
        ),
        constraints = listOf(
            "1 ≤ node count ≤ 100",
            "Solve in one pass — no length counting first.",
        ),
        hints = listOf(
            "Counting the length then walking half is two passes. Can one pointer's position encode another's?",
            "If one pointer moves twice as fast, where is the slow one when the fast one finishes?",
            "Exactly halfway — that is the entire solution.",
        ),
        approach = listOf(
            "Start both pointers at the head.",
            "Advance slow by one and fast by two each step.",
            "Stop when fast or fast.next is null.",
            "Slow now sits at the middle; the loop condition decides which middle an even-length list yields.",
        ),
        timeComplexity = "O(n) in one pass",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun middleNode(head: Node?): Node? {
                var slow = head
                var fast = head

                while (fast?.next != null) {
                    slow = slow?.next
                    fast = fast.next?.next
                }
                return slow
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "There is no index — position must be derived by walking."),
            ProblemPrereq("fast_slow_pointers", "Fast & Slow Pointers", "The 2:1 speed ratio is the whole technique."),
        ),
        linkedTopicId = "fast_slow_pointers",
        linkedTopicLabel = "Fast & Slow Pointers",
    ),
    PracticeProblem(
        id = "happy_number",
        title = "Happy Number",
        patternId = "fast_slow",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Repeatedly replace a number by the sum of the squares of its digits. It is happy if this reaches 1; otherwise the process loops forever. Decide whether n is happy.",
        examples = listOf(
            ProblemExample("n = 19", "true", "19 → 82 → 68 → 100 → 1."),
            ProblemExample("n = 2", "false", "Enters a cycle that never reaches 1."),
        ),
        constraints = listOf(
            "1 ≤ n ≤ 2³¹ - 1",
            "Aim for O(1) extra space — no seen-set.",
        ),
        hints = listOf(
            "The sequence is a chain of numbers where each has exactly one successor. What does 'loops forever' mean about that chain?",
            "It has a cycle — so cycle detection applies, with digit-square-sum as the next() function.",
            "Run fast/slow. They meet either at 1 (happy) or inside the unhappy cycle.",
        ),
        approach = listOf(
            "Write next(x) as the sum of squares of x's digits.",
            "Run slow one step and fast two steps per iteration.",
            "Stop when fast reaches 1 or the two meet.",
            "Meeting away from 1 means an unhappy cycle. A set of seen values also works but costs memory.",
        ),
        timeComplexity = "O(log n) per step, with a bounded number of steps",
        spaceComplexity = "O(1) — no seen-set",
        solutionCode = """
            fun isHappy(n: Int): Boolean {
                fun squareSum(x: Int): Int {
                    var v = x
                    var sum = 0
                    while (v > 0) {
                        val d = v % 10
                        sum += d * d
                        v /= 10
                    }
                    return sum
                }

                var slow = n
                var fast = squareSum(n)
                while (fast != 1 && slow != fast) {
                    slow = squareSum(slow)
                    fast = squareSum(squareSum(fast))
                }
                return fast == 1
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("fast_slow_pointers", "Fast & Slow Pointers", "Floyd's detection over an implicit successor function."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "The O(n)-space seen-set alternative is worth contrasting."),
        ),
        linkedTopicId = "fast_slow_pointers",
        linkedTopicLabel = "Fast & Slow Pointers",
    ),
)

private val moreMergeIntervals = listOf(
    PracticeProblem(
        id = "non_overlapping_intervals",
        title = "Non-Overlapping Intervals",
        patternId = "merge_intervals",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a set of intervals, return the minimum number you must remove so that the rest do not overlap.",
        examples = listOf(
            ProblemExample("[[1,2], [2,3], [3,4], [1,3]]", "1", "Remove [1,3]."),
            ProblemExample("[[1,2], [1,2], [1,2]]", "2", "Keep one copy."),
        ),
        constraints = listOf(
            "Intervals touching at a boundary do not overlap: [1,2] and [2,3] are fine.",
            "1 ≤ intervals.size ≤ 10⁵",
        ),
        hints = listOf(
            "Removing the fewest is the same as keeping the most — this is interval scheduling.",
            "Sorting by *start* is wrong here; a long early interval blocks many later ones.",
            "Sort by end time: always keeping the interval that finishes earliest leaves the most room for the rest.",
        ),
        approach = listOf(
            "Sort by end time.",
            "Keep the first interval and remember its end.",
            "Take each subsequent interval whose start is ≥ that end, updating the end each time you take one.",
            "The answer is total minus kept. The exchange argument proves earliest-finish is optimal.",
        ),
        timeComplexity = "O(n log n) for the sort, O(n) for the sweep",
        spaceComplexity = "O(1) beyond the sort",
        solutionCode = """
            fun eraseOverlapIntervals(intervals: Array<IntArray>): Int {
                if (intervals.isEmpty()) return 0
                intervals.sortBy { it[1] }        // earliest finish first

                var end = intervals[0][1]
                var kept = 1
                for (i in 1 until intervals.size) {
                    if (intervals[i][0] >= end) {
                        kept++
                        end = intervals[i][1]
                    }
                }
                return intervals.size - kept
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("job_sequencing", "Job Sequencing with Deadlines", "Same greedy family: sort by a deadline-like key, then take what fits."),
            ProblemPrereq("merge_sort", "Merge Sort", "Sorting by end time is the enabling step."),
        ),
        linkedTopicId = "merge_intervals_pattern",
        linkedTopicLabel = "Merge Intervals",
    ),
    PracticeProblem(
        id = "interval_intersections",
        title = "Interval List Intersections",
        patternId = "merge_intervals",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given two lists of pairwise-disjoint intervals, each sorted by start, return the intersection of the two lists.",
        examples = listOf(
            ProblemExample("a = [[0,2],[5,10]], b = [[1,5],[8,12]]", "[[1,2], [5,5], [8,10]]", "Single-point intersections count."),
            ProblemExample("a = [[1,3]], b = [[4,6]]", "[]", "No overlap."),
        ),
        constraints = listOf(
            "Each list is sorted and internally disjoint.",
            "0 ≤ list sizes ≤ 1000",
        ),
        hints = listOf(
            "Both lists are sorted, so this is closer to merging two sorted lists than to interval merging.",
            "For any pair, the intersection is [max of starts, min of ends] — and it is real only when that range is non-empty.",
            "Advance the interval that ends first; it cannot intersect anything further along the other list.",
        ),
        approach = listOf(
            "Keep an index into each list.",
            "Compute lo = max(starts) and hi = min(ends) for the current pair; emit [lo, hi] when lo ≤ hi.",
            "Advance the pointer whose interval has the smaller end.",
            "Each step consumes one interval, so the sweep is linear in the combined size.",
        ),
        timeComplexity = "O(m + n)",
        spaceComplexity = "O(1) beyond the output",
        solutionCode = """
            fun intervalIntersection(a: Array<IntArray>, b: Array<IntArray>): List<IntArray> {
                val out = mutableListOf<IntArray>()
                var i = 0
                var j = 0

                while (i < a.size && j < b.size) {
                    val lo = maxOf(a[i][0], b[j][0])
                    val hi = minOf(a[i][1], b[j][1])
                    if (lo <= hi) out += intArrayOf(lo, hi)

                    // Whichever ends first can meet nothing later in the other list.
                    if (a[i][1] < b[j][1]) i++ else j++
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("two_pointer", "Two Pointer Technique", "Two indices advance over two sorted sequences."),
            ProblemPrereq("merge_sort", "Merge Sort", "The merge step's advance-the-smaller rule is reused verbatim."),
        ),
        linkedTopicId = "merge_intervals_pattern",
        linkedTopicLabel = "Merge Intervals",
    ),
)

private val moreTopK = listOf(
    PracticeProblem(
        id = "k_closest_points",
        title = "K Closest Points to Origin",
        patternId = "top_k",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given points on a plane and an integer k, return the k points closest to the origin.",
        examples = listOf(
            ProblemExample("points = [[1,3], [-2,2]], k = 1", "[[-2,2]]", "√8 < √10."),
            ProblemExample("points = [[3,3], [5,-1], [-2,4]], k = 2", "[[3,3], [-2,4]]", "Any order."),
        ),
        constraints = listOf(
            "1 ≤ k ≤ points.size ≤ 10⁴",
            "The answer may be returned in any order.",
        ),
        hints = listOf(
            "Comparing distances never needs the square root — x² + y² preserves the ordering.",
            "You want the k smallest, so the heap must evict the current worst: a max-heap of size k.",
            "That is the mirror image of the kth-largest problem, which uses a min-heap.",
        ),
        approach = listOf(
            "Order a heap by squared distance, descending, so its root is the farthest of those kept.",
            "Push each point; whenever the heap exceeds k, poll the root.",
            "After the pass the heap holds exactly the k nearest points.",
            "Skipping the square root avoids floating point entirely.",
        ),
        timeComplexity = "O(n log k)",
        spaceComplexity = "O(k)",
        solutionCode = """
            import java.util.PriorityQueue

            fun kClosest(points: Array<IntArray>, k: Int): List<IntArray> {
                // Max-heap on squared distance: the root is the worst of the current best k.
                val heap = PriorityQueue<IntArray>(compareByDescending { it[0] * it[0] + it[1] * it[1] })

                for (p in points) {
                    heap.add(p)
                    if (heap.size > k) heap.poll()
                }
                return heap.toList()
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("heap", "Heap (Min / Max)", "A bounded max-heap selects the k smallest."),
            ProblemPrereq("quickselect", "Quickselect", "The O(n) average alternative — worth knowing when k is large."),
        ),
        linkedTopicId = "top_k_pattern",
        linkedTopicLabel = "Top-K Pattern",
    ),
    PracticeProblem(
        id = "task_scheduler",
        title = "Task Scheduler",
        patternId = "top_k",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given CPU tasks labelled A–Z and a cooldown n, identical tasks must be separated by at least n intervals. Return the minimum total intervals, counting idles.",
        examples = listOf(
            ProblemExample("tasks = [A,A,A,B,B,B], n = 2", "8", "A B idle A B idle A B."),
            ProblemExample("tasks = [A,A,A,B,B,B], n = 0", "6", "No cooldown, no idling."),
        ),
        constraints = listOf(
            "1 ≤ tasks.size ≤ 10⁴",
            "0 ≤ n ≤ 100",
        ),
        hints = listOf(
            "The schedule's length is dictated by the most frequent task — it defines the skeleton.",
            "With max count m, that task creates m - 1 gaps, each of width n + 1 including the task itself.",
            "Ties matter: every task sharing the max count adds one more slot to the final block.",
        ),
        approach = listOf(
            "Count each task's frequency and find the maximum m and how many tasks tie at m.",
            "The skeleton length is (m - 1) × (n + 1) + tiesAtMax.",
            "If there are enough other tasks, they fill the idles instead of extending the schedule — so the answer is at least tasks.size.",
            "Return the larger of the two. No simulation is needed.",
        ),
        timeComplexity = "O(n) to count, O(1) to compute",
        spaceComplexity = "O(1) — a fixed 26-slot count",
        solutionCode = """
            fun leastInterval(tasks: CharArray, n: Int): Int {
                val counts = IntArray(26)
                for (t in tasks) counts[t - 'A']++

                val maxCount = counts.max()
                val tiesAtMax = counts.count { it == maxCount }

                // Skeleton built from the most frequent task, plus the tail block.
                val slots = (maxCount - 1) * (n + 1) + tiesAtMax
                return maxOf(tasks.size, slots)   // idles get filled when there is other work
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("top_k_elements", "Top-K Elements", "Only the top frequencies decide the answer."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Task frequencies are a count map."),
            ProblemPrereq("priority_queue_adt", "Priority Queue", "The simulation solution schedules from a max-heap — compare the two."),
        ),
        linkedTopicId = "top_k_pattern",
        linkedTopicLabel = "Top-K Pattern",
    ),
)

private val moreBinarySearch = listOf(
    PracticeProblem(
        id = "first_last_position",
        title = "First and Last Position of an Element",
        patternId = "binary_search",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a sorted array with duplicates and a target, return the first and last index of the target, or [-1, -1] if absent.",
        examples = listOf(
            ProblemExample("nums = [5,7,7,8,8,10], target = 8", "[3, 4]", ""),
            ProblemExample("nums = [5,7,7,8,8,10], target = 6", "[-1, -1]", ""),
        ),
        constraints = listOf(
            "The array is sorted ascending and may contain duplicates.",
            "Required runtime is O(log n).",
        ),
        hints = listOf(
            "Plain binary search returns *an* occurrence, not the first or last one.",
            "On a hit, do not stop — record it and keep searching the side where an earlier (or later) copy would live.",
            "Two runs of the same loop, differing only in which side is kept, give both bounds.",
        ),
        approach = listOf(
            "Run the standard loop, but on equality record the index and continue instead of returning.",
            "For the first position, continue leftwards by setting hi = mid - 1.",
            "For the last position, continue rightwards by setting lo = mid + 1.",
            "Each run stays O(log n), so the pair costs O(log n) overall.",
        ),
        timeComplexity = "O(log n) — two halving searches",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun searchRange(nums: IntArray, target: Int): IntArray {
                fun bound(findFirst: Boolean): Int {
                    var lo = 0
                    var hi = nums.size - 1
                    var found = -1

                    while (lo <= hi) {
                        val mid = lo + (hi - lo) / 2
                        when {
                            nums[mid] < target -> lo = mid + 1
                            nums[mid] > target -> hi = mid - 1
                            else -> {
                                found = mid                       // record, then keep going
                                if (findFirst) hi = mid - 1 else lo = mid + 1
                            }
                        }
                    }
                    return found
                }

                return intArrayOf(bound(true), bound(false))
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search", "Binary Search", "Boundary search is the standard loop with a different stop rule."),
            ProblemPrereq("array", "Array", "Sorted contiguous storage is what the halving relies on."),
        ),
        linkedTopicId = "binary_search",
        linkedTopicLabel = "Binary Search",
    ),
    PracticeProblem(
        id = "search_2d_matrix",
        title = "Search a 2D Matrix",
        patternId = "binary_search",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Each row of a matrix is sorted ascending and each row's first value exceeds the previous row's last. Decide whether a target is present in O(log(m × n)).",
        examples = listOf(
            ProblemExample("matrix = [[1,3,5,7],[10,11,16,20],[23,30,34,60]], target = 3", "true", ""),
            ProblemExample("same matrix, target = 13", "false", ""),
        ),
        constraints = listOf(
            "1 ≤ rows, cols ≤ 100",
            "The required runtime rules out searching row by row.",
        ),
        hints = listOf(
            "Read the row conditions together: laid end to end, the whole matrix is one sorted sequence.",
            "So binary search the range 0 until rows × cols as if it were a flat array.",
            "Convert a flat index back with row = index / cols and column = index % cols.",
        ),
        approach = listOf(
            "Treat indices 0 until rows × cols as a virtual sorted array.",
            "Run the standard loop over that range.",
            "Map each midpoint to a cell with integer division and modulo.",
            "One search over m × n elements gives log(m × n) — no separate row search needed.",
        ),
        timeComplexity = "O(log(m × n))",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun searchMatrix(matrix: Array<IntArray>, target: Int): Boolean {
                if (matrix.isEmpty() || matrix[0].isEmpty()) return false
                val cols = matrix[0].size

                var lo = 0
                var hi = matrix.size * cols - 1
                while (lo <= hi) {
                    val mid = lo + (hi - lo) / 2
                    val value = matrix[mid / cols][mid % cols]   // flat index -> cell
                    when {
                        value == target -> return true
                        value < target -> lo = mid + 1
                        else -> hi = mid - 1
                    }
                }
                return false
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search", "Binary Search", "The matrix is searched as one virtual sorted array."),
            ProblemPrereq("array", "Array", "Row-major index arithmetic is array addressing in two dimensions."),
        ),
        linkedTopicId = "binary_search",
        linkedTopicLabel = "Binary Search",
    ),
)

private val morePrefixSum = listOf(
    PracticeProblem(
        id = "product_except_self",
        title = "Product of Array Except Self",
        patternId = "prefix_sum",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Return an array where each position holds the product of every other element. Solve without division and in O(n).",
        examples = listOf(
            ProblemExample("nums = [1, 2, 3, 4]", "[24, 12, 8, 6]", ""),
            ProblemExample("nums = [-1, 1, 0, -3, 3]", "[0, 0, 9, 0, 0]", "Zeroes are handled with no special case."),
        ),
        constraints = listOf(
            "Division is not allowed — it breaks on zeroes.",
            "O(1) extra space beyond the output array.",
        ),
        hints = listOf(
            "The product excluding i is (everything left of i) × (everything right of i).",
            "Those are prefix and suffix products — the multiplicative version of prefix sums.",
            "Write the prefixes into the output on one pass, then multiply the suffixes in on a reverse pass.",
        ),
        approach = listOf(
            "First pass, left to right: store the running product of everything before i into out[i].",
            "Second pass, right to left: multiply out[i] by the running product of everything after i.",
            "Both running products start at 1, which handles the ends without special cases.",
            "The output array doubles as scratch space, so no extra arrays are allocated.",
        ),
        timeComplexity = "O(n) — two passes",
        spaceComplexity = "O(1) excluding the output",
        solutionCode = """
            fun productExceptSelf(nums: IntArray): IntArray {
                val out = IntArray(nums.size)

                var prefix = 1
                for (i in nums.indices) {
                    out[i] = prefix          // product of everything before i
                    prefix *= nums[i]
                }

                var suffix = 1
                for (i in nums.indices.reversed()) {
                    out[i] *= suffix         // fold in everything after i
                    suffix *= nums[i]
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("prefix_sum", "Prefix Sum", "Prefix/suffix accumulation, with multiplication as the operator."),
            ProblemPrereq("array", "Array", "The output array is reused as scratch to hit O(1) space."),
        ),
        linkedTopicId = "prefix_sum",
        linkedTopicLabel = "Prefix Sum",
    ),
    PracticeProblem(
        id = "range_sum_2d",
        title = "Range Sum Query 2D",
        patternId = "prefix_sum",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a fixed matrix, answer many queries asking for the sum of a rectangular submatrix.",
        examples = listOf(
            ProblemExample("sumRegion(2, 1, 4, 3) on the classic 5×5 example", "8", ""),
            ProblemExample("sumRegion(1, 1, 2, 2)", "11", ""),
        ),
        constraints = listOf(
            "The matrix never changes after construction.",
            "Up to 10⁴ queries — each must be O(1).",
        ),
        hints = listOf(
            "Extend 1D prefix sums: prefix[r][c] = sum of the rectangle from the origin to (r, c).",
            "Building it needs inclusion-exclusion — the two overlapping strips double-count their shared corner.",
            "A query is the same idea in reverse: subtract the strip above and the strip left, then add back the corner.",
        ),
        approach = listOf(
            "Allocate a (rows + 1) × (cols + 1) prefix grid so index 0 acts as a zero border.",
            "Fill it with prefix[r+1][c+1] = value + prefix[r][c+1] + prefix[r+1][c] - prefix[r][c].",
            "Answer a query as prefix[r2+1][c2+1] - prefix[r1][c2+1] - prefix[r2+1][c1] + prefix[r1][c1].",
            "The final + prefix[r1][c1] restores the corner subtracted twice.",
        ),
        timeComplexity = "O(rows × cols) to build, O(1) per query",
        spaceComplexity = "O(rows × cols)",
        solutionCode = """
            class NumMatrix(matrix: Array<IntArray>) {
                private val prefix: Array<IntArray>

                init {
                    val rows = matrix.size
                    val cols = if (rows == 0) 0 else matrix[0].size
                    prefix = Array(rows + 1) { IntArray(cols + 1) }   // zero border

                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            prefix[r + 1][c + 1] =
                                matrix[r][c] + prefix[r][c + 1] + prefix[r + 1][c] - prefix[r][c]
                        }
                    }
                }

                fun sumRegion(r1: Int, c1: Int, r2: Int, c2: Int): Int =
                    prefix[r2 + 1][c2 + 1] - prefix[r1][c2 + 1] - prefix[r2 + 1][c1] + prefix[r1][c1]
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("prefix_sum", "Prefix Sum", "The 2D form is the same technique with inclusion-exclusion."),
            ProblemPrereq("array", "Array", "Row-major layout and index offsets carry the zero border."),
        ),
        linkedTopicId = "prefix_sum",
        linkedTopicLabel = "Prefix Sum",
    ),
)

private val moreHashing = listOf(
    PracticeProblem(
        id = "valid_anagram",
        title = "Valid Anagram",
        patternId = "hashing",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given two strings, decide whether one is an anagram of the other.",
        examples = listOf(
            ProblemExample("s = \"anagram\", t = \"nagaram\"", "true", ""),
            ProblemExample("s = \"rat\", t = \"car\"", "false", ""),
        ),
        constraints = listOf(
            "Lowercase English letters only.",
            "1 ≤ length ≤ 5 × 10⁴",
        ),
        hints = listOf(
            "Sorting both strings works but costs O(n log n).",
            "Anagrams are defined by letter counts, so count instead of sort.",
            "One pass can increment for one string and decrement for the other — all zeroes means anagram.",
        ),
        approach = listOf(
            "Return false immediately on a length mismatch.",
            "Walk both strings together, incrementing the count for s and decrementing for t.",
            "Every slot ending at zero means each letter appeared equally often.",
            "The fixed 26-slot array makes the space constant.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) — one 26-element count",
        solutionCode = """
            fun isAnagram(s: String, t: String): Boolean {
                if (s.length != t.length) return false

                val counts = IntArray(26)
                for (i in s.indices) {
                    counts[s[i] - 'a']++
                    counts[t[i] - 'a']--
                }
                return counts.all { it == 0 }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "A fixed-size count array is a hash map with a perfect hash."),
            ProblemPrereq("counting_sort", "Counting Sort", "Same tally step that counting sort begins with."),
        ),
        linkedTopicId = "hash_table",
        linkedTopicLabel = "Hash Table / Hash Map",
    ),
    PracticeProblem(
        id = "isomorphic_strings",
        title = "Isomorphic Strings",
        patternId = "hashing",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Two strings are isomorphic if the characters of one can be replaced to get the other, preserving order, with no two characters mapping to the same character.",
        examples = listOf(
            ProblemExample("s = \"egg\", t = \"add\"", "true", "e→a, g→d."),
            ProblemExample("s = \"foo\", t = \"bar\"", "false", "o would need to map to both a and r."),
        ),
        constraints = listOf(
            "1 ≤ length ≤ 5 × 10⁴",
            "The mapping must be one-to-one in both directions.",
        ),
        hints = listOf(
            "Record the mapping as you scan and check consistency at each position.",
            "One map is not enough: \"ab\" → \"aa\" passes a forward-only check but is not isomorphic.",
            "So also track which target characters are already claimed.",
        ),
        approach = listOf(
            "Keep a forward map from s-characters to t-characters and a record of claimed t-characters.",
            "At each index, if the s-character is unmapped, reject when its t-character is already claimed, otherwise record both.",
            "If it is already mapped, reject on any disagreement with the current t-character.",
            "Surviving the whole scan means the bijection is consistent.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(k) for the character maps",
        solutionCode = """
            fun isIsomorphic(s: String, t: String): Boolean {
                if (s.length != t.length) return false

                val forward = HashMap<Char, Char>()
                val claimed = HashSet<Char>()

                for (i in s.indices) {
                    val a = s[i]
                    val b = t[i]
                    val mapped = forward[a]

                    if (mapped == null) {
                        if (!claimed.add(b)) return false   // b already taken by another char
                        forward[a] = b
                    } else if (mapped != b) {
                        return false
                    }
                }
                return true
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Both the mapping and the claimed-set are O(1) lookups."),
            ProblemPrereq("map_adt", "Map", "The problem is literally asking whether a valid map exists."),
        ),
        linkedTopicId = "hash_table",
        linkedTopicLabel = "Hash Table / Hash Map",
    ),
)

private val moreStack = listOf(
    PracticeProblem(
        id = "min_stack",
        title = "Min Stack",
        patternId = "monotonic_stack",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Design a stack supporting push, pop, top and getMin, all in O(1).",
        examples = listOf(
            ProblemExample("push(-2), push(0), push(-3), getMin()", "-3", ""),
            ProblemExample("pop(), top(), getMin()", "0, then -2", "After popping -3 the minimum reverts."),
        ),
        constraints = listOf(
            "All four operations must be O(1) — scanning for the minimum is not allowed.",
            "pop, top and getMin are only called on a non-empty stack.",
        ),
        hints = listOf(
            "One variable holding the minimum breaks on pop — you cannot recover the previous minimum.",
            "Every element needs to remember what the minimum was while it was on top.",
            "Push that alongside it, in a parallel stack.",
        ),
        approach = listOf(
            "Keep two stacks: the values, and the minimum at each depth.",
            "On push, push min(newValue, currentMin) onto the second stack.",
            "On pop, pop both — the previous minimum is restored automatically.",
            "getMin reads the top of the minimum stack, so every operation is O(1) at the cost of O(n) extra space.",
        ),
        timeComplexity = "O(1) for every operation",
        spaceComplexity = "O(n) for the parallel minimum stack",
        solutionCode = """
            class MinStack {
                private val values = ArrayDeque<Int>()
                private val mins = ArrayDeque<Int>()   // minimum at each depth

                fun push(v: Int) {
                    values.addLast(v)
                    mins.addLast(if (mins.isEmpty()) v else minOf(v, mins.last()))
                }

                fun pop() {
                    values.removeLast()
                    mins.removeLast()                  // previous minimum restored
                }

                fun top(): Int = values.last()

                fun getMin(): Int = mins.last()
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("stack", "Stack", "The design is two stacks kept in lockstep."),
            ProblemPrereq("priority_queue_adt", "Priority Queue", "Contrast: a heap gives the minimum but not LIFO removal."),
        ),
        linkedTopicId = "stack",
        linkedTopicLabel = "Stack",
    ),
    PracticeProblem(
        id = "evaluate_rpn",
        title = "Evaluate Reverse Polish Notation",
        patternId = "monotonic_stack",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Evaluate an arithmetic expression in reverse Polish notation, where operators follow their operands.",
        examples = listOf(
            ProblemExample("[\"2\", \"1\", \"+\", \"3\", \"*\"]", "9", "(2 + 1) × 3."),
            ProblemExample("[\"4\", \"13\", \"5\", \"/\", \"+\"]", "6", "4 + (13 / 5)."),
        ),
        constraints = listOf(
            "Division truncates toward zero.",
            "The expression is always valid.",
        ),
        hints = listOf(
            "RPN needs no parentheses because the operand order is already fixed — what structure holds pending operands?",
            "A stack: push numbers, and an operator consumes the two most recent.",
            "Pop order matters — the first pop is the right operand.",
        ),
        approach = listOf(
            "Scan the tokens left to right.",
            "Push every number.",
            "On an operator, pop twice; the first pop is the right-hand operand, the second the left.",
            "Push the result back. The single value left at the end is the answer.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(n)",
        solutionCode = """
            fun evalRPN(tokens: Array<String>): Int {
                val stack = ArrayDeque<Int>()

                for (t in tokens) {
                    when (t) {
                        "+", "-", "*", "/" -> {
                            val b = stack.removeLast()   // right operand pops first
                            val a = stack.removeLast()
                            stack.addLast(
                                when (t) {
                                    "+" -> a + b
                                    "-" -> a - b
                                    "*" -> a * b
                                    else -> a / b        // truncates toward zero
                                },
                            )
                        }
                        else -> stack.addLast(t.toInt())
                    }
                }
                return stack.last()
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("stack", "Stack", "Postfix evaluation is the classic stack application."),
            ProblemPrereq("string", "String", "Tokens must be classified and parsed."),
        ),
        linkedTopicId = "stack",
        linkedTopicLabel = "Stack",
    ),
)

private val moreTraversal = listOf(
    PracticeProblem(
        id = "clone_graph",
        title = "Clone Graph",
        patternId = "traversal",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a reference to a node in a connected undirected graph, return a deep copy of the whole graph.",
        examples = listOf(
            ProblemExample("adjacency = [[2,4], [1,3], [2,4], [1,3]]", "an identical 4-node graph of new objects", ""),
            ProblemExample("adjacency = [[]]", "a single isolated node copy", ""),
        ),
        constraints = listOf(
            "The graph is connected and undirected, so cycles are guaranteed.",
            "0 ≤ node count ≤ 100",
        ),
        hints = listOf(
            "Undirected edges mean every edge is a 2-cycle — naive recursion never terminates.",
            "Keep a map from original node to its copy and consult it before recursing.",
            "Register a node's copy in the map *before* recursing into its neighbours, or a cycle re-enters and loops.",
        ),
        approach = listOf(
            "Maintain a map from original to copy, which doubles as the visited-set.",
            "On visiting a node, return its existing copy if present.",
            "Otherwise create the copy, store it immediately, then recurse into each neighbour and attach the returned copies.",
            "Register-before-recurse is what makes the cycle terminate.",
        ),
        timeComplexity = "O(V + E)",
        spaceComplexity = "O(V) for the map and recursion",
        solutionCode = """
            class GraphNode(val value: Int) {
                val neighbors = mutableListOf<GraphNode>()
            }

            fun cloneGraph(node: GraphNode?): GraphNode? {
                val copies = HashMap<GraphNode, GraphNode>()

                fun dfs(cur: GraphNode): GraphNode {
                    copies[cur]?.let { return it }

                    val copy = GraphNode(cur.value)
                    copies[cur] = copy                        // register BEFORE recursing
                    for (n in cur.neighbors) copy.neighbors += dfs(n)
                    return copy
                }

                return node?.let { dfs(it) }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "The traversal is DFS with a visited-map."),
            ProblemPrereq("graph", "Graph", "Adjacency structure and cycles are the whole difficulty."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Original → copy lookup must be O(1)."),
        ),
        linkedTopicId = "dfs",
        linkedTopicLabel = "Depth-First Search (DFS)",
    ),
    PracticeProblem(
        id = "course_schedule",
        title = "Course Schedule",
        patternId = "traversal",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a number of courses and prerequisite pairs [course, needs], decide whether every course can be completed.",
        examples = listOf(
            ProblemExample("numCourses = 2, prerequisites = [[1,0]]", "true", "Take 0 then 1."),
            ProblemExample("numCourses = 2, prerequisites = [[1,0], [0,1]]", "false", "Circular dependency."),
        ),
        constraints = listOf(
            "1 ≤ numCourses ≤ 2000",
            "Pairs may repeat.",
        ),
        hints = listOf(
            "Model it as a directed graph: an edge from a prerequisite to the course that needs it.",
            "Completing everything is possible exactly when the graph has no cycle — that is topological ordering.",
            "Kahn's algorithm: repeatedly take a course whose remaining prerequisite count is zero.",
        ),
        approach = listOf(
            "Build an adjacency list plus an in-degree count of unmet prerequisites per course.",
            "Queue every course with in-degree zero.",
            "Dequeue a course, count it as taken, and decrement its dependents; any that hits zero joins the queue.",
            "If the taken count reaches numCourses, a full ordering exists. A shortfall means a cycle held some courses back.",
        ),
        timeComplexity = "O(V + E)",
        spaceComplexity = "O(V + E) for the adjacency list and queue",
        solutionCode = """
            fun canFinish(numCourses: Int, prerequisites: Array<IntArray>): Boolean {
                val adjacency = Array(numCourses) { mutableListOf<Int>() }
                val indegree = IntArray(numCourses)

                for (pair in prerequisites) {
                    val course = pair[0]
                    val needs = pair[1]
                    adjacency[needs] += course     // needs -> course
                    indegree[course]++
                }

                val queue = ArrayDeque<Int>()
                for (c in 0 until numCourses) if (indegree[c] == 0) queue.addLast(c)

                var taken = 0
                while (queue.isNotEmpty()) {
                    val c = queue.removeFirst()
                    taken++
                    for (next in adjacency[c]) {
                        if (--indegree[next] == 0) queue.addLast(next)
                    }
                }
                return taken == numCourses         // shortfall means a cycle
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("bfs", "Breadth-First Search (BFS)", "Kahn's algorithm is BFS over in-degree-zero nodes."),
            ProblemPrereq("graph", "Graph", "Directed edges and cycle detection are the model."),
            ProblemPrereq("queue", "Queue", "The ready-set of unblocked courses is a FIFO queue."),
        ),
        linkedTopicId = "bfs",
        linkedTopicLabel = "Breadth-First Search (BFS)",
    ),
)

private val moreDp = listOf(
    PracticeProblem(
        id = "unique_paths",
        title = "Unique Paths",
        patternId = "dynamic_programming",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "A robot starts at the top-left of an m × n grid and may move only right or down. How many distinct paths reach the bottom-right?",
        examples = listOf(
            ProblemExample("m = 3, n = 7", "28", ""),
            ProblemExample("m = 3, n = 2", "3", ""),
        ),
        constraints = listOf(
            "1 ≤ m, n ≤ 100",
            "The answer fits in a 32-bit integer.",
        ),
        hints = listOf(
            "Every cell is entered from above or from the left, and nowhere else.",
            "So paths(r, c) = paths(r-1, c) + paths(r, c-1), with the first row and column all 1.",
            "Filling row by row means the previous row is all you need — one array suffices.",
        ),
        approach = listOf(
            "State: paths(r, c) = number of ways to reach that cell.",
            "Initialise a single row of 1s, representing the topmost row.",
            "For each following row, sweep left to right adding the value to its left — the array then holds that row.",
            "The last entry after m - 1 sweeps is the answer.",
        ),
        timeComplexity = "O(m × n)",
        spaceComplexity = "O(n) with the rolling row",
        solutionCode = """
            fun uniquePaths(m: Int, n: Int): Int {
                val row = IntArray(n) { 1 }        // top row: exactly one path each

                repeat(m - 1) {
                    for (c in 1 until n) {
                        // row[c] is still the cell above; row[c - 1] is the cell to the left.
                        row[c] += row[c - 1]
                    }
                }
                return row[n - 1]
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("fibonacci_dp", "Fibonacci (Dynamic Programming)", "Same additive recurrence, laid out in two dimensions."),
            ProblemPrereq("matrix_chain_multiplication", "Matrix Chain Multiplication", "Another table-filling DP where order of evaluation matters."),
        ),
        linkedTopicId = "fibonacci_dp",
        linkedTopicLabel = "Fibonacci (Dynamic Programming)",
    ),
    PracticeProblem(
        id = "longest_common_subsequence_problem",
        title = "Longest Common Subsequence",
        patternId = "dynamic_programming",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given two strings, return the length of their longest common subsequence — characters in order but not necessarily contiguous.",
        examples = listOf(
            ProblemExample("a = \"abcde\", b = \"ace\"", "3", "\"ace\"."),
            ProblemExample("a = \"abc\", b = \"def\"", "0", "Nothing in common."),
        ),
        constraints = listOf(
            "1 ≤ lengths ≤ 1000",
            "Subsequence, not substring — gaps are allowed.",
        ),
        hints = listOf(
            "Compare the last characters of the two prefixes you are considering.",
            "If they match, both can be consumed together: 1 + LCS of the shorter prefixes.",
            "If not, one of the two must be dropped — take the better of the two options.",
        ),
        approach = listOf(
            "Define dp[i][j] as the LCS length of a's first i characters and b's first j.",
            "Match → dp[i][j] = dp[i-1][j-1] + 1.",
            "Mismatch → dp[i][j] = max(dp[i-1][j], dp[i][j-1]).",
            "Row and column 0 stay 0 (an empty string shares nothing), and the answer is the bottom-right cell.",
        ),
        timeComplexity = "O(m × n)",
        spaceComplexity = "O(m × n), reducible to O(min(m, n)) with rolling rows",
        solutionCode = """
            fun longestCommonSubsequence(a: String, b: String): Int {
                val dp = Array(a.length + 1) { IntArray(b.length + 1) }

                for (i in 1..a.length) {
                    for (j in 1..b.length) {
                        dp[i][j] = if (a[i - 1] == b[j - 1]) {
                            dp[i - 1][j - 1] + 1              // consume both
                        } else {
                            maxOf(dp[i - 1][j], dp[i][j - 1]) // drop one
                        }
                    }
                }
                return dp[a.length][b.length]
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("longest_common_subsequence", "Longest Common Subsequence", "The topic derives this exact table."),
            ProblemPrereq("edit_distance", "Edit Distance", "Same grid shape with a different cost rule — learn them together."),
        ),
        linkedTopicId = "longest_common_subsequence",
        linkedTopicLabel = "Longest Common Subsequence",
    ),
)

private val moreBacktracking = listOf(
    PracticeProblem(
        id = "letter_combinations",
        title = "Letter Combinations of a Phone Number",
        patternId = "backtracking",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given digits 2–9, return every letter combination the number could spell on a phone keypad.",
        examples = listOf(
            ProblemExample("digits = \"23\"", "[\"ad\",\"ae\",\"af\",\"bd\",\"be\",\"bf\",\"cd\",\"ce\",\"cf\"]", ""),
            ProblemExample("digits = \"\"", "[]", "Empty input yields no combinations."),
        ),
        constraints = listOf(
            "0 ≤ digits.length ≤ 4",
            "Digits are 2 through 9 only.",
        ),
        hints = listOf(
            "Each digit contributes one character, so every result has exactly digits.length characters.",
            "That makes the recursion depth fixed — one level per digit.",
            "At each level, loop over that digit's letters, append, recurse, then remove.",
        ),
        approach = listOf(
            "Map each digit to its letters.",
            "Recurse on the digit index, carrying a partial string.",
            "Reaching the end of the digits means the partial string is complete — record it.",
            "Removing the appended character on the way out is the backtrack that lets siblings reuse the buffer.",
        ),
        timeComplexity = "O(4ⁿ × n) worst case, with n digits mapping to up to four letters",
        spaceComplexity = "O(n) for the buffer and recursion depth",
        solutionCode = """
            fun letterCombinations(digits: String): List<String> {
                if (digits.isEmpty()) return emptyList()

                val keys = mapOf(
                    '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
                    '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
                )

                val out = mutableListOf<String>()
                val path = StringBuilder()

                fun backtrack(index: Int) {
                    if (index == digits.length) {
                        out += path.toString()
                        return
                    }
                    for (c in keys[digits[index]].orEmpty()) {
                        path.append(c)
                        backtrack(index + 1)
                        path.deleteCharAt(path.lastIndex)   // undo
                    }
                }

                backtrack(0)
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("permutation_generation", "Permutation Generation", "Cartesian product enumeration is the same recursive shape."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "The digit tree is walked depth-first with undo."),
        ),
        linkedTopicId = "permutation_generation",
        linkedTopicLabel = "Permutation Generation",
    ),
    PracticeProblem(
        id = "word_search_grid",
        title = "Word Search",
        patternId = "backtracking",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a character grid and a word, decide whether the word can be spelled by walking to orthogonally adjacent cells without reusing a cell.",
        examples = listOf(
            ProblemExample("board = [[A,B,C,E],[S,F,C,S],[A,D,E,E]], word = \"ABCCED\"", "true", ""),
            ProblemExample("same board, word = \"ABCB\"", "false", "The B would have to be reused."),
        ),
        constraints = listOf(
            "1 ≤ rows, cols ≤ 6; 1 ≤ word.length ≤ 15",
            "A cell may not be used twice in one path.",
        ),
        hints = listOf(
            "Try every cell as a starting point, then walk letter by letter.",
            "\"No reuse\" needs a visited-marker — but only along the current path, not globally.",
            "Overwrite the cell with a sentinel during the recursion and restore it afterwards; that restore is the backtrack.",
        ),
        approach = listOf(
            "For each cell, launch a search matching word[0] onwards.",
            "Fail fast when out of bounds or the character does not match; succeed when the whole word is consumed.",
            "Mark the cell with a sentinel, recurse into all four neighbours, then restore the original character.",
            "Restoring is essential — a different starting cell must see the grid untouched.",
        ),
        timeComplexity = "O(rows × cols × 4^L) worst case for a word of length L",
        spaceComplexity = "O(L) recursion depth, with no separate visited grid",
        solutionCode = """
            fun exist(board: Array<CharArray>, word: String): Boolean {
                fun search(r: Int, c: Int, index: Int): Boolean {
                    if (index == word.length) return true
                    if (r !in board.indices || c !in board[0].indices) return false
                    if (board[r][c] != word[index]) return false

                    val saved = board[r][c]
                    board[r][c] = '#'          // mark for this path only
                    val found = search(r + 1, c, index + 1) ||
                        search(r - 1, c, index + 1) ||
                        search(r, c + 1, index + 1) ||
                        search(r, c - 1, index + 1)
                    board[r][c] = saved        // backtrack

                    return found
                }

                for (r in board.indices) {
                    for (c in board[0].indices) {
                        if (search(r, c, 0)) return true
                    }
                }
                return false
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Each start cell launches a depth-first walk with pruning."),
            ProblemPrereq("n_queens", "N-Queens", "The mark-recurse-restore skeleton is identical."),
            ProblemPrereq("trie", "Trie (Prefix Tree)", "Searching many words at once is where a trie takes over."),
        ),
        linkedTopicId = "n_queens",
        linkedTopicLabel = "N-Queens",
    ),
)

internal val extraPatternProblems: List<PracticeProblem> =
    moreSlidingWindow + moreTwoPointers + moreFastSlow + moreMergeIntervals + moreTopK +
        moreBinarySearch + morePrefixSum + moreHashing + moreStack + moreTraversal +
        moreDp + moreBacktracking
