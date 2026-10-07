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

internal val t5Content = TopicContent(
    topicId = "t5",
    figure = Figure(
        caption = "T5's one idea, as the page's lab lays it out: every task is text in, text out. " +
            "A short prefix names the task, the same encoder reads the input, and the same " +
            "decoder writes the answer as text — a German sentence, the word \"acceptable\", the " +
            "number \"3.8\" spelled as characters, or a summary. Classification and regression " +
            "become generation, so there is one model, one cross-entropy loss over target " +
            "tokens, and one decoding path for all of them. Pretraining fits the same mould: " +
            "spans of C4 text are replaced by sentinels — \"the <X> sat on <Y>\" — and the decoder " +
            "generates what was removed, \"<X> cat <Y> the mat\", so pretraining and fine-tuning " +
            "use the identical interface from T5-base to T5-11B.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("translate English to German:", "Das ist gut."),
                listOf("cola sentence:", "acceptable"),
                listOf("stsb sentence1: … sentence2:", "3.8"),
                listOf("summarize:", "(summary)"),
            ),
            colHeaders = listOf("task prefix", "decoder output"),
            marks = listOf(
                FigureCell(2, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "T5's claim is that every NLP task is text in, text out. Classification becomes generating the word \"acceptable\"; similarity scoring becomes generating \"3.8\"; translation, summarisation and question answering were already text-to-text. Put a task prefix on the front and one encoder-decoder handles all of them, with one loss and one decoding path — which is what makes multi-task pre-training and transfer between tasks a matter of mixing data rather than of building heads.",
        "Its pre-training objective corrupts spans rather than single tokens, and the shape of that is the economy of the whole design. Each contiguous run of dropped tokens becomes one sentinel in the input, and the target is only the dropped runs, each introduced by its sentinel. At the length these models actually run — 512 tokens, 15% corrupted, mean span 3 — the encoder reads 461 tokens and the decoder emits 104. BERT's objective makes a model produce an output at all 512 positions, so span corruption costs 4.9× fewer decoder steps for the same corruption budget.",
        "The price is that there are two stacks. Summed from its config, T5-base is 222,903,552 parameters, and they are not evenly split: the decoder is 113,274,624 against the encoder's 84,953,856, because every decoder layer carries a cross-attention block the encoder layers do not have. Against BERT-base's 109,482,240 (encoder only, cannot generate) and GPT-2 small's 124,439,808 (decoder only, reads one direction), T5 pays for both and gets both — bidirectional reading of the input and generation of the output. T5 also drops every bias term in the model, which is a small saving stated for completeness rather than a design highlight.",
    ),
    steps = listOf(
        StepCard(1, "Frame as Text", "A task prefix, an input string, a target string.", 0xFF3B82F6),
        StepCard(2, "Corrupt Spans", "15% of tokens, mean span 3, one sentinel per run.", 0xFFF59E0B),
        StepCard(3, "Encode Bidirectionally", "The encoder sees the whole corrupted input.", 0xFF6366F1),
        StepCard(4, "Decode the Spans Only", "104 target tokens for 512 of input.", 0xFF10B981),
        StepCard(5, "Cross-Attend", "Every decoder layer reads the encoder — the extra block.", 0xFF8B5CF6),
        StepCard(6, "Pay for Two Stacks", "222,903,552 parameters, the decoder the larger half.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Corruption budget", "0.15·n tokens, spans of ~3", "77 tokens in 26 spans at n = 512."),
        FormulaEntry("Input length", "n − corrupted + spans", "461 at n = 512."),
        FormulaEntry("Target length", "corrupted + spans + 1", "104 — the final sentinel is the stop signal."),
        FormulaEntry("Against masked LM", "512 → 104 output positions", "4.9× fewer decoder steps."),
        FormulaEntry("T5-base size", "222,903,552", "Encoder 84,953,856 · decoder 113,274,624."),
        FormulaEntry("Decoder layer", "2 attention blocks + FFN", "9,439,488 against the encoder layer's 7,079,424."),
    ),
    notationKey = listOf(
        NotationEntry("sentinel", "the <X0>, <X1>… placeholder tokens standing in for dropped spans"),
        NotationEntry("span", "a contiguous run of corrupted tokens — one sentinel covers all of it"),
        NotationEntry("task prefix", "the string that tells one model which task this is"),
        NotationEntry("cross-attention", "the extra block per decoder layer; why the decoder is the bigger half"),
        NotationEntry("relative position bias", "T5's position scheme — a learned bias per bucket, not an embedding"),
        NotationEntry("RMSNorm", "T5's normalisation: a scale and no bias, in a model with no biases anywhere"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Every task, one interface",
            accentColor = 0xFF3B82F6,
            code = """
                from transformers import T5ForConditionalGeneration, T5Tokenizer

                model = T5ForConditionalGeneration.from_pretrained("t5-base")
                tokenizer = T5Tokenizer.from_pretrained("t5-base")

                def run(text: str) -> str:
                    ids = tokenizer(text, return_tensors="pt").input_ids
                    return tokenizer.decode(model.generate(ids, max_new_tokens=32)[0], skip_special_tokens=True)

                run("translate English to German: the house is small")
                run("cola sentence: the boy are running")            # -> "unacceptable"
                run("stsb sentence1: a man plays guitar sentence2: a person plays a guitar")  # -> "4.4"
                run("summarize: " + article)

                # The prefix is data, not configuration. Adding a task means adding examples with a
                # new prefix -- no new head, no new loss, no new decoding path. That is the claim
                # the paper is named after, and it is also why the prefixes have to be spelled
                # exactly as they were during pre-training.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Span corruption, and why the target is short",
            accentColor = 0xFFF59E0B,
            code = """
                def span_corrupt(tokens, rate=0.15, mean_span=3):
                    budget = round(len(tokens) * rate)
                    spans = max(1, round(budget / mean_span))
                    # (span placement omitted -- what matters is the accounting below)
                    return {
                        "input_length": len(tokens) - budget + spans,
                        "target_length": budget + spans + 1,      # + the final sentinel
                        "masked_lm_output_positions": len(tokens),
                    }

                print(span_corrupt(list(range(512))))
                # {'input_length': 461, 'target_length': 104, 'masked_lm_output_positions': 512}
                #
                # A decoder pays per step it emits, so 104 against 512 is the whole reason this
                # objective is affordable in an encoder-decoder. It is also why the sentinels are
                # in the target at all: without them the model would not know where one recovered
                # span ends and the next begins.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "Translation & Summarisation", "The tasks an encoder-decoder was built for."),
        ApplicationCard("stack", 0xFF3B82F6, "Multi-Task Serving", "One checkpoint, many tasks, selected by prefix."),
        ApplicationCard("flask", 0xFF8B5CF6, "Transfer Studies", "The paper is an ablation matrix; the format is what made it possible."),
        ApplicationCard("finance", 0xFFEC4899, "Cost", "Two stacks and cross-attention — roughly double the layer bill."),
    ),
    takeaways = listOf(
        "Every task framed as text in, text out, selected by a prefix that is data rather than configuration.",
        "Span corruption replaces each run of dropped tokens with one sentinel and asks for only the runs back.",
        "At 512 tokens and 15% corruption: 77 tokens in 26 spans, input 461, target 104.",
        "That is 4.9× fewer decoder steps than a masked model's 512 output positions, for the same corruption.",
        "T5-base summed from its config is 222,903,552 parameters, against a reported 220M.",
        "The decoder is the larger half — 113M against 85M — because every decoder layer adds a cross-attention block.",
        "The trade against BERT and GPT is explicit: pay for two stacks, get bidirectional reading *and* generation.",
    ),
    crossLinks = listOf(
        CrossLink("encoder_decoder", "Encoder-Decoder Architecture"),
        CrossLink("bert", "BERT"),
        CrossLink("gpt", "GPT"),
        CrossLink("bart", "BART"),
        CrossLink("self_cross_attention", "Self- vs Cross-Attention"),
    ),
)
