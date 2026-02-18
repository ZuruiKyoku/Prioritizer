package com.slygames.prioritizer.data

import com.slygames.prioritizer.data.task.TaskEntity
import kotlinx.serialization.Serializable

@Serializable data class ExportedTaskList(val name: String, val tasks: List<TaskEntity>)
