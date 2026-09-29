import SwiftUI

// Port of MetricStoryLabs.kt: Confusion Matrix, Model Evaluation, Precision & Recall, Accuracy, ROC Curve,
// F1 Score, AUC, Log Loss and Cohen's Kappa, each a figure over one model's scores (a score histogram, a
// dot strip, a metric curve, F-beta bars or the ROC curve), the step's arithmetic, chips and a headline,
// then a threshold (or prediction) stepper and one action. Every number is computed from the fixed
// scores below.

let metricStoryTopicIds: Set<String> = [
    "confusion_matrix", "model_evaluation", "precision_recall", "accuracy", "roc_curve",
    "f1_score", "auc", "log_loss", "cohens_kappa",
]

private let negC = SimColors.blue
private let posC = CategoryAccents.pink
private let pinkInk = Color(hex: 0xF472B6)
private let violetInk = Color(hex: 0xB4A2FF)

// MARK: - Data: 1,000 scored cases, 88 of them positive

private struct MtScored { let score: Double; let positive: Bool }

private let cases: [MtScored] = {
    var random = KotlinRandom(17)
    let negatives = (0..<912).map { _ in MtScored(score: 0.52 * pow(random.nextDouble(), 6), positive: false) }
    let positives = (0..<88).map { _ in MtScored(score: pow(random.nextDouble(), 0.8), positive: true) }
    return negatives + positives
}()

private let nPos = 88
private let nNeg = 912

private struct MtCounts {
    let tp: Int, fn: Int, fp: Int, tn: Int
    var n: Int { tp + fn + fp + tn }
    var precision: Double { tp + fp == 0 ? 1 : Double(tp) / Double(tp + fp) }
    var recall: Double { tp + fn == 0 ? 0 : Double(tp) / Double(tp + fn) }
    var accuracy: Double { Double(tp + tn) / Double(n) }
    func fBeta(_ beta: Double) -> Double {
        let p = precision, r = recall, b2 = beta * beta
        return p + r == 0 ? 0 : (1 + b2) * p * r / (b2 * p + r)
    }
    var fpr: Double { Double(fp) / Double(fp + tn) }
    var chance: Double {
        let nn = Double(n)
        return Double(tp + fp) / nn * Double(tp + fn) / nn + Double(fn + tn) / nn * Double(fp + tn) / nn
    }
    var kappa: Double { (accuracy - chance) / (1 - chance) }
}

private func countsOf(_ data: [MtScored], _ t: Double) -> MtCounts {
    var tp = 0, fn = 0, fp = 0, tn = 0
    for c in data {
        let flag = c.score >= t
        if c.positive && flag { tp += 1 } else if c.positive { fn += 1 } else if flag { fp += 1 } else { tn += 1 }
    }
    return MtCounts(tp: tp, fn: fn, fp: fp, tn: tn)
}

private let aucPairs: (Int, Int) = {
    let pos = cases.filter(\.positive).map(\.score), neg = cases.filter { !$0.positive }.map(\.score)
    var right = 0
    for p in pos { for q in neg where p > q { right += 1 } }
    return (right, pos.count * neg.count)
}()

private let rocPoints: [(Double, Double)] = stride(from: 100, through: 0, by: -1).map { i in
    let c = countsOf(cases, Double(i) / 100)
    return (c.fpr, c.recall)
}

/// The ROC curve at every distinct score, so its trapezoid area equals the pair-counting AUC exactly.
private let exactRoc: [(Double, Double)] = {
    let sorted = cases.sorted { $0.score > $1.score }
    var points: [(Double, Double)] = [(0, 0)]
    var tp = 0, fp = 0
    for (i, c) in sorted.enumerated() {
        if c.positive { tp += 1 } else { fp += 1 }
        if i == sorted.count - 1 || sorted[i + 1].score != c.score { points.append((Double(fp) / Double(nNeg), Double(tp) / Double(nPos))) }
    }
    return points
}()

private let modelLogLoss: Double = cases.reduce(0.0) { acc, c in
    let p = min(max(c.score, 1e-4), 1 - 1e-4)
    return acc - (c.positive ? log(p) : log(1 - p))
} / Double(cases.count)

/// The small strip for Model Evaluation: nine negatives and nine positives on one score axis.
private let stripCases = [0.10, 0.15, 0.23, 0.27, 0.30, 0.36, 0.41, 0.49, 0.62].map { MtScored(score: $0, positive: false) }
    + [0.38, 0.45, 0.52, 0.56, 0.60, 0.64, 0.75, 0.82, 0.90].map { MtScored(score: $0, positive: true) }

// MARK: - Formatting

private func mx(_ v: Double, _ d: Int = 2) -> String {
    var p: Int64 = 1
    for _ in 0..<d { p *= 10 }
    let r = Int64((abs(v) * Double(p) + 0.5).rounded(.down))
    var body = "\(r / p)"
    if d > 0 {
        let frac = String(r % p)
        body += "." + String(repeating: "0", count: d - frac.count) + frac
    }
    return v < 0 && r != 0 ? "−" + body : body
}

private func pct(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }

private func thousands(_ n: Int) -> String {
    let s = String(n)
    var out = ""
    for (i, ch) in s.enumerated() {
        if i > 0 && (s.count - i) % 3 == 0 { out += "," }
        out.append(ch)
    }
    return out
}

// MARK: - Scenes

private struct MtSeries {
    let points: [(Double, Double)]
    let color: Color?
    var dashed = false
    var faint = false
    var width: CGFloat = 2.5
}

private struct MtMarker { let x: Double; let y: Double; let color: Color }

private struct MtCurvePlot {
    let series: [MtSeries]
    var markers: [MtMarker] = []
    var vline: Double? = nil
    let xTitle: String
    var yTitle: String? = nil
    var yTop = "1"
    var yMax = 1.0
    var fillUnder: [(Double, Double)]? = nil
}

private enum MtScene {
    case hist(t: Double, byOutcome: Bool)
    case strip(t: Double)
    case curve(MtCurvePlot)
    case bars(values: [Double], selected: Int)
}

private struct MtFrame {
    let headline: String
    let body: String
    let scene: MtScene
    var matrix: MtCounts? = nil
    var pair: (Double, Double)? = nil
    var formula: [String] = []
    var legend: [(color: Color?, style: SwatchStyle, label: String)] = []
    var chips: [LabChip] = []
}

/// p: the stepper's index; tab: the picker; flag: the action's own state (a toggle or a draw count).
private struct MtState: Equatable { var p: Int; var tab = 0; var flag = 0 }

private struct MtParam { let name: String; let symbol: String; let values: [Double] }

private struct MtLab {
    let initial: MtState
    let frame: (MtState) -> MtFrame
    var param: MtParam? = nil
    var tabs: [String] = []
    var tabsInDock = false
    var action: ((MtState) -> String)? = nil
    var onAction: (MtState) -> MtState = { $0 }
}

private func ladder(_ from: Double, _ to: Double, _ step: Double) -> [Double] {
    (0...Int(((to - from) / step).rounded())).map { from + Double($0) * step }
}

private let thresholds = ladder(0.05, 0.95, 0.05)

private func nearest(_ values: [Double], _ v: Double) -> Int { values.indices.min { abs(values[$0] - v) < abs(values[$1] - v) }! }

private func curve(_ f: (MtCounts) -> Double) -> [(Double, Double)] { (0...100).map { i in (Double(i) / 100, f(countsOf(cases, Double(i) / 100))) } }

private func thresholdLegend(_ t: Double) -> (color: Color?, style: SwatchStyle, label: String) { (SimColors.active, .dashedLine, "t = \(mx(t))") }

// MARK: - Labs

private func confusionLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(thresholds, 0.5)), frame: { s in
        let t = thresholds[s.p]
        let c = countsOf(cases, t)
        if s.flag == 0 {
            return MtFrame(
                headline: "Every metric in this chapter is built from these {four counts}.",
                body: "At t = \(mx(t)) the model flags \(c.tp + c.fp) cases. \(c.tp) are real and \(c.fn) positives slip under the line.",
                scene: .hist(t: t, byOutcome: false), matrix: c,
                legend: [(negC, .fill, "\(nNeg) negatives"), (posC, .fill, "\(nPos) positives"), (nil, .line, "Threshold")])
        }
        return MtFrame(
            headline: "{w:\(c.fn) positives} fall below t and {w:\(c.fp) negatives} rise above it.",
            body: "Those two cells are the only errors. Raising t trades false positives for false negatives; lowering it does the reverse.",
            scene: .hist(t: t, byOutcome: true), matrix: c,
            legend: [(SimColors.green, .fill, "Correct"), (SimColors.red, .fill, "Wrong"), (nil, .line, "Threshold")])
    }, param: MtParam(name: "Threshold", symbol: "t", values: thresholds),
       action: { $0.flag == 0 ? "Split by Outcome" : "Show Classes" },
       onAction: { var s = $0; s.flag = 1 - s.flag; return s })
}

private func rateChip(_ key: String, _ v: Double) -> LabChip {
    v >= 0.8 ? LabChip(key: key, value: mx(v), good: true) : v < 0.5 ? LabChip(key: key, value: mx(v), tint: .warn) : LabChip(key: key, value: mx(v))
}

private let stripThresholds = ladder(0.04, 0.96, 0.04)

private func modelEvaluationLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(stripThresholds, 0.72)), frame: { s in
        let t = stripThresholds[s.p]
        let c = countsOf(stripCases, t)
        let headline: String, body: String
        if c.fp == 0 && c.tp > 0 {
            headline = "At t = \(mx(t)) every flag is right, but {w:\(c.fn) of \(c.tp + c.fn)} positives are missed."
            body = "Precision is 1.00 and recall is \(mx(c.recall)). That suits a spam filter, where a false positive costs real mail."
        } else if c.fn == 0 {
            headline = "At t = \(mx(t)) every positive is caught, but {w:\(c.fp) of \(c.tp + c.fp)} flags are false alarms."
            body = "Recall is 1.00 and precision is \(mx(c.precision)). That suits screening, where a miss costs more than a second look."
        } else {
            headline = "At t = \(mx(t)) the model makes {w:\(c.fp) false alarm\(c.fp == 1 ? "" : "s")} and {w:\(c.fn) miss\(c.fn == 1 ? "" : "es")}."
            body = "Precision \(mx(c.precision)), recall \(mx(c.recall)), F1 \(mx(c.fBeta(1))). Find Best F1 picks the threshold that balances them."
        }
        return MtFrame(headline: headline, body: body, scene: .strip(t: t), matrix: c,
                      legend: [(negC, .dot, "Negative"), (posC, .dot, "Positive"), (SimColors.red, .ring, "Wrong")],
                      chips: [rateChip("P", c.precision), rateChip("R", c.recall), LabChip(key: "F1", value: mx(c.fBeta(1))), LabChip(key: "acc", value: mx(c.accuracy))])
    }, param: MtParam(name: "Threshold", symbol: "t", values: stripThresholds),
       action: { _ in "Find Best F1" },
       onAction: { var s = $0; s.p = stripThresholds.indices.max { countsOf(stripCases, stripThresholds[$0]).fBeta(1) < countsOf(stripCases, stripThresholds[$1]).fBeta(1) }!; return s })
}

private func precisionRecallLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(thresholds, 0.1)), frame: { s in
        let t = thresholds[s.p]
        let c = countsOf(cases, t)
        let flags = c.tp + c.fp
        let falseAlarms = flags == 0 ? 0 : Double(c.fp) / Double(flags)
        let headline: String
        let body = "Same model, same scores. Moving t only trades one error for the other, so it's a business decision."
        if abs(c.precision - c.recall) < 0.05 {
            headline = "At t = \(mx(t)) precision and recall {meet} near \(mx((c.precision + c.recall) / 2))."
        } else if falseAlarms > 0.3 {
            headline = "At t = \(mx(t)) recall is \(mx(c.recall)), but {w:\(pct(falseAlarms))} of flags are false alarms."
        } else {
            headline = "At t = \(mx(t)) precision is \(mx(c.precision)), but recall falls to {w:\(mx(c.recall))}."
        }
        return MtFrame(
            headline: headline,
            body: abs(c.precision - c.recall) < 0.05 ? "Below this t recall wins, above it precision does. The crossover is one reasonable default, not a rule." : body,
            scene: .curve(MtCurvePlot(series: [MtSeries(points: curve { $0.precision }, color: negC), MtSeries(points: curve { $0.recall }, color: posC)],
                                    markers: [MtMarker(x: t, y: c.precision, color: negC), MtMarker(x: t, y: c.recall, color: posC)],
                                    vline: t, xTitle: "threshold t")),
            formula: ["P = TP / (TP + FP) = \(c.tp) / \(flags) = {v:\(mx(c.precision, 3))}", "R = TP / (TP + FN) = \(c.tp) / \(nPos) = {v:\(mx(c.recall, 3))}"],
            legend: [(negC, .line, "Precision"), (posC, .line, "Recall"), thresholdLegend(t)],
            chips: [LabChip(key: "flags", value: "\(flags)"), LabChip(key: "false alarms", value: pct(falseAlarms), tint: falseAlarms > 0.3 ? .warn : nil)])
    }, param: MtParam(name: "Threshold", symbol: "t", values: thresholds),
       action: { _ in "Find Crossover" },
       onAction: { var s = $0; s.p = thresholds.indices.min { i, j in
           let a = countsOf(cases, thresholds[i]), b = countsOf(cases, thresholds[j])
           return abs(a.precision - a.recall) < abs(b.precision - b.recall)
       }!; return s })
}

private let accuracyThresholds = ladder(0.05, 1.0, 0.05)

private func accuracyLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(accuracyThresholds, 0.9)), frame: { s in
        let t = accuracyThresholds[s.p]
        let c = countsOf(cases, t)
        let baseline = Double(nNeg) / Double(nNeg + nPos)
        let headline: String, body: String
        if c.tp == 0 {
            headline = "Flag nobody and accuracy is still {\(mx(baseline, 3))}."
            body = "Recall is 0: the model catches no positives at all, yet it beats most thresholds on accuracy. That is why accuracy misleads on imbalanced data."
        } else if c.recall < 0.5 {
            headline = "At t = \(mx(t)) accuracy is \(mx(c.accuracy)), and recall is only {w:\(mx(c.recall))}."
            body = "Calling everyone negative already scores \(mx(baseline, 3)), because \(pct(baseline)) of cases are negative."
        } else {
            headline = "At t = \(mx(t)) accuracy is \(mx(c.accuracy)) and recall {m:\(mx(c.recall))}."
            body = "Accuracy barely moves across thresholds, because \(pct(baseline)) of cases are negative. Recall shows what it hides."
        }
        return MtFrame(
            headline: headline, body: body,
            scene: .curve(MtCurvePlot(series: [MtSeries(points: curve { $0.accuracy }, color: negC), MtSeries(points: curve { $0.recall }, color: posC),
                                             MtSeries(points: [(0, baseline), (1, baseline)], color: SimColors.grey, dashed: true, width: 1.5)],
                                    markers: [MtMarker(x: t, y: c.accuracy, color: negC), MtMarker(x: t, y: c.recall, color: posC)],
                                    vline: t, xTitle: "threshold t")),
            formula: ["acc = (TP + TN) / n = (\(c.tp) + \(c.tn)) / \(c.n) = {v:\(mx(c.accuracy, 3))}"],
            legend: [(negC, .line, "Accuracy"), (posC, .line, "Recall"), (SimColors.grey, .dashedLine, "Always “negative”")],
            chips: [LabChip(key: "acc", value: mx(c.accuracy, 3), tint: .path), LabChip(key: "recall", value: mx(c.recall, 3), tint: c.recall < 0.5 ? .warn : nil),
                    LabChip(key: "baseline", value: mx(baseline, 3))])
    }, param: MtParam(name: "Threshold", symbol: "t", values: accuracyThresholds),
       action: { _ in "Show Baseline" },
       onAction: { var s = $0; s.p = accuracyThresholds.count - 1; return s })
}

private let rocThresholds = ladder(0, 0.95, 0.05)

private func rocLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(rocThresholds, 0.2)), frame: { s in
        let t = rocThresholds[s.p]
        let c = countsOf(cases, t)
        let traced = stride(from: 100, through: Int((t * 100).rounded()), by: -1).map { i in
            let k = countsOf(cases, Double(i) / 100)
            return (k.fpr, k.recall)
        }
        let auc = Double(aucPairs.0) / Double(aucPairs.1)
        let full = t < 1e-9
        return MtFrame(
            headline: full ? "Traced end to end, the curve {hugs the top-left}: AUC \(mx(auc, 3))." : "Each threshold is {one point}. Lowering t moves up and to the right.",
            body: full ? "At t = 0 everything is flagged, so both rates reach 1. A useless model would follow the dashed diagonal."
                : "At t = \(mx(t)) the model catches \(pct(c.recall)) of positives for \(mx(c.fpr * 100, 1))% false alarms. The faint line is the path still to trace.",
            scene: .curve(MtCurvePlot(series: [MtSeries(points: [(0, 0), (1, 1)], color: SimColors.grey, dashed: true, width: 1.5),
                                             MtSeries(points: rocPoints, color: negC, faint: true), MtSeries(points: traced, color: negC)],
                                    markers: [MtMarker(x: c.fpr, y: c.recall, color: SimColors.active)],
                                    xTitle: "false positive rate", yTitle: "true positive rate")),
            formula: ["TPR = \(c.tp) / \(nPos) = {v:\(mx(c.recall, 3))}   FPR = \(c.fp) / \(nNeg) = {v:\(mx(c.fpr, 3))}"],
            legend: [(negC, .line, "Traced so far"), (SimColors.active, .dot, "t = \(mx(t))"), (SimColors.grey, .dashedLine, "Chance")])
    }, param: MtParam(name: "Threshold", symbol: "t", values: rocThresholds),
       action: { _ in "Trace Full Curve" },
       onAction: { var s = $0; s.p = 0; return s })
}

private let betas = [0.5, 1.0, 2.0]

private func f1Lab() -> MtLab {
    MtLab(initial: MtState(p: nearest(thresholds, 0.7), tab: 1), frame: { s in
        let t = thresholds[s.p]
        let c = countsOf(cases, t)
        let f = betas.map { c.fBeta($0) }
        let b = betas[s.tab]
        let name = s.tab == 0 ? "F0.5" : "F\(mx(b, 0))"
        let headline = s.tab == 0 ? "F0.5 weights precision {4×} as much as recall."
            : s.tab == 1 ? "F1 weights precision and recall {1 : 1}." : "F2 weights recall {4×} as much as precision."
        let p = mx(c.precision), r = mx(c.recall)
        return MtFrame(
            headline: headline,
            body: "At t = \(mx(t)) precision is \(p) and recall \(r). If a miss costs more, use β = 2: F2 is \(mx(f[2], 3)). β = 0.5 rewards the precision: \(mx(f[0], 3)).",
            scene: .bars(values: f, selected: s.tab),
            formula: ["Fβ = (1 + β²) PR / (β²P + R)",
                      s.tab == 1 ? "F1 = 2 × \(p) × \(r) / (\(p) + \(r)) = {v:\(mx(f[1], 3))}"
                          : "\(name) = \(mx(1 + b * b)) × \(p) × \(r) / (\(mx(b * b)) × \(p) + \(r)) = {v:\(mx(f[s.tab], 3))}"],
            chips: [LabChip(key: "P", value: p, tint: .path), LabChip(key: "R", value: r, tintColor: posC), LabChip(key: "t", value: mx(t))])
    }, param: MtParam(name: "Threshold", symbol: "t", values: thresholds), tabs: ["β = 0.5", "β = 1", "β = 2"], tabsInDock: true)
}

private func aucLab() -> MtLab {
    MtLab(initial: MtState(p: 0, tab: 1), frame: { s in
        let (right, all) = aucPairs
        let auc = Double(right) / Double(all)
        var trapezoid = 0.0
        for i in 1..<exactRoc.count {
            let a = exactRoc[i - 1], b = exactRoc[i]
            trapezoid += (b.0 - a.0) * (a.1 + b.1) / 2
        }
        let plot = MtCurvePlot(series: [MtSeries(points: [(0, 0), (1, 1)], color: SimColors.grey, dashed: true, width: 1.5), MtSeries(points: rocPoints, color: negC)],
                             xTitle: "false positive rate", yTitle: "true positive rate", fillUnder: rocPoints)
        if s.tab == 0 {
            return MtFrame(
                headline: "AUC is the {area} under the ROC curve.",
                body: "Trapezoids under each step of the curve add up to \(mx(trapezoid, 4)). A model that ranks at random scores 0.5, the area under the diagonal.",
                scene: .curve(plot), formula: ["AUC = Σ ½ (y₁ + y₂)(x₂ − x₁) = {v:\(mx(trapezoid, 4))}"])
        }
        var random = KotlinRandom(Int32(40 + s.flag))
        let posList = cases.filter(\.positive), negList = cases.filter { !$0.positive }
        let pos = posList[random.nextInt(posList.count)].score
        let neg = negList[random.nextInt(negList.count)].score
        let ranked = pos > neg
        return MtFrame(
            headline: ranked ? "AUC is the chance a random positive {outranks} a random negative." : "This pair is {w:ranked wrong}: the negative outscores the positive.",
            body: ranked ? "Counting all \(thousands(all)) pairs gives the same \(mx(auc, 4)) as the trapezoid area. No threshold needed."
                : "Only \(thousands(all - right)) of the \(thousands(all)) pairs go this way, which is why AUC is \(mx(auc, 4)).",
            scene: .curve(plot), pair: (pos, neg),
            formula: ["AUC = pairs ranked right / all pairs", "= \(thousands(right)) / \(thousands(all)) = {v:\(mx(auc, 4))}"])
    }, tabs: ["Trapezoid", "Pair counting"],
       action: { $0.tab == 0 ? "Count Pairs" : "Draw Another Pair" },
       onAction: { var s = $0; if s.tab == 0 { s.tab = 1 } else { s.flag += 1 }; return s })
}

private let predictions = [0.01] + ladder(0.06, 0.96, 0.05) + [0.99]

private func logLossLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(predictions, 0.51)), frame: { s in
        let p = predictions[s.p]
        let cost = -log(p)
        let pos = (1...99).map { Double($0) / 100 }.map { ($0, -log($0)) }
        let neg = (1...99).map { Double($0) / 100 }.map { ($0, -log(1 - $0)) }
        let headline: String, body: String
        if s.flag == 1 {
            headline = "Over all 1,000 cases the model averages {\(mx(modelLogLoss, 4))}."
            body = "Log loss is the mean of these costs, so a few confident mistakes dominate it. Accuracy would count them the same as near misses."
        } else if p < 0.3 {
            headline = "A positive at p = {\(mx(p))} costs {w:\(mx(cost))}: confident mistakes cost the most."
            body = "At t = 0.5 this is just one wrong answer, but log loss charges more the surer the model was."
        } else {
            headline = "A positive at p = {\(mx(p))} costs \(mx(cost)); at 0.99 it costs almost nothing."
            body = "Both count as correct at t = 0.5, but log loss scores the probability itself. Confident mistakes cost the most."
        }
        return MtFrame(
            headline: headline, body: body,
            scene: .curve(MtCurvePlot(series: [MtSeries(points: neg, color: negC.opacity(0.7)), MtSeries(points: pos, color: posC)],
                                    markers: [MtMarker(x: 0.05, y: -log(0.05), color: SimColors.red), MtMarker(x: 0.99, y: -log(0.99), color: SimColors.green),
                                              MtMarker(x: p, y: cost, color: SimColors.active)],
                                    xTitle: "predicted p", yTitle: "cost", yTop: "4", yMax: 4)),
            formula: ["positive: −log(\(mx(p))) = {v:\(mx(cost))}", "0.99 → \(mx(-log(0.99)))   0.05 → {w:\(mx(-log(0.05)))}"],
            legend: [(posC, .line, "−log p (positive)"), (negC, .line, "−log(1 − p) (negative)")],
            chips: s.flag == 1 ? [LabChip(key: "model log loss", value: mx(modelLogLoss, 4), tint: .path)] : [])
    }, param: MtParam(name: "Prediction", symbol: "p", values: predictions),
       action: { $0.flag == 0 ? "Score Whole Model" : "Back to One Case" },
       onAction: { var s = $0; s.flag = 1 - s.flag; return s })
}

private func kappaLab() -> MtLab {
    MtLab(initial: MtState(p: nearest(thresholds, 0.5)), frame: { s in
        let t = thresholds[s.p]
        let c = countsOf(cases, t)
        return MtFrame(
            headline: "Chance alone would get {\(mx(c.chance, 3))} right, so kappa only counts the part above that.",
            body: "Accuracy is \(mx(c.accuracy, 3)), but κ is \(mx(c.kappa, 3)). Across thresholds κ moves where accuracy stays flat.",
            scene: .curve(MtCurvePlot(series: [MtSeries(points: curve { $0.accuracy }, color: SimColors.grey), MtSeries(points: curve { max($0.kappa, 0) }, color: nil)],
                                    markers: [MtMarker(x: t, y: c.accuracy, color: SimColors.grey), MtMarker(x: t, y: c.kappa, color: SimColors.active)],
                                    vline: t, xTitle: "threshold t")),
            formula: ["κ = (p₀ − pₑ) / (1 − pₑ)", "= (\(mx(c.accuracy, 3)) − \(mx(c.chance, 3))) / (1 − \(mx(c.chance, 3))) = {v:\(mx(c.kappa, 3))}"],
            legend: [(SimColors.grey, .line, "Accuracy"), (nil, .line, "Kappa"), thresholdLegend(t)])
    }, param: MtParam(name: "Threshold", symbol: "t", values: thresholds),
       action: { _ in "Find Best κ" },
       onAction: { var s = $0; s.p = thresholds.indices.max { countsOf(cases, thresholds[$0]).kappa < countsOf(cases, thresholds[$1]).kappa }!; return s })
}

private func metricLab(_ topicId: String) -> MtLab {
    switch topicId {
    case "model_evaluation": modelEvaluationLab()
    case "precision_recall": precisionRecallLab()
    case "accuracy": accuracyLab()
    case "roc_curve": rocLab()
    case "f1_score": f1Lab()
    case "auc": aucLab()
    case "log_loss": logLossLab()
    case "cohens_kappa": kappaLab()
    default: confusionLab()
    }
}

// MARK: - Lab

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class MetricModel {
    let lab: MtLab
    var state: MtState { didSet { frame = lab.frame(state) } }
    private(set) var frame: MtFrame

    init(lab: MtLab) {
        self.lab = lab
        state = lab.initial
        frame = lab.frame(lab.initial)
    }
}

struct MetricStoryLab: View {
    @State private var model: MetricModel
    @Environment(\.labDock) private var dock
    @Environment(\.palette) private var palette

    init(topicId: String) {
        _model = State(initialValue: MetricModel(lab: metricLab(topicId)))
    }

    var body: some View {
        let lab = model.lab
        let frame = model.frame
        VStack(alignment: .leading, spacing: 0) {
            if !lab.tabs.isEmpty && !lab.tabsInDock {
                LabSegments(labels: lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0 })).padding(.bottom, 14)
            }
            LabCard {
                switch frame.scene {
                case let .hist(t, byOutcome): HistView(t: t, byOutcome: byOutcome)
                case .strip(let t): StripView(t: t)
                case .curve(let plot): MetricCurveView(plot: plot)
                case let .bars(values, selected): FBarsView(values: values, selected: selected)
                }
                if let m = frame.matrix { MatrixView(c: m).padding(.top, 14) }
                if let pair = frame.pair { PairRow(pair: pair).padding(.top, 12) }
                if !frame.formula.isEmpty { MetricFormula(lines: frame.formula).padding(.top, 12) }
                if !frame.legend.isEmpty {
                    StoryLegendRow(items: frame.legend.map { ($0.color ?? palette.primary, $0.style, $0.label) }).padding(.top, 14)
                }
            }
            if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                MetricControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(MetricControls(model: model)) }
        }
        .onDisappear { dock?.controls = nil }
    }
}

private struct MetricControls: View {
    let model: MetricModel

    var body: some View {
        let lab = model.lab
        let s = model.state
        VStack(spacing: 14) {
            if lab.tabsInDock {
                LabSegments(labels: lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0 }))
            }
            if let p = lab.param {
                LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: mx(p.values[s.p]),
                                                canDecrease: s.p > 0, canIncrease: s.p < p.values.count - 1)) { d in
                    model.state.p = min(max(model.state.p + d, 0), p.values.count - 1)
                }
            }
            if let label = lab.action {
                LabButton(label: label(s), primary: true) { model.state = lab.onAction(model.state) }
            }
        }
    }
}

// MARK: - Rendering

private struct Stage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct MetricFormula: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 2) {
            ForEach(lines.indices, id: \.self) { i in
                storyText(lines[i], palette).font(AppFont.mono(13)).multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(palette.onSurface.opacity(0.85))
        .lineSpacing(3)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

private let histBins: [(Int, Int)] = (0..<20).map { b in
    let lo = Double(b) / 20, hi = Double(b + 1) / 20
    let inBin = cases.filter { $0.score >= lo && ($0.score < hi || (b == 19 && $0.score <= hi)) }
    return (inBin.filter { !$0.positive }.count, inBin.filter(\.positive).count)
}

private struct HistView: View {
    let t: Double
    let byOutcome: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = 34, right = size.width - 12, top: CGFloat = 12, bottom = size.height - 24
            let peak = Double(histBins.map { max($0.0, $0.1) }.max() ?? 1).squareRoot()
            let slot = (right - left) / 20, bw = slot * 0.36
            for (b, bin) in histBins.enumerated() {
                let x = left + CGFloat(b) * slot
                let above = (Double(b) + 0.5) / 20 >= t
                func bar(_ n: Int, _ dx: CGFloat, _ color: Color) {
                    guard n > 0 else { return }
                    let h = CGFloat(Double(n).squareRoot() / peak) * (bottom - top)
                    ctx.fill(Path(CGRect(x: x + dx, y: bottom - h, width: bw, height: h)), with: .color(color))
                }
                bar(bin.0, slot * 0.1, !byOutcome ? negC : above ? SimColors.red : SimColors.green)
                bar(bin.1, slot * 0.1 + bw, !byOutcome ? posC : above ? SimColors.green : SimColors.red)
            }
            let tx = left + CGFloat(t) * (right - left)
            ctx.line(CGPoint(x: tx, y: top), CGPoint(x: tx, y: bottom), color: palette.primary, width: 3)
            func text(_ s: String, _ at: CGPoint) { ctx.draw(Text(s).font(AppFont.mono(11)).foregroundColor(palette.muted), at: at) }
            text("0", CGPoint(x: left - 12, y: bottom))
            text("0", CGPoint(x: left, y: bottom + 13))
            text("1", CGPoint(x: right, y: bottom + 13))
            text("score", CGPoint(x: (left + right) / 2, y: bottom + 13))
            var rotated = ctx
            rotated.translateBy(x: 12, y: (top + bottom) / 2)
            rotated.rotate(by: .degrees(-90))
            rotated.draw(Text("√count").font(AppFont.mono(11)).foregroundColor(palette.muted), at: .zero)
        }
        .aspectRatio(1.6, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct StripView: View {
    let t: Double
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = 22, right = size.width - 22
            func px(_ s: Double) -> CGFloat { left + CGFloat(s) * (right - left) }
            let rowNeg = size.height * 0.34, rowPos = size.height * 0.64
            let tx = px(t)
            ctx.fill(Path(CGRect(x: tx, y: 0, width: size.width - tx, height: size.height)), with: .color(posC.opacity(0.14)))
            ctx.draw(Text("Negative").font(AppFont.sans(12)).foregroundColor(palette.muted), at: CGPoint(x: left, y: rowNeg - 18), anchor: .leading)
            ctx.draw(Text("Positive").font(AppFont.sans(12)).foregroundColor(palette.muted), at: CGPoint(x: left, y: rowPos - 18), anchor: .leading)
            ctx.line(CGPoint(x: left, y: rowNeg), CGPoint(x: right, y: rowNeg), color: palette.outline, width: 1)
            ctx.line(CGPoint(x: left, y: rowPos), CGPoint(x: right, y: rowPos), color: palette.outline, width: 1)
            ctx.line(CGPoint(x: tx, y: 0), CGPoint(x: tx, y: size.height), color: palette.primary, width: 3)
            ctx.draw(Text("flag ≥ \(mx(t))").font(AppFont.mono(12, .bold)).foregroundColor(violetInk),
                     at: CGPoint(x: min(tx + 6, size.width - 90), y: 8), anchor: .topLeading)
            for c in stripCases {
                let center = CGPoint(x: px(c.score), y: c.positive ? rowPos : rowNeg)
                let dot = Path(ellipseIn: CGRect(x: center.x - 6, y: center.y - 6, width: 12, height: 12))
                ctx.fill(dot, with: .color(c.positive ? posC : negC))
                ctx.stroke(dot, with: .color(palette.surface), lineWidth: 1)
                if (c.score >= t) != c.positive {
                    ctx.stroke(Path(ellipseIn: CGRect(x: center.x - 10, y: center.y - 10, width: 20, height: 20)), with: .color(SimColors.red), lineWidth: 2)
                }
            }
            for (v, s) in [(0.0, "0"), (0.5, "0.5"), (1.0, "1")] {
                ctx.draw(Text(s).font(AppFont.mono(11)).foregroundColor(palette.muted), at: CGPoint(x: px(v), y: size.height - 12))
            }
            ctx.draw(Text("model score").font(AppFont.mono(11)).foregroundColor(palette.muted), at: CGPoint(x: px(0.78), y: size.height - 12))
        }
        .aspectRatio(1.75, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct MatrixView: View {
    let c: MtCounts
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Color.clear.frame(width: 68, height: 1)
                Text("Predicted +").frame(maxWidth: .infinity, alignment: .leading)
                Text("Predicted −").frame(maxWidth: .infinity, alignment: .leading)
            }
            .font(AppFont.sans(14)).foregroundStyle(palette.muted)
            ForEach(0..<2, id: \.self) { row in
                let cells = row == 0 ? [("TP", c.tp), ("FN", c.fn)] : [("FP", c.fp), ("TN", c.tn)]
                HStack(spacing: 8) {
                    Text(row == 0 ? "Actual +" : "Actual −").font(AppFont.sans(14)).foregroundStyle(palette.muted).frame(width: 68, alignment: .leading)
                    ForEach(cells.indices, id: \.self) { i in
                        let (key, n) = cells[i]
                        let good = key == "TP" || key == "TN"
                        HStack {
                            Text(key).font(AppFont.mono(14, .bold)).foregroundStyle((good ? StoryTone.done : StoryTone.warn).ink(palette))
                            Spacer()
                            Text("\(n)").font(AppFont.mono(19, .bold))
                        }
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background((good ? SimColors.green : SimColors.red).opacity(0.18), in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
        }
    }
}

private struct PairRow: View {
    let pair: (Double, Double)
    @Environment(\.palette) private var palette

    var body: some View {
        let ranked = pair.0 > pair.1
        HStack(spacing: 10) {
            (Text("positive ").foregroundColor(pinkInk) + Text(mx(pair.0, 3)).fontWeight(.bold))
                .font(AppFont.mono(15)).frame(maxWidth: .infinity).frame(height: 38)
                .background(posC.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
            Text(ranked ? ">" : "<").font(AppFont.mono(18, .bold)).foregroundStyle((ranked ? StoryTone.done : StoryTone.warn).ink(palette))
            (Text("negative ").foregroundColor(StoryTone.path.ink(palette)) + Text(mx(pair.1, 3)).fontWeight(.bold))
                .font(AppFont.mono(15)).frame(maxWidth: .infinity).frame(height: 38)
                .background(negC.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
        }
    }
}

private struct MetricCurveView: View {
    let plot: MtCurvePlot
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = plot.yTitle != nil ? 44 : 30, right = size.width - 14, top: CGFloat = 14, bottom = size.height - 26
            func px(_ x: Double) -> CGFloat { left + CGFloat(x) * (right - left) }
            func py(_ y: Double) -> CGFloat { bottom - CGFloat(min(max(y, 0), plot.yMax) / plot.yMax) * (bottom - top) }
            ctx.line(CGPoint(x: left, y: top), CGPoint(x: left, y: bottom), color: palette.outline, width: 1)
            ctx.line(CGPoint(x: left, y: bottom), CGPoint(x: right, y: bottom), color: palette.outline, width: 1)
            if let fill = plot.fillUnder, let first = fill.first, let last = fill.last {
                var p = Path()
                p.move(to: CGPoint(x: px(first.0), y: bottom))
                for (x, y) in fill { p.addLine(to: CGPoint(x: px(x), y: py(y))) }
                p.addLine(to: CGPoint(x: px(last.0), y: bottom))
                p.closeSubpath()
                ctx.fill(p, with: .color(negC.opacity(0.2)))
            }
            if let v = plot.vline {
                var p = Path(); p.move(to: CGPoint(x: px(v), y: top)); p.addLine(to: CGPoint(x: px(v), y: bottom))
                ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
            }
            for s in plot.series where s.points.count > 1 {
                var p = Path()
                for (i, pt) in s.points.enumerated() {
                    let c = CGPoint(x: px(pt.0), y: py(pt.1))
                    if i == 0 { p.move(to: c) } else { p.addLine(to: c) }
                }
                let base = s.color ?? palette.primary
                ctx.stroke(p, with: .color(s.faint ? base.opacity(0.3) : base),
                           style: StrokeStyle(lineWidth: s.faint ? 1.5 : s.width, dash: s.dashed ? [5, 4] : []))
            }
            for m in plot.markers {
                let dot = Path(ellipseIn: CGRect(x: px(m.x) - 6, y: py(m.y) - 6, width: 12, height: 12))
                ctx.fill(dot, with: .color(m.color))
                ctx.stroke(dot, with: .color(palette.surface), lineWidth: 1.5)
            }
            func text(_ s: String, _ at: CGPoint) { ctx.draw(Text(s).font(AppFont.mono(11)).foregroundColor(palette.muted), at: at) }
            text(plot.yTop, CGPoint(x: left - 10, y: top))
            text("0", CGPoint(x: left - 10, y: bottom))
            text("0", CGPoint(x: left, y: bottom + 13))
            text("1", CGPoint(x: right, y: bottom + 13))
            text(plot.xTitle, CGPoint(x: (left + right) / 2, y: bottom + 13))
            if let yTitle = plot.yTitle {
                var rotated = ctx
                rotated.translateBy(x: 14, y: (top + bottom) / 2)
                rotated.rotate(by: .degrees(-90))
                rotated.draw(Text(yTitle).font(AppFont.mono(11)).foregroundColor(palette.muted), at: .zero)
            }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct FBarsView: View {
    let values: [Double]
    let selected: Int
    @Environment(\.palette) private var palette

    var body: some View {
        let names = ["F0.5", "F1", "F2"]
        HStack(alignment: .bottom, spacing: 12) {
            ForEach(values.indices, id: \.self) { i in
                let on = i == selected
                VStack(spacing: 6) {
                    Text(mx(values[i], 3)).font(AppFont.mono(16, .bold)).foregroundStyle(on ? violetInk : palette.onSurface)
                    RoundedRectangle(cornerRadius: 10).fill(on ? palette.primary : SimColors.tint)
                        .frame(height: max(90 * CGFloat(values[i]), 4))
                    Text(names[i]).font(AppFont.mono(13)).foregroundStyle(on ? violetInk : palette.muted)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 150, alignment: .bottom)
    }
}
