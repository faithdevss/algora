package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val klDivergenceContent = TopicContent(
    topicId = "kl_divergence",
    whatIsIt = listOf(
        "KL divergence measures how one distribution differs from another: KL(P‖Q) = Σ P(x)·ln(P(x)/Q(x)). It is not a distance — it isn't symmetric, and computing it both directions on the same P=(0.5, 0.3, 0.15, 0.05) and Q=uniform proves it: KL(P‖Q)=0.2442, KL(Q‖P)=0.3112. Swapping the arguments changes the number. There's also an exact identity linking it to cross-entropy: cross-entropy(P,Q) = entropy(P) + KL(P‖Q) — checked here as 1.1421 + 0.2442 = 1.3863, matching cross-entropy computed directly to the digit.",
        "The asymmetry isn't just a technicality — the two directions optimize for different things. Fit a single Gaussian Q to a bimodal target P (two bumps at −2.5 and +2.5) by grid search, minimizing each direction separately. Forward KL, KL(P‖Q), is minimized by a wide Gaussian (μ=0.0, σ=3.0) that spreads mass across both modes at once — because forward KL penalizes Q being near-zero anywhere P has mass, so Q is forced to cover the whole target.",
        "Reverse KL, KL(Q‖P), is minimized by a narrow Gaussian (μ=−2.5, σ=0.8) that locks onto exactly one mode — because reverse KL only penalizes Q putting mass where P is near-zero, so Q can safely abandon the mode it doesn't fit and commit fully to the one it does. The reverse fit's own reverse-KL (0.6906) is actually higher than the forward fit's own forward-KL (0.4685), even though the reverse fit looks like the visually tighter match — the two numbers aren't on a comparable scale, which is exactly why picking a direction is a modeling decision, not a formality.",
    ),
    steps = listOf(
        StepCard(1, "Compute KL(P‖Q)", "Σ P(x)·ln(P(x)/Q(x)) — 0.2442 on the example distributions.", 0xFF0EA5E9),
        StepCard(2, "Compute KL(Q‖P)", "Same distributions, arguments swapped — 0.3112, a different number.", 0xFF3B82F6),
        StepCard(3, "Check the Identity", "H(P) + KL(P‖Q) = CE(P,Q): 1.1421 + 0.2442 = 1.3863, matching CE computed directly.", 0xFF8B5CF6),
        StepCard(4, "Fit a Gaussian, Forward Direction", "Minimize KL(P‖Q) over (μ,σ) against a bimodal P: lands wide, μ=0.0, σ=3.0.", 0xFFF59E0B),
        StepCard(5, "Fit a Gaussian, Reverse Direction", "Minimize KL(Q‖P) instead: lands narrow, μ=−2.5, σ=0.8 -- one mode exactly.", 0xFFEC4899),
        StepCard(6, "Compare What Each Costs", "Reverse fit's own reverse-KL (0.6906) exceeds forward fit's own forward-KL (0.4685).", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("KL divergence", "KL(P‖Q) = Σ P(x)·ln(P(x)/Q(x))", "Zero iff P=Q everywhere; otherwise strictly positive."),
        FormulaEntry("Asymmetry", "KL(P‖Q) ≠ KL(Q‖P)", "0.2442 vs 0.3112 on the example distributions."),
        FormulaEntry("Entropy-CE identity", "CE(P,Q) = H(P) + KL(P‖Q)", "1.3863 = 1.1421 + 0.2442, verified exact."),
        FormulaEntry("Forward-KL fit", "argmin_Q KL(P‖Q): μ=0.0, σ=3.0", "Mode-covering — wide, spans both modes."),
        FormulaEntry("Reverse-KL fit", "argmin_Q KL(Q‖P): μ=−2.5, σ=0.8", "Mode-seeking — narrow, locks onto one mode."),
        FormulaEntry("Fit costs", "forward-KL 0.4685 vs reverse-KL 0.6906", "Not directly comparable — different divergences, different scales."),
    ),
    notationKey = listOf(
        NotationEntry("P", "the target (or true) distribution"),
        NotationEntry("Q", "the approximating (or model) distribution"),
        NotationEntry("H(P)", "the entropy of P — the minimum possible average code length"),
        NotationEntry("CE(P,Q)", "cross-entropy — the average code length under Q's assumptions when the true distribution is P"),
        NotationEntry("mode-covering", "forward KL's behavior — Q spreads mass to avoid being zero anywhere P isn't"),
        NotationEntry("mode-seeking", "reverse KL's behavior — Q can ignore modes it doesn't fit and commit to one"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Asymmetry and the entropy identity",
            accentColor = 0xFF0EA5E9,
            code = """
                import math

                P = [0.5, 0.3, 0.15, 0.05]
                Q = [0.25, 0.25, 0.25, 0.25]

                def kl(a, b): return sum(x * math.log(x / y) for x, y in zip(a, b) if x > 0)
                def entropy(a): return -sum(x * math.log(x) for x in a if x > 0)
                def cross_entropy(a, b): return -sum(x * math.log(y) for x, y in zip(a, b))

                print(kl(P, Q), kl(Q, P))                  # 0.2442  0.3112 -- order matters
                print(entropy(P) + kl(P, Q), cross_entropy(P, Q))  # 1.3863  1.3863 -- exact match
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Forward vs reverse KL, fit by grid search",
            accentColor = 0xFFEC4899,
            code = """
                def normal_pdf(x, mu, sigma):
                    return (1 / (sigma * (2 * math.pi) ** 0.5)) * math.exp(-0.5 * ((x - mu) / sigma) ** 2)

                # P: a bimodal target, two bumps at -2.5 and +2.5 (discretized on a grid, omitted here)
                # Grid-search (mu, sigma) minimizing forward KL(P||Q) vs reverse KL(Q||P):

                # forward:  best Q ~ N(mu=0.0,  sigma=3.0)  KL = 0.4685   -- wide, covers both modes
                # reverse:  best Q ~ N(mu=-2.5, sigma=0.8)  KL = 0.6906   -- narrow, locks onto one mode
                #
                # The reverse fit's mu and sigma exactly match one of P's two true bumps -- reverse KL
                # found the single mode and committed, forward KL blurred across both.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Variational Autoencoders", "The KL term in a VAE's loss pulls the learned latent distribution toward a simple prior — see Variational Autoencoders."),
        ApplicationCard("trend", 0xFF3B82F6, "Knowledge Distillation", "A student model is trained to minimize KL divergence between its output distribution and a teacher's."),
        ApplicationCard("check", 0xFF8B5CF6, "RLHF and PPO", "KL divergence between the current and reference policy is used to keep fine-tuning from drifting too far."),
        ApplicationCard("help", 0xFFEC4899, "Not Symmetric — Choose a Direction", "Forward KL for mode-covering (e.g. supervised fitting); reverse KL for mode-seeking (e.g. some variational inference)."),
    ),
    takeaways = listOf(
        "KL(P‖Q) = Σ P(x)·ln(P(x)/Q(x)) — always non-negative, zero only when P=Q, and not symmetric.",
        "On the example distributions, KL(P‖Q)=0.2442 and KL(Q‖P)=0.3112 — swapping the arguments changes the number.",
        "The identity cross-entropy(P,Q) = entropy(P) + KL(P‖Q) holds exactly: 1.1421 + 0.2442 = 1.3863, matching CE directly.",
        "Fitting a single Gaussian to a bimodal target by minimizing forward KL(P‖Q) gives a wide fit that covers both modes.",
        "Minimizing reverse KL(Q‖P) instead gives a narrow fit that locks onto exactly one mode and ignores the other.",
        "The reverse fit's own reverse-KL cost (0.6906) exceeds the forward fit's own forward-KL cost (0.4685) — the two aren't comparable.",
        "Which direction to minimize is a modeling choice: mode-covering for a target distribution, mode-seeking for many variational settings.",
    ),
    crossLinks = listOf(
        CrossLink("cross_entropy_loss", "Cross-Entropy Loss"),
        CrossLink("vae", "Variational Autoencoders (VAE)"),
        CrossLink("rlhf", "RLHF (Reinforcement Learning from Human Feedback)"),
        CrossLink("diffusion_models", "Diffusion Models"),
    ),
)
