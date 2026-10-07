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

internal val loraQloraContent = TopicContent(
    topicId = "lora_qlora",
    figure = Figure(
        caption = "Rank against quality, with the floor drawn underneath it. The dashed curve is " +
            "not measured but predicted: Eckart-Young says a rank-r adapter cannot represent the " +
            "singular directions it drops, so the tail of ΔW's spectrum puts a hard lower bound " +
            "on the loss at every rank — 0.298, 0.111, 0.043, 0.020 at r = 1, 2, 4, 8. The solid " +
            "curve is what six trained adapters actually reached: 0.309, 0.121, 0.047, 0.023, " +
            "sitting just above the bound at every point rather than crossing it. That is the " +
            "whole low-rank claim, and it is a theorem with a learning rate attached rather than " +
            "a heuristic. Where it stops paying is visible too: three of 24 directions already " +
            "carry 90.2% of the update's energy, so rank 4 closes 91% of the gap to full " +
            "fine-tuning on 192 of 576 parameters, and doubling to rank 8 buys 0.024 more against " +
            "a floor that has almost nothing left in it. The step size is the trap — it scales as " +
            "(α/r)², 256× larger at r = 1 than at r = 16, and at one fixed rate every rank below " +
            "16 diverged to NaN.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "trained adapters",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.333f, 0.392f),
                        FigurePoint(0.667f, 0.152f), FigurePoint(1.000f, 0.074f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "Eckart-Young floor",
                    listOf(
                        FigurePoint(0.000f, 0.964f), FigurePoint(0.333f, 0.359f),
                        FigurePoint(0.667f, 0.139f), FigurePoint(1.000f, 0.065f),
                    ),
                    dashed = true,
                ),
            ),
            markers = listOf(
                FigurePoint(0.667f, 0.152f, "r = 4 · 91% of the gap", FigureTone.Accent),
            ),
            xLabel = "rank r = 1, 2, 4, 8",
            yLabel = "loss, 0 → 0.309",
        ),
    ),
    whatIsIt = listOf(
        "LoRA freezes a pretrained weight matrix and trains a low-rank correction beside it: W₀ + (α/r)·BA, with B initialised at zero so the adapter is a no-op at step 0. The claim underneath is not that models are low-rank — it is that fine-tuning *updates* are, because a narrow adaptation moves the weights along few directions.",
        "The lab tests that rather than assuming it. A 24×24 map is fully fine-tuned for real, ΔW = W − W₀ is extracted, and its singular values fall off a cliff after three: 2.34, 2.12, 1.19, 0.46, 0.41. Three directions carry **90.2%** of the update's energy, giving an effective rank of 3.05 against 11.51 for the pretrained weights themselves. The update genuinely is low-rank where the model is not.",
        "Then adapters are trained at six ranks and scored against a floor the spectrum puts under them. Eckart-Young says no rank-r adapter can do better than the tail singular values it cannot represent, and the trained sweep tracks that floor from above at every rank — 0.309 against a predicted 0.298 at r=1, 0.047 against 0.043 at r=4. Rank 4 closes **91% of the gap to full fine-tuning with 192 of 576 parameters**. The low-rank story is not a heuristic; it is a theorem with a learning rate attached.",
        "α is not cosmetic, and finding that out cost this lab a run. The update passes through the α/r scale going in and coming back, so the effective step on BA grows as (α/r)² — at r=1 that is 256× the step at r=16. Left at a single learning rate, **every rank below 16 diverged to NaN.** QLoRA then adds the other half: freeze the base in NF4 and keep the adapters in full precision. Quantizing W₀ alone costs 0.0066 of loss; rank-4 adapters trained on top of the quantized base land within 0.0013 of adapters on the fp base, absorbing **80% of the quantization damage**.",
    ),
    steps = listOf(
        StepCard(1, "Freeze W₀", "The pretrained matrix never receives a gradient and can stay in low precision.", 0xFFEF4444),
        StepCard(2, "Add B·A At Rank r", "Two thin factors, d×r and r×d, holding 2dr parameters instead of d².", 0xFFF97316),
        StepCard(3, "Start B At Zero", "The adapter contributes nothing at step 0, so training begins exactly at the checkpoint.", 0xFFF59E0B),
        StepCard(4, "Scale By α/r", "And scale the learning rate by (r/α)², or low ranks diverge.", 0xFF10B981),
        StepCard(5, "Merge For Inference", "W₀ + (α/r)·BA is one matrix again — zero added latency.", 0xFF3B82F6),
        StepCard(6, "Quantize The Base", "QLoRA: NF4 for the frozen weights, full precision for the adapters.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("LoRA", "W = W₀ + (α/r)·B·A, B ∈ ℝ^(d×r), A ∈ ℝ^(r×d)", "2dr trainable parameters instead of d²."),
        FormulaEntry("Update spectrum", "2.34, 2.12, 1.19, 0.46, 0.41, …", "Effective rank 3.05 against 11.51 for W₀."),
        FormulaEntry("Energy at rank 3", "90.2%", "Three of 24 directions carry nine tenths of the update."),
        FormulaEntry("Eckart-Young floor", "loss(r) ≥ Σ_{i>r} σᵢ² / d", "The trained sweep sits just above it at every rank."),
        FormulaEntry("Effective step", "∝ (α/r)²", "256× larger at r=1 than at r=16, which is why fixed rates diverge."),
        FormulaEntry("QLoRA recovery", "80% of the quantization damage absorbed", "0.0066 cost, 0.0013 residual after rank-4 adapters."),
    ),
    notationKey = listOf(
        NotationEntry("r", "the adapter rank — the one capacity dial"),
        NotationEntry("α", "the LoRA scale; α/r multiplies the adapter's output"),
        NotationEntry("ΔW", "the difference between the fine-tuned and pretrained weights"),
        NotationEntry("effective rank", "(Σσ²)² / Σσ⁴ — how many directions the matrix really uses"),
        NotationEntry("NF4", "4-bit normal-float: 16 levels placed at the quantiles of a normal distribution"),
        NotationEntry("merge", "folding B·A back into W₀ so inference sees a single matrix"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LoRA and QLoRA end to end",
            accentColor = 0xFFEF4444,
            code = """
                from transformers import AutoModelForCausalLM, BitsAndBytesConfig
                from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training

                # QLoRA: the frozen base lives in NF4 with a second-level scale per block.
                quant = BitsAndBytesConfig(
                    load_in_4bit=True,
                    bnb_4bit_quant_type="nf4",
                    bnb_4bit_use_double_quant=True,
                    bnb_4bit_compute_dtype=torch.bfloat16,
                )
                base = AutoModelForCausalLM.from_pretrained("meta-llama/Llama-2-7b-hf",
                                                            quantization_config=quant)
                base = prepare_model_for_kbit_training(base)

                # The adapters stay in bf16. They are what absorbs the quantization error.
                model = get_peft_model(base, LoraConfig(
                    r=16, lora_alpha=32, lora_dropout=0.05,
                    target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
                ))

                # Inference: fold the adapter back in and the extra latency is zero.
                merged = model.merge_and_unload()
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Look at the update before choosing a rank",
            accentColor = 0xFF3B82F6,
            code = """
                delta = fine_tuned_weight - pretrained_weight
                s = torch.linalg.svdvals(delta)

                energy = (s ** 2).cumsum(0) / (s ** 2).sum()
                print(energy[:4])              # 0.434, 0.790, 0.902, 0.919
                eff_rank = (s ** 2).sum() ** 2 / (s ** 4).sum()
                print(eff_rank)                # 3.05   (the base weights: 11.51)

                # The floor any rank-r adapter must respect, with no training involved:
                predicted = [(s[r:] ** 2).sum() / delta.shape[0] for r in (1, 2, 4, 8)]
                # 0.298, 0.111, 0.043, 0.020 at r = 1, 2, 4, 8   and the trained sweep landed at
                # 0.309, 0.121, 0.047, 0.023, ...   -- just above, at every rank.

                # And scale the rate with the rank, or low ranks blow up:
                lr = base_lr * (r / alpha) ** 2
                # At a single fixed rate, r = 1, 2, 4 and 8 all diverged to NaN.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("flame", 0xFFEF4444, "Single-GPU Fine-Tuning", "QLoRA is why a 7B model trains on one consumer card."),
        ApplicationCard("stack", 0xFF10B981, "Adapter Libraries", "Megabyte-scale artefacts, hot-swappable against one base."),
        ApplicationCard("chart", 0xFF3B82F6, "Rank Selection", "Look at the update's spectrum; the floor it implies is real."),
        ApplicationCard("help", 0xFFF59E0B, "Divergence At Low Rank", "α/r squares into the step size. Scale the rate with the rank."),
    ),
    takeaways = listOf(
        "The update is low-rank where the model is not: effective rank 3.05 against 11.51, with 90.2% of the energy in 3 directions.",
        "Rank 4 closed 91% of the gap to full fine-tuning using 192 of 576 parameters.",
        "Trained losses sit just above the Eckart-Young floor at every rank — the low-rank claim is a theorem, not a heuristic.",
        "The effective step scales as (α/r)²: at a fixed learning rate every rank below 16 diverged to NaN.",
        "QLoRA's adapters absorbed 80% of the damage NF4 quantization did to the frozen base.",
    ),
    crossLinks = listOf(
        CrossLink("peft", "PEFT"),
        CrossLink("quantization", "Quantization"),
        CrossLink("svd", "Singular Value Decomposition"),
        CrossLink("fine_tuning_full", "Fine-Tuning (Full)"),
    ),
)
