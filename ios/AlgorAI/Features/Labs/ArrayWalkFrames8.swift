import Foundation

// ArrayWalkSection.kt builders: the data-preprocessing column transforms. Every number comes from
// PreprocessMath.swift, which scores each rule against a model rather than asserting it.

private func take(_ s: String, _ n: Int) -> String { String(s.prefix(n)) }

private func coefficient(_ round: FeatureSelectionLab.Round, _ name: String) -> Double {
    round.coefficients.first { $0.0 == name }!.1
}

func labelEncodingFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let cats = EncodingLab.categories
    let sample = Array(EncodingLab.rows.prefix(10))
    let codes = sample.map { EncodingLab.labelCodes[$0.category]! }
    func code(_ c: String) -> Int { Int(EncodingLab.labelCodes[c]!) }

    frames.append(WalkFrame(
        status: "Label encoding replaces each category with an integer. Ten rows of a colour column, and the " +
            "codes \(cats.enumerated().map { "\($1)=\($0)" }.joined(separator: ", ")).",
        cells: sample.map { CellView($0.category) }, aux: codes.map { CellView("\(Int($0))", .window) }, auxLabel: "label code",
        readout: "one column in, one column out"))

    let ry = Int(EncodingLab.codeDistance("red", "yellow")), rg = Int(EncodingLab.codeDistance("red", "green"))
    frames.append(WalkFrame(
        status: "What that buys is width: one column instead of \(cats.count). What it costs is " +
            "an ordering and a spacing the categories do not have. Under these codes \"red\" is " +
            "\(ry) away from \"yellow\" and \(rg) from \"green\" — but the colours are not ordered " +
            "at all, and one-hot puts every pair at the same distance, √2 = \(fx(EncodingLab.oneHotDistance(), 3)).",
        cells: cats.map { CellView($0) }, aux: cats.map { CellView("\(code($0))", .window) }, auxLabel: "implied position on a line",
        readout: "red→yellow \(ry), red→green \(rg)"))

    let linear = EncodingLab.linearFits
    let label = linear.first { $0.name == "label encoding" }!
    let oneHot = linear.first { $0.name == "one-hot" }!
    frames.append(WalkFrame(
        status: "So score it. The true effect per category here is non-monotone in the code order " +
            "(\(cats.map { "\($0) \(Int(EncodingLab.effects[$0]!))" }.joined(separator: ", "))), which " +
            "is the case the rule is really about. A least-squares fit on the label code lands at MSE " +
            "\(fx(label.error)); on one-hot, \(fx(oneHot.error, 3)) — \(fx(label.error / oneHot.error, 0))× worse.",
        cells: cats.map { CellView($0) }, aux: cats.map { CellView("\(Int(EncodingLab.effects[$0]!))", .active) }, auxLabel: "true effect",
        readout: "MSE \(fx(label.error)) vs \(fx(oneHot.error, 3))"))

    let depths = [0, 1, 2, 3]
    frames.append(WalkFrame(
        status: "But that verdict is about the *model*, not the encoding. A tree never reads the code as a " +
            "number, only as somewhere to split — and grown on the same label-coded column it reaches MSE " +
            "\(fx(EncodingLab.treeError(2), 3)) at depth 2, which is one-hot's number to three decimals. " +
            "Label encoding is free for trees and expensive for anything that multiplies the code by a weight.",
        cells: depths.map { CellView("depth \($0)") },
        aux: depths.map { CellView(fx(EncodingLab.treeError($0)), $0 >= 2 ? .done : .dim) }, auxLabel: "tree MSE on the label code",
        readout: "depth 2 = \(fx(EncodingLab.treeError(2), 3)) = one-hot"))

    frames.append(WalkFrame(
        status: "Which makes the rule conditional rather than absolute: label-encode for trees and boosted " +
            "ensembles, one-hot for linear models, distance-based models and anything that reads the number. And " +
            "if the categories *are* ordered — small, medium, large — the code is the right representation and " +
            "one-hot throws information away.",
        cells: [CellView("trees", .done), CellView("linear", .active), CellView("k-NN", .active), CellView("ordinal data", .done)],
        aux: [CellView("free", .done), CellView("\(fx(label.error / oneHot.error, 0))× worse", .active), CellView("distorted", .active), CellView("correct", .done)],
        auxLabel: "label encoding is"))
    return frames
}

func imputationFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let show = 12
    let values = Array(ImputationLab.complete.prefix(show))
    let holes = Array(ImputationLab.missing.prefix(show))
    let missingCount = ImputationLab.missing.filter { $0 }.count
    let total = ImputationLab.complete.count
    let ratePct = fx(ImputationLab.missingRate * 100, 0)

    func row(_ fill: Double?) -> [CellView] {
        values.indices.map { i in !holes[i] ? CellView(fx(values[i], 1)) : fill == nil ? CellView("—", .active) : CellView(fx(fill!, 1), .result) }
    }

    frames.append(WalkFrame(
        status: "\(missingCount) of \(total) values in this column are missing — \(ratePct)%, completely at random. Twelve rows of it, " +
            "with the holes marked.",
        cells: row(nil), readout: "\(ImputationLab.observedCount) observed, \(missingCount) missing"))

    frames.append(WalkFrame(
        status: "The cheapest fix is a constant: the mean of what is observed, " +
            "\(fx(ImputationLab.meanFill, 3)). Nothing crashes, no rows are lost, and every downstream " +
            "model runs. The column, however, is not the column any more.",
        cells: row(ImputationLab.meanFill), aux: row(nil), auxLabel: "before", readout: "fill = \(fx(ImputationLab.meanFill, 3))"))

    let effects = ImputationLab.effects
    let complete = effects.first { $0.name == "complete data" }!
    let meanFill = effects.first { $0.name == "mean imputation" }!
    frames.append(WalkFrame(
        status: "Here is what it did, measured against the complete data it came from. Variance falls from " +
            "\(fx(complete.variance, 3)) to \(fx(meanFill.variance, 3)) — a ratio of " +
            "\(fx(meanFill.variance / complete.variance, 3)), against the " +
            "\(fx(ImputationLab.predictedVarianceRatio())) that the missing rate alone predicts, since " +
            "the filled values contribute nothing to the spread.",
        cells: effects.map { CellView(take($0.name, 12)) },
        aux: effects.map { CellView(fx($0.variance), $0.name == "complete data" ? .done : .active) }, auxLabel: "variance",
        readout: "\(fx(meanFill.variance / complete.variance, 3)) of the original spread"))

    frames.append(WalkFrame(
        status: "And the damage is not confined to one column. This column correlates with another at " +
            "\(fx(complete.correlation, 3)) in the complete data; after mean imputation, " +
            "\(fx(meanFill.correlation, 3)) — because \(ratePct)% of " +
            "the rows now carry a value that has nothing to do with their partner. Imputation attenuates every " +
            "relationship the column was in.",
        cells: effects.map { CellView(take($0.name, 12)) },
        aux: effects.map { CellView(fx($0.correlation, 3), $0.name == "complete data" ? .done : .active) }, auxLabel: "correlation with partner column",
        readout: "\(fx(complete.correlation, 3)) → \(fx(meanFill.correlation, 3))"))

    let dropped = effects.first { $0.name == "drop the rows" }!
    frames.append(WalkFrame(
        status: "Dropping the rows instead keeps the column honest — variance \(fx(dropped.variance, 3)) " +
            "and correlation \(fx(dropped.correlation, 3)), both essentially the originals — and costs " +
            "\(total - dropped.rows) of \(total) rows. That trade is the " +
            "actual decision, and it is only this clean because the values here are missing *at random*.",
        cells: effects.map { CellView(take($0.name, 12)) },
        aux: effects.map { CellView("\($0.rows)", $0.name == "drop the rows" ? .result : .dim) }, auxLabel: "rows surviving",
        readout: "unbiased, at \(fx((1 - Double(dropped.rows) / Double(total)) * 100, 0))% of the data"))

    let centre = ImputationLab.skewedCentre
    frames.append(WalkFrame(
        status: "One more choice the constant hides. On a skewed column — nine tenths small values, one tenth " +
            "large — the mean is \(fx(centre.mean, 1)) and the median \(fx(centre.median, 1)). Filling with the mean inserts a value that " +
            "almost no real row has. Median for skewed columns, mode for categorical ones, and the mean only when " +
            "the column is roughly symmetric.",
        cells: [CellView("mean"), CellView("median")],
        aux: [CellView(fx(centre.mean, 1), .active), CellView(fx(centre.median, 1), .done)], auxLabel: "centre of the skewed column",
        readout: "\(fx(centre.mean, 1)) vs \(fx(centre.median, 1))"))
    return frames
}

func outlierFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let zT = "\(OutlierLab.zThreshold)"

    func cellsFor(_ data: [Double], _ flagged: Set<Double>) -> [CellView] {
        data.sorted().suffix(14).map { v in CellView(v >= 1000 ? fx(v, 0) : fx(v, 1), flagged.contains(v) ? .result : .idle) }
    }

    let cleanZ = OutlierLab.zScoreFlags(OutlierLab.clean)
    let cleanIqr = OutlierLab.iqrFlags(OutlierLab.clean)
    frames.append(WalkFrame(
        status: "A clean column: \(OutlierLab.clean.count) values, no outliers. Both rules agree — z-score flags " +
            "\(cleanZ.flagged.count), the IQR rule flags \(cleanIqr.flagged.count). The fences are " +
            "\(cleanIqr.threshold). Agreement on easy data is not evidence that two rules are equivalent.",
        cells: cellsFor(OutlierLab.clean, []), readout: "largest 14 values shown"))

    let z = OutlierLab.zScoreFlags(OutlierLab.contaminated)
    let iqr = OutlierLab.iqrFlags(OutlierLab.contaminated)
    let d = OutlierLab.contaminated
    let m = d.average
    let sigma = (d.reduce(0.0) { $0 + ($1 - m) * ($1 - m) } / Double(d.count - 1)).squareRoot()
    frames.append(WalkFrame(
        status: "Add one extreme value, \(fx(OutlierLab.extreme, 0)). Both rules catch it: its z-score is " +
            "\(fx(OutlierLab.selfZScore())), far past \(zT), and it is outside the " +
            "IQR fences too. Note what the extreme value did to the statistics it is being judged by, though — the " +
            "mean moved to \(fx(m, 1)) and σ to \(fx(sigma, 1)).",
        cells: cellsFor(d, Set(z.flagged)),
        aux: [CellView("z flags \(z.flagged.count)", .result), CellView("IQR flags \(iqr.flagged.count)", .result)], auxLabel: "verdicts",
        readout: "its own z = \(fx(OutlierLab.selfZScore()))"))

    let maskedZ = OutlierLab.zScoreFlags(OutlierLab.masked)
    let maskedIqr = OutlierLab.iqrFlags(OutlierLab.masked)
    let maskedMad = OutlierLab.modifiedZFlags(OutlierLab.masked)
    frames.append(WalkFrame(
        status: "Now the failure. Outliers that arrive together hide each other: each one inflates σ for the " +
            "rest. Adding extremes until the largest z-score drops under the threshold takes " +
            "\(OutlierLab.maskingCount) of them — \(fx(OutlierLab.maskingShare * 100, 1))% of the sample — " +
            "and at that point the z-score rule flags \(maskedZ.flagged.count). The IQR rule flags all " +
            "\(maskedIqr.flagged.count), because the quartiles have not moved.",
        cells: cellsFor(OutlierLab.masked, Set(maskedIqr.flagged)),
        aux: [CellView("z flags \(maskedZ.flagged.count)", .dim), CellView("IQR flags \(maskedIqr.flagged.count)", .result),
              CellView("MAD flags \(maskedMad.flagged.count)", .result)],
        auxLabel: "verdicts", readout: "largest z now \(fx(OutlierLab.selfZScore(OutlierLab.masked))) — under \(zT)"))

    let sizes = [5, 10, 11, 20, 61]
    frames.append(WalkFrame(
        status: "There is a second, quieter failure that has nothing to do with masking. A single point in a " +
            "sample of n can never have |z| above (n−1)/√n, because it is inside the mean and the σ being used to " +
            "judge it. Below n = \(OutlierLab.smallestUsableSample) that ceiling is under " +
            "\(zT), so the rule cannot fire at all — it is not strict on small samples, it is inert.",
        cells: sizes.map { CellView("n=\($0)") },
        aux: sizes.map { CellView(fx(OutlierLab.maxPossibleZ($0)), OutlierLab.maxPossibleZ($0) > OutlierLab.zThreshold ? .done : .dim) },
        auxLabel: "max possible |z|", readout: "|z| > 3 is unreachable below n = \(OutlierLab.smallestUsableSample)"))

    let rules = OutlierLab.breakdownPoints
    frames.append(WalkFrame(
        status: "Both failures are the same property: the z-score rule estimates its threshold from statistics " +
            "the outliers are inside. The robust alternatives estimate from statistics they are not — quartiles " +
            "tolerate a quarter of the sample being contaminated, the median and MAD tolerate half. That number " +
            "is the breakdown point, and it is what to check before trusting any outlier rule.",
        cells: rules.map { CellView(take($0.0, 16)) },
        aux: rules.map { CellView("\(fx($0.1 * 100, 0))%", $0.1 > 0 ? .done : .active) }, auxLabel: "breakdown point",
        readout: "use IQR or MAD; keep z-scores for clean, large, symmetric columns"))
    return frames
}

func chiSquareFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let ranking = FeatureSelectionLab.chiSquareRanking()
    let usefulTable = FeatureSelectionLab.chiSquare(FeatureSelectionLab.data, 0)
    let c = usefulTable.counts

    frames.append(WalkFrame(
        status: "Chi-square feature selection scores every feature against the target on its own, from a " +
            "contingency table. For \"useful\" the table over \(FeatureSelectionLab.data.count) rows is " +
            "\(c.map { $0.map(String.init).joined(separator: ",") }.joined(separator: " / ")) — observed counts by feature value and label.",
        cells: ["f=0,y=0", "f=0,y=1", "f=1,y=0", "f=1,y=1"].map { CellView($0) },
        aux: [CellView("\(c[0][0])", .window), CellView("\(c[0][1])", .window), CellView("\(c[1][0])", .window), CellView("\(c[1][1])", .window)],
        auxLabel: "observed", readout: "χ² = Σ (observed − expected)² / expected = \(fx(usefulTable.chiSquare, 1))"))

    frames.append(WalkFrame(
        status: "Run over all \(FeatureSelectionLab.featureNames.count) features it ranks them without fitting a " +
            "single model — which is the whole appeal. \"useful\" scores \(fx(ranking[0].1, 0)), " +
            "its near-copy \"duplicate\" \(fx(ranking[1].1, 0)), and the rest are noise-level.",
        cells: ranking.map { CellView($0.0) }, aux: ranking.map { CellView(fx($0.1, 1), $0.1 > 50 ? .done : .dim) }, auxLabel: "χ²",
        readout: "\(FeatureSelectionLab.chiSquareFits()) model fits required"))

    frames.append(WalkFrame(
        status: "It also cannot tell a useful feature from a copy of one. \"duplicate\" agrees with \"useful\" " +
            "90% of the time, scores \(fx(ranking[1].1, 0)), and adds nothing a model does not " +
            "already have — a univariate score has no way to notice, because it never looks at two features together.",
        cells: [CellView("useful", .done), CellView("duplicate", .active)],
        aux: [CellView(fx(ranking[0].1, 0), .done), CellView(fx(ranking[1].1, 0), .active)], auxLabel: "χ²",
        readout: "redundant, and ranked second"))

    let xorRanking = FeatureSelectionLab.chiSquareRanking(FeatureSelectionLab.xorData)
    frames.append(WalkFrame(
        status: "And here is the blind spot that matters. On data where the label is exactly xorA ⊕ xorB — the " +
            "pair determines it perfectly, with no noise — chi-square ranks " +
            "\"\(xorRanking[0].0)\" first at \(fx(xorRanking[0].1, 1)) and puts the two " +
            "features that *are* the signal at \(xorRanking.filter { $0.0.hasPrefix("xor") }.map { fx($0.1, 1) }.joined(separator: " and ")). " +
            "A one-at-a-time score cannot see an interaction, and this is what that looks like.",
        cells: xorRanking.map { CellView($0.0) },
        aux: xorRanking.map { CellView(fx($0.1, 1), $0.0.hasPrefix("xor") ? .active : .dim) }, auxLabel: "χ² on XOR-labelled data",
        readout: "the signal ranks below the noise"))

    frames.append(WalkFrame(
        status: "So it is a filter, not a decision: use it to drop obviously-dead columns cheaply on wide data, " +
            "and do not use it to choose between features that might interact. It also requires non-negative " +
            "counts — chi-square on a scaled or centred numeric column is a category error, not a weak result.",
        cells: [CellView("wide data"), CellView("interactions"), CellView("redundancy"), CellView("negatives")],
        aux: [CellView("good", .done), CellView("blind", .active), CellView("blind", .active), CellView("invalid", .active)],
        auxLabel: "chi-square is"))
    return frames
}

func rfeFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let rounds = FeatureSelectionLab.recursiveElimination()
    let first = rounds[0], lastRound = rounds[rounds.count - 1]

    frames.append(WalkFrame(
        status: "Recursive feature elimination goes the other way from a filter: fit the model on everything, " +
            "drop the weakest coefficient, refit, repeat. It sees whatever the model can express, and it costs " +
            "\(FeatureSelectionLab.fitsRequired(FeatureSelectionLab.featureNames.count)) fits instead of " +
            "\(FeatureSelectionLab.chiSquareFits()).",
        cells: FeatureSelectionLab.featureNames.map { CellView($0) },
        aux: first.remaining.map { CellView(fx(coefficient(first, $0), 3), .window) }, auxLabel: "coefficient, all features in",
        readout: "MSE \(fx(first.error, 4))"))

    for (index, round) in rounds.enumerated() {
        frames.append(WalkFrame(
            status: "Round \(index + 1): the smallest coefficient belongs to " +
                "\"\(round.dropped)\" (\(fx(coefficient(round, round.dropped), 3))), so it goes. " +
                "MSE is \(fx(round.error, 4)) — dropping it costs " +
                (index == rounds.count - 1 ? "nothing measurable" : "essentially nothing") + ".",
            cells: round.remaining.map { CellView($0, $0 == round.dropped ? .active : .idle) },
            aux: round.remaining.map { CellView(fx(coefficient(round, $0), 3), $0 == round.dropped ? .active : .window) },
            auxLabel: "coefficient", readout: "MSE \(fx(round.error, 4))"))
    }

    let survivor = lastRound.remaining.first { $0 != lastRound.dropped }!
    frames.append(WalkFrame(
        status: "It ends on \"\(survivor)\" alone, having dropped the noise, the redundant copy and both XOR " +
            "features — and the MSE barely moved across the whole elimination " +
            "(\(fx(first.error, 4)) → \(fx(lastRound.error, 4))), which is the honest " +
            "signal that those four columns were carrying nothing this model could use.",
        cells: [CellView(survivor, .result)], aux: [CellView(fx(lastRound.error, 4), .result)], auxLabel: "MSE with one feature",
        readout: "\(rounds.count) rounds, \(rounds.count) fits"))

    let xorRounds = FeatureSelectionLab.recursiveElimination(FeatureSelectionLab.xorData)
    frames.append(WalkFrame(
        status: "RFE is not a cure for the interaction blindness, though — it inherits its model's. On the " +
            "XOR-labelled data it drops \"\(xorRounds[0].dropped)\" first and never finds the pair either, " +
            "because a linear model cannot express XOR and so gives both features a coefficient near zero. Both " +
            "selectors fail on that data, for different reasons: chi-square because it looks one at a time, RFE " +
            "because its model cannot see it.",
        cells: xorRounds.map { CellView($0.dropped) }, aux: xorRounds.map { CellView(fx($0.error, 4), .dim) },
        auxLabel: "dropped, in order · MSE", readout: "swap the estimator for a tree and this changes"))
    return frames
}
