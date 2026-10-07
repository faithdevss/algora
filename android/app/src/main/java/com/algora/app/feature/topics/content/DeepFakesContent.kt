package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val deepFakesContent = TopicContent(
    topicId = "deepfakes",
    figure = Figure(
        caption = "The page's lab: swap error, A's expression put onto B's face, as B's expression " +
            "space is rotated away from A's. The dashed line is the bar to clear: a model that " +
            "ignores its input and always outputs B's average face scores 0.366. The shared " +
            "encoder clears it easily when the two faces move alike, 0.181 at 0°, and holds to " +
            "0.230 at 45°. Then it climbs: 0.311 at 60°, and 0.612 at 75°, well past the " +
            "baseline. One encoder per identity is worse than the baseline even at 0°, at 0.480. " +
            "Both setups rebuild their own faces with error 0.000, so reconstruction quality says " +
            "nothing about the swap.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "shared encoder",
                    listOf(
                        FigurePoint(0f, 0.270f), FigurePoint(0.167f, 0.276f), FigurePoint(0.333f, 0.296f),
                        FigurePoint(0.5f, 0.343f), FigurePoint(0.667f, 0.464f), FigurePoint(0.833f, 0.913f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "mean-face baseline",
                    listOf(FigurePoint(0f, 0.546f), FigurePoint(1f, 0.546f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 0.716f, "independent 0.480", FigureTone.Warn),
                FigurePoint(0.833f, 0.913f, "0.612 at 75°", FigureTone.Warn),
            ),
            xLabel = "angle between expression spaces, 0° → 90°",
            yLabel = "swap error, 0 to 0.67",
        ),
    ),
    whatIsIt = listOf(
        "The classic face-swap architecture is one shared encoder and two identity-specific decoders. Both identities' faces go through the same encoder; identity A's faces are reconstructed by decoder A and identity B's by decoder B, each trained only on its own person. The swap is what happens when you break that pairing at inference: run A's face through the shared encoder and hand the code to decoder B. The intended division of labour is that the encoder captures pose and expression while each decoder supplies a specific face. Nothing in the loss states that division — it is a hoped-for consequence of the encoder being shared and the decoders not being.",
        "The sharing is genuinely load-bearing, and the failure without it is worse than it sounds. Give each identity its own encoder and the two latent spaces are fitted independently, so each orders and scales its directions by that identity's own variance. The same expression lands on different numbers and the receiving decoder reads it as a different expression. Scored against the correct answer — identity B's face wearing identity A's expression — the independent arrangement reaches an error of 0.480, while a model that ignores the input completely and always emits B's average face scores 0.366. Independent encoders do not merely degrade the swap; they are measurably worse than not attempting it. The shared encoder scores 0.181, comfortably better than the baseline, and this is not a reconstruction problem: both arrangements rebuild their own identity's faces essentially perfectly.",
        "But sharing is necessary and not sufficient, which is the part the usual explanation leaves out. A shared code only means the same thing to both decoders if the two identities' expression manifolds sit similarly in face space. Rotate them apart and the swap degrades continuously — 0.181 at no separation, 0.198 at 30°, 0.311 at 60° — until at 90°, where the manifolds are orthogonal, the decoder's implied inverse becomes singular and the output diverges entirely. There is no training fix on that curve; the information needed is not in the code. This is the measurable form of a thing every practitioner reports and no summary explains: face swaps work far better between people who already look and move alike, and a badly matched pair stays bad no matter how long it trains.",
    ),
    steps = listOf(
        StepCard(1, "One Encoder, Two Decoders", "Shared trunk; a separate head per identity.", 0xFF6366F1),
        StepCard(2, "Train Each Pair on Itself", "Encoder + decoder A on A's faces, encoder + decoder B on B's.", 0xFF0EA5E9),
        StepCard(3, "Break the Pairing to Swap", "A's face into the encoder, out through decoder B.", 0xFFF59E0B),
        StepCard(4, "Check Against Doing Nothing", "Independent encoders score 0.480; B's average face scores 0.366.", 0xFFEC4899),
        StepCard(5, "Sweep the Manifold Angle", "0.181 → 0.198 at 30° → 0.311 at 60° → divergence at 90°.", 0xFF10B981),
        StepCard(6, "Detect the Seam", "Blending boundaries and temporal inconsistency, not the face itself.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Architecture", "D_A(E(x)) ≈ x_A,  D_B(E(x)) ≈ x_B", "One E, two Ds. The swap is D_B(E(x_A))."),
        FormulaEntry("Shared encoder", "error 0.181", "Against a mean-face baseline of 0.366."),
        FormulaEntry("Independent encoders", "error 0.480", "Worse than emitting the average face."),
        FormulaEntry("Own-domain rebuild", "≈ 0 either way", "Reconstruction is not what differs."),
        FormulaEntry("Manifold angle", "0.181 · 0.198 · 0.311", "At 0°, 30°, 60° — a continuous cost."),
        FormulaEntry("At 90°", "diverges", "The implied inverse is singular; no training fixes it."),
    ),
    notationKey = listOf(
        NotationEntry("shared encoder", "one trunk over both identities — what puts the codes in one coordinate system"),
        NotationEntry("identity decoder", "a per-person head; supplies the face the code does not carry"),
        NotationEntry("expression manifold", "the subspace a person's face moves in; must align for a swap to transfer"),
        NotationEntry("mean-face baseline", "always emit the target's average face; the bar any swap must clear"),
        NotationEntry("blending seam", "the composite boundary back into the original frame — where detectors look"),
        NotationEntry("temporal consistency", "frame-to-frame coherence; absent when frames are generated independently"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The architecture, and the one line that makes it a swap",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                encoder   = nn.Sequential(...)      # shared -- sees both identities
                decoder_a = nn.Sequential(...)      # trained only on identity A
                decoder_b = nn.Sequential(...)      # trained only on identity B

                def training_step(batch_a, batch_b):
                    loss_a = mse(decoder_a(encoder(batch_a)), batch_a)
                    loss_b = mse(decoder_b(encoder(batch_b)), batch_b)
                    return loss_a + loss_b          # the encoder gets gradient from both

                # Inference crosses the wires, and that is the entire trick:
                swapped = decoder_b(encoder(face_from_a))

                # Note what the loss never says: that the code should carry expression and not
                # identity. That separation is hoped for, not stated, and it holds only to the
                # extent the two faces move in similar ways -- which is what the sweep below prices.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Scoring the swap against the bar it has to clear",
            accentColor = 0xFFEC4899,
            code = """
                # Ground truth for a swap is available only in a controlled setup: render both
                # identities from the same expression parameters, so "B wearing A's expression" is a
                # thing that exists and can be compared against.

                #   arrangement                     error     beats baseline?
                #   shared encoder                  0.181     yes
                #   independent encoders            0.480     NO
                #   baseline: always B's mean face  0.366     --
                #
                # Independent encoders are worse than ignoring the input. Each PCA orders its own
                # identity's directions by that identity's variance, so the codes are not comparable
                # and the decoder reads a different expression than the one that was sent.
                #
                # Both arrangements reconstruct their own identity's faces essentially exactly, so
                # reconstruction quality tells you nothing about whether a swap will work.

                #   manifold angle    shared-encoder error
                #        0 deg               0.181
                #       30 deg               0.198
                #       60 deg               0.311
                #       90 deg               diverges (the implied inverse is singular)
                #
                # Sharing the encoder is necessary and not sufficient. Past a point the code simply
                # does not contain what the far decoder needs, and no training schedule adds it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF8B5CF6, "Detection Research", "Detectors target the blending seam and temporal drift, not the face."),
        ApplicationCard("flask", 0xFF0EA5E9, "Media Forensics", "Provenance and signing scale better than per-artefact detection."),
        ApplicationCard("users", 0xFF10B981, "Consent & Policy", "Why platform rules attach to depiction rather than to method."),
        ApplicationCard("help", 0xFFEC4899, "Why It Often Fails", "Mismatched face pairs — a limit of the code, not of training time."),
    ),
    takeaways = listOf(
        "One shared encoder, two identity-specific decoders; the swap crosses the wires at inference.",
        "The loss never states that the code should carry expression and not identity — that is hoped for.",
        "Independent encoders score 0.480 against a mean-face baseline of 0.366: worse than not trying.",
        "A shared encoder scores 0.181, and reconstruction quality is identical either way — it tells you nothing.",
        "Sharing is necessary but not sufficient: the swap degrades as the two expression manifolds separate.",
        "0.181 at 0°, 0.198 at 30°, 0.311 at 60°, and divergence at 90° where the inverse goes singular.",
        "That curve is why swaps work best between similar faces, and why a bad pairing cannot be trained out.",
        "Detection targets the seam and the temporal drift, because those are what the architecture does not model.",
    ),
    crossLinks = listOf(
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("gans", "GANs"),
        CrossLink("stylegan", "StyleGAN"),
        CrossLink("vae", "Variational Autoencoders (VAE)"),
        CrossLink("transfer_learning", "Transfer Learning"),
    ),
)
