package com.algora.app.core.data

import com.algora.app.core.data.model.Category
import com.algora.app.feature.algorithms.AlgorithmsCategories
import com.algora.app.feature.analysis.AnalysisCategories
import com.algora.app.feature.datastructures.DataStructuresCategories
import com.algora.app.feature.deeplearning.DeepLearningCategories
import com.algora.app.feature.interviewprep.InterviewPrepCategories
import com.algora.app.feature.machinelearning.MachineLearningCategories
import com.algora.app.feature.nlp.NlpCategories
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningCategories

// Category counterpart of TopicRegistry: resolves a Topic.categoryId to its Category regardless of
// which section owns it. The Simulations tab uses it to group its catalog the same way the browser
// screens do (section -> category -> rows).
object CategoryRegistry {
    val all: List<Category> =
        DataStructuresCategories.all + AlgorithmsCategories.all + AnalysisCategories.all + InterviewPrepCategories.all +
            MachineLearningCategories.all + DeepLearningCategories.all + NlpCategories.all + ReinforcementLearningCategories.all

    private val byId: Map<String, Category> = all.associateBy { it.id }

    fun find(categoryId: String): Category? = byId[categoryId]
}
