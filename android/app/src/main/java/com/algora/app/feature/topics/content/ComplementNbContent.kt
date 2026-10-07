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

internal val complementNbContent = TopicContent(
    topicId = "complement_nb",
    figure = Figure(
        caption = "The page's lab: a sports test document, \"goal great\", against a training set " +
            "with 2 sports documents (6 tokens) and 8 politics documents (32 tokens). Multinomial NB " +
            "scores each class with its own counts plus its prior, and the higher score wins. Sports " +
            "rests on 6 tokens, has never seen \"great\" (smoothed to 1/12) and carries a 0.2 prior, " +
            "so it gets −5.19 against politics' −4.61, and politics wrongly wins. Complement NB " +
            "scores each class with the counts from outside it, and the lowest score wins: the " +
            "document fits the rest worst. Sports, scored with politics' 32 tokens, gets −4.38; " +
            "politics, scored with sports' 6, gets −3.58. Sports is lower, so the right answer " +
            "wins, and each estimate now rests on the large pool outside a class.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2 · 6", "8 · 32"),
                listOf("−5.19", "−4.61"),
                listOf("−4.38", "−3.58"),
            ),
            rowHeaders = listOf("docs · tokens", "multinomial, max", "complement, min"),
            colHeaders = listOf("sports", "politics"),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 0, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Complement naive Bayes fixes a specific failure of multinomial NB on imbalanced data. Instead of estimating a class's parameters from that class's own documents, it estimates them from every *other* class's documents — the class's complement — and takes their log, because a term common outside a class is evidence against it (Rennie et al. keep the sign as log θ̃ and take the minimum; scikit-learn stores −log θ̃ and takes the maximum, which is the same rule).",
        "Imbalance hurts multinomial NB twice, and both need fixing. The prior directly favours the majority. Less obviously, so does the likelihood: a rare class has few tokens, so its per-term estimates are noisy and its smoothing constant α|V| dominates a small denominator, systematically flattening its distribution toward uniform. Estimating from the complement means every class's parameters come from a similarly large pool, so the noise is comparable across classes. A second step — L1-normalizing the weights per class — corrects the weight-magnitude bias that dependent features cause under the independence assumption (Rennie et al.).",
        "Because the weights measure evidence *against* a class, the decision rule inverts: predict the class with the lowest complement score. Rennie et al. introduced this in 2003 alongside two related corrections (TF-IDF-style weighting and length normalization), and on skewed text benchmarks the combination substantially closes the gap to an SVM while keeping the one-pass training cost. On balanced data it offers no advantage over multinomial and is simply an unfamiliar way to write the same thing.",
    ),
    steps = listOf(
        StepCard(1, "Count the Complement", "For each class, count terms across all the *other* classes.", 0xFF14B8A6),
        StepCard(2, "Smooth and Divide", "Same Laplace form, applied to those complement counts.", 0xFF818CF8),
        StepCard(3, "Take the Log", "A term frequent outside the class has a high log-probability, which is evidence against the class.", 0xFF60A5FA),
        StepCard(4, "L1-Normalize per Class", "Divide by the sum of absolute weights, correcting weight-magnitude bias from dependent features.", 0xFF10B981),
        StepCard(5, "Score and Take the Minimum", "Lowest score wins — least evidence against.", 0xFFF59E0B),
        StepCard(6, "Check It Is Actually Needed", "On balanced data it matches multinomial. The gain is specifically from skew.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Complement count", "Ñₜ,c = Σ_{c′≠c} Nₜ,c′", "Every class except this one."),
        FormulaEntry("Weight", "wₜ,c = log((Ñₜ,c + α) / (Ñc + α|V|))", "High when the term is common outside c: evidence against c. (scikit-learn negates this and takes argmax.)"),
        FormulaEntry("Normalize", "wₜ,c ← wₜ,c / Σₜ|wₜ,c|", "Corrects weight-magnitude bias from dependent features."),
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
        "Parameters are estimated from every other class, so the weights measure evidence against.",
        "Imbalance biases multinomial NB through both the prior and the smoothed likelihood; CNB addresses both.",
        "L1-normalizing weights per class corrects weight-magnitude bias from dependent features.",
        "The decision rule is argmin of log weights (argmax if you negate them, as scikit-learn does), and on balanced data there is no advantage over multinomial.",
    ),
    crossLinks = listOf(
        CrossLink("multinomial_nb", "Multinomial Naive Bayes"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
    ),
)
