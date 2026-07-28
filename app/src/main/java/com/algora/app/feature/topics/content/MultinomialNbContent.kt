package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val multinomialNbContent = TopicContent(
    topicId = "multinomial_nb",
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
