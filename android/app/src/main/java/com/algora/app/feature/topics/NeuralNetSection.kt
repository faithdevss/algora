package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Neural network player ────────────────────────────────────────────────────
// The Deep Learning labs. Four render parts, mixed per frame:
//
//   net     — a layered node diagram, each node showing its current value
//   curves  — labelled function plots on shared axes (activations, loss over steps, trajectories)
//   grids   — small matrices with a highlighted window (convolution, pooling)
//   bars    — a labelled vector (gates, latents, gradients)
//
// Every number is computed here: the forward pass, the backward deltas, the three optimizers, the
// convolution, and the linear autoencoder's reconstruction error are all run for real on small
// fixed inputs, so a frame's narration cannot drift from what it draws.

private enum class NodeMood { IDLE, FORWARD, BACKWARD, OUTPUT }

private class NetNode(val value: Float, val mood: NodeMood = NodeMood.IDLE)

private class NetLayer(val label: String, val nodes: List<NetNode>)

private class Curve(val label: String, val points: List<Pair<Float, Float>>, val color: Color)

private class CurvePlot(
    val label: String,
    val curves: List<Curve>,
    val xRange: ClosedFloatingPointRange<Float>,
    val yRange: ClosedFloatingPointRange<Float>,
)

private class GridView(val label: String, val values: List<List<Float>>, val highlight: Set<Pair<Int, Int>> = emptySet())

private class NetBar(val label: String, val values: List<Float>, val color: Color, val captions: List<String> = emptyList())

private class NetFrame(
    val status: String,
    val layers: List<NetLayer> = emptyList(),
    val plot: CurvePlot? = null,
    val grids: List<GridView> = emptyList(),
    val bars: List<NetBar> = emptyList(),
    val readout: String? = null,
)

private class NetConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<NetFrame>,
)

private val ForwardColor = SimColors.Blue
private val BackwardColor = Color(0xFFEC4899)
private val OutputColor = Color(0xFF7C3AED)
private val NeutralColor = SimColors.Grey
private val AccentA = SimColors.Green
private val AccentB = Color(0xFFF97316)

private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

private fun relu(x: Float) = max(0f, x)

private val hiddenBias = listOf(0.1f, -0.2f, 0.05f)

private class Pass(
    val hiddenPre: List<Float>,
    val hidden: List<Float>,
    val outputPre: Float,
    val output: Float,
    val loss: Float,
)

// ── Neural network basics ────────────────────────────────────────────────────

// ── Backpropagation ──────────────────────────────────────────────────────────

// ── Activation functions ─────────────────────────────────────────────────────

private fun Float.pow(n: Int): Float {
    var result = 1f
    repeat(n) { result *= this }
    return result
}

// ── Gradient descent variants ────────────────────────────────────────────────

private class Optimizer(val name: String, val color: Color, val step: (List<Float>, List<Float>, Int) -> List<Float>)

// ── CNN ──────────────────────────────────────────────────────────────────────

// ── Autoencoder ──────────────────────────────────────────────────────────────

// ── GAN ──────────────────────────────────────────────────────────────────────

// ── RNN ──────────────────────────────────────────────────────────────────────

// ── LSTM / GRU ───────────────────────────────────────────────────────────────

// ── C5 · BPTT ────────────────────────────────────────────────────────────────
// Everything here comes out of `BpttLab`, which trains the same 12-unit recurrent classifier at six
// truncation windows on a task with one informative token nine steps before the decision. The lab
// ends on a comparison the plan did not expect to have to draw: a window that never reaches the cue
// solves the task on every seed, and two shorter ones are coin flips.

// ── Config ───────────────────────────────────────────────────────────────────

// ── Batch normalization ──────────────────────────────────────────────────────

// ── Dropout ──────────────────────────────────────────────────────────────────

// ── Transfer learning ────────────────────────────────────────────────────────

// ── Neural network basics (phase 9, batch C1) ────────────────────────────────
// Four labs on the same widget: a leaky integrate-and-fire neuron beside the artificial unit that
// abstracts it, a real MLP trained on XOR, and the two failure modes of depth. Everything is
// computed in DeepNetMath.kt by an actual forward and backward pass — the gradient decay in
// particular is measured rather than derived from a decay formula, and `DeepNetMathTest` pins the
// orderings the narration depends on.

// ── Activation functions (phase 9, batch C2) ─────────────────────────────────
// Ten labs sharing one shape: draw the function and its derivative, then measure the property that
// makes this activation worth having and the one that makes it fail. Everything comes from
// ActivationMath.kt, where each activation is defined once with its derivative and every claim is
// run through a real stack rather than quoted from a table.

// ── D4 · The feed-forward block ─────────────────────────────────────────────

// ── D4 · Scaling laws: GPT-3 and GPT-4 ──────────────────────────────────────

// ── D4 · LLaMA, Vicuna, and open weights ────────────────────────────────────

// ── D5 · Chain of thought and self-consistency ──────────────────────────────
// Both calculations are exact. The plurality vote is enumerated over the multinomial rather than
// sampled, which is what makes the case where voting *hurts* checkable rather than anecdotal.

// ── D5 · Hallucination mitigation ───────────────────────────────────────────

// ── Evaluation metrics (phase 9, batch B9) ───────────────────────────────────
// The six metric topics whose subject is a curve rather than a picture of the data: the two ranking
// summaries, the two probability/agreement corrections, and the two loss functions. All of them read
// MetricsMath.kt, and the classification four read the same score vector the PointCloudPlayer labs
// draw — so ROC AUC 0.969 and F1 0.569 are two readings of one model.

private fun rocCurveFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val roc = ScoredLab.rocCurve.map { it.first.toFloat() to it.second.toFloat() }
    val diagonal = listOf(0f to 0f, 1f to 1f)
    val half = ScoredLab.cellsAt(0.5)

    frames += NetFrame(
        status = "A ROC curve plots one point per threshold: true positive rate against false positive rate. At " +
            "t = 0.5 this model sits at FPR ${"%.3f".format(half.falsePositiveRate)}, TPR " +
            "${"%.3f".format(half.recall)} — one operating point out of the thousand the score vector allows.",
        plot = CurvePlot(
            "TPR against FPR",
            listOf(
                Curve("chance", diagonal, NeutralColor),
                Curve("this threshold", listOf(half.falsePositiveRate.toFloat() to half.recall.toFloat()), OutputColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "one threshold, one point",
    )

    frames += NetFrame(
        status = "Sweeping every threshold traces the curve. The diagonal is a model that has learned nothing — a " +
            "coin flip trades TPR for FPR one for one — so the distance above it is the whole signal. This curve " +
            "reaches TPR ${"%.2f".format(roc.first { it.first >= 0.1f }.second)} at 10% false positives.",
        plot = CurvePlot(
            "ROC",
            listOf(Curve("chance", diagonal, NeutralColor), Curve("model", roc, ForwardColor)),
            0f..1f, 0f..1f,
        ),
        readout = "${ScoredLab.rocCurve.size} operating points",
    )

    frames += NetFrame(
        status = "What makes it useful is what it does *not* depend on: the curve is a function of the ranking " +
            "only, so it does not move when the threshold moves and it does not move when the class balance " +
            "changes. That independence is also the trap — a curve this good on a 9% positive rate can coexist " +
            "with precision most teams would reject.",
        plot = CurvePlot(
            "ROC, with the 0.5 operating point marked",
            listOf(
                Curve("chance", diagonal, NeutralColor),
                Curve("model", roc, ForwardColor),
                Curve("t = 0.5", listOf(half.falsePositiveRate.toFloat() to half.recall.toFloat()), OutputColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "threshold-free by construction",
    )

    val pr = ScoredLab.prCurve.map { it.first.toFloat() to it.second.toFloat() }
    val atRecall = ScoredLab.prCurve.filter { it.first >= 0.8 }.maxByOrNull { it.second }
    frames += NetFrame(
        status = "Here is the same model's precision-recall curve, which does depend on the base rate. At 80% " +
            "recall the best precision available is ${"%.3f".format(atRecall?.second ?: 0.0)} — so finding four " +
            "fifths of the positives means ${"%.0f".format((1 - (atRecall?.second ?: 0.0)) * 100)}% of the flags " +
            "are wrong. The ROC curve above cannot show you that, because false positives are measured against " +
            "${ScoredLab.negatives} negatives rather than against the flags.",
        plot = CurvePlot(
            "precision against recall",
            listOf(
                Curve("base rate", listOf(0f to (ScoredLab.positives.toFloat() / ScoredLab.scored.size), 1f to (ScoredLab.positives.toFloat() / ScoredLab.scored.size)), NeutralColor),
                Curve("model", pr, AccentB),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "precision ${"%.3f".format(atRecall?.second ?: 0.0)} at recall 0.80",
    )

    frames += NetFrame(
        status = "So: use ROC to compare rankers and to choose an operating point when both classes matter, and " +
            "use precision-recall when the positive class is rare and the flags have a cost. On balanced data the " +
            "two tell the same story; on this data they do not, and the PR curve is the honest one.",
        plot = CurvePlot(
            "both curves, same model",
            listOf(Curve("ROC", roc, ForwardColor), Curve("PR", pr, AccentB), Curve("chance", diagonal, NeutralColor)),
            0f..1f, 0f..1f,
        ),
        readout = "same scores, two verdicts",
    )
    return frames
}

private fun aucFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val roc = ScoredLab.rocCurve.map { it.first.toFloat() to it.second.toFloat() }

    frames += NetFrame(
        status = "AUC is the area under that curve: ${"%.4f".format(ScoredLab.auc)} here, by the trapezoid rule " +
            "over ${ScoredLab.rocCurve.size} operating points. One number for a whole curve, and it needs no " +
            "threshold — which is why it is the default for comparing models before anyone has decided how the " +
            "model will be used.",
        plot = CurvePlot(
            "area under the ROC curve",
            listOf(Curve("chance = 0.5", listOf(0f to 0f, 1f to 1f), NeutralColor), Curve("model", roc, ForwardColor)),
            0f..1f, 0f..1f,
        ),
        readout = "AUC ${"%.4f".format(ScoredLab.auc)}",
    )

    frames += NetFrame(
        status = "It also has an exact probabilistic meaning, which is the more useful way to hold it: AUC is the " +
            "probability that a randomly chosen positive outranks a randomly chosen negative. Computed that way — " +
            "over all ${ScoredLab.positives} × ${ScoredLab.negatives} = " +
            "${ScoredLab.positives * ScoredLab.negatives} pairs — it comes to " +
            "${"%.4f".format(ScoredLab.aucByRanking)}, the same number the area gives.",
        bars = listOf(
            NetBar("AUC by area", listOf(ScoredLab.auc.toFloat()), ForwardColor, listOf("trapezoid")),
            NetBar("AUC by pair counting", listOf(ScoredLab.aucByRanking.toFloat()), AccentA, listOf("${ScoredLab.positives * ScoredLab.negatives} pairs")),
        ),
        readout = "two definitions, one number",
    )

    frames += NetFrame(
        status = "Being a ranking statistic is what AUC costs. Any monotone transform of the scores leaves the " +
            "ranking untouched, so it leaves AUC untouched to the digit — this lab pushes every score towards 0 or " +
            "1 and AUC stays ${"%.4f".format(ScoredLab.overconfidentAuc)} while log loss rises from " +
            "${"%.4f".format(ScoredLab.logLoss())} to ${"%.4f".format(ScoredLab.logLoss(ScoredLab.overconfident))}. " +
            "AUC cannot see calibration at all.",
        bars = listOf(
            NetBar("AUC", listOf(ScoredLab.auc.toFloat(), ScoredLab.overconfidentAuc.toFloat()), ForwardColor, listOf("original", "distorted")),
            NetBar("log loss", listOf(ScoredLab.logLoss().toFloat(), ScoredLab.logLoss(ScoredLab.overconfident).toFloat()), BackwardColor, listOf("original", "distorted")),
        ),
        readout = "identical AUC, ${"%.0f".format((ScoredLab.logLoss(ScoredLab.overconfident) / ScoredLab.logLoss() - 1) * 100)}% worse log loss",
    )

    frames += NetFrame(
        status = "And on an imbalanced problem AUC reads generously. ${"%.3f".format(ScoredLab.auc)} sounds close " +
            "to solved, while average precision — the same curve's PR counterpart — is " +
            "${"%.3f".format(ScoredLab.averagePrecision)} and precision at 80% recall is " +
            "${"%.3f".format(ScoredLab.prCurve.filter { it.first >= 0.8 }.maxOf { it.second })}. The false " +
            "positives are being divided by ${ScoredLab.negatives} negatives, which is a large denominator.",
        bars = listOf(
            NetBar(
                "three summaries of one model",
                listOf(ScoredLab.auc.toFloat(), ScoredLab.averagePrecision.toFloat(), ScoredLab.prCurve.filter { it.first >= 0.8 }.maxOf { it.second }.toFloat()),
                OutputColor,
                listOf("AUC", "avg precision", "P@R=0.8"),
            ),
        ),
        readout = "${"%.3f".format(ScoredLab.auc)} vs ${"%.3f".format(ScoredLab.averagePrecision)}",
    )

    frames += NetFrame(
        status = "So AUC answers exactly one question well — does this model rank better than that one — and three " +
            "questions badly: is it calibrated, how does it do at the operating point I will use, and is it good " +
            "enough on a rare class. Report it with average precision, and never ship a threshold chosen from it.",
        bars = listOf(
            NetBar("AUC answers", listOf(1f, 0f, 0f, 0f), AccentA, listOf("ranking", "calibration", "operating point", "rare class")),
        ),
        readout = "one question, well",
    )
    return frames
}

private fun logLossFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val loss = ScoredLab.logLoss()
    val error = ScoredLab.confidentError

    frames += NetFrame(
        status = "Log loss scores the probability, not the decision: −log p for a positive, −log(1 − p) for a " +
            "negative, averaged. On this model it is ${"%.4f".format(loss)}. Nothing is thresholded, so a " +
            "prediction of 0.51 and one of 0.99 are two different answers rather than the same one.",
        plot = CurvePlot(
            "−log p, the cost of a prediction on a positive case",
            listOf(Curve("−log p", (1..99).map { (it / 100f) to (-kotlin.math.ln(it / 100.0)).toFloat() }, BackwardColor)),
            0f..1f, 0f..5f,
        ),
        readout = "log loss ${"%.4f".format(loss)}",
    )

    frames += NetFrame(
        status = "The shape of that curve is the whole behaviour. It is unbounded as p → 0, so one confident " +
            "mistake can dominate an average over a thousand cases: the worst single prediction here contributes " +
            "${"%.3f".format(error.worstContribution)} against a mean contribution of " +
            "${"%.4f".format(error.meanContribution)} — ${"%.0f".format(error.worstContribution / error.meanContribution)}× " +
            "— from a case scored ${"%.3f".format(error.worstScore)} that was in fact positive.",
        bars = listOf(
            NetBar("contribution to the mean", listOf(error.meanContribution.toFloat(), error.worstContribution.toFloat()), BackwardColor, listOf("average case", "worst case")),
        ),
        readout = "${"%.0f".format(error.worstContribution / error.meanContribution)}× the average, from one case",
    )

    frames += NetFrame(
        status = "Which makes log loss the metric that notices calibration. Push this model's scores towards 0 and " +
            "1 by a monotone transform — the ranking is unchanged, so AUC stays at " +
            "${"%.4f".format(ScoredLab.overconfidentAuc)} exactly — and log loss goes " +
            "${"%.4f".format(loss)} → ${"%.4f".format(ScoredLab.logLoss(ScoredLab.overconfident))}. Overconfidence " +
            "is invisible to every ranking metric and expensive here.",
        bars = listOf(
            NetBar("log loss", listOf(loss.toFloat(), ScoredLab.logLoss(ScoredLab.overconfident).toFloat()), BackwardColor, listOf("original", "overconfident")),
            NetBar("AUC", listOf(ScoredLab.auc.toFloat(), ScoredLab.overconfidentAuc.toFloat()), NeutralColor, listOf("original", "overconfident")),
        ),
        readout = "the metric that can tell them apart",
    )

    val calibration = ScoredLab.calibration()
    frames += NetFrame(
        status = "It does not tell you *how* the probabilities are wrong, though — for that, bin them. This " +
            "model's upper bins are under-confident: cases it scores around " +
            "${"%.2f".format(calibration.first { it.lower >= 0.4 }.predicted)} come true " +
            "${"%.0f".format(calibration.first { it.lower >= 0.4 }.observed * 100)}% of the time. A reliability " +
            "curve above the diagonal means the model is too cautious, and log loss will punish that as surely as " +
            "it punishes overconfidence.",
        plot = CurvePlot(
            "observed rate against predicted probability",
            listOf(
                Curve("perfect", listOf(0f to 0f, 1f to 1f), NeutralColor),
                Curve("model", calibration.map { it.predicted.toFloat() to it.observed.toFloat() }, ForwardColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "reliability, in ten bins",
    )

    frames += NetFrame(
        status = "The gentler alternative is the Brier score, the mean squared error of the probability: " +
            "${"%.4f".format(ScoredLab.brier())} here, rising to " +
            "${"%.4f".format(ScoredLab.brier(ScoredLab.overconfident))} under the same distortion. It is bounded, " +
            "so one catastrophic prediction cannot swamp it — which makes it more robust and less sensitive, and " +
            "the choice between them is exactly that trade.",
        bars = listOf(
            NetBar("log loss", listOf(loss.toFloat(), ScoredLab.logLoss(ScoredLab.overconfident).toFloat()), BackwardColor, listOf("original", "overconfident")),
            NetBar("Brier", listOf(ScoredLab.brier().toFloat(), ScoredLab.brier(ScoredLab.overconfident).toFloat()), AccentA, listOf("original", "overconfident")),
        ),
        readout = "unbounded and sensitive, or bounded and blunt",
    )
    return frames
}

private fun kappaFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val half = ScoredLab.cellsAt(0.5)
    val majority = ScoredLab.cellsAt(0.999)

    frames += NetFrame(
        status = "Cohen's kappa asks what accuracy is worth after subtracting the agreement two raters would reach " +
            "by chance: κ = (observed − expected) / (1 − expected), where expected comes from the two sets of " +
            "marginals. On this matrix observed agreement is ${"%.3f".format(half.accuracy)} and kappa is " +
            "${"%.3f".format(half.kappa)}.",
        bars = listOf(
            NetBar("agreement", listOf(half.accuracy.toFloat(), half.kappa.toFloat()), ForwardColor, listOf("observed", "chance-corrected")),
        ),
        readout = "accuracy ${"%.3f".format(half.accuracy)} → κ ${"%.3f".format(half.kappa)}",
    )

    frames += NetFrame(
        status = "The correction bites hardest exactly where accuracy is most flattering. A model that predicts " +
            "\"negative\" for everything scores accuracy ${"%.3f".format(majority.accuracy)} on this data — and " +
            "kappa ${"%.3f".format(majority.kappa)}, because chance agreement between \"always negative\" and the " +
            "true labels is already ${"%.3f".format(majority.accuracy)}. Kappa reports what accuracy conceals: " +
            "nothing was learned.",
        bars = listOf(
            NetBar("predict-all-negative", listOf(majority.accuracy.toFloat(), majority.kappa.toFloat()), BackwardColor, listOf("accuracy", "kappa")),
            NetBar("the trained model", listOf(half.accuracy.toFloat(), half.kappa.toFloat()), ForwardColor, listOf("accuracy", "kappa")),
        ),
        readout = "accuracy ${"%.3f".format(majority.accuracy)}, κ ${"%.3f".format(majority.kappa)}",
    )

    val sweep = ScoredLab.thresholds.map { it to ScoredLab.cellsAt(it) }
    frames += NetFrame(
        status = "Across thresholds, kappa moves where accuracy does not. Accuracy spans " +
            "${"%.3f".format(sweep.minOf { it.second.accuracy })}–${"%.3f".format(sweep.maxOf { it.second.accuracy })} " +
            "while kappa spans ${"%.3f".format(sweep.minOf { it.second.kappa })}–${"%.3f".format(sweep.maxOf { it.second.kappa })}. " +
            "A metric with range is a metric you can optimise against.",
        plot = CurvePlot(
            "accuracy and kappa against threshold",
            listOf(
                Curve("accuracy", sweep.map { it.first.toFloat() to it.second.accuracy.toFloat() }, NeutralColor),
                Curve("kappa", sweep.map { it.first.toFloat() to it.second.kappa.toFloat() }, OutputColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "kappa has ${"%.1f".format((sweep.maxOf { it.second.kappa } - sweep.minOf { it.second.kappa }) / (sweep.maxOf { it.second.accuracy } - sweep.minOf { it.second.accuracy }))}× the range",
    )

    frames += NetFrame(
        status = "Its close relative is the Matthews correlation coefficient, which uses all four cells too and " +
            "reads ${"%.3f".format(half.matthews)} here against kappa's ${"%.3f".format(half.kappa)}. Both are " +
            "chance-corrected and both are symmetric in the classes; MCC is the one most commonly recommended for " +
            "imbalanced binary problems, and on this matrix they agree closely.",
        bars = listOf(
            NetBar("chance-corrected metrics", listOf(half.kappa.toFloat(), half.matthews.toFloat()), AccentA, listOf("Cohen's κ", "MCC")),
            NetBar("uncorrected", listOf(half.accuracy.toFloat(), half.f1.toFloat()), NeutralColor, listOf("accuracy", "F1")),
        ),
        readout = "κ ${"%.3f".format(half.kappa)}, MCC ${"%.3f".format(half.matthews)}",
    )

    frames += NetFrame(
        status = "One caution the interpretation tables never carry: κ depends on the marginals, so the same " +
            "quality of agreement scores differently at different base rates and two kappas from different " +
            "datasets are not comparable. Use it to compare models on one dataset, and never to claim a model is " +
            "\"substantially better than chance\" in the abstract.",
        bars = listOf(
            NetBar("what κ compares", listOf(1f, 0f), OutputColor, listOf("models on one dataset", "across datasets")),
        ),
        readout = "chance-corrected, not base-rate-free",
    )
    return frames
}

private fun giniImpurityFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val curve = ImpurityLab.impurityCurve()
    val xs = (0 until curve.size).map { it.toFloat() / (curve.size - 1) }

    frames += NetFrame(
        status = "Gini impurity is the probability that two items drawn from a node have different labels: " +
            "1 − Σpᵢ². It is 0 when a node is pure and ${"%.1f".format(ImpurityLab.giniMaximum)} at a 50/50 split " +
            "on a binary problem — which is its maximum, where entropy's is " +
            "${"%.1f".format(ImpurityLab.entropyMaximum)}.",
        plot = CurvePlot(
            "impurity against class balance",
            listOf(
                Curve("Gini", xs.zip(curve.map { it.first.toFloat() }), ForwardColor),
                Curve("entropy (bits)", xs.zip(curve.map { it.second.toFloat() }), AccentB),
                Curve("misclassification", xs.zip(curve.map { it.third.toFloat() }), NeutralColor),
            ),
            0f..1f, 0f..1.05f,
        ),
        readout = "Gini ≤ ${"%.1f".format(ImpurityLab.giniMaximum)}, entropy ≤ ${"%.1f".format(ImpurityLab.entropyMaximum)}",
    )

    frames += NetFrame(
        status = "A tree uses it as a *gain*: the parent's impurity minus the weighted impurity of the children. " +
            "On a parent of ${ImpurityLab.parentPositive + ImpurityLab.parentNegative} split ${ImpurityLab.parentPositive}/${ImpurityLab.parentNegative}, " +
            "four candidate splits score " +
            ImpurityLab.scores.joinToString(", ") { "${it.split.name.take(1)} ${"%.3f".format(it.giniGain)}" } +
            " — and the split that separates the classes completely wins with ${"%.3f".format(ImpurityLab.scores.maxOf { it.giniGain })}.",
        bars = listOf(
            NetBar("Gini gain", ImpurityLab.scores.map { it.giniGain.toFloat() }, ForwardColor, ImpurityLab.scores.map { it.split.name.take(1) }),
            NetBar("entropy gain", ImpurityLab.scores.map { it.entropyGain.toFloat() }, AccentB, ImpurityLab.scores.map { it.split.name.take(1) }),
        ),
        readout = "same ranking, different units",
    )

    val tied = ImpurityLab.tiedForError
    frames += NetFrame(
        status = "The third curve is the one that explains why trees are not grown on error rate. Splits " +
            "${tied.first.split.name.take(1)} and ${tied.second.split.name.take(1)} produce *identical* " +
            "misclassification gain — ${"%.3f".format(tied.first.errorGain)} both — while Gini prefers " +
            "${tied.second.split.name.take(1)} at ${"%.3f".format(tied.second.giniGain)} over " +
            "${"%.3f".format(tied.first.giniGain)}. Error rate is piecewise linear, so it cannot see progress " +
            "that does not yet change a majority vote.",
        bars = listOf(
            NetBar("misclassification gain", ImpurityLab.scores.map { it.errorGain.toFloat() }, NeutralColor, ImpurityLab.scores.map { it.split.name.take(1) }),
            NetBar("Gini gain", ImpurityLab.scores.map { it.giniGain.toFloat() }, ForwardColor, ImpurityLab.scores.map { it.split.name.take(1) }),
        ),
        readout = "tied at ${"%.3f".format(tied.first.errorGain)}, separated by Gini",
    )

    frames += NetFrame(
        status = "Gini and entropy, by contrast, almost never disagree — both are strictly concave, so both reward " +
            "the same kind of progress. On these four splits they rank identically. The practical difference is " +
            "cost: Gini needs no logarithm, which is why CART defaults to it and why it is the default in " +
            "scikit-learn.",
        plot = CurvePlot(
            "Gini against entropy/2, rescaled to compare shape",
            listOf(
                Curve("Gini", xs.zip(curve.map { it.first.toFloat() }), ForwardColor),
                Curve("entropy / 2", xs.zip(curve.map { (it.second / 2).toFloat() }), AccentB),
            ),
            0f..1f, 0f..0.55f,
        ),
        readout = "same shape, no logarithm",
    )

    frames += NetFrame(
        status = "Worth keeping separate from a metric, though: Gini impurity is a *splitting criterion*, not an " +
            "evaluation metric, and it is unrelated to the Gini coefficient from economics (which is 2·AUC − 1 in " +
            "this context, a ranking summary). Two different things with one name, and only one of them is what a " +
            "tree computes at every node.",
        bars = listOf(
            NetBar("Gini impurity, at a node", listOf(ImpurityLab.gini(ImpurityLab.parentPositive, ImpurityLab.parentNegative).toFloat()), ForwardColor, listOf("1 − Σp²")),
            NetBar("Gini coefficient, from AUC", listOf((2 * ScoredLab.auc - 1).toFloat()), AccentB, listOf("2·AUC − 1")),
        ),
        readout = "same name, different objects",
    )
    return frames
}

private fun hingeLossFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val hinge = MarginLab.curve(MarginLab::hinge).map { it.first.toFloat() to it.second.toFloat() }
    val logistic = MarginLab.curve(MarginLab::logistic).map { it.first.toFloat() to it.second.toFloat() }
    val zeroOne = MarginLab.curve(MarginLab::zeroOne).map { it.first.toFloat() to it.second.toFloat() }
    val active = MarginLab.active

    frames += NetFrame(
        status = "Hinge loss is max(0, 1 − y·f(x)): zero once a prediction is correct *by a margin of 1*, and " +
            "linear before that. The quantity on the x-axis is the margin, not the probability — hinge loss is " +
            "defined on a decision value, which is why it belongs to SVMs rather than to logistic regression.",
        plot = CurvePlot(
            "loss against margin y·f(x)",
            listOf(
                Curve("hinge", hinge, ForwardColor),
                Curve("0-1 loss", zeroOne, NeutralColor),
            ),
            -2f..3f, 0f..3f,
        ),
        readout = "zero past a margin of 1",
    )

    frames += NetFrame(
        status = "It exists because the loss anyone actually wants — 0-1 loss, the grey step — is not " +
            "differentiable and not convex, so nothing can be optimised against it. Hinge is the tightest convex " +
            "upper bound that is also an upper bound on 0-1 loss, which is the sense in which minimising it is a " +
            "principled stand-in for minimising errors.",
        plot = CurvePlot(
            "three losses on the same margin",
            listOf(
                Curve("0-1 loss", zeroOne, NeutralColor),
                Curve("hinge", hinge, ForwardColor),
                Curve("logistic", logistic, AccentB),
            ),
            -2f..3f, 0f..3f,
        ),
        readout = "convex surrogates for a step function",
    )

    frames += NetFrame(
        status = "The difference that matters between hinge and logistic is not the shape, it is the zero. Hinge " +
            "is exactly 0 past the margin, so those examples contribute no gradient at all — measured on this " +
            "model's ${active.total} margins, only ${active.hingeActive} still have any hinge loss, against " +
            "${active.logisticActive} that still have logistic loss. An SVM's solution depends on " +
            "${active.hingeActive} points; a logistic fit depends on all ${active.total}.",
        bars = listOf(
            NetBar(
                "examples the loss still cares about",
                listOf(active.hingeActive.toFloat(), active.logisticActive.toFloat()),
                ForwardColor,
                listOf("hinge", "logistic"),
            ),
        ),
        readout = "${active.hingeActive} of ${active.total} against ${active.logisticActive} of ${active.total}",
    )

    frames += NetFrame(
        status = "Priced as a gradient: at a margin of 1 the hinge gradient is exactly " +
            "${"%.0f".format(kotlin.math.abs(MarginLab.hingeGradient(1.0)))} and stays there, while the logistic " +
            "gradient is ${"%.3f".format(MarginLab.logisticGradientAt(1.0))} and never reaches zero — " +
            "${"%.1e".format(MarginLab.logisticGradientAt(10.0))} even at a margin of 10. Logistic regression " +
            "keeps pushing correct points further from the boundary forever; that is why it needs regularization " +
            "to stop.",
        plot = CurvePlot(
            "gradient magnitude against margin",
            listOf(
                Curve("hinge", MarginLab.curve({ kotlin.math.abs(MarginLab.hingeGradient(it)) }).map { it.first.toFloat() to it.second.toFloat() }, ForwardColor),
                Curve("logistic", MarginLab.curve({ MarginLab.logisticGradientAt(it) }).map { it.first.toFloat() to it.second.toFloat() }, AccentB),
            ),
            -2f..3f, 0f..1.05f,
        ),
        readout = "one stops, one does not",
    )

    frames += NetFrame(
        status = "Two consequences worth carrying. Hinge loss gives you support vectors — a sparse solution " +
            "determined by a few points — and it gives you no probabilities, because a margin is not a " +
            "likelihood; anything probabilistic from an SVM comes from a separate calibration step. Squared hinge " +
            "is the smooth variant, and it trades the sparsity back for differentiability everywhere.",
        plot = CurvePlot(
            // Squared hinge reaches (1 − (−2))² = 9 at the left edge, so the axis has to hold 9 and
            // not 3 — caught by the frame guard rather than by looking at the picture.
            "hinge against squared hinge",
            listOf(
                Curve("hinge", hinge, ForwardColor),
                Curve("squared hinge", MarginLab.curve(MarginLab::squaredHinge).map { it.first.toFloat() to it.second.toFloat() }, OutputColor),
            ),
            -2f..3f, 0f..9.2f,
        ),
        readout = "sparse and non-smooth, or smooth and dense",
    )
    return frames
}

// ── C7 · Generative deep learning ────────────────────────────────────────────
// Five of the batch's labs. Every number below comes from `GenerativeMath.kt`, which is pinned by
// `GenerativeMathTest` — nothing here is a literal typed twice.

private val netConfigs = mapOf(

    "roc_curve" to NetConfig(
        intro = "One point per threshold, then the precision-recall curve of the same model — which does not agree with it on a 9% base rate.",
        legend = listOf(
            ForwardColor to "ROC",
            AccentB to "Precision-recall",
            NeutralColor to "Chance",
        ),
        build = ::rocCurveFrames,
    ),
    "auc" to NetConfig(
        intro = "0.969 by area and by pair counting, unchanged by a monotone distortion that ruins the probabilities. One question answered well, three badly.",
        legend = listOf(
            ForwardColor to "AUC",
            BackwardColor to "Log loss",
            AccentA to "By pair counting",
        ),
        build = ::aucFrames,
    ),
    "log_loss" to NetConfig(
        intro = "The metric that reads probabilities. One confidently wrong case contributes 13x the average, and a monotone distortion AUC cannot see costs 64%.",
        legend = listOf(
            BackwardColor to "Log loss",
            ForwardColor to "Calibration",
            AccentA to "Brier",
        ),
        build = ::logLossFrames,
    ),
    "cohens_kappa" to NetConfig(
        intro = "Accuracy 0.912 with kappa 0.000, on the same predictions — what chance correction does to a majority-class model.",
        legend = listOf(
            ForwardColor to "Kappa",
            NeutralColor to "Accuracy",
            AccentA to "MCC",
        ),
        build = ::kappaFrames,
    ),
    "gini_impurity" to NetConfig(
        intro = "The splitting criterion, its two rivals, and the pair of splits misclassification rate cannot tell apart.",
        legend = listOf(
            ForwardColor to "Gini",
            AccentB to "Entropy",
            NeutralColor to "Misclassification",
        ),
        build = ::giniImpurityFrames,
    ),
    "hinge_loss" to NetConfig(
        intro = "Zero past the margin — measured, that leaves 110 of 1,000 examples with any gradient at all, against logistic loss's 1,000.",
        legend = listOf(
            ForwardColor to "Hinge",
            AccentB to "Logistic",
            NeutralColor to "0-1 loss",
        ),
        build = ::hingeLossFrames,
    ),

    // ── C8 · Optimizers & Training ────────────────────────────────────────────
)

// ── C9 · Regularization + Specialized ────────────────────────────────────────

// ── B10 · Restricted Boltzmann Machines + Deep Belief Networks ──────────────

// ── C8 · Optimizers & Training ────────────────────────────────────────────────

private fun netConfigFor(topicId: String): NetConfig =
    netConfigs[topicId] ?: netConfigs.getValue("roc_curve")

// ── UI ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeuralNetSection(topicId: String) {
    val config = remember(topicId) { netConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 850f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    // Laid out like the other redesigned labs (docs/ios-design/Simulations iOS.html): the intro above
    // the card; the network, plot, grids, bars and legend in it; the readout as chips and the step's
    // narration under it. A frame draws only the panels it carries, so the first one opens the card.
    Column(modifier = Modifier.fillMaxWidth()) {
        LabIntro(config.intro, Modifier.padding(bottom = 12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (frame.layers.isNotEmpty()) LayerDiagram(frame.layers)
                frame.plot?.let { PlotCanvas(it) }
                frame.grids.forEach { grid -> MatrixView(grid) }
                frame.bars.forEach { bar -> NetBars(bar) }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    config.legend.forEach { (color, label) -> NetLegend(color, label) }
                }
            }
        }

        frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 16.dp)) }
        LabNarration(frame.status, Modifier.padding(top = 16.dp))

        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

// Square swatches at the size the other redesigned labs use.
@Composable
private fun NetLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

private fun moodColor(mood: NodeMood): Color = when (mood) {
    NodeMood.IDLE -> NeutralColor
    NodeMood.FORWARD -> ForwardColor
    NodeMood.BACKWARD -> BackwardColor
    NodeMood.OUTPUT -> OutputColor
}

@Composable
private fun LayerDiagram(layers: List<NetLayer>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val edgeColor = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val idleFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
            val radius = 17.dp.toPx()
            val columnWidth = size.width / layers.size
            fun centerOf(layer: Int, index: Int, count: Int): Offset {
                val x = columnWidth * (layer + 0.5f)
                val usable = size.height - 34.dp.toPx()
                val y = if (count == 1) usable / 2f + 8.dp.toPx()
                else 8.dp.toPx() + usable * index / (count - 1f)
                return Offset(x, y)
            }

            // Edges first so the nodes sit on top of them.
            layers.dropLast(1).forEachIndexed { l, layer ->
                layer.nodes.indices.forEach { i ->
                    layers[l + 1].nodes.indices.forEach { j ->
                        drawLine(
                            color = edgeColor,
                            start = centerOf(l, i, layer.nodes.size),
                            end = centerOf(l + 1, j, layers[l + 1].nodes.size),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                }
            }

            layers.forEachIndexed { l, layer ->
                layer.nodes.forEachIndexed { i, node ->
                    val center = centerOf(l, i, layer.nodes.size)
                    // An idle neuron is flat grey with dark text, like an idle node in the tree and call
                    // tree labs; only neurons the step is about take a colour. The opaque base keeps
                    // the edges from showing through the translucent grey.
                    val idle = node.mood == NodeMood.IDLE
                    drawCircle(color = surface, radius = radius, center = center)
                    drawCircle(color = if (idle) idleFill else moodColor(node.mood), radius = radius, center = center)
                    val text = measurer.measure("%.2f".format(node.value), valueStyle)
                    drawText(
                        text,
                        color = if (idle) onSurface else Color.White,
                        topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f),
                    )
                }
                val label = measurer.measure(layer.label, labelStyle)
                drawText(
                    label,
                    topLeft = Offset(columnWidth * (l + 0.5f) - label.size.width / 2f, size.height - label.size.height),
                )
            }
        }
    }
}

@Composable
private fun PlotCanvas(plot: CurvePlot, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(plot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                fun place(x: Float, y: Float): Offset {
                    val nx = (x - plot.xRange.start) / (plot.xRange.endInclusive - plot.xRange.start)
                    val ny = (y - plot.yRange.start) / (plot.yRange.endInclusive - plot.yRange.start)
                    return Offset(nx * size.width, size.height - ny.coerceIn(-0.1f, 1.1f) * size.height)
                }

                if (0f in plot.yRange) {
                    val zero = place(plot.xRange.start, 0f)
                    drawLine(axisColor, Offset(0f, zero.y), Offset(size.width, zero.y), strokeWidth = 1.5.dp.toPx())
                }
                if (0f in plot.xRange) {
                    val zero = place(0f, plot.yRange.start)
                    drawLine(axisColor, Offset(zero.x, 0f), Offset(zero.x, size.height), strokeWidth = 1.5.dp.toPx())
                }

                plot.curves.forEach { curve ->
                    if (curve.points.size == 1) {
                        val p = curve.points.first()
                        drawCircle(curve.color, radius = 7.dp.toPx(), center = place(p.first, p.second))
                        return@forEach
                    }
                    curve.points.zipWithNext { a, b ->
                        drawLine(
                            color = curve.color,
                            start = place(a.first, a.second),
                            end = place(b.first, b.second),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                    // Trajectories read better with their endpoint marked.
                    val last = curve.points.last()
                    drawCircle(curve.color, radius = 5.dp.toPx(), center = place(last.first, last.second))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plot.curves.forEach { curve ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(curve.color, CircleShape))
                    Text(
                        curve.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixView(grid: GridView, modifier: Modifier = Modifier) {
    val peak = grid.values.flatten().maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f
    Column(modifier = modifier.fillMaxWidth()) {
        Text(grid.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            grid.values.forEachIndexed { r, row ->
                Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    row.forEachIndexed { c, value ->
                        val intensity = (abs(value) / peak).coerceIn(0f, 1f)
                        val base = if (value < 0f) BackwardColor else ForwardColor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.5.dp)
                                .background(base.copy(alpha = 0.10f + 0.75f * intensity), RoundedCornerShape(4.dp))
                                .then(
                                    if (r to c in grid.highlight) {
                                        Modifier.background(Color.Transparent, RoundedCornerShape(4.dp))
                                    } else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r to c in grid.highlight) {
                                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                    drawRoundRect(
                                        color = OutputColor,
                                        style = Stroke(width = 4.dp.toPx()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                    )
                                }
                            }
                            Text(
                                if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (intensity > 0.5f) FontWeight.Bold else FontWeight.Normal,
                                color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetBars(bar: NetBar, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            val mid = size.height / 2f
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5.dp.toPx())
            bar.values.forEachIndexed { index, value ->
                val height = (abs(value) / peak) * (size.height / 2f - 2f)
                drawRoundRect(
                    color = if (value >= 0f) bar.color else BackwardColor,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = androidx.compose.ui.geometry.Size(slot * 0.6f, height.coerceAtLeast(1.5f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
            }
        }
        if (bar.captions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                bar.captions.forEach { caption ->
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal val neuralNetTopicIds: Set<String> get() = netConfigs.keys

internal fun neuralNetFrameCount(topicId: String): Int {
    val frames = netConfigFor(topicId).build()
    frames.forEach { frame ->
        // A plot whose curve leaves its declared range is drawn clamped, so a builder that gets its
        // axis wrong looks plausible on screen. C1's log-scale plots span twenty orders of
        // magnitude, which is exactly where that mistake is easy to make.
        frame.plot?.let { plot ->
            val stray = plot.curves.flatMap { it.points }.count { (x, y) ->
                !x.isFinite() || !y.isFinite() ||
                    x !in plot.xRange || y !in (plot.yRange.start - 0.001f)..(plot.yRange.endInclusive + 0.001f)
            }
            require(stray == 0) { "$topicId plots $stray point(s) outside '${plot.label}' declared range" }
        }
        frame.bars.forEach { bar ->
            require(bar.values.all { it.isFinite() }) { "$topicId draws a non-finite bar in '${bar.label}'" }
            require(bar.captions.isEmpty() || bar.captions.size == bar.values.size) {
                "$topicId bar '${bar.label}' has ${bar.captions.size} captions for ${bar.values.size} values"
            }
        }
    }
    return frames.size
}
