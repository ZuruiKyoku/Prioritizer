package com.slygames.prioritizer.ui.splash

import android.net.Uri
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.PrioritizerApp
import com.slygames.prioritizer.data.user.UserPreferences
import kotlinx.coroutines.delay

@Composable
fun StandardSplashScreen(
        logoResId: Int,
        navController: NavHostController,
        launchAction: String?,
        userPrefs: UserPreferences,
        navigateTo: String = "home",
        fallbackScreen: String = "login",
        delayMillis: Long = 3000L
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var navigating by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val viewModel = (context.applicationContext as PrioritizerApp).mainViewModel
    viewModel.resetRequestsDialog()

    // Animate gradient fill % (0f to 1f)
    val gradientProgress by
            animateFloatAsState(
                    targetValue = if (visible && !navigating) 1f else 0f,
                    animationSpec = tween(durationMillis = 1000),
                    label = "Gradient Fill"
            )

    LaunchedEffect(Unit) {
        delay(100) // Give Compose one frame to compose
        visible = true

        val minDisplayTimeMillis = 3000L
        val startTime = System.currentTimeMillis()

        val loginType = userPrefs.getLoginType()
        val signedIn =
                when (loginType) {
                    "google" -> FirebaseAuth.getInstance().currentUser != null
                    "guest" -> true
                    else -> false
                }

        if (loginType == "google" && signedIn) {
            isSyncing = true
            val viewModel = (context.applicationContext as PrioritizerApp).mainViewModel
            viewModel.restoreFromCloud(context)
            isSyncing = false
        }

        val elapsed = System.currentTimeMillis() - startTime
        val remaining = minDisplayTimeMillis - elapsed
        if (remaining > 0) delay(remaining)

        navigating = true
        visible = false // Start fade out + gradient drain

        delay(1000) // Wait for full fade-out

        val destination = if (signedIn) navigateTo else fallbackScreen
        val actionSuffix = launchAction?.let { "?action=${Uri.encode(it)}" } ?: ""

        navController.navigate(destination + actionSuffix) {
            popUpTo("splash") { inclusive = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated gradient background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fullHeight = size.height
            val fillHeight = fullHeight * gradientProgress
            val topOffset = fullHeight - fillHeight

            drawRect(
                    brush =
                            Brush.verticalGradient(
                                    colors = listOf(Color(0xFF102426), Color(0xFF0A0E0E)),
                                    startY = 0f,
                                    endY = fullHeight
                            ),
                    topLeft = Offset(0f, topOffset),
                    size = Size(size.width, fillHeight)
            )
        }

        // Manual scale & alpha animation
        val logoScale by
                animateFloatAsState(
                        targetValue = if (visible) 1f else 0.8f,
                        animationSpec = tween(durationMillis = 1000, easing = EaseOutBack),
                        label = "LogoScale"
                )

        val contentAlpha by
                animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = tween(durationMillis = 1000),
                        label = "ContentAlpha"
                )

        Column(
                modifier =
                        Modifier.fillMaxSize().padding(32.dp).graphicsLayer {
                            alpha = contentAlpha
                        },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Studio Logo",
                    modifier =
                            Modifier.graphicsLayer {
                                        scaleX = logoScale
                                        scaleY = logoScale
                                    }
                                    .fillMaxWidth(1.0f)
                                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isSyncing) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                        text = "Syncing your data...",
                        color = Color.White.copy(alpha = contentAlpha),
                        modifier = Modifier.alpha(contentAlpha)
                )
            }
        }
    }
}
