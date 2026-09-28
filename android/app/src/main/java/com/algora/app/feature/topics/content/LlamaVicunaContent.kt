package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val llamaVicunaContent = TopicContent(
    topicId = "llama_vicuna",
    whatIsIt = listOf(
        "LLaMA's thesis is the inverse of Chinchilla's. Chinchilla asks how to spend a *training* budget optimally; LLaMA asks what to do when the model will be served millions of times, so inference cost — which depends on parameters alone and not at all on training tokens — dominates the lifetime bill. The answer is to train a smaller model far past compute-optimal: LLaMA-1 65B used 21.5 tokens per parameter, LLaMA-3 70B used 214, and Mistral 7B about 1,143 against Chinchilla's 20. None of those is a mistake; they are optimising a different objective.",
        "The arithmetic behind that is simple enough to check. Inference costs roughly 2N FLOPs per token, where N is the parameter count — training tokens do not appear in the formula. A 7B model costs 1.4 × 10¹⁰ FLOPs per token to run and a 70B costs 1.4 × 10¹¹, so ten times the training spend on the smaller model pays for itself once you serve enough tokens. The architecture changes are correspondingly small and have all been adopted elsewhere: pre-normalisation with RMSNorm instead of post-LayerNorm, SwiGLU in place of the GELU feed-forward, and rotary position embeddings instead of learned ones. Together they are what a modern transformer block looks like.",
        "Vicuna is the other half of the story, and it is about cost rather than capability: fine-tuning LLaMA-13B on roughly 70K shared ChatGPT conversations, for a few hundred dollars of compute, produced a chat model that GPT-4-as-judge rated close to the commercial systems of the time. Two caveats got lost in the excitement — an LLM judge is generous to models that imitate its own style, and instruction tuning on outputs teaches *format* far better than it teaches knowledge, so the benchmark gap was much wider than the vibes gap. What open weights genuinely buy is control: run the model where the data is, inspect and fine-tune it, keep using a version after a vendor deprecates it. What they cost is that you own the serving, the safety layer and the evaluation. \"Open\" is also loose here — LLaMA's licence carries use restrictions and the training data is not released, so this is open *weights*, not open source.",
    ),
    steps = listOf(
        StepCard(1, "Ask Who Pays", "Training is paid once; inference is paid per token forever.", 0xFF3B82F6),
        StepCard(2, "Shrink and Over-Train", "Smaller N, far more D — 10–50× past Chinchilla-optimal.", 0xFF6366F1),
        StepCard(3, "Modernise the Block", "RMSNorm pre-norm, SwiGLU, RoPE — small changes, universally copied.", 0xFF8B5CF6),
        StepCard(4, "Release the Weights", "Which is what turns one model into an ecosystem.", 0xFF06B6D4),
        StepCard(5, "Instruction-Tune Cheaply", "A few hundred dollars of LoRA on ~70K conversations gets a usable chat model.", 0xFF14B8A6),
        StepCard(6, "Distrust the Judge", "LLM-as-judge favours imitators; check a real benchmark before believing a claim.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Inference cost", "≈2N FLOPs per token", "7B → 1.4 × 10¹⁰; 70B → 1.4 × 10¹¹. Training tokens do not appear."),
        FormulaEntry("Tokens per parameter", "LLaMA-1 21.5 · LLaMA-3 214 · Mistral ≈1,143", "Against Chinchilla's 20 — deliberately, not accidentally."),
        FormulaEntry("Why over-train", "training is amortised; inference is not", "The break-even is a function of how many tokens you will serve."),
        FormulaEntry("RMSNorm", "x / RMS(x) · g, applied pre-block", "Cheaper than LayerNorm and more stable than post-norm."),
        FormulaEntry("SwiGLU width", "8/3 · d for three matrices", "d = 4096 → 11008 in LLaMA-7B."),
        FormulaEntry("Vicuna", "13B + ~70K conversations, a few hundred dollars", "The base model is the expensive part; alignment is not."),
    ),
    notationKey = listOf(
        NotationEntry("compute-optimal", "Chinchilla's training-budget objective — not the same as deployment-optimal"),
        NotationEntry("over-training", "training well past 20 tokens/parameter to cut inference cost"),
        NotationEntry("RMSNorm", "root-mean-square normalisation; no mean subtraction, no bias"),
        NotationEntry("pre-norm", "normalising before each sub-block rather than after — much more stable at depth"),
        NotationEntry("instruction tuning", "supervised fine-tuning on prompt/response pairs"),
        NotationEntry("LLM-as-judge", "using a strong model to score outputs; cheap, fast, and biased toward its own style"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The inference-first argument, in numbers",
            accentColor = 0xFF3B82F6,
            code = """
                def training_flops(n_b, d_b):   return 6 * n_b * 1e9 * d_b * 1e9
                def inference_flops(n_b):       return 2 * n_b * 1e9        # per token

                small = (7, 8000)     # Mistral-style: 7B over-trained on 8T tokens
                big   = (70, 1400)    # Chinchilla-optimal 70B

                print(f"{training_flops(*small):.2e}  {training_flops(*big):.2e}")
                print(f"{inference_flops(small[0]):.2e}  {inference_flops(big[0]):.2e}")
                # Training: comparable. Inference: 10x cheaper for the small model, forever.

                # Break-even in served tokens is roughly
                #   (train_big - train_small) / (infer_big - infer_small)
                # which for any consumer-facing product is reached almost immediately.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The cheap fine-tune, and what it actually teaches",
            accentColor = 0xFFEC4899,
            code = """
                from peft import LoraConfig, get_peft_model
                from transformers import AutoModelForCausalLM

                base = AutoModelForCausalLM.from_pretrained("meta-llama/Llama-3-8B")
                model = get_peft_model(base, LoraConfig(r=16, lora_alpha=32,
                                                        target_modules=["q_proj", "v_proj"]))
                model.print_trainable_parameters()   # <1% of the weights are trained

                # This is the Vicuna recipe modernised: a few hundred dollars, a few hours, and a
                # model that answers in the right register. What it does NOT do is add knowledge --
                # instruction tuning on outputs teaches format and style far more than facts.
                #
                # And the evaluation trap: GPT-4-as-judge rated Vicuna near the commercial models
                # of its day, while task benchmarks showed a much larger gap. An LLM judge rewards
                # outputs that look like its own. Use it for triage; confirm on a real benchmark.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("lock", 0xFF3B82F6, "Data-Resident Deployment", "Run the model where the data legally has to stay — the main reason enterprises pick open weights."),
        ApplicationCard("chip", 0xFF6366F1, "Edge & On-Device", "Over-trained small models are what make 7B-class deployment on consumer hardware work."),
        ApplicationCard("flask", 0xFF8B5CF6, "Research", "Open weights are what make interpretability and safety research on frontier-shaped models possible."),
        ApplicationCard("help", 0xFFEC4899, "What You Take On", "Serving, safety layer, evaluations and updates all become yours."),
    ),
    takeaways = listOf(
        "Chinchilla optimises training cost; LLaMA optimises deployment — hence 214 tokens/parameter, not 20.",
        "Inference is ≈2N FLOPs per token and ignores training data entirely, so over-training a small model pays back fast.",
        "RMSNorm pre-norm, SwiGLU and RoPE are the three changes, and every open model since copied all three.",
        "Vicuna showed alignment is cheap relative to pretraining — and that LLM-as-judge flatters imitators.",
        "Open weights buy control and cost you the serving, safety and evaluation stack; it is open weights, not open source.",
    ),
    crossLinks = listOf(
        CrossLink("gpt3_gpt4", "GPT-3 & GPT-4"),
        CrossLink("mistral_mixtral", "Mistral & Mixtral (MoE)"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("feed_forward", "Feed-Forward Networks"),
    ),
)
