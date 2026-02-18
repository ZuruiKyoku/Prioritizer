package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.data.user.FriendProfile

@Composable
fun ShareListWithFriendDialog(
    listId: Long,
    friends: List<FriendProfile>,
    onShare: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFriendIds by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onShare(selectedFriendIds.toList())
                    onDismiss()
                },
                enabled = selectedFriendIds.isNotEmpty()
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Share List with Friends") },
        text = {
            if (friends.isEmpty()) {
                Text("You have no friends added.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(friends) { friend ->
                        val isSelected = friend.uid in selectedFriendIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFriendIds = if (isSelected)
                                        selectedFriendIds - friend.uid
                                    else
                                        selectedFriendIds + friend.uid
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = friend.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                friend.profileImageUrl?.let {
                                    Text(
                                        text = friend.uid,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null // handled by row click
                            )
                        }
                    }
                }
            }
        }
    )
}