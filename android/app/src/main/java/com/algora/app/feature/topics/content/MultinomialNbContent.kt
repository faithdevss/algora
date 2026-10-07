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

internal val multinomialNbContent = TopicContent(
    topicId = "multinomial_nb",
    figure = Figure(
        caption = "The lab's whole training procedure, which is the first two columns: five " +
            "documents, 26 tokens, six vocabulary terms, counted per class. Five of the twelve " +
            "count cells are zero, and unsmoothed every one of them is fatal — the score is a " +
            "product, so a single zero takes the class to −∞ regardless of the other evidence. " +
            "α = 1 turns them into 1/21 and 1/17 instead, which is the last two columns. Scoring " +
            "\"goal goal great\" from those numbers and the 3:2 prior: −4.451 for sports against " +
            "−8.030 for politics, a log gap of 3.578, odds of 35.8 to 1, posterior 0.973. " +
            "Almost all of it is \"goal\", worth a likelihood ratio of 4.86 per occurrence; " +
            "\"great\" is worth 1.01, which is nothing. Counting, then dividing, is the " +
            "whole model.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("5", "0", "0.286", "0.059"),
                listOf("4", "0", "0.238", "0.059"),
                listOf("2", "0", "0.143", "0.059"),
                listOf("0", "5", "0.048", "0.353"),
                listOf("0", "3", "0.048", "0.235"),
                listOf("4", "3", "0.238", "0.235"),
            ),
            rowHeaders = listOf("goal", "match", "team", "vote", "policy", "great"),
            colHeaders = listOf("n sp", "n pol", "P | sp", "P | pol"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Warn),
                FigureCell(3, 0, FigureTone.Warn),
                FigureCell(4, 0, FigureTone.Warn),
                FigureCell(5, 2, FigureTone.Muted),
                FigureCell(5, 3, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Multinomial naive Bayes models a document as a bag of counts drawn from a per-class multinomial distribution. Position is discarded entirely — \"dog bites man\" and \"man bites dog\" are the same input — and what remains is how often each vocabulary term occurred.",
        "Training is counting. Sum the occurrences of each term within each class, divide by that class's total token count, and you have the likelihood. There is no gradient, no iteration and no hyperparameter beyond the smoothing constant, so it fits a corpus of millions of documents in a single pass and updates incrementally forever after.",
        "Laplace smoothing is not a detail; without it the model is broken. An unseen term gives probability zero, and because the score is a product, one zero annihilates all the other evidence no matter how strong. Adding α = 1 to every count fixes it. The one thing to be honest about is that the independence assumption is plainly false for text — \"New\" and \"York\" are not independent — and the effect is that dependent evidence gets counted repeatedly, pushing posteriors to 0 or 1. The argmax survives this, which is why the classifier works while its probabilities do not.",
    ),
    steps = listOf(
        StepCard(1, "Count Terms per Class", "One integer per (term, class). The entire training procedure.", 0xFF14B8A6),
        StepCard(2, "Smooth", "Add α to every count before dividing, so no term has probability zero.", 0xFF818CF8),
        StepCard(3, "Divide by Class Totals", "P(term|class) = (count + α) / (class total + α·|V|).", 0xFF60A5FA),
        StepCard(4, "Score in Logs", "log P(class) + Σ count · log P(term|class), summed over the document.", 0xFF10B981),
        StepCard(5, "Take the Argmax", "Highest score wins. Only the relative values matter.", 0xFFF59E0B),
        StepCard(6, "Weight Terms if It Helps", "TF-IDF instead of raw counts usually improves it, despite breaking the generative story.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Likelihood", "P(t|c) = (Nₜ,c + α) / (Nc + α|V|)", "Smoothed relative frequency."),
        FormulaEntry("Score", "log P(c) + Σₜ count(t)·log P(t|c)", "Repeats count separately."),
        FormulaEntry("α = 1", "Laplace smoothing", "α < 1 is Lidstone, and often better for large |V|."),
        FormulaEntry("Training cost", "O(total tokens)", "One pass; trivially parallel."),
        FormulaEntry("Decision rule", "argmax_c", "Only the argmax is trustworthy — not the value."),
        FormulaEntry("Log-linear form", "score = wᵀx + b", "It is a linear classifier in count space."),
    ),
    notationKey = listOf(
        NotationEntry("Nₜ,c", "occurrences of term t across class c"),
        NotationEntry("Nc", "total tokens in class c"),
        NotationEntry("|V|", "vocabulary size"),
        NotationEntry("α", "smoothing constant"),
        NotationEntry("bag of words", "counts with position discarded"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A text classifier in five lines",
            accentColor = 0xFF14B8A6,
            code = """
                from sklearn.feature_extraction.text import TfidfVectorizer
                from sklearn.naive_bayes import MultinomialNB
                from sklearn.pipeline import make_pipeline

                # TF-IDF weights are not counts and violate the multinomial's generative
                # story outright — and they still work better than raw counts in practice,
                # because down-weighting ubiquitous terms partly compensates for the
                # independence assumption over-counting them.
                model = make_pipeline(
                    TfidfVectorizer(sublinear_tf=True, min_df=2),
                    MultinomialNB(alpha=0.1),
                ).fit(train_texts, train_labels)

                print(model.score(test_texts, test_labels))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why smoothing is not optional",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                # A term that never appeared in class c during training.
                count, class_total, vocab = 0, 10_000, 50_000

                unsmoothed = count / class_total                       # 0.0
                # The document score is a product, so this single zero destroys everything:
                print(np.log(unsmoothed))                              # -inf

                for alpha in (1.0, 0.1, 0.01):
                    p = (count + alpha) / (class_total + alpha * vocab)
                    print(alpha, p, round(np.log(p), 2))
                # Small but finite. Note alpha=1 with a 50k vocabulary adds 50k pseudo-counts
                # to the denominator, which is heavy-handed — alpha=0.01..0.1 usually wins.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "Spam Filtering", "Paul Graham's 2002 \"A Plan for Spam\" made this the default, and it held the field for years."),
        ApplicationCard("book", 0xFF818CF8, "Topic Categorization", "News and support-ticket routing, where a strong one-pass baseline is often all that is needed."),
        ApplicationCard("chip", 0xFF10B981, "Streaming Updates", "partial_fit keeps only per-term counts, so the model updates forever in fixed memory."),
    ),
    takeaways = listOf(
        "A document is a bag of counts; position is discarded and repeats count separately.",
        "Training is counting — one pass, incrementally updatable, no hyperparameter but α.",
        "Without smoothing a single unseen term makes the whole product zero.",
        "TF-IDF breaks the generative story and usually improves accuracy anyway.",
    ),
    crossLinks = listOf(
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("bernoulli_nb", "Bernoulli Naive Bayes"),
        CrossLink("complement_nb", "Complement Naive Bayes"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
    ),
)
