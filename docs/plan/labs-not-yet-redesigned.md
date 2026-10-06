# Labs not yet redesigned

Topics whose simulation is still the old pre-storyboard widget on Android and shows "Simulation coming soon" on iOS. Redesign each group as a storyboard on both platforms, then retire the old widget path for those ids.

Generated 2026-10-05 from `ios/AlgorAI/Resources/Content/topic_content.json` against the topic-id sets routed in `ios/AlgorAI/Features/Labs/SimulationHost.swift` and Android's `SimulationHost.kt`.

**47 topics in 6 groups** (29 TokenStrip, 11 NeuralNet, 7 PointCloud).

## NLP preprocessing & statistical NLP (10)

| Done | Topic id | Title | Android today |
|---|---|---|---|
| [ ] | `text_cleaning` | Lowercasing & Cleaning | `TokenStripSection` |
| [ ] | `regex_nlp` | Regular Expressions | `TokenStripSection` |
| [ ] | `stop_words` | Stop Word Removal | `TokenStripSection` |
| [ ] | `stemming` | Stemming (Porter Stemmer) | `TokenStripSection` |
| [ ] | `lemmatization` | Lemmatization | `TokenStripSection` |
| [ ] | `n_grams` | N-Grams | `TokenStripSection` |
| [ ] | `bow_tfidf` | Bag-of-Words / TF-IDF | `TokenStripSection` |
| [ ] | `jaccard_similarity` | Jaccard Similarity | `TokenStripSection` |
| [ ] | `cosine_similarity` | Cosine Similarity | `PointCloudSection` |
| [ ] | `hmm` | Hidden Markov Models | `TokenStripSection` |

## NLP syntax & semantics (5)

| Done | Topic id | Title | Android today |
|---|---|---|---|
| [ ] | `ner` | Named Entity Recognition | `TokenStripSection` |
| [ ] | `pos_tagging` | Part-of-Speech Tagging | `TokenStripSection` |
| [ ] | `chunking` | Chunking | `TokenStripSection` |
| [ ] | `coreference` | Coreference Resolution | `TokenStripSection` |
| [ ] | `sentiment_lexicon` | Sentiment Analysis (Lexicon) | `TokenStripSection` |

## Word embeddings (7)

| Done | Topic id | Title | Android today |
|---|---|---|---|
| [ ] | `word_embeddings` | Word Embeddings | `TokenStripSection` |
| [ ] | `word2vec_cbow` | Word2Vec (CBOW) | `TokenStripSection` |
| [ ] | `word2vec_skipgram` | Word2Vec (Skip-Gram) | `TokenStripSection` |
| [ ] | `glove` | GloVe | `PointCloudSection` |
| [ ] | `fasttext` | FastText | `TokenStripSection` |
| [ ] | `elmo` | ELMo | `PointCloudSection` |
| [ ] | `rnn_lstm` | RNN / LSTM | `TokenStripSection` |

## Transformer internals & pretrained LMs (9)

| Done | Topic id | Title | Android today |
|---|---|---|---|
| [ ] | `feed_forward` | Feed-Forward Networks | `NeuralNetSection` |
| [ ] | `positional_encodings` | Positional Encodings | `TokenStripSection` |
| [ ] | `bart` | BART | `TokenStripSection` |
| [ ] | `xlnet` | XLNet | `TokenStripSection` |
| [ ] | `gpt3_gpt4` | GPT-3 & GPT-4 | `NeuralNetSection` |
| [ ] | `llama_vicuna` | LLaMA & Vicuna | `NeuralNetSection` |
| [ ] | `mistral_mixtral` | Mistral & Mixtral (MoE) | `TokenStripSection` |
| [ ] | `claude_gemini` | Claude & Gemini | `TokenStripSection` |
| [ ] | `llms` | LLMs | `TokenStripSection` |

## Modern LLM techniques (7)

| Done | Topic id | Title | Android today |
|---|---|---|---|
| [ ] | `prompt_engineering` | Prompt Engineering (Zero/Few Shot) | `TokenStripSection` |
| [ ] | `chain_of_thought` | Chain of Thought (CoT) | `NeuralNetSection` |
| [ ] | `react` | ReAct (Reasoning + Acting) | `TokenStripSection` |
| [ ] | `ai_agents` | AI Agents & Tool Use | `TokenStripSection` |
| [ ] | `rag` | Retrieval-Augmented Generation | `TokenStripSection` |
| [ ] | `vector_databases` | Vector Databases | `PointCloudSection` |
| [ ] | `hallucination_mitigation` | Hallucination Mitigation | `NeuralNetSection` |
