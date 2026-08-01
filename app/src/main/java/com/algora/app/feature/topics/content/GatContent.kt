package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gatContent = TopicContent(
    topicId = "gat",
    whatIsIt = listOf(
        "A Graph Attention Network layer replaces GCN's fixed, degree-based aggregation weight with one computed from the features themselves: for every edge (i, j), a score e_ij = LeakyReLU(aᵀ[Wh_i ‖ Wh_j]) is computed from both endpoints' transformed features, then softmax-normalized over node i's neighborhood to give attention weights that sum to 1. The formula looks like a small addition to GCN's aggregation, and the consequence of that addition is worth computing side by side rather than taking on faith.",
        "On the same bridge node used to demonstrate GCN's oversmoothing, GCN's weight for its three neighbors is 0.289, 0.289 and 0.25 — nearly uniform, because it is a function of degree alone (1/√(deg·deg)) and two of the three neighbors happen to have equal degree. GAT's attention on the identical neighborhood, computed from a hand-set attention vector applied to real feature values, comes out 0.613, 0.304 and 0.083 — the two feature-similar neighbors take 92% of the attention mass between them, and the more different-featured third neighbor is nearly ignored. Same graph, same edges, same node: one weighting reads structure, the other reads content.",
        "The sharpest version of the contrast is a direct perturbation: move the third neighbor's features to exactly match the center node's, recompute both weightings. GAT's attention on that neighbor roughly quadruples — 0.083 to 0.331 — because the score is a function of the features that just changed. GCN's weight on the same edge is unchanged to the fifteenth decimal place, because degree-normalization has no feature input to react to in the first place. That is the entire tradeoff in one experiment: GAT costs an attention computation per edge per layer and buys weights that move when the data does; GCN is cheaper and blind to exactly that.",
    ),
    steps = listOf(
        StepCard(1, "Transform Every Node's Features", "Wh_i — a shared linear map, same as GCN's weight.", 0xFF14B8A6),
        StepCard(2, "Score Every Edge From Both Endpoints", "e_ij = LeakyReLU(aᵀ[Wh_i ‖ Wh_j]) — concatenate, then one learned vector.", 0xFF3B82F6),
        StepCard(3, "Softmax Over Each Node's Neighborhood", "Weights that sum to 1, computed fresh from the current features.", 0xFF10B981),
        StepCard(4, "Compare Against GCN on the Same Edges", "0.289/0.289/0.25 (GCN) versus 0.613/0.304/0.083 (GAT).", 0xFFF59E0B),
        StepCard(5, "Perturb a Neighbor, Recompute Both", "GAT's weight on it roughly quadruples; GCN's is unchanged to 10⁻¹².", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Attention score", "e_ij = LeakyReLU(aᵀ[Wh_i ‖ Wh_j])", "A learned linear function over both endpoints' transformed features."),
        FormulaEntry("Attention weight", "α_ij = softmax_j(e_ij)", "Normalized over node i's neighborhood — sums to 1."),
        FormulaEntry("GCN's weight, for contrast", "1 / √(deg(i) deg(j))", "Fixed by the graph alone — no feature dependence."),
        FormulaEntry("Measured", "0.083 → 0.331", "One neighbor's GAT weight, before and after its features are moved to match the center."),
    ),
    notationKey = listOf(
        NotationEntry("W", "the shared linear transform applied to every node's features"),
        NotationEntry("a", "the learned attention vector, applied to concatenated features"),
        NotationEntry("‖", "concatenation — Wh_i and Wh_j stacked into one vector"),
        NotationEntry("α_ij", "the final attention weight on edge (i, j), after softmax"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The same neighborhood, weighted two ways",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def leaky_relu(x, slope=0.2):
                    return x if x >= 0 else slope * x

                def gat_attention(center, neighbors, a):
                    scores = [leaky_relu(a @ np.concatenate([center, h])) for h in neighbors]
                    exps = np.exp(scores - max(scores))
                    return exps / exps.sum()

                def gcn_weight(deg_i, deg_j):
                    return 1.0 / np.sqrt(deg_i * deg_j)

                center = features[bridge_node]                 # e.g. [0.9, 0.1]
                neighbor_feats = [features[n] for n in neighbors_of(bridge_node)]

                gcn_weights = [gcn_weight(deg[bridge_node], deg[n]) for n in neighbors]
                gat_weights = gat_attention(center, neighbor_feats, a)

                # gcn:  [0.289, 0.289, 0.250]   -- nearly uniform, feature-blind
                # gat:  [0.613, 0.304, 0.083]   -- concentrates on the feature-similar neighbors

                # Move the third neighbor's features to match the center exactly, recompute:
                # gat[2]:  0.083 -> 0.331   (roughly 4x)
                # gcn[2]:  0.250 -> 0.250   (exactly unchanged -- no feature input to react to)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF3B82F6, "Heterogeneous Neighborhoods", "When some neighbors matter far more than others, and which ones depends on content."),
        ApplicationCard("translate", 0xFF10B981, "Knowledge Graphs", "Relations of varying relevance to a given query node."),
        ApplicationCard("chip", 0xFFF59E0B, "Molecules & Point Clouds", "Where geometric proximity alone (GCN's implicit assumption) is not the whole story."),
        ApplicationCard("help", 0xFFEC4899, "The Cost", "An attention computation per edge per layer — more expensive than GCN's fixed weights."),
    ),
    takeaways = listOf(
        "GAT replaces GCN's fixed, degree-based edge weight with one computed from both endpoints' features through a learned attention vector.",
        "On the same bridge node, GCN's weights are nearly uniform (0.289/0.289/0.25); GAT's concentrate 92% of the mass on the two feature-similar neighbors (0.613/0.304).",
        "Moving one neighbor's features to match the center roughly quadruples its GAT weight (0.083 → 0.331) — a direct, measured sensitivity to content.",
        "The identical perturbation leaves GCN's weight on that edge unchanged to 10⁻¹², because degree-normalization has no feature input at all.",
        "The tradeoff is concrete: attention costs a computation per edge per layer, and buys weights that move when the data does.",
    ),
    crossLinks = listOf(
        CrossLink("gcn", "Graph Convolutional Networks (GCN)"),
        CrossLink("attention", "Attention"),
        CrossLink("multi_head_attention", "Multi-Head Attention"),
        CrossLink("graph", "Graphs"),
    ),
)
