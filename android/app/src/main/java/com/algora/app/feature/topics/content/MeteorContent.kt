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

internal val meteorContent = TopicContent(
    topicId = "meteor",
    figure = Figure(
        caption = "The page's lab: three candidates scored against \"the committee approved the " +
            "revised budget on friday\". The paraphrase shares 5 words: P = 0.500, R = 0.625, and " +
            "the recall-weighted F = 0.610. Its 4 chunks make a penalty of 0.256, so METEOR gives " +
            "0.454 where BLEU gives 0. The shuffled reference matches every word, so F = 1.000, " +
            "but one pair stays adjacent and the 8 matches form 7 chunks: penalty 0.335, score " +
            "0.665. An exact copy is a single chunk and scores 0.999. However badly the order is " +
            "broken, the penalty can never go past γ = 0.5.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.610", "4 / 5", "0.256", "0.454"),
                listOf("1.000", "7 / 8", "0.335", "0.665"),
                listOf("1.000", "1 / 8", "0.001", "0.999"),
            ),
            rowHeaders = listOf("paraphrase", "shuffled", "exact copy"),
            colHeaders = listOf("F", "chunks", "penalty", "METEOR"),
            marks = listOf(
                FigureCell(0, 3, FigureTone.Accent),
                FigureCell(1, 2, FigureTone.Warn),
                FigureCell(2, 3, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "METEOR exists because of BLEU's paraphrase zero. Instead of counting n-grams it builds an explicit word-to-word alignment, matching in stages — exact words first, then stems, then WordNet synonyms in the full metric. On the paraphrase BLEU scores 0.0000, that alignment finds **5 matched words** and METEOR reports **0.4537**.",
        "**The alignment is scored as a harmonic mean weighted 9:1 towards recall** — α = 0.9, so F = P·R / (αP + (1−α)R). That single constant is a claim about the task: in translation, missing content is worse than adding it. On the paraphrase, precision 0.500 and recall 0.625 combine to 0.610 — pulled towards recall rather than sitting halfway.",
        "**Then the fragmentation penalty, which is how an alignment-based metric recovers the word order a bag of matches threw away.** Group the alignment into runs contiguous in *both* sentences and count them: 4 chunks over 5 matched words gives γ·(chunks/matches)^β = 0.256, and the final score is F × (1 − penalty).",
        "**The penalty has a ceiling, and the lab's shuffle comes close to it.** Shuffle the reference's own words and every word still matches, so F is a perfect 1.000 — but the matches break into runs. In the lab's shuffle one pair stays adjacent, so 8 matches form 7 chunks: penalty 0.5·(7/8)³ = 0.335, score **0.665**. If no two words stay adjacent, every match is its own chunk, (chunks/matches)^β = 1 and the penalty reaches exactly γ = 0.5 — the code below shows that case scoring **0.500, half, never zero.** Word order can cost at most γ of the score, by construction, which is a design decision rather than an accident.",
    ),
    steps = listOf(
        StepCard(1, "Align In Stages", "Exact matches first, then stems, then synonyms — greedy, one-to-one.", 0xFF0EA5E9),
        StepCard(2, "Weight Towards Recall", "α = 0.9 makes omission cost roughly nine times what padding does.", 0xFF3B82F6),
        StepCard(3, "Count The Chunks", "Runs contiguous in both sentences — the unit the penalty is built on.", 0xFF6366F1),
        StepCard(4, "Apply The Penalty", "γ·(chunks/matches)^β, capped at γ when every match stands alone.", 0xFF8B5CF6),
        StepCard(5, "Compare Against BLEU", "They agree on the easy cases and split exactly on the paraphrase.", 0xFFEC4899),
        StepCard(6, "Check The Tooling", "Stemmer, WordNet and tuned constants — per language.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("F-mean", "P·R / (αP + (1−α)R), α = 0.9", "Recall weighted 9:1 against precision."),
        FormulaEntry("Penalty", "γ·(chunks / matches)^β, γ = 0.5, β = 3", "0.256 on the paraphrase."),
        FormulaEntry("METEOR", "F × (1 − penalty)", "0.610 × 0.744 = 0.4537."),
        FormulaEntry("The paraphrase", "BLEU 0.0000 → METEOR 0.4537", "The case the metric was built for."),
        FormulaEntry("Lab shuffle", "F 1.000, 7 chunks, penalty 0.335, score 0.665", "One pair stayed adjacent, so 7 chunks for 8 matches."),
        FormulaEntry("Full shuffle", "F 1.000, penalty 0.500, score 0.500", "The ceiling: chunks = matches ⇒ penalty = γ."),
        FormulaEntry("Degenerate output", "BLEU 0.0000, METEOR 0.1250", "Both reject it; only METEOR separates it from the paraphrase."),
    ),
    notationKey = listOf(
        NotationEntry("alignment", "a one-to-one word mapping between candidate and reference"),
        NotationEntry("chunk", "a run of matches contiguous in both sentences at once"),
        NotationEntry("α", "the recall weight in the harmonic mean — 0.9 in the standard tuning"),
        NotationEntry("γ, β", "the penalty's ceiling and its curvature — 0.5 and 3"),
        NotationEntry("stem match", "a match after suffix stripping, so \"approved\" and \"approve\" align"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The three parts, computed",
            accentColor = 0xFF0EA5E9,
            code = """
                from nltk.translate.meteor_score import meteor_score

                # Reference: "the committee approved the revised budget on friday"
                # Candidate: "on friday the panel signed off on the amended budget"
                #
                #   matched      5 of 10 candidate words (exact, then stem)
                #   precision    0.500     recall 0.625
                #   F (a=0.9)    0.610     <- pulled towards recall
                #   chunks       4 over 5 matches
                #   penalty      0.5 * (4/5)^3 = 0.256
                #   METEOR       0.610 * (1 - 0.256) = 0.4537
                #
                # BLEU on the same pair: 0.0000
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The penalty ceiling, and why it is a ceiling",
            accentColor = 0xFF8B5CF6,
            code = """
                # Take the reference and shuffle its own words:
                #   "friday on budget revised the approved committee the"
                #
                #   every word matches   -> F = 1.000
                #   8 chunks / 8 matches -> (1)^3 = 1
                #   penalty              -> gamma = 0.500  (its maximum)
                #   METEOR               -> 0.500
                #
                # Word order can cost at most gamma of the score. A metric that
                # zeroed a shuffle would be a different design decision; METEOR
                # says "half credit for having the right content, wrongly ordered".

                # The cost of all this: exact matching is universal, but stemming
                # needs a stemmer, synonymy needs a WordNet, and a/b/g were tuned
                # on human judgements -- per language. BLEU needs a tokenizer,
                # which is why BLEU won adoption despite correlating worse.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("translate", 0xFF0EA5E9, "MT Evaluation", "Reported alongside BLEU, where it correlates better with human ratings."),
        ApplicationCard("book", 0xFF3B82F6, "Paraphrase-Heavy Tasks", "Captioning and data-to-text, where the reference is one phrasing of many."),
        ApplicationCard("globe", 0xFF10B981, "Low-Resource Languages", "The place it hurts: no stemmer or WordNet means it degrades to exact matching."),
        ApplicationCard("robot", 0xFFF59E0B, "The Successors", "BERTScore and COMET are METEOR's argument finished — match meanings, not strings."),
    ),
    takeaways = listOf(
        "METEOR aligns words in stages (exact, stem, synonym) rather than counting n-grams, which is why it scores the paraphrase BLEU zeroes at 0.4537.",
        "Its F-mean weights recall 9:1 over precision — an explicit claim that omission is worse than padding.",
        "The fragmentation penalty recovers word order: 4 chunks over 5 matches costs 0.256 of the score.",
        "The lab's shuffle scores 0.665 (7 chunks); a shuffle with no adjacent pair hits the ceiling γ = 0.5 and scores 0.500 — order can never cost more than half.",
        "The price is portability: stemmers, WordNets and per-language tuned constants, which is why BLEU won adoption and learned metrics are replacing both.",
    ),
    crossLinks = listOf(
        CrossLink("bleu", "BLEU Score"),
        CrossLink("rouge", "ROUGE Score"),
        CrossLink("stemming", "Stemming"),
        CrossLink("word_embeddings", "Word Embeddings"),
    ),
)
