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

internal val pcfgContent = TopicContent(
    topicId = "pcfg",
    figure = Figure(
        caption = "The page's lab sentence, \"she saw the man with the telescope\", has two legal " +
            "parses, and a PCFG ranks them by multiplying the probabilities of the rules each one " +
            "uses. Under VP attachment the prepositional phrase modifies the seeing — she used the " +
            "telescope — and the tree scores 1.0 × 0.40 × 0.30 × 0.14 × 0.20 = 0.0034. Under NP " +
            "attachment it modifies the man, who has the telescope: 1.0 × 0.40 × 0.70 × 0.20 × " +
            "0.20 × 0.20 = 0.0022. Same words, same grammar, a 1.5× preference for VP attachment, " +
            "which is what probabilistic CYK reports as the best parse. The weakness is visible in " +
            "the arithmetic: the rules are scored without the words, so \"saw the man with the " +
            "hat\" would get exactly the same preference — which is why lexicalised and neural " +
            "parsers replaced plain PCFGs.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("she used the telescope", "0.0034"),
                listOf("the man has it", "0.0022"),
            ),
            rowHeaders = listOf("VP attach", "NP attach"),
            colHeaders = listOf("reading", "P(tree)"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A context-free grammar says which sentences a language allows and what structures they can have. Its problem in practice is not that it rejects too much — it is that it accepts too much: for an ordinary sentence a treebank grammar returns dozens to thousands of legal parse trees and has no way to say which one a person meant. A probabilistic CFG attaches a probability to every rule, with the rules sharing a left-hand side summing to 1, so a tree's score is the product of the rules used to build it and the parses become ranked instead of merely listed.",
        "The lab's sentence is the standard case: \"she saw the man with the telescope\". Under VP attachment the prepositional phrase modifies the seeing — she used the telescope — and scores 0.0034. Under NP attachment it modifies the man, who was holding it, and scores 0.0022. Same words, same grammar, two grammatical trees, and the grammar prefers the first by 1.5×. Nothing else in the sentence decides it; the preference comes entirely from VP → VP PP at 0.30 being a likelier rule than NP → NP PP at 0.20.",
        "The parser is probabilistic CYK: a bottom-up chart over spans, with the grammar in Chomsky Normal Form so every binary rule combines exactly two adjacent spans. Cell [i, j] keeps, for each non-terminal, the best parse of that span and a backpointer — max in place of the sum, exactly as Viterbi is to the forward algorithm, and the same table shape as matrix-chain multiplication in the DSA taxonomy. Cost is O(n³·|G|): 15 filled cells over 56 split points for this seven-word sentence. The known weakness is in the name — context-freeness means the rule probabilities cannot depend on the words, so this grammar prefers VP attachment for *every* sentence of this shape and has no idea a telescope is an instrument of seeing. Lexicalised PCFGs (Collins, Charniak) condition each rule on its head word to fix exactly that, at the cost of a far sparser table; neural parsers replaced the whole approach by scoring spans with a learned encoder.",
    ),
    steps = listOf(
        StepCard(1, "Binarise the Grammar", "Chomsky Normal Form: A → B C or A → word, so every span splits in exactly one place.", 0xFF8B5CF6),
        StepCard(2, "Estimate Rule Probabilities", "P(A → β) = count(A → β) / count(A), read straight off a treebank.", 0xFF6366F1),
        StepCard(3, "Fill the Diagonal", "Lexical rules for each word — the width-1 spans of the chart.", 0xFF3B82F6),
        StepCard(4, "Combine Spans", "For every span and split point, apply every binary rule and score = P(rule) × left × right.", 0xFF06B6D4),
        StepCard(5, "Keep the Max", "One best parse per label per cell, plus a backpointer. Losing parses cost no storage.", 0xFF14B8A6),
        StepCard(6, "Read the Root", "The S entry of the full-span cell is the Viterbi parse; backtrace gives the tree.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Tree probability", "P(T) = Π P(rule) over every rule in T", "The independence assumption: rules are chosen independently of context."),
        FormulaEntry("Normalisation", "Σ_β P(A → β) = 1 for each A", "VP → V NP at 0.70 and VP → VP PP at 0.30 form one distribution."),
        FormulaEntry("VP attachment", "1.0 × 0.40 × (0.30 × 0.14 × 0.20) = 0.0034", "She used the telescope to look."),
        FormulaEntry("NP attachment", "1.0 × 0.40 × (0.70 × (0.20 × 0.20 × 0.20)) = 0.0022", "The man had the telescope."),
        FormulaEntry("CYK recursion", "π(i,j,A) = max_{k,B,C} P(A→BC)·π(i,k,B)·π(k,j,C)", "Max where the inside algorithm sums — Viterbi's relationship to forward, again."),
        FormulaEntry("Cost", "O(n³ · |G|)", "15 filled cells over 28 spans and 56 split points for these 7 words."),
    ),
    notationKey = listOf(
        NotationEntry("CNF", "Chomsky Normal Form — every rule is A → B C or A → word"),
        NotationEntry("span [i, j)", "the substring from word i up to word j; the chart is indexed by spans"),
        NotationEntry("attachment ambiguity", "which constituent a modifier belongs to — the most common structural ambiguity in English"),
        NotationEntry("inside probability", "the summed probability of all parses of a span, the PCFG analogue of the forward algorithm"),
        NotationEntry("Viterbi parse", "the single most probable tree, what CYK's max recursion returns"),
        NotationEntry("lexicalisation", "conditioning rules on head words so \"saw … with telescope\" can beat \"man … with telescope\""),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Probabilistic CYK in twenty lines",
            accentColor = 0xFF8B5CF6,
            code = """
                binary = [("S", ("NP", "VP"), 1.00), ("VP", ("V", "NP"), 0.70),
                          ("VP", ("VP", "PP"), 0.30), ("NP", ("Det", "N"), 0.40),
                          ("NP", ("NP", "PP"), 0.20), ("PP", ("P", "NP"), 1.00)]
                lexical = {"she": [("NP", .40)], "saw": [("V", 1.)], "the": [("Det", 1.)],
                           "man": [("N", .5)], "with": [("P", 1.)], "telescope": [("N", .5)]}

                def cyk(words):
                    n = len(words)
                    chart = [[dict() for _ in range(n + 1)] for _ in range(n)]
                    for i, w in enumerate(words):
                        for label, p in lexical[w]:
                            chart[i][i + 1][label] = (p, None)
                    for span in range(2, n + 1):
                        for i in range(n - span + 1):
                            j = i + span
                            for k in range(i + 1, j):
                                for label, (b, c), p in binary:
                                    if b in chart[i][k] and c in chart[k][j]:
                                        score = p * chart[i][k][b][0] * chart[k][j][c][0]
                                        if score > chart[i][j].get(label, (0,))[0]:
                                            chart[i][j][label] = (score, (k, b, c))
                    return chart

                s = "she saw the man with the telescope".split()
                print(cyk(s)[0][len(s)]["S"][0])      # 0.00336  <- VP attachment wins
                # The NP-attachment tree scores 0.00224. Same grammar, 1.5x less probable.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the plain PCFG gets the next sentence wrong",
            accentColor = 0xFFEC4899,
            code = """
                # "she ate the pasta with a fork"    -> VP attachment is right (instrument)
                # "she ate the pasta with a sauce"   -> NP attachment is right (the pasta had sauce)
                #
                # A plain PCFG scores these identically: the rule probabilities cannot see the words
                # "fork" and "sauce", because context-freeness is exactly the assumption that they
                # cannot. It will answer VP for both, and be right half the time by construction.

                # Collins-style lexicalisation carries the head word into the non-terminal:
                #   VP(ate) -> VP(ate) PP(with-fork)     is common in a treebank
                #   NP(pasta) -> NP(pasta) PP(with-sauce) is too
                # so the decision becomes a lexical statistic rather than a structural one -- at the
                # cost of estimating probabilities for rules that now mention specific words, which
                # is a severe sparsity problem and needs heavy smoothing.

                import benepar, spacy                    # the modern answer: a neural span scorer
                nlp = spacy.load("en_core_web_md")
                nlp.add_pipe("benepar", config={"model": "benepar_en3"})
                print(list(nlp("she saw the man with the telescope").sents)[0]._.parse_string)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("browser", 0xFF8B5CF6, "Syntactic Parsing", "Treebank grammars over the Penn Treebank — the task that defined the field for a decade."),
        ApplicationCard("search", 0xFF6366F1, "Grammar Checking", "A tree that only parses with a low-probability rule is a signal something is wrong."),
        ApplicationCard("music", 0xFF3B82F6, "Speech & Music", "Language models over structure, and probabilistic grammars for harmonic analysis."),
        ApplicationCard("flask", 0xFF14B8A6, "RNA Structure", "Stochastic CFGs model base pairing, where nesting is exactly what a regex cannot do."),
    ),
    takeaways = listOf(
        "A PCFG ranks the trees a CFG merely allows: the tree's score is the product of its rules.",
        "\"she saw the man with the telescope\" parses two ways — 0.0034 for VP attachment against 0.0022 for NP, a 1.5× preference.",
        "CYK is a bottom-up chart over spans in CNF: max and a backpointer per cell, O(n³·|G|), 15 cells filled here.",
        "It is Viterbi's relationship to forward, one dimension up — and the same table shape as matrix-chain DP.",
        "Context-freeness is the flaw: rule probabilities cannot see the words, so lexicalised or neural parsers replaced it.",
    ),
    crossLinks = listOf(
        CrossLink("hmm", "Hidden Markov Models"),
        CrossLink("matrix_chain_multiplication", "Matrix Chain Multiplication"),
        CrossLink("tree", "Trees"),
        CrossLink("attention", "Attention"),
    ),
)
