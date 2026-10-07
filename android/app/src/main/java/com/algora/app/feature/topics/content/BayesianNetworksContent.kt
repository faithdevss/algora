package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bayesianNetworksContent = TopicContent(
    topicId = "bayesian_networks",
    figure = Figure(
        caption = "The sprinkler network the page reasons about, with the textbook tables: " +
            "P(cloudy) = 0.5; the sprinkler runs with probability 0.1 if cloudy and 0.5 if not; " +
            "rain falls with 0.8 and 0.2; wet grass is 0.99 with both, 0.9 with either, 0 with " +
            "neither. Four binary variables need 15 numbers as a full joint table and 9 here — " +
            "every missing arrow is an independence claim paid for in parameters saved. Wet grass " +
            "is a collider, and observing it couples its two parents. Alone, wet grass raises " +
            "P(rain) from 0.500 to 0.708. Learn the sprinkler was on and it falls to 0.320 — the " +
            "sprinkler explains the water away — and learn it was off and rain becomes certain, " +
            "1.000, because nothing else wets the grass. Sprinkler and rain were only linked " +
            "through the cloud before; the observation below them created a second link — the " +
            "warning-toned line, which is a dependence induced by evidence, not an arrow of the " +
            "network.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("cloudy", 0.50f, 0.10f, FigureTone.Muted),
                FigureGraphNode("sprinkler", 0.15f, 0.50f, FigureTone.Primary),
                FigureGraphNode("rain", 0.85f, 0.50f, FigureTone.Primary),
                FigureGraphNode("wet", 0.50f, 0.90f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 1, directed = true),
                FigureEdge(0, 2, directed = true),
                FigureEdge(1, 3, directed = true),
                FigureEdge(2, 3, directed = true),
                FigureEdge(1, 2, "explaining away", tone = FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Bayesian network is a directed acyclic graph over random variables, where each node carries P(node | its parents). Naive Bayes assumes every feature independent given the class; a Bayesian network instead states exactly which dependencies exist and lets the rest be independent.",
        "The graph's real content is its missing edges. With n binary variables a full joint table needs 2ⁿ − 1 numbers; the network needs only Σ 2^(parents of i), which for a sparse graph is dramatically smaller. Every absent arrow is a conditional-independence claim — a substantive assertion about the world that is written down, inspectable and testable, rather than buried in an implicit assumption.",
        "Reading independence off the graph is subtler than it looks, and the collider is where intuition fails. Along a chain or a common cause, conditioning on the middle node *blocks* the path: once you know it was cloudy, the sprinkler tells you nothing further about rain. But at a collider — two arrows meeting at one node — conditioning *creates* dependence. Sprinkler and rain are only linked through cloudiness (they are independent given Cloudy) until you observe that the grass is wet, which adds a second, collider-created dependence; then learning the sprinkler was off makes rain much more likely. That is explaining away, and it is why d-separation has to account for arrow direction rather than mere connectivity. Exact inference by variable elimination is efficient on sparse graphs and NP-hard in general, which is precisely where MCMC and variational methods take over.",
    ),
    steps = listOf(
        StepCard(1, "Name the Variables", "Each becomes a node. Getting the variable set right is most of the modelling work.", 0xFF14B8A6),
        StepCard(2, "Draw the Dependencies", "An arrow from cause to effect. The graph must stay acyclic.", 0xFF818CF8),
        StepCard(3, "Attach a CPT per Node", "P(node | parents). A node with k binary parents needs 2^k rows.", 0xFF60A5FA),
        StepCard(4, "Read Off the Factorization", "The joint is the product of those tables — that is the whole saving.", 0xFF10B981),
        StepCard(5, "Apply d-Separation Carefully", "Chains and common causes block when observed; colliders open when observed.", 0xFFF59E0B),
        StepCard(6, "Choose an Inference Method", "Variable elimination while the graph is sparse; sampling or variational once it is not.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Factorization", "P(x₁..xₙ) = ∏ᵢ P(xᵢ | parents(xᵢ))", "The chain rule, pruned by the graph."),
        FormulaEntry("Full joint cost", "2ⁿ − 1 parameters", "What the network avoids."),
        FormulaEntry("Network cost", "Σᵢ 2^|parents(i)|", "Sparse graphs are exponentially cheaper."),
        FormulaEntry("Chain / common cause", "A → B → C, A ← B → C", "Observing B blocks the path."),
        FormulaEntry("Collider", "A → B ← C", "Observing B (or a descendant) OPENS the path."),
        FormulaEntry("Inference", "NP-hard in general", "Tractable on sparse graphs, not on dense ones."),
    ),
    notationKey = listOf(
        NotationEntry("DAG", "directed acyclic graph"),
        NotationEntry("CPT", "conditional probability table"),
        NotationEntry("d-separation", "the graphical test for conditional independence"),
        NotationEntry("collider", "a node with two incoming arrows"),
        NotationEntry("explaining away", "conditioning on a collider making its causes dependent"),
        NotationEntry("Markov blanket", "parents, children and children's other parents — all a node depends on"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The sprinkler network, and the parameter saving",
            accentColor = 0xFF14B8A6,
            code = """
                from pgmpy.models import DiscreteBayesianNetwork
                from pgmpy.factors.discrete import TabularCPD
                from pgmpy.inference import VariableElimination

                model = DiscreteBayesianNetwork([
                    ("Cloudy", "Sprinkler"), ("Cloudy", "Rain"),
                    ("Sprinkler", "WetGrass"), ("Rain", "WetGrass"),
                ])
                model.add_cpds(
                    TabularCPD("Cloudy", 2, [[0.5], [0.5]]),
                    TabularCPD("Sprinkler", 2, [[0.5, 0.9], [0.5, 0.1]],
                               evidence=["Cloudy"], evidence_card=[2]),
                    TabularCPD("Rain", 2, [[0.8, 0.2], [0.2, 0.8]],
                               evidence=["Cloudy"], evidence_card=[2]),
                    TabularCPD("WetGrass", 2,
                               [[1.0, 0.1, 0.1, 0.01], [0.0, 0.9, 0.9, 0.99]],
                               evidence=["Sprinkler", "Rain"], evidence_card=[2, 2]),
                )
                # 1 + 2 + 2 + 4 = 9 parameters, against 2**4 - 1 = 15 for the full joint.
                assert model.check_model()
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Explaining away, measured",
            accentColor = 0xFFEC4899,
            code = """
                infer = VariableElimination(model)

                # Marginally, Sprinkler and Rain are only weakly related (through Cloudy).
                base = infer.query(["Rain"]).values[1]

                # Observe the grass is wet: rain becomes more likely.
                wet = infer.query(["Rain"], evidence={"WetGrass": 1}).values[1]

                # Now also learn the sprinkler was ON. It already explains the wet grass,
                # so the evidence for rain is withdrawn — P(Rain) FALLS back.
                wet_and_sprinkler = infer.query(
                    ["Rain"], evidence={"WetGrass": 1, "Sprinkler": 1}).values[1]

                print(round(base, 3), round(wet, 3), round(wet_and_sprinkler, 3))
                # Learning about the sprinkler changed our belief about rain — two variables
                # that are independent given Cloudy until their shared consequence is observed. No naive
                # Bayes model can represent this.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF14B8A6, "Medical Diagnosis", "Symptoms, diseases and risk factors, where explaining away is exactly how a clinician reasons."),
        ApplicationCard("chip", 0xFF818CF8, "Fault Diagnosis", "Microsoft's printer troubleshooter shipped one of these to millions of desktops."),
        ApplicationCard("chart", 0xFF10B981, "Causal Inference", "Pearl's do-calculus is built on this representation — the arrows are what make interventions expressible."),
    ),
    takeaways = listOf(
        "A DAG plus one conditional table per node factorizes the joint; the missing edges are the content.",
        "Sparse graphs need Σ 2^|parents| parameters instead of 2ⁿ − 1.",
        "Conditioning blocks chains and common causes but *opens* colliders — that is explaining away.",
        "Exact inference is efficient while the graph stays sparse and NP-hard in general.",
    ),
    crossLinks = listOf(
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("mcmc", "Markov Chain Monte Carlo (MCMC)"),
        CrossLink("graph", "Graph (DSA)"),
        CrossLink("topological_sort", "Topological Sort (DSA)"),
    ),
)
