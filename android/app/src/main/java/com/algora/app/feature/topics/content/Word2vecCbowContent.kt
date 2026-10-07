package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val word2vecCbowContent = TopicContent(
    topicId = "word2vec_cbow",
    figure = Figure(
        caption = "Every training example CBOW makes from the page's lab sentence, \"the king rules " +
            "the kingdom\", with a ±2 window: hide the centre word, average whatever context " +
            "vectors the window holds, and predict it. Near the edges the window is shorter, so " +
            "the first and last positions use only two context words, but every position yields " +
            "exactly one example — 5 here, against the 14 (centre, context) pairs skip-gram would " +
            "make from the same words. Averaging is the \"bag\": order inside the window is " +
            "discarded, and the repeated \"the\" simply counts twice. Trained on the middle row, " +
            "the model starts at P(rules) = 0.25 over four words and reaches 1.00 after 200 passes; " +
            "the two rows whose target is \"the\" cannot both be perfect, since similar contexts " +
            "ask for different answers.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("king, rules", "the"),
                listOf("the, rules, the", "king"),
                listOf("the, king, the, kingdom", "rules"),
                listOf("king, rules, kingdom", "the"),
                listOf("rules, the", "kingdom"),
            ),
            colHeaders = listOf("context (averaged)", "target"),
            marks = listOf(
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(0, 1, FigureTone.Muted),
                FigureCell(3, 1, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "CBOW — continuous bag of words — is one of word2vec's two objectives. Hide the centre word of a window, average the vectors of the words around it, and train the model to recover what was removed. No labels are needed anywhere: the text supplies its own supervision, which is why the method scaled to corpora nobody could annotate. The vectors are a by-product; the prediction task is only a pretext for forcing words that appear in the same company into the same region of space.",
        "\"Bag of words\" is literal and is the design decision that separates it from skip-gram: the context vectors are averaged before the prediction, so word order inside the window is discarded and the whole window costs one update. On the lab's sentence, \"the king rules the kingdom\" with a ±2 window, that is 5 training examples — one per position — against the 14 (centre, context) pairs skip-gram would make from the same words: 2.8× fewer updates, and that gap is the reason CBOW was the faster of the two on the same hardware in 2013.",
        "Training is the ordinary softmax loop, and the lab shows it end to end on one example: hide \"rules\", average the four context vectors, and score every vocabulary word. Untrained, every word is about equally likely — P(rules) = 0.25 over four words, a loss of 1.39. After 20 passes the target is at 0.51, and after 200 it is 1.00 with the loss at 0.00. Averaging also smooths what the model learns: a word's vector is shaped by the average of its contexts, so CBOW tends to compress distinctions as well as similarities, and the folklore that skip-gram wins on rare words is a claim about billions of tokens rather than a law — every embedding comparison is a corpus-size claim first.",
    ),
    steps = listOf(
        StepCard(1, "Slide a Window", "±2 to ±5 tokens around each position; the centre word is the target.", 0xFF6366F1),
        StepCard(2, "Average the Context", "h = mean of the context vectors — order inside the window is gone.", 0xFF8B5CF6),
        StepCard(3, "Score the Target", "Dot h against the output vector of the true centre word.", 0xFF3B82F6),
        StepCard(4, "Sample Negatives", "5–20 noise words drawn from unigram^0.75, pushed away from h.", 0xFF06B6D4),
        StepCard(5, "Backpropagate to the Inputs", "The gradient is split across the context words that produced h.", 0xFF14B8A6),
        StepCard(6, "Keep the Input Matrix", "The trained input vectors are the embeddings; the output matrix is discarded.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "maximise Σ log P(w_t | w_{t−c}…w_{t+c})", "Predict the centre from its context."),
        FormulaEntry("Hidden vector", "h = (1/2c) Σ v(w_{t+j}), j ≠ 0", "The averaging that gives the method its name."),
        FormulaEntry("Negative sampling", "log σ(h·u_t) + Σₖ log σ(−h·u_k)", "One positive, k noise words — a binary problem in place of a softmax."),
        FormulaEntry("Updates per pass", "one per position: 5 vs skip-gram's 14", "\"the king rules the kingdom\", window ±2."),
        FormulaEntry("Cost per example", "(k+1)·d vs |V|·d", "96 against 416 multiply-adds here; 1,800 against 300M at |V| = 1M, d = 300."),
        FormulaEntry("Training, one example", "P(rules) 0.25 → 0.51 → 1.00", "Untrained, after 20 passes, after 200 — loss 1.39 → 0.00."),
    ),
    notationKey = listOf(
        NotationEntry("c", "half-window size; the context is the 2c tokens around the centre"),
        NotationEntry("v(w) / u(w)", "input (embedding) and output (context) vectors — two matrices, one kept"),
        NotationEntry("negative sampling", "replacing the softmax with k+1 logistic decisions"),
        NotationEntry("subsampling", "dropping frequent words with probability 1 − √(t/f), a second frequency correction"),
        NotationEntry("self-supervision", "labels derived from the data's own structure rather than annotation"),
        NotationEntry("pretext task", "a task you do not care about, solved to obtain the representation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "One CBOW update, written out",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                V, d, k, lr = 26, 16, 5, 0.05
                W_in  = np.random.uniform(-.5/d, .5/d, (V, d))   # the embeddings, kept
                W_out = np.zeros((V, d))                          # context matrix, discarded

                def update(context_ids, target_id, noise_ids):
                    h = W_in[context_ids].mean(axis=0)             # <- the averaging
                    grad = np.zeros(d)
                    for word, label in [(target_id, 1.0)] + [(n, 0.0) for n in noise_ids]:
                        p = 1 / (1 + np.exp(-h @ W_out[word]))
                        g = (label - p) * lr
                        grad        += g * W_out[word]
                        W_out[word] += g * h
                    # one gradient, split across every context word that produced h
                    W_in[context_ids] += grad / len(context_ids)

                # gensim, for anything real:
                from gensim.models import Word2Vec
                model = Word2Vec(sentences, sg=0, vector_size=100, window=5, negative=5, min_count=1)
                #                          ^^^^ sg=0 is CBOW, sg=1 is skip-gram
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Choosing between the two objectives",
            accentColor = 0xFFEC4899,
            code = """
                # The published guidance:
                #   CBOW      faster to train, better on frequent words, smoother vectors
                #   skip-gram slower, better on rare words and small corpora
                #
                # On a tiny corpus that guidance can invert -- both models are underfitted in
                # different directions, and neither number transfers.
                #
                # So measure on YOUR corpus and YOUR task, with an intrinsic check:
                for name, model in [("cbow", cbow), ("sg", sg)]:
                    related   = model.wv.similarity("king", "queen")
                    unrelated = model.wv.similarity("king", "dog")
                    print(name, round(related - unrelated, 3))   # the contrast, not the raw score

                # ...and an extrinsic one, which is the only one that decides anything: swap the
                # embeddings into the downstream classifier and compare its metric.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF6366F1, "Text Classification Features", "Averaged CBOW vectors remain a fast, strong document representation for small labelled sets."),
        ApplicationCard("users", 0xFF8B5CF6, "Recommenders", "item2vec applies the identical objective to purchase and play sequences instead of sentences."),
        ApplicationCard("chip", 0xFF3B82F6, "On-Device NLP", "A lookup table plus a mean is cheap enough for hardware no transformer will run on."),
        ApplicationCard("flask", 0xFF14B8A6, "Domain Corpora", "Legal, clinical or code corpora where pretrained vectors do not cover the vocabulary."),
    ),
    takeaways = listOf(
        "CBOW predicts the centre word from its averaged context — self-supervised, no labels anywhere.",
        "Averaging discards order inside the window and makes the whole window one update: 5 per pass on the lab's sentence vs skip-gram's 14.",
        "Negative sampling replaces the |V|-way softmax with k+1 binary decisions — 96 multiply-adds against 416 on this corpus.",
        "The smoothing cuts both ways: averaged contexts pull similar words together and blur distinctions too.",
        "Rare-word folklore is a claim about billions of tokens — embedding comparisons are corpus-size claims, so measure on yours.",
    ),
    crossLinks = listOf(
        CrossLink("word2vec_skipgram", "Word2Vec (Skip-Gram)"),
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("glove", "GloVe"),
        CrossLink("n_grams", "N-Grams"),
    ),
)
