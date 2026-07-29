package com.algora.app.feature.nlp

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.model.Topic

private fun topic(
    id: String,
    name: String,
    category: Category,
    tagline: String,
    isPremium: Boolean = false,
    iconName: String = category.iconName,
    accentColor: Long = category.accentColor,
    difficulty: Difficulty? = null,
) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = iconName,
    accentColor = accentColor,
    isPremium = isPremium,
    difficulty = difficulty,
)

private val preprocessing = NlpCategories.preprocessing
private val statistical = NlpCategories.statistical
private val modeling = NlpCategories.modeling

// Ordered as a reading path — clean, split, filter, normalise, then the two vocabulary-building
// steps — rather than alphabetically.
private val preprocessingTopics = listOf(
    topic("text_cleaning", "Lowercasing & Cleaning", preprocessing, "Normalise, fold and strip before anything counts a word.", difficulty = Difficulty.BEGINNER),
    topic("tokenization", "Tokenization", preprocessing, "Split raw text into words, subwords or characters.", difficulty = Difficulty.BEGINNER),
    topic("regex_nlp", "Regular Expressions", preprocessing, "The pattern language every tokenizer and rule extractor is written in.", isPremium = true),
    topic("stop_words", "Stop Word Removal", preprocessing, "Drop the highest-frequency words — and find out what goes with them.", difficulty = Difficulty.BEGINNER),
    topic("stemming", "Stemming (Porter Stemmer)", preprocessing, "Chop words down to a crude root form.", difficulty = Difficulty.BEGINNER),
    topic("lemmatization", "Lemmatization", preprocessing, "Map words to their dictionary lemma using morphology.", isPremium = true),
    topic("n_grams", "N-Grams", preprocessing, "Contiguous token windows, and the count model built from them.", isPremium = true),
    topic("bpe", "Byte-Pair Encoding", preprocessing, "Learn a subword vocabulary by merging the most frequent pair.", isPremium = true),
)

// D1. `bow_tfidf` moves here from Preprocessing: the doc lists Bag of Words and TF-IDF under
// Statistical NLP, and they are the representation the rest of this category scores.
private val statisticalTopics = listOf(
    topic("bow_tfidf", "Bag-of-Words / TF-IDF", statistical, "Represent documents as weighted word-count vectors.", isPremium = true),
    topic("cosine_similarity", "Cosine Similarity", statistical, "Compare documents by angle, so length stops mattering.", isPremium = true),
    topic("jaccard_similarity", "Jaccard Similarity", statistical, "Set overlap — the metric behind near-duplicate detection.", isPremium = true),
    topic("hmm", "Hidden Markov Models", statistical, "Tag a sequence by finding the most probable hidden path.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("pcfg", "Probabilistic Context-Free Grammars", statistical, "Rank the parse trees a grammar allows instead of listing them.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

private val modelingTopics = listOf(
    topic("word_embeddings", "Word Embeddings", modeling, "Dense vectors that place similar words near each other.", isPremium = true),
    topic("rnn_lstm", "RNN / LSTM", modeling, "Sequential models that read text one token at a time.", isPremium = true),
    topic("attention", "Attention", modeling, "Let the model weigh every token against every other.", isPremium = true),
    topic("transformers", "Transformers", modeling, "Stacked self-attention — the backbone of modern NLP.", isPremium = true),
    topic("llms", "LLMs", modeling, "Transformers scaled to billions of parameters.", isPremium = true),
    topic("ner", "Named Entity Recognition", modeling, "Tag each token as person, place, organization or nothing.", isPremium = true),
    topic("rag", "Retrieval-Augmented Generation", modeling, "Retrieve relevant passages and condition the model on them.", isPremium = true),
)

object NlpTopics {
    val topics: List<Topic> = preprocessingTopics + statisticalTopics + modelingTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
