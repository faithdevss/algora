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
private val modeling = NlpCategories.modeling

private val preprocessingTopics = listOf(
    topic("tokenization", "Tokenization", preprocessing, "Split raw text into words, subwords or characters.", difficulty = Difficulty.BEGINNER),
    topic("stemming", "Stemming (Porter Stemmer)", preprocessing, "Chop words down to a crude root form.", difficulty = Difficulty.BEGINNER),
    topic("lemmatization", "Lemmatization", preprocessing, "Map words to their dictionary lemma using morphology.", isPremium = true),
    topic("bow_tfidf", "Bag-of-Words / TF-IDF", preprocessing, "Represent documents as weighted word-count vectors.", isPremium = true),
    topic("bpe", "Byte-Pair Encoding", preprocessing, "Learn a subword vocabulary by merging the most frequent pair.", isPremium = true),
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
    val topics: List<Topic> = preprocessingTopics + modelingTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
