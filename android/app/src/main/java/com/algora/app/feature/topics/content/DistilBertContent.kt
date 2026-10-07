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

internal val distilBertContent = TopicContent(
    topicId = "distilbert",
    figure = Figure(
        caption = "DistilBERT against its teacher, BERT-base: half the layers, 40% fewer " +
            "parameters, 60% faster inference, and about 97% of the GLUE score. The student keeps " +
            "the teacher's width and is initialised from every other teacher layer; what it trains " +
            "on is the teacher's full output distribution, softened by a temperature T, not just " +
            "the right answer. The page's lab shows why that carries more: at the teacher's least " +
            "certain step its top guess gets only 23%, with the runner-up at 22% — a near tie that " +
            "a one-hot label would erase, throwing away 77% of what the teacher knows. Raising T " +
            "from 1 to 2 lifts the distribution's entropy from 2.53 to 2.63 bits, spreading that " +
            "information further at the cost of a weaker signal on the top class.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("12", "6"),
                listOf("110M", "66M"),
                listOf("1×", "1.6×"),
                listOf("100%", "≈97%"),
            ),
            rowHeaders = listOf("layers", "parameters", "inference speed", "GLUE score"),
            colHeaders = listOf("BERT-base", "DistilBERT"),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Accent),
                FigureCell(3, 1, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Distillation trains a small model on a large one's *distribution* rather than on the labels. The reason that helps is measurable on any real distribution: at the step a trained model is least certain about, its top choice holds 0.296 of the mass and everything else holds 0.704, with the runner-up at 0.675 of the winner's probability. A hard label keeps the 0.296 and throws the rest away. The teacher's full output says not only which answer is right but which wrong answers were close — and that ranking is signal the student can learn from.",
        "Temperature is what makes that structure trainable. Dividing the logits by T flattens the distribution: on the same real teacher output, T = 4 takes entropy from 1.706 to 1.838 nats and the top choice from 0.296 to 0.193, so the gradient carries information about the alternatives instead of being dominated by the winner. Because soft-target gradients shrink as 1/T², the loss is multiplied by T² — 16 at T = 4 — to keep the two terms comparable. DistilBERT's actual loss is three terms: the distillation KL, the ordinary masked-LM loss, and a cosine term aligning the student's hidden states with the teacher's.",
        "The compression itself is blunt: keep every second layer of BERT-base, keep the embeddings, drop the pooler, and train. Summed from the configs that is 66,362,880 parameters against 109,482,240 — 39.4% smaller, for half the layers. The two numbers do not match because the embedding table does not shrink: it is 23,835,648 parameters in both, which is 36% of DistilBERT against 22% of BERT. That is the general rule this table teaches — depth is what compression reaches and vocabulary is what it cannot, which is also why the published result (97% of BERT's GLUE score at 60% faster inference) is believable: the layers are where the compute goes, and the layers are what was halved.",
    ),
    steps = listOf(
        StepCard(1, "Take the Teacher's Distribution", "Not the argmax — the 0.704 of mass beside it.", 0xFF3B82F6),
        StepCard(2, "Soften It", "Divide logits by T; entropy 1.706 → 1.838 at T = 4.", 0xFFF59E0B),
        StepCard(3, "Scale the Loss by T²", "Soft gradients shrink as 1/T²; 16× puts them back.", 0xFF8B5CF6),
        StepCard(4, "Halve the Depth", "Every second layer of the teacher, initialised from it.", 0xFF10B981),
        StepCard(5, "Add the Other Two Terms", "Masked-LM loss and a hidden-state cosine loss.", 0xFF6366F1),
        StepCard(6, "Read the Table", "39.4% smaller, not 50% — the embeddings do not move.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Soft targets", "pᵢ = softmax(zᵢ / T)", "T = 2 in DistilBERT's released recipe; T = 4 is used here as an illustration."),
        FormulaEntry("Distillation loss", "L = T²·KL(teacher ‖ student) + L_mlm + L_cos", "Three terms, not one."),
        FormulaEntry("Why T²", "soft gradients scale as 1/T²", "16× at T = 4 (4× at DistilBERT's T = 2), so the terms stay comparable."),
        FormulaEntry("Measured teacher", "top 0.296 · off-top 0.704", "Runner-up at 0.675 of the winner."),
        FormulaEntry("Size", "66,362,880 vs 109,482,240", "39.4% smaller at half the layers."),
        FormulaEntry("Embeddings", "23,835,648 in both", "36% of the student, 22% of the teacher."),
    ),
    notationKey = listOf(
        NotationEntry("T", "temperature — the divisor applied to logits before the softmax"),
        NotationEntry("soft targets", "the teacher's full distribution, used in place of a one-hot label"),
        NotationEntry("dark knowledge", "the off-top mass; the ranking of the wrong answers"),
        NotationEntry("KL", "Kullback-Leibler divergence between student and teacher distributions"),
        NotationEntry("L_cos", "cosine loss aligning student hidden states with the teacher's"),
        NotationEntry("layer selection", "every second teacher layer, used to initialise the student"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The three-term loss, with the T² that is easy to forget",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn.functional as F

                def distillation_loss(student_logits, teacher_logits, labels, hidden_s, hidden_t,
                                      T=4.0, alpha=5.0, beta=2.0, gamma=1.0):
                    soft = F.kl_div(
                        F.log_softmax(student_logits / T, dim=-1),
                        F.softmax(teacher_logits / T, dim=-1),
                        reduction="batchmean",
                    ) * T * T          # <- without this the soft term is ~1/16 of its intended size
                    hard = F.cross_entropy(student_logits.flatten(0, 1), labels.flatten())
                    cosine = 1 - F.cosine_similarity(hidden_s, hidden_t, dim=-1).mean()
                    return alpha * soft + beta * hard + gamma * cosine

                # The T² is the single most commonly dropped line in distillation code, and it does
                # not fail loudly -- training still converges, just onto the hard labels, which is
                # exactly the signal distillation was meant to add.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where the parameters actually are, before choosing what to cut",
            accentColor = 0xFFEC4899,
            code = """
                def bert_like(vocab=30522, d=768, layers=12, ffn=3072, positions=512, types=2, pooler=True):
                    embeddings = vocab * d + positions * d + types * d + 2 * d
                    per_layer = 4 * (d * d + d) + (d * ffn + ffn) + (ffn * d + d) + 4 * d
                    return {
                        "embeddings": embeddings,
                        "layers": layers * per_layer,
                        "pooler": d * d + d if pooler else 0,
                    }

                teacher = bert_like()                                     # 109,482,240 total
                student = bert_like(layers=6, types=0, pooler=False)       #  66,362,880 total

                # layers:     85,054,464 -> 42,527,232   (halved, as intended)
                # embeddings: 23,837,184 -> 23,835,648   (unchanged)
                #
                # Halving the layers removes 43M parameters and 0 embedding parameters, which is why
                # the model is 39.4% smaller rather than 50%. If a table's embedding share were 60%
                # instead of 22%, cutting depth would be the wrong lever entirely -- vocabulary
                # pruning or factorised embeddings (ALBERT) would be.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Serving Cost", "60% faster inference at 97% of the score, per the paper."),
        ApplicationCard("chip", 0xFF3B82F6, "On-Device NLP", "Six layers fits where twelve does not."),
        ApplicationCard("flask", 0xFF8B5CF6, "Any Teacher-Student Pair", "The recipe generalises; the temperature and T² do not change."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "If the table is mostly embeddings, cutting depth saves little."),
    ),
    takeaways = listOf(
        "Distillation trains on the teacher's distribution: on a real one, 0.704 of the mass sits off the top choice.",
        "Temperature exposes that structure — at T = 4, entropy 1.706 → 1.838 nats and the top choice 0.296 → 0.193.",
        "Soft-target gradients scale as 1/T², so the loss is multiplied by T² — 16 at T = 4.",
        "The real loss has three terms: distillation KL, masked-LM, and a cosine loss on hidden states.",
        "The compression is every second layer of BERT-base, no pooler, embeddings kept.",
        "66,362,880 against 109,482,240 — 39.4% smaller, not 50%, because the embedding table does not shrink.",
        "Embeddings are 36% of the student against 22% of the teacher; depth is what compression reaches.",
    ),
    crossLinks = listOf(
        CrossLink("bert", "BERT"),
        CrossLink("roberta", "RoBERTa"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("softmax", "Softmax (Output Layer)"),
        CrossLink("transformers", "Transformers"),
    ),
)
