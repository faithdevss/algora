package com.algora.app.feature.interviewprep.behavioral

internal val behavioralBank = BehavioralBank(
    id = "behavioral_question_bank",
    title = "Behavioral Question Bank",
    description = "Common behavioral prompts with what each one is really testing and how to frame a STAR answer.",
    questions = listOf(
        BehavioralQuestion(
            prompt = "Tell me about a time you disagreed with a teammate. How did you resolve it?",
            category = "Conflict",
            assesses = "Whether you can disagree on substance without making it personal, and reach a decision the team commits to.",
            starTip = "Situation: the technical decision at stake. Task: your stake in it. Action: how you surfaced data and heard them out. Result: the decision made and that the relationship held.",
        ),
        BehavioralQuestion(
            prompt = "Describe a project that failed or missed its goal. What did you learn?",
            category = "Failure",
            assesses = "Ownership over blame-shifting, and whether you extract a concrete, applied lesson.",
            starTip = "Pick a real miss you owned. Keep the setup short, be specific about your contribution to the failure, and end on the changed behavior — not a vague 'I learned to communicate more'.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a time you led without formal authority.",
            category = "Leadership",
            assesses = "Influence, initiative, and whether people follow you because of trust rather than a title.",
            starTip = "Show you saw a gap, rallied people around a plan, and drove it to a result. Quantify the impact and name what others contributed.",
        ),
        BehavioralQuestion(
            prompt = "Give an example of working under a tight deadline with shifting requirements.",
            category = "Ambiguity",
            assesses = "How you prioritize and stay effective when the target moves — scoping, not heroics.",
            starTip = "Emphasize how you cut scope to the essential, communicated trade-offs early, and still shipped something valuable on time.",
        ),
        BehavioralQuestion(
            prompt = "Describe a time you received difficult feedback. What did you do with it?",
            category = "Growth",
            assesses = "Coachability and self-awareness — do you act on feedback or defend against it?",
            starTip = "State the feedback plainly, resist justifying, then show the specific change you made and evidence it stuck.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about your most significant technical contribution and its impact.",
            category = "Impact",
            assesses = "Scope of ownership and whether you measure your work by outcomes, not effort.",
            starTip = "Lead with the business or user result, then explain the technical decision that drove it. Numbers beat adjectives.",
        ),
        BehavioralQuestion(
            prompt = "Describe a time you had to convince stakeholders to change direction.",
            category = "Influence",
            assesses = "Communicating trade-offs to non-experts and building buy-in without authority.",
            starTip = "Frame their concern first, present the evidence in their terms (cost, risk, timeline), and show how you reached alignment.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a time you had to learn something unfamiliar quickly to deliver.",
            category = "Learning",
            assesses = "Learning velocity and how you de-risk the unknown under time pressure.",
            starTip = "Show your approach to ramping fast — narrowing to what you needed, leaning on the right sources — and that it produced a shipped result.",
        ),
        BehavioralQuestion(
            prompt = "Describe a production incident you were involved in. What was your role?",
            category = "Ownership",
            assesses = "Behaviour under pressure, and whether you separate mitigation from root cause.",
            starTip = "Stop the bleeding first, then diagnose — say it in that order. End with the durable fix and the guardrail (alert, test, runbook) that stops a repeat.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a technical decision you made that you would make differently today.",
            category = "Judgment",
            assesses = "Honest retrospection and whether your judgment has actually improved.",
            starTip = "Give the constraints you had at the time so the decision sounds reasonable, then name the signal you now know to weigh more heavily.",
        ),
        BehavioralQuestion(
            prompt = "Describe a time you had to say no to a request from a stakeholder.",
            category = "Prioritization",
            assesses = "Whether you defend focus with reasons rather than either caving or stonewalling.",
            starTip = "Show the trade-off explicitly — what would have been dropped — and that you offered an alternative or a timeline rather than a flat refusal.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a time you improved a process or tool for your team.",
            category = "Initiative",
            assesses = "Whether you fix systemic friction or just route around it personally.",
            starTip = "Quantify the before and after — build minutes, on-call pages, review latency — and mention adoption, since a tool nobody uses is not an improvement.",
        ),
        BehavioralQuestion(
            prompt = "Describe a time you mentored someone or unblocked a struggling teammate.",
            category = "Mentorship",
            assesses = "Whether you scale through others or hoard the interesting work.",
            starTip = "Show that you diagnosed what they were missing rather than taking the keyboard, and point to what they could do afterwards without you.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a time you had to make a decision without enough data.",
            category = "Ambiguity",
            assesses = "Comfort with reversible risk and whether you make the uncertainty explicit.",
            starTip = "Name what you knew, what you assumed, and the cheapest experiment that would have proved you wrong. Say how you set a point to revisit the call.",
        ),
        BehavioralQuestion(
            prompt = "Describe a project where you had to work across teams with competing priorities.",
            category = "Collaboration",
            assesses = "Whether you can align incentives, not just escalate.",
            starTip = "Show you found the shared goal, made the dependency and its cost visible early, and kept the other team informed rather than surprised.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about the most complex system you have worked on. How did you get up to speed?",
            category = "Depth",
            assesses = "Genuine technical depth — a shallow answer collapses under two follow-ups.",
            starTip = "Pick something you can whiteboard. Explain the design, the part you owned, and one trade-off the system made, including what it gave up.",
        ),
        BehavioralQuestion(
            prompt = "Describe a time you pushed back on a deadline or a scope you thought was unrealistic.",
            category = "Communication",
            assesses = "Whether you raise risk early with evidence, or silently absorb it until it explodes.",
            starTip = "Emphasise timing — you raised it before the crunch — and that you came with options (cut scope, add people, move the date) rather than only a problem.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a time you shipped something and the outcome was not what you expected.",
            category = "Impact",
            assesses = "Whether you measure after shipping, and how you react to a disappointing metric.",
            starTip = "Show the instrumentation was in place before launch, what the data said, and the decision that followed — iterate, roll back, or kill it.",
        ),
        BehavioralQuestion(
            prompt = "Why are you looking to leave your current role, and what are you looking for next?",
            category = "Motivation",
            assesses = "Self-knowledge and whether this role actually matches what you want.",
            starTip = "Keep it forward-looking and specific to what this team does. Criticising your current employer costs you more than any story it buys.",
        ),
        BehavioralQuestion(
            prompt = "Tell me about a time you disagreed with a decision but had to commit to it anyway.",
            category = "Conflict",
            assesses = "Disagree-and-commit — whether the disagreement ends when the decision is made.",
            starTip = "Show you argued the case once, clearly, then executed without hedging. Say how you kept a way to revisit it if the evidence changed.",
        ),
    ),
)
