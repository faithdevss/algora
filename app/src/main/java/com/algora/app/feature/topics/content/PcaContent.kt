package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val pcaContent = TopicContent(
    topicId = "pca",
    whatIsIt = listOf(
        "Principal Component Analysis (PCA) reduces the dimensionality of data by projecting it onto the directions of greatest variance — the principal components.",
        "Those components are orthogonal axes ordered by how much variance they capture, so keeping the top few retains most of the information in far fewer dimensions.",
    ),
    steps = listOf(
        StepCard(1, "Center the Data", "Subtract the mean of each feature so the data is zero-centered.", 0xFF818CF8),
        StepCard(2, "Covariance Structure", "Compute the covariance matrix (or run SVD directly on the centered data).", 0xFF60A5FA),
        StepCard(3, "Eigen-Decompose", "The eigenvectors are the principal components; eigenvalues rank their variance.", 0xFF10B981),
        StepCard(4, "Project", "Keep the top-k components and project the data onto them.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Covariance", "Σ = (1/n) XᵀX", "On mean-centered data X."),
        FormulaEntry("Eigen equation", "Σvᵢ = λᵢvᵢ", "vᵢ is a component; λᵢ its variance."),
        FormulaEntry("Explained variance", "λᵢ / Σλ", "Fraction of total variance in component i."),
    ),
    notationKey = listOf(
        NotationEntry("component", "an orthogonal direction of variance"),
        NotationEntry("λᵢ", "variance captured by component i"),
        NotationEntry("explained variance", "share of total variance retained"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "PCA (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.decomposition import PCA

                pca = PCA(n_components=2)
                X_reduced = pca.fit_transform(X)   # data is centered internally

                print(pca.explained_variance_ratio_)   # variance kept per component
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF818CF8, "Visualization", "Squeezing high-dimensional data to 2–3 components makes it plottable."),
        ApplicationCard("chip", 0xFF60A5FA, "Compression & Denoising", "Dropping low-variance components shrinks data and filters noise."),
        ApplicationCard("robot", 0xFF10B981, "Preprocessing", "Reducing features speeds up and stabilizes downstream models."),
    ),
    takeaways = listOf(
        "PCA finds orthogonal directions of maximum variance and projects onto the top few.",
        "Components are ranked by explained variance, so you keep the most informative axes.",
        "It requires centered (often standardized) data to be meaningful.",
        "It's linear — for non-linear structure use t-SNE, UMAP, or autoencoders.",
    ),
    crossLinks = listOf(
        CrossLink("svd", "Singular Value Decomposition (SVD)"),
        CrossLink("kernel_pca", "Kernel PCA"),
        CrossLink("tsne", "t-SNE"),
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("kmeans", "K-Means Clustering"),
    ),
)
