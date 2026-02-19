package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.data.list.SharedListInvite
import com.slygames.prioritizer.data.user.FriendProfile
import com.slygames.prioritizer.ui.components.FriendRequestItem
import com.slygames.prioritizer.ui.components.SharedListInviteItem
import com.slygames.prioritizer.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun RequestsSummaryDialog(
        friendRequests: List<FriendProfile>,
        sharedListInvites: List<SharedListInvite>,
        onDismiss: () -> Unit,
        onViewRequests: () -> Unit
) {
    val friendCount = friendRequests.size
    val listCount = sharedListInvites.size

    if (friendCount == 0 && listCount == 0) return

    val summary = buildString {
        if (friendCount > 0)
                append("You have $friendCount friend request${if (friendCount > 1) "s" else ""}.")
        if (friendCount > 0 && listCount > 0) append("\n")
        if (listCount > 0)
                append("You have $listCount shared list${if (listCount > 1) "s" else ""}.")
    }

    AlertDialog(
            onDismissRequest = {},
            title = { Text("Incoming Requests") },
            text = { Text(summary) },
            confirmButton = { TextButton(onClick = onViewRequests) { Text("View Requests") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    )
}

@Composable
fun RequestsDetailDialog(
        viewModel: MainViewModel,
        friendRequests: List<FriendProfile>,
        sharedListInvites: List<SharedListInvite>,
        onAcceptFriend: (String) -> Unit,
        onDeclineFriend: (String) -> Unit,
        onAcceptSharedList: (String) -> Unit,
        onDeclineSharedList: (String) -> Unit,
        onDismiss: () -> Unit
) {
    val tabTitles = listOf("Friends", "Lists")
    val hasFriendRequests = friendRequests.isNotEmpty()
    val hasSharedInvites = sharedListInvites.isNotEmpty()
    val pendingSentFriends by viewModel.pendingSentFriendRequests.collectAsState()
    val pendingSentLists by viewModel.pendingSentListShares.collectAsState()
    val totalPending by viewModel.totalPendingSentCount.collectAsState()
    var showPendingSentPopup by remember { mutableStateOf(false) }

    val defaultTab =
            when {
                hasFriendRequests -> 0
                hasSharedInvites -> 1
                else -> 0
            }
    var selectedTabIndex by remember { mutableIntStateOf(defaultTab) }

    AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                            onClick = { showPendingSentPopup = true },
                            enabled = totalPending > 0,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.secondaryContainer
                                    )
                    ) { Text("$totalPending Pending Sent") }

                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            },
            title = { Text("Manage Requests") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp, max = 460.dp)) {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabTitles.forEachIndexed { index, title ->
                            val badgeCount =
                                    when (index) {
                                        0 -> friendRequests.size
                                        1 -> sharedListInvites.size
                                        else -> 0
                                    }
                            Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = {
                                        if (badgeCount > 0) Text("$title ($badgeCount)")
                                        else Text(title)
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val scrollState = rememberScrollState()
                    var hasAnimatedScroll by remember { mutableStateOf(false) }

                    LaunchedEffect(selectedTabIndex) {
                        if (!hasAnimatedScroll) {
                            delay(200)
                            scrollState.animateScrollBy(30f)
                            scrollState.animateScrollBy(-30f)
                            hasAnimatedScroll = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.verticalScroll(scrollState).fillMaxSize()) {
                            when (selectedTabIndex) {
                                0 -> {
                                    if (hasFriendRequests) {
                                        friendRequests.forEach { request ->
                                            FriendRequestItem(
                                                    friend = request,
                                                    onAccept = onAcceptFriend,
                                                    onDecline = onDeclineFriend
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    } else {
                                        Text(
                                                "No friend requests.",
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 24.dp),
                                                textAlign =
                                                        androidx.compose.ui.text.style.TextAlign
                                                                .Center
                                        )
                                    }
                                }
                                1 -> {
                                    if (hasSharedInvites) {
                                        sharedListInvites.forEach { invite ->
                                            SharedListInviteItem(
                                                    invite = invite,
                                                    onAcceptSharedList = { _: SharedListInvite ->
                                                        onAcceptSharedList(invite.uuid)
                                                    },
                                                    onDeclineSharedList = { _: SharedListInvite ->
                                                        onDeclineSharedList(invite.uuid)
                                                    }
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    } else {
                                        Text(
                                                "No shared list invites.",
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 24.dp),
                                                textAlign =
                                                        androidx.compose.ui.text.style.TextAlign
                                                                .Center
                                        )
                                    }
                                }
                            }
                        }

                        // Fading bottom edge
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(24.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                        Brush.verticalGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.Transparent,
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .background
                                                                        )
                                                        )
                                                )
                        )
                    }
                }

                if (showPendingSentPopup) {
                    AlertDialog(
                            onDismissRequest = { showPendingSentPopup = false },
                            title = { Text("Pending Sent Requests") },
                            text = {
                                Column {
                                    Text(
                                            "Pending Friend Requests",
                                            style = MaterialTheme.typography.titleMedium
                                    )
                                    if (pendingSentFriends.isEmpty()) {
                                        Text("No pending friend requests.", color = Color.Gray)
                                    } else {
                                        pendingSentFriends.forEach { req ->
                                            Text(req.displayName ?: req.uid)
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                            "Pending List Shares",
                                            style = MaterialTheme.typography.titleMedium
                                    )
                                    if (pendingSentLists.isEmpty()) {
                                        Text("No pending list shares.", color = Color.Gray)
                                    } else {
                                        pendingSentLists.forEach { invite ->
                                            Text(invite.listName)
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showPendingSentPopup = false }) {
                                    Text("Close")
                                }
                            }
                    )
                }
            }
    )
}
