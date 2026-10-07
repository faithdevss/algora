package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val chainOfThoughtContent = TopicContent(
    topicId = "chain_of_thought",
    whatIsIt = listOf(
        "Chain of thought asks the model to write out intermediate steps before committing to an answer. The empirical result is real and large on multi-step problems, and the mechanism is not mysterious: a transformer does a fixed amount of computation per token, so a problem needing more computation than one forward pass allows can only be solved by spending more tokens. Writing the steps is how the model buys that computation, and how each step's result becomes an input the later steps can attend to.",
        "It is a trade, not a free win. If a decomposed step is right with probability p and every step has to be right, the chain succeeds with probability pⁿ — which falls off a cliff. At the lab's numbers, a 92%-per-step chain beats a 55% direct answer up to seven steps and is worse than the guess it replaced by eight. \"Let's think step by step\" is good advice for problems short enough that pⁿ stays above the one-shot rate, and quietly bad advice past that. Nothing in the chain checks the earlier steps.",
        "Self-consistency is the standard repair: sample several independent chains and keep the answer most of them reach. The usual justification is that wrong chains go wrong in different directions while right ones agree — which is a claim about the *errors*, not about the model, and it is checkable. Computed exactly, voting over more samples raises accuracy when the wrong answers are scattered and *lowers* it when they all land on the same wrong value. That case is not a corner: it is what a systematic false belief looks like, and it is the same failure the hallucination topic is built around.",
    ),
    steps = listOf(
        StepCard(1, "Decompose", "Turn one hard step into several easier ones the model can attend to.", 0xFF10B981),
        StepCard(2, "Price the Chain", "pⁿ, not p — every added step is another chance to be wrong.", 0xFF3B82F6),
        StepCard(3, "Find the Break-Even", "Decomposition wins only while pⁿ beats the direct answer.", 0xFF6366F1),
        StepCard(4, "Sample Several Chains", "Independent runs, then keep the answer most of them reach.", 0xFF8B5CF6),
        StepCard(5, "Check the Error Shape", "Voting helps if errors scatter; it amplifies a systematic one.", 0xFFF59E0B),
        StepCard(6, "Know What's Left", "Neither mechanism adds a fact or catches a confident wrong step.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Chain accuracy", "P(correct) = pⁿ", "n independent steps, each correct with probability p."),
        FormulaEntry("Break-even", "largest n with pⁿ > q", "q is the direct-answer rate; at p = 0.92, q = 0.55 that is n = 7."),
        FormulaEntry("Plurality vote", "P(c₀ > max cᵢ)", "Enumerated over the multinomial, not sampled. Ties count as failures."),
        FormulaEntry("Wrong-answer spread", "each wrong value w.p. (1−p)/d", "d distinct wrong answers — the parameter that decides voting's sign."),
        FormulaEntry("Scattered errors", "0.40 → 0.590 at m = 9, d = 4", "Voting helps."),
        FormulaEntry("Systematic error", "0.40 → 0.267 at m = 9, d = 1", "Same vote, opposite direction."),
    ),
    notationKey = listOf(
        NotationEntry("p", "per-step accuracy once the problem is decomposed"),
        NotationEntry("q", "accuracy of answering in one shot, without decomposition"),
        NotationEntry("n", "number of steps in the chain"),
        NotationEntry("m", "number of independently sampled chains in self-consistency"),
        NotationEntry("d", "how many distinct wrong answers the errors spread over"),
        NotationEntry("plurality", "the most common answer across samples; the vote self-consistency takes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Self-consistency: sample, then vote",
            accentColor = 0xFF8B5CF6,
            code = """
                from collections import Counter
                import anthropic

                client = anthropic.Anthropic()

                PROMPT = (
                    "A shop had 50 boxes of 24 pens, sold 17, then restocked 3. "
                    "How many pens are in stock? Work step by step, then give the final "
                    "number on its own line prefixed with 'ANSWER:'."
                )

                def one_chain() -> str:
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=1024,
                        messages=[{"role": "user", "content": PROMPT}],
                    )
                    text = next(b.text for b in response.content if b.type == "text")
                    return text.rsplit("ANSWER:", 1)[-1].strip()

                answers = [one_chain() for _ in range(5)]
                counts = Counter(answers)
                answer, votes = counts.most_common(1)[0]

                # The vote is only as good as the spread of the wrong answers. Log it:
                # a 5-0 sweep on a question the model is systematically wrong about looks
                # identical to a 5-0 sweep on one it actually knows.
                print(answer, f"{votes}/{len(answers)} agreement", dict(counts))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The exact vote — including where it makes things worse",
            accentColor = 0xFFEC4899,
            code = """
                from itertools import product
                from math import comb

                def plurality_accuracy(p: float, m: int, d: int) -> float:
                    "Exact P(plurality is correct). Ties count as failures."
                    wrong = (1 - p) / d
                    total = 0.0
                    for counts in product(range(m + 1), repeat=d + 1):
                        if sum(counts) != m:
                            continue
                        if counts[0] <= max(counts[1:]):
                            continue
                        ways = 1
                        left = m
                        for c in counts:
                            ways *= comb(left, c)
                            left -= c
                        total += ways * p ** counts[0] * wrong ** (m - counts[0])
                    return total

                for d in (1, 2, 4):
                    row = [f"m={m}: {plurality_accuracy(0.40, m, d):.3f}" for m in (1, 3, 5, 7, 9)]
                    print(f"d={d}", " ".join(row))
                # d=1 m=1: 0.400 m=3: 0.352 m=5: 0.317 m=7: 0.290 m=9: 0.267   <- voting HURTS
                # d=2 m=1: 0.400 m=3: 0.352 m=5: 0.317 m=7: 0.399 m=9: 0.423
                # d=4 m=1: 0.400 m=3: 0.352 m=5: 0.447 m=7: 0.521 m=9: 0.590   <- voting helps

                # Same p, same vote, opposite direction. Majority voting amplifies whatever
                # the model does consistently -- and that includes being consistently wrong.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Multi-Step Reasoning", "Arithmetic, planning and derivations, where one forward pass isn't enough compute."),
        ApplicationCard("check", 0xFF3B82F6, "Answer Confidence", "Agreement across samples is cheap to compute — and easy to misread."),
        ApplicationCard("finance", 0xFF8B5CF6, "Cost Control", "m samples cost m times as much; the curve flattens well before it stops."),
        ApplicationCard("help", 0xFFEC4899, "What It Can't Fix", "Missing facts and confident wrong steps survive both mechanisms."),
    ),
    takeaways = listOf(
        "Writing steps buys computation: a fixed-depth model solves longer problems by spending tokens.",
        "The chain succeeds with probability pⁿ — at p = 0.92 it beats a 0.55 direct answer only up to 7 steps.",
        "Self-consistency's justification is a claim about the errors, not about the model, and it is checkable.",
        "Computed exactly: with 4 distinct wrong answers voting takes 0.40 → 0.59; with one systematic wrong answer it takes 0.40 → 0.27.",
        "Agreement measures consistency, never correctness — and it is highest exactly where a false belief is firm.",
    ),
    crossLinks = listOf(
        CrossLink("tree_of_thoughts", "Tree of Thoughts"),
        CrossLink("prompt_engineering", "Prompt Engineering"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
        CrossLink("react", "ReAct"),
    ),
)
