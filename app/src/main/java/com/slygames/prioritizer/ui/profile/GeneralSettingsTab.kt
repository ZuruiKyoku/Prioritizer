package com.slygames.prioritizer.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.PrioritizerApp
import com.slygames.prioritizer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun GeneralSettingsTab(
        onToggleDarkMode: (Boolean) -> Unit,
        onToggleAutoSwitch: (Boolean) -> Unit,
        viewModel: MainViewModel = viewModel(),
        onLogout: () -> Unit,
        onDeleteAccount: () -> Unit
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val userPrefs = (context.applicationContext as PrioritizerApp).userPrefs
        val isDarkMode by userPrefs.isDarkModeFlow.collectAsState(initial = false)
        val isAutoSwitch by userPrefs.isAutoSwitch.collectAsState(initial = false)

        var nickname by rememberSaveable { mutableStateOf("") }
        var showDialog by remember { mutableStateOf(false) }
        var importInfo by remember { mutableStateOf(listOf<String>()) }

        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        // --- Dark Mode ---
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Text(
                                        "Dark Mode",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                                text = if (isDarkMode) "🌙 Dark" else "☀️ Light",
                                                modifier = Modifier.padding(end = 8.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Switch(
                                                checked = isDarkMode,
                                                onCheckedChange = onToggleDarkMode,
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                uncheckedThumbColor =
                                                                        MaterialTheme.colorScheme
                                                                                .outline,
                                                                checkedTrackColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.4f
                                                                        ),
                                                                uncheckedTrackColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                        )
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Auto Switch ---
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Text(
                                        "Auto-Open Shared List",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                                text = if (isAutoSwitch) "✅" else "❌",
                                                modifier = Modifier.padding(end = 8.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Switch(
                                                checked = isAutoSwitch,
                                                onCheckedChange = onToggleAutoSwitch,
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                uncheckedThumbColor =
                                                                        MaterialTheme.colorScheme
                                                                                .outline,
                                                                checkedTrackColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.4f
                                                                        ),
                                                                uncheckedTrackColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                        )
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // --- Logout Button ---
                        Button(
                                onClick = {
                                        scope.launch {
                                                userPrefs.setLoggedIn(false)
                                                userPrefs.setLoginType("")
                                                FirebaseAuth.getInstance().signOut()
                                                onLogout()
                                        }
                                },
                                // Use a neutral or primary color for logout, keep error for delete
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                        ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Log Out", color = MaterialTheme.colorScheme.onSecondary) }

                        Spacer(Modifier.height(12.dp))

                        // --- Delete Account Button ---
                        Button(
                                onClick = onDeleteAccount,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                        ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Delete Account", color = MaterialTheme.colorScheme.onError) }

                        Spacer(Modifier.height(26.dp))

                        val context = LocalContext.current
                        val versionName =
                                context.packageManager.getPackageInfo(context.packageName, 0)
                                        .versionName

                        Text(text = "Ver: $versionName", modifier = Modifier.height(24.dp))
                }
        }
}
