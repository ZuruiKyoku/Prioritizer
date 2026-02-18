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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.messaging.FirebaseMessaging
import com.slygames.prioritizer.PrioritizerApp
import com.slygames.prioritizer.R
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.ui.components.TaskItem
import com.slygames.prioritizer.ui.dialogs.AddTaskDialog
import com.slygames.prioritizer.ui.dialogs.EditTaskDialog
import com.slygames.prioritizer.ui.dialogs.ListManagerDialog
import com.slygames.prioritizer.ui.dialogs.RequestsDetailDialog
import com.slygames.prioritizer.ui.dialogs.RequestsSummaryDialog
import com.slygames.prioritizer.util.loadProfileImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
        initialDarkMode: Boolean,
        navController: NavController,
        showFriendPopup: Boolean
) {
    val context = LocalContext.current
    val viewModel = (context.applicationContext as PrioritizerApp).mainViewModel

    val selectedListId by viewModel.selectedListId.collectAsStateWithLifecycle()
    val selectedListName by viewModel.selectedListName.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val lists by viewModel.taskLists.collectAsStateWithLifecycle() // ← unified source of truth

    var showDialog by remember { mutableStateOf(false) }
    var showListManager by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val userPrefs = (context.applicationContext as PrioritizerApp).userPrefs

    var isDarkMode by remember { mutableStateOf(initialDarkMode) }
    var newTaskText by remember { mutableStateOf("") }
    var newTaskPriority by remember { mutableStateOf(5f) }
    var selectedTab by remember { mutableStateOf(0) }
    var taskBeingEdited by remember { mutableStateOf<TaskEntity?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Requests
    val showRequestsDialog by viewModel.showRequestsDialog.collectAsStateWithLifecycle()
    var showDetailedRequestsDialog by remember { mutableStateOf(false) }
    val incomingFriendRequests by
            viewModel
                    .getIncomingFriendRequests()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
    val incomingShareRequests by
            viewModel
                    .getIncomingSharedLists()
                    .collectAsStateWithLifecycle(initialValue = emptyList())

    val friends by viewModel.getFriendList().collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        launch { userPrefs.isDarkModeFlow.collect { isDarkMode = it } }

        viewModel.loadUserProfile()

        // Wait for restore to finish
        isRestoring = true
        val restoreJob = viewModel.restoreFromCloud(context)
        restoreJob.join()
        isRestoring = false

        // Safe to start mirrors & proceed
        viewModel.ensureSharedMirrorsOnLaunch()
        viewModel.initializeList()
        viewModel.syncAllTaskCounts()

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModel.saveFcmToken(token)
        }
    }

    val hasShownRequestPopup = remember { mutableStateOf(false) }
    LaunchedEffect(incomingFriendRequests, incomingShareRequests) {
        if ((incomingFriendRequests.isNotEmpty() || incomingShareRequests.isNotEmpty()) &&
                        !hasShownRequestPopup.value
        ) {
            hasShownRequestPopup.value = true
        }
    }

    // Automatically close the dialog when no shared invites remain
    LaunchedEffect(incomingShareRequests) {
        if (showDetailedRequestsDialog && incomingShareRequests.isEmpty()) {
            showDetailedRequestsDialog = false
        }
    }

    val currentList = lists.find { it.id == selectedListId }
    val isShared = currentList?.isShared == true

    val activeTasks = tasks.filter { !it.done }.sortedByDescending { it.priority }
    val completedTasks = tasks.filter { it.done }.sortedByDescending { it.priority }
    val shownTasks = if (selectedTab == 0) activeTasks else completedTasks

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val bitmap =
            remember(userProfile?.profileImagePath) {
                loadProfileImage(userProfile?.profileImagePath)
            }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                floatingActionButton = {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Big Profile Button in Bottom Left
                        IconButton(
                                onClick = { navController.navigate("profile") },
                                modifier = Modifier.size(56.dp)
                        ) {
                            if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = "Profile Picture",
                                        modifier =
                                                Modifier.size(80.dp)
                                                        .clip(CircleShape)
                                                        .border(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary,
                                                                CircleShape
                                                        )
                                )
                            } else {
                                Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Profile",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .border(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary,
                                                                CircleShape
                                                        )
                                )
                            }
                        }

                        FilledTonalButton(
                                onClick = { showListManager = true },
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        ) {
                            Text(
                                    text =
                                            selectedListName.takeIf { it.isNotBlank() }?.let {
                                                "$it ▼"
                                            }
                                                    ?: "Select List ▼",
                                    maxLines = 1
                            )
                        }

                        if (!isRestoring) {
                            FloatingActionButton(
                                    onClick = { showDialog = true },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                            ) { Icon(Icons.Default.Add, contentDescription = "Add Task") }
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Image(
                        painter =
                                if (isDarkMode)
                                        painterResource(id = R.drawable.logo_prioritizer_dark)
                                else painterResource(id = R.drawable.logo_prioritizer),
                        contentDescription = "Prioritizer Logo",
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                        .heightIn(max = 30.dp)
                )

                val activeCount = tasks.count { !it.done }
                val completedCount = tasks.count { it.done }

                val tabs = listOf("Active ($activeCount)", "Completed ($completedCount)")

                TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                            text = title,
                                            maxLines = 1 // ensures it stays on a single line
                                    )
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (lists.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(top = 32.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                        "Looks like you don't have any lists yet!",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Button(onClick = { showListManager = true }) {
                                    Text("Create Your First List")
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shownTasks) { task ->
                                TaskItem(
                                        task = task,
                                        onToggleDone = { viewModel.toggleTaskDone(task) },
                                        onDelete = { viewModel.deleteTask(task) },
                                        onEdit = { edited -> taskBeingEdited = edited },
                                        listId = selectedListId ?: 0L
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
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
                            viewModel.addTask(text, priority, listId)
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
                        viewModel.updateTask(updatedTask)
                        taskBeingEdited = null
                    },
                    onDelete = {
                        viewModel.deleteTask(task)
                        taskBeingEdited = null
                    }
            )
        }

        if (showClearConfirmDialog) {
            AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    title = { Text("Clear Completed Tasks") },
                    text = {
                        Text(
                                "Are you sure you want to delete all completed tasks? This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(
                                onClick = {
                                    viewModel.clearCompletedTasks()
                                    showClearConfirmDialog = false
                                }
                        ) { Text("Yes") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
                    }
            )
        }

        if (showListManager) {
            val selectedListId by viewModel.selectedListId.collectAsState()

            ListManagerDialog(
                    allLists = lists,
                    selectedListId = selectedListId,
                    onSelectList = {
                        scope.launch {
                            viewModel.loadList(it)
                            showListManager = false
                        }
                    },
                    onRenameList = { id, name -> viewModel.renameList(id, name) },
                    onDeleteList = { id ->
                        val listToDelete = lists.find { it.id == id } ?: return@ListManagerDialog

                        if (listToDelete.isShared && !listToDelete.uuid.isNullOrBlank()) {
                            val me =
                                    com.google.firebase.auth.FirebaseAuth.getInstance()
                                            .currentUser
                                            ?.uid
                            val isOwner = listToDelete.ownerId == me

                            if (isOwner) {
                                // Owner: delete for everyone (cascade) + local cleanup
                                viewModel.deleteList(listToDelete)
                            } else {
                                // Safety guard: non‑owners should use onLeaveSharedList, but if
                                // this fires:
                                viewModel.leaveSharedList(listToDelete.id)
                            }
                        } else {
                            // Not shared → normal delete
                            viewModel.deleteList(listToDelete)
                        }
                    },
                    onShareListExternally = { id -> viewModel.shareList(context, id) },
                    onShareListWithFriends = { friendUids, id, fromName ->
                        viewModel.startShareList(context, id, friendUids, fromName)
                    },
                    onAddList = { name -> scope.launch { viewModel.createNewList(name) } },
                    onDismiss = { showListManager = false },
                    friends = friends,
                    userNickname = userProfile?.nickname,

                    // Non‑owner: explicit “Leave” path from the dialog row
                    onLeaveSharedList = { listId -> viewModel.leaveSharedList(listId) }
            )
        }

        if (showRequestsDialog) {
            RequestsSummaryDialog(
                    friendRequests = incomingFriendRequests,
                    sharedListInvites = incomingShareRequests,
                    onDismiss = { viewModel.dismissRequestsDialog() },
                    onViewRequests = {
                        viewModel.dismissRequestsDialog()
                        showDetailedRequestsDialog = true
                    }
            )
        }

        if (showDetailedRequestsDialog) {
            RequestsDetailDialog(
                    viewModel = viewModel,
                    friendRequests = incomingFriendRequests,
                    sharedListInvites = incomingShareRequests,
                    onAcceptFriend = { viewModel.acceptFriendRequest(it, context) },
                    onDeclineFriend = { viewModel.declineFriendRequest(it) },
                    onAcceptSharedList = { uuid -> viewModel.acceptSharedListInvite(uuid) },
                    onDeclineSharedList = { uuid -> viewModel.declineSharedListInvite(uuid) },
                    onDismiss = { showDetailedRequestsDialog = false }
            )
        }

        if (isRestoring) {
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .background(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Syncing from cloud…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
