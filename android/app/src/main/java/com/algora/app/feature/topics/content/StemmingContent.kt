package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stemmingContent = TopicContent(
    topicId = "stemming",
    figure = Figure(
        caption = "\"Running\", \"runner\" and \"runs\" all fall to \"run\" — the vocabulary shrinks and " +
            "every form now shares one bucket of evidence. \"Easily\" survives as \"easili\", not a " +
            "real word, because the rules strip by pattern with no dictionary to check against. And " +
            "\"university\" over-stems to \"univers\", the same stem \"universe\" would also produce — " +
            "the collision the rules cannot see.",
        shape = FigureShape.Strip(
            cells = listOf("running", "runner", "runs", "easily", "university"),
            bands = listOf(FigureBand(0, 2, "one stem")),
            pointers = listOf(
                FigurePointer(3, "not a word", FigureTone.Warn),
                FigurePointer(4, "over-stem", FigureTone.Warn),
            ),
            aux = listOf("run", "run", "run", "easili", "univers"),
            auxLabel = "Porter stems",
        ),
    ),
    whatIsIt = listOf(
        "Stemming crudely chops word endings to reduce related forms to a common root, so 'running', 'runner', and 'runs' collapse toward 'run'.",
        "The Porter stemmer is the classic: a fast set of suffix-stripping rules that ignores meaning, producing a stem that may not even be a real word.",
    ),
    steps = listOf(
        StepCard(1, "Apply Suffix Rules", "Match and strip common endings (-ing, -ed, -s, -ional) in ordered phases.", 0xFF818CF8),
        StepCard(2, "Measure Rules", "Conditions on the word's structure decide whether a suffix is actually removed.", 0xFF60A5FA),
        StepCard(3, "Produce a Stem", "The result is a truncated root, not guaranteed to be a dictionary word.", 0xFF10B981),
        StepCard(4, "Normalize the Vocabulary", "Many surface forms map to one token, shrinking the vocabulary.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Approach", "rule-based suffix stripping", "No dictionary, no part-of-speech."),
        FormulaEntry("Speed", "very fast", "Pure string rules, no lookups."),
        FormulaEntry("Errors", "over/under-stemming", "'university' → 'univers'; distinct words may collide."),
    ),
    notationKey = listOf(
        NotationEntry("stem", "the truncated root form"),
        NotationEntry("over-stemming", "too much removed — unrelated words merge"),
        NotationEntry("under-stemming", "too little removed — related words stay split"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Porter stemmer (NLTK)",
            accentColor = 0xFF6366F1,
            code = """
                from nltk.stem import PorterStemmer

                ps = PorterStemmer()
                for w in ["running", "runner", "runs", "easily"]:
                    print(w, "->", ps.stem(w))
                # running -> run,  easily -> easili  (not a real word)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF818CF8, "Search Engines", "Matching queries to documents regardless of word form boosts recall."),
        ApplicationCard("chart", 0xFF60A5FA, "Classic Text Mining", "Shrinking the vocabulary helps bag-of-words and TF-IDF pipelines."),
        ApplicationCard("chip", 0xFF10B981, "Cheap Preprocessing", "When speed matters more than linguistic precision, stemming is the fast option."),
    ),
    takeaways = listOf(
        "Stemming strips suffixes by rules to collapse word forms to a common root.",
        "It's fast but crude — stems needn't be real words, and errors go both ways.",
        "Use it for classic search and bag-of-words pipelines where recall beats precision.",
        "For accurate, dictionary-valid roots, use lemmatization instead.",
    ),
    crossLinks = listOf(
        CrossLink("lemmatization", "Lemmatization"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
    ),
)
