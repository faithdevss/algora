package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val cycleGanContent = TopicContent(
    topicId = "cyclegan",
    whatIsIt = listOf(
        "CycleGAN translates between two image domains — horses and zebras, photographs and Monets — without a single matched pair. That is the achievement, and the reason it is hard is a counting problem. An adversarial loss can only see distributions: it rewards the generator for producing outputs that look like domain B, and says nothing whatsoever about which input produced which output. Model both domains as six items and the arithmetic is finishable by hand. Every one of the 720 bijections from A to B produces exactly the right output distribution, so all 720 drive the adversarial loss to zero, and exactly one of them is the translation you wanted.",
        "The cycle-consistency loss is universally presented as the fix for this, and it is not. Requiring F(G(a)) = a forces F to be the inverse of G — and every bijection has an inverse. Enumerate it: of the 720 mappings that satisfy the adversarial loss, the number that also satisfy cycle consistency is 720. Not most of them, all of them. The cycle term removes exactly zero candidates. What it rules out are the non-bijective mappings — the many-to-one collapses where every horse becomes the same zebra — and those were already ruled out at the level of distributions. Cycle consistency prevents mode collapse. It does not select the semantically correct map, and after both losses are at zero the odds are still one in 720.",
        "So why does CycleGAN work? Because of everything that is not in the loss. The generator is a convolutional network with a receptive field far smaller than the image, so it physically cannot implement an arbitrary permutation of content — it can only apply roughly local, roughly consistent transformations. Put a crude version of that bias into the count and the effect is immediate: restricting the mapping to move any item at most one position cuts 720 candidates to 13, and at most two positions leaves 73. That is what is actually doing the selecting. It is also exactly why CycleGAN fails on the cases it is famous for failing on — geometric changes, like turning a cat into a dog — since those need precisely the large, structured rearrangement the architecture cannot express.",
    ),
    steps = listOf(
        StepCard(1, "Two Generators, Two Discriminators", "G: A → B and F: B → A, each with its own critic.", 0xFF0EA5E9),
        StepCard(2, "Adversarial Loss Matches Distributions", "All 720 bijections score zero — it never sees pairs.", 0xFFF97316),
        StepCard(3, "Add Cycle Consistency", "‖F(G(a)) − a‖₁ — and count what it removed: none of them.", 0xFFEC4899),
        StepCard(4, "Find the Real Constraint", "A limited receptive field cannot express an arbitrary permutation.", 0xFF10B981),
        StepCard(5, "Count Again With Locality", "720 → 73 at a two-step budget, → 13 at one step.", 0xFF6366F1),
        StepCard(6, "Predict the Failures", "Geometric changes need the rearrangement the bias forbids.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Full objective", "L_GAN(G) + L_GAN(F) + λ·L_cyc", "λ = 10 in the paper."),
        FormulaEntry("Cycle loss", "‖F(G(a)) − a‖₁ + ‖G(F(b)) − b‖₁", "Both directions."),
        FormulaEntry("Adversarially optimal", "n! = 720 at n = 6", "Every bijection matches the distribution."),
        FormulaEntry("Also cycle-consistent", "720 of 720", "F = G⁻¹ always exists. Zero removed."),
        FormulaEntry("Semantically correct", "1", "Odds after both losses: 1 in 720."),
        FormulaEntry("With locality", "13 at budget 1 · 73 at budget 2", "The architecture, not the loss."),
    ),
    notationKey = listOf(
        NotationEntry("G, F", "the two generators, A → B and B → A"),
        NotationEntry("cycle consistency", "F(G(a)) = a; forces F = G⁻¹ and nothing more"),
        NotationEntry("bijection", "a one-to-one onto mapping; all n! of them satisfy both losses"),
        NotationEntry("inductive bias", "what the architecture cannot express — here, the real constraint"),
        NotationEntry("receptive field", "how much of the input one output pixel can see"),
        NotationEntry("identity loss", "an optional extra term anchoring colour; not what selects the map"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Enumerate what each loss actually rules out",
            accentColor = 0xFFEC4899,
            code = """
                from itertools import permutations, product

                n = 6
                distribution_ok = cycle_ok = 0

                for mapping in product(range(n), repeat=n):     # every A -> B map, 6^6 of them
                    hits = [0] * n
                    for b in mapping:
                        hits[b] += 1
                    if any(h != 1 for h in hits):
                        continue                                # collapses the distribution
                    distribution_ok += 1

                    inverse = [0] * n                           # F is forced to be G's inverse
                    for a, b in enumerate(mapping):
                        inverse[b] = a
                    if all(inverse[mapping[a]] == a for a in range(n)):
                        cycle_ok += 1

                print(distribution_ok, cycle_ok)     # 720 720
                #
                # The second number is the point. Cycle consistency eliminated nothing: every
                # bijection has an inverse, so every mapping the adversarial loss accepted is also
                # perfectly cycle-consistent. Both losses at exactly zero, 720 candidates, one right.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The constraint that does the work is in the architecture",
            accentColor = 0xFF10B981,
            code = """
                def with_locality(n, budget):
                    # Mappings that move no item further than `budget` positions.
                    count = 0
                    used = [False] * n

                    def recurse(i):
                        nonlocal count
                        if i == n:
                            count += 1
                            return
                        for b in range(n):
                            if used[b] or abs(b - i) > budget:
                                continue
                            used[b] = True
                            recurse(i + 1)
                            used[b] = False

                    recurse(0)
                    return count

                for budget in range(5):
                    print(budget, with_locality(6, budget))

                # 0    1     a strictly local map is unique
                # 1   13
                # 2   73
                # 3  230
                # 4  504     ->  720 unconstrained
                #
                # A convolutional generator with a small receptive field is a soft version of this.
                # It is the reason CycleGAN converges on the sensible answer, and the reason it
                # cannot do cat -> dog: that needs the rearrangement this budget forbids.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFF0EA5E9, "Unpaired Translation", "Style, season and species transfer with no matched pairs."),
        ApplicationCard("flask", 0xFFF97316, "Domain Adaptation", "Synthetic-to-real for training data where labels transfer and pixels do not."),
        ApplicationCard("chart", 0xFF6366F1, "Medical Imaging", "Cross-modality synthesis where paired scans are impossible to collect."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Geometric change — the failure the counting argument predicts."),
    ),
    takeaways = listOf(
        "The adversarial loss constrains distributions only: all 720 bijections at n = 6 score exactly zero.",
        "Cycle consistency removes none of them — F = G⁻¹ exists for every bijection. 720 of 720 survive.",
        "It does rule out many-to-one collapse, which is a real job, just not the job it is usually credited with.",
        "After both losses are at zero the odds of the correct mapping are still 1 in 720.",
        "What selects the mapping is the architecture: a limited receptive field cannot express an arbitrary permutation.",
        "Priced as a locality budget, that cuts 720 to 73 at two steps and to 13 at one.",
        "It also predicts the failures — geometric changes need exactly the rearrangement the bias forbids.",
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("dcgan", "DCGAN (Deep Convolutional GAN)"),
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("unet", "U-Net (Medical Segmentation)"),
        CrossLink("neural_style_transfer", "Neural Style Transfer"),
    ),
)
