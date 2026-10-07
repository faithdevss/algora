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

internal val dpoContent = TopicContent(
    topicId = "dpo",
    figure = Figure(
        caption = "The page's lab after 1,000 DPO steps at its three values of β: four responses " +
            "to one prompt, a uniform reference (25% each), and seven preference pairs of which one " +
            "is mislabelled. Each row is where the policy ends up. β = 0.1 is a loose leash — the " +
            "preferences dominate, 91% lands on the response that cites a source, and the policy is " +
            "1.08 nats of KL from where it started. β = 0.5 stops at 57% / 37% between the two " +
            "correct answers, 0.48 nats out. β = 2 holds the policy so close to the reference that " +
            "it barely moves at all: 35% on the best answer and still 16% on the confident, wrong " +
            "one, 0.055 nats away. The implicit reward is β·log(π/π_ref), so β decides how much a " +
            "unit of preference is allowed to move the policy — and the mislabelled D ≻ A pair " +
            "pulls against the other six at every one of those steps.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("91%", "9.2%", "0.0%", "0.0%", "1.08"),
                listOf("57%", "37%", "3.8%", "2.5%", "0.48"),
                listOf("35%", "31%", "18%", "16%", "0.055"),
            ),
            rowHeaders = listOf("β = 0.1", "β = 0.5", "β = 2"),
            colHeaders = listOf("A cites", "B terse", "C hedged", "D wrong", "KL nats"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(2, 3, FigureTone.Warn),
                FigureCell(0, 4, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "DPO removes the reward model from preference tuning. RLHF fits a Bradley-Terry reward to pairwise preferences and then runs reinforcement learning against it; DPO observes that the KL-regularised optimum of that second stage has a closed form, inverts it to express the reward in terms of the policy, and substitutes. What is left is a supervised loss on preference pairs with no sampling, no value function and no reward network.",
        "The lab runs DPO itself on one prompt with four candidate responses — A cites a source, B is correct but terse, C hedges, D is confident and wrong — starting from a uniform reference policy (25% each) and seven preference pairs. Six of the pairs agree on the ranking A > B > C > D; the seventh is mislabelled, D ≻ A. Every update raises the implicit reward β·log(π/π_ref) of a chosen response against its rejected one, and the ranking emerges within ten steps.",
        "β is the leash, and the lab shows how hard it pulls. At β = 0.1 the policy is free to move: after 1,000 steps it puts 91% on A and has travelled 1.08 nats of KL from the reference. At β = 0.5 it settles at 57% on A and 37% on B, 0.48 nats away. At β = 2 it barely leaves the start — 35% on A, still 16% on the confident-wrong D, 0.055 nats. Larger β keeps the policy near the reference; smaller β lets the preferences dominate.",
        "The mislabelled pair never goes away. D ≻ A pulls against six correct pairs on every step, and nothing in the loss can tell which label is wrong — DPO fits the annotation it is given. A single scalar reward per response cannot represent a contradiction or a cycle in the labels, so bad pairs cost quality whichever pipeline runs on them. When an aligned model plateaus, audit the annotation before the optimizer.",
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
        FormulaEntry("β = 0.1, 1,000 steps", "A 91% · B 9.2% · KL 1.08 nats", "Weak leash: the preferences dominate."),
        FormulaEntry("β = 0.5, 1,000 steps", "A 57% · B 37% · KL 0.48 nats", "A middle setting."),
        FormulaEntry("β = 2, 1,000 steps", "A 35% · D 16% · KL 0.055 nats", "Strong leash: the policy stays near uniform."),
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
                # A contradiction or cycle in the labels is not noise you can average away --
                # one scalar reward per response cannot express it, so it costs quality whichever
                # pipeline (RLHF or DPO) is run on top.

                beats = {(w, l) for w, l in majority_labels}
                contradictions = [(a, b) for a, b in beats if (b, a) in beats]
                cycles = [(a, b, c)
                          for a, b, c in combinations(responses, 3)
                          if ((a, b) in beats and (b, c) in beats and (c, a) in beats)
                          or ((a, c) in beats and (c, b) in beats and (b, a) in beats)]   # both orientations

                print(contradictions)   # the lab's set has one: A > D and D > A
                print(cycles)
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
        "DPO optimises the same KL-regularised objective as RLHF's second stage, in closed form — one supervised loss on preference pairs.",
        "The reward model was a parameterisation of the policy, which is why it can be substituted away rather than approximated.",
        "In the lab, one mislabelled pair (D ≻ A) out of seven keeps pulling against the other six at every step.",
        "β sets the trade: 0.1 puts 91% on the best response 1.08 nats from the reference; 2 barely moves (35%, 0.055 nats).",
        "β is the only dial and it is a leash, not a quality knob: it decides how far the policy may move from the reference.",
    ),
    crossLinks = listOf(
        CrossLink("rlhf", "RLHF"),
        CrossLink("fine_tuning_full", "Fine-Tuning (Full)"),
        CrossLink("llms", "LLMs"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
    ),
)
