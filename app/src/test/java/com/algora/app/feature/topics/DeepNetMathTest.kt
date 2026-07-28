package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Pins the properties C1's lab copy leans on, in the pattern `DimReductionMathTest` and `B7MathTest`
 * established: exact assertions where the arithmetic is exact, ordering assertions where it is not.
 *
 * The gradient tests matter most. Vanishing and exploding gradients are almost always taught as an
 * assertion about repeated multiplication, and the labs claim to *measure* them instead — so if the
 * measurement ever stopped showing what the narration describes, the topics would still render
 * consistently and would have quietly become a diagram of nothing.
 */
class DeepNetMathTest {

    // ── The biological neuron ────────────────────────────────────────────────

    @Test
    fun `the neuron does not fire below its rheobase and does above it`() {
        val rheo = rheobase()
        assertEquals("R = 1, so the rheobase is exactly the threshold-to-rest gap", 15.0, rheo, 1e-9)

        // Below: the leak balances the input short of threshold, for any duration.
        listOf(5.0, 10.0, 14.0, 14.9).forEach {
            assertEquals("current $it should never fire", 0.0, firingRate(it, 2000.0), 1e-9)
        }
        // Above: it fires, and faster with more current.
        val rates = listOf(16.0, 20.0, 30.0, 50.0, 90.0).map { firingRate(it) }
        assertTrue("rate should be positive above the rheobase, got $rates", rates.all { it > 0 })
        rates.zipWithNext { a, b -> assertTrue("f-I curve should be increasing, got $rates", b > a) }
    }

    @Test
    fun `the f-I curve saturates, which is what makes it look like an activation function`() {
        // Measured: 30 Hz at I = 16, 228 Hz at I = 90. The rate rises far less than proportionally,
        // because the refractory period puts a ceiling on how fast spikes can follow each other.
        val low = firingRate(20.0)
        val high = firingRate(90.0)
        val currentRatio = 90.0 / 20.0
        val rateRatio = high / low
        assertTrue("the curve should bend over: rate ×${"%.1f".format(rateRatio)} for current ×$currentRatio", rateRatio < currentRatio)
    }

    @Test
    fun `a sub-threshold trace settles and a supra-threshold one resets`() {
        val quiet = lifTrace(12.0, 200.0)
        assertTrue("no spikes expected below rheobase", quiet.spikeTimes.isEmpty())
        assertTrue("it should settle short of threshold, peaked at ${quiet.potential.max()}", quiet.potential.max() < LifThreshold)

        val firing = lifTrace(20.0, 200.0)
        assertTrue(firing.spikeTimes.isNotEmpty())
        assertTrue("every reset should go to the reset potential", firing.potential.min() <= LifReset + 1e-9)
    }

    // ── The multi-layer perceptron ───────────────────────────────────────────

    @Test
    fun `the lab's chosen MLP actually solves XOR`() {
        val run = trainXor(hiddenUnits = 3, seed = 11)
        assertTrue("the seed the lab ships must converge, loss ${run.final.loss}", run.solved)
        assertTrue("and converge properly, not marginally", run.final.loss < 0.01)
        // Each input's output on the right side of 0.5 by a clear margin.
        run.final.outputs.forEachIndexed { i, out ->
            val expected = xorTargets[i]
            assertTrue("input $i predicted $out for target $expected", abs(out - expected) < 0.1)
        }
    }

    @Test
    fun `removing the non-linearity collapses the network to chance`() {
        val linear = trainXor(hiddenUnits = 3, seed = 11, linearHidden = true)
        assertTrue("a linear stack cannot separate XOR", !linear.solved)
        // ln 2 is the loss of a constant 0.5 prediction — the frame quotes this number exactly.
        assertEquals("a composition of linear maps is a linear map", ln(2.0), linear.final.loss, 1e-3)
        linear.final.outputs.forEach { assertEquals(0.5, it, 1e-3) }
    }

    @Test
    fun `width buys trainability rather than expressiveness`() {
        // Two hidden units are provably enough for XOR, but the loss surface has minima that trap
        // gradient descent. Measured over these five seeds: 2 solved twice, 3 solved four times,
        // 4 solved five times. The frame quotes those counts.
        val seeds = listOf(11, 17, 23, 31, 41)
        fun solvedCount(h: Int) = seeds.count { trainXor(hiddenUnits = h, seed = it).solved }

        val two = solvedCount(2)
        val four = solvedCount(4)
        assertTrue("two units should sometimes fail, solved $two of ${seeds.size}", two < seeds.size)
        assertTrue("four units should do better, $two vs $four", four > two)
    }

    // ── Vanishing and exploding gradients ────────────────────────────────────

    @Test
    fun `the sigmoid derivative ceiling is a quarter`() {
        assertEquals("this bound is the vanishing argument", 0.25, maxSigmoidDerivative(), 1e-6)
    }

    @Test
    fun `a deep sigmoid stack starves its early layers at every init scale`() {
        // The claim the topic exists for. Checked across scales *and* seeds, because a result that
        // held only for the one configuration on screen would not be a property of sigmoid stacks.
        listOf(0.25, 0.5, 1.0, 1.5).forEach { scale ->
            listOf(29, 37, 43).forEach { seed ->
                val g = deepGradients(activation = DeepActivation.Sigmoid, initScale = scale, seed = seed)
                assertTrue(
                    "sigmoid at scale $scale seed $seed should vanish, ratio ${g.ratio}",
                    g.ratio > 10.0,
                )
                assertTrue("and it should be monotone-ish in the layer index", g.perLayer.last() > g.perLayer.first())
            }
        }
    }

    @Test
    fun `ReLU with He initialisation does not`() {
        // Measured: ratios between 0.38 and 0.62 across seeds, against sigmoid's 500-3000.
        val he = sqrt(2.0 / 16.0)
        listOf(29, 37, 43, 51, 67).forEach { seed ->
            val relu = deepGradients(activation = DeepActivation.Relu, initScale = he, seed = seed)
            val sigmoid = deepGradients(activation = DeepActivation.Sigmoid, initScale = 1.0, seed = seed)
            assertTrue(
                "ReLU+He should stay within an order of magnitude, got ${relu.ratio} (sigmoid ${sigmoid.ratio})",
                relu.ratio in 0.05..20.0,
            )
            assertTrue(sigmoid.ratio > relu.ratio)
        }
    }

    @Test
    fun `the vanishing failure is invisible in the forward pass`() {
        // The reason it went undiagnosed for years, and the reason one lab frame is about the
        // forward pass looking fine. Sigmoid activations sit in a normal range at every depth.
        val g = deepGradients(activation = DeepActivation.Sigmoid, initScale = 1.0)
        g.activation.forEach {
            assertTrue("a sigmoid activation should look healthy, got $it", it in 0.1..0.9)
        }
        assertTrue("while the gradient has decayed by orders of magnitude", g.ratio > 100.0)
    }

    @Test
    fun `a careless init scale compounds activations upward with depth`() {
        val blown = deepGradients(activation = DeepActivation.Relu, initScale = 1.5)
        val stable = deepGradients(activation = DeepActivation.Relu, initScale = sqrt(2.0 / 16.0))

        // Measured: 1.4 -> 5.7e6 over twelve layers, about 3.6x per layer.
        assertTrue("activations should grow with depth, got ${blown.activation}", blown.activation.last() > 1e4)
        assertTrue("while He init stays put, got ${stable.activation.last()}", stable.activation.last() < 10.0)
        assertTrue("and the gradients follow", blown.perLayer.max() > 1e8)
    }

    @Test
    fun `global-norm clipping caps the length and leaves the direction alone`() {
        val blown = deepGradients(activation = DeepActivation.Relu, initScale = 1.5)
        val clip = clipGlobalNorm(blown.perLayer, 5.0)

        assertEquals("the norm should land on the threshold", 5.0, clip.afterNorm, 1e-6)
        assertTrue(clip.beforeNorm > clip.afterNorm)
        // One uniform factor: every layer's share of the total is unchanged, which is exactly the
        // property the frame claims and the reason per-parameter clipping is not the same thing.
        blown.perLayer.indices.forEach { i ->
            assertEquals(
                "layer $i's share of the gradient should survive clipping",
                blown.perLayer[i] / blown.perLayer.max(),
                clip.clippedPerLayer[i] / clip.clippedPerLayer.max(),
                1e-9,
            )
        }
        // Below the threshold, clipping must do nothing at all.
        val small = clipGlobalNorm(listOf(0.1, 0.2, 0.3), 5.0)
        assertEquals(1.0, small.scale, 1e-12)
    }
}
