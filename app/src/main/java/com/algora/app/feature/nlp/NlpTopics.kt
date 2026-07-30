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
private val syntax = NlpCategories.syntax
private val embeddings = NlpCategories.embeddings
private val transformer = NlpCategories.transformer
private val pretrained = NlpCategories.pretrained
private val modernLlm = NlpCategories.modernLlm
private val recurrent = NlpCategories.rnn

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

// D2. `ner` moves here from Modeling — the doc lists Named Entity Recognition under Syntactic &
// Semantic Analysis, alongside the five topics this batch authors.
private val syntaxTopics = listOf(
    topic("pos_tagging", "Part-of-Speech Tagging", syntax, "Assign each token its syntactic category, in context.", difficulty = Difficulty.BEGINNER),
    topic("chunking", "Chunking", syntax, "Find flat phrases without building a whole parse tree.", isPremium = true),
    topic("ner", "Named Entity Recognition", syntax, "Tag each token as person, place, organization or nothing.", isPremium = true),
    topic("dependency_parsing", "Dependency Parsing", syntax, "One labelled arc per word — what relates to what.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("constituency_parsing", "Constituency Parsing", syntax, "Nested phrase structure, scored by labelled brackets.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("coreference", "Coreference Resolution", syntax, "Decide which mentions point at the same entity.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("sentiment_lexicon", "Sentiment Analysis (Lexicon)", syntax, "Score text by summing word valences — and handling negation.", isPremium = true),
)

// D3. `word_embeddings` moves here from Modeling and stays as the category's landing topic — it is
// the umbrella the doc's five models sit under, and the five below author them properly.
private val embeddingTopics = listOf(
    topic("word_embeddings", "Word Embeddings", embeddings, "Dense vectors that place similar words near each other.", isPremium = true),
    topic("word2vec_cbow", "Word2Vec (CBOW)", embeddings, "Predict the missing centre word from its averaged context.", isPremium = true),
    topic("word2vec_skipgram", "Word2Vec (Skip-Gram)", embeddings, "Predict each neighbour from the centre word, one pair at a time.", isPremium = true),
    topic("glove", "GloVe", embeddings, "Fit vectors to the whole corpus co-occurrence matrix at once.", isPremium = true),
    topic("fasttext", "FastText", embeddings, "Words as bags of character n-grams, so unseen words still get vectors.", isPremium = true),
    topic("elmo", "ELMo", embeddings, "One vector per occurrence, read out of a bidirectional language model.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// D4. `attention` and `transformers` move here from Modeling — the doc's "The Transformer
// Architecture" heading is exactly what they cover; C6 adds self-attention and multi-head.
private val transformerTopics = listOf(
    topic("attention", "Attention", transformer, "Let the model weigh every token against every other.", isPremium = true),
    // C6, cross-listed with Deep Learning — the doc lists these under both sections.
    topic("self_cross_attention", "Self-Attention", transformer, "One operation, two wirings — and the experiment that ends the RNN bottleneck story.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("multi_head_attention", "Multi-Head Attention", transformer, "Free in parameters, and what it actually buys is simultaneous reads.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("positional_encodings", "Positional Encodings", transformer, "Inject order into a mechanism that cannot see it.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("feed_forward", "Feed-Forward Networks", transformer, "Two thirds of a transformer block, and where facts appear to live.", isPremium = true),
    topic("transformers", "Transformers", transformer, "Stacked self-attention — the backbone of modern NLP.", isPremium = true),
)

// D4. `llms` moves here from Modeling as the landing topic for the model families.
private val pretrainedTopics = listOf(
    topic("llms", "LLMs", pretrained, "Transformers scaled to billions of parameters.", isPremium = true),
    // C6, cross-listed with Deep Learning. Gating follows the doc: BERT is the one free entry here.
    topic("bert", "BERT (Encoder Only)", pretrained, "Fill in the blanks — 2× the context per prediction, 6.4× fewer of them.", difficulty = Difficulty.INTERMEDIATE),
    topic("gpt", "GPT-2 (Decoder Only)", pretrained, "One triangular mask, and everything that follows from it.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("t5", "T5 (Text-to-Text)", pretrained, "Every task as text, and span corruption priced at 512 tokens.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("roberta", "RoBERTa", pretrained, "Same architecture, better recipe — and 0.85ᵏ is the whole masking argument.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("distilbert", "DistilBERT", pretrained, "Half the layers, 40% smaller — and why it is not 50%.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("bart", "BART", pretrained, "Corrupt a document five ways, then reconstruct it.", isPremium = true),
    topic("xlnet", "XLNet", pretrained, "Autoregression over permuted orders, with no [MASK] anywhere.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("gpt3_gpt4", "GPT-3 & GPT-4", pretrained, "Scale as the contribution — and the law that made it predictable.", isPremium = true),
    topic("llama_vicuna", "LLaMA & Vicuna", pretrained, "Over-train a small model, open the weights, fine-tune cheaply.", isPremium = true),
    topic("mistral_mixtral", "Mistral & Mixtral (MoE)", pretrained, "Route each token to 2 of 8 experts — big model, small compute.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("claude_gemini", "Claude & Gemini", pretrained, "What actually separates the frontier families.", isPremium = true),
)

// D5. `rag` moves here from Modeling — the doc lists it under Modern LLM Techniques, and it is the
// technique the other six are built around. Ordered as the stack is assembled: shape the prompt,
// decompose the reasoning, search over the decomposition, then retrieve, act, and check.
private val modernLlmTopics = listOf(
    topic("prompt_engineering", "Prompt Engineering (Zero/Few Shot)", modernLlm, "Demonstrations as data — each one kills the rules it contradicts.", difficulty = Difficulty.BEGINNER),
    topic("chain_of_thought", "Chain of Thought (CoT)", modernLlm, "Decompose the answer, and pay for every step you add.", isPremium = true),
    topic("tree_of_thoughts", "Tree of Thoughts", modernLlm, "Keep alternatives, score them, and prune — reasoning as search.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("rag", "Retrieval-Augmented Generation", modernLlm, "Retrieve relevant passages and condition the model on them.", isPremium = true),
    topic("vector_databases", "Vector Databases", modernLlm, "Approximate nearest neighbours, and what each approximation costs.", isPremium = true),
    topic("react", "ReAct (Reasoning + Acting)", modernLlm, "Interleave thought and tool call, because one retrieval is not enough.", isPremium = true),
    topic("ai_agents", "AI Agents & Tool Use", modernLlm, "The loop, its schemas, and the bill that grows quadratically.", isPremium = true),
    topic("hallucination_mitigation", "Hallucination Mitigation", modernLlm, "Ground, cite, abstain — and why confidence is the wrong signal.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// C5. `rnn_lstm` moves here from Modeling, which retires that category — it was the last topic in
// it. The three topics after it are cross-listed with Deep Learning (same ids, one content file
// each), the way `perceptron` is cross-listed between ML and DL: the doc lists this block under both
// sections, and a reader who arrives from NLP should not be sent to another section to find it.
private val recurrentTopics = listOf(
    topic("rnn_lstm", "RNN / LSTM", recurrent, "Sequential models that read text one token at a time.", isPremium = true),
    topic("bidirectional_rnn", "Bidirectional LSTMs", recurrent, "A second pass right to left, and the ceiling it lifts — counted before it is trained.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("encoder_decoder", "Encoder-Decoder Architecture", recurrent, "Two networks, one vector between them, and what that vector drops.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("seq2seq", "Sequence-to-Sequence (Seq2Seq)", recurrent, "Greedy, beam and the two kinds of error only one of them fixes.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

object NlpTopics {
    val topics: List<Topic> = preprocessingTopics + statisticalTopics + syntaxTopics + embeddingTopics +
        recurrentTopics + transformerTopics + pretrainedTopics + modernLlmTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
