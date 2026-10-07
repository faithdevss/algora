package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val peftContent = TopicContent(
    topicId = "peft",
    whatIsIt = listOf(
        "PEFT is the family of methods that adapt a pretrained model by training a small number of new or selected parameters and freezing everything else. BitFit trains only biases; LoRA trains low-rank factors beside the attention projections; adapters insert small bottleneck blocks; prefix tuning learns key/value vectors prepended to every layer; IA³ learns one multiplier per channel. The lab counts all six exactly over BERT-base's configuration — 12 layers, width 768, 108.9M parameters — rather than quoting the papers.",
        "The headline every PEFT paper leads with is the trainable share, and those numbers are real: IA³ trains **0.051%** of the model, LoRA at rank 8 on the query and value projections trains **0.271%**, adapters at bottleneck 64 train 2.19%. Optimizer state follows that headline exactly, because it is charged per trainable parameter: 1.6 GB for full fine-tuning against 0.2 GB for LoRA.",
        "The number that decides whether the job fits on the card does not follow it. Activations — everything the backward pass has to keep from the forward pass — are **1.5 GB** at batch 32 and sequence 128, and freezing a weight does not remove them, because the gradient still has to flow *through* the frozen layers to reach whatever sits beneath them. Add that term and LoRA's total goes from 3.2 GB to 1.8 GB: a **1.8× saving, not a 369× one**. Priced as a ratio of ratios, the trainable-parameter headline overstates the memory saving by 204× for LoRA and 1,088× for IA³ — and the more extreme the method, the worse the overstatement, because all of them are converging on the same activation floor.",
        "What PEFT buys unambiguously is the artefact and the serving story. A fully fine-tuned BERT is 208 MB per task; a rank-8 LoRA adapter is 0.6 MB and merges back into the base weights at inference, so it costs no extra latency. Fifty tasks is fifty adapters and one copy of the model. That, not the memory, is why PEFT won.",
    ),
    steps = listOf(
        StepCard(1, "Freeze The Base", "Every pretrained weight becomes read-only, and needs no gradient, master copy or moments.", 0xFFEF4444),
        StepCard(2, "Insert Or Select", "Add adapters and low-rank factors, or simply select the biases already there.", 0xFFF97316),
        StepCard(3, "Count Honestly", "Trainable share drives optimizer state and the shipped artefact — and nothing else.", 0xFFF59E0B),
        StepCard(4, "Add The Activations", "The backward pass through frozen layers still stores its inputs. This is the floor.", 0xFF10B981),
        StepCard(5, "Check Inference Cost", "LoRA merges to zero overhead; adapters and prefixes add layers and tokens.", 0xFF3B82F6),
        StepCard(6, "Ship Per Task", "One base, many small adapters — the operational win that survives the accounting.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Trainable share", "0.271% for LoRA r=8 on Wq, Wv", "294,912 of 108,891,648 parameters."),
        FormulaEntry("Optimizer state", "16·trainable + 2·frozen bytes", "1.6 GB → 0.2 GB, exactly as the headline promises."),
        FormulaEntry("Activation memory", "layers·(34·b·s·d + 5·b·s²·h)", "1.5 GB at batch 32, sequence 128 — unmoved by freezing."),
        FormulaEntry("Total training memory", "3.2 GB → 1.8 GB", "A 1.8× saving from a 369× parameter reduction."),
        FormulaEntry("Headline overstatement", "204× for LoRA, 1088× for IA³", "Parameter ratio ÷ memory ratio."),
        FormulaEntry("Shipped artefact", "208 MB → 0.6 MB per task", "The saving that is as large as advertised."),
    ),
    notationKey = listOf(
        NotationEntry("trainable share", "the fraction of parameters receiving gradients"),
        NotationEntry("BitFit", "train only the bias terms"),
        NotationEntry("adapter", "a small down-project / non-linearity / up-project block inserted per layer"),
        NotationEntry("prefix tuning", "learned key/value vectors prepended to every layer's attention"),
        NotationEntry("IA³", "one learned multiplier per key, value and FFN channel"),
        NotationEntry("activation memory", "forward-pass tensors the backward pass needs — the floor under every method"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "PEFT with the huggingface library, and what to print afterwards",
            accentColor = 0xFFEF4444,
            code = """
                from peft import LoraConfig, get_peft_model, TaskType

                config = LoraConfig(
                    task_type=TaskType.SEQ_CLS,
                    r=8, lora_alpha=16, lora_dropout=0.05,
                    target_modules=["query", "value"],
                )
                model = get_peft_model(base_model, config)
                model.print_trainable_parameters()
                # trainable params: a little over 294,912 (the LoRA factors plus the classifier head, which PEFT
                # also trains via modules_to_save) || all params: ~109.8M (BertForSequenceClassification
                # includes the pooler) || trainable%: ~0.27

                # That line is true and it is not the number that decides what fits.
                # Print the one that does:
                torch.cuda.reset_peak_memory_stats()
                train_one_step(model, batch)
                print(torch.cuda.max_memory_allocated() / 1e9)   # ~1.8 GB, not ~0.01
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where the memory actually goes",
            accentColor = 0xFF10B981,
            code = """
                LAYERS, D, FFN, HEADS = 12, 768, 3072, 12
                BATCH, SEQ = 32, 128
                TOTAL = 108_891_648

                def state_bytes(trainable):
                    # fp16 weight + fp16 grad + fp32 master + 2 fp32 Adam moments = 16 B
                    return 16 * trainable + 2 * (TOTAL - trainable)

                def activation_bytes():
                    return LAYERS * (34 * BATCH * SEQ * D + 5 * BATCH * SEQ * SEQ * HEADS)

                full = state_bytes(TOTAL) + activation_bytes()      # 3.2 GB
                lora = state_bytes(294_912) + activation_bytes()    # 1.8 GB

                print(TOTAL / 294_912)      # 369x  fewer trainable parameters
                print(full / lora)          # 1.8x  less memory
                print(activation_bytes() / 1e9)   # 1.5 GB, and PEFT does not touch it

                # The real levers on that floor are gradient checkpointing (trades
                # compute for memory) and a smaller batch. Neither is a PEFT method.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("stack", 0xFFEF4444, "Multi-Tenant Serving", "One base model in memory, a small adapter swapped in per customer."),
        ApplicationCard("check", 0xFF10B981, "Cheap Experiments", "Dozens of task variants at megabytes each instead of gigabytes."),
        ApplicationCard("help", 0xFFF59E0B, "Memory Planning", "Budget from activations first — the trainable share will mislead you."),
        ApplicationCard("lock", 0xFF3B82F6, "Preserved Capability", "A frozen base cannot forget, which full fine-tuning cannot promise."),
    ),
    takeaways = listOf(
        "LoRA at rank 8 trains 0.271% of BERT-base — 294,912 of 108,891,648 parameters — and that number is honest.",
        "It buys 1.8× less training memory, not 369×, because the 1.5 GB activation floor is untouched by freezing weights.",
        "The headline overstates the memory saving by 204× for LoRA and 1,088× for IA³: the more extreme the method, the worse.",
        "Gradients still flow through frozen layers to reach the adapters beneath them, which is why activations do not shrink.",
        "The unambiguous win is operational: 0.6 MB per task instead of 208 MB, mergeable, with one base model in memory.",
    ),
    crossLinks = listOf(
        CrossLink("lora_qlora", "LoRA & QLoRA"),
        CrossLink("fine_tuning_full", "Fine-Tuning (Full)"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("quantization", "Quantization"),
    ),
)
