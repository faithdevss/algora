package com.algora.app.feature.analysis

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Topic

private fun topic(id: String, name: String, category: Category, tagline: String) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = category.iconName,
    accentColor = category.accentColor,
)

private val fundamentals = AnalysisCategories.fundamentals
private val growth = AnalysisCategories.growthVisualization
private val caseAnalysis = AnalysisCategories.caseAnalysis
private val empirical = AnalysisCategories.empiricalAnalysis
private val advanced = AnalysisCategories.advanced
private val exploration = AnalysisCategories.explorationGames

private val fundamentalsTopics = listOf(
    topic("operation_counter", "Operation Counter", fundamentals, "Counts operations executed by real code."),
    topic("cost_profiler", "Cost Profiler", fundamentals, "Profiles runtime cost across input sizes."),
    topic("parameterized_input_generator", "Parameterized Input Generator", fundamentals, "Generates inputs of controllable size and shape."),
)

private val growthTopics = listOf(
    topic("growth_curve_chart", "Growth Curve Chart", growth, "Plots how runtime scales with input size."),
    topic("growth_class_comparison", "Log vs Linear vs Exponential", growth, "Overlays growth classes on one chart."),
    topic("complexity_class_comparison", "Complexity Class Comparison", growth, "Side-by-side comparison of Big-O classes."),
)

private val caseTopics = listOf(
    topic("best_case", "Best Case", caseAnalysis, "The most favorable input for an algorithm."),
    topic("worst_case", "Worst Case", caseAnalysis, "The least favorable input for an algorithm."),
    topic("average_case", "Average Case", caseAnalysis, "Expected performance across typical inputs."),
)

private val empiricalTopics = listOf(
    topic("benchmark_dashboard", "Benchmark Dashboard", empirical, "Real measured runtime across input sizes."),
    topic("real_vs_predicted_runtime", "Real vs Predicted Runtime", empirical, "Compares measured runtime to theoretical Big-O."),
    topic("input_size_scaling", "Input Size Scaling", empirical, "How measured runtime changes as inputs grow."),
)

private val advancedTopics = listOf(
    topic("amortized_analysis", "Amortized Analysis", advanced, "Average cost per operation over a sequence."),
    topic("space_time_tradeoffs", "Space-Time Tradeoffs", advanced, "Trading memory for speed, or vice versa."),
    topic("master_theorem", "Master Theorem", advanced, "Solves recurrence relations for divide-and-conquer algorithms."),
)

private val explorationTopics = listOf(
    topic("complexity_map", "Complexity Map", exploration, "Visual map of algorithms by their complexity class."),
    topic("sandbox_mode", "Sandbox Mode", exploration, "Free-form playground for experimenting with algorithms."),
)

object AnalysisTopics {
    val topics: List<Topic> =
        fundamentalsTopics + growthTopics + caseTopics + empiricalTopics + advancedTopics + explorationTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
