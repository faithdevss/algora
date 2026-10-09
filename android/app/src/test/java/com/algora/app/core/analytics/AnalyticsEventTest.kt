package com.algora.app.core.analytics

import com.algora.app.core.data.CategoryRegistry
import com.algora.app.core.data.model.Topic
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Firebase rejects a malformed event or parameter name silently — the event simply never appears in
// the console, and nobody finds out until the funnel is needed. These are the documented limits,
// asserted here by reflection so a name added later is held to them without anyone remembering to.
class AnalyticsEventTest {

    private val legal = Regex("^[A-Za-z][A-Za-z0-9_]*$")
    private val reservedPrefixes = listOf("firebase_", "google_", "ga_")

    private fun constantsOf(owner: Any): List<String> =
        owner.javaClass.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }

    private fun assertWellFormed(names: List<String>, limit: Int) {
        assertTrue("expected some names", names.isNotEmpty())
        names.forEach { name ->
            assertTrue("$name is not a legal Firebase name", legal.matches(name))
            assertTrue("$name exceeds $limit chars", name.length <= limit)
            reservedPrefixes.forEach { prefix ->
                assertTrue("$name uses the reserved prefix $prefix", !name.startsWith(prefix))
            }
        }
        assertEquals("names must be unique", names.size, names.distinct().size)
    }

    @Test
    fun event_names_are_legal() = assertWellFormed(constantsOf(Event), limit = 40)

    @Test
    fun parameter_names_are_legal() = assertWellFormed(constantsOf(Param), limit = 40)

    @Test
    fun user_property_names_are_legal() = assertWellFormed(constantsOf(UserProperty), limit = 24)

    // The typed surface is the only thing screens call, so what it emits is the real contract — a
    // renamed parameter key breaks a dashboard silently, exactly like a malformed one.
    @Test
    fun topic_events_carry_the_topics_section_and_tier() {
        val category = CategoryRegistry.all.first()
        val topic = Topic(
            id = "test_topic",
            name = "Test",
            categoryId = category.id,
            tagline = "",
            description = "",
            iconName = "chip",
            accentColor = 0L,
        )
        val recorder = RecordingAnalytics()

        recorder.topicOpened(topic, TopicAccessKind.AD_UNLOCKED)

        val (event, params) = recorder.events.single()
        assertEquals(Event.TOPIC_OPENED, event)
        assertEquals(
            mapOf(
                Param.TOPIC_ID to "test_topic",
                Param.SECTION to category.section.name,
                Param.TIER to "premium",
                Param.ACCESS to "ad_unlocked",
            ),
            params,
        )
    }

    @Test
    fun a_review_session_records_its_size_and_mode() {
        val recorder = RecordingAnalytics()

        recorder.reviewSession(cardsReviewed = 7, studyingAhead = true)

        val (event, params) = recorder.events.single()
        assertEquals(Event.REVIEW_SESSION, event)
        assertEquals(mapOf(Param.CARDS to 7, Param.MODE to "study_ahead"), params)
    }

    private class RecordingAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun log(event: String, params: Map<String, Any>) {
            events += event to params
        }

        override fun setUserProperty(name: String, value: String) = Unit
    }
}
