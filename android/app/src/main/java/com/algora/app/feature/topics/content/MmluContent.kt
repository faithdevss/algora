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

internal val mmluContent = TopicContent(
    topicId = "mmlu",
    figure = Figure(
        caption = "The page's lab: four models' MMLU accuracies with the two things the single " +
            "number leaves out. The chance-corrected column subtracts the 0.25 a model gets by " +
            "guessing among four options and rescales: model D's 0.310 is only 8% of the way from " +
            "guessing to perfect. The error-bar columns are ±2 binomial standard errors. On all " +
            "14,042 questions they are ±0.008, so A's 1.4-point lead over B is 2.57 standard " +
            "errors of the difference — real. Score the same models on 1,000 questions and the bars " +
            "widen to ±0.029; the same gap is 0.69 standard errors, a coin flip. On a 100-question " +
            "subject they are ±0.09, wider than everything separating A, B and C. The benchmark " +
            "separates tiers; on subsets it cannot rank neighbours.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.712", "0.616", "± 0.008", "± 0.029"),
                listOf("0.698", "0.597", "± 0.008", "± 0.029"),
                listOf("0.655", "0.540", "± 0.008", "± 0.030"),
                listOf("0.310", "0.080", "± 0.008", "± 0.029"),
            ),
            rowHeaders = listOf("model A", "model B", "model C", "model D"),
            colHeaders = listOf("accuracy", "over chance", "n = 14,042", "n = 1,000"),
            marks = listOf(
                FigureCell(3, 1, FigureTone.Warn),
                FigureCell(0, 2, FigureTone.Accent),
                FigureCell(0, 3, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "MMLU is 14,042 four-way multiple-choice questions across 57 subjects, from abstract algebra to professional law, and it is quoted everywhere as a single accuracy. The first thing that number needs is its **floor**: with four options, a model that has learned nothing scores 0.25. A reported 0.310 is therefore not \"31% of the way there\" — it is 0.080 of the way from guessing to perfect.",
        "**The second thing it needs is its error bar, and this is where most leaderboard claims fail.** At p ≈ 0.7, 14,042 questions carry a binomial standard error of 0.0039 — two standard errors is ±0.8 points. The lab compares a model at 0.712 with one at 0.698: the 1.4-point gap is 2.57 standard errors of the difference, so on the full benchmark it is real. Score the same two models on 1,000 questions and the error bar is ±2.9 points and the gap is 0.69 standard errors — a coin flip. On 100 questions it is ±9. **The benchmark separates tiers; on a subset it does not even rank neighbours.**",
        "**Subject-level claims are far worse.** The error scales with 1/√n and the subjects are small: a 100-question subject has standard error 0.0458, twelve times the full benchmark's. Most per-subject bar charts are comparing numbers whose error bars overlap, and their ordering reverses on resampling.",
        "**Then two things no error bar catches.** Subject sizes range from 100 to 1,534 questions here, so weighting every *question* equally and every *subject* equally give different numbers from identical predictions, and almost nobody states which they used. And contamination: if a fifth of the test set appeared in pretraining, a reported 0.700 implies a true ability of 0.625. Contamination moves the score by more than every honest gap on the board, and it is invisible from the score alone.",
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
        FormulaEntry("Separation", "|a − b| / √(SEa² + SEb²)", "0.712 vs 0.698: 2.57 SE at n = 14,042, 0.69 SE at n = 1,000."),
        FormulaEntry("Micro vs macro", "per-question vs per-subject weighting", "Same predictions, different number — state which."),
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

                # On a real subject table, many pairs sit inside 2 SE of each other.
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
                print(micro, macro)            # different numbers, same predictions

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
        "At 14,042 questions the standard error is 0.0039, so the lab's 0.712 vs 0.698 is a real 2.57-SE gap — and a coin flip (0.69 SE) on 1,000 questions.",
        "Subject-level claims are 12× noisier still: a 100-question subject has a standard error of 0.0458.",
        "Micro and macro averaging give different numbers on identical predictions — say which one you report.",
        "Contamination is the largest and least visible term: a reported 0.700 implies a true 0.625 if a fifth of the benchmark leaked into pretraining.",
    ),
    crossLinks = listOf(
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("llms", "LLMs"),
        CrossLink("perplexity", "Perplexity"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
    ),
)
