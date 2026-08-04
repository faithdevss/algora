package com.algora.app.core.data.entitlement

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EntitlementAccessTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): EntitlementRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("entitlement.preferences_pb") },
        )
        return EntitlementRepository(dataStore)
    }

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun freeTopicIsAlwaysOpen() {
        assertEquals(
            TopicAccess.Open,
            accessOf(isPremiumTopic = false, premiumOwned = false, unlocks = emptyMap(), topicId = "array", now = now),
        )
    }

    @Test
    fun premiumTopicIsLockedByDefault() {
        assertEquals(
            TopicAccess.Locked,
            accessOf(isPremiumTopic = true, premiumOwned = false, unlocks = emptyMap(), topicId = "master_theorem", now = now),
        )
    }

    @Test
    fun adUnlockIsLiveBeforeExpiryAndDeadAfter() {
        val expiry = now + AD_UNLOCK_DURATION_MS
        val unlocks = mapOf("master_theorem" to expiry)

        assertEquals(
            TopicAccess.AdUnlocked(expiry),
            accessOf(true, premiumOwned = false, unlocks = unlocks, topicId = "master_theorem", now = now + 5 * hour),
        )
        assertEquals(
            TopicAccess.Locked,
            accessOf(true, premiumOwned = false, unlocks = unlocks, topicId = "master_theorem", now = now + 7 * hour),
        )
    }

    @Test
    fun adUnlockDoesNotLeakToOtherTopics() {
        val unlocks = mapOf("master_theorem" to now + hour)

        assertEquals(
            TopicAccess.Locked,
            accessOf(true, premiumOwned = false, unlocks = unlocks, topicId = "sandbox_mode", now = now),
        )
    }

    @Test
    fun ownedPremiumBeatsEverything() {
        assertEquals(
            TopicAccess.Owned,
            accessOf(true, premiumOwned = true, unlocks = emptyMap(), topicId = "master_theorem", now = now),
        )
    }

    @Test
    fun grantAdUnlock_makesTopicReadableFor6h() = runTest {
        val repository = newRepository()

        repository.grantAdUnlock("master_theorem")

        val unlocks = repository.adUnlocks.first()
        val expiry = unlocks.getValue("master_theorem")
        val remaining = expiry - System.currentTimeMillis()
        assertTrue("expected ~6h left, got ${remaining}ms", remaining > 5 * hour && remaining <= AD_UNLOCK_DURATION_MS)
        assertEquals(
            TopicAccess.AdUnlocked(expiry),
            repository.accessFor("master_theorem", isPremiumTopic = true).first(),
        )
    }

    @Test
    fun expiredUnlockIsIgnoredOnRead() {
        // Read filtering is what actually protects content; pruning on write is only housekeeping.
        val stale = parseUnlock(serializeUnlock("sandbox_mode", now - hour))
        assertEquals("sandbox_mode" to now - hour, stale)
        assertEquals(
            TopicAccess.Locked,
            accessOf(true, premiumOwned = false, unlocks = mapOf(stale!!), topicId = "sandbox_mode", now = now),
        )
    }

    @Test
    fun regrantingSameTopicKeepsOneEntry() = runTest {
        val repository = newRepository()

        repository.grantAdUnlock("master_theorem")
        repository.grantAdUnlock("master_theorem")
        repository.grantAdUnlock("sandbox_mode")

        assertEquals(setOf("master_theorem", "sandbox_mode"), repository.adUnlocks.first().keys)
    }

    @Test
    fun setPremium_flipsEntitlementBothWays() = runTest {
        val repository = newRepository()

        repository.setPremium(true)
        assertEquals(TopicAccess.Owned, repository.accessFor("master_theorem", isPremiumTopic = true).first())

        // Refund / account switch path: Play says "not owned", the cache must follow.
        repository.setPremium(false)
        assertEquals(TopicAccess.Locked, repository.accessFor("master_theorem", isPremiumTopic = true).first())
    }
}
