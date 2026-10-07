package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hallucinationMitigationContent = TopicContent(
    topicId = "hallucination_mitigation",
    figure = Figure(
        caption = "The page's ten-question population, scored three ways at the same 60% coverage " +
            "— six of ten questions answered, the rest refused. Answering everything gets 0.605. " +
            "Refusing when self-consistency confidence falls below 0.8 lifts that only to 0.668, " +
            "because on this population confidence does not separate the two groups: the four " +
            "unanswerable questions are built so every sampled chain lands on the same wrong " +
            "answer, which is what a hallucination is, so the model's agreement with itself is " +
            "high exactly where it is wrong — 0.88 on the most confident false claim against 0.71 " +
            "on the least confident true one. Grounding refuses unless a retrieved passage " +
            "supports the claim, and reaches 0.959 at the same coverage. The difference is the " +
            "signal, not the threshold: one reads the evidence, the other reads how firmly the " +
            "model believes what it says.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("answer all", 0.605f, FigureTone.Muted),
                FigureBar("confidence ≥ 0.8", 0.668f, FigureTone.Warn),
                FigureBar("grounded", 0.959f, FigureTone.Accent),
            ),
            yLabel = "accuracy on answered, 0 to 1",
        ),
    ),
    whatIsIt = listOf(
        "A hallucination is not a random error. A model that is unsure produces different answers on different samples, which is detectable and mostly harmless. A hallucination is the other case: a fluent, specific, confidently-held claim that happens to be false, produced the same way every time. The distinction matters because almost every popular mitigation is a detector, and a detector's usefulness depends entirely on which of the two it is looking at.",
        "The lab makes that concrete on ten questions — six the corpus supports and four it cannot. Confidence is what self-consistency would report (the expected share of samples landing on the modal answer), and accuracy is what those samples would actually get right; both are computed exactly rather than sampled. The four unanswerable questions are constructed so the model is *systematically* wrong about them — every chain lands on the same wrong answer — because that is what a hallucination is. Under that construction the reported confidence does not separate the two groups at all: the least-confident supported question sits at 0.71 while the most-confident unsupported one sits at 0.88, and the expected calibration error is 0.225.",
        "So thresholding on confidence barely works here. The best threshold that still answers half the questions refuses 40% of them and lifts accuracy from 0.605 only to 0.668. Grounding — refuse unless a retrieved passage supports the claim — reaches **0.959 at the same 60% coverage**, because it reads the evidence rather than the model's agreement with itself. The control run proves the mechanism rather than asserting it: rerun the identical population with the errors *scattered* over four wrong answers instead of one, and confidence separates cleanly and thresholding scores 0.959 too. Self-consistency measures how firmly the model believes something. That is a good signal against random slips and a worthless one against a confident false belief.",
    ),
    steps = listOf(
        StepCard(1, "Retrieve First", "A fact the model does not have cannot be recovered by reasoning harder.", 0xFF10B981),
        StepCard(2, "Constrain to Evidence", "Answer from the passages, and say so when they do not cover the question.", 0xFF14B8A6),
        StepCard(3, "Cite", "A claim tied to a passage can be checked; one that isn't, can't.", 0xFF3B82F6),
        StepCard(4, "Abstain", "Refusing is an outcome, not a failure — price it in coverage.", 0xFF6366F1),
        StepCard(5, "Measure Calibration", "Confidence against accuracy, binned. ECE tells you if the signal is real.", 0xFF8B5CF6),
        StepCard(6, "Know the Signal's Range", "Agreement catches random error and misses systematic error entirely.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Reported confidence", "E[ max cᵢ / m ]", "Expected share of m samples on the modal answer."),
        FormulaEntry("Selective accuracy", "accuracy on the answered subset", "Paired with coverage; neither number means anything alone."),
        FormulaEntry("Coverage", "answered / total", "What abstention costs, in questions not answered."),
        FormulaEntry("ECE", "Σ (nᵦ/N) · |conf(b) − acc(b)|", "0.225 on this population — the signal is badly calibrated."),
        FormulaEntry("Confidence threshold", "τ = 0.8 → coverage 0.60, accuracy 0.668", "The best point that still answers half."),
        FormulaEntry("Grounding", "coverage 0.60, accuracy 0.959", "Same coverage, different signal — evidence rather than agreement."),
    ),
    notationKey = listOf(
        NotationEntry("hallucination", "a confident, specific, false claim — reproduced, not sampled at random"),
        NotationEntry("grounding", "requiring every claim to be supported by a retrieved passage"),
        NotationEntry("abstention", "declining to answer below a confidence or support threshold"),
        NotationEntry("coverage", "the fraction of questions the system chose to answer"),
        NotationEntry("selective accuracy", "accuracy measured only over the answered questions"),
        NotationEntry("ECE", "expected calibration error — mean gap between stated confidence and observed accuracy"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Grounded answering with an explicit refusal path",
            accentColor = 0xFF10B981,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                SYSTEM = (
                    "Answer only from the passages provided. Cite the passage number for "
                    "every claim. If the passages do not contain the answer, reply exactly "
                    "INSUFFICIENT_EVIDENCE and nothing else — do not answer from prior "
                    "knowledge, and do not guess."
                )

                def grounded_answer(question: str) -> str | None:
                    passages = retrieve(question, k=5)
                    context = "\n\n".join(f"[{i}] {p}" for i, p in enumerate(passages))
                    response = client.messages.create(
                        model="claude-opus-5",
                        max_tokens=512,
                        system=SYSTEM,
                        messages=[{"role": "user", "content": f"{context}\n\nQ: {question}"}],
                    )
                    text = next(b.text for b in response.content if b.type == "text").strip()
                    # Abstention is a first-class outcome. Return None and let the caller
                    # decide what to show -- do not fall back to an ungrounded answer.
                    return None if text == "INSUFFICIENT_EVIDENCE" else text

                # Report coverage alongside accuracy. A system that answers everything at
                # 60% is not comparable to one that answers 60% of questions at 96%.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the confidence signal fails — and the control that proves it",
            accentColor = 0xFFEC4899,
            code = """
                # Confidence = expected share of samples on the modal answer.
                # Accuracy   = probability that modal answer is right.
                # Both exact, both from the same (p, d) parameters.

                for q in questions:
                    print(f"conf={confidence(q):.3f} acc={accuracy(q):.3f} "
                          f"supported={q.supported}")
                # conf=0.940 acc=0.999 supported=True
                # ...
                # conf=0.746 acc=0.163 supported=False
                # conf=0.883 acc=0.014 supported=False   <- most confident, essentially never right

                print(min_supported_confidence(), max_unsupported_confidence())  # 0.712 0.883
                # The signal does not separate the groups. ECE = 0.225.

                print(best_threshold())            # tau=0.8 -> coverage 0.60, accuracy 0.668
                print(grounded_coverage(), grounded_selective_accuracy())  # 0.60  0.959
                # At identical coverage, evidence beats agreement 0.959 to 0.668.

                # The control: same questions, errors SCATTERED over 4 wrong answers
                # instead of always landing on the same one.
                print(best_threshold(scattered))   # tau=0.5 -> coverage 0.60, accuracy 0.959
                print(confidence_separates(scattered))                     # True
                # One property flipped, and the confidence signal works perfectly. It was
                # never measuring correctness -- only how firmly the model believes.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("check", 0xFF10B981, "Grounded Assistants", "Citations make a claim checkable; abstention makes a gap visible."),
        ApplicationCard("chart", 0xFF3B82F6, "Calibration Monitoring", "ECE over a labelled sample tells you whether your confidence signal is real."),
        ApplicationCard("lock", 0xFF8B5CF6, "High-Stakes Answers", "Coverage is a product decision — refusing is cheaper than being wrong."),
        ApplicationCard("help", 0xFFEC4899, "Confidence Theatre", "A displayed score that tracks conviction rather than truth is worse than none."),
    ),
    takeaways = listOf(
        "A hallucination is a confident reproducible falsehood, not a random error — and that difference decides which detector works.",
        "On this population confidence does not separate supported from unsupported: 0.71 min supported against 0.88 max unsupported, ECE 0.225.",
        "The best confidence threshold refuses 40% of questions to reach 0.668 accuracy; grounding reaches 0.959 at the same coverage.",
        "The control run names the mechanism: scatter the wrong answers and confidence separates cleanly and thresholding works.",
        "Self-consistency measures conviction, not correctness — useful against random slips, worthless against a firm false belief.",
    ),
    crossLinks = listOf(
        CrossLink("rag", "Retrieval-Augmented Generation"),
        CrossLink("chain_of_thought", "Chain of Thought"),
        CrossLink("react", "ReAct"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
