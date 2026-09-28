package com.algora.app.core.data

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section
import com.algora.app.core.data.model.Topic
import com.algora.app.feature.algorithms.AlgorithmsCategories
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.analysis.AnalysisCategories
import com.algora.app.feature.analysis.AnalysisTopics
import com.algora.app.feature.datastructures.DataStructuresCategories
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningCategories
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepCategories
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.machinelearning.MachineLearningCategories
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpCategories
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningCategories
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browsers render a category list and then the topics whose `categoryId` matches. Nothing checks
 * that those two agree, so a mistyped id produces a topic that exists in the registry, opens fine by
 * deep link, and is invisible in the browser — and a category left behind by a reorganisation renders
 * as an empty row.
 *
 * Both failure modes are live risks rather than theoretical ones: phase 9 moved ML topics into five
 * new categories and phase 10 moved `kmp`, `rabin_karp` and `manacher` out of `algo_misc`, which is
 * exactly the edit that produces an orphan.
 */
class CategoryIntegrityTest {

    private class SectionUnderTest(
        val name: String,
        val categories: List<Category>,
        val topics: List<Topic>,
    )

    private val sections = listOf(
        SectionUnderTest("DataStructures", DataStructuresCategories.all, DataStructuresTopics.topics),
        SectionUnderTest("Algorithms", AlgorithmsCategories.all, AlgorithmsTopics.topics),
        SectionUnderTest("Analysis", AnalysisCategories.all, AnalysisTopics.topics),
        SectionUnderTest("InterviewPrep", InterviewPrepCategories.all, InterviewPrepTopics.topics),
        SectionUnderTest("MachineLearning", MachineLearningCategories.all, MachineLearningTopics.topics),
        SectionUnderTest("DeepLearning", DeepLearningCategories.all, DeepLearningTopics.topics),
        SectionUnderTest("Nlp", NlpCategories.all, NlpTopics.topics),
        SectionUnderTest("ReinforcementLearning", ReinforcementLearningCategories.all, ReinforcementLearningTopics.topics),
    )

    @Test
    fun `every topic's categoryId resolves to a category in its own section`() {
        val orphans = sections.flatMap { section ->
            val known = section.categories.map { it.id }.toSet()
            section.topics
                .filter { it.categoryId !in known }
                .map { "${section.name}: ${it.id} -> categoryId '${it.categoryId}'" }
        }
        assertTrue(
            "Topics whose categoryId matches no category in their section — these render nowhere:\n" +
                orphans.joinToString("\n"),
            orphans.isEmpty(),
        )
    }

    @Test
    fun `no category is empty`() {
        val empty = sections.flatMap { section ->
            val used = section.topics.map { it.categoryId }.toSet()
            section.categories.filter { it.id !in used }.map { "${section.name}: ${it.id} (${it.name})" }
        }
        assertTrue("Categories with no topics — these render as empty rows:\n" + empty.joinToString("\n"), empty.isEmpty())
    }

    @Test
    fun `category ids are unique across the whole app`() {
        val duplicates = sections.flatMap { it.categories }
            .groupBy { it.id }
            .filter { it.value.size > 1 }
            .keys
        assertTrue("Category ids used more than once: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every category declares the section it is listed under`() {
        val expected = mapOf(
            "DataStructures" to Section.DATA_STRUCTURES,
            "Algorithms" to Section.ALGORITHMS,
            "Analysis" to Section.ANALYSIS,
            "InterviewPrep" to Section.INTERVIEW_PREP,
            "MachineLearning" to Section.ML,
            "DeepLearning" to Section.DL,
            "Nlp" to Section.NLP,
            "ReinforcementLearning" to Section.RL,
        )
        val wrong = sections.flatMap { section ->
            section.categories
                .filter { it.section != expected.getValue(section.name) }
                .map { "${section.name}: ${it.id} declares ${it.section}" }
        }
        assertTrue("Categories whose section does not match the list holding them: $wrong", wrong.isEmpty())
    }
}
