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

internal val crossEntropyLossContent = TopicContent(
    topicId = "cross_entropy_loss",
    figure = Figure(
        caption = "The loss depends on one number: the probability the model happened to assign to the " +
            "class that was actually right. −ln(p) costs almost nothing near p = 1 and is unbounded as " +
            "p → 0, so the same prediction (0.659, 0.242, 0.099) costs 0.417 when it is right and " +
            "2.317 — 5.56× more — when the truth was its least-favoured class. Confidently wrong is " +
            "the expensive case, by construction.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    label = "L = −ln p",
                    points = listOf(
                        FigurePoint(0.02f, 0.978f),
                        FigurePoint(0.1f, 0.576f),
                        FigurePoint(0.242f, 0.355f),
                        FigurePoint(0.4f, 0.229f),
                        FigurePoint(0.659f, 0.104f),
                        FigurePoint(1f, 0f),
                    ),
                ),
            ),
            xLabel = "p the model gave the true class",
            yLabel = "loss",
            markers = listOf(
                FigurePoint(0.099f, 0.578f, "2.317", FigureTone.Warn),
                FigurePoint(0.659f, 0.104f, "0.417", FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Cross-entropy loss for a C-class classifier is L = −Σ_c y_c·ln(p_c), where p = softmax(z) and y is the one-hot true label. Paired with softmax specifically, this loss has a gradient with respect to the logits that reduces to one line: dL/dz = p − y — prediction minus label, nothing else. That's not a coincidence of small examples; it's an algebraic identity of the softmax-plus-cross-entropy combination, checked here two independent ways on the same numbers.",
        "Logits (2.0, 1.0, 0.1) soften into p = (0.659, 0.242, 0.099). With true class 0 — the model's own favorite — loss is 0.4170. Compute dL/dz directly as p−y, and separately compute it the long way: dL/dp (nonzero only at the true class, −1/p_true) multiplied through softmax's actual Jacobian, J_ij = p_i(δ_ij−p_j). The two results agree to a maximum difference of 0.0 (floating-point exact) — which is the entire reason this combination is used everywhere instead of computing that Jacobian at every training step.",
        "Change only which class is true, keeping the same logits: true class 2 — the one the model likes least — pushes the loss to 2.317, 5.56× higher for an identical prediction that simply happened to be pointed the wrong way. That ratio is the loss doing its job: confidently wrong should cost more than uncertain, and −ln(p) is unbounded as p→0 while a correct guess near p=0.66 costs comparatively little.",
    ),
    steps = listOf(
        StepCard(1, "Compute Logits", "Raw, unnormalized scores z, one per class.", 0xFF0EA5E9),
        StepCard(2, "Apply Softmax", "p = exp(z)/Σexp(z) — turns logits into a probability distribution.", 0xFF3B82F6),
        StepCard(3, "Take −ln(p) at the True Class", "L = −ln(p_true) — only the true class's probability matters.", 0xFF8B5CF6),
        StepCard(4, "Compute dL/dz Directly", "p − y — the clean formula.", 0xFFF59E0B),
        StepCard(5, "Verify via the Jacobian", "The same gradient through softmax's actual Jacobian: max difference 0.0.", 0xFFEC4899),
        StepCard(6, "Flip the True Class", "Same logits, true class 2 instead of 0: loss 2.317 vs 0.417 — 5.56x higher.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Cross-entropy", "L = −Σ_c y_c·ln(p_c)", "Only the true class's term is nonzero for a one-hot y."),
        FormulaEntry("Softmax", "p_c = exp(z_c)/Σ_k exp(z_k)", "Turns logits into a valid probability distribution."),
        FormulaEntry("Combined gradient", "dL/dz = p − y", "Exact, verified against the Jacobian to 0.0 difference."),
        FormulaEntry("Softmax Jacobian", "J_ij = p_i(δ_ij − p_j)", "What the gradient would require without the shortcut."),
        FormulaEntry("Confident-correct loss", "0.4170", "True class 0, the model's own highest-probability class."),
        FormulaEntry("Misclassified loss", "2.3170", "Same logits, true class 2 — 5.56x higher."),
    ),
    notationKey = listOf(
        NotationEntry("z", "the raw logits — unnormalized class scores before softmax"),
        NotationEntry("p", "the softmax probabilities — sum to 1, each in (0,1)"),
        NotationEntry("y", "the one-hot true-label vector"),
        NotationEntry("δ_ij", "the Kronecker delta — 1 if i=j, else 0"),
        NotationEntry("dL/dp", "the loss gradient with respect to the probabilities, before the softmax Jacobian is applied"),
        NotationEntry("one-hot", "a vector with a single 1 at the true class's index and 0 elsewhere"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The gradient two ways — direct and via the Jacobian",
            accentColor = 0xFF0EA5E9,
            code = """
                import math

                def softmax(z):
                    m = max(z)
                    exps = [math.exp(x - m) for x in z]
                    s = sum(exps)
                    return [e / s for e in exps]

                z = [2.0, 1.0, 0.1]
                y_idx = 0
                p = softmax(z)
                one_hot = [1.0 if i == y_idx else 0.0 for i in range(3)]

                grad_direct = [p[i] - one_hot[i] for i in range(3)]

                dLdp = [(-1.0 / p[y_idx]) if i == y_idx else 0.0 for i in range(3)]
                J = [[p[i] * ((1.0 if i == j else 0.0) - p[j]) for j in range(3)] for i in range(3)]
                grad_jacobian = [sum(dLdp[i] * J[i][j] for i in range(3)) for j in range(3)]

                print(max(abs(a - b) for a, b in zip(grad_direct, grad_jacobian)))  # 0.0
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What confident-wrong costs",
            accentColor = 0xFFEC4899,
            code = """
                z = [2.0, 1.0, 0.1]
                p = softmax(z)  # (0.659, 0.242, 0.099) -- unchanged, only the true label moves

                loss_correct = -math.log(p[0])   # true class 0: 0.4170
                loss_wrong   = -math.log(p[2])   # true class 2: 2.3170
                print(loss_wrong / loss_correct) # 5.56x -- confident wrong is punished, hard
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Standard Classifier Loss", "The default training loss for multi-class classification across vision, NLP and beyond."),
        ApplicationCard("trend", 0xFF3B82F6, "Language Model Training", "Next-token prediction is C-way classification over the vocabulary, trained with exactly this loss."),
        ApplicationCard("check", 0xFF8B5CF6, "Cheap Gradients at Scale", "The p−y identity avoids ever materializing softmax's C×C Jacobian, which matters when C is a 50k-token vocabulary."),
        ApplicationCard("help", 0xFFEC4899, "Two Classes Only", "For a single sigmoid output instead of a softmax over C classes, use Binary Cross-Entropy instead."),
    ),
    takeaways = listOf(
        "Cross-entropy loss is L = −Σ_c y_c·ln(p_c), applied after softmax turns logits into probabilities.",
        "Paired with softmax, the gradient dL/dz reduces to exactly p − y — prediction minus one-hot label.",
        "That identity is verified two ways on the same logits: direct formula and through softmax's actual Jacobian, matching to 0.0.",
        "The identity is why this combination is used everywhere — no C×C Jacobian is ever computed during training.",
        "On logits (2.0, 1.0, 0.1), true class 0 gives loss 0.417; true class 2 (the least favored) gives loss 2.317.",
        "That's a 5.56x penalty for an identical prediction pointed the wrong way — confidence in the wrong direction costs more.",
        "For two classes with a single sigmoid output rather than a softmax over C, the matching loss is Binary Cross-Entropy.",
    ),
    crossLinks = listOf(
        CrossLink("softmax", "Softmax (Output Layer)"),
        CrossLink("log_loss", "Binary Cross-Entropy"),
        CrossLink("kl_divergence", "Kullback-Leibler (KL) Divergence"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
    ),
)
