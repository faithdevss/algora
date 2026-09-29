package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Bayesian storyboards ─────────────────────────────────────────────────────
// Naive Bayes (Gaussian, on a plane), Multinomial, Bernoulli, Complement and Categorical naive Bayes,
// Bayesian networks and MCMC as step-by-step storyboards: one figure in the card, the step's arithmetic
// under it, then chips and a headline, and a transport whose button names the next step ("Score
// “great”"). Every number is computed from the small fixed data below.

internal val bayesStoryTopicIds = setOf(
    "naive_bayes", "multinomial_nb", "bernoulli_nb", "complement_nb", "categorical_nb", "bayesian_networks", "mcmc",
)

private val BlueClass = SimColors.Blue
private val PinkClass = CategoryAccents.Pink

// ── Scenes ──

private sealed interface BayesScene

/** Two classes on a plane with a query; the per-class bell curves along x and y once fitted. */
private class GaussScene(val showX: Boolean, val showY: Boolean, val predicted: Int?) : BayesScene

/** A document's tokens over each class's word counts; [current] is the vocabulary column being scored. */
private class CountScene(val tokens: List<StoryTone>, val current: Int?) : BayesScene

/** Bernoulli: the document's present words, then one row per vocabulary word as it is scored. */
private class PresenceScene(val scored: Int, val current: Int?) : BayesScene

/** Complement: the two models' score cards, filled as each is run. */
private class ComplementScene(val multinomial: Boolean, val complement: Boolean) : BayesScene

/** Categorical: the row's four values, and the count table of the feature being looked up. */
private class TableScene(val tones: List<StoryTone>, val feature: Int?, val highlight: Int?) : BayesScene

private enum class BnState { Idle, Observed, Question }

/** The sprinkler network: node states and the value pill beside each node. */
private class BnScene(val states: List<BnState>, val pills: List<String>, val pillTones: List<StoryTone>) : BayesScene

/** A Metropolis chain over the crescent: its path so far, refused proposals and the proposal in hand. */
private class ChainScene(val upTo: Int, val proposal: Int?, val dotsOnly: Boolean = false) : BayesScene

private class BayesFrame(
    val headline: String,
    val body: String,
    val scene: BayesScene,
    val action: String,
    val formula: List<String> = emptyList(),
    val chips: List<LabChip> = emptyList(),
)

private class BayesLab(val frames: List<BayesFrame>, val legend: List<Triple<Color, SwatchStyle, String>> = emptyList())

// ── Formatting ──

/** Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−". */
private fun bx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun quoted(word: String) = "“$word”"

// ── Naive Bayes (Gaussian, 2D) ──

private class GaussFit(val mean: Double, val sd: Double) {
    fun pdf(v: Double) = exp(-0.5 * ((v - mean) / sd).let { it * it }) / (sd * sqrt(2 * PI))
}

private class GaussData(
    val points: List<Triple<Double, Double, Int>>,
    val query: Pair<Double, Double>,
    val fx: List<GaussFit>,
    val fy: List<GaussFit>,
)

private val gaussData: GaussData by lazy {
    val random = Random(11)
    fun normal(): Double {
        val u = max(random.nextDouble(), 1e-12)
        return sqrt(-2 * ln(u)) * cos(2 * PI * random.nextDouble())
    }
    val points = (0 until 10).map { Triple(3.0 + normal() * 1.1, 7.2 + normal() * 0.6, 0) } +
        (0 until 10).map { Triple(7.0 + normal() * 1.0, 4.4 + normal() * 1.2, 1) }
    fun fit(values: List<Double>): GaussFit {
        val m = values.average()
        return GaussFit(m, sqrt(values.sumOf { (it - m) * (it - m) } / values.size))
    }
    GaussData(
        points,
        4.9 to 6.3,
        (0..1).map { c -> fit(points.filter { it.third == c }.map { it.first }) },
        (0..1).map { c -> fit(points.filter { it.third == c }.map { it.second }) },
    )
}

private fun naiveBayesLab(): BayesLab {
    val d = gaussData
    val (qx, qy) = d.query
    val px = d.fx.map { it.pdf(qx) }
    val py = d.fy.map { it.pdf(qy) }
    val score = (0..1).map { 0.5 * px[it] * py[it] }
    val win = if (score[0] >= score[1]) 0 else 1
    val lose = 1 - win
    val name = listOf("class 0", "class 1")
    val xWin = if (px[0] >= px[1]) 0 else 1
    val yWin = if (py[0] >= py[1]) 0 else 1
    val formula = (0..1).map { c -> "${name[c]} ∝ 0.5 × ${bx(px[c])} × ${bx(py[c])} = ${bx(score[c], 3)}" }
    val posterior = score[win] / (score[0] + score[1])
    return BayesLab(
        listOf(
            BayesFrame(
                "Where does the {yellow query} belong?",
                "Naive Bayes fits one bell curve per class and per feature, then multiplies what they say about the query.",
                GaussScene(showX = false, showY = false, predicted = null),
                "Fit x Curves",
            ),
            BayesFrame(
                "Along x, {${name[xWin]}'s curve} is higher at the query: ${bx(px[xWin])} against ${bx(px[1 - xWin])}.",
                "Each curve is a Gaussian fitted to one class's x values alone: a mean and a spread.",
                GaussScene(showX = true, showY = false, predicted = null),
                "Fit y Curves",
            ),
            BayesFrame(
                "Along y, {${name[yWin]}} is higher: ${bx(py[yWin])} against ${bx(py[1 - yWin])}.",
                "The dashed ovals are the two curves together. They stay axis-aligned, because the model never looks at x and y jointly.",
                GaussScene(showX = true, showY = true, predicted = null),
                "Multiply",
            ),
            BayesFrame(
                "Class $win wins, ${bx(score[win], 3)} to ${bx(score[lose], 3)}.",
                "Each factor is one bell curve read at the query's x or y. Multiplying them is the naive part: it treats x and y as independent.",
                GaussScene(showX = true, showY = true, predicted = null),
                "Predict",
                formula = formula,
            ),
            BayesFrame(
                "Predicted {class $win}, with P = ${bx(posterior)} once the two scores are normalised.",
                "Divide each score by their sum: ${bx(score[win], 3)} / (${bx(score[0], 3)} + ${bx(score[1], 3)}). The priors were equal, so the curves decided it.",
                GaussScene(showX = true, showY = true, predicted = win),
                "Start Over",
                formula = formula,
            ),
        ),
        legend = listOf(
            Triple(BlueClass, SwatchStyle.Dot, "Class 0"),
            Triple(PinkClass, SwatchStyle.Dot, "Class 1"),
            Triple(SimColors.Active, SwatchStyle.Dot, "Query"),
        ),
    )
}

// ── Multinomial ──

private val nbVocab = listOf("goal", "match", "team", "vote", "policy", "great")
private val sportsCounts = listOf(5, 4, 2, 0, 0, 4)
private val politicsCounts = listOf(0, 0, 0, 5, 3, 3)
private val nbDoc = listOf("goal", "goal", "great")

private fun multinomialLab(): BayesLab {
    val sTotal = sportsCounts.sum()
    val pTotal = politicsCounts.sum()
    val v = nbVocab.size
    fun logP(counts: List<Int>, total: Int, word: String) = ln((counts[nbVocab.indexOf(word)] + 1.0) / (total + v))
    val priors = listOf(ln(3.0 / 5), ln(2.0 / 5))
    fun terms(k: Int) = nbDoc.take(k).map { listOf(logP(sportsCounts, sTotal, it), logP(politicsCounts, pTotal, it)) }
    fun line(label: String, c: Int, k: Int): String {
        val parts = listOf(priors[c]) + terms(k).map { it[c] }
        val sum = parts.sum()
        return if (parts.size == 1) "$label ${bx(sum)}" else "$label ${parts.joinToString(" ") { bx(it) }} = ${bx(sum)}"
    }
    fun formula(k: Int) = listOf(line("sports", 0, k), line("politics", 1, k))
    fun total(k: Int, c: Int) = priors[c] + terms(k).sumOf { it[c] }
    fun tokens(current: Int?) = nbDoc.indices.map { i ->
        when {
            current == null -> StoryTone.Idle
            i < current -> StoryTone.Done
            i == current -> StoryTone.Active
            else -> StoryTone.Idle
        }
    }
    val gap2 = total(2, 0) - total(2, 1)
    val gap3 = total(3, 0) - total(3, 1)
    return BayesLab(
        listOf(
            BayesFrame(
                "A document is just its word counts: {goal ×2}, great ×1.",
                "Multinomial NB scores each class by how often that class used each word, one factor per occurrence.",
                CountScene(tokens(null), null),
                "Count Words",
            ),
            BayesFrame(
                "Sports has {$sTotal tokens} of training text, politics $pTotal.",
                "These counts are the whole model: P(word | class) is a word's share of its class's tokens.",
                CountScene(tokens(null), null),
                "Add Priors",
            ),
            BayesFrame(
                "Start from the priors: {3 of 5} training documents were sports.",
                "Scores are summed in logs, so a long document never underflows to zero.",
                CountScene(tokens(null), null),
                "Score ${quoted("goal")}",
                formula = formula(0),
            ),
            BayesFrame(
                "Sports used {${quoted("goal")}} ${sportsCounts[0]} times in $sTotal tokens; politics never did.",
                "Smoothing adds 1 to every count: (${sportsCounts[0]} + 1) / ($sTotal + $v) for sports, 1/${pTotal + v} for politics, so no word can zero a class out.",
                CountScene(tokens(0), 0),
                "Score ${quoted("goal")}",
                formula = formula(1),
            ),
            BayesFrame(
                "The second {${quoted("goal")}} is scored again. Counts add up; they don't just flag presence.",
                "Politics never saw ${quoted("goal")}, so smoothing gives it 1/${pTotal + v} and it falls ${bx(gap2, 1)} behind.",
                CountScene(tokens(1), 0),
                "Score ${quoted("great")}",
                formula = formula(2),
            ),
            BayesFrame(
                "{${quoted("great")}} is nearly even: ${sportsCounts[5] + 1}/${sTotal + v} for sports, ${politicsCounts[5] + 1}/${pTotal + v} for politics.",
                "Politics stays ${bx(gap3, 1)} behind. One even word can't undo two lopsided ones.",
                CountScene(tokens(2), 5),
                "Predict",
                formula = formula(3),
            ),
            BayesFrame(
                "{m:Sports} wins, ${bx(total(3, 0))} to ${bx(total(3, 1))}.",
                "Both uses of ${quoted("goal")} counted. Bernoulli NB would count that word once.",
                CountScene(tokens(3), null),
                "Start Over",
                formula = formula(3),
            ),
        ),
    )
}

// ── Bernoulli ──

private val bernSports = listOf(0.8, 0.6, 0.6, 0.2, 0.2, 0.6)
private val bernPolitics = listOf(0.25, 0.25, 0.25, 0.75, 0.75, 0.5)
private val bernPresent = listOf(true, false, false, false, false, true)

private fun bernFactor(k: Int, c: Int): Double {
    val p = if (c == 0) bernSports[k] else bernPolitics[k]
    return if (bernPresent[k]) p else 1 - p
}

private fun bernLog(scored: Int, c: Int) = ln(if (c == 0) 0.6 else 0.4) + (0 until scored).sumOf { ln(bernFactor(it, c)) }

private fun bernoulliLab(): BayesLab {
    fun logLine(k: Int) = "log sports {v:${bx(bernLog(k, 0))}} log politics ${bx(bernLog(k, 1))}"
    val frames = mutableListOf(
        BayesFrame(
            "Bernoulli sees a {set of words}: goal is present, so is great.",
            "The second ${quoted("goal")} changes nothing here. Every other vocabulary word counts as absent.",
            PresenceScene(0, null),
            "Score ${quoted(nbVocab[0])}",
            formula = listOf("log P(sports) = −0.51, log P(politics) = −0.92", logLine(0)),
        ),
    )
    nbVocab.indices.forEach { k ->
        val w = nbVocab[k]
        val s = bernSports[k]
        val p = bernPolitics[k]
        val present = bernPresent[k]
        val headline = if (present) {
            "{${quoted(w)}} is present: sports keeps ${bx(s)}, politics ${bx(p)}."
        } else {
            "{${quoted(w)}} is absent, and Bernoulli counts that as evidence."
        }
        val body = if (present) {
            "A present word works as in multinomial, but it counts once however often it appears."
        } else {
            "Sports keeps 1 − ${bx(s)} = ${bx(1 - s)}; politics keeps 1 − ${bx(p)} = ${bx(1 - p)}. Multinomial would skip this term."
        }
        frames += BayesFrame(
            headline,
            body,
            PresenceScene(k + 1, k),
            if (k + 1 < nbVocab.size) "Score ${quoted(nbVocab[k + 1])}" else "Add Up",
            formula = listOf(
                if (present) "present → × P($w | class)" else "absent → × (1 − P($w | class))",
                logLine(k + 1),
            ),
        )
    }
    val n = nbVocab.size
    val absentShift = nbVocab.indices.filter { !bernPresent[it] }.sumOf { ln(bernFactor(it, 0)) - ln(bernFactor(it, 1)) }
    val gap = bernLog(n, 0) - bernLog(n, 1)
    frames += BayesFrame(
        "{m:Sports} wins, ${bx(bernLog(n, 0))} to ${bx(bernLog(n, 1))}.",
        "Every one of the ${n} vocabulary words voted, present or not.",
        PresenceScene(n, null),
        "Compare",
        formula = listOf("sum over all $n words, present and absent", logLine(n)),
    )
    frames += BayesFrame(
        "The four absent words moved the gap by {${bx(absentShift)}} toward ${if (absentShift >= 0) "sports" else "politics"}.",
        "Without them sports would lead by ${bx(gap - absentShift)}, not ${bx(gap)}. Bernoulli suits short texts, where what is missing says as much as what is there.",
        PresenceScene(n, null),
        "Start Over",
        formula = listOf("absent words: Σ log(1 − P) = ${bx(absentShift)} in sports' favour", logLine(n)),
    )
    return BayesLab(frames)
}

// ── Complement ──

private val compVocab = listOf("goal", "match", "great", "vote", "policy", "law")
private val compSports = listOf(3, 3, 0, 0, 0, 0)
private val compPolitics = listOf(1, 0, 8, 11, 8, 4)
private val compDoc = listOf("goal", "great")

private fun compLog(counts: List<Int>, word: String) = ln((counts[compVocab.indexOf(word)] + 1.0) / (counts.sum() + compVocab.size))

private val compMultinomial = listOf(
    ln(0.2) + compDoc.sumOf { compLog(compSports, it) },
    ln(0.8) + compDoc.sumOf { compLog(compPolitics, it) },
)

// Each class scored with the other class's counts; the worst fit to the rest wins.
private val compComplement = listOf(
    compDoc.sumOf { compLog(compPolitics, it) },
    compDoc.sumOf { compLog(compSports, it) },
)

private fun complementLab(): BayesLab {
    val chips = listOf(LabChip("sports", "2 docs · 6 tok"), LabChip("politics", "8 docs · 32 tok"))
    val g = compLog(compPolitics, "goal")
    val gr = compLog(compPolitics, "great")
    return BayesLab(
        listOf(
            BayesFrame(
                "Sports has {2 documents}, politics 8, and the test document is sports.",
                "With so little sports text, every sports estimate rests on 6 tokens.",
                ComplementScene(multinomial = false, complement = false),
                "Score Multinomial",
                chips = chips,
            ),
            BayesFrame(
                "Multinomial calls it {w:politics}: sports has only 6 tokens and never saw ${quoted("great")}.",
                "Smoothing gives ${quoted("great")} 1/12 in sports, and the prior, 0.2 against 0.8, piles on.",
                ComplementScene(multinomial = true, complement = false),
                "Score Complement",
                formula = listOf(
                    "politics: log 0.8 + log P(goal | pol) + log P(great | pol)",
                    "= ${bx(ln(0.8))} ${bx(g)} ${bx(gr)} = {v:${bx(compMultinomial[1])}}",
                ),
                chips = chips,
            ),
            BayesFrame(
                "Complement NB flips it to {m:sports}: it asks which class the rest fits worst.",
                "Complement NB scores each class with the other classes' counts. Politics' 32 tokens now estimate sports' score, and sports wins.",
                ComplementScene(multinomial = true, complement = true),
                "Predict",
                formula = listOf(
                    "sports: log P(goal | pol) + log P(great | pol)",
                    "= ${bx(g)} ${bx(gr)} = {v:${bx(compComplement[0])}}",
                ),
                chips = chips,
            ),
            BayesFrame(
                "{m:Sports} is right. Complement NB is steadier when the classes are unbalanced.",
                "Each estimate now rests on the many tokens outside a class, not the few inside it.",
                ComplementScene(multinomial = true, complement = true),
                "Start Over",
                chips = chips,
            ),
        ),
    )
}

// ── Categorical ──

private class CatFeature(val name: String, val value: String, val adjective: String, val levels: List<String>, val yes: List<Int>, val no: List<Int>)

private val catFeatures = listOf(
    CatFeature("Outlook", "sunny", "sunny", listOf("sunny", "overcast", "rain"), listOf(2, 4, 3), listOf(3, 0, 2)),
    CatFeature("Temp", "cool", "cool", listOf("hot", "mild", "cool"), listOf(2, 4, 3), listOf(2, 2, 1)),
    CatFeature("Humidity", "high", "humid", listOf("high", "normal"), listOf(3, 6), listOf(4, 1)),
    CatFeature("Windy", "true", "windy", listOf("false", "true"), listOf(6, 3), listOf(2, 3)),
)

private fun catProducts(k: Int): Pair<Double, Double> {
    var yes = 9.0 / 14
    var no = 5.0 / 14
    catFeatures.take(k).forEach { f ->
        val i = f.levels.indexOf(f.value)
        yes *= f.yes[i] / 9.0
        no *= f.no[i] / 5.0
    }
    return yes to no
}

private fun categoricalLab(): BayesLab {
    fun tones(current: Int?, done: Int) = catFeatures.indices.map { i ->
        when {
            i == current -> StoryTone.Active
            i < done -> StoryTone.Done
            else -> StoryTone.Idle
        }
    }
    fun chips(k: Int) = catProducts(k).let { (y, n) ->
        listOf(LabChip("yes", bx(y, 4), tint = StoryTone.Path), LabChip("no", bx(n, 4), tint = StoryTone.Answer))
    }
    val frames = mutableListOf(
        BayesFrame(
            "Classify one day: {sunny, cool, high, true}.",
            "Each feature is a category, not a number. Categorical NB keeps one count table per feature.",
            TableScene(tones(null, 0), null, null),
            "Add Priors",
        ),
        BayesFrame(
            "Start from the priors: {9 of 14} days were ${quoted("yes")}.",
            "Each feature's table then multiplies in P(value | class).",
            TableScene(tones(null, 0), null, null),
            "Look Up ${catFeatures[0].name}",
            chips = chips(0),
        ),
    )
    catFeatures.forEachIndexed { k, f ->
        val i = f.levels.indexOf(f.value)
        val yesShare = f.yes[i] / 9.0
        val noShare = f.no[i] / 5.0
        val first = if (noShare > yesShare) "${f.no[i]} of 5 ${quoted("no")} days" else "${f.yes[i]} of 9 ${quoted("yes")} days"
        val second = if (noShare > yesShare) "${f.yes[i]} of 9 ${quoted("yes")} days" else "${f.no[i]} of 5 ${quoted("no")} days"
        val body = when (k) {
            0, 2 -> "Each feature has its own count table, so ${quoted(f.value)} is a label to look up, never a number to compare."
            1 -> "This one favours ${if (yesShare > noShare) "yes" else "no"}. The chips are the running products, prior included."
            else -> "The last factor. Both products are now complete."
        }
        frames += BayesFrame(
            "${f.name} = {${f.value}}: $first were ${f.adjective}, against $second.",
            body,
            TableScene(tones(k, k), k, i),
            if (k + 1 < catFeatures.size) "Look Up ${catFeatures[k + 1].name}" else "Compare",
            formula = listOf(
                "P(${f.value} | yes) = ${f.yes[i]}/9 = ${bx(yesShare)}",
                "P(${f.value} | no) = ${f.no[i]}/5 = ${bx(noShare)}",
            ),
            chips = chips(k + 1),
        )
    }
    val (y, n) = catProducts(catFeatures.size)
    val winner = if (n > y) "No" else "Yes"
    frames += BayesFrame(
        "{m:$winner} wins, ${bx(max(y, n), 4)} to ${bx(min(y, n), 4)}.",
        "Normalised, that is P(${winner.lowercase()}) = ${bx(max(y, n) / (y + n))}. Every factor came straight from a count table.",
        TableScene(tones(null, catFeatures.size), null, null),
        "Check Zero Counts",
        chips = chips(catFeatures.size),
    )
    frames += BayesFrame(
        "A value never seen with a class gives a {w:0 count}, and one zero wipes out the whole product.",
        "Overcast never occurred on a ${quoted("no")} day: 0/5. Categorical NB adds α to every count so an unseen value only lowers the score.",
        TableScene(tones(null, catFeatures.size), 0, 1),
        "Start Over",
        formula = listOf("P(overcast | no) = 0/5 = 0.00", "smoothed: (0 + 1) / (5 + 3) = 0.13"),
        chips = chips(catFeatures.size),
    )
    return BayesLab(frames)
}

// ── Bayesian network ──

private val netNames = listOf("Cloudy", "Sprinkler", "Rain", "WetGrass")

/** P(query = true | evidence) on the sprinkler network, by enumerating all 16 worlds. */
private fun netPosterior(query: Int, evidence: Map<Int, Boolean>): Double {
    var num = 0.0
    var den = 0.0
    for (mask in 0 until 16) {
        val v = BooleanArray(4) { (mask shr it) and 1 == 1 }
        if (evidence.any { (k, b) -> v[k] != b }) continue
        val pc = 0.5
        val ps = if (v[0]) 0.1 else 0.5
        val pr = if (v[0]) 0.8 else 0.2
        val pw = when {
            v[1] && v[2] -> 0.99
            v[1] || v[2] -> 0.9
            else -> 0.0
        }
        val p = pc * (if (v[1]) ps else 1 - ps) * (if (v[2]) pr else 1 - pr) * (if (v[3]) pw else 1 - pw)
        den += p
        if (v[query]) num += p
    }
    return num / den
}

private fun bayesNetLab(): BayesLab {
    val prior = (0..3).map { netPosterior(it, emptyMap()) }
    val wet = (0..3).map { netPosterior(it, mapOf(3 to true)) }
    val both = (0..3).map { netPosterior(it, mapOf(3 to true, 1 to true)) }
    fun pills(values: List<Double>, observed: Set<Int>, question: Int?) = BnScene(
        states = (0..3).map { if (it in observed) BnState.Observed else if (it == question) BnState.Question else BnState.Idle },
        pills = (0..3).map { if (it in observed) "= true" else bx(values[it]) },
        pillTones = (0..3).map { if (it == question) StoryTone.Answer else StoryTone.Idle },
    )
    return BayesLab(
        listOf(
            BayesFrame(
                "Before any evidence, P(Rain) is {${bx(prior[2])}}.",
                "Each arrow is a conditional table: rain depends on cloud, wet grass on the sprinkler and the rain.",
                pills(prior, emptySet(), null),
                "Observe WetGrass",
                chips = listOf(LabChip("P(Rain)", bx(prior[2]))),
            ),
            BayesFrame(
                "The grass is wet: {p:WetGrass} is now observed.",
                "An observed node is fixed. Every other probability has to be recomputed given it.",
                pills(prior, setOf(3), null),
                "Update Sprinkler",
                chips = listOf(LabChip("P(Rain)", bx(prior[2]))),
            ),
            BayesFrame(
                "Wet grass raises P(Sprinkler) from ${bx(prior[1])} to {v:${bx(wet[1])}}.",
                "Evidence flows against the arrows: a sprinkler is one way the grass gets wet.",
                BnScene(
                    states = listOf(BnState.Idle, BnState.Question, BnState.Idle, BnState.Observed),
                    pills = listOf(bx(prior[0]), bx(wet[1]), bx(prior[2]), "= true"),
                    pillTones = listOf(StoryTone.Idle, StoryTone.Answer, StoryTone.Idle, StoryTone.Idle),
                ),
                "Update Rain",
                chips = listOf(LabChip("P(Spr)", bx(prior[1])), LabChip("P(Spr | wet)", bx(wet[1]), tint = StoryTone.Answer)),
            ),
            BayesFrame(
                "Wet grass raises P(Rain) from ${bx(prior[2])} to {v:${bx(wet[2])}}.",
                "Evidence flows against the arrows. Sprinkler rises too, from ${bx(prior[1])} to ${bx(wet[1])}, since either could have wet the grass.",
                pills(wet, setOf(3), 2),
                "Observe Sprinkler",
                chips = listOf(LabChip("P(Rain)", bx(prior[2])), LabChip("P(Rain | wet)", bx(wet[2]), tint = StoryTone.Answer)),
            ),
            BayesFrame(
                "The sprinkler was on, and P(Rain) falls back to {v:${bx(both[2])}}.",
                "The sprinkler already explains the wet grass, so rain is no longer needed. This is explaining away.",
                pills(both, setOf(1, 3), 2),
                "Summary",
                chips = listOf(LabChip("P(Rain | wet)", bx(wet[2])), LabChip("P(Rain | wet, spr)", bx(both[2]), tint = StoryTone.Answer)),
            ),
            BayesFrame(
                "{Rain and Sprinkler} were independent causes until wet grass tied them together.",
                "Observing a common effect makes its causes compete. Cloudy moved too: ${bx(prior[0])}, then ${bx(wet[0])}, then ${bx(both[0])}.",
                pills(both, setOf(1, 3), null),
                "Start Over",
                chips = listOf(LabChip("P(Rain)", "${bx(prior[2])} → ${bx(wet[2])} → ${bx(both[2])}")),
            ),
        ),
        legend = listOf(
            Triple(SimColors.Blue, SwatchStyle.Dot, "Observed"),
            Triple(SimColors.Active, SwatchStyle.Dot, "In question"),
            Triple(SimColors.Answer, SwatchStyle.Dot, "Posterior"),
        ),
    )
}

// ── MCMC ──

private const val MCMC_STEPS = 500

/** An unnormalised crescent: a parabola's neighbourhood, fading along x. */
private fun crescent(x: Double, y: Double): Double {
    val r = (y - (0.85 * x * x - 1.1)) / 0.32
    return exp(-0.5 * r * r - 0.5 * (x / 1.25) * (x / 1.25))
}

private class McmcStep(val fromX: Double, val fromY: Double, val toX: Double, val toY: Double, val alpha: Double, val u: Double, val accepted: Boolean)

private val mcmcRun: List<McmcStep> by lazy {
    val random = Random(21)
    fun normal(): Double {
        val u = max(random.nextDouble(), 1e-12)
        return sqrt(-2 * ln(u)) * cos(2 * PI * random.nextDouble())
    }
    var x = -1.55
    var y = 0.95
    (0 until MCMC_STEPS).map {
        val nx = x + normal() * 0.35
        val ny = y + normal() * 0.35
        val alpha = min(1.0, crescent(nx, ny) / crescent(x, y))
        val u = random.nextDouble()
        val ok = u < alpha
        val step = McmcStep(x, y, nx, ny, alpha, u, ok)
        if (ok) { x = nx; y = ny }
        step
    }
}

private fun mcmcLab(): BayesLab {
    val run = mcmcRun
    val uphill = run.indexOfFirst { it.alpha >= 1.0 }
    val downhill = (60 until run.size).firstOrNull { run[it].alpha in 0.15..0.6 } ?: 60
    fun accepted(n: Int) = run.take(n).count { it.accepted }
    fun chips(n: Int) = listOf(
        LabChip("iteration", "$n"),
        LabChip("accepted", "${accepted(n)}/$n", tint = StoryTone.Path),
        LabChip("rate", if (n == 0) "—" else "${(100.0 * accepted(n) / n).roundToInt()}%"),
    )
    fun alphaLine(i: Int) = run[i].let { s ->
        "α = min(1, p(x′) / p(x)) = min(1, ${bx(crescent(s.toX, s.toY))} / ${bx(crescent(s.fromX, s.fromY))}) = {v:${bx(s.alpha)}}"
    }
    val d = run[downhill]
    val rate = (100.0 * accepted(run.size) / run.size).roundToInt()
    return BayesLab(
        listOf(
            BayesFrame(
                "The chain starts at one point on the {crescent}.",
                "Metropolis never needs the normalising constant. It only compares p at two points.",
                ChainScene(upTo = 0, proposal = null),
                "Propose",
                chips = chips(0),
            ),
            BayesFrame(
                "This proposal is {m:uphill}, so α = 1 and it is always accepted.",
                "A proposal is a small random jump from where the chain is now.",
                ChainScene(upTo = uphill, proposal = uphill),
                "Test Proposal",
                formula = listOf(alphaLine(uphill)),
                chips = chips(uphill),
            ),
            BayesFrame(
                "Accepted: the chain {moves} to the proposal.",
                "An uphill move is never refused. Downhill ones are where it gets interesting.",
                ChainScene(upTo = uphill + 1, proposal = null),
                "Run to ${downhill}",
                chips = chips(uphill + 1),
            ),
            BayesFrame(
                "After $downhill proposals, ${accepted(downhill)} were accepted.",
                "Hollow circles are refused proposals. When one is refused the chain stays put, and that repeat counts as a sample.",
                ChainScene(upTo = downhill, proposal = null),
                "Propose",
                chips = chips(downhill),
            ),
            BayesFrame(
                "This proposal is {downhill}, so it is accepted with probability ${bx(d.alpha)}.",
                "Taking some downhill moves lets the chain cover the whole crescent. Uphill-only moves would leave it stuck at the peak.",
                ChainScene(upTo = downhill, proposal = downhill),
                "Test Proposal",
                formula = listOf(alphaLine(downhill)),
                chips = chips(downhill),
            ),
            BayesFrame(
                if (d.accepted) "u = ${bx(d.u)} is below α, so the downhill move is {m:accepted}."
                else "u = ${bx(d.u)} is above α, so the proposal is {w:refused} and the chain stays.",
                "Over many steps each region is visited in proportion to its probability.",
                ChainScene(upTo = downhill + 1, proposal = null),
                "Run $MCMC_STEPS Steps",
                formula = listOf("u = ${bx(d.u)} ${if (d.accepted) "<" else "≥"} α = ${bx(d.alpha)}"),
                chips = chips(downhill + 1),
            ),
            BayesFrame(
                "After $MCMC_STEPS steps the samples {trace the crescent}.",
                "Acceptance rate $rate%: a proposal step of 0.35 keeps it in the 20–50% range samplers usually aim for.",
                ChainScene(upTo = run.size, proposal = null, dotsOnly = true),
                "Start Over",
                chips = chips(run.size),
            ),
        ),
        legend = listOf(
            Triple(SimColors.Blue, SwatchStyle.Dot, "Chain"),
            Triple(SimColors.Active, SwatchStyle.Dot, "Proposal"),
            Triple(SimColors.Grey, SwatchStyle.Ring, "Rejected"),
        ),
    )
}

// ── Lab ──

private fun bayesLab(topicId: String): BayesLab = when (topicId) {
    "multinomial_nb" -> multinomialLab()
    "bernoulli_nb" -> bernoulliLab()
    "complement_nb" -> complementLab()
    "categorical_nb" -> categoricalLab()
    "bayesian_networks" -> bayesNetLab()
    "mcmc" -> mcmcLab()
    else -> naiveBayesLab()
}

internal fun bayesStoryFrameCount(topicId: String): Int = bayesLab(topicId).frames.size

@Composable
internal fun BayesStorySection(topicId: String) {
    val lab = remember(topicId) { bayesLab(topicId) }
    val playback = rememberPlaybackState(key = topicId, stepCount = lab.frames.size, initialSpeedMs = 1000f)
    val frame = lab.frames[playback.index.coerceIn(0, lab.frames.lastIndex)]

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (val scene = frame.scene) {
                    is GaussScene -> GaussView(scene)
                    is CountScene -> CountView(scene)
                    is PresenceScene -> PresenceView(scene)
                    is ComplementScene -> ComplementView(scene)
                    is TableScene -> TableView(scene)
                    is BnScene -> NetView(scene)
                    is ChainScene -> ChainView(scene)
                }
                if (frame.formula.isNotEmpty()) {
                    if (frame.scene is GaussScene) GaussFormula(frame.formula, Modifier.padding(top = 12.dp))
                    else BayesFormula(frame.formula, Modifier.padding(top = 12.dp))
                }
                StoryLegendRow(lab.legend, Modifier.padding(top = 14.dp))
            }
        }
        LabChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = lab.frames.map { storyPlain(it.headline) }, action = { lab.frames[it.coerceIn(0, lab.frames.lastIndex)].action })
    }
}

// ── Rendering ──

private fun Modifier.stage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun BayesFormula(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(
                storyAnnotated(line),
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

/** The two class scores, each class name in its colour and the larger score in violet. */
@Composable
private fun GaussFormula(lines: List<String>, modifier: Modifier = Modifier) {
    val violet = StoryTone.Answer.ink()
    val values = lines.map { it.substringAfterLast("= ").replace("−", "-").toDoubleOrNull() ?: 0.0 }
    val best = values.indices.maxByOrNull { values[it] } ?: 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEachIndexed { i, line ->
            val name = line.substringBefore(" ∝")
            val tail = line.substring(name.length)
            val eq = tail.lastIndexOf("= ")
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (i == 0) Color(0xFF93B4F8) else Color(0xFFF472B6))) { append(name) }
                    append(tail.substring(0, eq + 2))
                    withStyle(SpanStyle(color = if (i == best) violet else Color.Unspecified)) { append(tail.substring(eq + 2)) }
                },
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

@Composable
private fun GaussView(scene: GaussScene) {
    val d = gaussData
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.45f).stage()) {
        val lo = 0.0
        val hi = 10.0
        fun px(x: Double) = ((x - lo) / (hi - lo) * size.width).toFloat()
        fun py(y: Double) = (size.height - (y - lo) / (hi - lo) * size.height).toFloat()
        val (qx, qy) = d.query
        val colors = listOf(BlueClass, PinkClass)
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        // Bells along the bottom (x) and the left edge (y), each scaled to its own peak.
        if (scene.showX) {
            val peak = d.fx.maxOf { it.pdf(it.mean) }
            (0..1).forEach { c ->
                val path = Path()
                (0..120).forEach { i ->
                    val x = lo + (hi - lo) * i / 120
                    val h = d.fx[c].pdf(x) / peak * size.height * 0.22
                    val o = Offset(px(x), size.height - h.toFloat())
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, colors[c], style = Stroke(width = 2.dp.toPx()))
            }
            drawLine(SimColors.Active, Offset(px(qx), py(qy)), Offset(px(qx), size.height), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
        if (scene.showY) {
            val peak = d.fy.maxOf { it.pdf(it.mean) }
            (0..1).forEach { c ->
                val path = Path()
                (0..120).forEach { i ->
                    val y = lo + (hi - lo) * i / 120
                    val w = d.fy[c].pdf(y) / peak * size.width * 0.12
                    val o = Offset(w.toFloat(), py(y))
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, colors[c], style = Stroke(width = 2.dp.toPx()))
                // The class's two curves together: a 1.5σ oval, axis-aligned.
                val rx = (d.fx[c].sd * 1.5 / (hi - lo) * size.width).toFloat()
                val ry = (d.fy[c].sd * 1.5 / (hi - lo) * size.height).toFloat()
                drawOval(
                    colors[c],
                    topLeft = Offset(px(d.fx[c].mean) - rx, py(d.fy[c].mean) - ry),
                    size = Size(rx * 2, ry * 2),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash),
                )
            }
            drawLine(SimColors.Active, Offset(px(qx), py(qy)), Offset(0f, py(qy)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
        val r = 5.5.dp.toPx()
        d.points.forEach { (x, y, c) -> drawCircle(colors[c], r, Offset(px(x), py(y))) }
        val q = Offset(px(qx), py(qy))
        drawCircle(SimColors.Active, 7.dp.toPx(), q)
        drawCircle(surface, 7.dp.toPx(), q, style = Stroke(width = 1.5.dp.toPx()))
        scene.predicted?.let { drawCircle(colors[it], 12.dp.toPx(), q, style = Stroke(width = 2.5.dp.toPx())) }
    }
}

@Composable
private fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A word or value tile, tinted in its state: green once used, yellow while scored, grey still to come. */
@Composable
private fun WordTile(text: String, tone: StoryTone, modifier: Modifier = Modifier, caption: String? = null) {
    val fill = if (tone == StoryTone.Idle) SimColors.Tint else tone.color().copy(alpha = 0.2f)
    val ink = if (tone == StoryTone.Idle) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else tone.ink()
    Column(
        modifier = modifier.height(if (caption == null) 52.dp else 50.dp).background(fill, RoundedCornerShape(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        if (caption != null) Text(caption, fontSize = 11.sp, color = ink.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun CountView(scene: CountScene) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CardLabel("Document to classify")
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nbDoc.forEachIndexed { i, w -> WordTile(w, scene.tokens[i], Modifier.weight(1f)) }
        }
        CountBars("Counts · sports · ${sportsCounts.sum()} tokens", sportsCounts, BlueClass, scene.current, Modifier.padding(top = 14.dp))
        CountBars("Counts · politics · ${politicsCounts.sum()} tokens", politicsCounts, PinkClass, scene.current, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun CountBars(title: String, counts: List<Int>, color: Color, current: Int?, modifier: Modifier) {
    val top = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(modifier = modifier.fillMaxWidth()) {
        CardLabel(title)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            counts.forEachIndexed { i, n ->
                val on = i == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) SimColors.Active.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$n", fontFamily = IBMPlexMono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (n > 0) {
                            Box(Modifier.width(30.dp).height((40f * n / top).dp).background(color, RoundedCornerShape(4.dp)))
                        } else {
                            Box(Modifier.width(30.dp).height(2.dp).background(color.copy(alpha = 0.45f), RoundedCornerShape(1.dp)))
                        }
                    }
                    Text(
                        nbVocab[i],
                        fontSize = 12.sp,
                        color = if (on) StoryTone.Active.ink() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresenceView(scene: PresenceScene) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        CardLabel("Document · ${nbDoc.joinToString(" ")}")
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nbVocab.indices.filter { bernPresent[it] }.forEach { WordTile(nbVocab[it], StoryTone.Done, Modifier.weight(1f), caption = "present") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 10.dp, end = 10.dp)) {
            listOf("term" to 1.1f, "in doc" to 1.3f, "× sports" to 1f, "× politics" to 1f).forEachIndexed { i, (h, w) ->
                Text(h, fontSize = 13.sp, color = muted, textAlign = if (i >= 2) TextAlign.End else TextAlign.Start, modifier = Modifier.weight(w))
            }
        }
        nbVocab.indices.forEach { k ->
            val on = k == scene.current
            val done = k < scene.scored
            val ink = if (on) StoryTone.Active.ink() else MaterialTheme.colorScheme.onSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) SimColors.Active.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    nbVocab[k],
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done || on) ink else muted,
                    modifier = Modifier.weight(1.1f),
                )
                Text(
                    if (bernPresent[k]) "present" else "absent",
                    fontFamily = IBMPlexMono,
                    fontSize = 14.sp,
                    color = if (bernPresent[k]) StoryTone.Done.ink() else muted,
                    modifier = Modifier.weight(1.3f),
                )
                listOf(0, 1).forEach { c ->
                    Text(
                        if (done) bx(bernFactor(k, c)) else "–",
                        fontFamily = IBMPlexMono,
                        fontSize = 14.sp,
                        color = if (done) ink else muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplementView(scene: ComplementScene) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CardLabel("Document · true label sports")
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            compDoc.forEach { WordTile(it, StoryTone.Done, Modifier.weight(1f)) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreCard("Multinomial", "highest fit wins", if (scene.multinomial) compMultinomial else null, pickMax = true, Modifier.weight(1f))
            ScoreCard("Complement", "worst fit to the rest wins", if (scene.complement) compComplement else null, pickMax = false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreCard(title: String, subtitle: String, scores: List<Double>?, pickMax: Boolean, modifier: Modifier) {
    val names = listOf("sports", "politics")
    val winner = scores?.let { s -> if ((s[0] > s[1]) == pickMax) 0 else 1 }
    val right = winner == 0
    val tone = if (right) StoryTone.Done else StoryTone.Warn
    Column(
        modifier = modifier
            .background(SimColors.Tint, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        names.forEachIndexed { i, name ->
            val on = i == winner
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    name,
                    fontFamily = IBMPlexMono,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) tone.ink() else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    scores?.let { bx(it[i]) } ?: "–",
                    fontFamily = IBMPlexMono,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) tone.ink() else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            if (winner == null) " " else "→ ${names[winner]} ${if (right) "✓" else "✗"}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = tone.ink(),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TableView(scene: TableScene) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        CardLabel("Row to classify")
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            catFeatures.forEachIndexed { i, f -> WordTile(f.value, scene.tones[i], Modifier.weight(1f), caption = f.name) }
        }
        val f = scene.feature?.let { catFeatures[it] } ?: return@Column
        CardLabel("${f.name} count table", Modifier.padding(top = 14.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 10.dp, end = 10.dp)) {
            Box(Modifier.weight(1f))
            Text("play = yes (9)", fontSize = 13.sp, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
            Text("play = no (5)", fontSize = 13.sp, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
        }
        f.levels.forEachIndexed { i, level ->
            val on = i == scene.highlight
            val ink = if (on) StoryTone.Active.ink() else MaterialTheme.colorScheme.onSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) SimColors.Active.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(level, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (on) ink else ink.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                Text("${f.yes[i]}", fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = ink, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                Text("${f.no[i]}", fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = ink, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun NetView(scene: BnScene) {
    val measurer = rememberTextMeasurer()
    val dark = LocalDarkTheme.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nodeFill = if (dark) Color(0xFF2A2F3A) else Color(0xFFF1F2F6)
    val answer = StoryTone.Answer.ink()
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        val w = 104.dp.toPx()
        val h = 40.dp.toPx()
        val centres = listOf(
            Offset(size.width * 0.5f, size.height * 0.17f),
            Offset(size.width * 0.24f, size.height * 0.5f),
            Offset(size.width * 0.76f, size.height * 0.5f),
            Offset(size.width * 0.5f, size.height * 0.83f),
        )
        // Arrows end on the target box's edge, so the heads stay visible.
        fun edge(from: Offset, to: Offset): Pair<Offset, Offset> {
            val d = to - from
            val len = sqrt(d.x * d.x + d.y * d.y)
            val u = Offset(d.x / len, d.y / len)
            fun inset(c: Offset, sign: Float): Offset {
                val tx = if (abs(u.x) < 1e-3f) Float.MAX_VALUE else (w / 2) / abs(u.x)
                val ty = if (abs(u.y) < 1e-3f) Float.MAX_VALUE else (h / 2) / abs(u.y)
                val t = min(tx, ty) + 4.dp.toPx()
                return c + u * (t * sign)
            }
            return inset(from, 1f) to inset(to, -1f)
        }
        listOf(0 to 1, 0 to 2, 1 to 3, 2 to 3).forEach { (a, b) ->
            val (s, e) = edge(centres[a], centres[b])
            drawLine(muted, s, e, strokeWidth = 1.5.dp.toPx())
            val d = e - s
            val len = sqrt(d.x * d.x + d.y * d.y)
            val u = Offset(d.x / len, d.y / len)
            val n = Offset(-u.y, u.x)
            val head = 8.dp.toPx()
            val path = Path().apply {
                moveTo(e.x, e.y)
                lineTo(e.x - u.x * head + n.x * head * 0.5f, e.y - u.y * head + n.y * head * 0.5f)
                lineTo(e.x - u.x * head - n.x * head * 0.5f, e.y - u.y * head - n.y * head * 0.5f)
                close()
            }
            drawPath(path, muted)
        }
        val nameStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
        centres.forEachIndexed { i, c ->
            val (fill, stroke) = when (scene.states[i]) {
                BnState.Observed -> SimColors.Blue.copy(alpha = 0.25f) to SimColors.Blue
                BnState.Question -> SimColors.Active.copy(alpha = 0.18f) to SimColors.Active
                BnState.Idle -> nodeFill to muted.copy(alpha = 0.6f)
            }
            val topLeft = Offset(c.x - w / 2, c.y - h / 2)
            drawRoundRect(fill, topLeft, Size(w, h), CornerRadius(h / 2))
            drawRoundRect(stroke, topLeft, Size(w, h), CornerRadius(h / 2), style = Stroke(width = 2.dp.toPx()))
            label(measurer, netNames[i], nameStyle, c)
            // The value pill: right of Cloudy and WetGrass, under Sprinkler and Rain.
            val pillText = scene.pills[i]
            val pillStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (scene.pillTones[i] == StoryTone.Answer) answer else onSurface.copy(alpha = 0.85f))
            val layout = measurer.measure(pillText, pillStyle)
            val pw = layout.size.width + 16.dp.toPx()
            val ph = 24.dp.toPx()
            val pc = if (i == 0 || i == 3) Offset(c.x + w / 2 + 10.dp.toPx() + pw / 2, c.y) else Offset(c.x + if (i == 1) -w * 0.1f else w * 0.1f, c.y + h / 2 + 6.dp.toPx() + ph / 2)
            drawRoundRect(
                if (scene.pillTones[i] == StoryTone.Answer) SimColors.Answer.copy(alpha = 0.22f) else SimColors.Tint,
                Offset(pc.x - pw / 2, pc.y - ph / 2),
                Size(pw, ph),
                CornerRadius(6.dp.toPx()),
            )
            drawText(layout, topLeft = Offset(pc.x - layout.size.width / 2f, pc.y - layout.size.height / 2f))
        }
    }
}

@Composable
private fun ChainView(scene: ChainScene) {
    val run = mcmcRun
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).stage()) {
        val xLo = -2.5
        val xHi = 2.5
        val yLo = -1.9
        val yHi = 2.1
        fun at(x: Double, y: Double) = Offset(((x - xLo) / (xHi - xLo) * size.width).toFloat(), (size.height - (y - yLo) / (yHi - yLo) * size.height).toFloat())
        // The target density as a field of dots, each sized by p there.
        val cols = 40
        val rows = 26
        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val x = xLo + (xHi - xLo) * (i + 0.5) / cols
                val y = yLo + (yHi - yLo) * (j + 0.5) / rows
                val p = crescent(x, y)
                if (p > 0.05) drawCircle(grey.copy(alpha = 0.55f), (0.6f + 1.6f * p.toFloat()).dp.toPx(), at(x, y))
            }
        }
        val steps = run.take(scene.upTo)
        val blue = SimColors.Blue
        if (scene.dotsOnly) {
            // Each step's sample: where the chain is after it, the proposal if taken, else where it was.
            steps.forEach { s -> drawCircle(blue.copy(alpha = 0.55f), 2.5.dp.toPx(), if (s.accepted) at(s.toX, s.toY) else at(s.fromX, s.fromY)) }
            return@Canvas
        }
        steps.filter { !it.accepted }.forEach { drawCircle(grey, 4.dp.toPx(), at(it.toX, it.toY), style = Stroke(width = 1.2.dp.toPx())) }
        val path = Path()
        val start = at(run[0].fromX, run[0].fromY)
        path.moveTo(start.x, start.y)
        steps.filter { it.accepted }.forEach { val o = at(it.toX, it.toY); path.lineTo(o.x, o.y) }
        drawPath(path, blue.copy(alpha = 0.8f), style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(blue, 3.dp.toPx(), start)
        steps.filter { it.accepted }.forEach { drawCircle(blue, 3.dp.toPx(), at(it.toX, it.toY)) }
        val current = steps.lastOrNull { it.accepted }?.let { at(it.toX, it.toY) } ?: start
        drawCircle(blue, 6.dp.toPx(), current)
        scene.proposal?.let { i ->
            val p = at(run[i].toX, run[i].toY)
            drawLine(SimColors.Active, current, p, strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            drawCircle(SimColors.Active, 6.dp.toPx(), p)
        }
    }
}
