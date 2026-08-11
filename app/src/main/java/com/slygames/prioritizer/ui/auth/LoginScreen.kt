package com.slygames.prioritizer.ui.auth

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.slygames.prioritizer.R
import com.slygames.prioritizer.viewmodel.AuthUiState
import com.slygames.prioritizer.viewmodel.AuthViewModel
import com.slygames.prioritizer.viewmodel.SettingsViewModel

@Composable
fun LoginScreen(
        initialDarkMode: Boolean,
        onLoginSuccess: () -> Unit,
        onNavigateToRegister: () -> Unit,
        onNavigateToForgotPassword: () -> Unit,
        viewModel: AuthViewModel = hiltViewModel(),
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var isDarkMode by remember { mutableStateOf(initialDarkMode) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    viewModel.signInWithGoogleIdToken(account.idToken ?: "")
                } catch (e: Exception) {
                    Log.e("LoginScreen", "Google sign-in failed", e)
                    Toast.makeText(context, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.SignedIn -> onLoginSuccess()
            is AuthUiState.Error -> Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                    painter =
                            if (isDarkMode) painterResource(id = R.drawable.logo_prioritizer_dark)
                            else painterResource(id = R.drawable.logo_prioritizer),
                    contentDescription = "Prioritizer Logo",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(max = 50.dp)
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onNavigateToForgotPassword, enabled = !isLoading, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                    onClick = { viewModel.signInWithEmail(email, password) },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Log In")
                }
            }

            TextButton(onClick = onNavigateToRegister, enabled = !isLoading) { Text("Don't have an account? Sign up") }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("  or  ", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            Button(
                    onClick = { launcher.launch(googleSignInClient.signInIntent) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Unspecified),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    border = null,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.wrapContentSize()
            ) {
                Image(
                        painter =
                                if (isDarkMode) painterResource(id = R.drawable.android_light_rd_si)
                                else painterResource(id = R.drawable.android_dark_rd_si),
                        contentDescription = "Google Sign-In Image",
                        modifier = Modifier.sizeIn(maxWidth = 300.dp).aspectRatio(4.725f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = { viewModel.continueAsGuest() }, enabled = !isLoading, modifier = Modifier.width(200.dp)) {
                Text("Continue as Guest")
            }

            Spacer(Modifier.height(24.dp))

            IconToggleButton(
                    checked = isDarkMode,
                    onCheckedChange = {
                        isDarkMode = it
                        settingsViewModel.setDarkMode(it)
                    },
                    modifier = Modifier.size(56.dp)
            ) {
                Icon(
                        imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = if (isDarkMode) "Dark Mode" else "Light Mode",
                        tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
