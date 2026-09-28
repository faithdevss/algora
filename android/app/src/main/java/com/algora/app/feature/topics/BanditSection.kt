package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// ── Multi-armed bandit ───────────────────────────────────────────────────────
// The exploration-strategy topics all answer the same question — which arm do I pull next, given
// what I know so far — so one bandit run covers them, with the arm-selection rule as the config.
// Four arms with fixed hidden win rates, a fixed seed, 200 pulls; a frame every few pulls records
// each arm's estimate, pull count, and which arm was chosen and why.

private val ARM_NAMES = listOf("A", "B", "C", "D")
private val TRUE_RATES = listOf(0.30f, 0.55f, 0.45f, 0.72f) // D is best; C is a plausible decoy
private const val BEST_ARM = 3
private const val PULLS = 200

private class BanditFrame(
    val estimates: List<Float>,
    val counts: List<Int>,
    val chosen: Int,
    val pull: Int,
    val optimalPulls: Int,
    // Which of the config's modes chose this pull; null on the closing summary.
    val mode: Int?,
    val reward: Int?,
    val headline: String,
    val detail: String,
) {
    val caption: String get() = "$headline $detail"
}

/** One pull's decision: the arm, which of the config's modes chose it, and the sentence saying so. */
private class Choice(val arm: Int, val mode: Int, val headline: String)

private class BanditConfig(
    val intro: String,
    // The decision modes shown in the header strip ("Explore 10%", "Exploit 90%"). With more than
    // one, mode 0 is the exploring one and its pulls are ticked on the scrub track as [markLabel].
    val modes: List<String>,
    val markLabel: String,
    // The strategy's parameter as a readout chip ("ε = 0.10").
    val param: String,
    // What, for this strategy, can still rescue a best arm that currently looks worse.
    val correction: String,
    val choose: (List<Float>, List<Int>, Int, Random) -> Choice,
)

private val ChosenArm = SimColors.Active
private val BestArm = SimColors.Green
private val EstimateFill = SimColors.Blue

private fun runBandit(config: BanditConfig): List<BanditFrame> {
    val random = Random(11) // fixed seed so the narrative in the status line is reproducible
    val estimates = MutableList(TRUE_RATES.size) { 0f }
    val counts = MutableList(TRUE_RATES.size) { 0 }
    val frames = mutableListOf<BanditFrame>()
    var optimal = 0
    val best = ARM_NAMES[BEST_ARM]

    for (pull in 1..PULLS) {
        val choice = config.choose(estimates, counts, pull, random)
        val arm = choice.arm
        val reward = if (random.nextFloat() < TRUE_RATES[arm]) 1f else 0f
        counts[arm]++
        // Incremental sample mean — no need to keep the reward history.
        estimates[arm] += (reward - estimates[arm]) / counts[arm]
        if (arm == BEST_ARM) optimal++

        // Dense frames early (where the strategies differ most), sparse later.
        if (pull <= 12 || pull % 10 == 0) {
            val leader = estimates.indices.maxBy { estimates[it] }
            val detail = when {
                arm == BEST_ARM -> "$best is the truly best arm, with a true win rate of ${TRUE_RATES[BEST_ARM]}."
                counts[BEST_ARM] == 0 -> "$best is truly best but hasn't been tried yet."
                estimates[BEST_ARM] < estimates[leader] ->
                    "$best is truly best but looked worse after ${counts[BEST_ARM]} " +
                        "pull${if (counts[BEST_ARM] == 1) "" else "s"}. ${config.correction}"
                else -> "$best is truly best, and its estimate already leads."
            }
            frames.add(
                BanditFrame(
                    estimates.toList(), counts.toList(), arm, pull, optimal,
                    mode = choice.mode,
                    reward = reward.toInt(),
                    headline = choice.headline,
                    detail = detail,
                ),
            )
        }
    }

    frames.add(
        BanditFrame(
            estimates.toList(), counts.toList(), BEST_ARM, PULLS, optimal,
            mode = null,
            reward = null,
            headline = "After $PULLS pulls, ${optimal * 100 / PULLS}% went to $best, the truly best arm.",
            detail = "Its true win rate is ${TRUE_RATES[BEST_ARM]}. The gap from 100% is the cost of finding out.",
        ),
    )
    return frames
}

private fun name(arm: Int) = ARM_NAMES[arm]

private val banditConfigs = mapOf(
    "epsilon_greedy" to BanditConfig(
        intro = "Four arms with hidden win rates. ε-greedy plays the current best arm 90% of the time and picks at random the other 10% — simple, and it never stops exploring.",
        modes = listOf("Explore 10%", "Exploit 90%"),
        markLabel = "explore",
        param = "ε = 0.10",
        correction = "Only the 10% of random pulls can correct that.",
        choose = { estimates, counts, _, random ->
            when {
                counts.any { it == 0 } -> counts.indexOfFirst { it == 0 }.let { Choice(it, 0, "Explore: ${name(it)} is untried, so try it once.") }
                random.nextFloat() < 0.10f -> random.nextInt(estimates.size).let { Choice(it, 0, "Explore: a random pull lands on ${name(it)}.") }
                else -> estimates.indices.maxByOrNull { estimates[it] }!!.let {
                    Choice(it, 1, "Exploit: ${name(it)} has the highest estimate, so pull it again.")
                }
            }
        },
    ),
    "ucb" to BanditConfig(
        intro = "UCB adds a confidence bonus that shrinks as an arm is pulled more, so a rarely-tried arm stays attractive until its estimate is trustworthy. No randomness at all.",
        modes = listOf("Untried", "Highest bound"),
        markLabel = "untried",
        param = "bonus = √(2·ln t / n)",
        correction = "Its confidence bonus keeps growing until it gets another look.",
        choose = { estimates, counts, pull, _ ->
            val untried = counts.indexOfFirst { it == 0 }
            if (untried >= 0) {
                Choice(untried, 0, "${name(untried)} is untried, so its upper bound is infinite.")
            } else {
                val scores = estimates.indices.map { estimates[it] + sqrt(2f * ln(pull.toFloat()) / counts[it]) }
                val best = scores.indices.maxByOrNull { scores[it] }!!
                Choice(best, 1, "${name(best)} has the highest upper bound, ${"%.2f".format(scores[best])}.")
            }
        },
    ),
    "thompson_sampling" to BanditConfig(
        intro = "Thompson sampling keeps a belief distribution per arm and plays whichever arm wins a random draw from those beliefs. Uncertain arms sample widely; settled arms barely move.",
        modes = listOf("Posterior draw"),
        markLabel = "",
        param = "belief = Beta(w+1, l+1)",
        correction = "Its belief is still wide, so it keeps winning a draw now and then.",
        choose = { estimates, counts, _, random ->
            // Approximate the Beta posterior with a normal of matching spread — enough to show the
            // behaviour (wide early, tight once an arm is well sampled) without a Beta sampler.
            val samples = estimates.indices.map { i ->
                val n = counts[i]
                val spread = if (n == 0) 1f else 1f / sqrt(n.toFloat())
                estimates[i] + (random.nextFloat() - 0.5f) * 2f * spread
            }
            val best = samples.indices.maxByOrNull { samples[it] }!!
            Choice(best, 0, "${name(best)} won the posterior draw at ${"%.2f".format(samples[best])}.")
        },
    ),
    "boltzmann_exploration" to BanditConfig(
        intro = "Boltzmann (softmax) exploration turns estimates into pull probabilities, so a clearly worse arm is picked rarely rather than as often as any other — unlike ε-greedy's uniform random.",
        modes = listOf("Untried", "Softmax draw"),
        markLabel = "untried",
        param = "τ = 0.15",
        correction = "It still gets pulled in proportion to exp(estimate / τ).",
        choose = { estimates, counts, _, random ->
            val untried = counts.indexOfFirst { it == 0 }
            if (untried >= 0) {
                Choice(untried, 0, "${name(untried)} is untried, so try it once.")
            } else {
                val tau = 0.15f
                val weights = estimates.map { exp(it / tau) }
                val total = weights.sum()
                var roll = random.nextFloat() * total
                var pick = 0
                for (i in weights.indices) {
                    roll -= weights[i]
                    if (roll <= 0f) { pick = i; break }
                }
                Choice(pick, 1, "${name(pick)} was drawn with probability ${"%.0f".format(weights[pick] / total * 100)}%.")
            }
        },
    ),
    // The problem's own baseline: no learning at all, ever. This is the other bad extreme from
    // `exploration_exploitation`'s pure greedy — that one stops exploring after one sample each and
    // gets stuck; this one never stops exploring and so never commits to what it has already
    // learned. Together the two bracket why the strategies in between (epsilon-greedy, UCB, Thompson
    // sampling) exist at all.
    "multi_armed_bandit" to BanditConfig(
        intro = "The problem itself: four arms, hidden win rates, and a pull budget. This baseline picks uniformly " +
            "at random every single time -- it never uses an estimate at all, which is what every strategy in the " +
            "Exploration Strategies category improves on.",
        modes = listOf("Random pick"),
        markLabel = "",
        param = "policy = random",
        correction = "A random policy never uses its estimates, so it never gets better.",
        choose = { _, _, _, random -> random.nextInt(TRUE_RATES.size).let { Choice(it, 0, "Random pick: the pull lands on ${name(it)}.") } },
    ),
    // Pure greedy — deliberately the broken strategy. Each arm is sampled once, then the agent
    // commits forever to whichever looked best. When a good arm's single sample happens to lose,
    // its estimate never gets another chance to be corrected, and the counts show the agent
    // pouring every remaining pull into an arm it has no evidence is best. That silent lock-in is
    // the whole argument for exploring on purpose.
    "exploration_exploitation" to BanditConfig(
        intro = "Pure exploitation, so you can watch it fail. Every arm is tried exactly once, then the agent always plays its current best estimate — and stops collecting the evidence that would tell it otherwise.",
        modes = listOf("Try once", "Exploit"),
        markLabel = "untried",
        param = "ε = 0",
        correction = "Greedy will never pull it again to find out.",
        choose = { estimates, counts, _, _ ->
            val untried = counts.indexOfFirst { it == 0 }
            if (untried >= 0) {
                Choice(untried, 0, "${name(untried)} is untried, the only exploration greedy ever does.")
            } else {
                val best = estimates.indices.maxByOrNull { estimates[it] }!!
                Choice(best, 1, "Exploit: ${name(best)} has the highest estimate, ${"%.2f".format(estimates[best])}.")
            }
        },
    ),
)

private fun banditConfigFor(topicId: String): BanditConfig =
    banditConfigs[topicId] ?: banditConfigs.getValue("epsilon_greedy")

/** Final optimal-arm pull rate for a config, over the fixed `PULLS`-pull run -- for tests and copy. */
internal fun banditOptimalRate(topicId: String): Float {
    val frames = runBandit(banditConfigFor(topicId))
    val last = frames.last()
    return last.optimalPulls.toFloat() / last.pull
}

// ── UI ───────────────────────────────────────────────────────────────────────
// Player layout: a stage card (mode strip, one bar per arm, legend), readout chips, narration, then
// the transport — pinned in thumb reach when docked, with exploring pulls ticked on its track.

@Composable
fun BanditSection(topicId: String) {
    val config = remember(topicId) { banditConfigFor(topicId) }
    val frames = remember(config) { runBandit(config) }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 700f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val marks = remember(frames) {
        if (config.modes.size > 1) TrackMarks(frames.indices.filter { frames[it].mode == 0 }.toSet(), config.markLabel) else null
    }
    val dock = LocalLabDock.current

    LabIntro(config.intro)
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ModeStrip(config.modes, frame.mode)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TRUE_RATES.indices.forEach { arm -> ArmRow(arm, frame) }
                }
                BanditLegend(Modifier.padding(top = 16.dp))
                if (dock == null) {
                    BanditReadout(config, frame, Modifier.padding(top = 16.dp))
                    BanditNarration(frame, Modifier.padding(top = 14.dp))
                }
                PlaybackTransport(
                    playback,
                    captions = frames.map { it.caption },
                    stepLabel = { "Pull ${frames[it].pull} of $PULLS" },
                    marks = marks,
                )
            }
        }
        if (dock != null) {
            BanditReadout(config, frame, Modifier.padding(top = 14.dp))
            BanditNarration(frame, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }
}

/** The strategy's decision modes, read-only, with the one that chose this pull lit. */
@Composable
private fun ModeStrip(modes: List<String>, active: Int?) {
    val dark = LocalDarkTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.background(SimColors.Tint, RoundedCornerShape(9.dp)).padding(2.dp)) {
            modes.forEachIndexed { i, label ->
                val on = i == active
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .background(if (on) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent, RoundedCornerShape(7.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f))
        Text("estimated value", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ArmRow(arm: Int, frame: BanditFrame) {
    val chosen = arm == frame.chosen
    val estimate = frame.estimates[arm]
    val shape = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            ARM_NAMES[arm],
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (chosen) ChosenArm else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(30.dp),
        )
        // Bar length is the estimated win rate; the tick is the arm's hidden true rate.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(shape)
                .background(SimColors.Tint, shape)
                .then(if (chosen) Modifier.border(2.dp, ChosenArm, shape) else Modifier),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(estimate.coerceIn(0f, 1f))
                    .height(44.dp)
                    .background(if (chosen) ChosenArm else EstimateFill.copy(alpha = 0.6f)),
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * TRUE_RATES[arm] - 1.dp)
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(26.dp)
                    .background(if (chosen) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
            )
            Text(
                "%.2f".format(estimate),
                fontFamily = IBMPlexMono,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = if (chosen) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            )
        }
        Column(modifier = Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("${frame.counts[arm]}") }
                    append(if (frame.counts[arm] == 1) " pull" else " pulls")
                },
                fontFamily = IBMPlexMono,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (arm == BEST_ARM) {
                Text("TRULY BEST", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = BestArm)
            }
        }
    }
}

@Composable
private fun BanditLegend(modifier: Modifier) {
    val label = @Composable { text: String ->
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp))
    }
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(ChosenArm, RoundedCornerShape(3.dp)))
            label("Pulled now")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(EstimateFill, RoundedCornerShape(3.dp)))
            label("Estimate")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(2.dp).height(13.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)))
            label("True mean")
        }
    }
}

@Composable
private fun BanditReadout(config: BanditConfig, frame: BanditFrame, modifier: Modifier) {
    val parts = buildList<Pair<String?, String>> {
        addAll(readoutParts(config.param))
        frame.reward?.let { add("reward" to if (it > 0) "+1" else "0") }
        add("best arm" to "${frame.optimalPulls} / ${frame.pull}")
    }
    ReadoutChips(parts, modifier)
}

@Composable
private fun BanditNarration(frame: BanditFrame, modifier: Modifier) {
    // The pulled arm is named in yellow wherever the headline mentions it.
    val arm = ARM_NAMES[frame.chosen]
    val headline = buildAnnotatedString {
        var last = 0
        Regex("\\b$arm\\b").findAll(frame.headline).forEach { m ->
            append(frame.headline.substring(last, m.range.first))
            withStyle(SpanStyle(color = ChosenArm)) { append(m.value) }
            last = m.range.last + 1
        }
        append(frame.headline.substring(last))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            frame.detail,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
