package com.algora.app.feature.review

// Curated decks that sit alongside the takeaway cards generated from topic content. A takeaway card
// asks the generic "recall a key takeaway"; these carry their own question, so they drill the facts
// an interview actually asks for — complexities, pattern triggers, metric definitions — rather than
// whatever sentence a topic happened to end on. Both kinds flow through the same SM-2 scheduler.

data class Flashcard(val front: String, val back: String)

data class FlashcardDeck(val id: String, val name: String, val cards: List<Flashcard>)

private val complexityDeck = FlashcardDeck(
    id = "deck_complexity",
    name = "Complexity Cheat Sheet",
    cards = listOf(
        Flashcard("Binary search on a sorted array — time and space?", "O(log n) time, O(1) space iteratively (O(log n) stack if recursive)."),
        Flashcard("Merge sort — time, space, stability?", "O(n log n) always, O(n) extra space, stable."),
        Flashcard("Quicksort — average, worst, space?", "O(n log n) average, O(n²) worst on bad pivots, O(log n) stack. Not stable."),
        Flashcard("Heap sort — time, space, stability?", "O(n log n) always, O(1) extra space, not stable."),
        Flashcard("Building a heap from an unsorted array — cost?", "O(n), not O(n log n) — most nodes sift down only a level or two."),
        Flashcard("Hash table lookup — average and worst case?", "O(1) average, O(n) worst when every key collides into one bucket."),
        Flashcard("Appending to a dynamic array — amortized cost, and why?", "O(1) amortized: doubling makes n appends cost under 2n total work."),
        Flashcard("BFS and DFS on a graph — time and space?", "O(V + E) time. BFS space is the widest level; DFS space is the deepest path."),
        Flashcard("Dijkstra with a binary heap — time?", "O((V + E) log V). Fails on negative edge weights — use Bellman-Ford, O(V·E)."),
        Flashcard("Balanced BST — search, insert, delete?", "O(log n) each. Unbalanced degrades to O(n) on sorted insertions."),
        Flashcard("Trie lookup of a word of length L?", "O(L), independent of how many words the trie holds."),
        Flashcard("0/1 knapsack DP — time and space?", "O(n·W) time, O(n·W) space, reducible to O(W) with a rolling row."),
        Flashcard("Counting sort — time, and when is it usable?", "O(n + k) for k distinct keys — only when the key range is small and integral."),
        Flashcard("Quickselect for the kth smallest — average and worst?", "O(n) average, O(n²) worst. Median-of-medians makes it O(n) worst-case."),
    ),
)

private val patternDeck = FlashcardDeck(
    id = "deck_patterns",
    name = "Pattern Triggers",
    cards = listOf(
        Flashcard("Which pattern: longest/shortest contiguous subarray meeting a condition?", "Sliding window — expand right, shrink left while the condition holds."),
        Flashcard("Which pattern: a pair summing to a target in a sorted array, O(1) space?", "Two pointers from both ends — move left up when the sum is small, right down when large."),
        Flashcard("Which pattern: detect a cycle in a linked list with O(1) space?", "Fast & slow pointers — they meet inside the cycle."),
        Flashcard("Which pattern: overlapping intervals to merge or a room count?", "Merge intervals — sort by start, then merge or sweep the endpoints."),
        Flashcard("Which pattern: k largest / k most frequent elements?", "Top-K with a size-k heap — O(n log k) instead of a full sort."),
        Flashcard("Which pattern: many range-sum queries on a static array?", "Prefix sums — sum(l..r) = prefix[r+1] - prefix[l]."),
        Flashcard("Which pattern: minimise a maximum (capacity, speed, time) with a monotone feasibility check?", "Binary search on the answer, not on the array."),
        Flashcard("Which pattern: next greater or previous smaller element?", "Monotonic stack — one pass, each element pushed and popped once."),
        Flashcard("Which pattern: find missing/duplicate values in 1..n in O(1) space?", "Cyclic sort — swap each value to its index, then scan for the mismatch."),
        Flashcard("Which pattern: merge k sorted lists?", "K-way merge with a size-k heap of the current heads — O(N log k)."),
        Flashcard("Which pattern: maximum non-overlapping activities?", "Greedy intervals — sort by end time and take the earliest finisher."),
        Flashcard("Which pattern: generate all permutations, subsets or valid boards?", "Backtracking — choose, explore, un-choose, and prune early."),
        Flashcard("Which pattern: tasks with prerequisites, or detect a cycle in a DAG?", "Topological sort — Kahn's in-degree queue or DFS post-order."),
        Flashcard("Which pattern: connected components with dynamic merges?", "Union-Find with path compression and union by rank."),
        Flashcard("Which pattern: count regions in a grid?", "Matrix traversal / flood fill — BFS or DFS from each unvisited cell."),
        Flashcard("Which pattern: level-by-level tree output?", "Tree BFS — a queue, processing one full level per outer iteration."),
    ),
)

private val dataStructureDeck = FlashcardDeck(
    id = "deck_ds_choices",
    name = "Data Structure Choices",
    cards = listOf(
        Flashcard("Array vs linked list — the real trade-off?", "Array: O(1) random access, O(n) middle insert. Linked list: O(1) splice given the node, no random access, worse cache locality."),
        Flashcard("When does a hash map beat a balanced BST?", "When you need only point lookups. A BST wins when you need ordering: range queries, successor, sorted iteration."),
        Flashcard("What does a heap give you that a sorted array does not?", "O(log n) insert with O(1) access to the min/max, without maintaining full order."),
        Flashcard("When is a deque the right structure?", "Push/pop at both ends in O(1) — sliding-window maxima, BFS with 0/1 weights."),
        Flashcard("What problem does a Fenwick (BIT) tree solve?", "Prefix sums with point updates, both O(log n), in far less code and space than a segment tree."),
        Flashcard("Segment tree vs Fenwick tree?", "Segment tree handles any associative range query (min, max, gcd) and range updates with lazy propagation; Fenwick is smaller but essentially sum-only."),
        Flashcard("What is a Bloom filter's guarantee?", "No false negatives, some false positives, and no deletions — a cheap membership pre-filter."),
        Flashcard("How does an LRU cache reach O(1) for get and put?", "Hash map to nodes plus a doubly linked list for recency; both ends splice in constant time."),
        Flashcard("Why does a BST degrade to O(n)?", "Sorted insertions make it a linked list. AVL / red-black rotations keep the height O(log n)."),
        Flashcard("What is a disjoint-set union used for?", "Connectivity and grouping — Kruskal's MST, cycle detection in an undirected graph, dynamic components."),
        Flashcard("Adjacency list vs adjacency matrix?", "List: O(V + E) space, best for sparse graphs. Matrix: O(V²) space, O(1) edge lookup, fine for dense graphs."),
        Flashcard("Stack vs queue in traversal?", "Stack (or recursion) gives DFS; queue gives BFS, which finds the fewest-edge path in an unweighted graph."),
    ),
)

private val mlDeck = FlashcardDeck(
    id = "deck_ml_metrics",
    name = "ML Metrics & Concepts",
    cards = listOf(
        Flashcard("Precision vs recall — in one line each?", "Precision: of what I flagged, how much was right. Recall: of what was actually positive, how much I caught."),
        Flashcard("When is accuracy a misleading metric?", "Under class imbalance — 99.5% accuracy is what predicting the majority class alone scores."),
        Flashcard("What does F1 balance, and when is PR-AUC preferred over ROC-AUC?", "F1 is the harmonic mean of precision and recall. PR-AUC is more informative when positives are rare."),
        Flashcard("High bias vs high variance — the symptom?", "High bias: poor on train and test (underfit). High variance: great on train, poor on test (overfit)."),
        Flashcard("L1 vs L2 regularisation?", "L1 drives weights to exactly zero and selects features; L2 shrinks weights smoothly and handles correlated features better."),
        Flashcard("Why standardise features before PCA or k-means?", "Both use Euclidean distance/variance, so a large-unit feature would dominate purely because of its scale."),
        Flashcard("What is data leakage, and its most common cause?", "Information unavailable at prediction time entering training — usually fitting a transform or splitting after aggregation."),
        Flashcard("Bagging vs boosting?", "Bagging trains independent models in parallel to cut variance; boosting trains sequentially on prior errors to cut bias."),
        Flashcard("Why does the vanishing gradient problem happen, and one fix?", "Repeated multiplication by small derivatives through depth. Fixes: ReLU, residual connections, normalisation, LSTM gates."),
        Flashcard("What does dropout do at training and at inference?", "Randomly zeroes units during training to prevent co-adaptation; at inference it is off, with activations scaled to match."),
        Flashcard("Self-attention cost in sequence length n?", "O(n²·d) time and O(n²) memory — the reason long-context work targets exactly this term."),
        Flashcard("Why do transformers need positional encodings?", "Self-attention is permutation-invariant; without position information the model cannot tell word order."),
        Flashcard("Batch norm vs layer norm — when each?", "Batch norm normalises across the batch (vision, large batches); layer norm across features per example (transformers, variable-length sequences)."),
        Flashcard("Value-based vs policy-based RL?", "Value-based (Q-learning, DQN) learns action values and acts greedily; policy-based (REINFORCE, PPO) optimises the policy directly and handles continuous actions."),
        Flashcard("On-policy vs off-policy?", "On-policy learns from the current policy's data (SARSA, PPO); off-policy learns from other data, enabling replay buffers (Q-learning, DQN)."),
    ),
)

private val systemDesignDeck = FlashcardDeck(
    id = "deck_system_design",
    name = "System Design Recall",
    cards = listOf(
        Flashcard("CAP theorem in one sentence?", "Under a network partition you keep either consistency or availability — partitions are not optional, so pick CP or AP and say why."),
        Flashcard("What is the hard part of caching?", "Invalidation. Decide TTL vs write-through up front, and name the staleness the design accepts."),
        Flashcard("Why consistent hashing instead of hash mod N?", "Adding or removing a node remaps only ~1/N of keys instead of nearly all of them."),
        Flashcard("What does a message queue buy you?", "Decoupling and buffering — producers survive slow consumers, and spikes are absorbed instead of dropped."),
        Flashcard("Read replicas: what do you give up?", "Freshness. Followers lag the leader, so reads can be stale — fine for feeds, not for read-after-write."),
        Flashcard("How do you pick a shard key?", "Even load distribution and locality for the dominant query. A monotonic key (timestamp, auto-id) creates a hot shard."),
        Flashcard("Two common rate-limiting algorithms?", "Token bucket (allows bursts up to the bucket size) and sliding window (smoother, more state)."),
        Flashcard("What does idempotency give an API?", "Safe retries — a repeated request has the same effect as one, which is what makes at-least-once delivery workable."),
        Flashcard("First step of any design round?", "Clarify functional requirements and non-functional targets (scale, latency, availability), then estimate QPS and storage before drawing."),
        Flashcard("When is SQL the wrong default?", "When the access pattern is a huge single-key keyspace with no joins and write throughput exceeding one primary — but say what consistency you are trading away."),
        Flashcard("What is the point of a CDN?", "Serving static and media content from an edge near the user — lower latency, and origin bandwidth offloaded."),
        Flashcard("Training/serving skew — what is it and why does it matter?", "Features computed differently offline and online; it is the usual reason a strong offline model underperforms live."),
        Flashcard("Why two-stage retrieval and ranking?", "Scoring millions of candidates per request is infeasible — a cheap retriever narrows to hundreds, then an expensive ranker orders them."),
        Flashcard("How do you roll out a new model safely?", "Shadow traffic first, then an A/B test on the online metric, then a ramp — with versioned artifacts so rollback is one step."),
    ),
)

// Order matters only for stable keys; the review queue shuffles anyway.
val flashcardDecks: List<FlashcardDeck> = listOf(
    complexityDeck,
    patternDeck,
    dataStructureDeck,
    mlDeck,
    systemDesignDeck,
)

// Keys mirror the takeaway cards' "topicId#index" shape. Deck ids carry a "deck_" prefix, so a
// curated card can never collide with a topic's card in the SRS store.
fun curatedFlashcards(): List<ReviewCard> =
    flashcardDecks.flatMap { deck ->
        deck.cards.mapIndexed { i, card ->
            ReviewCard(
                key = "${deck.id}#$i",
                topicName = deck.name,
                takeaway = card.back,
                prompt = card.front,
            )
        }
    }
