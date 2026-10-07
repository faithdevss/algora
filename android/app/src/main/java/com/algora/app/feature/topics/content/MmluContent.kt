package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mmluContent = TopicContent(
    topicId = "mmlu",
    whatIsIt = listOf(
        "MMLU is 14,042 four-way multiple-choice questions across 57 subjects, from abstract algebra to professional law, and it is quoted everywhere as a single accuracy. The first thing that number needs is its **floor**: with four options, a model that has learned nothing scores 0.25. A reported 0.310 is therefore not \"31% of the way there\" — it is 0.080 of the way from guessing to perfect.",
        "**The second thing it needs is its error bar, and this is where most leaderboard claims fail.** 14,042 questions at 0.702 accuracy carry a binomial standard error of 0.0039 — about four tenths of a point. So the gap between a model reporting 0.702 and one reporting 0.694 is **1.46 standard errors: not a difference, a coin flip.** A model at 0.658 is 7.91 SE back, which is real. The benchmark separates tiers; it does not rank neighbours.",
        "**Subject-level claims are far worse.** The error scales with 1/√n and the subjects are small: a 100-question subject has standard error 0.0458, twelve times the full benchmark's. Run that across a real subject table and **10 of 45 pairs are within two standard errors of each other** — their ordering reverses on resampling. A fifth of a per-subject bar chart's orderings are noise.",
        "**Then two things no error bar catches.** Subject sizes range from 100 to 1,534 questions here, so weighting every *question* equally and every *subject* equally give different numbers — micro 0.6084, macro 0.6140. That 0.56-point spread is 70% of the gap the leaderboard uses to rank its top two models, and almost nobody states which they used. And contamination: if a fifth of the test set appeared in pretraining, a reported 0.700 implies a true ability of 0.625. Contamination moves the score by more than every honest gap on the board, and it is invisible from the score alone.",
    ),
    steps = listOf(
        StepCard(1, "Subtract The Chance Floor", "0.25 with four options. Rescale before interpreting any score.", 0xFF0EA5E9),
        StepCard(2, "Attach The Standard Error", "√(p(1−p)/n) — 0.0039 at full size, 0.0458 per subject.", 0xFF3B82F6),
        StepCard(3, "Count The Separation", "Divide the gap by the combined SE. Under 2, it is noise.", 0xFF6366F1),
        StepCard(4, "State The Averaging", "Micro and macro differ by most of the gap being reported.", 0xFF8B5CF6),
        StepCard(5, "Assume Contamination", "Test-set leakage is the largest single term and the least measurable.", 0xFFEC4899),
        StepCard(6, "Quote It As A Tier", "Capability bands survive all of the above. Ranks do not.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("Chance floor", "1 / 4 = 0.25", "0.310 reported is 0.080 chance-corrected."),
        FormulaEntry("Chance correction", "(acc − 0.25) / 0.75", "What a multiple-choice score actually means."),
        FormulaEntry("Standard error", "√(p(1−p)/n)", "0.0039 at 14,042 · 0.0458 at 100."),
        FormulaEntry("Separation", "|a − b| / √(SEa² + SEb²)", "0.702 vs 0.694 = 1.46 SE. Not a difference."),
        FormulaEntry("Micro vs macro", "0.6084 vs 0.6140", "Same predictions, 70% of the top-two gap."),
        FormulaEntry("Contamination", "reported = ability·(1−c) + c", "Reported 0.700 at c = 0.20 ⇒ ability 0.625."),
    ),
    notationKey = listOf(
        NotationEntry("standard error", "the sampling noise in an accuracy estimated from n questions"),
        NotationEntry("chance-corrected", "rescaled so 0 is guessing and 1 is perfect"),
        NotationEntry("micro average", "every question weighted equally — large subjects dominate"),
        NotationEntry("macro average", "every subject weighted equally — small subjects dominate"),
        NotationEntry("contamination", "benchmark questions present in the training data, answered from memory"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The error bar the leaderboard omits",
            accentColor = 0xFF0EA5E9,
            code = """
                import math

                def se(p, n):
                    return math.sqrt(p * (1 - p) / n)

                def separation(a, b, n):
                    return abs(a - b) / math.hypot(se(a, n), se(b, n))

                print(se(0.702, 14_042))                  # 0.0039
                print(se(0.702, 100))                     # 0.0458  (one subject)

                print(separation(0.702, 0.694, 14_042))   # 1.46 SE -> noise
                print(separation(0.702, 0.658, 14_042))   # 7.91 SE -> real
                print(separation(0.702, 0.694, 100))      # 0.12 SE -> nothing

                # 10 of 45 subject pairs in the lab's table sit inside 2 SE.
                # Their ordering is resampling noise, drawn as a bar chart.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Two effects larger than the gaps being reported",
            accentColor = 0xFFEC4899,
            code = """
                # 1. Averaging. Subject sizes run 100 - 1,534 questions.
                micro = sum(a * n for a, n in subjects) / sum(n for _, n in subjects)
                macro = sum(a for a, _ in subjects) / len(subjects)
                print(micro, macro)            # 0.6084  0.6140

                # The 0.56-point spread is 70% of the 0.8-point gap between the
                # top two models. Nobody states which average they used.

                # 2. Contamination.
                def reported(ability, c):  return ability * (1 - c) + c
                def true_ability(rep, c):  return (rep - c) / (1 - c)

                print(reported(0.55, 0.10))    # 0.595
                print(true_ability(0.70, 0.20))# 0.625

                # None of this makes MMLU useless -- it gives it a resolution.
                # Tiers survive. Neighbouring ranks and subject claims do not.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("crown", 0xFF0EA5E9, "Model Selection", "Valid at tier granularity — is this model in the frontier band or a tier below?"),
        ApplicationCard("chart", 0xFF3B82F6, "Release Reporting", "Quote the interval, the averaging method and the date, or the number is decoration."),
        ApplicationCard("flask", 0xFF10B981, "Benchmark Design", "Why MMLU-Pro moved to ten options: the chance floor drops from 0.25 to 0.10."),
        ApplicationCard("help", 0xFFF59E0B, "What It Cannot Measure", "Reasoning depth, tool use, long context, safety — a multiple-choice quiz measures recall under four options."),
    ),
    takeaways = listOf(
        "MMLU's chance floor is 0.25, so scores must be chance-corrected before they mean anything — 0.310 reported is 0.080 of the way from guessing to perfect.",
        "At 14,042 questions the standard error is 0.0039, making 0.702 against 0.694 a 1.46-SE gap: the leaderboard's top two are reporting noise.",
        "Subject-level claims are 12× noisier still — 10 of 45 subject pairs in the lab's table are not distinguishable at 2 SE.",
        "Micro and macro averaging differ by 0.56 points on identical predictions, which is 70% of the gap used to rank the top two models.",
        "Contamination is the largest and least visible term: a reported 0.700 implies a true 0.625 if a fifth of the benchmark leaked into pretraining.",
    ),
    crossLinks = listOf(
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("llms", "LLMs"),
        CrossLink("perplexity", "Perplexity"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
    ),
)
