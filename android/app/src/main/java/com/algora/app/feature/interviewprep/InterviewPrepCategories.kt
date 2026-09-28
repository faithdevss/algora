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

    // Difficulty-tiered interview rounds. Beginner is the free on-ramp for a new learner; Advanced is
    // the premium step up. Both mix concept checks with scenario questions solved in the explanation.
    val beginnerInterview = Category("interview_beginner", "Beginner Interview", Section.INTERVIEW_PREP, 0xFF10B981, "target")
    val advancedInterview = Category("interview_advanced", "Advanced Interview", Section.INTERVIEW_PREP, 0xFFEF4444, "flame")

    // Subject-by-subject quizzes, so a learner can drill one area instead of a mixed set.
    val topicQuizzes = Category("interview_topic_quizzes", "Topic Quizzes", Section.INTERVIEW_PREP, 0xFF0EA5E9, "check")

    // AI-mode interview prep — the ML counterpart of the DSA mock + system-design rounds.
    val aiInterview = Category("interview_ai", "AI Interview", Section.INTERVIEW_PREP, 0xFF6366F1, "robot")

    // Browser order. Patterns is filtered out of the browser (it has its own screen), so AI Interview
    // is the second section shown, right after Interview Styles.
    val all = listOf(patterns, interviewStyles, aiInterview, mock, beginnerInterview, advancedInterview, topicQuizzes)
}
