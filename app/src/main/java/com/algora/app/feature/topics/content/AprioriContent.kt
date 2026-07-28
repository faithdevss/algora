package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val aprioriContent = TopicContent(
    topicId = "apriori",
    whatIsIt = listOf(
        "Association rule mining asks which things co-occur in a collection of sets — baskets in a shop, pages in a session, symptoms in a chart. The obstacle is arithmetic: with d items there are 2ᵈ possible itemsets, so a hundred products give more subsets than there are atoms in the observable universe. No amount of engineering scans that space.",
        "Apriori's contribution is a single observation that makes the space searchable. If an itemset is infrequent, every superset of it is infrequent too — you cannot appear in more baskets by demanding more things. So the search can proceed level by level, and any candidate with an infrequent subset is discarded before a single basket is read. That is the Apriori property, sometimes called downward closure, and it is the whole algorithm; everything else is bookkeeping.",
        "The cost is that it needs one pass over the database per level, and it generates candidates it then has to count. On a database with long frequent itemsets this becomes painful, which is exactly what Eclat and FP-Growth are responses to. It also produces rules, not just itemsets — and that step brings its own trap, because the obvious rule score, confidence, is blind to how common the consequent is. The simulation shows a rule with 75% confidence whose lift is 0.83: the antecedent makes the consequent *less* likely, and confidence alone reports it as a strong finding.",
    ),
    steps = listOf(
        StepCard(1, "Count Single Items", "One pass. Everything below minimum support is dropped, and it can never come back.", 0xFF06B6D4),
        StepCard(2, "Join to Make Candidates", "Combine frequent (k−1)-itemsets that share their first k−2 items.", 0xFF22D3EE),
        StepCard(3, "Prune by Downward Closure", "Discard any candidate with an infrequent (k−1)-subset — no database access needed.", 0xFF8B5CF6),
        StepCard(4, "Count What Survived", "One more pass. This is the expensive step, which is why step 3 matters.", 0xFF6366F1),
        StepCard(5, "Repeat Until Empty", "Stop when no candidates survive. The frequent itemsets are everything kept along the way.", 0xFF10B981),
        StepCard(6, "Generate and Score Rules", "Split each frequent itemset every way; score with confidence *and* lift, never confidence alone.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Support", "supp(X) = |{T : X ⊆ T}| / |D|", "The fraction of baskets containing all of X."),
        FormulaEntry("Apriori property", "X ⊆ Y ⟹ supp(Y) ≤ supp(X)", "Downward closure. The one fact the pruning rests on."),
        FormulaEntry("Confidence", "conf(X→Y) = supp(X∪Y) / supp(X)", "P(Y | X). Says nothing about how common Y is."),
        FormulaEntry("Lift", "lift(X→Y) = conf(X→Y) / supp(Y)", "1 means independent, below 1 means negatively associated."),
        FormulaEntry("Leverage", "supp(X∪Y) − supp(X)supp(Y)", "The same idea on an additive rather than a ratio scale."),
        FormulaEntry("Conviction", "(1 − supp(Y)) / (1 − conf(X→Y))", "Sensitive to the direction of the rule, unlike lift."),
        FormulaEntry("Search space", "2ᵈ itemsets over d items", "The number the whole method exists to avoid enumerating."),
    ),
    notationKey = listOf(
        NotationEntry("D", "the database — a collection of transactions"),
        NotationEntry("T", "one transaction: a set of items"),
        NotationEntry("Lₖ", "the frequent k-itemsets"),
        NotationEntry("Cₖ", "the candidate k-itemsets, before counting"),
        NotationEntry("minsup", "minimum support threshold — the only knob that matters"),
        NotationEntry("downward closure", "a superset of an infrequent set is infrequent"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Apriori, with the pruning step visible",
            accentColor = 0xFF06B6D4,
            code = """
                from itertools import combinations

                def apriori(transactions, minsup):
                    n = len(transactions)
                    items = {i for t in transactions for i in t}
                    frequent, level = {}, {
                        frozenset([i]) for i in items
                        if sum(i in t for t in transactions) / n >= minsup
                    }

                    k = 1
                    while level:
                        for s in level:
                            frequent[s] = sum(s <= t for t in transactions) / n
                        k += 1

                        # Join, then prune. The prune is what makes this tractable: a candidate
                        # with any infrequent (k-1)-subset cannot be frequent, and rejecting it
                        # here costs nothing while counting it would cost a database pass.
                        candidates = {a | b for a in level for b in level if len(a | b) == k}
                        candidates = {
                            c for c in candidates
                            if all(frozenset(s) in level for s in combinations(c, k - 1))
                        }
                        level = {
                            c for c in candidates
                            if sum(c <= t for t in transactions) / n >= minsup
                        }
                    return frequent
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Rules — and why confidence on its own will embarrass you",
            accentColor = 0xFFF59E0B,
            code = """
                import pandas as pd
                from mlxtend.frequent_patterns import apriori, association_rules
                from mlxtend.preprocessing import TransactionEncoder

                baskets = [["bread", "milk", "eggs"], ["bread", "milk"], ["milk", "cereal"],
                           ["bread", "milk", "eggs"], ["milk", "eggs"], ["milk", "cereal"]]

                te = TransactionEncoder()
                df = pd.DataFrame(te.fit(baskets).transform(baskets), columns=te.columns_)

                sets = apriori(df, min_support=0.3, use_colnames=True)
                rules = association_rules(sets, metric="lift", min_threshold=0.0)

                # Sort by confidence and the top rules are the ones whose consequent is simply
                # common. Milk is in every basket here, so "anything -> milk" has high confidence
                # and lift at or below 1: it predicts nothing you did not already know.
                print(rules.sort_values("confidence", ascending=False)[
                    ["antecedents", "consequents", "support", "confidence", "lift"]
                ].head())

                # Filter on lift, not confidence. A rule with lift <= 1 is not a finding.
                print(rules[(rules.lift > 1.2) & (rules.support > 0.3)])
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF06B6D4, "Market Basket Analysis", "Store layout, bundling and promotions. The origin of the method and still where most of it runs."),
        ApplicationCard("flask", 0xFF8B5CF6, "Adverse Drug Reactions", "Co-occurring drug and symptom codes in clinical records — a setting where lift matters far more than confidence."),
        ApplicationCard("globe", 0xFF6366F1, "Web Usage Mining", "Page sets visited in one session, used for navigation redesign and prefetching."),
        ApplicationCard("search", 0xFF10B981, "Query Log Analysis", "Terms that co-occur across sessions, for query expansion and related-search suggestions."),
    ),
    takeaways = listOf(
        "The search space is 2ᵈ; downward closure is what makes it navigable at all.",
        "Pruning happens before counting — that is where the savings are.",
        "Cost is one database pass per level, which is the weakness on long itemsets.",
        "Support and confidence are not enough: lift below 1 means negatively associated.",
        "Minimum support is the only real parameter, and it changes the output dramatically.",
    ),
    crossLinks = listOf(
        CrossLink("eclat", "Eclat Algorithm"),
        CrossLink("fp_growth", "FP-Growth Algorithm"),
        CrossLink("subsets_bitmask", "Subsets using Bitmask (DSA)"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
