package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// AI-mode problems. Same shape as the DSA bank, but the thing being implemented is a learning rule
// or an inference step rather than a data-structure operation — gradient descent, backprop, TF-IDF,
// attention, a Q-update. Prerequisites point into the ML / DL / NLP / RL taxonomy.

internal val aiPatterns = listOf(
    ProblemPattern(
        id = "ml_foundations",
        name = "ML Foundations",
        blurb = "Fit, predict, cluster and evaluate — the loops under every classical model.",
        accentColor = 0xFF6366F1,
        topicId = "linear_regression",
    ),
    ProblemPattern(
        id = "neural_nets",
        name = "Neural Networks",
        blurb = "Forward pass, loss, gradients and the update rule, written out by hand.",
        accentColor = 0xFFDB2777,
        topicId = "backpropagation",
    ),
    ProblemPattern(
        id = "nlp_pipeline",
        name = "NLP Pipeline",
        blurb = "Turning text into vectors — counts, weights, subwords and attention.",
        accentColor = 0xFF0D9488,
        topicId = "tokenization",
    ),
    ProblemPattern(
        id = "rl_control",
        name = "RL Control",
        blurb = "Acting, bootstrapping and learning from stored experience.",
        accentColor = 0xFFEA580C,
        topicId = "q_learning",
    ),
)

private val mlProblems = listOf(
    PracticeProblem(
        id = "gradient_descent_line_fit",
        title = "Fit a Line with Gradient Descent",
        patternId = "ml_foundations",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given paired x and y values, fit y = wx + b by minimising mean squared error with batch gradient descent. Return the learned w and b.",
        examples = listOf(
            ProblemExample("x = [1,2,3,4], y = [3,5,7,9], lr = 0.01, epochs = 5000", "w ≈ 2.0, b ≈ 1.0", "The data is exactly y = 2x + 1."),
            ProblemExample("lr = 1.5 on the same data", "diverges to NaN", "Too large a step overshoots the minimum every time."),
        ),
        constraints = listOf(
            "Use the closed-form gradient, not numerical differences.",
            "One update per epoch, computed over the whole batch.",
        ),
        hints = listOf(
            "Write the loss first: MSE = mean((wx + b - y)²). Gradients follow from the chain rule.",
            "∂MSE/∂w = mean(2·error·x) and ∂MSE/∂b = mean(2·error), where error = prediction - actual.",
            "Accumulate both gradients across all points *before* updating — updating mid-loop makes it stochastic, not batch.",
        ),
        approach = listOf(
            "Initialise w and b to zero.",
            "Each epoch, sweep the data accumulating dw and db from the per-point error.",
            "Apply the update after the sweep: w -= lr · dw, b -= lr · db.",
            "The learning rate controls step size — too small crawls, too large diverges, which is the single most common bug here.",
        ),
        timeComplexity = "O(epochs × n)",
        spaceComplexity = "O(1) — two parameters and two gradient accumulators",
        solutionCode = """
            fun fitLine(x: DoubleArray, y: DoubleArray, lr: Double, epochs: Int): Pair<Double, Double> {
                var w = 0.0
                var b = 0.0
                val n = x.size

                repeat(epochs) {
                    var dw = 0.0
                    var db = 0.0
                    for (i in 0 until n) {
                        val error = (w * x[i] + b) - y[i]
                        dw += 2 * error * x[i] / n
                        db += 2 * error / n
                    }
                    // Update only after the full batch is accumulated.
                    w -= lr * dw
                    b -= lr * db
                }
                return w to b
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("linear_regression", "Linear Regression", "The model and its squared-error loss."),
            ProblemPrereq("gradient_descent_variants", "Gradient Descent Variants", "Batch versus stochastic versus mini-batch is exactly the choice made here."),
        ),
        linkedTopicId = "linear_regression",
        linkedTopicLabel = "Linear Regression",
    ),
    PracticeProblem(
        id = "knn_classify",
        title = "k-Nearest Neighbours Classifier",
        patternId = "ml_foundations",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given labelled training points and a query point, predict its label by majority vote among the k nearest neighbours.",
        examples = listOf(
            ProblemExample("train = [([1,1], 0), ([2,2], 0), ([8,8], 1)], query = [1.5, 1.5], k = 3", "0", "Two of three neighbours are class 0."),
            ProblemExample("same train, query = [9,9], k = 1", "1", ""),
        ),
        constraints = listOf(
            "Euclidean distance.",
            "Ties may be broken arbitrarily.",
        ),
        hints = listOf(
            "There is no training step — the model *is* the stored data.",
            "Comparing distances never needs the square root; the squared distance preserves the ordering.",
            "After taking the k nearest, the prediction is the most common label among them.",
        ),
        approach = listOf(
            "Compute the squared distance from the query to every training point.",
            "Sort by that distance and take the first k.",
            "Count labels among those k and return the most frequent.",
            "Cost is per-query, not per-fit — this is what makes k-NN a lazy learner.",
        ),
        timeComplexity = "O(n·d + n log n) per query",
        spaceComplexity = "O(n) — the whole training set is the model",
        solutionCode = """
            fun classify(train: List<Pair<DoubleArray, Int>>, query: DoubleArray, k: Int): Int {
                fun squaredDistance(a: DoubleArray, b: DoubleArray): Double {
                    var sum = 0.0
                    for (i in a.indices) {
                        val d = a[i] - b[i]
                        sum += d * d
                    }
                    return sum        // no sqrt — ordering is unchanged
                }

                return train
                    .sortedBy { squaredDistance(it.first, query) }
                    .take(k)
                    .groupingBy { it.second }
                    .eachCount()
                    .maxByOrNull { it.value }!!
                    .key
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("knn", "k-Nearest Neighbors", "The algorithm, its k trade-off and why scaling features matters."),
            ProblemPrereq("heap", "Heap (Min / Max)", "A size-k heap replaces the sort when n is large — the top-k pattern again."),
        ),
        linkedTopicId = "knn",
        linkedTopicLabel = "k-Nearest Neighbors",
    ),
    PracticeProblem(
        id = "kmeans_lloyd_step",
        title = "One Step of K-Means",
        patternId = "ml_foundations",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given points and current centroids, perform one Lloyd iteration: assign every point to its nearest centroid, then move each centroid to the mean of its assigned points.",
        examples = listOf(
            ProblemExample("points = [[1,1],[1,2],[9,9]], centroids = [[0,0],[10,10]]", "[[1.0,1.5], [9.0,9.0]]", ""),
            ProblemExample("a centroid with no assigned points", "left unchanged", "Moving it to the mean of nothing is undefined."),
        ),
        constraints = listOf(
            "Exactly one iteration — no convergence loop.",
            "Handle empty clusters without dividing by zero.",
        ),
        hints = listOf(
            "The iteration has two distinct halves: assign, then update. Do not interleave them.",
            "Accumulate a running sum and count per cluster during assignment, so the update needs no second pass over the points.",
            "A cluster that captured nothing must keep its old position — dividing by a count of zero is the classic crash.",
        ),
        approach = listOf(
            "For each point, find the nearest centroid by squared distance.",
            "Add the point into that cluster's coordinate sums and bump its count.",
            "After the sweep, each new centroid is its sums divided by its count.",
            "Empty clusters keep their previous coordinates. Repeating this step until nothing moves is the full algorithm.",
        ),
        timeComplexity = "O(n × k × d) per iteration",
        spaceComplexity = "O(k × d) for the sums and counts",
        solutionCode = """
            fun lloydStep(points: List<DoubleArray>, centroids: List<DoubleArray>): List<DoubleArray> {
                val dim = centroids[0].size
                val sums = Array(centroids.size) { DoubleArray(dim) }
                val counts = IntArray(centroids.size)

                // 1. Assign each point to its nearest centroid.
                for (p in points) {
                    var best = 0
                    var bestDistance = Double.MAX_VALUE
                    for (c in centroids.indices) {
                        var d = 0.0
                        for (i in 0 until dim) {
                            val diff = p[i] - centroids[c][i]
                            d += diff * diff
                        }
                        if (d < bestDistance) {
                            bestDistance = d
                            best = c
                        }
                    }
                    counts[best]++
                    for (i in 0 until dim) sums[best][i] += p[i]
                }

                // 2. Move each centroid to the mean of what it captured.
                return centroids.mapIndexed { c, old ->
                    if (counts[c] == 0) old else DoubleArray(dim) { i -> sums[c][i] / counts[c] }
                }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("kmeans", "K-Means Clustering", "Lloyd's algorithm, initialisation sensitivity and choosing k."),
            ProblemPrereq("dbscan", "DBSCAN", "Density clustering needs no k and finds non-spherical clusters — the contrast is the lesson."),
        ),
        linkedTopicId = "kmeans",
        linkedTopicLabel = "K-Means Clustering",
    ),
    PracticeProblem(
        id = "precision_recall_f1",
        title = "Precision, Recall and F1",
        patternId = "ml_foundations",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given actual and predicted binary labels, compute precision, recall and F1 for the positive class.",
        examples = listOf(
            ProblemExample("actual = [1,1,0,0], predicted = [1,0,1,0]", "precision 0.5, recall 0.5, F1 0.5", "One TP, one FP, one FN."),
            ProblemExample("predicted all 0 on an imbalanced set", "precision 0, recall 0, F1 0", "Accuracy would still look excellent."),
        ),
        constraints = listOf(
            "Labels are 0 or 1.",
            "Return 0 rather than NaN when a denominator is zero.",
        ),
        hints = listOf(
            "Everything follows from three counts: true positives, false positives, false negatives.",
            "Precision asks \"of what I flagged, how much was right?\" — TP / (TP + FP). Recall asks \"of what was there, how much did I catch?\" — TP / (TP + FN).",
            "F1 is their harmonic mean, which stays low unless both are high.",
        ),
        approach = listOf(
            "Walk both arrays together, incrementing TP, FP or FN as each pair dictates. True negatives never enter these formulas.",
            "Compute precision and recall, guarding each zero denominator.",
            "F1 = 2·P·R / (P + R), guarded the same way.",
            "The harmonic mean is deliberate: an arithmetic mean would reward a model that maximises one metric and abandons the other.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1)",
        solutionCode = """
            data class Metrics(val precision: Double, val recall: Double, val f1: Double)

            fun evaluate(actual: IntArray, predicted: IntArray): Metrics {
                var tp = 0
                var fp = 0
                var fn = 0

                for (i in actual.indices) {
                    when {
                        predicted[i] == 1 && actual[i] == 1 -> tp++
                        predicted[i] == 1 && actual[i] == 0 -> fp++
                        predicted[i] == 0 && actual[i] == 1 -> fn++
                    }
                }

                val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
                val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
                val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
                return Metrics(precision, recall, f1)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("logistic_regression", "Logistic Regression", "Thresholding a probability is what produces these predictions."),
            ProblemPrereq("naive_bayes", "Naive Bayes", "Imbalanced text classification is where these metrics earn their keep."),
        ),
        linkedTopicId = "logistic_regression",
        linkedTopicLabel = "Logistic Regression",
    ),
)

private val neuralNetProblems = listOf(
    PracticeProblem(
        id = "mlp_forward_pass",
        title = "Forward Pass of a Two-Layer Network",
        patternId = "neural_nets",
        difficulty = Difficulty.BEGINNER,
        prompt = "Implement the forward pass of a network with one ReLU hidden layer and a single sigmoid output.",
        examples = listOf(
            ProblemExample("x = [1,0], one hidden unit with w = [1,1], b = 0", "hidden = 1, output = sigmoid(w2·1 + b2)", ""),
            ProblemExample("a hidden pre-activation of -3", "hidden = 0", "ReLU clamps negatives to zero."),
        ),
        constraints = listOf(
            "ReLU on the hidden layer, sigmoid on the output.",
            "No external matrix library.",
        ),
        hints = listOf(
            "Each hidden unit is a dot product with its own weight row, plus a bias, then the activation.",
            "ReLU is max(0, z) — nothing more.",
            "The output unit repeats the same pattern over the hidden activations, then applies sigmoid to get a probability.",
        ),
        approach = listOf(
            "For each hidden unit, accumulate bias + Σ wᵢ·xᵢ, then apply max(0, ·).",
            "Accumulate the output pre-activation over the hidden vector, plus its bias.",
            "Apply sigmoid: 1 / (1 + e^-z).",
            "Cache the hidden activations — backprop needs them, which is why the backward pass is cheap.",
        ),
        timeComplexity = "O(hidden × input)",
        spaceComplexity = "O(hidden)",
        solutionCode = """
            fun forward(
                x: DoubleArray,
                w1: Array<DoubleArray>,   // one weight row per hidden unit
                b1: DoubleArray,
                w2: DoubleArray,
                b2: Double,
            ): Double {
                val hidden = DoubleArray(w1.size) { j ->
                    var sum = b1[j]
                    for (i in x.indices) sum += w1[j][i] * x[i]
                    maxOf(0.0, sum)                       // ReLU
                }

                var out = b2
                for (j in hidden.indices) out += w2[j] * hidden[j]
                return 1.0 / (1.0 + Math.exp(-out))       // sigmoid
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("neural_network_basics", "Neural Network Basics", "Layers, weights, biases and how a prediction is produced."),
            ProblemPrereq("activation_functions", "Activation Functions", "Why a non-linearity must sit between the layers at all."),
        ),
        linkedTopicId = "neural_network_basics",
        linkedTopicLabel = "Neural Network Basics",
    ),
    PracticeProblem(
        id = "softmax_cross_entropy",
        title = "Softmax and Cross-Entropy Loss",
        patternId = "neural_nets",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Convert a vector of logits into a probability distribution, then return the cross-entropy loss against a correct class index.",
        examples = listOf(
            ProblemExample("logits = [2.0, 1.0, 0.1], label = 0", "loss ≈ 0.417", ""),
            ProblemExample("logits = [1000, 999], label = 0", "finite, not NaN", "Naive exponentiation overflows here."),
        ),
        constraints = listOf(
            "Must be numerically stable for large logits.",
            "Loss uses the natural log.",
        ),
        hints = listOf(
            "Softmax is exp(zᵢ) / Σ exp(zⱼ) — but exp of a large logit overflows to infinity.",
            "Subtracting the maximum logit from every logit leaves the result unchanged, since the constant cancels in the ratio.",
            "Cross-entropy for a one-hot target reduces to -log(probability of the correct class).",
        ),
        approach = listOf(
            "Find the maximum logit and subtract it from each before exponentiating — the shift-invariance trick.",
            "Normalise by the sum of exponentials to get probabilities.",
            "Take the negative log of the correct class's probability.",
            "Clamp that probability away from zero so a confident wrong answer yields a large loss instead of -infinity.",
        ),
        timeComplexity = "O(k) for k classes",
        spaceComplexity = "O(k)",
        solutionCode = """
            fun softmax(logits: DoubleArray): DoubleArray {
                val max = logits.max()                    // shift for stability
                val exps = DoubleArray(logits.size) { Math.exp(logits[it] - max) }
                val sum = exps.sum()
                return DoubleArray(exps.size) { exps[it] / sum }
            }

            fun crossEntropy(logits: DoubleArray, label: Int): Double {
                val p = softmax(logits)
                return -Math.log(p[label].coerceAtLeast(1e-12))   // guard log(0)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("activation_functions", "Activation Functions", "Softmax as the multi-class output activation."),
            ProblemPrereq("logistic_regression", "Logistic Regression", "Binary cross-entropy is the two-class case of this loss."),
        ),
        linkedTopicId = "activation_functions",
        linkedTopicLabel = "Activation Functions",
    ),
    PracticeProblem(
        id = "dense_layer_backward",
        title = "Backprop Through a Dense Layer",
        patternId = "neural_nets",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a layer's input, its weight matrix and the gradient of the loss with respect to its output, return the weight gradients and the gradient to pass to the previous layer.",
        examples = listOf(
            ProblemExample("input = [1,2], gradOut = [3]", "gradW = [[3, 6]]", "Outer product of gradOut and input."),
            ProblemExample("weights = [[1,2]], gradOut = [3]", "gradInput = [3, 6]", "Wᵀ · gradOut."),
        ),
        constraints = listOf(
            "The layer is a plain matrix multiply plus bias; the activation gradient is already folded into gradOut.",
            "No autograd — write both gradients explicitly.",
        ),
        hints = listOf(
            "Write the forward rule first: outⱼ = Σᵢ Wⱼᵢ · inputᵢ + bⱼ.",
            "∂out_j/∂W_ji is just input_i, so the weight gradient is the outer product gradOut ⊗ input.",
            "∂outⱼ/∂inputᵢ is Wⱼᵢ, so the gradient flowing back is Wᵀ · gradOut — the transpose is the whole trick.",
        ),
        approach = listOf(
            "Build gradW as a matrix where entry (j, i) is gradOut[j] × input[i].",
            "Build gradInput by summing weights[j][i] × gradOut[j] over every output unit j.",
            "The bias gradient, not returned here, is simply gradOut itself.",
            "Chaining this layer-by-layer from the loss backwards is backpropagation; each layer needs only its cached input.",
        ),
        timeComplexity = "O(out × in) — same cost as the forward pass",
        spaceComplexity = "O(out × in) for the weight gradients",
        solutionCode = """
            // gradW = gradOut ⊗ input ;  gradInput = Wᵀ · gradOut
            fun denseBackward(
                input: DoubleArray,
                weights: Array<DoubleArray>,
                gradOut: DoubleArray,
            ): Pair<Array<DoubleArray>, DoubleArray> {
                val gradW = Array(weights.size) { j ->
                    DoubleArray(input.size) { i -> gradOut[j] * input[i] }
                }

                val gradInput = DoubleArray(input.size) { i ->
                    var sum = 0.0
                    for (j in weights.indices) sum += weights[j][i] * gradOut[j]
                    sum
                }

                return gradW to gradInput
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("backpropagation", "Backpropagation", "The chain rule applied layer by layer — this is one link of it."),
            ProblemPrereq("neural_network_basics", "Neural Network Basics", "The forward rule you are differentiating."),
        ),
        linkedTopicId = "backpropagation",
        linkedTopicLabel = "Backpropagation",
    ),
    PracticeProblem(
        id = "sgd_with_momentum",
        title = "SGD with Momentum",
        patternId = "neural_nets",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Implement a parameter update that keeps an exponentially decaying average of past gradients and steps along that velocity instead of the raw gradient.",
        examples = listOf(
            ProblemExample("repeated identical gradients", "steps accelerate toward a terminal velocity", ""),
            ProblemExample("gradients that flip sign each step", "oscillation is damped", "The average cancels the alternating components."),
        ),
        constraints = listOf(
            "β defaults to 0.9.",
            "Velocity must persist across calls.",
        ),
        hints = listOf(
            "Plain SGD forgets everything between steps, so it zigzags across narrow ravines.",
            "Keep a velocity vector updated as v = β·v + (1-β)·grad.",
            "Step along v rather than grad; velocity must be state on the optimiser, not a local.",
        ),
        approach = listOf(
            "Allocate the velocity lazily on the first step, matching the parameter shape.",
            "Blend the new gradient into the velocity with weight (1 - β).",
            "Subtract lr × velocity from each parameter.",
            "Consistent gradients compound into larger steps; alternating ones cancel — that is the damping.",
        ),
        timeComplexity = "O(p) per step for p parameters",
        spaceComplexity = "O(p) for the velocity buffer",
        solutionCode = """
            class SgdMomentum(private val lr: Double, private val beta: Double = 0.9) {
                private var velocity: DoubleArray? = null

                fun step(params: DoubleArray, grads: DoubleArray) {
                    val v = velocity ?: DoubleArray(params.size).also { velocity = it }

                    for (i in params.indices) {
                        v[i] = beta * v[i] + (1 - beta) * grads[i]   // decaying average
                        params[i] -= lr * v[i]
                    }
                }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("gradient_descent_variants", "Gradient Descent Variants", "Momentum, RMSProp and Adam all build on this update."),
            ProblemPrereq("backpropagation", "Backpropagation", "The gradients being consumed come from the backward pass."),
        ),
        linkedTopicId = "gradient_descent_variants",
        linkedTopicLabel = "Gradient Descent Variants",
    ),
)

private val nlpProblems = listOf(
    PracticeProblem(
        id = "bag_of_words_vectors",
        title = "Build Bag-of-Words Vectors",
        patternId = "nlp_pipeline",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given a list of documents, build a shared vocabulary and return each document as a count vector over it.",
        examples = listOf(
            ProblemExample("[\"the cat sat\", \"the dog sat\"]", "vocab [cat, dog, sat, the]; vectors [1,0,1,1] and [0,1,1,1]", ""),
            ProblemExample("[\"a a b\"]", "vocab [a, b]; vector [2, 1]", "Counts, not presence flags."),
        ),
        constraints = listOf(
            "Lowercase and split on spaces.",
            "Vocabulary order must be stable across documents.",
        ),
        hints = listOf(
            "Every document must map onto the *same* axes, so build the vocabulary before vectorising anything.",
            "Sorting the vocabulary makes the index assignment deterministic.",
            "A word-to-index map turns each vectorisation into a single pass.",
        ),
        approach = listOf(
            "Collect distinct tokens across all documents and sort them into the vocabulary.",
            "Build a term → column index map.",
            "For each document, allocate a zero vector and increment the column for every token.",
            "The result is order-insensitive by construction — \"dog bites man\" and \"man bites dog\" are identical, which is the model's known weakness.",
        ),
        timeComplexity = "O(total tokens + V log V) for the sort",
        spaceComplexity = "O(documents × V)",
        solutionCode = """
            fun bagOfWords(docs: List<String>): Pair<List<String>, List<IntArray>> {
                val vocab = docs
                    .flatMap { it.lowercase().split(" ") }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()                                   // stable column order
                val column = vocab.withIndex().associate { (i, w) -> w to i }

                val vectors = docs.map { doc ->
                    val v = IntArray(vocab.size)
                    for (w in doc.lowercase().split(" ")) column[w]?.let { v[it]++ }
                    v
                }
                return vocab to vectors
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("bow_tfidf", "Bag-of-Words / TF-IDF", "The representation and what it discards."),
            ProblemPrereq("tokenization", "Tokenization", "Splitting text is the step before any vectorisation."),
        ),
        linkedTopicId = "bow_tfidf",
        linkedTopicLabel = "Bag-of-Words / TF-IDF",
    ),
    PracticeProblem(
        id = "tfidf_weights",
        title = "TF-IDF Weighting",
        patternId = "nlp_pipeline",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given tokenised documents, return each document as a map from term to TF-IDF weight.",
        examples = listOf(
            ProblemExample("a term appearing in every document", "weight near zero", "IDF suppresses it."),
            ProblemExample("a term appearing in one document only", "high weight there", "Rare terms discriminate."),
        ),
        constraints = listOf(
            "TF is the term's count divided by the document length.",
            "Smooth the IDF denominator so an unseen term cannot divide by zero.",
        ),
        hints = listOf(
            "Raw counts over-reward long documents — divide by document length to get term frequency.",
            "Document frequency counts *documents* containing a term, not total occurrences, so deduplicate per document.",
            "IDF = log(N / (1 + df)) + 1, with the +1s smoothing and keeping weights positive.",
        ),
        approach = listOf(
            "First pass: count how many documents each term appears in, using a per-document set.",
            "Second pass: for each document, compute term counts and divide by its length for TF.",
            "Multiply by the term's IDF.",
            "Common words collapse toward zero while distinctive ones dominate — which is what makes the vectors useful for retrieval.",
        ),
        timeComplexity = "O(total tokens)",
        spaceComplexity = "O(V + total tokens)",
        solutionCode = """
            fun tfidf(docs: List<List<String>>): List<Map<String, Double>> {
                val n = docs.size

                // Document frequency: how many docs contain each term at least once.
                val df = HashMap<String, Int>()
                for (doc in docs) {
                    for (term in doc.toSet()) df[term] = (df[term] ?: 0) + 1
                }

                return docs.map { doc ->
                    val counts = doc.groupingBy { it }.eachCount()
                    counts.mapValues { (term, count) ->
                        val tf = count.toDouble() / doc.size
                        val idf = Math.log(n.toDouble() / (1 + (df[term] ?: 0))) + 1.0
                        tf * idf
                    }
                }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("bow_tfidf", "Bag-of-Words / TF-IDF", "The weighting scheme this implements."),
            ProblemPrereq("hash_table", "Hash Table / Hash Map", "Both counting passes are map lookups."),
        ),
        linkedTopicId = "bow_tfidf",
        linkedTopicLabel = "Bag-of-Words / TF-IDF",
    ),
    PracticeProblem(
        id = "bpe_merge_step",
        title = "One BPE Merge Step",
        patternId = "nlp_pipeline",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a corpus of words split into symbols, find the most frequent adjacent symbol pair and merge every occurrence of it into a single symbol.",
        examples = listOf(
            ProblemExample("[[l,o,w], [l,o,w,e,r]]", "merge (l, o) → [[lo,w], [lo,w,e,r]]", "The pair occurs twice."),
            ProblemExample("a corpus with no adjacent pair", "no merge", "Single-symbol words contribute nothing."),
        ),
        constraints = listOf(
            "Count pair occurrences across the whole corpus, not per word.",
            "Merging must be left to right and non-overlapping.",
        ),
        hints = listOf(
            "Two phases: count every adjacent pair, then rewrite the corpus with the winner merged.",
            "When rewriting, a matched pair consumes two symbols — advance the index by two, not one.",
            "Repeating this step until the vocabulary reaches its target size is the whole BPE training loop.",
        ),
        approach = listOf(
            "Sweep every word counting adjacent (a, b) pairs into a map.",
            "Take the most frequent pair; no pairs means the corpus is fully merged.",
            "Rewrite each word: on a match emit the concatenated symbol and skip two positions, otherwise emit one symbol and advance by one.",
            "This is why subword tokenizers handle unseen words — rare words survive as pieces rather than becoming a single unknown token.",
        ),
        timeComplexity = "O(total symbols) per merge step",
        spaceComplexity = "O(distinct pairs)",
        solutionCode = """
            fun mostFrequentPair(corpus: List<List<String>>): Pair<String, String>? {
                val counts = HashMap<Pair<String, String>, Int>()
                for (word in corpus) {
                    for (i in 0 until word.size - 1) {
                        val pair = word[i] to word[i + 1]
                        counts[pair] = (counts[pair] ?: 0) + 1
                    }
                }
                return counts.maxByOrNull { it.value }?.key
            }

            fun applyMerge(corpus: List<List<String>>, pair: Pair<String, String>): List<List<String>> =
                corpus.map { word ->
                    val out = mutableListOf<String>()
                    var i = 0
                    while (i < word.size) {
                        if (i < word.size - 1 && word[i] == pair.first && word[i + 1] == pair.second) {
                            out += pair.first + pair.second
                            i += 2                      // the pair is consumed
                        } else {
                            out += word[i]
                            i++
                        }
                    }
                    out
                }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("tokenization", "Tokenization", "Subword tokenisation and the out-of-vocabulary problem it solves."),
            ProblemPrereq("llms", "LLMs", "Every modern LLM tokenizer is built by repeating this step."),
        ),
        linkedTopicId = "tokenization",
        linkedTopicLabel = "Tokenization",
    ),
    PracticeProblem(
        id = "scaled_dot_product_attention",
        title = "Scaled Dot-Product Attention",
        patternId = "nlp_pipeline",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given a query vector, a set of key vectors and their matching value vectors, return the attention output: a softmax-weighted average of the values.",
        examples = listOf(
            ProblemExample("a query identical to one key", "output dominated by that key's value", ""),
            ProblemExample("all keys equally similar", "output is the plain mean of the values", ""),
        ),
        constraints = listOf(
            "Scale scores by 1/√d before the softmax.",
            "Softmax must be numerically stable.",
        ),
        hints = listOf(
            "Relevance of a key to the query is their dot product.",
            "Without the 1/√d scaling, dot products grow with dimension, pushing softmax into a near-one-hot regime with vanishing gradients.",
            "The output is Σ weightₖ · valueₖ — a convex combination, not a selection.",
        ),
        approach = listOf(
            "Compute the dot product of the query with each key and divide by √d.",
            "Softmax those scores into weights summing to one.",
            "Accumulate the weighted sum of the value vectors.",
            "Repeating this for every position in parallel, with separate learned projections, is multi-head self-attention.",
        ),
        timeComplexity = "O(n × d) for n keys of dimension d",
        spaceComplexity = "O(n) for the weights",
        solutionCode = """
            fun attention(
                query: DoubleArray,
                keys: Array<DoubleArray>,
                values: Array<DoubleArray>,
            ): DoubleArray {
                val d = query.size

                val scores = DoubleArray(keys.size) { k ->
                    var dot = 0.0
                    for (i in 0 until d) dot += query[i] * keys[k][i]
                    dot / Math.sqrt(d.toDouble())        // scale before softmax
                }

                val weights = softmax(scores)

                val out = DoubleArray(values[0].size)
                for (k in values.indices) {
                    for (i in out.indices) out[i] += weights[k] * values[k][i]
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("attention", "Attention", "Queries, keys, values and why the scaling factor exists."),
            ProblemPrereq("transformers", "Transformers", "Multi-head self-attention is this function repeated in parallel."),
            ProblemPrereq("word_embeddings", "Word Embeddings", "The vectors being attended over are embeddings."),
        ),
        linkedTopicId = "attention",
        linkedTopicLabel = "Attention",
    ),
)

private val rlProblems = listOf(
    PracticeProblem(
        id = "epsilon_greedy_action",
        title = "Epsilon-Greedy Action Selection",
        patternId = "rl_control",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given action values for the current state and an exploration rate ε, pick an action: random with probability ε, otherwise the highest-valued one.",
        examples = listOf(
            ProblemExample("q = [1.0, 5.0, 2.0], ε = 0", "1", "Pure exploitation."),
            ProblemExample("ε = 1", "uniformly random", "Pure exploration."),
        ),
        constraints = listOf(
            "0 ≤ ε ≤ 1",
            "Random selection is over all actions, including the greedy one.",
        ),
        hints = listOf(
            "Always taking the best-known action means never discovering a better one — that is the exploration/exploitation trade-off.",
            "Draw one uniform number and compare it against ε.",
            "Decaying ε over training shifts the agent from exploring toward exploiting.",
        ),
        approach = listOf(
            "Sample a uniform number in [0, 1).",
            "Below ε: return a uniformly random action index.",
            "Otherwise: return the index of the maximum value.",
            "Note the random branch may still pick the greedy action, so the true exploration rate is slightly below ε.",
        ),
        timeComplexity = "O(A) for A actions",
        spaceComplexity = "O(1)",
        solutionCode = """
            import kotlin.random.Random

            fun selectAction(q: DoubleArray, epsilon: Double, random: Random): Int =
                if (random.nextDouble() < epsilon) {
                    random.nextInt(q.size)                     // explore
                } else {
                    q.indices.maxByOrNull { q[it] }!!          // exploit
                }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("q_learning", "Q-Learning (off-policy)", "ε-greedy is the behaviour policy Q-learning explores with."),
            ProblemPrereq("mdp", "MDP", "States, actions and why exploration is needed at all."),
        ),
        linkedTopicId = "q_learning",
        linkedTopicLabel = "Q-Learning (off-policy)",
    ),
    PracticeProblem(
        id = "q_learning_update",
        title = "The Q-Learning Update",
        patternId = "rl_control",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Apply one Q-learning update to a tabular Q given a transition (state, action, reward, next state, done).",
        examples = listOf(
            ProblemExample("reward = 1, done = true, α = 0.5, Q[s][a] = 0", "Q[s][a] becomes 0.5", "No bootstrap on a terminal step."),
            ProblemExample("reward = 0, γ = 0.9, max Q[next] = 10, α = 0.1, Q[s][a] = 0", "Q[s][a] becomes 0.9", ""),
        ),
        constraints = listOf(
            "Terminal transitions must not bootstrap from the next state.",
            "Update in place.",
        ),
        hints = listOf(
            "The target is what you now believe the value should be: reward plus the discounted best next value.",
            "Q-learning is off-policy because the target uses max over next actions, not the action actually taken next.",
            "A terminal state has no future, so its bootstrap term is zero — forgetting this is the classic bug.",
        ),
        approach = listOf(
            "Compute the bootstrap: 0 if done, otherwise the maximum Q value of the next state.",
            "Form the target: reward + γ × bootstrap.",
            "Compute the TD error, target - current estimate.",
            "Move the estimate a fraction α along that error. Using the *taken* next action instead of the max turns this into SARSA.",
        ),
        timeComplexity = "O(A) per update",
        spaceComplexity = "O(1) — the table is updated in place",
        solutionCode = """
            fun qLearningUpdate(
                q: Array<DoubleArray>,
                state: Int,
                action: Int,
                reward: Double,
                nextState: Int,
                done: Boolean,
                alpha: Double,
                gamma: Double,
            ) {
                // A terminal transition has no future to bootstrap from.
                val bootstrap = if (done) 0.0 else q[nextState].max()
                val target = reward + gamma * bootstrap
                val tdError = target - q[state][action]
                q[state][action] += alpha * tdError
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("q_learning", "Q-Learning (off-policy)", "The update rule and its convergence conditions."),
            ProblemPrereq("mdp", "MDP", "Discounting and the Bellman optimality equation behind the target."),
        ),
        linkedTopicId = "q_learning",
        linkedTopicLabel = "Q-Learning (off-policy)",
    ),
    PracticeProblem(
        id = "discounted_returns",
        title = "Discounted Returns of an Episode",
        patternId = "rl_control",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given the rewards of one episode in order, return the discounted return from every timestep onwards.",
        examples = listOf(
            ProblemExample("rewards = [1, 1, 1], γ = 0.5", "[1.75, 1.5, 1.0]", "Each is r + γ·(next return)."),
            ProblemExample("rewards = [0, 0, 10], γ = 1.0", "[10, 10, 10]", "No discounting."),
        ),
        constraints = listOf(
            "0 ≤ γ ≤ 1",
            "Single pass — no nested loops.",
        ),
        hints = listOf(
            "The definition Gₜ = Σ γᵏ·r_{t+k} looks quadratic if computed literally.",
            "But Gₜ = rₜ + γ·G_{t+1}, so each return is one multiply-add away from the next.",
            "Walk the rewards backwards carrying a running value.",
        ),
        approach = listOf(
            "Start a running return at 0 for the step after the episode ends.",
            "Sweep timesteps in reverse, setting running = reward + γ × running.",
            "Store the running value at each index as you go.",
            "This is the suffix-accumulation idea from prefix sums, with a decay factor — and it is what REINFORCE weights its gradients by.",
        ),
        timeComplexity = "O(T) for an episode of T steps",
        spaceComplexity = "O(T) for the output",
        solutionCode = """
            fun discountedReturns(rewards: DoubleArray, gamma: Double): DoubleArray {
                val out = DoubleArray(rewards.size)
                var running = 0.0

                // G_t = r_t + gamma * G_{t+1}, so build it back to front.
                for (t in rewards.indices.reversed()) {
                    running = rewards[t] + gamma * running
                    out[t] = running
                }
                return out
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("mdp", "MDP", "The return and the role of the discount factor."),
            ProblemPrereq("reinforce", "REINFORCE", "Policy-gradient updates are scaled by exactly these returns."),
            ProblemPrereq("prefix_sum", "Prefix Sum", "Same suffix-accumulation trick, with decay."),
        ),
        linkedTopicId = "mdp",
        linkedTopicLabel = "MDP",
    ),
    PracticeProblem(
        id = "replay_buffer",
        title = "Experience Replay Buffer",
        patternId = "rl_control",
        difficulty = Difficulty.ADVANCED,
        prompt = "Implement a fixed-capacity replay buffer that overwrites the oldest transition when full and can sample a random batch.",
        examples = listOf(
            ProblemExample("capacity 3, adding 4 transitions", "the first is overwritten", ""),
            ProblemExample("sample(2)", "two transitions drawn uniformly", "Sampling with replacement is standard."),
        ),
        constraints = listOf(
            "O(1) add — no shifting or reallocation.",
            "Uniform sampling over stored transitions.",
        ),
        hints = listOf(
            "Removing from the front of a list is O(n). A ring buffer avoids the shift entirely.",
            "Keep a write cursor that wraps with modulo capacity.",
            "Grow the backing list until it reaches capacity, then start overwriting at the cursor.",
        ),
        approach = listOf(
            "Append while below capacity; once full, overwrite the slot at the cursor.",
            "Advance the cursor modulo capacity after every add.",
            "Sample by drawing random indices from the currently stored range.",
            "The point is decorrelation: consecutive transitions are highly correlated, and training on them directly destabilises the network.",
        ),
        timeComplexity = "O(1) add, O(batch) sample",
        spaceComplexity = "O(capacity)",
        solutionCode = """
            import kotlin.random.Random

            data class Transition(
                val state: Int,
                val action: Int,
                val reward: Double,
                val nextState: Int,
                val done: Boolean,
            )

            class ReplayBuffer(private val capacity: Int) {
                private val storage = ArrayList<Transition>(capacity)
                private var cursor = 0

                fun add(t: Transition) {
                    if (storage.size < capacity) storage += t else storage[cursor] = t
                    cursor = (cursor + 1) % capacity     // ring, so add stays O(1)
                }

                fun sample(batch: Int, random: Random): List<Transition> =
                    List(batch) { storage[random.nextInt(storage.size)] }

                val size: Int get() = storage.size
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("experience_replay", "Experience Replay", "Why decorrelating samples stabilises training."),
            ProblemPrereq("dqn", "DQN", "The buffer is one of the two tricks that made DQN work."),
            ProblemPrereq("queue", "Queue", "A ring buffer is the circular-array queue applied here."),
        ),
        linkedTopicId = "experience_replay",
        linkedTopicLabel = "Experience Replay",
    ),
)

internal val aiProblems: List<PracticeProblem> =
    mlProblems + neuralNetProblems + nlpProblems + rlProblems
