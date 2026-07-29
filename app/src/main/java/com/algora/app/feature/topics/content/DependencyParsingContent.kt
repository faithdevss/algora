package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dependencyParsingContent = TopicContent(
    topicId = "dependency_parsing",
    whatIsIt = listOf(
        "A dependency parse is a set of labelled arcs between words: every token has exactly one head, one token is headed by ROOT, and the label says what the relation is — nsubj, obj, det, amod. There are no phrase nodes anywhere. That makes the structure directly usable: \"who did what to whom\" is a lookup on the arcs out of the verb, which is why information extraction, relation extraction and most multilingual work run on dependencies rather than phrase trees.",
        "The dominant algorithm family is transition-based, and it is startlingly simple. A stack, a buffer and three moves: SHIFT pushes the next word onto the stack; LEFT-ARC attaches the second stack item to the top as its dependent and pops it; RIGHT-ARC attaches the top to the second and pops it. An arc is only committed once its dependent has collected all of its own children, so nothing is ever revisited. Parsing the lab's six-word sentence takes exactly 12 transitions — 2n, because every word is shifted once and attached once — so a greedy transition parser is linear in sentence length, with a classifier that only ever has to choose among three moves.",
        "Two things are worth carrying away beyond the algorithm. First, evaluation is per word and comes in two numbers: UAS counts correct heads, LAS also requires the correct label, and the gap between them is label errors on attachments that were already right — the lab's wrong parse scores 0.83 UAS and 0.67 LAS. Second, arc-standard can only produce *projective* trees, where no two arcs cross. \"A hearing is scheduled on the issue today\" needs a crossing arc and is unreachable by any sequence of the three moves; roughly 1% of English sentences are non-projective, and far more in German, Dutch or Czech. That single limitation is why the swap transition, pseudo-projective transforms and graph-based parsers (which search all spanning trees with Chu-Liu/Edmonds, at O(n²)) all exist.",
    ),
    steps = listOf(
        StepCard(1, "Tag First", "Transition classifiers read POS tags as features; the parse is downstream of tagging.", 0xFFF59E0B),
        StepCard(2, "Initialise", "Stack holds ROOT, buffer holds the sentence, arc set is empty.", 0xFFEAB308),
        StepCard(3, "Choose a Move", "SHIFT, LEFT-ARC or RIGHT-ARC — a three-way classification over the current configuration.", 0xFF06B6D4),
        StepCard(4, "Commit the Arc", "Attach and pop; a dependent leaves only when it has all its own children.", 0xFF3B82F6),
        StepCard(5, "Stop at 2n", "Every word shifted once, attached once — a complete parse in linear time.", 0xFF6366F1),
        StepCard(6, "Score UAS and LAS", "Heads, then heads-and-labels. Quote both, because the gap is informative.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Transitions", "exactly 2n for n words", "12 for the lab's six-word sentence — one shift and one attachment each."),
        FormulaEntry("UAS", "correct heads / tokens = 5/6 = 0.83", "Unlabelled attachment score on the lab's wrong parse."),
        FormulaEntry("LAS", "correct head and label / tokens = 4/6 = 0.67", "The gap is a label error on an attachment that was right."),
        FormulaEntry("Greedy cost", "O(n) with a constant-time classifier", "Against O(n²) for graph-based maximum spanning tree parsing."),
        FormulaEntry("Projectivity", "no two arcs may cross", "Arc-standard cannot build a crossing arc at all."),
        FormulaEntry("Non-projective rate", "~1% of English sentences, far more in Czech/German", "Which is why the swap transition and graph parsers exist."),
    ),
    notationKey = listOf(
        NotationEntry("head / dependent", "the governing word and the word attached to it"),
        NotationEntry("ROOT", "the artificial node heading the sentence's main predicate"),
        NotationEntry("nsubj / obj / det / amod", "nominal subject, object, determiner, adjectival modifier"),
        NotationEntry("arc-standard / arc-eager", "the two classic transition systems; arc-eager attaches right arcs earlier"),
        NotationEntry("projective", "a tree whose arcs never cross when drawn above the sentence"),
        NotationEntry("Universal Dependencies", "one label set and annotation guide across 100+ languages"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Arc-standard, in full",
            accentColor = 0xFFF59E0B,
            code = """
                SHIFT, LEFT, RIGHT = "shift", "left", "right"

                def parse(words, oracle):
                    stack, buffer, arcs = [0], list(range(1, len(words) + 1)), []
                    while buffer or len(stack) > 1:
                        move, label = oracle(stack, buffer, arcs)
                        if move == SHIFT:
                            stack.append(buffer.pop(0))
                        elif move == LEFT:                     # second <- top
                            arcs.append((stack[-1], stack[-2], label))
                            stack.pop(-2)
                        else:                                  # RIGHT: top <- second
                            arcs.append((stack[-2], stack[-1], label))
                            stack.pop()
                    return arcs

                # "the small dog chased a cat" -> 12 transitions, 6 arcs:
                #   SHIFT SHIFT SHIFT LEFT(amod) LEFT(det) SHIFT LEFT(nsubj)
                #   SHIFT SHIFT LEFT(det) RIGHT(obj) RIGHT(root)
                #
                # In a real parser `oracle` is a classifier over the configuration -- the top few
                # stack items, the next buffer items, their tags and already-built children. Chen &
                # Manning 2014 replaced the sparse feature templates with a small feed-forward net
                # over embeddings of exactly those slots, and that is what made neural parsing fast.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Reading a parse, and checking projectivity",
            accentColor = 0xFFEC4899,
            code = """
                import spacy
                nlp = spacy.load("en_core_web_sm")
                doc = nlp("the small dog chased a cat")

                for t in doc:
                    print(t.text, t.dep_, "<-", t.head.text)
                # small amod <- dog ; dog nsubj <- chased ; cat obj <- chased ...

                # Relation extraction is now a lookup rather than a tree walk:
                verb = [t for t in doc if t.dep_ == "ROOT"][0]
                subj = [c for c in verb.children if c.dep_ == "nsubj"]
                obj  = [c for c in verb.children if c.dep_ == "obj"]
                print(subj, verb, obj)      # (dog, chased, cat)

                def crossings(heads):       # heads is 1-indexed, 0 = ROOT
                    arcs = [(min(h, i + 1), max(h, i + 1)) for i, h in enumerate(heads) if h]
                    return [(a, b) for a, b in arcs
                            for c, d in arcs if a < c < b < d]
                print(crossings([2, 4, 4, 0, 2, 7, 5, 4]))   # non-empty: not projective
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFFF59E0B, "Relation Extraction", "Subject-verb-object triples read straight off the arcs out of the predicate."),
        ApplicationCard("globe", 0xFF3B82F6, "Multilingual NLP", "Universal Dependencies annotates 100+ languages with one label set — the reason dependencies dominate."),
        ApplicationCard("help", 0xFF8B5CF6, "Question Answering", "Matching a question's dependency path against a candidate answer's is a strong classical baseline."),
        ApplicationCard("chip", 0xFF14B8A6, "Grammar Tools", "Agreement and attachment checks are local conditions on arcs."),
    ),
    takeaways = listOf(
        "One labelled arc per word, one ROOT, no phrase nodes — structure you can query directly.",
        "Arc-standard needs exactly 2n transitions (12 for six words), so greedy parsing is linear in sentence length.",
        "UAS scores heads, LAS scores heads and labels: 0.83 vs 0.67 on the lab's wrong parse, and the gap is pure label error.",
        "The transition system can only build projective trees; crossing arcs need swap, pseudo-projective transforms or a graph parser.",
        "Choose dependencies when the question is what relates to what, and for anything multilingual.",
    ),
    crossLinks = listOf(
        CrossLink("constituency_parsing", "Constituency Parsing"),
        CrossLink("pos_tagging", "Part-of-Speech Tagging"),
        CrossLink("pcfg", "Probabilistic CFGs"),
        CrossLink("topological_sort", "Topological Sort"),
    ),
)
