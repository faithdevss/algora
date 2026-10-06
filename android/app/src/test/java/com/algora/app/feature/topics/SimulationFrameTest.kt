package com.algora.app.feature.topics

import org.junit.Assert.assertTrue
import org.junit.Test

// Every simulation is a config plus a pure frame builder, so the builders can run on the JVM. Until
// this existed, a builder that threw — an index off the end of a graph's edge list, a DP cell key
// outside the declared table — only showed up by opening that one topic in the app. The per-section
// helpers also assert the frames stay inside the shape their config declares.
class SimulationFrameTest {

    private fun check(section: String, ids: Set<String>, count: (String) -> Int) {
        val failures = ids.mapNotNull { id ->
            runCatching { count(id) }
                .fold(
                    onSuccess = { frames -> if (frames > 0) null else "$section/$id built zero frames" },
                    onFailure = { "$section/$id threw ${it::class.simpleName}: ${it.message}" },
                )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `every array-walk config builds frames`() =
        check("ArrayWalk", arrayWalkTopicIds, ::arrayWalkFrameCount)

    @Test
    fun `every text story builds frames inside its columns`() =
        check("TextStory", textStoryTopicIds, ::textStoryFrameCount)

    @Test
    fun `every number story builds frames`() =
        check("NumberStory", numberStoryTopicIds, ::numberStoryFrameCount)

    @Test
    fun `every recursion story builds frames at every size`() =
        check("RecursionStory", recursionStoryTopicIds, ::recursionStoryFrameCount)

    @Test
    fun `every geometry story builds frames`() =
        check("Geometry", geometryTopicIds, ::geometryFrameCount)

    @Test
    fun `every neural story builds frames at every drop rate`() =
        check("NeuralStory", neuralStoryTopicIds, ::neuralStoryFrameCount)

    @Test
    fun `every nlp and llm board story builds frames`() =
        check("NlpStory", nlpStoryTopicIds, ::nlpStoryFrameCount)

    @Test
    fun `every deep-learning story builds frames at every setting`() =
        check("DeepStory", deepStoryTopicIds, ::deepStoryFrameCount)

    @Test
    fun `every fine-tuning, beyond-transformer and nlp-metric story builds frames on every tab`() =
        check("LlmStory", llmStoryTopicIds, ::llmStoryFrameCount)

    @Test
    fun `every reinforcement-learning board builds frames on every option`() =
        check("RlBoard", rlBoardTopicIds, ::rlBoardFrameCount)

    @Test
    fun `every ml story builds frames`() =
        check("MlStory", mlStoryTopicIds, ::mlStoryFrameCount)

    @Test
    fun `every preprocessing story builds frames at every setting`() =
        check("PreprocessStory", preprocessStoryTopicIds, ::preprocessStoryFrameCount)

    @Test
    fun `every clustering story builds frames at every setting`() =
        check("ClusterStory", clusterStoryTopicIds, ::clusterStoryFrameCount)

    @Test
    fun `every dimensionality-reduction story builds frames at every setting`() =
        check("DimStory", dimStoryTopicIds, ::dimStoryFrameCount)

    @Test
    fun `every forecasting and pattern-mining story builds frames at every setting`() =
        check("SeriesStory", seriesStoryTopicIds, ::seriesStoryFrameCount)

    @Test
    fun `every regression story evaluates at every stepper value`() =
        check("RegressionStory", regressionStoryTopicIds, ::regressionStoryProbe)

    @Test
    fun `the decision-tree story's data grows the tree its diagram draws`() =
        assertTrue(treeStoryShapeHolds())

    @Test
    fun `every bit story builds frames as wide as their header`() =
        check("BitStory", bitStoryTopicIds, ::bitStoryFrameCount)

    @Test
    fun `every dp-grid config builds frames inside its declared table`() =
        check("DpGrid", dpGridTopicIds, ::dpGridFrameCount)

    @Test
    fun `every graph-algorithm config builds frames over its own graph`() =
        check("GraphAlgorithm", graphAlgoTopicIds, ::graphAlgoFrameCount)

    @Test
    fun `every pathfinding config builds frames inside the grid`() =
        check("Pathfinding", pathfindingTopicIds, ::pathfindingFrameCount)

    @Test
    fun `every point-cloud config builds frames inside the unit square`() =
        check("PointCloud", pointCloudTopicIds, ::pointCloudFrameCount)

    @Test
    fun `every tree config builds frames with resolvable parents and links`() =
        check("TreeVisualizer", treeVisualizerTopicIds, ::treeVisualizerFrameCount)

    @Test
    fun `every bit-board config builds frames of actual bits`() =
        check("BitBoard", bitBoardTopicIds, ::bitBoardFrameCount)

    // Added with C1: the neural-net labs are the phase's most-used widget from here on (C1-C9 all
    // lean on it) and had no frame guard at all. The helper also checks that a plot's curves stay
    // inside the axis range the frame declared, which a log-scale plot makes easy to get wrong.
    @Test
    fun `every neural-net config builds frames inside its declared axes`() =
        check("NeuralNet", neuralNetTopicIds, ::neuralNetFrameCount)

    // Added with D1. The token strip carries a third of the AI section's labs — every preprocessing,
    // statistical-NLP and naive-Bayes topic — and had no guard at all. It also checks the two things
    // this renderer swallows silently: bar captions that do not line up with their values, and a
    // heat grid whose labels do not match its matrix.
    @Test
    fun `every token-strip config builds frames whose bars and heat grids are labelled`() =
        check("TokenStrip", tokenStripTopicIds, ::tokenStripFrameCount)

    // The tokenization topics moved out of TokenStrip into their own lab; the helper also checks that
    // every split point falls inside the input it marks.
    @Test
    fun `every tokenizer-lab config builds frames with splits inside the input`() =
        check("TokenizerLab", tokenizerLabTopicIds, ::tokenizerLabFrameCount)

    // Added with B9. The regression lab is interactive rather than frame-based and had no guard at
    // all, despite carrying seventeen topics: a config missing from the map silently falls back to
    // the polynomial one, and a non-finite curve silently draws nothing. This runs every config at
    // each slider's extremes and midpoint.
    @Test
    fun `every regression-lab config evaluates across its slider range`() =
        check("RegressionLab", regressionLabTopicIds, ::regressionLabProbe)

    // The recursion tree is the one widget with a live parameter, so this covers every n the slider
    // can reach, not just the default one.
    @Test
    fun `every recursion-tree config builds frames across its whole slider range`() =
        check("RecursionTree", recursionTreeTopicIds, ::recursionTreeFrameCount)

    // Added with the pattern-guide simulation batches. The sorting player had no guard, and its
    // frames address bars by index — a partition or merge bound that is one past the end silently
    // draws nothing rather than failing.
    @Test
    fun `every sorting config builds frames whose highlights are real bars`() =
        check("Sorting", sortingVisualizerTopicIds, ::sortingFrameCount)

    @Test
    fun `every search config probes inside its own array`() =
        check("Search", searchVisualizerTopicIds, ::searchFrameCount)

    @Test
    fun `every hashing config highlights slots that exist`() =
        check("Hashing", hashingVisualizerTopicIds, ::hashingFrameCount)

    @Test
    fun `every linked-structure config marks a lane per node`() =
        check("LinkedStructure", linkedStructureTopicIds, ::linkedStructureFrameCount)

    @Test
    fun `every game-search config builds legal boards and labelled bars`() =
        check("GameSearch", gameSearchTopicIds, ::gameSearchFrameCount)
}
