package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Nesting is the signal: a stack replaces the recursion these
// problems would otherwise need.
internal val expressionStackPatternContent = TopicContent(
    topicId = "expression_stack_pattern",
    whatIsIt = listOf(
        "Anything nested — brackets, repeated blocks, directory paths, arithmetic with parentheses — is parsed with a stack. Opening a context pushes the state you must restore; closing it pops and folds the finished piece back into its parent.",
        "This is iterative recursion. The stack holds exactly what a recursive parser would keep in its call frames, without the depth limit, and one pass over the input is enough.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Nested brackets, encoded strings like 3[ab], calculators with parentheses, path simplification, or matching-pair validation.", 0xFFF59E0B),
        StepCard(2, "Decide What a Frame Holds", "Push everything the enclosing context needs restored: the partial result so far, a repeat count, a pending operator.", 0xFF3B82F6),
        StepCard(3, "Fold on Close", "On the closing token, pop the frame and merge the finished piece into it — concatenate, apply the operator, multiply out the repeat.", 0xFFEF4444),
        StepCard(4, "Check the End State", "A leftover open frame means unbalanced input. For calculators, apply the final pending operator after the loop.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n) tokens", "One pass; each token is pushed and popped at most once."),
        FormulaEntry("Space", "O(depth)", "The stack is only as deep as the nesting."),
        FormulaEntry("Balance check", "counter ≥ 0 throughout, 0 at the end", "One bracket type needs a counter, not a stack."),
    ),
    notationKey = listOf(
        NotationEntry("frame", "the saved state of the enclosing context"),
        NotationEntry("cur", "result being built inside the current context"),
        NotationEntry("depth", "current nesting level"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Decode a nested encoded string (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def decode(s):                             # "3[a2[c]]" -> "accaccacc"
                    stack, cur, count = [], "", 0
                    for ch in s:
                        if ch.isdigit():
                            count = count * 10 + int(ch)   # multi-digit repeats
                        elif ch == '[':
                            stack.append((cur, count))     # save the enclosing context
                            cur, count = "", 0
                        elif ch == ']':
                            prev, repeat = stack.pop()
                            cur = prev + cur * repeat      # fold back into the parent
                        else:
                            cur += ch
                    return cur

                def is_balanced(s):
                    pairs, stack = {')': '(', ']': '[', '}': '{'}, []
                    for ch in s:
                        if ch in "([{":
                            stack.append(ch)
                        elif ch in pairs:
                            if not stack or stack.pop() != pairs[ch]:
                                return False
                    return not stack                       # nothing left open
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.StackVisualizer,
    applications = listOf(
        ApplicationCard("code", 0xFF3B82F6, "Expression Evaluation", "Calculators, shunting-yard conversion and postfix evaluation."),
        ApplicationCard("browser", 0xFF10B981, "Markup & Paths", "HTML/JSON tag matching and simplifying Unix paths with .. segments."),
        ApplicationCard("undo", 0xFF8B5CF6, "Undo & Backtracking", "Editor history and any \"restore the previous context\" behaviour."),
    ),
    takeaways = listOf(
        "Nesting means a stack — and a stack means you never need the recursion depth.",
        "Name the frame contents first; most bugs are a piece of context that was never saved.",
        "Fold the child into the parent on close, in that order, or the result comes out reversed.",
        "Validate the end state: an unbalanced input leaves a frame behind.",
    ),
    crossLinks = listOf(
        CrossLink("stack", "Stack (Data Structures)"),
        CrossLink("monotonic_stack_pattern", "Monotonic Stack"),
        CrossLink("backtracking_pattern", "Backtracking Pattern"),
    ),
)
