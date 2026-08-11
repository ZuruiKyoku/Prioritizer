package com.slygames.prioritizer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.firebase.messaging.FirebaseMessaging
import com.slygames.prioritizer.R
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.ui.components.ResponsiveContent
import com.slygames.prioritizer.ui.components.TaskItem
import com.slygames.prioritizer.ui.dialogs.AddTaskDialog
import com.slygames.prioritizer.ui.dialogs.EditTaskDialog
import com.slygames.prioritizer.ui.dialogs.ListManagerDialog
import com.slygames.prioritizer.ui.dialogs.RequestsDetailDialog
import com.slygames.prioritizer.ui.dialogs.RequestsSummaryDialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.slygames.prioritizer.util.loadProfileImage
import com.slygames.prioritizer.viewmodel.FriendsViewModel
import com.slygames.prioritizer.viewmodel.ProfileViewModel
import com.slygames.prioritizer.viewmodel.RequestsViewModel
import com.slygames.prioritizer.viewmodel.SettingsViewModel
import com.slygames.prioritizer.viewmodel.TaskViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
        initialDarkMode: Boolean,
        navController: NavController,
        showFriendPopup: Boolean,
        taskViewModel: TaskViewModel = hiltViewModel(),
        requestsViewModel: RequestsViewModel = hiltViewModel(),
        friendsViewModel: FriendsViewModel = hiltViewModel(),
        profileViewModel: ProfileViewModel = hiltViewModel(),
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val selectedListId by taskViewModel.selectedListId.collectAsStateWithLifecycle()
    val selectedListName by taskViewModel.selectedListName.collectAsStateWithLifecycle()
    val tasks by taskViewModel.tasks.collectAsStateWithLifecycle()
    val lists by taskViewModel.taskLists.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }
    var showListManager by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    var isDarkMode by remember { mutableStateOf(initialDarkMode) }
    var newTaskText by remember { mutableStateOf("") }
    var newTaskPriority by remember { mutableStateOf(5f) }
    var selectedTab by remember { mutableStateOf(0) }
    var taskBeingEdited by remember { mutableStateOf<TaskEntity?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val searchQuery by taskViewModel.searchQuery.collectAsStateWithLifecycle()

    // Requests
    val showRequestsDialog by requestsViewModel.showDialog.collectAsStateWithLifecycle()
    var showDetailedRequestsDialog by remember { mutableStateOf(false) }
    val incomingFriendRequests by requestsViewModel.incomingFriendRequests.collectAsStateWithLifecycle()
    val incomingShareRequests by requestsViewModel.incomingSharedListInvites.collectAsStateWithLifecycle()

    val friends by friendsViewModel.friends.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        launch { settingsViewModel.isDarkMode.collect { isDarkMode = it } }

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            profileViewModel.saveFcmToken(token)
        }
    }

    // Automatically close the dialog when no shared invites remain
    LaunchedEffect(incomingShareRequests) {
        if (showDetailedRequestsDialog && incomingShareRequests.isEmpty()) {
            showDetailedRequestsDialog = false
        }
    }

    val activeTasks = tasks.filter { !it.done }
    val completedTasks = tasks.filter { it.done }
    val shownTasks = if (selectedTab == 0) activeTasks else completedTasks

    val userProfile by profileViewModel.userProfile.collectAsStateWithLifecycle()
    val bitmap = remember(userProfile?.profileImagePath) { loadProfileImage(userProfile?.profileImagePath) }
    val isOnline by taskViewModel.isOnline.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        ResponsiveContent {
        Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                floatingActionButton = {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.navigate("profile") }, modifier = Modifier.size(56.dp)) {
                            if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = "Profile Picture",
                                        modifier =
                                                Modifier.size(80.dp)
                                                        .clip(CircleShape)
                                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            } else {
                                Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Profile",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }

                        FilledTonalButton(
                                onClick = { showListManager = true },
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        ) {
                            Text(
                                    text = selectedListName.takeIf { it.isNotBlank() }?.let { "$it ▼" } ?: "Select List ▼",
                                    maxLines = 1
                            )
                        }

                        FloatingActionButton(
                                onClick = { showDialog = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.Default.Add, contentDescription = "Add Task") }
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Image(
                            painter =
                                    if (isDarkMode) painterResource(id = R.drawable.logo_prioritizer_dark)
                                    else painterResource(id = R.drawable.logo_prioritizer),
                            contentDescription = "Prioritizer Logo",
                            modifier = Modifier.weight(1f).heightIn(max = 30.dp)
                    )
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) taskViewModel.setSearchQuery("") }) {
                        Icon(
                                imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearch) "Close search" else "Search tasks"
                        )
                    }
                }

                if (showSearch) {
                    OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { taskViewModel.setSearchQuery(it) },
                            singleLine = true,
                            placeholder = { Text("Search tasks") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                val activeCount = tasks.count { !it.done }
                val completedCount = tasks.count { it.done }
                val tabs = listOf("Active ($activeCount)", "Completed ($completedCount)")

                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(text = title, maxLines = 1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (lists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                        "Looks like you don't have any lists yet!",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Button(onClick = { showListManager = true }) { Text("Create Your First List") }
                            }
                        }
                    } else if (shownTasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                    when {
                                        searchQuery.isNotBlank() -> "No tasks match \"$searchQuery\"."
                                        selectedTab == 0 -> "Nothing here yet.\nAdd your first task and give it a priority."
                                        else -> "No completed tasks yet."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shownTasks, key = { it.uuid }) { task ->
                                TaskItem(
                                        task = task,
                                        onToggleDone = { taskViewModel.toggleTaskDone(task) },
                                        onDelete = { taskViewModel.deleteTask(task) },
                                        onEdit = { edited -> taskBeingEdited = edited },
                                        listId = selectedListId ?: 0L,
                                        isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        }

        if (!isOnline) {
            Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            ) {
                Text(
                        "Offline — changes will sync when you're back online.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
            }
        }

        if (selectedTab == 1 && completedTasks.isNotEmpty()) {
            Button(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
            ) { Text("Clear Completed") }
        }

        if (showDialog) {
            AddTaskDialog(
                    initialText = newTaskText,
                    initialPriority = newTaskPriority,
                    onAdd = { text, priority ->
                        selectedListId?.let { listId ->
                            taskViewModel.addTask(text, priority, listId)
                            newTaskText = ""
                            newTaskPriority = 5f
                            showDialog = false
                        }
                    },
                    onDismiss = { showDialog = false },
                    isDarkMode = isDarkMode
            )
        }

        taskBeingEdited?.let { task: TaskEntity ->
            EditTaskDialog(
                    task = task,
                    isDarkMode = isDarkMode,
                    onDismiss = { taskBeingEdited = null },
                    onSave = { updatedTask: TaskEntity ->
                        taskViewModel.updateTask(updatedTask)
                        taskBeingEdited = null
                    },
                    onDelete = {
                        taskViewModel.deleteTask(task)
                        taskBeingEdited = null
                    }
            )
        }

        if (showClearConfirmDialog) {
            AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    title = { Text("Clear Completed Tasks") },
                    text = { Text("Are you sure you want to delete all completed tasks? This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                                onClick = {
                                    taskViewModel.clearCompletedTasks()
                                    showClearConfirmDialog = false
                                }
                        ) { Text("Yes") }
                    },
                    dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") } }
            )
        }

        if (showListManager) {
            val currentSelectedListId by taskViewModel.selectedListId.collectAsState()

            ListManagerDialog(
                    allLists = lists,
                    selectedListId = currentSelectedListId,
                    onSelectList = {
                        scope.launch {
                            taskViewModel.loadList(it)
                            showListManager = false
                        }
                    },
                    onRenameList = { id, name -> taskViewModel.renameList(id, name) },
                    onDeleteList = { id ->
                        val listToDelete = lists.find { it.id == id } ?: return@ListManagerDialog
                        taskViewModel.deleteList(listToDelete)
                    },
                    onShareListExternally = { id -> taskViewModel.shareListAsFile(context, id) },
                    onShareListWithFriends = { friendUids, id, fromName ->
                        taskViewModel.startShareList(context, id, friendUids, fromName)
                    },
                    onLoadSharedMembers = { uuid -> taskViewModel.getSharedListMemberStatuses(uuid) },
                    onApplySharingChanges = { uuid, added, removed ->
                        taskViewModel.applySharingChanges(uuid, added, removed)
                    },
                    onAddList = { name -> scope.launch { taskViewModel.createNewList(name) } },
                    onDismiss = { showListManager = false },
                    friends = friends,
                    userNickname = userProfile?.nickname,
                    onLeaveSharedList = { listId -> taskViewModel.leaveSharedList(listId) }
            )
        }

        if (showRequestsDialog) {
            RequestsSummaryDialog(
                    friendRequests = incomingFriendRequests,
                    sharedListInvites = incomingShareRequests,
                    onDismiss = { requestsViewModel.dismissDialog() },
                    onViewRequests = {
                        requestsViewModel.dismissDialog()
                        showDetailedRequestsDialog = true
                    }
            )
        }

        if (showDetailedRequestsDialog) {
            RequestsDetailDialog(
                    viewModel = requestsViewModel,
                    friendRequests = incomingFriendRequests,
                    sharedListInvites = incomingShareRequests,
                    onAcceptFriend = { requestsViewModel.acceptFriendRequest(it) },
                    onDeclineFriend = { requestsViewModel.declineFriendRequest(it) },
                    onAcceptSharedList = { uuid -> requestsViewModel.acceptSharedListInvite(uuid) },
                    onDeclineSharedList = { uuid -> requestsViewModel.declineSharedListInvite(uuid) },
                    onDismiss = { showDetailedRequestsDialog = false }
            )
        }
    }
}
