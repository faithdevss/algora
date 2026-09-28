package com.algora.app.feature.topics

// ── D2 · Syntactic and semantic analysis math ────────────────────────────────
// POS tagging, dependency and constituency parsing, chunking, coreference and lexicon sentiment.
// Everything the six labs quote is computed here — taggers and parsers are run against gold
// annotations and scored, rather than described — and `D2MathTest` pins the results the copy leans
// on. Same rule as D1 and D3: a claim that stops being true fails a test instead of shipping.

// ── Part-of-speech tagging ───────────────────────────────────────────────────

internal object PosLab {

    /** A hand-tagged mini treebank. Tags follow the Penn Treebank set. */
    val train: List<List<Pair<String, String>>> = listOf(
        listOf("the" to "DT", "dog" to "NN", "chased" to "VBD", "a" to "DT", "cat" to "NN"),
        listOf("a" to "DT", "small" to "JJ", "dog" to "NN", "barks" to "VBZ"),
        listOf("the" to "DT", "cat" to "NN", "watched" to "VBD", "the" to "DT", "birds" to "NNS"),
        listOf("dogs" to "NNS", "chase" to "VBP", "cats" to "NNS"),
        listOf("the" to "DT", "old" to "JJ", "man" to "NN", "walks" to "VBZ", "slowly" to "RB"),
        listOf("they" to "PRP", "man" to "VBP", "the" to "DT", "boats" to "NNS"),
        listOf("she" to "PRP", "walks" to "VBZ", "the" to "DT", "dog" to "NN"),
        listOf("the" to "DT", "small" to "JJ", "cat" to "NN", "sleeps" to "VBZ"),
        listOf("a" to "DT", "man" to "NN", "walks" to "VBZ", "quickly" to "RB"),
        listOf("the" to "DT", "birds" to "NNS", "watched" to "VBD", "a" to "DT", "cat" to "NN"),
        // Added after the first probe: without VBP → RB evidence anywhere in training, the HMM
        // tagged the held-out "the dogs walk slowly" as DT NN VBZ RB and tied the baseline, so the
        // topic's whole comparison collapsed. These two sentences supply that transition.
        listOf("the" to "DT", "birds" to "NNS", "fly" to "VBP", "quickly" to "RB"),
        listOf("dogs" to "NNS", "bark" to "VBP", "loudly" to "RB"),
    )

    /** Held-out sentences with gold tags — the only text either tagger is scored on. */
    val test: List<List<Pair<String, String>>> = listOf(
        listOf("the" to "DT", "old" to "JJ", "man" to "NN", "chased" to "VBD", "a" to "DT", "cat" to "NN"),
        listOf("they" to "PRP", "man" to "VBP", "a" to "DT", "boat" to "NN"),
        listOf("the" to "DT", "dogs" to "NNS", "walk" to "VBP", "slowly" to "RB"),
    )

    val tagset: List<String> by lazy { (train + test).flatten().map { it.second }.distinct().sorted() }

    private val tagCounts: Map<String, Map<String, Int>> by lazy {
        train.flatten().groupBy({ it.first }, { it.second })
            .mapValues { (_, tags) -> tags.groupingBy { it }.eachCount() }
    }

    val ambiguousTypes: List<String> by lazy { tagCounts.filter { it.value.size > 1 }.keys.sorted() }

    val typeCount: Int get() = tagCounts.size

    /** Share of *tokens* whose type is ambiguous — always far higher than the share of types. */
    val ambiguousTokenShare: Double
        get() {
            val tokens = train.flatten()
            return tokens.count { it.first in ambiguousTypes }.toDouble() / tokens.size
        }

    fun tagsOf(word: String): Map<String, Int> = tagCounts[word].orEmpty()

    /** Baseline every tagger is measured against: each word gets its most frequent tag. */
    fun mostFrequentTag(word: String): String =
        tagCounts[word]?.maxByOrNull { it.value }?.key ?: "NN"

    fun baselineTags(sentence: List<String>): List<String> = sentence.map { mostFrequentTag(it) }

    // A bigram HMM estimated from the same training text, so the comparison is like for like.
    private val transition: Map<String, Map<String, Double>> by lazy {
        val counts = mutableMapOf<String, MutableMap<String, Int>>()
        for (sentence in train) {
            val tags = listOf("<s>") + sentence.map { it.second } + listOf("</s>")
            tags.zipWithNext().forEach { (a, b) ->
                counts.getOrPut(a) { mutableMapOf() }.merge(b, 1, Int::plus)
            }
        }
        counts.mapValues { (_, row) ->
            val total = row.values.sum().toDouble()
            row.mapValues { (it.value + 0.1) / (total + 0.1 * tagset.size) }
        }
    }

    private val emission: Map<String, Map<String, Double>> by lazy {
        val counts = train.flatten().groupBy({ it.second }, { it.first })
        counts.mapValues { (_, words) ->
            val total = words.size.toDouble()
            words.groupingBy { it }.eachCount().mapValues { it.value / total }
        }
    }

    fun a(from: String, to: String): Double = transition[from]?.get(to) ?: 1e-4
    fun b(tag: String, word: String): Double = emission[tag]?.get(word) ?: 1e-4

    /** Viterbi over the estimated HMM — the contextual tagger the baseline is compared against. */
    fun viterbiTags(sentence: List<String>): List<String> {
        var column = tagset.associateWith { a("<s>", it) * b(it, sentence[0]) }
        val back = mutableListOf<Map<String, String>>()
        for (i in 1 until sentence.size) {
            val pointers = mutableMapOf<String, String>()
            val next = tagset.associateWith { to ->
                val best = tagset.maxByOrNull { from -> column.getValue(from) * a(from, to) }!!
                pointers[to] = best
                column.getValue(best) * a(best, to) * b(to, sentence[i])
            }
            back += pointers
            column = next
        }
        val finals = column.mapValues { (tag, p) -> p * a(tag, "</s>") }
        var tag = finals.maxByOrNull { it.value }!!.key
        val path = mutableListOf(tag)
        for (i in back.indices.reversed()) {
            tag = back[i].getValue(tag)
            path.add(0, tag)
        }
        return path
    }

    class Score(val correct: Int, val total: Int) {
        val accuracy: Double get() = correct.toDouble() / total
    }

    fun score(tagger: (List<String>) -> List<String>): Score {
        var correct = 0
        var total = 0
        for (sentence in test) {
            val predicted = tagger(sentence.map { it.first })
            sentence.forEachIndexed { i, (_, gold) ->
                if (predicted[i] == gold) correct++
                total++
            }
        }
        return Score(correct, total)
    }

    val baselineScore: Score get() = score(::baselineTags)
    val viterbiScore: Score get() = score(::viterbiTags)

    /** Where the baseline goes wrong, listed rather than summarised. */
    fun baselineErrors(): List<Triple<String, String, String>> = test.flatMap { sentence ->
        val predicted = baselineTags(sentence.map { it.first })
        sentence.mapIndexedNotNull { i, (word, gold) ->
            if (predicted[i] != gold) Triple(word, gold, predicted[i]) else null
        }
    }
}

// ── Dependency parsing ───────────────────────────────────────────────────────

internal object DependencyLab {

    val sentence = listOf("the", "small", "dog", "chased", "a", "cat")

    /** Gold heads, 1-indexed with 0 = ROOT. */
    val goldHeads = listOf(3, 3, 4, 0, 6, 4)
    val goldLabels = listOf("det", "amod", "nsubj", "root", "det", "obj")

    enum class Move { SHIFT, LEFT_ARC, RIGHT_ARC }

    class Step(
        val move: Move,
        val label: String?,
        val stack: List<Int>,
        val buffer: List<Int>,
        val arcs: List<Triple<Int, Int, String>>,
    )

    /**
     * Arc-standard transition parsing, driven by an oracle that reads the gold tree. Each step is
     * recorded so the lab can replay the stack, the buffer and the arcs as they are built.
     */
    fun parse(): List<Step> {
        val stack = mutableListOf(0)
        val buffer = (1..sentence.size).toMutableList()
        val arcs = mutableListOf<Triple<Int, Int, String>>()
        val steps = mutableListOf<Step>()

        fun hasAllChildren(node: Int): Boolean =
            goldHeads.indices.none { goldHeads[it] == node && arcs.none { arc -> arc.second == it + 1 } }

        while (buffer.isNotEmpty() || stack.size > 1) {
            val top = stack.lastOrNull() ?: break
            val second = stack.getOrNull(stack.size - 2)
            val move: Move
            var label: String? = null

            if (second != null && second != 0 && goldHeads[second - 1] == top && hasAllChildren(second)) {
                move = Move.LEFT_ARC
                label = goldLabels[second - 1]
            } else if (second != null && top != 0 && goldHeads[top - 1] == second && hasAllChildren(top)) {
                move = Move.RIGHT_ARC
                label = goldLabels[top - 1]
            } else if (buffer.isNotEmpty()) {
                move = Move.SHIFT
            } else {
                break
            }

            when (move) {
                Move.SHIFT -> stack += buffer.removeAt(0)
                Move.LEFT_ARC -> {
                    arcs += Triple(top, second!!, label!!)
                    stack.removeAt(stack.size - 2)
                }
                Move.RIGHT_ARC -> {
                    arcs += Triple(second!!, top, label!!)
                    stack.removeAt(stack.size - 1)
                }
            }
            steps += Step(move, label, stack.toList(), buffer.toList(), arcs.toList())
        }
        return steps
    }

    fun finalArcs(): List<Triple<Int, Int, String>> = parse().last().arcs

    /** Every word gets exactly one head, so a complete parse is 2n transitions. */
    fun transitionCount(): Int = parse().size
    fun expectedTransitions(): Int = 2 * sentence.size

    fun wordAt(index: Int): String = if (index == 0) "ROOT" else sentence[index - 1]

    /**
     * A classic non-projective sentence: the arc from "hearing" to "on" crosses the arc from
     * "scheduled" to "today". Arc-standard cannot produce a crossing arc at all.
     */
    val nonProjective = listOf("a", "hearing", "is", "scheduled", "on", "the", "issue", "today")
    val nonProjectiveHeads = listOf(2, 4, 4, 0, 2, 7, 5, 4)

    fun crossingArcs(heads: List<Int> = nonProjectiveHeads): List<Pair<Int, Int>> {
        val arcs = heads.mapIndexed { i, h -> minOf(h, i + 1) to maxOf(h, i + 1) }.filter { it.first > 0 }
        val crossings = mutableListOf<Pair<Int, Int>>()
        for (i in arcs.indices) {
            for (j in i + 1 until arcs.size) {
                val (a1, b1) = arcs[i]
                val (a2, b2) = arcs[j]
                if (a1 < a2 && a2 < b1 && b1 < b2) crossings += arcs[i]
                if (a2 < a1 && a1 < b2 && b2 < b1) crossings += arcs[j]
            }
        }
        return crossings.distinct()
    }

    val isProjective: Boolean get() = crossingArcs(goldHeads).isEmpty()

    /** Unlabelled and labelled attachment score against a predicted parse. */
    fun uas(predictedHeads: List<Int>): Double =
        goldHeads.indices.count { predictedHeads[it] == goldHeads[it] }.toDouble() / goldHeads.size

    fun las(predictedHeads: List<Int>, predictedLabels: List<String>): Double =
        goldHeads.indices.count {
            predictedHeads[it] == goldHeads[it] && predictedLabels[it] == goldLabels[it]
        }.toDouble() / goldHeads.size

    /**
     * One plausible wrong parse: "small" attached to "chased" instead of "dog" (a head error), and
     * "cat" labelled nsubj instead of obj (a label error on a correct attachment). The two error
     * kinds are what separate UAS from LAS.
     */
    val predictedHeads = listOf(3, 4, 4, 0, 6, 4)
    val predictedLabels = listOf("det", "amod", "nsubj", "root", "det", "nsubj")
}

// ── Constituency parsing ─────────────────────────────────────────────────────

internal object ConstituencyLab {

    val sentence = DependencyLab.sentence

    class Node(val label: String, val children: List<Node> = emptyList(), val word: String? = null) {
        val isLeaf: Boolean get() = word != null
    }

    /** The gold phrase-structure tree for the same sentence the dependency lab parses. */
    val gold: Node = Node(
        "S",
        listOf(
            Node(
                "NP",
                listOf(
                    Node("DT", word = "the"),
                    Node("JJ", word = "small"),
                    Node("NN", word = "dog"),
                ),
            ),
            Node(
                "VP",
                listOf(
                    Node("VBD", word = "chased"),
                    Node("NP", listOf(Node("DT", word = "a"), Node("NN", word = "cat"))),
                ),
            ),
        ),
    )

    /** A wrong parse: the object NP swallowed into a flat VP, which costs two brackets. */
    val predicted: Node = Node(
        "S",
        listOf(
            Node(
                "NP",
                listOf(
                    Node("DT", word = "the"),
                    Node("JJ", word = "small"),
                    Node("NN", word = "dog"),
                ),
            ),
            Node(
                "VP",
                listOf(
                    Node("VBD", word = "chased"),
                    Node("DT", word = "a"),
                    Node("NN", word = "cat"),
                ),
            ),
        ),
    )

    fun bracket(node: Node): String =
        if (node.isLeaf) "(${node.label} ${node.word})"
        else "(${node.label} ${node.children.joinToString(" ") { bracket(it) }})"

    /** (label, start, end) for every non-terminal — the units evalb scores. */
    fun spans(node: Node, start: Int = 0): Pair<List<Triple<String, Int, Int>>, Int> {
        if (node.isLeaf) return emptyList<Triple<String, Int, Int>>() to start + 1
        var cursor = start
        val out = mutableListOf<Triple<String, Int, Int>>()
        for (child in node.children) {
            val (childSpans, next) = spans(child, cursor)
            out += childSpans
            cursor = next
        }
        return (out + Triple(node.label, start, cursor)) to cursor
    }

    fun spanList(node: Node): List<Triple<String, Int, Int>> = spans(node).first

    class Prf(val precision: Double, val recall: Double) {
        val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    }

    /** evalb: labelled bracket precision, recall and F1. */
    fun evalb(predicted: Node = ConstituencyLab.predicted, gold: Node = ConstituencyLab.gold): Prf {
        val p = spanList(predicted).toSet()
        val g = spanList(gold).toSet()
        val hits = p.count { it in g }
        return Prf(hits.toDouble() / p.size, hits.toDouble() / g.size)
    }

    /**
     * Head percolation: the rule table that turns a constituency tree into a dependency tree —
     * how the two treebank formats are actually converted into each other.
     */
    private val headRules = mapOf(
        "S" to listOf("VP", "NP"),
        "VP" to listOf("VBD", "VBZ", "VBP", "VB", "NP"),
        "NP" to listOf("NN", "NNS", "NP"),
    )

    /** Index of the head *word* of this subtree, 1-based over the sentence. */
    fun headOf(node: Node, offset: Int = 0): Int {
        if (node.isLeaf) return offset + 1
        val positions = mutableListOf<Pair<Node, Int>>()
        var cursor = offset
        for (child in node.children) {
            positions += child to cursor
            cursor += leafCount(child)
        }
        val priority = headRules[node.label].orEmpty()
        val chosen = priority.firstNotNullOfOrNull { label -> positions.lastOrNull { it.first.label == label } }
            ?: positions.last()
        return headOf(chosen.first, chosen.second)
    }

    fun leafCount(node: Node): Int = if (node.isLeaf) 1 else node.children.sumOf { leafCount(it) }

    /** Convert the whole tree to head indices, which should reproduce the dependency gold. */
    fun toDependencies(node: Node = gold): List<Int> {
        val heads = IntArray(leafCount(node))
        fun walk(current: Node, offset: Int, parentHead: Int) {
            if (current.isLeaf) {
                heads[offset] = parentHead
                return
            }
            val head = headOf(current, offset)
            var cursor = offset
            for (child in current.children) {
                val childHead = headOf(child, cursor)
                if (childHead == head) walk(child, cursor, parentHead) else walk(child, cursor, head)
                cursor += leafCount(child)
            }
        }
        walk(node, 0, 0)
        return heads.toList()
    }

    val conversionMatchesDependencyGold: Boolean
        get() = toDependencies() == DependencyLab.goldHeads
}

// ── Chunking (shallow parsing) ───────────────────────────────────────────────

internal object ChunkLab {

    val sentence = listOf("the", "small", "dog", "chased", "a", "cat", "in", "the", "garden")
    val tags = listOf("DT", "JJ", "NN", "VBD", "DT", "NN", "IN", "DT", "NN")

    /** Gold chunks as (type, start, endExclusive). */
    val gold = listOf(
        Triple("NP", 0, 3),
        Triple("VP", 3, 4),
        Triple("NP", 4, 6),
        Triple("PP", 6, 7),
        Triple("NP", 7, 9),
    )

    /**
     * A regex-over-tags chunker, which is what NLTK's RegexpParser does and what most production
     * shallow parsers still are: NP = DT? JJ* NN+, VP = a verb, PP = a preposition.
     */
    fun chunk(): List<Triple<String, Int, Int>> {
        val out = mutableListOf<Triple<String, Int, Int>>()
        var i = 0
        while (i < tags.size) {
            when {
                tags[i] == "DT" || tags[i] == "JJ" || tags[i].startsWith("NN") -> {
                    var j = i
                    if (tags[j] == "DT") j++
                    while (j < tags.size && tags[j] == "JJ") j++
                    val nounStart = j
                    while (j < tags.size && tags[j].startsWith("NN")) j++
                    if (j > nounStart) out += Triple("NP", i, j) else j = i + 1
                    i = j
                }
                tags[i].startsWith("VB") -> { out += Triple("VP", i, i + 1); i++ }
                tags[i] == "IN" -> { out += Triple("PP", i, i + 1); i++ }
                else -> i++
            }
        }
        return out
    }

    /** BIO tags for the predicted chunks — the format sequence labellers are trained on. */
    fun bio(chunks: List<Triple<String, Int, Int>> = chunk()): List<String> {
        val out = MutableList(sentence.size) { "O" }
        for ((type, start, end) in chunks) {
            out[start] = "B-$type"
            for (k in start + 1 until end) out[k] = "I-$type"
        }
        return out
    }

    fun evaluate(predicted: List<Triple<String, Int, Int>> = chunk()): ConstituencyLab.Prf {
        val hits = predicted.count { it in gold }
        return ConstituencyLab.Prf(hits.toDouble() / predicted.size, hits.toDouble() / gold.size)
    }

    /**
     * The evaluation trap: a span that overlaps the gold one but does not match its boundaries
     * exactly scores zero, not partial credit. This variant gets "small dog" instead of
     * "the small dog".
     */
    val boundaryError = listOf(
        Triple("NP", 1, 3),
        Triple("VP", 3, 4),
        Triple("NP", 4, 6),
        Triple("PP", 6, 7),
        Triple("NP", 7, 9),
    )

    /** Token accuracy over the same prediction — the number that looks much kinder. */
    fun tokenAccuracy(predicted: List<Triple<String, Int, Int>>): Double {
        val goldBio = bio(gold)
        val predictedBio = bio(predicted)
        return goldBio.indices.count { goldBio[it] == predictedBio[it] }.toDouble() / goldBio.size
    }

    /** Cost: chunking is one linear pass; full constituency parsing is cubic. */
    fun chunkOperations(n: Int = sentence.size): Int = n
    fun parseOperations(n: Int = sentence.size): Int = n * n * n
}

// ── Coreference resolution ───────────────────────────────────────────────────

internal object CorefLab {

    class Mention(val text: String, val index: Int, val number: String, val gender: String, val animate: Boolean)

    /**
     * The Winograd schema pair. One word changes between them and the correct antecedent flips,
     * which is why the pair is a benchmark: no surface cue distinguishes the two sentences.
     */
    val winogradA = "the city council refused the demonstrators a permit because they feared violence"
    val winogradB = "the city council refused the demonstrators a permit because they advocated violence"

    val mentionsA = listOf(
        Mention("the city council", 0, "sg", "neuter", animate = false),
        Mention("the demonstrators", 1, "pl", "neuter", animate = true),
        Mention("they", 2, "pl", "neuter", animate = true),
    )

    val goldA = "the city council"
    val goldB = "the demonstrators"

    /** Most-recent-compatible antecedent: the classic baseline, and it cannot tell these apart. */
    fun recencyBaseline(candidates: List<Mention> = mentionsA.dropLast(1)): String =
        candidates.last().text

    fun agreementFilter(pronoun: Mention, candidates: List<Mention>): List<Mention> =
        candidates.filter { it.number == pronoun.number }

    /** Both sentences get the same answer from the baseline, so it is right exactly half the time. */
    fun baselineAccuracyOnPair(): Double {
        val answer = recencyBaseline()
        return listOf(goldA, goldB).count { it == answer }.toDouble() / 2
    }

    /** A chain the surface features *can* resolve: number and gender rule out the alternatives. */
    val easyDocument = "Ada Lovelace met Charles Babbage in London. She read his notes and wrote her own program."

    val easyMentions = listOf(
        Mention("Ada Lovelace", 0, "sg", "fem", animate = true),
        Mention("Charles Babbage", 1, "sg", "masc", animate = true),
        Mention("London", 2, "sg", "neuter", animate = false),
        Mention("She", 3, "sg", "fem", animate = true),
        Mention("his", 4, "sg", "masc", animate = true),
        Mention("her", 5, "sg", "fem", animate = true),
    )

    /**
     * Mention-pair systems link a mention to its nearest compatible antecedent, which is often
     * another pronoun — resolving "her" gives "She", not "Ada Lovelace". Chains are the transitive
     * closure of those links, so following them back to the first non-pronoun is what produces an
     * actual entity. Reporting the immediate link as the answer is a real evaluation mistake.
     */
    private val pronouns = setOf("she", "he", "her", "his", "him", "they", "them", "it")

    fun resolveChain(pronounIndex: Int): String? {
        var current = resolveEasy(pronounIndex) ?: return null
        var guard = 0
        while (current.lowercase() in pronouns && guard++ < easyMentions.size) {
            val index = easyMentions.indexOfFirst { it.text == current }
            current = resolveEasy(index) ?: return current
        }
        return current
    }

    fun resolveEasy(pronounIndex: Int): String? {
        val pronoun = easyMentions[pronounIndex]
        val candidates = easyMentions.take(pronounIndex).filter {
            it.number == pronoun.number && it.gender == pronoun.gender && it.animate == pronoun.animate
        }
        return candidates.lastOrNull()?.text
    }

    /** Chains after transitive closure: entity → every mention that refers to it. */
    fun chains(): Map<String, List<String>> {
        val out = linkedMapOf<String, MutableList<String>>()
        for (i in easyMentions.indices) {
            val mention = easyMentions[i]
            if (mention.text.lowercase() !in pronouns) continue
            val entity = resolveChain(i) ?: continue
            out.getOrPut(entity) { mutableListOf(entity) } += mention.text
        }
        return out
    }

    private val goldLinks = mapOf(3 to "Ada Lovelace", 4 to "Charles Babbage", 5 to "Ada Lovelace")

    /** Scored on the immediate antecedent — "her" → "She" counts as wrong. */
    fun pairAccuracy(): Double =
        goldLinks.count { (i, answer) -> resolveEasy(i) == answer }.toDouble() / goldLinks.size

    /** Scored on the entity the chain resolves to, which is what the task actually asks for. */
    fun easyAccuracy(): Double =
        goldLinks.count { (i, answer) -> resolveChain(i) == answer }.toDouble() / goldLinks.size

    /** Candidate pairs a mention-pair model scores: O(m²) in the number of mentions. */
    fun candidatePairs(mentionCount: Int = easyMentions.size): Int = mentionCount * (mentionCount - 1) / 2
}

// ── Lexicon-based sentiment ──────────────────────────────────────────────────

internal object SentimentLexiconLab {

    /** An AFINN-style lexicon: word → valence in [−3, +3]. */
    val lexicon = mapOf(
        "good" to 2, "great" to 3, "excellent" to 3, "love" to 3, "like" to 2, "happy" to 3,
        "fine" to 1, "works" to 1, "fast" to 1, "recommend" to 2,
        "bad" to -2, "terrible" to -3, "awful" to -3, "hate" to -3, "slow" to -1,
        "broken" to -3, "disappointing" to -2, "poor" to -2, "worst" to -3, "boring" to -2,
    )

    val negators = setOf("not", "never", "no", "cannot", "nor", "isn't", "wasn't", "don't")
    val intensifiers = mapOf("very" to 1.5, "extremely" to 2.0, "really" to 1.5)
    val diminishers = mapOf("slightly" to 0.5, "somewhat" to 0.5, "barely" to 0.3)
    val negationWindow = 3

    /** Labelled reviews: +1 positive, −1 negative. */
    val testSet: List<Pair<String, Int>> = listOf(
        "the movie was good" to 1,
        "the movie was not good" to -1,
        "i love this product" to 1,
        "i do not like this product" to -1,
        "the battery is terrible" to -1,
        "the screen is very good" to 1,
        "it is not bad at all" to 1,
        "the app is slightly slow but the design is excellent" to 1,
        "never buy this awful thing" to -1,
        "the plot was boring and the acting was poor" to -1,
    )

    /** Sum of lexicon values, no rules at all. */
    fun plainScore(text: String): Double =
        text.split(" ").sumOf { (lexicon[it] ?: 0).toDouble() }

    /**
     * The same sum with the three rules every lexicon system grows: negation flips the next few
     * words, intensifiers scale, and a clause after "but" outweighs what came before it.
     */
    fun ruleScore(text: String): Double {
        val words = text.split(" ")
        val butIndex = words.indexOf("but")
        var total = 0.0
        for ((i, word) in words.withIndex()) {
            var value = (lexicon[word] ?: 0).toDouble()
            if (value == 0.0) continue
            val window = words.subList(maxOf(0, i - negationWindow), i)
            if (window.any { it in negators }) value = -value * 0.75
            window.lastOrNull()?.let { previous ->
                intensifiers[previous]?.let { value *= it }
                diminishers[previous]?.let { value *= it }
            }
            if (butIndex >= 0) value *= if (i < butIndex) 0.5 else 1.5
            total += value
        }
        return total
    }

    fun classify(score: Double): Int = if (score >= 0) 1 else -1

    fun accuracy(scorer: (String) -> Double): Double =
        testSet.count { (text, label) -> classify(scorer(text)) == label }.toDouble() / testSet.size

    val plainAccuracy: Double get() = accuracy(::plainScore)
    val ruleAccuracy: Double get() = accuracy(::ruleScore)

    fun errors(scorer: (String) -> Double): List<Pair<String, Double>> =
        testSet.filter { (text, label) -> classify(scorer(text)) != label }.map { it.first to scorer(it.first) }

    /** Coverage: how much of the text the lexicon has any opinion about at all. */
    fun coverage(): Double {
        val tokens = testSet.flatMap { it.first.split(" ") }
        return tokens.count { it in lexicon }.toDouble() / tokens.size
    }

    val lexiconSize: Int get() = lexicon.size
}
