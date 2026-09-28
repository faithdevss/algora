import Foundation

// ArrayWalkSection.kt builders: the D6 labs — Flash Attention, SSMs, Mamba and RWKV — driven by
// FineTuneMath.swift, which runs each algorithm for real so the captions quote measured numbers.

private func ex(_ x: Double, _ digits: Int) -> String { String(format: "%.\(digits)e", x) }

func flashAttentionFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let blockSize = 64
    let tiles = FlashLab.tileTrace(FlashLab.demoScores, blockSize)
    let blocks = tiles.count

    func blockCells(_ upTo: Int, _ active: Int?) -> [CellView] {
        (0..<blocks).map { i in CellView("b\(i)", i == active ? .active : i < upTo ? .done : .dim) }
    }
    func sumRow(_ upTo: Int) -> [CellView] {
        (0..<blocks).map { i in i <= upTo ? CellView(fx(tiles[i].runningSum, 0), .window) : CellView("·", .dim) }
    }

    frames.append(WalkFrame(
        status: "\(FlashLab.demoScores.count) keys in \(blocks) blocks of \(blockSize). The textbook softmax needs every " +
            "score at once, because the denominator is a sum over all of them. This scan never holds more than one " +
            "block — it carries a running maximum m, a running denominator ℓ, and a running output.",
        cells: blockCells(0, nil), aux: sumRow(-1), auxLabel: "ℓ (running denominator)"))

    for tile in tiles {
        let raised = tile.rescale < 1.0
        frames.append(WalkFrame(
            status: raised
                ? "Block \(tile.index) contains a score of \(fx(tile.blockMax)), above the running maximum. " +
                    "Everything accumulated so far was scaled against the old maximum, so it is corrected by " +
                    "×\(fx(tile.rescale, 4)) before this block is added. m becomes \(fx(tile.runningMax))."
                : "Block \(tile.index) peaks at \(fx(tile.blockMax)), below the running maximum of " +
                    "\(fx(tile.runningMax)). Nothing is rescaled — the block is added and the scan moves on.",
            cells: blockCells(tile.index, tile.index), pointers: [tile.index: "m=\(fx(tile.runningMax, 1))"],
            aux: sumRow(tile.index), auxLabel: "ℓ (running denominator)",
            readout: raised ? "rescale ×\(fx(tile.rescale, 4))" : nil))
    }

    let diff = FlashLab.maxDifference(blockSize)
    frames.append(WalkFrame(
        status: "The scan's output against the textbook softmax on the same scores: they differ by " +
            "\(ex(diff, 1)), which is floating-point noise. This is the part " +
            "worth being clear about — Flash Attention is not an approximation. It computes the same function, and " +
            "the block size changes nothing about the answer.",
        cells: blockCells(blocks, nil), aux: sumRow(blocks - 1), auxLabel: "ℓ (final denominator)",
        readout: "max difference \(ex(diff, 1))"))

    frames.append(WalkFrame(
        status: "The running maximum is not an optimisation, it is what makes the sum finite. Without it every term " +
            "is exp(score), which overflows a float64 above \(fx(FlashLab.overflowScore, 1)) — and attention " +
            "logits routinely reach that in long-context models. A peak score of 500 survives; 710 returns NaN.",
        cells: [100.0, 300.0, 500.0, 700.0, 710.0, 800.0].map { CellView(fx($0, 0), FlashLab.overflows($0) ? .active : .done) },
        auxLabel: "peak score", readout: "overflow above \(fx(FlashLab.overflowScore))"))

    let lengths = [1_024, 4_096, 16_384, 65_536]
    frames.append(WalkFrame(
        status: "What it buys is memory. The N×N score matrix is never written: at N = 65,536 over 32 heads that " +
            "matrix alone is \(bytesToGb(FlashLab.scoreMatrixBytes(65_536))) GB of activations, and the tiled kernel " +
            "allocates none of it. This is the saving that scales — it is the whole reason 128k-token training fits.",
        cells: lengths.map { CellView("\($0 / 1024)k", .window) },
        aux: lengths.map { CellView("\(bytesToGb(FlashLab.scoreMatrixBytes($0)))G", .result) },
        auxLabel: "score matrix, 32 heads", readout: "never allocated"))

    let tileSizes = [64, 128, 256]
    frames.append(WalkFrame(
        status: "The traffic saving is smaller than the folklore, and it has no N in it. Standard attention moves " +
            "about 4N² elements; the tiled kernel re-reads K and V once per query block, which is 2N²·d/Br. The ratio " +
            "is exactly 2·Br/d — a property of the tile and the head dimension, not of the sequence. At Br = 128 and " +
            "d = 64 that is \(fx(FlashLab.asymptoticTrafficRatio(), 0))×, and it is \(fx(FlashLab.trafficRatio(65_536)))× " +
            "measured at N = 65,536.",
        cells: tileSizes.map { CellView("Br=\($0)", .window) },
        aux: tileSizes.map { CellView("\(fx(FlashLab.asymptoticTrafficRatio(headDim: 64, queryBlock: $0), 0))×", .result) },
        auxLabel: "traffic ratio at d = 64", readout: "exactly 2·Br/d"))

    frames.append(WalkFrame(
        status: "And it costs arithmetic. The backward pass has no stored score matrix to read, so it recomputes " +
            "QKᵀ — \(fx((FlashLab.flopOverhead(4_096) - 1) * 100, 1))% more FLOPs across forward and backward. " +
            "It is faster anyway, which is the lesson: on this hardware the arithmetic is nearly free and the memory " +
            "traffic is not.",
        cells: [CellView("fwd", .done), CellView("bwd", .done), CellView("+recompute", .active)],
        readout: "\(fx(FlashLab.flopOverhead(4_096), 3))× the FLOPs, and still faster"))
    return frames
}

// One diagonal LTI system, walked as the recurrence and then rebuilt as a convolution.
func ssmFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let steps = 8
    let input = Array(SsmLab.demoInput.prefix(steps))
    let trace = SsmLab.stateTrace(input)
    let output = SsmLab.recurrent(input)

    func inputRow(_ active: Int?) -> [CellView] {
        (0..<steps).map { i in CellView(fx(input[i], 1), i == active ? .active : active != nil && i < active! ? .done : .idle) }
    }

    frames.append(WalkFrame(
        status: "A state space model is a linear recurrence: h ← Ā·h + B̄·x, y = C·h. Ā is diagonal here, so the " +
            "state is \(SsmLab.stateDim) independent channels with decay rates " +
            "\(SsmLab.aBar.map { fx($0, 3) }.joined(separator: ", ")) — deliberately spread over decades, so one state " +
            "carries several timescales at once.",
        cells: inputRow(nil), aux: (0..<steps).map { _ in CellView("·", .dim) }, auxLabel: "y (output)"))

    for t in 0..<steps {
        frames.append(WalkFrame(
            status: "Step \(t): every channel decays by its own Ā and takes in B̄·x. State is now " +
                "(\(trace[t].map { fx($0) }.joined(separator: ", "))); the read-out C·h gives " +
                "y = \(fx(output[t], 3)). One multiply-add per channel per token — the whole cost of decoding.",
            cells: inputRow(t), pointers: [t: "h"],
            aux: (0..<steps).map { i in i <= t ? CellView(fx(output[i]), .window) : CellView("·", .dim) }, auxLabel: "y (output)"))
    }

    let kernel = SsmLab.kernel(steps)
    frames.append(WalkFrame(
        status: "Now the other form. Because the system is linear and time-invariant, its whole behaviour is one " +
            "impulse response: K[t] = C·Āᵗ·B̄. Convolving the input with K has to produce the same outputs the " +
            "recurrence just produced — not approximately, identically.",
        cells: kernel.map { CellView(fx($0), .window) }, aux: (0..<steps).map { CellView("K\($0)", .dim) }, auxLabel: "K (convolution kernel)"))

    let conv = SsmLab.convolutional(input)
    let gap = SsmLab.formEquivalenceGap()
    frames.append(WalkFrame(
        status: "Run over the full \(SsmLab.demoInput.count)-token input, the two forms differ by " +
            "\(ex(gap, 1)). That is the family's entire structural argument: train " +
            "with the convolution, which is parallel over the sequence, then decode with the recurrence, which is " +
            "O(1) memory per token. Attention has no second form to switch into.",
        cells: (0..<steps).map { CellView(fx(output[$0]), .done) }, aux: (0..<steps).map { CellView(fx(conv[$0]), .result) },
        auxLabel: "convolution output", readout: "gap \(ex(gap, 1))"))

    let horizon = SsmLab.effectiveHorizon()
    frames.append(WalkFrame(
        status: "What the state remembers is set by the decay rates, and it is a half-life. Channel 0 keeps half of " +
            "a token's contribution \(fx(SsmLab.halfLife(0), 0)) tokens later; channel 3 keeps half of it for " +
            "\(fx(SsmLab.halfLife(3), 1)). Together the impulse response is still above 1% of its peak at " +
            "token \(horizon) — a real memory, and a fixed one.",
        cells: (0..<SsmLab.stateDim).map { CellView("ch\($0)", .window) },
        aux: (0..<SsmLab.stateDim).map { CellView(fx(SsmLab.halfLife($0), 0), .result) }, auxLabel: "half-life, in tokens",
        readout: "effective horizon \(horizon) tokens"))

    let costs = SsmLab.costs()
    let lastCost = costs[costs.count - 1]
    func ratio(_ c: SsmLab.CostRow) -> String { fx(Double(c.attentionOps) / Double(c.ssmRecurrentOps), 0) }
    frames.append(WalkFrame(
        status: "And the cost is linear. At 1M tokens attention does \(ratio(lastCost))× the arithmetic " +
            "this recurrence does. The scan is also associative — (a₂,b₂)∘(a₁,b₁) = (a₂a₁, a₂b₁+b₂) — so training " +
            "parallelises to depth \(lastCost.ssmScanDepth) instead of 1,048,576 sequential steps.",
        cells: costs.map { CellView("\($0.length / 1024)k", .window) }, aux: costs.map { CellView("\(ratio($0))×", .result) },
        auxLabel: "attention ops ÷ SSM ops", readout: "scan depth \(lastCost.ssmScanDepth) at 1M tokens"))
    return frames
}

// The selective-copying task on three one-channel systems; the aux row is the state.
func mambaFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let fillers = 7
    let seq = MambaLab.sequence(fillers)
    let arms = MambaLab.arms

    func tokenRow(_ active: Int?) -> [CellView] {
        seq.indices.map { i in CellView(i == 0 ? "SIG" : "·", i == active ? .active : i == 0 ? .result : .idle) }
    }

    frames.append(WalkFrame(
        status: "The selective-copying task. One token worth remembering (SIG, value \(MambaLab.signalValue)) arrives " +
            "first, then \(fillers) filler tokens carrying \(MambaLab.fillerValue) each. At the end the model is asked " +
            "for the signal. A one-channel recurrence h ← a·h + b·x has to hold it through the fillers.",
        cells: tokenRow(0), aux: seq.map { CellView(fx($0), .dim) }, auxLabel: "token value"))

    for arm in arms {
        let trace = arm.trace(seq)
        let status: String
        switch arm.short {
        case "decaying":
            status = "Arm one: a fixed decay of 0.90. It can forget the fillers, which is what you want — but " +
                "it forgets on a timer, so it forgets the signal at the same rate. After \(fillers) fillers the state " +
                "is \(fx(trace.last!, 3)), and only \(ex(MambaLab.signalContribution(arm, fillers), 1)) of that came from SIG."
        case "lossless":
            status = "Arm two: no decay at all, a = 1.00. Now nothing is forgotten — including every filler. " +
                "The state reaches \(fx(trace.last!, 1)), of which the signal is a fixed " +
                "\(fx(MambaLab.signalContribution(arm, fillers), 1)) and the rest is noise it had no way to refuse."
        default:
            status = "Arm three: Δ depends on the token. SIG gets Δ = 4, so a = e⁻⁴ and b = 1 − a — the state is " +
                "overwritten with it. Filler gets Δ = 0, so a = 1 and b = 0 — the state is held exactly and " +
                "nothing is written. The state stays at \(fx(trace.last!, 4)) for as long as you like."
        }
        frames.append(WalkFrame(
            status: status, cells: tokenRow(nil),
            aux: trace.map { CellView(fx($0), arm.short == "selective" ? .result : .window) }, auxLabel: "h (state) — \(arm.name)",
            readout: "signal contribution \(fx(MambaLab.signalContribution(arm, fillers), 3))"))
    }

    let near = MambaLab.signalContribution(arms[0], 0), far = MambaLab.signalContribution(arms[0], 100)
    let held = MambaLab.signalContribution(arms[2], 100)
    frames.append(WalkFrame(
        status: "Stretched out, the two time-invariant arms fail in opposite directions and the gap is not close. " +
            "The decaying system's signal contribution falls from \(fx(near, 3)) " +
            "to \(ex(far, 1)) over 100 fillers — a factor of \(fx(near / far, 0)). " +
            "The selective system holds \(fx(held, 4)) at every distance, because holding costs it nothing.",
        cells: MambaLab.fillerCounts.map { CellView("\($0)", .window) },
        aux: MambaLab.fillerCounts.map { CellView(ex(MambaLab.signalContribution(arms[0], $0), 0), .active) },
        auxLabel: "decaying arm: signal contribution by filler count", readout: "selective holds \(fx(held, 4)) throughout"))

    let residual = MambaLab.bestFixedKernelResidual()
    frames.append(WalkFrame(
        status: "Selectivity is not free: it costs the convolution. An LTI system is one fixed kernel, which is why " +
            "SSMs can train in parallel. A selective one has a different kernel at every position — the best single " +
            "fixed kernel fitted to this system's own outputs still leaves a residual of " +
            "\(fx(residual, 3)). That is why Mamba needs a hardware-aware parallel " +
            "scan instead: the scan survives input-dependence, the FFT convolution does not.",
        cells: [CellView("LTI", .done), CellView("conv ✓", .done), CellView("selective", .active), CellView("conv ✗", .active), CellView("scan ✓", .result)],
        readout: "fixed-kernel residual \(fx(residual, 3))"))

    let lengths = [1_024, 32_768, 1_048_576]
    func cacheRatio(_ l: Int) -> String { fx(Double(MambaLab.transformerCacheBytes(l)) / Double(MambaLab.mambaStateBytes()), 0) }
    frames.append(WalkFrame(
        status: "What it buys at inference is a state that does not grow. Mamba carries " +
            "\(MambaLab.mambaStateBytes() / 1024) KB per layer whatever the sequence length; one transformer layer's " +
            "KV cache at 1M tokens is \(MambaLab.transformerCacheBytes(1_048_576) / (1024 * 1024)) MB and climbing " +
            "linearly. That ratio — \(cacheRatio(1_048_576))× — is the argument for the whole family.",
        cells: lengths.map { CellView("\($0 / 1024)k", .window) }, aux: lengths.map { CellView("\(cacheRatio($0))×", .result) },
        auxLabel: "KV cache ÷ Mamba state, one layer", readout: "Mamba's state is constant in the sequence length"))
    return frames
}

// The WKV operator as a decaying weighted average, then the retrieval experiment that prices it.
func rwkvFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let span = 8
    let keys = Array(RwkvLab.demoKeys.prefix(span))
    let values = Array(RwkvLab.demoValues.prefix(span))

    func keyRow(_ active: Int?) -> [CellView] {
        (0..<span).map { i in CellView(fx(keys[i], 1), i == active ? .active : active != nil && i < active! ? .done : .idle) }
    }

    frames.append(WalkFrame(
        status: "RWKV replaces attention with a weighted average that has no query in it. A token's weight is " +
            "exp(k) — how much it asked to be remembered — times exp(−w·distance), a decay the model learns once per " +
            "channel. There is no per-read choice anywhere: the weights are fixed before the reader exists.",
        cells: keyRow(nil), aux: values.map { CellView(fx($0, 1), .dim) }, auxLabel: "v (values)"))

    for t in 1..<span {
        frames.append(WalkFrame(
            status: "At position \(t) the operator is a ratio of two running sums — numerator Σ e^{k−w·d}·v and " +
                "denominator Σ e^{k−w·d} — plus a bonus u = \(fx(RwkvLab.bonus, 1)) on the current token, " +
                "so the present is not drowned by the past. The read-out is \(fx(RwkvLab.wkvStable(keys, values, t), 3)).",
            cells: keyRow(t), pointers: [t: "t"],
            aux: (0..<span).map { i in i <= t ? CellView(fx(RwkvLab.wkvStable(keys, values, i)), .window) : CellView("·", .dim) },
            auxLabel: "wkv output"))
    }

    let gap = RwkvLab.stabilityGap()
    frames.append(WalkFrame(
        status: "Both sums are carried in one state and rescaled by a running maximum, exactly the trick Flash " +
            "Attention uses. Written the textbook way it agrees to \(ex(gap, 0)) — and it " +
            "returns NaN outright once a key reaches 720, because e^720 does not fit in a float64. The decay also has " +
            "to be applied before the new token is folded in, not after; the two orders differ by 5e-02.",
        cells: [10.0, 100.0, 500.0, 700.0, 720.0, 800.0].map { CellView(fx($0, 0), RwkvLab.naiveOverflowsAt($0) ? .active : .done) },
        auxLabel: "key magnitude", readout: "stable form agrees to \(ex(gap, 0))"))

    frames.append(WalkFrame(
        status: "What it gives up is retrieval at distance. Plant a needle with a strong key and ask how much of the " +
            "read-out it accounts for. Softmax attention can aim its query at that key and holds " +
            "\(fx(RwkvLab.needle(100).attentionShare * 100, 0))% of the weight even 100 tokens back. RWKV's " +
            "weight was decided when the needle was written, so it falls with distance whatever the reader wants: " +
            "\(fx(RwkvLab.needle(5).rwkvShare * 100, 0))% at 5 tokens, " +
            "\(fx(RwkvLab.needle(50).rwkvShare * 100, 1))% at 50, " +
            "\(fx(RwkvLab.needle(100).rwkvShare * 100))% at 100.",
        cells: RwkvLab.needleDistances.map { CellView("\($0)", .window) },
        aux: RwkvLab.needleDistances.map { CellView(ex(RwkvLab.needle($0).rwkvShare, 0), .active) },
        auxLabel: "needle's share of the RWKV read-out",
        readout: "attention holds \(fx(RwkvLab.needle(500).attentionShare * 100, 0))% at 500 tokens"))

    let lengths = [1_024, 32_768, 1_048_576]
    frames.append(WalkFrame(
        status: "And what it buys is the other side of that trade. RWKV's state is (a, b, p) per channel — " +
            "\(RwkvLab.stateBytes() / 1024) KB, fixed — while a transformer's KV cache at 1M tokens is " +
            "\(bytesToGb(RwkvLab.cacheBytes(1_048_576))) GB. Constant memory per token and no quadratic prefill, " +
            "bought by giving up the query. Which side of that trade is right is a question about the workload.",
        cells: lengths.map { CellView("\($0 / 1024)k", .window) },
        aux: lengths.map { CellView("\(bytesToGb(RwkvLab.cacheBytes($0)))G", .result) },
        auxLabel: "transformer KV cache (RWKV: \(RwkvLab.stateBytes() / 1024) KB at every length)",
        readout: "\(fx(Double(RwkvLab.cacheBytes(1_048_576)) / Double(RwkvLab.stateBytes()), 0))× at 1M tokens"))
    return frames
}
