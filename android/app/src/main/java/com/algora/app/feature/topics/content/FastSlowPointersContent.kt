package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fastSlowPointersContent = TopicContent(
    topicId = "fast_slow_pointers",
    figure = Figure(
        caption = "Two pointers over the same list at different speeds. If a cycle exists the fast one " +
            "laps the slow one and they meet inside it; the distance from the head to the entry equals " +
            "the distance from the meeting point to the entry, which is why a second walk finds it.",
        shape = FigureShape.Strip(
            cells = listOf("A", "B", "C", "D", "E", "F"),
            bands = listOf(
                FigureBand(0, 1, "tail", FigureTone.Muted),
                FigureBand(2, 5, "cycle"),
            ),
            pointers = listOf(FigurePointer(3, "slow"), FigurePointer(5, "fast")),
        ),
    ),
    whatIsIt = listOf(
        "The fast & slow pointer pattern (Floyd's tortoise and hare) runs two pointers at different speeds through a sequence or linked list.",
        "Because the fast pointer gains one step per move, it detects cycles and finds the midpoint without measuring length first; a fixed-gap pair of same-speed pointers does the same for the k-th-from-end node.",
    ),
    steps = listOf(
        StepCard(1, "Two Speeds", "Advance slow by one node and fast by two on every iteration.", 0xFF8B5CF6),
        StepCard(2, "Cycle Detection", "If a cycle exists, fast eventually laps slow and they land on the same node.", 0xFFEF4444),
        StepCard(3, "Find the Midpoint", "When fast reaches the end, slow sits exactly at the middle.", 0xFF3B82F6),
        StepCard(4, "Locate the Start", "Reset one pointer to the head; advancing both by one now meets at the cycle's entry.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Fast pointer traverses at most twice the list length."),
        FormulaEntry("Space", "O(1)", "No auxiliary set of visited nodes."),
        FormulaEntry("Hash-set alternative", "O(n) space", "Storing seen nodes works but costs linear memory."),
    ),
    notationKey = listOf(
        NotationEntry("slow", "the one-step pointer (tortoise)"),
        NotationEntry("fast", "the two-step pointer (hare)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Cycle detection (Python)",
            accentColor = 0xFF8B5CF6,
            code = """
                def has_cycle(head):
                    slow = fast = head
                    while fast and fast.next:
                        slow = slow.next
                        fast = fast.next.next
                        if slow is fast:
                            return True     # hare lapped the tortoise
                    return False
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("link", 0xFF8B5CF6, "Linked-List Cycles", "Detect loops and find where the cycle begins in O(1) space."),
        ApplicationCard("search", 0xFF3B82F6, "Middle of a List", "Split a list for merge sort or find the median node in one pass."),
        ApplicationCard("help", 0xFFF59E0B, "Happy Number", "Cycle detection on a number-transformation sequence."),
    ),
    takeaways = listOf(
        "Two speeds reveal cycles without any extra memory.",
        "When the hare finishes, the tortoise is at the midpoint.",
        "A pointer reset finds the exact cycle-entry node (Floyd's second phase).",
        "The O(1)-space answer to problems a hash set would solve in O(n) space.",
    ),
    crossLinks = listOf(
        CrossLink("two_pointer_pattern", "Two Pointer Pattern"),
        CrossLink("singly_linked_list", "Singly Linked List"),
    ),
)
