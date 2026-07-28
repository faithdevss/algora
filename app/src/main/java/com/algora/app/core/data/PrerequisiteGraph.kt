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
        "partition_problem" to listOf("subset_sum"),
        // Greedy, continued — both gap-fill topics are about when the greedy choice is safe
        "activity_selection" to listOf("fractional_knapsack"),
        "coin_change_greedy" to listOf("fractional_knapsack", "coin_change"),
        // Linear structures, continued
        "deque" to listOf("queue", "stack"),
        "sliding_window" to listOf("deque"),
        // Static range queries
        "sparse_table" to listOf("binary_search", "prefix_sum"),
        "lca" to listOf("tree", "sparse_table"),
        // Bit manipulation: the place-value idioms first, then what they are used to build
        "count_set_bits" to listOf("bit_basics"),
        "subsets_bitmask" to listOf("bit_basics"),
        "xor_tricks" to listOf("bit_basics", "prefix_sum"),
        // Math & number theory: gcd first, then the modulus, then everything the modulus enables
        "modular_arithmetic" to listOf("euclid_gcd"),
        "fast_power" to listOf("binary_search"),
        "modular_exponentiation" to listOf("fast_power", "modular_arithmetic"),
        "fermats_little_theorem" to listOf("modular_exponentiation"),
        "chinese_remainder_theorem" to listOf("modular_arithmetic", "euclid_gcd"),
        "sieve_of_eratosthenes" to listOf("array"),
        // String algorithms
        "naive_string_search" to listOf("string"),
        "kmp" to listOf("naive_string_search"),
        "rabin_karp" to listOf("naive_string_search", "hash_table"),
        "z_algorithm" to listOf("naive_string_search"),
        "manacher" to listOf("longest_palindromic_substring", "z_algorithm"),
        "longest_palindromic_substring" to listOf("string", "two_pointer"),
        "longest_common_substring" to listOf("longest_common_subsequence"),
        "aho_corasick" to listOf("trie", "kmp", "bfs"),
        // Graph algorithms, continued
        "topological_sort" to listOf("dfs", "queue"),
        "articulation_points" to listOf("dfs"),
        "max_flow" to listOf("bfs", "graph"),
        "eulerian_path" to listOf("graph", "dfs"),
        "hamiltonian_path" to listOf("eulerian_path", "n_queens"),
        // Pathfinding: Dijkstra is UCS under another name, and IDA* is A* with the frontier removed
        "uniform_cost_search" to listOf("dijkstras_algorithm"),
        "ida_star" to listOf("a_star_search", "dfs"),
        // Computational geometry — the orientation test first, then everything built on a hull
        "convex_hull" to listOf("polygon_area"),
        "line_intersection" to listOf("polygon_area"),
        "rotating_calipers" to listOf("convex_hull", "two_pointer"),
        // Sorting, continued
        "bucket_sort" to listOf("insertion_sort", "counting_sort"),
        // ML regression: the plain fit, then the penalties, then the estimators that change the loss
        "polynomial_regression" to listOf("linear_regression"),
        "ridge_regression" to listOf("polynomial_regression", "bias_variance"),
        "lasso_regression" to listOf("ridge_regression"),
        "elasticnet_regression" to listOf("lasso_regression", "ridge_regression"),
        "lars" to listOf("lasso_regression"),
        "stepwise_regression" to listOf("linear_regression", "model_evaluation"),
        "robust_regression" to listOf("linear_regression"),
        "quantile_regression" to listOf("linear_regression"),
        "bayesian_ridge" to listOf("ridge_regression", "naive_bayes"),
        "poisson_regression" to listOf("linear_regression", "logistic_regression"),
        "isotonic_regression" to listOf("linear_regression"),
        // ML classification: the kernel and discriminant families
        "svm_rbf" to listOf("svm"),
        "nu_svc" to listOf("svm_rbf"),
        "lda" to listOf("logistic_regression", "naive_bayes"),
        "qda" to listOf("lda", "bias_variance"),
        "passive_aggressive" to listOf("perceptron", "svm"),
        // ML Bayesian: the shared idea, then the likelihoods, then dropping the naive assumption
        "gaussian_nb" to listOf("naive_bayes", "qda"),
        "multinomial_nb" to listOf("naive_bayes"),
        "bernoulli_nb" to listOf("multinomial_nb"),
        "complement_nb" to listOf("multinomial_nb", "model_evaluation"),
        "categorical_nb" to listOf("naive_bayes"),
        "bayesian_networks" to listOf("naive_bayes", "graph"),
        "mcmc" to listOf("bayesian_networks", "bayesian_ridge"),
        // ML ensembles: averaging independent models, then correcting sequential errors
        "bagging" to listOf("decision_trees", "bias_variance"),
        "extra_trees" to listOf("random_forest"),
        "voting" to listOf("logistic_regression", "decision_trees"),
        "stacking" to listOf("voting", "model_evaluation"),
        "adaboost" to listOf("decision_trees", "bagging"),
        "xgboost" to listOf("gradient_boosting", "regularization"),
        "lightgbm" to listOf("xgboost"),
        "catboost" to listOf("xgboost", "categorical_nb"),
        "isolation_forest" to listOf("random_forest"),
        // ML clustering: centroid family, hierarchical family, density family, then the rest
        "k_medians" to listOf("kmeans"),
        "k_modes" to listOf("kmeans", "categorical_nb"),
        "hierarchical_divisive" to listOf("hierarchical_clustering"),
        "optics" to listOf("dbscan"),
        "hdbscan" to listOf("optics"),
        "mean_shift" to listOf("kmeans"),
        "birch" to listOf("hierarchical_clustering", "b_tree"),
        "affinity_propagation" to listOf("kmeans"),
        "spectral_clustering" to listOf("kmeans", "pca", "graph"),
        "gmm" to listOf("kmeans", "qda"),
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
        // RL: the vocabulary comes before the quantities defined over it, which come before control
        "state_action_reward" to listOf("agent_environment"),
        "policy" to listOf("state_action_reward"),
        "mdp" to listOf("state_action_reward", "policy"),
        "discount_factor" to listOf("state_action_reward"),
        "value_function" to listOf("mdp", "discount_factor"),
        "q_function" to listOf("value_function"),
        "q_learning" to listOf("q_function", "exploration_exploitation"),
        "exploration_exploitation" to listOf("agent_environment"),
        "pomdp" to listOf("mdp"),
        // RL tabular: the equation, then the two ways to solve it with a model, then the two
        // without one, then control
        "bellman_equation" to listOf("value_function"),
        "dynamic_programming" to listOf("bellman_equation"),
        "policy_iteration" to listOf("dynamic_programming"),
        "value_iteration" to listOf("policy_iteration"),
        "monte_carlo_rl" to listOf("bellman_equation", "policy"),
        "td_learning" to listOf("monte_carlo_rl", "dynamic_programming"),
        "sarsa" to listOf("td_learning", "q_function"),
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
