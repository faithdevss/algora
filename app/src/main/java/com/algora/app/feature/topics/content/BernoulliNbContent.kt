package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bernoulliNbContent = TopicContent(
    topicId = "bernoulli_nb",
    whatIsIt = listOf(
        "Bernoulli naive Bayes models each feature as a binary present/absent indicator rather than a count. A term appearing five times and a term appearing once are the same input; what the model estimates is P(term appears | class).",
        "The consequence that actually distinguishes it is easy to miss: Bernoulli scores *every term in the vocabulary*, not only the ones the document contains. An absent term contributes log(1 − P(term | class)), so a word that does not appear still moves the score. Multinomial NB ignores absent terms completely. If a spam classifier learns that legitimate mail nearly always contains the recipient's name, then the *absence* of that name is evidence — and only Bernoulli can use it.",
        "That property sets where each one wins. Short documents with a small vocabulary suit Bernoulli, because absence is informative when there are few slots to fill: SMS messages, tweets, tags, checkbox-style features. Long documents suit multinomial, because repetition carries the signal and the absent-term term becomes a near-constant offset dominated by vocabulary size. Bernoulli also costs O(|V|) per prediction rather than O(document length), which matters when the vocabulary is large and the documents are not.",
    ),
    steps = listOf(
        StepCard(1, "Binarize", "Every feature becomes 0 or 1. Counts above one are discarded.", 0xFF14B8A6),
        StepCard(2, "Count Documents, Not Tokens", "P(term|class) is the fraction of that class's documents containing the term.", 0xFF818CF8),
        StepCard(3, "Smooth", "Add α, so a term present in every document of a class is not assigned probability 1.", 0xFF60A5FA),
        StepCard(4, "Score the Whole Vocabulary", "Present terms add log p; absent terms add log(1 − p). This is the difference.", 0xFF10B981),
        StepCard(5, "Add the Log Prior", "Same as any naive Bayes.", 0xFFF59E0B),
        StepCard(6, "Match the Model to the Text", "Short documents and small vocabularies favour Bernoulli; long documents favour multinomial.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Likelihood", "P(t|c) = (dfₜ,c + α) / (Nc + 2α)", "Document frequency, not term frequency."),
        FormulaEntry("Score", "log P(c) + Σₜ∈V [xₜ log p + (1−xₜ) log(1−p)]", "Sums over ALL of V."),
        FormulaEntry("Absent term", "log(1 − P(t|c))", "The term multinomial has no analogue for."),
        FormulaEntry("Prediction cost", "O(|V|)", "Independent of document length."),
        FormulaEntry("Smoothing denominator", "+2α", "Two outcomes per feature, not |V|."),
        FormulaEntry("Feature type", "xₜ ∈ {0,1}", "Binary indicators throughout."),
    ),
    notationKey = listOf(
        NotationEntry("dfₜ,c", "number of class-c documents containing term t"),
        NotationEntry("Nc", "number of documents in class c"),
        NotationEntry("xₜ", "1 if term t is present, 0 otherwise"),
        NotationEntry("binarize", "map any positive count to 1"),
        NotationEntry("negative evidence", "the contribution of a term that is absent"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Comparing the two on the same corpus",
            accentColor = 0xFF14B8A6,
            code = """
                from sklearn.feature_extraction.text import CountVectorizer
                from sklearn.naive_bayes import BernoulliNB, MultinomialNB
                from sklearn.pipeline import make_pipeline
                from sklearn.model_selection import cross_val_score

                # binary=True is the modelling choice, not an optimization: it discards how
                # often each term occurred and keeps only whether it did.
                bern = make_pipeline(CountVectorizer(binary=True), BernoulliNB(alpha=1.0))
                mult = make_pipeline(CountVectorizer(), MultinomialNB(alpha=0.1))

                for name, model in (("bernoulli", bern), ("multinomial", mult)):
                    print(name, cross_val_score(model, texts, labels, cv=5).mean())
                # Expect Bernoulli ahead on short texts (SMS, tweets, titles) and behind on
                # long ones, where the absent-term sum swamps the present-term signal.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measuring what absence contributes",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                # feature_log_prob_ is log P(term present | class); the absent-term
                # contribution is log(1 - exp(that)), which sklearn folds into the intercept.
                fit = BernoulliNB().fit(X_binary, y)
                log_present = fit.feature_log_prob_
                log_absent = np.log(1 - np.exp(log_present))

                # For each term, how much its ABSENCE separates the two classes:
                absent_signal = log_absent[1] - log_absent[0]
                top = np.argsort(-np.abs(absent_signal))[:10]
                for i in top:
                    print(vocab[i], round(absent_signal[i], 3))
                # These are terms whose non-appearance is informative. Multinomial NB gives
                # every one of them a contribution of exactly zero.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "SMS & Short Text", "Messages too short for repetition to mean much, where absence carries real information."),
        ApplicationCard("search", 0xFF818CF8, "Binary Feature Sets", "Tags, permissions, checkbox questionnaires — data that is natively present/absent."),
        ApplicationCard("chip", 0xFF10B981, "Fixed-Cost Inference", "O(|V|) per prediction regardless of input length, which is predictable in a way multinomial is not."),
    ),
    takeaways = listOf(
        "Features are binary indicators; repetition is discarded entirely.",
        "It scores the whole vocabulary, so an absent term contributes log(1 − p) — multinomial contributes nothing.",
        "Short documents with small vocabularies favour Bernoulli; long documents favour multinomial.",
        "Estimates come from document frequency, not token frequency, and smoothing adds 2α rather than α|V|.",
    ),
    crossLinks = listOf(
        CrossLink("multinomial_nb", "Multinomial Naive Bayes"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("categorical_nb", "Categorical Naive Bayes"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
    ),
)
