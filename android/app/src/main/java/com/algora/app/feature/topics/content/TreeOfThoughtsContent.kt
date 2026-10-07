package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val treeOfThoughtsContent = TopicContent(
    topicId = "tree_of_thoughts",
    figure = Figure(
        caption = "The page's lab: Game of 24 with 4, 9, 10 and 13, searched with a cheap " +
            "one-operation lookahead as the evaluator. There are 36 possible first moves, and the " +
            "evaluator ranks the best solvable one eighth — none of its top five can reach 24 at " +
            "all. A chain of thought is beam width 1: it commits to the evaluator's favourite " +
            "(4 + 9 = 13) and dead-ends, though (10 − 4) × (13 − 9) = 24 was one sibling away. " +
            "Width 5 still throws the answer away. Width 8 is the first that keeps it, and nothing " +
            "about the evaluator changed. The alternative is a better evaluator: one more operation " +
            "of lookahead lets width 1 solve it, but costs 702 evaluator calls against 180 for the " +
            "cheap one at width 8. Width substitutes for evaluator quality, and you pay for one or " +
            "the other.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("cheap", "✗", "—"),
                listOf("cheap", "✗", "—"),
                listOf("cheap", "✓", "180"),
                listOf("deeper", "✓", "702"),
            ),
            rowHeaders = listOf("width 1", "width 5", "width 8", "width 1"),
            colHeaders = listOf("evaluator", "solves 24", "evaluator calls"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(3, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A chain of thought is a search with beam width 1. It picks the next step that looks best and commits, and it has no way back — nothing in the trace records that alternatives existed. Tree of Thoughts keeps them: generate several candidate next steps, score each partial state, keep the best few, and expand from there. That is beam search, and the only genuinely new part is that the generator and the evaluator are both the language model.",
        "The scoring step is where it lives or dies. The paper prompts the model to judge a partial state as sure, likely or impossible; the lab here stands that in with a cheap one-operation lookahead, and then does the thing that matters — measures how good it is. On Game of 24 with the numbers 4, 9, 10, 13, that evaluator ranks the best genuinely-solvable first move eighth out of 36, and none of its top five can reach 24 at all. Beam width 1 fails. Width 5 fails. Width 8 is the first that succeeds, and nothing about the evaluator changed — the search simply stopped trusting it enough to throw the answer away.",
        "That gives the technique's real shape: **width substitutes for evaluator quality**, and which one to buy is a measurement rather than a principle. Give the evaluator one more operation of lookahead and width 1 solves the puzzle — but that deeper evaluator costs 702 evaluator calls against 180 for the cheap one at width 8, so the *better* evaluator is nearly 4× more expensive here. With an LLM doing the scoring, every one of those calls is a request. Search buys robustness against a weak evaluator; a stronger evaluator buys narrower search; you pay either way.",
    ),
    steps = listOf(
        StepCard(1, "Define the State", "A partial solution the model can extend and score — not a full answer.", 0xFF10B981),
        StepCard(2, "Generate Candidates", "Several next steps from the current state, not one.", 0xFF14B8A6),
        StepCard(3, "Evaluate", "Score each partial state. This is the component that decides everything.", 0xFF3B82F6),
        StepCard(4, "Prune to Width b", "Keep the top b; the rest are discarded, correct or not.", 0xFF6366F1),
        StepCard(5, "Expand and Repeat", "Depth is fixed by the problem; width is your dial.", 0xFF8B5CF6),
        StepCard(6, "Price It", "Nodes expanded × evaluator calls per node — both are model requests.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Beam search", "Fᵢ₊₁ = top_b( ⋃ succ(s) : s ∈ Fᵢ )", "Frontier of width b, expanded and re-pruned each level."),
        FormulaEntry("Chain of thought", "b = 1", "The special case with no alternatives kept."),
        FormulaEntry("Exhaustive", "b = ∞", "4,565 states on this puzzle, 9 of them ending at 24."),
        FormulaEntry("Evaluator quality", "rank of first solvable = 8 of 36", "The cheap evaluator's actual ranking, measured against reachability."),
        FormulaEntry("Width needed", "b = 8 at depth-1 evaluation", "The smallest beam that keeps a solvable state."),
        FormulaEntry("The trade", "180 calls at b = 8 vs 702 at b = 1, depth 2", "A better evaluator is not automatically the cheaper lever."),
    ),
    notationKey = listOf(
        NotationEntry("thought / state", "a partial solution — here, the multiset of numbers still in play"),
        NotationEntry("b (width)", "how many states survive each pruning step"),
        NotationEntry("evaluator", "the scorer over partial states; the paper's value prompt"),
        NotationEntry("depth", "how many operations of lookahead the evaluator does before guessing"),
        NotationEntry("expanded", "states whose successors were generated — the search's real cost"),
        NotationEntry("Game of 24", "reach 24 from four numbers with + − × ÷; the paper's lead benchmark"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Beam search over model-generated thoughts",
            accentColor = 0xFF3B82F6,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                def propose(state: str, k: int = 5) -> list[str]:
                    "Generator: k candidate next steps from the current partial state."
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=512,
                        messages=[{"role": "user", "content":
                            f"Numbers left: {state}. Give {k} different next moves, one per "
                            f"line, each as 'a op b = result'. No commentary."}],
                    )
                    text = next(b.text for b in response.content if b.type == "text")
                    return [line.strip() for line in text.splitlines() if line.strip()]

                def evaluate(state: str) -> float:
                    "Evaluator: score a partial state. This is the component that decides."
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=8,
                        messages=[{"role": "user", "content":
                            f"Can 24 still be reached from {state}? "
                            f"Answer exactly one word: sure, likely, or impossible."}],
                    )
                    verdict = next(b.text for b in response.content if b.type == "text")
                    return {"sure": 2.0, "likely": 1.0}.get(verdict.strip().lower(), 0.0)

                def apply(state: str, move: str) -> str:
                    "Turn 'a op b = r' into the NEW state: remove a and b, add r."
                    a, _, b, _, r = move.split()
                    nums = state.split()
                    nums.remove(a); nums.remove(b); nums.append(r)
                    return " ".join(nums)

                frontier = ["4 9 10 13"]
                for _ in range(3):                      # depth is fixed by the puzzle
                    candidates = [apply(state, move) for state in frontier
                                  for move in propose(state)]
                    frontier = sorted(candidates, key=evaluate, reverse=True)[:8]
                # Both propose() and evaluate() are requests. Cost is
                # (states expanded) x (1 generator call + k evaluator calls) -- budget it.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why width 8, measured rather than chosen",
            accentColor = 0xFFEC4899,
            code = """
                # The evaluator is cheap and optimistic: "could one more operation on some
                # pair land on 24?", ignoring the numbers left over. Its quality is not
                # assumed -- it is scored against exact reachability.

                ranked = ranked_frontier(depth=1)        # 36 distinct first moves, best first
                first_solvable = next(i for i, (_, _, ok) in enumerate(ranked, 1) if ok)
                top5_solvable = sum(ok for _, _, ok in ranked[:5])

                print(first_solvable, top5_solvable)     # 8 0
                # The evaluator's favourite five first moves contain zero that can reach 24.
                # That is not a bug in the implementation -- it is what a one-step heuristic
                # is worth on a four-number state.

                for b in (1, 5, 8):
                    r = beam(width=b, depth=1)
                    print(b, r.solved, r.expanded, r.evaluator_calls)
                # 1 False  3 54
                # 5 False 11 126
                # 8 True  17 180      <- first width that keeps a solvable state

                print(beam(width=1, depth=2).evaluator_calls)   # 702
                # A two-step evaluator solves it at width 1 -- and costs 3.9x more than the
                # weak evaluator at width 8. Width and evaluator quality are substitutes.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("game", 0xFF10B981, "Puzzles and Planning", "Game of 24, crosswords, creative writing plans — the paper's own benchmarks."),
        ApplicationCard("network", 0xFF3B82F6, "Multi-Step Tool Plans", "Keep two candidate plans alive rather than committing to the first."),
        ApplicationCard("finance", 0xFF8B5CF6, "Budgeted Search", "Every node is a request; width and depth are both spend."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "One-step problems get nothing from a frontier and pay for it anyway."),
    ),
    takeaways = listOf(
        "Chain of thought is beam width 1 — Tree of Thoughts is the same search with alternatives kept.",
        "The evaluator decides everything: here it ranks the best solvable first move 8th of 36, and 0 of its top 5 can reach 24.",
        "Width substitutes for evaluator quality — width 8 succeeds with the same evaluator that fails at width 5.",
        "Measured, the better evaluator was the more expensive lever: 702 calls at width 1 against 180 at width 8.",
        "Exhaustive search over this puzzle visits 4,565 states and finds 9 solutions; the beam found one after 17 expansions.",
    ),
    crossLinks = listOf(
        CrossLink("chain_of_thought", "Chain of Thought"),
        CrossLink("mcts", "Monte Carlo Tree Search"),
        CrossLink("ai_agents", "AI Agents & Tool Use"),
        CrossLink("bfs", "Breadth-First Search"),
    ),
)
