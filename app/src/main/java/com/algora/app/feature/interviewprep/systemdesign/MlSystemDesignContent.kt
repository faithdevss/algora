package com.algora.app.feature.interviewprep.systemdesign

// The AI counterpart of systemDesignPrimer. An ML design round asks the same "clarify, estimate,
// sketch, defend" questions, but the failure modes are different: label quality, training/serving
// skew, drift and feedback loops rather than QPS and shard keys.
internal val mlSystemDesignPrimer = SystemDesignPrimer(
    id = "ml_system_design_primer",
    title = "ML System Design Primer",
    description = "How to drive an ML design round — framing the problem, shaping the data, serving the model, and catching it when it rots.",
    framework = listOf(
        SystemDesignStep(
            "Frame it as an ML problem",
            "State the business goal, then the ML formulation: what is predicted, at what granularity, and what a single training example looks like. Ask whether ML is warranted at all — many rounds reward proposing a rules baseline first.",
        ),
        SystemDesignStep(
            "Define labels and the offline metric",
            "Where does ground truth come from, and how delayed is it? Choose a metric that survives class imbalance, and separate it from the online metric the business actually cares about.",
        ),
        SystemDesignStep(
            "Design the data and features",
            "Sources, volume, freshness, and how features are computed. Name which are batch and which are real time, and how the same code serves both — training/serving skew is the classic failure.",
        ),
        SystemDesignStep(
            "Pick a model and a baseline",
            "Start simple — logistic regression or gradient-boosted trees — and justify anything heavier by the data volume and latency budget. State how you would split train, validation and test without leakage.",
        ),
        SystemDesignStep(
            "Serve it",
            "Batch scoring versus online inference, latency budget, caching, and how the model is versioned and rolled back. Say where the model runs and what happens when it is unavailable.",
        ),
        SystemDesignStep(
            "Monitor and retrain",
            "Track input drift, prediction distribution and the online metric. Decide the retraining trigger and how new models are validated — shadow traffic, then A/B, then ramp.",
        ),
    ),
    concepts = listOf(
        SystemDesignConcept(
            "Feature Store", "Data",
            "A shared store of computed features, serving the same definitions to training and inference.",
            "Whenever features are non-trivial or reused across models. Its main job is eliminating training/serving skew.",
        ),
        SystemDesignConcept(
            "Training/Serving Skew", "Data",
            "The gap between how a feature is computed offline and online — different code, different time windows, different nulls.",
            "Name it explicitly in any design round; it is the most common reason a strong offline model underperforms in production.",
        ),
        SystemDesignConcept(
            "Data Leakage", "Data",
            "Information unavailable at prediction time leaking into training, inflating offline scores.",
            "Guard by splitting before any fitting, and by splitting on time for anything with a temporal ordering.",
        ),
        SystemDesignConcept(
            "Offline vs Online Metric", "Evaluation",
            "AUC or F1 measured on held-out data versus click-through, revenue or retention measured live.",
            "Always state both and how they connect. A model can improve offline and hurt the online metric.",
        ),
        SystemDesignConcept(
            "Batch vs Online Inference", "Serving",
            "Precomputing predictions on a schedule versus computing them per request.",
            "Batch when the input set is known and latency is loose; online when inputs arrive per request or freshness matters.",
        ),
        SystemDesignConcept(
            "Model Registry & Versioning", "Serving",
            "Tracks model artifacts with their training data, code and metrics, so any deployed model is reproducible.",
            "Needed the moment more than one model version exists — it is what makes rollback possible.",
        ),
        SystemDesignConcept(
            "Shadow Deployment", "Serving",
            "Runs the new model on live traffic without using its output, purely to compare.",
            "Before any risky rollout — it surfaces latency and skew problems with no user impact.",
        ),
        SystemDesignConcept(
            "Drift Detection", "Monitoring",
            "Watches input feature distributions and prediction distributions for change over time.",
            "Any long-lived model. Data drift precedes accuracy loss, and it is visible without waiting for labels.",
        ),
        SystemDesignConcept(
            "Feedback Loop", "Monitoring",
            "The model's own predictions shape the data it later trains on — recommendations create the clicks they learn from.",
            "Call it out in ranking and recommendation designs, and propose exploration or randomised holdouts to break it.",
        ),
        SystemDesignConcept(
            "Two-Stage Retrieval & Ranking", "Architecture",
            "A cheap model retrieves a few hundred candidates from millions; an expensive model ranks them.",
            "Any recommendation or search design — scoring the whole catalogue per request is infeasible under a latency budget.",
        ),
        SystemDesignConcept(
            "Cold Start", "Architecture",
            "New users or items have no interaction history for the model to use.",
            "Recommenders and personalisation. Fall back to content features or popularity until signal accumulates.",
        ),
        SystemDesignConcept(
            "Quantisation & Distillation", "Serving",
            "Shrinking a model by lowering numeric precision or training a small model to imitate a large one.",
            "When the latency or cost budget will not fit the model that scores best offline.",
        ),
        SystemDesignConcept(
            "Label Delay & Proxy Labels", "Data",
            "Ground truth that arrives days later (a chargeback, a churn event), so a proxy signal stands in meanwhile.",
            "Fraud, churn and long-horizon predictions. Say how long the delay is and what the proxy costs you in bias.",
        ),
        SystemDesignConcept(
            "Class Imbalance Handling", "Data",
            "Resampling, class weights or threshold tuning when positives are a fraction of a percent.",
            "Fraud, spam, rare-disease and safety models. Always pair it with a metric that survives imbalance — never accuracy.",
        ),
        SystemDesignConcept(
            "Embedding Store & ANN Index", "Architecture",
            "Vectors served through an approximate nearest-neighbour index (HNSW, IVF) rather than exact search.",
            "Retrieval, semantic search and RAG. Name the recall/latency knob and how the index is rebuilt as embeddings change.",
        ),
        SystemDesignConcept(
            "Human-in-the-Loop Review", "Monitoring",
            "Low-confidence or high-impact predictions route to a reviewer, whose decisions become training labels.",
            "Moderation, medical and financial decisions. It gives both a safety net and a cheap stream of high-quality labels.",
        ),
        SystemDesignConcept(
            "Guardrails & Fallbacks", "Serving",
            "Rules, filters and a default response wrapping the model for when it is wrong, slow or unavailable.",
            "Any user-facing model, and mandatory for LLM designs. State what the system returns when inference times out.",
        ),
        SystemDesignConcept(
            "Experiment Design (A/B)", "Evaluation",
            "Randomised assignment, a pre-declared primary metric, and enough traffic to detect the expected effect.",
            "Every model rollout. Bring up sample size, guardrail metrics and the risk of peeking at results early.",
        ),
    ),
)
