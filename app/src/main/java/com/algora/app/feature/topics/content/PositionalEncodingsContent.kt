package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val positionalEncodingsContent = TopicContent(
    topicId = "positional_encodings",
    whatIsIt = listOf(
        "Self-attention is permutation-equivariant: shuffle the input tokens and the output representations shuffle with them, otherwise unchanged. \"the cat sat\" and \"sat cat the\" are the same computation. Order has to be injected as part of the input, because the mechanism itself provably cannot see it — and every difference between the schemes below is a different answer to how.",
        "The original transformer adds a fixed sinusoid per dimension pair, with wavelengths in geometric progression from 2π to 10000·2π, so early dimensions cycle every few positions and late ones barely move across the whole sequence. The property that makes it work is that the dot product of two encodings depends only on the *offset* between them: measured across absolute positions 0, 4, 8 and 12, the spread for a fixed offset is 0.0000 to floating-point precision. Relative distance becomes available to attention without ever being stored.",
        "What that dot product does *not* do is decay monotonically. It falls to offset 3 and then rises again at offsets 4, 5, 6, 11 and 12 — it is a sum of cosines at different frequencies, so \"nearby positions look similar\" is true on average and false pointwise, whatever the smooth curves in the diagrams suggest. The schemes that replaced it fix exactly that. RoPE rotates each 2-D slice of the query and key by an angle proportional to position, making the score an algebraic function of the gap alone (measured spread across four absolute positions: 0.000000). ALiBi drops embeddings entirely and subtracts a per-head linear penalty from the score, which *is* monotone by construction and therefore extrapolates past the training length gracefully. Learned tables — BERT's 512 × 768, 393,216 parameters — work well inside their range and have no row at position 513 at all; that hard wall, not accuracy, is why the field moved on.",
    ),
    steps = listOf(
        StepCard(1, "Notice the Symmetry", "Attention is permutation-equivariant — position is not implicit anywhere in it.", 0xFF3B82F6),
        StepCard(2, "Build the Table", "PE(pos, 2i) = sin(pos / 10000^(2i/d)), odd dimensions cosine.", 0xFF6366F1),
        StepCard(3, "Add, Don't Concatenate", "The encoding is summed into the embedding, so it costs no extra width.", 0xFF8B5CF6),
        StepCard(4, "Check Offset Invariance", "PE(p)·PE(p+k) must depend on k alone — the whole reason for the design.", 0xFF06B6D4),
        StepCard(5, "Prefer Relative for Long Context", "RoPE makes the relative property exact; ALiBi makes the penalty monotone.", 0xFF14B8A6),
        StepCard(6, "Test Past the Training Length", "Extrapolation is where the schemes actually differ, and where learned tables die.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Sinusoidal", "PE(p, 2i) = sin(p / 10000^(2i/d)) · PE(p, 2i+1) = cos(…)", "Wavelengths from 6.3 to 198.7 positions in the lab's 16-dimensional table."),
        FormulaEntry("Offset invariance", "PE(p)·PE(p+k) is a function of k only", "Measured spread across p ∈ {0,4,8,12}: 0.0000."),
        FormulaEntry("Not monotone", "similarity rises again at offsets 4, 5, 6, 11, 12", "A sum of cosines, not a decay curve — the usual diagram is wrong."),
        FormulaEntry("RoPE", "⟨R_m q, R_n k⟩ = f(q, k, m − n)", "An identity, not an approximation: measured spread 0.000000."),
        FormulaEntry("ALiBi", "score += −slopeₕ · |i − j|", "Slopes 0.5, 0.25, … 0.0039 across 8 heads — monotone by construction."),
        FormulaEntry("Learned table", "512 × 768 = 393,216 parameters", "And nothing at all at position 513."),
    ),
    notationKey = listOf(
        NotationEntry("permutation equivariance", "shuffling inputs shuffles outputs identically — attention's blind spot"),
        NotationEntry("absolute vs relative", "encode where a token is, or how far apart two tokens are"),
        NotationEntry("RoPE", "rotary position embedding — rotate query/key pairs by an angle ∝ position"),
        NotationEntry("ALiBi", "attention with linear biases — a distance penalty added to the score"),
        NotationEntry("extrapolation", "behaviour past the training context length; the practical dividing line"),
        NotationEntry("position interpolation", "rescaling RoPE angles to stretch a trained window to a longer one"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The table, and the property it is built for",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np

                def sinusoidal(max_len, d, base=10000.0):
                    pos = np.arange(max_len)[:, None]
                    i = np.arange(d)[None, :]
                    angle = pos / base ** (2 * (i // 2) / d)
                    pe = np.where(i % 2 == 0, np.sin(angle), np.cos(angle))
                    return pe

                pe = sinusoidal(64, 16)

                # The dot product depends only on the offset, not the absolute position:
                for p in (0, 4, 8, 12):
                    print(round(float(pe[p] @ pe[p + 4]), 6))   # same value every time

                # ...but it is NOT a monotone decay. Print the profile and look:
                print([round(float(pe[0] @ pe[k]), 2) for k in range(9)])
                # [8.0, 7.49, 6.37, 5.54, 5.56, 6.14, 6.45, 5.89, 4.7]   <- rises at k=4,5,6
            """.trimIndent(),
        ),
        CodeBlock(
            title = "RoPE, which is what modern models actually use",
            accentColor = 0xFFEC4899,
            code = """
                import torch

                def rope(x, position, base=10000.0):
                    d = x.shape[-1]
                    pair = torch.arange(d // 2)
                    theta = position / base ** (2 * pair / d)
                    cos, sin = torch.cos(theta), torch.sin(theta)
                    even, odd = x[..., 0::2], x[..., 1::2]
                    out = torch.empty_like(x)
                    out[..., 0::2] = even * cos - odd * sin
                    out[..., 1::2] = even * sin + odd * cos
                    return out

                q, k = torch.randn(16), torch.randn(16)
                print(rope(q, 5) @ rope(k, 0))      # same gap...
                print(rope(q, 25) @ rope(k, 20))    # ...same score, exactly

                # Extending a trained window: position interpolation divides the angles by the
                # stretch factor, so a model trained at 4K can be fine-tuned to 32K cheaply
                # instead of retrained. NTK-aware and YaRN scaling are refinements of the same
                # idea. None of this is available to a learned table, which simply has no row.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Every Transformer", "No positional scheme, no word order — this is load-bearing, not a detail."),
        ApplicationCard("browser", 0xFF6366F1, "Long-Context Models", "RoPE plus interpolation is how a 4K-trained model becomes a 128K one."),
        ApplicationCard("flask", 0xFF8B5CF6, "Non-Text Sequences", "Protein chains, audio frames and time series need the same trick."),
        ApplicationCard("help", 0xFFEC4899, "Where It Breaks", "Past the trained length — learned tables have no answer, sinusoids degrade, ALiBi holds up best."),
    ),
    takeaways = listOf(
        "Attention cannot see order at all — position is injected, and the scheme is a real design choice.",
        "Sinusoidal encodings are offset-invariant to 0.0000, which is the property they are chosen for.",
        "They do not decay monotonically: similarity rises again at offsets 4, 5, 6, 11 and 12 on the lab's table.",
        "RoPE makes the relative score an exact identity (spread 0.000000) and is what modern open models use.",
        "Learned tables (512 × 768 = 393,216 params) work in range and have no row past it — the wall that ended them.",
    ),
    crossLinks = listOf(
        CrossLink("attention", "Attention"),
        CrossLink("transformers", "Transformers"),
        CrossLink("feed_forward", "Feed-Forward Networks"),
        CrossLink("llms", "LLMs"),
    ),
)
