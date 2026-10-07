package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val elmoContent = TopicContent(
    topicId = "elmo",
    whatIsIt = listOf(
        "ELMo — Embeddings from Language Models, 2018 — broke the assumption every embedding before it made: that a word has one vector. In word2vec, GloVe and FastText, \"bank\" has a single row in a table, and every occurrence in every corpus votes on the same numbers, so the river sense and the money sense are averaged into one point. ELMo makes the representation a function of the whole sentence, so the same word produces a different vector in each occurrence it appears in.",
        "The mechanism is a two-layer bidirectional LSTM trained as a language model — forward predicting the next token, backward predicting the previous one — over character-CNN inputs, so it has no vocabulary limit either. The contribution people underrate is the *deep* part: rather than taking the top layer, ELMo exposes all of them and lets each downstream task learn its own mixture, γ·Σⱼ sⱼhⱼ. Lower layers turn out to carry syntax and upper layers semantics, so a POS tagger and a question-answering model weight them differently. Freezing the language model and adding its layer mixture to each task's existing model — whose own parameters are all trained — beat the state of the art on six benchmarks at once.",
        "In the lab, one word in four sentences produces four vectors: the two river sentences sit at cosine 0.953 with each other and the two money sentences at 0.954, while every cross-sense pair scores lower — 0.904 on average. Word sense disambiguation with no sense inventory and no labels; the senses were never enumerated, they fell out of context. The trade ELMo introduced is one every contextual model since inherits: a 1M-word lookup table at 300 dimensions is 300M parameters you can memory-map and read instantly, while ELMo is 93.6M parameters that must *run* on every sentence. Smaller model, larger bill — nothing can be precomputed. Within a year BERT replaced the LSTM with a transformer, made the objective deeply bidirectional rather than two independent directions, and made fine-tuning the whole model the default; the idea that survived intact is the one ELMo established, that a token's representation is a function of its context.",
    ),
    steps = listOf(
        StepCard(1, "Embed Characters", "A character CNN produces the input vector, so any word — seen or not — has one.", 0xFF6366F1),
        StepCard(2, "Run Two LSTMs", "Forward and backward language models, two layers each, trained on 1B words.", 0xFF8B5CF6),
        StepCard(3, "Collect Every Layer", "2L+1 representations per token: the input plus each direction's hidden states.", 0xFF3B82F6),
        StepCard(4, "Learn a Task Mixture", "ELMo_task = γ Σⱼ sⱼ hⱼ, with softmax-normalised sⱼ trained per task.", 0xFF06B6D4),
        StepCard(5, "Freeze and Concatenate", "The biLM stays fixed; its output is concatenated to the task model's own embeddings.", 0xFF14B8A6),
        StepCard(6, "Pay Per Sentence", "No lookup: the model runs at inference, which is the cost of context.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("biLM objective", "Σ log p(tₖ | t₁…tₖ₋₁) + log p(tₖ | tₖ₊₁…t_N)", "Two independent directions — not jointly bidirectional, which is BERT's later fix."),
        FormulaEntry("Layer mixture", "ELMoₖ = γ Σ_{j=0..L} sⱼ h_{k,j}", "s softmax-normalised and learned per task; γ scales the whole vector."),
        FormulaEntry("Representations per token", "2L + 1 = 5 for the two-layer model", "The character embedding plus two hidden states in each direction."),
        FormulaEntry("Sense separation, measured", "within 0.953 / 0.954 · across 0.904", "Every same-sense pair beats every cross-sense pair in the lab."),
        FormulaEntry("Static baseline", "cos(bank, bank) = 1.00 by construction", "One vector per type — there is nothing else it could be."),
        FormulaEntry("Parameter trade", "300M lookup (1M × 300) vs 93.6M that must run", "Smaller model, and nothing can be precomputed."),
    ),
    notationKey = listOf(
        NotationEntry("contextual embedding", "a vector computed per occurrence rather than looked up per type"),
        NotationEntry("biLM", "bidirectional language model — here two separately trained directions"),
        NotationEntry("h_{k,j}", "token k's representation at layer j; ELMo keeps all of them"),
        NotationEntry("sⱼ / γ", "learned layer weights and a global scale, the only ELMo-specific parameters a task trains (the task model's own parameters are trained too)"),
        NotationEntry("feature-based transfer", "freeze the pretrained model and feed its output in — as opposed to fine-tuning it"),
        NotationEntry("polysemy", "one form, several senses — the failure of static embeddings this removes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "One word, two vectors",
            accentColor = 0xFF6366F1,
            code = """
                import torch
                from allennlp.modules.elmo import Elmo, batch_to_ids

                elmo = Elmo(options_file, weight_file, num_output_representations=1)

                sentences = [["we", "sat", "on", "the", "river", "bank"],
                             ["she", "deposited", "cash", "at", "the", "bank"]]
                ids = batch_to_ids(sentences)
                out = elmo(ids)["elmo_representations"][0]      # (2, 6, 1024)

                river, money = out[0, 5], out[1, 5]             # "bank" in each sentence
                print(torch.cosine_similarity(river, money, dim=0))   # well below 1.0

                # A static model cannot produce this number at all: word2vec's "bank" is one row,
                # so the same comparison is 1.0 by construction, whatever the sentences were.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The layer mixture, which is the part that generalised",
            accentColor = 0xFFEC4899,
            code = """
                import torch, torch.nn as nn

                class LayerMix(nn.Module):
                    # ELMo_task = gamma * sum_j softmax(s)_j * h_j
                    def __init__(self, n_layers=3):
                        super().__init__()
                        self.s = nn.Parameter(torch.zeros(n_layers))   # learned PER TASK
                        self.gamma = nn.Parameter(torch.ones(1))

                    def forward(self, layers):                          # list of (B, T, D)
                        w = torch.softmax(self.s, dim=0)
                        return self.gamma * sum(wi * h for wi, h in zip(w, layers))

                # Trained weights are interpretable and consistently task-dependent: POS tagging
                # leans on the lower layer (syntax), coreference and QA on the upper one (semantics).
                # Taking only the top layer -- the obvious thing -- measurably underperforms it.
                #
                # BERT kept the idea and dropped the freezing: fine-tune everything, use a
                # transformer instead of an LSTM, and make the objective deeply bidirectional with
                # masking rather than concatenating two one-directional models.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF6366F1, "Word Sense Disambiguation", "Senses separate without a sense inventory, labels or a lexicon."),
        ApplicationCard("users", 0xFF8B5CF6, "Coreference & QA", "The tasks where ELMo's gains were largest, and where the upper layer dominates the mixture."),
        ApplicationCard("flask", 0xFF3B82F6, "Domain Transfer", "Freezing a biLM and training only the layer weights is still the cheapest way to adapt on small data."),
        ApplicationCard("history", 0xFFEC4899, "The Turn It Marked", "The moment NLP moved from downloading vectors to downloading models — BERT arrived months later."),
    ),
    takeaways = listOf(
        "A word's vector becomes a function of its sentence: four occurrences of \"bank\", four different vectors.",
        "Measured in the lab, same-sense pairs score 0.953 and 0.954 while every cross-sense pair is lower (0.904 average).",
        "It is a two-layer biLM over character inputs, so there is no vocabulary limit and no <unk>.",
        "The layer mixture γΣsⱼhⱼ is the durable idea — lower layers carry syntax, upper layers semantics, and tasks weight them differently.",
        "The cost is inference: 93.6M parameters that must run per sentence, against a 300M-parameter table you could memory-map.",
    ),
    crossLinks = listOf(
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("rnn_lstm", "RNN / LSTM"),
        CrossLink("transformers", "Transformers"),
        CrossLink("transfer_learning", "Transfer Learning"),
    ),
)
