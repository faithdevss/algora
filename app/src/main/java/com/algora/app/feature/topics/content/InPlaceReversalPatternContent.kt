package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val inPlaceReversalPatternContent = TopicContent(
    topicId = "in_place_reversal_pattern",
    whatIsIt = listOf(
        "In-place reversal rewires a linked list's next-pointers as it walks, using three references — previous, current, and a saved copy of the next node — and no extra list.",
        "Interviewers like it because the naive answers (copy into an array, or recurse) both cost O(n) space, and because sub-list variants force you to keep track of the boundary nodes.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Reverse the list / a sub-list / every k nodes\" with an explicit O(1)-space requirement.", 0xFFF59E0B),
        StepCard(2, "Save Before You Break", "next = cur.next first — the moment you reassign cur.next, the rest of the list is unreachable.", 0xFFEF4444),
        StepCard(3, "Flip and Slide", "cur.next = prev; then prev = cur, cur = next. Three assignments per node, one pass.", 0xFF3B82F6),
        StepCard(4, "Reconnect the Ends", "For sub-lists, remember the node before the segment and the segment's original head — they become the new joins.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each node is visited and rewired exactly once."),
        FormulaEntry("Space", "O(1)", "Three references regardless of list length."),
        FormulaEntry("Reverse in groups of k", "O(n) time", "⌊n/k⌋ segments, each reversed in place then stitched."),
    ),
    notationKey = listOf(
        NotationEntry("prev", "head of the already-reversed part"),
        NotationEntry("cur", "node being rewired now"),
        NotationEntry("next", "saved successor, the only way back to the rest"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Reverse a sub-list from p to q (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def reverse_between(head, p, q):
                    dummy = Node(0, head)
                    before = dummy
                    for _ in range(p - 1):
                        before = before.next
                    tail = cur = before.next        # becomes the segment's tail
                    prev = None
                    for _ in range(q - p + 1):
                        nxt = cur.next
                        cur.next = prev
                        prev, cur = cur, nxt
                    before.next = prev              # front joins the reversed head
                    tail.next = cur                 # old head joins the remainder
                    return dummy.next
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("link", 0xFF3B82F6, "List Surgery", "Reverse whole lists, sub-lists, alternating k-groups or rotate by k."),
        ApplicationCard("check", 0xFF10B981, "Palindrome Check", "Reverse the second half in place, compare, then restore the list."),
        ApplicationCard("undo", 0xFF8B5CF6, "Undo Chains", "Re-pointing a chain of records without allocating a second chain."),
    ),
    takeaways = listOf(
        "Save next before overwriting cur.next — that single line is the whole bug surface.",
        "prev ends as the new head; cur ends as the node after the segment.",
        "A dummy head removes the special case where the reversal starts at node 1.",
        "Sub-list variants live or die on the two boundary nodes, not on the loop.",
    ),
    crossLinks = listOf(
        CrossLink("singly_linked_list", "Singly Linked List (Data Structures)"),
        CrossLink("fast_slow_pointers", "Fast & Slow Pointers"),
        CrossLink("faang_set", "Practice: FAANG Set"),
    ),
)
