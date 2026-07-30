package com.algora.app.feature.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Curated deck cards share the SRS keyspace with the takeaway cards. A duplicate key would make two
// cards schedule as one, so these pin the key shape as well as the content being answerable.
class FlashcardDeckTest {

    @Test
    fun `every curated card has a question and an answer`() {
        val broken = flashcardDecks.flatMap { deck ->
            deck.cards.filter { it.front.isBlank() || it.back.isBlank() }.map { "${deck.id}: ${it.front.take(40)}" }
        }
        assertTrue("Cards missing a front or back: $broken", broken.isEmpty())
    }

    @Test
    fun `curated card keys are unique and never collide with topic cards`() {
        val curated = curatedFlashcards()
        assertEquals("Duplicate curated card keys", curated.size, curated.map { it.key }.toSet().size)

        val topicKeys = allReviewCards().map { it.key }.toSet() - curated.map { it.key }.toSet()
        val collisions = curated.map { it.key }.filter { it in topicKeys }
        assertTrue("Curated keys colliding with topic cards: $collisions", collisions.isEmpty())
    }

    @Test
    fun `curated keys carry the deck prefix and an index`() {
        val malformed = curatedFlashcards().map { it.key }.filter {
            !it.startsWith("deck_") || it.substringAfter('#').toIntOrNull() == null
        }
        assertTrue("Malformed curated keys: $malformed", malformed.isEmpty())
    }

    @Test
    fun `the curated decks add a meaningful number of cards`() {
        assertTrue("Expected at least 60 curated cards, found ${curatedFlashcards().size}", curatedFlashcards().size >= 60)
    }

    @Test
    fun `the review queue includes both takeaway and curated cards`() {
        val all = allReviewCards()
        assertTrue("No curated cards in the queue", all.any { it.prompt != null })
        assertTrue("No takeaway cards in the queue", all.any { it.prompt == null })
    }
}
