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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.data.task.ExportTask
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.ui.TaskListScreen
import com.slygames.prioritizer.ui.auth.LoginScreen
import com.slygames.prioritizer.ui.profile.ProfileScreen
import com.slygames.prioritizer.ui.splash.StandardSplashScreen
import com.slygames.prioritizer.ui.theme.PrioritizerTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseApp.initializeApp(this)

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

        // Handle deep link import if launched with ACTION_VIEW
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val inputStream = contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.readText()
                inputStream?.close()

                val imported = Json.decodeFromString<List<ExportTask>>(content ?: "")
                val db = AppDatabase.getInstance(this)
                val dao = db.taskDao()

                lifecycleScope.launch {
                    val listId =
                            dao.insertTaskList(
                                    TaskListEntity(
                                            name = "Imported List ${System.currentTimeMillis()}",
                                            id = 0,
                                            todoCount = 0,
                                            completedCount = 0,
                                            totalCount = 0
                                    )
                            )
                    imported.forEach {
                        dao.insertTask(
                                TaskEntity(
                                        description = it.description,
                                        priority = it.priority,
                                        done = it.done,
                                        listId = listId
                                )
                        )
                    }
                }
                intent.data = null
            }
        }

        val launchAction = intent?.action

        setContent {
            val userPrefs = (applicationContext as PrioritizerApp).userPrefs
            val isDark by userPrefs.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = false)

            PrioritizerTheme(darkTheme = isDark) {
                Surface(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val auth = remember { FirebaseAuth.getInstance() }
                    val isLoggedIn by
                            userPrefs.isLoggedInFlow.collectAsStateWithLifecycle(
                                    initialValue = false
                            )
                    val scope = rememberCoroutineScope()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            StandardSplashScreen(
                                    logoResId = R.drawable.sly_games_logo,
                                    navController = navController,
                                    launchAction = launchAction,
                                    userPrefs = userPrefs,
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
                                    showFriendPopup =
                                            action ==
                                                    "com.slygames.prioritizer.SHOW_FRIEND_REQUESTS"
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                    onLogout = {
                                        scope.launch {
                                            userPrefs.setLoggedIn(false)
                                            navController.navigate("login") {
                                                popUpTo("profile") { inclusive = true }
                                            }
                                        }
                                    }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                    initialDarkMode = isDark,
                                    onLoginSuccess = {
                                        navController.navigate("tasks") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
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
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            ) {
                Log.d("FCM", "Notifications permission granted")
            } else {
                Toast.makeText(this, "Please enable notifications in settings", Toast.LENGTH_LONG)
                        .show()
            }
        }
    }
}
