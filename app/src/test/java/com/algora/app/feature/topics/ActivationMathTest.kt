package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pins the properties C2's lab copy leans on, in the pattern the earlier batches established.
 *
 * Most of these are exact, because an activation function is a formula and its shape facts do not
 * depend on a seed. The ones that do depend on a run — depth propagation, dead-unit counts — are
 * asserted as orderings and checked across seeds, because a result that held only for the single
 * configuration on screen would not be a property of the activation.
 */
class ActivationMathTest {

    private fun maxDerivative(a: Activation) = (-800..800).maxOf { a.df(it / 100.0) }

    @Test
    fun `the sigmoid derivative ceiling is exactly a quarter`() {
        assertEquals(0.25, maxDerivative(sigmoidActivation), 1e-9)
        // And tanh's is exactly four times it, which is the whole numeric case for tanh.
        assertEquals(1.0, maxDerivative(tanhActivation), 1e-9)
    }

    @Test
    fun `tanh is zero-centred and sigmoid is not`() {
        // Measured: −0.009 against 0.497. The frame quotes both.
        assertTrue(abs(meanOutput(tanhActivation)) < 0.05)
        assertTrue(meanOutput(sigmoidActivation) > 0.45)
    }

    @Test
    fun `saturation grows with pre-activation width for the squashing pair and not for ReLU`() {
        // The distinction the ReLU lab is built on: ReLU's zero-derivative fraction is fixed by the
        // shape, sigmoid's is a failure that worsens as weights grow. Measured: sigmoid 0.000 to
        // 0.777 across the sweep, ReLU flat at ~0.50.
        val widths = listOf(1.0, 2.0, 4.0, 8.0, 16.0)
        val sigmoid = widths.map { saturationAtScale(sigmoidActivation, it) }
        sigmoid.zipWithNext { a, b -> assertTrue("sigmoid saturation should climb, got $sigmoid", b > a) }
        assertTrue(sigmoid.first() < 0.01)
        assertTrue(sigmoid.last() > 0.6)

        val relu = widths.map { saturationAtScale(reluActivation, it) }
        relu.forEach { assertEquals("ReLU's zero region is fixed at half the domain", 0.5, it, 0.02) }
    }

    @Test
    fun `ReLU units die at a large learning rate and Leaky ReLU units never do`() {
        // The dying-ReLU claim, run rather than described. Measured: 0%, 12.5%, 78.1%, 100% for
        // ReLU as the rate climbs; 0% for Leaky at every one.
        val rates = listOf(1.0, 30.0, 60.0, 100.0)
        val relu = rates.map { dyingRelu(reluActivation, learningRate = it).deadFraction }
        assertEquals("a sane rate should kill nothing", 0.0, relu.first(), 1e-9)
        assertTrue("a large rate should kill most of the layer, got $relu", relu.last() > 0.9)
        relu.zipWithNext { a, b -> assertTrue("more deaths at higher rates, got $relu", b >= a) }

        rates.forEach {
            assertEquals(
                "Leaky ReLU should never lose a unit, failed at lr $it",
                0.0, dyingRelu(leakyReluActivation(), learningRate = it).deadFraction, 1e-9,
            )
        }
    }

    @Test
    fun `the deaths are early and permanent`() {
        // The frame's strongest claim: once a unit is off for every input its gradient is exactly
        // zero forever, so the dead fraction can never fall. Measured at lr 100 it reaches 1.0 by
        // the second sample and stays there.
        val run = dyingRelu(learningRate = 100.0)
        run.deadOverTime.zipWithNext { a, b ->
            assertTrue("a dead unit cannot recover, saw $a then $b", b >= a - 1e-12)
        }
        assertTrue("most deaths should happen early", run.deadOverTime[1] > 0.5 * run.deadFraction)
    }

    @Test
    fun `PReLU learns the slope the data was generated with`() {
        val run = trainPrelu(trueSlope = 0.25)
        assertEquals("α should start at plain ReLU", 0.0, run.alphaHistory.first(), 1e-12)
        assertEquals("and converge on the true slope", 0.25, run.alphaHistory.last(), 1e-3)
        assertTrue(run.lossHistory.last() < run.lossHistory.first())
    }

    @Test
    fun `ELU moves the mean toward zero and Leaky ReLU does not`() {
        // These are often described as solving the same problem. Measured, they do not: ELU 0.151
        // against ReLU's 0.394, while Leaky ReLU is 0.390 — essentially unchanged.
        val relu = meanOutput(reluActivation)
        val elu = meanOutput(eluActivation())
        val leaky = meanOutput(leakyReluActivation())
        assertTrue("ELU should shift the mean, $elu vs $relu", elu < relu * 0.6)
        assertTrue("Leaky ReLU should barely move it, $leaky vs $relu", abs(leaky - relu) < 0.02)
    }

    @Test
    fun `SELU self-normalises across twenty layers`() {
        // The headline claim, and the reason this file exists. Checked across seeds so it is a
        // property of the constants rather than of one draw.
        listOf(71, 89, 103).forEach { seed ->
            val p = propagate(seluActivation, seed = seed)
            val last = p.perLayer.last()
            assertTrue("SELU mean should stay near 0 at seed $seed, got ${last.mean}", abs(last.mean) < 0.25)
            assertTrue("SELU std should stay near 1 at seed $seed, got ${last.std}", last.std in 0.7..1.3)
        }
    }

    @Test
    fun `ReLU and tanh both lose their signal over the same twenty layers`() {
        // The comparison the SELU frame draws. Measured: std 0.090 and 0.148 against SELU's 0.971.
        val selu = propagate(seluActivation).perLayer.last().std
        val relu = propagate(reluActivation).perLayer.last().std
        val tanh = propagate(tanhActivation).perLayer.last().std
        assertTrue("ReLU should decay, $relu vs SELU's $selu", relu < selu * 0.5)
        assertTrue("tanh should decay, $tanh vs SELU's $selu", tanh < selu * 0.5)
    }

    @Test
    fun `SELU's self-normalisation depends on the initialisation it was derived for`() {
        // The condition that is easy to miss, and the frame states it. Under He instead of LeCun,
        // layer 20 measures mean ~2.0 and std ~4.8.
        val wrong = propagate(seluActivation, initScale = sqrt(2.0 / 32.0)).perLayer.last()
        assertTrue("under He init the property should break, got mean ${wrong.mean} std ${wrong.std}", wrong.std > 2.0)
    }

    @Test
    fun `Swish and GELU are non-monotone and can amplify`() {
        // Measured: Swish min −0.2785 at −1.2785, GELU min −0.1700 at −0.752, derivatives peaking
        // at 1.0998 and 1.1289. Every earlier activation in the category caps its derivative at 1.
        val swishMin = minimumOf({ swishActivation().f(it) })
        assertEquals(-0.2785, swishMin.second, 1e-3)
        assertEquals(-1.2785, swishMin.first, 1e-2)

        val geluMin = minimumOf({ geluActivation.f(it) })
        assertEquals(-0.1700, geluMin.second, 1e-3)

        assertTrue(maxDerivative(swishActivation()) > 1.0)
        assertTrue(maxDerivative(geluActivation) > 1.0)
        assertTrue("ReLU cannot amplify", maxDerivative(reluActivation) <= 1.0)
    }

    @Test
    fun `Swish interpolates between a line and ReLU`() {
        listOf(-3.0, -1.0, 0.5, 3.0).forEach { z ->
            assertEquals("β → 0 should give z/2", z / 2, swishActivation(0.001).f(z), 1e-2)
            assertEquals("β → ∞ should give ReLU", reluActivation.f(z), swishActivation(500.0).f(z), 1e-6)
        }
    }

    @Test
    fun `the GELU tanh approximation is close enough to matter operationally and not mathematically`() {
        // Measured max difference 0.00047. Small, and the frame's point is that pretrained
        // checkpoints still depend on which one you use.
        val deviation = maxDeviation({ geluActivation.f(it) }, ::geluTanhApproximation)
        assertTrue("should be tiny, got ${deviation.second}", deviation.second < 1e-3)
        assertTrue("but not zero", deviation.second > 1e-5)
    }

    @Test
    fun `softmax is shift invariant, and the naive form is not`() {
        val logits = listOf(2.0, 1.0, 0.1, -0.5)
        val base = softmax(logits)
        assertEquals("it is a distribution", 1.0, base.sum(), 1e-12)

        val shifted = softmax(logits.map { it + 500.0 })
        base.indices.forEach { assertEquals(base[it], shifted[it], 1e-12) }

        // And the failure the frame demonstrates rather than asserts.
        val big = listOf(1000.0, 1001.0, 1002.0)
        assertTrue("the naive form should overflow", softmaxNaive(big).any { it.isNaN() })
        assertEquals("the stable one should not", 1.0, softmax(big).sum(), 1e-12)
    }

    @Test
    fun `temperature only changes peakedness, and monotonically`() {
        val logits = listOf(2.0, 1.0, 0.1, -0.5)
        val entropies = listOf(0.25, 0.5, 1.0, 2.0, 5.0).map { entropyOf(softmax(logits, it)) }
        entropies.zipWithNext { a, b -> assertTrue("entropy should rise with T, got $entropies", b > a) }
        // The argmax is invariant: temperature never changes which class is on top.
        listOf(0.25, 1.0, 5.0).forEach { t ->
            val p = softmax(logits, t)
            assertEquals(0, p.indexOf(p.max()))
        }
    }

    @Test
    fun `every softmax Jacobian row sums to zero`() {
        // Because the outputs are constrained to sum to 1, raising one probability must lower the
        // others — which is what the row sum states, and why the matrix cancels against
        // cross-entropy.
        val jacobian = softmaxJacobian(softmax(listOf(2.0, 1.0, 0.1, -0.5)))
        jacobian.forEach { assertEquals(0.0, it.sum(), 1e-12) }
    }
}
