package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val selfPlayContent = TopicContent(
    topicId = "self_play",
    figure = Figure(
        caption = "The page's lab: fictitious play on rock-paper-scissors — each side best-responds to " +
            "the other's history — and the exploitability of the average strategy, how much a " +
            "perfect opponent could win against it (0 at the Nash equilibrium of one third each). " +
            "After 10 rounds the mix is lopsided, 36% / 45% / 18%, and exploitable by 0.182. It " +
            "settles as the history grows: 0.098 after 40 rounds, 0.033 after 300, 0.029 after " +
            "2,000, with the average sitting near one third each — an equilibrium nobody " +
            "programmed in, found with no external opponent. The current policy never converges: " +
            "a best response is always a single pure strategy, and always exploitable. It is the " +
            "average that converges, which is why large self-play systems keep a league of past " +
            "versions to play against.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "exploitability",
                    listOf(FigurePoint(0.000f, 0.910f), FigurePoint(0.262f, 0.490f), FigurePoint(0.642f, 0.165f), FigurePoint(1.000f, 0.145f)),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 0.91f, "10 rounds: 0.182", FigureTone.Warn),
                FigurePoint(1f, 0.145f, "2,000: 0.029"),
            ),
            xLabel = "rounds, 10 → 2,000 (log)",
            yLabel = "exploitability, 0 to 0.2",
        ),
    ),
    whatIsIt = listOf(
        "Self-play trains an agent against copies of itself, so the opponent improves exactly as fast as the agent does — a curriculum that never gets too easy or too hard and needs no external opponent or data. The simplest form is fictitious play: each side plays a best response to the other's historical average strategy.",
        "The lab runs fictitious play on rock-paper-scissors. Early on the frequencies swing as each side chases the other's latest habit — after 10 rounds the mix is 36% / 45% / 18% and its exploitability, how much a perfect opponent could win against it, is 0.18. The averages settle: exploitability is 0.098 after 40 rounds, and after 300 the average sits near one third each, the Nash equilibrium nobody programmed in. Over 2,000 rounds exploitability falls from 0.182 to 0.029, with the curriculum entirely internal.",
        "The detail that matters is that the average converges, not the current policy. The latest best response is always a pure strategy and always exploitable — play only that and a cycle of rock, paper, scissors results. Large systems keep the idea by keeping old versions around: AlphaZero trains against itself, and OpenAI Five and AlphaStar train against leagues of past and present agents so that no single exploitable habit survives.",
    ),
    steps = listOf(
        StepCard(1, "Play Against Yourself", "The agent's current (or past) policies serve as opponents.", 0xFF818CF8),
        StepCard(2, "Learn from the Games", "Update the policy with RL on the resulting match outcomes.", 0xFF60A5FA),
        StepCard(3, "Auto-Scaling Difficulty", "As the agent improves, so does its opponent — an ever-harder curriculum.", 0xFF10B981),
        StepCard(4, "Maintain a Pool", "Keep a league of past versions to avoid cycles and forgetting.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Opponent", "π_opp = past or current π", "The agent plays versions of itself."),
        FormulaEntry("Curriculum", "difficulty tracks skill", "Always an even match."),
        FormulaEntry("Stability", "opponent pool / league", "Prevents chasing and forgetting."),
    ),
    notationKey = listOf(
        NotationEntry("self-play", "training against copies of oneself"),
        NotationEntry("league", "a pool of past agent versions"),
        NotationEntry("curriculum", "automatically increasing difficulty"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Self-play training loop (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                pool = [policy.copy()]
                for _ in range(iterations):
                    opponent = sample(pool)              # a past or current self
                    games = play(policy, opponent)
                    policy.update(games)                 # RL on outcomes
                    if improved(policy): pool.append(policy.copy())
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "AlphaZero & OpenAI Five", "Superhuman Go, chess, and Dota 2 emerged largely from self-play."),
        ApplicationCard("users", 0xFF60A5FA, "Competitive MARL", "Trains robust strategies in adversarial multi-agent games."),
        ApplicationCard("robot", 0xFF10B981, "Emergent Skills", "Self-play produced tool use and complex tactics in hide-and-seek."),
    ),
    takeaways = listOf(
        "Self-play trains an agent against versions of itself for an auto-scaling curriculum.",
        "The opponent's difficulty always tracks the agent's own skill.",
        "A league of past versions prevents strategy cycling and forgetting.",
        "It drove landmark superhuman results in Go, chess, and Dota 2.",
        "In the lab exploitability of the average strategy falls from 0.182 after 10 rounds to 0.029 after 2,000 — converging on ⅓ each without being told.",
    ),
    crossLinks = listOf(
        CrossLink("alphazero", "AlphaZero"),
        CrossLink("minimax", "Minimax"),
    ),
)
