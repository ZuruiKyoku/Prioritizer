package com.slygames.prioritizer.ui.auth

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.slygames.prioritizer.PrioritizerApp
import com.slygames.prioritizer.R
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    initialDarkMode: Boolean,
    onLoginSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPrefs = (context.applicationContext as PrioritizerApp).userPrefs
    val activity = context as Activity
    val auth = remember { FirebaseAuth.getInstance() }
    val viewModel = remember { (context.applicationContext as PrioritizerApp).mainViewModel }
    var isDarkMode by remember { mutableStateOf(initialDarkMode) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { signInTask ->
                if (signInTask.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null) {
                        Log.e("LoginScreen", "User is null after Firebase sign-in!")
                        Toast.makeText(context, "Sign-in failed: user is null", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    scope.launch {
                        userPrefs.setLoginType("google")
                        userPrefs.setLoggedIn(true)

                        // Cloud restore (VM will handle zero-list seeding for Google if needed)
                        viewModel.restoreFromCloud(context)

                        Toast.makeText(context, "Google sign-in successful", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    }
                } else {
                    Log.e("LoginScreen", "Firebase auth failed", signInTask.exception)
                    Toast.makeText(context, "Login failed: ${signInTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LoginScreen", "Google sign-in failed", e)
            Toast.makeText(context, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        userPrefs.isDarkModeFlow.collect { isDarkMode = it }
        when (userPrefs.getLoginType()) {
            "guest" -> onLoginSuccess()
            "google" -> {
                if (auth.currentUser != null) {
                    scope.launch {
                        // Returning Google user: restore then continue
                        viewModel.restoreFromCloud(context)
                        onLoginSuccess()
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = if (isDarkMode)
                    painterResource(id = R.drawable.logo_prioritizer_dark)
                else
                    painterResource(id = R.drawable.logo_prioritizer),
                contentDescription = "Prioritizer Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .heightIn(max = 50.dp)
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.Unspecified
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                border = null,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.wrapContentSize()
            ) {
                Image(
                    painter = if (isDarkMode)
                        painterResource(id = R.drawable.android_light_rd_si)
                    else
                        painterResource(id = R.drawable.android_dark_rd_si),
                    contentDescription = "Google Sign-In Image",
                    modifier = Modifier
                        .sizeIn(maxWidth = 300.dp)
                        .aspectRatio(4.725f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        userPrefs.setLoginType("guest")
                        userPrefs.setLoggedIn(true)
                        viewModel.markInitialRestoreDoneFromUi()
                        onLoginSuccess()
                    }
                },
                modifier = Modifier.width(200.dp)
            ) {
                Text("Continue as Guest")
            }

            Spacer(Modifier.height(24.dp))

            IconToggleButton(
                checked = isDarkMode,
                onCheckedChange = {
                    isDarkMode = it
                    scope.launch { userPrefs.setDarkMode(it) }
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