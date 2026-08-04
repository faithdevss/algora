package com.algora.app.feature.interviewprep

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats() for Interview Prep.
// Full mock question content is Phase 6's job — this phase only builds the browser chrome + row metadata.
object InterviewPrepCategories {
    val patterns = Category("interview_patterns", "Patterns", Section.INTERVIEW_PREP, 0xFFF59E0B, "help")

    // Was "Company Sets", matching the design mock's original Google/Meta/Amazon rows. These are
    // authored questions written in a house style, not questions sourced from those companies, so
    // both the mock and this label now say style. The id stays as-is — it keys nothing user-visible
    // and renaming it would churn saved state for no gain.
    val interviewStyles = Category("interview_company_sets", "Interview Styles", Section.INTERVIEW_PREP, 0xFF3B82F6, "browser")
    val mock = Category("interview_mock", "Mock", Section.INTERVIEW_PREP, 0xFF8B5CF6, "history")

    // Subject-by-subject quizzes, so a learner can drill one area instead of a mixed set.
    val topicQuizzes = Category("interview_topic_quizzes", "Topic Quizzes", Section.INTERVIEW_PREP, 0xFF0EA5E9, "check")

    // AI-mode interview prep — the ML counterpart of the DSA mock + system-design rounds.
    val aiInterview = Category("interview_ai", "AI Interview", Section.INTERVIEW_PREP, 0xFF6366F1, "robot")

    val all = listOf(patterns, interviewStyles, mock, topicQuizzes, aiInterview)
}
