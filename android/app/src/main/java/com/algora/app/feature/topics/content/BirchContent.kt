package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val birchContent = TopicContent(
    topicId = "birch",
    figure = Figure(
        caption = "The page's lab: points arrive one at a time with threshold T = 0.4, and each is " +
            "either absorbed by the nearest clustering feature or starts a new one. Points 1–6 " +
            "are too far from everything (absorbing point 2 would make a radius of 2.64, point 6 " +
            "one of 0.50), so each starts its own CF. From point 7 on, the entries are in place: " +
            "7 joins CF3 at radius 0.35, then 8, 9, 10 and 11 are absorbed too, and only N, LS " +
            "and SS change. Point 12 is 0.72 from its nearest entry and starts CF7. No point is " +
            "kept, so one pass is enough, and the result depends on the order the points " +
            "arrived in.",
        shape = FigureShape.Strip(
            cells = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
            bands = listOf(
                FigureBand(0, 5, "start new CFs", FigureTone.Warn),
                FigureBand(6, 10, "absorbed", FigureTone.Accent),
                FigureBand(11, 11, "new", FigureTone.Warn),
            ),
            aux = listOf("CF1", "CF2", "CF3", "CF4", "CF5", "CF6", "CF3", "CF4", "CF5", "CF6", "CF3", "CF7"),
            auxLabel = "entry",
        ),
    ),
    whatIsIt = listOf(
        "BIRCH was designed for data that does not fit in memory. It makes a single pass, never stores a data point, and keeps only a running summary of each group it has encountered — then runs a conventional clustering algorithm on those summaries instead of on the data.",
        "The summary is the whole idea. A clustering feature is three numbers: N, the count; LS, the vector sum; and SS, the sum of squares. From those alone you can compute the group's centroid, radius and diameter, and — critically — two clustering features can be merged by adding them componentwise. That additivity is what makes a streaming pass possible: a new point is absorbed into the nearest feature if it fits within a threshold radius, and starts a new one otherwise, with the original point discarded either way.",
        "The costs are real and specific. The threshold is a hard parameter with no good default: too small and the CF-tree grows past memory, too large and genuinely distinct clusters get absorbed into one summary and can never be separated afterwards. Because it is a single pass, the result depends on the order the data arrived in — the same dataset shuffled gives a different tree. And the whole construction assumes clusters are roughly spherical, since a radius is the only shape a clustering feature can express, which rules out the elongated and nested structures DBSCAN handles comfortably.",
    ),
    steps = listOf(
        StepCard(1, "Define the Summary", "CF = (N, linear sum, squared sum). Centroid and radius follow from those three.", 0xFF3B82F6),
        StepCard(2, "Absorb Each Point", "Descend the CF-tree to the nearest leaf and add the point if it fits the threshold.", 0xFF818CF8),
        StepCard(3, "Or Start a New Feature", "If absorbing would exceed the threshold radius, create a new entry instead.", 0xFF60A5FA),
        StepCard(4, "Split When Nodes Overflow", "The CF-tree splits like a B-tree when a node exceeds its branching factor.", 0xFF10B981),
        StepCard(5, "Rebuild if Memory Runs Out", "Raise the threshold and rebuild from the existing tree — no rereading of data.", 0xFF14B8A6),
        StepCard(6, "Cluster the Summaries", "Run agglomerative or k-means on the leaf CFs, not on the original rows.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Clustering feature", "CF = (N, LS, SS)", "Count, vector sum, sum of squares."),
        FormulaEntry("Additivity", "CF₁ + CF₂ = (N₁+N₂, LS₁+LS₂, SS₁+SS₂)", "Why merging is O(1) and streaming works."),
        FormulaEntry("Centroid", "LS / N", "Recovered from the summary alone."),
        FormulaEntry("Radius", "√(SS/N − (LS/N)²)", "RMS distance from the centroid."),
        FormulaEntry("Complexity", "O(n) single pass", "Plus the cost of clustering the summaries."),
        FormulaEntry("Memory", "O(number of CFs)", "Independent of n, which is the point."),
    ),
    notationKey = listOf(
        NotationEntry("CF", "clustering feature — the three-number summary"),
        NotationEntry("CF-tree", "a height-balanced tree of CFs, structured like a B-tree"),
        NotationEntry("threshold T", "maximum radius of a leaf entry; the key parameter"),
        NotationEntry("branching factor", "maximum children per internal node"),
        NotationEntry("microcluster", "one leaf CF, standing in for the points it absorbed"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Streaming, and the threshold that decides everything",
            accentColor = 0xFF3B82F6,
            code = """
                from sklearn.cluster import Birch, AgglomerativeClustering
                import numpy as np

                model = Birch(
                    threshold=0.5,          # max radius of a leaf CF — the parameter that matters
                    branching_factor=50,
                    n_clusters=AgglomerativeClustering(n_clusters=5),   # the second stage
                )

                # partial_fit is the reason BIRCH exists: the data never has to be resident.
                for chunk in read_in_chunks("huge.csv", rows=10_000):
                    model.partial_fit(chunk)

                print(len(model.subcluster_centers_), "microclusters retained")
                labels = model.predict(X_sample)

                # n_clusters=None skips the second stage and returns the microclusters
                # themselves, which is often what you actually want downstream.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Everything from three numbers",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                class CF:
                    def __init__(self, dim):
                        self.n = 0
                        self.ls = np.zeros(dim)      # linear sum
                        self.ss = 0.0                # sum of squared norms

                    def add(self, x):                # O(1), and x is then discarded
                        self.n += 1
                        self.ls += x
                        self.ss += x @ x

                    def merge(self, other):          # O(1) — this is the additivity property
                        self.n += other.n
                        self.ls += other.ls
                        self.ss += other.ss

                    @property
                    def centroid(self):
                        return self.ls / self.n

                    @property
                    def radius(self):
                        # RMS distance from the centroid, recovered without the points.
                        return np.sqrt(max(0.0, self.ss / self.n - self.centroid @ self.centroid))

                # Note what is absent: any list of points. That absence is the algorithm.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Out-of-Core Clustering", "Datasets larger than memory, clustered in one pass over a file or a stream."),
        ApplicationCard("globe", 0xFF818CF8, "Streaming Pipelines", "partial_fit keeps a live clustering current as events arrive, in bounded memory."),
        ApplicationCard("stack", 0xFF10B981, "Preprocessing", "Compressing millions of rows into thousands of microclusters so an O(n²) method becomes feasible."),
    ),
    takeaways = listOf(
        "A clustering feature is (N, LS, SS), and centroid, radius and diameter all follow from those three.",
        "Additivity makes merges O(1), which is what allows a single streaming pass in bounded memory.",
        "The threshold is unforgiving: too large permanently merges distinct clusters, too small blows the memory budget.",
        "Being single-pass makes it order-dependent, and the radius-based summary assumes spherical clusters.",
    ),
    crossLinks = listOf(
        CrossLink("hierarchical_clustering", "Hierarchical (Agglomerative)"),
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("b_tree", "B-Tree (DSA)"),
        CrossLink("reservoir_sampling", "Reservoir Sampling (DSA)"),
    ),
)
