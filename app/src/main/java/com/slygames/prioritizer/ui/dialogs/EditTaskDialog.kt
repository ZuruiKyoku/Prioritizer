package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.ui.theme.priorityBackgroundColor

@Composable
fun EditTaskDialog(
    task: TaskEntity,
    isDarkMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: () -> Unit
) {
    var updatedTitle by remember { mutableStateOf(task.description) }
    var updatedPriority by remember { mutableStateOf(task.priority.toFloat()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus and keyboard for editing
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = updatedTitle,
                    onValueChange = { updatedTitle = it },
                    label = { Text("Task Description") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor  = priorityBackgroundColor(
                            priority = updatedPriority.toInt(),
                            isDarkMode = isDarkMode
                        ),
                        cursorColor  = priorityBackgroundColor(
                            priority = updatedPriority.toInt(),
                            isDarkMode = isDarkMode
                        ),
                        focusedLabelColor  = priorityBackgroundColor(
                            priority = updatedPriority.toInt(),
                            isDarkMode = isDarkMode
                        )
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Priority: ${updatedPriority.toInt()}")
                    Slider(
                        value = updatedPriority,
                        onValueChange = { updatedPriority = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = priorityBackgroundColor(
                                priority = updatedPriority.toInt(),
                                isDarkMode = isDarkMode
                            ),
                            activeTrackColor = priorityBackgroundColor(
                                priority = updatedPriority.toInt(),
                                isDarkMode = isDarkMode
                            ),
                            activeTickColor = priorityBackgroundColor(
                                priority = updatedPriority.toInt(),
                                isDarkMode = isDarkMode
                            ),
                            inactiveTickColor = priorityBackgroundColor(
                                priority = updatedPriority.toInt(),
                                isDarkMode = isDarkMode
                            )
                        )
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: red trash icon
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete task"
                    )
                }

                // Right: Cancel + Save
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        if (updatedTitle.isNotBlank()) {
                            onSave(task.copy(
                                description = updatedTitle.trim(),
                                priority = updatedPriority.toInt()
                            ))
                        }
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    )
}