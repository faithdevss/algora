package com.algora.app.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionResultTest {

    @Test
    fun `question keys round trip`() {
        assertEquals("faang_set" to 12, parseQuestionKey(questionKey("faang_set", 12)))
    }

    @Test
    fun `malformed question keys are rejected`() {
        assertNull(parseQuestionKey("faang_set"))
        assertNull(parseQuestionKey("#3"))
        assertNull(parseQuestionKey("faang_set#x"))
    }

    @Test
    fun `results round trip through the preference encoding`() {
        assertEquals("startup_set#4" to true, parseQuestionResult(serializeQuestionResult("startup_set#4", true)))
        assertEquals("startup_set#4" to false, parseQuestionResult(serializeQuestionResult("startup_set#4", false)))
        assertNull(parseQuestionResult("startup_set#4|maybe"))
    }

    @Test
    fun `a mistake card key resolves back to its question`() {
        val card = quizCardKey(questionKey("finance_trading_set", 7))
        assertEquals("finance_trading_set" to 7, parseQuestionKey(card.removePrefix(QUIZ_CARD_PREFIX)))
    }
}
