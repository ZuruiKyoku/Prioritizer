package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.util.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AddFriendDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val focus1 = remember { FocusRequester() }
    val focus2 = remember { FocusRequester() }
    val focus3 = remember { FocusRequester() }

    var part1 by remember { mutableStateOf("") }
    var part2 by remember { mutableStateOf("") }
    var part3 by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focus1.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = part1,
                        onValueChange = {
                            if (it.length <= 2) {
                                part1 = it.uppercase(Locale.ROOT)
                                if (it.length == 2) focus2.requestFocus()
                            }
                        },
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(focus1),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Characters)
                    )

                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.padding(horizontal = 4.dp))

                    OutlinedTextField(
                        value = part2,
                        onValueChange = {
                            if (it.length <= 2) {
                                part2 = it.uppercase(Locale.ROOT)
                                if (it.length == 2) focus3.requestFocus()
                            }
                        },
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(focus2)
                            .onKeyEvent { event ->
                                if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown && part2.isEmpty()) {
                                    focus1.requestFocus()
                                    true
                                } else false
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Characters)
                    )

                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.padding(horizontal = 4.dp))

                    OutlinedTextField(
                        value = part3,
                        onValueChange = {
                            if (it.length <= 2) {
                                part3 = it.uppercase(Locale.ROOT)
                                if (it.length == 2) keyboardController?.hide()
                            }
                        },
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(focus3)
                            .onKeyEvent { event ->
                                if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown && part3.isEmpty()) {
                                    focus2.requestFocus()
                                    true
                                } else false
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Characters)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val combined = "${part1.trim()}-${part2.trim()}-${part3.trim()}"
                    if (part1.length == 2 && part2.length == 2 && part3.length == 2) {
                        onAdd(combined)
                    }
                },
                enabled = part1.length == 2 && part2.length == 2 && part3.length == 2
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}