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

    // Added with C3, extended by C4. The three mechanics labs rebuild every frame from the
    // kernel/stride/padding on screen, so the helper runs the whole control sweep — including
    // combinations that empty the feature map, which are two taps away. C4's labs draw boxes and
    // label masks, so the same helper also checks that no box escapes its own scene.
    @Test
    fun `every feature-map config builds frames that match the geometry they claim`() =
        check("FeatureMap", featureMapTopicIds, ::featureMapFrameCount)

    // The recursion tree is the one widget with a live parameter, so this covers every n the slider
    // can reach, not just the default one.
    @Test
    fun `every recursion-tree config builds frames across its whole slider range`() =
        check("RecursionTree", recursionTreeTopicIds, ::recursionTreeFrameCount)
}
