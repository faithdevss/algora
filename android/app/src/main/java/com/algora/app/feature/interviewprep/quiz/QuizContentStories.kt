package com.algora.app.feature.interviewprep.quiz

import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureSpan
import com.algora.app.core.data.model.FigureStack

// Story rounds: an interviewer narrates a situation — a person, a product, a problem that just
// landed — then asks several questions inside that one world. Each story and its figure are shared
// by the questions that follow, the way a real case interview keeps building on the same whiteboard.

// ---- QuickBite (beginner) --------------------------------------------------------------------

private val lunchRush = QuizStory(
    title = "QuickBite · The lunch rush",
    text = "Maya is the first engineer at QuickBite, a food-delivery startup. Every order has an id, a " +
        "restaurant and the minute it was placed. At lunch the app takes 50,000 orders, and three teams " +
        "come to Maya with requests.",
)

private val lunchRushFigure = Figure(
    caption = "Orders per minute, 12:00 to 12:05",
    shape = FigureShape.Strip(
        cells = listOf("12", "30", "45", "41", "28", "9"),
        aux = listOf("12:00", "12:01", "12:02", "12:03", "12:04", "12:05"),
        auxLabel = "minute",
    ),
)

private val delivery = QuizStory(
    title = "QuickBite · Getting the food there",
    text = "Rider Sam picks up at restaurant R and delivers to a customer at C. The map shows the streets " +
        "between them, each labelled with the minutes it takes to ride.",
)

private val deliveryFigure = Figure(
    caption = "Street map, minutes per street",
    shape = FigureShape.Graph(
        nodes = listOf(
            FigureGraphNode("R", 0.08f, 0.5f),
            FigureGraphNode("X", 0.42f, 0.16f),
            FigureGraphNode("Y", 0.3f, 0.84f),
            FigureGraphNode("Z", 0.66f, 0.66f),
            FigureGraphNode("C", 0.92f, 0.3f),
        ),
        edges = listOf(
            FigureEdge(0, 1, "4"), FigureEdge(0, 2, "2"), FigureEdge(2, 1, "1"),
            FigureEdge(1, 4, "5"), FigureEdge(2, 3, "7"), FigureEdge(3, 4, "1"),
            FigureEdge(1, 3, "2"),
        ),
    ),
)

private val shifts = QuizStory(
    title = "QuickBite · Tomorrow's rider shifts",
    text = "Priya plans tomorrow's rider shifts on a 24-hour clock. Customers can order from 8:00 to " +
        "22:00, and the timeline shows each rider's shift.",
)

private val shiftsFigure = Figure(
    caption = "Rider shifts, hour of the day",
    shape = FigureShape.Timeline(
        spans = listOf(
            FigureSpan(8, 14, "Ana 8–14"),
            FigureSpan(11, 17, "Ben 11–17"),
            FigureSpan(12, 15, "Cal 12–15"),
            FigureSpan(16, 18, "Eve 16–18"),
            FigureSpan(19, 22, "Dev 19–22"),
        ),
        axisMax = 24,
    ),
)

internal val beginnerStorySet = Quiz(
    id = "beginner_story_set",
    title = "Story Round: The Food Delivery App",
    description = "Three stories from a food-delivery startup — orders, routes and shifts — with questions on each.",
    questions = listOf(
        QuizQuestion(
            prompt = "Support needs to pull up any order by its id instantly, while the customer waits on the phone. How should Maya store the orders?",
            options = listOf(
                "A hash map from order id to order — O(1) average lookup",
                "A plain list, scanned from the start",
                "A list sorted by restaurant name",
                "A stack with the newest order on top",
            ),
            correctIndex = 0,
            patternTag = "Story · Hashing",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: the question is always \"give me order #id\", which is exactly what a hash map answers in O(1) on average. A scan checks up to 50,000 orders per call; sorting by restaurant does not help a lookup by id; a stack can only show its top.",
            linkedTopicId = "hash_table",
            linkedTopicLabel = "Hash Table / Hash Map",
            figure = lunchRushFigure,
            story = lunchRush,
        ),
        QuizQuestion(
            prompt = "Each kitchen must see its orders in exactly the order they were placed, and remove each one when it is cooked. Which structure?",
            options = listOf(
                "A queue per kitchen — first in, first out",
                "A stack per kitchen — newest first",
                "A hash set of order ids",
                "A max-heap keyed by order value",
            ),
            correctIndex = 0,
            patternTag = "Story · Queue",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: new orders join the back; the cook takes from the front. Both are O(1), and the oldest order is always served first — which is what \"in the order they were placed\" means. A stack would make the first customer wait for everyone after them.",
            linkedTopicId = "queue",
            linkedTopicLabel = "Queue",
            figure = lunchRushFigure,
            story = lunchRush,
        ),
        QuizQuestion(
            prompt = "Finance keeps asking how many orders arrived between two minutes. From the figure, how many arrived from 12:01 to 12:03 inclusive — and how should Maya answer thousands of these questions?",
            options = listOf(
                "116 — build prefix sums once; each question is one subtraction",
                "128 — adding 12:00 to 12:03",
                "71 — adding only the two end minutes",
                "153 — adding 12:01 to the end",
            ),
            correctIndex = 0,
            patternTag = "Story · Prefix Sum",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: 30 + 45 + 41 = 116. With prefix = [0, 12, 42, 87, 128, 156, 165], minutes 1 to 3 are prefix[4] − prefix[1] = 128 − 12 = 116. Building it once is O(n), and every later question is O(1) instead of re-adding the range.",
            linkedTopicId = "prefix_sum",
            linkedTopicLabel = "Prefix Sum",
            figure = lunchRushFigure,
            story = lunchRush,
        ),
        QuizQuestion(
            prompt = "At the end of the day, Maya must show the 3 restaurants with the most orders, out of 2,000 restaurants. Best approach?",
            options = listOf(
                "Count orders per restaurant in a hash map, then keep a size-3 min-heap",
                "Sort all 50,000 orders by time",
                "Show the 3 restaurants that got the earliest orders",
                "Binary search the list of orders",
            ),
            correctIndex = 0,
            patternTag = "Story · Top-K",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: one pass fills counts[restaurant]++. Then push each (restaurant, count) onto a min-heap and pop when it holds more than 3. The heap's root is the weakest of the current top 3. O(n + r log 3). Sorting orders by time says nothing about how many each restaurant got.",
            linkedTopicId = "top_k_pattern",
            linkedTopicLabel = "Top-K Pattern",
            figure = lunchRushFigure,
            story = lunchRush,
        ),
        QuizQuestion(
            prompt = "What is Sam's fastest route from R to C?",
            options = listOf(
                "6 minutes, via R–Y–X–Z–C",
                "7 minutes, via R–X–Z–C",
                "8 minutes, via R–Y–X–C",
                "9 minutes, via R–X–C",
            ),
            correctIndex = 0,
            patternTag = "Story · Dijkstra",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve with Dijkstra from R: Y = 2, then X = min(4, 2 + 1) = 3, then Z = min(2 + 7, 3 + 2) = 5, then C = min(3 + 5, 5 + 1) = 6. The route with the fewest streets (R–X–C) is the slowest of the four.",
            linkedTopicId = "dijkstras_algorithm",
            linkedTopicLabel = "Dijkstra's Algorithm",
            figure = deliveryFigure,
            story = delivery,
        ),
        QuizQuestion(
            prompt = "A bug makes the app count streets instead of minutes. Which route would it pick, and why is that wrong here?",
            options = listOf(
                "R–X–C, just 2 streets — BFS ignores minutes, so Sam rides 9 minutes instead of 6",
                "R–Y–X–Z–C — BFS always finds the fastest route",
                "R–Y–Z–C, 3 streets — it is the first route BFS reaches",
                "None — BFS cannot run on a street map",
            ),
            correctIndex = 0,
            patternTag = "Story · BFS",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: BFS finds the fewest edges, which here is R–X–C with 2 streets. That is only the fastest route when every street takes the same time. With weights, use Dijkstra — the 4-street route wins at 6 minutes.",
            linkedTopicId = "bfs",
            linkedTopicLabel = "Breadth-First Search (BFS)",
            figure = deliveryFigure,
            story = delivery,
        ),
        QuizQuestion(
            prompt = "The street between X and Z closes for repairs. What is the fastest route now?",
            options = listOf(
                "8 minutes, via R–Y–X–C",
                "6 minutes — nothing changes",
                "9 minutes, via R–X–C",
                "10 minutes, via R–Y–Z–C",
            ),
            correctIndex = 0,
            patternTag = "Story · Shortest Path",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: without X–Z, Z can only be reached from Y (2 + 7 = 9), so C through Z costs 10. C through X costs 3 + 5 = 8. Remove the edge and run Dijkstra again: 8 via R–Y–X–C.",
            linkedTopicId = "dijkstras_algorithm",
            linkedTopicLabel = "Dijkstra's Algorithm",
            figure = deliveryFigure,
            story = delivery,
        ),
        QuizQuestion(
            prompt = "What is the largest number of riders on duty at the same time?",
            options = listOf(
                "3 — from 12:00 to 14:00",
                "2",
                "4",
                "5 — one for each rider",
            ),
            correctIndex = 0,
            patternTag = "Story · Sweep Line",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: sweep through the day, adding one at each shift start and removing one at each end. From 12:00 to 14:00 Ana, Ben and Cal all work — 3. No other moment has more than 2.",
            linkedTopicId = "sweep_line_pattern",
            linkedTopicLabel = "Line Sweep & Difference Array",
            figure = shiftsFigure,
            story = shifts,
        ),
        QuizQuestion(
            prompt = "Is there any time between 8:00 and 22:00 when no rider is on duty?",
            options = listOf(
                "Yes — from 18:00 to 19:00",
                "No — the shifts cover the whole day",
                "Yes — from 14:00 to 16:00",
                "Yes — from 17:00 to 18:00",
            ),
            correctIndex = 0,
            patternTag = "Story · Merge Intervals",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: sort by start and merge. 8–14, 11–17 and 12–15 merge into 8–17; 16–18 overlaps, giving 8–18. Dev starts at 19, so 18:00–19:00 has nobody. From 14 to 16 Ben is still working, and from 17 to 18 Eve is.",
            linkedTopicId = "merge_intervals_pattern",
            linkedTopicLabel = "Merge Intervals",
            figure = shiftsFigure,
            story = shifts,
        ),
        QuizQuestion(
            prompt = "Priya wants the app to reject any new shift that overlaps one the same rider already has. Riders can have many shifts. What should the check do?",
            options = listOf(
                "Keep each rider's shifts sorted by start; the new one fits if it ends before the next shift starts and starts after the previous one ends",
                "Compare it only with the rider's first shift",
                "Check that the rider's total hours stay under 24",
                "Check that no other rider works at that time",
            ),
            correctIndex = 0,
            patternTag = "Story · Intervals",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: existing shifts never overlap each other, so only the two neighbours of the new start time can clash. With a sorted structure (a TreeMap of start → end), find them in O(log n) and check prev.end ≤ start and end ≤ next.start. Other riders are irrelevant — they are different people.",
            linkedTopicId = "avl_red_black_tree",
            linkedTopicLabel = "AVL / Red-Black Tree",
            figure = shiftsFigure,
            story = shifts,
        ),
    ),
)

// ---- StreamFlix (advanced) -------------------------------------------------------------------

private val outage = QuizStory(
    title = "StreamFlix · The 3 a.m. page",
    text = "Rahim is on call at StreamFlix, a video-streaming service. At 3 a.m. the database (DB) goes " +
        "down. The diagram shows which service needs which: Gate is the API gateway, Cat the catalog, " +
        "Play the video player. An arrow from A to B means A needs B to work.",
)

private val outageFigure = Figure(
    caption = "Service dependencies (A → B: A needs B)",
    shape = FigureShape.Graph(
        nodes = listOf(
            FigureGraphNode("Gate", 0.1f, 0.5f),
            FigureGraphNode("Auth", 0.42f, 0.16f),
            FigureGraphNode("Cat", 0.42f, 0.84f),
            FigureGraphNode("DB", 0.86f, 0.16f),
            FigureGraphNode("Cache", 0.86f, 0.84f),
            FigureGraphNode("Play", 0.94f, 0.5f),
        ),
        edges = listOf(
            FigureEdge(0, 1, directed = true),
            FigureEdge(0, 2, directed = true),
            FigureEdge(1, 3, directed = true),
            FigureEdge(2, 3, directed = true),
            FigureEdge(2, 4, directed = true),
            FigureEdge(5, 4, directed = true),
        ),
    ),
)

private val spike = QuizStory(
    title = "StreamFlix · The error spike",
    text = "While the database recovers, Rahim watches the errors-per-minute dashboard. Before writing " +
        "the incident report, he has a few questions about the numbers.",
)

private val spikeFigure = Figure(
    caption = "Errors per minute",
    shape = FigureShape.Strip(
        cells = listOf("2", "3", "40", "38", "5", "41", "2"),
        aux = listOf("3:00", "3:01", "3:02", "3:03", "3:04", "3:05", "3:06"),
        auxLabel = "minute",
    ),
)

private val cache = QuizStory(
    title = "StreamFlix · The cache",
    text = "To take load off the database, Rahim adds a cache that holds 3 videos and evicts the least " +
        "recently used one when it is full. The figure shows the cache right now, most recently used on top.",
)

private val cacheFigure = Figure(
    caption = "Most recently used first",
    shape = FigureShape.Stacks(
        columns = listOf(FigureStack("cache (3 slots)", listOf("C", "B", "A"))),
    ),
)

internal val advancedStorySet = Quiz(
    id = "advanced_story_set",
    title = "Story Round: On Call at StreamFlix",
    description = "An outage, an error spike and a new cache — one engineer's night, with questions on each part.",
    questions = listOf(
        QuizQuestion(
            prompt = "Which services are broken because the database is down?",
            options = listOf(
                "Gate, Auth and Cat — everything with a path to DB",
                "Only Auth and Cat — the services that call DB directly",
                "Every service",
                "Gate, Auth, Cat and Play",
            ),
            correctIndex = 0,
            patternTag = "Story · Graph Traversal",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: reverse the arrows and BFS from DB. DB's callers are Auth and Cat; their caller is Gate. Play only needs Cache, which is healthy, so videos that are already cached keep playing. Stopping at direct callers misses Gate, which fails because Auth and Cat do.",
            linkedTopicId = "bfs",
            linkedTopicLabel = "Breadth-First Search (BFS)",
            figure = outageFigure,
            story = outage,
        ),
        QuizQuestion(
            prompt = "After the fix, services restart one at a time, each only once everything it needs is up. Which order works?",
            options = listOf(
                "DB, Cache, Auth, Cat, Play, Gate",
                "Gate, Auth, Cat, DB, Cache, Play",
                "DB, Auth, Gate, Cache, Cat, Play",
                "Cache, Play, Cat, DB, Auth, Gate",
            ),
            correctIndex = 0,
            patternTag = "Story · Topological Sort",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: this is a topological order of the reversed graph — dependencies first. Check each arrow: Auth and Cat after DB, Cat and Play after Cache, Gate after Auth and Cat. DB, Auth, Gate… starts Gate before Cat is up; Cache, Play, Cat… starts Cat before DB.",
            linkedTopicId = "topological_sort",
            linkedTopicLabel = "Topological Sort",
            figure = outageFigure,
            story = outage,
        ),
        QuizQuestion(
            prompt = "A teammate proposes that DB should call Gate to report its health. What does that do to the restart plan?",
            options = listOf(
                "It creates a cycle Gate → Auth → DB → Gate, so no valid restart order exists",
                "Nothing — it is just one more arrow",
                "DB now simply has to start last",
                "Gate can now start without Auth",
            ),
            correctIndex = 0,
            patternTag = "Story · Cycle Detection",
            difficulty = Difficulty.ADVANCED,
            explanation = "Solve: every service on the loop now needs another service on the loop to be up first, so none of them can start first — there is no topological order. Find it with DFS: reaching a node still on the current path means a cycle. The usual fix is to make the health report asynchronous, so DB does not need Gate.",
            linkedTopicId = "graph_coloring_pattern",
            linkedTopicLabel = "Two-Colouring & Cycle Detection",
            figure = outageFigure,
            story = outage,
        ),
        QuizQuestion(
            prompt = "What is the most errors in any 3 consecutive minutes?",
            options = listOf(
                "84 — from 3:03 to 3:05",
                "83 — from 3:02 to 3:04",
                "119 — the three worst minutes",
                "81 — from 3:01 to 3:03",
            ),
            correctIndex = 0,
            patternTag = "Story · Sliding Window",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: slide a 3-minute window, adding the minute that enters and subtracting the one that leaves. The sums are 45, 81, 83, 84, 48 — the best is 38 + 5 + 41 = 84. The three worst minutes (41, 40, 38) add up to 119, but they are not consecutive.",
            linkedTopicId = "sliding_window",
            linkedTopicLabel = "Sliding Window",
            figure = spikeFigure,
            story = spike,
        ),
        QuizQuestion(
            prompt = "For the report, what is the median number of errors per minute across these 7 minutes?",
            options = listOf(
                "5",
                "18.7 — the average",
                "38",
                "3",
            ),
            correctIndex = 0,
            patternTag = "Story · Statistics",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: sorted, the minutes are 2, 2, 3, 5, 38, 40, 41; the middle (4th) value is 5. The average is 131 / 7 ≈ 18.7, pulled up by the three spike minutes — which is why incident reports quote the median for a \"typical\" minute. Quickselect finds it in O(n) average without sorting.",
            linkedTopicId = "quickselect",
            linkedTopicLabel = "Quickselect",
            figure = spikeFigure,
            story = spike,
        ),
        QuizQuestion(
            prompt = "A known bug produces exactly 79 errors split across two minutes. Do any two minutes add up to 79, and how do you check in one pass?",
            options = listOf(
                "Yes — 38 and 41; keep a set of values seen so far and look up 79 − x",
                "No — no two minutes add up to 79",
                "Yes — 40 and 38",
                "Yes — 40 and 41",
            ),
            correctIndex = 0,
            patternTag = "Story · Hashing",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: walk the minutes; for each x, check whether 79 − x is in the seen set, then add x. At 41 the set already holds 38, so 38 + 41 = 79. 40 + 38 = 78 and 40 + 41 = 81 are close but wrong. O(n) time — the Two Sum pattern.",
            linkedTopicId = "hash_table",
            linkedTopicLabel = "Hash Table / Hash Map",
            figure = spikeFigure,
            story = spike,
        ),
        QuizQuestion(
            prompt = "A viewer requests video A, then a new video D. Which video gets evicted?",
            options = listOf(
                "B",
                "A — it was at the bottom",
                "C",
                "D — the newest one is dropped",
            ),
            correctIndex = 0,
            patternTag = "Story · LRU Cache",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: requesting A is a hit, so A moves to the top: A, C, B. D is a miss and the cache is full, so the least recently used video — now B at the bottom — is evicted. A was at the bottom a moment ago, but using it made it the most recent.",
            linkedTopicId = "lru_cache",
            linkedTopicLabel = "LRU Cache",
            figure = cacheFigure,
            story = cache,
        ),
        QuizQuestion(
            prompt = "After those two requests (A, then D), what does the cache hold, most recent first?",
            options = listOf(
                "D, A, C",
                "D, C, B",
                "A, D, C",
                "D, B, C",
            ),
            correctIndex = 0,
            patternTag = "Story · LRU Cache",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: C, B, A → request A → A, C, B → add D, evict B → D, A, C. Each access moves its item to the front, and eviction always takes the item at the back.",
            linkedTopicId = "lru_cache",
            linkedTopicLabel = "LRU Cache",
            figure = cacheFigure,
            story = cache,
        ),
        QuizQuestion(
            prompt = "From that state (D, A, C), the next requests are C, E, A. How many of those three are cache hits?",
            options = listOf(
                "1 — C hits; A was evicted just before it came back",
                "2 — C and A both hit",
                "0",
                "3",
            ),
            correctIndex = 0,
            patternTag = "Story · LRU Cache",
            difficulty = Difficulty.ADVANCED,
            explanation = "Solve: C hits → C, D, A. E misses and evicts A → E, C, D. A misses → A, E, C. Only one hit. A was in the cache two requests earlier, but E pushed it out — this is how a cache that is too small thrashes.",
            linkedTopicId = "lru_cache",
            linkedTopicLabel = "LRU Cache",
            figure = cacheFigure,
            story = cache,
        ),
        QuizQuestion(
            prompt = "Traffic grows, and 3 slots are far too few. Rahim measures hit rates: 10 slots → 60%, 100 → 90%, 1,000 → 92%. Which size should he choose?",
            options = listOf(
                "About 100 — beyond that, 10× more memory buys only 2 more points",
                "1,000 — always take the highest hit rate",
                "10 — the smallest cache is cheapest",
                "It cannot be decided without doubling to 2,000",
            ),
            correctIndex = 0,
            patternTag = "Story · Trade-offs",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: going from 10 to 100 slots cuts DB traffic from 40% to 10% of requests — four times less load. Going from 100 to 1,000 only cuts it from 10% to 8%, for ten times the memory. Popularity is skewed (a few videos get most views), so returns flatten quickly; pick the knee of the curve and say why.",
            figure = cacheFigure,
            story = cache,
        ),
    ),
)
