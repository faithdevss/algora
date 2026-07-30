package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val outlierDetectionContent = TopicContent(
    topicId = "outlier_detection",
    whatIsIt = listOf(
        "The two standard rules for flagging a numeric outlier look interchangeable. The z-score rule flags a value more than three standard deviations from the mean; the IQR rule flags anything outside the quartiles by more than 1.5 interquartile ranges. On clean data they agree — on the lab's 60 clean values, both flag zero — and agreement on easy data is not evidence that two rules are equivalent.",
        "They are not, and the reason is structural: a z-score is computed from the mean and standard deviation, both of which the outliers themselves move. Outliers that arrive together therefore hide each other, each inflating σ for the rest. Adding extreme values to the lab's column until the largest z-score falls under the threshold takes 7 of them — 10.4% of the sample — and at that point the z-score rule flags zero while the IQR rule flags all 7, because the quartiles have not moved at all.",
        "There is a second, quieter failure with nothing to do with masking. A single point in a sample of n can never have |z| above (n−1)/√n, because it sits inside the mean and the σ being used to judge it. Below n = 11 that ceiling is under 3, so the usual threshold cannot fire at all — the rule is not strict on small samples, it is inert. Both failures are the same property, and the fix is to estimate the threshold from statistics the outliers are not inside: quartiles tolerate a quarter of the sample being contaminated, the median and MAD tolerate half. That number is the breakdown point, and it is what to check before trusting any outlier rule.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Rule", "z-score, IQR fences, or modified z on the MAD.", 0xFFF97316),
        StepCard(2, "Test It on Clean Data", "All three agree. This proves nothing.", 0xFF10B981),
        StepCard(3, "Add One Extreme", "All three catch it — |z| = 7.68 here.", 0xFF3B82F6),
        StepCard(4, "Add Several", "At 7 of 67 the z-score rule flags nothing.", 0xFFEC4899),
        StepCard(5, "Check the Sample Size", "|z| > 3 is unreachable below n = 11.", 0xFF8B5CF6),
        StepCard(6, "Read the Breakdown Point", "0% for mean/σ, 25% for quartiles, 50% for MAD.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Z-score rule", "|x − μ| / σ > 3", "μ and σ are both moved by the outliers."),
        FormulaEntry("IQR rule", "x < Q₁ − 1.5·IQR or x > Q₃ + 1.5·IQR", "Quartiles barely move."),
        FormulaEntry("Modified z", "0.6745·|x − median| / MAD > 3.5", "The robust version of the same idea."),
        FormulaEntry("Masking threshold", "7 of 67 = 10.4%", "Solved for, not guessed: enough outliers hide each other."),
        FormulaEntry("Small-sample ceiling", "max|z| = (n−1)/√n", "Under 3 for every n below 11."),
        FormulaEntry("Breakdown points", "0% · 25% · 50%", "mean/σ, quartiles, median/MAD."),
    ),
    notationKey = listOf(
        NotationEntry("masking", "outliers inflating σ until none of them is flagged"),
        NotationEntry("breakdown point", "the fraction of contamination an estimator survives"),
        NotationEntry("MAD", "median absolute deviation — the robust spread estimate"),
        NotationEntry("Q₁, Q₃", "the first and third quartiles, which define the fences"),
        NotationEntry("IQR", "Q₃ − Q₁"),
        NotationEntry("0.6745", "the constant making MAD comparable to σ for normal data"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Three rules, and the one to reach for by default",
            accentColor = 0xFFF97316,
            code = """
                import numpy as np

                def z_score_flags(x, threshold=3.0):
                    return np.abs(x - x.mean()) / x.std(ddof=1) > threshold

                def iqr_flags(x, factor=1.5):
                    q1, q3 = np.percentile(x, [25, 75])
                    return (x < q1 - factor * (q3 - q1)) | (x > q3 + factor * (q3 - q1))

                def modified_z_flags(x, threshold=3.5):
                    med = np.median(x)
                    mad = np.median(np.abs(x - med))
                    return 0.6745 * np.abs(x - med) / mad > threshold

                # On the lab's masked column (7 extremes in 67 values):
                #   z_score_flags        -> 0 flagged
                #   iqr_flags            -> 7 flagged
                #   modified_z_flags     -> 7 flagged
                #
                # Default to one of the bottom two. The z-score rule is the one that fails exactly
                # when there is something to find.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Two sanity checks before you trust any flag count",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np

                def z_rule_is_usable(n, threshold=3.0):
                    "A single point in n values cannot exceed (n-1)/sqrt(n) standard deviations."
                    return (n - 1) / np.sqrt(n) > threshold

                print([ (n, round((n - 1) / np.sqrt(n), 2)) for n in (5, 10, 11, 20, 60) ])
                # [(5, 1.79), (10, 2.85), (11, 3.02), (20, 4.25), (60, 7.62)]
                # Below n=11 a threshold of 3 can never fire, whatever the data looks like.

                # And: "zero outliers found" on a column you believe is dirty usually means
                # masking. Compare the two rules -- a large disagreement is the diagnosis.
                print(z_score_flags(x).sum(), iqr_flags(x).sum())
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFFF97316, "Fraud & Anomalies", "Where the outliers are the subject, not the nuisance."),
        ApplicationCard("music", 0xFF3B82F6, "Sensor Validation", "Stuck sensors produce clusters of extremes — the masking case."),
        ApplicationCard("browser", 0xFF10B981, "Data Cleaning", "Free-text numeric fields collect impossible values in bulk."),
        ApplicationCard("help", 0xFFEC4899, "Small Samples", "Below 11 values the z-score rule cannot flag anything."),
    ),
    takeaways = listOf(
        "On clean data every rule agrees, which is why clean data proves nothing about them.",
        "A single extreme value is caught by all three — its z-score here is 7.68.",
        "Outliers arriving together hide each other: at 7 of 67 values (10.4%) the z-score rule flags zero.",
        "The IQR and MAD rules flag all 7 of them, because quartiles and the median do not move.",
        "A single point in n values can never exceed (n−1)/√n σ, so below n = 11 a threshold of 3 is inert.",
        "Breakdown points: 0% for mean/σ, 25% for quartiles, 50% for median/MAD — check this first.",
        "\"No outliers found\" on data you believe is dirty is usually masking; compare two rules to see it.",
    ),
    crossLinks = listOf(
        CrossLink("z_score_standardization", "Z-Score Standardization"),
        CrossLink("isolation_forest", "Isolation Forest"),
        CrossLink("dbscan", "DBSCAN"),
        CrossLink("robust_regression", "Robust Regression (RANSAC)"),
        CrossLink("missing_value_imputation", "Missing Value Imputation"),
    ),
)
