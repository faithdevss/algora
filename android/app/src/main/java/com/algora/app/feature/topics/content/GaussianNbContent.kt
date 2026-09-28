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

internal val gaussianNbContent = TopicContent(
    topicId = "gaussian_nb",
    figure = Figure(
        caption = "The entire fitted model on the lab's 80 points: one Gaussian per feature per " +
            "class, which is what the name promises and all there is. Class −1 sits at " +
            "(−0.986, −0.806) with variances 0.183 and 0.187; class +1 at (1.054, 1.043) with " +
            "0.647 and 0.644. Eight numbers — 2pK, four means and four variances — against QDA's " +
            "ten on the same data. Within each class the two curves nearly coincide, so the " +
            "picture looks like two bells rather than four, and that is the point: the quantity " +
            "that separates this data is not on it. The positive class's true correlation is " +
            "0.943, and QDA fits it as an off-diagonal 0.609; naive Bayes has no parameter that " +
            "could hold it, so it is silently zero. On this sample both models misclassify none " +
            "of the 80 — the model is wrong and the predictions are not.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "x | class −1",
                    listOf(
                        FigurePoint(0.053f, 0.011f), FigurePoint(0.083f, 0.037f),
                        FigurePoint(0.112f, 0.101f), FigurePoint(0.142f, 0.230f),
                        FigurePoint(0.171f, 0.438f), FigurePoint(0.201f, 0.693f),
                        FigurePoint(0.231f, 0.912f), FigurePoint(0.260f, 1.000f),
                        FigurePoint(0.290f, 0.912f), FigurePoint(0.319f, 0.693f),
                        FigurePoint(0.349f, 0.438f), FigurePoint(0.379f, 0.230f),
                        FigurePoint(0.408f, 0.101f), FigurePoint(0.438f, 0.037f),
                        FigurePoint(0.467f, 0.011f),
                    ),
                    tone = FigureTone.Primary,
                ),
                FigureSeries(
                    "y | class −1",
                    listOf(
                        FigurePoint(0.080f, 0.011f), FigurePoint(0.110f, 0.036f),
                        FigurePoint(0.140f, 0.100f), FigurePoint(0.170f, 0.228f),
                        FigurePoint(0.200f, 0.433f), FigurePoint(0.230f, 0.686f),
                        FigurePoint(0.259f, 0.903f), FigurePoint(0.289f, 0.990f),
                        FigurePoint(0.319f, 0.903f), FigurePoint(0.349f, 0.686f),
                        FigurePoint(0.379f, 0.433f), FigurePoint(0.409f, 0.228f),
                        FigurePoint(0.439f, 0.100f), FigurePoint(0.469f, 0.036f),
                        FigurePoint(0.499f, 0.011f),
                    ),
                    tone = FigureTone.Primary,
                    dashed = true,
                ),
                FigureSeries(
                    "x | class +1",
                    listOf(
                        FigurePoint(0.200f, 0.006f), FigurePoint(0.256f, 0.020f),
                        FigurePoint(0.311f, 0.054f), FigurePoint(0.367f, 0.122f),
                        FigurePoint(0.423f, 0.233f), FigurePoint(0.478f, 0.369f),
                        FigurePoint(0.534f, 0.486f), FigurePoint(0.589f, 0.532f),
                        FigurePoint(0.645f, 0.486f), FigurePoint(0.701f, 0.369f),
                        FigurePoint(0.756f, 0.233f), FigurePoint(0.812f, 0.122f),
                        FigurePoint(0.867f, 0.054f), FigurePoint(0.923f, 0.020f),
                        FigurePoint(0.979f, 0.006f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "y | class +1",
                    listOf(
                        FigurePoint(0.199f, 0.006f), FigurePoint(0.255f, 0.020f),
                        FigurePoint(0.310f, 0.054f), FigurePoint(0.366f, 0.123f),
                        FigurePoint(0.421f, 0.233f), FigurePoint(0.477f, 0.369f),
                        FigurePoint(0.532f, 0.487f), FigurePoint(0.588f, 0.534f),
                        FigurePoint(0.643f, 0.487f), FigurePoint(0.699f, 0.369f),
                        FigurePoint(0.754f, 0.233f), FigurePoint(0.810f, 0.123f),
                        FigurePoint(0.865f, 0.054f), FigurePoint(0.921f, 0.020f),
                        FigurePoint(0.976f, 0.006f),
                    ),
                    tone = FigureTone.Accent,
                    dashed = true,
                ),
            ),
            xLabel = "feature value, −2.6 → 3.6",
            yLabel = "class-conditional density",
        ),
    ),
    whatIsIt = listOf(
        "Gaussian naive Bayes handles continuous features by assuming each one is normally distributed within each class. Training reduces to computing a mean and a variance per feature per class — two passes over the data, no optimization, no learning rate, no iterations.",
        "Placed beside QDA the relationship is exact: Gaussian NB *is* QDA with the off-diagonal covariance entries forced to zero. \"Features are conditionally independent given the class\" and \"the covariance matrix is diagonal\" are the same statement. That is why the simulation on this page shows ellipses locked to the axes — there is no parameter that could tilt them, however obviously tilted the data is.",
        "The parameter count is what makes it worth the wrong assumption. QDA needs p(p+1)/2 covariance entries per class; Gaussian NB needs p. At p = 100 that is 5,050 against 100, so with limited data the naive model is often the *more accurate* one despite being the less correct one — a clean case of variance mattering more than bias. Its known weakness is calibration: because dependent features get counted as independent evidence, the same signal is multiplied in repeatedly and posteriors are pushed toward 0 or 1. The ranking stays good, which is why it is a fine classifier and a poor probability estimator.",
    ),
    steps = listOf(
        StepCard(1, "Split by Class", "Group the training rows by their label. Nothing else is shared between classes.", 0xFF14B8A6),
        StepCard(2, "Mean and Variance per Feature", "Two numbers per feature per class — the whole model.", 0xFF818CF8),
        StepCard(3, "Add var_smoothing", "A small constant on every variance, so a near-constant feature cannot divide by zero.", 0xFF60A5FA),
        StepCard(4, "Score in Log Space", "Sum log priors and log densities. Products of small numbers underflow.", 0xFF10B981),
        StepCard(5, "Take the Argmax", "Highest total wins. Normalize only if you need a probability out.", 0xFFF59E0B),
        StepCard(6, "Distrust That Probability", "Ranking is reliable; the number attached to it is not. Calibrate before using it as one.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Likelihood", "P(xⱼ|y) = (1/√(2πσ²ⱼ,y))·exp(−(xⱼ−μⱼ,y)²/2σ²ⱼ,y)", "One Gaussian per feature per class."),
        FormulaEntry("Posterior", "log P(y|x) ∝ log P(y) + Σⱼ log P(xⱼ|y)", "Sum, because of the independence assumption."),
        FormulaEntry("Parameters", "2·p·K", "Against QDA's K·p(p+1)/2."),
        FormulaEntry("Relation to QDA", "Σₖ diagonal ⟺ Gaussian NB", "Same model, one constraint added."),
        FormulaEntry("var_smoothing", "σ² ← σ² + ε·max(σ²)", "Guards against zero-variance features."),
        FormulaEntry("Training cost", "O(np)", "One pass. No iteration at all."),
    ),
    notationKey = listOf(
        NotationEntry("μⱼ,y", "mean of feature j within class y"),
        NotationEntry("σ²ⱼ,y", "variance of feature j within class y"),
        NotationEntry("conditional independence", "features independent *given the class*, not marginally"),
        NotationEntry("var_smoothing", "additive variance floor"),
        NotationEntry("calibration", "whether predicted probabilities match observed frequencies"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole model in two lines of statistics",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def fit(X, y):
                    classes = np.unique(y)
                    # That is it. No optimizer, no epochs, no learning rate.
                    means = np.stack([X[y == c].mean(axis=0) for c in classes])
                    vars_ = np.stack([X[y == c].var(axis=0) for c in classes]) + 1e-9
                    priors = np.array([(y == c).mean() for c in classes])
                    return classes, means, vars_, priors

                def predict(X, classes, means, vars_, priors):
                    # Log space throughout: a product of 100 densities underflows to 0.0.
                    log_like = -0.5 * (
                        np.log(2 * np.pi * vars_)[None] +
                        (X[:, None, :] - means[None]) ** 2 / vars_[None]
                    ).sum(axis=2)
                    return classes[(log_like + np.log(priors)).argmax(axis=1)]
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Good ranking, bad probabilities",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.naive_bayes import GaussianNB
                from sklearn.calibration import CalibratedClassifierCV
                from sklearn.metrics import roc_auc_score, brier_score_loss

                raw = GaussianNB().fit(X_train, y_train)
                p = raw.predict_proba(X_test)[:, 1]
                print(roc_auc_score(y_test, p))        # often strong — ranking is fine
                print(brier_score_loss(y_test, p))     # poor — the numbers are not honest
                print((p > 0.99).mean() + (p < 0.01).mean())   # most mass piles at the ends

                # Correlated features are counted as independent evidence, so the same signal
                # gets multiplied in repeatedly. Isotonic or Platt calibration fixes the
                # numbers without changing the ranking at all — AUC is unmoved.
                cal = CalibratedClassifierCV(GaussianNB(), method="isotonic", cv=5)
                cal.fit(X_train, y_train)
                print(brier_score_loss(y_test, cal.predict_proba(X_test)[:, 1]))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DecisionSurface,
    applications = listOf(
        ApplicationCard("flask", 0xFF14B8A6, "Baselines", "Trains in one pass, so it is the sane first number to beat before anything expensive is attempted."),
        ApplicationCard("chip", 0xFF818CF8, "Streaming & Embedded", "partial_fit updates the sufficient statistics incrementally, in constant memory."),
        ApplicationCard("chart", 0xFF10B981, "High-Dimensional, Small-n", "When p ≫ n, QDA's covariance is unusable and the naive assumption is what keeps the model estimable."),
    ),
    takeaways = listOf(
        "Gaussian NB is exactly QDA with a diagonal covariance — the independence assumption is that constraint.",
        "Training is a mean and a variance per feature per class: one pass, no optimization.",
        "It often beats QDA despite the wrong assumption, because 2pK parameters are estimable where K·p²/2 are not.",
        "Ranking is reliable but posteriors are badly calibrated; calibrate before treating them as probabilities.",
    ),
    crossLinks = listOf(
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("qda", "Quadratic Discriminant Analysis (QDA)"),
        CrossLink("lda", "Linear Discriminant Analysis (LDA)"),
        CrossLink("isotonic_regression", "Isotonic Regression"),
    ),
)
