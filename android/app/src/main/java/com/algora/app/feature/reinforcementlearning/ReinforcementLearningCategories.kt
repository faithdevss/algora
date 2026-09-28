package com.algora.app.feature.reinforcementlearning

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats()['rl'] — the most detailed
// AI taxonomy in the mock.
object ReinforcementLearningCategories {
    val foundations = Category("rl_foundations", "Foundations", Section.RL, 0xFFF97316, "game")

    // Not in the mock's cats()['rl'], which jumps straight from the vocabulary to DQN. Added in
    // Phase 9 because docs/topics.ai.md lists the classical methods as their own block, and they are
    // what the whole deep-RL section is an approximation of.
    val tabular = Category("rl_tabular", "Tabular Methods (Classical)", Section.RL, 0xFF6366F1, "map")
    val dqn = Category("rl_dqn", "Deep Q-Networks (DQN Family)", Section.RL, 0xFF3B82F6, "network")
    val policyGradient = Category("rl_policy_gradient", "Policy Gradient Methods", Section.RL, 0xFF10B981, "trend")
    val continuousControl = Category("rl_continuous", "Continuous Control (Robotics)", Section.RL, 0xFF8B5CF6, "robot")
    val modelBased = Category("rl_model_based", "Model-Based & Planning", Section.RL, 0xFFEC4899, "chip")
    val exploration = Category("rl_exploration", "Exploration Strategies", Section.RL, 0xFF06B6D4, "search")
    val marl = Category("rl_marl", "Multi-Agent Systems (MARL)", Section.RL, 0xFFF59E0B, "users")
    val advanced = Category("rl_advanced", "Advanced Frontiers", Section.RL, 0xFF6366F1, "stack")
    val benchmarks = Category("rl_benchmarks", "Famous Benchmarks", Section.RL, 0xFF14B8A6, "game")

    val all = listOf(
        foundations, tabular, dqn, policyGradient, continuousControl,
        modelBased, exploration, marl, advanced, benchmarks,
    )
}
