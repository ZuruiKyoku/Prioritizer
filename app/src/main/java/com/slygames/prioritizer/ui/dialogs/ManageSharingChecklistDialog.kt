package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.data.user.FriendProfile

@Composable
fun ManageSharingChecklistDialog(
        friends: List<FriendProfile>,
        initialStatuses: Map<String, String>,
        onDismiss: () -> Unit,
        onApplyChanges: (added: List<String>, removed: List<String>) -> Unit
) {
    // Local selection state keyed by friend UID (true = shared/kept, false = removed/not shared)
    val selections =
            remember(initialStatuses) {
                mutableStateMapOf<String, Boolean>().apply {
                    // initialize every friend to whether they're currently shared
                    friends.forEach { put(it.uid, it.uid in initialStatuses) }
                }
            }

    // Sort: shared first, then not shared; inside each group, by display name (fallback uid)
    val sortedFriends by
            remember(selections, friends) {
                derivedStateOf {
                    friends.sortedWith(
                            compareByDescending<FriendProfile> { selections[it.uid] == true }
                                    .thenBy {
                                        (it.displayName?.ifBlank { null } ?: it.uid).lowercase()
                                    }
                    )
                }
            }

    AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {},
            title = { Text("Manage Sharing") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Container box for the scrollable list
                    Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp)
                    ) {
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(sortedFriends, key = { it.uid }) { friend ->
                                val isSelected = selections[friend.uid] == true
                                val isPending = initialStatuses[friend.uid] == "pending"

                                // If selected and was originally pending, show indeterminate
                                // If selected and was accepted (or new), show checked
                                // If not selected, show unchecked
                                val state =
                                        when {
                                            isSelected && isPending -> ToggleableState.Indeterminate
                                            isSelected -> ToggleableState.On
                                            else -> ToggleableState.Off
                                        }

                                FriendCheckboxRow(
                                        name = friend.displayName?.takeIf { it.isNotBlank() }
                                                        ?: friend.uid,
                                        state = state,
                                        onClick = {
                                            // Toggle inclusion
                                            selections[friend.uid] = !isSelected
                                        }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Buttons row
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                                onClick = {
                                    // Compute diff vs initiallySharedWith keys
                                    val finalSelected = selections.filterValues { it }.keys
                                    val initialKeys = initialStatuses.keys

                                    val added = finalSelected.minus(initialKeys)
                                    val removed = initialKeys.minus(finalSelected)
                                    onApplyChanges(added.toList(), removed.toList())
                                }
                        ) { Text("Save") }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
    )
}

@Composable
private fun FriendCheckboxRow(name: String, state: ToggleableState, onClick: () -> Unit) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
        )
        // Checkbox at far right
        TriStateCheckbox(state = state, onClick = onClick)
    }
}
