package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val capsuleNetworksContent = TopicContent(
    topicId = "capsule_networks",
    whatIsIt = listOf(
        "A capsule replaces a single scalar activation with a vector, and replaces max-pooling's \"keep the strongest, discard the rest\" with routing-by-agreement: a vote that agrees with the emerging consensus gets more say in the next round, and one that disagrees gets less. Concretely, three lower-level capsules each cast a vote — a small vector — for what a higher-level digit capsule should output. Two of the three votes here point in nearly the same direction; the third points nearly opposite. Routing starts every vote at equal weight and asks, over a few iterations, which votes keep agreeing with where the group is heading.",
        "After three iterations — the number the original Capsule Networks paper uses — the two agreeing votes' routing coefficients have risen from 0.5 to 0.59 and 0.64, while the disagreeing vote's has fallen to 0.41: a real but partial correction. Run the identical process for ten iterations instead of three, and the coefficients diverge sharply — 0.994, 0.999, and 0.004 — the disagreeing vote is functionally voted out. The digit capsule's output direction moves correspondingly: a naive unweighted average of all three votes points 24.4° off the two agreeing votes' true consensus direction; three rounds of routing narrows that to 16.7°; ten rounds narrows it to 6.1°, matching the pure two-vote agreement almost exactly.",
        "That iterative reweighting is precisely what a fixed operation cannot do. Average pooling assigns every input equal weight forever, no matter how much one of them disagrees with the rest — it has no mechanism to notice agreement. Max pooling keeps a single winner and discards everything else, including partial evidence from votes that agree with each other but lose to a stronger unrelated activation. Routing-by-agreement keeps all three votes but reweights them based on a measured property of the vote itself: how well it agrees with the very output it is contributing to, recomputed fresh every round.",
    ),
    steps = listOf(
        StepCard(1, "Lower Capsules Cast Votes", "Each vote is a vector — not a scalar — for what the higher capsule should output.", 0xFF14B8A6),
        StepCard(2, "Start Every Route at Equal Weight", "Routing logits at zero — no vote is favored before any agreement is measured.", 0xFF3B82F6),
        StepCard(3, "Combine, Then Squash", "A weighted sum of the votes, squashed to keep vector length between 0 and 1.", 0xFF10B981),
        StepCard(4, "Measure Agreement, Update the Weights", "Each vote's dot product with the squashed output raises or lowers its own logit.", 0xFFF59E0B),
        StepCard(5, "Repeat — the Weights Converge to the Agreeing Votes", "3 rounds: a partial shift. 10 rounds: the disagreeing vote's weight collapses to 0.004.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Routing weight", "c_ij = softmax_j(b_ij)", "Over the higher capsules j a lower capsule i could route to."),
        FormulaEntry("Weighted sum", "s_j = Σᵢ c_ij û_{j|i}", "The candidate output before squashing."),
        FormulaEntry("Squash", "v_j = (‖s_j‖² / (1+‖s_j‖²)) · (s_j / ‖s_j‖)", "Keeps direction, bounds length to [0, 1)."),
        FormulaEntry("Agreement update", "b_ij ← b_ij + û_{j|i} · v_j", "A vote's own dot product with the output it helped produce."),
    ),
    notationKey = listOf(
        NotationEntry("û_{j|i}", "capsule i's vote for capsule j's output"),
        NotationEntry("c_ij", "the routing weight from capsule i to capsule j — sums to 1 over j"),
        NotationEntry("‖v_j‖", "the output capsule's length — read as the probability the entity is present"),
        NotationEntry("routing-by-agreement", "reweighting votes by how well each agrees with the current consensus"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Three rounds versus ten, on the same three votes",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def squash(s):
                    norm_sq = s @ s
                    return (norm_sq / (1 + norm_sq)) * (s / (np.sqrt(norm_sq) + 1e-9))

                def route(votes_a, votes_b, iterations):
                    b_a = np.zeros(len(votes_a))
                    b_b = np.zeros(len(votes_a))
                    for _ in range(iterations):
                        c_a = np.exp(b_a) / (np.exp(b_a) + np.exp(b_b))
                        c_b = 1 - c_a
                        v_a = squash(sum(c_a[i] * votes_a[i] for i in range(len(votes_a))))
                        v_b = squash(sum(c_b[i] * votes_b[i] for i in range(len(votes_b))))
                        b_a += [votes_a[i] @ v_a for i in range(len(votes_a))]
                        b_b += [votes_b[i] @ v_b for i in range(len(votes_b))]
                    return c_a, v_a

                # votes_a[0], votes_a[1] roughly agree; votes_a[2] points nearly opposite.

                c_a_3, v_a_3 = route(votes_a, votes_b, iterations=3)
                # c_a_3 = [0.59, 0.64, 0.41]   -- a partial correction

                c_a_10, v_a_10 = route(votes_a, votes_b, iterations=10)
                # c_a_10 = [0.994, 0.999, 0.004]   -- the disagreeing vote is essentially voted out

                # output direction: naive average 24.4 deg off consensus -> 16.7 deg (3 rounds)
                #                                                        -> 6.1 deg (10 rounds)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFF3B82F6, "Pose-Aware Recognition", "A vector's direction can encode orientation/pose — a scalar cannot."),
        ApplicationCard("tree", 0xFF10B981, "Part-Whole Hierarchies", "Agreement between parts' votes for a whole is the mechanism, not a side effect."),
        ApplicationCard("help", 0xFFF59E0B, "Why They Are Rare in Practice", "Routing is far more expensive per layer than a convolution or a max-pool."),
        ApplicationCard("chart", 0xFFEC4899, "The Core Contrast", "Fixed pooling cannot reweight a vote by its agreement — routing recomputes that every round."),
    ),
    takeaways = listOf(
        "A capsule outputs a vector, not a scalar, and higher capsules combine lower capsules' votes by routing-by-agreement instead of pooling.",
        "Three routing iterations (the standard count) shift weight toward two agreeing votes (0.5 → 0.59, 0.64) and away from a conflicting one (0.5 → 0.41) — a real but partial correction.",
        "Ten iterations diverge sharply: 0.994, 0.999, 0.004 — the disagreeing vote is functionally excluded.",
        "The output direction moves correspondingly: 24.4° off true consensus (naive average) → 16.7° (3 rounds) → 6.1° (10 rounds, essentially converged).",
        "That reweighting-by-agreement, recomputed every round, is exactly what a fixed pooling operation has no mechanism to do.",
    ),
    crossLinks = listOf(
        CrossLink("cnn", "CNNs"),
        CrossLink("pooling_layers", "Pooling Layers (Max/Average)"),
        CrossLink("attention", "Attention"),
        CrossLink("gat", "Graph Attention Networks (GAT)"),
    ),
)
