package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fineTuningFullContent = TopicContent(
    topicId = "fine_tuning_full",
    whatIsIt = listOf(
        "Full fine-tuning unfreezes every weight and continues training on the downstream task. It is the strongest form of transfer and the most expensive one, and the choice between it and simply training a new head on frozen features is a question about your dataset size, not about your ambition.",
        "The lab trains all three regimes for real on the same data: from scratch, head-only on a frozen pretrained body, and full fine-tuning. Pretraining is multi-task — one body, six heads — and that detail turned out to be load-bearing. The first version of this lab pretrained on a single scalar output, which only needs one direction of the representation; the \"reusable feature extractor\" it was supposed to produce collapsed, and feature extraction could never fit anything. Six heads force a full-rank representation, which is what makes frozen features worth having.",
        "The result is the textbook curve, measured. At 8 examples the frozen body wins 0.171 to 0.288. The crossover is at **32 examples**, and past it full fine-tuning pulls away: 0.0008 against 0.0121 at 256, fifteen times better. The frozen curve flattens because a linear read-out of someone else's features has a ceiling — and training from scratch reaching that same ceiling at 256 examples (0.0119) is a usable signal that the features, not the data, had become the constraint.",
        "What the downstream number never shows is the bill. Score the six pretraining tasks again after the body has moved and their loss goes from 0.0002 to 0.0595 — **253× worse**. That is catastrophic forgetting, it is invisible from every metric anyone watches during fine-tuning, and it is why the general capabilities of a fine-tuned model have to be re-evaluated rather than assumed.",
    ),
    steps = listOf(
        StepCard(1, "Start From The Checkpoint", "Pretrained weights are the initialisation. Random init throws away everything the corpus paid for.", 0xFFEF4444),
        StepCard(2, "Attach A Task Head", "A fresh output layer for the new label space, randomly initialised.", 0xFFF97316),
        StepCard(3, "Pick A Much Smaller Rate", "Pretrained weights are near a good solution; a pretraining-sized step walks straight out of it.", 0xFFF59E0B),
        StepCard(4, "Warm Up The Head First", "A random head produces large early gradients that flow into the body and undo it.", 0xFF10B981),
        StepCard(5, "Watch Both Tasks", "Downstream loss falls while upstream loss rises. Only one of them is on your dashboard.", 0xFF3B82F6),
        StepCard(6, "Price The Optimizer", "16 bytes per trainable parameter under mixed-precision Adam — 104 GB of state for a 7B model.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min over all θ of L_down(θ), from θ = θ_pretrained", "Nothing constrains θ to stay near where it started."),
        FormulaEntry("Regime crossover", "32 examples", "Below it the frozen body wins; above it, full fine-tuning."),
        FormulaEntry("Frozen ceiling", "0.0121 at any dataset size", "A linear read-out of fixed features cannot do better."),
        FormulaEntry("Forgetting", "0.0002 → 0.0595 upstream", "253× worse, with no downstream symptom."),
        FormulaEntry("Optimizer state", "16 bytes per trainable parameter", "fp16 weight + fp16 grad + fp32 master + 2 fp32 moments."),
        FormulaEntry("7B training state", "104.3 GB full vs 13.3 GB at 20M trainable", "The gap PEFT exists to close."),
    ),
    notationKey = listOf(
        NotationEntry("θ_pretrained", "the checkpoint the run starts from, not a random draw"),
        NotationEntry("feature extraction", "freeze the body, train only a new head"),
        NotationEntry("full fine-tuning", "every parameter trainable, including the embeddings"),
        NotationEntry("catastrophic forgetting", "loss of upstream capability caused by downstream training"),
        NotationEntry("discriminative rates", "a different learning rate per layer, lowest at the bottom"),
        NotationEntry("optimizer state", "everything the optimizer holds per parameter besides the weight"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Full fine-tuning with a warmed-up head and layer-wise rates",
            accentColor = 0xFFEF4444,
            code = """
                import torch
                from transformers import AutoModelForSequenceClassification, get_linear_schedule_with_warmup

                model = AutoModelForSequenceClassification.from_pretrained(
                    "bert-base-uncased", num_labels=3
                )

                # Stage 1: the head is random, so its gradients are large. Let it settle
                # before those gradients are allowed into the body.
                for p in model.bert.parameters():
                    p.requires_grad = False
                train(model, epochs=1, lr=1e-3)

                # Stage 2: unfreeze, with a much smaller rate at the bottom of the stack.
                for p in model.bert.parameters():
                    p.requires_grad = True

                groups = [
                    {"params": model.classifier.parameters(), "lr": 1e-4},
                    {"params": model.bert.encoder.layer[8:].parameters(), "lr": 3e-5},
                    {"params": model.bert.encoder.layer[:8].parameters(), "lr": 1e-5},
                    {"params": model.bert.embeddings.parameters(), "lr": 5e-6},
                ]
                opt = torch.optim.AdamW(groups, weight_decay=0.01)
                sched = get_linear_schedule_with_warmup(opt, num_warmup_steps=100,
                                                        num_training_steps=total_steps)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measuring what the downstream metric will not tell you",
            accentColor = 0xFF3B82F6,
            code = """
                # Downstream loss falls the whole time. That is not the question.
                # The question is what happened to everything the model could already do.

                before = evaluate_upstream(model)          # 0.0002
                model = full_finetune(model, downstream, n=256)
                after  = evaluate_upstream(model)          # 0.0595

                print(after / before)                      # 253x worse
                print(evaluate_downstream(model))          # 0.0008  <- looks great

                # Two cheap mitigations, both of which trade some downstream gain:
                #   1. Freeze the lower half. Most forgetting happens in the layers
                #      that hold general features.
                #   2. Add an L2 pull toward the original weights, which turns
                #      "go anywhere" into "stay near the checkpoint".
                loss = task_loss + 0.01 * sum(
                    ((p - p0) ** 2).sum() for p, p0 in zip(model.parameters(), pretrained)
                )
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("flame", 0xFFEF4444, "Domain Adaptation", "Legal, medical or code corpora where the vocabulary itself has shifted."),
        ApplicationCard("target", 0xFF10B981, "Task Specialisation", "One task, enough labels, and no need for the model to stay general."),
        ApplicationCard("help", 0xFFF59E0B, "Small-Data Traps", "Under ~32 examples here, a frozen body beats fine-tuning outright."),
        ApplicationCard("check", 0xFF3B82F6, "Regression Testing", "Re-run the general benchmarks. Forgetting has no downstream symptom."),
    ),
    takeaways = listOf(
        "The crossover is measured, not philosophical: frozen features win at 8 examples, full fine-tuning wins from 32.",
        "A frozen body has a ceiling (0.0121 here) that more data never lifts — when training from scratch catches it, unfreeze.",
        "Full fine-tuning made the six pretraining tasks 253× worse while every downstream number improved.",
        "Multi-task pretraining is what makes frozen features reusable; a single-output pretrain collapses the representation.",
        "Mixed-precision Adam costs 16 bytes per trainable parameter — 104 GB of state for a 7B model, before activations.",
    ),
    crossLinks = listOf(
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("peft", "PEFT"),
        CrossLink("lora_qlora", "LoRA & QLoRA"),
        CrossLink("rlhf", "RLHF"),
    ),
)
