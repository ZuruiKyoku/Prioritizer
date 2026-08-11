package com.slygames.prioritizer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.data.user.FriendProfile
import com.slygames.prioritizer.ui.components.ListManagerItem

@Composable
fun ListManagerDialog(
        allLists: List<TaskListEntity>,
        selectedListId: Long?,
        friends: List<FriendProfile>,
        userNickname: String?,
        onSelectList: (Long) -> Unit,
        onRenameList: (Long, String) -> Unit,
        onDeleteList: (Long) -> Unit,
        onLeaveSharedList: (Long) -> Unit,
        onShareListExternally: (Long) -> Unit,
        onShareListWithFriends: (List<String>, Long, String) -> Unit,
        onLoadSharedMembers: suspend (listUuid: String) -> Map<String, String>,
        onApplySharingChanges: (listUuid: String, added: List<String>, removed: List<String>) -> Unit,
        onAddList: (String) -> Unit,
        onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<TaskListEntity?>(null) }

    // Share flow state
    var showShareOptions by remember { mutableStateOf(false) }
    var listIdToShare by remember { mutableStateOf<Long?>(null) }
    var showShareToFriendDialog by remember { mutableStateOf(false) }
    var isOwnerOfSelectedList by remember { mutableStateOf(false) }
    var currentListIsShared by remember { mutableStateOf(false) }

    // Manage sharing state (owner-only)
    var initiallySharedWith by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingMembers by remember { mutableStateOf(false) }

    AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {},
            title = { Text("Manage Lists", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(
                                allLists,
                                key = { list ->
                                    val prefix = if (list.isShared) "shared" else "owned"
                                    "$prefix-${list.id}"
                                }
                        ) { list ->
                            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                            val isOwner = list.ownerId != null && list.ownerId == currentUid
                            val isShared = list.isShared && !list.uuid.isNullOrBlank()

                            ListManagerItem(
                                    list = list,
                                    currentUid = currentUid,
                                    isSelected = list.id == selectedListId,
                                    onSelect = onSelectList,
                                    onShare = {
                                        listIdToShare = list.id
                                        isOwnerOfSelectedList = isOwner
                                        currentListIsShared = isShared

                                        if (isShared) {
                                            showShareToFriendDialog = true
                                        } else {
                                            showShareOptions = true
                                        }
                                    },
                                    onRename = { id, newName ->
                                        showRenameDialogFor = list.copy(name = newName)
                                    },
                                    onDelete = {
                                        if (isShared) {
                                            if (isOwner) {
                                                // Owner: delete for everyone (VM should cascade +
                                                // local cleanup)
                                                onDeleteList(list.id)
                                            } else {
                                                // Member: leave shared list
                                                onLeaveSharedList(list.id)
                                            }
                                        } else {
                                            // Not shared: normal delete
                                            onDeleteList(list.id)
                                        }
                                    }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth()
                    ) { Text("Create New List") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
    )

    if (showShareOptions && listIdToShare != null) {
        AlertDialog(
                onDismissRequest = { showShareOptions = false },
                title = { Text("Share List") },
                text = { Text("How would you like to share this list?") },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showShareOptions = false
                                showShareToFriendDialog = true
                            }
                    ) { Text("With Friends") }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                onShareListExternally(listIdToShare!!)
                                showShareOptions = false
                            }
                    ) { Text("Via Messages/Email") }
                }
        )
    }

    // --- Owner path: ManageSharingChecklistDialog (checkboxes) ---
    if (showShareToFriendDialog &&
                    listIdToShare != null &&
                    currentListIsShared &&
                    isOwnerOfSelectedList
    ) {
        // find the list + uuid
        val list =
                remember(allLists, listIdToShare) {
                    allLists.firstOrNull { it.id == listIdToShare }
                }
        val listUuid = list?.uuid

        // Load current shared members once when dialog opens
        LaunchedEffect(listUuid) {
            if (!listUuid.isNullOrBlank()) {
                isLoadingMembers = true
                initiallySharedWith = onLoadSharedMembers(listUuid)
                isLoadingMembers = false
            }
        }

        if (isLoadingMembers) {
            AlertDialog(
                    onDismissRequest = { showShareToFriendDialog = false },
                    confirmButton = {},
                    title = { Text("Manage Sharing") },
                    text = { CircularProgressIndicator() }
            )
        } else {
            ManageSharingChecklistDialog(
                    friends = friends,
                    initialStatuses = initiallySharedWith,
                    onDismiss = { showShareToFriendDialog = false },
                    onApplyChanges = { added, removed ->
                        if (listUuid.isNullOrBlank()) {
                            showShareToFriendDialog = false
                            return@ManageSharingChecklistDialog
                        }
                        onApplySharingChanges(listUuid, added, removed)
                        showShareToFriendDialog = false
                    }
            )
        }
    }

    // --- Non-owner or not-shared path: simple friend picker ---
    if (showShareToFriendDialog &&
                    listIdToShare != null &&
                    (!currentListIsShared || !isOwnerOfSelectedList)
    ) {
        ShareListWithFriendDialog(
                listId = listIdToShare!!,
                friends = friends,
                onShare = { friendUids ->
                    onShareListWithFriends(friendUids, listIdToShare!!, userNickname ?: "Someone")
                },
                onDismiss = { showShareToFriendDialog = false }
        )
    }

    // --- Create New List Dialog ---
    if (showCreateDialog) {
        var newListName by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New List") },
                text = {
                    OutlinedTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            label = { Text("List Name") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (newListName.isNotBlank()) {
                                    onAddList(newListName.trim())
                                    newListName = ""
                                    showCreateDialog = false
                                }
                            }
                    ) { Text("Create") }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    // --- Rename List Dialog ---
    if (showRenameDialogFor != null) {
        var newName by remember { mutableStateOf(showRenameDialogFor!!.name) }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        AlertDialog(
                onDismissRequest = { showRenameDialogFor = null },
                title = { Text("Rename List") },
                text = {
                    OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("List Name") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialogFor = null }) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onRenameList(showRenameDialogFor!!.id, newName.trim())
                                    showRenameDialogFor = null
                                }
                            }
                    ) { Text("Rename") }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}
