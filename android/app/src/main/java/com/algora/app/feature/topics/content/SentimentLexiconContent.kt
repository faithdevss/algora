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

internal val sentimentLexiconContent = TopicContent(
    topicId = "sentiment_lexicon",
    figure = Figure(
        caption = "The page's lab review, scored word by word. Only the two \"good\"s are in the " +
            "lexicon (+2 each), so the plain sum says +4.0 — clearly positive, although the writer " +
            "said the plot was not good. The three rules every lexicon system grows change that. " +
            "\"not\" within three words flips and damps the first \"good\" by −0.75 — milder than " +
            "\"bad\" — and because it sits before \"but\" it is halved again: 2 × −0.75 × 0.5 = " +
            "−0.75. The second \"good\" is intensified by \"very\" (× 1.5) and sits after \"but\", " +
            "the clause the writer endorses (× 1.5): +4.50. The review lands at +3.75 — still " +
            "positive, but no longer as glowing as the count of nice words suggested.",
        shape = FigureShape.Strip(
            cells = listOf("plot", "was", "not", "good", "but", "acting", "was", "very", "good"),
            bands = listOf(
                FigureBand(0, 3, "before but × 0.5", FigureTone.Muted),
                FigureBand(5, 8, "after but × 1.5", FigureTone.Primary),
            ),
            pointers = listOf(
                FigurePointer(2, "flip", FigureTone.Warn),
                FigurePointer(7, "× 1.5"),
            ),
            aux = listOf("", "", "", "−0.75", "", "", "", "", "+4.50"),
            auxLabel = "score",
        ),
    ),
    whatIsIt = listOf(
        "Lexicon-based sentiment analysis scores text by looking words up in a dictionary of valences and adding them up. AFINN gives 3,382 words a score from −5 to +5; VADER adds intensity, emoji, slang and punctuation handling; SentiWordNet attaches polarity to WordNet senses. There is no training data and no model, and the result is completely auditable — you can point at the exact words that produced the score, which is why regulated and low-resource settings still use this approach on purpose rather than as a fallback.",
        "The failure everyone hits in week one is negation: \"the movie was not good\" contains a +2 word and sums to positive. So every lexicon system grows the same three rules. A negator flips the polarity of scored words within a small window, damped rather than mirrored because \"not good\" is milder than \"bad\". Intensifiers and diminishers scale the value — \"very good\" up, \"slightly slow\" down. And a clause after \"but\" outweighs what came before it, because contrastive conjunctions mark which half the writer means. The lab runs all three on one review — \"the plot was not good but the acting was very good\" — where the plain sum of the two \"good\"s says +4.0. With the rules the first \"good\" becomes 2 × −0.75 × 0.5 = −0.75 (negated, and before \"but\"), the second 2 × 1.5 × 1.5 = +4.50 (intensified, and after it), and the review lands at +3.75: still positive, as the writer meant, but no longer as glowing as the naive count.",
        "The rules then produce their own errors — in \"never buy this awful thing\", \"never\" sits three tokens before \"awful\", so the flip turns a negative review positive. A fixed window has no idea what a negator actually scopes over — getting that right needs a parse, and by the time you are parsing, a classifier trained on labelled reviews is cheaper and more accurate. The structural limit is coverage: most words have no entry at all — in the lab's review only the two \"good\"s score — and the lexicon has no opinion about sarcasm, comparison (\"better than their last one\") or domain sense — \"unpredictable\" is praise for a thriller and a complaint about a car. Use a lexicon when no labelled data exists, when the score must be explainable, or as a feature inside a trained model rather than instead of one.",
    ),
    steps = listOf(
        StepCard(1, "Choose a Lexicon", "AFINN for simplicity, VADER for social text, SentiWordNet for sense-level scores.", 0xFFF59E0B),
        StepCard(2, "Normalise and Tokenise", "Match the lexicon's form — casing and lemmatisation decide whether lookups hit.", 0xFFEAB308),
        StepCard(3, "Sum the Valences", "The baseline score, and already the right answer on unambiguous text.", 0xFF06B6D4),
        StepCard(4, "Handle Negation", "Flip scored words within a small window, damped. The highest-value rule by far.", 0xFF3B82F6),
        StepCard(5, "Scale and Contrast", "Intensifiers, diminishers, and \"but\" weighting the clause that follows it.", 0xFF6366F1),
        StepCard(6, "Measure Coverage", "What share of tokens the lexicon even scores — the ceiling on everything above.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Plain score", "score(d) = Σ valence(w)", "+4.0 on the lab's \"not good … very good\" review."),
        FormulaEntry("With rules", "negation × −0.75, intensifier × 1.5, post-\"but\" × 1.5", "+3.75 on the same review: −0.75 + 4.50."),
        FormulaEntry("Negation window", "flip scored words within 3 tokens of a negator", "VADER looks three words back."),
        FormulaEntry("Damped flip", "\"not good\" ≠ \"bad\"", "Which is why the multiplier is −0.75 rather than −1."),
        FormulaEntry("Coverage", "most tokens score 0", "\"plot\", \"acting\" and \"the\" contribute nothing, whatever they mean."),
        FormulaEntry("Where the rule misfires", "\"never buy this awful thing\" → +2.25", "The negator's scope is not what a fixed window assumes."),
    ),
    notationKey = listOf(
        NotationEntry("valence", "a word's signed sentiment strength, e.g. AFINN's −5…+5"),
        NotationEntry("negator", "not, never, no, cannot — a word that flips polarity within its scope"),
        NotationEntry("scope", "the span a negator actually affects; a fixed window is an approximation of it"),
        NotationEntry("intensifier / diminisher", "very, extremely / slightly, barely — multipliers rather than terms"),
        NotationEntry("contrastive conjunction", "but, however, although — marks which clause the writer endorses"),
        NotationEntry("VADER", "a rule-tuned lexicon built for social media: emoji, slang, caps and punctuation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The sum, then the three rules",
            accentColor = 0xFFF59E0B,
            code = """
                LEX = {"good": 2, "great": 3, "terrible": -3, "awful": -3, "slow": -1, "excellent": 3}
                NEG = {"not", "never", "no", "cannot"}
                INT = {"very": 1.5, "extremely": 2.0, "slightly": 0.5}

                def plain(text):
                    return sum(LEX.get(w, 0) for w in text.split())

                def with_rules(text, window=3):
                    words, total = text.split(), 0.0
                    cut = words.index("but") if "but" in words else -1
                    for i, w in enumerate(words):
                        v = LEX.get(w, 0)
                        if not v:
                            continue
                        before = words[max(0, i - window):i]
                        if any(n in NEG for n in before):
                            v *= -0.75                     # damped: "not good" is not "bad"
                        if before and before[-1] in INT:
                            v *= INT[before[-1]]
                        if cut >= 0:
                            v *= 0.5 if i < cut else 1.5   # the clause after "but" wins
                        total += v
                    return total

                print(plain("the movie was not good"))      # +2.0  -> wrong
                print(with_rules("the movie was not good"))  # -1.5  -> right
                # The negation rule is the one that fixes the most errors in practice.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where to stop, and what to use instead",
            accentColor = 0xFFEC4899,
            code = """
                from nltk.sentiment.vader import SentimentIntensityAnalyzer
                sia = SentimentIntensityAnalyzer()
                print(sia.polarity_scores("The app is slightly slow but the design is EXCELLENT!!!"))
                # VADER already handles caps, punctuation, emoji and the "but" clause -- do not
                # rebuild it; start here and only write rules it lacks for your domain.

                print(sia.polarity_scores("never buy this awful thing"))
                # Still hard: a window-based negator does not know its own scope. Fixing that needs
                # the dependency parse -- and once you are parsing, a supervised classifier on a few
                # thousand labelled reviews is both cheaper and more accurate.

                # The honest decision rule:
                #   no labels, or the score must be explainable       -> lexicon
                #   a few thousand labels and a fixed domain          -> TF-IDF + linear model
                #   accuracy matters and compute is available         -> fine-tuned transformer
                #   ...and in the first two cases, the lexicon score is a useful FEATURE, not a rival.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFFF59E0B, "Brand & Social Monitoring", "Volume-scale scoring where per-item accuracy matters less than the trend."),
        ApplicationCard("finance", 0xFF3B82F6, "Finance", "Loughran-McDonald exists because general lexicons mis-score financial language badly."),
        ApplicationCard("lock", 0xFF8B5CF6, "Auditable Scoring", "Regulated settings where a score must be explained by the words that caused it."),
        ApplicationCard("help", 0xFFEC4899, "Where It Fails", "Sarcasm, comparison and domain sense — none of which a word list can represent."),
    ),
    takeaways = listOf(
        "A lexicon plus a sum is a complete sentiment system with no training data and full explainability.",
        "Negation is the first and largest failure: \"not good\" sums positive.",
        "Negation, intensity and \"but\" rules turn the lab's review from a naive +4.0 into +3.75, with \"not good\" at −0.75.",
        "The rules then misfire on scope — \"never buy this awful thing\" scores +2.25 — which needs a parse to fix properly.",
        "Coverage is the ceiling: most words score zero, and sarcasm and domain sense (\"fast\" for a car vs a battery) are outside it entirely.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("stop_words", "Stop Word Removal"),
        CrossLink("dependency_parsing", "Dependency Parsing"),
    ),
)
