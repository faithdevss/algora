package com.algora.app.core.data.entitlement

/**
 * The whole access model for lessons, labs, quizzes and interview rounds, in one table.
 *
 * - [freeIds] (~2%): always open.
 * - [adUnlockIds] (~5%): locked, but a rewarded ad opens one topic for 6h.
 * - everything else (~93%): subscription or lifetime only.
 *
 * Both lists are samplers, chosen to show the product at its best: a runnable lab in every pick, and
 * an even DSA/AI split. Problem groups are separate (see ProblemPattern.isPremium).
 */
object AccessTiers {
    val freeIds: Set<String> = setOf(
        // DSA
        "array", "stack", "bubble_sort", "binary_search", "bfs", "fibonacci_recursive",
        // AI
        "linear_regression", "confusion_matrix", "perceptron", "relu", "tokenization", "bellman_equation",
    )

    val adUnlockIds: Set<String> = setOf(
        // DSA
        "singly_linked_list", "queue", "tree", "graph", "string", "insertion_sort", "linear_search", "dfs",
        "hash_table", "heap", "two_pointer", "merge_sort", "quick_sort", "factorial",
        // AI
        "logistic_regression", "attention", "mlp", "missing_value_imputation", "one_hot_encoding", "accuracy",
        "precision_recall", "sigmoid", "conv_layers", "transfer_learning", "data_augmentation", "text_cleaning",
        "prompt_engineering", "agent_environment", "state_action_reward", "policy",
    )

    fun isPremium(topicId: String): Boolean = topicId !in freeIds

    fun isAdUnlockable(topicId: String): Boolean = topicId in adUnlockIds
}
