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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.slygames.prioritizer.viewmodel.ProfileViewModel

@Composable
fun GeneralSettingsTab(
        isDarkMode: Boolean,
        isAutoSwitch: Boolean,
        onToggleDarkMode: (Boolean) -> Unit,
        onToggleAutoSwitch: (Boolean) -> Unit,
        onLogout: () -> Unit,
        profileViewModel: ProfileViewModel = hiltViewModel(),
) {
        var isLoggingOut by remember { mutableStateOf(false) }

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
                                        isLoggingOut = true
                                        profileViewModel.logout { onLogout() }
                                },
                                enabled = !isLoggingOut,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                        ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Log Out", color = MaterialTheme.colorScheme.onSecondary) }

                        Spacer(Modifier.height(12.dp))
                }
        }
}
