package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val votingContent = TopicContent(
    topicId = "voting",
    figure = Figure(
        caption = "Each bar is one member's P(class B) on a single row. Hard voting reads only which " +
            "side of 0.5 each bar falls on — A, A, B — and calls it A, two to one. Soft voting " +
            "averages the bars themselves: (0.49 + 0.48 + 0.95)/3 = 0.64, which is B. The third " +
            "member is nearly certain and the first two are coin-flips, and only one of the two " +
            "rules can tell the difference. This is the ordinary situation near a boundary, not a " +
            "constructed edge case — which is why soft voting is the better default, provided the " +
            "probabilities are calibrated enough to average.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("m₁", 0.49f, FigureTone.Muted),
                FigureBar("m₂", 0.48f, FigureTone.Muted),
                FigureBar("m₃", 0.95f, FigureTone.Primary),
                FigureBar("soft", 0.64f, FigureTone.Accent),
            ),
            yLabel = "P(class B)",
        ),
    ),
    whatIsIt = listOf(
        "A voting classifier combines several independently trained models with a fixed rule. Hard voting takes the majority class; soft voting averages the predicted probabilities and takes the argmax of that. Unlike bagging, the members are usually of different *kinds* — a tree, an SVM, a logistic regression — rather than the same model on different data.",
        "Soft voting is generally the better default, and the reason is that hard voting throws away the one piece of information that matters when members disagree. Three models predicting {A at 0.51, A at 0.52, B at 0.95} produce a hard vote for A, two to one, and a soft vote for B — because two near-coin-flips should not outvote a model that is nearly certain. That is not a contrived case; it is the normal situation near a decision boundary.",
        "The whole thing rests on the members making *different* mistakes. Combining three models that agree everywhere gives you the same model at triple the cost, and if the members are unequal in quality, averaging can pull a good model down toward a bad one. So diversity is the design goal — different algorithm families, different feature views, different hyperparameters — and the sanity check is that the ensemble beats its best individual member on held-out data. Soft voting also requires calibrated probabilities to mean anything, which is a real caveat given that models like naive Bayes and SVMs produce badly calibrated scores by default.",
    ),
    steps = listOf(
        StepCard(1, "Train Members Independently", "Different families, on the same data. No coordination between them.", 0xFFF59E0B),
        StepCard(2, "Check They Differ", "Compare their error sets. Identical mistakes mean nothing to gain.", 0xFF818CF8),
        StepCard(3, "Collect Predictions", "Classes for hard voting; probability vectors for soft.", 0xFF60A5FA),
        StepCard(4, "Combine", "Majority, or the argmax of the averaged probabilities.", 0xFF10B981),
        StepCard(5, "Weight if Justified", "Unequal weights help only when relative quality is known from held-out data.", 0xFF14B8A6),
        StepCard(6, "Verify It Beat the Best Member", "If it did not, the ensemble is cost without benefit.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Hard vote", "ŷ = mode{h₁(x), …, h_m(x)}", "Discards all confidence information."),
        FormulaEntry("Soft vote", "ŷ = argmax_c (1/m)Σᵢ pᵢ(c|x)", "Averages probabilities instead."),
        FormulaEntry("Weighted soft", "argmax_c Σᵢ wᵢ·pᵢ(c|x)", "Weights need held-out evidence."),
        FormulaEntry("Independent-error bound", "P(error) = Σₖ>ₘ/₂ C(m,k)εᵏ(1−ε)^(m−k)", "Condorcet — and it assumes independence."),
        FormulaEntry("Reality", "errors are correlated", "Which is why the bound is optimistic in practice."),
        FormulaEntry("Requirement", "each member better than chance", "Below that, more members make it worse."),
    ),
    notationKey = listOf(
        NotationEntry("m", "number of ensemble members"),
        NotationEntry("hard voting", "majority over predicted classes"),
        NotationEntry("soft voting", "argmax of averaged probabilities"),
        NotationEntry("diversity", "members making different mistakes"),
        NotationEntry("calibration", "whether a predicted 0.9 really happens 90% of the time"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building one, and checking it earned its keep",
            accentColor = 0xFFF59E0B,
            code = """
                from sklearn.ensemble import VotingClassifier, RandomForestClassifier
                from sklearn.linear_model import LogisticRegression
                from sklearn.svm import SVC
                from sklearn.model_selection import cross_val_score

                members = [
                    ("lr", LogisticRegression(max_iter=1000)),
                    ("rf", RandomForestClassifier(n_estimators=200)),
                    # probability=True is required for soft voting and costs an internal
                    # cross-validated Platt scaling — it is not free.
                    ("svc", SVC(probability=True)),
                ]

                ensemble = VotingClassifier(members, voting="soft")

                for name, model in members + [("VOTE", ensemble)]:
                    print(name, round(cross_val_score(model, X, y, cv=5).mean(), 4))
                # If VOTE does not beat every row above it, ship the best single member.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Diversity is the thing to measure",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                preds = {name: m.fit(X_tr, y_tr).predict(X_te) for name, m in members}

                # Disagreement rate: the fraction of rows where two members differ. Near 0
                # means they are the same model wearing different names.
                names = list(preds)
                for i in range(len(names)):
                    for j in range(i + 1, len(names)):
                        d = (preds[names[i]] != preds[names[j]]).mean()
                        print(names[i], names[j], round(d, 4))

                # More useful still: do they fail on the SAME rows?
                wrong = {n: set(np.flatnonzero(p != y_te)) for n, p in preds.items()}
                common = set.intersection(*wrong.values())
                print(len(common), "rows every member gets wrong")
                # Voting cannot fix those. It only helps where the members disagree.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFFF59E0B, "Competition Ensembles", "The standard final move: average several strong, differently-shaped models."),
        ApplicationCard("flask", 0xFF818CF8, "Second Opinions", "Diagnostic systems that combine models trained on genuinely different data sources."),
        ApplicationCard("chart", 0xFF10B981, "Cheap Robustness", "A single model's bad day is diluted, which matters more in production than a fraction of a point of accuracy."),
    ),
    takeaways = listOf(
        "Hard voting counts classes; soft voting averages probabilities and usually wins.",
        "Two hesitant members should not outvote one confident member — that is the case soft voting fixes.",
        "The benefit comes entirely from members making different mistakes; measure the disagreement.",
        "Soft voting needs calibrated probabilities, and many models do not produce them by default.",
    ),
    crossLinks = listOf(
        CrossLink("stacking", "Stacking & Blending"),
        CrossLink("bagging", "Bagging (Bootstrap Aggregating)"),
        CrossLink("isotonic_regression", "Isotonic Regression"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
