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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.ui.components.ResponsiveContent
import com.slygames.prioritizer.ui.dialogs.EditProfileDialog
import com.slygames.prioritizer.util.loadProfileImage
import com.slygames.prioritizer.viewmodel.ProfileViewModel
import com.slygames.prioritizer.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
        onLogout: () -> Unit,
        profileViewModel: ProfileViewModel = hiltViewModel(),
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("General", "Friends", "About")
    val isDarkMode by settingsViewModel.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val isAutoSwitch by settingsViewModel.isAutoSwitch.collectAsStateWithLifecycle(initialValue = false)

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showReauthDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val userProfile by profileViewModel.userProfile.collectAsStateWithLifecycle()

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

    Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding()
    ) {
        ResponsiveContent {
        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
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
                                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    } else {
                        Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Default Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier =
                                        Modifier.size(64.dp)
                                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = userProfile?.nickname ?: "User", style = MaterialTheme.typography.titleMedium)
                    Text(
                            text =
                                    when {
                                        FirebaseAuth.getInstance().currentUser == null -> "Logged in as Guest"
                                        FirebaseAuth.getInstance().currentUser?.providerData?.any {
                                            it.providerId == "google.com"
                                        } == true -> "Logged in via: Google"
                                        else -> "Logged in via: Email"
                                    },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    userProfile?.friendCode?.takeIf { it.isNotBlank() }?.let { code ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                                text = "Your Friend Code: $code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                        Modifier.clickable {
                                            clipboard.setText(AnnotatedString(code))
                                            Toast.makeText(context, "Friend code copied!", Toast.LENGTH_SHORT).show()
                                        }
                        )
                    }
                }

                TextButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            Spacer(Modifier.height(24.dp))

            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    0 ->
                            GeneralSettingsTab(
                                    isDarkMode = isDarkMode,
                                    isAutoSwitch = isAutoSwitch,
                                    onToggleDarkMode = { newMode -> settingsViewModel.setDarkMode(newMode) },
                                    onToggleAutoSwitch = { newMode -> settingsViewModel.setAutoSwitch(newMode) },
                                    onLogout = onLogout,
                            )
                    1 -> FriendsTab()
                    2 ->
                            AboutTab(
                                    onDeleteAccount = { showDeleteAccountDialog = true },
                                    initialDarkMode = isDarkMode
                            )
                }
            }
        }
        }

        if (showEditDialog) {
            EditProfileDialog(
                    initialProfile = userProfile,
                    onDismiss = { showEditDialog = false },
                    onSave = { newNickname, imageBytes ->
                        profileViewModel.updateProfile(newNickname, imageBytes)
                        scope.launch {
                            delay(100) // Give the file time to write
                            reloadImageKey++
                        }
                        showEditDialog = false
                    }
            )
        }

        if (showDeleteAccountDialog) {
            val isGuest = FirebaseAuth.getInstance().currentUser == null
            var deleteLocalData by remember { mutableStateOf(false) }

            AlertDialog(
                    onDismissRequest = { showDeleteAccountDialog = false },
                    title = { Text(if (isGuest) "Delete All Data" else "Delete Account") },
                    text = {
                        Column {
                            Text(
                                    if (isGuest)
                                            "Are you sure you want to delete all your data? This will permanently remove all your lists and settings from this device. This action cannot be undone."
                                    else
                                            "Are you sure you want to delete your account? This will permanently delete your shared lists, profile, and remove you from other lists. This action cannot be undone."
                            )
                            if (!isGuest) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = deleteLocalData, onCheckedChange = { deleteLocalData = it })
                                    Text("Delete local data from this device?")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    showDeleteAccountDialog = false
                                    isDeleting = true
                                    if (isGuest) {
                                        profileViewModel.deleteGuestData {
                                            scope.launch {
                                                onLogout()
                                                isDeleting = false
                                            }
                                        }
                                    } else {
                                        profileViewModel.deleteAccount(
                                                deleteLocalData = deleteLocalData,
                                                onSuccess = {
                                                    scope.launch {
                                                        if (deleteLocalData) {
                                                            onLogout()
                                                        } else {
                                                            isDeleting = false
                                                            showDeleteAccountDialog = false
                                                            Toast.makeText(context, "Switched to Guest Mode", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                onError = { error ->
                                                    isDeleting = false
                                                    if (error == "For security, please log in again before continuing.") {
                                                        showReauthDialog = true
                                                    } else {
                                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(if (isGuest) "Delete Data" else "Delete Forever") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") } }
            )
        }

        if (showReauthDialog) {
            AlertDialog(
                    onDismissRequest = { /* Force action */},
                    title = { Text("Security Check") },
                    text = { Text("For your security, you must log in again before you can delete your account. We will log you out now.") },
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
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Deleting account...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
