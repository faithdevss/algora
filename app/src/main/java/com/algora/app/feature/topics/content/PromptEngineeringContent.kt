package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val promptEngineeringContent = TopicContent(
    topicId = "prompt_engineering",
    whatIsIt = listOf(
        "A prompt is an induction problem. You describe a task, and the model has to pick one rule out of every rule consistent with what you said — the instruction narrows that space, each demonstration narrows it further, and the answer you get is whichever rule survived. Zero-shot means asking the model to guess your rule from the description alone. Few-shot means handing it evidence. Neither is magic, and both are doing the same thing: eliminating hypotheses.",
        "Framing it that way makes the practical advice fall out instead of having to be memorised. Demonstrations are worth exactly the hypotheses they kill, which is why a carefully chosen pair can beat a careless five, and why adding examples that all agree with each other buys nothing. An instruction is not data at all — it is a prior, putting zero mass on rules you have ruled out before any example is seen. The famous prompt-engineering results (few-shot beats zero-shot, example selection matters, order effects exist) are all statements about the shape of that hypothesis space.",
        "The limit is the part that stays true no matter how good you get at this. Elimination can only pick among rules the model already entertains. If your intended rule is outside that space — because it depends on a convention the model has never seen, or on a fact it does not have — no number of examples will produce it, and the model will confidently return the best surviving alternative instead. That failure looks exactly like a correct answer, which is why the rest of this category is about retrieval, verification and evidence rather than about wording.",
    ),
    steps = listOf(
        StepCard(1, "State the Task", "The description is a prior: it decides which rules are in play at all.", 0xFF10B981),
        StepCard(2, "Enumerate What Fits", "Several rules match a plain description. That ambiguity is the problem.", 0xFF14B8A6),
        StepCard(3, "Add a Discriminating Example", "The best demonstration is the one the wrong rules disagree on.", 0xFF3B82F6),
        StepCard(4, "Check What Survives", "One rule left means determined; two means the answer is a coin flip.", 0xFF6366F1),
        StepCard(5, "Prefer Instruction to Repetition", "A sentence can eliminate what several examples would have.", 0xFF8B5CF6),
        StepCard(6, "Watch for the Rule You Didn't Imagine", "If the intended rule isn't in the space, elimination can't reach it.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Version space", "H_k = { h ∈ H : h(xᵢ) = yᵢ for all i ≤ k }", "The rules still consistent after k demonstrations."),
        FormulaEntry("Prediction", "P(y | x) = |{h ∈ H_k : h(x) = y}| / |H_k|", "Uniform posterior over survivors — the model's answer is their vote."),
        FormulaEntry("Determined", "|H_k| = 1", "The point at which more examples stop changing anything."),
        FormulaEntry("Instruction", "H₀ ← H₀ \\ R", "A prior, not data: it removes rules before any example arrives."),
        FormulaEntry("Zero-shot", "k = 0", "Answer drawn from the whole space the description allows."),
        FormulaEntry("Few-shot", "k small, chosen", "Which examples, not how many — the lab measures both."),
    ),
    notationKey = listOf(
        NotationEntry("H", "the hypothesis space — every rule the task description leaves open"),
        NotationEntry("version space", "the subset of H still consistent with the demonstrations given"),
        NotationEntry("demonstration", "an input/output pair placed in the prompt; evidence, not training"),
        NotationEntry("in-context learning", "performing a task from prompt examples with no weight update"),
        NotationEntry("zero-shot / few-shot", "no demonstrations versus a handful of them"),
        NotationEntry("discriminating example", "one the surviving rules disagree on — the only kind that narrows"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Zero-shot and few-shot, same task",
            accentColor = 0xFF10B981,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                def ask(prompt: str) -> str:
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=16,
                        messages=[{"role": "user", "content": prompt}],
                    )
                    return next(b.text for b in response.content if b.type == "text")

                # Zero-shot: the description is the only constraint, and it is ambiguous.
                print(ask("Give one letter from the word: kayak"))

                # Few-shot: two demonstrations chosen to disagree under the wrong rules.
                print(ask(
                    "level -> v\n"
                    "sonar -> n\n"
                    "kayak ->"
                ))

                # Instruction + one example: the sentence rules out three hypotheses that
                # would otherwise need three demonstrations to eliminate.
                print(ask(
                    "Return the letter at the middle position of the word.\n"
                    "level -> v\n"
                    "kayak ->"
                ))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Choosing demonstrations by what they eliminate",
            accentColor = 0xFF6366F1,
            code = """
                # The selection rule, made explicit: score a candidate demonstration by how
                # many surviving hypotheses it kills, not by how "representative" it looks.

                RULES = {
                    "first":  lambda w: w[0],
                    "last":   lambda w: w[-1],
                    "middle": lambda w: w[len(w) // 2],
                    "freq":   lambda w: max(sorted(set(w)), key=w.count),
                    "alpha":  lambda w: min(w),
                }
                TRUE = RULES["middle"]

                def survivors(demos):
                    return {n for n, r in RULES.items() if all(r(w) == TRUE(w) for w in demos)}

                pool = ["banana", "adage", "otter", "level", "melon", "sonar"]
                for word in pool:
                    print(f"{word:7} leaves {len(survivors([word]))} of {len(RULES)} rules")
                # banana leaves 4 -- agrees with almost everything, so it teaches almost nothing
                # level  leaves 1 -- identifies the rule on its own

                # The useful question about a prompt is not "is this a good example?" but
                # "which hypotheses does this example rule out?" -- and those differ.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF10B981, "Product Prompts", "Most LLM features ship a prompt, not a fine-tune — this is the whole interface."),
        ApplicationCard("search", 0xFF3B82F6, "Structured Extraction", "Ambiguous field definitions are ambiguous rules; examples pin them down."),
        ApplicationCard("check", 0xFF8B5CF6, "Evaluation Sets", "A demo set that never discriminates hides the failure until production."),
        ApplicationCard("help", 0xFFEC4899, "When Prompting Can't Win", "A rule outside the space, or a fact the model lacks, needs retrieval instead."),
    ),
    takeaways = listOf(
        "A prompt narrows a space of rules; the answer is whichever rule survived your evidence.",
        "Demonstrations are worth the hypotheses they eliminate — examples that all agree buy nothing.",
        "Which examples beats how many: in the lab, 12 of 15 two-shot prompts identify the rule and 3 do not.",
        "An instruction is a prior, not data — one sentence here does the work of three demonstrations.",
        "If the intended rule isn't in the space at all, elimination cannot reach it, and the wrong answer looks right.",
    ),
    crossLinks = listOf(
        CrossLink("chain_of_thought", "Chain of Thought"),
        CrossLink("llms", "LLMs"),
        CrossLink("rag", "Retrieval-Augmented Generation"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
    ),
)
