package com.slygames.prioritizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.slygames.prioritizer.data.task.ExportTask
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.ui.TaskListScreen
import com.slygames.prioritizer.ui.auth.ForgotPasswordScreen
import com.slygames.prioritizer.ui.auth.LoginScreen
import com.slygames.prioritizer.ui.auth.RegisterScreen
import com.slygames.prioritizer.ui.profile.ProfileScreen
import com.slygames.prioritizer.ui.splash.StandardSplashScreen
import com.slygames.prioritizer.ui.theme.PrioritizerTheme
import com.slygames.prioritizer.viewmodel.AuthViewModel
import com.slygames.prioritizer.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1001
                )
            }
        }

        // Handle deep link import if launched with ACTION_VIEW (opening a shared .prioritizer file)
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val inputStream = contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.readText()
                inputStream?.close()

                val imported = runCatching { Json.decodeFromString<List<ExportTask>>(content ?: "") }.getOrNull()
                if (imported != null) {
                    lifecycleScope.launch {
                        val listId =
                                taskRepository.addTaskList(
                                        name = "Imported List ${System.currentTimeMillis()}",
                                        ownerId = null
                                )
                        imported.forEach {
                            taskRepository.addTask(
                                    description = it.description,
                                    priority = it.priority,
                                    listId = listId,
                                    done = it.done
                            )
                        }
                    }
                }
                intent.data = null
            }
        }

        val launchAction = intent?.action

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val isDark by settingsViewModel.isDarkMode.collectAsStateWithLifecycle(initialValue = false)

            PrioritizerTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            val authViewModel: AuthViewModel = hiltViewModel()
                            StandardSplashScreen(
                                    logoResId = R.drawable.sly_games_logo,
                                    navController = navController,
                                    launchAction = launchAction,
                                    authViewModel = authViewModel,
                                    navigateTo = "tasks",
                                    fallbackScreen = "login"
                            )
                        }
                        composable(
                                "tasks?action={action}",
                                arguments = listOf(navArgument("action") { nullable = true })
                        ) {
                            val action = it.arguments?.getString("action")
                            TaskListScreen(
                                    initialDarkMode = isDark,
                                    navController = navController,
                                    showFriendPopup = action == "com.slygames.prioritizer.SHOW_FRIEND_REQUESTS"
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                    onLogout = {
                                        scope.launch {
                                            navController.navigate("login") { popUpTo(0) { inclusive = true } }
                                        }
                                    }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                    initialDarkMode = isDark,
                                    onLoginSuccess = {
                                        navController.navigate("tasks") { popUpTo("login") { inclusive = true } }
                                    },
                                    onNavigateToRegister = { navController.navigate("register") },
                                    onNavigateToForgotPassword = { navController.navigate("forgotPassword") }
                            )
                        }
                        composable("register") {
                            RegisterScreen(
                                    initialDarkMode = isDark,
                                    onRegisterSuccess = {
                                        navController.navigate("tasks") { popUpTo("login") { inclusive = true } }
                                    },
                                    onBackToLogin = { navController.popBackStack() }
                            )
                        }
                        composable("forgotPassword") {
                            ForgotPasswordScreen(
                                    initialDarkMode = isDark,
                                    onBackToLogin = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d("FCM", "Notifications permission granted")
            } else {
                Toast.makeText(this, "Please enable notifications in settings", Toast.LENGTH_LONG).show()
            }
        }
    }
}
