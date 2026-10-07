package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val nuSvcContent = TopicContent(
    topicId = "nu_svc",
    figure = Figure(
        caption = "ν reads like a free choice in (0,1], and on balanced data it is. The curve is the " +
            "feasibility cap 2·min(n₊,n₋)/n as the positive class goes from 5% to 95% of the sample: " +
            "at a 50/50 split the whole range is available, at 90/10 everything above ν = 0.2 has no " +
            "solution at all, and at 95/5 the ceiling is 0.1. The cap collapses exactly where the " +
            "parameter is most tempting — skewed data is where you would want to assert a 30% noise " +
            "rate, and that is precisely the fit that will not converge.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "max feasible ν",
                    listOf(
                        FigurePoint(0.05f, 0.10f), FigurePoint(0.10f, 0.20f),
                        FigurePoint(0.20f, 0.40f), FigurePoint(0.30f, 0.60f),
                        FigurePoint(0.40f, 0.80f), FigurePoint(0.50f, 1.00f),
                        FigurePoint(0.60f, 0.80f), FigurePoint(0.70f, 0.60f),
                        FigurePoint(0.80f, 0.40f), FigurePoint(0.90f, 0.20f),
                        FigurePoint(0.95f, 0.10f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.50f, 1.00f, "all of (0,1]"),
                FigurePoint(0.10f, 0.20f, "ν ≤ 0.2", FigureTone.Warn),
            ),
            xLabel = "positive-class fraction",
            yLabel = "ν ceiling",
        ),
    ),
    whatIsIt = listOf(
        "The standard SVM's C is an awkward hyperparameter: it runs from 0 to infinity, its useful range shifts with the data's scale and size, and no value of it corresponds to any quantity you care about. You find it by grid search because there is nothing else to do.",
        "ν-SVC reparameterizes the same problem so the knob means something. ν lives in (0,1] and satisfies a two-sided bound: the fraction of margin errors is at most ν, and the fraction of support vectors is at least ν. Set ν = 0.1 and you have asserted that no more than 10% of training points may violate the margin, and at least 10% will be support vectors. That is a specification, not a search result.",
        "The two formulations solve equivalent problems — for every ν there is a C giving the identical classifier — so the choice is about which parameter you would rather reason about, not about accuracy. ν is the better interface when you have a prior on the noise level, and it also makes the model's sparsity directly controllable, since the support-vector fraction is bounded below. Two cautions: the bounds hold exactly at the optimum (what is asymptotic is that both fractions converge to ν), and not every ν is feasible — values above roughly 2·min(n₊,n₋)/n produce no solution at all on imbalanced data.",
    ),
    steps = listOf(
        StepCard(1, "Pick ν, Not C", "A fraction in (0,1] rather than an unbounded penalty with no units.", 0xFF8B5CF6),
        StepCard(2, "Introduce ρ", "The margin width becomes a variable to be maximized, not a fixed 1.", 0xFF818CF8),
        StepCard(3, "Trade Margin Against Errors", "The objective pays −νρ, so a wider margin is worth tolerating more violations.", 0xFF60A5FA),
        StepCard(4, "Get the Two-Sided Bound", "At the optimum, margin errors ≤ ν ≤ support-vector fraction. Both are checkable.", 0xFF10B981),
        StepCard(5, "Check Feasibility", "ν above ~2·min(n₊,n₋)/n has no solution. Imbalanced data limits the usable range.", 0xFFF59E0B),
        StepCard(6, "Convert if Needed", "Every ν corresponds to some C. The classifiers are identical; only the interface differs.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min ½‖w‖² − νρ + (1/n)Σξᵢ", "ρ is the margin, now optimized."),
        FormulaEntry("Constraints", "yᵢ(wᵀxᵢ + b) ≥ ρ − ξᵢ, ξᵢ ≥ 0, ρ ≥ 0", "Violations measured against ρ, not 1."),
        FormulaEntry("Upper bound", "margin errors / n ≤ ν", "At most this fraction violates the margin."),
        FormulaEntry("Lower bound", "support vectors / n ≥ ν", "At least this fraction are support vectors."),
        FormulaEntry("Dual box", "0 ≤ αᵢ ≤ 1/n, Σαᵢ ≥ ν", "C's box becomes 1/n with a ν floor."),
        FormulaEntry("Feasibility", "ν ≤ 2·min(n₊,n₋)/n", "Beyond this there is no solution."),
    ),
    notationKey = listOf(
        NotationEntry("ν", "the fraction parameter, in (0,1]"),
        NotationEntry("ρ", "margin width, a variable rather than a constant"),
        NotationEntry("ξᵢ", "slack — how far point i intrudes into the margin"),
        NotationEntry("margin error", "a point with ξᵢ > 0, whether or not it is misclassified"),
        NotationEntry("one-class SVM", "the ν formulation applied to novelty detection"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Checking the bound empirically",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.svm import NuSVC
                import numpy as np

                for nu in (0.05, 0.1, 0.3, 0.5):
                    fit = NuSVC(nu=nu, kernel="linear").fit(X, y)
                    n = len(y)
                    sv_fraction = fit.support_.size / n
                    margins = y_signed * fit.decision_function(X)
                    error_fraction = (margins < 1).mean()
                    print(f"nu={nu}  errors={error_fraction:.3f}  sv={sv_fraction:.3f}")
                    # errors <= nu <= sv holds exactly at the optimum; any tiny excess is solver tolerance
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The same idea, applied to novelty detection",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.svm import OneClassSVM

                # OneClassSVM is the nu formulation with no labels at all: it finds a region
                # containing most of the data, and nu directly specifies "most". Here at most
                # 5% of the training data is allowed to fall outside the learned region, which
                # doubles as the expected false-positive rate on clean data.
                detector = OneClassSVM(nu=0.05, kernel="rbf", gamma="scale").fit(X_normal)

                outside = (detector.predict(X_normal) == -1).mean()
                print(outside)      # lands near 0.05 by construction

                # Try specifying that with C. There is no way to.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DecisionSurface,
    applications = listOf(
        ApplicationCard("search", 0xFF8B5CF6, "Novelty Detection", "One-class SVM inherits ν directly, so the expected false-positive rate is set rather than discovered."),
        ApplicationCard("flask", 0xFF818CF8, "Known Noise Rates", "If a labelling process is about 10% unreliable, ν = 0.1 encodes that prior directly."),
        ApplicationCard("chip", 0xFF10B981, "Bounded Model Size", "The support-vector fraction is bounded below, so inference cost is predictable in advance."),
    ),
    takeaways = listOf(
        "ν-SVC and C-SVC solve equivalent problems — the difference is that ν means something.",
        "ν upper-bounds the margin-error fraction and lower-bounds the support-vector fraction, simultaneously.",
        "The bounds hold exactly; asymptotically both fractions approach ν.",
        "Not every ν is feasible: imbalanced classes cap the usable range at about 2·min(n₊,n₋)/n.",
    ),
    crossLinks = listOf(
        CrossLink("svm", "Support Vector Machines (Linear)"),
        CrossLink("svm_rbf", "SVM (Radial Basis Function)"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("dbscan", "DBSCAN"),
    ),
)
