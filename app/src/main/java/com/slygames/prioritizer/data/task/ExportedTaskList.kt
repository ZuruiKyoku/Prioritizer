package com.slygames.prioritizer.data.task

import kotlinx.serialization.Serializable

@Serializable
data class ExportedTaskList(
    val name: String,
    val tasks: List<TaskEntity>
)