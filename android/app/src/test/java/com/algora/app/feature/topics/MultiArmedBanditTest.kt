package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B10's bandit guard. Pure uniform random, with 4 arms, should land close to the 25% chance floor
 * over 200 pulls -- confirmed rather than assumed.
 */
class MultiArmedBanditTest {
    @Test
    fun `pure random exploration lands close to the 1-in-4 chance floor`() {
        assertEquals(0.25f, banditOptimalRate("multi_armed_bandit"), 0.05f)
    }
}
