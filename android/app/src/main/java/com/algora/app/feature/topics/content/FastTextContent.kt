package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fastTextContent = TopicContent(
    topicId = "fasttext",
    whatIsIt = listOf(
        "FastText keeps word2vec's objective exactly and changes what a word *is*. Instead of one vector per type, a word is a bag of character n-grams of length 3–6 taken from the word wrapped in boundary markers, plus the whole word as one more token: \"king\" becomes <ki, kin, ing, ng>, <kin, king, ing>, <king, king>, <king> and king — nine pieces. The word's vector is the sum of its subwords' vectors, and training is the same skip-gram or CBOW objective over those sums.",
        "That single change fixes word2vec's two hardest failures. Out-of-vocabulary words stop being a wall: a word the corpus never contained still has n-grams that were seen, so a vector can be composed for it. In the lab, \"kings\" is unseen — word2vec returns <unk> and stops — while FastText builds it from 6 of its 15 n-grams and lands at cosine 0.98 to \"king\". \"monarchy\", also unseen and sharing no whole word with the corpus, scores 0.81 to \"king\" and 0.82 to \"kingdom\" against 0.36 to \"dog\". And morphology becomes shared evidence rather than a source of unrelated types, which is why the gains are largest in Turkish, Finnish, German and Arabic, where one lemma has hundreds of surface forms and each is otherwise its own row in the table.",
        "The cost is size and a little blurring. This tiny corpus already needs 299 n-gram vectors for 26 word types, and a real corpus needs millions — production FastText hashes n-grams into a fixed 2M-bucket table and accepts the collisions. Sharing characters also means words that look alike are pulled together whether or not they are related, which is a real error mode on short words and proper nouns. What stays is the property that makes it deployable: inference is a lookup and a sum, no network runs, so it serves language identification and text classification at a scale and latency no transformer touches.",
    ),
    steps = listOf(
        StepCard(1, "Wrap and Cut", "Add < and > to the word, then take every n-gram of length 3–6.", 0xFF6366F1),
        StepCard(2, "Add the Word Itself", "The whole word is one more token, so frequent words keep a dedicated vector.", 0xFF8B5CF6),
        StepCard(3, "Sum the Subwords", "v(word) = Σ v(g) over its n-grams — the only change to word2vec's maths.", 0xFF3B82F6),
        StepCard(4, "Train As Before", "Same skip-gram/CBOW objective, same negative sampling, gradients flow into every n-gram.", 0xFF06B6D4),
        StepCard(5, "Compose the Unseen", "An OOV word is the sum of whichever of its n-grams exist. No <unk>.", 0xFF14B8A6),
        StepCard(6, "Bound the Memory", "Hash n-grams into a fixed bucket table and accept collisions.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Word vector", "v(w) = Σ_{g ∈ G(w)} z_g", "G(w) is the n-gram set of w, plus w itself."),
        FormulaEntry("Scoring", "s(w, c) = Σ_{g ∈ G(w)} z_g · u_c", "The dot product distributes over the sum, so nothing else changes."),
        FormulaEntry("Subword count", "\"king\" → 9 n-grams at 3 ≤ n ≤ 6", "Wrapped as <king>, plus the whole word."),
        FormulaEntry("Vocabulary growth", "26 word types → 299 distinct n-grams", "Measured on the lab's corpus; hashing bounds it in production."),
        FormulaEntry("OOV composed", "\"kings\": 6 of 15 n-grams known → cos 0.98 to \"king\"", "word2vec has no vector for it at all."),
        FormulaEntry("Derivation, not inflection", "\"monarchy\" → 0.81 king, 0.82 kingdom, 0.36 dog", "Also unseen, and shares no whole word with the corpus."),
    ),
    notationKey = listOf(
        NotationEntry("subword / character n-gram", "a contiguous run of n characters inside the marked word"),
        NotationEntry("< >", "boundary markers, so a prefix is distinguishable from the same letters mid-word"),
        NotationEntry("OOV", "out of vocabulary — the case word2vec cannot answer and FastText composes"),
        NotationEntry("hashing trick", "mapping n-grams into a fixed table of buckets, accepting collisions"),
        NotationEntry("morphologically rich", "languages where one lemma has many surface forms — the family this helps most"),
        NotationEntry("supervised FastText", "the classifier variant: average the word vectors, one linear layer, hierarchical softmax"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Subwords, and composing a word that was never seen",
            accentColor = 0xFF6366F1,
            code = """
                def subwords(word, lo=3, hi=6):
                    padded = f"<{word}>"
                    grams = [padded[i:i + n] for n in range(lo, hi + 1)
                             for i in range(len(padded) - n + 1)]
                    return grams + [word]

                print(subwords("king"))
                # ['<ki', 'kin', 'ing', 'ng>', '<kin', 'king', 'ing>', '<king', 'king>', '<king>', 'king']

                import numpy as np
                def vector(word, Z):                       # Z: n-gram -> vector
                    known = [Z[g] for g in subwords(word) if g in Z]
                    return np.mean(known, axis=0) if known else None   # None only if NOTHING matches

                # "kings" never appeared in training:
                #   word2vec  -> KeyError / <unk>
                #   fastText  -> composed from 6 of its 15 n-grams, cos 0.98 to "king"
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The library, and where it still wins",
            accentColor = 0xFFEC4899,
            code = """
                import fasttext

                # Unsupervised vectors, subwords on by default:
                model = fasttext.train_unsupervised("corpus.txt", model="skipgram",
                                                    minn=3, maxn=6, bucket=2_000_000, dim=300)
                model.get_word_vector("antidisestablishmentarianism")   # works, never seen

                # Supervised classification -- average the word vectors, one linear layer, and a
                # hierarchical softmax over labels. It trains a 1M-document classifier in seconds
                # on a laptop CPU and is still the right first baseline before anything neural.
                clf = fasttext.train_supervised("train.txt", epoch=25, wordNgrams=2)
                print(clf.test("valid.txt"))

                # Where it goes wrong: shared characters pull unrelated short words together, and
                # proper nouns suffer most -- "Jon" and "Jan" share two of four trigrams. Raising
                # minn helps; the honest fix is to check on your own vocabulary.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF6366F1, "Multilingual NLP", "Pretrained vectors for 157 languages, and the largest gains where morphology is richest."),
        ApplicationCard("search", 0xFF8B5CF6, "Search & Typos", "Misspellings and inflections still land near the intended word instead of falling to <unk>."),
        ApplicationCard("chip", 0xFF3B82F6, "Edge Classification", "Language identification and topic tagging at millions of documents per hour on a CPU."),
        ApplicationCard("help", 0xFFEC4899, "Where It Blurs", "Short words and proper nouns share characters without sharing meaning."),
    ),
    takeaways = listOf(
        "A word is a bag of character 3–6-grams plus itself; its vector is the sum of theirs — word2vec's maths, otherwise unchanged.",
        "Unseen words get real vectors: \"kings\" composes from 6 of 15 n-grams to cosine 0.98 with \"king\".",
        "Morphological relatives share evidence, which is why the gains concentrate in morphologically rich languages.",
        "The cost is table size — 299 n-grams for 26 words here — handled in production by hashing into 2M buckets.",
        "Inference stays a lookup and a sum, so it still serves classification at scales no transformer reaches.",
    ),
    crossLinks = listOf(
        CrossLink("word2vec_skipgram", "Word2Vec (Skip-Gram)"),
        CrossLink("bpe", "Byte-Pair Encoding"),
        CrossLink("tokenization", "Tokenization"),
        CrossLink("elmo", "ELMo"),
    ),
)
