package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val xlnetContent = TopicContent(
    topicId = "xlnet",
    whatIsIt = listOf(
        "XLNet is best understood as a precise critique of BERT with an architecture attached. The critique has two halves. First, masking corrupts the input with a [MASK] token that appears in 12% of pretraining positions (15% selected, 80% of those masked) and in exactly none at fine-tuning time, so the model spends pretraining learning about a symbol its real inputs never contain. Second — and this is the sharper one — masking multiple tokens and predicting them independently assumes they are conditionally independent given the context, and they are not.",
        "The lab prices that assumption. Mask both tokens of \"New York\" and BERT's objective multiplies two marginals: 0.30 × 0.35 = 0.105. The true joint factorises as P(New) × P(York | New) = 0.30 × 0.90 = 0.270, because seeing \"New\" nearly determines \"York\". That is a 2.6× gap on one ordinary bigram, and BERT's objective has no way to represent the dependency at all.",
        "XLNet's answer keeps autoregression and randomises the *order*: predict tokens one at a time in a factorization order sampled per example, so every token eventually conditions on every other, in some order, with no [MASK] ever entering the input. A four-token sequence has 24 orders; an eight-token one has 40,320. That creates an implementation problem — to predict position k the model must know which position it is predicting without seeing what is there — solved with two attention streams: a content stream that sees the token and a query stream that sees only its position plus what the order has already revealed. It beat BERT on twenty tasks in 2019 and then largely lost the argument anyway: RoBERTa showed most of BERT's gap was under-training rather than the objective, and the field went to decoder-only scaling. Learn it for the analysis, which keeps recurring, rather than the architecture, which did not survive.",
    ),
    steps = listOf(
        StepCard(1, "Name the Assumption", "Independent prediction of masked tokens is a modelling choice with a cost.", 0xFFEC4899),
        StepCard(2, "Sample an Order", "A permutation of positions; the sequence itself is never reordered.", 0xFFF43F5E),
        StepCard(3, "Predict Autoregressively", "Each target conditions on everything earlier *in that order*.", 0xFF8B5CF6),
        StepCard(4, "Run Two Streams", "Content sees the token; query sees only the position being predicted.", 0xFF6366F1),
        StepCard(5, "Predict the Tail Only", "About the last sixth of the order — earlier targets have too little context.", 0xFF3B82F6),
        StepCard(6, "Add Segment Recurrence", "Transformer-XL's memory and relative encodings, for long documents.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Permutation objective", "max E_{z~Z_T} Σ log P(x_{z_t} | x_{z<t})", "Expectation over factorization orders, not over masks."),
        FormulaEntry("BERT's independence", "P(New, York) ≈ P(New)·P(York) = 0.105", "Two marginals multiplied."),
        FormulaEntry("The true joint", "P(New)·P(York | New) = 0.270", "2.6× higher — the dependency BERT cannot express."),
        FormulaEntry("Order count", "T! — 24 for 4 tokens, 40,320 for 8", "The space sampled from, one order per example."),
        FormulaEntry("Two-stream attention", "content h sees x_{z≤t}; query g sees x_{z<t} + position z_t", "Needed because the target's identity must be known but not its content."),
        FormulaEntry("Prediction budget", "~1/6 of positions (XLNet) vs 15% (BERT)", "Targets with almost no context are noise."),
    ),
    notationKey = listOf(
        NotationEntry("factorization order", "the sequence in which positions are predicted; the input order never changes"),
        NotationEntry("permutation LM", "autoregressive prediction under a sampled order — XLNet's objective"),
        NotationEntry("two-stream attention", "parallel content and query streams; the mechanism that makes the objective computable"),
        NotationEntry("pretrain/finetune mismatch", "a symbol present at pretraining and absent downstream — BERT's [MASK]"),
        NotationEntry("Transformer-XL", "segment recurrence plus relative encodings; XLNet's backbone"),
        NotationEntry("partial prediction", "predicting only the last part of each order, where context is sufficient"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The independence assumption, priced",
            accentColor = 0xFFEC4899,
            code = """
                # "New York is a city", with both tokens of the name masked.
                p_new           = 0.30    # P(New | context)
                p_york          = 0.35    # P(York | context)
                p_york_give_new = 0.90    # P(York | context, New)

                bert  = p_new * p_york            # 0.105 -- independent factorisation
                truth = p_new * p_york_give_new   # 0.270 -- chain rule
                print(truth / bert)               # 2.57x

                # BERT cannot represent that dependency: both predictions are made from the same
                # encoder state, in parallel, with no path from one to the other. XLNet's ordering
                # makes it explicit -- if New comes first in the sampled order, York conditions
                # on it.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Permuted orders and the two streams",
            accentColor = 0xFF6366F1,
            code = """
                import itertools, random

                positions = [0, 1, 2, 3]
                print(len(list(itertools.permutations(positions))))   # 24 orders

                order = random.sample(positions, len(positions))      # e.g. [2, 0, 3, 1]
                for step, target in enumerate(order):
                    content_sees = order[:step + 1]   # includes the target's own token
                    query_sees   = order[:step]       # position of target only, not its content
                    print(target, content_sees, query_sees)

                # The input tensor is never permuted -- the order is realised as an attention mask,
                # so absolute positions stay intact and the model still knows where words are.
                #
                # Practical note: XLNet is ~5x BERT's pretraining compute for the two streams and
                # partial prediction, which is part of why RoBERTa's "just train BERT properly"
                # answer won on cost-effectiveness.
                from transformers import XLNetLMHeadModel, XLNetTokenizer
                tok = XLNetTokenizer.from_pretrained("xlnet-base-cased")
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Long-Document Tasks", "The Transformer-XL backbone made it strong on documents that overflow BERT's window."),
        ApplicationCard("search", 0xFF8B5CF6, "Ranking & QA", "Its 2019 gains were largest on reading comprehension and passage ranking."),
        ApplicationCard("flask", 0xFF6366F1, "Objective Design", "The clearest worked example of what a pretraining objective assumes and what it costs."),
        ApplicationCard("history", 0xFF14B8A6, "Method Lineage", "Permutation and order-agnostic training resurface in diffusion LMs and any-order models."),
    ),
    takeaways = listOf(
        "Two complaints about BERT: [MASK] never appears downstream, and masked tokens are predicted independently.",
        "That independence costs a measured 2.6× on \"New York\" — 0.105 against a true joint of 0.270.",
        "Permutation LM keeps autoregression and samples the factorization order (24 orders for 4 tokens, 40,320 for 8).",
        "Two attention streams are required so a target's position is known while its content stays hidden.",
        "RoBERTa showed most of BERT's gap was under-training, and decoder-only scaling won — the analysis outlived the model.",
    ),
    crossLinks = listOf(
        CrossLink("bart", "BART"),
        CrossLink("attention", "Attention"),
        CrossLink("transformers", "Transformers"),
        CrossLink("n_grams", "N-Grams"),
    ),
)
