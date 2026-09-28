package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D6's guard for the fine-tuning and post-transformer labs.
 *
 * Several of these exist because the probe disagreed with the copy that was about to be written: the
 * fine-tuning lab's frozen features were worthless until pretraining became multi-task, the LoRA
 * sweep diverged to NaN at every rank below 16, the reward fit was unbounded without weight decay,
 * RWKV's stabilised form was applying its decay one step late, and the quantization lab was scoring
 * itself by a task loss that could not measure what it claimed to.
 *
 * Where a number could drift without mattering, the assertion pins an *ordering* instead — a drifted
 * value is inaccurate, an inverted ordering makes the topic pointless.
 */
class D6MathTest {

    // ── Fine-tuning (full) ───────────────────────────────────────────────────

    @Test
    fun `frozen features win on small data and lose on large`() {
        val small = FineTuneLab.regimes(8)
        val large = FineTuneLab.regimes(256)
        assertTrue(
            "Feature extraction must beat full fine-tuning at 8 examples, or the topic has no trade-off",
            small[1].downstreamLoss < small[2].downstreamLoss,
        )
        assertTrue(
            "Full fine-tuning must beat feature extraction at 256 examples",
            large[2].downstreamLoss < large[1].downstreamLoss,
        )
        assertEquals(32, FineTuneLab.crossoverExamples())
    }

    @Test
    fun `the frozen body has a ceiling that more data does not lift`() {
        val at64 = FineTuneLab.regimes(64)[1].downstreamLoss
        val at256 = FineTuneLab.regimes(256)[1].downstreamLoss
        assertTrue("Frozen features should have flattened by 64 examples", at256 > at64 * 0.8)
        // And training from scratch catches that ceiling, which is the signal to unfreeze.
        assertTrue(FineTuneLab.regimes(256)[0].downstreamLoss <= at256 * 1.1)
    }

    @Test
    fun `full fine-tuning forgets the pretraining tasks catastrophically`() {
        assertTrue(
            "Forgetting must be an order of magnitude or the frame is not worth drawing",
            FineTuneLab.forgettingRatio(256) > 100.0,
        )
        // And the downstream number improves at the same time, which is the whole problem.
        assertTrue(FineTuneLab.regimes(256)[2].downstreamLoss < FineTuneLab.regimes(32)[2].downstreamLoss)
    }

    @Test
    fun `mixed-precision Adam costs sixteen bytes per trainable parameter`() {
        assertEquals(16, FineTuneLab.trainableBytesPerParam)
        assertEquals(104.3, bytesToGb(FineTuneLab.trainingBytes(7_000_000_000, 7_000_000_000)), 0.05)
        assertEquals(13.3, bytesToGb(FineTuneLab.trainingBytes(7_000_000_000, 20_000_000)), 0.05)
    }

    // ── DPO ──────────────────────────────────────────────────────────────────

    @Test
    fun `annotator noise produces a preference cycle`() {
        assertEquals(2, DpoLab.mislabelledPairs().size)
        val cycles = DpoLab.preferenceCycles()
        assertEquals(1, cycles.size)
        // The cycle is exactly the three responses the flipped labels touch.
        assertEquals(setOf(0, 1, 2), setOf(cycles[0].first, cycles[0].second, cycles[0].third))
    }

    @Test
    fun `Bradley-Terry cannot represent the cycle and gives its members equal reward`() {
        val cycle = DpoLab.preferenceCycles().first()
        val members = listOf(cycle.first, cycle.second, cycle.third).map { DpoLab.rewardModel[it] }
        assertEquals(members[0], members[1], 1e-3)
        assertEquals(members[1], members[2], 1e-3)
        assertEquals(0.8, DpoLab.rewardAccuracy(), 1e-9)
    }

    @Test
    fun `DPO and the closed-form RLHF policy agree at matched divergence`() {
        val trajectory = DpoLab.dpoTrajectory(0.1)
        val (_, gap) = DpoLab.matchedPolicyGap(0.1, trajectory.last().step)
        assertTrue("DPO's theorem is the topic's headline — a gap of $gap would falsify it", gap < 1e-6)
    }

    @Test
    fun `the alignment ceiling is set by the labels rather than the optimizer`() {
        val ceiling = DpoLab.alignmentCeiling()
        assertTrue(ceiling < DpoLab.bestPossibleQuality * 0.8)
        // Alignment still helps a great deal — the point is that it stops short, not that it fails.
        assertTrue(ceiling > DpoLab.expectedQuality(DpoLab.reference) * 5)
        // And no β recovers the gap: the best policy at any β is still the ceiling.
        listOf(0.05, 0.1, 0.5, 1.0).forEach {
            assertTrue(DpoLab.expectedQuality(DpoLab.rlhfPolicy(it)) <= ceiling + 1e-6)
        }
    }

    @Test
    fun `the reward fit is bounded`() {
        // Without weight decay this diverges and the "fitted reward" is whatever step the loop
        // stopped at. Every value must stay in a range a reader can be shown.
        assertTrue(DpoLab.rewardModel.all { kotlin.math.abs(it) < 5.0 })
        assertEquals(0.0, DpoLab.rewardModel.average(), 1e-9)
    }

    // ── PEFT ─────────────────────────────────────────────────────────────────

    @Test
    fun `the parameter counts are BERT-base's`() {
        assertEquals(108_891_648L, PeftLab.totalParams)
        assertEquals(294_912L, PeftLab.methods.first { it.name.startsWith("LoRA") }.trainable)
        assertEquals(121_344L, PeftLab.methods.first { it.name.startsWith("BitFit") }.trainable)
        assertEquals(55_296L, PeftLab.methods.last().trainable)
    }

    @Test
    fun `the trainable-parameter headline badly overstates the memory saving`() {
        val full = PeftLab.methods.first()
        val lora = PeftLab.methods.first { it.name.startsWith("LoRA") }
        val paramRatio = full.trainable.toDouble() / lora.trainable
        val memoryRatio = PeftLab.totalTrainingBytes(full).toDouble() / PeftLab.totalTrainingBytes(lora)
        assertTrue("LoRA should train ~369x fewer parameters", paramRatio > 300)
        assertTrue("...and save under 3x the memory, which is the topic's point", memoryRatio < 3.0)
        assertTrue(PeftLab.headlineOverstatement(lora) > 100)
        // The more extreme the method, the worse the overstatement — all converge on one floor.
        assertTrue(PeftLab.headlineOverstatement(PeftLab.methods.last()) > PeftLab.headlineOverstatement(lora))
    }

    @Test
    fun `activations are the floor every method converges on`() {
        val totals = PeftLab.methods.drop(1).map { PeftLab.totalTrainingBytes(it) }
        assertTrue(totals.max().toDouble() / totals.min() < 1.5)
        assertTrue(PeftLab.activationBytes > PeftLab.stateBytes(PeftLab.methods.last()) / 2)
    }

    // ── LoRA & QLoRA ─────────────────────────────────────────────────────────

    @Test
    fun `the update is low-rank where the model is not`() {
        val updateRank = LoraLab.effectiveRank(LoraLab.updateSpectrum)
        val baseRank = LoraLab.effectiveRank(LoraLab.baseSpectrum)
        assertTrue("ΔW's effective rank was $updateRank against $baseRank for W₀", updateRank < baseRank / 3)
        assertTrue(LoraLab.energyAtRank(3) > 0.85)
    }

    @Test
    fun `the spectrum accounts for its own energy`() {
        val frobenius = LoraLab.update.sumOf { row -> row.sumOf { it * it } }
        assertEquals(frobenius, LoraLab.updateSpectrum.sumOf { it * it }, 1e-8)
    }

    @Test
    fun `the trained rank sweep sits above the Eckart-Young floor at every rank`() {
        LoraLab.rankSweep.forEach { fit ->
            val floor = LoraLab.predictedLossAtRank(fit.rank)
            assertTrue(
                "rank ${fit.rank} trained to ${fit.loss}, below its floor of $floor — impossible, so the " +
                    "held-out set is no longer isotropic",
                fit.loss >= floor - 1e-6,
            )
            assertTrue("rank ${fit.rank} should get within 30% of its floor", fit.loss <= floor * 1.3 + 0.02)
        }
    }

    @Test
    fun `loss falls monotonically with rank`() {
        val losses = LoraLab.rankSweep.map { it.loss }
        assertEquals(losses.sortedDescending(), losses)
        assertTrue(LoraLab.gapClosed(LoraLab.rankSweep[2]) > 0.85)
    }

    @Test
    fun `the alpha over r scaling squares into the step size`() {
        // At one fixed rate every low rank diverges, which is what makes α worth a frame.
        assertTrue(LoraLab.divergesAtFixedRate(1))
        assertTrue(LoraLab.divergesAtFixedRate(8))
        assertFalse(LoraLab.divergesAtFixedRate(16))
    }

    @Test
    fun `QLoRA adapters absorb most of the base's quantization damage`() {
        val quantizationCost = LoraLab.quantizedBaseLoss - LoraLab.baseLoss
        val residual = LoraLab.qloraFit(4).loss - LoraLab.rankSweep[2].loss
        assertTrue("Quantizing the base must cost something measurable", quantizationCost > 1e-4)
        assertTrue("The adapters must recover most of it, or QLoRA has no story", residual < quantizationCost * 0.5)
    }

    // ── Quantization ─────────────────────────────────────────────────────────

    @Test
    fun `NF4 beats uniform int4 at the same width`() {
        val errors = QuantLab.errors().toMap()
        assertTrue(errors.getValue("NF4, per tensor") < errors.getValue("int4, per tensor") * 0.7)
        assertTrue(errors.getValue("NF4, blocks of 64") < errors.getValue("int4, blocks of 64"))
        // And int8 is effectively lossless, which is why the interesting question is 4-bit.
        assertTrue(QuantLab.snrDb(QuantLab.weights, QuantLab.schemes[0].quantize(QuantLab.weights)) > 40)
    }

    @Test
    fun `one outlier wrecks per-tensor scaling and blockwise contains it`() {
        assertTrue(QuantLab.outlierPenalty(QuantLab.schemes[0]) > 10)
        assertTrue(QuantLab.outlierPenalty(QuantLab.schemes[1]) > 10)
        assertTrue(QuantLab.outlierPenalty(QuantLab.schemes[3]) < 3)
        assertTrue(QuantLab.outlierPenalty(QuantLab.schemes[4]) < 3)
    }

    @Test
    fun `output drift orders the schemes the way weight error does`() {
        val int8 = QuantLab.outputDrift(QuantLab.int8Levels, null)
        val int4 = QuantLab.outputDrift(QuantLab.int4Levels, null)
        val blocked = QuantLab.outputDrift(QuantLab.int4Levels, 16)
        assertTrue(int8 < blocked)
        assertTrue(blocked < int4)
        // The earlier version of this lab scored by task loss and reported NF4 as beating fp16.
        assertTrue("Quantization must never improve on the tensor it quantizes", int8 > 0.0)
    }

    // ── Flash Attention ──────────────────────────────────────────────────────

    @Test
    fun `the tiled softmax is exact at every block size`() {
        listOf(16, 64, 128, 512).forEach {
            assertTrue("block $it drifted", FlashLab.maxDifference(it) < 1e-12)
        }
    }

    @Test
    fun `the running maximum is what keeps the sum finite`() {
        assertFalse(FlashLab.overflows(500.0))
        assertTrue(FlashLab.overflows(710.0))
        assertEquals(709.78, FlashLab.overflowScore, 0.01)
    }

    @Test
    fun `the traffic saving is exactly two Br over d`() {
        // No N in it — which is the part "10x less IO" loses.
        listOf(64L, 128L, 256L).forEach { br ->
            assertEquals(
                FlashLab.asymptoticTrafficRatio(64, br),
                FlashLab.trafficRatio(1 shl 20, 64, br),
                0.01,
            )
        }
        assertEquals(4.0, FlashLab.asymptoticTrafficRatio(64, 128), 1e-9)
        assertTrue("...and it costs FLOPs, which is the trade", FlashLab.flopOverhead(4_096) > 1.1)
    }

    // ── State space models ───────────────────────────────────────────────────

    @Test
    fun `the recurrence and the convolution are the same function`() {
        assertTrue(
            "The two forms differing is the one thing that would make this family pointless",
            SsmLab.formEquivalenceGap() < 1e-10,
        )
    }

    @Test
    fun `the state carries several timescales at once`() {
        val halfLives = (0 until SsmLab.stateDim).map { SsmLab.halfLife(it) }
        assertEquals(halfLives.sortedDescending(), halfLives)
        assertTrue("The slowest channel must span dozens of tokens", halfLives.first() > 50)
        assertTrue("The fastest must be near-instant", halfLives.last() < 2)
        assertTrue(SsmLab.effectiveHorizon() > 400)
    }

    @Test
    fun `the scan is associative, which is what parallelises it`() {
        val a = 0.9 to 0.3
        val b = 0.8 to 0.1
        val c = 0.7 to 0.5
        val left = SsmLab.scanCompose(SsmLab.scanCompose(a, b), c)
        val right = SsmLab.scanCompose(a, SsmLab.scanCompose(b, c))
        assertEquals(left.first, right.first, 1e-12)
        assertEquals(left.second, right.second, 1e-12)
        assertEquals(39, SsmLab.parallelScanDepth(1_048_576))
    }

    // ── Mamba ────────────────────────────────────────────────────────────────

    @Test
    fun `a fixed decay loses the signal and no decay cannot ignore the filler`() {
        val decaying = MambaLab.arms[0]
        val lossless = MambaLab.arms[1]
        val selective = MambaLab.arms[2]

        val near = MambaLab.signalContribution(decaying, 0)
        val far = MambaLab.signalContribution(decaying, 100)
        assertTrue("The decaying arm must collapse by orders of magnitude", near / far > 1_000)

        // The lossless arm keeps the signal exactly and drowns it in filler it could not refuse.
        assertEquals(
            MambaLab.signalContribution(lossless, 0),
            MambaLab.signalContribution(lossless, 100),
            1e-9,
        )
        assertTrue(lossless.recovered(100) > 50 * MambaLab.signalValue)

        // The selective arm holds its contribution at every distance. If this ever varies with the
        // filler count, the topic's central claim has inverted.
        assertEquals(
            MambaLab.signalContribution(selective, 0),
            MambaLab.signalContribution(selective, 100),
            1e-12,
        )
        assertTrue(MambaLab.signalContribution(selective, 100) > 0.95)
    }

    @Test
    fun `selectivity costs the convolution`() {
        assertTrue(
            "A fixed kernel must fail to reproduce the selective system, or Mamba would not need a scan",
            MambaLab.bestFixedKernelResidual() > 0.1,
        )
    }

    // ── RWKV ─────────────────────────────────────────────────────────────────

    @Test
    fun `the stabilised WKV matches the textbook form`() {
        // The decay has to be applied before the new token is folded in. The other order agrees to
        // 5e-02, which is small enough to read as rounding and is not.
        assertTrue(RwkvLab.stabilityGap() < 1e-12)
        assertFalse(RwkvLab.naiveOverflowsAt(500.0))
        assertTrue(RwkvLab.naiveOverflowsAt(720.0))
    }

    @Test
    fun `a query-free weight cannot be aimed at a distant needle`() {
        val near = RwkvLab.needle(5)
        val far = RwkvLab.needle(100)
        assertTrue("RWKV should still find a nearby needle", near.rwkvShare > 0.5)
        assertTrue("...and lose a distant one entirely", far.rwkvShare < 0.01)
        // Attention, with a query aimed at the same key, does not degrade. That contrast is the topic.
        assertTrue(far.attentionShare > 0.95)
        assertTrue(RwkvLab.needle(500).attentionShare > 0.9)
    }

    @Test
    fun `the state does not grow with the sequence`() {
        assertEquals(RwkvLab.stateBytes(), RwkvLab.stateBytes())
        assertTrue(RwkvLab.cacheBytes(1_048_576) / RwkvLab.stateBytes() > 1_000_000)
    }

    // ── Long context ─────────────────────────────────────────────────────────

    @Test
    fun `the KV cache is the model several times over`() {
        assertEquals(2.0, bytesToGb(LongContextLab.kvCacheBytes(4_096)), 0.05)
        assertEquals(512.0, bytesToGb(LongContextLab.kvCacheBytes(1_048_576)), 0.5)
        assertTrue(
            LongContextLab.kvCacheBytes(1_048_576) > 30 * LongContextLab.params * LongContextLab.bytesPerElement,
        )
        // Grouped-query divides it by the group size, exactly.
        assertEquals(
            LongContextLab.kvCacheBytes(1_048_576) / 4,
            LongContextLab.kvCacheBytes(1_048_576, 8),
        )
    }

    @Test
    fun `attention overtakes everything else at about six times the width`() {
        assertTrue(LongContextLab.attentionShare(4_096) < 0.2)
        assertTrue(LongContextLab.attentionShare(1_048_576) > 0.95)
        val crossover = LongContextLab.flopCrossover()
        val sixD = 6 * LongContextLab.dModel
        assertTrue("crossover $crossover should be within 5% of 6·d = $sixD", crossover in (sixD * 95 / 100)..(sixD * 105 / 100))
    }

    @Test
    fun `stuffing the window is orders of magnitude dearer than retrieving`() {
        assertTrue(LongContextLab.stuffingOverhead(1_048_576) > 10_000)
        assertTrue(LongContextLab.stuffingOverhead(4_096) < 5)
    }

    @Test
    fun `ALiBi's own slopes cap most heads well short of the advertised window`() {
        val windows = LongContextLab.advertisedVersusEffective(1_048_576).map { it.second }
        assertEquals(windows.sorted(), windows)
        assertTrue("The steepest head must see almost nothing", windows.first() < 20)
        assertTrue("Even the shallowest must fall far short of 1M", windows.last() < 5_000)
    }
}
