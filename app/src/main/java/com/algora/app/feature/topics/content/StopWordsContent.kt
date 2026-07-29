package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stopWordsContent = TopicContent(
    topicId = "stop_words",
    whatIsIt = listOf(
        "Stop word removal deletes the highest-frequency words in a language — the, is, of, and, to — before anything downstream sees the text. They follow Zipf's law: the top 100 English words are roughly half of all running text, and they appear in nearly every document, so under a bag-of-words model they cost storage and contribute almost nothing to telling one document from another. On the six-review corpus in the lab, NLTK's list removes 30 of 51 tokens and takes the vocabulary from 37 types to 19.",
        "That is a real saving for the systems the technique was invented for. A 1970s inverted index could not afford a postings list containing every document; dropping 40–50% of tokens halved the index and made the common case fast. The same argument still holds for topic models, where high-frequency function words otherwise dominate every topic, and for keyword search, where a query term matching every document ranks nothing.",
        "The cost is that frequency is not the same as unimportance. NLTK's English list contains not, no, nor and against — so \"the movie was not good at all\" and \"the movie was good\" both reduce to {movie, good}, an identical vector with an opposite meaning. Modern pipelines mostly do not apply a stop list at all: TF-IDF already down-weights ubiquitous terms continuously rather than by a hard cut, subword tokenizers have no notion of a word to remove, and a transformer wants the whole string including the negation. Use a stop list when your model is a bag of counts and your task is topical; skip it when word order or polarity carries the answer.",
    ),
    steps = listOf(
        StepCard(1, "Pick a List", "NLTK's 179 English words, spaCy's 326, or one built from your own corpus by document frequency.", 0xFF14B8A6),
        StepCard(2, "Match After Normalising", "Compare lowercased, post-cleaning tokens — otherwise \"The\" survives a list containing \"the\".", 0xFF06B6D4),
        StepCard(3, "Filter", "Keep every token not on the list. One pass, O(n) with a hash set.", 0xFF3B82F6),
        StepCard(4, "Measure What Left", "Token count, vocabulary size, and — the step everyone skips — whether any label-bearing word went with it.", 0xFF6366F1),
        StepCard(5, "Curate for the Domain", "Add corpus-specific noise (\"patient\", \"study\"); remove negations and domain words the generic list would eat.", 0xFF8B5CF6),
        StepCard(6, "Compare Against Doing Nothing", "Run the task with and without. If TF-IDF is downstream, the list often changes nothing measurable.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Zipf's law", "f(r) ∝ 1 / r", "Word frequency falls as the inverse of its rank — the reason a short list covers so much text."),
        FormulaEntry("Removal rate", "removed / total = 30 / 51 = 58.8%", "Measured on the lab's six-review corpus with NLTK's list."),
        FormulaEntry("Vocabulary effect", "37 types → 19 types", "The index-size argument, which is separate from the token-count one."),
        FormulaEntry("Document frequency cut", "drop w if df(w) / N > τ", "The data-driven alternative: build the list from your own corpus rather than importing one."),
        FormulaEntry("Inverse document frequency", "idf(w) = ln(N / df(w))", "Goes to 0 for a word in every document — the soft version of the same idea."),
        FormulaEntry("Measured idf", "idf(the) = 0.18 vs idf(movie) = 1.10", "TF-IDF down-weights by a factor of six here without deleting anything."),
    ),
    notationKey = listOf(
        NotationEntry("stop word", "a word removed for being too frequent to discriminate, not for being meaningless"),
        NotationEntry("function word", "article, preposition, auxiliary, pronoun — the grammatical scaffolding"),
        NotationEntry("content word", "noun, verb, adjective, adverb — where topical signal lives"),
        NotationEntry("df(w)", "document frequency: how many documents contain w at least once"),
        NotationEntry("negation scope", "the span a \"not\" flips; deleting the negation silently flips it back"),
        NotationEntry("truecasing", "restoring correct case, the cleaning-side analogue of curating a stop list"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Applying a list, and measuring what it cost",
            accentColor = 0xFF14B8A6,
            code = """
                from nltk.corpus import stopwords

                stop = set(stopwords.words("english"))     # 179 words
                print("not" in stop, "no" in stop)          # True True  <- the trap

                corpus = ["the movie was not good at all", "the movie was good"]
                kept = [[w for w in doc.split() if w not in stop] for doc in corpus]

                print(kept[0])                              # ['movie', 'good']
                print(kept[1])                              # ['movie', 'good']
                print(kept[0] == kept[1])                   # True -- opposite reviews, one vector

                # Keep the negations and the pipeline survives:
                stop -= {"not", "no", "nor", "against", "but"}
                kept = [[w for w in doc.split() if w not in stop] for doc in corpus]
                print(kept[0], kept[1])                     # ['movie', 'not', 'good'] ['movie', 'good']
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Building the list from the corpus instead of importing one",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.feature_extraction.text import TfidfVectorizer

                # max_df drops terms that appear in more than 90% of documents -- a stop list
                # derived from this corpus, so domain noise ("patient", "study") goes too, and a
                # word that is generic in English but discriminative here stays.
                vec = TfidfVectorizer(max_df=0.9, min_df=2)
                X = vec.fit_transform(docs)
                print(sorted(vec.stop_words_)[:10])   # what it decided to drop, and why you can audit it

                # Or skip the whole question: idf already weights a term in every document at
                # ln(N/N) = 0, so with TF-IDF downstream an explicit list usually changes nothing
                # you can measure -- run it both ways before assuming it helps.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF14B8A6, "Inverted Indexes", "Smaller postings lists and fewer useless matches — the original motivation, and still valid for keyword search."),
        ApplicationCard("chart", 0xFF3B82F6, "Topic Modelling", "Without a list, LDA topics fill with function words and every topic looks the same."),
        ApplicationCard("users", 0xFF8B5CF6, "Document Clustering", "Similarity over raw counts is dominated by shared function words, which every pair shares."),
        ApplicationCard("help", 0xFFEC4899, "Where It Backfires", "Sentiment, QA and translation — anything where \"not\", \"but\" or a pronoun is the answer."),
    ),
    takeaways = listOf(
        "Removes 30 of 51 tokens (58.8%) and 18 of 37 vocabulary types on the lab's corpus — the saving is real.",
        "NLTK's list includes not/no/nor, so two reviews with opposite verdicts collapse to the same bag of words.",
        "A query of pure function words (\"to be or not to be\") survives as nothing at all.",
        "TF-IDF is the continuous version: idf = ln(N/df) reaches 0 for a term in every document, no hard cut needed.",
        "Apply it for topic models, keyword indexes and clustering; skip it for sentiment, QA and any transformer.",
    ),
    crossLinks = listOf(
        CrossLink("text_cleaning", "Lowercasing & Cleaning"),
        CrossLink("tokenization", "Tokenization"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
