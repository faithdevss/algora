package com.algora.app.feature.topics

import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.TopicContent
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A figure that says it shows the page's lab must agree with that lab and with the lesson around it:
 * every measured number it quotes has to appear in the lesson text or in a string the lab draws
 * ([LabText]). Phases 15–17 found figures quoting labs that had since been redesigned; this sweep
 * makes that drift a test failure instead of something an audit has to stumble on.
 *
 * Figures that do not cite the lab are self-contained worked examples (a hand-countable AUC grid,
 * two rings in NumPy, a formula curve) and define their own numbers, so they are not swept.
 *
 * Axis labels are skipped: "gradient sd, 0 to 0.31" names the plot's extent, not a measurement.
 * Numbers a figure derives by plain arithmetic from the lab's own values, which the lab draws as a
 * shape rather than text (0.92⁸ on a curve, a percentage split shown as bars), are listed in
 * [derived] with the arithmetic that produces them.
 *
 * "Measured" means a decimal or a percentage — 0.181, 54%, 2.64. Bare integers are left out: they are
 * mostly counts, sizes and indices that a caption restates in its own words.
 *
 * A figure number matches when the lesson has the same value, ignoring sign (captions say "error
 * −4.38" where prose says "4.38 lower"), or a more precise value that rounds to it (prose 0.6098,
 * figure 0.610), or the same quantity as a percentage or a fraction (54% and 0.54).
 */
class FigureTextAgreementTest {

    /** Figure numbers computed from the lab's own values; each line says how. */
    private val derived: Map<String, List<String>> = mapOf(
        // ACF at lags 8 and 12 — the lab prints lag 1 only; same formula on the lab's training months.
        "arima" to listOf("0.292", "0.322", "0.250"),
        // A hand-countable worked example the caption defines; only its 80,256-pair total is the lab's.
        "auc" to listOf("0.7", "0.4", "0.8", "0.3", "0.1", "0.75"),
        // 0.92⁸ and 0.92¹², the page's own per-step accuracy compounded.
        "chain_of_thought" to listOf("0.513", "0.368"),
        // The lab's other two class scores, from the same smoothed counts it shows.
        "complement_nb" to listOf("5.19", "3.58"),
        // Layers 1, 3 and 5: (64, 128, 192)·128 + 9·128·32 — the lab prints layers 2, 4 and 6.
        "densenet" to listOf("45.1", "53.2", "61.4"),
        // The lab's per-step reward, −0.04.
        "discount_factor" to listOf("0.04"),
        // "60% faster" written as a speed ratio.
        "distilbert" to listOf("1.6"),
        // The He run's activation range, deepRun(ReLU, 12, 16, √(2/16), seed 77) — drawn, not printed.
        "exploding_gradient" to listOf("0.20", "0.52"),
        // holtWinters(train, 0.3, 0.1, 0.3) — level, trend and season are drawn as lines.
        "exponential_smoothing" to listOf("0.1", "43.55", "61.96", "7.80", "4.10", "0.77", "0.315", "61.963", "0.776", "63.055", "0.181"),
        // fitGaussianNb / fitQda on the surface's starting data, unequalCovarianceBlobs(5).
        "gaussian_nb" to listOf("0.986", "0.806", "0.183", "0.187", "1.054", "1.043", "0.647", "0.644", "0.943", "0.609"),
        // f(1.5) = (1.5 / 3)^0.75.
        "glove" to listOf("0.59"),
        // 1,400 / 65 and 2,000 / 70 — the lab rounds them to 22 and 29.
        "gpt3_gpt4" to listOf("21.5", "28.6"),
        // The lab's mutual-reachability MST at minPts = 4, and λ-spans 1/ε₁ − 1/ε₂ from it.
        "hdbscan" to listOf("0.394", "1.120", "2.368", "1.974", "1.248", "2.12", "0.47", "4.5", "1.755", "1.835", "2.110", "2.441"),
        // 0.855 · 0.6³, the plain RNN's decay applied to the lab's stored value.
        "lstm_gru" to listOf("0.18"),
        // exp(Q/α) splits for Q = 1.0 and 0.9; the lab prints α = 0.05's and draws the rest.
        "max_entropy_rl" to listOf("88%", "12%", "62%", "38%", "55%", "45%"),
        // One move in five, chosen at random.
        "mcts" to listOf("20%"),
        // 1/9, and the speed-up 1 / (1/32 + 1/9).
        "mobilenet" to listOf("0.111", "7.0"),
        // The lab's counts with α = 1, and the scores it prints to two decimals, to three.
        "multinomial_nb" to listOf("4.451", "3.578", "35.8", "0.973", "4.86", "1.01", "0.286", "0.059", "0.238", "0.143", "0.048", "0.353", "0.235"),
        // optics(densityData, 4) — reachability is drawn as bars.
        "optics" to listOf("0.305", "1.516", "2.260", "2.368", "0.573", "0.675", "1.203", "1.829", "0.145"),
        // Vπ to three decimals; the lab's grid prints two.
        "policy_iteration" to listOf("0.519", "0.886", "0.621"),
        // Converged Vπ of the lab's two policies, RbGw.evalPi(…, 50) — its grid prints two decimals.
        "value_function" to listOf("0.519", "0.621", "0.734", "0.837"),
        // prioritizedFrames' per-episode error, drawn as two lines.
        "prioritized_replay" to listOf("0.700", "0.428", "0.153", "0.056", "0.647", "0.159", "0.048"),
        // The lab's 44 points: mean line, least squares, the IRLS absolute-error line and a bad line.
        "r_squared" to listOf("9.101", "1802.4", "651.8", "835.7", "1.235", "1.751", "2890.5", "0.362", "0.464", "1.604"),
        // 0.95³⁰, the slowest channel's decay.
        "ssm" to listOf("21%"),
        // Monte Carlo's 500-episode error from the monte_carlo_rl lab, drawn here for comparison.
        "td_learning" to listOf("0.686"),
    )

    private val number = Regex("""(?<![\w.])\d[\d,]*\.\d+%?|(?<![\w.])\d[\d,]*%""")

    private data class Num(val value: BigDecimal, val percent: Boolean)

    private fun numbers(text: String): List<Num> = number.findAll(text).map { m ->
        val raw = m.value.replace(",", "")
        val pct = raw.endsWith("%")
        Num(BigDecimal(raw.removeSuffix("%")), pct)
    }.toList()

    private fun figureText(f: Figure): String {
        val parts = mutableListOf(f.caption)
        when (val s = f.shape) {
            is FigureShape.Strip -> {
                parts += s.cells; parts += s.aux; parts += s.bands.map { it.label }; parts += s.pointers.map { it.label }
                s.auxLabel?.let { parts += it }
            }
            is FigureShape.Timeline -> { parts += s.spans.map { it.label }; s.markerLabel?.let { parts += it } }
            is FigureShape.Grid -> {
                parts += s.rows.flatten(); parts += s.rowHeaders; parts += s.colHeaders
                parts += s.arrows.mapNotNull { it.label }
            }
            is FigureShape.Stacks -> s.columns.forEach { c -> parts += c.label; parts += c.entries; c.note?.let { parts += it } }
            is FigureShape.Tree -> parts += s.nodes.map { it.label }
            is FigureShape.Graph -> { parts += s.nodes.map { it.label }; parts += s.edges.mapNotNull { it.label } }
            is FigureShape.Plot -> {
                parts += s.series.map { it.label }; parts += s.bars.map { it.label }
                parts += s.markers.mapNotNull { it.label }
            }
            is FigureShape.LayerStack -> s.layers.forEach { l -> parts += l.label; l.detail?.let { parts += it } }
            is FigureShape.Heatmap -> { parts += s.rowLabels; parts += s.colLabels; s.legend?.let { parts += it } }
        }
        return parts.joinToString("\n")
    }

    private fun lessonText(c: TopicContent): String = buildList {
        addAll(c.whatIsIt)
        c.steps.forEach { add(it.title); add(it.body) }
        c.formulas.forEach { add(it.label); add(it.formula); add(it.note) }
        c.notationKey.forEach { add(it.symbol); add(it.meaning) }
        c.codeBlocks.forEach { b -> add(b.title); add(b.code); b.variants.forEach { add(it.code) } }
        c.applications.forEach { add(it.title); add(it.body) }
        addAll(c.takeaways)
    }.joinToString("\n")

    private fun BigDecimal.roundsTo(target: BigDecimal): Boolean =
        scale() >= target.scale() && setScale(target.scale(), RoundingMode.HALF_UP).compareTo(target) == 0

    private fun matches(f: Num, lesson: List<Num>): Boolean {
        val want = f.value.abs()
        return lesson.any { l ->
            val have = l.value.abs()
            val candidates = buildList {
                add(have)
                if (l.percent && !f.percent) add(have.movePointLeft(2))
                if (!l.percent && f.percent) add(have.movePointRight(2))
            }
            candidates.any { it.compareTo(want) == 0 || it.roundsTo(want) }
        }
    }

    private val citesLab = Regex("""\blab(?:'s|s)?\b""", RegexOption.IGNORE_CASE)

    /** Every lab-citing figure's unmatched numbers, so a failure lists the whole problem at once. */
    private fun mismatches(): Map<String, List<String>> =
        TopicContentProvider.all.values.mapNotNull { c ->
            val f = c.figure ?: return@mapNotNull null
            if (!citesLab.containsMatchIn(f.caption)) return@mapNotNull null
            val lesson = numbers(lessonText(c) + "\n" + LabText.of(c.topicId))
            val allowed = derived[c.topicId].orEmpty().map { BigDecimal(it.removeSuffix("%")) }
            val missing = numbers(figureText(f))
                .filterNot { matches(it, lesson) }
                .filterNot { n -> allowed.any { it.compareTo(n.value.abs()) == 0 } }
                .map { it.value.toPlainString() + if (it.percent) "%" else "" }
                .distinct()
            if (missing.isEmpty()) null else c.topicId to missing
        }.toMap()

    @Test
    fun `every measured number in a figure appears in its lesson`() {
        val bad = mismatches()
        System.getenv("FIGURE_TEXT_REPORT")?.let { path ->
            java.io.File(path).writeText(bad.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" })
            val dir = java.io.File("$path.d").apply { mkdirs() }
            bad.keys.forEach { id ->
                val c = TopicContentProvider.get(id)!!
                java.io.File(dir, "$id.txt").writeText(
                    "MISSING: ${bad[id]}\n\n### FIGURE\n${figureText(c.figure!!)}\n\n### LESSON\n${lessonText(c)}\n\n### LAB\n${LabText.of(id)}",
                )
            }
        }
        assertTrue(
            "Figures quoting numbers their lesson never states (${bad.size} topics):\n" +
                bad.entries.sortedBy { it.key }.joinToString("\n") { "  ${it.key}: ${it.value.joinToString(", ")}" },
            bad.isEmpty(),
        )
    }
}
