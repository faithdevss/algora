package com.algora.app.feature.interviewprep

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Topic

private fun topic(id: String, name: String, category: Category, tagline: String, isPremium: Boolean = false) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = category.iconName,
    accentColor = category.accentColor,
    isPremium = isPremium,
)

private val patterns = InterviewPrepCategories.patterns
private val companySets = InterviewPrepCategories.companySets
private val mock = InterviewPrepCategories.mock
private val topicQuizzes = InterviewPrepCategories.topicQuizzes
private val aiInterview = InterviewPrepCategories.aiInterview

private val patternTopics = listOf(
    topic("sliding_window_pattern", "Sliding Window Pattern", patterns, "Recognize and apply the sliding-window pattern.", isPremium = true),
    topic("two_pointer_pattern", "Two Pointer Pattern", patterns, "Recognize and apply the two-pointer pattern.", isPremium = true),
    topic("fast_slow_pointers", "Fast & Slow Pointers", patterns, "Cycle detection and midpoint-finding pattern.", isPremium = true),
    topic("merge_intervals_pattern", "Merge Intervals", patterns, "Recognize and apply the merge-intervals pattern.", isPremium = true),
    topic("top_k_pattern", "Top-K Pattern", patterns, "Recognize and apply the top-k heap pattern.", isPremium = true),
    topic("prefix_sum_pattern", "Prefix Sum Pattern", patterns, "Turn repeated range questions into two lookups.", isPremium = true),
    topic("binary_search_answer", "Binary Search on Answer", patterns, "Search the answer space, not the array.", isPremium = true),
    topic("monotonic_stack_pattern", "Monotonic Stack", patterns, "Next-greater / previous-smaller in one pass.", isPremium = true),
    topic("cyclic_sort_pattern", "Cyclic Sort", patterns, "Place 1..n values by index to find what's missing.", isPremium = true),
    topic("in_place_reversal_pattern", "In-place Linked List Reversal", patterns, "Rewire next-pointers with three moving references.", isPremium = true),
    topic("k_way_merge_pattern", "K-way Merge", patterns, "Merge k sorted inputs through a size-k heap.", isPremium = true),
    topic("greedy_intervals_pattern", "Greedy Intervals", patterns, "Sort by end time and take the earliest finisher.", isPremium = true),
    topic("backtracking_pattern", "Backtracking Pattern", patterns, "Choose, explore, un-choose — with pruning.", isPremium = true),
    topic("subsets_pattern", "Subsets & Combinations", patterns, "Enumerate the power set without duplicates.", isPremium = true),
    topic("tree_bfs_pattern", "Tree BFS (Level Order)", patterns, "Queue-driven level-by-level tree traversal.", isPremium = true),
    topic("tree_dfs_pattern", "Tree DFS (Path Sum)", patterns, "Recursive root-to-leaf paths carrying state down.", isPremium = true),
    topic("topological_sort_pattern", "Topological Sort Pattern", patterns, "Order tasks under prerequisite constraints.", isPremium = true),
    topic("union_find_pattern", "Union-Find Pattern", patterns, "Group merging and connectivity queries.", isPremium = true),
    topic("matrix_islands_pattern", "Matrix Traversal (Islands)", patterns, "Flood-fill a grid to count connected regions.", isPremium = true),
    topic("bit_manipulation_pattern", "Bit Manipulation Pattern", patterns, "XOR pairing, masks and low-bit tricks.", isPremium = true),
)

private val companySetTopics = listOf(
    topic("faang_set", "FAANG Set", companySets, "Curated question set from large tech companies.", isPremium = true),
    topic("startup_set", "Startup Set", companySets, "Curated question set from startup-style interviews.", isPremium = true),
    topic("finance_trading_set", "Finance / Trading Set", companySets, "Curated question set from finance and trading firms.", isPremium = true),
)

private val mockTopics = listOf(
    topic("timed_mock_interview", "Timed Mock Interview", mock, "Full-length interview under a countdown timer."),
    topic("behavioral_question_bank", "Behavioral Question Bank", mock, "Common behavioral questions with guidance.", isPremium = true),
    topic("system_design_primer", "System Design Primer", mock, "Foundations for system design interview rounds.", isPremium = true),
)

// One quiz per subject area. Each id is also its QuizRegistry key — the detail page renders
// QuizScreen instead of the 7-section template.
private val quizTopics = listOf(
    topic("arrays_strings_quiz", "Arrays & Strings Quiz", topicQuizzes, "Indexing, shifting, windows and in-place edits."),
    topic("linked_lists_quiz", "Linked Lists Quiz", topicQuizzes, "Pointer rewiring, cycles and traversal costs."),
    topic("stacks_queues_quiz", "Stacks & Queues Quiz", topicQuizzes, "LIFO, FIFO, monotonic stacks and deques."),
    topic("trees_bst_quiz", "Trees & BST Quiz", topicQuizzes, "Traversal orders, balance and the BST invariant."),
    topic("graphs_quiz", "Graphs Quiz", topicQuizzes, "Representations, BFS/DFS and shortest paths."),
    topic("sorting_searching_quiz", "Sorting & Searching Quiz", topicQuizzes, "Stability, pivots and logarithmic search."),
    topic("hashing_heaps_quiz", "Hashing & Heaps Quiz", topicQuizzes, "Collisions, load factor and priority queues."),
    topic("dp_greedy_quiz", "DP & Greedy Quiz", topicQuizzes, "States, recurrences and when greedy is provably safe."),
    topic("complexity_quiz", "Complexity Analysis Quiz", topicQuizzes, "Big-O, amortized cost and space trade-offs."),
    topic("ml_foundations_quiz", "ML Foundations Quiz", topicQuizzes, "Bias/variance, loss functions and evaluation."),
    topic("deep_learning_quiz", "Deep Learning Quiz", topicQuizzes, "Backpropagation, activations and regularisation."),
    topic("nlp_rl_quiz", "NLP & RL Quiz", topicQuizzes, "Embeddings, attention, rewards and policies."),
    topic("transformers_llm_quiz", "Transformers & LLMs Quiz", topicQuizzes, "Attention cost, positional information and decoding."),
    topic("rl_algorithms_quiz", "RL Algorithms Quiz", topicQuizzes, "Value versus policy methods and stability tricks."),
)

// AI-mode interview rounds, mirroring the DSA mock + system-design pair.
private val aiInterviewTopics = listOf(
    topic("ai_ml_mock_interview", "AI/ML Mock Interview", aiInterview, "Mixed ML, DL, NLP and RL questions under a clock."),
    topic("ml_system_design_primer", "ML System Design Primer", aiInterview, "Framing, data, serving, drift and retraining.", isPremium = true),
)

object InterviewPrepTopics {
    val topics: List<Topic> = patternTopics + companySetTopics + mockTopics + quizTopics + aiInterviewTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
