package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val textCleaningContent = TopicContent(
    topicId = "text_cleaning",
    whatIsIt = listOf(
        "Cleaning is everything done to a string before it is split into tokens: Unicode normalisation, case folding, URL and number placeholders, punctuation handling, accent folding and whitespace collapse. Each stage exists to merge spellings that mean the same thing, because a model that counts words treats \"Apple's\", \"Apple's\" (curly apostrophe) and \"apple\" as three unrelated types with a third of the evidence each. On the lab's four-document corpus the pipeline takes the vocabulary from 35 types to 29.",
        "The order is not arbitrary. Unicode normalisation comes first, before anything compares or measures a string, because NFKC is what makes the curly apostrophe and the ASCII one the same character. Placeholder substitution (URLs, numbers, e-mails) comes before punctuation stripping, or the URL is shredded into fragments that will never be seen again. Whitespace collapse comes last, so splitting on spaces is safe from that point on. Get the order wrong and later stages operate on text earlier ones were supposed to have fixed.",
        "Every stage is also a loss, and lowercasing is the clearest case: in the lab's corpus it merges \"Apple's\" with \"apple\" — useful, two types become one — and in the same pass merges \"US\" the country with \"us\" the pronoun. Truecasing (lowercase only sentence-initial words, or restore case with a model) is the principled fix and is almost never applied. The rule that survives contact with real pipelines: clean for the model you are feeding. Count-based models want aggressive folding; subword-tokenized transformers want the raw string, because BERT-cased and its successors learn that \"US\" and \"us\" are different tokens and use the difference.",
    ),
    steps = listOf(
        StepCard(1, "Normalise Unicode", "NFKC first, before any comparison — it folds curly quotes, ligatures and full-width forms onto canonical characters.", 0xFF14B8A6),
        StepCard(2, "Replace, Don't Delete", "URLs, e-mails, handles and numbers become placeholders; the fact that one was there is signal.", 0xFF06B6D4),
        StepCard(3, "Decide on Case", "Lowercase for count models; keep case for NER, acronyms and anything transformer-based.", 0xFF3B82F6),
        StepCard(4, "Handle Punctuation", "Stripping is cheap and lossy — it splits clitics and decimals. A regex tokenizer is the better tool.", 0xFF6366F1),
        StepCard(5, "Fold Accents Carefully", "café → cafe merges spellings users type; in French, Spanish or Vietnamese it destroys words.", 0xFF8B5CF6),
        StepCard(6, "Collapse Whitespace Last", "Once runs of space are single spaces, splitting is safe — and the vocabulary can be measured.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Vocabulary reduction", "35 types → 29 types", "Measured across the lab's four documents, eight stages."),
        FormulaEntry("Where the drops happen", "emoji −1, lowercase −2, punctuation −1, digits −2", "Per-stage, so a stage that pays for itself is distinguishable from one that does not."),
        FormulaEntry("The merge that pays", "{Apple's, apple} → {apple}", "2 types → 1, doubling the evidence for the same word."),
        FormulaEntry("The merge that costs", "{US, us} → {us}", "2 senses → 1 type, in the same pass, on the same corpus."),
        FormulaEntry("NFKC", "compatibility decomposition, then canonical composition", "The normalisation form that folds typographic variants; NFC keeps them apart."),
        FormulaEntry("Digit folding", "\\d+(\\.\\d+)? → <num>", "\"12%\" and \"5.2%\" were unique types with no shared meaning; the placeholder keeps the fact of a number."),
    ),
    notationKey = listOf(
        NotationEntry("type vs token", "types are distinct strings, tokens are occurrences — cleaning cuts types much faster than tokens"),
        NotationEntry("NFKC / NFD", "Unicode normalisation forms: NFKC folds compatibility variants, NFD splits accents off for stripping"),
        NotationEntry("truecasing", "restoring the correct case of a word rather than destroying it"),
        NotationEntry("placeholder token", "<url>, <num>, <user> — a token that preserves the presence of something unique"),
        NotationEntry("clitic", "the 's in \"Apple's\" — punctuation stripping splits it, a tokenizer keeps or separates it deliberately"),
        NotationEntry("singleton", "a type appearing once; cleaning exists largely to stop creating them"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The pipeline, in the order that matters",
            accentColor = 0xFF14B8A6,
            code = """
                import re, unicodedata

                def clean(text: str) -> str:
                    text = unicodedata.normalize("NFKC", text)      # 1. before anything compares
                    text = text.replace("’", "'")
                    text = re.sub(r"https?://\S+", " <url> ", text)  # 2. replace, don't delete
                    text = re.sub(r"\S+@\S+\.\w+", " <email> ", text)
                    text = text.lower()                              # 3. the lossy one
                    text = re.sub(r"[^\w\s<>]", " ", text)           # 4. crude; a tokenizer is better
                    text = re.sub(r"\d+(?:\.\d+)?", "<num>", text)   # 5. one token for all numbers
                    text = "".join(c for c in unicodedata.normalize("NFD", text)
                                   if unicodedata.category(c) != "Mn")   # 6. cafe <- café
                    return re.sub(r"\s+", " ", text).strip()         # 7. last, so split() is safe

                print(clean("Café  visits   rose 12% in Q3 (per @IMF)."))
                # cafe visits rose <num> in q<num> per imf
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measuring the trade instead of assuming it",
            accentColor = 0xFFEC4899,
            code = """
                raw_types   = {w for d in docs for w in d.split()}
                clean_types = {w for d in docs for w in clean(d).split()}
                print(len(raw_types), "->", len(clean_types))     # 35 -> 29 on the lab corpus

                # What the fold bought:
                print({w for w in raw_types if w.lower().startswith("apple")})   # {"Apple's", 'apple'}
                # What it cost, in the same corpus:
                print("US" in raw_types and "us" in raw_types)                   # True -> one type now

                # The transformer answer is to not do most of this at all: a cased subword tokenizer
                # keeps "US" and "us" as different tokens and learns the difference from data.
                from transformers import AutoTokenizer
                tok = AutoTokenizer.from_pretrained("bert-base-cased")
                print(tok.tokenize("US markets closed for us"))   # ['US', 'markets', 'closed', 'for', 'us']
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF14B8A6, "Search Indexing", "Query and document must be cleaned identically, or a match that should exist silently does not."),
        ApplicationCard("chart", 0xFF3B82F6, "Count-Based Models", "Smaller vocabulary means fewer singletons and better count estimates for everything else."),
        ApplicationCard("users", 0xFF8B5CF6, "Social Text", "Handles, hashtags and emoji need policies, not deletion — on social text emoji carry sentiment."),
        ApplicationCard("help", 0xFFEC4899, "Where It Backfires", "NER, acronym-heavy domains and accented languages, where the folded character was the word."),
    ),
    takeaways = listOf(
        "Cleaning merges spellings so counts concentrate: 35 vocabulary types → 29 on the lab's corpus.",
        "Order is part of the design — normalise, replace, fold, strip, collapse whitespace last.",
        "Lowercasing merges Apple's/apple (2 types → 1) and US/us (2 senses → 1 type) in the same pass.",
        "Replace URLs, e-mails and numbers with placeholders instead of deleting them: their presence is signal.",
        "Clean for the downstream model — aggressive folding for count models, near-raw text for subword transformers.",
    ),
    crossLinks = listOf(
        CrossLink("tokenization", "Tokenization"),
        CrossLink("stop_words", "Stop Word Removal"),
        CrossLink("regex_nlp", "Regular Expressions"),
        CrossLink("stemming", "Stemming"),
    ),
)
