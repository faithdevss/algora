package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val xgboostContent = TopicContent(
    topicId = "xgboost",
    whatIsIt = listOf(
        "XGBoost is gradient boosting rewritten as an explicit regularized optimization problem. Plain GBM fits each new tree to the negative gradient of the loss — a first-order approximation. XGBoost takes the second-order Taylor expansion instead, so every node carries two sums: G, the gradients of the rows reaching it, and H, their second derivatives.",
        "That extra term buys a closed form. The optimal value of a leaf is −G/(H+λ) — a Newton step, exact rather than found by line search — and the gain of a candidate split follows directly from the same expansion. The objective also carries explicit penalties that plain GBM has no analogue for: λ shrinks leaf values toward zero, and γ charges a fixed toll per split, so a split whose improvement is smaller than γ has negative gain and is not taken.",
        "Its pruning is worth understanding as a design choice. XGBoost grows to `max_depth` first and prunes back afterwards, rather than stopping the moment gain goes negative — because a weak split whose children are excellent would be discarded by greedy early stopping. The rest of its reputation is systems engineering rather than statistics: a sparsity-aware split finder that learns a default direction for missing values instead of imputing them, a cache-conscious block structure, and an approximate quantile sketch so split candidates need not be found by sorting every value. Those are why it dominated tabular competitions for years, and they are also what LightGBM and CatBoost then competed on.",
    ),
    steps = listOf(
        StepCard(1, "Compute G and H per Row", "First and second derivatives of the loss at the current prediction.", 0xFFF59E0B),
        StepCard(2, "Aggregate Them per Node", "Every candidate split needs only the sums on each side.", 0xFF818CF8),
        StepCard(3, "Score the Split", "gain = ½[G_L²/(H_L+λ) + G_R²/(H_R+λ) − G²/(H+λ)] − γ.", 0xFF60A5FA),
        StepCard(4, "Grow to max_depth", "Take the best split at each node, level-wise across the tree.", 0xFF10B981),
        StepCard(5, "Prune Backwards", "Remove splits with negative gain, after growing — not during.", 0xFF14B8A6),
        StepCard(6, "Set Leaves and Shrink", "w = −G/(H+λ), then scale the whole tree by the learning rate.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "Σ l(yᵢ,ŷᵢ) + Σₖ [γT + ½λ‖w‖²]", "Loss plus explicit tree complexity."),
        FormulaEntry("Second-order expansion", "≈ Σ [gᵢf(xᵢ) + ½hᵢf(xᵢ)²] + Ω(f)", "Where the H term enters."),
        FormulaEntry("Optimal leaf", "w* = −G/(H+λ)", "Closed form — a Newton step."),
        FormulaEntry("Split gain", "½[G_L²/(H_L+λ) + G_R²/(H_R+λ) − G²/(H+λ)] − γ", "Negative means do not split."),
        FormulaEntry("γ", "fixed cost per additional leaf", "Structural regularization GBM lacks."),
        FormulaEntry("Shrinkage", "ŷ ← ŷ + η·f(x)", "Learning rate; smaller needs more trees."),
    ),
    notationKey = listOf(
        NotationEntry("G, H", "sums of first and second derivatives at a node"),
        NotationEntry("λ", "L2 penalty on leaf values"),
        NotationEntry("γ", "minimum gain required to keep a split (min_split_loss)"),
        NotationEntry("η", "learning rate / shrinkage"),
        NotationEntry("level-wise", "growing all nodes at a depth before descending"),
        NotationEntry("sparsity-aware", "learning a default branch for missing values"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The parameters that actually matter, and early stopping",
            accentColor = 0xFFF59E0B,
            code = """
                import xgboost as xgb

                model = xgb.XGBClassifier(
                    n_estimators=2000,        # set high; early stopping decides the real count
                    learning_rate=0.05,       # eta — trades against n_estimators directly
                    max_depth=6,              # level-wise, so depth controls size tightly
                    subsample=0.8,            # row sampling per tree
                    colsample_bytree=0.8,     # feature sampling per tree
                    reg_lambda=1.0,           # L2 on leaf values
                    gamma=0.0,                # min gain per split; raise it to prune harder
                    early_stopping_rounds=50,
                    eval_metric="logloss",
                )
                model.fit(X_train, y_train, eval_set=[(X_valid, y_valid)], verbose=False)
                print(model.best_iteration)   # the n_estimators you should actually report

                # Missing values need no imputation: the split finder learns which side
                # NaNs should go, per split, from the data.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The gain formula, worked",
            accentColor = 0xFF10B981,
            code = """
                def leaf(G, H, lam=1.0):
                    return -G / (H + lam)

                def gain(GL, HL, GR, HR, lam=1.0, gamma=0.5):
                    return 0.5 * (GL**2 / (HL + lam)
                                  + GR**2 / (HR + lam)
                                  - (GL + GR)**2 / (HL + HR + lam)) - gamma

                # A split that separates rows with different gradients pays for itself:
                print(round(gain(-6, 4, -1, 4), 4))       # +0.478 -> take it
                # One that does not, cannot clear gamma:
                print(round(gain(-0.6, 2, -0.4, 2), 4))   # -0.513 -> pruned

                print(round(leaf(-6, 4), 3), round(leaf(-6, 4, lam=0), 3))
                # 1.2 with lambda=1, 1.5 without — the penalty pulls leaves toward zero.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("target", 0xFFF59E0B, "Tabular Competitions", "The default winner for years, and still the model to beat on structured data."),
        ApplicationCard("finance", 0xFF818CF8, "Credit & Risk Scoring", "Strong on mixed-type tabular features, and explainable enough via SHAP to survive review."),
        ApplicationCard("search", 0xFF10B981, "Learning to Rank", "rank:pairwise and rank:ndcg objectives make it a standard search-ranking baseline."),
    ),
    takeaways = listOf(
        "Second-order gradients give a closed-form leaf value and an exact split gain.",
        "λ shrinks leaves and γ charges per split — regularization plain GBM does not have.",
        "It grows to max_depth then prunes backwards, so a weak split with strong children survives.",
        "Missing values get a learned default direction rather than imputation.",
    ),
    crossLinks = listOf(
        CrossLink("gradient_boosting", "Gradient Boosting Machines (GBM)"),
        CrossLink("lightgbm", "LightGBM"),
        CrossLink("catboost", "CatBoost"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
    ),
)
