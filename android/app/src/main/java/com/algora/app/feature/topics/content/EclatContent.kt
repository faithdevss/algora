package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val eclatContent = TopicContent(
    topicId = "eclat",
    whatIsIt = listOf(
        "Eclat finds exactly the frequent itemsets Apriori finds. What changes is the data layout, and the algorithm follows from it. Apriori stores the database horizontally — basket 1 holds these items, basket 2 holds those — so counting an itemset means reading every basket. Eclat stores it vertically: for each item, the set of transaction ids containing it, its tid-list.",
        "In that layout support stops being something you count and becomes something you already have. The support of an itemset is the length of its tid-list, and the tid-list of X ∪ Y is the intersection of the tid-lists of X and Y. So after one initial pass to build the lists, the database is never read again — the search is pure set intersection, done depth-first, extending one prefix as far as it will go before backtracking.",
        "The trade is memory for scans, and it is not always a good trade. A tid-list for a common item is nearly the whole database, and intersecting two long lists is expensive in both time and space; on dense data with low minimum support the lists can dwarf the original table. Eclat wins on sparse data with a high support threshold, where the lists are short and the depth-first order means only one path's worth of them is live at a time. The standard implementation trick, diffsets, stores the *difference* between a prefix's tid-list and its child's rather than the child's list itself — on dense data that is dramatically smaller, and it is what makes the dEclat variant practical.",
    ),
    steps = listOf(
        StepCard(1, "Transpose the Database", "One pass to build a tid-list per item. The last time the database is read.", 0xFF06B6D4),
        StepCard(2, "Drop Infrequent Items", "Any tid-list shorter than minsup goes, along with everything it could have extended.", 0xFF22D3EE),
        StepCard(3, "Order by Support", "Process rarer items first so intersections shrink quickly. Ordering is a real optimisation here.", 0xFF8B5CF6),
        StepCard(4, "Intersect to Extend", "t(X ∪ {y}) = t(X) ∩ t(y). Support is the result's length — no counting.", 0xFF6366F1),
        StepCard(5, "Recurse Depth-First", "Follow one prefix down as far as it stays frequent, then backtrack. Only one branch is in memory.", 0xFF10B981),
        StepCard(6, "Consider Diffsets", "On dense data, store the difference from the parent instead of the list. That is dEclat.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Tid-list", "t(X) = {i : X ⊆ Tᵢ}", "The transaction ids containing X."),
        FormulaEntry("Support", "supp(X) = |t(X)|", "A length, not a count — nothing is scanned."),
        FormulaEntry("Extension", "t(X ∪ Y) = t(X) ∩ t(Y)", "The single operation the whole search is made of."),
        FormulaEntry("Diffset", "d(Xy) = t(X) \\ t(Xy)", "What dEclat stores instead."),
        FormulaEntry("Support from a diffset", "supp(Xy) = supp(X) − |d(Xy)|", "Recovered without ever materialising the tid-list."),
        FormulaEntry("Memory", "O(|D| · |I|) worst case", "Every item's list, and a common item's list is nearly the database."),
    ),
    notationKey = listOf(
        NotationEntry("t(X)", "tid-list — the set of transaction ids containing X"),
        NotationEntry("vertical layout", "item → transactions, the transpose of the usual table"),
        NotationEntry("diffset", "the tid-list difference between a prefix and its extension"),
        NotationEntry("dEclat", "the diffset variant, for dense data"),
        NotationEntry("|I|", "number of distinct items"),
        NotationEntry("depth-first", "extend one prefix fully before starting the next"),
        NotationEntry("prefix", "the itemset currently being extended"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Eclat, in about fifteen lines",
            accentColor = 0xFF06B6D4,
            code = """
                def eclat(transactions, minsup_count):
                    # One pass: transpose into tid-lists. The database is not read again.
                    tid_lists = {}
                    for i, t in enumerate(transactions):
                        for item in t:
                            tid_lists.setdefault(item, set()).add(i)

                    items = sorted(
                        (k for k, v in tid_lists.items() if len(v) >= minsup_count),
                        key=lambda k: len(tid_lists[k]),
                    )

                    found = {}

                    def extend(prefix, tids, rest):
                        for idx, item in enumerate(rest):
                            merged = tids & tid_lists[item]        # support IS the intersection
                            if len(merged) >= minsup_count:
                                new_prefix = prefix | {item}
                                found[frozenset(new_prefix)] = len(merged)
                                extend(new_prefix, merged, rest[idx + 1:])

                    extend(set(), set(range(len(transactions))), items)
                    return found
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Diffsets — the change that makes it work on dense data",
            accentColor = 0xFF10B981,
            code = """
                def declat(transactions, minsup_count):
                    tid_lists = {}
                    for i, t in enumerate(transactions):
                        for item in t:
                            tid_lists.setdefault(item, set()).add(i)

                    items = [k for k, v in tid_lists.items() if len(v) >= minsup_count]
                    found = {}

                    def extend(prefix, members):
                        # members: (item, support, diffset), every diffset taken relative
                        # to `prefix` -- siblings must share the same parent to subtract.
                        for idx, (item, support, d_item) in enumerate(members):
                            new_prefix = prefix | {item}
                            found[frozenset(new_prefix)] = support
                            children = []
                            for other, _, d_other in members[idx + 1:]:
                                d = d_other - d_item          # d(PXY) = d(PY) - d(PX)
                                s = support - len(d)          # sup(PXY) = sup(PX) - |d(PXY)|
                                if s >= minsup_count:
                                    children.append((other, s, d))
                            extend(new_prefix, children)

                    everyone = set(range(len(transactions)))
                    roots = [(k, len(tid_lists[k]), everyone - tid_lists[k]) for k in items]
                    extend(set(), roots)
                    return found

                # On dense data a tid-list is nearly the whole database and a diffset is nearly
                # empty, so this is the difference between a method that runs and one that does not.
                # On sparse data it is the other way round -- measure, do not assume.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF06B6D4, "High-Threshold Basket Mining", "Where support is set high and tid-lists stay short, Eclat is usually the fastest of the three."),
        ApplicationCard("chip", 0xFF8B5CF6, "Bitset Implementations", "Tid-lists as bitmaps make the intersection a hardware AND, which is why this layout survives in production miners."),
        ApplicationCard("flask", 0xFF6366F1, "Gene Expression Patterns", "Dense binary matrices — exactly the case diffsets were introduced for."),
    ),
    takeaways = listOf(
        "Same answer as Apriori; different data layout, and the algorithm follows from the layout.",
        "Support is the length of a tid-list, so extension is intersection and nothing is counted.",
        "One database pass total, then everything happens in memory.",
        "Depth-first search means only one branch's tid-lists are live at a time.",
        "Memory is the risk on dense data; diffsets (dEclat) are the standard answer.",
    ),
    crossLinks = listOf(
        CrossLink("apriori", "Apriori Algorithm"),
        CrossLink("fp_growth", "FP-Growth Algorithm"),
        CrossLink("set_adt", "Set ADT (DSA)"),
        CrossLink("bit_basics", "Bit Manipulation Basics (DSA)"),
    ),
)
