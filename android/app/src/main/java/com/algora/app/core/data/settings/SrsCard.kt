package com.algora.app.core.data.settings

// Per-card spaced-repetition state (SM-2). Persisted as one "key|reps|ef|interval|due" string per
// card in the SRS preference set. `dueDay` is an epoch-day (millis / 86_400_000).
data class SrsCard(
    val reps: Int,
    val ef: Double,
    val intervalDays: Int,
    val dueDay: Long,
)

// SM-2 update. quality: 0-5 (grade of recall). A grade below 3 lapses the card back to the start.
fun sm2(prev: SrsCard?, quality: Int, today: Long): SrsCard {
    val base = prev ?: SrsCard(reps = 0, ef = 2.5, intervalDays = 0, dueDay = today)
    val newEf = (base.ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))).coerceAtLeast(1.3)
    val (reps, interval) = if (quality < 3) {
        0 to 1
    } else {
        val r = base.reps + 1
        val i = when (r) {
            1 -> 1
            2 -> 6
            else -> Math.round(base.intervalDays * newEf).toInt().coerceAtLeast(1)
        }
        r to i
    }
    return SrsCard(reps = reps, ef = newEf, intervalDays = interval, dueDay = today + interval)
}

// Serialization for the preference set.
fun SrsCard.serialize(key: String): String =
    "$key|$reps|${"%.3f".format(ef)}|$intervalDays|$dueDay"

fun parseSrs(entry: String): Pair<String, SrsCard>? {
    val parts = entry.split("|")
    if (parts.size != 5) return null
    return try {
        parts[0] to SrsCard(
            reps = parts[1].toInt(),
            ef = parts[2].toDouble(),
            intervalDays = parts[3].toInt(),
            dueDay = parts[4].toLong(),
        )
    } catch (e: NumberFormatException) {
        null
    }
}
