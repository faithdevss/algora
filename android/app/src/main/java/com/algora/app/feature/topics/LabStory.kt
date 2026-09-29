package com.algora.app.feature.topics

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── Story card pieces ────────────────────────────────────────────────────────
// What the redesigned step-by-step labs share (docs mocks for Dijkstra, Coin Change, Fractional
// Knapsack, Job Sequencing): one set of state colours, a legend of only what is on screen, value chips
// whose number can take a state's colour, and a headline whose key term does.

/** A state a story paints its data in. Empty is a slot still to fill, drawn dashed. */
internal enum class StoryTone { Idle, Active, Done, Warn, Path, Answer, Empty }

internal fun StoryTone.color(): Color = when (this) {
    StoryTone.Active -> SimColors.Active
    StoryTone.Done -> SimColors.Green
    StoryTone.Warn -> SimColors.Red
    StoryTone.Path -> SimColors.Blue
    StoryTone.Answer -> SimColors.Answer
    StoryTone.Idle, StoryTone.Empty -> SimColors.Grey
}

/** The tone as text: lighter on the dark card, deeper on white, where the fills are too pale to read. */
@Composable
internal fun StoryTone.ink(): Color {
    val dark = LocalDarkTheme.current
    return when (this) {
        StoryTone.Active -> if (dark) SimColors.Active else Color(0xFFB7791F)
        StoryTone.Done -> if (dark) Color(0xFF7DD3A0) else Color(0xFF15803D)
        StoryTone.Warn -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
        StoryTone.Path -> if (dark) Color(0xFF93B4F8) else Color(0xFF2563EB)
        StoryTone.Answer -> if (dark) Color(0xFFB4A2FF) else Color(0xFF6D4AFF)
        StoryTone.Idle -> MaterialTheme.colorScheme.onSurface
        StoryTone.Empty -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// `{…}` in a headline is the term drawn in its state's colour: yellow by default (the thing being looked
// at), `{w:…}` red, `{m:…}` green, `{p:…}` blue and `{v:…}` violet. `\{` is a literal brace ("\{0,1}").
private val StoryMark = Regex("""(?<!\\)\{(?:([wmpv]):)?(.+?)\}""")

private fun unescape(text: String) = text.replace("\\{", "{")

/** [text] with its colour marks dropped, for captions and the All steps sheet. */
internal fun storyPlain(text: String): String = unescape(StoryMark.replace(text) { it.groupValues[2] })

/** [text] with each marked term in its tone's text colour. */
@Composable
internal fun storyAnnotated(text: String): AnnotatedString {
    val tones = mapOf(
        "" to StoryTone.Active.ink(),
        "w" to StoryTone.Warn.ink(),
        "m" to StoryTone.Done.ink(),
        "p" to StoryTone.Path.ink(),
        "v" to StoryTone.Answer.ink(),
    )
    return buildAnnotatedString {
        var at = 0
        StoryMark.findAll(text).forEach { m ->
            append(unescape(text.substring(at, m.range.first)))
            withStyle(SpanStyle(color = tones.getValue(m.groupValues[1]))) { append(m.groupValues[2]) }
            at = m.range.last + 1
        }
        append(unescape(text.substring(at)))
    }
}

/** A step's narration: the headline large and bold with its marked term coloured, the why muted under it. */
@Composable
internal fun LabStoryNarration(headline: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            storyAnnotated(headline),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body.isNotEmpty()) {
            Text(
                body,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The recurrence being evaluated, centred in a tinted strip: "min( skip {p:2} , use 1 + {p:2} ) = {2}". */
@Composable
internal fun StoryFormula(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            storyAnnotated(text),
            fontFamily = IBMPlexMono,
            fontSize = 15.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

/** A labelled line of arithmetic: "k = 2" muted on the left, "{p:1500} + 0 + 3000 = {4500}" on the right. */
internal class StoryFormulaRow(val label: String, val formula: String)

@Composable
internal fun StoryFormulaRows(rows: List<StoryFormulaRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(SimColors.Tint, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.label,
                    fontFamily = IBMPlexMono,
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    storyAnnotated(row.formula),
                    fontFamily = IBMPlexMono,
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

// Line and DashedLine are short strokes, for a plotted curve or boundary.
internal enum class SwatchStyle { Fill, Dashed, Ring, Dot, Line, DashedLine }

/** A legend swatch: filled, a dashed outline (a frontier, a node not made yet) or a solid ring (a goal). */
@Composable
internal fun StorySwatch(color: Color, style: SwatchStyle, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(3.dp)
        when (style) {
            SwatchStyle.Fill -> Box(Modifier.size(10.dp).background(color, shape))
            SwatchStyle.Dot -> Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
            SwatchStyle.Ring -> Box(Modifier.size(10.dp).border(1.5.dp, color, shape))
            SwatchStyle.Dashed -> Box(Modifier.size(10.dp).dashedOutline(color, 3.dp, 1.2.dp, 2.dp, 1.5.dp))
            SwatchStyle.Line -> Box(Modifier.width(16.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
            SwatchStyle.DashedLine -> androidx.compose.foundation.Canvas(Modifier.width(16.dp).height(3.dp)) {
                drawLine(
                    color,
                    androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                    androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            }
        }
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
internal fun StoryLegendRow(items: List<Triple<Color, SwatchStyle, String>>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (color, style, label) -> StorySwatch(color, style, label) }
    }
}

internal fun Modifier.dashedOutline(color: Color, radius: Dp, dash: Dp = 4.dp, gap: Dp = 3.dp, width: Dp = 1.5.dp): Modifier =
    drawBehind {
        val w = width.toPx()
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
        )
    }

/** One value chip: the key muted, the value in its tone's colour ("greedy 3" in red). */
internal class StoryChip(val key: String, val value: String, val tone: StoryTone = StoryTone.Idle)

@Composable
internal fun StoryChips(chips: List<StoryChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .background(SimColors.Tint, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(chip.key, fontFamily = IBMPlexMono, fontSize = 15.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(chip.value, fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, color = chip.tone.ink())
            }
        }
    }
}

// ── Story card ───────────────────────────────────────────────────────────────
// Coin Change, Fractional Knapsack, Job Sequencing, Quickselect, Median of Medians, Strassen and
// Karatsuba: a header naming the setup (or a size stepper), then sections of tinted table rows,
// proportional blocks (coins, kilograms), equal cells (a dp row, slots), labelled rows of cells (groups)
// and a one-level call fan.

internal class StoryBlock(val text: String, val weight: Float, val tone: StoryTone)

/** [captionTone] colours the caption under the cell (Quickselect's "k"); Idle leaves it muted. */
internal class StoryCell(
    val text: String,
    val tone: StoryTone,
    val caption: String? = null,
    val captionTone: StoryTone = StoryTone.Idle,
)

internal class StoryTableRow(val values: List<String>, val tone: StoryTone)

internal enum class ColAlign { Start, Center, End }

/** Empty [headers] draws the rows alone. [aligns] defaults to every column at the start. */
internal class StoryTable(
    val headers: List<String>,
    val weights: List<Float>,
    val rows: List<StoryTableRow>,
    val aligns: List<ColAlign> = weights.map { ColAlign.Start },
)

/** One labelled row of equal cells ("g1  1 3 8 12 17"). */
internal class StoryGridLine(val label: String, val cells: List<StoryCell>)

/** A pill in a call fan, with an optional line under it ("= 672"). */
internal class StoryPill(val text: String, val tone: StoryTone, val caption: String? = null)

/** One call and the calls it makes, a level deep: Karatsuba's three products under the full multiply. */
internal class StoryFan(val root: StoryPill, val children: List<StoryPill>)

/** A block of the card under an optional caps [label]; exactly one of the content fields is set. */
internal class StorySection(
    val label: String? = null,
    val note: String? = null,
    val table: StoryTable? = null,
    val blocks: List<StoryBlock>? = null,
    val cells: List<StoryCell>? = null,
    val grid: List<StoryGridLine>? = null,
    val fan: StoryFan? = null,
)

/** A size control in place of the card's title: "matrix size 4" with a − | + pill. */
internal class StoryStepper(
    val label: String,
    val value: Int,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
    val onChange: (Int) -> Unit,
)

internal class GreedyStory(
    val title: String,
    val note: String,
    val sections: List<StorySection>,
    // In display order; an entry shows only while its tone is on screen.
    val legend: List<Pair<StoryTone, String>>,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
) {
    val status: String get() = storyPlain(headline) + if (body.isEmpty()) "" else " $body"

    val tones: Set<StoryTone> get() = sections.flatMap { s ->
        s.table?.rows.orEmpty().map { it.tone } + s.blocks.orEmpty().map { it.tone } + s.cells.orEmpty().map { it.tone } +
            s.grid.orEmpty().flatMap { line -> line.cells.map { it.tone } } +
            listOfNotNull(s.fan?.root?.tone) + s.fan?.children.orEmpty().map { it.tone }
    }.toSet()
}

@Composable
internal fun GreedyStoryLab(
    story: GreedyStory,
    playback: PlaybackState,
    captions: List<String>,
    stepper: StoryStepper? = null,
    stepLabel: ((Int) -> String)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (stepper != null) StoryStepperRow(stepper) else StoryHeader(story.title, story.note)
                story.sections.forEachIndexed { i, section ->
                    if (section.label != null) {
                        StoryLabel(section.label, section.note, top = if (i == 0) 12.dp else 16.dp)
                    }
                    val top = if (section.label != null) 0.dp else 12.dp
                    section.table?.let { StoryTableView(it, Modifier.padding(top = top)) }
                    section.blocks?.let { StoryBlocks(it, Modifier.padding(top = top)) }
                    section.cells?.let { StoryCells(it, Modifier.padding(top = top)) }
                    section.grid?.let { StoryGridLines(it, Modifier.padding(top = top)) }
                    section.fan?.let { StoryFanView(it, Modifier.padding(top = top)) }
                }
                val present = story.tones
                StoryLegendRow(
                    story.legend.filter { it.first in present }.map { (tone, label) -> Triple(tone.color(), SwatchStyle.Fill, label) },
                    Modifier.padding(top = 14.dp),
                )
            }
        }
        StoryChips(story.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(story.headline, story.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = captions, stepLabel = stepLabel)
    }
}

@Composable
internal fun StoryStepperRow(stepper: StoryStepper) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stepper.label, fontSize = 15.sp, color = muted)
        Text(
            "${stepper.value}",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Row(
            modifier = Modifier.height(36.dp).background(SimColors.Tint, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(-1 to "−", 1 to "+").forEachIndexed { i, (delta, glyph) ->
                val enabled = if (delta < 0) stepper.canDecrease else stepper.canIncrease
                if (i == 1) Box(Modifier.size(width = 1.dp, height = 18.dp).background(muted.copy(alpha = 0.35f)))
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { stepper.onChange(delta) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        glyph,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun StoryHeader(title: String, note: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = muted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (note.isNotEmpty()) {
            Text(note, fontSize = 13.sp, color = muted, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun StoryLabel(label: String, note: String?, top: Dp) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(top = top, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        note?.let { Text(it, fontSize = 13.sp, color = muted, maxLines = 1) }
    }
}

/** A tone as a solid tile: its fill and the text on it. Warn is tinted and outlined instead of filled. */
@Composable
internal fun tileColors(tone: StoryTone): Pair<Color, Color> = when (tone) {
    StoryTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
    StoryTone.Done, StoryTone.Path, StoryTone.Answer -> tone.color() to Color.White
    StoryTone.Warn -> SimColors.Red.copy(alpha = 0.18f) to StoryTone.Warn.ink()
    StoryTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f) to MaterialTheme.colorScheme.onSurface
    StoryTone.Empty -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun Modifier.tile(tone: StoryTone, radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val (fill, _) = tileColors(tone)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return this
        .background(fill, shape)
        .then(
            when (tone) {
                StoryTone.Warn -> Modifier.border(1.5.dp, SimColors.Red.copy(alpha = 0.8f), shape)
                StoryTone.Empty -> Modifier.dashedOutline(muted.copy(alpha = 0.4f), radius)
                else -> Modifier
            },
        )
}

/** Proportional blocks in one row: coins by value, a sack by kilograms. */
@Composable
private fun StoryBlocks(blocks: List<StoryBlock>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            Box(
                modifier = Modifier.weight(block.weight).height(46.dp).tile(block.tone, 10.dp).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    block.text,
                    fontFamily = IBMPlexMono,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = tileColors(block.tone).second,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Equal cells, each with an optional caption under it (a dp row's amounts). */
@Composable
private fun StoryCells(cells: List<StoryCell>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEach { cell ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(44.dp).tile(cell.tone, 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(cell.text, fontFamily = IBMPlexMono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tileColors(cell.tone).second, maxLines = 1)
                }
                cell.caption?.let {
                    Text(
                        it,
                        fontFamily = IBMPlexMono,
                        fontSize = 12.sp,
                        fontWeight = if (cell.captionTone == StoryTone.Idle) FontWeight.Normal else FontWeight.Bold,
                        color = if (cell.captionTone == StoryTone.Idle) muted else cell.captionTone.ink(),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Column headers, then one tinted row per entry in its tone's colour. */
@Composable
private fun StoryTableView(table: StoryTable, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (table.headers.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            table.headers.forEachIndexed { i, h ->
                Text(
                    h,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = muted,
                    maxLines = 1,
                    modifier = Modifier.weight(table.weights[i]),
                )
            }
        }
        table.rows.forEach { row ->
            val tint = when (row.tone) {
                StoryTone.Idle, StoryTone.Empty -> muted.copy(alpha = 0.12f)
                else -> row.tone.color().copy(alpha = if (LocalDarkTheme.current) 0.18f else 0.14f)
            }
            val ink = row.tone.ink()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(tint, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.values.forEachIndexed { i, v ->
                    Text(
                        v,
                        fontFamily = IBMPlexMono,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
                        maxLines = 1,
                        textAlign = when (table.aligns[i]) {
                            ColAlign.Start -> TextAlign.Start
                            ColAlign.Center -> TextAlign.Center
                            ColAlign.End -> TextAlign.End
                        },
                        modifier = Modifier.weight(table.weights[i]),
                    )
                }
            }
        }
    }
}

/** Labelled rows of equal cells: a gutter label in mono, then the row. */
@Composable
private fun StoryGridLines(lines: List<StoryGridLine>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(line.label, fontFamily = IBMPlexMono, fontSize = 13.sp, color = muted, modifier = Modifier.width(34.dp))
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    line.cells.forEach { cell ->
                        Box(
                            modifier = Modifier.weight(1f).height(40.dp).tile(cell.tone, 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cell.text, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tileColors(cell.tone).second, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The fan: the call on top, its children spread evenly under it, an edge to each in the child's colour. */
@Composable
private fun StoryFanView(fan: StoryFan, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val count = fan.children.size
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StoryPillView(fan.root)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
            val top = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f)
            fan.children.forEachIndexed { i, child ->
                val color = when (child.tone) {
                    StoryTone.Done -> SimColors.Green
                    StoryTone.Active -> SimColors.Blue
                    else -> muted.copy(alpha = 0.5f)
                }
                val end = androidx.compose.ui.geometry.Offset(size.width * (i + 0.5f) / count, size.height)
                drawLine(color, top, end, strokeWidth = 1.5.dp.toPx())
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            fan.children.forEach { child ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StoryPillView(child)
                    child.caption?.let {
                        Text(
                            it,
                            fontFamily = IBMPlexMono,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (child.tone == StoryTone.Idle) muted else child.tone.ink(),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryPillView(pill: StoryPill) {
    Box(
        modifier = Modifier.height(34.dp).tile(pill.tone, 8.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(pill.text, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tileColors(pill.tone).second, maxLines = 1)
    }
}

// ── Sandbox controls ─────────────────────────────────────────────────────────
// The operation bar of the hands-on labs (Array, Linked List, Stack, Queue, Deque): a segmented op
// picker, an inline segmented option row ("End  Front | Back"), a value field with − / +, and a
// labelled action button in place of a bare play icon, so the button says what it will do.

/** "End" on the left, a compact segmented control on the right. */
@Composable
internal fun LabOptionRow(label: String, options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        LabSegments(options, selected, Modifier.width((options.size * 76).dp)) { if (enabled) onSelect(it) }
    }
}

/** "Value 40" with a − | + pill; the number can also be typed when [editable]. */
@Composable
internal fun LabValueStepper(
    label: String,
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    editable: Boolean = true,
    onStep: (Int) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        modifier = modifier
            .height(60.dp)
            .background(SimColors.Tint, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = muted, maxLines = 1, softWrap = false)
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { input ->
                val sign = if (input.startsWith("-")) "-" else ""
                onText(sign + input.filter(Char::isDigit).take(4))
            },
            enabled = editable,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Row(
            modifier = Modifier.background(muted.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("−", canDecrease) { focus.clearFocus(); onStep(-1) }
            Box(Modifier.width(1.dp).height(18.dp).background(muted.copy(alpha = 0.35f)))
            StepperButton("+", canIncrease) { focus.clearFocus(); onStep(1) }
        }
    }
}

@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

/** The run button, labelled with the operation it performs ("Enqueue →"). */
@Composable
internal fun LabActionButton(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(60.dp)
            .then(
                if (enabled) {
                    Modifier.shadow(14.dp, CircleShape, ambientColor = accent, spotColor = accent)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(accent.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Text("→", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
