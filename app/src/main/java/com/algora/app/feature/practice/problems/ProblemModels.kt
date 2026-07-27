package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// A worked practice problem. Unlike QuizQuestion (multiple choice, graded), a problem is solved by
// thinking: the screen reveals hints one at a time, then the approach, then the solution — so the
// learner can stop at whatever depth they still need.
data class PracticeProblem(
    val id: String,
    val title: String,
    val patternId: String,
    val difficulty: Difficulty,
    val prompt: String,
    val examples: List<ProblemExample>,
    val constraints: List<String>,
    // Ordered cheapest-nudge-first; the UI reveals them one tap at a time.
    val hints: List<String>,
    val approach: List<String>,
    val timeComplexity: String,
    val spaceComplexity: String,
    val solutionCode: String,
    // What you must already understand to solve this — each entry points at a real TopicRegistry
    // topic, so "I'm stuck" turns into a reading list rather than a dead end.
    val prerequisites: List<ProblemPrereq> = emptyList(),
    // Cross-link back to the pattern topic in Interview Prep, when one exists.
    val linkedTopicId: String? = null,
    val linkedTopicLabel: String? = null,
)

// A single piece of required knowledge. `why` says what the problem actually does with it, so the
// link reads as a reason rather than a tag.
data class ProblemPrereq(val topicId: String, val label: String, val why: String)

data class ProblemExample(val input: String, val output: String, val note: String = "")

// Groups problems in the list screen. `topicId` points at the Interview Prep pattern topic.
data class ProblemPattern(
    val id: String,
    val name: String,
    val blurb: String,
    val accentColor: Long,
    val topicId: String,
)
