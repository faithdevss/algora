package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lemmatizationContent = TopicContent(
    topicId = "lemmatization",
    whatIsIt = listOf(
        "Lemmatization reduces a word to its dictionary form (lemma) using vocabulary and grammar, so 'better' → 'good' and 'was' → 'be'.",
        "Unlike stemming, it's meaning-aware: it consults a lexicon and often the word's part of speech, always producing a real word.",
    ),
    steps = listOf(
        StepCard(1, "Determine Part of Speech", "Knowing whether a word is a verb or noun changes its lemma ('saw' → 'see' vs 'saw').", 0xFF818CF8),
        StepCard(2, "Look Up the Lemma", "Consult a lexicon (e.g. WordNet) to map the inflected form to its base.", 0xFF60A5FA),
        StepCard(3, "Handle Irregulars", "Irregular forms ('went' → 'go', 'mice' → 'mouse') resolve via the dictionary, not rules.", 0xFF10B981),
        StepCard(4, "Return a Real Word", "The lemma is always a valid dictionary entry.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Approach", "lexicon + morphology", "Dictionary-backed, POS-aware."),
        FormulaEntry("vs stemming", "accurate but slower", "Lookups and tagging cost more than rule-stripping."),
        FormulaEntry("Guarantee", "lemma is a valid word", "Unlike a stem, which may not be."),
    ),
    notationKey = listOf(
        NotationEntry("lemma", "the canonical dictionary form"),
        NotationEntry("POS", "part of speech, disambiguating the lemma"),
        NotationEntry("lexicon", "the word database consulted (e.g. WordNet)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Lemmatization (spaCy)",
            accentColor = 0xFF6366F1,
            code = """
                import spacy

                nlp = spacy.load("en_core_web_sm")
                for tok in nlp("The mice were running better"):
                    print(tok.text, "->", tok.lemma_)
                # mice -> mouse, were -> be, running -> run, better -> well
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Semantic Search", "Matching by meaning-preserving base forms improves relevance over raw stems."),
        ApplicationCard("flask", 0xFF60A5FA, "Linguistic Analysis", "Corpus and grammar studies need accurate, valid base forms."),
        ApplicationCard("search", 0xFF10B981, "Chatbots & QA", "Normalizing user input to lemmas helps intent matching."),
    ),
    takeaways = listOf(
        "Lemmatization maps words to valid dictionary forms using a lexicon and part of speech.",
        "It's more accurate than stemming and always yields a real word.",
        "It costs more compute — POS tagging plus lookups.",
        "Choose it when meaning matters; choose stemming when speed and recall dominate.",
    ),
    crossLinks = listOf(
        CrossLink("stemming", "Stemming (Porter Stemmer)"),
        CrossLink("tokenization", "Tokenization"),
    ),
)
