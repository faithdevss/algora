package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mcmcContent = TopicContent(
    topicId = "mcmc",
    whatIsIt = listOf(
        "Bayesian inference keeps running into the same wall. The posterior is p(θ|D) ∝ p(D|θ)p(θ), and turning that proportionality into an equality requires dividing by ∫p(D|θ)p(θ)dθ — an integral over the whole parameter space that is almost never solvable in closed form and hopeless numerically past a handful of dimensions.",
        "MCMC sidesteps it entirely. Rather than compute the posterior, build a Markov chain whose stationary distribution *is* the posterior, run it, and treat the states it visits as samples. Metropolis-Hastings does this with one idea: propose a move, and accept it with probability min(1, p(new)/p(old)). Because that ratio is all you need, the unknown normalizing constant cancels — you never had to compute the thing that was blocking you. Uphill moves are always accepted; downhill moves are accepted sometimes, which is what stops the chain collapsing onto the mode and lets it map the whole distribution.",
        "What makes it usable in practice is knowing its failure modes, which are quiet rather than loud. Early samples come from wherever you initialized, not from the posterior, so they must be discarded as burn-in. Consecutive samples are correlated, so a thousand iterations are worth far fewer independent draws — the effective sample size, not the iteration count, is the number that matters. And step size fails in both directions: too small and the chain accepts nearly everything while barely moving; too large and almost every proposal is rejected so it sticks in place. Both produce highly correlated samples from opposite causes, which is why roughly 25% acceptance is the rule of thumb for a random walk. Convergence is never provable — you can only fail to detect a problem, using R̂ across independently initialized chains and by looking at the traces.",
    ),
    steps = listOf(
        StepCard(1, "Write the Unnormalized Posterior", "Likelihood times prior. The constant is exactly what you skip.", 0xFF14B8A6),
        StepCard(2, "Propose a Move", "A random step from the current point — symmetric, for plain Metropolis.", 0xFF818CF8),
        StepCard(3, "Compute the Ratio", "p(proposed)/p(current). The normalizing constant cancels here.", 0xFF60A5FA),
        StepCard(4, "Accept or Reject", "Always accept uphill; accept downhill with that probability. A rejection still records a sample.", 0xFF10B981),
        StepCard(5, "Discard Burn-In", "Early samples reflect the starting point, not the posterior.", 0xFFF59E0B),
        StepCard(6, "Diagnose Before Believing", "R̂ near 1, healthy effective sample size, traces that look like noise rather than drift.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The blocker", "p(θ|D) = p(D|θ)p(θ) / ∫p(D|θ)p(θ)dθ", "The denominator is the intractable part."),
        FormulaEntry("Acceptance", "α = min(1, p(θ′)/p(θ))", "Symmetric proposal; the constant cancels."),
        FormulaEntry("General MH", "α = min(1, [p(θ′)q(θ|θ′)] / [p(θ)q(θ′|θ)])", "Corrects an asymmetric proposal."),
        FormulaEntry("Detailed balance", "p(θ)P(θ→θ′) = p(θ′)P(θ′→θ)", "Why the posterior is the stationary distribution."),
        FormulaEntry("Effective sample size", "ESS = n / (1 + 2Σₖ ρₖ)", "Autocorrelation is what erodes it."),
        FormulaEntry("Target acceptance", "≈ 0.23 random walk, ≈ 0.65 HMC", "Rules of thumb worth checking against."),
    ),
    notationKey = listOf(
        NotationEntry("burn-in", "initial samples discarded before the chain has converged"),
        NotationEntry("mixing", "how quickly the chain explores the whole distribution"),
        NotationEntry("R̂", "Gelman-Rubin statistic; near 1 across chains suggests convergence"),
        NotationEntry("ESS", "effective sample size — independent-equivalent draws"),
        NotationEntry("HMC / NUTS", "gradient-based proposals that avoid random-walk behaviour"),
        NotationEntry("trace plot", "parameter value against iteration; should look like noise"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Metropolis-Hastings, complete",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def metropolis(log_target, start, steps=50_000, scale=0.5, seed=0):
                    \"\"\"log_target may be unnormalized — that is the entire point.\"\"\"
                    rng = np.random.default_rng(seed)
                    theta = np.asarray(start, dtype=float)
                    current = log_target(theta)
                    chain, accepted = np.empty((steps, theta.size)), 0

                    for i in range(steps):
                        proposal = theta + rng.normal(scale=scale, size=theta.size)
                        proposed = log_target(proposal)
                        # Log space: exp(proposed - current) is the density RATIO, and the
                        # unknown normalizing constant appears in both terms and cancels.
                        if np.log(rng.random()) < proposed - current:
                            theta, current = proposal, proposed
                            accepted += 1
                        chain[i] = theta        # a rejection records the CURRENT state again

                    return chain, accepted / steps
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The diagnostics, which are not optional",
            accentColor = 0xFFEC4899,
            code = """
                import arviz as az

                # Run several chains from DIFFERENT starting points. One chain cannot tell you
                # it missed a mode; several disagreeing chains can.
                chains = np.stack([metropolis(log_post, start=s, seed=i)[0]
                                   for i, s in enumerate(dispersed_starts)])

                idata = az.from_dict(posterior={"theta": chains[:, burn_in:, :]})
                print(az.rhat(idata))    # want < 1.01 — above that, do not trust the samples
                print(az.ess(idata))     # 50,000 iterations can be worth ~500 independent draws

                # And look at the traces. A healthy one is featureless noise; drift, long
                # flat runs, or chains sitting in different places are all failures that
                # every summary statistic will happily average over.
                az.plot_trace(idata)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF14B8A6, "Bayesian Modelling", "Stan, PyMC and NumPyro are MCMC engines; hierarchical models are their standard use."),
        ApplicationCard("flask", 0xFF818CF8, "Statistical Physics", "Metropolis 1953 came from simulating equations of state — the algorithm predates its statistical use."),
        ApplicationCard("finance", 0xFF10B981, "Risk & Epidemiology", "Parameter uncertainty propagated into forecasts, rather than a single point estimate reported as fact."),
    ),
    takeaways = listOf(
        "MCMC samples a posterior without computing its normalizing constant — the ratio is all that is needed.",
        "Accepting downhill moves with probability p(new)/p(old) is what stops the chain collapsing onto the mode.",
        "Burn-in must be discarded, and effective sample size is the number that matters, not iteration count.",
        "Step size fails in both directions, and convergence can only be failed to disprove — check R̂ and the traces.",
    ),
    crossLinks = listOf(
        CrossLink("bayesian_networks", "Bayesian Networks"),
        CrossLink("bayesian_ridge", "Bayesian Ridge Regression"),
        CrossLink("monte_carlo_method", "Monte Carlo Method (DSA)"),
        CrossLink("monte_carlo_rl", "Monte Carlo Methods (RL)"),
    ),
)
