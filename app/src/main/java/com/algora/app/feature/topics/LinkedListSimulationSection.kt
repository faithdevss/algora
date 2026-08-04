package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_NODES = 10
private val initialNodes = listOf(10, 20, 30)
private const val DEFAULT_VALUE_INPUT = "40"
private const val DEFAULT_INDEX_INPUT = "1"
private const val SEARCH_STEP_MS = 420L

@Composable
fun LinkedListSimulationSection() {
    val nodes = remember { mutableStateListOf(*initialNodes.toTypedArray()) }
    var highlightedIndex by remember { mutableStateOf<Int?>(null) }
    var foundIndex by remember { mutableStateOf<Int?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    // Pre-filled so every op does something meaningful on first tap.
    var valueInput by remember { mutableStateOf(DEFAULT_VALUE_INPUT) }
    var indexInput by remember { mutableStateOf(DEFAULT_INDEX_INPUT) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    fun parsedValue(): Int? = valueInput.toIntOrNull()
    fun parsedIndex(): Int? = indexInput.toIntOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (nodes.isEmpty()) {
                    Text(
                        "head → NULL (empty)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    nodes.forEachIndexed { index, value ->
                        LinkedListNode(
                            value = value,
                            isFound = foundIndex == index,
                            isHighlighted = highlightedIndex == index,
                        )
                        Text(
                            "→",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    Text(
                        "NULL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimNumberField(
                    value = valueInput,
                    onValueChange = { valueInput = it.filter(Char::isDigit) },
                    label = "Value",
                    modifier = Modifier.weight(1f),
                )
                SimNumberField(
                    value = indexInput,
                    onValueChange = { indexInput = it.filter(Char::isDigit) },
                    label = "Index",
                    modifier = Modifier.weight(1f),
                )
            }

            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Insert Head", Icons.Filled.FirstPage, SimColors.Blue) {
                        cancelSearch()
                        foundIndex = null
                        val v = parsedValue()
                        when {
                            v == null -> statusMessage = "Enter a value first"
                            nodes.size >= MAX_NODES -> statusMessage = "List is full ($MAX_NODES max)"
                            else -> {
                                nodes.add(0, v)
                                highlightedIndex = 0
                                statusMessage = "Inserted $v at head — O(1)"
                            }
                        }
                    },
                    SimOp("Insert Tail", Icons.Filled.LastPage, SimColors.Blue) {
                        cancelSearch()
                        foundIndex = null
                        val v = parsedValue()
                        when {
                            v == null -> statusMessage = "Enter a value first"
                            nodes.size >= MAX_NODES -> statusMessage = "List is full ($MAX_NODES max)"
                            else -> {
                                nodes.add(v)
                                highlightedIndex = nodes.lastIndex
                                statusMessage = "Inserted $v at tail"
                            }
                        }
                    },
                ),
            )
            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Insert At", Icons.Filled.Add, SimColors.Violet) {
                        cancelSearch()
                        foundIndex = null
                        val v = parsedValue()
                        val i = parsedIndex()
                        when {
                            v == null || i == null -> statusMessage = "Enter both value and index"
                            nodes.size >= MAX_NODES -> statusMessage = "List is full ($MAX_NODES max)"
                            i !in 0..nodes.size -> statusMessage = "Index out of bounds"
                            else -> {
                                nodes.add(i, v)
                                highlightedIndex = i
                                statusMessage = "Inserted $v at index $i"
                            }
                        }
                    },
                    SimOp("Delete At", Icons.Filled.Delete, SimColors.Red) {
                        cancelSearch()
                        foundIndex = null
                        val i = parsedIndex()
                        when {
                            i == null -> statusMessage = "Enter an index first"
                            i !in nodes.indices -> statusMessage = "Index out of bounds"
                            else -> {
                                val removed = nodes.removeAt(i)
                                highlightedIndex = null
                                statusMessage = "Deleted $removed"
                            }
                        }
                    },
                ),
            )
            // Reset is icon-only next to Search rather than a row of its own — it is a utility, not
            // one of the list operations the widget is teaching.
            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Search", Icons.Filled.Search, SimColors.Amber) {
                        cancelSearch()
                        foundIndex = null
                        val v = parsedValue()
                        if (v == null) {
                            statusMessage = "Enter a value first"
                        } else {
                            searchJob = scope.launch {
                                for (i in nodes.indices) {
                                    highlightedIndex = i
                                    delay(SEARCH_STEP_MS)
                                    if (nodes[i] == v) {
                                        foundIndex = i
                                        statusMessage = "Found $v at index $i"
                                        return@launch
                                    }
                                }
                                highlightedIndex = null
                                statusMessage = "$v not found — walked ${nodes.size} nodes, O(n)"
                            }
                        }
                    },
                    SimOp("", Icons.Filled.Refresh, SimColors.Grey, weight = 0.55f, contentDescription = "Reset") {
                        cancelSearch()
                        nodes.clear()
                        nodes.addAll(initialNodes)
                        highlightedIndex = null
                        foundIndex = null
                        statusMessage = "Reset to initial state"
                    },
                ),
            )
        }
    }
}

// The mock's llNodes logic: found wins over highlighted, both over the resting fill.
@Composable
private fun LinkedListNode(value: Int, isFound: Boolean, isHighlighted: Boolean) {
    SimValueChip(
        value = value.toString(),
        chip = when {
            isFound -> ChipGreen
            isHighlighted -> ChipAmber
            else -> ChipViolet
        },
        width = 74.dp,
        height = 48.dp,
    )
}
