package com.algora.app.core.data.settings

// The latest answer to each quiz question, whichever run it came from — a timed set, a learn-mode
// pass, a missed-only retry, the daily drill or a weak-spot drill. QUIZ_ATTEMPTS cannot answer "how
// am I doing on sliding window?": it only holds full timed runs, keyed by position in each run, and
// the drills' positions point into sets that exist for one sitting. This store is keyed by the
// question itself, so every run can report back to the question it actually asked.
//
// Persisted as "quizId#index|0|1" entries in one preference set, like the SRS and attempt stores.

fun questionKey(quizId: String, index: Int): String = "$quizId#$index"

// Inverse of questionKey. The quiz id is everything before the *last* '#', so an id can never be
// split by an index that happens to contain one.
fun parseQuestionKey(key: String): Pair<String, Int>? {
    val cut = key.lastIndexOf('#')
    if (cut <= 0) return null
    val index = key.substring(cut + 1).toIntOrNull() ?: return null
    return key.substring(0, cut) to index
}

fun serializeQuestionResult(key: String, correct: Boolean): String = "$key|${if (correct) 1 else 0}"

fun parseQuestionResult(entry: String): Pair<String, Boolean>? {
    val cut = entry.lastIndexOf('|')
    if (cut <= 0) return null
    return when (entry.substring(cut + 1)) {
        "1" -> entry.substring(0, cut) to true
        "0" -> entry.substring(0, cut) to false
        else -> null
    }
}

// A missed quiz question becomes a flashcard under this prefix. The rest of the key is the
// questionKey, so the card resolves straight back to the question it came from.
const val QUIZ_CARD_PREFIX = "quiz:"

fun quizCardKey(questionKey: String): String = QUIZ_CARD_PREFIX + questionKey
