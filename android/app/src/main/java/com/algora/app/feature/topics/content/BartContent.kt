package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bartContent = TopicContent(
    topicId = "bart",
    whatIsIt = listOf(
        "BART is the obvious architecture nobody had shipped: a complete encoder-decoder transformer, pretrained by corrupting text arbitrarily and asking it to reconstruct the original. BERT's encoder can fill blanks but cannot generate; GPT's decoder can generate but only sees leftward context. BART has both stacks, so a single pretrained model fine-tunes for classification *and* generation without any architectural surgery — and pays about 10% more parameters than BERT-large (400M vs 340M) for the extra decoder.",
        "The corruption is where the design work is, and the five noise functions are not variations on one idea. Token masking replaces individual tokens with [MASK] — BERT's objective. Token deletion removes them with no placeholder, so the *position* of what is missing becomes part of the prediction. Text infilling replaces a whole span with one [MASK], which can stand for zero, one or many tokens, so the model must predict how much is missing as well as what; it is the strongest single objective in the paper's ablation. Sentence permutation and document rotation lose no tokens at all — they destroy order and hide where the document begins, moving the problem up to document level.",
        "Because the decoder is autoregressive and the encoder is bidirectional, fine-tuning is uniform: classification feeds the input to both stacks and reads the decoder's final state, generation feeds the source to the encoder and writes the target with the decoder, and translation adds a small randomly-initialised encoder in front to map a foreign vocabulary into BART's. It set the state of the art on CNN/DailyMail summarisation and matched RoBERTa on GLUE at comparable training cost — the argument for the shape in one line. The decoder-only scaling wave that followed made BART less central, but the encoder-decoder shape remains the right default when input and output are different objects (summarise, translate, rewrite) rather than a continuation of the same stream.",
    ),
    steps = listOf(
        StepCard(1, "Take a Document", "Corruption is applied at document level, not sentence level.", 0xFFEC4899),
        StepCard(2, "Corrupt It", "Sample from the five noise functions — infilling does most of the work.", 0xFFF43F5E),
        StepCard(3, "Encode Bidirectionally", "The encoder sees the whole corrupted document at once.", 0xFF8B5CF6),
        StepCard(4, "Decode Autoregressively", "The decoder reconstructs the original left to right, cross-attending to the encoder.", 0xFF6366F1),
        StepCard(5, "Score Reconstruction", "Cross-entropy against the original document — no masking bookkeeping needed.", 0xFF3B82F6),
        StepCard(6, "Fine-Tune Unchanged", "Classification reads the decoder's end; generation just runs it.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "maximise log P(original | corrupted)", "Plain sequence-to-sequence reconstruction — any corruption is legal."),
        FormulaEntry("Token masking", "tokens → [MASK], length preserved", "BERT's objective, one of five."),
        FormulaEntry("Token deletion", "tokens removed, no placeholder", "The position of the gap becomes part of the prediction."),
        FormulaEntry("Text infilling", "span → one [MASK], length hidden", "The strongest single objective in the ablation."),
        FormulaEntry("Permutation / rotation", "no tokens lost, order destroyed", "Document-level structure as the training signal."),
        FormulaEntry("Parameter cost", "400M vs BERT-large's 340M", "≈10% for a decoder that makes generation free."),
    ),
    notationKey = listOf(
        NotationEntry("denoising autoencoder", "a model trained to reconstruct clean input from a corrupted version"),
        NotationEntry("encoder-decoder", "bidirectional encoding plus autoregressive decoding, joined by cross-attention"),
        NotationEntry("noise function", "one corruption strategy; BART samples across five"),
        NotationEntry("text infilling", "replacing a variable-length span with a single mask token"),
        NotationEntry("cross-attention", "decoder attending over encoder states — the join between the stacks"),
        NotationEntry("seq2seq fine-tuning", "source in, target out; the shape summarisation and translation both want"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The five corruptions",
            accentColor = 0xFFEC4899,
            code = """
                import random

                doc = "the cat sat on the mat . it purred loudly .".split()

                def token_masking(x, k=2):
                    x = x[:]
                    for i in random.sample(range(len(x)), k):
                        x[i] = "[MASK]"
                    return x                      # length preserved, positions given away

                def token_deletion(x, k=2):
                    drop = set(random.sample(range(len(x)), k))
                    return [t for i, t in enumerate(x) if i not in drop]   # position hidden

                def text_infilling(x, spans=((2, 3), (7, 2))):
                    out, removed = x[:], 0
                    for start, length in sorted(spans, reverse=True):
                        out[start:start + length] = ["[MASK]"]             # length hidden too
                    return out

                def sentence_permutation(x):
                    stop = x.index(".") + 1
                    return x[stop:] + x[:stop]

                def document_rotation(x, pivot=4):
                    return x[pivot:] + x[:pivot]   # pivot mid-sentence, or it equals permutation

                # The taxonomy that matters is a 2x2: are tokens GONE, and is ORDER changed?
                #   masking      no / no      deletion    yes / no
                #   infilling    yes / no (and length unknown)
                #   permutation, rotation      no / yes
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Fine-tuning the same checkpoint two ways",
            accentColor = 0xFF6366F1,
            code = """
                from transformers import BartForConditionalGeneration, BartForSequenceClassification, BartTokenizer

                tok = BartTokenizer.from_pretrained("facebook/bart-large-cnn")

                # Generation: source in, target out. No architectural change from pretraining.
                gen = BartForConditionalGeneration.from_pretrained("facebook/bart-large-cnn")
                ids = tok(long_article, return_tensors="pt", truncation=True).input_ids
                print(tok.decode(gen.generate(ids, num_beams=4, max_length=128)[0], skip_special_tokens=True))

                # Classification: feed the input to BOTH stacks and read the decoder's final token.
                clf = BartForSequenceClassification.from_pretrained("facebook/bart-large", num_labels=3)

                # The point of the shape: one pretrained checkpoint, both jobs. A BERT checkpoint
                # cannot do the first without bolting on a decoder that was never pretrained; a GPT
                # checkpoint does the second with only leftward context over the source.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Summarisation", "BART-large-CNN was the default abstractive summariser for years and still ships in production."),
        ApplicationCard("globe", 0xFF8B5CF6, "Translation", "A small added encoder maps a new vocabulary into a frozen BART."),
        ApplicationCard("check", 0xFF6366F1, "Grammar & Rewriting", "Denoising is literally the pretraining task, so correction fine-tunes cheaply."),
        ApplicationCard("search", 0xFF14B8A6, "Data Augmentation", "The corruption functions themselves are a reusable augmentation toolkit."),
    ),
    takeaways = listOf(
        "A full encoder-decoder pretrained by reconstructing corrupted text — classification and generation from one checkpoint.",
        "Five noise functions, split by what they destroy: tokens, length, or order.",
        "Text infilling is the strongest because one mask can stand for zero, one or many tokens.",
        "The extra decoder costs ~10% over BERT-large (400M vs 340M) and makes generation free.",
        "Encoder-decoder is still the right default when the output is a different object from the input.",
    ),
    crossLinks = listOf(
        CrossLink("xlnet", "XLNet"),
        CrossLink("transformers", "Transformers"),
        CrossLink("attention", "Attention"),
        CrossLink("llms", "LLMs"),
    ),
)
