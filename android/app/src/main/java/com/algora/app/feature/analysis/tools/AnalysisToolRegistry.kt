package com.algora.app.feature.analysis.tools

import androidx.compose.runtime.Composable

// Analysis "tools" (features.md §3.3) are live dashboards, not prose topics — they bypass
// TopicContent/TopicDetailScreen's 7-section template entirely and render their own layout.
// Topics not listed here fall through to the standard "coming soon" placeholder.
object AnalysisToolRegistry {
    private val tools: Map<String, @Composable () -> Unit> = mapOf(
        "operation_counter" to { OperationCounterTool() },
        "growth_curve_chart" to { GrowthCurveChartTool() },
        // Sub-phase A
        "growth_class_comparison" to { GrowthClassComparisonTool() },
        "complexity_class_comparison" to { ComplexityClassComparisonTool() },
        "best_case" to { CaseAnalysisTool(CaseMode.BEST) },
        "worst_case" to { CaseAnalysisTool(CaseMode.WORST) },
        "average_case" to { CaseAnalysisTool(CaseMode.AVERAGE) },
        // Sub-phase B
        "cost_profiler" to { CostProfilerTool() },
        "parameterized_input_generator" to { InputGeneratorTool() },
        "amortized_analysis" to { AmortizedAnalysisTool() },
        "space_time_tradeoffs" to { SpaceTimeTradeoffTool() },
        // Sub-phase C
        "benchmark_dashboard" to { BenchmarkTool(BenchVariant.BENCHMARK) },
        "real_vs_predicted_runtime" to { BenchmarkTool(BenchVariant.REAL_VS_PREDICTED) },
        "input_size_scaling" to { BenchmarkTool(BenchVariant.SCALING) },
        // Sub-phase D
        "master_theorem" to { MasterTheoremTool() },
        "complexity_map" to { ComplexityMapTool() },
        "sandbox_mode" to { SandboxTool() },
    )

    fun get(topicId: String): (@Composable () -> Unit)? = tools[topicId]
}
