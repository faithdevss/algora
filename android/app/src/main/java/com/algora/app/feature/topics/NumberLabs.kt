package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Number labs ──────────────────────────────────────────────────────────────
// Euclid's GCD, Modular Arithmetic, Modular Exponentiation, Fast Power and the Sieve as tabbed
// storyboards. Each frame carries whichever figure its lab draws — a remainder table, the residues of a
// modulus on a clock, a column of recursive calls, or the sieve's number grid — then the arithmetic,
// chips and a headline. Every value is computed.

private enum class NumTone { Idle, Current, Done, Next, Answer, Stack, Waiting, Prime, StruckNow, StruckEarlier, Target, Blank, Operand, Raw }

private class NumCell(val text: String, val tone: NumTone)
private class NumTable(val headers: List<String>, val rows: List<List<NumCell>>, val current: Int?)

private class NumClock(
    val modulus: Int,
    val tones: Map<Int, NumTone> = emptyMap(),
    // A value written beside a residue ("23" next to 2), in that residue's colour.
    val labels: Map<Int, String> = emptyMap(),
    // Residues joined in order by a dotted yellow line: the steps being counted.
    val path: List<Int> = emptyList(),
)

private class NumRow(val label: String, val expr: String, val value: String, val tone: NumTone)

private class NumFrame(
    val headline: String,
    val body: String,
    val table: NumTable? = null,
    val clock: NumClock? = null,
    val rows: List<NumRow> = emptyList(),
    val sieve: List<NumCell> = emptyList(),
    val formula: List<String> = emptyList(),
    val formulaRows: List<StoryFormulaRow> = emptyList(),
    val chips: List<StoryChip> = emptyList(),
)

private class NumTab(val label: String, val frames: List<NumFrame>, val legend: List<Triple<Color, SwatchStyle, String>>)

private val DimSwatch = Color.Gray.copy(alpha = 0.4f)

private fun sup(n: Int) = "$n".map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
private fun grouped(n: Long) = String.format(java.util.Locale.US, "%,d", n)
private fun grouped(n: Int) = grouped(n.toLong())

// ── Euclid ──

private fun euclidTabs(): List<NumTab> {
    val a0 = 252
    val b0 = 105
    val steps = mutableListOf<List<Int>>()
    var a = a0
    var b = b0
    while (b != 0) {
        steps += listOf(a, b, a / b, a % b)
        val r = a % b
        a = b
        b = r
    }
    val g = a
    val headers = listOf("a", "b", "q", "a mod b")
    fun cells(s: List<Int>, tone: NumTone) = s.map { NumCell("$it", tone) }
    fun nextCells(x: Int, y: Int) = listOf(NumCell("$x", NumTone.Next), NumCell("$y", NumTone.Next), NumCell("·", NumTone.Next), NumCell("·", NumTone.Next))
    fun table(current: Int, answer: Boolean = false): NumTable {
        val rows = mutableListOf<List<NumCell>>()
        for ((i, s) in steps.withIndex()) {
            when {
                i < current -> rows += cells(s, NumTone.Done)
                i == current -> rows += cells(s, NumTone.Current)
                i == current + 1 -> rows += nextCells(s[0], s[1])
            }
        }
        if (current >= steps.size - 1) {
            rows += listOf(NumCell("$g", if (answer) NumTone.Answer else NumTone.Next), NumCell("0", NumTone.Next), NumCell("·", NumTone.Next), NumCell("·", NumTone.Next))
        }
        return NumTable(headers, rows, current)
    }
    val gcd = mutableListOf(
        NumFrame(
            "Euclid replaces (a, b) with (b, a mod b) until b is 0.", "Division with remainder is all it needs: no factoring, no trial divisors.",
            table = NumTable(headers, listOf(nextCells(a0, b0)), null), formula = listOf("gcd(a, b) = gcd(b, a mod b)"),
            chips = listOf(StoryChip("a", "$a0"), StoryChip("b", "$b0")),
        ),
    )
    steps.forEachIndexed { i, s ->
        val last = s[3] == 0
        gcd += NumFrame(
            if (last) "${s[0]} mod ${s[1]} = {0}: ${s[1]} divides ${s[0]} exactly." else "${s[0]} mod ${s[1]} = {${s[3]}}, so the next pair is (${s[1]}, ${s[3]}).",
            if (last) "A zero remainder ends the loop; the divisor is the answer."
            else "gcd(a, b) = gcd(b, a mod b), because both pairs have exactly the same common divisors.",
            table = table(i), formula = listOf("${s[0]} = {${s[2]}} · ${s[1]} + {${s[3]}}"),
            chips = listOf(StoryChip("a", "${s[0]}"), StoryChip("b", "${s[1]}")),
        )
    }
    gcd += NumFrame(
        "b reached 0, so gcd($a0, $b0) = {v:$g} after ${steps.size} divisions.",
        "Remainders at least halve every two steps, so Euclid takes O(log min(a, b)) divisions.",
        table = table(steps.size, answer = true), formula = listOf("gcd($a0, $b0) = {v:$g}"),
        chips = listOf(StoryChip("divisions", "${steps.size}"), StoryChip("gcd", "$g", StoryTone.Answer)),
    )
    val product = a0 * b0
    val lcm = a0 / g * b0
    val done = table(steps.size, answer = true)
    val lcmTab = listOf(
        NumFrame(
            "With the gcd in hand, the lcm is one multiply and one divide.",
            "a · b counts the shared factor $g twice; dividing by the gcd removes the extra copy.",
            table = done, formula = listOf("lcm(a, b) = a · b / gcd(a, b)"), chips = listOf(StoryChip("gcd", "$g", StoryTone.Answer)),
        ),
        NumFrame(
            "Multiply first: $a0 · $b0 = {${grouped(product)}}.",
            "Correct, but the product can be far bigger than the answer, which is where overflow bites.",
            table = done, formula = listOf("$a0 · $b0 = {${grouped(product)}}"), chips = listOf(StoryChip("a · b", grouped(product))),
        ),
        NumFrame(
            "${grouped(product)} / $g = {v:${grouped(lcm)}}, the smallest number both divide.",
            "${grouped(lcm)} = $a0 · ${lcm / a0} = $b0 · ${lcm / b0}.",
            table = done, formula = listOf("${grouped(product)} / $g = {v:${grouped(lcm)}}"), chips = listOf(StoryChip("lcm", grouped(lcm), StoryTone.Answer)),
        ),
        NumFrame(
            "Divide first and the biggest value is the answer itself, {v:${grouped(lcm)}}.",
            "Same result with no intermediate larger than the lcm, so it cannot overflow unless the answer does.",
            table = done, formula = listOf("$a0 / $g · $b0 = ${a0 / g} · $b0 = {v:${grouped(lcm)}}"),
            chips = listOf(StoryChip("lcm", grouped(lcm), StoryTone.Answer), StoryChip("largest", grouped(lcm))),
        ),
    )
    val legend = listOf(
        Triple(SimColors.Active, SwatchStyle.Fill, "Current step"),
        Triple(SimColors.Green, SwatchStyle.Fill, "Done"),
        Triple(DimSwatch, SwatchStyle.Fill, "Next pair"),
    )
    return listOf(NumTab("GCD", gcd, legend), NumTab("LCM", lcmTab, legend + Triple(SimColors.Answer, SwatchStyle.Fill, "Answer")))
}

// ── Modular arithmetic ──

private fun modularTabs(): List<NumTab> {
    val m = 7
    val a = 17
    val b = 23
    val ra = a % m
    val rb = b % m
    val add = (ra + rb) % m
    val sub = ((ra - rb) % m + m) % m
    val mul = (ra * rb) % m
    val raw = (a - b) % m
    val op = NumTone.Operand
    val ans = NumTone.Answer
    val subPath = (0..rb).map { ((ra - it) % m + m) % m }
    val addTab = listOf(
        NumFrame(
            "Mod $m, every number sits on one of $m positions: $a is at {p:$ra}, $b at {p:$rb}.",
            "Only the remainder matters, so the big numbers can be reduced before anything else.",
            clock = NumClock(m, mapOf(ra to op, rb to op), mapOf(ra to "$a", rb to "$b")),
            formula = listOf("$a % $m = $ra    $b % $m = $rb"), chips = listOf(StoryChip("a", "$a → $ra"), StoryChip("b", "$b → $rb")),
        ),
        NumFrame(
            "Adding is walking the clock: $rb steps on from $ra lands on {v:$add}.",
            "(a + b) mod m = ((a mod m) + (b mod m)) mod m, so the full sum never has to be formed.",
            clock = NumClock(m, mapOf(ra to op, add to ans), mapOf(ra to "$a", add to "${a + b}"), (ra..ra + rb).map { it % m }),
            formula = listOf("($ra + $rb) % $m = {v:$add} = ${a + b} % $m"),
            chips = listOf(StoryChip("sum", "${a + b}"), StoryChip("residue", "$add", StoryTone.Answer)),
        ),
    )
    val subClock = NumClock(m, mapOf(ra to op, rb to op, sub to ans), mapOf(ra to "$a", rb to "$b", sub to "${a - b}"), subPath)
    val subTab = listOf(
        NumFrame(
            "Subtracting walks backwards: $rb steps back from $ra lands on {v:$sub}.",
            "On the clock there are no negatives. Every difference is one of 0 to ${m - 1}.",
            clock = subClock, formula = listOf("($ra − $rb) % $m = {v:$sub}"),
            chips = listOf(StoryChip("a − b", "${a - b}"), StoryChip("residue", "$sub", StoryTone.Answer)),
        ),
        NumFrame(
            "$a − $b = ${a - b}, but % gives {w:$raw}, not {v:$sub}.",
            "% keeps the dividend's sign. Adding m and taking % again lands on the residue.",
            clock = subClock, formula = listOf("((${if (a - b < 0) "{w:${a - b}}" else "${a - b}"} % $m) + $m) % $m = {v:$sub}"),
            chips = listOf(StoryChip("${a - b} % $m", "$raw", StoryTone.Warn), StoryChip("normalised", "$sub", StoryTone.Answer)),
        ),
    )
    val mulTab = listOf(
        NumFrame(
            "$a · $b = ${a * b}, but only its place on the clock matters.",
            "Multiply the residues instead: they are small, and the answer is the same.",
            clock = NumClock(m, mapOf(ra to op, rb to op), mapOf(ra to "$a", rb to "$b")),
            formula = listOf("$a · $b = ${a * b}"), chips = listOf(StoryChip("a · b", "${a * b}")),
        ),
        NumFrame(
            "$rb hops of $ra from 0 land on {v:$mul}.",
            "(a · b) mod m = ((a mod m) · (b mod m)) mod m, which keeps every product below m².",
            clock = NumClock(m, mapOf(ra to op, mul to ans), mapOf(ra to "$a", mul to "${a * b}"), (0..rb).map { (it * ra) % m }),
            formula = listOf("($ra · $rb) % $m = {v:$mul} = ${a * b} % $m"), chips = listOf(StoryChip("residue", "$mul", StoryTone.Answer)),
        ),
    )
    val inv = (1 until m).first { (it * ra) % m == 1 }
    val invHops = (0..inv).map { (it * ra) % m }
    val invTab = listOf(
        NumFrame(
            "Division needs an inverse: the x that makes $ra · x land on {1}.",
            "Hop by $ra from 0 until the clock reads 1. It only works when gcd(a, m) = 1.",
            clock = NumClock(m, mapOf(ra to op), mapOf(ra to "$ra"), invHops),
            formula = listOf("$ra · x ≡ 1 (mod $m)"), chips = listOf(StoryChip("a", "$ra"), StoryChip("hops", "$inv")),
        ),
        NumFrame(
            "$inv hops of $ra land on 1, so $ra⁻¹ = {v:$inv} (mod $m).",
            "Dividing by $ra mod $m means multiplying by $inv. For large m, extended Euclid finds it in O(log m).",
            clock = NumClock(m, mapOf(ra to op, 1 to ans), mapOf(ra to "$ra", 1 to "×$inv"), invHops),
            formula = listOf("$ra · $inv = ${ra * inv} = ${ra * inv / m} · $m + {v:1}"), chips = listOf(StoryChip("inverse", "$inv", StoryTone.Answer)),
        ),
    )
    val legend = listOf(
        Triple(SimColors.Blue, SwatchStyle.Fill, "Operand residue"),
        Triple(SimColors.Active, SwatchStyle.Fill, "Steps"),
        Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
    )
    return listOf(
        NumTab("Add", addTab, legend),
        NumTab(
            "Subtract", subTab,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Operand residue"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Step back $rb"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Raw % result"),
            ),
        ),
        NumTab("Multiply", mulTab, legend),
        NumTab("Inverse", invTab, legend),
    )
}

// ── Powers ──

/** Recursive and iterative squaring for 3¹³, reduced mod [mod] when given. */
private fun powerTabs(mod: Long?): List<NumTab> {
    val base = 3L
    val e0 = 13
    fun red(v: Long) = if (mod != null) v % mod else v
    val modText = mod?.let { " mod $it" }.orEmpty()
    val exps = mutableListOf<Int>()
    var e = e0
    while (true) {
        exps += e
        if (e == 0) break
        e /= 2
    }
    val value = mutableMapOf(0 to 1L)
    for (x in exps.reversed()) if (x > 0) {
        val h = value.getValue(x / 2)
        value[x] = red(red(h * h) * (if (x % 2 == 1) base else 1L))
    }
    fun expr(x: Int, known: Boolean): String {
        if (x == 0) return "base"
        // Mod shows the reduced number being squared; plain powers keep the exponent form.
        val inner = if (known && mod != null) "(${value.getValue(x / 2)})" else "($base${sup(x / 2)})"
        val body = inner + "²" + if (x % 2 == 1) "·$base" else ""
        return if (mod == null) "${if (x % 2 == 1) "odd" else "even"} · $body" else body + modText
    }
    fun rows(deepest: Int, returning: Int?) = exps.take(deepest + 1).mapIndexed { i, x ->
        val label = "$base${sup(x)}"
        when {
            returning != null && i > returning -> NumRow(label, expr(x, true), "${value[x]}", NumTone.Done)
            returning != null && i == returning -> NumRow(label, expr(x, true), "${value[x]}", NumTone.Current)
            else -> NumRow(label, expr(x, false), if (mod == null) "waiting" else "…", NumTone.Waiting)
        }
    }
    val naive = e0 - 1
    fun stepFormula(x: Int, final: Boolean): String {
        if (x == 0) return "$base⁰ = {1}"
        val h = value.getValue(x / 2)
        val v = value.getValue(x)
        val mark = if (final && mod != null) "{v:$v}" else "{$v}"
        if (mod != null) {
            val sq = h * h
            return if (x % 2 == 1) "$h² = $sq ≡ ${sq % mod} → ${sq % mod} · $base = $mark (mod $mod)" else "$h² = $sq ≡ $mark (mod $mod)"
        }
        val tail = if (x % 2 == 1) "·$base" else ""
        return "$base${sup(x)} = ($base${sup(x / 2)})²$tail = ${grouped(h)}²$tail = {${grouped(v)}}"
    }
    var largest = 1L
    val rec = mutableListOf(
        NumFrame(
            "Compute $base${sup(e0)}$modText by halving the exponent.", "Even n: xⁿ = (x^(n/2))². Odd n: one extra multiply by x.",
            rows = rows(0, null), formula = listOf("$base${sup(e0)}$modText"),
            chips = listOf(StoryChip("calls", "1"), StoryChip("naive", "$naive mults")),
        ),
    )
    exps.forEachIndexed { i, x ->
        if (x > 0) {
            rec += NumFrame(
                "$x is ${if (x % 2 == 1) "odd" else "even"}, so {$base${sup(x)}} calls $base${sup(x / 2)} and waits.",
                "Each call halves the exponent, so $e0 needs ${exps.size} calls instead of $naive multiplications.",
                rows = rows(i + 1, null), formula = listOf("$base${sup(x)} needs $base${sup(x / 2)} first"),
                chips = listOf(StoryChip("calls", "${i + 2}"), StoryChip("naive", "$naive mults")),
            )
        }
    }
    for (i in exps.indices.reversed()) {
        val x = exps[i]
        if (x > 0) {
            val h = value.getValue(x / 2)
            largest = maxOf(largest, h * h * (if (mod == null && x % 2 == 1) base else 1L))
        }
        val final = i == 0
        val v = value.getValue(x)
        val headline = when {
            x == 0 -> "$base⁰ is the base case: {1}."
            final -> "$base${sup(x)}$modText = {v:${grouped(v)}}."
            x % 2 == 0 -> "$x is even, so $base${sup(x)} just squares ${grouped(value.getValue(x / 2))}: {${grouped(v)}}."
            else -> "$x is odd, so $base${sup(x)} squares ${grouped(value.getValue(x / 2))} and multiplies by $base: {${grouped(v)}}."
        }
        var full = 1L
        repeat(e0) { full *= base }
        val body = when {
            final && mod != null -> "Reducing after every multiply keeps each value below $mod², so it never overflows. Without mod, $base${sup(e0)} = ${grouped(full)}."
            final -> "${exps.size} calls and at most two multiplies each: O(log n) instead of $naive multiplications."
            else -> "Each call halves the exponent, so $e0 needs ${exps.size} calls instead of $naive multiplications."
        }
        val chips = if (final && mod != null) {
            listOf(StoryChip("$base${sup(e0)} mod $mod", "$v", StoryTone.Answer), StoryChip("largest", "$largest"))
        } else {
            listOf(StoryChip("calls", "${exps.size}"), StoryChip("naive", "$naive mults"))
        }
        rec += NumFrame(headline, body, rows = rows(exps.size - 1, i), formula = listOf(stepFormula(x, final)), chips = chips)
    }
    // Iterative: read the exponent's bits from the bottom, squaring the base each time.
    var r = 1L
    var bb = base
    var k = e0
    var bit = 0
    var iterRows = listOf<NumRow>()
    val modSuffix = mod?.let { " (mod $it)" }.orEmpty()
    val iter = mutableListOf(
        NumFrame(
            "The loop reads $e0's bits from the lowest: ${e0.toString(2)}.",
            "Each 1 bit multiplies the current power of $base into the result; every bit squares the base.",
            formula = listOf("$e0 = ${e0.toString(2)} in binary"), chips = listOf(StoryChip("result", "1"), StoryChip("base", "$base")),
        ),
    )
    while (k > 0) {
        val on = k and 1 == 1
        val newR = if (on) red(r * bb) else r
        val newB = red(bb * bb)
        val exprText = if (on) "r = ${grouped(r)}·${grouped(bb)}$modText" else "skip, r = ${grouped(r)}"
        iterRows = listOf(NumRow("bit $bit = ${if (on) 1 else 0}", exprText, grouped(newR), NumTone.Current)) +
            iterRows.map { NumRow(it.label, it.expr, it.value, NumTone.Done) }
        val lastBit = k / 2 == 0
        iter += NumFrame(
            if (on) "Bit $bit is 1, so the result picks up $base${sup(1 shl bit)}: {${grouped(newR)}}." else "Bit $bit is 0, so the result stays {${grouped(r)}}.",
            if (lastBit) "No bits left: $base${sup(e0)}$modText = ${grouped(newR)}, in ${bit + 1} iterations." else "The base squares every step: $base, $base², $base⁴, $base⁸.",
            rows = iterRows,
            formula = listOf(
                if (on) "r = ${grouped(r)} · ${grouped(bb)} = {${grouped(newR)}}$modSuffix" else "bit is 0 → r stays {${grouped(r)}}",
                "base = ${grouped(bb)}² = ${grouped(newB)}$modSuffix",
            ),
            chips = listOf(StoryChip("result", grouped(newR), if (lastBit) StoryTone.Answer else StoryTone.Idle), StoryChip("base", grouped(newB))),
        )
        r = newR
        bb = newB
        k = k shr 1
        bit++
    }
    iter += NumFrame(
        "$base${sup(e0)}$modText = {v:${grouped(r)}} after $bit iterations.",
        "Same answer as the recursion with no stack at all: one loop per bit, O(log n).",
        rows = iterRows.mapIndexed { i, row -> NumRow(row.label, row.expr, row.value, if (i == 0) NumTone.Answer else NumTone.Done) },
        formula = listOf("$base${sup(e0)}$modText = {v:${grouped(r)}}"),
        chips = listOf(StoryChip("result", grouped(r), StoryTone.Answer), StoryChip("iterations", "$bit")),
    )
    val recLegend = if (mod == null) {
        listOf(
            Triple(SimColors.Active, SwatchStyle.Fill, "Active"),
            Triple(SimColors.Active, SwatchStyle.Ring, "On the stack"),
            Triple(SimColors.Green, SwatchStyle.Fill, "Returned"),
        )
    } else {
        listOf(
            Triple(SimColors.Active, SwatchStyle.Fill, "Active"),
            Triple(SimColors.Green, SwatchStyle.Fill, "Returned"),
            Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
        )
    }
    return listOf(
        NumTab("Recursive", rec, recLegend),
        NumTab(
            "Iterative", iter,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current bit"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Done"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
            ),
        ),
    )
}

// ── Sieve ──

private fun sieveTabs(): List<NumTab> {
    val n = 30
    val struckBy = mutableMapOf<Int, Int>()
    val frames = mutableListOf<NumFrame>()
    var primesDone = listOf<Int>()
    val limit = sqrt(n.toDouble()).toInt()
    val passes = (2..limit).filter { p -> (2 until p).all { p % it != 0 } }
    fun cells(current: Int?, target: Int? = null) = (1..n).map { v ->
        when {
            v == 1 -> NumCell("", NumTone.Blank)
            v == target -> NumCell("$v", NumTone.Target)
            v == current -> NumCell("$v", NumTone.Current)
            v in primesDone -> NumCell("$v", NumTone.Prime)
            struckBy[v] != null -> NumCell("$v", if (struckBy[v] == current) NumTone.StruckNow else NumTone.StruckEarlier)
            else -> NumCell("$v", NumTone.Stack)
        }
    }
    fun standing() = (2..n).count { struckBy[it] == null }
    val passesRow = listOf(StoryFormulaRow("passes needed", "p ≤ √$n → " + passes.joinToString(", ")))
    frames += NumFrame(
        "Every number from 2 to $n starts out standing.", "The sieve never tests a number for primality; it only crosses out multiples.",
        sieve = cells(null), formula = listOf("cross out multiples of each prime p ≤ √$n"), formulaRows = passesRow,
        chips = listOf(StoryChip("n", "$n"), StoryChip("standing", "${standing()}")),
    )
    for (p in passes) {
        // Odd primes step by 2p: every other multiple is even and already gone.
        val multiples = (p * p..n step (if (p == 2) 2 else 2 * p)).toList()
        fun list(upto: Int?) = multiples.joinToString(", ") { if (it == upto) "{w:$it}" else "$it" }
        frames += NumFrame(
            "{$p} is still standing, so it is prime. Strike its multiples from $p² = ${p * p}.",
            if (p == 2) "Anything smaller than p² with a factor p has a smaller factor too, so it is already gone."
            else "$p · 2 up to $p · ${p - 1} were struck by smaller primes already.",
            sieve = cells(p), formula = listOf("p = {$p} : start at $p² = ${p * p} → " + list(null)), formulaRows = passesRow,
            chips = listOf(StoryChip("p", "$p"), StoryChip("standing", "${standing()}")),
        )
        if (p == 2) {
            multiples.forEach { struckBy[it] = p }
            frames += NumFrame(
                "p = 2 strikes every even number from 4 up: {w:${multiples.size}} of them.", "Half the grid gone in one pass, with nothing but additions of 2.",
                sieve = cells(p), formula = listOf("p = {2} : " + multiples.joinToString(", ")), formulaRows = passesRow,
                chips = listOf(StoryChip("p", "2"), StoryChip("struck", "${multiples.size}"), StoryChip("standing", "${standing()}")),
            )
        } else {
            for (j in multiples) {
                struckBy[j] = p
                frames += NumFrame(
                    if (j == p * p) "p = $p strikes {w:$j} first, its own square."
                    else "p = $p strikes {w:$j}. It started at ${p * p} because ${p * 2} was already struck by 2.",
                    "No division happens, and the step is 2p because the even multiples are already gone. After p = ${passes.last()}, whatever is still blue is prime.",
                    sieve = cells(p, j), formula = listOf("p = {$p} : start at $p² = ${p * p} → " + list(j)), formulaRows = passesRow,
                    chips = listOf(StoryChip("p", "$p"), StoryChip("j", "$j"), StoryChip("standing", "${standing()}")),
                )
            }
        }
        primesDone = primesDone + p
    }
    val primes = (2..n).filter { struckBy[it] == null }
    primesDone = primes
    frames += NumFrame(
        "${passes.size} passes leave {v:${primes.size}} primes up to $n.",
        "Total work is about n log log n: each number is struck once per prime factor below √n.",
        sieve = cells(null), formula = listOf("primes ≤ $n: {v:" + primes.joinToString(" ") + "}"), formulaRows = passesRow,
        chips = listOf(StoryChip("primes", "${primes.size}", StoryTone.Answer), StoryChip("passes", "${passes.size}")),
    )
    return listOf(
        NumTab(
            "Sieve", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current prime"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Prime"),
                Triple(SimColors.Blue, SwatchStyle.Fill, "Still standing"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Struck this pass"),
                Triple(DimSwatch, SwatchStyle.Fill, "Struck earlier"),
            ),
        ),
    )
}

// ── Lab ──

internal val numberStoryTopicIds = setOf("euclid_gcd", "modular_arithmetic", "modular_exponentiation", "fast_power", "sieve_of_eratosthenes")

private fun numberTabs(topicId: String) = when (topicId) {
    "euclid_gcd" -> euclidTabs()
    "modular_arithmetic" -> modularTabs()
    "modular_exponentiation" -> powerTabs(17L)
    "fast_power" -> powerTabs(null)
    else -> sieveTabs()
}

internal fun numberStoryFrameCount(topicId: String): Int = numberTabs(topicId).sumOf { it.frames.size }

@Composable
internal fun NumberStorySection(topicId: String) {
    val tabs = remember(topicId) { numberTabs(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(0) }
    val frames = tabs[tab].frames
    val playback = rememberPlaybackState(key = topicId to tab, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (tabs.size > 1) LabSegments(tabs.map { it.label }, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
                frame.table?.let { NumTableView(it) }
                frame.clock?.let {
                    NumClockView(
                        it,
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.16f)),
                    )
                }
                if (frame.rows.isNotEmpty()) NumRowsView(frame.rows)
                if (frame.sieve.isNotEmpty()) NumSieveView(frame.sieve)
                if (frame.formula.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(SimColors.Tint, RoundedCornerShape(10.dp))
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        frame.formula.forEach { line ->
                            Text(storyAnnotated(line), fontFamily = IBMPlexMono, fontSize = 14.sp, color = onSurface.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        }
                    }
                }
                if (frame.formulaRows.isNotEmpty()) StoryFormulaRows(frame.formulaRows, Modifier.padding(top = 8.dp))
                StoryLegendRow(tabs[tab].legend, Modifier.padding(top = 14.dp))
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

/** Fill and text for a tone, shared by the table, rows and sieve. */
@Composable
private fun numColors(tone: NumTone): Pair<Color, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val soft = if (LocalDarkTheme.current) 0.22f else 0.16f
    return when (tone) {
        NumTone.Current -> SimColors.Active to Color(0xFF1F1A0A)
        NumTone.Done, NumTone.Prime -> SimColors.Green.copy(alpha = soft) to StoryTone.Done.ink()
        NumTone.Next -> muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.6f)
        NumTone.Answer -> SimColors.Answer to Color.White
        NumTone.Stack, NumTone.Operand -> SimColors.Blue to Color.White
        NumTone.Waiting -> muted.copy(alpha = 0.1f) to onSurface
        NumTone.StruckNow -> SimColors.Red.copy(alpha = 0.2f) to StoryTone.Warn.ink()
        NumTone.StruckEarlier -> muted.copy(alpha = 0.06f) to muted.copy(alpha = 0.45f)
        NumTone.Target -> SimColors.Red.copy(alpha = 0.14f) to StoryTone.Warn.ink()
        NumTone.Blank -> muted.copy(alpha = 0.05f) to Color.Transparent
        NumTone.Raw -> SimColors.Red to Color.White
        NumTone.Idle -> muted.copy(alpha = 0.22f) to onSurface.copy(alpha = 0.75f)
    }
}

@Composable
private fun NumTableView(table: NumTable) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(22.dp))
            table.headers.forEach { Text(it, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        table.rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val on = r == table.current
                Text(
                    "${r + 1}", fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) StoryTone.Active.ink() else muted, modifier = Modifier.width(22.dp),
                )
                row.forEach { cell ->
                    val (fill, ink) = numColors(cell.tone)
                    Box(Modifier.weight(1f).height(42.dp).background(fill, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Text(cell.text, fontFamily = IBMPlexMono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumRowsView(rows: List<NumRow>) {
    val dark = LocalDarkTheme.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            val shape = RoundedCornerShape(9.dp)
            val (fill, ink) = when (row.tone) {
                NumTone.Current -> SimColors.Active to Color(0xFF1F1A0A)
                NumTone.Answer -> SimColors.Answer.copy(alpha = if (dark) 0.3f else 0.2f) to StoryTone.Answer.ink()
                NumTone.Waiting -> muted.copy(alpha = 0.1f) to onSurface
                else -> SimColors.Green.copy(alpha = if (dark) 0.22f else 0.16f) to StoryTone.Done.ink()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(fill, shape)
                    .then(if (row.tone == NumTone.Waiting) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.label, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, modifier = Modifier.weight(1f))
                Text(row.expr, fontFamily = IBMPlexMono, fontSize = 12.sp, color = ink.copy(alpha = 0.85f), maxLines = 1)
                Text(
                    row.value, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.padding(start = 8.dp).widthIn(min = 52.dp),
                )
            }
        }
    }
}

@Composable
private fun NumSieveView(cells: List<NumCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        cells.chunked(10).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                line.forEach { cell ->
                    val (fill, ink) = numColors(cell.tone)
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(fill, shape)
                            .then(if (cell.tone == NumTone.Target) Modifier.border(1.5.dp, SimColors.Red, shape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            cell.text, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1,
                            textDecoration = if (cell.tone == NumTone.StruckEarlier) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumClockView(clock: NumClock, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val pathInk = StoryTone.Path.ink()
    val answerInk = StoryTone.Answer.ink()
    val warnInk = StoryTone.Warn.ink()
    val digitStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Canvas(modifier) {
        val m = clock.modulus
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2 - 30.dp.toPx()
        fun at(i: Int): Offset {
            val a = i.toDouble() / m * 2 * PI - PI / 2
            return Offset((center.x + radius * cos(a)).toFloat(), (center.y + radius * sin(a)).toFloat())
        }
        drawCircle(muted.copy(alpha = 0.3f), radius, center, style = Stroke(1.5.dp.toPx()))
        if (clock.path.size > 1) {
            val p = Path().apply {
                val first = at(clock.path[0])
                moveTo(first.x, first.y)
                clock.path.drop(1).forEach { val c = at(it); lineTo(c.x, c.y) }
            }
            drawPath(
                p, SimColors.Active,
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))),
            )
        }
        val modLabel = measurer.measure("mod $m", TextStyle(fontFamily = IBMPlexMono, fontSize = 13.sp, color = muted))
        drawText(modLabel, topLeft = Offset(center.x - modLabel.size.width / 2f, center.y - modLabel.size.height / 2f))
        for (i in 0 until m) {
            val c = at(i)
            val tone = clock.tones[i] ?: NumTone.Idle
            val (fill, ink) = when (tone) {
                NumTone.Operand -> SimColors.Blue to Color.White
                NumTone.Answer -> SimColors.Answer to Color.White
                NumTone.Raw -> SimColors.Red to Color.White
                else -> muted.copy(alpha = 0.25f) to onSurface.copy(alpha = 0.75f)
            }
            val r = 16.dp.toPx()
            drawCircle(surface, r, c)
            drawCircle(fill, r, c)
            val digit = measurer.measure("$i", digitStyle.copy(color = ink))
            drawText(digit, topLeft = Offset(c.x - digit.size.width / 2f, c.y - digit.size.height / 2f))
            clock.labels[i]?.let { text ->
                val labelInk = when (tone) {
                    NumTone.Answer -> answerInk
                    NumTone.Raw -> warnInk
                    else -> pathInk
                }
                val layout = measurer.measure(text, labelStyle.copy(color = labelInk))
                val right = c.x >= center.x - 1
                val x = if (right) c.x + r + 8.dp.toPx() else c.x - r - 8.dp.toPx() - layout.size.width
                drawText(layout, topLeft = Offset(x, c.y - layout.size.height / 2f))
            }
        }
    }
}
