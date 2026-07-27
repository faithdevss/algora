package com.algora.app.core.data.entitlement

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class EntitlementRepository(private val dataStore: DataStore<Preferences>) {

    val isPremium: Flow<Boolean> = dataStore.data.map { prefs -> prefs[EntitlementKeys.PREMIUM] ?: false }

    // Expired unlocks are filtered on read, so a stale entry can never grant access even if the
    // pruning write never happens.
    val adUnlocks: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        val now = System.currentTimeMillis()
        (prefs[EntitlementKeys.AD_UNLOCKS] ?: emptySet())
            .mapNotNull { parseUnlock(it) }
            .filter { (_, expiry) -> expiry > now }
            .toMap()
    }

    fun accessFor(topicId: String, isPremiumTopic: Boolean): Flow<TopicAccess> =
        combine(isPremium, adUnlocks) { premium, unlocks ->
            accessOf(isPremiumTopic, premium, unlocks, topicId, System.currentTimeMillis())
        }

    // Grants (or extends) 24h access to one topic after a completed rewarded ad, pruning expired
    // entries in the same write.
    suspend fun grantAdUnlock(topicId: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val kept = (prefs[EntitlementKeys.AD_UNLOCKS] ?: emptySet())
                .mapNotNull { parseUnlock(it) }
                .filter { (id, expiry) -> expiry > now && id != topicId }
            prefs[EntitlementKeys.AD_UNLOCKS] =
                (kept + (topicId to now + AD_UNLOCK_DURATION_MS))
                    .map { (id, expiry) -> serializeUnlock(id, expiry) }
                    .toSet()
        }
    }

    // Only the billing layer calls this — it mirrors Play's answer, including revoking on refund.
    suspend fun setPremium(owned: Boolean) {
        dataStore.edit { prefs -> prefs[EntitlementKeys.PREMIUM] = owned }
    }
}
