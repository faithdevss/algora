package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val missingValueImputationContent = TopicContent(
    topicId = "missing_value_imputation",
    whatIsIt = listOf(
        "Imputation fills missing values so that models which cannot accept them will run. The cheapest version is a constant — the mean of what was observed — and it does exactly what it promises: nothing crashes, no rows are lost, every downstream step works. What it also does is change the column, and the lab measures that against the complete data the holes were cut from.",
        "With 30% of a column missing completely at random, mean imputation drops its variance from 8.09 to 5.89 — a ratio of 0.728, against the 0.70 that the missing rate alone predicts, because the filled values contribute nothing to the spread. The damage is not confined to one column either: this column's correlation with another falls from 0.982 to 0.829, because 30% of the rows now carry a value that has nothing to do with their partner. Imputation attenuates every relationship the column was in, and it does so silently.",
        "Dropping the rows keeps the column honest — variance 8.44, correlation 0.982, both essentially the originals — at a cost of 60 of 200 rows. That trade is the actual decision, and it is only this clean because the values here are missing at random; when missingness depends on the value itself (income unreported because it is high) dropping rows biases the sample and no constant can repair it. One more choice the constant hides: on a skewed column the mean is 43.3 and the median 5.3, so filling with the mean inserts a value almost no real row has. Median for skewed columns, mode for categorical ones, mean only when the column is roughly symmetric — and add a was-missing indicator column, because the fact of absence is often the signal.",
    ),
    steps = listOf(
        StepCard(1, "Count and Classify", "How much is missing, and is it missing at random?", 0xFFF97316),
        StepCard(2, "Choose the Filler", "Mean, median or mode — the column's shape decides.", 0xFF3B82F6),
        StepCard(3, "Fill", "Nothing crashes; the column is now different.", 0xFF10B981),
        StepCard(4, "Measure the Damage", "Variance 8.09 → 5.89, ratio 0.728.", 0xFFEC4899),
        StepCard(5, "Check the Correlations", "0.982 → 0.829 with a partner column.", 0xFF8B5CF6),
        StepCard(6, "Keep the Indicator", "Was-missing is a feature, and often a strong one.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Mean imputation", "xᵢ = x̄ for missing i", "Estimated on observed values only."),
        FormulaEntry("Variance ratio", "≈ 1 − p", "0.70 predicted at p = 0.30; 0.728 measured."),
        FormulaEntry("Correlation attenuation", "0.982 → 0.829", "The filled rows carry no relationship."),
        FormulaEntry("Complete-case cost", "60 of 200 rows", "Unbiased under MCAR, biased otherwise."),
        FormulaEntry("Skewed column", "mean 43.3 · median 5.3", "The mean is a value nearly no row has."),
        FormulaEntry("MCAR / MAR / MNAR", "why it is missing", "Decides whether any of this is valid."),
    ),
    notationKey = listOf(
        NotationEntry("p", "the missing rate — 0.30 in the lab"),
        NotationEntry("MCAR", "missing completely at random; the only case where dropping rows is unbiased"),
        NotationEntry("MAR", "missingness explained by other observed columns; model-based imputation works"),
        NotationEntry("MNAR", "missingness depends on the missing value itself; no fill repairs it"),
        NotationEntry("indicator column", "a binary was-missing flag kept alongside the filled value"),
        NotationEntry("KNN / iterative imputation", "predicting the value from the other columns instead of a constant"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fill, and keep the fact that you filled",
            accentColor = 0xFFF97316,
            code = """
                from sklearn.impute import SimpleImputer
                from sklearn.pipeline import make_pipeline

                imputer = SimpleImputer(
                    strategy="median",        # for a skewed column; "mean" only if symmetric
                    add_indicator=True,       # keeps a was-missing column per feature
                )

                model = make_pipeline(imputer, estimator)

                # add_indicator is the cheapest win here. Missingness is frequently informative --
                # a blank income field, an unanswered survey question, a sensor that dropped out --
                # and a constant fill destroys that signal while an indicator preserves it.
                #
                # Fit inside the pipeline: an imputer fitted on the full dataset has seen the test
                # set's mean, which is the same leak as fitting a scaler on everything.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measure what the fill did before trusting it",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                import pandas as pd

                def imputation_report(df, column, filled):
                    observed = df[column].dropna()
                    return pd.Series({
                        "missing_rate":   df[column].isna().mean(),
                        "var_observed":   observed.var(),
                        "var_filled":     filled.var(),
                        "var_ratio":      filled.var() / observed.var(),
                        "corr_observed":  df.loc[observed.index, column].corr(df.loc[observed.index, "partner"]),
                        "corr_filled":    filled.corr(df["partner"]),
                    })

                # On the lab's column: var_ratio 0.728, corr 0.982 -> 0.829.
                # If the variance ratio is close to 1 - missing_rate, you are looking at pure
                # shrinkage: the model downstream will see a column with less signal than the data
                # actually contains, and no amount of tuning recovers it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFF97316, "Survey & Form Data", "Blanks are the norm, and often informative."),
        ApplicationCard("music", 0xFF3B82F6, "Sensor Streams", "Dropouts need forward-fill or interpolation, not a global mean."),
        ApplicationCard("finance", 0xFF10B981, "Reporting Gaps", "Unreported values are usually MNAR — the fill cannot fix it."),
        ApplicationCard("help", 0xFFEC4899, "What It Costs", "Variance shrinks and every correlation attenuates, silently."),
    ),
    takeaways = listOf(
        "Constant imputation makes models run; it also changes the column, measurably.",
        "At 30% missing, mean imputation took variance 8.09 → 5.89 — a 0.728 ratio against the 0.70 the rate predicts.",
        "Correlation with a partner column fell 0.982 → 0.829: the damage crosses columns.",
        "Dropping rows kept both statistics intact and cost 60 of 200 rows — that trade is the real decision.",
        "It is only a clean trade under MCAR; when missingness depends on the value, nothing repairs it.",
        "On a skewed column the mean was 43.3 and the median 5.3 — the mean fills in a value nearly no row has.",
        "Keep a was-missing indicator: absence is frequently the strongest signal in the column.",
    ),
    crossLinks = listOf(
        CrossLink("outlier_detection", "Outlier Detection"),
        CrossLink("knn", "K-Nearest Neighbors"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("min_max_normalization", "Min-Max Normalization"),
        CrossLink("random_forest", "Random Forests"),
    ),
)
