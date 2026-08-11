package com.slygames.prioritizer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.data.list.TaskListEntity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ListManagerItem(
    list: TaskListEntity,
    currentUid: String?,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    val isOwner = (currentUid == list.ownerId)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        if (dragOffset.value != 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .pointerInput(list.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta ->
                            scope.launch {
                                val newOffset = (dragOffset.value + delta).coerceAtMost(0f)
                                dragOffset.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (dragOffset.value <= -200f) {
                                    showConfirmDialog = true
                                }
                                dragOffset.animateTo(0f)
                            }
                        }
                    )
                }
                .clickable { onSelect(list.id) }
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Title
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outline
                )

                // Row 1: To Do + count + Share/Shared
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "To Do:",
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        "${list.todoCount}",
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier.width(64.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (list.isShared) {
                            if (isOwner) {
                                // Full-size (48dp) touch target with a smaller glyph inside it,
                                // rather than shrinking the IconButton itself below Android's
                                // minimum recommended touch target.
                                IconButton(onClick = { onShare(list.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.ManageAccounts,
                                        contentDescription = "Manage sharing for ${list.name}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "Shared ✅",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = { onShare(list.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share ${list.name}",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Row 2: Done + count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Done:",
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        "${list.completedCount}",
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.weight(1f)) // Aligns with icon column
                }

                // Row 3: Total + count + Edit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total:",
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        "${list.totalCount}",
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.weight(1f)) // takes remaining space between count and icon

                    // Tap-based delete/leave alongside the swipe gesture above -- swipe-only
                    // actions are unreachable for TalkBack and keyboard/switch users.
                    IconButton(onClick = { showConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription =
                                if (list.isShared && !isOwner) "Leave ${list.name}" else "Delete ${list.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { onRename(list.id, list.name) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename ${list.name}",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (showConfirmDialog) {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val isOwner = list.ownerId == currentUid
            val isShared = list.isShared
            val actionText = when {
                isShared && isOwner -> "Delete"
                isShared && !isOwner -> "Leave"
                else -> "Delete"
            }

            val titleText = when {
                isShared && isOwner -> "Delete Shared List for Everyone"
                isShared && !isOwner -> "Leave Shared List"
                else -> "Delete List"
            }

            val messageText = when {
                isShared && isOwner -> "Are you sure you want to delete '${list.name}'? This will remove it for all shared members."
                isShared && !isOwner -> "Are you sure you want to leave '${list.name}'? You will no longer see its updates."
                else -> "Are you sure you want to delete '${list.name}'? This cannot be undone."
            }

            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text(titleText) },
                text = { Text(messageText) },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog = false
                        onDelete(list.id)
                    }) {
                        Text(actionText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showConfirmDialog = false
                        scope.launch { dragOffset.animateTo(0f, animationSpec = tween(300)) }
                    }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}