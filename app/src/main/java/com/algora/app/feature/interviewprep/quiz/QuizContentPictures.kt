package com.algora.app.feature.interviewprep.quiz

import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureSpan
import com.algora.app.core.data.model.FigureStack

// Picture rounds: each question hands over a diagram — the graph, grid, tree or array an interviewer
// would sketch on the whiteboard — and asks for the result of working it. Figures stay in neutral
// tones (Primary / Muted); highlighting the answer in the picture would give the question away.

internal val beginnerPictureSet = Quiz(
    id = "beginner_picture_set",
    title = "Picture Round: Read the Diagram",
    description = "Whiteboard-style questions — look at the array, tree, grid or graph and work out the answer.",
    questions = listOf(
        QuizQuestion(
            prompt = "Two pointers on this sorted array, looking for a pair that sums to 9. Right now L + R = 2 + 15 = 17. What happens next?",
            options = listOf(
                "Move R left — the sum is too big",
                "Move L right — the sum is too big",
                "Stop — no pair can exist",
                "Move both pointers inward at once",
            ),
            correctIndex = 0,
            patternTag = "Picture · Two Pointer",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: moving L right can only make the sum bigger, so the only useful move is R left. 2 + 11 = 13, still too big, so R moves again: 2 + 7 = 9. The pair is at indices 0 and 1. Each step rules out one element, so the whole search is O(n).",
            linkedTopicId = "two_pointer",
            linkedTopicLabel = "Two Pointer Technique",
            figure = Figure(
                caption = "Sorted input, target 9",
                shape = FigureShape.Strip(
                    cells = listOf("2", "7", "11", "15"),
                    pointers = listOf(FigurePointer(0, "L"), FigurePointer(3, "R")),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Insert 5 into this binary search tree. Where does it go?",
            options = listOf(
                "Right child of 4",
                "Left child of 6",
                "Right child of 3",
                "Left child of 10",
            ),
            correctIndex = 0,
            patternTag = "Picture · BST",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: 5 < 8 go left to 3; 5 > 3 go right to 6; 5 < 6 go left to 4; 5 > 4 go right — that slot is empty, so 5 becomes 4's right child. Insertion always ends at an empty slot; the left of 6 and the right of 3 are already taken.",
            linkedTopicId = "binary_search_tree",
            linkedTopicLabel = "Binary Search Tree",
            figure = Figure(
                caption = "A BST — smaller keys to the left",
                shape = FigureShape.Tree(
                    nodes = listOf(
                        // Depth-first, left before right: the layout gives leaves slots in list
                        // order, so a level-order list would put 14 left of 4 and 7.
                        FigureNode("8", null),
                        FigureNode("3", 0),
                        FigureNode("1", 1),
                        FigureNode("6", 1),
                        FigureNode("4", 3),
                        FigureNode("7", 3),
                        FigureNode("10", 0),
                        FigureNode("14", 6),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Run BFS from A on this graph, visiting neighbours in alphabetical order. What is the visit order?",
            options = listOf(
                "A, B, C, D, E, F",
                "A, B, D, F, E, C",
                "A, C, E, F, D, B",
                "A, B, C, F, D, E",
            ),
            correctIndex = 0,
            patternTag = "Picture · BFS",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: queue [A] → visit A, enqueue B, C. Visit B, enqueue D. Visit C, enqueue E. Visit D, enqueue F. Visit E (F is already queued). Visit F. BFS finishes a whole level before going deeper. A, B, D, F, E, C is the DFS order.",
            linkedTopicId = "bfs",
            linkedTopicLabel = "Breadth-First Search (BFS)",
            figure = Figure(
                caption = "Undirected graph, start at A",
                shape = FigureShape.Graph(
                    nodes = listOf(
                        FigureGraphNode("A", 0.08f, 0.5f),
                        FigureGraphNode("B", 0.36f, 0.18f),
                        FigureGraphNode("C", 0.36f, 0.82f),
                        FigureGraphNode("D", 0.64f, 0.18f),
                        FigureGraphNode("E", 0.64f, 0.82f),
                        FigureGraphNode("F", 0.92f, 0.5f),
                    ),
                    edges = listOf(
                        FigureEdge(0, 1), FigureEdge(0, 2), FigureEdge(1, 3),
                        FigureEdge(2, 4), FigureEdge(3, 5), FigureEdge(4, 5),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Checking brackets for balance: after reading \"{([\" the stack looks like this. The next character is ')'. What happens?",
            options = listOf(
                "Invalid — ')' must match the top '[', and it does not",
                "Pop the '(' from the middle of the stack",
                "Push ')' onto the stack",
                "Skip ')' and keep reading",
            ),
            correctIndex = 0,
            patternTag = "Picture · Stack",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: a closer can only close the most recent opener — the top of the stack. The top is '[', so ')' fails right away and the string is invalid, whatever follows. A stack cannot reach into its middle, and that is exactly the rule nesting needs.",
            linkedTopicId = "stack",
            linkedTopicLabel = "Stack",
            figure = Figure(
                caption = "Open brackets so far, top first",
                shape = FigureShape.Stacks(
                    columns = listOf(FigureStack("stack", listOf("[", "(", "{"), note = "next: )")),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Merge the overlapping intervals shown on this timeline. What is the result?",
            options = listOf(
                "[1, 6], [8, 10], [15, 18]",
                "[1, 10], [15, 18]",
                "[1, 3], [2, 6], [8, 18]",
                "[1, 18]",
            ),
            correctIndex = 0,
            patternTag = "Picture · Intervals",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: sort by start. [1, 3] and [2, 6] overlap (2 ≤ 3), so they become [1, 6]. [8, 10] starts after 6, so it stands alone, and so does [15, 18]. Gaps between the blocks stay gaps — merging never fills them.",
            linkedTopicId = "merge_intervals_pattern",
            linkedTopicLabel = "Merge Intervals",
            figure = Figure(
                caption = "Four intervals on one axis",
                shape = FigureShape.Timeline(
                    spans = listOf(
                        FigureSpan(1, 3, "1–3"),
                        FigureSpan(2, 6, "2–6"),
                        FigureSpan(8, 10, "8–10"),
                        FigureSpan(15, 18, "15–18"),
                    ),
                    axisMax = 18,
                ),
            ),
        ),
        QuizQuestion(
            prompt = "1 is land and 0 is water. Land cells connect up, down, left and right (not diagonally). How many islands are there?",
            options = listOf(
                "3",
                "2 — diagonal cells count as touching",
                "9 — one per land cell",
                "4",
            ),
            correctIndex = 0,
            patternTag = "Picture · Grid DFS",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: scan the cells; on each unvisited 1, add one island and flood-fill it. The top-left 2×2 block is one island. The lone 1 in the middle is the second. The L-shape down the right edge is the third. The middle cell only touches that L-shape diagonally, which does not count here.",
            linkedTopicId = "matrix_islands_pattern",
            linkedTopicLabel = "Matrix Traversal (Islands)",
            figure = Figure(
                caption = "4 × 5 map",
                shape = FigureShape.Grid(
                    rows = listOf(
                        listOf("1", "1", "0", "0", "0"),
                        listOf("1", "1", "0", "0", "1"),
                        listOf("0", "0", "1", "0", "1"),
                        listOf("0", "0", "0", "1", "1"),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Binary search for 11. The first probe, mid, lands on 7. What is the next search range?",
            options = listOf(
                "lo moves to index 4 — search 9, 11, 13",
                "hi moves to index 2 — search 1, 3, 5",
                "lo moves to index 3 — search 7 onwards",
                "Stop — 11 is not at mid",
            ),
            correctIndex = 0,
            patternTag = "Picture · Binary Search",
            difficulty = Difficulty.BEGINNER,
            explanation = "Solve: 7 < 11, so the target can only be to the right: lo = mid + 1 = 4. The next mid is index 5, which holds 11 — found in 2 probes. Setting lo = mid instead of mid + 1 is the classic bug that loops forever once two elements remain.",
            linkedTopicId = "binary_search",
            linkedTopicLabel = "Binary Search",
            figure = Figure(
                caption = "Sorted array, target 11",
                shape = FigureShape.Strip(
                    cells = listOf("1", "3", "5", "7", "9", "11", "13"),
                    pointers = listOf(FigurePointer(0, "lo"), FigurePointer(3, "mid"), FigurePointer(6, "hi")),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "This linked list has a cycle. Floyd's slow and fast pointers both start at node 1. Which node is the cycle's entry?",
            options = listOf(
                "Node 3",
                "Node 5",
                "Node 1",
                "There is no cycle",
            ),
            correctIndex = 0,
            patternTag = "Picture · Fast & Slow",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: 5 points back to 3, so 3 is the first node visited twice. Floyd finds it in two phases: slow (1 step) and fast (2 steps) meet somewhere inside the loop; then restart one pointer at the head and step both one at a time — they meet at the entry. O(n) time, O(1) space.",
            linkedTopicId = "fast_slow_pointers",
            linkedTopicLabel = "Fast & Slow Pointers",
            figure = Figure(
                caption = "next pointers, head = 1",
                shape = FigureShape.Graph(
                    nodes = listOf(
                        FigureGraphNode("1", 0.08f, 0.5f),
                        FigureGraphNode("2", 0.3f, 0.5f),
                        FigureGraphNode("3", 0.52f, 0.5f),
                        FigureGraphNode("4", 0.86f, 0.18f),
                        FigureGraphNode("5", 0.86f, 0.82f),
                    ),
                    edges = listOf(
                        FigureEdge(0, 1, directed = true),
                        FigureEdge(1, 2, directed = true),
                        FigureEdge(2, 3, directed = true),
                        FigureEdge(3, 4, directed = true),
                        FigureEdge(4, 2, directed = true),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Move only right or down from S to E. '#' is blocked. How many different paths are there?",
            options = listOf(
                "2",
                "6",
                "4",
                "3",
            ),
            correctIndex = 0,
            patternTag = "Picture · Grid DP",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: paths[r][c] = paths from above + paths from the left, and a blocked cell holds 0. The top row and left column have 1 each; the centre is 0; the cell right of centre gets 1 + 0 = 1, the cell below centre 0 + 1 = 1, so E gets 1 + 1 = 2 — around the top, or around the left. Without the block it would be 6.",
            linkedTopicId = "grid_dp_pattern",
            linkedTopicLabel = "Grid & Sequence DP",
            figure = Figure(
                caption = "3 × 3 grid with one blocked cell",
                shape = FigureShape.Grid(
                    rows = listOf(
                        listOf("S", "·", "·"),
                        listOf("·", "#", "·"),
                        listOf("·", "·", "E"),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Insert 2 into this min-heap. Where does 2 end up?",
            options = listOf(
                "Where 6 was — the root's right child — after one swap",
                "At the root",
                "As a new leaf under 6, with no swaps",
                "As the left child of 3",
            ),
            correctIndex = 0,
            patternTag = "Picture · Heap",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: a new value goes into the next free slot — the second child of 6 — then sifts up. 2 < 6, so they swap; 2 > 1, so it stops. The array becomes [1, 3, 2, 5, 9, 8, 6]. It never passes 1, because a min-heap only needs each parent ≤ its children.",
            linkedTopicId = "heap",
            linkedTopicLabel = "Heap (Min / Max)",
            figure = Figure(
                caption = "Min-heap [1, 3, 6, 5, 9, 8]",
                shape = FigureShape.Tree(
                    nodes = listOf(
                        FigureNode("1", null),
                        FigureNode("3", 0),
                        FigureNode("5", 1),
                        FigureNode("9", 1),
                        FigureNode("6", 0),
                        FigureNode("8", 4),
                    ),
                ),
            ),
        ),
    ),
)

internal val advancedPictureSet = Quiz(
    id = "advanced_picture_set",
    title = "Picture Round: Solve the Diagram",
    description = "Harder whiteboard problems — weighted graphs, DP tables, heaps and windows drawn out for you to solve.",
    questions = listOf(
        QuizQuestion(
            prompt = "Edge labels are travel costs. What is the cheapest path from A to E?",
            options = listOf(
                "7, via A–C–B–D–E",
                "8, via A–B–D–E",
                "9, via A–C–D–E",
                "6, via A–C–D–E",
            ),
            correctIndex = 0,
            patternTag = "Picture · Dijkstra",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve with Dijkstra: settle A = 0. C = 1 is closest; through C, B improves from 4 to 3 and D becomes 6. Settle B = 3; through B, D improves to 4. Settle D = 4, so E = 7. The direct A–B edge looks shorter but costs more than going round through C.",
            linkedTopicId = "dijkstras_algorithm",
            linkedTopicLabel = "Dijkstra's Algorithm",
            figure = Figure(
                caption = "Undirected, weighted",
                shape = FigureShape.Graph(
                    nodes = listOf(
                        FigureGraphNode("A", 0.08f, 0.5f),
                        FigureGraphNode("B", 0.4f, 0.16f),
                        FigureGraphNode("C", 0.4f, 0.84f),
                        FigureGraphNode("D", 0.68f, 0.5f),
                        FigureGraphNode("E", 0.92f, 0.5f),
                    ),
                    edges = listOf(
                        FigureEdge(0, 1, "4"), FigureEdge(0, 2, "1"), FigureEdge(2, 1, "2"),
                        FigureEdge(1, 3, "1"), FigureEdge(2, 3, "5"), FigureEdge(3, 4, "3"),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Arrows point from a task to the task that depends on it. Which order runs every task after its prerequisites?",
            options = listOf(
                "B, A, E, C, D",
                "A, C, B, E, D",
                "B, E, D, A, C",
                "D, C, E, A, B",
            ),
            correctIndex = 0,
            patternTag = "Picture · Topological Sort",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: check each arrow. A→C, B→C, C→D, B→E, E→D. In B, A, E, C, D, every task comes before everything it points to. A, C, B… runs C before its prerequisite B; B, E, D… runs D before C. Kahn's algorithm builds such an order by repeatedly taking a task with no remaining prerequisites.",
            linkedTopicId = "topological_sort",
            linkedTopicLabel = "Topological Sort",
            figure = Figure(
                caption = "Task dependencies",
                shape = FigureShape.Graph(
                    nodes = listOf(
                        FigureGraphNode("A", 0.1f, 0.2f),
                        FigureGraphNode("B", 0.1f, 0.8f),
                        FigureGraphNode("C", 0.48f, 0.2f),
                        FigureGraphNode("E", 0.48f, 0.8f),
                        FigureGraphNode("D", 0.88f, 0.5f),
                    ),
                    edges = listOf(
                        FigureEdge(0, 2, directed = true),
                        FigureEdge(1, 2, directed = true),
                        FigureEdge(2, 4, directed = true),
                        FigureEdge(1, 3, directed = true),
                        FigureEdge(3, 4, directed = true),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "These are today's meetings. What is the fewest meeting rooms that fits them all?",
            options = listOf(
                "3",
                "2",
                "4",
                "1",
            ),
            correctIndex = 0,
            patternTag = "Picture · Sweep Line",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: the answer is the most meetings running at one moment. From 15 to 20, the 0–30, 10–25 and 15–20 meetings all overlap, so you need 3 rooms. At 10, the 5–10 meeting ends just as 10–25 starts, and a room freed at 10 can be reused at 10. Sweep the start/end events in time order, or keep a min-heap of end times.",
            linkedTopicId = "sweep_line_pattern",
            linkedTopicLabel = "Line Sweep & Difference Array",
            figure = Figure(
                caption = "Meetings, minutes past 9:00",
                shape = FigureShape.Timeline(
                    spans = listOf(
                        FigureSpan(0, 30, "0–30"),
                        FigureSpan(5, 10, "5–10"),
                        FigureSpan(10, 25, "10–25"),
                        FigureSpan(15, 20, "15–20"),
                    ),
                    axisMax = 30,
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Move only right or down, adding each cell's cost. What is the cheapest total from the top-left to the bottom-right?",
            options = listOf(
                "7 — right, right, down, down",
                "9 — always step to the cheaper neighbour",
                "8 — down, right, right, down",
                "5 — the three corner cells",
            ),
            correctIndex = 0,
            patternTag = "Picture · Grid DP",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: best[r][c] = cost + min(best above, best left). Row 1: 1, 4, 5. Row 2: 2, 7, 6. Row 3: 6, 8, 7. The answer is 7: 1 → 3 → 1 → 1 → 1. Greedy steps down to the cheap 1 first, then gets stuck paying 4 and 2 — a total of 9.",
            linkedTopicId = "grid_dp_pattern",
            linkedTopicLabel = "Grid & Sequence DP",
            figure = Figure(
                caption = "Cost of entering each cell",
                shape = FigureShape.Grid(
                    rows = listOf(
                        listOf("1", "3", "1"),
                        listOf("1", "5", "1"),
                        listOf("4", "2", "1"),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Is this a valid binary search tree?",
            options = listOf(
                "No — 6 is in 10's right subtree but smaller than 10",
                "Yes — every node is ordered correctly against its own children",
                "No — 20 is too large for a BST",
                "Yes — its in-order traversal is sorted",
            ),
            correctIndex = 0,
            patternTag = "Picture · Validate BST",
            difficulty = Difficulty.ADVANCED,
            explanation = "Solve: every node in the right subtree must be greater than the root, not just greater than its parent. 6 < 15 is fine locally, but 6 < 10 breaks the rule. Pass bounds down: the right subtree of 10 has range (10, ∞), and 6 falls outside it. The in-order walk 5, 10, 6, 15, 20 is not sorted either.",
            linkedTopicId = "binary_search_tree",
            linkedTopicLabel = "Binary Search Tree",
            figure = Figure(
                caption = "Candidate BST",
                shape = FigureShape.Tree(
                    nodes = listOf(
                        FigureNode("10", null),
                        FigureNode("5", 0),
                        FigureNode("15", 0),
                        FigureNode("6", 2),
                        FigureNode("20", 2),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Edges are added in the order e1 to e5, using union-find. Which edge is the first to join two nodes that are already connected (a cycle)?",
            options = listOf(
                "e4 (A–D)",
                "e3 (B–C)",
                "e5 (C–E)",
                "None — the edges form a tree",
            ),
            correctIndex = 0,
            patternTag = "Picture · Union-Find",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: e1 unions {A, B}; e2 unions {C, D}; e3 joins them into {A, B, C, D}. At e4, find(A) == find(D) — already one set — so A–D closes the loop A–B–C–D–A. e5 then adds E safely. This is exactly how Kruskal decides which edges to skip.",
            linkedTopicId = "union_find_pattern",
            linkedTopicLabel = "Union-Find Pattern",
            figure = Figure(
                caption = "Edges labelled by insertion order",
                shape = FigureShape.Graph(
                    nodes = listOf(
                        FigureGraphNode("A", 0.12f, 0.18f),
                        FigureGraphNode("B", 0.5f, 0.18f),
                        FigureGraphNode("C", 0.5f, 0.82f),
                        FigureGraphNode("D", 0.12f, 0.82f),
                        FigureGraphNode("E", 0.88f, 0.5f),
                    ),
                    edges = listOf(
                        FigureEdge(0, 1, "e1"), FigureEdge(2, 3, "e2"), FigureEdge(1, 2, "e3"),
                        FigureEdge(0, 3, "e4"), FigureEdge(2, 4, "e5"),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "For each element, find the next element to its right that is strictly greater (−1 if there is none). What is the output?",
            options = listOf(
                "[4, 2, 4, −1, −1]",
                "[4, 4, 4, −1, −1]",
                "[1, 2, 4, 3, −1]",
                "[2, 2, 4, 4, 3]",
            ),
            correctIndex = 0,
            patternTag = "Picture · Monotonic Stack",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: keep a stack of indices whose answer is still unknown. The 1 arrives, is not bigger than 2, and gets pushed. The second 2 pops the 1 (answer 2) but not the first 2, because it is not strictly greater. Then 4 pops both 2s (answer 4). 3 is smaller than 4, and nothing follows, so 4 and 3 get −1. Each index is pushed and popped once: O(n).",
            linkedTopicId = "monotonic_stack_pattern",
            linkedTopicLabel = "Monotonic Stack",
            figure = Figure(
                caption = "Input array",
                shape = FigureShape.Strip(cells = listOf("2", "1", "2", "4", "3")),
            ),
        ),
        QuizQuestion(
            prompt = "Longest substring without repeating characters: the window holds \"abc\", and R reaches another 'a'. Where does the left edge move?",
            options = listOf(
                "To index 1 — just past the earlier 'a'",
                "To index 3 — restart at R",
                "It stays at 0 — the window just grows",
                "To index 2 — halfway through the window",
            ),
            correctIndex = 0,
            patternTag = "Picture · Sliding Window",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Solve: keep lastSeen[ch]. On a repeat, L = max(L, lastSeen[ch] + 1) — here 0 + 1 = 1 — so the window becomes \"bca\" with no repeat. Restarting at R throws away \"bc\", which is still valid. The max() stops L moving backwards when the old copy is already outside the window. The answer for \"abcabcbb\" is 3.",
            linkedTopicId = "sliding_window_pattern",
            linkedTopicLabel = "Sliding Window Pattern",
            figure = Figure(
                caption = "s = \"abcabcbb\"",
                shape = FigureShape.Strip(
                    cells = listOf("a", "b", "c", "a", "b", "c", "b", "b"),
                    bands = listOf(FigureBand(0, 2, "window")),
                    pointers = listOf(FigurePointer(0, "L"), FigurePointer(3, "R")),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "Can this graph's nodes be coloured with two colours so that no edge joins two nodes of the same colour?",
            options = listOf(
                "No — it is a cycle of odd length (5)",
                "Yes — every node has exactly two neighbours",
                "No — any graph with a cycle fails",
                "Yes — 5 nodes can always be split into two groups",
            ),
            correctIndex = 0,
            patternTag = "Picture · Bipartite",
            difficulty = Difficulty.ADVANCED,
            explanation = "Solve: colour A red; B and E must be blue; C must be red and D blue — but D and E are neighbours and both blue. Any odd cycle forces this clash, and a graph is two-colourable exactly when it has no odd cycle. A square (an even cycle) would be fine, so \"has a cycle\" is the wrong reason.",
            linkedTopicId = "graph_coloring_pattern",
            linkedTopicLabel = "Two-Colouring & Cycle Detection",
            figure = Figure(
                caption = "Undirected graph",
                shape = FigureShape.Graph(
                    nodes = listOf(
                        FigureGraphNode("A", 0.5f, 0.1f),
                        FigureGraphNode("B", 0.86f, 0.4f),
                        FigureGraphNode("C", 0.72f, 0.9f),
                        FigureGraphNode("D", 0.28f, 0.9f),
                        FigureGraphNode("E", 0.14f, 0.4f),
                    ),
                    edges = listOf(
                        FigureEdge(0, 1), FigureEdge(1, 2), FigureEdge(2, 3),
                        FigureEdge(3, 4), FigureEdge(4, 0),
                    ),
                ),
            ),
        ),
        QuizQuestion(
            prompt = "A running median uses two heaps, shown here. After inserting 6 and rebalancing, what is the median?",
            options = listOf(
                "5.5",
                "6",
                "5",
                "7",
            ),
            correctIndex = 0,
            patternTag = "Picture · Two Heaps",
            difficulty = Difficulty.ADVANCED,
            explanation = "Solve: 6 > 5 (the low half's top), so it goes into the high half: low {5, 3, 1}, high {6, 7, 9}. The sizes are equal (3 and 3), so no rebalance is needed, and the median is the average of the two tops: (5 + 6) / 2 = 5.5. Sorted, that is 1, 3, 5 | 6, 7, 9.",
            linkedTopicId = "two_heaps_pattern",
            linkedTopicLabel = "Two Heaps",
            figure = Figure(
                caption = "Heap tops first",
                shape = FigureShape.Stacks(
                    columns = listOf(
                        FigureStack("max-heap (low)", listOf("5", "3", "1")),
                        FigureStack("min-heap (high)", listOf("7", "9")),
                    ),
                ),
            ),
        ),
    ),
)
