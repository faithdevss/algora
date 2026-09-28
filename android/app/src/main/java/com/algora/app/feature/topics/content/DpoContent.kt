package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dpoContent = TopicContent(
    topicId = "dpo",
    whatIsIt = listOf(
        "DPO removes the reward model from preference tuning. RLHF fits a Bradley-Terry reward to pairwise preferences and then runs reinforcement learning against it; DPO observes that the KL-regularised optimum of that second stage has a closed form, inverts it to express the reward in terms of the policy, and substitutes. What is left is a supervised loss on preference pairs with no sampling, no value function and no reward network.",
        "The lab runs both pipelines end to end on one prompt with six candidate responses, a reference policy whose mode is \"fluent and unsupported\" at 39%, and fifteen preference pairs labelled by three annotators each. The theorem checks out and it is worth watching it check out: find the β whose closed-form RLHF policy sits at the DPO run's own divergence, and the two policies agree on every response to floating-point precision. The reward model was never load-bearing — it was a parameterisation of the policy.",
        "The interesting result is the number neither method moves. Two of the fifteen pairs come back contradicting the ground truth, and those two flips create a preference **cycle**: cites the passage > hedged and correct > correct-without-citation > cites the passage. Bradley-Terry assigns one scalar per response, so no setting of its parameters can represent a cycle. It is forced to give all three the same reward and caps out at **80% accuracy on its own training data**. More preferences do not help. A bigger reward network does not help.",
        "Downstream of that, both pipelines converge to expected quality 1.739 while the best available response is worth 2.400 — **28% of the achievable quality left on the table by two mislabelled pairs**. No value of β recovers it, and switching between RLHF and DPO does not touch it. When an aligned model plateaus, the first thing to audit is the annotation, not the optimizer.",
    ),
    steps = listOf(
        StepCard(1, "Collect Pairs", "Two responses to one prompt, and a human choosing. That is the entire supervision signal.", 0xFFEF4444),
        StepCard(2, "Write The Implicit Reward", "β·log(π/π_ref) is a reward function, because the RL optimum says it is.", 0xFFF97316),
        StepCard(3, "Descend The Logistic Loss", "−log σ(implicit reward of winner − implicit reward of loser). Supervised, one stage.", 0xFFF59E0B),
        StepCard(4, "Hold β", "β is the KL leash. Small β means a policy far from the reference — for better or worse.", 0xFF10B981),
        StepCard(5, "Check For Cycles", "Intransitive preferences cannot be scored. Find them before you fit anything.", 0xFF3B82F6),
        StepCard(6, "Measure Against Truth", "Preference accuracy is not quality. The gap between them is your annotation error.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("RLHF stage two", "max E[r̂] − β·KL(π‖π_ref)", "Whose optimum is π ∝ π_ref·exp(r̂/β)."),
        FormulaEntry("Inverted", "r(y) = β·log(π(y)/π_ref(y)) + β·log Z", "The log Z cancels inside a pairwise comparison."),
        FormulaEntry("DPO loss", "−log σ(β·log(π_w/π_ref,w) − β·log(π_l/π_ref,l))", "No reward model, no rollouts, no value head."),
        FormulaEntry("Agreement", "max |π_DPO − π_RLHF| < 1e-16 at matched KL", "Measured, not quoted."),
        FormulaEntry("Cycle cost", "reward accuracy caps at 80%", "3 responses locked at identical reward by one cycle."),
        FormulaEntry("Alignment ceiling", "1.739 reached, 2.400 available", "28% lost to 2 of 15 labels."),
    ),
    notationKey = listOf(
        NotationEntry("π_ref", "the reference (SFT) policy the KL term is measured against"),
        NotationEntry("β", "the KL coefficient — the only dial, and it sets how far the policy may move"),
        NotationEntry("Bradley-Terry", "P(a beats b) = σ(r(a) − r(b)), one scalar reward per response"),
        NotationEntry("implicit reward", "β·log(π/π_ref) — a reward read off the policy rather than a network"),
        NotationEntry("intransitivity", "a > b > c > a, which no scalar scoring can reproduce"),
        NotationEntry("overoptimization", "gaining reward against a fitted r̂ while losing true quality"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The DPO loss, in full",
            accentColor = 0xFFEF4444,
            code = """
                import torch.nn.functional as F

                def dpo_loss(policy_chosen_logps, policy_rejected_logps,
                             ref_chosen_logps, ref_rejected_logps, beta=0.1):
                    # The implicit reward is the log-ratio against the frozen reference.
                    chosen_rewards   = beta * (policy_chosen_logps   - ref_chosen_logps)
                    rejected_rewards = beta * (policy_rejected_logps - ref_rejected_logps)
                    # log Z cancels here, which is the whole trick.
                    return -F.logsigmoid(chosen_rewards - rejected_rewards).mean()

                # The reference model is frozen and only ever run in inference mode --
                # its log-probs can be precomputed once for the whole dataset.
                with torch.no_grad():
                    ref_chosen = ref_model(chosen).log_probs
                    ref_rejected = ref_model(rejected).log_probs

                loss = dpo_loss(policy(chosen).log_probs, policy(rejected).log_probs,
                                ref_chosen, ref_rejected, beta=0.1)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Audit the preferences before you fit anything",
            accentColor = 0xFF3B82F6,
            code = """
                # A cycle is not noise you can average away -- it is a shape Bradley-Terry
                # cannot express, and it silently caps the reward model's accuracy.

                beats = {(w, l) for w, l in majority_labels}
                cycles = [(a, b, c)
                          for a, b, c in combinations(responses, 3)
                          if (a, b) in beats and (b, c) in beats and (c, a) in beats]

                print(cycles)                 # [(cites, hedged, correct_no_citation)]
                print(reward_accuracy())      # 0.800  <- and it will not go higher

                # The three responses in the cycle all receive the same fitted reward:
                print(reward_model)           # [1.119, 1.119, 1.119, -1.080, -1.959, -0.318]

                # Which costs real quality, whichever pipeline you then run:
                print(aligned_quality(), best_possible_quality())   # 1.739  2.400
                # 28% of the available quality, gone before optimization starts.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("flame", 0xFFEF4444, "Preference Tuning", "One supervised stage instead of a reward model plus PPO."),
        ApplicationCard("check", 0xFF10B981, "Cheaper Pipelines", "No rollouts, no value head, no reward network to serve."),
        ApplicationCard("help", 0xFFF59E0B, "Annotation Audits", "Cycles and flipped labels cap quality before any training runs."),
        ApplicationCard("chart", 0xFF3B82F6, "β Selection", "The KL leash is the only dial, and it is worth sweeping."),
    ),
    takeaways = listOf(
        "DPO and RLHF reach the same policy: at matched KL the two agree to floating-point precision on every response.",
        "The reward model was a parameterisation of the policy, which is why it can be substituted away rather than approximated.",
        "Two flipped labels in fifteen created a preference cycle, and a cycle caps Bradley-Terry at 80% accuracy permanently.",
        "Both pipelines converge to quality 1.739 against an available 2.400 — the ceiling is the annotation, not the algorithm.",
        "β is the only dial and it is a leash, not a quality knob: it decides how far the policy may move from the reference.",
    ),
    crossLinks = listOf(
        CrossLink("rlhf", "RLHF"),
        CrossLink("fine_tuning_full", "Fine-Tuning (Full)"),
        CrossLink("llms", "LLMs"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
    ),
)
