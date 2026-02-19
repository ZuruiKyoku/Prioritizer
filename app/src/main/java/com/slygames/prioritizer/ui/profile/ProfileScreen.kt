package com.slygames.prioritizer.ui.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.slygames.prioritizer.PrioritizerApp
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.ui.dialogs.EditProfileDialog
import com.slygames.prioritizer.util.loadProfileImage
import com.slygames.prioritizer.viewmodel.MainViewModel
import com.slygames.prioritizer.viewmodel.MainViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPrefs = (context.applicationContext as PrioritizerApp).userPrefs
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("General", "Friends", "About")
    val isDarkMode by userPrefs.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = false)

    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.taskDao() }
    val repository = remember { TaskRepository(dao) }
    val database = remember { AppDatabase.getInstance(context) }
    val factory = remember {
        MainViewModelFactory(appContext = context, taskRepository = repository, database = database)
    }
    val viewModel: MainViewModel = viewModel(factory = factory)

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showReauthDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var friendCode by remember { mutableStateOf<String?>(null) }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    var reloadImageKey by remember { mutableStateOf(0) }
    val bitmap by
            produceState<ImageBitmap?>(
                    initialValue = null,
                    userProfile?.profileImagePath,
                    reloadImageKey
            ) {
                value =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            loadProfileImage(userProfile?.profileImagePath)
                        }
            }

    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { doc -> friendCode = doc.getString("friendCode") }
        }
    }

    Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding()
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                // Profile Picture
                Box(
                        modifier =
                                Modifier.size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                                bitmap = bitmap!!,
                                contentDescription = "Profile Picture",
                                modifier =
                                        Modifier.fillMaxSize()
                                                .border(
                                                        2.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                )
                        )
                    } else {
                        Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Default Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier =
                                        Modifier.size(64.dp)
                                                .border(
                                                        2.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                )
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Nickname & login status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = userProfile?.nickname ?: "User",
                            style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                            text =
                                    if (FirebaseAuth.getInstance().currentUser?.providerData?.any {
                                                it.providerId == "google.com"
                                            } == true
                                    )
                                            "Logged in via: Google"
                                    else "Logged in as Guest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    friendCode?.let { code ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                                text = "Your Friend Code: $code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                        Modifier.clickable {
                                            clipboard.setText(AnnotatedString(code))
                                            Toast.makeText(
                                                            context,
                                                            "Friend code copied!",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                        )
                    }
                }

                // Edit Profile Button
                TextButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Tabs ---
            TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Tab Content ---
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    0 ->
                            GeneralSettingsTab(
                                    onToggleDarkMode = { newMode ->
                                        scope.launch { userPrefs.setDarkMode(newMode) }
                                    },
                                    onToggleAutoSwitch = { newMode ->
                                        scope.launch { userPrefs.setAutoSwitch(newMode) }
                                    },
                                    onLogout = onLogout,
                            )
                    1 -> FriendsTab(viewModel)
                    2 ->
                            AboutTab(
                                    viewModel,
                                    onDeleteAccount = { showDeleteAccountDialog = true },
                                    initialDarkMode = isDarkMode
                            )
                }
            }
        }

        if (showEditDialog) {
            EditProfileDialog(
                    initialProfile = userProfile,
                    onDismiss = { showEditDialog = false },
                    onSave = { newNickname, imageBytes ->
                        viewModel.updateUserProfile(newNickname, imageBytes, context)
                        scope.launch {
                            delay(100) // Give the file time to write
                            reloadImageKey++
                        }
                        showEditDialog = false
                    }
            )
        }

        if (showDeleteAccountDialog) {
            var deleteLocalData by remember { mutableStateOf(false) }

            AlertDialog(
                    onDismissRequest = { showDeleteAccountDialog = false },
                    title = { Text("Delete Account") },
                    text = {
                        Column {
                            Text(
                                    "Are you sure you want to delete your account? This will permanently delete your shared lists, profile, and remove you from other lists. This action cannot be undone."
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                        checked = deleteLocalData,
                                        onCheckedChange = { deleteLocalData = it }
                                )
                                Text("Delete local data from this device?")
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    showDeleteAccountDialog = false
                                    isDeleting = true // Show loading
                                    viewModel.deleteAccount(
                                            deleteLocalData = deleteLocalData,
                                            onSuccess = {
                                                scope.launch {
                                                    userPrefs.setLoggedIn(false)
                                                    userPrefs.setLoginType("")
                                                    // Dismiss loading is implicit by navigating
                                                    // away/logout
                                                    isDeleting = false
                                                    onLogout() // Navigate to login
                                                }
                                            },
                                            onError = { error ->
                                                isDeleting = false
                                                if (error == "Re-authentication required") {
                                                    showReauthDialog = true
                                                } else {
                                                    Toast.makeText(
                                                                    context,
                                                                    error,
                                                                    Toast.LENGTH_LONG
                                                            )
                                                            .show()
                                                }
                                            }
                                    )
                                },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                        )
                        ) { Text("Delete Forever") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
                    }
            )
        }

        if (showReauthDialog) {
            AlertDialog(
                    onDismissRequest = { /* Force action */},
                    title = { Text("Security Check") },
                    text = {
                        Text(
                                "For your security, you must log in again before you can delete your account. We will log you out now."
                        )
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    showReauthDialog = false
                                    onLogout()
                                }
                        ) { Text("Log Out & Retry") }
                    }
            )
        }

        if (isDeleting) {
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                ) {
                    Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Deleting account...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
