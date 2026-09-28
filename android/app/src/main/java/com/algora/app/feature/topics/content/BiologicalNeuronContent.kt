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

internal val biologicalNeuronContent = TopicContent(
    topicId = "biological_neuron",
    figure = Figure(
        caption = "The f–I curve the lab measures, not a drawn one: firing rate against input " +
            "current, with rate divided by 220 Hz and current by 80 units to fit the axes. Nothing " +
            "in the leaky integrate-and-fire code puts a rectifier there. Below 15 units the leak " +
            "cancels the input and the membrane settles short of −55 mV forever — 0 Hz at 14, 30 Hz " +
            "at 16. Above it the rate climbs and then bends, because the 2 ms refractory period " +
            "caps how fast spikes can follow one another: 54 Hz at 20 units, but only 212 Hz at 80. " +
            "ReLU is the rectifying half of this shape and sigmoid the saturating half — the " +
            "abstraction had a source. What it discarded is not on the plot: time, spike timing, " +
            "and any mechanism a synapse could use to run backpropagation.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "firing rate",
                    listOf(
                        FigurePoint(0f, 0f),
                        FigurePoint(0.125f, 0f),
                        FigurePoint(0.1875f, 0f),
                        FigurePoint(0.20f, 0.136f),
                        FigurePoint(0.225f, 0.200f),
                        FigurePoint(0.25f, 0.245f),
                        FigurePoint(0.3125f, 0.345f),
                        FigurePoint(0.375f, 0.427f),
                        FigurePoint(0.50f, 0.573f),
                        FigurePoint(0.75f, 0.800f),
                        FigurePoint(1f, 0.964f),
                    ),
                    FigureTone.Primary,
                ),
            ),
            xLabel = "input current I (0–80 units)",
            yLabel = "rate",
            markers = listOf(
                FigurePoint(0.1875f, 0f, "rheobase = 15", FigureTone.Warn),
                FigurePoint(1f, 0.964f, "212 Hz — refractory cap", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A neuron collects electrical input through its dendrites, integrates it across its cell body, and — if the accumulated membrane potential crosses about −55 mV — fires an action potential down its axon to the synapses of the next cells. The pulse is all-or-nothing: it is the same size every time, so a neuron cannot signal \"a bit\" by firing a smaller spike. What varies is *when* and *how often*.",
        "The simplest model anyone actually uses is leaky integrate-and-fire, and this topic's simulation runs it: τ dV/dt = −(V − V_rest) + RI, with a threshold, a reset and a refractory period. Two things fall out that no one designed in. Below a critical current — the rheobase, exactly 15 units in the lab's parameterisation — the leak balances the input and the cell never fires at all, however long you wait. Above it, the firing rate rises and then bends over as the refractory period starts limiting how fast spikes can follow one another. Sweep the current and plot the rate and you get the f–I curve: flat, then rising, then saturating.",
        "That shape is the whole reason this topic sits at the front of a deep learning section. An artificial unit computes Σwᵢxᵢ + b and passes it through an activation function, and the two activations that dominate practice — ReLU and sigmoid — are the rectifying and saturating halves of the curve a real neuron produces. The abstraction was not arbitrary. But it is worth being equally precise about what it discards: all of time, since an artificial unit has no state between inputs and spike *timing* carries real information; the dendrites' own non-linear computation, which makes a single neuron closer to a small network than to a single unit; and any biological mechanism for backpropagation, for which none has ever been found. \"Neural network\" stopped being a model of the brain around 1960. It is a useful caricature, and knowing which one you are holding matters.",
    ),
    steps = listOf(
        StepCard(1, "Input Arrives at the Dendrites", "Thousands of synapses, each excitatory or inhibitory. This is the weighted sum, and the weights are synaptic strengths.", 0xFF06B6D4),
        StepCard(2, "The Membrane Integrates and Leaks", "Charge accumulates while continuously draining back toward −70 mV. Integration over *time*, which the artificial unit has no equivalent for.", 0xFF22D3EE),
        StepCard(3, "Threshold, or Nothing", "Cross −55 mV and an action potential fires. Below it, the potential simply settles — the biological version of a unit that is off.", 0xFF8B5CF6),
        StepCard(4, "Reset and Refract", "Back to −75 mV, then a couple of milliseconds when nothing can happen. This is what makes the f–I curve saturate.", 0xFF6366F1),
        StepCard(5, "Rate Encodes Magnitude", "Since every spike is identical, strength is carried by frequency — which is the number the artificial unit outputs directly.", 0xFF10B981),
        StepCard(6, "Compare the Two", "The f–I curve is a rectifier that saturates. ReLU is the first half, sigmoid the second. Then note what got dropped.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Leaky integrate-and-fire", "τ dV/dt = −(V − V_rest) + R·I", "A leaky capacitor. τ ≈ 10 ms in the lab."),
        FormulaEntry("Fire condition", "V ≥ V_th ⟹ spike, V ← V_reset", "−55 mV threshold, −75 mV reset."),
        FormulaEntry("Steady state", "V_∞ = V_rest + R·I", "Below threshold, this is where it settles and stays."),
        FormulaEntry("Rheobase", "I_rheo = (V_th − V_rest)/R", "15 units here. Below it, no spike is possible at any duration."),
        FormulaEntry("Firing rate", "f(I) = 1 / (t_ref + τ·ln[(RI)/(RI − (V_th−V_rest))])", "The f–I curve in closed form; the lab measures it instead."),
        FormulaEntry("Artificial unit", "y = φ(Σᵢ wᵢxᵢ + b)", "The abstraction: weights for synapses, φ for the f–I curve."),
        FormulaEntry("Hebbian learning", "Δwᵢⱼ ∝ xᵢ · xⱼ", "\"Fire together, wire together\" — local, unlike backpropagation."),
    ),
    notationKey = listOf(
        NotationEntry("V_rest", "resting potential, about −70 mV"),
        NotationEntry("V_th", "firing threshold, about −55 mV"),
        NotationEntry("τ", "membrane time constant — how fast the leak acts"),
        NotationEntry("rheobase", "the minimum current that can ever cause a spike"),
        NotationEntry("refractory period", "the dead time after a spike; the source of saturation"),
        NotationEntry("f–I curve", "firing rate as a function of input current"),
        NotationEntry("action potential", "the all-or-nothing spike; identical every time"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The model, and the curve that falls out of it",
            accentColor = 0xFF06B6D4,
            code = """
                import numpy as np

                def lif(current, ms=500, tau=10.0, r=1.0, v_rest=-70, v_th=-55,
                        v_reset=-75, refractory=2.0, dt=0.1):
                    v, refrac, spikes = v_rest, 0.0, []
                    for step in range(int(ms / dt)):
                        if refrac > 0:
                            refrac -= dt
                            v = v_reset
                            continue
                        v += dt / tau * (-(v - v_rest) + r * current)
                        if v >= v_th:
                            spikes.append(step * dt)
                            v, refrac = v_reset, refractory
                    return spikes

                # Nobody put a rectifier in this code. It emerges: below the rheobase the leak
                # balances the input and the cell settles short of threshold forever.
                for i in (12, 14, 16, 20, 40, 80):
                    print(i, len(lif(i)) / 0.5, "Hz")   # 0, 0, ~30, ~54, ~130, ~215
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What the abstraction dropped, in one comparison",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn as nn

                # The artificial unit: stateless, continuous-valued, differentiable.
                unit = nn.Sequential(nn.Linear(3, 1), nn.ReLU())
                print(unit(torch.tensor([0.9, 0.2, 0.5])))     # one number in, one number out

                # It has no time. Feed the same input twice and nothing carries over:
                x = torch.tensor([0.9, 0.2, 0.5])
                assert torch.equal(unit(x), unit(x))

                # A spiking network has to be simulated over time, and its output is a set of
                # spike TIMES rather than a value. Frameworks like snnTorch and Norse exist for
                # exactly this, and training them is hard for a specific reason: a threshold is
                # not differentiable, so backprop needs a surrogate gradient to work at all.
                #
                # And backpropagation itself has no known biological implementation -- it needs
                # each synapse to know the downstream error signal, which the wiring does not
                # provide. Real synaptic change is local (Hebbian, STDP). Deep learning borrowed
                # a picture from neuroscience in the 1940s and has not been a model of it since.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF06B6D4, "Neuromorphic Hardware", "Loihi and SpiNNaker run spiking networks directly, trading arithmetic for events — orders of magnitude less power when activity is sparse."),
        ApplicationCard("flask", 0xFF8B5CF6, "Computational Neuroscience", "LIF and Hodgkin-Huxley models are how hypotheses about circuits get tested against recordings."),
        ApplicationCard("robot", 0xFF6366F1, "Brain-Computer Interfaces", "Decoding intent from spike trains, where the timing the artificial abstraction discards is the entire signal."),
    ),
    takeaways = listOf(
        "A neuron integrates input over time and fires an identical all-or-nothing spike at threshold.",
        "Below the rheobase it never fires; above it the rate rises and saturates — that is the f–I curve.",
        "ReLU is the rectifying half of that curve and sigmoid the saturating half; the abstraction has a source.",
        "The artificial unit has no time, no spikes, and no dendritic computation.",
        "Backpropagation has no known biological mechanism — the metaphor stopped being a model long ago.",
    ),
    crossLinks = listOf(
        CrossLink("perceptron", "The Perceptron"),
        CrossLink("activation_functions", "Activation Functions"),
        CrossLink("mlp", "Multi-Layer Perceptron (MLP)"),
        CrossLink("neural_network_basics", "Feedforward Networks"),
    ),
)
