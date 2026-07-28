package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val complementNbContent = TopicContent(
    topicId = "complement_nb",
    whatIsIt = listOf(
        "Complement naive Bayes fixes a specific failure of multinomial NB on imbalanced data. Instead of estimating a class's parameters from that class's own documents, it estimates them from every *other* class's documents — the class's complement — and then negates, because a term common outside a class is evidence against it.",
        "Imbalance hurts multinomial NB twice, and both need fixing. The prior directly favours the majority. Less obviously, so does the likelihood: a rare class has few tokens, so its per-term estimates are noisy and its smoothing constant α|V| dominates a small denominator, systematically flattening its distribution toward uniform. Estimating from the complement means every class's parameters come from a similarly large pool, so the noise is comparable across classes. A second step — L1-normalizing the weights per class — removes the remaining magnitude advantage that a class with more training tokens would otherwise accumulate.",
        "Because the weights measure evidence *against* a class, the decision rule inverts: predict the class with the lowest complement score. Rennie et al. introduced this in 2003 alongside two related corrections (TF-IDF-style weighting and length normalization), and on skewed text benchmarks the combination substantially closes the gap to an SVM while keeping the one-pass training cost. On balanced data it offers no advantage over multinomial and is simply an unfamiliar way to write the same thing.",
    ),
    steps = listOf(
        StepCard(1, "Count the Complement", "For each class, count terms across all the *other* classes.", 0xFF14B8A6),
        StepCard(2, "Smooth and Divide", "Same Laplace form, applied to those complement counts.", 0xFF818CF8),
        StepCard(3, "Take the Negative Log", "A term frequent outside the class argues against it, so the sign flips.", 0xFF60A5FA),
        StepCard(4, "L1-Normalize per Class", "Divide by the sum of absolute weights, cancelling the majority's magnitude edge.", 0xFF10B981),
        StepCard(5, "Score and Take the Minimum", "Lowest score wins — least evidence against.", 0xFFF59E0B),
        StepCard(6, "Check It Is Actually Needed", "On balanced data it matches multinomial. The gain is specifically from skew.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Complement count", "Ñₜ,c = Σ_{c′≠c} Nₜ,c′", "Every class except this one."),
        FormulaEntry("Weight", "wₜ,c = −log((Ñₜ,c + α) / (Ñc + α|V|))", "Negated: evidence against."),
        FormulaEntry("Normalize", "wₜ,c ← wₜ,c / Σₜ|wₜ,c|", "The step that removes the size advantage."),
        FormulaEntry("Decision", "argmin_c Σₜ count(t)·wₜ,c", "Minimum, not maximum."),
        FormulaEntry("Balanced case", "≈ multinomial NB", "The correction only bites under skew."),
        FormulaEntry("Cost", "O(total tokens)", "Still one pass."),
    ),
    notationKey = listOf(
        NotationEntry("Ñₜ,c", "count of term t outside class c"),
        NotationEntry("complement", "all classes other than c"),
        NotationEntry("class imbalance", "very unequal numbers of training documents per class"),
        NotationEntry("weight normalization", "the L1 rescaling per class"),
        NotationEntry("argmin", "the inverted decision rule"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Where the correction shows up",
            accentColor = 0xFF14B8A6,
            code = """
                from sklearn.naive_bayes import MultinomialNB, ComplementNB
                from sklearn.metrics import f1_score, classification_report

                for name, model in (("multinomial", MultinomialNB()),
                                    ("complement", ComplementNB())):
                    fit = model.fit(X_train, y_train)
                    pred = fit.predict(X_test)
                    # Macro-averaged F1 weights every class equally, so it exposes what
                    # accuracy hides: a model that has quietly stopped predicting the
                    # minority class at all still scores well on accuracy.
                    print(name, round(f1_score(y_test, pred, average="macro"), 4))

                print(classification_report(y_test, MultinomialNB().fit(X_train, y_train)
                                            .predict(X_test)))
                # Look at recall on the rare classes — that is the row CNB moves.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The two ways imbalance biases multinomial NB",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                # 1. The prior. 8:2 imbalance is a fixed log-odds head start.
                print(np.log(0.8) - np.log(0.2))          # ~1.386, before any evidence

                # 2. The likelihood, which is the less obvious one. Smoothing adds
                #    alpha * |V| to the denominator. On a small class that dominates.
                vocab, alpha = 50_000, 1.0
                for class_tokens in (200, 2_000, 200_000):
                    count = 20
                    p = (count + alpha) / (class_tokens + alpha * vocab)
                    print(class_tokens, f"{p:.3e}")
                # The rare class's estimates are pulled toward uniform far harder than the
                # majority's — its real signal is smoothed away. Estimating from the
                # complement gives every class a similarly large pool to divide by.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF14B8A6, "Skewed Text Corpora", "Support tickets and news categories where a few classes hold most of the documents."),
        ApplicationCard("search", 0xFF818CF8, "Rare-Class Detection", "Fraud, abuse and defect reports, where the class you care about is the small one."),
        ApplicationCard("history", 0xFF10B981, "Rennie et al. 2003", "\"Tackling the Poor Assumptions of Naive Bayes Text Classifiers\" — this plus two related corrections."),
    ),
    takeaways = listOf(
        "Parameters are estimated from every other class, then negated — the weights measure evidence against.",
        "Imbalance biases multinomial NB through both the prior and the smoothed likelihood; CNB addresses both.",
        "L1-normalizing weights per class removes the majority's remaining magnitude advantage.",
        "The decision rule is argmin, and on balanced data there is no advantage over multinomial.",
    ),
    crossLinks = listOf(
        CrossLink("multinomial_nb", "Multinomial Naive Bayes"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
    ),
)
