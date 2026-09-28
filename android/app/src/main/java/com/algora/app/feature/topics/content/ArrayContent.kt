package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CodeVariant
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val arrayContent = TopicContent(
    topicId = "array",
    figure = Figure(
        caption = "Contiguity is the whole bargain. Every element is the same size and sits next to the " +
            "last, so a[i] is one multiply-and-add — no search, no pointer chase. The same layout is why " +
            "inserting at the front has to shift everything after it.",
        shape = FigureShape.Strip(
            cells = listOf("7", "3", "9", "4", "1", "8"),
            bands = listOf(FigureBand(0, 5, "one contiguous block")),
            pointers = listOf(FigurePointer(3, "a[3]")),
        ),
    ),
    whatIsIt = listOf(
        "An array is a collection of elements stored in contiguous memory, each reachable directly by its index.",
        "Think of it like a row of numbered mailboxes on a street — if you know the box number, you can walk straight to it without checking every box along the way.",
    ),
    steps = listOf(
        StepCard(
            number = 1,
            title = "Contiguous Memory",
            body = "All elements sit back-to-back in one memory block, so the whole array can be described by a single starting address.",
            accentColor = 0xFF8B5CF6,
        ),
        StepCard(
            number = 2,
            title = "Index-Based Addressing",
            body = "Any element's address is computed directly — no searching required: address = base + (index × elementSize).",
            accentColor = 0xFF3B82F6,
        ),
        StepCard(
            number = 3,
            title = "Fixed Capacity, Amortized Growth",
            body = "A raw array has a fixed size. Dynamic arrays (like Kotlin's ArrayList) grow by allocating a bigger backing array and copying everything over when full.",
            accentColor = 0xFFF59E0B,
        ),
        StepCard(
            number = 4,
            title = "Shifting on Insert/Delete",
            body = "Inserting or deleting anywhere but the end means shifting every following element one slot over, to keep the array contiguous.",
            accentColor = 0xFFEC4899,
        ),
    ),
    formulas = listOf(
        FormulaEntry("Access by index", "O(1)", "Direct address computation — no traversal needed."),
        FormulaEntry("Search (unsorted)", "O(n)", "Worst case checks every element."),
        FormulaEntry("Insert / Delete at end", "O(1)*", "*Amortized — occasional O(n) resize when capacity is exceeded."),
        FormulaEntry("Insert / Delete at start or middle", "O(n)", "Every following element must shift by one position."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements currently in the array"),
        NotationEntry("O(1)", "constant time — independent of n"),
        NotationEntry("O(n)", "linear time — grows proportionally with n"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dynamic Array — core operations",
            accentColor = 0xFF6366F1,
            code = """
                class DynamicArray {
                    private var data = IntArray(4)
                    var size = 0
                        private set

                    fun get(index: Int): Int {
                        require(index in 0 until size) { "Index out of bounds" }
                        return data[index]
                    }

                    fun insertAt(index: Int, value: Int) {
                        if (size == data.size) grow()
                        for (i in size downTo index + 1) {
                            data[i] = data[i - 1]
                        }
                        data[index] = value
                        size++
                    }

                    fun deleteAt(index: Int) {
                        for (i in index until size - 1) {
                            data[i] = data[i + 1]
                        }
                        size--
                    }

                    private fun grow() {
                        // Double capacity and copy — the amortized O(1) trick.
                        data = data.copyOf(data.size * 2)
                    }
                }
            """.trimIndent(),
            variants = listOf(
                CodeVariant(
                    "Kotlin",
                    """
                        fun insertAt(index: Int, value: Int) {
                            if (size == data.size) grow()
                            for (i in size downTo index + 1) data[i] = data[i - 1]
                            data[index] = value
                            size++
                        }
                    """.trimIndent(),
                ),
                CodeVariant(
                    "Python",
                    """
                        def insert_at(self, index, value):
                            self.data.append(None)          # amortized grow
                            for i in range(len(self.data) - 1, index, -1):
                                self.data[i] = self.data[i - 1]
                            self.data[index] = value
                    """.trimIndent(),
                ),
                CodeVariant(
                    "Java",
                    """
                        void insertAt(int index, int value) {
                            if (size == data.length) grow();
                            for (int i = size; i > index; i--) data[i] = data[i - 1];
                            data[index] = value;
                            size++;
                        }
                    """.trimIndent(),
                ),
                CodeVariant(
                    "JavaScript",
                    """
                        insertAt(index, value) {
                            for (let i = this.size; i > index; i--) this.data[i] = this.data[i - 1];
                            this.data[index] = value;
                            this.size++;
                        }
                    """.trimIndent(),
                ),
            ),
        ),
        CodeBlock(
            title = "Complexity summary",
            accentColor = 0xFF16A34A,
            code = """
                // Operation         Average      Worst
                // ---------         -------      -----
                // Access             O(1)        O(1)
                // Search             O(n)        O(n)
                // Insert (end)       O(1)*       O(n)
                // Insert (start/mid) O(n)        O(n)
                // Delete (end)       O(1)        O(1)
                // Delete (start/mid) O(n)        O(n)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayVisualizer,
    applications = listOf(
        ApplicationCard(
            iconName = "Image",
            accentColor = 0xFF3B82F6,
            title = "Image Pixel Buffers",
            body = "Bitmaps store pixels in a flat array, indexed by row × width + column, for fast direct access.",
        ),
        ApplicationCard(
            iconName = "TableChart",
            accentColor = 0xFFF59E0B,
            title = "Spreadsheets & Tables",
            body = "Rows and columns map naturally onto 2D arrays, giving O(1) access to any cell.",
        ),
        ApplicationCard(
            iconName = "Memory",
            accentColor = 0xFF10B981,
            title = "CPU Cache Friendliness",
            body = "Contiguous memory means sequential access patterns benefit heavily from CPU cache prefetching.",
        ),
        ApplicationCard(
            iconName = "Layers",
            accentColor = 0xFF8B5CF6,
            title = "Backing Store for Other Structures",
            body = "ArrayLists, stacks, heaps, and hash tables are all commonly implemented on top of a resizable array.",
        ),
    ),
    takeaways = listOf(
        "Arrays give O(1) random access because element addresses are computed, not searched for.",
        "Dynamic arrays grow by allocating a bigger array and copying — expensive per-call, but O(1) amortized over many appends.",
        "Inserting or deleting in the middle costs O(n) because following elements must shift.",
        "Contiguous memory layout makes arrays very CPU cache-friendly for sequential access.",
        "Arrays are the foundation most other data structures (lists, stacks, heaps, hash tables) are built on top of.",
    ),
)
