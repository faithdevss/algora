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

internal val posTaggingContent = TopicContent(
    topicId = "pos_tagging",
    figure = Figure(
        caption = "\"They man a boat\" — the baseline tags every occurrence of \"man\" NN, its most " +
            "frequent training tag, and gets this one wrong. The HMM tags the same sentence 14/14 " +
            "against the baseline's 12/14 because it reads \"man\" in context: a pronoun is followed " +
            "by a verb far more often than not, so PRP shifts the next tag to VBP. Nothing about the " +
            "string \"man\" decided that — only its neighbours did.",
        shape = FigureShape.Strip(
            cells = listOf("they", "man", "a", "boat"),
            bands = listOf(FigureBand(0, 1, "PRP→verb")),
            pointers = listOf(FigurePointer(1, "not NN here", FigureTone.Warn)),
            aux = listOf("PRP", "VBP", "DT", "NN"),
            auxLabel = "HMM tags, 14/14 — the sequence decides \"man\"",
        ),
    ),
    whatIsIt = listOf(
        "Part-of-speech tagging assigns every token its syntactic category — noun, verb, determiner, adjective — from a fixed tagset. The Penn Treebank set has 45 tags, which is more granular than school grammar: NN, NNS, NNP and NNPS are all nouns, and VB, VBD, VBG, VBN, VBP and VBZ are all verbs, because the distinctions carry information a parser needs. It is the first task in almost every classical pipeline, and everything downstream — chunking, parsing, lexicon sentiment — reads its output rather than the words.",
        "The task exists because words are ambiguous in context, not in isolation. \"Man\" is a noun in \"the old man chased a cat\" and a verb in \"they man a boat\", and nothing about the string decides which. On the lab's mini treebank only one type of 24 is ambiguous, which understates the problem badly: on the Brown corpus around 11% of *types* are ambiguous but roughly 40% of *tokens* are, because the ambiguous words are the frequent ones. Any evaluation that reports type-level statistics is measuring the easy half.",
        "The baseline is the number that keeps everything honest: give each word its most frequent training tag and unknown words NN, and you get about 90% on English. In the lab it scores 12/14 while a bigram HMM estimated from the same text scores 14/14 — it fixes \"man\" after a pronoun, where PRP is far more often followed by a verb, and it tags the unseen word \"walk\" as VBP purely from its neighbours. That is the whole argument for context. Modern taggers reach ~97% with suffix features, capitalisation and a wider window, which is roughly where human annotators agree with each other, so the remaining errors are a mix of genuinely hard noun/verb and adjective/participle cases and disagreement about what the right answer even is.",
    ),
    steps = listOf(
        StepCard(1, "Fix a Tagset", "45 Penn tags, 17 Universal POS tags, or your own — the choice decides what \"accuracy\" means.", 0xFFF59E0B),
        StepCard(2, "Count the Training Text", "Word→tag counts and tag→tag transitions; that is a complete generative tagger.", 0xFFEAB308),
        StepCard(3, "Run the Baseline First", "Most-frequent-tag, unknowns to NN. ~90% on English, and the bar anything else must clear.", 0xFF06B6D4),
        StepCard(4, "Add Context", "Viterbi over an HMM, a CRF, or a neural tagger — all of them are scoring sequences, not tokens.", 0xFF3B82F6),
        StepCard(5, "Handle Unknown Words", "Suffixes, capitalisation, hyphenation, digit shape — where the remaining accuracy lives.", 0xFF6366F1),
        StepCard(6, "Read the Confusion Matrix", "Not the accuracy: noun/verb and adjective/participle are almost the whole error budget.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Baseline", "tag(w) = argmax_t count(w, t)", "12/14 = 85.7% on the lab's held-out sentences; ~90% on English."),
        FormulaEntry("Sequence model", "argmax_t Π P(tᵢ | tᵢ₋₁) P(wᵢ | tᵢ)", "14/14 = 100% on the same sentences, from the same training text."),
        FormulaEntry("Ambiguity, types vs tokens", "~11% of Brown types, ~40% of its tokens", "The frequent words are the ambiguous ones."),
        FormulaEntry("Accuracy ceiling", "~97% inter-annotator agreement", "A tagger at 97.3% is arguing with the annotators, not failing."),
        FormulaEntry("Unknown-word rate", "~2–3% of tokens in running text", "And a disproportionate share of the errors."),
        FormulaEntry("Tagset size", "45 (PTB) · 17 (UPOS) · 12 (universal coarse)", "Coarser tagsets score higher and say less."),
    ),
    notationKey = listOf(
        NotationEntry("NN / NNS / NNP", "singular, plural and proper nouns — separate tags in the Penn set"),
        NotationEntry("VBD / VBZ / VBP", "past tense, third-person singular present, other present"),
        NotationEntry("JJ / RB / IN / DT", "adjective, adverb, preposition or subordinating conjunction, determiner"),
        NotationEntry("UPOS", "the 17-tag Universal POS set used by Universal Dependencies"),
        NotationEntry("closed class", "determiners, prepositions, pronouns — a fixed, small membership"),
        NotationEntry("open class", "nouns, verbs, adjectives, adverbs — where new words and unknowns arrive"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The baseline, and why it is hard to beat",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import Counter, defaultdict
                import nltk

                train = nltk.corpus.treebank.tagged_sents()[:3000]
                test  = nltk.corpus.treebank.tagged_sents()[3000:]

                counts = defaultdict(Counter)
                for sent in train:
                    for word, tag in sent:
                        counts[word][tag] += 1

                def baseline(word):
                    return counts[word].most_common(1)[0][0] if word in counts else "NN"

                correct = total = 0
                for sent in test:
                    for word, gold in sent:
                        correct += baseline(word) == gold
                        total += 1
                print(correct / total)        # ~0.90 -- report this before anything else

                # Ambiguity is a token-level problem, not a type-level one:
                ambiguous_types = sum(1 for w in counts if len(counts[w]) > 1)
                print(ambiguous_types / len(counts))    # ~0.11 of TYPES
                # ...but weight by frequency and it is ~0.40 of TOKENS.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What a real tagger adds",
            accentColor = 0xFFEC4899,
            code = """
                import spacy
                nlp = spacy.load("en_core_web_sm")

                for text in ["the old man chased a cat", "they man a boat"]:
                    print([(t.text, t.tag_, t.pos_) for t in nlp(text)])
                # 'man' -> NN in the first, VBP in the second: the sequence decides, not the word.

                # The features that buy the last few points, in order of value:
                #   1. the previous one or two tags        (sequence context)
                #   2. word suffixes -ing -ed -ly -tion    (unknown-word morphology)
                #   3. capitalisation and position         (proper nouns, sentence-initial case)
                #   4. digit/hyphen shape                  (numbers, compounds)
                #
                # Evaluate on the confusion matrix, not the accuracy -- and check the unknown-word
                # accuracy separately, because it is typically 10-15 points below the overall figure
                # and is what breaks first on a new domain.
                print(nlp.get_pipe("tagger").labels)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFF59E0B, "Pipeline Input", "Chunkers, parsers and rule-based extractors read tags rather than words."),
        ApplicationCard("search", 0xFF3B82F6, "Search & Indexing", "Tags disambiguate query terms and drive noun-phrase extraction for facets."),
        ApplicationCard("music", 0xFF8B5CF6, "Text-to-Speech", "\"record\" as noun and verb are pronounced differently; the tag decides."),
        ApplicationCard("flask", 0xFF14B8A6, "Corpus Linguistics", "Frequency studies over categories rather than strings — the field the tagsets came from."),
    ),
    takeaways = listOf(
        "Tagging is a sequence problem because ambiguity is contextual: \"man\" is NN or VBP depending on what precedes it.",
        "Report the most-frequent-tag baseline first — it reaches ~90% on English, and the lab's 12/14 against the HMM's 14/14 is the honest comparison.",
        "Type-level ambiguity (~11%) badly understates token-level ambiguity (~40%); the common words are the ambiguous ones.",
        "Unknown words are where accuracy is really won — suffix, capitalisation and shape features, not the tagger's architecture.",
        "~97% is the annotator agreement ceiling, so the last few points are partly disagreement rather than error.",
    ),
    crossLinks = listOf(
        CrossLink("hmm", "Hidden Markov Models"),
        CrossLink("chunking", "Chunking"),
        CrossLink("dependency_parsing", "Dependency Parsing"),
        CrossLink("ner", "Named Entity Recognition"),
    ),
)
