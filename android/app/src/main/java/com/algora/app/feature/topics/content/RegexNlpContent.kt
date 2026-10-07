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

internal val regexNlpContent = TopicContent(
    topicId = "regex_nlp",
    figure = Figure(
        caption = "The page's lab sentence, tokenised three ways, counting how many pieces each " +
            "tricky unit is cut into. \\w+ — the pattern everyone writes first — finds 21 tokens " +
            "where a linguist counts 12, because every apostrophe, hyphen, dot and @ becomes a " +
            "split point: the e-mail address falls into 4 pieces and the date into 3. Allowing inner " +
            "punctuation when a word character follows, \\w+(?:['@.\\-]\\w+)*, holds most units " +
            "together and gets 12 — but it still drops the final dot of U.S. and the % of 3.5%. " +
            "Adding an abbreviation alternative and an optional % keeps all six whole. Each fix is " +
            "a rule about one kind of unit; the patterns grow with the corner cases, which is why " +
            "regex tokenisers are where real tokenisers start, not where they end.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2", "1", "1"),
                listOf("2", "1", "1"),
                listOf("4", "1", "1"),
                listOf("2", "1 (no dot)", "1"),
                listOf("2", "1 (no %)", "1"),
                listOf("3", "1", "1"),
            ),
            rowHeaders = listOf("Smith's", "e-mail", "a.smith@x.co", "U.S.", "3.5%", "2024-01-05"),
            colHeaders = listOf("\\w+", "+ inner punct.", "+ abbrev, %"),
            marks = listOf(
                FigureCell(2, 0, FigureTone.Warn),
                FigureCell(3, 1, FigureTone.Warn),
                FigureCell(4, 1, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A regular expression is a pattern describing a set of strings, and in NLP it is the layer everything else stands on: word tokenizers, sentence splitters, date and money extractors, cleaning rules and the fallback rules inside every industrial NER system are regexes. The reason is that they are declarative, auditable and need no training data — for a well-defined surface form like an ISO date or a VAT number, a pattern beats a model on precision, cost and explainability, and always will.",
        "The craft is in what the pattern does to the text you did not think about. \\w+ is what everyone writes first, and on one ordinary sentence it finds 21 tokens where a linguist would count 12: \\w matches letters, digits and underscore and nothing else, so every internal dot, hyphen and apostrophe is a boundary. \"a.smith@x.co\" becomes four tokens, none of which is an e-mail address; \"3.5%\" becomes 3 and 5; \"U.S.\" becomes U and S. Fixing it means an alternation of specific branches — e-mail, ISO date, dotted abbreviation, hyphenated word, decimal with percent — before the general one, because alternation is first-match, not longest-match. Moving the general branch to the front is a one-line edit that takes the same six branches from 12 tokens to 16, and the damage is not uniform: the branches that begin with a digit still fire, while the e-mail is left as \".smith@x.co\" after the letters branch has eaten its first character — a token that looks like the pattern worked.",
        "The second thing to know is how they fail in production. A backtracking engine — PCRE, Python's re, Java's, JavaScript's — explores alternatives on failure, and a nested quantifier makes that search exponential: (a+)+$ against a run of a's followed by anything else must try 2^(n−1) ways of splitting the run before reporting failure. At n = 24 that is 8,388,608 attempts for a 25-character input, which is the ReDoS class of denial-of-service. The fixes are structural: remove the nesting, make the group atomic or possessive, or use a finite-automaton engine (RE2, Rust's regex, Go's regexp) that cannot express the shape at all and runs in O(n·m) guaranteed.",
    ),
    steps = listOf(
        StepCard(1, "State the Token Shapes", "List what must survive whole: e-mails, dates, decimals, abbreviations, hyphenated words, clitics.", 0xFF14B8A6),
        StepCard(2, "Write Specific Branches First", "Alternation takes the first branch that matches, so the longest, most specific goes first.", 0xFF06B6D4),
        StepCard(3, "Anchor and Bound", "\\b, ^, $ and explicit {2,4} bounds keep a pattern from matching across the boundaries you meant.", 0xFF3B82F6),
        StepCard(4, "Run It On Real Text", "Count the tokens. Every difference between your count and a hand count is a rule you have not written.", 0xFF6366F1),
        StepCard(5, "Audit for Nested Quantifiers", "(a+)+, (a|a)*, (\\s+)+ — any quantifier containing a quantifier over the same characters is a ReDoS.", 0xFF8B5CF6),
        StepCard(6, "Know When to Stop", "Nested structure, unbounded ambiguity and semantics are not regular. Reach for a parser or a model.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Naive vs tuned", "\\w+ → 21 tokens; tuned alternation → 12", "Measured on the lab's sentence; six tokens are rescued whole."),
        FormulaEntry("Branch order", "first-match, not longest-match", "Moving the letters branch to the front of the same six takes 12 tokens to 16, and leaves \".smith@x.co\"."),
        FormulaEntry("Backtracking blow-up", "(a+)+$ on aⁿ → 2^(n−1) attempts", "n=8 → 128, n=16 → 32,768, n=24 → 8,388,608, walked explicitly in the lab."),
        FormulaEntry("Safe rewrites", "a+$  ·  (?>a+)+$  ·  a++$", "Removing the nesting, atomic group, possessive quantifier — all linear in n."),
        FormulaEntry("Automaton engines", "O(n · m) guaranteed", "RE2/Rust/Go simulate an NFA and refuse backreferences, so ReDoS is impossible by construction."),
        FormulaEntry("Chomsky bound", "regular ⊂ context-free", "Balanced brackets and nested clauses are provably not matchable by any regex."),
    ),
    notationKey = listOf(
        NotationEntry("\\w vs \\p{L}", "\\w is ASCII-only in some engines (and with re.ASCII) but Unicode-aware by default in Python 3 str patterns; \\p{L} is any Unicode letter, which is what NLP wants"),
        NotationEntry("greedy / lazy", "a+ takes as much as it can and gives back; a+? takes as little as possible"),
        NotationEntry("possessive / atomic", "a++ and (?>a+) take as much as they can and never give back — the ReDoS fix"),
        NotationEntry("lookahead", "(?=…) / (?!…) — a zero-width assertion, used to split without consuming"),
        NotationEntry("catastrophic backtracking", "exponential retry on failure caused by nested or overlapping quantifiers"),
        NotationEntry("ReDoS", "denial of service by feeding a crafted string to a vulnerable pattern"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A tokenizer that survives real text",
            accentColor = 0xFF14B8A6,
            code = """
                import re

                text = "Dr. Smith's e-mail is a.smith@x.co, the U.S. GDP rose 3.5% on 2024-01-05."

                print(re.findall(r"\w+", text))          # 21 tokens -- and no e-mail among them

                TOKEN = re.compile(
                    r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"   # e-mail FIRST or it is eaten
                    r"|\d{4}-\d{2}-\d{2}"                                # ISO date
                    r"|(?:[A-Za-z]\.){2,}"                               # U.S., e.g.
                    r"|[A-Za-z]+(?:[-'][A-Za-z]+)+"                      # e-mail, Smith's
                    r"|\d+(?:\.\d+)?%?"                                  # 3.5%, 2024
                    r"|[A-Za-z]+"                                        # everything else
                )
                print(TOKEN.findall(text))               # 12 tokens, all of them things
                # ['Dr', "Smith's", 'e-mail', 'is', 'a.smith@x.co', 'the', 'U.S.', 'GDP',
                #  'rose', '3.5%', 'on', '2024-01-05']

                # Move the last branch to the front and you get 16 tokens (including '.smith@x.co'): alternation returns the
                # FIRST branch that matches at a position, not the longest.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The pattern that takes the service down",
            accentColor = 0xFFEC4899,
            code = """
                import re, time

                bad = re.compile(r"(a+)+$")        # nested quantifier over the same character
                s = "a" * 24 + "!"                  # 25 characters

                t = time.time()
                bad.search(s)                       # tries 2**23 = 8,388,608 splits before failing
                print(f"{time.time() - t:.1f}s")

                # Three structural fixes -- none of them is "add a timeout":
                re.compile(r"a+$")                  # no nesting at all
                re.compile(r"(?>a+)+$")             # atomic group (Python 3.11+, PCRE, Java)
                re.compile(r"a++$")                 # possessive quantifier

                # Or change engine. Google's RE2, Rust's regex crate and Go's regexp simulate an
                # automaton: linear time guaranteed, no backreferences, no lookaround, no ReDoS.
                # If a pattern comes from user input, this is the only safe answer.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF14B8A6, "Tokenizers & Sentence Splitters", "spaCy's rules, NLTK's TreebankWordTokenizer and every log parser are regex at heart."),
        ApplicationCard("search", 0xFF3B82F6, "Entity Extraction", "Dates, money, phone numbers, IDs — a pattern beats a model on precision and needs no labels."),
        ApplicationCard("lock", 0xFF8B5CF6, "Input Validation", "Also the most common place a ReDoS lands, because the pattern meets attacker-controlled text."),
        ApplicationCard("help", 0xFFEC4899, "Where It Stops", "Nested structure and meaning are not regular — that is where PCFGs and taggers start."),
    ),
    takeaways = listOf(
        "\\w+ finds 21 tokens where a linguist counts 12: internal dots, hyphens and apostrophes are all boundaries to it.",
        "Alternation is first-match, so specific branches go before general ones — reordering them silently undoes the pattern.",
        "(a+)+$ needs 2^(n−1) attempts to reject aⁿ — 8.4 million for a 25-character string.",
        "Fix it structurally: drop the nesting, use an atomic/possessive group, or switch to RE2-style linear-time engines.",
        "Regexes cannot match nested structure at all; that boundary is exactly where parsing begins.",
    ),
    crossLinks = listOf(
        CrossLink("tokenization", "Tokenization"),
        CrossLink("text_cleaning", "Lowercasing & Cleaning"),
        CrossLink("kmp", "KMP String Matching"),
        CrossLink("ner", "Named Entity Recognition"),
    ),
)
