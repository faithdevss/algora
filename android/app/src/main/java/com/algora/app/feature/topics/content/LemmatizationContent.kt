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

internal val lemmatizationContent = TopicContent(
    topicId = "lemmatization",
    figure = Figure(
        caption = "The page's lab: five words through the Porter stemmer and the WordNet lemmatizer. " +
            "Regular inflection is where they agree — \"running\" is \"run\" either way. A suffix " +
            "rule can be right about the suffix and wrong about the word: \"flies\" stems to \"fli\" " +
            "and \"studies\" to \"studi\", where the lemmatizer, with a dictionary, gives \"fly\" and " +
            "\"study\". Irregular forms have no suffix to strip at all: \"mice\" passes through the " +
            "stemmer unchanged, and \"better\" becomes \"good\" only because the tagger marked it a " +
            "comparative adjective — as the verb in \"to better oneself\", its lemma is \"better\". " +
            "One stem out of five is a dictionary word; given the POS tags, all five lemmas are.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("run", "run"),
                listOf("fli", "fly"),
                listOf("studi", "study"),
                listOf("better", "good"),
                listOf("mice", "mouse"),
            ),
            rowHeaders = listOf("running", "flies", "studies", "better (JJR)", "mice"),
            colHeaders = listOf("Porter stem", "WordNet lemma"),
            marks = listOf(
                FigureCell(1, 0, FigureTone.Warn),
                FigureCell(2, 0, FigureTone.Warn),
                FigureCell(3, 1, FigureTone.Accent),
                FigureCell(4, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Lemmatization reduces a word to its dictionary form — its lemma — so that \"studies\", \"studying\" and \"studied\" all become \"study\". Unlike stemming, which chops suffixes by rule and can produce non-words, a lemmatizer looks the word up, and it needs two things a stemmer does not: a dictionary, and the word's part of speech in this sentence.",
        "The lab runs five words through the Porter stemmer and the WordNet lemmatizer. Regular inflection is where they agree: \"running\" becomes \"run\" both ways. \"flies\" stems to \"fli\" — the IES→I rule is right for the suffix and wrong for the word — while the lemmatizer knows the noun \"fly\"; \"studies\" likewise becomes \"studi\" against \"study\". Irregular forms have no suffix to strip: \"mice\" passes through the stemmer unchanged and becomes \"mouse\" only by dictionary, and \"better\" becomes \"good\" only because it is tagged as a comparative adjective. Only 1 of the 5 stems is a dictionary word; given the POS tags, all 5 lemmas are.",
        "The part of speech genuinely decides the answer: as a verb, \"to better oneself\", the lemma of \"better\" is \"better\". That dependence makes lemmatization slower than stemming and only as good as the tagger in front of it. For search and classification, stemming's crude merging is often good enough; when the output has to be real words, or the language has rich irregular morphology, lemmatization is worth the cost.",
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
        "In the lab only 1 of 5 Porter stems is a dictionary word; with POS tags all 5 WordNet lemmas are.",
    ),
    crossLinks = listOf(
        CrossLink("stemming", "Stemming (Porter Stemmer)"),
        CrossLink("tokenization", "Tokenization"),
    ),
)
