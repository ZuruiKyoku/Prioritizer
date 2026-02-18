package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.ui.theme.priorityBackgroundColor

@Composable
fun AddTaskDialog(
    initialText: String = "",
    initialPriority: Float = 5f,
    onAdd: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    isDarkMode: Boolean = false
) {
    var taskText by remember { mutableStateOf(initialText) }
    var taskPriority by remember { mutableStateOf(initialPriority) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    label = { Text("Task Description") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor  = priorityBackgroundColor(
                            priority = taskPriority.toInt(),
                            isDarkMode = isDarkMode
                        ),
                        cursorColor  = priorityBackgroundColor(
                            priority = taskPriority.toInt(),
                            isDarkMode = isDarkMode
                        ),
                        focusedLabelColor  = priorityBackgroundColor(
                            priority = taskPriority.toInt(),
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
                    Text("Priority: ${taskPriority.toInt()}")
                    Slider(
                        value = taskPriority,
                        onValueChange = { taskPriority = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = priorityBackgroundColor(
                                priority = taskPriority.toInt(),
                                isDarkMode = isDarkMode
                            ),
                            activeTrackColor = priorityBackgroundColor(
                                priority = taskPriority.toInt(),
                                isDarkMode = isDarkMode
                            ),
                            activeTickColor = priorityBackgroundColor(
                                priority = taskPriority.toInt(),
                                isDarkMode = isDarkMode
                            ),
                            inactiveTickColor = priorityBackgroundColor(
                                priority = taskPriority.toInt(),
                                isDarkMode = isDarkMode
                            )
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (taskText.isNotBlank()) {
                        onAdd(taskText.trim(), taskPriority.toInt())
                    }
                }
            ) {
                Text("Add")
            }
        }
    )
}
