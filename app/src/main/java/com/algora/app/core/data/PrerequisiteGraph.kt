package com.algora.app.core.data

// Curated learning-order DAG over core DSA topics. Edge topic → [prereqs] means "learn the prereqs
// first". Not exhaustive (the taxonomy is large) — it covers the backbone so the detail page can
// show a "learn first / unlocks" path. Every id here must exist in TopicRegistry.
object PrerequisiteGraph {
    private val prerequisites: Map<String, List<String>> = mapOf(
        // Linear structures
        "string" to listOf("array"),
        "singly_linked_list" to listOf("array"),
        "doubly_linked_list" to listOf("singly_linked_list"),
        "stack" to listOf("array"),
        "queue" to listOf("array"),
        "hash_table" to listOf("array"),
        // Non-linear
        "tree" to listOf("singly_linked_list"),
        "binary_search_tree" to listOf("tree"),
        "heap" to listOf("tree", "array"),
        "trie" to listOf("tree"),
        "graph" to listOf("tree"),
        // Traversal / search
        "binary_search" to listOf("array"),
        "bfs" to listOf("graph", "queue"),
        "dfs" to listOf("graph", "stack"),
        // Sorting
        "merge_sort" to listOf("array", "factorial"),
        "quick_sort" to listOf("array"),
        "heap_sort" to listOf("heap"),
        // Graph algorithms
        "dijkstras_algorithm" to listOf("graph", "heap"),
        // Recursion / backtracking
        "fibonacci_recursive" to listOf("factorial"),
        "tower_of_hanoi" to listOf("fibonacci_recursive"),
        "n_queens" to listOf("dfs"),
        // Dynamic programming
        "fibonacci_dp" to listOf("fibonacci_recursive"),
        "longest_common_subsequence" to listOf("fibonacci_dp"),
        "edit_distance" to listOf("longest_common_subsequence"),
        "coin_change" to listOf("fibonacci_dp"),
        "knapsack_01" to listOf("coin_change"),
        "bitmask_dp" to listOf("knapsack_01", "subset_sum"),
        "tree_dp" to listOf("tree", "dfs"),
        // Linear structures, continued
        "deque" to listOf("queue", "stack"),
        "sliding_window" to listOf("deque"),
        // Static range queries
        "sparse_table" to listOf("binary_search", "prefix_sum"),
        "lca" to listOf("tree", "sparse_table"),
        // String algorithms
        "kmp" to listOf("string"),
        "rabin_karp" to listOf("string", "hash_table"),
        "manacher" to listOf("kmp"),
        // Graph algorithms, continued
        "topological_sort" to listOf("dfs", "queue"),
        "articulation_points" to listOf("dfs"),
        "max_flow" to listOf("bfs", "graph"),
        // Sorting, continued
        "bucket_sort" to listOf("insertion_sort", "counting_sort"),
        // ML: the practice layer sits on top of a first model
        "bias_variance" to listOf("linear_regression"),
        "regularization" to listOf("linear_regression", "bias_variance"),
        "model_evaluation" to listOf("logistic_regression"),
        "random_forest" to listOf("decision_trees"),
        "gradient_boosting" to listOf("decision_trees", "gradient_descent_variants"),
        // DL: training techniques presuppose the training loop
        "batch_normalization" to listOf("neural_network_basics", "backpropagation"),
        "dropout" to listOf("neural_network_basics", "regularization"),
        "transfer_learning" to listOf("cnn"),
        "diffusion_models" to listOf("neural_network_basics"),
        // NLP
        "bpe" to listOf("tokenization"),
        "ner" to listOf("tokenization", "rnn_lstm"),
        "rag" to listOf("word_embeddings", "llms"),
    )

    fun prereqsOf(topicId: String): List<String> = prerequisites[topicId].orEmpty()

    // Reverse edges: topics that list [topicId] among their prerequisites.
    fun unlockedBy(topicId: String): List<String> =
        prerequisites.filter { topicId in it.value }.keys.toList()
}
