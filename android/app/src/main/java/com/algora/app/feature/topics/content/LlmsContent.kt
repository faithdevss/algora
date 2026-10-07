package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val llmsContent = TopicContent(
    topicId = "llms",
    whatIsIt = listOf(
        "Large language models (LLMs) are Transformer networks with billions of parameters, trained on vast text to predict the next token — and from that simple objective emerge translation, reasoning, and coding abilities.",
        "They are pretrained on raw text, then aligned to follow instructions and human preferences, yielding assistants like GPT and Claude.",
    ),
    steps = listOf(
        StepCard(1, "Pretrain on Next-Token Prediction", "Learn language by predicting the next token across trillions of words.", 0xFF818CF8),
        StepCard(2, "Scale Up", "More parameters, data, and compute unlock new capabilities — the scaling laws.", 0xFF60A5FA),
        StepCard(3, "Instruction-Tune & Align", "Fine-tune on demonstrations, then RLHF to follow instructions and human preferences.", 0xFF10B981),
        StepCard(4, "Prompt & Generate", "At inference, the model autoregressively samples tokens, steered by the prompt and context.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "maximize Σ log P(tokenₜ | token₁…ₜ₋₁)", "Next-token prediction."),
        FormulaEntry("Scaling laws", "loss ∝ power law in N, D, C", "Params N, data D, compute C."),
        FormulaEntry("Sampling", "temperature, top-p", "Control randomness of generation."),
    ),
    notationKey = listOf(
        NotationEntry("autoregressive", "generating one token at a time, left to right"),
        NotationEntry("RLHF", "reinforcement learning from human feedback"),
        NotationEntry("context window", "max tokens the model can attend to at once"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Sampling from a causal LLM",
            accentColor = 0xFF6366F1,
            code = """
                from transformers import pipeline

                gen = pipeline("text-generation", model="gpt2")
                # gpt2 is a base model, not instruction-tuned; swap in an instruct model for chat.
                # temperature is ignored unless sampling is on:
                out = gen("The key idea behind attention is",
                          max_new_tokens=30, do_sample=True, temperature=0.7)
                print(out[0]["generated_text"])
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Assistants & Chatbots", "Conversational AI, coding copilots, and writing tools are LLM-powered."),
        ApplicationCard("search", 0xFF60A5FA, "RAG & Search", "Retrieval-augmented generation grounds LLM answers in external documents."),
        ApplicationCard("book", 0xFF10B981, "Summarize / Translate / Extract", "One model handles many NLP tasks via prompting, no task-specific training."),
    ),
    takeaways = listOf(
        "LLMs are large Transformers trained on next-token prediction over huge corpora.",
        "Capabilities emerge with scale, described by empirical scaling laws.",
        "Instruction tuning and RLHF align raw models into helpful assistants.",
        "They hallucinate and lack grounding — retrieval and tools mitigate this.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("rlhf", "RLHF"),
    ),
)
