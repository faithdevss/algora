package com.algora.app.core.data

import com.algora.app.core.data.model.Topic
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.analysis.AnalysisTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics

// Aggregates topics across all sections (both DSA and AI modes) so TopicDetailScreen can resolve
// any topicId regardless of which tab it was reached from. Some ids are intentionally cross-listed
// across sections (e.g. "perceptron" in both ML and DL) — find() returns the first, which is fine
// since cross-listed entries resolve to the same detail content.
object TopicRegistry {
    private val allTopics: List<Topic> =
        DataStructuresTopics.topics + AlgorithmsTopics.topics + AnalysisTopics.topics + InterviewPrepTopics.topics +
            MachineLearningTopics.topics + DeepLearningTopics.topics + NlpTopics.topics + ReinforcementLearningTopics.topics

    // Cross-listed ids (e.g. "perceptron" in ML + DL) collapse to the first occurrence, matching the
    // prior find()-returns-first behavior. `associateBy` keeps the last, so build it first-wins.
    private val byId: Map<String, Topic> =
        allTopics.foldRight(LinkedHashMap<String, Topic>()) { topic, acc -> acc.apply { put(topic.id, topic) } }

    fun find(topicId: String): Topic? = byId[topicId]
}
