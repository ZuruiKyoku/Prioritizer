package com.slygames.prioritizer.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slygames.prioritizer.R
import com.slygames.prioritizer.viewmodel.MainViewModel

@Composable
fun AboutTab(
        viewModel: MainViewModel = viewModel(),
        onDeleteAccount: () -> Unit,
        initialDarkMode: Boolean
) {
        val context = LocalContext.current

        var isDarkMode by remember { mutableStateOf(initialDarkMode) }

        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // --- About Prioritizer ---
                        Image(
                                painter =
                                        if (isDarkMode)
                                                painterResource(
                                                        id = R.drawable.logo_prioritizer_dark
                                                )
                                        else painterResource(id = R.drawable.logo_prioritizer),
                                contentDescription = "Prioritizer Logo",
                                modifier = Modifier.height(100.dp).padding(bottom = 16.dp)
                        )

                        Text(
                                text = "Focus on what matters most.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                                text =
                                        "Prioritizer helps you organize your tasks with shared lists, smart sorting, and cloud sync. Designed for simplicity and effectiveness, it ensures you never lose track of your goals.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                        )

                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))

                        // --- About Sly Games ---
                        Text(
                                text = "Developed by",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clickable Logo Container
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                        Modifier.clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                        try {
                                                                context.startActivity(
                                                                        Intent(
                                                                                Intent.ACTION_VIEW,
                                                                                Uri.parse(
                                                                                        "market://search?q=pub:SlyGames"
                                                                                )
                                                                        )
                                                                )
                                                        } catch (e: Exception) {
                                                                context.startActivity(
                                                                        Intent(
                                                                                Intent.ACTION_VIEW,
                                                                                Uri.parse(
                                                                                        "https://play.google.com/store/search?q=pub:SlyGames"
                                                                                )
                                                                        )
                                                                )
                                                        }
                                                }
                                                .padding(8.dp)
                        ) {
                                Image(
                                        painter = painterResource(id = R.drawable.sly_games_logo),
                                        contentDescription = "Sly Games Logo",
                                        modifier = Modifier.height(80.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                                text = "Check Out My Other Apps",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "Go to Play Store",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                        )
                                }
                        }

                        Text(
                                text =
                                        "Sly Games serves as a portfolio for indie developer Nathan Mester. Dedicated to creating clean, user-friendly, and enjoyable experiences.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                        )

                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Privacy Policy ---
                        TextButton(
                                onClick = {
                                        val intent =
                                                Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(
                                                                "https://slygamesdev.notion.site/Privacy-Policy-for-Prioritizer-24943a2cda59805eb6b8cf2fcb595317"
                                                        )
                                                )
                                        context.startActivity(intent)
                                }
                        ) { Text("Privacy Policy") }

                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Delete Account ---
                        val isGuest =
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ==
                                        null
                        Button(
                                onClick = onDeleteAccount,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                        ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                                Text(
                                        if (isGuest) "Delete Data" else "Delete Account",
                                        color = MaterialTheme.colorScheme.onError
                                )
                        }

                        Spacer(Modifier.height(16.dp))

                        val versionName =
                                try {
                                        context.packageManager.getPackageInfo(
                                                        context.packageName,
                                                        0
                                                )
                                                .versionName
                                } catch (e: Exception) {
                                        "Unknown"
                                }

                        Text(
                                text = "Ver: $versionName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                        )
                }
        }
}
