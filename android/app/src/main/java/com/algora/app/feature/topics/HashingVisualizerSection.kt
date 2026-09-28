package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── Hashing / bucket-array visualizer ────────────────────────────────────────
// Everything here is "an array of slots plus a rule for which slot a key lands in", so one renderer
// covers separate chaining, a bloom filter's bit array, and an LRU cache's recency order. Frames are
// precomputed like the other players; each carries the slot contents, which slots to highlight, and
// the strip of operations (requests, keys, inputs) with the outcome of each one so far.
//
// Frames are plain data and are exported to the iOS app as JSON (IosContentExportTest), so both
// platforms play the same script.

enum class HashOpState { DONE, CURRENT, TODO }

/** One operation in the strip above the slots: its key, where it is in the run, and its outcome. */
class HashOp(val text: String, val state: HashOpState, val result: String? = null, val good: Boolean = true)

class HashFrame(
    val slots: List<List<String>>,
    val probed: Set<Int>,
    val hit: Set<Int>,
    val miss: Set<Int>,
    /** Narration: the first sentence is the headline, the rest the explanation. */
    val status: String,
    val ops: List<HashOp> = emptyList(),
    /** A tag at the right of a slot row ("loaded", "hit"). */
    val tags: Map<Int, String> = emptyMap(),
    /** An entry that just left, drawn as a dashed ghost row under the slots. */
    val evicted: String? = null,
    /** "hits = 1 · misses = 5"; hits/found chips are green and misses red while nonzero. */
    val readout: String? = null,
    /** The key this frame is about, tinted yellow wherever the headline names it. */
    val key: String? = null,
    /** When set, [status] is the whole headline and this the explanation, instead of splitting. */
    val detail: String? = null,
    /** More words tinted yellow in the headline (a bloom filter's bit numbers). */
    val marks: List<String> = emptyList(),
    // Bloom filter only: the key being hashed, its hash values in order, and every key added so far.
    val probeKey: String? = null,
    val probeHashes: List<Int> = emptyList(),
    val added: List<String> = emptyList(),
) {
    val caption: String get() = if (detail == null) status else "$status $detail"
}

// A bit array reads naturally as one horizontal strip; bucket chains and a recency list need one
// row each so their contents have somewhere to go.
enum class SlotLayout { Rows, Strip }

enum class HashLegendKind { CURRENT, FILLED, HIT, MISS }

class HashLegend(val kind: HashLegendKind, val label: String)

private class HashConfig(
    val intro: String,
    val layout: SlotLayout = SlotLayout.Rows,
    val slotLabel: (Int) -> String,
    val opsLabel: String,
    val legend: List<HashLegend>,
    val build: () -> List<HashFrame>,
)

/** A config as data, with its frames built and its slot labels resolved — what iOS reads. */
class HashLabExport(
    val intro: String,
    val layout: SlotLayout,
    val slotLabels: List<String>,
    val opsLabel: String,
    val legend: List<HashLegend>,
    val frames: List<HashFrame>,
)

private val ProbedSlot = SimColors.Active
private val HitSlot = SimColors.Green
private val MissSlot = SimColors.Red
private val FilledSlot = SimColors.Blue

// Deterministic small hash so the walkthrough text can state the arithmetic exactly.
private fun hashOf(key: String, buckets: Int): Int {
    var h = 0
    for (ch in key) h = (h * 31 + ch.code) % 1_000_003
    return h % buckets
}

/** The operation strip for one script: fill in outcomes as the run reaches each operation. */
private class OpTrack(private val texts: List<String>) {
    private val results = MutableList<Pair<String, Boolean>?>(texts.size) { null }

    fun result(index: Int, text: String, good: Boolean) {
        results[index] = text to good
    }

    fun strip(current: Int?): List<HashOp> = texts.mapIndexed { i, text ->
        val state = when {
            current == null -> if (results[i] != null) HashOpState.DONE else HashOpState.TODO
            i < current -> HashOpState.DONE
            i == current -> HashOpState.CURRENT
            else -> HashOpState.TODO
        }
        val r = if (state == HashOpState.TODO) null else results[i]
        HashOp(text, state, r?.first, r?.second ?: true)
    }
}

// ── Hash table: separate chaining, including a deliberate collision ──────────
private fun hashTableFrames(): List<HashFrame> {
    val buckets = 7
    val slots = MutableList(buckets) { mutableListOf<String>() }
    val frames = mutableListOf<HashFrame>()
    val inserts = listOf("cat", "dog", "bird", "fish", "owl")
    val lookup = "fish"
    val missing = "wolf"
    val ops = OpTrack(inserts + lookup + missing)
    var collisions = 0
    fun snapshot(status: String, op: Int?, key: String? = null, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        frames.add(
            HashFrame(
                slots.map { it.toList() }, probed, hit, miss, status,
                ops = ops.strip(op),
                readout = "stored = ${slots.sumOf { it.size }} · buckets = $buckets · collisions = $collisions",
                key = key,
            ),
        )
    }

    snapshot("Seven buckets, separate chaining. A collision appends to the bucket's list rather than displacing anything.", op = null)
    inserts.forEachIndexed { i, word ->
        val index = hashOf(word, buckets)
        snapshot("hash(\"$word\") % $buckets = $index. The key goes straight to bucket $index.", op = i, key = word, probed = setOf(index))
        val collided = slots[index].isNotEmpty()
        if (collided) collisions++
        slots[index].add(word)
        ops.result(i, "b$index", !collided)
        snapshot(
            if (collided) "Bucket $index already holds ${slots[index].first()}, so \"$word\" is chained behind it. That is a collision, and it costs one extra comparison later."
            else "\"$word\" is stored in bucket $index. Nothing else is touched.",
            op = i, key = word, hit = setOf(index),
        )
    }

    val li = inserts.size
    val index = hashOf(lookup, buckets)
    snapshot("Look up \"$lookup\": hash straight to bucket $index. No other bucket is scanned.", op = li, key = lookup, probed = setOf(index))
    ops.result(li, "found", true)
    snapshot(
        "Found \"$lookup\" after ${slots[index].indexOf(lookup) + 1} comparison(s) inside the chain. Short chains are what keep this O(1) on average.",
        op = li, key = lookup, hit = setOf(index),
    )

    val mi = li + 1
    val missIndex = hashOf(missing, buckets)
    snapshot("Look up \"$missing\": hash to bucket $missIndex.", op = mi, key = missing, probed = setOf(missIndex))
    ops.result(mi, "absent", false)
    snapshot("\"$missing\" is not in bucket $missIndex, so it is not in the table at all. One bucket answers the question.", op = mi, key = missing, miss = setOf(missIndex))
    return frames
}

// ── Bloom filter: k hash functions over a bit array, with a false positive ───
private fun bloomFilterFrames(): List<HashFrame> {
    val bits = 12
    val k = 2
    val state = MutableList(bits) { mutableListOf<String>() }
    val frames = mutableListOf<HashFrame>()
    val adds = listOf("cat", "dog", "bird")
    // Never added, but both of its bits end up set by other keys: the false positive the lab is for.
    val absent = "duck"
    val present = "dog"
    val ops = OpTrack(adds + absent + present)
    val added = mutableListOf<String>()
    // Two independent-ish hashes over the same bit array.
    fun h1(key: String) = hashOf(key, bits)
    fun h2(key: String) = hashOf(key + "#salt", bits)

    fun snapshot(
        headline: String,
        detail: String,
        op: Int?,
        probeKey: String? = null,
        probed: Set<Int> = emptySet(),
        miss: Set<Int> = emptySet(),
    ) {
        val set = state.count { it.isNotEmpty() }
        // The chance a key never added reads as present, given the bits set right now: (set / m)^k.
        val falsePositive = Math.pow(set.toDouble() / bits, k.toDouble()) * 100
        frames.add(
            HashFrame(
                state.map { it.toList() }, probed, emptySet(), miss, headline,
                ops = ops.strip(op),
                readout = "bits set = $set / $bits · false positive = ≈${"%.0f".format(falsePositive)}%",
                detail = detail,
                marks = probed.sorted().map { it.toString() },
                probeKey = probeKey,
                probeHashes = probeKey?.let { listOf(h1(it), h2(it)) }.orEmpty(),
                added = added.toList(),
            ),
        )
    }
    fun bitsPhrase(a: Int, b: Int) = if (a == b) "bit $a" else "bits ${minOf(a, b)} and ${maxOf(a, b)}"

    snapshot(
        "A Bloom filter stores bits, not keys.",
        "Each key sets $k bits, one from each hash function. A 1 only means some key set it.",
        op = null,
    )
    adds.forEachIndexed { i, word ->
        val a = h1(word)
        val b = h2(word)
        val alreadySet = listOf(a, b).distinct().filter { state[it].isNotEmpty() }
        if (word !in state[a]) state[a].add(word)
        if (word !in state[b]) state[b].add(word)
        added += word
        ops.result(i, "added", true)
        snapshot(
            "Add \"$word\": set ${bitsPhrase(a, b)}.",
            when {
                i == adds.lastIndex -> "Bits are only ever set, never cleared. That's why a Bloom filter can't delete."
                alreadySet.isNotEmpty() -> "Bit ${alreadySet.first()} was already set by ${state[alreadySet.first()].first()}, so " +
                    "the two keys now share it. Shared bits are where false positives come from."
                else -> "The word itself is never stored. Two bits stand in for it, which is where the space saving comes from."
            },
            op = i, probeKey = word, probed = setOf(a, b),
        )
    }

    val ai = adds.size
    val sa = h1(absent)
    val sb = h2(absent)
    if (state[sa].isNotEmpty() && state[sb].isNotEmpty()) {
        ops.result(ai, "maybe", false)
        snapshot(
            "Check \"$absent\": ${bitsPhrase(sa, sb)} ${if (sa == sb) "is" else "are"} already set.",
            "It was never added, so \"probably present\" is wrong here. That false positive is the price of the space saving.",
            op = ai, probeKey = absent, probed = setOf(sa, sb), miss = setOf(sa, sb),
        )
    } else {
        val zeroBit = if (state[sa].isEmpty()) sa else sb
        ops.result(ai, "no", true)
        snapshot(
            "Check \"$absent\": bit $zeroBit is 0.",
            "So \"$absent\" was definitely never added. A Bloom filter can say definitely not, but never definitely yes.",
            op = ai, probeKey = absent, probed = setOf(sa, sb), miss = setOf(zeroBit),
        )
    }

    val pi = ai + 1
    ops.result(pi, "maybe", true)
    snapshot(
        "Check \"$present\": ${bitsPhrase(h1(present), h2(present))} ${if (h1(present) == h2(present)) "is" else "are both"} set.",
        "So \"$present\" is probably present. Confirming it needs the real store behind the filter.",
        op = pi, probeKey = present, probed = setOf(h1(present), h2(present)),
    )
    return frames
}

// ── LRU cache: fixed capacity, recency order, evictions and a promotion ──────
private fun lruCacheFrames(): List<HashFrame> {
    val capacity = 4
    val requests = listOf("A", "B", "C", "A", "D", "E", "A", "F", "B")
    // Index 0 = most recently used, index capacity-1 = next to be evicted.
    val order = mutableListOf<String>()
    val lastTouched = mutableMapOf<String, Int>()
    val loadedAt = mutableMapOf<String, Int>()
    val ops = OpTrack(requests)
    val frames = mutableListOf<HashFrame>()
    var hits = 0
    var misses = 0

    requests.forEachIndexed { i, key ->
        val hit = key in order
        var evicted: String? = null
        val headline: String
        val detail: String
        if (hit) {
            hits++
            order.remove(key)
            order.add(0, key)
            ops.result(i, "hit", true)
            headline = "Hit on $key. It moves to the front."
            detail = "Touching $key makes it the most recently used, so ${order.last()} is now next in line to go."
        } else {
            misses++
            if (order.size == capacity) evicted = order.removeAt(order.lastIndex)
            order.add(0, key)
            loadedAt[key] = i
            ops.result(i, "miss", false)
            if (evicted != null) {
                headline = "Miss on $key. The cache is full, so $evicted, the least recently used, is evicted."
                // A key loaded before the evicted one that is still cached survived by being touched again.
                val survivor = order.drop(1).firstOrNull { k ->
                    (loadedAt[k] ?: 0) < (loadedAt[evicted] ?: 0) && (lastTouched[k] ?: -1) > (loadedAt[k] ?: 0)
                }
                detail = if (survivor != null) {
                    "$survivor survives because request ${lastTouched.getValue(survivor) + 1} touched it again."
                } else {
                    "$key takes the front. A hash map finds entries and a linked list keeps the order, so both steps are O(1)."
                }
            } else if (order.size == capacity) {
                headline = "Miss on $key. It loads at the front and fills the last free slot."
                detail = "From here every miss has to evict something, starting with ${order.last()}."
            } else {
                headline = "Miss on $key. It loads at the front, and the cache still has room."
                detail = "Nothing is evicted until all $capacity slots are full."
            }
        }
        lastTouched[key] = i
        frames.add(
            HashFrame(
                slots = List(capacity) { s -> order.getOrNull(s)?.let { listOf(it) } ?: emptyList() },
                probed = setOf(0),
                hit = emptySet(),
                miss = emptySet(),
                status = headline,
                detail = detail,
                ops = ops.strip(i),
                tags = mapOf(0 to if (hit) "hit" else "loaded"),
                evicted = evicted,
                readout = "hits = $hits · misses = $misses · capacity = $capacity",
                key = key,
            ),
        )
    }
    return frames
}

// ── Set ADT: membership, and what "no duplicates" costs to enforce ───────────
private fun setAdtFrames(): List<HashFrame> {
    val buckets = 7
    val slots = MutableList(buckets) { mutableListOf<String>() }
    val frames = mutableListOf<HashFrame>()
    val inserts = listOf("red", "blue", "green", "blue", "amber")
    val present = "green"
    val absent = "violet"
    val ops = OpTrack(inserts + present + absent)
    fun snapshot(status: String, op: Int?, key: String? = null, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        frames.add(
            HashFrame(
                slots.map { it.toList() }, probed, hit, miss, status,
                ops = ops.strip(op),
                readout = "size = ${slots.sumOf { it.size }} · buckets = $buckets",
                key = key,
            ),
        )
    }

    snapshot(
        "A set stores unordered values with no duplicates. It promises add, contains and remove, and nothing about order or position.",
        op = null,
    )

    inserts.forEachIndexed { i, word ->
        val index = hashOf(word, buckets)
        val duplicate = slots[index].contains(word)
        if (duplicate) {
            ops.result(i, "dup", false)
            snapshot(
                "add(\"$word\") hashes to $index, and the bucket already holds it, so the add is rejected. Uniqueness costs only " +
                    "the ${slots[index].size} comparison(s) inside one bucket, not a scan of everything stored.",
                op = i, key = word, probed = setOf(index), miss = setOf(index),
            )
        } else {
            slots[index].add(word)
            ops.result(i, "added", true)
            snapshot("add(\"$word\") hashes to bucket $index and is new, so it is stored.", op = i, key = word, probed = setOf(index), hit = setOf(index))
        }
    }

    val pi = inserts.size
    val presentIndex = hashOf(present, buckets)
    ops.result(pi, "yes", true)
    snapshot(
        "contains(\"$present\") hashes straight to bucket $presentIndex and checks only what is in it. The other " +
            "${inserts.distinct().size - slots[presentIndex].size} stored values are never compared.",
        op = pi, key = present, probed = setOf(presentIndex), hit = setOf(presentIndex),
    )

    val ai = pi + 1
    val absentIndex = hashOf(absent, buckets)
    ops.result(ai, "no", true)
    snapshot(
        "contains(\"$absent\") hashes to bucket $absentIndex" +
            (if (slots[absentIndex].isEmpty()) ", which is empty, so the answer is no after one lookup."
            else ", which holds ${slots[absentIndex].joinToString(", ")}. None match, so the answer is no."),
        op = ai, key = absent, probed = setOf(absentIndex), miss = setOf(absentIndex),
    )

    snapshot(
        "A hash table is one way to honour the contract, not the contract itself. A balanced tree gives the same " +
            "operations in O(log n) and keeps the elements sorted; a bit array does it in O(1) when the universe is small.",
        op = null,
        hit = slots.indices.filter { slots[it].isNotEmpty() }.toSet(),
    )
    return frames
}

// ── Map ADT: the same machinery, keyed ───────────────────────────────────────
private fun mapAdtFrames(): List<HashFrame> {
    val buckets = 7
    val slots = MutableList(buckets) { mutableListOf<String>() }
    val frames = mutableListOf<HashFrame>()
    val entries = listOf("cat" to "9", "dog" to "4", "owl" to "2")
    val updateKey = "dog"
    val getKey = "cat"
    val missingKey = "fox"
    val ops = OpTrack(entries.map { it.first } + updateKey + getKey + missingKey)
    fun snapshot(status: String, op: Int?, key: String? = null, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        frames.add(
            HashFrame(
                slots.map { it.toList() }, probed, hit, miss, status,
                ops = ops.strip(op),
                readout = "pairs = ${slots.sumOf { it.size }} · buckets = $buckets",
                key = key,
            ),
        )
    }

    snapshot("A map stores key → value pairs, each key once. Only the key is hashed; the value comes along and is never searched.", op = null)

    entries.forEachIndexed { i, (key, value) ->
        val index = hashOf(key, buckets)
        slots[index].add("$key→$value")
        ops.result(i, "put", true)
        snapshot("put(\"$key\", $value): hash(\"$key\") % $buckets = $index. The pair is stored in that bucket.", op = i, key = key, probed = setOf(index), hit = setOf(index))
    }

    val ui = entries.size
    val updateIndex = hashOf(updateKey, buckets)
    val existing = slots[updateIndex].indexOfFirst { it.startsWith("$updateKey→") }
    val oldValue = slots[updateIndex][existing].substringAfter("→")
    slots[updateIndex][existing] = "$updateKey→7"
    ops.result(ui, "update", true)
    snapshot(
        "put(\"$updateKey\", 7) finds the key already in bucket $updateIndex, so it overwrites $oldValue → 7 instead of adding a " +
            "second entry. That is the difference between a map and a multimap, enforced on the key alone.",
        op = ui, key = updateKey, probed = setOf(updateIndex), hit = setOf(updateIndex),
    )

    val gi = ui + 1
    val getIndex = hashOf(getKey, buckets)
    val got = slots[getIndex].first { it.startsWith("$getKey→") }.substringAfter("→")
    ops.result(gi, got, true)
    snapshot(
        "get(\"$getKey\") hashes to bucket $getIndex and returns $got. The cost depends on how full that one bucket is, not on how many pairs the map holds.",
        op = gi, key = getKey, probed = setOf(getIndex), hit = setOf(getIndex),
    )

    val mi = gi + 1
    val missingIndex = hashOf(missingKey, buckets)
    ops.result(mi, "absent", false)
    snapshot(
        "get(\"$missingKey\") hashes to bucket $missingIndex and finds no matching key, so the map reports it absent. " +
            "A set is just a map whose values are ignored: the same buckets and hashing, one less thing stored.",
        op = mi, key = missingKey, probed = setOf(missingIndex), miss = setOf(missingIndex),
    )
    return frames
}

// ── Interview-prep pattern guide ─────────────────────────────────────────────
// Subarrays summing to k, via a map of prefix sums. The map is not counting the input — it is
// counting *prefixes*, which is the step that removes the inner loop.
private val prefixCountInput = listOf(3, 4, 7, 2, -3, 1, 4, 2)
private const val PREFIX_TARGET = 7

private fun prefixCountFrames(): List<HashFrame> {
    val buckets = 7
    val slots = MutableList(buckets) { mutableListOf<String>() }
    val counts = HashMap<Int, Int>()
    val frames = mutableListOf<HashFrame>()
    val ops = OpTrack(prefixCountInput.map { it.toString() })
    var prefix = 0
    var total = 0

    fun snapshot(status: String, op: Int?, probed: Set<Int> = emptySet(), hit: Set<Int> = emptySet(), miss: Set<Int> = emptySet()) {
        frames.add(
            HashFrame(
                slots.map { it.toList() }, probed, hit, miss, status,
                ops = ops.strip(op),
                readout = "prefix = $prefix · found = $total · target = $PREFIX_TARGET",
            ),
        )
    }

    fun store(prefix: Int) {
        val n = (counts[prefix] ?: 0) + 1
        counts[prefix] = n
        val index = hashOf(prefix.toString(), buckets)
        val existing = slots[index].indexOfFirst { it.startsWith("$prefix×") }
        if (existing >= 0) slots[index][existing] = "$prefix×$n" else slots[index].add("$prefix×$n")
    }

    store(0)
    snapshot(
        "Count the subarrays summing to $PREFIX_TARGET. The map holds prefix sums and how often each has occurred, " +
            "seeded with 0×1: the empty prefix, which is why a subarray starting at index 0 counts at all.",
        op = null,
        hit = setOf(hashOf("0", buckets)),
    )

    prefixCountInput.forEachIndexed { i, value ->
        prefix += value
        val wanted = prefix - PREFIX_TARGET
        val wantedIndex = hashOf(wanted.toString(), buckets)
        val found = counts[wanted] ?: 0
        total += found
        ops.result(i, if (found > 0) "+$found" else "0", found > 0)
        snapshot(
            "Prefix is now $prefix, so look up $prefix − $PREFIX_TARGET = $wanted. " +
                if (found > 0) "The map holds $wanted $found time(s): $found subarray(s) end here, found without looking at a single element."
                else "$wanted has never occurred, so no subarray ends here.",
            op = i,
            probed = setOf(wantedIndex),
            hit = if (found > 0) setOf(wantedIndex) else emptySet(),
            miss = if (found > 0) emptySet() else setOf(wantedIndex),
        )
        store(prefix)
        snapshot(
            "Record prefix $prefix so later positions can ask about it. Looking up before storing is what stops a zero-length subarray counting itself.",
            op = i,
            hit = setOf(hashOf(prefix.toString(), buckets)),
        )
    }

    snapshot(
        "$total subarrays sum to $PREFIX_TARGET, in one pass and ${prefixCountInput.size} lookups. Swap the key for a " +
            "character count, a remainder or a parity and the same skeleton answers anagrams, divisibility and even-odd questions.",
        op = null,
        hit = slots.indices.filter { slots[it].isNotEmpty() }.toSet(),
    )
    return frames
}

private val bucketLegend = listOf(
    HashLegend(HashLegendKind.CURRENT, "Hashed to"),
    HashLegend(HashLegendKind.FILLED, "Stored"),
    HashLegend(HashLegendKind.HIT, "Found"),
    HashLegend(HashLegendKind.MISS, "Absent"),
)

private val hashConfigs = mapOf(
    "hash_counting_pattern" to HashConfig(
        intro = "Subarrays summing to $PREFIX_TARGET, counted with a map of prefix sums. Each step asks the map one " +
            "question — has this complement been seen? — which is the inner loop the pattern deletes.",
        slotLabel = { "b$it" },
        opsLabel = "INPUT",
        legend = listOf(
            HashLegend(HashLegendKind.CURRENT, "Looked up"),
            HashLegend(HashLegendKind.FILLED, "Stored"),
            HashLegend(HashLegendKind.HIT, "Matched"),
            HashLegend(HashLegendKind.MISS, "Not seen"),
        ),
        build = ::prefixCountFrames,
    ),
    "hash_table" to HashConfig(
        intro = "Inserting five words into seven buckets with separate chaining, then one hit and one miss. The bucket index comes straight from the hash — no scanning.",
        slotLabel = { "b$it" },
        opsLabel = "KEYS",
        legend = bucketLegend,
        build = ::hashTableFrames,
    ),
    "bloom_filter" to HashConfig(
        intro = "A 12-bit filter with two hash functions per key. Watch what happens when a word that was never added hashes onto bits other words already set.",
        layout = SlotLayout.Strip,
        slotLabel = { it.toString() },
        // No strip: the key box and the Added row already say which key is where.
        opsLabel = "",
        legend = listOf(
            HashLegend(HashLegendKind.CURRENT, "Setting now"),
            HashLegend(HashLegendKind.FILLED, "Already set"),
            HashLegend(HashLegendKind.MISS, "False positive"),
        ),
        build = ::bloomFilterFrames,
    ),
    "lru_cache" to HashConfig(
        intro = "A capacity-4 LRU cache ordered by recency. A hit promotes its entry to the front; a miss on a full cache evicts whatever sits at the back.",
        slotLabel = { if (it == 0) "MRU" else if (it == 3) "LRU" else (it + 1).toString() },
        opsLabel = "REQUESTS",
        legend = listOf(
            HashLegend(HashLegendKind.CURRENT, "Accessing"),
            HashLegend(HashLegendKind.FILLED, "Cached"),
            HashLegend(HashLegendKind.MISS, "Evicted"),
        ),
        build = ::lruCacheFrames,
    ),
    "set_adt" to HashConfig(
        intro = "The set contract — add, contains, remove, no duplicates and no order — shown against one " +
            "implementation of it, including the moment a duplicate add is rejected.",
        slotLabel = { "b$it" },
        opsLabel = "OPERATIONS",
        legend = listOf(
            HashLegend(HashLegendKind.CURRENT, "Hashed to"),
            HashLegend(HashLegendKind.FILLED, "Stored"),
            HashLegend(HashLegendKind.HIT, "Added / found"),
            HashLegend(HashLegendKind.MISS, "Rejected / absent"),
        ),
        build = ::setAdtFrames,
    ),
    "map_adt" to HashConfig(
        intro = "Key → value pairs where only the key is hashed. Watch the second put on an existing key overwrite " +
            "rather than duplicate.",
        slotLabel = { "b$it" },
        opsLabel = "KEYS",
        legend = bucketLegend,
        build = ::mapAdtFrames,
    ),
)

private fun hashConfigFor(topicId: String): HashConfig =
    hashConfigs[topicId] ?: hashConfigs.getValue("hash_table")

// Exposed so PatternCoverageTest can tell a topic that configured this widget from one that only
// inherits the fallback above.
internal val hashingVisualizerTopicIds: Set<String> get() = hashConfigs.keys

/** Every config as data, for the iOS export. */
internal val hashingLabExports: Map<String, HashLabExport> by lazy {
    hashConfigs.mapValues { (_, config) ->
        val frames = config.build()
        HashLabExport(
            intro = config.intro,
            layout = config.layout,
            slotLabels = frames.first().slots.indices.map(config.slotLabel),
            opsLabel = config.opsLabel,
            legend = config.legend,
            frames = frames,
        )
    }
}

/** Highlights and tags are slot indices, and the slot count is whatever the frame itself declares. */
internal fun hashingFrameCount(topicId: String): Int {
    val frames = hashConfigFor(topicId).build()
    frames.forEachIndexed { index, frame ->
        require(frame.slots.isNotEmpty()) { "$topicId frame $index has no slots" }
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        (frame.probed + frame.hit + frame.miss + frame.tags.keys).forEach { slot ->
            require(slot in frame.slots.indices) {
                "$topicId frame $index highlights slot $slot of ${frame.slots.size}"
            }
        }
        require(frame.ops.count { it.state == HashOpState.CURRENT } <= 1) { "$topicId frame $index has two current operations" }
    }
    return frames.size
}

// ── UI ───────────────────────────────────────────────────────────────────────
// Player layout: a stage card (operation strip, slots, legend), readout chips, narration, then the
// transport — pinned in thumb reach when docked.

@Composable
fun HashingVisualizerSection(topicId: String) {
    val config = remember(topicId) { hashConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val dock = LocalLabDock.current

    LabIntro(config.intro)
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val showOps = frame.ops.isNotEmpty() && config.opsLabel.isNotEmpty()
                if (showOps) OpStrip(config.opsLabel, frame.ops)
                val top = if (showOps) 18.dp else 0.dp
                when (config.layout) {
                    SlotLayout.Rows -> Column(
                        modifier = Modifier.fillMaxWidth().padding(top = top),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        frame.slots.forEachIndexed { index, entries ->
                            SlotRow(config.slotLabel(index), entries, slotLook(frame, index, entries), frame.tags[index])
                        }
                        frame.evicted?.let { GhostRow(it) }
                    }
                    SlotLayout.Strip -> BloomStage(frame, config.slotLabel, Modifier.padding(top = top))
                }
                HashLegendRow(config.legend, Modifier.padding(top = 16.dp))
                if (dock == null) {
                    HashReadout(frame, Modifier.padding(top = 16.dp))
                    HashNarration(frame, Modifier.padding(top = 14.dp))
                }
                PlaybackTransport(playback, captions = frames.map { it.caption })
            }
        }
        if (dock != null) {
            HashReadout(frame, Modifier.padding(top = 14.dp))
            HashNarration(frame, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }
}

private class SlotLook(val fill: Color, val border: Color?, val text: Color)

@Composable
private fun slotLook(frame: HashFrame, index: Int, entries: List<String>): SlotLook {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return when {
        index in frame.miss -> SlotLook(MissSlot.copy(alpha = 0.16f), MissSlot, onSurface)
        index in frame.hit -> SlotLook(HitSlot.copy(alpha = 0.2f), HitSlot, onSurface)
        index in frame.probed -> SlotLook(ProbedSlot, null, Color(0xFF1A1A1A))
        entries.isNotEmpty() -> SlotLook(FilledSlot.copy(alpha = 0.28f), FilledSlot, onSurface)
        else -> SlotLook(SimColors.Tint.copy(alpha = 0.35f), null, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OpStrip(label: String, ops: List<HashOp>) {
    val dark = Color(0xFF1A1A1A)
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ops.forEach { op ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .widthIn(min = 36.dp)
                            .background(
                                when (op.state) {
                                    HashOpState.CURRENT -> SimColors.Active
                                    HashOpState.DONE -> SimColors.Tint
                                    HashOpState.TODO -> Color.Transparent
                                },
                                RoundedCornerShape(9.dp),
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            op.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (op.state) {
                                HashOpState.CURRENT -> dark
                                HashOpState.DONE -> MaterialTheme.colorScheme.onSurface
                                HashOpState.TODO -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                    }
                    Text(
                        op.result ?: " ",
                        fontFamily = IBMPlexMono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (op.good) SimColors.Green else SimColors.Red,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotLabel(label: String) {
    Text(
        label,
        fontFamily = IBMPlexMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(52.dp),
    )
}

@Composable
private fun SlotRow(label: String, entries: List<String>, look: SlotLook, tag: String?) {
    val shape = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SlotLabel(label)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(look.fill, shape)
                .then(if (look.border != null) Modifier.border(1.5.dp, look.border, shape) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chained entries render as an "a → b" run, which is exactly the collision story.
            Text(
                entries.joinToString("  →  "),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = look.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            tag?.let { Text(it, fontSize = 15.sp, color = look.text.copy(alpha = 0.85f)) }
        }
    }
}

/** An entry that just left: a dashed red outline under the slots. */
@Composable
private fun GhostRow(text: String) {
    val shape = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SlotLabel("")
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(MissSlot.copy(alpha = 0.1f), shape)
                .drawBehind {
                    drawRoundRect(
                        MissSlot.copy(alpha = 0.8f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MissSlot.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            Text("evicted", fontSize = 15.sp, color = MissSlot.copy(alpha = 0.85f))
        }
    }
}

// Bit-array view: the key being hashed and its hash values, the bits six to a row with their index
// underneath, then every key added so far.
@Composable
private fun BloomStage(frame: HashFrame, labelOf: (Int) -> String, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        frame.probeKey?.let { key ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
                Box(
                    modifier = Modifier
                        .background(ProbedSlot.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(2.dp, ProbedSlot, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("\"$key\"", fontFamily = IBMPlexMono, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Text("→", fontSize = 22.sp, color = muted, modifier = Modifier.padding(horizontal = 12.dp))
                frame.probeHashes.forEachIndexed { i, h ->
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(40.dp)
                            .background(SimColors.Tint, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("h${"₁₂₃₄".getOrElse(i) { '?' }}", fontFamily = IBMPlexMono, fontSize = 16.sp, color = muted)
                        Text("$h", fontFamily = IBMPlexMono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ProbedSlot, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            frame.slots.indices.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { index ->
                        val entries = frame.slots[index]
                        val look = slotLook(frame, index, entries)
                        val shape = RoundedCornerShape(12.dp)
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .background(look.fill, shape)
                                    .then(if (look.border != null) Modifier.border(1.5.dp, look.border, shape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (entries.isEmpty()) "0" else "1",
                                    fontFamily = IBMPlexMono,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = look.text,
                                )
                            }
                            Text(
                                labelOf(index),
                                fontFamily = IBMPlexMono,
                                fontSize = 13.sp,
                                color = if (index in frame.probed) ProbedSlot else muted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (frame.added.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Text("Added", fontSize = 15.sp, color = muted, modifier = Modifier.padding(end = 10.dp))
                frame.added.forEach { key ->
                    val current = key == frame.probeKey
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(if (current) ProbedSlot.copy(alpha = 0.18f) else SimColors.Tint, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(key, fontFamily = IBMPlexMono, fontSize = 15.sp, color = if (current) ProbedSlot else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun HashLegendRow(items: List<HashLegend>, modifier: Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(3.dp)
                Box(
                    modifier = when (item.kind) {
                        HashLegendKind.CURRENT -> Modifier.size(10.dp).background(ProbedSlot, shape)
                        HashLegendKind.FILLED -> Modifier.size(10.dp).background(FilledSlot, shape)
                        HashLegendKind.HIT -> Modifier.size(10.dp).background(HitSlot, shape)
                        HashLegendKind.MISS -> Modifier.size(10.dp).border(2.dp, MissSlot, shape)
                    },
                )
                Text(item.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private val goodKeys = setOf("hits", "found")
private val badKeys = setOf("misses", "collisions")

@Composable
private fun HashReadout(frame: HashFrame, modifier: Modifier) {
    val readout = frame.readout ?: return
    val parts = readoutParts(readout)
    fun nonzero(i: Int) = parts[i].second.trim() != "0"
    ReadoutChips(
        parts,
        modifier,
        positive = parts.indices.filter { parts[it].first in goodKeys && nonzero(it) }.toSet(),
        warned = parts.indices.filter { parts[it].first in badKeys && nonzero(it) }.toSet(),
    )
}

@Composable
private fun HashNarration(frame: HashFrame, modifier: Modifier) {
    val (head, tail) = if (frame.detail != null) frame.status to frame.detail else LabCaptionText.split(frame.status)
    val key = if (LocalDarkTheme.current) SimColors.Active else Color(0xFFB45309)
    // The key this frame is about, and any marked words, are tinted yellow in the headline.
    val headline = buildAnnotatedString {
        val words = (listOfNotNull(frame.key) + frame.marks).toSet()
        var last = 0
        Regex("[\\p{L}\\p{N}]+").findAll(head).forEach { m ->
            if (m.value in words) {
                append(head.substring(last, m.range.first))
                withStyle(SpanStyle(color = key)) { append(m.value) }
                last = m.range.last + 1
            }
        }
        append(head.substring(last))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        if (tail != null) {
            Text(tail, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
