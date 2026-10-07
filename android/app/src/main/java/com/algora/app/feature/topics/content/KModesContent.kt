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

internal val kModesContent = TopicContent(
    topicId = "k_modes",
    figure = Figure(
        caption = "The page's lab: eight rows of four categorical attributes (colour, size, " +
            "shape, finish) and two centres, c1 = red small round matte and c2 = blue large " +
            "square gloss. There is no distance and no mean: each row's cost to a centre is the " +
            "number of attributes that differ, and it joins the centre with fewer mismatches. " +
            "Rows 1–3 join c1, rows 4–8 join c2. Each centre then takes the most common value in " +
            "each column of its rows. c2's shape becomes round, one centre value changes, and the " +
            "loop repeats until nothing moves.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "4", "c1"),
                listOf("1", "3", "c1"),
                listOf("1", "3", "c1"),
                listOf("4", "0", "c2"),
                listOf("3", "1", "c2"),
                listOf("3", "1", "c2"),
                listOf("3", "2", "c2"),
                listOf("3", "1", "c2"),
            ),
            rowHeaders = listOf("row 1", "row 2", "row 3", "row 4", "row 5", "row 6", "row 7", "row 8"),
            colHeaders = listOf("to c1", "to c2", "joins"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent), FigureCell(1, 0, FigureTone.Accent),
                FigureCell(2, 0, FigureTone.Accent), FigureCell(3, 1), FigureCell(4, 1),
                FigureCell(5, 1), FigureCell(6, 1), FigureCell(7, 1),
            ),
        ),
    ),
    whatIsIt = listOf(
        "K-modes is Lloyd's algorithm for data that has no arithmetic. There is no mean of {red, blue, green} and no meaningful Euclidean distance between them, so k-modes replaces the distance with a count of mismatched attributes and the centre with the most frequent value in each attribute.",
        "The two obvious workarounds are both worse. Label-encoding categories as 0, 1, 2 and running k-means asserts that blue lies numerically between red and green and that red is twice as far from green as from blue — a total fabrication the algorithm will then optimize against. One-hot encoding avoids the false ordering but inflates dimensionality, and in the resulting space every pair of distinct categories is exactly √2 apart, so the geometry k-means depends on has been flattened away.",
        "The honest limitations are worth stating. Hamming distance weights every attribute equally, which is rarely what you want — a mismatch on \"country\" is usually more meaningful than one on \"preferred contact time\" — and fixing that means hand-weighting the attributes. Ties in the mode are common on small clusters and have to be broken arbitrarily. And for the very common case of mixed categorical and numeric columns, neither k-means nor k-modes applies: k-prototypes sums a Hamming term and a Euclidean one with a γ weight between them, and choosing that γ is the real work.",
    ),
    steps = listOf(
        StepCard(1, "Confirm the Attributes Are Nominal", "Unordered labels. Ordered ones carry more information than Hamming can use.", 0xFF3B82F6),
        StepCard(2, "Initialize k Modes", "Huang or Cao initialization beats random seeding noticeably here.", 0xFF818CF8),
        StepCard(3, "Assign by Mismatch Count", "Hamming distance: how many attributes differ, nothing more.", 0xFF60A5FA),
        StepCard(4, "Update to the Per-Attribute Mode", "Most frequent value per column. The centre is a valid row of the same type.", 0xFF10B981),
        StepCard(5, "Repeat to Convergence", "Same monotone-decrease guarantee as k-means.", 0xFF14B8A6),
        StepCard(6, "Weight or Switch if Needed", "Unequal attribute importance needs weights; mixed data needs k-prototypes.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Dissimilarity", "d(x,y) = Σⱼ δ(xⱼ, yⱼ)", "δ is 0 if equal, 1 if not."),
        FormulaEntry("Centre", "mode per attribute", "The most frequent value in each column."),
        FormulaEntry("Objective", "min Σᵢ d(xᵢ, c_{a(i)})", "Total mismatch count."),
        FormulaEntry("k-prototypes", "d = Σ_num (xⱼ−cⱼ)² + γ·Σ_cat δ(xⱼ,cⱼ)", "For mixed data; γ balances the two."),
        FormulaEntry("Weighted variant", "Σⱼ wⱼ·δ(xⱼ,yⱼ)", "When attributes are not equally important."),
        FormulaEntry("Complexity", "O(nkdm)", "n rows, k clusters, d attributes, m iterations."),
    ),
    notationKey = listOf(
        NotationEntry("mode", "the most frequently occurring value"),
        NotationEntry("Hamming distance", "number of positions at which two rows differ"),
        NotationEntry("nominal", "categorical with no order"),
        NotationEntry("k-prototypes", "the mixed numeric/categorical extension"),
        NotationEntry("Huang / Cao init", "initialization schemes designed for categorical data"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "k-modes, and the mixed-data case",
            accentColor = 0xFF3B82F6,
            code = """
                from kmodes.kmodes import KModes
                from kmodes.kprototypes import KPrototypes
                import numpy as np

                # Purely categorical. Cao initialization is density-based and reproducible,
                # and generally beats random seeding on this kind of data.
                km = KModes(n_clusters=4, init="Cao", n_init=10).fit(X_categorical)
                print(km.cluster_centroids_)   # each row is a valid record, not an average

                # Mixed columns: gamma trades the numeric term against the categorical one.
                # Left unset the library picks 0.5 * mean(std of numeric columns), which is a
                # reasonable default and worth overriding once you know which side matters.
                kp = KPrototypes(n_clusters=4, gamma=0.5).fit(
                    X_mixed, categorical=[2, 4, 5],     # indices of the categorical columns
                )
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why one-hot plus k-means is not the same thing",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                # One-hot two distinct categories out of three:
                red   = np.array([1, 0, 0])
                blue  = np.array([0, 1, 0])
                green = np.array([0, 0, 1])

                print(np.linalg.norm(red - blue))    # 1.414
                print(np.linalg.norm(red - green))   # 1.414
                # Every pair of distinct categories is EXACTLY equidistant. That is faithful
                # to the data, and it also means the geometry k-means relies on carries no
                # information at all — it is Hamming distance wearing a Euclidean costume,
                # scaled by sqrt(2), in d times as many dimensions.

                # And the centroid is worse: k-means will place a centre at [0.4, 0.35, 0.25],
                # which is not any colour. k-modes returns "red".
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Survey Segmentation", "Responses drawn from fixed option sets, where a centroid must be a real answer to be reportable."),
        ApplicationCard("flask", 0xFF818CF8, "Genotype Clustering", "SNP data is categorical by nature, and the categories have no ordering to exploit."),
        ApplicationCard("globe", 0xFF10B981, "Customer Profiles", "Region, plan, channel and device — attributes with no numeric meaning between their values."),
    ),
    takeaways = listOf(
        "Hamming distance and the per-attribute mode replace Euclidean distance and the mean; the loop is unchanged.",
        "The centre is a valid record of the same type as the data, which a mean over one-hot columns is not.",
        "Hamming weights every attribute equally — usually wrong, and fixable only by hand-weighting.",
        "Mixed numeric and categorical data needs k-prototypes, whose γ weight is the real decision.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("k_medians", "K-Medians"),
        CrossLink("categorical_nb", "Categorical Naive Bayes"),
        CrossLink("edit_distance", "Edit Distance (DSA)"),
    ),
)
