package com.slygames.prioritizer.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.slygames.prioritizer.ui.theme.DarkColors
import com.slygames.prioritizer.ui.theme.LightColors
import com.slygames.prioritizer.ui.theme.priorityBackgroundColor
import com.slygames.prioritizer.ui.theme.priorityForeColor

@Composable
fun TaskListScreenPreviewContent(
    isDarkMode: Boolean
) {
    val colors = if (isDarkMode) DarkColors else LightColors
    val foreColor = priorityForeColor(isDarkMode)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = if (isDarkMode) "Task List (Dark Mode)" else "Task List (Light Mode)",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = foreColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            sampleTasks.forEach { task ->
                TaskCardPreview(
                    title = task.title,
                    priority = task.priority,
                    cardColor = priorityBackgroundColor(task.priority, isDarkMode),
                    surfaceColor = colors.surface,
                    foreColor = foreColor
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun TaskCardPreview(
    title: String,
    priority: Int,
    cardColor: Color,
    surfaceColor: Color,
    foreColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "($priority) $title",
                style = TextStyle(
                    color = foreColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Mark as Done",
                tint = foreColor
            )
        }
    }
}

private val sampleTasks = listOf(
    PreviewTask("Walk the dog", 1),
    PreviewTask("Do the dishes", 4),
    PreviewTask("Finish project", 7),
    PreviewTask("Call mom", 10)
)

data class PreviewTask(val title: String, val priority: Int)

@Preview(showBackground = true)
@Composable
fun TaskListScreenPreviewLight() {
    TaskListScreenPreviewContent(isDarkMode = false)
}

@Preview(showBackground = true)
@Composable
fun TaskListScreenPreviewDark() {
    TaskListScreenPreviewContent(isDarkMode = true)
}