package com.slygames.prioritizer.data

data class TaskListWithCounts(
    val id: Long,
    val name: String,
    val todoCount: Int,
    val completedCount: Int,
    val totalCount: Int
)