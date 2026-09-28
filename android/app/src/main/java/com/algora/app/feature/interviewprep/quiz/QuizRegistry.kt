package com.algora.app.feature.interviewprep.quiz

// Maps a topic id to its timed quiz. Same role as AnalysisToolRegistry: topics listed here bypass
// the 7-section TopicContent template and render QuizScreen instead. Others fall through normally.
object QuizRegistry {
    private val quizzes: Map<String, Quiz> = mapOf(
        "timed_mock_interview" to timedMockInterview,
        "faang_set" to bigTechSet,
        "startup_set" to startupSet,
        "finance_trading_set" to financeTradingSet,
        "ml_engineer_set" to mlEngineerSet,
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
        // Second DSA batch (QuizContentTopicsExtra.kt)
        "recursion_backtracking_quiz" to recursionBacktrackingQuiz,
        "bit_manipulation_quiz" to bitManipulationQuiz,
        "string_matching_quiz" to stringMatchingQuiz,
        "math_number_theory_quiz" to mathNumberTheoryQuiz,
        "ml_foundations_quiz" to mlFoundationsQuiz,
        "deep_learning_quiz" to deepLearningQuiz,
        "nlp_rl_quiz" to nlpRlQuiz,
        "transformers_llm_quiz" to transformersLlmQuiz,
        "rl_algorithms_quiz" to rlAlgorithmsQuiz,
        // Second AI batch (QuizContentAiExtra.kt)
        "unsupervised_learning_quiz" to unsupervisedLearningQuiz,
        "computer_vision_quiz" to computerVisionQuiz,
        "data_preprocessing_quiz" to dataPreprocessingQuiz,
        "ai_ml_mock_interview" to aiMockInterview,
        // Third AI batch (QuizContentAiTraining.kt)
        "optimizers_training_quiz" to optimizersTrainingQuiz,
        "activation_functions_quiz" to activationFunctionsQuiz,
        // Beginner interview sets (QuizContentBeginner.kt)
        "beginner_arrays_strings_set" to beginnerArraysStringsSet,
        "beginner_hashing_set" to beginnerHashingSet,
        "beginner_big_o_set" to beginnerBigOSet,
        "beginner_linear_structures_set" to beginnerLinearStructuresSet,
        "beginner_recursion_search_set" to beginnerRecursionSearchSet,
        // Beginner coding rounds (QuizContentCoding.kt)
        "beginner_coding_arrays_set" to beginnerCodingArraysSet,
        "beginner_coding_lists_set" to beginnerCodingListsSet,
        "beginner_coding_trees_set" to beginnerCodingTreesSet,
        // Picture rounds (QuizContentPictures.kt)
        "beginner_picture_set" to beginnerPictureSet,
        // Story rounds (QuizContentStories.kt)
        "beginner_story_set" to beginnerStorySet,
        // Advanced interview sets (QuizContentAdvanced.kt)
        "advanced_graphs_set" to advancedGraphsSet,
        "advanced_dp_set" to advancedDpSet,
        "advanced_data_structures_set" to advancedDataStructuresSet,
        "advanced_systems_scenarios_set" to advancedSystemsScenariosSet,
        "advanced_constraints_scenarios_set" to advancedConstraintsScenariosSet,
        // Advanced coding rounds (QuizContentCoding.kt)
        "advanced_coding_trees_graphs_set" to advancedCodingTreesGraphsSet,
        "advanced_coding_dp_set" to advancedCodingDpSet,
        "advanced_coding_hard_set" to advancedCodingHardSet,
        "advanced_picture_set" to advancedPictureSet,
        "advanced_story_set" to advancedStorySet,
        // AI interview rounds by field (QuizContentAiInterview.kt)
        "ai_nlp_interview_set" to aiNlpInterviewSet,
        "ai_llm_engineering_set" to aiLlmEngineeringSet,
        "ai_cv_interview_set" to aiCvInterviewSet,
        "ai_recsys_interview_set" to aiRecsysInterviewSet,
        "ai_sector_interview_set" to aiSectorInterviewSet,
    )

    fun get(topicId: String): Quiz? = quizzes[topicId]

    // Practice tab's quiz catalog lists every set; the topic id doubles as the route target, since
    // a quiz topic renders QuizScreen from its detail page.
    val all: List<Pair<String, Quiz>> = quizzes.toList()
}
