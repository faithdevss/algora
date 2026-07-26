package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val status: String,
)

private class BanditConfig(
    val intro: String,
    val extraLabel: String,
    // Given estimates, counts and the pull index, which arm to play and a one-line reason.
    val choose: (List<Float>, List<Int>, Int, Random) -> Pair<Int, String>,
)

private val ChosenArm = Color(0xFFFACC15)
private val BestArm = SimColors.Green
private val OtherArm = Color(0xFF3B82F6)

private fun runBandit(config: BanditConfig): List<BanditFrame> {
    val random = Random(11) // fixed seed so the narrative in the status line is reproducible
    val estimates = MutableList(TRUE_RATES.size) { 0f }
    val counts = MutableList(TRUE_RATES.size) { 0 }
    val frames = mutableListOf<BanditFrame>()
    var optimal = 0

    for (pull in 1..PULLS) {
        val (arm, reason) = config.choose(estimates, counts, pull, random)
        val reward = if (random.nextFloat() < TRUE_RATES[arm]) 1f else 0f
        counts[arm]++
        // Incremental sample mean — no need to keep the reward history.
        estimates[arm] += (reward - estimates[arm]) / counts[arm]
        if (arm == BEST_ARM) optimal++

        // Dense frames early (where the strategies differ most), sparse later.
        if (pull <= 12 || pull % 10 == 0) {
            frames.add(
                BanditFrame(
                    estimates.toList(), counts.toList(), arm, pull, optimal,
                    "Pull $pull: arm ${ARM_NAMES[arm]} — $reason. Reward ${reward.toInt()}.",
                ),
            )
        }
    }

    frames.add(
        BanditFrame(
            estimates.toList(), counts.toList(), BEST_ARM, PULLS, optimal,
            "After $PULLS pulls: ${optimal * 100 / PULLS}% went to the best arm (D, true rate 0.72). The gap from 100% is the cost of finding out.",
        ),
    )
    return frames
}

private val banditConfigs = mapOf(
    "epsilon_greedy" to BanditConfig(
        intro = "Four arms with hidden win rates. ε-greedy plays the current best arm 90% of the time and picks at random the other 10% — simple, and it never stops exploring.",
        extraLabel = "ε = 0.10",
        choose = { estimates, counts, _, random ->
            when {
                counts.any { it == 0 } -> counts.indexOfFirst { it == 0 } to "untried, so try it once"
                random.nextFloat() < 0.10f -> random.nextInt(estimates.size) to "random (exploring)"
                else -> estimates.indices.maxByOrNull { estimates[it] }!! to "current best estimate (exploiting)"
            }
        },
    ),
    "ucb" to BanditConfig(
        intro = "UCB adds a confidence bonus that shrinks as an arm is pulled more, so a rarely-tried arm stays attractive until its estimate is trustworthy. No randomness at all.",
        extraLabel = "estimate + √(2·ln t / n)",
        choose = { estimates, counts, pull, _ ->
            val untried = counts.indexOfFirst { it == 0 }
            if (untried >= 0) {
                untried to "untried, so its bound is infinite"
            } else {
                val scores = estimates.indices.map { estimates[it] + sqrt(2f * ln(pull.toFloat()) / counts[it]) }
                val best = scores.indices.maxByOrNull { scores[it] }!!
                best to "highest upper bound (${"%.2f".format(scores[best])})"
            }
        },
    ),
    "thompson_sampling" to BanditConfig(
        intro = "Thompson sampling keeps a belief distribution per arm and plays whichever arm wins a random draw from those beliefs. Uncertain arms sample widely; settled arms barely move.",
        extraLabel = "sampled from Beta(wins+1, losses+1)",
        choose = { estimates, counts, _, random ->
            // Approximate the Beta posterior with a normal of matching spread — enough to show the
            // behaviour (wide early, tight once an arm is well sampled) without a Beta sampler.
            val samples = estimates.indices.map { i ->
                val n = counts[i]
                val spread = if (n == 0) 1f else 1f / sqrt(n.toFloat())
                estimates[i] + (random.nextFloat() - 0.5f) * 2f * spread
            }
            val best = samples.indices.maxByOrNull { samples[it] }!!
            best to "won the posterior draw (${"%.2f".format(samples[best])})"
        },
    ),
    "boltzmann_exploration" to BanditConfig(
        intro = "Boltzmann (softmax) exploration turns estimates into pull probabilities, so a clearly worse arm is picked rarely rather than as often as any other — unlike ε-greedy's uniform random.",
        extraLabel = "P(arm) ∝ exp(estimate / τ), τ = 0.15",
        choose = { estimates, counts, _, random ->
            val untried = counts.indexOfFirst { it == 0 }
            if (untried >= 0) {
                untried to "untried, so try it once"
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
                pick to "sampled with probability ${"%.0f".format(weights[pick] / total * 100)}%"
            }
        },
    ),
)

private fun banditConfigFor(topicId: String): BanditConfig =
    banditConfigs[topicId] ?: banditConfigs.getValue("epsilon_greedy")

@Composable
fun BanditSection(topicId: String) {
    val config = remember(topicId) { banditConfigFor(topicId) }
    val frames = remember(config) { runBandit(config) }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 350f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                config.extraLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TRUE_RATES.indices.forEach { arm ->
                    ArmRow(
                        name = ARM_NAMES[arm],
                        estimate = frame.estimates[arm],
                        count = frame.counts[arm],
                        totalPulls = frame.pull,
                        color = when {
                            arm == frame.chosen -> ChosenArm
                            arm == BEST_ARM -> BestArm
                            else -> OtherArm
                        },
                    )
                }
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Optimal-arm pulls: ${frame.optimalPulls} / ${frame.pull}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BanditLegend(ChosenArm, "Pulled now")
                BanditLegend(BestArm, "Truly best arm")
                BanditLegend(OtherArm, "Other arms")
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun BanditLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun ArmRow(name: String, estimate: Float, count: Int, totalPulls: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(22.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Bar length is the estimated win rate; the trailing text is how often the arm was played.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(7.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(estimate.coerceIn(0f, 1f))
                    .height(26.dp)
                    .background(color, RoundedCornerShape(7.dp)),
            )
            Text(
                "%.2f".format(estimate),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            )
        }
        Box(modifier = Modifier.width(70.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                "$count pulls",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
