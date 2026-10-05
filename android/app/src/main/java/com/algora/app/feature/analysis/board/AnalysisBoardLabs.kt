package com.algora.app.feature.analysis.board

import com.algora.app.feature.topics.Rb
import com.algora.app.feature.topics.lsExp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ── The Analysis tools' frames ───────────────────────────────────────────────
// Operation counter to sandbox, each a function of its settings (and, for the two timing tools, the
// on-device benchmark). Counts come from running the algorithms; the iOS port (AnalysisBoardLabs.swift)
// matches every number.

/** The Analysis topics drawn as boards; Benchmark Dashboard and Complexity Class Comparison keep their tools. */
internal val analysisBoardIds = setOf(
    "operation_counter", "cost_profiler", "parameterized_input_generator", "growth_curve_chart", "growth_class_comparison",
    "best_case", "worst_case", "average_case", "real_vs_predicted_runtime", "input_size_scaling", "amortized_analysis",
    "space_time_tradeoffs", "master_theorem", "complexity_map", "sandbox_mode",
)

internal fun analysisBoardLab(topicId: String): AnLab? = when (topicId) {
    "operation_counter" -> opCounter()
    "cost_profiler" -> costProfiler()
    "parameterized_input_generator" -> inputGenerator()
    "growth_curve_chart" -> growthCurves()
    "growth_class_comparison" -> logLinearExp()
    "best_case" -> cases(0)
    "worst_case" -> cases(1)
    "average_case" -> cases(2)
    "real_vs_predicted_runtime" -> realVsPredicted()
    "input_size_scaling" -> sizeScaling()
    "amortized_analysis" -> amortized()
    "space_time_tradeoffs" -> spaceTime()
    "master_theorem" -> masterTheorem()
    "complexity_map" -> complexityMap()
    "sandbox_mode" -> sandbox()
    else -> null
}

// ── The design's palette and helpers ──

private const val B = "#3b82f6"
private const val Bl = "#8fb6ff"
private const val P = "#6d5dfc"
private const val Pl = "#b3abff"
private const val G = "#22a06b"
private const val Gl = "#5fd09f"
private const val Y = "#f5c542"
private const val R = "#e5484d"
private const val Rl = "#ff8a8e"
private const val K = "#e5337a"
private const val Kl = "#f47aa8"
private const val N = "#3a3f4c"
private const val ON = "#f2f3f7"
private const val DIM = "#9aa0ae"

/** The design's rng: `s = seed·2654435761 mod (2³¹ − 1)` (7 if that is 0), then Park–Miller, in [0, 1). */
internal fun anRng(seed: Long): () -> Double {
    var s = ((seed * 2654435761L) % 2147483647L).let { if (it == 0L) 7L else it }
    return {
        s = (s * 16807L) % 2147483647L
        (s - 1) / 2147483646.0
    }
}

private fun fx(x: Double, d: Int) = Rb.fixed(x, d)

/** JavaScript's toFixed, sign included. */
private fun tf(x: Double, d: Int) = (if (x < 0) "-" else "") + Rb.fixed(abs(x), d)

/** `Math.round(x).toLocaleString('en-US')`: grouped, with an ASCII minus as the design prints it. */
private fun num(x: Double): String {
    val r = Rb.round(x).toLong()
    val g = kotlin.math.abs(r).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (r < 0) "-$g" else g
}

/** The design's big(): 12.5K, 1.05M; under 10,000 a rounded, grouped number. */
private fun big(n: Double): String {
    fun g(x: Double): String {
        val s = fx(x, if (x < 10) 2 else if (x < 100) 1 else 0)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
    return when {
        n >= 1e12 -> g(n / 1e12) + "T"
        n >= 1e9 -> g(n / 1e9) + "B"
        n >= 1e6 -> g(n / 1e6) + "M"
        n >= 1e4 -> g(n / 1e3) + "K"
        else -> num(n)
    }
}

private fun js(x: Double) = Rb.js(x)

private fun perm(n: Int, r: () -> Double): IntArray {
    val a = IntArray(n) { it }
    for (i in n - 1 downTo 1) {
        val j = floor(r() * (i + 1)).toInt()
        val t = a[i]; a[i] = a[j]; a[j] = t
    }
    return a
}

/** Sorted, reversed, random or nearly sorted (a few neighbours swapped). */
private fun shape(sh: Int, n: Int, seed: Int): IntArray {
    val r = anRng(seed.toLong() + n * 31L + sh)
    return when (sh) {
        0 -> IntArray(n) { it }
        1 -> IntArray(n) { n - 1 - it }
        2 -> perm(n, r)
        else -> {
            val a = IntArray(n) { it }
            repeat(max(1, n / 6)) {
                val i = floor(r() * (n - 1)).toInt()
                val t = a[i]; a[i] = a[i + 1]; a[i + 1] = t
            }
            a
        }
    }
}

private fun insC(x: IntArray): Int {
    val a = x.copyOf()
    var c = 0
    for (i in 1 until a.size) {
        val k = a[i]
        var j = i - 1
        while (j >= 0) {
            c++
            if (a[j] > k) { a[j + 1] = a[j]; j-- } else break
        }
        a[j + 1] = k
    }
    return c
}

private fun bubC(x: IntArray): Int {
    val a = x.copyOf()
    val n = a.size
    var c = 0
    for (i in 0 until n - 1) {
        var sw = false
        for (j in 0 until n - 1 - i) {
            c++
            if (a[j] > a[j + 1]) { val t = a[j]; a[j] = a[j + 1]; a[j + 1] = t; sw = true }
        }
        if (!sw) break
    }
    return c
}

private fun selC(x: IntArray): Int {
    val a = x.copyOf()
    val n = a.size
    var c = 0
    for (i in 0 until n - 1) {
        var m = i
        for (j in i + 1 until n) { c++; if (a[j] < a[m]) m = j }
        val t = a[i]; a[i] = a[m]; a[m] = t
    }
    return c
}

private fun linC(a: IntArray, t: Int): Int {
    var c = 0
    for (i in a.indices) { c++; if (a[i] == t) break }
    return c
}

private fun tok(t: String, k: String = "plain") = when (k) {
    "cur" -> AnTok(t, "rgba(245,197,66,.2)", Y)
    "fut" -> AnTok(t, "#1f232d", "#6b7180")
    else -> AnTok(t, "#2a2e39", ON)
}

private fun row(w: String, v: String, frac: Double, bar: String, wc: String = "#c3c7d1", mk: Double? = null) =
    AnRow(w, v, max(0.0, min(frac, 1.0)), bar, wc, mk)

private fun L(t: String) = AnBlock.Label(t)
private fun F(a: String, b: String = "", c: String = ON) = AnFx(a, b, c)
private fun bigs(vararg items: Triple<String, String, String>) = AnBlock.Big(items.map { AnBig(it.first, it.second, it.third) })

/** The design's V(): bars scaled to the tallest, at least 2pt. */
private class VIn(val label: String, val v: String, val vals: List<Double>, val bg: List<String>? = null, val ring: List<String>? = null)

private fun vcols(cols: List<VIn>, h: Double, bg: String = G, gap: Int = 6, bw: Int? = 26, axis: String = ""): AnBlock.Cols {
    val mx = max(cols.flatMap { it.vals }.maxOrNull() ?: 0.0, 1e-9)
    return AnBlock.Cols(cols.map { c ->
        AnVCol(c.label, c.v, c.vals.mapIndexed { i, v -> AnVBar(max(2.0, v / mx * h), c.bg?.getOrNull(i) ?: bg, c.ring?.getOrNull(i) ?: "none") })
    }, gap, bw, axis)
}

private fun frame(title: String, blocks: List<AnBlock>, fx: List<AnFx>, cap: Pair<String, String>, dock: AnDock, legend: List<Triple<String, String, String>> = emptyList()) =
    AnFrame(title, blocks, fx, cap.first, cap.second, legend, dock)

private fun lg(c: String, label: String, ring: String = "none") = Triple(c, label, ring)

private fun log2(x: Double) = ln(x) / ln(2.0)

// ── 57a Operation Counter ──

private fun opCounter() = AnLab(AnVals(opt = 0, ni = 1, seed = 1)) { s, _ ->
    val ns = listOf(8, 16, 32, 64, 128, 256, 512, 1024)
    val n = ns[s.ni]
    val bin = s.opt == 1
    val t = floor(anRng(s.seed * 7919L + n)() * n).toInt()
    val lc = t + 1
    var lo = 0
    var hi = n - 1
    val pr = ArrayList<Int>()
    while (lo <= hi) {
        val m = (lo + hi) shr 1
        pr += m
        if (m == t) break
        if (m < t) lo = m + 1 else hi = m - 1
    }
    val bc = pr.size
    val wb = floor(log2(n.toDouble())).toInt() + 1
    val cw = 1.0 / n
    val cells = if (bin) pr.mapIndexed { i, p -> AnCell(p.toDouble() / n, cw, if (i == pr.lastIndex) Y else Pl) }
    else listOf(AnCell(0.0, t.toDouble() / n, "rgba(59,130,246,.5)"), AnCell(t.toDouble() / n, cw, Y))
    frame("Operation Counter", listOf(
        L("sorted array · target at index $t"),
        AnBlock.Strip(cells, "index 0", "index ${n - 1}"),
        bigs(Triple("${if (bin) bc else lc}", "comparisons, this run", if (bin) Pl else Bl), Triple("${if (bin) wb else n}", "worst case at n = $n", ON)),
        L("same target, both searches"),
        AnBlock.Bars(listOf(row("Linear", "$lc", lc.toDouble() / n, B, if (bin) DIM else ON), row("Binary", "$bc", bc.toDouble() / n, P, if (bin) ON else DIM))),
    ), listOf(F("linear worst = n =", "$n", Bl), F("binary worst = ⌊log₂ n⌋ + 1 =", "$wb", Pl)),
        if (bin) "$bc probes to find index $t." to "Each probe halves what's left. From n = 8 to 1,024 the worst case grows by only ${wb - 4} probes."
        else "$lc comparisons — one per element up to index $t." to "Linear search checks left to right. Double n and the worst case doubles.",
        AnDock(
            listOf(AnSeg(listOf("Linear search", "Binary search"), s.opt) { v, i -> v.copy(opt = i) }),
            AnSlider("Array size", ns.map { "n = $it" }),
            AnBtn("Run on a new target") { it.copy(seed = it.seed + 1) },
        ),
        if (bin) listOf(lg(Pl, "Probed"), lg(Y, "Found")) else listOf(lg("rgba(59,130,246,.5)", "Checked"), lg(Y, "Found")),
    )
}

// ── 57b Cost Profiler ──

private fun costProfiler() = AnLab(AnVals(opt = 1, ni = 3)) { s, _ ->
    val mxs = listOf(8, 16, 24, 32, 48, 64)
    val mx = mxs[s.ni]
    val alg = s.opt
    val col = listOf(B, Y, K)[alg]
    val pts = (0 until 8).map { Rb.round(mx * (it + 1) / 8.0).toInt() }
    fun cnt(n: Int): Double {
        var tot = 0.0
        for (k in 0 until 5) {
            val r = anRng(101L + k * 13 + n)
            val a = perm(n, r)
            tot += when (alg) { 0 -> linC(a, floor(r() * n).toInt()); 1 -> insC(a); else -> bubC(a) }
        }
        return tot / 5
    }
    val ops = pts.map { cnt(it) }
    val ratio = ops[7] / ops[3]
    frame("Cost Profiler", listOf(
        L("operations at 8 sizes up to n = $mx · random input, mean of 5 runs"),
        vcols(pts.mapIndexed { i, n -> VIn("$n", big(ops[i]), listOf(ops[i])) }, 150.0, bg = col, axis = "n →"),
    ), listOf(F("ops($mx) ÷ ops(${mx / 2}) =", fx(ratio, 2) + "×", if (col == B) Bl else col), F("model:", listOf("≈ n / 2", "≈ n² / 4", "≤ n(n − 1) / 2")[alg])),
        if (alg == 0) "Linear: double n, double the work." to "The bars climb in a straight line — ratio ${fx(ratio, 2)}×, close to 2."
        else "Quadratic: double n, four times the work." to (if (alg == 2) "Ratio ${fx(ratio, 2)}×. Bubble sort compares nearly every pair, so it sits above insertion." else "Ratio ${fx(ratio, 2)}×. Insertion stops shifting early, averaging about n²/4."),
        AnDock(listOf(AnSeg(listOf("Linear", "Insertion", "Bubble"), s.opt) { v, i -> v.copy(opt = i) }), AnSlider("Largest n", mxs.map { "n = $it" })),
    )
}

// ── 57c Input Generator ──

private val SH4 = listOf("Sorted", "Reversed", "Random", "Nearly")

private fun inputGenerator() = AnLab(AnVals(opt = 2, ni = 4, seed = 3)) { s, _ ->
    val ns = listOf(4, 6, 8, 10, 12, 14, 16)
    val n = ns[s.ni]
    val sh = s.opt
    val arr = shape(sh, n, s.seed)
    val cmp = insC(arr)
    val all = (0..3).map { insC(shape(it, n, s.seed)) }
    val w = n * (n - 1) / 2
    val cap = listOf(
        "Already sorted: one check per element." to "The inner loop breaks on its first comparison every time.",
        "Reversed: every pair gets compared." to "Each new element shifts all the way left — the true worst case.",
        "Random: about half the worst case." to "Expected ≈ n²/4 comparisons; this input took $cmp. Generate another to see it move.",
        "Nearly sorted: close to the best case." to "Only the few swapped neighbours move — why insertion sort finishes off faster sorts.",
    )[sh]
    frame("Input Generator", listOf(
        L("${SH4[sh].lowercase()} input · yellow = smaller than its left neighbour"),
        AnBlock.Chips(arr.mapIndexed { i, v -> tok("$v", if (i > 0 && v < arr[i - 1]) "cur" else "plain") }),
        bigs(Triple("$cmp", "insertion-sort comparisons", Pl)),
        L("same n = $n, all four shapes"),
        AnBlock.Bars(all.mapIndexed { k, v -> row(SH4[k], "$v", v.toDouble() / w, if (k == sh) P else N, if (k == sh) ON else DIM) }),
    ), listOf(F("sorted = n − 1 =", "${n - 1}", Gl), F("reversed = n(n − 1) / 2 =", "$w", Rl)), cap,
        AnDock(listOf(AnSeg(SH4, s.opt) { v, i -> v.copy(opt = i) }), AnSlider("Array size", ns.map { "n = $it" }), AnBtn("Generate new input") { it.copy(seed = it.seed + 1) }),
    )
}

// ── Growth curves ──

private class Cls(val n: String, val f: (Double) -> Double, val c: String)

private fun classes(withExp: Boolean): List<Cls> {
    fun l2(n: Double) = max(1.0, log2(n))
    val a = listOf(
        Cls("O(1)", { 1.0 }, B), Cls("O(log n)", { l2(it) }, G), Cls("O(n)", { it }, Y),
        Cls("O(n log n)", { it * l2(it) }, "#8f84ff"), Cls("O(n²)", { it * it }, R),
    )
    return if (withExp) a + Cls("O(2ⁿ)", { 2.0.pow(it) }, K) else a
}

private fun curves(cls: List<Cls>, mx: Int, log: Boolean, h: Int): AnBlock.Lines {
    val w = 333.0
    val pad = 10.0
    val top = cls.maxOf { it.f(mx.toDouble()) }
    val ymax = if (log) log10(top) else top
    fun x(n: Double) = pad + (n - 1) / (mx - 1) * (w - 2 * pad)
    fun y(v: Double) = h - pad - (if (log) log10(max(v, 1.0)) / ymax else v / ymax) * (h - 2 * pad)
    val paths = cls.map { c -> AnPath((0..60).map { i -> val n = 1 + (mx - 1) * i / 60.0; x(n) to y(c.f(n)) }, c.c) }
    val ticks = if (log) (0..floor(ymax).toInt()).map { 10.0.pow(it) } else listOf(0.0, .25, .5, .75, 1.0).map { it * ymax }
    return AnBlock.Lines(h, w, paths, ticks.map { AnGrid(y(it), big(it)) })
}

// ── 57d Growth Curve Chart ──

private fun growthCurves() = AnLab(AnVals(ni = 2)) { s, _ ->
    val mxs = listOf(10, 20, 50, 100, 200, 500)
    val mx = mxs[s.ni]
    val cl = classes(false)
    val m = mx.toDouble()
    val top = log10(m * m)
    val r = m * m / (m * log2(m))
    frame("Growth Curve Chart", listOf(
        L("operations vs n · y-axis log-scaled"), curves(cl, mx, true, 180), L("at n = $mx"),
        AnBlock.Bars(cl.map { c -> row(c.n, "≈ " + big(c.f(m)), max(log10(c.f(m)), 0.02) / top, c.c, ON) }, 88, 70),
    ), listOf(F("n² ÷ n log n at n = $mx =", fx(r, 1) + "×", Rl)),
        "At n = $mx, O(n²) is ${fx(r, 1)}× O(n log n)." to "Slide n up and the gap keeps widening — the curves never cross back.",
        AnDock(slider = AnSlider("Max n", mxs.map { "n = $it" })),
    )
}

// ── 57e Log vs Linear vs Exponential ──

private fun logLinearExp() = AnLab(AnVals(opt = 0, ni = 3)) { s, _ ->
    val ns = listOf(5, 10, 15, 20, 25, 30)
    val n = ns[s.ni]
    val log = s.opt == 0
    val cl = classes(true)
    val nd = n.toDouble()
    val mxv = 2.0.pow(nd)
    val top = log10(mxv)
    frame("Log vs Linear vs Exponential", listOf(
        L(if (log) "log scale — each gridline is ×10" else "linear scale — true magnitudes"), curves(cl, n, log, 180), L("at n = $n"),
        AnBlock.Bars(cl.map { c -> row(c.n, big(c.f(nd)), if (log) max(log10(c.f(nd)), 0.02) / top else c.f(nd) / mxv, c.c, ON) }, 88, 64),
    ), listOf(F("2ⁿ ÷ n² at n = $n =", big(mxv / (nd * nd)) + "×", Kl)),
        if (log) "Log scale: every curve readable." to "Equal steps up are ×10, so shapes separate — but the distances understate the gaps."
        else "Linear scale: 2ⁿ reaches ${big(mxv)}." to "Everything polynomial is flattened against the floor. This is what “exponential” means.",
        AnDock(listOf(AnSeg(listOf("Log scale", "Linear scale"), s.opt) { v, i -> v.copy(opt = i) }), AnSlider("n", ns.map { "n = $it" })),
    )
}

// ── 57f Best / Worst / Average ──

private fun cases(start: Int) = AnLab(AnVals(opt = start, ni = 1)) { s, _ ->
    val ns = listOf(4, 8, 12, 16, 20, 24, 32)
    val n = ns[s.ni]
    val k = s.opt
    val best = n - 1
    val worst = n * (n - 1) / 2
    var tot = 0.0
    for (q in 0 until 200) tot += insC(perm(n, anRng(900L + q)))
    val avg = tot / 200
    val sample = perm(n, anRng(900))
    val inp = listOf(IntArray(n) { it }, IntArray(n) { n - 1 - it }, sample)[k]
    val show = inp.take(if (n > 16) 15 else n).mapIndexed { i, v -> tok("$v", if (i > 0 && v < inp[i - 1]) "cur" else "plain") } +
        (if (n > 16) listOf(tok("…", "fut")) else emptyList())
    val v = listOf(best.toDouble(), worst.toDouble(), avg)[k]
    val col = listOf(Gl, Rl, Y)[k]
    frame(listOf("Best Case", "Worst Case", "Average Case")[k], listOf(
        L(listOf("sorted input", "reversed input", "one of 200 random inputs")[k]),
        AnBlock.Chips(show),
        bigs(
            Triple(if (k == 2) fx(avg, 1) else js(v), if (k == 2) "mean comparisons, 200 runs" else "comparisons", col),
            Triple(fx(v / best, 1) + "×", "vs best case", ON),
        ),
        L("same n = $n, all three cases"),
        AnBlock.Bars(listOf(
            row("Best", "$best", best.toDouble() / worst, G, if (k == 0) ON else DIM),
            row("Average", fx(avg, 1), avg / worst, Y, if (k == 2) ON else DIM),
            row("Worst", "$worst", 1.0, R, if (k == 1) ON else DIM),
        )),
    ), listOf(listOf(F("n − 1 =", "$best", Gl)), listOf(F("n(n − 1) / 2 =", "$worst", Rl)), listOf(F("≈ n² / 4 =", js(n * n / 4.0), Y)))[k],
        listOf(
            "Sorted input: the fastest case." to "The inner loop breaks on its first check every time — linear.",
            "Reversed input: the true worst case." to "Every element shifts past all the others: ${fx(worst.toDouble() / best, 1)}× the best case at this n.",
            "Random input: about half the worst." to "Averaged over 200 inputs. Typical still grows as n² — the constant is just smaller.",
        )[k],
        AnDock(listOf(AnSeg(listOf("Best", "Average", "Worst"), listOf(0, 2, 1)[k]) { vv, i -> vv.copy(opt = listOf(0, 2, 1)[i]) }), AnSlider("Array size", ns.map { "n = $it" })),
    )
}

// ── Timing ──

private val NS_BENCH = listOf(250, 500, 1000, 2000, 4000)

/** µs below a millisecond, ms above. */
private fun fT(us: Double) = if (us >= 1000) fx(us / 1000, if (us >= 10000) 1 else 2) + " ms" else fx(us, if (us < 10) 2 else 1) + " µs"

private fun benchWaiting(title: String, dock: AnDock) = frame(title, listOf(L("Timing sorts on this device…")), listOf(F("n =", "250 → 4,000")),
    "Benchmark running." to "Each size is timed three times; the median is kept.", dock)

// ── 57g Real vs Predicted ──

private fun realVsPredicted() = AnLab(AnVals(opt = 0), bench = true) { s, bench ->
    val dock = AnDock(listOf(AnSeg(listOf("Insertion sort", "Bubble sort"), s.opt) { v, i -> v.copy(opt = i) }), btn = AnBtn("Run benchmark", bench = true))
    val t = bench?.get(listOf("ins", "bub")[s.opt]) ?: return@AnLab benchWaiting("Real vs Predicted Runtime", dock)
    val ns = NS_BENCH.map { it.toDouble() }
    val c = t.indices.sumOf { t[it] * ns[it] * ns[it] } / ns.sumOf { it.pow(4) }
    val p = ns.map { c * it * it }
    val dv = t.indices.map { (t[it] - p[it]) / p[it] }
    val mdev = dv.drop(2).maxOf { abs(it) }
    frame("Real vs Predicted Runtime", listOf(
        L("${if (s.opt == 1) "bubble" else "insertion"} sort on random input · median of 3"),
        vcols(NS_BENCH.mapIndexed { i, n -> VIn("$n", "", listOf(t[i], p[i]), listOf(G, "transparent"), listOf("none", "inset 0 0 0 1.5px #9aa0ae")) }, 130.0, gap = 10, bw = 22, axis = "n →"),
        AnBlock.Table(listOf("n", "measured", "c·n²", "Δ"), NS_BENCH.mapIndexed { i, n ->
            AnTRow("$n", fT(t[i]), fT(p[i]), (if (dv[i] >= 0) "+" else "−") + fx(abs(dv[i] * 100), 0) + "%", if (abs(dv[i]) < .25) Gl else Y)
        }),
    ), listOf(F("c fit to all 5 points =", lsExp(c) + " µs")),
        if (mdev < .3) "Measured tracks c·n²." to "From n = 1,000 up, every point is within ${Rb.round(mdev * 100).toInt()}% of the quadratic fit."
        else "Noisy run — tap Run again." to "Timing varies on this device; small n is dominated by timer and cache effects.",
        dock, listOf(lg(G, "Measured"), lg("transparent", "Predicted c·n²", "inset 0 0 0 1.5px #9aa0ae")),
    )
}

// ── 57h Input Size Scaling ──

private fun sizeScaling() = AnLab(AnVals(opt = 0), bench = true) { s, bench ->
    val dock = AnDock(listOf(AnSeg(listOf("Insertion · O(n²)", "Built-in · O(n log n)"), s.opt) { v, i -> v.copy(opt = i) }), btn = AnBtn("Run benchmark", bench = true))
    val t = bench?.get(listOf("ins", "sys")[s.opt]) ?: return@AnLab benchWaiting("Input Size Scaling", dock)
    fun ex(i: Int) = if (s.opt == 1) 2 * log2(NS_BENCH[i].toDouble()) / log2(NS_BENCH[i - 1].toDouble()) else 4.0
    val rat = (1 until t.size).map { t[it] / t[it - 1] }
    val sorted = rat.sorted()
    val med = sorted[1] / 2 + sorted[2] / 2
    frame("Input Size Scaling", listOf(
        L("measured time · ${if (s.opt == 1) "built-in sort" else "insertion sort"}"),
        vcols(NS_BENCH.mapIndexed { i, n -> VIn("$n", fT(t[i]), listOf(t[i])) }, 110.0, gap = 8, bw = 34),
        L("time ÷ previous size · yellow mark = expected"),
        AnBlock.Bars(rat.mapIndexed { i, r -> row("${NS_BENCH[i]}→${NS_BENCH[i + 1]}", fx(r, 2) + "×", r / 6, P, "#c3c7d1", ex(i + 1) / 6) }, 88, 56),
    ), listOf(F("expected per doubling =", if (s.opt == 1) "≈ 2.2×" else "4×", Y), F("median measured =", fx(med, 2) + "×", Pl)),
        if (s.opt == 1) "Double n, a little over ×2." to "O(n log n): each doubling costs 2 · log(2n) / log n ≈ 2.2×."
        else "Double n, ×4 the time." to "Ratios settle near 4× as n grows — the fingerprint of O(n²).",
        dock,
    )
}

// ── 57i Amortized Analysis ──

private fun amortized() = AnLab(AnVals(opt = 0, ni = 2)) { s, _ ->
    val ps = listOf(8, 12, 16, 24, 32, 48, 64)
    val p = ps[s.ni]
    val grow: (Int) -> Int = listOf<(Int) -> Int>({ it * 2 }, { ceil(it * 1.5).toInt() }, { it + 4 })[s.opt]
    var cap = 1
    var size = 0
    val cs = ArrayList<Pair<Int, Boolean>>()
    repeat(p) {
        var c = 1
        var rs = false
        if (size == cap) { c += size; cap = grow(cap); rs = true }
        size++
        cs += c to rs
    }
    val tot = cs.sumOf { it.first }
    val am = tot.toDouble() / p
    val wst = cs.maxOf { it.first }
    val step = if (p <= 16) 1 else if (p <= 32) 4 else 8
    frame("Amortized Analysis", listOf(
        L("cost of each push · 1 write + copies on resize"),
        vcols(cs.mapIndexed { i, (c, rs) ->
            VIn(if ((i + 1) % step == 0 || i == 0) "${i + 1}" else "", if (rs && p <= 24) "$c" else "", listOf(c.toDouble()), listOf(if (rs) R else G))
        }, 140.0, gap = if (p > 32) 1 else 2, bw = null, axis = "push #"),
        bigs(Triple("$tot", "total cost", ON), Triple(fx(am, 2), "amortized / push", Pl), Triple("$wst", "worst single push", Rl)),
    ), listOf(F("total ÷ pushes =", fx(am, 2), Pl), F("final capacity =", "$cap")),
        listOf(
            "Spikes, but a flat average." to "Doubling makes each resize twice as rare. The average stays under 3 per push — amortized O(1).",
            "Smaller steps, more copies." to "×1.5 growth is still geometric, so still amortized O(1) — with a larger constant and less wasted space.",
            "Fixed +4 growth: the average climbs." to "A resize every 4 pushes, each copying everything. Cost per push grows with n — amortized O(n).",
        )[s.opt],
        AnDock(listOf(AnSeg(listOf("Grow ×2", "Grow ×1.5", "Grow +4"), s.opt) { v, i -> v.copy(opt = i) }), AnSlider("Pushes", ps.map { "$it" })),
        listOf(lg(G, "Plain push"), lg(R, "Push + resize copy")),
    )
}

// ── 57j Space-Time Tradeoffs ──

private fun spaceTime() = AnLab(AnVals(opt = 0, ni = 2)) { s, _ ->
    val ns = listOf(8, 16, 24, 32, 48, 64)
    val n = ns[s.ni]
    val end = s.opt == 0
    val pp = if (end) n - 2 to n - 1 else 0 to 1
    var bc = 0
    outer@ for (i in 0 until n) for (j in i + 1 until n) { bc++; if (i == pp.first && j == pp.second) break@outer }
    var hl = 0
    var hs = 0
    for (i in 0 until n) { hl++; if (i == pp.second) break; hs++ }
    val mx = max(bc, hl).toDouble()
    frame("Space-Time Tradeoffs", listOf(
        AnBlock.Duel(listOf(
            AnDuel("Brute force", Rl, "rgba(229,72,77,.12)", num(bc.toDouble()) + " pair checks", "O(n²) time", "0 extra cells", "O(1) space"),
            AnDuel("Hash set", Gl, "rgba(34,160,107,.12)", "$hl lookups", "O(n) time", "$hs cells", "O(n) space"),
        )),
        L("operations"), AnBlock.Bars(listOf(row("Brute", num(bc.toDouble()), bc / mx, R), row("Hash", "$hl", hl / mx, G))),
        L("extra memory, cells"), AnBlock.Bars(listOf(row("Brute", "0", 0.0, R), row("Hash", "$hs", hs / mx, G))),
    ), listOf(F("operations saved =", num((bc - hl).toDouble()), Gl), F("memory paid =", "$hs cells", Y)),
        if (end) "Hash set: ${num((bc - hl).toDouble())} fewer operations for $hs cells." to "Brute force checks every pair; the hash set remembers what it has seen. Memory buys time."
        else "Pair at the start: both finish instantly." to "Big-O describes the worst case — here brute force simply got lucky.",
        AnDock(listOf(AnSeg(listOf("Pair at the end", "Pair at the start"), s.opt) { v, i -> v.copy(opt = i) }), AnSlider("Array size", ns.map { "n = $it" })),
    )
}

// ── 57k Master Theorem ──

private fun sup(x: String) = x.map { ch -> if (ch.isDigit()) "⁰¹²³⁴⁵⁶⁷⁸⁹"[ch - '0'] else if (ch == '.') '·' else ch }.joinToString("")

private fun np(e: Double): String = when (e) {
    0.0 -> "1"
    1.0 -> "n"
    else -> "n" + sup(js(if (e == floor(e)) e else fx(e, 2).toDouble()))
}

private fun masterTheorem() = AnLab(AnVals(a = 2, b = 2, d = 1)) { s, _ ->
    val a = s.a
    val b = s.b
    val d = s.d
    val lgv = ln(a.toDouble()) / ln(b.toDouble())
    val eq = abs(lgv - d) < 1e-9
    val res = when {
        eq -> if (d == 0) "Θ(log n)" else "Θ(${np(d.toDouble())} log n)"
        lgv > d -> "Θ(${np(if (fx(lgv, 9).toDouble().let { it == floor(it) }) Rb.round(lgv) else lgv)})"
        else -> "Θ(${np(d.toDouble())})"
    }
    val cs = if (eq) 2 else if (lgv > d) 1 else 3
    val q = a / b.toDouble().pow(d)
    val lv = (0..4).map { q.pow(it) }
    val lm = lv.max()
    val ex = listOf(Triple(2, 2, 1), Triple(1, 2, 0), Triple(3, 2, 1), Triple(7, 2, 2))
    val sel = ex.indexOfFirst { it.first == a && it.second == b && it.third == d }
    fun st(l: String, sub: String, get: (AnVals) -> Int, put: (AnVals, Int) -> AnVals, lo: Int, hi: Int) = AnStep(
        l, sub, "${get(s)}", get(s) > lo, get(s) < hi, { v -> put(v, max(lo, get(v) - 1)) }, { v -> put(v, min(hi, get(v) + 1)) },
    )
    frame("Master Theorem", listOf(
        AnBlock.Steps(listOf(
            st("a", "subproblems", { it.a }, { v, x -> v.copy(a = x) }, 1, 9),
            st("b", "shrink factor", { it.b }, { v, x -> v.copy(b = x) }, 2, 6),
            st("d", "exponent of f(n)", { it.d }, { v, x -> v.copy(d = x) }, 0, 3),
        )),
        AnBlock.Res("rgba(109,93,252,.14)", Pl, "log_$b($a) = ${fx(lgv, 3)}  vs  d = $d", res, listOf(
            "Case 1 · leaves dominate — work grows down the tree.", "Case 2 · every level does equal work.", "Case 3 · the root dominates — work shrinks down the tree.",
        )[cs - 1]),
        L("work per recursion level, relative to the root (×${fx(q, 2)} per level)"),
        AnBlock.Bars(lv.mapIndexed { i, w -> row("level $i", fx(w, 2) + "×", w / lm, listOf(R, B, Y)[cs - 1]) }, 64, 60),
    ), listOf(F("T(n) = $a·T(n/$b) + Θ(${np(d.toDouble())})", "", Pl)),
        "Compare d with log_b a." to "Level i does (a / bᵈ)ⁱ times the root’s work. Above 1 the leaves win; below 1 the root wins.",
        AnDock(listOf(AnSeg(listOf("Merge", "Binary", "Karatsuba", "Strassen"), sel) { v, i -> v.copy(a = ex[i].first, b = ex[i].second, d = ex[i].third) })),
    )
}

// ── 57l Complexity Map ──

private fun complexityMap() = AnLab(AnVals(ni = 2)) { s, _ ->
    val ns = listOf(10.0, 100.0, 1e3, 1e4, 1e5, 1e6)
    val n = ns[s.ni]
    val tiers = listOf(
        listOf("O(1)", 0.0, Gl, "rgba(34,160,107,.16)", listOf("Array access", "Hash lookup", "Stack push")),
        listOf("O(log n)", log10(max(1.0, log2(n))), Gl, "rgba(34,160,107,.16)", listOf("Binary search", "BST insert", "Heap push")),
        listOf("O(n)", log10(n), Bl, "rgba(59,130,246,.16)", listOf("Linear search", "Array scan", "BFS / DFS")),
        listOf("O(n log n)", log10(n * log2(n)), Y, "rgba(245,197,66,.14)", listOf("Merge sort", "Heap sort", "Quicksort (avg)")),
        listOf("O(n²)", 2 * log10(n), Kl, "rgba(229,51,122,.14)", listOf("Bubble sort", "Insertion sort", "Selection sort")),
        listOf("O(2ⁿ)", n * log10(2.0), Rl, "rgba(229,72,77,.16)", listOf("Naive Fibonacci", "Subset generation", "TSP brute force")),
    )
    fun tm(lgv: Double): String {
        val sec = lgv - 9
        if (sec > 17.64) return "> age of universe"
        val v = 10.0.pow(sec)
        return when {
            v < 1e-6 -> "${Rb.round(v * 1e9).toLong()} ns"
            v < 1e-3 -> fx(v * 1e6, 0) + " µs"
            v < 1 -> fx(v * 1e3, 0) + " ms"
            v < 60 -> fx(v, 1) + " s"
            v < 3600 -> fx(v / 60, 0) + " min"
            v < 86400 -> fx(v / 3600, 0) + " h"
            v < 3.15e7 -> fx(v / 86400, 0) + " days"
            else -> big(v / 3.15e7) + " yrs"
        }
    }
    fun tc(lgv: Double) = if (lgv - 9 < 0) Gl else if (lgv - 9 < 3.56) Y else Rl
    fun ops(lgv: Double) = if (lgv < 15) big(10.0.pow(lgv)) else "10^" + Rb.round(lgv).toLong()
    @Suppress("UNCHECKED_CAST")
    frame("Complexity Map", listOf(
        L("operations and time at n = ${big(n)}, 1 ns per operation"),
        AnBlock.Tiers(tiers.map { t ->
            val lgv = t[1] as Double
            AnTier(t[0] as String, t[2] as String, t[3] as String, t[4] as List<String>, ops(lgv), tm(lgv), tc(lgv))
        }),
    ), listOf(F("n² at n = ${big(n)} =", tm(2 * log10(n)), tc(2 * log10(n)))),
        "At n = ${big(n)}, O(2ⁿ) takes ${tm(n * log10(2.0))}." to "Green finishes under a millisecond, yellow within an hour, red is impractical.",
        AnDock(slider = AnSlider("Input size", ns.map { "n = ${big(it)}" })),
    )
}

// ── 57m Sandbox ──

private val ALG = listOf("Linear", "Insertion", "Bubble", "Selection")
private val SH3 = listOf("Sorted", "Reversed", "Random")

private fun sandbox() = AnLab(AnVals(opt = 1, opt2 = 1, ni = 1, runs = listOf(Triple(0, 1, 16), Triple(1, 1, 16)))) { s, _ ->
    val ns = listOf(8, 16, 24, 32, 48, 64)
    val col = listOf(B, Y, K, G)
    fun run(alg: Int, sh: Int, n: Int): Int {
        val a = shape(sh, n, 5)
        return when (alg) { 0 -> linC(a, n - 1); 1 -> insC(a); 2 -> bubC(a); else -> selC(a) }
    }
    val rs = s.runs.map { (alg, sh, n) -> listOf(alg, sh, n, run(alg, sh, n)) }
    val mx = max(1, rs.maxOfOrNull { it[3] } ?: 0).toDouble()
    val last = rs.lastOrNull()
    val blocks = if (last != null) listOf(
        bigs(Triple("${last[3]}", "${ALG[last[0]].lowercase()} · ${SH3[last[1]].lowercase()} · n = ${last[2]}", ON)),
        L("every run so far, operations"),
        AnBlock.Bars(rs.map { r -> row("${ALG[r[0]]} · ${SH3[r[1]].take(3).lowercase()} · ${r[2]}", num(r[3].toDouble()), r[3] / mx, col[r[0]], "#c3c7d1") }, 128, 48),
    ) else listOf(L("No runs yet — pick an algorithm, a shape and a size, then tap Run."))
    frame("Sandbox Mode", blocks,
        if (rs.size > 1) listOf(F("latest ÷ first =", fx(last!![3].toDouble() / rs[0][3], 1) + "×", Pl)) else emptyList(),
        "Stack runs, compare anything." to "Try one algorithm on all three shapes, or one shape across all four algorithms.",
        AnDock(
            listOf(AnSeg(ALG, s.opt) { v, i -> v.copy(opt = i) }, AnSeg(SH3, s.opt2) { v, i -> v.copy(opt2 = i) }),
            AnSlider("Input size", ns.map { "n = $it" }),
            AnBtn("Run") { v -> v.copy(runs = (v.runs + Triple(v.opt, v.opt2, ns[v.ni])).takeLast(7)) },
            AnBtn("Clear") { v -> v.copy(runs = emptyList()) },
        ),
        ALG.mapIndexed { i, l -> lg(col[i], l) },
    )
}
