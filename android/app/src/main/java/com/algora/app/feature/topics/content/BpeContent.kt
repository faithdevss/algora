package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bpeContent = TopicContent(
    topicId = "bpe",
    figure = Figure(
        caption = "The page's lab: four BPE merges learned from a tiny corpus — \"low\", \"lower\", " +
            "\"newest\" and \"widest\", weighted by how often each appears — and then replayed to " +
            "encode \"lowest\", a word the corpus never contained. Each round merges the most " +
            "frequent adjacent pair and adds exactly one symbol to the vocabulary: e + s (9 times), " +
            "es + t (9), est + </w> (9), l + o (7), taking the vocabulary from 11 symbols to 15. " +
            "Encoding does not search for a best split; it applies the merges in the order they " +
            "were learned, and \"lowest\" shrinks from 7 symbols to 6, 5, 4 and finally 3 tokens, " +
            "none unknown, by reusing pieces learned from other words.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("e + s → es", "9", "6"),
                listOf("es + t → est", "9", "5"),
                listOf("est + </w>", "9", "4"),
                listOf("l + o → lo", "7", "3"),
            ),
            rowHeaders = listOf("merge 1", "merge 2", "merge 3", "merge 4"),
            colHeaders = listOf("pair", "count", "\"lowest\" tokens"),
            marks = listOf(
                FigureCell(3, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Byte-pair encoding builds a subword vocabulary by repeatedly merging the most frequent adjacent pair of symbols. It starts from single characters — so any text can be written and nothing is ever unknown — and each merge adds exactly one new symbol to the vocabulary. The number of merges is the only knob: more merges mean longer pieces, shorter sequences and a larger vocabulary.",
        "The lab learns four merges from a tiny corpus of \"low\", \"lower\", \"newest\" and \"widest\" with their counts, starting from 11 symbols. Every adjacent pair is counted, weighted by how often its word appears: \"e + s\" occurs 9 times and becomes \"es\"; then \"es + t\" makes \"est\" (9 times); then \"est + </w>\" makes the word-final \"est</w>\"; then \"l + o\" (7 times) makes \"lo\". The vocabulary is now 15.",
        "Encoding a word does not search for the best split; it replays the merges in the order they were learned. \"lowest\" never appeared in the corpus, but it starts as 7 symbols and each merge applies in turn — es, est, est</w>, lo — leaving 3 tokens, none unknown, because it reuses pieces learned from other words. That is the property GPT-2's 50,257-token vocabulary relies on: frequent words collapse to one token, rare ones split into a few, and sequence length — which drives attention cost — stays manageable.",
    ),
    steps = listOf(
        StepCard(1, "Split Into Characters", "Every word becomes a character sequence with an end-of-word marker.", 0xFF14B8A6),
        StepCard(2, "Count Adjacent Pairs", "Tally every neighbouring symbol pair across the corpus, weighted by word frequency.", 0xFF3B82F6),
        StepCard(3, "Merge the Most Frequent", "Replace every occurrence of the winning pair with a single new symbol.", 0xFFF59E0B),
        StepCard(4, "Repeat to Target Size", "Each merge adds one vocabulary entry; stop at 30k–100k for a typical model.", 0xFF10B981),
        StepCard(5, "Apply Merges in Order", "Encoding new text replays the learned merge list — same order, deterministic result.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Merge rule", "argmax_(a,b) count(a·b)", "The most frequent adjacent pair wins each round."),
        FormulaEntry("Vocabulary size", "|V| = |base alphabet| + merges", "One new token per merge — the count is a hyperparameter."),
        FormulaEntry("Sequence length", "shorter as |V| grows", "The direct tradeoff: bigger vocabulary, fewer tokens per sentence."),
        FormulaEntry("Coverage", "100% with byte-level base", "GPT-style byte BPE can never produce an unknown token."),
    ),
    notationKey = listOf(
        NotationEntry("</w>", "end-of-word marker separating word-final from word-internal pieces"),
        NotationEntry("merge list", "the ordered pairs learned during training"),
        NotationEntry("subword", "a token between a character and a whole word"),
        NotationEntry("WordPiece", "BERT's variant, merging by likelihood gain rather than raw count"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Learning BPE merges",
            accentColor = 0xFF6366F1,
            code = """
                // corpus: word -> frequency. Words start out as space-separated characters.
                fun learnBpe(corpus: Map<String, Int>, merges: Int): List<Pair<String, String>> {
                    var vocab = corpus.mapKeys { (word, _) -> word.toList().joinToString(" ") + " </w>" }
                    val learned = mutableListOf<Pair<String, String>>()

                    repeat(merges) {
                        // Count every adjacent symbol pair, weighted by how often the word occurs.
                        val pairs = mutableMapOf<Pair<String, String>, Int>()
                        for ((word, freq) in vocab) {
                            val symbols = word.split(" ")
                            for (i in 0 until symbols.size - 1) {
                                val pair = symbols[i] to symbols[i + 1]
                                pairs[pair] = (pairs[pair] ?: 0) + freq
                            }
                        }
                        val best = pairs.maxByOrNull { it.value }?.key ?: return learned
                        learned += best

                        // Apply the merge everywhere it appears, as whole symbols only. Plain
                        // substring replace would also match "a b" inside "ca bd" -> "cabd".
                        val target = Regex("(?<!\\S)" + Regex.escape(best.first) + " " +
                            Regex.escape(best.second) + "(?!\\S)")
                        val replacement = Regex.escapeReplacement(best.first + best.second)
                        vocab = vocab.mapKeys { (word, _) -> target.replace(word, replacement) }
                    }
                    return learned
                }

                // Encoding replays the merges in the order they were learned.
                fun encode(word: String, merges: List<Pair<String, String>>): List<String> {
                    var symbols = word.toList().map { it.toString() } + "</w>"
                    for ((a, b) in merges) {
                        val out = mutableListOf<String>()
                        var i = 0
                        while (i < symbols.size) {
                            if (i < symbols.size - 1 && symbols[i] == a && symbols[i + 1] == b) {
                                out += a + b
                                i += 2
                            } else {
                                out += symbols[i]
                                i++
                            }
                        }
                        symbols = out
                    }
                    return symbols
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF14B8A6, "LLM Tokenizers", "GPT, Llama and most modern models tokenize with byte-level BPE."),
        ApplicationCard("globe", 0xFF3B82F6, "Multilingual Models", "One shared subword vocabulary covers scripts and morphologies that no word list could."),
        ApplicationCard("chip", 0xFF10B981, "Code Models", "Identifiers like `getUserById` split into reusable pieces instead of unique unknown tokens."),
    ),
    takeaways = listOf(
        "BPE is greedy frequency-driven compression applied to text, learned once and replayed at encode time.",
        "Common words become single tokens; rare words decompose — so nothing is out-of-vocabulary.",
        "Vocabulary size trades against sequence length, and sequence length drives attention cost.",
        "Token boundaries are why models miscount characters: 'strawberry' is a few tokens, not ten letters.",
        "In the lab four merges (es, est, est</w>, lo) turn the unseen word \"lowest\" from 7 symbols into 3 tokens, none unknown.",
    ),
    crossLinks = listOf(
        CrossLink("tokenization", "Tokenization"),
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("huffman_coding", "Huffman Coding"),
    ),
)
