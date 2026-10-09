package com.algora.app.export

import com.algora.app.core.data.CategoryRegistry
import com.algora.app.core.data.PrerequisiteGraph
import com.algora.app.core.data.model.Topic
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.analysis.AnalysisTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.interviewprep.behavioral.BehavioralRegistry
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.interviewprep.systemdesign.SYSTEM_DESIGN_PRIMER_ID
import com.algora.app.feature.interviewprep.systemdesign.SystemDesignRegistry
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.practice.problems.ProblemRegistry
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.review.flashcardDecks
import com.algora.app.feature.topics.content.TopicContentProvider
import com.algora.app.feature.topics.hashingLabExports
import com.algora.app.feature.topics.tokenizerLabConfigs
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

// Writes the app's content (topics, pages, quizzes, problems, decks) as JSON for the iOS app, which
// reads the same data instead of keeping a hand-ported copy. Kotlin stays the source of truth: edit
// content here, then re-run
//
//     EXPORT_IOS_CONTENT=1 ./gradlew :app:testDebugUnitTest --tests '*IosContentExportTest*'
//
// Skipped otherwise, so a normal test run never rewrites files outside the module.
class IosContentExportTest {

    @Test
    fun exportContent() {
        assumeTrue(System.getenv("EXPORT_IOS_CONTENT") == "1")
        val out = File("../../ios/AlgorAI/Resources/Content").apply { mkdirs() }

        write(
            out, "catalog.json",
            mapOf(
                "categories" to CategoryRegistry.all,
                "sections" to linkedMapOf(
                    "DATA_STRUCTURES" to DataStructuresTopics.topics,
                    "ALGORITHMS" to AlgorithmsTopics.topics,
                    "ANALYSIS" to AnalysisTopics.topics,
                    "INTERVIEW_PREP" to InterviewPrepTopics.topics,
                    "ML" to MachineLearningTopics.topics,
                    "DL" to DeepLearningTopics.topics,
                    "NLP" to NlpTopics.topics,
                    "RL" to ReinforcementLearningTopics.topics,
                ),
                "prerequisites" to privateField(PrerequisiteGraph, "prerequisites"),
                // Registry order of the topic pages — JSON objects lose it, and the Simulations
                // catalog and Home's featured-lab rotation both depend on it.
                "contentOrder" to TopicContentProvider.all.keys.toList(),
            ),
        )
        write(out, "topic_content.json", TopicContentProvider.all)
        write(out, "quizzes.json", QuizRegistry.all.map { (id, quiz) -> mapOf("topicId" to id, "quiz" to quiz) })
        write(
            out, "problems.json",
            mapOf("patterns" to ProblemRegistry.patterns, "problems" to ProblemRegistry.all),
        )
        write(
            out, "interview.json",
            mapOf(
                "behavioral" to listOfNotNull(BehavioralRegistry.get("behavioral_question_bank")),
                "systemDesign" to listOfNotNull(
                    SystemDesignRegistry.get(SYSTEM_DESIGN_PRIMER_ID),
                    SystemDesignRegistry.get("ml_system_design_primer"),
                ),
            ),
        )
        write(out, "flashcards.json", flashcardDecks)
        // The tokenizer lab's frames, built from the real trainers in PretrainMath.kt, so iOS plays
        // the same numbers without a Swift port of them.
        write(out, "tokenizer_labs.json", tokenizerLabConfigs)
        // The hashing lab's frames (hash table, set, map, bloom filter, LRU cache, prefix counting).
        write(out, "hashing_labs.json", hashingLabExports)
    }

    private fun write(dir: File, name: String, value: Any?) {
        File(dir, name).writeText(StringBuilder().also { encode(value, it) }.toString())
    }

    private fun privateField(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    // Reflection encoder over the content types. Data classes become objects of their fields; the
    // cases of a sealed interface (FigureShape, SimulationType) carry their case name as "type", and
    // payload-free cases (the SimulationType data objects) encode as that name alone.
    private fun encode(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is String -> encodeString(value, sb)
            is Boolean, is Int, is Long -> sb.append(value.toString())
            is Float -> sb.append(value.toString())
            is Double -> sb.append(value.toString())
            is Enum<*> -> encodeString(value.name, sb)
            is Map<*, *> -> {
                sb.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    encodeString(k.toString(), sb); sb.append(':'); encode(v, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v -> if (i > 0) sb.append(','); encode(v, sb) }
                sb.append(']')
            }
            else -> encodeObject(value, sb)
        }
    }

    private fun encodeObject(value: Any, sb: StringBuilder) {
        val cls = value.javaClass
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        val sealedCase = cls.enclosingClass?.isInterface == true
        if (fields.isEmpty() && sealedCase) {
            encodeString(cls.simpleName, sb)
            return
        }
        sb.append('{')
        var first = true
        if (sealedCase) {
            sb.append("\"type\":"); encodeString(cls.simpleName, sb); first = false
        }
        for (field in fields) {
            field.isAccessible = true
            val v = field.get(value) ?: continue
            if (!first) sb.append(',')
            first = false
            encodeString(field.name, sb); sb.append(':'); encode(v, sb)
        }
        // Derived from AccessTiers rather than stored, so the field scan above cannot see it.
        if (value is Topic) {
            if (!first) sb.append(',')
            sb.append("\"isPremium\":").append(value.isPremium)
        }
        sb.append('}')
    }

    private fun encodeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
