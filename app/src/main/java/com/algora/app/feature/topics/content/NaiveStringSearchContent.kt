package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val naiveStringSearchContent = TopicContent(
    topicId = "naive_string_search",
    figure = Figure(
        caption = "Searching aaaab for aaab. Three characters agree, the fourth does not, and the shift " +
            "is one — which throws away everything just learned. The next alignment re-reads the same " +
            "three a's it already compared, and on a text of one repeated character that repeats for " +
            "every alignment, which is the O(n·m) worst case in full. On ordinary text it never happens: " +
            "a mismatch usually lands on the first or second character, the inner loop almost never runs " +
            "to completion, and the observed cost is close to O(n) with no preprocessing and no extra " +
            "memory. The three faster algorithms each attack the discarded knowledge from a different " +
            "side — KMP records it in a prefix table, Boyer-Moore compares right-to-left so a mismatch " +
            "can justify shifting a whole pattern length, Rabin-Karp replaces the comparison with a hash.",
        shape = FigureShape.Strip(
            cells = listOf("a", "a", "a", "a", "b"),
            bands = listOf(
                FigureBand(0, 2, "matched"),
                FigureBand(3, 3, "fails", FigureTone.Warn),
            ),
            aux = listOf("a", "a", "a", "b"),
            auxLabel = "the next alignment, shifted by one — it re-reads three characters it already knows",
        ),
    ),
    whatIsIt = listOf(
        "Naive pattern search lines the pattern up against the start of the text, compares left to right, and on any mismatch slides the pattern one position right and starts over. It is four lines of code with no preprocessing and no extra memory, and it is the baseline every other string-matching algorithm is measured against.",
        "On ordinary text it is also perfectly adequate. The reason is that a mismatch usually happens on the first or second character, so the inner loop almost never runs to completion and the observed cost is close to O(n). What the O(n·m) worst case describes is the pathological input: a text of one repeated character and a pattern that is almost the same, where every alignment matches m−1 characters before failing on the last one. Searching \"aaaaaaaaab\" for \"aaab\" does seven alignments of four comparisons each — twenty-eight comparisons for a ten-character text.",
        "The waste in that case is specific and is what the better algorithms attack. When \"aaab\" fails against \"aaaa\" at the last character, the naive shift of one throws away everything just learned: it already knows the next three text characters are \"aaa\", because it just compared them. KMP records that knowledge in a prefix table and never re-reads a text character. Boyer-Moore attacks it from the other end, comparing right to left so a mismatch can justify a shift of a whole pattern length, which makes it sublinear on large alphabets. Rabin-Karp replaces the comparison itself with a rolling hash. All three cost preprocessing, so on short patterns and small texts the naive scan is the faster choice — and it remains the correct choice when the code has to be obviously right.",
    ),
    steps = listOf(
        StepCard(1, "Align at Position 0", "Place the pattern's first character over the text's first character.", 0xFFEC4899),
        StepCard(2, "Compare Left to Right", "Walk the pattern against the text until a character disagrees or the pattern runs out.", 0xFF3B82F6),
        StepCard(3, "Report a Full Match", "The pattern running out means every character agreed — record the alignment.", 0xFF10B981),
        StepCard(4, "Shift by One", "On a mismatch, move the pattern one position right. Not by the number of characters matched — by one.", 0xFFF59E0B),
        StepCard(5, "Discard What You Learned", "The next alignment re-reads text the previous one already compared. That redundancy is the whole cost.", 0xFFEF4444),
        StepCard(6, "Stop at n − m", "Past that alignment the pattern no longer fits, so the scan ends.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Worst case", "O(n · m)", "Reached at (n − m + 1) alignments each doing m comparisons."),
        FormulaEntry("Alignments", "n − m + 1", "The number of positions where the pattern still fits."),
        FormulaEntry("Space", "O(1)", "No preprocessing table — the only algorithm here with none."),
        FormulaEntry("Typical text", "≈ O(n)", "Most mismatches occur within one or two characters on natural language."),
        FormulaEntry("Worst-case input", "text aⁿ, pattern aᵐ⁻¹b", "Every alignment matches m−1 characters before failing."),
        FormulaEntry("Against KMP", "O(n + m)", "KMP's preprocessing buys the guarantee the naive scan cannot make."),
    ),
    notationKey = listOf(
        NotationEntry("n, m", "lengths of the text and the pattern"),
        NotationEntry("alignment / shift", "a candidate starting position for the pattern in the text"),
        NotationEntry("i, j", "the text index and the pattern index inside the current alignment"),
        NotationEntry("backtracking", "moving the text pointer back after a mismatch — what KMP eliminates"),
        NotationEntry("preprocessing", "work done on the pattern before the scan; this algorithm does none"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Naive search",
            accentColor = 0xFFEC4899,
            code = """
                fun naiveSearch(text: String, pattern: String): List<Int> {
                    val hits = mutableListOf<Int>()
                    if (pattern.isEmpty() || pattern.length > text.length) return hits

                    for (shift in 0..text.length - pattern.length) {
                        var j = 0
                        while (j < pattern.length && text[shift + j] == pattern[j]) j++
                        if (j == pattern.length) hits += shift
                        // On a mismatch the shift advances by exactly 1 — everything the inner
                        // loop just learned about the text is discarded here.
                    }
                    return hits
                }

                naiveSearch("ABCABCABD", "ABCABD")   // [3]
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Counting the waste",
            accentColor = 0xFFEF4444,
            code = """
                fun countComparisons(text: String, pattern: String): Int {
                    var comparisons = 0
                    for (shift in 0..text.length - pattern.length) {
                        var j = 0
                        while (j < pattern.length && text[shift + j] == pattern[j]) {
                            comparisons++
                            j++
                        }
                        if (j < pattern.length) comparisons++   // the failing comparison
                    }
                    return comparisons
                }

                countComparisons("ABCABCABD", "ABCABD")   // 14 over 4 alignments
                countComparisons("AAAAAAAAAB", "AAAB")    // 28 over 7 alignments — n·m, near enough

                // The second call is the whole argument. Every alignment matches "AAA" and then
                // fails, and the shift of one immediately re-compares two of those three
                // characters. KMP's prefix table is exactly a record of what that comparison
                // already established.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Short Patterns & Small Inputs", "Below a few hundred characters the preprocessing of a smarter algorithm costs more than it saves."),
        ApplicationCard("bulb", 0xFF3B82F6, "The Baseline", "Every claim KMP, Boyer-Moore and Rabin-Karp make is a claim about beating this loop on a specific input class."),
        ApplicationCard("chip", 0xFF10B981, "Obviously-Correct Code", "No table to build wrong, no hash to collide — sometimes the right trade in embedded or safety-critical code."),
    ),
    takeaways = listOf(
        "Compare, and on any mismatch shift by one — no preprocessing, O(1) space, and usually near-linear in practice.",
        "The O(n·m) worst case needs a repetitive text and a pattern that nearly matches it, not ordinary prose.",
        "Its waste is concrete: the shift of one re-reads characters the previous alignment already compared.",
        "KMP records that knowledge, Boyer-Moore scans from the right to skip further, Rabin-Karp replaces comparison with hashing.",
    ),
    crossLinks = listOf(
        CrossLink("kmp", "KMP String Matching"),
        CrossLink("rabin_karp", "Rabin-Karp"),
        CrossLink("z_algorithm", "Z Algorithm"),
    ),
)
