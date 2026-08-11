package com.slygames.prioritizer.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slygames.prioritizer.viewmodel.AuthUiState
import com.slygames.prioritizer.viewmodel.AuthViewModel

/** Spec: password reset is a minimum required auth capability. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
        initialDarkMode: Boolean,
        onBackToLogin: () -> Unit,
        viewModel: AuthViewModel = hiltViewModel()
) {
    var email by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading
    val sent = uiState is AuthUiState.ResetEmailSent

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Reset password") },
                        navigationIcon = {
                            IconButton(onClick = onBackToLogin) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                )
            }
    ) { padding ->
        Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
        ) {
            if (sent) {
                Text(
                        "If an account exists for $email, a reset link is on its way. Check your inbox.",
                        style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBackToLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back to log in")
                }
            } else {
                Text(
                        "Enter the email you used to sign up and we'll send you a link to reset your password.",
                        style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                )
                if (uiState is AuthUiState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                            (uiState as AuthUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                        onClick = { viewModel.sendPasswordReset(email) },
                        enabled = !isLoading && email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Send reset link")
                }
            }
        }
    }
}
