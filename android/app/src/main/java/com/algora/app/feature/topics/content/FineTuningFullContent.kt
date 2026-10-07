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

internal val fineTuningFullContent = TopicContent(
    topicId = "fine_tuning_full",
    figure = Figure(
        caption = "The page's lab after 300 epochs of full-batch gradient descent, all three " +
            "regimes trained on the same 16 points and scored on the same 60 unseen ones. The task " +
            "is whether a point lies inside a circle, and the pretrained body's six tanh units " +
            "already describe distance from the centre. Training from scratch fits 94% of its " +
            "training points and only 55% of new ones — 25 weights learning features from 16 " +
            "examples is memorisation. A new head on the frozen body has 7 weights to fit and " +
            "reaches 95% on unseen points. Full fine-tuning starts from the same good features but " +
            "is free to move all 25 weights, and with this little data it moves them towards the " +
            "16 points it can see: 100% on those, 82% on the rest. More examples are what tip the " +
            "balance back towards unfreezing; with 16, the body is worth more left alone.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("25", "94%", "55%"),
                listOf("7", "100%", "95%"),
                listOf("25", "100%", "82%"),
            ),
            rowHeaders = listOf("from scratch", "head only", "full fine-tune"),
            colHeaders = listOf("trainable", "train acc.", "test acc."),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Warn),
                FigureCell(1, 2, FigureTone.Accent),
                FigureCell(2, 1, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Full fine-tuning unfreezes every weight and continues training on the downstream task. It is the strongest form of transfer and the most expensive one, and the choice between it and simply training a new head on frozen features is a question about your dataset size, not about your ambition.",
        "The lab trains all three regimes for real on the same small problem: is a point inside a circle? There are 16 training points and 60 held-out ones. The network is a body of six tanh units (18 weights) and a sigmoid head (7 weights). The pretrained body comes from a related task, and its six units already respond to distance from the centre — the feature this task needs. Each regime then runs 300 epochs of full-batch gradient descent at a learning rate of 0.5.",
        "With 16 examples, the frozen body wins outright. Training from scratch reaches 94% on its training points and only **55%** on unseen ones: 25 weights have to invent their features from 16 points, and they memorise instead. A new head on the frozen body fits just 7 weights and scores **95%**. Full fine-tuning starts from the same good features and ends at 100% on the training set but **82%** on unseen points — every weight was free to move, and with so little data it moved towards the 16 points it could see. That is the small-data trap: more capacity to adapt is also more capacity to overfit.",
        "The trade reverses as data grows: a linear read-out of someone else's features has a ceiling, and once there are enough examples to adapt the body without memorising, unfreezing pays. Moving the body has a second cost the downstream score never shows. Whatever else the pretrained weights were good at drifts as they move, which is catastrophic forgetting — why a fine-tuned model's general abilities have to be re-measured rather than assumed.",
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
        FormulaEntry("Trainable weights", "25 full · 7 head only", "Body 2 → 6 tanh (18) plus head 6 → 1 sigmoid (7)."),
        FormulaEntry("Test accuracy, 16 examples", "scratch 55% · head 95% · full 82%", "After 300 epochs, on 60 unseen points."),
        FormulaEntry("Overfitting gap", "full: 100% train vs 82% test", "Freedom to move every weight, and too little data to move them well."),
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
                    {"params": model.bert.pooler.parameters(), "lr": 1e-4},   # otherwise never updated
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

                before = evaluate_upstream(model)
                model = full_finetune(model, downstream)
                after  = evaluate_upstream(model)

                print(after / before)                      # > 1: upstream loss went up
                print(evaluate_downstream(model))          # improved  <- looks great

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
        ApplicationCard("help", 0xFFF59E0B, "Small-Data Traps", "With 16 examples here, a frozen body beats fine-tuning outright, 95% to 82%."),
        ApplicationCard("check", 0xFF3B82F6, "Regression Testing", "Re-run the general benchmarks. Forgetting has no downstream symptom."),
    ),
    takeaways = listOf(
        "Dataset size decides the regime: with 16 examples the frozen body scores 95% on unseen points, full fine-tuning 82%, scratch 55%.",
        "Full fine-tuning's 100% training accuracy against 82% test is overfitting — it had every weight free and too little data.",
        "A frozen body has a ceiling more data never lifts; once there is enough data to adapt it without memorising, unfreeze.",
        "Moving the body costs upstream capability (catastrophic forgetting) that no downstream number shows — re-test the general benchmarks.",
        "Mixed-precision Adam costs 16 bytes per trainable parameter — 104 GB of state for a 7B model, before activations.",
    ),
    crossLinks = listOf(
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("peft", "PEFT"),
        CrossLink("lora_qlora", "LoRA & QLoRA"),
        CrossLink("rlhf", "RLHF"),
    ),
)
