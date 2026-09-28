package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val reactContent = TopicContent(
    topicId = "react",
    whatIsIt = listOf(
        "ReAct interleaves reasoning and acting: the model writes a thought, takes an action, reads the observation, and only then writes its next thought. The two halves it combines each fail on their own. Chain of thought reasons but cannot check anything, so a missing fact becomes a fluent invention. A single tool call acts but cannot decide what to ask for next, so a question whose answer depends on an earlier answer is unreachable. Interleaving is what makes the second query a function of the first observation.",
        "The lab's question is deliberately two-hop — what year was the language the Linux kernel is written in first released — and the retrieval is real TF-IDF cosine over an eight-passage corpus, so the measurement is not a story. The passage holding the answer is about C, not about Linux, and it shares almost no vocabulary with the question as asked: it ranks **fifth of eight**, outside any sensible top-k. One retrieval cannot reach it. After the first observation establishes that the kernel is written in C, the second thought writes a different query — \"C language first released\" — and the same retriever puts that passage at **rank 1**. The rewrite is the entire contribution.",
        "The loop's failure modes follow from its shape. A thought that writes a bad query produces a bad observation and the model reasons on from it, so errors compound rather than cancel. A loop with no step cap can reissue the same action forever, because nothing in the pattern notices repetition. And every turn is a full model request over a transcript that keeps growing, which is priced properly in the agents topic next door. Log every action and observation: a ReAct trace that only records the final answer is not debuggable.",
    ),
    steps = listOf(
        StepCard(1, "Thought", "State what is known and what is still missing. This decides the next action.", 0xFF10B981),
        StepCard(2, "Action", "One tool call, with arguments the thought just chose.", 0xFF14B8A6),
        StepCard(3, "Observation", "The tool's real output, appended verbatim to the transcript.", 0xFF3B82F6),
        StepCard(4, "Rewrite and Repeat", "The next query is a function of what came back — that is the whole point.", 0xFF6366F1),
        StepCard(5, "Finish", "Answer only once the observations support it; cite which ones.", 0xFF8B5CF6),
        StepCard(6, "Cap the Loop", "A step limit and a repeat check, or the trace runs forever.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The loop", "thought → action → observation → thought → …", "Until an action is finish, or the step cap is hit."),
        FormulaEntry("Single-shot", "top-k(q₀)", "One query, one shot; the answer passage ranks 5 of 8 here."),
        FormulaEntry("Two-hop", "q₁ = f(o₀)", "The second query is written from the first observation — rank 1."),
        FormulaEntry("Closed-book", "answer without retrieval", "Fluent, specific, and wrong: \"1970\" against the true 1972."),
        FormulaEntry("Compounding", "P(correct) ≈ Πᵢ P(good queryᵢ)", "A bad query is reasoned on, not corrected."),
        FormulaEntry("Step cap", "n ≤ n_max", "The only thing between a stuck loop and an unbounded bill."),
    ),
    notationKey = listOf(
        NotationEntry("thought", "the model's reasoning turn — no side effects, decides the next action"),
        NotationEntry("action", "a tool call with arguments; the only thing that touches the world"),
        NotationEntry("observation", "the tool's output, fed back verbatim as context"),
        NotationEntry("multi-hop", "a question whose answer needs a fact found by an earlier retrieval"),
        NotationEntry("query rewriting", "issuing a different query than the user's, chosen from what was learned"),
        NotationEntry("step cap", "the maximum number of loop iterations before forced termination"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The loop, with a real tool and a real cap",
            accentColor = 0xFF10B981,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                SEARCH = {
                    "name": "search",
                    "description": (
                        "Search the document corpus. Call this whenever the answer depends on "
                        "a fact not already present in the conversation."
                    ),
                    "input_schema": {
                        "type": "object",
                        "properties": {"query": {"type": "string"}},
                        "required": ["query"],
                    },
                }

                question = "In what year was the language the Linux kernel is written in first released?"
                messages = [{"role": "user", "content": question}]

                for step in range(6):                     # the cap is not optional
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=1024,
                        tools=[SEARCH],
                        messages=messages,
                    )
                    messages.append({"role": "assistant", "content": response.content})

                    if response.stop_reason != "tool_use":
                        break                             # the model answered

                    results = []
                    for block in response.content:
                        if block.type == "tool_use":
                            print(f"[{step}] action: {block.input['query']}")
                            results.append({
                                "type": "tool_result",
                                "tool_use_id": block.id,
                                "content": retrieve(block.input["query"]),
                            })
                    messages.append({"role": "user", "content": results})
                else:
                    raise RuntimeError("step cap reached without an answer")
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why one retrieval is not enough — measured",
            accentColor = 0xFFEC4899,
            code = """
                # Scored with real TF-IDF cosine over the corpus, not asserted.

                question = "In what year was the language the Linux kernel is written in first released?"
                for passage, score in ranked(question):
                    print(f"{score:.3f}  {passage.title}")
                # 0.317  Linux kernel
                # 0.277  Rust
                # 0.236  Git
                # 0.184  Python
                # 0.148  C language      <- the answer, at rank 5 of 8

                # A top-3 retriever never sees it. The question is about Linux; the answer
                # passage is about C, and shares almost no vocabulary with the question.

                print(rank_of("Linux kernel written language", "C language"))   # not in top 1
                print(rank_of("C language first released", "C language"))       # 1

                # The second query could only be written after the first observation said
                # "C". That dependency is what the loop exists for -- and it is why a
                # bad first query poisons everything downstream rather than being corrected.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF10B981, "Multi-Hop Question Answering", "Questions whose second query depends on the first answer."),
        ApplicationCard("browser", 0xFF3B82F6, "Research Assistants", "Search, read, refine — the loop is the product."),
        ApplicationCard("check", 0xFF8B5CF6, "Verified Answers", "Every claim traceable to an observation in the trace."),
        ApplicationCard("help", 0xFFEC4899, "Compounding Errors", "A bad query is reasoned on, not corrected — log every step."),
    ),
    takeaways = listOf(
        "Reasoning alone invents missing facts; a single action can't choose what to ask next. ReAct interleaves them.",
        "Measured: the answer passage ranks 5 of 8 for the question as asked and 1 for the query the second thought writes.",
        "Query rewriting is the contribution — the second query is a function of the first observation.",
        "Errors compound rather than cancel: a bad query yields a bad observation the model then reasons from.",
        "A step cap and a repeat check are load-bearing; the pattern itself has no way to notice a stuck loop.",
    ),
    crossLinks = listOf(
        CrossLink("ai_agents", "AI Agents & Tool Use"),
        CrossLink("rag", "Retrieval-Augmented Generation"),
        CrossLink("chain_of_thought", "Chain of Thought"),
        CrossLink("hallucination_mitigation", "Hallucination Mitigation"),
    ),
)
