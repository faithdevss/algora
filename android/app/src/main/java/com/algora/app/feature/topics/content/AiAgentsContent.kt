package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val aiAgentsContent = TopicContent(
    topicId = "ai_agents",
    whatIsIt = listOf(
        "An agent is a loop with three parts: a model that emits a tool call, a runtime that executes it, and a transcript that carries the result back. The tools are declared as typed schemas — a name, a description, and a JSON Schema for the arguments — and the model chooses among them. The description is not documentation: it is the only thing the model has when deciding whether this tool applies, so it should say *when* to call the tool, not just what the tool does.",
        "The cost model is where intuition fails. The API is stateless, so every model turn resends the entire transcript. Context grows linearly in the number of steps; the bill is the sum of every request, which grows quadratically. On the lab's real eight-entry trajectory the final context is 700 tokens and the amount actually billed is 1,970 — **2.81×** — and the multiple gets worse the longer the agent runs. That is also why a failed tool call is expensive out of proportion to its error message: dropping the failed unit call and its retry takes the bill from 1,970 to 1,323, so those two entries cost **647 tokens, a 49% overhead**, because everything after them is resent with them attached.",
        "The levers follow directly. Tighten the schema so the bad call is impossible — a strict enum on a unit parameter is cheaper than any retry. Batch independent calls into one turn: three tools with 240, 15 and 12 ms latencies cost 267 ms sequentially and 240 ms in parallel, and collapse three billed turns into one. Sequential turns are only required when a later call needs an earlier observation, which is exactly the ReAct case. And cap the loop — a step limit is the only thing standing between a model that has decided to retry forever and an unbounded bill.",
    ),
    steps = listOf(
        StepCard(1, "Declare Tools", "Typed schemas. The description states when to call, not just what it does.", 0xFF10B981),
        StepCard(2, "Model Emits a Call", "Structured arguments, validated against the schema you wrote.", 0xFF14B8A6),
        StepCard(3, "Runtime Executes", "Your code, your permissions. Errors come back as observations, not exceptions.", 0xFF3B82F6),
        StepCard(4, "Append and Resend", "Stateless API: the whole transcript goes back every turn.", 0xFF6366F1),
        StepCard(5, "Batch What's Independent", "Parallel calls in one turn; sequential only when data flows between them.", 0xFF8B5CF6),
        StepCard(6, "Cap and Gate", "A step limit, and human approval for anything hard to reverse.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Context at turn t", "system + schemas + Σ tokens of steps < t", "Linear in the number of steps."),
        FormulaEntry("Tokens billed", "Σₜ context(t)", "Quadratic — every turn resends everything before it."),
        FormulaEntry("Measured", "1,970 billed vs 700 final = 2.81×", "An eight-entry trajectory with one retry."),
        FormulaEntry("Retry cost", "1,970 − 1,323 = 647 tokens", "49% overhead from two entries, because they are resent."),
        FormulaEntry("Fixed overhead", "system + tool schemas, every turn", "180 + 86 = 266 tokens before any work happens."),
        FormulaEntry("Parallel vs sequential", "max(latency) vs Σ(latency)", "240 ms against 267 ms, and two fewer billed turns."),
    ),
    notationKey = listOf(
        NotationEntry("tool schema", "name, description and JSON Schema for arguments — the model's whole view of the tool"),
        NotationEntry("tool_use / tool_result", "the model's structured call and your runtime's reply, both transcript entries"),
        NotationEntry("trajectory", "the full sequence of thoughts, calls and observations for one task"),
        NotationEntry("agentic loop", "call → execute → append → resend, until the model stops calling tools"),
        NotationEntry("step cap", "hard limit on iterations; the bound on a runaway loop"),
        NotationEntry("parallel tool use", "several independent calls in one assistant turn, all results returned together"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The loop, written out",
            accentColor = 0xFF10B981,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                TOOLS = [
                    {
                        "name": "units",
                        # Say WHEN to call it, not only what it does -- this description is
                        # the model's entire basis for choosing this tool.
                        "description": "Convert a value between units. Call this whenever a "
                                       "quantity must be expressed in a different unit.",
                        "input_schema": {
                            "type": "object",
                            "properties": {
                                "value": {"type": "number"},
                                # A strict enum makes the bad call impossible, which is
                                # cheaper than any retry (see the cost block below).
                                "from": {"type": "string", "enum": ["mile", "km", "m"]},
                                "to": {"type": "string", "enum": ["mile", "km", "m"]},
                            },
                            "required": ["value", "from", "to"],
                            "additionalProperties": False,
                        },
                        "strict": True,
                    },
                ]

                messages = [{"role": "user", "content": "How far is 240 miles in kilometres?"}]

                for _ in range(8):                       # step cap
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=2048,
                        tools=TOOLS,
                        messages=messages,
                    )
                    messages.append({"role": "assistant", "content": response.content})
                    if response.stop_reason != "tool_use":
                        break

                    # Execute every call in this turn, and return ALL results in ONE user
                    # message -- splitting them teaches the model to stop calling in parallel.
                    results = []
                    for block in response.content:
                        if block.type == "tool_use":
                            try:
                                out = run_tool(block.name, block.input)
                                results.append({"type": "tool_result",
                                                "tool_use_id": block.id, "content": out})
                            except ToolError as exc:
                                # An error is an observation, not an exception. Return it
                                # so the model can correct itself.
                                results.append({"type": "tool_result",
                                                "tool_use_id": block.id,
                                                "content": str(exc), "is_error": True})
                    messages.append({"role": "user", "content": results})
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Pricing the trajectory — linear transcript, quadratic bill",
            accentColor = 0xFFEC4899,
            code = """
                SYSTEM = 180
                SCHEMAS = 86                              # every turn, before any work

                # A real eight-entry trajectory; steps 4 and 5 are a failed unit call and
                # its retry.
                STEPS = [48, 96, 61, 34, 54, 24, 43, 74]
                CLEAN = [48, 96, 61, 24, 43, 74]          # same run, without the failure

                def context_at(step, steps):
                    return SYSTEM + SCHEMAS + sum(steps[:step])

                def billed(steps):
                    # One request per MODEL turn; the odd entries are tool output.
                    return sum(context_at(i + 1, steps) for i in range(0, len(steps), 2))

                print(context_at(len(STEPS), STEPS))       # 700   final context
                print(billed(STEPS))                       # 1970  actually billed  (2.81x)
                print(billed(STEPS) - billed(CLEAN))       # 647   cost of one retry (+49%)

                # The retry is expensive not because the error message is long, but because
                # every turn after it resends both entries. The cheapest fix is upstream:
                # a strict enum in the schema, so the failing call cannot be emitted.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF10B981, "Task Automation", "Multi-step work where each action's result decides the next."),
        ApplicationCard("finance", 0xFF3B82F6, "Cost Modelling", "Quadratic billing is the number that surprises people in production."),
        ApplicationCard("lock", 0xFF8B5CF6, "Approval Gates", "Hard-to-reverse actions gated behind a human, inside the tool function."),
        ApplicationCard("help", 0xFFEC4899, "Runaway Loops", "No step cap means no bound on spend when the model decides to retry."),
    ),
    takeaways = listOf(
        "An agent is call → execute → append → resend; the tool description is the model's whole basis for choosing.",
        "The transcript grows linearly and the bill grows quadratically — 1,970 tokens billed against a 700-token final context.",
        "One failed call plus its retry cost 647 tokens, 49% overhead, because everything after them is resent too.",
        "Tighten the schema before adding retry logic: a strict enum prevents the call that a retry would fix.",
        "Batch independent calls into one turn — 267 ms of round trips becomes 240 ms, and three billed turns become one.",
    ),
    crossLinks = listOf(
        CrossLink("react", "ReAct"),
        CrossLink("tree_of_thoughts", "Tree of Thoughts"),
        CrossLink("llms", "LLMs"),
        CrossLink("agent_environment", "Agent & Environment"),
    ),
)
