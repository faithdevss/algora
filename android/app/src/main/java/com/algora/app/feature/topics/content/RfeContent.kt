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

internal val rfeContent = TopicContent(
    topicId = "rfe",
    figure = Figure(
        caption = "The page's lab, round by round: a linear model fitted to five 0/1 features, the " +
            "one with the smallest |w| dropped, the rest refitted. xorA goes first at 0.013 — the " +
            "XOR pair matters (together they predict y 88% of the time), but a linear model cannot " +
            "express XOR, so to it both halves look empty. The duplicate goes second at 0.030 even " +
            "though on its own it explains 44% of y: with \"useful\" still present its weight " +
            "collapses, which is redundancy a one-feature-at-a-time filter cannot see. Noise and " +
            "xorB follow at 0.045, and \"useful\" is left after five fits. Through all four " +
            "eliminations the error barely moves, 0.1143 to 0.1154 — the honest reading is that " +
            "nothing this model could use was lost, not that nothing was in the dropped columns.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("xorA", "0.013"),
                listOf("duplicate", "0.030"),
                listOf("noise", "0.045"),
                listOf("xorB", "0.045"),
                listOf("useful kept", "—"),
            ),
            rowHeaders = listOf("round 1", "round 2", "round 3", "round 4", "end"),
            colHeaders = listOf("dropped", "|w|"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(3, 0, FigureTone.Warn),
                FigureCell(1, 0, FigureTone.Primary),
                FigureCell(4, 0, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Recursive feature elimination goes the other way from a filter. Fit a model on every feature, drop the one with the weakest coefficient, refit, repeat. Because it fits a real model it sees things a univariate score cannot — redundancy in particular, since a duplicated feature has to share its coefficient with the original — and because it fits a real model p−1 times, it costs what a filter does not.",
        "On the lab's five 0/1 features it eliminates in the order xorA (|w| = 0.013), duplicate (0.030), noise (0.045) and xorB (0.045), and ends on \"useful\" alone after five fits. The MSE barely moves across the whole elimination, 0.1143 to 0.1154, which is the honest signal that those four columns were carrying nothing this model could use. Compare that with chi-square on the same data, which ranks the redundant copy second at 177.7: RFE drops it in round two even though on its own it explains 44% of y, because with the original present its coefficient collapses — the copy adds nothing the original does not already carry.",
        "What it does not do is escape the blind spot; it inherits its estimator's. In the same data xorA ⊕ xorB predicts y 88% of the time, and RFE with a linear model throws xorA out first and xorB fourth, because a linear model cannot express XOR and so gives both features a coefficient near zero. Both selectors fail on that data for different reasons: chi-square because it looks one feature at a time, RFE because its model cannot see the pattern. Swap the estimator for a tree and the result changes, which is the actual lesson: RFE reports what your model can use, not what is in the data.",
    ),
    steps = listOf(
        StepCard(1, "Fit on Everything", "Coefficients for all p features.", 0xFF8B5CF6),
        StepCard(2, "Rank by Magnitude", "Comparable only if the features are scaled.", 0xFF3B82F6),
        StepCard(3, "Drop the Weakest", "One per round — xorA first at |w| = 0.013 here.", 0xFFEC4899),
        StepCard(4, "Refit", "The remaining coefficients change; that is the point.", 0xFF10B981),
        StepCard(5, "Watch the Error", "0.1143 → 0.1154 across four eliminations.", 0xFFF59E0B),
        StepCard(6, "Choose the Estimator", "It decides what RFE is able to see.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("The loop", "fit → drop argminⱼ|βⱼ| → refit", "Until the target feature count is reached."),
        FormulaEntry("Cost", "p − 1 fits", "4 here, against chi-square's 0."),
        FormulaEntry("Elimination order", "xorA, duplicate, noise, xorB", "Ending on \"useful\" alone."),
        FormulaEntry("Error across it", "0.1143 → 0.1154", "Nothing measurable was lost."),
        FormulaEntry("Redundancy", "duplicate dropped in round 2", "Alone it explains 44% of y; chi-square ranks it second."),
        FormulaEntry("Inherited blindness", "drops the XOR pair too", "A linear model cannot express it."),
    ),
    notationKey = listOf(
        NotationEntry("βⱼ", "the fitted coefficient whose magnitude ranks feature j"),
        NotationEntry("wrapper method", "a selector that repeatedly fits a model — as opposed to a filter"),
        NotationEntry("step", "how many features are dropped per round; 1 is the careful setting"),
        NotationEntry("RFECV", "RFE with cross-validation choosing the feature count"),
        NotationEntry("estimator", "the model RFE fits — it decides what the selection can see"),
        NotationEntry("scaling", "required before comparing coefficient magnitudes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Scale first, cross-validate the count, and pick the estimator deliberately",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.ensemble import RandomForestClassifier
                from sklearn.feature_selection import RFECV
                from sklearn.linear_model import LogisticRegression
                from sklearn.pipeline import make_pipeline
                from sklearn.preprocessing import StandardScaler

                # Coefficient magnitudes are only comparable on comparable scales, so the scaler is
                # not optional -- unscaled, RFE ranks by unit rather than by importance.
                linear_rfe = make_pipeline(
                    StandardScaler(),
                    RFECV(LogisticRegression(), step=1, cv=5, scoring="roc_auc"),
                )

                # A tree-based estimator sees interactions a linear one cannot, and needs no scaling:
                tree_rfe = RFECV(RandomForestClassifier(random_state=0), step=1, cv=5)

                # RFECV chooses how many features to keep instead of making you guess, at the cost
                # of p * folds fits. On wide data, filter first and run RFE on the survivors.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Read the elimination log, not just the final mask",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.feature_selection import RFE

                selector = RFE(estimator, n_features_to_select=1, step=1).fit(X, y)
                order = sorted(zip(selector.ranking_, feature_names))
                for rank, name in order:
                    print(f"{rank:3} {name}")     # rank 1 survived; higher = dropped earlier

                # Two things worth reading here:
                #   - a feature dropped early that you expected to matter usually means either bad
                #     scaling or a model that cannot express its contribution;
                #   - an error curve that stays flat across eliminations (0.1143 -> 0.1154 in the
                #     lab) says the dropped columns were carrying nothing THIS model could use.
                #     That is not the same as "nothing was in them".
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF8B5CF6, "Model Slimming", "Fewer features means cheaper serving and simpler monitoring."),
        ApplicationCard("flask", 0xFF3B82F6, "Redundancy Removal", "Correlated duplicates lose their coefficient and get dropped."),
        ApplicationCard("chart", 0xFF10B981, "Interpretability", "A short feature list is one a domain expert can actually check."),
        ApplicationCard("help", 0xFFEC4899, "The Catch", "It reports what your estimator can use, not what the data holds."),
    ),
    takeaways = listOf(
        "Fit, drop the weakest coefficient, refit — a wrapper method costing p − 1 fits.",
        "On the lab's data it eliminates xorA, duplicate, noise and xorB, ending on \"useful\".",
        "It sees redundancy where chi-square cannot: the near-copy that explains 44% of y alone is dropped in round two.",
        "The error moved 0.1143 → 0.1154 across all four eliminations — nothing measurable was lost.",
        "It does not escape interaction blindness: with a linear model it drops the XOR pair too, though together it predicts y 88% of the time.",
        "The two selectors fail on that data for different reasons — univariate scoring versus model capacity.",
        "Scale before comparing coefficients, and choose the estimator deliberately: it defines what RFE can see.",
    ),
    crossLinks = listOf(
        CrossLink("chi_square_selection", "Chi-Square Feature Selection"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("lasso_regression", "Lasso Regression (L1)"),
        CrossLink("random_forest", "Random Forests"),
        CrossLink("z_score_standardization", "Z-Score Standardization"),
    ),
)
