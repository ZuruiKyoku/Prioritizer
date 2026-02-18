package com.slygames.prioritizer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.PrioritizerApp
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.ui.theme.*

@Composable
fun TaskItem(
    task: TaskEntity,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (TaskEntity) -> Unit,
    listId: Long
) {
    val context = LocalContext.current
    val userPrefs = (context.applicationContext as PrioritizerApp).userPrefs
    val isDarkMode by userPrefs.isDarkModeFlow.collectAsState(initial = false)

    val bgColor = if (task.done) {
        priorityCompletedBGColor(isDarkMode)
    } else {
        priorityBackgroundColor(task.priority, isDarkMode)
    }

    val foreColor = if (task.done) {
        priorityCompletedForeColor(isDarkMode)
    } else {
        priorityForeColor(isDarkMode)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = bgColor,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (task.done) 1.dp else 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "(${task.priority}) ${task.description}",
                style = TextStyle(
                    color = foreColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit(task) }
            )

            IconButton(onClick = onToggleDone) {
                Icon(
                    imageVector = if (task.done) Icons.AutoMirrored.Filled.Undo else Icons.Default.CheckCircle,
                    contentDescription = if (task.done) "Undo" else "Mark as Done",
                    tint = foreColor
                )
            }
        }
    }
}
