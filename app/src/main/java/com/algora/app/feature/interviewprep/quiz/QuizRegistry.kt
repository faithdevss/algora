package com.algora.app.feature.interviewprep.quiz

// Maps a topic id to its timed quiz. Same role as AnalysisToolRegistry: topics listed here bypass
// the 7-section TopicContent template and render QuizScreen instead. Others fall through normally.
object QuizRegistry {
    private val quizzes: Map<String, Quiz> = mapOf(
        "timed_mock_interview" to timedMockInterview,
        "faang_set" to faangSet,
        "startup_set" to startupSet,
        "finance_trading_set" to financeTradingSet,
        // Subject quizzes (QuizContentTopics.kt / QuizContentAi.kt)
        "arrays_strings_quiz" to arraysStringsQuiz,
        "linked_lists_quiz" to linkedListsQuiz,
        "stacks_queues_quiz" to stacksQueuesQuiz,
        "trees_bst_quiz" to treesBstQuiz,
        "graphs_quiz" to graphsQuiz,
        "sorting_searching_quiz" to sortingSearchingQuiz,
        "hashing_heaps_quiz" to hashingHeapsQuiz,
        "dp_greedy_quiz" to dpGreedyQuiz,
        "complexity_quiz" to complexityQuiz,
        "ml_foundations_quiz" to mlFoundationsQuiz,
        "deep_learning_quiz" to deepLearningQuiz,
        "nlp_rl_quiz" to nlpRlQuiz,
        "transformers_llm_quiz" to transformersLlmQuiz,
        "rl_algorithms_quiz" to rlAlgorithmsQuiz,
        "ai_ml_mock_interview" to aiMockInterview,
    )

    fun get(topicId: String): Quiz? = quizzes[topicId]

    // Practice tab's quiz catalog lists every set; the topic id doubles as the route target, since
    // a quiz topic renders QuizScreen from its detail page.
    val all: List<Pair<String, Quiz>> = quizzes.toList()
}
