package com.slygames.prioritizer.ui.profile

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slygames.prioritizer.data.user.FriendProfile
import com.slygames.prioritizer.ui.components.FriendItem
import com.slygames.prioritizer.ui.dialogs.AddFriendDialog
import com.slygames.prioritizer.ui.dialogs.RequestsDetailDialog
import com.slygames.prioritizer.viewmodel.FriendsViewModel
import com.slygames.prioritizer.viewmodel.RequestsViewModel

@Composable
fun FriendsTab(
        friendsViewModel: FriendsViewModel = hiltViewModel(),
        requestsViewModel: RequestsViewModel = hiltViewModel()
) {
    val friendList by friendsViewModel.friends.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showRequestsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var friendToDelete by remember { mutableStateOf<FriendProfile?>(null) }
    val incomingFriendRequests by requestsViewModel.incomingFriendRequests.collectAsStateWithLifecycle()
    val incomingShareRequests by requestsViewModel.incomingSharedListInvites.collectAsStateWithLifecycle()

    fun inviteFriend() {
        val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join me on Prioritizer! Download it here: https://google.com")
                }
        context.startActivity(Intent.createChooser(intent, "Invite a friend"))
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                friendList.isEmpty() -> {
                    Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(32.dp))
                        Text("You have no friends added yet.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                                "Add friends by clicking the button above and entering their friend code.",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(32.dp))
                        OutlinedButton(onClick = { inviteFriend() }) { Text("Invite a Friend") }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState())) {
                        Column {
                            friendList.forEach { friend ->
                                FriendItem(friend = friend, onRemove = { friendToDelete = it })
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Add Friend by Code")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (incomingFriendRequests.isNotEmpty() || incomingShareRequests.isNotEmpty()) {
                Button(onClick = { showRequestsDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("View Requests")
                }
            }
        }
    }

    if (showAddDialog) {
        AddFriendDialog(
                onAdd = { formattedCode ->
                    friendsViewModel.addFriend(formattedCode) { success, error ->
                        if (success) {
                            Toast.makeText(context, "Friend request sent!", Toast.LENGTH_SHORT).show()
                            showAddDialog = false
                        } else {
                            Toast.makeText(context, error ?: "Failed to send request", Toast.LENGTH_SHORT).show()
                            // Keep dialog open so user can retry
                        }
                    }
                },
                onDismiss = { showAddDialog = false }
        )
    }

    if (showRequestsDialog) {
        RequestsDetailDialog(
                viewModel = requestsViewModel,
                friendRequests = incomingFriendRequests,
                sharedListInvites = incomingShareRequests,
                onAcceptFriend = { requestsViewModel.acceptFriendRequest(it) },
                onDeclineFriend = { requestsViewModel.declineFriendRequest(it) },
                onAcceptSharedList = { uuid -> requestsViewModel.acceptSharedListInvite(uuid) },
                onDeclineSharedList = { uuid -> requestsViewModel.declineSharedListInvite(uuid) },
                onDismiss = { showRequestsDialog = false }
        )
    }

    friendToDelete?.let { friend ->
        AlertDialog(
                onDismissRequest = { friendToDelete = null },
                title = { Text("Remove Friend") },
                text = { Text("Are you sure you want to remove ${friend.displayName}?") },
                confirmButton = {
                    TextButton(
                            onClick = {
                                friendsViewModel.removeFriend(friend)
                                Toast.makeText(context, "Removed ${friend.displayName}", Toast.LENGTH_SHORT).show()
                                friendToDelete = null
                            }
                    ) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { friendToDelete = null }) { Text("Cancel") } }
        )
    }
}
