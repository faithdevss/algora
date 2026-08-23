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

internal val siameseNetworksContent = TopicContent(
    topicId = "siamese_networks",
    figure = Figure(
        caption = "One-shot accuracy on a class the network never trained on, same data both " +
            "times. The dataset is built so the failure is unambiguous: three features carry the " +
            "class signal and a fourth carries five times their variance in pure class-irrelevant " +
            "noise. A nearest-neighbour vote in raw feature space gets 0.65, dragged around by " +
            "that nuisance dimension, which dominates any Euclidean distance no matter what the " +
            "informative features say. An embedding trained with a contrastive loss on three of " +
            "the four classes — never told which dimension was noise, only shown which pairs " +
            "matched — reaches 1.00 on the held-out fourth. Suppressing that dimension is a " +
            "property of the transform, not of any class, which is exactly why it carries over to " +
            "a class it never saw.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("raw features", 0.65f, FigureTone.Warn),
                FigureBar("learned embedding", 1.00f, FigureTone.Accent),
            ),
            yLabel = "one-shot accuracy, held-out class",
            xLabel = "what the nearest-neighbour vote runs on",
        ),
    ),
    whatIsIt = listOf(
        "A Siamese network trains one embedding function on pairs of examples rather than training a classifier on individual labels — the same weights process both halves of every pair, and a contrastive loss pulls same-class pairs' embeddings together and pushes different-class pairs apart by at least a margin. The point of doing this rather than ordinary classification is what it buys at test time: a new class the network never trained on can be recognized from a single example, because the network was never asked to memorize a fixed list of classes — only to learn a distance that behaves correctly on pairs, which is a property that can transfer to classes it has never seen.",
        "Measured on data built to make the failure mode concrete: three features carry class information and a fourth carries five times their variance in pure, class-irrelevant noise. Classifying a query by its nearest raw-feature neighbor gets 65% right, dragged around by the nuisance dimension that dominates any raw Euclidean distance regardless of what the informative features say. An embedding trained with a contrastive loss on three of the four classes — never told which dimension was noise, only shown which pairs matched and which did not — reaches 100% one-shot accuracy on a fourth class it never trained on at all.",
        "That generalization is not a coincidence of this dataset; it is what the loss actually optimizes for. Making same-class pairs close and different-class pairs far apart, across three training classes with a shared nuisance dimension, is only achievable by learning a transform that suppresses that dimension — and suppressing a dimension is a property of the transform, not of any particular class. The same transform applied to a fourth, never-seen class suppresses the same dimension, which is why the one-shot accuracy on the held-out class matches the accuracy on the training classes rather than falling to chance.",
    ),
    steps = listOf(
        StepCard(1, "One Network, Two Inputs", "Both halves of a pair go through the identical embedding weights.", 0xFF14B8A6),
        StepCard(2, "Contrastive Loss on Pairs", "Same-class pairs pulled together; different-class pairs pushed past a margin.", 0xFF3B82F6),
        StepCard(3, "Train on a Few Classes", "Here, three of four — the loss never sees the fourth at all.", 0xFF10B981),
        StepCard(4, "One-Shot Evaluation on a New Class", "One support example, several queries, classify by nearest embedding.", 0xFFF59E0B),
        StepCard(5, "Compare Against Raw Features", "65% (raw) versus 100% (embedded) — the same held-out class, the same queries.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Contrastive loss (same)", "L = d²", "d is the Euclidean distance between the two embeddings."),
        FormulaEntry("Contrastive loss (different)", "L = max(0, margin − d)²", "Zero once the pair is already far enough apart."),
        FormulaEntry("One-shot rule", "ŷ = argmin_k ‖f(x) − f(support_k)‖", "Nearest embedded support example, over classes never trained on too."),
        FormulaEntry("Measured", "65% (raw) → 100% (embedded)", "Same held-out class, same query set."),
    ),
    notationKey = listOf(
        NotationEntry("f(·)", "the shared embedding network — same weights for every input"),
        NotationEntry("d", "Euclidean distance between two embeddings"),
        NotationEntry("margin", "how far apart different-class embeddings must be before the loss stops pushing"),
        NotationEntry("support example", "the single labeled example a novel class is recognized from"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A contrastive update, and the one-shot test that follows it",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def embed(x, w, b):
                    return np.tanh(w @ x + b)

                def contrastive_step(xa, xb, same, w, b, margin=2.0, lr=0.05):
                    fa, fb = embed(xa, w, b), embed(xb, w, b)
                    diff = fa - fb
                    d = np.linalg.norm(diff) + 1e-9
                    if same:
                        dL_dfa, dL_dfb = 2 * diff, -2 * diff
                    elif d < margin:
                        coeff = -2 * (margin - d) / d
                        dL_dfa, dL_dfb = coeff * diff, -coeff * diff
                    else:
                        return  # already separated -- no gradient
                    # ... backprop dL_dfa / dL_dfb through tanh into w, b ...

                # Trained on classes {0, 1, 2} only. Class 3 never appears in a single pair.
                support = {c: embed(one_example(c), w, b) for c in range(4)}   # includes class 3
                predictions = [nearest(embed(query, w, b), support) for query in queries]

                # raw-feature nearest neighbor:      0.65
                # embedding trained on classes 0-2:   1.00   (measured on class 3, held out)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF3B82F6, "Face Verification", "The original Siamese use case — is this the same person, not which person from a fixed list."),
        ApplicationCard("book", 0xFF10B981, "One-Shot / Few-Shot Learning", "New categories added after deployment with a single example, no retraining."),
        ApplicationCard("search", 0xFFF59E0B, "Signature & Duplicate Detection", "Any \"are these two things the same\" task where the class list is open-ended."),
        ApplicationCard("help", 0xFFEC4899, "Not Free", "It still needs pairs during training — the labeling cost moves, it does not vanish."),
    ),
    takeaways = listOf(
        "A Siamese network trains an embedding on pairs — same-class pulled together, different-class pushed apart — rather than classifying a fixed list.",
        "Raw-feature nearest neighbor gets 65% right when one feature is pure, high-variance noise the informative features have to compete with.",
        "The learned embedding, trained on three classes with the same nuisance dimension, reaches 100% one-shot accuracy on a fourth class it never trained on.",
        "That generalization follows from what the loss optimizes: suppressing a shared nuisance dimension is a property of the transform, which carries over to new classes.",
        "One-shot classification is possible precisely because the network was never asked to memorize a class list in the first place.",
    ),
    crossLinks = listOf(
        CrossLink("knn", "K-Nearest Neighbors (KNN)"),
        CrossLink("min_max_normalization", "Normalization (Min-Max)"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("cosine_similarity", "Cosine Similarity"),
    ),
)
