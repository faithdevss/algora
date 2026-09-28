package com.algora.app.core.data.entitlement

import com.algora.app.feature.interviewprep.InterviewPrepCategories
import com.algora.app.feature.interviewprep.InterviewPrepTopics

/**
 * Practice content — the timed interview sets, the behavioral bank, the system-design primers and
 * the premium problem groups — opens only with the lifetime purchase, and so do a few flagship
 * lessons (see [flagshipLessonIds]). A rewarded ad still opens every other Learning topic, pattern
 * guide and simulation for 6h, but never these: they are what the purchase is sold on.
 *
 * Problem groups are not listed here. Their ids share a namespace with Learning topics
 * (`sliding_window` is both), so they are gated where they are checked — ProblemListScreen and
 * ProblemDetailScreen — rather than by id, which would lock the Learning topic too.
 */
object PaidOnly {
    // Every Interview Prep row except the pattern guides, which are topic pages with labs and keep
    // the ad unlock like the rest of Learning. These ids collide with nothing else.
    private val topicIds: Set<String> by lazy {
        InterviewPrepTopics.topics
            .filter { it.categoryId != InterviewPrepCategories.patterns.id }
            .map { it.id }
            .toSet()
    }

    /**
     * The most in-demand and most advanced lessons in each track — the interview staples and advanced
     * structures of DSA, and the current core and frontier models of AI. Ids that appear in two sections (transformers in DL and NLP, RLHF in NLP and RL) are the same
     * lesson listed twice, so one entry covers both. Fundamentals such as hash tables, heaps, sliding
     * window and two pointers stay ad-unlockable on purpose: a free user needs a real taste of the
     * premium material before paying for the rest.
     */
    val flagshipLessonIds: Set<String> = setOf(
        // DSA — interview staples
        "dijkstras_algorithm", "topological_sort", "a_star_search", "trie", "lru_cache", "disjoint_set",
        "segment_tree", "knapsack_01", "longest_common_subsequence", "edit_distance", "bitmask_dp", "kmp",
        // DSA — advanced structures and algorithms
        "b_tree", "suffix_tree", "skip_list", "sparse_table", "bloom_filter", "kd_tree", "fenwick_tree",
        "avl_red_black_tree", "max_flow", "tarjans_algorithm", "kosarajus_algorithm", "articulation_points",
        "floyd_warshall", "bellman_ford", "matrix_chain_multiplication", "aho_corasick", "manacher",
        "convex_hull", "tree_dp", "longest_increasing_subsequence",
        // AI — the current core
        "transformers", "llms", "rag", "vector_databases", "ai_agents", "lora_qlora", "rlhf",
        "diffusion_models", "vit", "yolo", "xgboost", "ppo",
        // AI — advanced models and methods, by track: ML, DL, NLP, RL
        "lightgbm", "catboost", "stacking", "bayesian_networks", "mcmc", "umap", "prophet",
        "resnet", "efficientnet", "faster_rcnn", "mask_rcnn", "unet", "gpt", "gans", "stylegan",
        "stable_diffusion", "gcn",
        "gpt3_gpt4", "llama_vicuna", "mistral_mixtral", "claude_gemini", "dpo", "peft", "quantization",
        "flash_attention", "mamba", "hallucination_mitigation",
        "sac", "td3", "alphazero", "muzero", "dreamer", "offline_rl", "decision_transformer", "mcts", "maddpg",
        // Specialist lessons that keep every section at 20% or more (PaidOnlyTest holds that floor)
        "master_theorem", "amortized_analysis", "benchmark_dashboard", "sandbox_mode",
        "elasticnet_regression", "quantile_regression", "bayesian_ridge", "poisson_regression", "lars", "nu_svc",
        "qda", "extra_trees", "voting", "adaboost", "hdbscan", "spectral_clustering", "gmm", "kernel_pca",
        "sarima", "restricted_boltzmann_machines",
        "densenet", "inception", "retinanet", "cyclegan", "capsule_networks",
        "trpo", "rainbow_dqn", "qmix", "meta_rl",
    )

    fun isPaidOnlyTopic(topicId: String): Boolean = topicId in topicIds || topicId in flagshipLessonIds
}
