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
private val interviewStyles = InterviewPrepCategories.interviewStyles
private val mock = InterviewPrepCategories.mock
private val beginnerInterview = InterviewPrepCategories.beginnerInterview
private val advancedInterview = InterviewPrepCategories.advancedInterview
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
    topic("two_heaps_pattern", "Two Heaps", patterns, "Split the data in half to keep the median one peek away.", isPremium = true),
    topic("monotonic_deque_pattern", "Monotonic Deque (Window Max)", patterns, "Window maxima in O(n) by dropping dominated indices.", isPremium = true),
    topic("knapsack_dp_pattern", "0/1 Knapsack DP", patterns, "Take-it-or-leave-it decisions against a budget.", isPremium = true),
    topic("grid_dp_pattern", "Grid & Sequence DP", patterns, "Two indices, one table — LCS, edit distance and paths.", isPremium = true),
    topic("range_query_pattern", "Range Query Structures", patterns, "Updates and range aggregates, both in O(log n).", isPremium = true),
    topic("trie_prefix_pattern", "Trie / Prefix Search", patterns, "Answer prefix questions a hash map cannot.", isPremium = true),
    topic("multi_source_bfs_pattern", "Multi-source BFS", patterns, "One wave from every source at once.", isPremium = true),
    topic("shortest_path_pattern", "Weighted Shortest Path", patterns, "Pick BFS, Dijkstra or Bellman-Ford from the weights.", isPremium = true),
    topic("sweep_line_pattern", "Line Sweep & Difference Array", patterns, "Keep the endpoints, throw away the intervals.", isPremium = true),
    topic("running_best_pattern", "Running Best (Kadane)", patterns, "Extend or restart — best contiguous run in one pass.", isPremium = true),
    topic("rolling_hash_pattern", "Rolling Hash", patterns, "Compare every substring at O(1) per slide.", isPremium = true),
    topic("hash_counting_pattern", "Hashing & Counting", patterns, "Trade memory for the inner loop you were about to write.", isPremium = true),
    topic("modified_binary_search_pattern", "Modified Binary Search", patterns, "Boundaries, insertion points and rotated arrays.", isPremium = true),
    topic("memo_recursion_pattern", "Top-down Memoization", patterns, "Write the recursion, then cache it into a DP.", isPremium = true),
    topic("state_machine_dp_pattern", "State Machine DP", patterns, "Carry a mode, not just a position, through the array.", isPremium = true),
    topic("bitmask_state_pattern", "Bitmask State DP", patterns, "When n ≤ 20, the subset itself is the state.", isPremium = true),
    topic("divide_conquer_pattern", "Divide & Conquer", patterns, "Split, recurse, and count what crosses the middle.", isPremium = true),
    topic("matrix_transform_pattern", "Matrix Manipulation", patterns, "Rotate, spiral and search a grid in place.", isPremium = true),
    topic("graph_coloring_pattern", "Two-Colouring & Cycle Detection", patterns, "Bipartite checks and the directed/undirected cycle rules.", isPremium = true),
    topic("composite_design_pattern", "Design with Paired Structures", patterns, "O(1) everything by combining two containers.", isPremium = true),
    topic("expression_stack_pattern", "Parsing with a Stack", patterns, "Nesting means a stack — fold each context on close.", isPremium = true),
    topic("meet_in_middle_pattern", "Meet in the Middle", patterns, "Halve the exponent when n sits between 30 and 40.", isPremium = true),
    topic("randomized_pattern", "Randomised Sampling & Shuffling", patterns, "Reservoirs, unbiased shuffles and adversary-proof pivots.", isPremium = true),
    topic("prefix_function_pattern", "Prefix Function & Borders", patterns, "Periodicity and search from prefix-suffix overlaps.", isPremium = true),
    topic("interval_dp_pattern", "Interval DP", patterns, "State a range, choose what happens last inside it.", isPremium = true),
    topic("tree_dp_pattern", "Tree DP (Subtree Aggregation)", patterns, "Post-order: return to the parent, combine locally.", isPremium = true),
    topic("binary_lifting_pattern", "Binary Lifting & LCA", patterns, "Power-of-two jumps answer ancestor queries in O(log n).", isPremium = true),
    topic("dutch_flag_pattern", "In-place Partitioning", patterns, "Two or three pointers that sort into regions.", isPremium = true),
    topic("greedy_exchange_pattern", "Greedy & the Exchange Argument", patterns, "Prove the greedy choice, or find why it fails.", isPremium = true),
    topic("palindrome_expansion_pattern", "Expand Around Centre", patterns, "Enumerate 2n-1 centres, not every substring.", isPremium = true),
    topic("bst_inorder_pattern", "BST In-order Traversal", patterns, "Sorted order, iteratively, with early exit.", isPremium = true),
    topic("lis_patience_pattern", "Longest Increasing Subsequence", patterns, "Tails array plus binary search — O(n log n).", isPremium = true),
    topic("bit_trie_pattern", "Bit Trie (Maximum XOR)", patterns, "Walk the opposite bit, most significant first.", isPremium = true),
    topic("dag_dp_pattern", "DAG DP (Longest Path)", patterns, "Topological order is the iteration order.", isPremium = true),
    topic("game_theory_dp_pattern", "Game Theory DP", patterns, "Negate across the turn — the opponent plays optimally.", isPremium = true),
    topic("heap_scheduling_pattern", "Heap Scheduling", patterns, "Sort by arrival, let a heap pick what runs next.", isPremium = true),
    topic("prefix_2d_pattern", "2D Prefix Sums", patterns, "Any submatrix sum in four lookups.", isPremium = true),
)

// Taglines describe the *style* a set is written in, never its provenance: these are authored
// questions, not transcripts, and "from <company>" would claim a sourcing that does not exist.
private val interviewStyleTopics = listOf(
    topic("faang_set", "Big Tech Set", interviewStyles, "Written in the style of a big-tech screen — classic patterns, then real problems at scale.", isPremium = true),
    topic("startup_set", "Startup Set", interviewStyles, "Written in the style of a product-company screen — design and hashing, then real product problems.", isPremium = true),
    topic("finance_trading_set", "Finance / Trading Set", interviewStyles, "Written in the style of a quant-desk screen — latency and math, then order books and pricing.", isPremium = true),
)

private val mockTopics = listOf(
    topic("timed_mock_interview", "Timed Mock Interview", mock, "Full-length interview under a countdown timer."),
    topic("ai_ml_mock_interview", "AI/ML Mock Interview", mock, "Mixed ML, DL, NLP and RL questions under a clock."),
    topic("behavioral_question_bank", "Behavioral Question Bank", mock, "Common behavioral questions with guidance.", isPremium = true),
    topic("system_design_primer", "System Design Primer", mock, "Foundations for system design interview rounds.", isPremium = true),
)

// Each id is also its QuizRegistry key, like the subject quizzes below.
private val beginnerInterviewTopics = listOf(
    topic("beginner_arrays_strings_set", "Arrays & Strings Warm-up", beginnerInterview, "Indexing, two pointers, windows and counting."),
    topic("beginner_hashing_set", "Hash Maps & Sets Warm-up", beginnerInterview, "Lookups, counting and membership in O(1)."),
    topic("beginner_big_o_set", "Big-O Warm-up", beginnerInterview, "Read a loop, name its cost, check it fits the input."),
    topic("beginner_linear_structures_set", "Stacks, Queues & Lists Warm-up", beginnerInterview, "Back buttons, print queues and undo."),
    topic("beginner_recursion_search_set", "Recursion, Sorting & Search Warm-up", beginnerInterview, "Recursion, binary search variants and one-pass sorting."),
    topic("beginner_coding_arrays_set", "Coding Round: Arrays & Hashing", beginnerInterview, "Products, triplets, Sudoku checks and matrix walks."),
    topic("beginner_coding_lists_set", "Coding Round: Linked Lists & Stacks", beginnerInterview, "Palindromes, intersections, paths and decoding."),
    topic("beginner_coding_trees_set", "Coding Round: Trees", beginnerInterview, "Depth, symmetry, path sums and balanced BSTs."),
    topic("beginner_picture_set", "Picture Round: Read the Diagram", beginnerInterview, "Look at the array, tree, grid or graph and solve it."),
    topic("beginner_story_set", "Story Round: The Food Delivery App", beginnerInterview, "Orders, routes and rider shifts at a startup."),
)

private val advancedInterviewTopics = listOf(
    topic("advanced_graphs_set", "Advanced Graphs Round", advancedInterview, "Weighted paths, cycles, components and spanning trees.", isPremium = true),
    topic("advanced_dp_set", "Advanced DP Round", advancedInterview, "State design, recurrences and where greedy breaks.", isPremium = true),
    topic("advanced_data_structures_set", "Advanced Data Structures Round", advancedInterview, "Range queries, time-indexed stores and O(1) designs.", isPremium = true),
    topic("advanced_systems_scenarios_set", "Scenario Round: Real Systems", advancedInterview, "Rate limits, logs, trends and calendars, solved.", isPremium = true),
    topic("advanced_constraints_scenarios_set", "Scenario Round: Constraints & Trade-offs", advancedInterview, "Let n, memory and accuracy pick the algorithm.", isPremium = true),
    topic("advanced_coding_trees_graphs_set", "Coding Round: Trees & Graphs", advancedInterview, "LCA, serialisation, word ladders and alien alphabets.", isPremium = true),
    topic("advanced_coding_dp_set", "Coding Round: DP & Backtracking", advancedInterview, "Grid paths, partitions, regex matching and palindrome cuts.", isPremium = true),
    topic("advanced_coding_hard_set", "Coding Round: Hard Classics", advancedInterview, "Rain water, histograms and two-array medians.", isPremium = true),
    topic("advanced_picture_set", "Picture Round: Solve the Diagram", advancedInterview, "Weighted graphs, DP tables, heaps and windows, drawn out.", isPremium = true),
    topic("advanced_story_set", "Story Round: On Call at StreamFlix", advancedInterview, "An outage, an error spike and a cache, one night.", isPremium = true),
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
    topic("recursion_backtracking_quiz", "Recursion & Backtracking Quiz", topicQuizzes, "Base cases, call-stack cost, pruning and un-choosing."),
    topic("bit_manipulation_quiz", "Bit Manipulation Quiz", topicQuizzes, "Masks, XOR pairing, low-bit tricks and bitmask states."),
    topic("string_matching_quiz", "String Matching Quiz", topicQuizzes, "Prefix functions, rolling hashes, palindromes and tries."),
    topic("math_number_theory_quiz", "Math & Number Theory Quiz", topicQuizzes, "GCD, modular arithmetic, fast power and primes."),
    topic("ml_foundations_quiz", "ML Foundations Quiz", topicQuizzes, "Bias/variance, loss functions and evaluation."),
    topic("deep_learning_quiz", "Deep Learning Quiz", topicQuizzes, "Backpropagation, activations and regularisation."),
    topic("nlp_rl_quiz", "NLP & RL Quiz", topicQuizzes, "Embeddings, attention, rewards and policies."),
    topic("transformers_llm_quiz", "Transformers & LLMs Quiz", topicQuizzes, "Attention cost, positional information and decoding."),
    topic("rl_algorithms_quiz", "RL Algorithms Quiz", topicQuizzes, "Value versus policy methods and stability tricks."),
    topic("unsupervised_learning_quiz", "Unsupervised Learning Quiz", topicQuizzes, "Clustering assumptions and dimensionality reduction."),
    topic("computer_vision_quiz", "Computer Vision Quiz", topicQuizzes, "Convolutions, receptive fields, detection and segmentation."),
    topic("data_preprocessing_quiz", "Data Preprocessing Quiz", topicQuizzes, "Imputation, encoding, scaling, imbalance and leakage."),
    topic("optimizers_training_quiz", "Optimizers & Training Quiz", topicQuizzes, "Momentum, adaptive rates, schedules and the losses they minimize."),
    topic("activation_functions_quiz", "Activation Functions Quiz", topicQuizzes, "ReLU, sigmoid, GELU and the saturation, dead-unit trade-offs."),
)

// AI-mode interview rounds. The AI/ML mock lives under Mock beside the DSA mock, so every timed mock
// round is in one place. The ML Engineer set sits here rather than under Interview Styles: it is
// scoped by role, not by an interview house style, and its neighbour is the other ML round.
private val aiInterviewTopics = listOf(
    topic("ml_engineer_set", "ML Engineer Set", aiInterview, "An ML-engineer screen — modelling plus production reality.", isPremium = true),
    topic("ai_nlp_interview_set", "NLP Interview Round", aiInterview, "Sentiment, search, entities, translation and speech.", isPremium = true),
    topic("ai_llm_engineering_set", "LLM & GenAI Engineering Round", aiInterview, "RAG, fine-tuning, agents, evals and prompt injection.", isPremium = true),
    topic("ai_cv_interview_set", "Computer Vision Interview Round", aiInterview, "Factories, hospitals, phones and satellites.", isPremium = true),
    topic("ai_recsys_interview_set", "Recommender Systems Round", aiInterview, "Cold start, ranking, scale and feedback loops.", isPremium = true),
    topic("ai_sector_interview_set", "AI Across Industries Round", aiInterview, "Healthcare, finance, retail, factories, farms, logistics.", isPremium = true),
    topic("ml_system_design_primer", "ML System Design Primer", aiInterview, "Framing, data, serving, drift and retraining.", isPremium = true),
)

object InterviewPrepTopics {
    val topics: List<Topic> = patternTopics + interviewStyleTopics + mockTopics + beginnerInterviewTopics + advancedInterviewTopics +
        quizTopics + aiInterviewTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
