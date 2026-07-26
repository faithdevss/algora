package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors

// ── Hashing / bucket-array visualizer ────────────────────────────────────────
// Everything here is "an array of slots plus a rule for which slot a key lands in", so one renderer
// covers separate chaining, a bloom filter's bit array, and an LRU cache's recency order. Frames are
// precomputed like the other players; each carries the slot contents plus which slots to highlight.

private class HashFrame(
    val slots: List<List<String>>,
    val probed: Set<Int>,
    val hit: Set<Int>,
    val miss: Set<Int>,
    val status: String,
)

// A bit array reads naturally as one horizontal strip; bucket chains and a recency list need one
// row each so their contents have somewhere to go.
private enum class SlotLayout { Rows, Strip }

private class HashConfig(
    val intro: String,
    val layout: SlotLayout = SlotLayout.Rows,
    val slotLabel: (Int) -> String,
    val hitLabel: String,
    val probeLabel: String,
    val build: () -> List<HashFrame>,
)

private val ProbedSlot = Color(0xFFFACC15)
private val HitSlot = SimColors.Green
private val MissSlot = Color(0xFFEF4444)
private val FilledSlot = Color(0xFF3B82F6)

// Deterministic small hash so the walkthrough text can state the arithmetic exactly.
private fun hashOf(key: String, buckets: Int): Int {
    var h = 0
    for (ch in key) h = (h * 31 + ch.code) % 1_000_003
    return h % buckets
}

// ── Hash table: separate chaining, including a deliberate collision ──────────
private fun hashTableFrames(): List<HashFrame> {
    val buckets = 7
    val slots = MutableList(buckets) { mutableListOf<String>() }
    val frames = mutableListOf<HashFrame>()
    fun snapshot(status: String, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        frames.add(HashFrame(slots.map { it.toList() }, probed, hit, miss, status))
    }

    snapshot("Seven buckets, separate chaining: a collision appends to the bucket's list rather than displacing anything.")
    for (word in listOf("cat", "dog", "bird", "fish", "owl")) {
        val index = hashOf(word, buckets)
        snapshot("hash(\"$word\") % $buckets = $index", probed = setOf(index))
        val collided = slots[index].isNotEmpty()
        slots[index].add(word)
        snapshot(
            if (collided) "Bucket $index already holds ${slots[index].first()} — collision, so \"$word\" is chained behind it."
            else "\"$word\" stored in bucket $index.",
            hit = setOf(index),
        )
    }

    val lookup = "fish"
    val index = hashOf(lookup, buckets)
    snapshot("Lookup \"$lookup\": hash straight to bucket $index — no scanning of the other buckets.", probed = setOf(index))
    snapshot(
        "Found \"$lookup\" after ${slots[index].indexOf(lookup) + 1} comparison(s) inside the chain. Short chains are what keeps this O(1) on average.",
        hit = setOf(index),
    )

    val missing = "wolf"
    val missIndex = hashOf(missing, buckets)
    snapshot("Lookup \"$missing\": bucket $missIndex", probed = setOf(missIndex))
    snapshot("\"$missing\" is not in that bucket, so it is not in the table at all — one bucket answers the question.", miss = setOf(missIndex))
    return frames
}

// ── Bloom filter: k hash functions over a bit array, with a false positive ───
private fun bloomFilterFrames(): List<HashFrame> {
    val bits = 12
    val state = MutableList(bits) { mutableListOf<String>() }
    val frames = mutableListOf<HashFrame>()
    fun snapshot(status: String, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        frames.add(HashFrame(state.map { it.toList() }, probed, hit, miss, status))
    }
    // Two independent-ish hashes over the same bit array.
    fun h1(k: String) = hashOf(k, bits)
    fun h2(k: String) = hashOf(k + "#salt", bits)

    snapshot("A bloom filter stores no keys — only bits. Two hash functions per key here; 1 means \"some key set this\".")
    for (word in listOf("cat", "dog", "bird")) {
        val a = h1(word)
        val b = h2(word)
        snapshot("Add \"$word\": h₁ = $a, h₂ = $b", probed = setOf(a, b))
        if (word !in state[a]) state[a].add(word)
        if (word !in state[b]) state[b].add(word)
        snapshot("Bits $a and $b set. The word itself is never stored — that is where the space saving comes from.", hit = setOf(a, b))
    }

    val absent = "shark"
    val sa = h1(absent)
    val sb = h2(absent)
    val absentBitsSet = state[sa].isNotEmpty() && state[sb].isNotEmpty()
    snapshot("Query \"$absent\": check bits $sa and $sb", probed = setOf(sa, sb))
    if (absentBitsSet) {
        snapshot(
            "Both bits are already set by other keys → the filter answers \"probably present\" for a word never added. That is a false positive, and it is the price of the space saving.",
            miss = setOf(sa, sb),
        )
    } else {
        val zeroBit = if (state[sa].isEmpty()) sa else sb
        snapshot(
            "Bit $zeroBit is 0, so \"$absent\" was definitely never added. A bloom filter can say \"definitely not\" but never \"definitely yes\".",
            miss = setOf(zeroBit),
        )
    }

    val present = "dog"
    snapshot("Query \"$present\": check bits ${h1(present)} and ${h2(present)}", probed = setOf(h1(present), h2(present)))
    snapshot("Both set → \"probably present\". Confirming it needs the real store behind the filter.", hit = setOf(h1(present), h2(present)))
    return frames
}

// ── LRU cache: fixed capacity, recency order, an eviction and a promotion ────
private fun lruCacheFrames(): List<HashFrame> {
    val capacity = 4
    // Slot 0 = most recently used, slot capacity-1 = eviction candidate.
    val order = mutableListOf<String>()
    val frames = mutableListOf<HashFrame>()
    fun snapshot(status: String, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        val slots = List(capacity) { i -> order.getOrNull(i)?.let { listOf(it) } ?: emptyList() }
        frames.add(HashFrame(slots, probed, hit, miss, status))
    }

    snapshot("Capacity 4, ordered by recency: slot 0 is the most recently used, slot 3 is next to be evicted.")
    for (page in listOf("A", "B", "C", "D")) {
        order.add(0, page)
        snapshot("Miss on $page → load it and put it at the front.", hit = setOf(0))
    }

    snapshot("Access B — already cached.", probed = setOf(order.indexOf("B")))
    order.remove("B")
    order.add(0, "B")
    snapshot("Hit: B is promoted to the front. A is now the least recently used.", hit = setOf(0))

    val evicted = order.last()
    snapshot("Insert E, but the cache is full — evict slot 3 ($evicted), the least recently used.", miss = setOf(capacity - 1))
    order.removeAt(order.lastIndex)
    order.add(0, "E")
    snapshot("$evicted evicted, E inserted at the front. Every operation is O(1) — a hash map for lookup plus a linked list for order.", hit = setOf(0))
    return frames
}

private val hashConfigs = mapOf(
    "hash_table" to HashConfig(
        intro = "Inserting five words into seven buckets with separate chaining, then one hit and one miss. The bucket index comes straight from the hash — no scanning.",
        slotLabel = { it.toString() },
        hitLabel = "Stored / found",
        probeLabel = "Hashed to",
        build = ::hashTableFrames,
    ),
    "bloom_filter" to HashConfig(
        intro = "A 12-bit filter with two hash functions per key. Watch what happens when a word that was never added hashes onto bits other words already set.",
        layout = SlotLayout.Strip,
        slotLabel = { it.toString() },
        hitLabel = "Bit set",
        probeLabel = "Checking",
        build = ::bloomFilterFrames,
    ),
    "lru_cache" to HashConfig(
        intro = "A capacity-4 LRU cache ordered by recency. A hit promotes its entry to the front; an insert into a full cache evicts whatever sits at the back.",
        slotLabel = { if (it == 0) "MRU" else if (it == 3) "LRU" else it.toString() },
        hitLabel = "Cached",
        probeLabel = "Accessing",
        build = ::lruCacheFrames,
    ),
)

private fun hashConfigFor(topicId: String): HashConfig =
    hashConfigs[topicId] ?: hashConfigs.getValue("hash_table")

@Composable
fun HashingVisualizerSection(topicId: String) {
    val config = remember(topicId) { hashConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            fun colorFor(index: Int, entries: List<String>) = when {
                index in frame.miss -> MissSlot
                index in frame.hit -> HitSlot
                index in frame.probed -> ProbedSlot
                entries.isNotEmpty() -> FilledSlot.copy(alpha = 0.45f)
                else -> FilledSlot.copy(alpha = 0.10f)
            }

            when (config.layout) {
                SlotLayout.Rows -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    frame.slots.forEachIndexed { index, entries ->
                        SlotRow(
                            label = config.slotLabel(index),
                            entries = entries,
                            color = colorFor(index, entries),
                        )
                    }
                }

                SlotLayout.Strip -> BitStrip(
                    slots = frame.slots,
                    labelOf = config.slotLabel,
                    colorOf = ::colorFor,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HashLegend(ProbedSlot, config.probeLabel)
                HashLegend(HitSlot, config.hitLabel)
                HashLegend(MissSlot, "Miss / evicted")
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun HashLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

// Bit-array view: one cell per slot showing 0/1, with the index underneath.
@Composable
private fun BitStrip(
    slots: List<List<String>>,
    labelOf: (Int) -> String,
    colorOf: (Int, List<String>) -> Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            slots.forEachIndexed { index, entries ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(colorOf(index, entries), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (entries.isEmpty()) "0" else "1",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (entries.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            slots.indices.forEach { index ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        labelOf(index),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotRow(label: String, entries: List<String>, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth()
                .height(30.dp)
                .background(color, RoundedCornerShape(7.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Chained entries render as a "a → b" run, which is exactly the collision story.
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    Text("→", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                Text(
                    entry,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
