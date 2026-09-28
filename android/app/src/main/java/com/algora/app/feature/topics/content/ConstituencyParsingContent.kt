package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val constituencyParsingContent = TopicContent(
    topicId = "constituency_parsing",
    whatIsIt = listOf(
        "A constituency (phrase-structure) parse groups words into nested phrases: a sentence is a noun phrase plus a verb phrase, the noun phrase is a determiner, an adjective and a noun, and so on down to the words at the leaves. It answers a different question from a dependency parse — not \"what relates to what\" but \"which spans of this sentence behave as units\". Those units are what substitution, movement and coordination tests identify, and they are what a grammar-checking or phrase-extraction task actually wants.",
        "Parsers are scored on labelled brackets, and the evaluation has a shape worth knowing. evalb compares the set of (label, start, end) spans, excluding part-of-speech nodes so that a tagger's accuracy cannot inflate the parser's. The lab's flawed parse — the object noun phrase flattened into the verb phrase — gets every bracket it predicts right, so precision is 1.00, but recovers only 3 of the gold tree's 4 spans, so recall is 0.75 and F1 is 0.86. That asymmetry is why all three numbers are reported: a parser that emits fewer, safer brackets can hold precision at 1.00 forever.",
        "The two formalisms are convertible, and the conversion is a table of head rules — the head of a VP is its verb, the head of an NP is its rightmost noun, the head of an S is its VP's head. Percolating those upward turns the lab's phrase tree into heads that match the dependency lab's gold parse for the same sentence exactly, which is computed rather than claimed. This is how the Penn Treebank became a dependency treebank, and it means any argument that one formalism carries more information than the other has to be about the annotation, not the notation. Algorithmically, exact parsing is chart-based and cubic — CYK over a PCFG — while modern neural parsers either score spans independently and decode with a chart, or generate the bracketed string with a sequence model.",
    ),
    steps = listOf(
        StepCard(1, "Tag the Words", "Pre-terminals are the POS layer; evalb excludes them from scoring.", 0xFFF59E0B),
        StepCard(2, "Build Constituents", "Combine adjacent spans into phrases wherever the grammar allows.", 0xFFEAB308),
        StepCard(3, "Resolve Ambiguity", "Rule probabilities (PCFG) or a learned span scorer choose among legal trees.", 0xFF06B6D4),
        StepCard(4, "Read the Root", "The S entry over the whole sentence, with backpointers, is the parse.", 0xFF3B82F6),
        StepCard(5, "Score Brackets", "Labelled precision, recall and F1 over (label, start, end) triples.", 0xFF6366F1),
        StepCard(6, "Convert if Needed", "Head rules turn the tree into dependencies — the treebanks are the same annotation.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Scored units", "(label, start, end), POS nodes excluded", "4 spans in the lab's gold tree: NP, NP, VP, S."),
        FormulaEntry("evalb", "P = 1.00, R = 0.75, F1 = 0.86", "The flattened-VP parse: every bracket right, one bracket missing."),
        FormulaEntry("Why all three", "fewer brackets → higher precision, lower recall", "A cautious parser can hold P at 1.00 indefinitely."),
        FormulaEntry("Exact parsing", "O(n³·|G|) chart (CYK)", "The same cubic table the PCFG topic fills."),
        FormulaEntry("Head percolation", "S → VP, VP → verb, NP → rightmost noun", "Converts the lab's tree to heads [3, 3, 4, 0, 6, 4] — the dependency gold."),
        FormulaEntry("Human ceiling", "~95% F1 on WSJ; annotator agreement is close behind", "Which is why parser progress on that benchmark stopped being interesting."),
    ),
    notationKey = listOf(
        NotationEntry("constituent", "a span that behaves as a unit under substitution, movement and coordination"),
        NotationEntry("pre-terminal", "the POS node directly above a word; excluded from evalb"),
        NotationEntry("bracketed form", "(S (NP (DT the) (NN dog)) (VP (VBD ran))) — the treebank's text format"),
        NotationEntry("evalb", "the standard labelled-bracket scorer used on the Penn Treebank"),
        NotationEntry("head rules", "the table that picks each phrase's head child — Magerman's and Collins's are the classics"),
        NotationEntry("lexicalisation", "carrying the head word into the non-terminal, e.g. VP(chased)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Spans, and what evalb actually compares",
            accentColor = 0xFFF59E0B,
            code = """
                from nltk import Tree

                gold = Tree.fromstring("(S (NP (DT the)(JJ small)(NN dog)) "
                                       "   (VP (VBD chased) (NP (DT a)(NN cat))))")
                pred = Tree.fromstring("(S (NP (DT the)(JJ small)(NN dog)) "
                                       "   (VP (VBD chased) (DT a)(NN cat)))")

                def spans(tree, start=0, out=None):
                    out = [] if out is None else out
                    i = start
                    for child in tree:
                        if isinstance(child, Tree):
                            i = spans(child, i, out)
                        else:
                            i += 1
                    if tree.height() > 2:            # skip pre-terminals
                        out.append((tree.label(), start, i))
                    return i if out is None else (out, i)[0] and i

                g = {("NP",0,3), ("NP",4,6), ("VP",3,6), ("S",0,6)}
                p = {("NP",0,3), ("VP",3,6), ("S",0,6)}
                hits = len(g & p)
                print(hits/len(p), hits/len(g))      # precision 1.00, recall 0.75 -> F1 0.86
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Head rules, and the conversion they perform",
            accentColor = 0xFFEC4899,
            code = """
                HEAD_RULES = {                 # priority order, left to right
                    "S":  ["VP", "NP"],
                    "VP": ["VBD", "VBZ", "VBP", "VB", "NP"],
                    "NP": ["NN", "NNS", "NP"],
                }

                # Percolating heads upward and attaching every non-head child to its parent's head
                # turns the tree above into:  heads = [3, 3, 4, 0, 6, 4]
                # ...which is exactly the dependency gold for the same sentence.
                #
                # This is not a curiosity: the Penn Treebank was annotated as phrase structure, and
                # every English dependency treebank before Universal Dependencies was produced by
                # running a head-rule table over it. The rules encode linguistic decisions -- whether
                # the auxiliary or the main verb heads a clause, whether the preposition or its
                # object heads a PP -- and different tables give measurably different dependencies.

                import benepar, spacy                # modern constituency parsing
                nlp = spacy.load("en_core_web_md")
                nlp.add_pipe("benepar", config={"model": "benepar_en3"})
                print(list(nlp("the small dog chased a cat").sents)[0]._.parse_string)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("browser", 0xFFF59E0B, "Phrase Extraction", "Noun-phrase and clause extraction for indexing, summarisation and terminology mining."),
        ApplicationCard("check", 0xFF3B82F6, "Grammar Checking", "A sentence that only parses via a low-probability rule is a signal something is wrong."),
        ApplicationCard("music", 0xFF8B5CF6, "Speech Synthesis", "Prosody and phrase breaks follow constituent boundaries, not word boundaries."),
        ApplicationCard("flask", 0xFF14B8A6, "Treebank Conversion", "Head rules turn phrase-structure corpora into dependency corpora — how most of them were made."),
    ),
    takeaways = listOf(
        "Constituency parses answer \"which spans are units\", where dependency parses answer \"what relates to what\".",
        "evalb scores labelled brackets excluding POS nodes: the lab's flat-VP parse gets P 1.00, R 0.75, F1 0.86.",
        "Precision and recall must both be quoted — a parser emitting fewer brackets keeps precision at 1.00 for free.",
        "Head rules convert the tree into dependencies; on the lab's sentence the conversion reproduces the dependency gold exactly.",
        "Exact parsing is the cubic CYK chart; the formalism choice is about the task, not about information content.",
    ),
    crossLinks = listOf(
        CrossLink("pcfg", "Probabilistic CFGs"),
        CrossLink("dependency_parsing", "Dependency Parsing"),
        CrossLink("tree", "Trees"),
        CrossLink("matrix_chain_multiplication", "Matrix Chain Multiplication"),
    ),
)
