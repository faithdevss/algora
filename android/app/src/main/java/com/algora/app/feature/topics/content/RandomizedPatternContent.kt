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

// Interview-prep pattern guide. Randomness as an algorithmic tool: uniform sampling without the
// length, shuffling without bias, and adversary-proof pivots.
internal val randomizedPatternContent = TopicContent(
    topicId = "randomized_pattern",
    // The lab below animates Fisher-Yates, so the figure carries the other primitive: reservoir
    // sampling, where the stream length is never known and the probability has to be right anyway.
    figure = Figure(
        caption = "Reservoir sampling keeps k items from a stream of unknown length: the first k are " +
            "kept outright, and the i-th arrival replaces a random one with probability k/i. Item 5 " +
            "enters with probability 3/5 — falling as the stream grows, which is exactly what leaves " +
            "every item seen so far holding k/n at the end. The lab below runs the other primitive, " +
            "Fisher-Yates, where the length *is* known and the draw range is what must be right.",
        shape = FigureShape.Strip(
            cells = listOf("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"),
            bands = listOf(
                FigureBand(0, 2, "first k kept outright", FigureTone.Muted),
                FigureBand(4, 4, "arriving", FigureTone.Accent),
            ),
            pointers = listOf(FigurePointer(4, "i = 5")),
            aux = listOf("s1", "s5", "s3"),
            auxLabel = "reservoir (k = 3) — s5 kept with probability k/i = 3/5, replacing a uniformly chosen s2",
        ),
    ),
    whatIsIt = listOf(
        "Some questions are only answerable with randomness: sample uniformly from a stream of unknown length, shuffle without bias, or pick a pivot an adversary cannot predict.",
        "Reservoir sampling keeps k items and replaces one with probability k/i as the i-th arrives. Fisher-Yates swaps each position with a uniform choice from the *unprocessed* suffix. Both are one pass, O(1) extra state per item, and both are easy to get subtly wrong.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Unknown or unbounded length\", \"equal probability\", \"shuffle in place\", or a worst-case input that a deterministic pivot choice would exploit.", 0xFFF59E0B),
        StepCard(2, "Pick the Right Primitive", "Streaming sample → reservoir. Permute a known array → Fisher-Yates. Kill adversarial worst cases → randomised pivot or hash seed.", 0xFF3B82F6),
        StepCard(3, "Get the Probability Exactly Right", "Reservoir: replace with probability k/i. Fisher-Yates: swap i with a uniform index in [i, n), never [0, n).", 0xFFEF4444),
        StepCard(4, "State the Guarantee", "Expected time, not worst case. Quickselect is O(n) expected and O(n²) in the worst case — say which one you mean.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Reservoir", "P(keep item i) = k / i", "Induction gives every seen item probability k/n at the end."),
        FormulaEntry("Fisher-Yates", "swap i ↔ uniform(i, n-1)", "All n! permutations equally likely; sampling [0, n) does not."),
        FormulaEntry("Randomised select", "O(n) expected", "Expected halving of the range; O(n²) adversarially only if the pivot is predictable."),
    ),
    notationKey = listOf(
        NotationEntry("k", "reservoir size — how many samples to keep"),
        NotationEntry("i", "1-based index of the item currently arriving"),
        NotationEntry("uniform(a, b)", "integer drawn uniformly from [a, b]"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Reservoir sampling and an unbiased shuffle (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import random

                def reservoir(stream, k):
                    kept = []
                    for i, item in enumerate(stream, start=1):
                        if i <= k:
                            kept.append(item)
                        else:
                            j = random.randrange(i)        # 0 .. i-1
                            if j < k:                      # replace with probability k/i
                                kept[j] = item
                    return kept

                def shuffle(a):
                    for i in range(len(a) - 1):
                        j = random.randrange(i, len(a))    # suffix only — [i, n)
                        a[i], a[j] = a[j], a[i]
                    return a
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF3B82F6, "Log Sampling", "Keep a uniform sample of an unbounded event stream in bounded memory."),
        ApplicationCard("game", 0xFF10B981, "Shuffling", "Card decks, playlists and A/B bucket assignment without bias."),
        ApplicationCard("lock", 0xFF8B5CF6, "Adversary Defence", "Randomised pivots and seeded hashes defeat inputs crafted to hit worst cases."),
    ),
    takeaways = listOf(
        "Reservoir sampling needs no length up front — that is precisely why it exists.",
        "Fisher-Yates must draw from the unprocessed suffix; drawing from the whole array is the classic biased shuffle.",
        "Randomised algorithms give expected bounds; state the worst case too.",
        "Weighted sampling is prefix sums plus a binary search on a uniform draw.",
    ),
    crossLinks = listOf(
        CrossLink("reservoir_sampling", "Reservoir Sampling (Algorithms)"),
        CrossLink("quickselect", "Quickselect (Algorithms)"),
        CrossLink("monte_carlo_method", "Monte Carlo Method (Algorithms)"),
    ),
)
