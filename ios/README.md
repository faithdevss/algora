# AlgorAI iOS — simulation port status

Native SwiftUI port of the Android app (`com.saimum.algorai`). Everything except ads and payments is
ported (see `docs/plan/ios-port.md`); the only open phase is **I5, simulation labs**.

Each topic's `SimulationType` is routed through `AlgorAI/Features/Labs/SimulationHost.swift`. A type
with no lab falls back to the "Simulation coming soon" card.

**Summary:** 31 of 34 lab types done · 3 pending · 181 topics still show "coming soon".

## Done (31)

| # | SimulationType | Swift lab | Topics |
|---|---|---|---|
| 1 | ArrayVisualizer | `ArrayLab` | 1 |
| 2 | LinkedListVisualizer | `LinkedListLab` | 1 |
| 3 | StackVisualizer | `StackQueueLab` | 1 |
| 4 | QueueVisualizer | `StackQueueLab` | 1 |
| 5 | GraphVisualizer | `GraphBuilderLab` | 3 |
| 6 | GraphAlgorithmPlayer | `GraphAlgorithmLab` | 20 |
| 7 | ArrayWalkPlayer | `ArrayWalkLab` | 64 |
| 8 | RlTrainingPlayer | `RlTrainingLab` | 9 |
| 9 | PolicyGradientPlayer | `PolicyGradientLab` | 12 |
| 10 | GameSearchPlayer | `GameSearchLab` | 10 |
| 11 | OfflineRlPlayer | `OfflineRlLab` | 7 |
| 12 | MultiAgentPlayer | `MultiAgentLab` | 4 |
| 13 | ExplorationPlayer | `ExplorationLab` | 4 |
| 14 | LinkedStructurePlayer | `LinkedStructureLab` | 4 |
| 15 | EnvironmentPlayer | `EnvironmentLab` | 6 |
| 16 | RegressionExplorer | `RegressionExplorerLab` | 1 |
| 17 | RegressionLab | `RegressionLab` | 22 |
| 18 | DecisionSurface | `DecisionSurfaceLab` | 6 |
| 19 | BitBoardPlayer | `BitBoardLab` | 7 |
| 20 | FeatureMapPlayer | `FeatureMapLab` | 21 |
| 21 | PerceptronVisualizer | `ClassifierPlaygroundLab` (gates) | 1 |
| 22 | ClassifierPlayground | `ClassifierPlaygroundLab` | 2 |
| 23 | RecursionTreeVisualizer | `RecursionTreeLab` | 14 |
| 24 | DpGridVisualizer | `DpGridLab` | 18 |
| 25 | SortingVisualizer | `SortingLab` | 12 |
| 26 | SearchVisualizer | `SearchLab` | 6 |
| 27 | TreeVisualizer | `TreeVisualizerLab` | 29 |
| 28 | PathfindingGrid | `PathfindingLab` | 7 |
| 29 | HashingVisualizer | `HashingLab` | 6 |
| 30 | RlGridWorld | `RlGridWorldLab` | 18 |
| 31 | BanditExplorer | `BanditLab` | 6 |

## Pending (3)

| # | SimulationType | Android source | Kotlin lines | Topics | Areas |
|---|---|---|---|---|---|
| 1 | PointCloudPlayer | `PointCloudSection.kt` | 5,416 | 59 | clustering, classic ML, PCA/t-SNE/UMAP/ICA, geometry, embeddings, eval metrics, GANs |
| 2 | TokenStripPlayer | `TokenStripSection.kt` | 6,228 | 55 | text preprocessing, tokenizers, statistical NLP, attention/transformers, RNNs |
| 3 | NeuralNetPlayer | `NeuralNetSection.kt` | 6,117 | 67 | activations, optimizers, regularization, deep nets, CNN/detection, fine-tuning, generative |

### Math dependencies for the pending labs

Ported (in `AlgorAI/Features/Labs/`): `DimReductionMath`, `EmbeddingMath`, `GenerativeMath`,
`MetricsMath`, `SpecializedMath`, `ModernLlmMath` (all objects), `StatisticalNlpMath` (all objects),
`ClusteringMath`, `AssociationMath`, `PreprocessMath`, `DetectionMath`, `CnnMath`, `TimeSeriesMath`,
`RegressionLabMath`, `DecisionSurfaceMath`.

Not yet ported:

| Kotlin file | Status |
|---|---|
| `ActivationMath.kt` | not started |
| `AttentionMath.kt` | not started |
| `DeepNetMath.kt` | not started |
| `NlpMetricsMath.kt` | not started |
| `OptimizerMath.kt` | not started |
| `PretrainMath.kt` | not started |
| `RbmMath.kt` | not started |
| `RegularizationMath.kt` | not started |
| `RnnMath.kt` | not started |
| `TextPreprocessingMath.kt` | not started |
| `TransformerMath.kt` | not started |
| `NaiveBayesFrames.kt` | not started |
| `SyntaxMath.kt` | partial — `PosLab`, `ChunkLab`, `CorefLab`, `SentimentLexiconLab` missing |
| `FineTuneMath.kt` | partial — `FineTuneLab`, `DpoLab`, `PeftLab`, `LoraLab`, `QuantLab`, `LongContextLab` missing |

`NotYetAvailable` (1 topic) is intentionally the "coming soon" card on both platforms.
