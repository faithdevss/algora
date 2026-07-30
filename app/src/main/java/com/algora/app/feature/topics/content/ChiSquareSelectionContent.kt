package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val chiSquareSelectionContent = TopicContent(
    topicId = "chi_square_selection",
    whatIsIt = listOf(
        "Chi-square feature selection scores each feature against the target on its own, from a contingency table: χ² = Σ (observed − expected)² / expected, where expected is what the counts would be if feature and target were independent. It needs no model, no fitting and no iteration — on the lab's five features it produces a full ranking at a cost of zero model fits, which is the entire reason it is still the default filter on very wide data.",
        "On data with a genuine univariate signal it works exactly as advertised: \"useful\" scores 275, its near-copy \"duplicate\" 171, and the three uninformative features come in under 1.5. It also cannot tell the useful feature from the copy — \"duplicate\" agrees with \"useful\" 90% of the time, adds nothing a model does not already have, and ranks second. A univariate score has no way to notice redundancy, because it never looks at two features together.",
        "The same blindness has a sharper form. On data where the label is exactly xorA ⊕ xorB — the pair determines it perfectly, with no noise — chi-square ranks \"noise\" first at 3.79 and puts the two features that *are* the signal at 2.62 and 1.98. The signal ranks below the noise. So treat it as a cheap filter for obviously dead columns, not as a decision about which features matter, and remember its input requirement: chi-square is defined on non-negative counts, so applying it to a centred or standardized column is a category error rather than a weak result.",
    ),
    steps = listOf(
        StepCard(1, "Build the Table", "Observed counts by feature value and label.", 0xFF06B6D4),
        StepCard(2, "Compute Expected", "What independence would predict, per cell.", 0xFF3B82F6),
        StepCard(3, "Sum the Discrepancy", "χ² = Σ (O − E)² / E — 275 for the useful feature.", 0xFF10B981),
        StepCard(4, "Rank", "Every feature, zero model fits.", 0xFF8B5CF6),
        StepCard(5, "Check for Redundancy", "It cannot see it: the copy ranks second.", 0xFFF59E0B),
        StepCard(6, "Check for Interactions", "On XOR data the signal ranks below the noise.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Chi-square", "χ² = Σ (O − E)² / E", "Over the cells of the contingency table."),
        FormulaEntry("Expected count", "E = row total × column total / n", "Under independence."),
        FormulaEntry("Measured ranking", "useful 275 · duplicate 171", "Noise features under 1.5."),
        FormulaEntry("XOR data", "noise 3.79 ranks first", "xorB 2.62, xorA 1.98 — the actual signal."),
        FormulaEntry("Cost", "0 model fits", "Against RFE's p − 1."),
        FormulaEntry("Requirement", "non-negative counts", "Not valid on centred or scaled columns."),
    ),
    notationKey = listOf(
        NotationEntry("O", "observed count in a cell of the contingency table"),
        NotationEntry("E", "the count independence would predict for that cell"),
        NotationEntry("filter method", "a selector that scores features without fitting a model"),
        NotationEntry("univariate", "one feature at a time — the source of both blind spots"),
        NotationEntry("redundancy", "two features carrying the same information"),
        NotationEntry("interaction", "signal that exists only in a combination of features"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Cheap, and correct only on counts",
            accentColor = 0xFF06B6D4,
            code = """
                from sklearn.feature_selection import SelectKBest, chi2, mutual_info_classif

                # chi2 requires non-negative features. Counts, one-hot columns and frequencies are
                # fine; standardized columns are not -- this raises, and that is the API being kind.
                selector = SelectKBest(chi2, k=20).fit(X_counts, y)
                print(sorted(zip(selector.scores_, feature_names), reverse=True)[:5])

                # For numeric features, or when interactions are plausible, mutual information sees
                # more -- it is still univariate, but it is not restricted to a linear-ish
                # association and it accepts continuous input:
                SelectKBest(mutual_info_classif, k=20).fit(X, y)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The XOR check — two lines that show you the blind spot",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.feature_selection import chi2

                rng = np.random.default_rng(0)
                a, b, noise = (rng.integers(0, 2, 400) for _ in range(3))
                X = np.column_stack([noise, a, b])
                y = a ^ b                      # the pair determines the label exactly

                scores, _ = chi2(X, y)
                print(scores.round(2))         # [3.79 1.98 2.62]  <- noise ranks first
                print(f"a alone: {(y == a).mean():.2f}, b alone: {(y == b).mean():.2f}")   # ~0.50

                # Each feature is individually independent of the label -- 50% agreement -- so no
                # univariate score can rank them. If interactions are plausible in your data, a
                # filter is the wrong tool and a model-based selector is the right one.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF06B6D4, "Wide Tabular Data", "Thousands of columns, ranked without fitting anything."),
        ApplicationCard("chart", 0xFF3B82F6, "Text Features", "Bag-of-words counts are exactly the input it expects."),
        ApplicationCard("finance", 0xFF10B981, "Cheap Pre-Filtering", "Cut obviously dead columns before an expensive model."),
        ApplicationCard("help", 0xFFEC4899, "What It Misses", "Redundancy and interactions — both invisible one at a time."),
    ),
    takeaways = listOf(
        "χ² = Σ (O − E)² / E over a contingency table: a ranking for zero model fits.",
        "On univariate signal it works — useful 275, duplicate 171, the rest under 1.5.",
        "It cannot see redundancy: a 90%-identical copy of the best feature ranks second.",
        "On XOR-labelled data it ranks noise first at 3.79 and the two signal features at 2.62 and 1.98.",
        "That is not a tuning problem: each XOR feature is individually independent of the label.",
        "Valid only on non-negative counts — chi-square on a standardized column is a category error.",
        "Use it as a cheap filter on wide data, and a model-based selector when interactions are plausible.",
    ),
    crossLinks = listOf(
        CrossLink("rfe", "Recursive Feature Elimination"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("pca", "Principal Component Analysis"),
        CrossLink("multinomial_nb", "Multinomial Naive Bayes"),
    ),
)
