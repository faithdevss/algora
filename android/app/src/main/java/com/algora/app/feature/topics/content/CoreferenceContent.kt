package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val coreferenceContent = TopicContent(
    topicId = "coreference",
    whatIsIt = listOf(
        "Coreference resolution decides which mentions in a text point at the same entity: \"Ada Lovelace … she … her\" is one chain, \"Charles Babbage … his\" is another. It runs in two parts — find the mentions (every noun phrase and pronoun is a candidate, including the ones that refer to nothing) and then partition them into chains. Everything that consumes text as a whole document rather than a sentence at a time depends on it: summarisation that says \"she\" without an antecedent is broken, and a knowledge-base extractor that treats \"the company\" and \"Acme\" as two entities has doubled its facts.",
        "Cheap agreement features get you further than expected. A singular feminine pronoun cannot refer to \"Charles Babbage\" or to \"London\", so number, gender and animacy filtering resolves the lab's easy document with no learning at all. But there is an evaluation subtlety that catches people: a mention-pair model links each mention to its nearest compatible antecedent, and that antecedent is frequently another pronoun — \"her\" resolves to \"She\", not to \"Ada Lovelace\". Scored on immediate links the lab's resolver gets 67%; scored on the entity each chain resolves to after transitive closure, which is what the task actually asks for, it gets 100%. Reporting the first number is a real mistake, not a hypothetical one.",
        "Then there are the sentences agreement cannot touch. \"The city council refused the demonstrators a permit because they feared violence\" — \"they\" is the council. Change one word to \"advocated\" and it becomes the demonstrators. Nothing syntactic differs, so any recency or salience heuristic answers the same for both and scores exactly 50% on the pair: the score of guessing. That is what Winograd schemas are designed to measure, and why the task resisted feature engineering for decades — it needs world knowledge about who fears and who advocates. Large language models finally pushed accuracy past 90% on these, and they did it with that knowledge rather than a better syntactic feature. Cost matters too: mention-pair scoring is quadratic in mentions (15 pairs for the lab's 6), and the standard metric is the average of MUC, B³ and CEAF, because each one alone can be gamed by over- or under-merging chains.",
    ),
    steps = listOf(
        StepCard(1, "Detect Mentions", "Every NP and pronoun is a candidate; over-generate, then filter.", 0xFFF59E0B),
        StepCard(2, "Filter by Agreement", "Number, gender and animacy remove most candidates for free.", 0xFFEAB308),
        StepCard(3, "Score Candidate Pairs", "Distance, syntactic role, string match, semantic compatibility — O(m²) pairs.", 0xFF06B6D4),
        StepCard(4, "Link and Close", "Take the best antecedent per mention, then the transitive closure into chains.", 0xFF3B82F6),
        StepCard(5, "Evaluate on Chains", "MUC, B³ and CEAF averaged — not on immediate links.", 0xFF6366F1),
        StepCard(6, "Check the Hard Cases", "Winograd-style pairs, where every syntactic heuristic scores 50%.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Candidate pairs", "m(m−1)/2 = 15 for 6 mentions", "Quadratic in document length — the reason for mention ranking and pruning."),
        FormulaEntry("Pair vs chain scoring", "67% on immediate links, 100% on chain entities", "Measured on the lab's document; \"her\" → \"She\" is a correct link and a wrong entity."),
        FormulaEntry("Winograd baseline", "recency = 50% on the pair", "One word flips the gold answer; nothing syntactic changes."),
        FormulaEntry("CoNLL F1", "mean of MUC, B³ and CEAF", "Each metric alone rewards a different degenerate strategy."),
        FormulaEntry("MUC", "link-based recall/precision over chain edges", "Blind to singleton entities, which B³ fixes."),
        FormulaEntry("Agreement filter", "number · gender · animacy", "Free, and sufficient for the easy majority of pronouns."),
    ),
    notationKey = listOf(
        NotationEntry("mention", "a span that could refer to an entity — a name, a noun phrase or a pronoun"),
        NotationEntry("antecedent", "the earlier mention a later one refers back to"),
        NotationEntry("anaphora / cataphora", "reference backwards / forwards in the text"),
        NotationEntry("chain (cluster)", "all mentions of one entity — the transitive closure of the links"),
        NotationEntry("singleton", "a mention in a chain of one; MUC cannot see them, B³ can"),
        NotationEntry("Winograd schema", "a sentence pair where one word flips the answer and syntax cannot help"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Agreement filtering, and the closure that follows it",
            accentColor = 0xFFF59E0B,
            code = """
                mentions = [("Ada Lovelace","sg","fem",True), ("Charles Babbage","sg","masc",True),
                            ("London","sg","neuter",False), ("She","sg","fem",True),
                            ("his","sg","masc",True), ("her","sg","fem",True)]
                PRONOUNS = {"she","he","her","his","him","they","them","it"}

                def antecedent(i):
                    _, num, gen, anim = mentions[i]
                    prior = [m for m in mentions[:i] if (m[1], m[2], m[3]) == (num, gen, anim)]
                    return prior[-1][0] if prior else None

                print(antecedent(5))          # 'She'  <- a correct LINK, and not an entity

                def entity(i):                # follow links until a non-pronoun
                    cur = antecedent(i)
                    while cur and cur.lower() in PRONOUNS:
                        cur = antecedent([m[0] for m in mentions].index(cur))
                    return cur

                print(entity(5))              # 'Ada Lovelace'
                # 67% correct scored on links, 100% scored on entities. Score the chains.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The case features cannot reach",
            accentColor = 0xFFEC4899,
            code = """
                a = "The city council refused the demonstrators a permit because they feared violence."
                b = "The city council refused the demonstrators a permit because they advocated violence."
                #  gold: a -> the city council      b -> the demonstrators

                # Recency answers "the demonstrators" for both -> 1 of 2 correct.
                # Syntactic parallelism answers "the city council" for both -> also 1 of 2.
                # Any function of the syntax alone is at 50% here BY CONSTRUCTION: the two sentences
                # have identical parses.

                import spacy, coreferee                  # a modern pipeline component
                nlp = spacy.load("en_core_web_trf")
                nlp.add_pipe("coreferee")
                print(nlp(a)._.coref_chains)

                # Evaluate with the CoNLL scorer, which averages MUC, B-cubed and CEAF -- and read
                # the three separately when they disagree: a system that merges everything into one
                # chain scores well on MUC recall alone, and B-cubed is what catches it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFF59E0B, "Summarisation", "A summary containing an unresolved \"she\" is unusable; coreference is what substitutes the name."),
        ApplicationCard("search", 0xFF3B82F6, "Knowledge Base Population", "Merging \"Acme\", \"the company\" and \"it\" into one entity before facts are stored."),
        ApplicationCard("users", 0xFF8B5CF6, "Dialogue Systems", "Resolving \"that one\", \"the second\" and \"it\" against the conversation state."),
        ApplicationCard("flask", 0xFF14B8A6, "Reasoning Benchmarks", "Winograd schemas became a general test of commonsense, not just of coreference."),
    ),
    takeaways = listOf(
        "Two steps: detect mentions, then partition them into chains — one chain per entity.",
        "Number, gender and animacy agreement resolves the easy majority with no model at all.",
        "Score chains, not links: the lab's resolver is 67% on immediate antecedents and 100% on the entities they close to.",
        "Winograd pairs pin every syntactic heuristic to 50% — one word flips the answer and the parse is identical.",
        "Mention-pair scoring is O(m²), and CoNLL F1 averages MUC, B³ and CEAF because each alone can be gamed.",
    ),
    crossLinks = listOf(
        CrossLink("ner", "Named Entity Recognition"),
        CrossLink("dependency_parsing", "Dependency Parsing"),
        CrossLink("attention", "Attention"),
        CrossLink("llms", "LLMs"),
    ),
)
