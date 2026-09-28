package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gpt3Gpt4Content = TopicContent(
    topicId = "gpt3_gpt4",
    whatIsIt = listOf(
        "GPT-3's contribution was not architectural — it is a decoder-only transformer of the same shape as GPT-2, scaled to 175B parameters and 300B training tokens. What that scale produced was qualitative rather than incremental: in-context learning, where the model performs a task from a handful of examples in the prompt with no gradient update at all. The interface to a language model stopped being fine-tuning and became prompting, which is the reason the paper is titled \"Language Models are Few-Shot Learners\".",
        "Scaling laws are why anyone spent the money. Test loss falls as a smooth power law in parameters, data and compute across many orders of magnitude, so the return on a larger run is predictable before it starts. Chinchilla then showed GPT-3 had allocated its budget wrongly: for a fixed compute budget, loss is minimised at roughly 20 training tokens per parameter, and GPT-3 used 1.7. It was not too large — it was under-trained for its size, and a 70B model on 1.4T tokens beat it using less compute. Every lab's budget arithmetic changed after that paper.",
        "GPT-4's technical report published no parameter count, no dataset size and no architecture. Its methodological contribution was predictable scaling: the final model's performance was forecast in advance from runs using 1,000–10,000× less compute, which is a real capability independent of any one model. Two things the loss curves do not capture then decided how these systems are actually used. Post-training — instruction tuning and RLHF — mattered more than raw scale for usefulness: InstructGPT's 1.3B model was preferred by human raters to the raw 175B GPT-3, a 100× parameter gap closed by alignment. And once next-token loss stopped being the differentiator, competition moved to multimodality, tool use, and context length.",
    ),
    steps = listOf(
        StepCard(1, "Fix the Architecture", "Decoder-only transformer — GPT-3 changed the size, not the design.", 0xFF3B82F6),
        StepCard(2, "Fit the Power Law", "Measure loss at small scales; extrapolate before committing a budget.", 0xFF6366F1),
        StepCard(3, "Allocate Compute", "≈20 tokens per parameter is compute-optimal; GPT-3 used 1.7.", 0xFF8B5CF6),
        StepCard(4, "Train Once", "6ND FLOPs, no restarts — the forecast is what makes this survivable.", 0xFF06B6D4),
        StepCard(5, "Post-Train", "Instruction tuning and RLHF; where usefulness is actually won.", 0xFF14B8A6),
        StepCard(6, "Evaluate Beyond Loss", "Reasoning, tool use, refusals — none of it visible in perplexity.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Kaplan scaling law", "L(N) ∝ N^(−0.076)", "Loss as a power of parameters — smooth, and it never plateaus."),
        FormulaEntry("Training compute", "C ≈ 6ND FLOPs", "GPT-3: 3.15 × 10²³. Chinchilla: 5.88 × 10²³ for a better model."),
        FormulaEntry("Chinchilla-optimal", "D ≈ 20N", "Tokens per parameter for a fixed compute budget."),
        FormulaEntry("GPT-3's ratio", "300B / 175B = 1.7", "The only under-trained model in the lab's table — by more than 10×."),
        FormulaEntry("Predictable scaling", "forecast from 1,000–10,000× less compute", "GPT-4's actual published contribution."),
        FormulaEntry("Post-training", "1.3B InstructGPT preferred to 175B GPT-3", "A 100× parameter gap closed by alignment."),
    ),
    notationKey = listOf(
        NotationEntry("N / D / C", "parameters, training tokens, compute — the three axes of the scaling laws"),
        NotationEntry("in-context learning", "performing a task from prompt examples, with no weight update"),
        NotationEntry("few-shot / zero-shot", "with or without examples in the prompt"),
        NotationEntry("compute-optimal", "the N/D split minimising loss for a fixed C — Chinchilla's result"),
        NotationEntry("emergent ability", "a capability that appears abruptly with scale; the metric choice is part of the debate"),
        NotationEntry("RLHF", "reinforcement learning from human feedback — the post-training step that made these usable"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Budget arithmetic, the way a lab does it",
            accentColor = 0xFF3B82F6,
            code = """
                def training_flops(params_b, tokens_b):
                    return 6 * params_b * 1e9 * tokens_b * 1e9         # the standard 6ND estimate

                models = {
                    "GPT-3":      (175, 300),
                    "Chinchilla": (70, 1400),
                    "LLaMA-3 70B": (70, 15000),
                }
                for name, (n, d) in models.items():
                    print(f"{name}: {d/n:5.1f} tokens/param, {training_flops(n, d):.2e} FLOPs")
                # GPT-3:        1.7 tokens/param, 3.15e+23 FLOPs
                # Chinchilla:  20.0 tokens/param, 5.88e+23 FLOPs   <- better model, similar budget
                # LLaMA-3 70B: 214.3 tokens/param, 6.30e+24 FLOPs  <- deliberately past optimal

                # Chinchilla-optimal minimises TRAINING loss for a fixed budget. It does not
                # minimise the cost of SERVING the model, which depends on parameters alone --
                # which is why LLaMA-3 sits at 214 tokens/param and is not making a mistake.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "In-context learning, and why it changed the interface",
            accentColor = 0xFFEC4899,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                # Few-shot: the "training set" is three lines of the prompt. No gradients, no
                # fine-tuning job, no deployment step -- this is the shift GPT-3 demonstrated.
                prompt = (
                    "cheese -> fromage\n"
                    "bread -> pain\n"
                    "apple -> pomme\n"
                    "window ->"
                )
                response = client.messages.create(
                    model="claude-opus-5",
                    max_tokens=16,
                    messages=[{"role": "user", "content": prompt}],
                )
                print(response.content[0].text)

                # What the scaling laws do NOT predict: instruction following, refusals, tool use,
                # or format adherence. Those come from post-training, and they are the reason a
                # 1.3B InstructGPT was preferred to the raw 175B base model.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Capacity Planning", "Scaling laws turn a training run from a gamble into a forecast."),
        ApplicationCard("browser", 0xFF6366F1, "Prompt-First Products", "In-context learning is why most LLM products ship prompts, not fine-tunes."),
        ApplicationCard("finance", 0xFF8B5CF6, "Budget Allocation", "Chinchilla's ratio is the first check on any pretraining plan."),
        ApplicationCard("help", 0xFFEC4899, "What Scale Doesn't Fix", "Alignment, calibration and tool use are post-training problems."),
    ),
    takeaways = listOf(
        "GPT-3 changed the size, not the architecture — and in-context learning fell out of it.",
        "Loss follows a smooth power law in parameters, data and compute, so a run's result is forecastable.",
        "Chinchilla: ≈20 tokens per parameter is compute-optimal, and GPT-3's 1.7 made it badly under-trained.",
        "GPT-4 published no architecture; its contribution was predicting the final result from 1,000–10,000× smaller runs.",
        "Post-training beat a 100× parameter advantage — scale buys capability, alignment buys usefulness.",
    ),
    crossLinks = listOf(
        CrossLink("llms", "LLMs"),
        CrossLink("llama_vicuna", "LLaMA & Vicuna"),
        CrossLink("rlhf", "RLHF"),
        CrossLink("transformers", "Transformers"),
    ),
)
