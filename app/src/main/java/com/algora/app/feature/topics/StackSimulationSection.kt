package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_ITEMS = 8
private val initialItems = listOf(10, 20, 30)
private const val PEEK_FLASH_MS = 700L
private const val DEFAULT_VALUE_INPUT = "40"

@Composable
fun StackSimulationSection() {
    val items = remember { mutableStateListOf(*initialItems.toTypedArray()) }
    var peekedIndex by remember { mutableStateOf<Int?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    // Pre-filled so Push works on first tap without typing.
    var valueInput by remember { mutableStateOf(DEFAULT_VALUE_INPUT) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (items.isEmpty()) {
                Text(
                    "Stack is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    for (index in items.indices.reversed()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StackBox(value = items[index], isPeeked = peekedIndex == index)
                            if (index == items.lastIndex) {
                                Text(
                                    "← top",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                        if (index != 0) Box(modifier = Modifier.size(4.dp))
                    }
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

            SimNumberField(
                value = valueInput,
                onValueChange = { valueInput = it.filter(Char::isDigit) },
                label = "Value",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            SimOpRow(
                modifier = Modifier.padding(top = 4.dp),
                ops = listOf(
                    SimOp("Push", Icons.Filled.Add, SimColors.Blue) {
                        peekedIndex = null
                        val v = valueInput.toIntOrNull()
                        when {
                            v == null -> statusMessage = "Enter a value first"
                            items.size >= MAX_ITEMS -> statusMessage = "Stack is full ($MAX_ITEMS max)"
                            else -> {
                                items.add(v)
                                statusMessage = "Pushed $v — O(1)"
                            }
                        }
                    },
                    SimOp("Pop", Icons.Filled.Remove, SimColors.Red) {
                        peekedIndex = null
                        if (items.isEmpty()) {
                            statusMessage = "Stack is empty"
                        } else {
                            val popped = items.removeAt(items.lastIndex)
                            statusMessage = "Popped $popped"
                        }
                    },
                ),
            )
            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Peek", Icons.Filled.Visibility, SimColors.Amber) {
                        if (items.isEmpty()) {
                            statusMessage = "Stack is empty"
                        } else {
                            val topIndex = items.lastIndex
                            statusMessage = "Top is ${items[topIndex]}"
                            scope.launch {
                                peekedIndex = topIndex
                                delay(PEEK_FLASH_MS)
                                peekedIndex = null
                            }
                        }
                    },
                    SimOp("", Icons.Filled.Refresh, SimColors.Grey, weight = 0.55f, contentDescription = "Reset") {
                        peekedIndex = null
                        items.clear()
                        items.addAll(initialItems)
                        statusMessage = "Reset to initial state"
                    },
                ),
            )
        }
    }
}

@Composable
private fun StackBox(value: Int, isPeeked: Boolean) {
    SimValueChip(
        value = value.toString(),
        chip = if (isPeeked) ChipAmber else ChipViolet,
        width = 120.dp,
        height = 44.dp,
    )
}
