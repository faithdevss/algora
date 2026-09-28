package com.algora.app.feature.interviewprep.systemdesign

internal val systemDesignPrimer = SystemDesignPrimer(
    id = "system_design_primer",
    title = "System Design Primer",
    description = "How to drive a design round, plus the building blocks you'll assemble — and when each one earns its place.",
    framework = listOf(
        SystemDesignStep("Clarify requirements", "Pin down functional needs and non-functional targets (scale, latency, availability) before drawing anything. Ask; don't assume."),
        SystemDesignStep("Estimate scale", "Back-of-envelope the QPS, data volume, and bandwidth. The numbers decide whether one box or a fleet is the answer."),
        SystemDesignStep("Sketch the API", "Name the core endpoints and their inputs/outputs. It anchors the rest of the design in concrete operations."),
        SystemDesignStep("Design the data model", "Choose storage and schema for the access patterns — reads vs writes, relational vs key-value, hot vs cold."),
        SystemDesignStep("Scale and defend", "Add caching, replication, sharding, and queues where the numbers demand them, and state the trade-offs you're accepting."),
    ),
    concepts = listOf(
        SystemDesignConcept(
            "Load Balancer", "Networking",
            "Spreads incoming requests across a pool of servers and routes around unhealthy ones.",
            "Any time you scale horizontally — it removes the single-server hotspot and enables rolling deploys.",
        ),
        SystemDesignConcept(
            "Caching", "Scaling",
            "Keeps hot data in fast storage (in-memory / CDN) close to where it's read.",
            "Read-heavy workloads with repeated queries. The hard part is invalidation — decide TTL vs write-through up front.",
        ),
        SystemDesignConcept(
            "Database Sharding", "Storage",
            "Partitions data across nodes by a shard key so no single database holds everything.",
            "When one database can't hold the volume or serve the write throughput. Pick a key that spreads load evenly.",
        ),
        SystemDesignConcept(
            "Replication", "Reliability",
            "Maintains copies of data for failover and read scaling (leader/follower or multi-leader).",
            "High availability and read-heavy scaling. Accept that followers lag — reads may be slightly stale.",
        ),
        SystemDesignConcept(
            "Message Queue", "Scaling",
            "Decouples producers from consumers, buffering work to be processed asynchronously.",
            "Spiky load, long-running or async tasks, and smoothing bursts so downstream systems aren't overwhelmed.",
        ),
        SystemDesignConcept(
            "CAP Theorem", "Reliability",
            "Under a network partition you can keep either consistency or availability, not both.",
            "Framing the core trade-off. Partitions are inevitable, so state whether the system is CP or AP and why.",
        ),
        SystemDesignConcept(
            "CDN", "Networking",
            "Edge servers cache static assets geographically close to users.",
            "Global delivery of static or media content — cuts latency and offloads origin bandwidth.",
        ),
        SystemDesignConcept(
            "Rate Limiting", "Reliability",
            "Caps how many requests a client may make in a window (token bucket, sliding window).",
            "Protecting the system from abuse, runaway clients, and thundering-herd overload.",
        ),
        SystemDesignConcept(
            "Consistent Hashing", "Storage",
            "Maps keys to nodes on a ring so adding/removing a node reshuffles only a small slice of keys.",
            "Distributed caches and shard routing where nodes come and go without a full rebalance.",
        ),
        SystemDesignConcept(
            "Indexing", "Storage",
            "A secondary structure that speeds lookups at the cost of extra writes and space.",
            "Frequent reads that filter or sort on a column. Every index you add taxes writes — don't over-index.",
        ),
        SystemDesignConcept(
            "Idempotency", "Reliability",
            "A repeated request has the same effect as a single one, usually via a client-supplied key.",
            "Anywhere retries exist — payments, webhooks, queue consumers. It is what makes at-least-once delivery survivable.",
        ),
        SystemDesignConcept(
            "Circuit Breaker", "Reliability",
            "Stops calling a failing dependency for a cooling-off period instead of retrying into the fire.",
            "Any synchronous call to a service that can degrade. Without it, retries turn one slow dependency into a cascading outage.",
        ),
        SystemDesignConcept(
            "Write-Ahead Log", "Storage",
            "Appends every change to a durable sequential log before applying it to the main structure.",
            "Durability and crash recovery, and the same log doubles as the replication stream and a change-data-capture feed.",
        ),
        SystemDesignConcept(
            "Leader Election", "Reliability",
            "Consensus (Raft/Paxos, or a coordination service) picks one node to accept writes.",
            "Any replicated store or scheduler that needs a single writer. State how a failover is detected and what happens to in-flight writes.",
        ),
        SystemDesignConcept(
            "Blob Store + Metadata DB", "Storage",
            "Large objects live in object storage; the database holds only pointers and attributes.",
            "Media, uploads and attachments. Keeping megabyte payloads out of the primary database is what keeps its working set cacheable.",
        ),
        SystemDesignConcept(
            "Fan-out on Write vs Read", "Architecture",
            "Push a new item into every follower's feed at write time, or assemble the feed per request.",
            "Feed and notification designs. Write fan-out gives fast reads but explodes for celebrity accounts — real systems mix both.",
        ),
        SystemDesignConcept(
            "Backpressure", "Scaling",
            "Signals producers to slow down when consumers or queues fall behind, instead of buffering forever.",
            "Any pipeline with variable load. Without it, an unbounded queue converts a throughput problem into an out-of-memory crash.",
        ),
        SystemDesignConcept(
            "Bloom Filter", "Storage",
            "A compact probabilistic membership test: no false negatives, some false positives.",
            "Skipping expensive lookups — checking whether an SSTable or a cache could possibly hold a key before reading it.",
        ),
    ),
)
