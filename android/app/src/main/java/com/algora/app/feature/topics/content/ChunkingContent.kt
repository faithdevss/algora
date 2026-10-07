package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val chunkingContent = TopicContent(
    topicId = "chunking",
    figure = Figure(
        caption = "The page's lab sentence, chunked. One regular expression over the POS tags — " +
            "DT? JJ* NN+, an optional determiner, any adjectives, one or more nouns — scans left to " +
            "right and finds three noun phrases, [0, 3), [4, 6) and [7, 9); two more rules add the " +
            "verb and the preposition, and every word now sits in exactly one flat phrase. The row " +
            "underneath is the same output written as BIO labels — B- begins a chunk, I- continues " +
            "it — which is why chunking and named-entity recognition share their models. What the " +
            "bracketing cannot say is the point of the trade: nothing records that \"in the " +
            "garden\" attaches to \"chased\" rather than to \"a cat\". One linear pass buys the " +
            "phrases; attachment and nesting need a parser.",
        shape = FigureShape.Strip(
            cells = listOf("the", "small", "dog", "chased", "a", "cat", "in", "the", "garden"),
            bands = listOf(
                FigureBand(0, 2, "NP", FigureTone.Accent),
                FigureBand(3, 3, "VP", FigureTone.Primary),
                FigureBand(4, 5, "NP", FigureTone.Accent),
                FigureBand(6, 6, "PP", FigureTone.Muted),
                FigureBand(7, 8, "NP", FigureTone.Accent),
            ),
            aux = listOf("B-NP", "I-NP", "I-NP", "B-VP", "B-NP", "I-NP", "B-PP", "B-NP", "I-NP"),
            auxLabel = "BIO",
        ),
    ),
    whatIsIt = listOf(
        "Chunking — shallow parsing — finds the flat phrases in a sentence without building a tree. Chunks never nest and never overlap, so \"the small dog chased a cat in the garden\" is [NP the small dog] [VP chased] [NP a cat] [PP in] [NP the garden] and nothing more is said about how those pieces relate. It runs over POS tags rather than words, which is why a regular expression per phrase type is enough: NP = DT? JJ* NN+, VP = a verb tag, PP = a preposition.",
        "The output is normally written as BIO tags — B- starts a chunk, I- continues one, O is outside — which turns a span problem into a per-token classification and lets a sequence labeller learn it. That encoding is the reason chunking and named entity recognition share their models entirely: they differ in what the spans mean, not in how they are represented or trained.",
        "The part worth internalising is the evaluation. Chunks are scored as exact-match spans, so a boundary that moves by one token loses the whole span, not part of it. In the scoring example below, moving the first NP's start from \"the\" to \"small\" leaves two of its three tokens correct and scores that span zero: F1 falls from 1.00 to 0.80, while per-token accuracy only falls to 0.78. Quoting the token number is how shallow parsers get oversold. The reason to accept that strictness is cost: chunking is a single linear pass — 9 steps for this sentence against 729 for a cubic parse — and for information extraction, template filling or feeding a noun-phrase list to a search index, the tree was never needed. Chunk when you want the phrases; parse when you need to know what attaches to what.",
    ),
    steps = listOf(
        StepCard(1, "Tag First", "Chunk grammars match tag sequences, so tagging accuracy caps chunking accuracy.", 0xFFF59E0B),
        StepCard(2, "Write One Pattern per Type", "NP = DT? JJ* NN+, VP = a verb, PP = a preposition. Non-nesting by construction.", 0xFFEAB308),
        StepCard(3, "Scan Left to Right", "Longest match wins; unmatched tokens stay outside every chunk.", 0xFF06B6D4),
        StepCard(4, "Encode as BIO", "B-/I-/O per token — the format a CRF or neural tagger trains on.", 0xFF3B82F6),
        StepCard(5, "Score Exact Spans", "Precision, recall and F1 over whole spans. No partial credit for near misses.", 0xFF6366F1),
        StepCard(6, "Know When to Parse", "Need attachment, nesting or long-range structure? This is the wrong tool.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("NP pattern", "DT? JJ* NN+", "An optional determiner, any adjectives, then one or more nouns."),
        FormulaEntry("BIO encoding", "B-NP I-NP I-NP B-VP …", "One label per token; a span problem becomes classification."),
        FormulaEntry("Exact-match scoring", "a span counts only if both boundaries and the label match", "Two of three tokens right scores 0."),
        FormulaEntry("Measured cost of one boundary", "F1 1.00 → 0.80, token accuracy → 0.78", "One token moved, one whole span lost."),
        FormulaEntry("Complexity", "O(n) vs O(n³)", "9 steps vs 729 for this nine-token sentence."),
        FormulaEntry("Reported CoNLL-2000 F1", "~94% for chunking on newswire", "Well above what full parsing achieves, on a much easier question."),
    ),
    notationKey = listOf(
        NotationEntry("chunk", "a flat, non-overlapping phrase — usually base NP, VP or PP"),
        NotationEntry("base NP", "a noun phrase with no nested noun phrase inside it"),
        NotationEntry("BIO / IOB2", "the begin-inside-outside tagging scheme; IOB1 differs on adjacent same-type chunks"),
        NotationEntry("chinking", "defining what is *not* in a chunk instead of what is — sometimes a shorter grammar"),
        NotationEntry("exact match", "the span-level criterion; contrast with token-level accuracy, which is kinder"),
        NotationEntry("CoNLL-2000", "the shared task that fixed the standard chunking dataset and metric"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A grammar, and its BIO output",
            accentColor = 0xFFF59E0B,
            code = """
                import nltk

                grammar = (r"NP: {<DT>?<JJ>*<NN.*>+}" "\n"
                           r"PP: {<IN>}"              "\n"
                           r"VP: {<VB.*>}")
                chunker = nltk.RegexpParser(grammar)

                tagged = [("the","DT"),("small","JJ"),("dog","NN"),("chased","VBD"),
                          ("a","DT"),("cat","NN"),("in","IN"),("the","DT"),("garden","NN")]
                tree = chunker.parse(tagged)
                print(nltk.chunk.tree2conlltags(tree))
                # [('the','DT','B-NP'), ('small','JJ','I-NP'), ('dog','NN','I-NP'),
                #  ('chased','VBD','B-VP'), ('a','DT','B-NP'), ('cat','NN','I-NP'),
                #  ('in','IN','B-PP'), ('the','DT','B-NP'), ('garden','NN','I-NP')]

                # A learned chunker is the same output from a different model -- and the identical
                # setup NER uses, which is the point:
                from sklearn_crfsuite import CRF
                crf = CRF(algorithm="lbfgs", c1=0.1, c2=0.1)
                crf.fit(X_train_features, y_train_bio)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Scoring spans, not tokens",
            accentColor = 0xFFEC4899,
            code = """
                gold = {("NP",0,3), ("VP",3,4), ("NP",4,6), ("PP",6,7), ("NP",7,9)}
                pred = {("NP",1,3), ("VP",3,4), ("NP",4,6), ("PP",6,7), ("NP",7,9)}
                #        ^ one boundary moved: "small dog" instead of "the small dog"

                hits = len(gold & pred)
                p, r = hits/len(pred), hits/len(gold)
                print(p, r, 2*p*r/(p+r))            # 0.80 0.80 0.80 -- the span is simply lost

                # Token accuracy on the same prediction is 7/9 = 0.78. Neither number is wrong; the
                # trap is quoting whichever is higher without saying which unit it counts. seqeval
                # is the library that gets this right by default:
                from seqeval.metrics import classification_report
                print(classification_report(y_true_bio, y_pred_bio))   # span-level, per type

                # And check the boundary errors specifically -- they are usually the largest error
                # class, and they are the ones a downstream extractor notices most.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFFF59E0B, "Keyphrase & Facet Extraction", "Noun-phrase lists for search facets, tag clouds and terminology mining."),
        ApplicationCard("browser", 0xFF3B82F6, "Information Extraction", "Template filling over flat phrases, without paying for a parse."),
        ApplicationCard("chip", 0xFF8B5CF6, "High-Throughput Pipelines", "A linear pass over tags at corpus scale, where cubic parsing is out of the question."),
        ApplicationCard("users", 0xFF14B8A6, "Preprocessing for NER", "Same BIO encoding, same models — chunking is the task NER's machinery was built on."),
    ),
    takeaways = listOf(
        "Chunks are flat, non-overlapping phrases matched over POS tags — one regex per phrase type is a working chunker.",
        "BIO encoding turns spans into per-token labels, which is exactly why chunking and NER share their models.",
        "Exact-match span scoring gives no partial credit: one boundary token wrong takes F1 from 1.00 to 0.80 in the example below.",
        "Token accuracy (0.78 here) counts a different unit — say which one you are reporting.",
        "It is O(n) against a parse's O(n³) — 9 steps vs 729 — and gives phrases without attachment.",
    ),
    crossLinks = listOf(
        CrossLink("pos_tagging", "Part-of-Speech Tagging"),
        CrossLink("ner", "Named Entity Recognition"),
        CrossLink("constituency_parsing", "Constituency Parsing"),
        CrossLink("regex_nlp", "Regular Expressions"),
    ),
)
