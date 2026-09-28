package com.algora.app.core.data.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Monetization state (Phase 8), kept in its own store so clearing settings or progress can never
// grant or revoke premium. PREMIUM is only ever a cache of what Google Play reported — the billing
// layer re-queries Play on every launch and overwrites it.
val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(name = "algora_entitlement")

object EntitlementKeys {
    val PREMIUM = booleanPreferencesKey("premium_owned")
    // One "topicId|expiryEpochMillis" entry per rewarded-ad unlock.
    val AD_UNLOCKS = stringSetPreferencesKey("ad_unlocks")
}
