package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val affinityPropagationContent = TopicContent(
    topicId = "affinity_propagation",
    whatIsIt = listOf(
        "Affinity propagation picks its cluster centres from among the data points themselves, and is never told how many to find. Every point starts as a candidate exemplar, and the algorithm runs a message-passing negotiation until a stable set of exemplars emerges.",
        "Two messages circulate between every pair. Responsibility r(i,k) is point i telling candidate k how well-suited it looks *compared to i's other options* — it is a competitive signal. Availability a(i,k) is candidate k telling i how much support it has already accumulated from everyone else — a cooperative one. Each is computed from the other, alternately, with damping to stop the pair oscillating. Where they settle, r(k,k) + a(k,k) > 0 identifies the exemplars.",
        "Its distinguishing property is that exemplars are real data points rather than averages, which matters whenever a mean would be meaningless — a mean sentence, a mean molecule and a mean face are all nonsense, but a representative one is not. The cluster count is controlled indirectly through the preference value placed on the similarity diagonal: raise it and more points are willing to nominate themselves, so more clusters appear. That indirection is genuinely awkward, and it is not the main cost. The similarity matrix is O(n²) in memory and each iteration is O(n²) in time, which caps the method at a few thousand points; and without enough damping it simply fails to converge, oscillating between two candidate solutions.",
    ),
    steps = listOf(
        StepCard(1, "Build the Similarity Matrix", "s(i,k), usually negative squared distance. O(n²) memory, and that is the ceiling.", 0xFF3B82F6),
        StepCard(2, "Set the Preference", "s(k,k) on the diagonal — how willing a point is to be an exemplar. This sets the count.", 0xFF818CF8),
        StepCard(3, "Update Responsibilities", "How well k suits i, relative to i's best alternative.", 0xFF60A5FA),
        StepCard(4, "Update Availabilities", "How much support k has already gathered from other points.", 0xFF10B981),
        StepCard(5, "Damp and Repeat", "Blend each update with the previous value, or it oscillates rather than converging.", 0xFF14B8A6),
        StepCard(6, "Read Off the Exemplars", "Points where r(k,k) + a(k,k) > 0. Everyone else joins their nearest exemplar.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Similarity", "s(i,k) = −‖xᵢ − xₖ‖²", "The usual choice; any similarity works."),
        FormulaEntry("Preference", "s(k,k)", "Higher ⟹ more exemplars ⟹ more clusters."),
        FormulaEntry("Responsibility", "r(i,k) ← s(i,k) − max_{k′≠k}[a(i,k′) + s(i,k′)]", "Competitive."),
        FormulaEntry("Availability", "a(i,k) ← min(0, r(k,k) + Σ_{i′∉{i,k}} max(0, r(i′,k)))", "Cooperative."),
        FormulaEntry("Exemplar test", "r(k,k) + a(k,k) > 0", "Identifies the chosen centres."),
        FormulaEntry("Damping", "new ← λ·old + (1−λ)·update, λ ≈ 0.5–0.9", "Required for convergence."),
    ),
    notationKey = listOf(
        NotationEntry("exemplar", "a data point chosen as a cluster centre"),
        NotationEntry("preference", "the similarity diagonal; the indirect cluster-count control"),
        NotationEntry("responsibility", "i's message to k about its suitability"),
        NotationEntry("availability", "k's message to i about its accumulated support"),
        NotationEntry("damping λ", "the blend factor that prevents oscillation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Preference is the cluster-count dial",
            accentColor = 0xFF3B82F6,
            code = """
                from sklearn.cluster import AffinityPropagation
                from sklearn.metrics import euclidean_distances
                import numpy as np

                S = -euclidean_distances(X, squared=True)
                median_similarity = np.median(S)     # sklearn's default preference

                for scale in (3.0, 1.0, 0.3):
                    model = AffinityPropagation(
                        preference=median_similarity * scale,
                        damping=0.9,                  # raise this if it fails to converge
                        max_iter=500,
                        random_state=0,
                    ).fit(X)
                    print(scale, len(model.cluster_centers_indices_), "clusters")
                # Similarities are negative, so multiplying by a LARGER scale makes the
                # diagonal more negative — points become less willing to nominate
                # themselves, and fewer clusters emerge.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Exemplars where a mean would be meaningless",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.cluster import AffinityPropagation
                import numpy as np

                # Any similarity matrix works — it need not come from a vector space, and it
                # need not even be a metric. Here, negative edit distance between strings.
                def neg_edit(a, b):
                    return -levenshtein(a, b)

                S = np.array([[neg_edit(a, b) for b in sentences] for a in sentences])
                np.fill_diagonal(S, np.median(S))

                model = AffinityPropagation(affinity="precomputed", damping=0.9).fit(S)
                for i in model.cluster_centers_indices_:
                    print(sentences[i])       # a real sentence, not an average of sentences

                # k-means cannot do this at all: there is no mean of a set of strings.
                # k-medoids can, and needs to be told k.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF3B82F6, "Document Summarization", "Choosing representative sentences, where an averaged sentence would be meaningless."),
        ApplicationCard("flask", 0xFF818CF8, "Gene Expression", "Frey and Dueck's original application — finding representative genes from a similarity matrix."),
        ApplicationCard("browser", 0xFF10B981, "Image Set Summarization", "Picking real photographs to represent clusters, rather than a blurred average."),
    ),
    takeaways = listOf(
        "Exemplars are actual data points, which matters whenever a mean is meaningless.",
        "Responsibility is competitive and availability cooperative; they are computed alternately until stable.",
        "The cluster count is set indirectly through the preference on the similarity diagonal.",
        "O(n²) memory and time per iteration caps it at a few thousand points, and it needs damping to converge at all.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("spectral_clustering", "Spectral Clustering"),
        CrossLink("k_medians", "K-Medians"),
        CrossLink("bayesian_networks", "Bayesian Networks"),
    ),
)
