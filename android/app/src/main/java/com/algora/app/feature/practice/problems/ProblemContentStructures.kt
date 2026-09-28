package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// Three groups organised by the structure being manipulated rather than by a technique: pointer
// surgery on linked lists, ordered-tree properties, and greedy exchange arguments.

internal val structurePatterns = listOf(
    ProblemPattern(
        id = "linked_list_ops",
        name = "Linked List Surgery",
        blurb = "Rewiring next-pointers in place — reversal, weaving and offset walks.",
        accentColor = 0xFF06B6D4,
        topicId = "singly_linked_list",
        isPremium = true,
    ),
    ProblemPattern(
        id = "trees_bst",
        name = "Trees & BST",
        blurb = "Recursive structure plus the ordering invariant that makes a BST searchable.",
        accentColor = 0xFF84CC16,
        topicId = "binary_search_tree",
        isPremium = true,
    ),
    ProblemPattern(
        id = "greedy",
        name = "Greedy",
        blurb = "Commit to the locally best choice — when an exchange argument proves it safe.",
        accentColor = 0xFFD946EF,
        topicId = "fractional_knapsack",
        isPremium = true,
    ),
)

private val linkedListProblems = listOf(
    PracticeProblem(
        id = "reverse_linked_list",
        title = "Reverse a Linked List",
        patternId = "linked_list_ops",
        difficulty = Difficulty.BEGINNER,
        prompt = "Reverse a singly linked list in place and return the new head.",
        examples = listOf(
            ProblemExample("1 → 2 → 3 → 4 → 5", "5 → 4 → 3 → 2 → 1", ""),
            ProblemExample("empty list", "null", ""),
        ),
        constraints = listOf(
            "O(1) extra space — do not build a new list or an array.",
            "0 ≤ node count ≤ 5000",
        ),
        hints = listOf(
            "Each node's next must point at its predecessor instead of its successor.",
            "Flipping a pointer destroys the only reference to the rest of the list — save it first.",
            "Three references are enough: the node behind, the current node, and the one ahead.",
        ),
        approach = listOf(
            "Start with prev = null and cur = head.",
            "Save cur.next before overwriting it — this is the step everyone loses the list on.",
            "Point cur.next at prev, then shift prev and cur forward by one.",
            "When cur reaches null, prev is the new head.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) — pure pointer rewiring",
        solutionCode = """
            class Node(val value: Int) { var next: Node? = null }

            fun reverseList(head: Node?): Node? {
                var prev: Node? = null
                var cur = head

                while (cur != null) {
                    val next = cur.next   // save before destroying the link
                    cur.next = prev
                    prev = cur
                    cur = next
                }
                return prev               // old tail is the new head
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "Node/next mechanics and why order of assignment matters."),
            ProblemPrereq("doubly_linked_list", "Doubly Linked List", "Contrast: a back-pointer makes reversal trivial, at a memory cost."),
        ),
        linkedTopicId = "singly_linked_list",
        linkedTopicLabel = "Singly Linked List",
    ),
    PracticeProblem(
        id = "merge_two_sorted_lists",
        title = "Merge Two Sorted Lists",
        patternId = "linked_list_ops",
        difficulty = Difficulty.BEGINNER,
        prompt = "Merge two sorted linked lists into one sorted list by splicing the existing nodes together.",
        examples = listOf(
            ProblemExample("a = 1 → 2 → 4, b = 1 → 3 → 4", "1 → 1 → 2 → 3 → 4 → 4", ""),
            ProblemExample("a = empty, b = 0", "0", ""),
        ),
        constraints = listOf(
            "Reuse the input nodes; do not allocate new ones.",
            "Both inputs are sorted ascending.",
        ),
        hints = listOf(
            "At each step the next output node is the smaller of the two current heads.",
            "Appending needs a tail reference, and the first append has no tail yet.",
            "A dummy head removes that special case — return dummy.next at the end.",
        ),
        approach = listOf(
            "Create a dummy node and let tail track the end of the output.",
            "While both lists have nodes, splice the smaller head on and advance that list.",
            "When one list empties, attach the remainder of the other in one assignment — it is already sorted.",
            "Return dummy.next, the real head.",
        ),
        timeComplexity = "O(m + n)",
        spaceComplexity = "O(1) — nodes are spliced, not copied",
        solutionCode = """
            fun mergeTwoLists(a: Node?, b: Node?): Node? {
                val dummy = Node(0)
                var tail = dummy
                var p = a
                var q = b

                while (p != null && q != null) {
                    if (p.value <= q.value) {
                        tail.next = p
                        p = p.next
                    } else {
                        tail.next = q
                        q = q.next
                    }
                    tail = tail.next!!
                }
                tail.next = p ?: q      // one list is already sorted and can be attached whole
                return dummy.next
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "Splicing means rewiring next, not copying values."),
            ProblemPrereq("merge_sort", "Merge Sort", "This is merge sort's merge step on lists instead of arrays."),
        ),
        linkedTopicId = "singly_linked_list",
        linkedTopicLabel = "Singly Linked List",
    ),
    PracticeProblem(
        id = "remove_nth_from_end",
        title = "Remove the Nth Node From the End",
        patternId = "linked_list_ops",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Remove the nth node counting from the end of a singly linked list and return the head.",
        examples = listOf(
            ProblemExample("1 → 2 → 3 → 4 → 5, n = 2", "1 → 2 → 3 → 5", ""),
            ProblemExample("1, n = 1", "empty list", "Removing the only node."),
        ),
        constraints = listOf(
            "1 ≤ n ≤ node count",
            "Do it in one pass.",
        ),
        hints = listOf(
            "Counting the length first is two passes. A fixed gap between two pointers encodes the same information.",
            "Advance one pointer n + 1 steps first, then move both until the leader falls off the end.",
            "The trailing pointer then sits just before the target — deleting needs the predecessor.",
        ),
        approach = listOf(
            "Anchor both pointers at a dummy node placed before the head, so removing the first node needs no special case.",
            "Advance the leader n + 1 steps.",
            "Move both together until the leader is null; the trailer is now the target's predecessor.",
            "Splice the target out with trailer.next = trailer.next.next and return dummy.next.",
        ),
        timeComplexity = "O(n) in one pass",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun removeNthFromEnd(head: Node?, n: Int): Node? {
                val dummy = Node(0)
                dummy.next = head

                var fast: Node? = dummy
                var slow: Node? = dummy
                repeat(n + 1) { fast = fast?.next }   // open the gap

                while (fast != null) {
                    fast = fast?.next
                    slow = slow?.next
                }
                slow?.next = slow?.next?.next          // splice out the target
                return dummy.next
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "Deletion requires holding the predecessor."),
            ProblemPrereq("two_pointer", "Two Pointer Technique", "A fixed-gap pair converts an end-relative index into a live position."),
        ),
        linkedTopicId = "singly_linked_list",
        linkedTopicLabel = "Singly Linked List",
    ),
    PracticeProblem(
        id = "palindrome_linked_list",
        title = "Palindrome Linked List",
        patternId = "linked_list_ops",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Decide whether a singly linked list reads the same forwards and backwards, using O(1) extra space.",
        examples = listOf(
            ProblemExample("1 → 2 → 2 → 1", "true", ""),
            ProblemExample("1 → 2", "false", ""),
        ),
        constraints = listOf(
            "O(1) extra space — copying values into an array is not allowed.",
            "1 ≤ node count ≤ 10⁵",
        ),
        hints = listOf(
            "A singly linked list cannot be walked backwards — so make part of it point backwards.",
            "Find the middle with fast/slow, then reverse the second half in place.",
            "Compare the two halves in lockstep; unequal lengths simply stop when the reversed half ends.",
        ),
        approach = listOf(
            "Locate the middle with the fast/slow walk.",
            "Reverse the list from the middle onwards using the standard three-pointer loop.",
            "Walk the original head and the reversed tail together, comparing values.",
            "Stopping when the reversed half runs out handles odd lengths — the middle node is never compared against anything.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1) — the second half is reversed in place",
        solutionCode = """
            fun isPalindrome(head: Node?): Boolean {
                // 1. Middle via fast/slow.
                var slow = head
                var fast = head
                while (fast?.next != null) {
                    slow = slow?.next
                    fast = fast.next?.next
                }

                // 2. Reverse from the middle onwards.
                var prev: Node? = null
                var cur = slow
                while (cur != null) {
                    val next = cur.next
                    cur.next = prev
                    prev = cur
                    cur = next
                }

                // 3. Walk both halves inwards.
                var front = head
                var back = prev
                while (back != null) {
                    if (front?.value != back.value) return false
                    front = front.next
                    back = back.next
                }
                return true
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "In-place reversal and traversal are both needed."),
            ProblemPrereq("fast_slow_pointers", "Fast & Slow Pointers", "Finding the midpoint in one pass."),
        ),
        linkedTopicId = "singly_linked_list",
        linkedTopicLabel = "Singly Linked List",
    ),
    PracticeProblem(
        id = "reorder_list",
        title = "Reorder List",
        patternId = "linked_list_ops",
        difficulty = Difficulty.ADVANCED,
        prompt = "Reorder a list L0 → L1 → … → Ln into L0 → Ln → L1 → Ln-1 → … in place, without changing node values.",
        examples = listOf(
            ProblemExample("1 → 2 → 3 → 4", "1 → 4 → 2 → 3", ""),
            ProblemExample("1 → 2 → 3 → 4 → 5", "1 → 5 → 2 → 4 → 3", ""),
        ),
        constraints = listOf(
            "Only pointers may be modified — no value swapping.",
            "1 ≤ node count ≤ 5 × 10⁴",
        ),
        hints = listOf(
            "The output alternates between the front of the list and the back — but there is no way to walk backwards.",
            "Split at the middle and reverse the second half; then \"the back\" becomes a forward walk.",
            "Weave the two halves by alternately splicing one node from each.",
        ),
        approach = listOf(
            "Find the middle with fast/slow and cut the list into two halves.",
            "Reverse the second half in place.",
            "Weave: repeatedly attach a node from the reversed half after the current front node, saving both successors first.",
            "The loop ends when the reversed half is exhausted, which leaves the shorter first half correctly terminated.",
        ),
        timeComplexity = "O(n) — three linear passes",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun reorderList(head: Node?) {
                if (head?.next == null) return

                // 1. Split at the middle.
                var slow = head
                var fast = head
                while (fast?.next?.next != null) {
                    slow = slow?.next
                    fast = fast.next?.next
                }
                var second = slow?.next
                slow?.next = null

                // 2. Reverse the second half.
                var prev: Node? = null
                while (second != null) {
                    val next = second.next
                    second.next = prev
                    prev = second
                    second = next
                }

                // 3. Weave the two halves together.
                var first = head
                var back = prev
                while (back != null) {
                    val f = first?.next
                    val b = back.next
                    first?.next = back
                    back.next = f
                    first = f
                    back = b
                }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("singly_linked_list", "Singly Linked List", "Three separate pointer manipulations composed together."),
            ProblemPrereq("fast_slow_pointers", "Fast & Slow Pointers", "The split point comes from the midpoint walk."),
        ),
        linkedTopicId = "singly_linked_list",
        linkedTopicLabel = "Singly Linked List",
    ),
)

private val treeProblems = listOf(
    PracticeProblem(
        id = "invert_binary_tree",
        title = "Invert a Binary Tree",
        patternId = "trees_bst",
        difficulty = Difficulty.BEGINNER,
        prompt = "Mirror a binary tree: every node's left and right subtrees swap places.",
        examples = listOf(
            ProblemExample("[4, 2, 7, 1, 3, 6, 9]", "[4, 7, 2, 9, 6, 3, 1]", ""),
            ProblemExample("null", "null", ""),
        ),
        constraints = listOf(
            "0 ≤ node count ≤ 100",
            "Mirror the whole tree, not just the root's children.",
        ),
        hints = listOf(
            "Swapping only the root's children is not enough — every level must mirror.",
            "The mirror of a tree is a node whose left is the mirror of its right, and vice versa.",
            "Capture one inverted subtree in a local before overwriting, or you will read back the value you just wrote.",
        ),
        approach = listOf(
            "Return null for a null node — the base case.",
            "Invert the left subtree and hold the result in a local variable.",
            "Assign the inverted right subtree to left, then the saved value to right.",
            "Ordering matters: assigning left first without saving loses the original left subtree.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(h) for the recursion stack",
        solutionCode = """
            class TreeNode(val value: Int) {
                var left: TreeNode? = null
                var right: TreeNode? = null
            }

            fun invertTree(root: TreeNode?): TreeNode? {
                if (root == null) return null

                val invertedLeft = invertTree(root.left)   // save before overwriting
                root.left = invertTree(root.right)
                root.right = invertedLeft
                return root
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("tree", "Tree", "Recursive structure: a node plus two subtrees."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "The traversal that reaches every node once."),
        ),
        linkedTopicId = "tree",
        linkedTopicLabel = "Tree",
    ),
    PracticeProblem(
        id = "diameter_of_binary_tree",
        title = "Diameter of a Binary Tree",
        patternId = "trees_bst",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Return the length of the longest path between any two nodes, measured in edges. The path need not pass through the root.",
        examples = listOf(
            ProblemExample("[1, 2, 3, 4, 5]", "3", "The path 4 → 2 → 1 → 3."),
            ProblemExample("[1, 2]", "1", ""),
        ),
        constraints = listOf(
            "1 ≤ node count ≤ 10⁴",
            "Length counts edges, not nodes.",
        ),
        hints = listOf(
            "Any path has a single highest node — its turning point.",
            "Through that node, the path length is leftDepth + rightDepth.",
            "So compute depths recursively and, at every node, update a running best with that sum.",
        ),
        approach = listOf(
            "Write a depth function returning 1 + max(left, right), with 0 for null.",
            "Before returning, record left + right as a diameter candidate.",
            "Track the maximum candidate across all nodes in an outer variable.",
            "One post-order pass computes every depth and every candidate together — no repeated depth queries.",
        ),
        timeComplexity = "O(n) — each node's depth is computed once",
        spaceComplexity = "O(h) recursion depth",
        solutionCode = """
            fun diameterOfBinaryTree(root: TreeNode?): Int {
                var best = 0

                fun depth(node: TreeNode?): Int {
                    if (node == null) return 0
                    val l = depth(node.left)
                    val r = depth(node.right)
                    best = maxOf(best, l + r)   // path turning at this node
                    return 1 + maxOf(l, r)
                }

                depth(root)
                return best
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("tree", "Tree", "Height and path definitions come straight from the topic."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Post-order lets a node see both children's results."),
        ),
        linkedTopicId = "tree",
        linkedTopicLabel = "Tree",
    ),
    PracticeProblem(
        id = "lowest_common_ancestor_bst",
        title = "Lowest Common Ancestor in a BST",
        patternId = "trees_bst",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a binary search tree and two values present in it, return their lowest common ancestor.",
        examples = listOf(
            ProblemExample("root = [6,2,8,0,4,7,9], p = 2, q = 8", "6", "They split at the root."),
            ProblemExample("root = [6,2,8,0,4,7,9], p = 2, q = 4", "2", "A node can be its own ancestor."),
        ),
        constraints = listOf(
            "Both values exist in the tree and are distinct.",
            "The BST ordering invariant holds everywhere.",
        ),
        hints = listOf(
            "In a general binary tree this needs a full search. The BST ordering gives you far more.",
            "If both values are smaller than the current node, the answer must be in the left subtree — and symmetrically for larger.",
            "The first node where they fall on opposite sides (or one equals the node) is the answer.",
        ),
        approach = listOf(
            "Walk down from the root, no recursion required.",
            "Both values below the node → go left. Both above → go right.",
            "Anything else means the values split here, or one *is* here — that node is the LCA.",
            "The walk is a single root-to-node descent, so it costs the tree's height.",
        ),
        timeComplexity = "O(h) — O(log n) on a balanced tree",
        spaceComplexity = "O(1) iteratively",
        solutionCode = """
            fun lowestCommonAncestor(root: TreeNode?, p: Int, q: Int): TreeNode? {
                var node = root

                while (node != null) {
                    node = when {
                        p < node.value && q < node.value -> node.left
                        p > node.value && q > node.value -> node.right
                        else -> return node       // they split here
                    }
                }
                return null
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search_tree", "Binary Search Tree", "The ordering invariant is what makes the descent decidable."),
            ProblemPrereq("binary_search", "Binary Search", "Descending a BST is binary search over tree structure."),
        ),
        linkedTopicId = "binary_search_tree",
        linkedTopicLabel = "Binary Search Tree",
    ),
    PracticeProblem(
        id = "validate_bst",
        title = "Validate Binary Search Tree",
        patternId = "trees_bst",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Decide whether a binary tree is a valid binary search tree: every node's whole left subtree is smaller and its whole right subtree larger.",
        examples = listOf(
            ProblemExample("[2, 1, 3]", "true", ""),
            ProblemExample("[5, 1, 4, null, null, 3, 6]", "false", "4 sits in 5's right subtree but 3 is below 5."),
        ),
        constraints = listOf(
            "1 ≤ node count ≤ 10⁴",
            "Values are distinct; equality is not allowed on either side.",
        ),
        hints = listOf(
            "Checking node.left.value < node.value < node.right.value locally is the classic wrong answer.",
            "A node deep in a right subtree is still constrained by every ancestor it descended from.",
            "Pass an allowed (low, high) range down and tighten it at each step.",
        ),
        approach = listOf(
            "Recurse with an open interval that the node's value must lie strictly inside.",
            "Going left tightens the upper bound to the current value; going right raises the lower bound.",
            "A null node is vacuously valid.",
            "Start with an unbounded range, using Long so that Int.MIN_VALUE and Int.MAX_VALUE nodes are handled.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(h) recursion depth",
        solutionCode = """
            fun isValidBST(root: TreeNode?): Boolean {
                fun check(node: TreeNode?, low: Long, high: Long): Boolean {
                    if (node == null) return true
                    if (node.value <= low || node.value >= high) return false

                    // Each descent tightens exactly one side of the range.
                    return check(node.left, low, node.value.toLong()) &&
                        check(node.right, node.value.toLong(), high)
                }

                return check(root, Long.MIN_VALUE, Long.MAX_VALUE)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search_tree", "Binary Search Tree", "The invariant being validated is the topic's definition."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Bounds are threaded down a depth-first walk."),
        ),
        linkedTopicId = "binary_search_tree",
        linkedTopicLabel = "Binary Search Tree",
    ),
    PracticeProblem(
        id = "kth_smallest_bst",
        title = "Kth Smallest Element in a BST",
        patternId = "trees_bst",
        difficulty = Difficulty.ADVANCED,
        prompt = "Return the kth smallest value in a binary search tree, counting from 1.",
        examples = listOf(
            ProblemExample("root = [3,1,4,null,2], k = 1", "1", ""),
            ProblemExample("root = [5,3,6,2,4,null,null,1], k = 3", "3", ""),
        ),
        constraints = listOf(
            "1 ≤ k ≤ node count",
            "Stop as soon as the answer is known — do not flatten the whole tree.",
        ),
        hints = listOf(
            "In-order traversal of a BST visits values in ascending order.",
            "So the kth node visited in-order is the answer — no sorting needed.",
            "Writing the traversal with an explicit stack lets you stop the moment the count reaches k.",
        ),
        approach = listOf(
            "Push the leftmost spine onto a stack.",
            "Pop a node — that is the next value in ascending order — and decrement the remaining count.",
            "Hitting zero returns immediately, so the traversal short-circuits.",
            "Otherwise descend into the popped node's right child and push its leftmost spine.",
        ),
        timeComplexity = "O(h + k) — only the visited prefix is traversed",
        spaceComplexity = "O(h) for the stack",
        solutionCode = """
            fun kthSmallest(root: TreeNode?, k: Int): Int {
                val stack = ArrayDeque<TreeNode>()
                var node = root
                var remaining = k

                while (node != null || stack.isNotEmpty()) {
                    while (node != null) {          // push the left spine
                        stack.addLast(node)
                        node = node.left
                    }
                    val current = stack.removeLast()
                    if (--remaining == 0) return current.value   // short-circuit
                    node = current.right
                }
                return -1
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("binary_search_tree", "Binary Search Tree", "In-order traversal yields sorted order only because of the BST invariant."),
            ProblemPrereq("stack", "Stack", "The explicit stack replaces recursion so the walk can stop early."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "In-order is one of the three depth-first orders."),
        ),
        linkedTopicId = "binary_search_tree",
        linkedTopicLabel = "Binary Search Tree",
    ),
)

private val greedyProblems = listOf(
    PracticeProblem(
        id = "assign_cookies",
        title = "Assign Cookies",
        patternId = "greedy",
        difficulty = Difficulty.BEGINNER,
        prompt = "Each child has a greed factor and each cookie a size. A child is content if their cookie is at least their greed. Maximise the number of content children.",
        examples = listOf(
            ProblemExample("greed = [1, 2, 3], cookies = [1, 1]", "1", "Only the least greedy child can be satisfied."),
            ProblemExample("greed = [1, 2], cookies = [1, 2, 3]", "2", ""),
        ),
        constraints = listOf(
            "Each child receives at most one cookie.",
            "0 ≤ sizes ≤ 10⁴",
        ),
        hints = listOf(
            "Giving a big cookie to a child a small one would satisfy is pure waste.",
            "Sort both sides and match the least greedy child with the smallest cookie that works.",
            "Every cookie is examined once, whether it is used or discarded.",
        ),
        approach = listOf(
            "Sort greeds and cookies ascending.",
            "Walk both with one index each. If the current cookie satisfies the current child, count it and advance the child.",
            "Advance the cookie index either way — a cookie too small for the least greedy remaining child is useless to everyone else.",
            "The exchange argument: swapping any matched pair for a smaller-cookie match never reduces the total.",
        ),
        timeComplexity = "O(n log n + m log m) for the sorts",
        spaceComplexity = "O(1) beyond the sorts",
        solutionCode = """
            fun findContentChildren(greed: IntArray, cookies: IntArray): Int {
                greed.sort()
                cookies.sort()

                var child = 0
                var cookie = 0
                while (child < greed.size && cookie < cookies.size) {
                    if (cookies[cookie] >= greed[child]) child++   // satisfied
                    cookie++                                       // spent or discarded
                }
                return child
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("fractional_knapsack", "Fractional Knapsack", "The same sort-then-take-greedily template, with a proof of optimality."),
            ProblemPrereq("two_pointer", "Two Pointer Technique", "Two indices advance over two sorted arrays."),
        ),
        linkedTopicId = "fractional_knapsack",
        linkedTopicLabel = "Fractional Knapsack",
    ),
    PracticeProblem(
        id = "jump_game",
        title = "Jump Game",
        patternId = "greedy",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Each element is the maximum jump length from that position. Starting at index 0, decide whether the last index is reachable.",
        examples = listOf(
            ProblemExample("nums = [2, 3, 1, 1, 4]", "true", "Jump 1 then 3."),
            ProblemExample("nums = [3, 2, 1, 0, 4]", "false", "Index 3 is a dead end that cannot be skipped."),
        ),
        constraints = listOf(
            "1 ≤ nums.size ≤ 10⁴",
            "0 ≤ nums[i] ≤ 10⁵",
        ),
        hints = listOf(
            "You do not need the *path*, only whether the end is reachable — so track reachability, not routes.",
            "Sweep left to right maintaining the furthest index reachable so far.",
            "Standing at an index beyond that furthest reach means the sweep hit an unbridgeable gap.",
        ),
        approach = listOf(
            "Keep a single `reach` value, starting at 0.",
            "At each index, fail immediately if the index exceeds `reach` — nothing can get here.",
            "Otherwise extend `reach` to max(reach, i + nums[i]).",
            "Surviving the whole sweep means the final index was always within reach. DP over every jump also works but costs O(n²).",
        ),
        timeComplexity = "O(n) — one pass",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun canJump(nums: IntArray): Boolean {
                var reach = 0

                for (i in nums.indices) {
                    if (i > reach) return false          // gap that cannot be crossed
                    reach = maxOf(reach, i + nums[i])
                }
                return true
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("fractional_knapsack", "Fractional Knapsack", "Greedy correctness rests on an exchange argument, as here."),
            ProblemPrereq("kadanes_algorithm", "Kadane's Algorithm", "Another single-pass running-best sweep over an array."),
        ),
        linkedTopicId = "fractional_knapsack",
        linkedTopicLabel = "Fractional Knapsack",
    ),
    PracticeProblem(
        id = "partition_labels",
        title = "Partition Labels",
        patternId = "greedy",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Split a string into as many parts as possible so that each letter appears in at most one part. Return the part sizes in order.",
        examples = listOf(
            ProblemExample("s = \"ababcbacadefegdehijhklij\"", "[9, 7, 8]", ""),
            ProblemExample("s = \"eccbbbbdec\"", "[10]", "No split is possible."),
        ),
        constraints = listOf(
            "Lowercase English letters only.",
            "1 ≤ s.length ≤ 500",
        ),
        hints = listOf(
            "A part cannot close while any letter inside it appears again later.",
            "So precompute each letter's last index in one pass.",
            "Sweep with a running end that stretches to the furthest last-index seen so far; when the cursor reaches it, the part closes.",
        ),
        approach = listOf(
            "First pass: record the last index of each letter.",
            "Second pass: extend `end` to max(end, last[current letter]).",
            "When the cursor equals `end`, every letter in the current part is fully contained — emit the length and start a new part.",
            "Cutting at the earliest legal point is what maximises the number of parts.",
        ),
        timeComplexity = "O(n) — two passes",
        spaceComplexity = "O(1) — 26 last-index slots",
        solutionCode = """
            fun partitionLabels(s: String): List<Int> {
                val last = HashMap<Char, Int>()
                for (i in s.indices) last[s[i]] = i     // last occurrence of each letter

                val out = mutableListOf<Int>()
                var start = 0
                var end = 0

                for (i in s.indices) {
                    end = maxOf(end, last.getValue(s[i]))
                    if (i == end) {                     // nothing inside recurs later
                        out += end - start + 1
                        start = i + 1
                    }
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Last-occurrence lookup per letter."),
            ProblemPrereq("merge_intervals_pattern", "Merge Intervals", "Each letter spans an interval; parts are merged interval blocks."),
        ),
        linkedTopicId = "fractional_knapsack",
        linkedTopicLabel = "Fractional Knapsack",
    ),
    PracticeProblem(
        id = "gas_station",
        title = "Gas Station",
        patternId = "greedy",
        difficulty = Difficulty.ADVANCED,
        prompt = "Stations sit in a circle with gas[i] fuel available and cost[i] fuel needed to reach the next. Return the starting index that allows a full loop, or -1.",
        examples = listOf(
            ProblemExample("gas = [1,2,3,4,5], cost = [3,4,5,1,2]", "3", "Start at index 3."),
            ProblemExample("gas = [2,3,4], cost = [3,4,3]", "-1", "No valid start."),
        ),
        constraints = listOf(
            "A solution, if it exists, is unique.",
            "1 ≤ n ≤ 10⁵",
        ),
        hints = listOf(
            "First decide *whether* an answer exists: compare total gas against total cost.",
            "If the tank goes negative somewhere between i and j, no start in that whole range can work.",
            "So on failure, jump the candidate start past j entirely instead of retrying i + 1.",
        ),
        approach = listOf(
            "Sweep once, accumulating both a total balance and a running tank of gas[i] - cost[i].",
            "Whenever the tank drops below zero, reset it and move the candidate start to the next station.",
            "The total balance decides feasibility: negative means no start works at all.",
            "Otherwise the last candidate start is the answer — every station it skipped was proven impossible.",
        ),
        timeComplexity = "O(n) — one pass instead of n simulated loops",
        spaceComplexity = "O(1)",
        solutionCode = """
            fun canCompleteCircuit(gas: IntArray, cost: IntArray): Int {
                var total = 0
                var tank = 0
                var start = 0

                for (i in gas.indices) {
                    val diff = gas[i] - cost[i]
                    total += diff
                    tank += diff
                    if (tank < 0) {      // nothing in [start, i] can be a valid start
                        start = i + 1
                        tank = 0
                    }
                }
                return if (total < 0) -1 else start
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("prefix_sum", "Prefix Sum", "The tank is a running prefix sum of gas minus cost."),
            ProblemPrereq("kadanes_algorithm", "Kadane's Algorithm", "Resetting on a negative running total is the same restart rule."),
            ProblemPrereq("fractional_knapsack", "Fractional Knapsack", "Greedy correctness again rests on an exchange argument."),
        ),
        linkedTopicId = "fractional_knapsack",
        linkedTopicLabel = "Fractional Knapsack",
    ),
)

internal val structureProblems: List<PracticeProblem> =
    linkedListProblems + treeProblems + greedyProblems
